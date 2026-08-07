package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit;
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
     * A chunk's influencing units, gathered once (see {@link #queryUnits}) so the per-block merge never
     * re-queries the spatial index. Produced by {@link #prefetchChunk} / {@link #queryUnits} and consumed
     * by {@link #carvePrefetched}.
     */
    public record PrefetchedUnits(HydrologicalUnit[] units) {}

    /** Single-point carve; {@link #prefetchChunk} is the path for anything hot. */
    public float carveAtPixel(double[] pt, double shellElevAtPixel) {
        final PrefetchedUnits prefetched = queryUnits(pt, 0.0);
        return carvePrefetched(prefetched, pt, shellElevAtPixel);
    }

    /** Amortizes the influence query across a whole chunk — one tree query per chunk rather than one
     *  per block. Feed the result to {@link #carvePrefetched}. */
    public PrefetchedUnits prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        return queryUnits(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
    }

    /** Query the units influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedUnits queryUnits(double[] pt, double extraRadius) {
        return new PrefetchedUnits(localRiver.queryInfluence(pt, extraRadius));
    }

    /** Merges every influencing unit into one elevation, resolved through the {@link ZoneCategory}
     *  hierarchy. Averages within a zone but switches hard between zones, deliberately: two rivers
     *  sharing a floodplain should blend, a bed crossing it should cut through. */
    public float carvePrefetched(PrefetchedUnits prefetched, double[] pt, double elevAtPixel) {
        final HydrologicalUnit[] units = prefetched.units();
        final double[] zoneSums = new double[ZoneCategory.COUNT];
        final double[] zoneWeights = new double[ZoneCategory.COUNT];

        for (final HydrologicalUnit unit : units) {
            final double[] unitCoord = unit.getCenter();
            final double radialDist = Math.hypot(pt[0] - unitCoord[0], pt[1] - unitCoord[1]);
            final HydrologyProfile profile = unit.getProfile();
            final ZoneCategory zone = profile.categoryAt(unit, radialDist);
            if (zone == null) continue; // out of this unit's reach
            final double weight = profile.zoneWeight(unit, zone, radialDist);
            if (weight <= 0) continue;
            final int slot = zone.ordinal();
            zoneSums[slot] += weight * unit.carveFineGrained(pt, elevAtPixel);
            zoneWeights[slot] += weight;
        }

        for (final ZoneCategory zone : ZoneCategory.BY_PRIORITY) {
            final int slot = zone.ordinal();
            if (zoneWeights[slot] > 1e-6) return (float) (zoneSums[slot] / zoneWeights[slot]);
        }
        return (float) elevAtPixel;
    }

    // -------------------------------------------------------------------------
    // Tile-level shell pre-carve (moved from LocalRiverProvider)
    // -------------------------------------------------------------------------

    /** Slack around the tile grid for the unit index bounds (units may overshoot the pad). */
    private static final double CARVE_INDEX_SLACK = 64.0;

    /** Carves the valley shell in place. Does not zone — the shell is one broad pull, applied before any
     *  unit has a bed to distinguish. Compounds across calls on the same buffer, which
     *  {@code buildTile} relies on when it carves twice per tile. */
    public static void carveRiverShells(float[] elevation, HydrologicalUnit[] units, int paddedSize) {
 //       carveRiverShellsNearest(elevation, units, paddedSize);
        if (units.length == 0) return;
        final ImmutableRTree<HydrologicalUnit> index =
                new ImmutableRTree<>(Arrays.asList(units), HydrologicalUnit.PROTOTYPE);

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalUnit> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;
                nearby.sort((HydrologicalUnit a,HydrologicalUnit b) -> {
                    if(a.getRadius() < b.getRadius()) return -1;
                    return 1;
                });
                double curElev = elevation[idx];
                for (final HydrologicalUnit unit : nearby) {
                    final double[] coord = unit.getCenter();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    final double influenceRadius = unit.getRadius();
                    if (radialDist >= influenceRadius) continue; // outside this unit's influence
                    if(curElev <= unit.getProfile().shellElevation(unit, radialDist, elevation[idx])) continue;
                    curElev = unit.getProfile().shellElevation(unit, radialDist, curElev);
                }

                elevation[idx] = (float) curElev;
            }
        }
    }

    @TestOnly
    public static void carveRiverShellsNearest(float[] elevation, HydrologicalUnit[] units, int paddedSize) {
        if (units.length == 0) return;
        final ImmutableRTree<HydrologicalUnit> index =
                new ImmutableRTree<>(Arrays.asList(units), HydrologicalUnit.PROTOTYPE);

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalUnit> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;

                HydrologicalUnit nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (final HydrologicalUnit unit : nearby) {
                    final double[] coord = unit.getCenter();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    final double influenceRadius = unit.getRadius();
                    if (radialDist >= influenceRadius) continue; // outside this unit's influence
                    if( radialDist < nearestDist && unit instanceof RiverUnit river) {
                        nearestDist = radialDist;
                        nearest = river;
                    }
                }

                if(nearest == null) continue;
                elevation[idx] = (float) nearest.getProfile().shellElevation(nearest,nearestDist,elevation[idx]);
            }
        }
    }

}
