package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.VectorOps;

import java.util.Arrays;

public record River(
        double[] coord,
        double radius,
        RosgenType rosgenType,
        double[] normal,
        double width,
        double elevation ) implements HydrologicalUnit {
    @Override
    public double[] getCoords() {
        return coord;
    }

    @Override
    public double[] getCenter() {
        return coord;
    }

    @Override
    public double getRadius() {
        return radius;
    }

    public boolean channelContains(double distSqFromCentre) {
        return false;
    }


    @Override
    public byte[] serializeUnit() {
        return new byte[0];
    }

    @Override
    public HydrologicalUnit deserializeUnit(byte[] rawBytes) {
        return null;
    }

    @Override
    public HydrologicalUnit getPrototype() {
        return null;
    }

    @Override
    public HydrologyProfile getProfile() {
        return RosgenProfile.of(rosgenType);
    }

    @Override
    public double carveFineGrained(double[] pt, double elevAtPixel) {
        if (normal == null) return elevAtPixel;
        final RosgenProfile profile = (RosgenProfile) getProfile();

        final double[] normTangent = VectorOps.perpendicular(normal);
        final double floodPlainLength = profile.floodPlainLength(width);
        final double radiusSq = floodPlainLength * floodPlainLength;
        if (VectorOps.distanceSquared(pt, coord) >= radiusSq) return elevAtPixel;
        if (Math.abs(normal[0]) < 1e-6 || Math.abs(normal[1]) < 1e-6) {
            return elevAtPixel;
        }

        final double SignedPerpDist;
        final double alongDist;
        final double[] ptToUnit = VectorOps.sub(pt, coord);
        SignedPerpDist = VectorOps.dot(normal, ptToUnit);
        alongDist = Math.abs(VectorOps.dot(normTangent, ptToUnit));

        final double uninterpolatedDelta =
                profile.riverAreaDelta(Arrays.hashCode(coord), SignedPerpDist, alongDist, width);

        if (radiusSq - SignedPerpDist * SignedPerpDist < 1e-6) return elevAtPixel;
        final double eccentricity =
                Math.sqrt(Math.abs(1 - (alongDist * alongDist) / (radiusSq - SignedPerpDist * SignedPerpDist)));
        final double t = Math.clamp(0.5 * (Math.tanh(8 * (eccentricity - HydrologyTuning.MAX_ECCENTRICITY)) + 1), 0, 1);
        //  final double t = Math.clamp(eccentricity / HydrologyTuning.MAX_ECCENTRICITY, 0, 1);
        return elevAtPixel + t * uninterpolatedDelta;
    }

    /** Rosgen stream classification (A–D); selects the unit's {@link RosgenProfile}. */
    public enum RosgenType {
        A,
        Aa,
        B,
        C,
        D,
        DA,
        E,
        F,
        G
    }
}
