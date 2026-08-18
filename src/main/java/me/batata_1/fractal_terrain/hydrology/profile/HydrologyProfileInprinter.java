package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;

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
