package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.math.VectorOps;

/**
 * Shared primitives for the two sides of the same coin — {@link HydrologyProfileCarver} (elevation) and
 * {@code HydrologyProfilePainter} (blocks/biomes/vegetation). Both consume the flat
 * {@link HydrologicalUnit}[] returned by
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider#queryInfluence} the same way: every
 * unit contributes a per-unit value ({@link #computeForUnit} for elevation), and the caller merges the
 * contributions in one flat distance-weighted average (weight 1 at the unit, 0 at that unit's own
 * {@link FractalTerrainConfig#riverInfluence influence} radius).
 */
public final class HydrologyProfile {

    private HydrologyProfile() {}

    /**
     * The elevation this single unit would carve the point {@code (pixelX, pixelZ)} to, if it were the
     * only unit in the world (relief-pixel frame throughout). Projects the point onto the line through
     * the unit's coordinate along the unit's channel-perpendicular normal; the absolute perpendicular
     * projected distance is the bed cross-section parameter fed to the unit's {@link RosgenProfile}.
     *
     * <p>Anchors on {@code shellElevAtPixel} — the elevation already carved into the tile-level
     * valley/floodplain shell at this pixel by {@code HydrologyProfileCarver#carveRiverShells} — and cuts
     * only the per-pixel bed residual below it ({@link RosgenProfile#riverAreaDelta}), within the bed
     * half-width. This is cross-stage conservation (the shell carve and this residual sum to the intended
     * trench): the detail stage never re-cuts from the original terrain.
     */
    public static double computeForUnit(double[] pt, HydrologicalUnit unit, double elevAtPixel) {
        return elevAtPixel;
//        final double[] normal = unit.normal();
//        if (normal == null) return elevAtPixel;
//        final double[] normTangent = VectorOps.perpendicular(normal);
//
//        final double[] unitCoord = unit.coord();
//
//        final double SignedPerpDist;
//        final double alongDist;
//        final double[] ptToUnit = VectorOps.sub(pt, unitCoord);
//        SignedPerpDist = VectorOps.dot(normal, ptToUnit);
//        alongDist = Math.abs(VectorOps.dot(normTangent, ptToUnit));
//
//        final RosgenProfile profile = RosgenProfile.of(unit.rosgenType() == null ? RosgenType.A : unit.rosgenType());
//        final double uninterpolatedDelta = profile.riverAreaDelta(SignedPerpDist, alongDist, unit.width());
//        final double unAffectedAreaDistMag = profile.unAffectedDist(unit.width());
//        final double[] unAffectedAreaDist = VectorOps.scale(normTangent, unAffectedAreaDistMag);
//        final double[] circlePtR = VectorOps.add(unitCoord, unAffectedAreaDist);
//        final double[] circlePtL = VectorOps.sub(unitCoord, unAffectedAreaDist);
//        if (VectorOps.distanceSquared(circlePtL, pt) <= unAffectedAreaDistMag * unAffectedAreaDistMag
//                && VectorOps.distanceSquared(circlePtR, pt) <= unAffectedAreaDistMag * unAffectedAreaDistMag) {
//            return elevAtPixel + uninterpolatedDelta;
//        }
//
//        return elevAtPixel;
    }
}
