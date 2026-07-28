package me.batata_1.fractal_terrain.world.biome.parameters;

import me.batata_1.fractal_terrain.math.Range;

/**
 * Peaks-and-valleys bands derived from weirdness via {@code PV = 1 − |3·|weirdness| − 2|}
 * (wiki "Weirdness (Ridges)"). Valleys are where vanilla carves rivers.
 */
public enum PeaksValleys implements Band {
    VALLEYS(-1.0f, -0.85f),
    LOW(-0.85f, -0.2f),
    MID(-0.2f, 0.2f),
    HIGH(0.2f, 0.7f),
    PEAKS(0.7f, 1.0f);

    public final Range range;

    PeaksValleys(float min, float max) {
        this.range = new Range(min, max);
    }

    /**
     * Folds weirdness into the peaks-and-valleys (ridges) value.
     */
    public static float pv(float weirdness) {
        return 1f - Math.abs(3f * Math.abs(weirdness) - 2f);
    }

    @Override
    public Range range() {
        return range;
    }

    /** The band the weirdness value's folded {@link #pv} lands in (clamped to the extremes). */
    public static PeaksValleys of(float weirdness) {
        return Band.containing(values(), pv(weirdness));
    }
}
