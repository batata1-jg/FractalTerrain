package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.rosgen.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import org.apache.commons.lang3.NotImplementedException;

/**
 * The elevation side of the hydrology profile. Two carve stages live here:
 *
 * <ul>
 *   <li><b>Tile-level shell carve</b> ({@link #carveRiverShells}, static) — the valley/floodplain carve
 *       run over a whole padded relief tile by {@code LocalRiverProvider.buildTile}. For each pixel it
 *       selects the single <em>nearest</em> influencing unit and lerps the elevation toward that unit's
 *       reference elevation ({@link RosgenProfile#riverInfluenceElevation}). {@code buildTile} calls it
 *       twice, both times over an <em>unfiltered</em> unit collection. The first call is global-only by
 *       timing alone — the local network does not exist yet — while the second, running after the local
 *       trace, carves local shells too.</li>
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
       // return carvePrefetchedNearest(prefetched, pt, elevAtPixel);
        final HydrologicalUnit[] units = prefetched.units();
        double avgSumInfluence = 0;
        double avgWeightInfluence = 0;
        boolean intersectsFloodplain = false;
        double avgSumFloodPlain = 0;
        double avgWeightFloodPlain = 0;
        boolean intersectsBed = false;
        double avgSumBed = 0;
        double avgWeightBed = 0;
        for (final HydrologicalUnit unit : units) {
            final double influenceRadius = unit.getRadius();
            final double[] unitCoord = unit.coord();
            final double dist = Math.hypot(pt[0] - unitCoord[0], pt[1] - unitCoord[1]);
            if (dist >= influenceRadius) continue; // outside this unit's reach
            final double width = unit.width();
            final RosgenProfile profile =
                    RosgenProfile.of(unit.rosgenType() == null ? HydrologicalUnit.RosgenType.A : unit.rosgenType());
            final double floodPlainLength = profile.floodPlainLength(width);
            final double unitElev = HydrologyProfile.computeForUnit(pt,floodPlainLength,width, unit, elevAtPixel);
            avgSumInfluence += unitElev * (1 - dist / influenceRadius);
            avgWeightInfluence += (1 - dist / influenceRadius);
            if(dist>=floodPlainLength) continue;
            intersectsFloodplain = true;
            avgSumFloodPlain += unitElev * (1 - dist / floodPlainLength);
            avgWeightFloodPlain += (1 - dist / floodPlainLength);
            if(dist>=width/2) continue;
            intersectsBed = true;
            avgSumBed += unitElev * (1 - (2 * dist) / width);
            avgWeightBed += (1 - (2 * dist) / width);
        }

        double avgSum=avgSumInfluence;
        double avgWeight=avgWeightInfluence;

        if(intersectsFloodplain) {
            avgSum=avgSumFloodPlain;
            avgWeight=avgWeightFloodPlain;
        }

        if(intersectsBed) {
            avgSum=avgSumBed;
            avgWeight=avgWeightBed;
        }

        if (avgWeight<1e-6) return (float) elevAtPixel;
        return (float) (avgSum / avgWeight);
    }

//    public float carvePrefetchedNearest(PrefetchedUnits prefetched, double[] pt, double elevAtPixel) {
//        throw new NotImplementedException();
//        final HydrologicalUnit[] units = prefetched.units();
//        HydrologicalUnit nearest = null;
//        double nearestDist = Double.POSITIVE_INFINITY;
//        for (HydrologicalUnit unit : units) {
//            final double[] unitCoord = unit.coord();
//            final double dist = Math.hypot(pt[0] - unitCoord[0], pt[1] - unitCoord[1]);
//            if (dist >= unit.getRadius()) continue; // outside this unit's influence
//            if (dist < nearestDist) {
//                nearestDist = dist;
//                nearest = unit;
//            }
//        }
//        if (nearest == null) return (float) elevAtPixel;
//        return (float) HydrologyProfile.computeForUnit(pt, nearest, elevAtPixel);
//    }

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

        double avgSum=0;
        double avgWeight=0;

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalUnit> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;

                final double curElev = elevation[idx];
                avgSum = avgWeight = 0;
                RosgenProfile mutableProfile = null;
                for (HydrologicalUnit unit : nearby) {
                    final double[] coord = unit.coord();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    if (radialDist >= unit.getRadius()) continue; // outside this unit's influence
                    final double width = unit.width();
                    mutableProfile = RosgenProfile.of(unit.rosgenType() == null ? RosgenType.A : unit.rosgenType());
                    avgSum += (1 - radialDist / unit.getRadius()) * mutableProfile.riverInfluenceElevation(radialDist,width,curElev,unit.elevation());
                    avgWeight += (1 - radialDist / unit.getRadius());
                }

                if(avgWeight<=1e-6) continue;
                elevation[idx] = (float) (avgSum / avgWeight);
            }
        }
    }
}
