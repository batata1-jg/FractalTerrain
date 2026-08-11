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
        int channelId) {}
