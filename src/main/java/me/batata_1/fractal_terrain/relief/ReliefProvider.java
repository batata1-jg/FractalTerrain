package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.RELIEF_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.NEIGHBOR_OFFSET_X;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.NEIGHBOR_OFFSET_Z;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Builds final relief tiles ({@code [RELIEF_CHANNELS=8, 512, 512]}) by decoding the diffusion
 * residual and carving the global river network into the elevation.
 *
 * <p>Channel layout: {@code [0]} elev (carved) {@code [1]} blurredElev {@code [2]} gradX
 * {@code [3]} gradY {@code [4]} refinedGrad {@code [5]} lowFreqGrad {@code [6]} res
 * {@code [7]} drainageDirection (the D8 steepest-descent bitfield).
 *
 * <p>Per-tile pipeline: {@link #decodeBaseChannels} (padded 514×514) → fetch the 2×2 coarse global
 * river block (plus exit-neighbour widths) → trace the arrows into river splines → carve the
 * elevation along those splines → recompute the drainage direction on the carved (padded) elevation
 * → crop to the inner 512×512. All tuning literals live grouped near the top.
 */
public class ReliefProvider {

    // ---- Tuning knobs (edit here during testing) ----------------------------
    /** Max distance (native px) from a river sample at which carving still applies. */
    private static final double MAX_CARVE_DIST = 24.0;
    /** Arc-length spacing (native px) used to densify river splines for the spatial index. */
    private static final double CARVE_SAMPLE_SPACING = 2.0;
    /** Channel depth scale: full-depth carve at a river's centre is CARVE_DEPTH_SCALE × width. */
    private static final double CARVE_DEPTH_SCALE = 0.6;

    // ---- Geometry -----------------------------------------------------------
    private static final int INNER = 512;
    private static final int PAD = 1;
    private static final int PADDED = INNER + 2 * PAD; // 514
    private static final int BASE_CHANNELS = DECODER_CHANNELS - 1; // 7 (relief ch0..6)
    /** Native px per coarse cell. */
    private static final int COARSE_PX = 256;
    /** Half a coarse cell — offset from a cell origin to its centre. */
    private static final int COARSE_HALF = COARSE_PX / 2;

    private final NonIntersectingInfiniteTensor final_tiles;

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path + "/final_relief_tiles", new int[] {RELIEF_CHANNELS, INNER, INNER}, this::buildReliefTile);
    }

    @TestOnly
    public void setGlobalRiverProvider(GlobalRiverProvider provider) {
        this.globalRiverOverride = provider;
    }

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    /**
     * The full computed relief tile {@code [RELIEF_CHANNELS, 512, 512]} covering native px
     * {@code [tileX<<9, (tileX+1)<<9) × [tileZ<<9, (tileZ+1)<<9)}. Used by {@code LocalRiverProvider}
     * to read the whole {@code elev}/{@code drainageDirection} channels at once.
     */
    public FloatTensor getTile(int tileX, int tileZ) {
        return final_tiles.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    private FloatTensor buildReliefTile(TileKey key) {
        return computeTile(key.get(X), key.get(Z), null);
    }

    private FloatTensor computeTile(int x, int z, @Nullable Stages stages) {
        // 1. decode the 7 weight-normalized base channels at padded 514×514.
        final float[][] baseChannels = decodeBaseChannels(x, z);

        // 2-3. trace the global river arrows over this tile into splines (control points + widths).
        final List<RiverPolyline> riverSplines = traceRiverSplines(x, z);

        // 4. carve the elevation (base channel 0) along the splines.
        final float[] carvedElevation = baseChannels[0].clone();
        carveRiver(carvedElevation, riverSplines, stages);

        // 5. drainage direction on the carved padded elevation (uniform weight = already normalized).
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final float[] drainageDirection =
                PipelinePreprocessing.computeDrainageDirection(carvedElevation, uniformWeight, PADDED);

        // 6. crop to the inner 512×512 and assemble the result tensor.
        final float[] entries = new float[RELIEF_CHANNELS * INNER * INNER];
        for (int ix = 0; ix < INNER; ix++) {
            for (int iz = 0; iz < INNER; iz++) {
                final int paddedIndex = (PAD + ix) * PADDED + (PAD + iz);
                final int innerIndex = ix * INNER + iz;
                entries[innerIndex] = carvedElevation[paddedIndex];
                for (int ch = 1; ch < BASE_CHANNELS; ch++) {
                    entries[ch * INNER * INNER + innerIndex] = baseChannels[ch][paddedIndex];
                }
                entries[7 * INNER * INNER + innerIndex] = drainageDirection[paddedIndex];
            }
        }

        final FloatTensor result = new FloatTensor(entries, new int[] {RELIEF_CHANNELS, INNER, INNER});
        if (stages != null) {
            stages.baseChannels = baseChannels;
            stages.riverSplines = riverSplines;
            stages.carvedElevation = carvedElevation;
            stages.drainageDirection = drainageDirection;
            stages.result = result;
        }
        return result;
    }

    /**
     * Fetch a +1-pixel-halo decoder slice ({@code [DECODER_CHANNELS=8, 514, 514]}) and weight-normalize
     * it into the 7 relief base channels at padded resolution. Decoder channel 0 is the blend weight;
     * channels 1..7 are the real outputs, each divided by the weight (guarded) and shifted down by one
     * so relief channel {@code c-1} = decoder channel {@code c} / weight.
     *
     * @return {@code baseChannels[c][paddedIndex]} for {@code c} in 0..6, each {@code 514*514} long.
     */
    public float[][] decodeBaseChannels(int x, int z) {
        final FloatTensor fineSlice =
                pipeline.getDecoderSlice((x << 9) - PAD, (z << 9) - PAD, ((x + 1) << 9) + PAD, ((z + 1) << 9) + PAD);
        final int pixelCount = PADDED * PADDED;
        final float[][] baseChannels = new float[BASE_CHANNELS][pixelCount];
        for (int px = 0; px < pixelCount; px++) {
            final float weight = fineSlice.data[px];
            final float inverse = (weight > 1e-6f) ? 1f / weight : 0f;
            for (int c = 1; c < DECODER_CHANNELS; c++) {
                baseChannels[c - 1][px] = fineSlice.data[c * pixelCount + px] * inverse;
            }
        }
        return baseChannels;
    }

    // -------------------------------------------------------------------------
    // River spline tracing (step 2-3)
    // -------------------------------------------------------------------------

    /** One traced global-river branch: ordered control points (padded px) with per-vertex widths. */
    public static final class RiverPolyline {
        final ArrayList<double[]> controlPoints = new ArrayList<>();
        final ArrayList<Double> widths = new ArrayList<>();
        QuinticHermiteSpline spline;
    }

    /**
     * A coarse cell in the relevant set: its decoded arrow, width, and padded-px centre.
     */
    public record Cell(int coarseX, int coarseZ, int arrow, double width, double centerX, double centerZ) {
    }

    private static long cellKey(int coarseX, int coarseZ) {
        return ((long) coarseX << 32) ^ (coarseZ & 0xffffffffL);
    }

    /**
     * Build the river polylines covering relief tile {@code (x, z)} from the global river arrows. The
     * tile spans a 2×2 block of coarse cells; any river leaving the block contributes its single
     * downstream-neighbour cell (for its terminal width). Directed edges chain cell centres into
     * ordered control-point lists, which are fitted with Catmull-Rom.
     */
    private List<RiverPolyline> traceRiverSplines(int x, int z) {
        final GlobalRiverProvider riverProvider = globalRiverProvider();
        final int blockOriginX = x<<1;
        final int blockOriginZ = z<<1;

        // Relevant set: the 4 block cells plus the exit-neighbor cells rivers flow into.
        final Map<Long, Cell> cells = new HashMap<>();
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                final int ccx = blockOriginX + a;
                final int ccz = blockOriginZ + b;
                cells.put(cellKey(ccx, ccz), makeCell(riverProvider, x, z, ccx, ccz));
            }
        }
        for (Cell cell : new ArrayList<>(cells.values())) {
            final int outgoing = GlobalRiverProvider.outgoingMask(cell.arrow);
            for (int d = 0; d < 8; d++) {
                if ((outgoing & (1 << d)) == 0) continue;
                final int nx = cell.coarseX + NEIGHBOR_OFFSET_X[d];
                final int nz = cell.coarseZ + NEIGHBOR_OFFSET_Z[d];
                final long nk = cellKey(nx, nz);
                if (!cells.containsKey(nk)) cells.put(nk, makeCell(riverProvider, x, z, nx, nz));
            }
        }

        // Downstream pointer (≤1 per cell) and in-degree within the relevant set.
        final Map<Long, Cell> downstream = new HashMap<>();
        final Map<Long, Integer> inDegree = new HashMap<>();
        for (Cell cell : cells.values()) {
            final int outgoing = GlobalRiverProvider.outgoingMask(cell.arrow);
            for (int d = 0; d < 8; d++) {
                if ((outgoing & (1 << d)) == 0) continue;
                final long nk = cellKey(cell.coarseX + NEIGHBOR_OFFSET_X[d], cell.coarseZ + NEIGHBOR_OFFSET_Z[d]);
                final Cell target = cells.get(nk);
                if (target == null) continue;
                downstream.put(cellKey(cell.coarseX, cell.coarseZ), target);
                inDegree.merge(nk, 1, Integer::sum);
            }
        }

        // Entries: chain heads (have a downstream edge but no in-set upstream).
        final List<RiverPolyline> polylines = new ArrayList<>();
        for (Cell cell : cells.values()) {
            final long key = cellKey(cell.coarseX, cell.coarseZ);
            if (!downstream.containsKey(key)) continue; // no outgoing edge → not a chain head
            if (inDegree.getOrDefault(key, 0) != 0) continue; // mid-chain or confluence branch tail
            polylines.add(walkChain(cell, downstream, cells.size()));
        }

        for (RiverPolyline polyline : polylines) {
            polyline.spline = QuinticHermiteSpline.createCatmullRom(polyline.controlPoints);
        }
        return polylines;
    }

    private Cell makeCell(GlobalRiverProvider riverProvider, int tileX, int tileZ, int ccx, int ccz) {
        final int arrow = riverProvider.getArrow(ccx, ccz);
        final double width = riverProvider.getWidth(ccx, ccz);
        final double centerX = PAD + COARSE_HALF + (ccx - tileX * 2) * COARSE_PX;
        final double centerZ = PAD + COARSE_HALF + (ccz - tileZ * 2) * COARSE_PX;
        return new Cell(ccx, ccz, arrow, width, centerX, centerZ);
    }

    /** Follow downstream pointers from {@code head} appending cell centres + widths. */
    public RiverPolyline walkChain(Cell head, Map<Long, Cell> downstream, int cap) {
        final RiverPolyline polyline = new RiverPolyline();
        Cell current = head;
        for (int step = 0; step <= cap && current != null; step++) {
            polyline.controlPoints.add(new double[] {current.centerX, current.centerZ});
            polyline.widths.add(current.width);
            current = downstream.get(cellKey(current.coarseX, current.coarseZ));
        }
        return polyline;
    }

    // -------------------------------------------------------------------------
    // River carving (step 4)
    // -------------------------------------------------------------------------

    /** A densified river sample carrying its interpolated width, indexed in the carve QuadTree. */
    private static final class RiverSample extends QuadTreePoint {
        final double width;

        RiverSample(double px, double pz, double width) {
            super(new double[] {px, pz});
            this.width = width;
        }
    }

    /**
     * Carve {@code elevation} (padded 514×514) along the river splines. Each spline is densified into
     * sample points (with width lerped between adjacent control points) and inserted into a QuadTree;
     * each pixel then queries the nearest sample within {@link #MAX_CARVE_DIST} and subtracts
     * {@link #riverDepth}. Pixels with no nearby sample are left untouched.
     */
    private void carveRiver(float[] elevation, List<RiverPolyline> riverSplines, @Nullable Stages stages) {
        if (riverSplines.isEmpty()) return;

        final QuadTree<RiverSample> index = new QuadTree<>(
                new double[] {-COARSE_PX * 4, -COARSE_PX * 4},
                new double[] {PADDED + COARSE_PX * 4, PADDED + COARSE_PX * 4});
        for (RiverPolyline polyline : riverSplines) {
            if (polyline.controlPoints.size() < 2) continue;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                final double startWidth = polyline.widths.get(i);
                final double endWidth = polyline.widths.get(i + 1);
                for (int s = 0; s <= sampleCount; s++) {
                    final double frac = (double) s / sampleCount;
                    final double[] point = spline.sample(i + frac);
                    final double width = startWidth + (endWidth - startWidth) * frac;
                    index.insertPoint(new RiverSample(point[0], point[1], width));
                }
            }
        }

        final float[] carveDepthField = (stages != null) ? new float[PADDED * PADDED] : null;
        for (int pi = 0; pi < PADDED; pi++) {
            for (int pj = 0; pj < PADDED; pj++) {
                if(elevation[pi * PADDED + pj]<0) continue;
                final double[] pixel = {pi, pj};
                final List<RiverSample> nearby = index.getPointsInCircle(pixel, MAX_CARVE_DIST);
                if (nearby.isEmpty()) continue;
                double nearestDist = Double.MAX_VALUE;
                double nearestWidth = 0;
                for (RiverSample sample : nearby) {
                    final double dist = VectorOps.distance(pixel, sample.toArray());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestWidth = sample.width;
                    }
                }
                final float depth = (float) riverDepth(nearestDist, nearestWidth);
                elevation[pi * PADDED + pj] -= depth;
                if (carveDepthField != null) carveDepthField[pi * PADDED + pj] = depth;
            }
        }
        if (stages != null) stages.carveDepthField = carveDepthField;
    }

    /**
     * River carve depth as a function of distance to the nearest river sample and the river width
     * there. Full depth ({@link #CARVE_DEPTH_SCALE} × width) within the half-width banks, falling off
     * linearly to 0 at {@link #MAX_CARVE_DIST}. Tunable / easily swappable.
     */
    private static double riverDepth(double dist, double width) {
        if (dist >= MAX_CARVE_DIST) return 0;
        final double maxDepth = CARVE_DEPTH_SCALE * width;
        final double bankHalf = width * 0.5;
        if (dist <= bankHalf) return maxDepth;
        final double falloff = 1.0 - (dist - bankHalf) / (MAX_CARVE_DIST - bankHalf);
        return maxDepth * Math.max(0.0, falloff);
    }

    // -------------------------------------------------------------------------
    // Pixel accessors
    // -------------------------------------------------------------------------

    public Float get_entry(final int[] mutableCoords, final int ch) {
        mutableCoords[me.batata_1.fractal_terrain.FractalTerrainConfig.CH] = ch;
        return final_tiles.getValue(mutableCoords);
    }

    public Float getElev(int[] xz) {
        return get_entry(xz, 0);
    }

    public Float getBlurredElev(final int[] xz) {
        return get_entry(xz, 1);
    }

    public Float getGradX(final int[] xz) {
        return get_entry(xz, 2);
    }

    public Float getGradY(final int[] xz) {
        return get_entry(xz, 3);
    }

    public Float getRefinedGrad(final int[] xz) {
        return get_entry(xz, 4);
    }

    public Float getLowFreqGrad(final int[] xz) {
        return get_entry(xz, 5);
    }

    public Float getRes(final int[] xz) {
        return get_entry(xz, 6);
    }

    public Float getDrainageDirection(final int[] xz) {
        return get_entry(xz, 7);
    }

    public double getContinentalElev(final int[] xz) {
        return 0;
    }

    public double getRawTemp(final int[] xz) {
        return 0;
    }

    public Float getRawTempSTD(final int[] xz) {
        return (float) 0;
    }

    public double getRawPrecip(final int[] xz) {
        return 0;
    }

    public Float getRawPrecipSTD(final int[] xz) {
        return (float) 0;
    }

    public int getRawGrad(final int[] xz) {
        return 0;
    }

    public double getBlurredGrad(final int[] xz) {
        return 0;
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int x, int z) {
        final Stages stages = new Stages();
        computeTile(x, z, stages);
        return stages;
    }

    /**
     * The densified river-spline sample points (padded px) traced for {@code stages}. Mirrors
     * {@link #carveRiver}'s sampling so a rasterization of these points matches what gets carved.
     * {@code RiverPolyline} is private, so callers (the debug tests) get plain {@code [x, z]} arrays.
     */
    @TestOnly
    public List<double[]> debugRiverSplinePoints(Stages stages) {
        final List<double[]> points = new ArrayList<>();
        final List<RiverPolyline> riverSplines = stages.riverSplines;
        if (riverSplines == null) return points;
        for (RiverPolyline polyline : riverSplines) {
            if (polyline.controlPoints.size() < 2) continue;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                for (int s = 0; s <= sampleCount; s++) {
                    points.add(spline.sample(i + (double) s / sampleCount));
                }
            }
        }
        return points;
    }

    @TestOnly
    public static final class Stages {
        public float[][] baseChannels;
        public List<RiverPolyline> riverSplines;
        public float[] carveDepthField;
        public float[] carvedElevation;
        public float[] drainageDirection;
        public FloatTensor result;
    }
}
