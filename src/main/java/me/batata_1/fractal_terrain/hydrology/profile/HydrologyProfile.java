package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.math.VectorOps;

/**
 * Per-unit elevation primitive shared by the two sides of the hydrology profile —
 * {@link HydrologyProfileCarver} (elevation) and {@code HydrologyProfilePainter}
 * (blocks/biomes/vegetation). Both consume the flat {@link HydrologicalUnit}[] returned by
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider#queryInfluence}: every unit
 * contributes a per-unit value ({@link #computeForUnit} for elevation) and the caller merges the
 * contributions, taking the minimum (deepest) across influencing units.
 */
public final class HydrologyProfile {

    private HydrologyProfile() {}

    /**
     * The bed elevation at {@code pt} contributed by a single influencing {@code unit}: the unit's
     * cross-section delta ({@link RosgenProfile#riverAreaDelta}) faded in over an elliptical footprint
     * centred on the unit, i.e. {@code elevAtPixel + t · delta}.
     *
     * <p>The fade weight {@code t} is read off the point's position in the unit's own frame — {@code x}
     * along the channel (tangent), {@code y} across it (the unit {@code normal}) — via
     * {@code e = 1 − x²/(l² − y²)}, with {@code l} the floodplain half-extent. The level sets of
     * {@code e} are ellipses of semi-axis {@code l·sqrt(1 − e)} along the channel and {@code l} across
     * it: {@code e = 1} on the unit's own cross-section line (the {@code x = 0} segment), decaying to
     * {@code e = 0} on the circle of radius {@code l}. Points at or above
     * {@link HydrologyTuning#MAX_ECCENTRICITY} take the delta in full ({@code t = 1}); below it {@code t}
     * lerps linearly down to {@code 0}, and outside radius {@code l} the pixel is returned untouched.
     *
     * <p>So a unit carves its full cross-section for the whole width of the floodplain but tapers off
     * along the channel, leaving the stretch between two units to their overlapping ellipses — which is
     * why the {@code dx <= width/2} unit spacing noted on {@link RosgenProfile} matters here too.
     */
    public static double computeForUnit(double[] pt, HydrologicalUnit unit, double elevAtPixel) {
      //  return elevAtPixel;
        final double[] normal = unit.normal();
        if (normal == null) return elevAtPixel;
        final RosgenProfile profile =
                RosgenProfile.of(unit.rosgenType() == null ? HydrologicalUnit.RosgenType.A : unit.rosgenType());
        final double[] normTangent = VectorOps.perpendicular(normal);
        final double[] unitCoord = unit.coord();
        final double width = unit.width();
        final double floodPlainLength = profile.floodPlainLength(width);
        if(Math.abs(normal[0])<1e-6||Math.abs(normal[1])<1e-6) {

           // final double t = Math.clamp(, 0, 1);

            return elevAtPixel;
        }

        final double SignedPerpDist;
        final double alongDist;
        final double[] ptToUnit = VectorOps.sub(pt, unitCoord);
        SignedPerpDist = VectorOps.dot(normal, ptToUnit);
        alongDist = Math.abs(VectorOps.dot(normTangent, ptToUnit));

        final double uninterpolatedDelta = profile.riverAreaDelta(SignedPerpDist, alongDist, width);

        // Outside the floodplain disc the unit contributes nothing; the >= also keeps the eccentricity
        // denominator strictly positive (it vanishes exactly at the poles |y| = l of the disc).
        final double radiusSq = floodPlainLength * floodPlainLength;
        if (SignedPerpDist * SignedPerpDist + alongDist * alongDist >= radiusSq) return elevAtPixel;
        if (radiusSq - SignedPerpDist * SignedPerpDist < 1e-6) return elevAtPixel;
        final double eccentricity = 1 - (alongDist * alongDist) / (radiusSq - SignedPerpDist * SignedPerpDist);
        final double t = Math.clamp(eccentricity / HydrologyTuning.MAX_ECCENTRICITY, 0, 1);

        return elevAtPixel + t * uninterpolatedDelta;
    }
}
