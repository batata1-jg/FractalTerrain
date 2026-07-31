package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * The profile a feature type uses before it has one of its own — every {@link HydrologyProfile} default,
 * unmodified.
 *
 * <p>Exists so a newly added feature type carves something plausible from the day it is indexed: a unit
 * on this profile still blends into the influence average and simply loses to any overlapping unit
 * claiming a higher-priority zone. Swapping in a real profile needs no carve-side change.
 */
public enum DefaultProfile implements HydrologyProfile {
    INSTANCE
}
