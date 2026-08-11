package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;

/**
 * One channel's cross-section as seen from one point — the single answer that replaces the
 * disagreeing per-primitive tangent-line distances.
 *
 * <p>Every field is read at the foot point on the centreline, not at the nearest knot, so the
 * profile evaluates one coherent cross-section instead of a knot's snapshot of it.
 *
 * <p>Produced by {@code HydrologyProfileInprinter.sampleNearestChannel}, consumed by the per-pixel
 * pass in {@code PopulateNoiseStep}. Purely geometric: turning it into terrain is {@code carveInto}.
 */
public record NearestChannelSample(
        double signedPerpDist,
        double channelWidth,
        double channelCurvature,
        double bedElevation,
        RosgenType rosgenType,
        int channelId) {

    /** How far the rim rounds where the valley cone meets untouched ground, in relief pixels. */
    private static final double CARVE_BLEND_RANGE = 2.0;

    /**
     * Cuts this cross-section into the shell-carved terrain.
     *
     * <p>Needs no influence radius: outside the floodplain the profile is a cone rising away from
     * the channel, so the min hands back ambient wherever that cone clears it.
     */
    public double carveInto(double ambientElevation) {
        final RosgenProfile profile = RosgenProfile.of(rosgenType == null ? RosgenType.A : rosgenType);
        final double bedTarget =
                bedElevation + profile.delta(channelId, signedPerpDist, channelWidth, channelCurvature);
        return RosgenProfile.blendMin(ambientElevation, bedTarget, CARVE_BLEND_RANGE);
    }
}
