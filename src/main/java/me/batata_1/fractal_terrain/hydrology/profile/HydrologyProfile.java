package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
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

    public static double computeForUnit(double[] pt, HydrologicalUnit unit, double elevAtPixel) {
      //  return elevAtPixel;
        final double[] normal = unit.normal();
        if (normal == null) return elevAtPixel;
        final double[] normTangent = VectorOps.perpendicular(normal);
        final double[] unitCoord = unit.coord();
        final double width = unit.width();

        final double SignedPerpDist;
        final double alongDist;
        final double[] ptToUnit = VectorOps.sub(pt, unitCoord);
        SignedPerpDist = VectorOps.dot(normal, ptToUnit);
        alongDist = Math.abs(VectorOps.dot(normTangent, ptToUnit));

        final RosgenProfile profile = RosgenProfile.of(unit.rosgenType() == null ? HydrologicalUnit.RosgenType.A : unit.rosgenType());
        final double floodPlainLength = profile.floodPlainLength(width);
        final double uninterpolatedDelta = profile.riverAreaDelta(SignedPerpDist, alongDist, width);

        return elevAtPixel;
    }
}
