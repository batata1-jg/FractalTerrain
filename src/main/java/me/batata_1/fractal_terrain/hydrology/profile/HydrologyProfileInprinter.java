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
