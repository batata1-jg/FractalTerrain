package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
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
public final class HydrologyProfileCarver {

    private final LocalRiverProvider localRiver;

    public HydrologyProfileCarver(LocalRiverProvider localRiver) {
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
    public record PrefetchedPrimitives(HydrologicalPrimitive[] primitives) {}

    /** Single-point carve; {@link #prefetchChunk} is the path for anything hot. */
    public float carveAtPixel(double[] pt, double shellElevAtPixel) {
        final PrefetchedPrimitives prefetched = queryPrimitives(pt, 0.0);
        return carvePrefetched(prefetched, pt, shellElevAtPixel);
    }

    /** Amortizes the influence query across a whole chunk — one tree query per chunk rather than one
     *  per block. Feed the result to {@link #carvePrefetched}. */
    public PrefetchedPrimitives prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        return queryPrimitives(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
    }

    /** Query the primitives influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedPrimitives queryPrimitives(double[] pt, double extraRadius) {
        return new PrefetchedPrimitives(localRiver.queryInfluence(pt, extraRadius));
    }

    /** Merges every influencing primitive into one elevation, resolved through the {@link ZoneCategory}
     *  hierarchy. Averages within a zone but switches hard between zones, deliberately: two rivers
     *  sharing a floodplain should blend, a bed crossing it should cut through. */
    public float carvePrefetched(PrefetchedPrimitives prefetched, double[] pt, double elevAtPixel) {
        final HydrologicalPrimitive[] primitives = prefetched.primitives();

        double weight = 0;
        double weightedElev = 0;
        for (final HydrologicalPrimitive primitive : primitives) {
            final double w = primitive.w(pt);
            if (!(primitive instanceof RiverPrimitive)) continue;
            weightedElev += w * primitive.h(pt);
            weight += w;
        }
        if(weight <= 1e-6) return (float) elevAtPixel;
        return (float) (weightedElev / weight);
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
                    final double[] coord = primitive.getCenter();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    final double influenceRadius = primitive.getRadius();
                    if (radialDist >= influenceRadius) continue;
                    if( primitive instanceof RiverPrimitive river) {
                        final double deltaWeight = river.w(pixel);
                        weight += deltaWeight;
                        weightedElev += deltaWeight * river.h(pixel);
                    }
                }
                if(weight <= 1e-6) continue;
//                final double elev = weightedElev / weight;
                elevation[idx] = (float) ( weightedElev /weight);
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
                    final double[] coord = primitive.getCenter();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    final double influenceRadius = primitive.getRadius();
                    if (radialDist >= influenceRadius) continue; // outside this primitive's influence
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
