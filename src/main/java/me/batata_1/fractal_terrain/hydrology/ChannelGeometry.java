package me.batata_1.fractal_terrain.hydrology;

/**
 * The single authority for channel cross-section geometry shared across the hydrology pipeline: the bed
 * half-width, the empirical depth-from-width law, and the channel-overlap test. Centralizing these here
 * keeps the carve ({@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver}), paint
 * ({@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfilePainter}), meander
 * ({@link me.batata_1.fractal_terrain.hydrology.meanders.Channel} /
 * {@link me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork}) and profile
 * ({@link me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile}) sides agreeing on one definition.
 */
public final class ChannelGeometry {

    private ChannelGeometry() {}

    /** Empirical depth-from-width law constants: {@code depth = max(1, (width / SCALE) ^ (1 / EXP))}. */
    private static final double DEPTH_WIDTH_SCALE = 18.8;

    private static final double DEPTH_WIDTH_EXP = 1.41;

    /** Half the channel width — the bed/water half-extent measured from the centreline (native px). */
    public static double bedHalfWidth(double width) {
        return width * 0.5;
    }

    /** Channel depth for the given width (native px), floored at 1. */
    public static double depthForWidth(double width) {
        return Math.max(1.0, Math.pow(width / DEPTH_WIDTH_SCALE, 1.0 / DEPTH_WIDTH_EXP));
    }

    /**
     * Whether two channels of widths {@code widthA} / {@code widthB} whose centrelines are {@code distance}
     * apart overlap — i.e. their bed half-widths meet. Used by the meander crossing/merge detection.
     */
    public static boolean channelsOverlap(double distance, double widthA, double widthB) {
        return distance <= bedHalfWidth(widthA) + bedHalfWidth(widthB);
    }
}
