package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;

/**
 * The elevation side of the hydrology profile. Two carve stages live here:
 *
 * <ul>
 *   <li><b>Tile-level shell carve</b> ({@link #carveRiverShells}, static) — the valley/floodplain carve
 *       run over a whole padded relief tile by {@code LocalRiverProvider.buildTile}. Each pixel is pulled
 *       toward the distance-weighted average of every influencing unit's
 *       {@link HydrologyProfile#shellElevation}. {@code buildTile} calls it twice, both times over an
 *       <em>unfiltered</em> unit collection. The first call is global-only by timing alone — the local
 *       network does not exist yet — while the second, running after the local trace, carves local shells
 *       too.</li>
 *   <li><b>Per-pixel refinement</b> ({@link #carveAtPixel}, {@link #carvePrefetched}) — queries the
 *       per-tile river network ({@link LocalRiverProvider#queryInfluence}) and resolves the influencing
 *       units through the {@link ZoneCategory} hierarchy, cutting the bed trench below the shell. Driven
 *       per chunk from {@code PopulateNoiseStep}.</li>
 * </ul>
 *
 * All geometry is in the relief-pixel frame. This is the carving twin of {@code HydrologyProfilePainter}.
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

    /**
     * Carve at a point already in the relief-pixel frame: query the influencing units, then merge via
     * {@link #carvePrefetched}.
     */
    public float carveAtPixel(double[] pt, double shellElevAtPixel) {
        final PrefetchedUnits prefetched = queryUnits(pt, 0.0);
        return carvePrefetched(prefetched, pt, shellElevAtPixel);
    }

    /**
     * One cross-tile influence query serving a whole chunk of carve calls: gathers every unit that could
     * influence <em>any</em> point within {@code chunkRadiusPx} of {@code (centerPixelX, centerPixelZ)}
     * (a unit is kept when the center lies within its influence radius {@code + chunkRadiusPx}). Feed the
     * result to {@link #carvePrefetched} for each block — one tree query per chunk instead of one per
     * block. All arguments are in the relief-pixel frame.
     */
    public PrefetchedUnits prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        return queryUnits(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
    }

    /** Query the units influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedUnits queryUnits(double[] pt, double extraRadius) {
        return new PrefetchedUnits(localRiver.queryInfluence(pt, extraRadius));
    }

    /**
     * The elevation at {@code pt} after every prefetched unit that reaches it has had its say, resolved
     * through the {@link ZoneCategory} hierarchy. Three steps:
     *
     * <ol>
     *   <li><b>Claim.</b> Each unit asks its own {@link HydrologyProfile} which single zone the point
     *       falls in at this radial distance ({@link HydrologyProfile#categoryAt}) — its bed, its
     *       floodplain, its outer influence band, or nothing at all. A unit claims exactly one zone, the
     *       innermost it defines, never several.</li>
     *   <li><b>Average per zone.</b> The unit's own cross-section
     *       ({@link HydrologicalUnit#carveFineGrained}) goes into that zone's running average, weighted by
     *       {@link HydrologyProfile#zoneWeight} so nearer units dominate and each fades out smoothly at
     *       its zone boundary. Zones accumulate independently — a bed contribution never dilutes the
     *       floodplain average.</li>
     *   <li><b>Resolve.</b> The winner is the first zone in {@link ZoneCategory}'s declared priority order
     *       that any unit actually claimed, and its average is the answer. <b>Empty zones are skipped, not
     *       ranked</b>: a pixel deep in a floodplain that no bed contains resolves to the floodplain
     *       average, even though {@code BED} outranks it. So the hierarchy decides who wins where features
     *       overlap, and is silent everywhere else.</li>
     * </ol>
     *
     * <p>Averaging within a zone but switching hard between zones is deliberate: two rivers sharing a
     * floodplain should blend, but a channel bed crossing that same floodplain should cut through it
     * rather than be averaged into it.
     *
     * <p>Returns {@code elevAtPixel} untouched when no unit claims anything.
     */
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

    /**
     * Carves the valley/floodplain shell for {@code units} into {@code elevation} in place (a
     * {@code paddedSize × paddedSize} row-major tile buffer, relief-pixel frame).
     *
     * <p>Per pixel: stab a freshly-built R-tree over {@code units}, keep the candidates whose influence
     * circle actually contains the pixel, and overwrite the pixel with the distance-weighted average of
     * their {@link HydrologyProfile#shellElevation} values — so a confluence blends the profiles of both
     * channels rather than snapping to one. Pixels with a negative ambient elevation (ocean) are skipped.
     *
     * <p>Unlike {@link #carvePrefetched} this stage does not zone: the shell is the one broad valley pull,
     * and it runs before any unit has a bed to distinguish.
     *
     * <p>Writes into the same buffer it reads, so repeated calls on one buffer compound: {@code buildTile}
     * relies on this, carving the global shell once before the drainage trace and again after the
     * unified bed-elevation assignment.
     */
    public static void carveRiverShells(float[] elevation, HydrologicalUnit[] units, int paddedSize) {
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

                final double curElev = elevation[idx];
                double avgSum = 0;
                double avgWeight = 0;
                for (final HydrologicalUnit unit : nearby) {
                    final double[] coord = unit.getCenter();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    final double influenceRadius = unit.getRadius();
                    if (radialDist >= influenceRadius) continue; // outside this unit's influence
                    final double weight = 1 - radialDist / influenceRadius;
                    avgSum += weight * unit.getProfile().shellElevation(unit, radialDist, curElev);
                    avgWeight += weight;
                }

                if (avgWeight <= 1e-6) continue;
                elevation[idx] = (float) (avgSum / avgWeight);
            }
        }
    }
}
