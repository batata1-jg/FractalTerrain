package me.batata_1.fractal_terrain.world.biome.parameters;

import me.batata_1.fractal_terrain.math.Range;

/**
 * Erosion levels 0–6 (hilly → flat). Level 5 is vanilla's shattered-terrain band.
 */
public enum ErosionLevel {
    LEVEL_0(-1.0f, -0.78f),
    LEVEL_1(-0.78f, -0.375f),
    LEVEL_2(-0.375f, -0.2225f),
    LEVEL_3(-0.2225f, 0.05f),
    LEVEL_4(0.05f, 0.45f),
    LEVEL_5(0.45f, 0.55f),
    LEVEL_6(0.55f, 1.0f);

    public final Range range;

    ErosionLevel(float min, float max) {
        this.range = new Range(min, max);
    }

    public static ErosionLevel of(float erosion) {
        ErosionLevel level = LEVEL_0;
        for (ErosionLevel candidate : values()) {
            if (erosion >= candidate.range.min()) level = candidate;
        }
        return level;
    }
}
