package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;

/**
 * The elevation side of the hydrology profile. Two carve stages live here:
 *
 * <ul>
 *   <li><b>Tile-level shell carve</b> ({@link #carveRiverShells}, static) — the valley/floodplain carve
 *       run over a whole padded relief tile by {@code LocalRiverProvider.buildTile}. For each pixel it
 *       selects the single <em>nearest</em> influencing unit and lerps the elevation toward that unit's
 *       reference elevation ({@link RosgenProfile#riverInfluenceElevation}). {@code buildTile} calls it
 *       twice, both times over GLOBAL units only (local channels are never shell-carved).</li>
 *   <li><b>Per-pixel refinement</b> ({@link #carve}, {@link #carveAtPixel}, {@link #carvePrefetched}) —
 *       queries the per-tile river network ({@link LocalRiverProvider#queryInfluence}) and takes the
 *       minimum (deepest) {@link HydrologyProfile#computeForUnit} elevation over every influencing unit.
 *       Each unit contributes its cross-section delta faded over an elliptical footprint, so the stage
 *       cuts the bed trench below the shell; it is driven per chunk from {@code PopulateNoiseStep}.</li>
 * </ul>
 *
 * All geometry is in the relief-pixel frame; only {@link #carve} converts from world/block coordinates
 * (divide by {@link FractalTerrainConfig#GLOBAL_SCALE_CORRECTION}). This is the carving twin of
 * {@code HydrologyProfilePainter}.
 */
public final class HydrologyProfileCarver {

    private final LocalRiverProvider localRiver;

    public HydrologyProfileCarver(LocalRiverProvider localRiver) {
        this.localRiver = localRiver;
    }

    // -------------------------------------------------------------------------
    // Per-pixel refinement (nearest influencing unit)
    // -------------------------------------------------------------------------

    /**
     * Refined elevation at world/block coordinates {@code (worldBlockX, worldBlockZ)} whose already-carved
     * shell elevation is {@code shellElevAtPixel}. World coords are converted into the relief-pixel frame
     * (divide by {@link FractalTerrainConfig#GLOBAL_SCALE_CORRECTION}) that the unit query uses. Returns
     * {@code shellElevAtPixel} unchanged when no feature influences the point.
     */
    public float carve(double worldBlockX, double worldBlockZ, double shellElevAtPixel) {
        final double pixelX = worldBlockX / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final double pixelZ = worldBlockZ / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        return carveAtPixel(new double[] {pixelX, pixelZ}, shellElevAtPixel);
    }

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
     * (a unit is kept when the center lies within {@code maxRiverInfluence(unit.width()) +
     * chunkRadiusPx}). Feed the result to {@link #carvePrefetched} for each block — one tree query per
     * chunk instead of one per block. All arguments are in the relief-pixel frame.
     */
    public PrefetchedUnits prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        return queryUnits(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
    }

    /** Query the units influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedUnits queryUnits(double[] pt, double extraRadius) {
        return new PrefetchedUnits(localRiver.queryInfluence(pt, extraRadius));
    }

    public float carvePrefetched(PrefetchedUnits prefetched, double[] pt, double elevAtPixel) {
        return carvePrefetchedNearest(prefetched, pt, elevAtPixel);
        //        final HydrologicalUnit[] units = prefetched.units();
        //        double avgSum = 0;
        //        double avgCount = 0;
        //        for (final HydrologicalUnit unit : units) {
        //            final double influenceRadius = unit.getRadius(); // = riverInfluence(unit.width()), the circle's
        // own radius
        //            final double[] unitCoord = unit.coord();
        //            final double dist = Math.hypot(pt[0] - unitCoord[0], pt[1] - unitCoord[1]);
        //            if (dist >= influenceRadius) continue; // outside this unit's reach
        //            final double unitElev = (1 - dist / influenceRadius) * HydrologyProfile.computeForUnit(pt, unit,
        // elevAtPixel);
        //            avgSum += unitElev;
        //            avgCount++;
        //        }
        //
        //        if (avgCount==0) return (float) elevAtPixel;
        //        return (float) (avgSum / avgCount);
    }

    public float carvePrefetchedNearest(PrefetchedUnits prefetched, double[] pt, double elevAtPixel) {
        final HydrologicalUnit[] units = prefetched.units();
        HydrologicalUnit nearest = null;
        double nearestDist = Double.POSITIVE_INFINITY;
        for (HydrologicalUnit unit : units) {
            final double[] unitCoord = unit.coord();
            final double dist = Math.hypot(pt[0] - unitCoord[0], pt[1] - unitCoord[1]);
            if (dist >= unit.getRadius()) continue; // outside this unit's influence
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = unit;
            }
        }
        if (nearest == null) return (float) elevAtPixel;
        return (float) HydrologyProfile.computeForUnit(pt, nearest, elevAtPixel);
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
     * circle actually contains the pixel, take the <em>nearest</em> one, and overwrite the pixel with
     * {@link RosgenProfile#riverInfluenceElevation}. Only the nearest unit contributes — contributions
     * are not composited — so a confluence takes the profile of whichever channel passes closest rather
     * than the deepest. Pixels with a negative ambient elevation (ocean) are skipped.
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
                HydrologicalUnit nearest = null;
                double nearestDist = Double.POSITIVE_INFINITY;
                for (HydrologicalUnit unit : nearby) {
                    final double[] coord = unit.coord();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    if (radialDist >= unit.getRadius()) continue; // outside this unit's influence
                    if (radialDist < nearestDist) {
                        nearestDist = radialDist;
                        nearest = unit;
                    }
                }
                if (nearest == null) continue;

                final RosgenType type = nearest.rosgenType() == null ? RosgenType.A : nearest.rosgenType();
                final RosgenProfile profile = RosgenProfile.of(type);
                elevation[idx] = (float)
                        profile.riverInfluenceElevation(nearestDist, nearest.width(), curElev, nearest.elevation());
            }
        }
    }
}
