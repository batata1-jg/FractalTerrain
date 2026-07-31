package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * The profile a feature type uses before it has one of its own: a single {@link ZoneCategory#INFLUENCE}
 * disc at the unit's {@link me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit#getRadius()
 * radius}, linear falloff, no valley shell — every {@link HydrologyProfile} default, unmodified.
 *
 * <p>A unit on this profile still participates: it blends its own elevation into the influence average
 * like any other unit, and simply loses to any overlapping unit that claims a higher-priority zone. That
 * makes it a safe placeholder — a newly added feature type carves something plausible from the day it is
 * indexed, and gains its real cross-section by swapping its {@code getProfile()} for a dedicated
 * implementation, with no change on the carve side.
 */
public enum DefaultProfile implements HydrologyProfile {
    INSTANCE
}
