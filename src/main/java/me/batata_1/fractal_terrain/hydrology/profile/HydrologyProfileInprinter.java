package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The elevation side of the hydrology profile — where rivers actually cut the terrain.
 *
 * <p>Split across two stages because they run at different times against different data: the tile-level
 * shell carve broad-brushes the valley during {@code RiverProvider.buildTile}, and the per-pixel
 * refinement cuts the bed trench below it later, driven per chunk from {@code PopulateNoiseStep}.
 *
 * <p>All geometry is in the relief-pixel frame. The carving twin of {@code HydrologyProfilePainter}.
 */
public final class HydrologyProfileInprinter {

    private final RiverProvider riverProvider;

    public HydrologyProfileInprinter(RiverProvider riverProvider) {
        this.riverProvider = riverProvider;
    }

    public static void carveRiverInfluence(float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize) {
        carveRiverInfluence(elevation, primitives, paddedSize, null);
    }

    /**
     * {@code blendSink} records the peak floodplain blend ratio each lattice point saw, for debug
     * renders; production passes {@code null}, which costs the carve one never-taken branch and no
     * allocation. Sized {@code paddedSize²} and zero-filled by the caller — the carve only maxes into it.
     */
    public static void carveRiverInfluence(
            float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize, @Nullable float[] blendSink) {
        if (primitives.isEmpty()) return;
        final GridBuffers buffers = SHELL_BUFFERS.get();
        buffers.ensure(paddedSize, maxLutLen(paddedSize, 1.0));

        Arrays.fill(buffers.acc, 0, 3 * paddedSize * paddedSize, 0f);
        for(int i=0;i<paddedSize*paddedSize;i++) {
            buffers.acc[3*i] = elevation[i];
        }

        computeRiverInfluenceGrid(
                paddedSize,
                primitives,
                buffers.typeMask,
                buffers.acc,
                buffers.dist,
                buffers.lut,
                buffers.perpRow,
                buffers.perpCol,
                buffers.tangRow,
                buffers.tangCol,
                elevation,
                blendSink);

        for(int i=0;i<paddedSize*paddedSize;i++) {
            final float w = buffers.acc[3*i+1];
            elevation[i] = elevation[i] * (1-w) + w * buffers.acc[3*i];
        }

    }

    // -------------------------------------------------------------------------
    // Per-pixel refinement (zone-priority merge)
    // -------------------------------------------------------------------------

    public List<HydrologicalPrimitive> prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        var primitives = riverProvider.queryInfluence(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
        primitives.sort(HydrologicalPrimitive.comparator);
        return primitives;
    }

    /**
     * The signed perpendicular distance from {@code point} to the nearest channel, with that channel's
     * cross-section read at the foot point.
     *
     * <p>Measures against the two-segment polyline through the nearest knot rather than the quintic:
     * the primitives carry no velocity or acceleration, so the true curve cannot be rebuilt here.
     */

    // -------------------------------------------------------------------------
    // Lattice carve (shared by the bed and shell stages)
    // -------------------------------------------------------------------------

    /** Per-lattice-point "no primitive seen yet" distance, as a footprint scale factor. Any value above
     *  1 sits outside every primitive's rectangle, so the first primitive to reach a point wins outright. */
    public static final double UNSET_MIN_DIST = 64;

    /**
     * The buffers {@link #computeRiverGrid} writes, bundled so each call site keeps one sizing rule
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

    /** Longest cross-section table any primitive can need on this grid: a primitive cannot span more
     *  perp than the grid's diagonal, nor its own influence diameter. Assumes influence is capped at
     *  {@link HydrologyTuning#MAX_INFLUENCE_RADIUS}; unenforced by the constructor. */
    public static int maxLutLen(int gridSize, double resolution) {
        final int diagonal = (int) Math.ceil((gridSize - 1) * Math.sqrt(2.0));
        final int influence = (int) Math.ceil(2 * HydrologyTuning.MAX_INFLUENCE_RADIUS / resolution);
        return Math.min(diagonal, influence) + 3;
    }

    /**
     * Merges every river primitive into a lattice of (height, water, weight) triples in {@code acc}.
     * {@code primitives} MUST be sorted by {@link HydrologicalPrimitive#comparator} — the merge is a
     * sequential recurrence, so order is load-bearing for determinism.
     *
     * @return the index of the first non-river primitive, where a later family pass resumes
     */
    public static int computeRiverGrid(
            double startX,
            double startZ,
            double resolution,
            int gridSize,
            List<HydrologicalPrimitive> primitives,
            float[] acc,
            long[] typeMask,
            float[] dist,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs) {
        final int points = gridSize * gridSize;
        Arrays.fill(acc, 0, 3 * points, 0f);
        Arrays.fill(typeMask, 0, points, HydrologicalPrimitive.HydrologicalFeature.NONE);
        Arrays.fill(dist, 0, points, (float) UNSET_MIN_DIST);

        int stop = 0;
        while (stop < primitives.size() && primitives.get(stop) instanceof RiverPrimitive river) {
            carvePrimitive(
                    river,
                    startX,
                    startZ,
                    resolution,
                    gridSize,
                    acc,
                    typeMask,
                    dist,
                    lut,
                    perpRow,
                    perpCol,
                    tangRow,
                    tangCol,
                    elevs);
            stop++;
        }
        return stop;
    }

    private static void computeRiverInfluenceGrid(
            int gridSize,
            List<HydrologicalPrimitive> primitives,
            long[] typeMask,
            float[] acc,
            float[] dist,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs,
            @Nullable float[] blendSink) {
        final int points = gridSize * gridSize;
        Arrays.fill(typeMask, 0, points, HydrologicalPrimitive.HydrologicalFeature.NONE);
        Arrays.fill(dist, 0, points, (float) 1);

        int stop = 0;
        while (stop < primitives.size() && primitives.get(stop) instanceof RiverPrimitive river) {
            carvePrimitiveInfluence(river, gridSize, acc,dist, lut, perpRow, perpCol, tangRow, tangCol, elevs, blendSink);
            stop++;
        }
    }

    /** One primitive's contribution, clipped to the lattice points its footprint reaches. Primitive-outer
     *  so the profile, the seed, the width-invariant extents and the LUT are computed once, not per point. */
    private static void carvePrimitive(
            RiverPrimitive river,
            double startX,
            double startZ,
            double resolution,
            int gridSize,
            float[] acc,
            long[] typeMask,
            float[] dist,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs) {
        final double[] normal = river.normal();
        // A null normal has no tangent -- river.h() returns flat elevation and the projection would NPE.
        if (normal == null) return;
        final double nx = normal[0], nz = normal[1];
        final double cx = river.coord()[0], cz = river.coord()[1];
        // Half-extents of the primitive's footprint rectangle: along the flow tangent (nz, -nx), and across
        // it along the normal. Read from the same accessors the spatial index stabs, so a primitive whose
        // rectangle stops being square carves the shape it was indexed under.
        final double influenceLen = river.getLength() * 0.5;
        final double influenceWidth = river.getWidth() * 0.5;

        // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow one
        // would silently drop carve -- the exact containment test still runs per lattice point.
        final double halfExtentX = influenceLen * Math.abs(nz) + influenceWidth * Math.abs(nx);
        final double halfExtentZ = influenceLen * Math.abs(nx) + influenceWidth * Math.abs(nz);
        final long rowLo = (long) Math.floor((cx - halfExtentX - startX) / resolution);
        final long rowHi = (long) Math.ceil((cx + halfExtentX - startX) / resolution);
        final long colLo = (long) Math.floor((cz - halfExtentZ - startZ) / resolution);
        final long colHi = (long) Math.ceil((cz + halfExtentZ - startZ) / resolution);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        // perp is affine in the lattice coordinates, so its extrema over the clipped box are at the four
        // corners. Intersecting with the influence band is what caps the LUT at the grid diagonal.
        final double x0 = startX + rowMin * resolution, x1 = startX + rowMax * resolution;
        final double z0 = startZ + colMin * resolution, z1 = startZ + colMax * resolution;
        final double p00 = nx * (x0 - cx) + nz * (z0 - cz);
        final double p01 = nx * (x0 - cx) + nz * (z1 - cz);
        final double p10 = nx * (x1 - cx) + nz * (z0 - cz);
        final double p11 = nx * (x1 - cx) + nz * (z1 - cz);
        final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influenceWidth);
        final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influenceWidth);
        if (perpMin > perpMax) return;

        final double invStep = 1.0 / resolution;
        final int baseIdx = (int) Math.floor(perpMin * invStep);
        final int n = (int) Math.floor(perpMax * invStep) - baseIdx + 2;

        final double width = river.width();
        final double curvature = river.curvature();
        final double elevation = river.elevation();
        final RosgenProfile profile = (RosgenProfile) river.getProfile();
        final long seed = river.seed();
        final double floodPlainLen = profile.floodPlainLength(width);
        final double marginLen = width / 2;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width);
        final float waterSurface = (float) (elevation + HydrologicalPrimitive.waterLine(width));
        final long packed = HydrologicalPrimitive.HydrologicalFeature.RIVER.pack(
                RiverPrimitive.RosgenType.orDefault(river.rosgenType()).ordinal());
        profile.sampleCrossSection(
                lut, n, resolution, baseIdx, seed, elevation, floodPlainLen, marginLen, depth, curvature);

        // :PERF: both projections are affine, so each splits into a row term and a column term; tabulating
        // the two axes costs 2 * gridSize entries and lets the merge rebuild any point with one add.
        for (int row = rowMin; row <= rowMax; row++) {
            final double ddx = (startX + row * resolution) - cx;
            perpRow[row] = nx * ddx;
            tangRow[row] = nz * ddx;
        }
        for (int col = colMin; col <= colMax; col++) {
            final double ddz = (startZ + col * resolution) - cz;
            perpCol[col] = nz * ddz;
            tangCol[col] = -nx * ddz;
        }

        final double invLen = 1.0 / influenceLen;
        final double invWidth = 1.0 / influenceWidth;
        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double perpAtRow = perpRow[row];
            final double tangAtRow = tangRow[row];
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double perp = perpAtRow + perpCol[col];
                final double tang = tangAtRow + tangCol[col];
                // How far the footprint rectangle must be scaled to swallow the point: 1 exactly at the
                // rim, so the recurrence ranks primitives by rectangle penetration, not radial distance.
                final double d = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
                final double mask = d <= 1.0 ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;
                final double f = perp * invStep - baseIdx;
                // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
                // body still evaluates h for those lanes.
                final int i0 = Math.clamp((int) f, 0, n - 2);
                final double h = (elevs != null)
                        ? Math.min(elevs[i], lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]))
                        : lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);

                dist[i] = (float) ((1 - w) * dist[i] + w * d);
                final int a = 3 * i;
                acc[a] = (float) ((1 - w) * acc[a] + w * h);
                acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
                // Whoever owns the majority of the blend owns the type. With SMOOTH_STEP_DIVISOR at 0.1
                // the weight is a near-hard selector, so this is the true nearest bar a 0.1-wide band.
                typeMask[i] = w > 0.5 ? packed : typeMask[i];
                acc[a + 2] = 1 - Math.clamp(dist[i], 0, 1);
            }
        }
    }

    private static void carvePrimitiveInfluence(
            RiverPrimitive river,
            int gridSize,
            float[] acc,
            float[] dist,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs,
            @Nullable float[] blendSink) {
        final double[] normal = river.normal();
        // A null normal has no tangent -- river.h() returns flat elevation and the projection would NPE.
        if (normal == null) return;
        final double nx = normal[0], nz = normal[1];
        final double cx = river.coord()[0], cz = river.coord()[1];
        // Half-extents of the primitive's footprint rectangle: along the flow tangent (nz, -nx), and across
        // it along the normal. Read from the same accessors the spatial index stabs, so a primitive whose
        // rectangle stops being square carves the shape it was indexed under.
        final double influenceLen = river.getLength() * 0.5;
        final double influenceWidth = river.getWidth() * 0.5;

        // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow one
        // would silently drop carve -- the exact containment test still runs per lattice point.
        final double halfExtentX = influenceLen * Math.abs(nz) + influenceWidth * Math.abs(nx);
        final double halfExtentZ = influenceLen * Math.abs(nx) + influenceWidth * Math.abs(nz);
        final long rowLo = (long) Math.floor((cx - halfExtentX - (double) 0));
        final long rowHi = (long) Math.ceil((cx + halfExtentX - (double) 0));
        final long colLo = (long) Math.floor((cz - halfExtentZ - (double) 0));
        final long colHi = (long) Math.ceil((cz + halfExtentZ - (double) 0));
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        // perp is affine in the lattice coordinates, so its extrema over the clipped box are at the four
        // corners. Intersecting with the influence band is what caps the LUT at the grid diagonal.
        final double x0 = (double) 0 + rowMin * 1.0, x1 = (double) 0 + rowMax * 1.0;
        final double z0 = (double) 0 + colMin * 1.0, z1 = (double) 0 + colMax * 1.0;
        final double p00 = nx * (x0 - cx) + nz * (z0 - cz);
        final double p01 = nx * (x0 - cx) + nz * (z1 - cz);
        final double p10 = nx * (x1 - cx) + nz * (z0 - cz);
        final double p11 = nx * (x1 - cx) + nz * (z1 - cz);
        final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influenceWidth);
        final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influenceWidth);
        if (perpMin > perpMax) return;

        final double invStep = 1.0;
        final int baseIdx = (int) Math.floor(perpMin * invStep);
        final int n = (int) Math.floor(perpMax * invStep) - baseIdx + 2;

        final double width = river.width();
        final double curvature = river.curvature();
        final double elevation = river.elevation();
        final double influence = river.influence();
        final RosgenProfile profile = (RosgenProfile) river.getProfile();
        final long seed = river.seed();
        final double floodPlainLen = profile.floodPlainLength(width);
        final double marginLen = width / 2;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width);
        profile.sampleCrossSection(lut, n, 1.0, baseIdx, seed, elevation, floodPlainLen, marginLen, depth, curvature);

        for (int i = 0; i < lut.length; i++) if (lut[i] < elevation) lut[i] = (float) elevation;

        // :PERF: both projections are affine, so each splits into a row term and a column term; tabulating
        // the two axes costs 2 * gridSize entries and lets the merge rebuild any point with one add.
        for (int row = rowMin; row <= rowMax; row++) {
            final double ddx = ((double) 0 + row * 1.0) - cx;
            perpRow[row] = nx * ddx;
            tangRow[row] = nz * ddx;
        }
        for (int col = colMin; col <= colMax; col++) {
            final double ddz = ((double) 0 + col * 1.0) - cz;
            perpCol[col] = nz * ddz;
            tangCol[col] = -nx * ddz;
        }

        double floodPlainThreshold = Math.max(floodPlainLen / influenceLen, floodPlainLen / influenceWidth);
        if(floodPlainThreshold > 0.8) floodPlainThreshold = 0.8;
        final double invLen = 1.0 / influenceLen;
        final double invWidth = 1.0 / influenceWidth;
        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double perpAtRow = perpRow[row];
            final double tangAtRow = tangRow[row];
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double perp = perpAtRow + perpCol[col];
                final double tang = tangAtRow + tangCol[col];
                // How far the footprint rectangle must be scaled to swallow the point: 1 exactly at the
                // rim, so the recurrence ranks primitives by rectangle penetration, not radial distance.
                final double d = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
                final double dd = Math.max(0,(d-floodPlainThreshold)/(1-floodPlainThreshold));
                final double mask = 1;
                final double t = Math.clamp(((dist[i] - dd) / HydrologyTuning.INFLUENCE_BLEND_STRENGH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;
                final double f = perp * invStep - baseIdx;
                // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
                // body still evaluates h for those lanes.
                final int i0 = Math.clamp((int) f, 0, n - 2);

                final int a = 3 * i;
                double heightBeforeCarve = (elevs!=null) ? elevs[i] * (1-acc[a+1]) + acc[a] * acc[a+1] : 1e9;
                final double h = Math.min(heightBeforeCarve, lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]));

                dist[i] = (float) ((1 - w) * dist[i] + w * dd);
                // TODO: treat case where elev is lower than drain height


                acc[a] = (float) ( acc[a] * (1 - w) + h * w);
                acc[a+1] = (float) 1 - Math.clamp(dist[i],0,1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tile-level shell pre-carve, called from RiverProvider's tile pipeline
    // -------------------------------------------------------------------------

    /** One instance of this class serves every tile build, so the carve buffers cannot be fields. */
    private static final ThreadLocal<GridBuffers> SHELL_BUFFERS = ThreadLocal.withInitial(GridBuffers::new);

    /**
     * The distance field the last {@link #carveRiverInfluence} on THIS thread left behind: per lattice
     * point, the footprint scale at which the winning primitive swallows it, {@link #UNSET_MIN_DIST}
     * where none reached. The live scratch buffer, so a reader copies before the next carve overwrites
     * it, and a carve that returned early on an empty primitive list leaves the previous tile's values.
     */
    @TestOnly
    public static float[] shellDistanceField() {
        return SHELL_BUFFERS.get().dist;
    }
}
