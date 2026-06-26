package me.batata_1.fractal_terrain.math;

/**
 * Half-open value interval {@code [min, max)} for a biome-parameter band.
 */
public record Range(float min, float max) {
    public boolean contains(float v) {
        return v >= min && v < max;
    }

    /**
     * Representative midpoint of the band.
     */
    public float mid() {
        return (min + max) * 0.5f;
    }

    public float clamp(float v) {
        return Math.clamp(v, min, max);
    }
}
