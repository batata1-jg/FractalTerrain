package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.List;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteQuadTree;
import me.batata_1.fractal_terrain.math.Blur;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.math.MarchingSquares;
import me.batata_1.fractal_terrain.math.Skeletonizer;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the tiled river/ridge network. Each tile is built by chaining:
 * coarse elevation → Difference-of-Gaussians → ridge/valley thresholding → {@link Skeletonizer}
 * (pass 1) → ridge/valley point trees → {@link FieldLinePlacer} → field thresholding →
 * {@link Skeletonizer} (pass 2) → final per-tile {@link QuadTree} (global coarse-px).
 *
 * <p>This class is the single place that holds the tuning literals (so they can be edited during
 * testing); every helper it builds receives those values through its constructor. Final river tiles
 * are cached in a {@link NonIntersectingInfiniteQuadTree} (cache-only).
 *
 * <p>The "crop to 64×64" at the end is symbolic: final points may physically land in the padded
 * halo, but {@link NonIntersectingInfiniteQuadTree#query} only ever returns points inside the
 * owning tile's logical 64×64 window.
 */
public class GlobalRiverProvider {

    // ---- Tuning knobs (edit here during testing) ----------------------------
    private static final double SIGMA1 = 0.5;
    //  private static final double SIGMA2 = 2.0;
    /** Ridge mask: DoG ≥ +RIDGE_THRESHOLD. */
    private static final double RIDGE_THRESHOLD = 0.0;
    /**
     * Field mask: normalized field ≥ FIELD_THRESHOLD marks a (uniformly thin) field line. After the
     * fwidth pass the sin <em>peaks</em> (the lines, cf. the old {@code sin ≥ 0.8}) become large
     * values, while flat no-hit regions and zero-crossings stay near 0 — so we threshold HIGH.
     */
    private static final double FIELD_THRESHOLD = 0.0;
    /** Arc-length resample spacing for the first (ridge/valley) skeleton pass. */
    private static final double DX1 = 4;
    /** Arc-length resample spacing for the second (field) skeleton pass. */
    private static final double DX2 = 0.25;

    private static final double FREQUENCY = 4;
    private static final double QUERY_RADIUS = 1024.0;
    private static final double LINE_THICKNESS = 1.0;
    private static final int MIN_POLYLINE_LEN = 4;

    /** Radius used by {@link #query} on the final per-tile tree. */
    public static final double FINAL_QUERY_RADIUS = DX2;


    private static final Logger LOG = getLogger(GlobalRiverProvider.class);
    // ---- Derived geometry ---------------------------------------------------
    private static final int TILE_SIZE = 64;
    private final int pad = Blur.padFor(SIGMA1);
    private final int paddedSide = TILE_SIZE + 2 * pad;
    private final double fieldResolution = 1.0 / FieldLinePlacer.UPSAMPLE;

    private final Skeletonizer maskSkeletonizer = new Skeletonizer(MIN_POLYLINE_LEN, DX1);
    /** Valley regions are represented by their border (contour), so trace it with marching squares. */
    private final MarchingSquares valleyTracer = new MarchingSquares(MIN_POLYLINE_LEN, DX1);

    private final Skeletonizer fieldSkeletonizer = new Skeletonizer(MIN_POLYLINE_LEN, DX2);
    private final FieldLinePlacer placer =
            new FieldLinePlacer(paddedSide, paddedSide, fieldResolution, QUERY_RADIUS, LINE_THICKNESS, FREQUENCY);
    /** Reused, cleared per tile so we don't reallocate the intermediate trees each compute. */
    private final ThreadLocal<QuadTree<QuadTreePoint>> ridgeScratch = ThreadLocal.withInitial(this::newLocalTree);

    private final ThreadLocal<QuadTree<QuadTreePoint>> valleyScratch = ThreadLocal.withInitial(this::newLocalTree);

    private final NonIntersectingInfiniteQuadTree<QuadTreePoint> globalRiverTile;

    public GlobalRiverProvider(String path) {
        this.globalRiverTile =
                new NonIntersectingInfiniteQuadTree<>(path, new int[] {1,TILE_SIZE, TILE_SIZE}, this::buildRiverTile);
    }

    public NonIntersectingInfiniteQuadTree<QuadTreePoint> getInfiniteQuadTree() {
        return globalRiverTile;
    }

    /** River points within {@link #FINAL_QUERY_RADIUS} of {@code (cx, cz)} (global coarse-px). */
    public List<QuadTreePoint> query(double cx, double cz) {
        return globalRiverTile.query(cx / 256.0, cz / 256.0, FINAL_QUERY_RADIUS);
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    /** Production tile compute: uses the reused scratch trees and returns only the final tree. */
    private QuadTree<QuadTreePoint> buildRiverTile(TileKey key) {
        final int tx = key.get(X);
        final int tz = key.get(Z);

        final QuadTree<QuadTreePoint> ridgeTree = ridgeScratch.get();
        final QuadTree<QuadTreePoint> valleyTree = valleyScratch.get();
        ridgeTree.clear();
        valleyTree.clear();

        return computeTile(tx, tz, ridgeTree, valleyTree, null);
    }

    /**
     * Full per-tile compute. When {@code stages != null}, every intermediate artifact is recorded
     * into it (for the debug harness). Returns the final per-tile river tree (global coarse-px).
     */
    private QuadTree<QuadTreePoint> computeTile(
            int tx,
            int tz,
            QuadTree<QuadTreePoint> ridgeTree,
            QuadTree<QuadTreePoint> valleyTree,
            @Nullable Stages stages) {
        final int tileOriginCx = tx * TILE_SIZE;
        final int tileOriginCz = tz * TILE_SIZE;

        // 1. padded, ch6-normalized coarse elevation
        final float[] paddedElevation = paddedElevation(tileOriginCx, tileOriginCz);

        // 2. padded Difference-of-Gaussians
        // final float[] paddedBlur = DifferenceOfGaussians.run(paddedElevation, paddedSide,paddedSide,SIGMA1, SIGMA2);

        final float[] paddedBlur = Blur.gaussianSeparable(paddedElevation, paddedSide, paddedSide, SIGMA1);

        // 3. ridge / valley masks
        final boolean[][] ridgeMask = ridgeMask(paddedBlur);
        final boolean[][] valleyMask = valleyMask(paddedBlur);

        // 4-5. trace masks → splines → insert points into the (padded-local) trees. Ridges use the
        // skeleton (medial axis); valleys use the region border via marching squares.
        final List<QuinticHermiteSpline> ridgeSplines = maskSkeletonizer.trace(ridgeMask);
        final List<QuinticHermiteSpline> valleySplines = valleyTracer.trace(valleyMask);
        insertLocalSplinePoints(ridgeTree, ridgeSplines);
        insertLocalSplinePoints(valleyTree, valleySplines);

        // 6. field-line placement at higher resolution, then drop lines over negative elevation.
        final int fieldW = placer.outputWidth();
        final int fieldH = placer.outputHeight();
        final float[] fieldRaw = placer.applyRaw(ridgeTree, valleyTree);
        maskFieldByElevation(fieldRaw, fieldW, fieldH, paddedElevation);
        final float[] fieldNorm = FieldLinePlacer.normalizeByFwidth(fieldRaw, fieldW, fieldH);

        // 7. threshold the field into a mask
        final boolean[][] fieldMask = fieldMask(fieldRaw, fieldW, fieldH);

        // 8-9. skeletonize the field, transform back to global coarse-px, insert into the final tree
        final List<QuinticHermiteSpline> fieldSplines = fieldSkeletonizer.trace(fieldMask);
        final QuadTree<QuadTreePoint> finalTree = newFinalTree(tileOriginCx, tileOriginCz);
        for (QuinticHermiteSpline spline : fieldSplines) {
            for (double[] fieldPoint : spline.points()) {
                final double globalX = tileOriginCx - pad + fieldPoint[0] * fieldResolution;
                final double globalZ = tileOriginCz - pad + fieldPoint[1] * fieldResolution;
                finalTree.insertPoint(new QuadTreePoint(new double[] {globalX, globalZ}));
            }
        }
        LOG.info("tile quadTreeSize {}",finalTree.numPoints());
        if (stages != null) {
            stages.paddedSide = paddedSide;
            stages.pad = pad;
            stages.tileOriginCx = tileOriginCx;
            stages.tileOriginCz = tileOriginCz;
            stages.paddedElevation = paddedElevation;
            stages.paddedBlur = paddedBlur;
            stages.ridgeMask = ridgeMask;
            stages.valleyMask = valleyMask;
            stages.ridgeSplines = ridgeSplines;
            stages.valleySplines = valleySplines;
            stages.fieldSplines = fieldSplines;
            stages.ridgeTree = ridgeTree;
            stages.valleyTree = valleyTree;
            stages.fieldWidth = fieldW;
            stages.fieldHeight = fieldH;
            stages.fieldRaw = fieldRaw;
            stages.fieldNorm = fieldNorm;
            stages.fieldMask = fieldMask;
            stages.finalTree = finalTree;
        }
        return finalTree;
    }

    /** Pulls the padded coarse slice and normalizes elevation by the blend weight (channel 6). */
    private float[] paddedElevation(int tileOriginCx, int tileOriginCz) {
        final int ci0 = tileOriginCx - pad;
        final int cj0 = tileOriginCz - pad;
        final int ci1 = tileOriginCx + TILE_SIZE + pad;
        final int cj1 = tileOriginCz + TILE_SIZE + pad;
        final FloatTensor slice = pipeline.getCoarseSlice(ci0, cj0, ci1, cj1);
        final int pixelCount = paddedSide * paddedSide;
        final float[] elevation = new float[pixelCount];
        for (int px = 0; px < pixelCount; px++) {
            final float weight = slice.data[6 * pixelCount + px];
            final float normalized = (weight > 1e-6f) ? slice.data[px] / weight : 0f;
            elevation[px] = normalized;
        }
        return elevation;
    }

    private boolean[][] ridgeMask(float[] paddedDog) {
        final boolean[][] mask = new boolean[paddedSide][paddedSide];
        for (int di = 0; di < paddedSide; di++) {
            for (int dj = 0; dj < paddedSide; dj++) {
                mask[di][dj] = paddedDog[di * paddedSide + dj] >= RIDGE_THRESHOLD;
            }
        }
        return mask;
    }

    private boolean[][] valleyMask(float[] paddedDog) {
        final boolean[][] mask = new boolean[paddedSide][paddedSide];
        for (int di = 0; di < paddedSide; di++) {
            for (int dj = 0; dj < paddedSide; dj++) {
                mask[di][dj] = paddedDog[di * paddedSide + dj] <= 0;
            }
        }
        return mask;
    }

    /**
     * Zero every field cell whose underlying coarse elevation is negative, so no river/field line is
     * traced over below-sea-level terrain. The field grid is {@link FieldLinePlacer#UPSAMPLE}× the
     * padded elevation per axis, so cell {@code (row, col)} maps to elevation pixel
     * {@code (row*fieldResolution, col*fieldResolution)}.
     */
    private void maskFieldByElevation(float[] field, int fieldW, int fieldH, float[] paddedElevation) {
        for (int row = 0; row < fieldH; row++) {
            final int di = Math.min((int) (row * fieldResolution), paddedSide - 1);
            for (int col = 0; col < fieldW; col++) {
                final int dj = Math.min((int) (col * fieldResolution), paddedSide - 1);
                if (paddedElevation[di * paddedSide + dj] < 0f) {
                    field[row * fieldW + col] = 0f;
                }
            }
        }
    }

    private static boolean[][] fieldMask(float[] field, int fieldW, int fieldH) {
        final boolean[][] mask = new boolean[fieldH][fieldW];
        for (int row = 0; row < fieldH; row++) {
            for (int col = 0; col < fieldW; col++) {
                mask[row][col] = field[row * fieldW + col] >= FIELD_THRESHOLD;
            }
        }
        return mask;
    }

    /** Insert spline sample points (padded-local row/col coords) into {@code tree}. */
    private static void insertLocalSplinePoints(QuadTree<QuadTreePoint> tree, List<QuinticHermiteSpline> splines) {
        for (QuinticHermiteSpline spline : splines) {
            for (double[] point : spline.points()) {
                tree.insertPoint(new QuadTreePoint(new double[] {point[0], point[1]}));
            }
        }
    }

    private QuadTree<QuadTreePoint> newLocalTree() {
        return new QuadTree<>(new double[] {0, 0}, new double[] {paddedSide, paddedSide});
    }

    private QuadTree<QuadTreePoint> newFinalTree(int tileOriginCx, int tileOriginCz) {
        return new QuadTree<>(
                new double[] {tileOriginCx - pad, tileOriginCz - pad},
                new double[] {tileOriginCx + TILE_SIZE + pad, tileOriginCz + TILE_SIZE + pad});
    }

    // -------------------------------------------------------------------------
    // Debug access (used by GlobalRiverTest to render every intermediate stage)
    // -------------------------------------------------------------------------

    /** Recompute one tile with fresh trees, capturing every intermediate stage for visualization. */
    @TestOnly
    public Stages debugStages(int tx, int tz) {
        final Stages stages = new Stages();
        computeTile(tx, tz, newLocalTree(), newLocalTree(), stages);
        return stages;
    }

    /** Snapshot of every intermediate artifact produced while building a single tile. */
    @TestOnly
    public static final class Stages {
        public int paddedSide;
        public int pad;
        public int tileOriginCx;
        public int tileOriginCz;
        public float[] paddedElevation;
        public float[] paddedBlur;
        public boolean[][] ridgeMask;
        public boolean[][] valleyMask;
        public List<QuinticHermiteSpline> ridgeSplines;
        public List<QuinticHermiteSpline> valleySplines;
        public List<QuinticHermiteSpline> fieldSplines;
        public QuadTree<QuadTreePoint> ridgeTree;
        public QuadTree<QuadTreePoint> valleyTree;
        public int fieldWidth;
        public int fieldHeight;
        public float[] fieldRaw;
        public float[] fieldNorm;
        public boolean[][] fieldMask;
        public QuadTree<QuadTreePoint> finalTree;
    }
}
