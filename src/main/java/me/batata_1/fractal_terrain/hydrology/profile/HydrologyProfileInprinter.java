package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import org.jetbrains.annotations.TestOnly;

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

    /**
     * A chunk's influencing primitives, gathered once (see {@link #queryPrimitives}) so the per-block merge never
     * re-queries the spatial index. Produced by {@link #prefetchChunk} / {@link #queryPrimitives} and consumed
     * by {@link #carvePrefetched}.
     */
    public record PrefetchedPrimitives(List<HydrologicalPrimitive> primitives) {}

    /** Amortizes the influence query across a whole chunk — one tree query per chunk rather than one
     *  per block. Feed the result to {@link #carvePrefetched}. */
    public List<HydrologicalPrimitive> prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        var primitives = localRiver.queryInfluence(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
        primitives.sort(HydrologicalPrimitive.comparator);
        return primitives;
    }

    /** Query the primitives influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedPrimitives queryPrimitives(double[] pt, double extraRadius) {
        return new PrefetchedPrimitives(localRiver.queryInfluence(pt, extraRadius));
    }

    private static int resolveRiverNearestId(List<HydrologicalPrimitive> primitives, double[] pt) {
        int nearestid = -1;
        double dist = 1e9;
        for (int id = 0; id < primitives.size(); id++) {
            if (!(primitives.get(id) instanceof RiverPrimitive river)) break;
            if (VectorOps.distanceSquared(pt, river.coord()) >= dist) continue;
            nearestid = id;
            dist = VectorOps.distanceSquared(pt, river.coord());
        }
        return nearestid;
    }

    /**
     * The signed perpendicular distance from {@code point} to the nearest channel, with that channel's
     * cross-section read at the foot point.
     *
     * <p>Measures against the two-segment polyline through the nearest knot rather than the quintic:
     * the primitives carry no velocity or acceleration, so the true curve cannot be rebuilt here.
     */
    public static NearestChannelSample sampleNearestChannel(
            List<HydrologicalPrimitive> primitives, int nearestPrimitiveIndex, double[] point) {
        if (nearestPrimitiveIndex < 0 || nearestPrimitiveIndex >= primitives.size()) return null;
        if (!(primitives.get(nearestPrimitiveIndex) instanceof RiverPrimitive nearestKnot)) return null;

        final double[] projection = new double[2];
        RiverPrimitive segStart = null;
        RiverPrimitive segEnd = null;
        double bestSegParam = 0.0;
        double bestDistSq = Double.MAX_VALUE;

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
            if (projection[1] >= bestDistSq) continue;
            bestDistSq = projection[1];
            bestSegParam = projection[0];
            segStart = start;
            segEnd = end;
        }

        if (segStart == null) return isolatedKnotSample(nearestKnot, point);
        return interpolatedSample(segStart, segEnd, bestSegParam, bestDistSq, point);
    }

    /** A knot with no knot-adjacent neighbour in range influences the point alone, so its tangent
     *  line is the correct answer — not a degraded one. */
    private static NearestChannelSample isolatedKnotSample(RiverPrimitive knot, double[] point) {
        return new NearestChannelSample(
                knot.d(point), knot.width(), knot.curvature(), knot.elevation(), knot.rosgenType(), knot.channelId());
    }

    /** Signs the distance with the interpolated normal so it agrees with {@link RiverPrimitive#d},
     *  which is the convention the asymmetric Rosgen profiles were tuned against. */
    private static NearestChannelSample interpolatedSample(
            RiverPrimitive segStart, RiverPrimitive segEnd, double segParam, double distSq, double[] point) {
        final double footX = lerp(segStart.coord()[0], segEnd.coord()[0], segParam);
        final double footZ = lerp(segStart.coord()[1], segEnd.coord()[1], segParam);
        final double[] footNormal = VectorOps.normalize(new double[] {
            lerp(segStart.normal()[0], segEnd.normal()[0], segParam),
            lerp(segStart.normal()[1], segEnd.normal()[1], segParam)
        });
        final double side = footNormal[0] * (point[0] - footX) + footNormal[1] * (point[1] - footZ);
        return new NearestChannelSample(
                Math.signum(side) * Math.sqrt(distSq),
                lerp(segStart.width(), segEnd.width(), segParam),
                lerp(segStart.curvature(), segEnd.curvature(), segParam),
                lerp(segStart.elevation(), segEnd.elevation(), segParam),
                segParam < 0.5 ? segStart.rosgenType() : segEnd.rosgenType(),
                segStart.channelId());
    }

    private static double lerp(double from, double to, double t) {
        return from + t * (to - from);
    }

    // -------------------------------------------------------------------------
    // Tile-level shell pre-carve (moved from LocalRiverProvider)
    // -------------------------------------------------------------------------

    /** Slack around the tile grid for the primitive index bounds (primitives may overshoot the pad). */
    private static final double CARVE_INDEX_SLACK = 64.0;

    /** Carves the valley shell in place. Does not zone — the shell is one broad pull, applied before any
     *  primitive has a bed to distinguish. Compounds across calls on the same buffer, which
     *  {@code buildTile} relies on when it carves twice per tile. */
    public static void carveRiverShells(float[] elevation, HydrologicalPrimitive[] primitives, int paddedSize) {
        //       carveRiverShellsNearest(elevation, primitives, paddedSize);
        if (primitives.length == 0) return;
        final ImmutableRTree<HydrologicalPrimitive> index =
                new ImmutableRTree<>(Arrays.asList(primitives), HydrologicalPrimitive.PROTOTYPE);

        double weight = 0;
        double weightedElev = 0;
        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalPrimitive> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;
                weight = 0;
                weightedElev = 0;
                for (final HydrologicalPrimitive primitive : nearby) {
                    if (!primitive.containsPoint(pixel)) continue;
                    if (primitive instanceof RiverPrimitive river) {
                        final double deltaWeight = river.w(pixel);
                        weight += deltaWeight;
                        weightedElev += deltaWeight * river.h(pixel);
                    }
                }
                if (weight <= 1e-8) continue;
                final double elev = weightedElev / weight;
                weight = Math.clamp(weight, 0, 1);
                elevation[idx] = (float) ((1 - weight) * elevation[idx] + weight * elev);
            }
        }
    }

    @TestOnly
    public static void carveRiverShellsNearest(float[] elevation, HydrologicalPrimitive[] primitives, int paddedSize) {
        if (primitives.length == 0) return;
        final ImmutableRTree<HydrologicalPrimitive> index =
                new ImmutableRTree<>(Arrays.asList(primitives), HydrologicalPrimitive.PROTOTYPE);

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalPrimitive> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;

                HydrologicalPrimitive nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (final HydrologicalPrimitive primitive : nearby) {
                    final double[] coord = primitive.coord();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);

                    if (!primitive.containsPoint(pixel)) continue; // outside this primitive's influence
                    if (radialDist < nearestDist && primitive instanceof RiverPrimitive river) {
                        nearestDist = radialDist;
                        nearest = river;
                    }
                }

                if (nearest == null) continue;
                elevation[idx] = (float) nearest.getProfile().shellElevation(nearest, nearestDist, elevation[idx]);
            }
        }
    }
}
