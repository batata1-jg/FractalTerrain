package me.batata_1.fractal_terrain.world.biome.parameters;

import me.batata_1.fractal_terrain.math.Range;

/**
 * One band of a biome parameter's value line.
 *
 * <p>Every parameter enum in this package declares its constants in ascending {@link Range#min()}
 * order and covers the line without gaps. That ordering is what lets {@link #containing} resolve a
 * value with a single forward scan, so it is load-bearing: reordering an enum's constants changes
 * which band a value resolves to.
 */
interface Band {

    /** The value range this band covers. */
    Range range();

    /**
     * The band {@code value} falls in, clamped to the extremes: the last constant whose lower bound
     * {@code value} has reached, or {@code bands[0]} when {@code value} sits below every lower bound.
     *
     * @param bands the enum's constants in declaration order (ascending by {@link Range#min()}).
     * @param value the parameter value to classify.
     */
    static <T extends Band> T containing(T[] bands, float value) {
        T match = bands[0];
        for (T candidate : bands) {
            if (value >= candidate.range().min()) match = candidate;
        }
        return match;
    }
}
