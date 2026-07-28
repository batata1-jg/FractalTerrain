package me.batata_1.fractal_terrain.world.biome.parameters;

import me.batata_1.fractal_terrain.math.Range;

/**
 * Humidity (vegetation) levels 0–4 (dry → lush), from the wiki "Humidity (Vegetation)" table.
 */
public enum HumidityLevel implements Band {
    LEVEL_0(-1.0f, -0.35f),
    LEVEL_1(-0.35f, -0.1f),
    LEVEL_2(-0.1f, 0.1f),
    LEVEL_3(0.1f, 0.3f),
    LEVEL_4(0.3f, 1.0f);

    public final Range range;

    HumidityLevel(float min, float max) {
        this.range = new Range(min, max);
    }

    @Override
    public Range range() {
        return range;
    }

    public static HumidityLevel of(float humidity) {
        return Band.containing(values(), humidity);
    }
}
