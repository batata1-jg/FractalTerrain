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

    /** The band a value falls in, clamped at both extremes so classification is total.
     *  {@code bands} must be in declaration order, ascending by lower bound. */
    static <T extends Band> T containing(T[] bands, float value) {
        T match = bands[0];
        for (T candidate : bands) {
            if (value >= candidate.range().min()) match = candidate;
        }
        return match;
    }
}
