package me.batata_1.fractal_terrain.hydrology.carvers;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import org.jetbrains.annotations.TestOnly;

/**
 * The stateless lattice carve shared by every carve call site: {@link #computeBedGrid} merges every
 * touching primitive — rivers first, then the radial families — into one (height, water, weight) triple
 * per lattice point, and {@link #carveInfluenceGrid} wraps it for the padded-tile shell pass.
 *
 * <p>This pure lattice math carries no {@code RiverProvider} dependency: an instance field here would
 * force a cycle back onto {@code hydrology.providers}, which only needs this static half.
 */
public final class LatticeCarve {

    private LatticeCarve() {}

    /**
     * {@code blendSink} records the peak floodplain blend ratio each lattice point saw, for debug
     * renders; production passes {@code null}, which costs the carve one never-taken branch and no
     * allocation. Sized {@code paddedSize²} and zero-filled by the caller — the carve only maxes into it.
     */
    public static void carveInfluenceGrid(float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize) {
        if (primitives.isEmpty()) return;
        final GridBuffers buffers = SHELL_BUFFERS.get();
        buffers.ensure(paddedSize, maxLutLen(paddedSize, 1.0));
        final int points = paddedSize * paddedSize;

        Arrays.fill(buffers.acc, 0, 3 * points, 0f);
        for (int i = 0; i < points; i++) {
            buffers.acc[3 * i] = elevation[i];
        }
        // Preserved only for RiverProvider's debug-only shellDistanceField() capture; no shell carver
        // reads or writes dist -- the shell merge is an order-independent hard min (Math.min per call
        // site, applied once against the fully-merged height), so there is no distance recurrence here
        // to rank a primitive against.
        Arrays.fill(buffers.dist, 0, points, 1f);

        final ShellGrid grid = new ShellGrid(
                paddedSize,
                buffers.acc,
                buffers.lut,
                buffers.perpRow,
                buffers.perpCol,
                buffers.tangRow,
                buffers.tangCol,
                elevation);
        for (HydrologicalPrimitive primitive : primitives) {
            primitive.carveInfluence(grid);
        }
    }

    /** Longest cross-section table any primitive can need on this grid: a primitive cannot span more
     *  perp than the grid's diagonal, nor its own influence diameter. The radial pass is bounded the
     *  same way, by the clipped box's diagonal, with one entry of margin. */
    public static int maxLutLen(int gridSize, double resolution) {
        final int diagonal = (int) Math.ceil((gridSize - 1) * Math.sqrt(2.0));
        final int influence = (int) Math.ceil(
                Math.max(RiverPrimitive.PROTOTYPE.getWidth(), RiverPrimitive.PROTOTYPE.getLength()) / resolution);
        return Math.min(diagonal, influence) + 3;
    }

    /**
     * Merges every primitive touching the lattice into one (height, water, weight) triple per point in
     * {@code grid.acc()}, plus the winning primitive's packed type in {@code grid.typeMask()}.
     * {@code grid.dist()} is published into {@code Types.RIVER_DIST}, so it now reflects whichever
     * primitive won each cell rather than the river pass alone.
     *
     * <p>{@code primitives} MUST be sorted by {@link HydrologicalPrimitive#comparator}. The merge is a
     * sequential recurrence over one shared ranking buffer, so the caller's sort is what decides which
     * primitive owns a lattice point — a contract, not a convenience.
     */
    public static void computeBedGrid(BedGrid grid, List<HydrologicalPrimitive> primitives) {
        final int points = grid.gridSize() * grid.gridSize();
        Arrays.fill(grid.acc(), 0, 3 * points, 0f);
        Arrays.fill(grid.typeMask(), 0, points, HydrologicalPrimitive.HydrologicalFeature.NONE);
        Arrays.fill(grid.dist(), 0, points, (float) UNSET_MIN_DIST);

        for (final HydrologicalPrimitive primitive : primitives) {
            primitive.carveBed(grid);
        }
    }

    /** Per-lattice-point "no primitive seen yet" distance, as a footprint scale factor. Any value above
     *  1 sits outside every primitive's rectangle, so the first primitive to reach a point wins outright. */
    public static final double UNSET_MIN_DIST = 64;

    /** Fixed regardless of primitive width, so a paint consumer classifies a point with no access to it. */
    public static final double BED_EDGE = 0.25;

    /** The banded coordinate at a primitive's floodplain edge, where the influence band begins. */
    public static final double FLOODPLAIN_EDGE = 0.5;

    /** One instance of this class serves every tile build, so the carve buffers cannot be fields. */
    private static final ThreadLocal<GridBuffers> SHELL_BUFFERS = ThreadLocal.withInitial(GridBuffers::new);

    /**
     * The elevation {@link #carveInfluenceGrid}'s closing blend would publish at {@code (px, pz)} given
     * only the primitives merged into {@code acc} so far. Lets a primitive cap its own bed against ground
     * its already-merged neighbours cut, so the influence carve cannot fill.
     */
    private static double mergedElevationAt(double px, double pz, int side, float[] acc, float[] elevs) {
        // No ambient field means nothing to cap against; an infinite cap leaves Math.min inert.
        if (elevs == null) return Double.POSITIVE_INFINITY;
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        final double fx = px - x0;
        final double fz = pz - z0;
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        x0 = Math.clamp(x0, 0, side - 1);
        x1 = Math.clamp(x1, 0, side - 1);
        z0 = Math.clamp(z0, 0, side - 1);
        z1 = Math.clamp(z1, 0, side - 1);
        final double v0 = mergedElevationAtPoint(x0 * side + z0, acc, elevs) * (1 - fz)
                + mergedElevationAtPoint(x0 * side + z1, acc, elevs) * fz;
        final double v1 = mergedElevationAtPoint(x1 * side + z0, acc, elevs) * (1 - fz)
                + mergedElevationAtPoint(x1 * side + z1, acc, elevs) * fz;
        return v0 * (1 - fx) + v1 * fx;
    }

    /** One lattice point's merged elevation, on the same law the closing blend of {@link
     *  #carveInfluenceGrid} applies: ambient carried toward the merged river surface by its weight. */
    private static double mergedElevationAtPoint(int i, float[] acc, float[] elevs) {
        final double w = acc[3 * i + 1];
        return elevs[i] * (1 - w) + acc[3 * i] * w;
    }

    /**
     * The buffers {@link #computeBedGrid} writes, bundled so each call site keeps one sizing rule
     * rather than four. Deliberately not a parameter of the carve itself — see the design spec's
     * "Why no scratch class": a second primitive family needs its own {@code acc} against the same grid.
     *
     * <p>{@code acc} holds (height, water, weight) triples; {@code typeMask} the nearest primitive's
     * packed type. Ambient-free by construction: a caller recovers its carved elevation as
     * {@code (1 - w) * ambient + w * min(h, ambient)}.
     *
     * <p>Not thread-safe by construction. Chunk generation is multithreaded, so each thread owns one.
     */
    public static final class GridBuffers {
        public float[] acc = new float[0];
        public long[] typeMask = new long[0];
        public float[] dist = new float[0];

        public float[] lut = new float[0];
        /** Scratch: the row half of each lattice point's across-flow projection. */
        public double[] perpRow = new double[0];
        /** Scratch: the column half of each lattice point's across-flow projection. */
        public double[] perpCol = new double[0];
        /** Scratch: the row half of each lattice point's along-flow projection. */
        public double[] tangRow = new double[0];
        /** Scratch: the column half of each lattice point's along-flow projection. */
        public double[] tangCol = new double[0];

        /** Grows any buffer that is too small. Never shrinks — the carve fills only the range it uses. */
        public void ensure(int gridSize, int lutLen) {
            final int points = gridSize * gridSize;
            if (acc.length < 3 * points) acc = new float[3 * points];
            if (typeMask.length < points) typeMask = new long[points];
            if (dist.length < points) dist = new float[points];
            if (lut.length < lutLen) lut = new float[lutLen];
            if (perpRow.length < gridSize) perpRow = new double[gridSize];
            if (perpCol.length < gridSize) perpCol = new double[gridSize];
            if (tangRow.length < gridSize) tangRow = new double[gridSize];
            if (tangCol.length < gridSize) tangCol = new double[gridSize];
        }
    }

    /**
     * The distance field the last {@link #carveInfluenceGrid} on THIS thread left behind: per lattice
     * point, the footprint scale at which the winning primitive swallows it, {@link #UNSET_MIN_DIST}
     * where none reached. The live scratch buffer, so a reader copies before the next carve overwrites
     * it, and a carve that returned early on an empty primitive list leaves the previous tile's values.
     */
    @TestOnly
    public static float[] shellDistanceField() {
        return SHELL_BUFFERS.get().dist;
    }

    /** Per-call scratch the shell pass's primitive carvers share: the grid size, the pre-carve
     *  ambient snapshot ({@code acc}, read but never rewritten once filled), the per-primitive
     *  cross-section LUT, the tangent/perpendicular projection scratch (unused by a radial carve),
     *  and the buffer every primitive's contribution is {@code Math.min}'d into. */
    public record ShellGrid(
            int gridSize,
            float[] acc,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs) {}

    /** Per-call scratch the bed pass's primitive carvers share: the lattice frame, the merge buffers
     *  every primitive blends into, the per-primitive cross-section LUT, the projection scratch a
     *  rectangle carve tabulates, and the ambient field each contribution is capped against. One
     *  instance per carve call, which is once per chunk — above the per-column loop, not inside it. */
    public record BedGrid(
            int gridSize,
            double startX,
            double startZ,
            double resolution,
            float[] acc,
            long[] typeMask,
            float[] dist,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs) {}
}
