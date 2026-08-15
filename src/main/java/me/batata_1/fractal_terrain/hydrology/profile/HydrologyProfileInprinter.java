package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import net.minecraft.world.level.ChunkPos;

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

    /** Cuts the bed of every channel reaching this chunk into {@code columns}, one (elevation, weight)
     *  pair per block. Split per channel rather than per nearest knot so a column caught between two
     *  channels - a confluence, a tight meander - is carved by both instead of only the closer one. */
    public static double[] resolveRiverPrimitives(
            ChunkPos chunkPos,
            double scale,
            List<HydrologicalPrimitive> primitives,
            float[] ambientElevation,
            HydrologicalPrimitive.HydrologicalFeature[] riverType,
            float[] waterElev) {
        final int startX = chunkPos.getMinBlockX();
        final int startZ = chunkPos.getMinBlockZ();

        final double[] columns = new double[256 * 2];
        final double[] mutablePt = new double[2];
        final double[] mutableArray = new double[2];
        final float[] indexWeightDistance = new float[3];
        // Channel runs depend only on the prefetched list, so they are resolved once for all 256 columns.
        final int[] channelRuns = resolveChannelRuns(primitives);
        final int channelCount = Math.max(channelRuns.length - 1, 0);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                riverType[pos] = null;
                mutablePt[0] = (startX + dx) / scale;
                mutablePt[1] = (startZ + dz) / scale;

                final double curElev = ambientElevation[pos];
                double weightSum = 0;
                double weightedElev = -1;
                // The strongest contributor, which carries the column when every influence weight is zero.
                double bestWeight = -1;
                double bestElev = 0;
                // Tracked apart from bestWeight: the channel whose bed the column sits in owns the water
                // surface, and that need not be the channel with the largest influence weight.
                double claimWeight = -1;

                for (int channel = 0; channel < channelCount; channel++) {
                    final int nearestPrimitiveIndex = resolveNearestRiverPrimitiveIndex(
                            primitives, mutablePt, channelRuns[channel], channelRuns[channel + 1]);
                    if (nearestPrimitiveIndex == -1) continue;
                    if (!primitives.get(nearestPrimitiveIndex).containsPoint(mutablePt)) continue;

                    indexWeightDistance[0] = -1;
                    indexWeightDistance[1] = 1;
                    indexWeightDistance[2] = Float.MAX_VALUE;
                    sampleNearestChannel(
                            primitives, nearestPrimitiveIndex, mutablePt, indexWeightDistance, mutableArray);

                    final int neighborIndex = Float.floatToIntBits(indexWeightDistance[0]);
                    final boolean paired = neighborIndex > 0;
                    final RiverPrimitive n0 = (RiverPrimitive) primitives.get(nearestPrimitiveIndex);
                    final RiverPrimitive n1 =
                            (RiverPrimitive) primitives.get(paired ? neighborIndex : nearestPrimitiveIndex);
                    final double signedDist = paired ? indexWeightDistance[2] : n0.d(mutablePt);
                    final double lerpWeight = paired ? 1 : indexWeightDistance[1];

                    final double channelWeight = Interpolation.lerp(n0.w(mutablePt), n1.w(mutablePt), lerpWeight);
                    final double channelElev = Interpolation.lerp(
                            Math.min(curElev, n0.h(signedDist)), Math.min(curElev, n1.h(signedDist)), lerpWeight);

                    weightSum += channelWeight;
                    if (weightedElev < 0) weightedElev = channelElev;
                    else {
                        weightedElev = channelElev * channelWeight + (1 - channelWeight) * weightedElev;
                    }
                    if (channelWeight > bestWeight) {
                        bestWeight = channelWeight;
                        bestElev = channelElev;
                    }

                    final double width = Interpolation.lerp(n0.width(), n1.width(), lerpWeight);
                    if (Math.abs(signedDist) > (width / 2) + 0.25) continue;
                    if (claimWeight >= 0 && channelWeight <= claimWeight) continue;
                    claimWeight = channelWeight;
                    final double bedElev = Interpolation.lerp(n0.elevation(), n1.elevation(), lerpWeight);
                    riverType[pos] = HydrologicalPrimitive.HydrologicalFeature.RIVER;
                    waterElev[pos] = (float) (HydrologicalPrimitive.waterLine(width) + bedElev);
                }

                if (bestWeight < 0) continue;
                columns[pos * 2] = weightSum > 1e-8 ? weightedElev : 0;
                columns[pos * 2 + 1] = Math.clamp(weightSum, 0, 1);
            }
        }
        return columns;
    }

    // -------------------------------------------------------------------------
    // Per-pixel refinement (zone-priority merge)
    // -------------------------------------------------------------------------

    /**
     * A chunk's influencing primitives, gathered once (see {@link #queryPrimitives}) so the per-block carve never
     * re-queries the spatial index. Produced by {@link #prefetchChunk} / {@link #queryPrimitives} and consumed
     * by {@link #resolveNearestRiverPrimitiveIndex} / {@link #sampleNearestChannel}.
     */
    public record PrefetchedPrimitives(List<HydrologicalPrimitive> primitives) {}

    /** Amortizes the influence query across a whole chunk — one tree query per chunk rather than one
     *  per block. Feed the result to {@link #sampleNearestChannel}. */
    public List<HydrologicalPrimitive> prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        var primitives = localRiver.queryInfluence(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
        primitives.sort(HydrologicalPrimitive.comparator);
        return primitives;
    }

    /** Query the primitives influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedPrimitives queryPrimitives(double[] pt, double extraRadius) {
        return new PrefetchedPrimitives(localRiver.queryInfluence(pt, extraRadius));
    }

    /** As {@link #resolveNearestRiverPrimitiveIndex(List, double[])}, narrowed to one channel's knot run so the
     *  carve can resolve each channel's own nearest knot instead of a single global winner. */
    public static int resolveNearestRiverPrimitiveIndex(
            List<HydrologicalPrimitive> primitives, double[] point, int fromIndex, int toIndex) {
        int nearestIndex = -1;
        double nearestDistSq = Double.MAX_VALUE;
        for (int i = fromIndex; i < toIndex; i++) {
            if (!(primitives.get(i) instanceof RiverPrimitive river)) break;
            final double distSq = VectorOps.distanceSquared(point, river.coord());
            if (distSq >= nearestDistSq) continue;
            nearestIndex = i;
            nearestDistSq = distSq;
        }
        return nearestIndex;
    }

    public static int resolveNearestPrimitiveIndex(List<HydrologicalPrimitive> primitives, double[] point) {
        int nearestIndex = -1;
        double nearestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < primitives.size(); i++) {
            if (!(primitives.get(i) instanceof RiverPrimitive river)) break;
            final double distSq = VectorOps.distanceSquared(point, river.coord());
            if (distSq >= nearestDistSq) continue;
            nearestIndex = i;
            nearestDistSq = distSq;
        }
        return nearestIndex;
    }

    /** No channel reaches the query - the empty run table. */
    private static final int[] NO_CHANNELS = new int[0];

    /** Start index of each channel's knot run, terminated by the end of the last run. Relies on
     *  {@code HydrologicalPrimitive.comparator} sorting rivers first and then by packed {@code ids}, which
     *  leaves every channel's knots in one half-open interval {@code [runs[c], runs[c + 1])}. */
    public static int[] resolveChannelRuns(List<HydrologicalPrimitive> primitives) {
        int riverCount = 0;
        while (riverCount < primitives.size() && primitives.get(riverCount) instanceof RiverPrimitive) riverCount++;
        if (riverCount == 0) return NO_CHANNELS;

        final int[] runs = new int[riverCount + 1];
        int runCount = 0;
        int previousChannelId = 0;
        for (int i = 0; i < riverCount; i++) {
            final int channelId = ((RiverPrimitive) primitives.get(i)).channelId();
            if (i == 0 || channelId != previousChannelId) runs[runCount++] = i;
            previousChannelId = channelId;
        }
        runs[runCount++] = riverCount;
        return Arrays.copyOf(runs, runCount);
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
                        weightedElev += deltaWeight * river.h(river.d(pixel));
                    }
                }
                if (weight <= 1e-8) continue;
                final double elev = weightedElev / weight;
                weight = Math.clamp(weight, 0, 1);
                elevation[idx] = (float) ((1 - weight) * elevation[idx] + weight * elev);
            }
        }
    }
}
