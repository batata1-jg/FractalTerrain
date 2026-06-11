package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.List;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteQuadTree;
import me.batata_1.fractal_terrain.math.Blur;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
    /** Valley mask: DoG ≤ −VALLEY_THRESHOLD. */
    private static final double VALLEY_THRESHOLD = 0.0;
    /**
     * Field mask: normalized field ≥ FIELD_THRESHOLD marks a (uniformly thin) field line. After the
     * fwidth pass the sin <em>peaks</em> (the lines, cf. the old {@code sin ≥ 0.8}) become large
     * values, while flat no-hit regions and zero-crossings stay near 0 — so we threshold HIGH.
     */
    private static final double FIELD_THRESHOLD = 4.0;
    /** Arc-length resample spacing for the first (ridge/valley) skeleton pass. */
    private static final double DX1 = 8.0;
    /** Arc-length resample spacing for the second (field) skeleton pass. */
    private static final double DX2 = 2.0;

    private static final double FREQUENCY = 1.0;
    private static final double QUERY_RADIUS = 1024.0;
    private static final double LINE_THICKNESS = 1.0;
    private static final int MIN_POLYLINE_LEN = 4;

    /** Radius used by {@link #query} on the final per-tile tree. */
    public static final double FINAL_QUERY_RADIUS = 16.0;

    // ---- Derived geometry ---------------------------------------------------
    private static final int TILE_SIZE = DifferenceOfGaussians.COARSE_TILE_SIZE; // 64
    private final int pad = Blur.padFor(SIGMA1);
    private final int paddedSide = TILE_SIZE + 2 * pad;
    private final double fieldResolution = 1.0 / FieldLinePlacer.UPSAMPLE;

    private final Skeletonizer maskSkeletonizer = new Skeletonizer(MIN_POLYLINE_LEN, DX1);
    private final Skeletonizer fieldSkeletonizer = new Skeletonizer(MIN_POLYLINE_LEN, DX2);
    private final FieldLinePlacer placer = new FieldLinePlacer(paddedSide, paddedSide, fieldResolution, QUERY_RADIUS, LINE_THICKNESS, FREQUENCY);
    /** Reused, cleared per tile so we don't reallocate the intermediate trees each compute. */
    private final ThreadLocal<QuadTree<QuadTreePoint>> ridgeScratch = ThreadLocal.withInitial(this::newLocalTree);

    private final ThreadLocal<QuadTree<QuadTreePoint>> valleyScratch = ThreadLocal.withInitial(this::newLocalTree);

    private final NonIntersectingInfiniteQuadTree<QuadTreePoint> globalRiverTile;

    public GlobalRiverProvider(String path) {
        this.globalRiverTile = new NonIntersectingInfiniteQuadTree<>(
                path + "/global_river_tiles", new int[] {TILE_SIZE, TILE_SIZE}, this::buildRiverTile);
    }

    public NonIntersectingInfiniteQuadTree<QuadTreePoint> getInfiniteQuadTree() {
        return globalRiverTile;
    }

    /** River points within {@link #FINAL_QUERY_RADIUS} of {@code (cx, cz)} (global coarse-px). */
    public List<QuadTreePoint> query(double cx, double cz) {
        return globalRiverTile.query(cx, cz, FINAL_QUERY_RADIUS);
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
            int tx, int tz, QuadTree<QuadTreePoint> ridgeTree, QuadTree<QuadTreePoint> valleyTree, @Nullable Stages stages) {
        final int tileOriginCx = tx * TILE_SIZE;
        final int tileOriginCz = tz * TILE_SIZE;

        // 1. padded, ch6-normalized coarse elevation
        final float[] paddedElevation = paddedElevation(tileOriginCx, tileOriginCz);

        // 2. padded Difference-of-Gaussians
        // final float[] paddedBlur = DifferenceOfGaussians.run(paddedElevation, paddedSide,paddedSide,SIGMA1, SIGMA2);

        final float[] paddedBlur = Blur.gaussianSeparable(paddedElevation,paddedSide,paddedSide,SIGMA1);

        // 3. ridge / valley masks
        final boolean[][] ridgeMask = ridgeMask(paddedBlur);
        final boolean[][] valleyMask = valleyMask(paddedBlur);

        // 4-5. skeletonize masks → splines → insert points into the (padded-local) trees
        insertLocalSplinePoints(ridgeTree, maskSkeletonizer.trace(ridgeMask));


        //marching squares
        insertLocalSplinePoints(valleyTree, maskSkeletonizer.trace(valleyMask));

        // 6. field-line placement at higher resolution
        final int fieldW = placer.outputWidth();
        final int fieldH = placer.outputHeight();
        final float[] fieldRaw = placer.applyRaw(ridgeTree, valleyTree);
        final float[] fieldNorm = FieldLinePlacer.normalizeByFwidth(fieldRaw, fieldW, fieldH);

        // 7. threshold the field into a mask
        final boolean[][] fieldMask = fieldMask(fieldNorm, fieldW, fieldH);

        // 8-9. skeletonize the field, transform back to global coarse-px, insert into the final tree
        final QuadTree<QuadTreePoint> finalTree = newFinalTree(tileOriginCx, tileOriginCz);
        for (QuinticHermiteSpline spline : fieldSkeletonizer.trace(fieldMask)) {
            for (double[] fieldPoint : spline.points()) {
                final double globalX = tileOriginCx - pad + fieldPoint[0] * fieldResolution;
                final double globalZ = tileOriginCz - pad + fieldPoint[1] * fieldResolution;
                finalTree.insertPoint(new QuadTreePoint(new double[] {globalX, globalZ}));
            }
        }

        if (stages != null) {
            stages.paddedSide = paddedSide;
            stages.pad = pad;
            stages.tileOriginCx = tileOriginCx;
            stages.tileOriginCz = tileOriginCz;
            stages.paddedElevation = paddedElevation;
            stages.paddedBlur = paddedBlur;
            stages.ridgeMask = ridgeMask;
            stages.valleyMask = valleyMask;
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
                mask[di][dj] = paddedDog[di * paddedSide + dj] <= -VALLEY_THRESHOLD;
            }
        }
        return mask;
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
