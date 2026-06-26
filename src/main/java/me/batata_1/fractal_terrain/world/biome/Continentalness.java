package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.math.Range;

/**
 * Continentalness regions, ocean → inland (higher = further inland). Boundaries are the
 * wiki's "Continentalness (Continents)" table.
 */
public enum Continentalness {
    MUSHROOM_FIELDS(-1.2f, -1.05f),
    DEEP_OCEAN(-1.05f, -0.455f),
    OCEAN(-0.455f, -0.19f),
    COAST(-0.19f, -0.11f),
    NEAR_INLAND(-0.11f, 0.03f),
    MID_INLAND(0.03f, 0.3f),
    FAR_INLAND(0.3f, 1.0f);

    public final Range range;

    Continentalness(float min, float max) {
        this.range = new Range(min, max);
    }

    public float mid() {
        return range.mid();
    }

    /**
     * The region a continentalness value falls in (clamped to the extremes).
     */
    public static Continentalness of(float continentalness) {
        Continentalness region = MUSHROOM_FIELDS;
        for (Continentalness candidate : values()) {
            if (continentalness >= candidate.range.min()) region = candidate;
        }
        return region;
    }
}
