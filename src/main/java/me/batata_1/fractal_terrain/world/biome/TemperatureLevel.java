package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.math.Range;

/**
 * Temperature levels 0–4 (cold → hot), from the wiki "Temperature" table.
 */
public enum TemperatureLevel {
    LEVEL_0(-1.0f, -0.45f),
    LEVEL_1(-0.45f, -0.15f),
    LEVEL_2(-0.15f, 0.2f),
    LEVEL_3(0.2f, 0.55f),
    LEVEL_4(0.55f, 1.0f);

    public final Range range;

    TemperatureLevel(float min, float max) {
        this.range = new Range(min, max);
    }

    public static TemperatureLevel of(float temperature) {
        TemperatureLevel level = LEVEL_0;
        for (TemperatureLevel candidate : values()) {
            if (temperature >= candidate.range.min()) level = candidate;
        }
        return level;
    }
}
