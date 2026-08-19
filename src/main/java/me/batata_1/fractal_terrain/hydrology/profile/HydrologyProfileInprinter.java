package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.math.VectorOps;

/**
 * The elevation side of the hydrology profile — where rivers actually cut the terrain.
 *
 * <p>Split across two stages because they run at different times against different data: the tile-level
 * shell carve broad-brushes the valley during {@code LocalRiverProvider.buildTile}, and the per-pixel
 * refinement cuts the bed trench below it later, driven per chunk from {@code PopulateNoiseStep}.
 *
 * <p>All geometry is in the relief-pixel frame. The carving twin of {@code HydrologyProfilePainter}.
 */
public final class HydrologyProfileInprinter {

    private final LocalRiverProvider localRiver;

    public HydrologyProfileInprinter(LocalRiverProvider localRiver) {
        this.localRiver = localRiver;
    }

    // -------------------------------------------------------------------------
    // Per-pixel refinement (zone-priority merge)
    // -------------------------------------------------------------------------

    /** Amortizes the influence query across a whole chunk — one tree query per chunk rather than one
     *  per block. Feed the result to {@link #sampleNearestChannel}. */
    public List<HydrologicalPrimitive> prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        var primitives = localRiver.queryInfluence(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
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
    public static void sampleNearestChannel(
            List<HydrologicalPrimitive> primitives,
            int nearestPrimitiveIndex,
            double[] point,
            float[] indexWeightDistance,
            double[] projection) {
        if (nearestPrimitiveIndex < 0 || nearestPrimitiveIndex >= primitives.size()) return;
        if (!(primitives.get(nearestPrimitiveIndex) instanceof RiverPrimitive nearestKnot)) return;

        int otherIndex = -1;
        double nearestWeight = 1.0;
        double bestDist = Double.MAX_VALUE;

        for (int offset = -1; offset <= 1; offset += 2) {
            final int neighbourIndex = nearestPrimitiveIndex + offset;
            if (neighbourIndex < 0 || neighbourIndex >= primitives.size()) continue;
            if (!(primitives.get(neighbourIndex) instanceof RiverPrimitive neighbour)) continue;
            if (!nearestKnot.isKnotAdjacentTo(neighbour)) continue;
            // Always orient the segment downstream, so segParam interpolates from the lower knot up.
            final boolean neighbourIsUpstream = neighbour.knotIndex() < nearestKnot.knotIndex();
            final RiverPrimitive start = neighbourIsUpstream ? neighbour : nearestKnot;
            final RiverPrimitive end = neighbourIsUpstream ? nearestKnot : neighbour;
            VectorOps.projectPointOntoSegment(point, start.coord(), end.coord(), projection);
            if (Math.abs(projection[1]) >= Math.abs(bestDist)) continue;
            bestDist = projection[1];
            // segParam runs start -> end, so the nearest knot's share of it is segParam only when the
            // neighbour is the upstream end of the segment.
            nearestWeight = neighbourIsUpstream ? projection[0] : 1.0 - projection[0];
            otherIndex = neighbourIndex;
        }

        // A knot with no knot-adjacent neighbour in range influences the point alone, so its tangent
        // line is the correct answer — not a degraded one — and the whole lerpWeight is its own.
        if (otherIndex == -1) return;

        indexWeightDistance[0] = Float.intBitsToFloat(otherIndex);
        indexWeightDistance[1] = (float) nearestWeight;
        indexWeightDistance[2] = (float) bestDist;
    }

    // -------------------------------------------------------------------------
    // Lattice carve (shared by the bed and shell stages)
    // -------------------------------------------------------------------------

    /** Per-lattice-point "no primitive seen yet" distance, in relief-pixels. */
    public static final double UNSET_MIN_DIST = 64;

    // Testing override: at 0.1 this collapses the distance blend to a near-hard 0/1 selector. Restoring
    // the real blend needs HydrologyTuning.PRIMITIVE_BLEND_STRENGTH in its place.
    /** Blend width of the distance smoothstep, in relief-pixels. */
    public static final double SMOOTH_STEP_DIVISOR = 0.1;

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

        /** Grows any buffer that is too small. Never shrinks — the carve fills only the range it uses. */
        public void ensure(int points, int lutLen) {
            if (acc.length < 3 * points) acc = new float[3 * points];
            if (typeMask.length < points) typeMask = new long[points];
            if (dist.length < points) dist = new float[points];
            if (lut.length < lutLen) lut = new float[lutLen];
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
            float[] elevs
    ) {
        final int points = gridSize * gridSize;
        Arrays.fill(acc, 0, 3 * points, 0f);
        Arrays.fill(typeMask, 0, points, HydrologicalPrimitive.HydrologicalFeature.NONE);
        Arrays.fill(dist, 0, points, (float) UNSET_MIN_DIST);

        int stop = 0;
        while (stop < primitives.size() && primitives.get(stop) instanceof RiverPrimitive river) {
            carvePrimitive(river, startX, startZ, resolution, gridSize, acc, typeMask, dist, lut,elevs);
            stop++;
        }

        // The height lane accumulates weighted, so it needs one division to become a surface. The water
        // lane does not: its blend default is 0, and (1 - W) * 0 + W * (acc / W) is the raw accumulator.
        for (int i = 0; i < points; i++) {
            final float weight = acc[3 * i + 2];
            if (weight > 0) acc[3 * i] /= weight;
        }
        return stop;
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
            float[] elevs) {
        final double[] normal = river.normal();
        // A null normal has no tangent -- river.h() returns flat elevation and the projection would NPE.
        if (normal == null) return;
        final double nx = normal[0], nz = normal[1];
        final double cx = river.coord()[0], cz = river.coord()[1];
        final double influence = river.influence();

        // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow one
        // would silently drop carve -- the exact containment test still runs per lattice point.
        final double halfExtent = influence * (Math.abs(nx) + Math.abs(nz));
        final long rowLo = (long) Math.floor((cx - halfExtent - startX) / resolution);
        final long rowHi = (long) Math.ceil((cx + halfExtent - startX) / resolution);
        final long colLo = (long) Math.floor((cz - halfExtent - startZ) / resolution);
        final long colHi = (long) Math.ceil((cz + halfExtent - startZ) / resolution);
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
        final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influence);
        final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influence);
        if (perpMin > perpMax) return;

        final double invStep = 1.0 / resolution;
        final int baseIdx = (int) Math.floor(perpMin * invStep);
        final int n = (int) Math.floor(perpMax * invStep) - baseIdx + 2;

        final double width = river.width();
        final double curvature = river.curvature();
        final double elevation = river.elevation();
        final RosgenProfile profile = (RosgenProfile) river.getProfile();
        final long seed = river.ids();
        final double floodPlainLen = profile.floodPlainLength(width);
        final double marginLen = width / 2;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width);
        final float waterSurface = (float) (elevation + HydrologicalPrimitive.waterLine(width));
        final long packed = HydrologicalPrimitive.HydrologicalFeature.RIVER.pack(
                RiverPrimitive.RosgenType.orDefault(river.rosgenType()).ordinal());
        profile.sampleCrossSection(
                lut, n, resolution, baseIdx, seed, elevation, floodPlainLen, marginLen, depth, curvature);

        for (int row = rowMin; row <= rowMax; row++) {
            final double ddx = (startX + row * resolution) - cx;
            final double ddz0 = (startZ + colMin * resolution) - cz;
            double perp = nx * ddx + nz * ddz0;
            double tang = ddx * nz - ddz0 * nx;
            double f = perp * invStep - baseIdx;
            final int rowBase = row * gridSize;
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double d = (tang * tang + perp * perp) / (influence*influence);
                final double mask = Math.abs(tang) <= influence && Math.abs(perp) <= influence ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / SMOOTH_STEP_DIVISOR + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;
                // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
                // body still evaluates h for those lanes.
                final int i0 = Math.clamp((int) f, 0, n - 2);
                final double h = (elevs!=null) ? Math.min(elevs[i],lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0])):lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);

                dist[i] = (float) ((1 - w) * dist[i] + w * d);
                final int a = 3 * i;
                acc[a] = (float) ((1 - w) * acc[a] + w * h);
                acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
                // Whoever owns the majority of the blend owns the type. With SMOOTH_STEP_DIVISOR at 0.1
                // the weight is a near-hard selector, so this is the true nearest bar a 0.1-wide band.
                typeMask[i] = w > 0.5 ? packed : typeMask[i];
                acc[a + 2] = (float) (acc[a + 2] + w * (1 - acc[a + 2]));

                perp += nz * resolution;
                tang -= nx * resolution;
                f += nz;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tile-level shell pre-carve (moved from LocalRiverProvider)
    // -------------------------------------------------------------------------

    /** One instance of this class serves every tile build, so the carve buffers cannot be fields. */
    private static final ThreadLocal<GridBuffers> SHELL_BUFFERS = ThreadLocal.withInitial(GridBuffers::new);

    /** Carves the valley shell in place. Does not zone — the shell is one broad pull, applied before any
     *  primitive has a bed to distinguish. Compounds across calls on the same buffer. */
    public static void carveRiverShells(float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize) {
        if (primitives.isEmpty()) return;
        final int points = paddedSize * paddedSize;
        final GridBuffers buffers = SHELL_BUFFERS.get();
        buffers.ensure(points, maxLutLen(paddedSize, 1.0));
        final float[] acc = buffers.acc;

        computeRiverGrid(0, 0, 1.0, paddedSize, primitives, acc, buffers.typeMask, buffers.dist, buffers.lut, null);

        for (int i = 0; i < points; i++) {
            final float ambient = elevation[i];
            if (ambient < 0) continue;
            final float weight = acc[3 * i + 2];
            if (weight <= 1e-8f) continue;
            elevation[i] = (float) ((1 - weight) * ambient + weight * Math.min(acc[3 * i], ambient));
        }
    }
}
