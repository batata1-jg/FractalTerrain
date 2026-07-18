package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.math.VectorOps;

/**
 * Per-unit elevation primitive shared by the two sides of the hydrology profile —
 * {@link HydrologyProfileCarver} (elevation) and {@code HydrologyProfilePainter}
 * (blocks/biomes/vegetation). Both consume the flat {@link HydrologicalUnit}[] returned by
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider#queryInfluence}: every unit
 * contributes a per-unit value ({@link #computeForUnit} for elevation) and the caller merges the
 * contributions, taking the minimum (deepest) across influencing units.
 *
 * <p><b>The per-pixel bed stage is currently disabled</b> — see {@link #computeForUnit}.
 */
public final class HydrologyProfile {

    private HydrologyProfile() {}

    /**
     * <b>Currently a no-op: returns {@code elevAtPixel} unchanged.</b> The per-unit bed cross-section
     * math is commented out below pending the in-progress river rework, so no per-pixel bed residual is
     * cut anywhere in the mod today. Because {@code PopulateNoiseStep} derives
     * {@code Types.RIVER_DIFFERENCE} from this stage's delta, that heightmap is uniformly {@code 0} and
     * {@code HydrologyProfilePainter} consequently places no river water.
     *
     * <p>When re-enabled, the commented-out body anchors on {@code elevAtPixel} — the elevation already
     * carved into the tile-level valley shell by {@link HydrologyProfileCarver#carveRiverShells} — and
     * cuts only the bed residual below it ({@link RosgenProfile#riverAreaDelta}), so the detail stage
     * never re-cuts from the original decoded terrain.
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
