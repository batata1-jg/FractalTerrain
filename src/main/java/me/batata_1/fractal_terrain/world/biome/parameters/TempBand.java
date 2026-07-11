package me.batata_1.fractal_terrain.world.biome.parameters;

/**
 * Internal °C climate temperature bands used by the temperature and vegetation models. These are
 * the raw-temperature bands, distinct from the vanilla {@link TemperatureLevel} output parameter.
 */
public enum TempBand {
    FROZEN,
    COLD,
    COOL,
    TEMPERATE,
    WARM,
    HOT;

    // Temperature band edges (°C) used by the tree-cover and temperature models.
    private static final float TEMP_FROZEN_MAX = -5f;
    private static final float TEMP_COLD_MAX = 5f;
    private static final float TEMP_COOL_MAX = 12f;
    private static final float TEMP_TEMPERATE_MAX = 20f;
    private static final float TEMP_WARM_MAX = 26f;

    public static TempBand of(float tempC) {
        if (tempC < TEMP_FROZEN_MAX) return FROZEN;
        if (tempC < TEMP_COLD_MAX) return COLD;
        if (tempC < TEMP_COOL_MAX) return COOL;
        if (tempC < TEMP_TEMPERATE_MAX) return TEMPERATE;
        if (tempC < TEMP_WARM_MAX) return WARM;
        return HOT;
    }
}
