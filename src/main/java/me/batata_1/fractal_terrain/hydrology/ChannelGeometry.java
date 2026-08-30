package me.batata_1.fractal_terrain.hydrology;

import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;

/**
 * The single authority for channel cross-section geometry shared across the hydrology pipeline: the bed
 * half-width, the empirical depth-from-width law, and the channel-overlap test. Centralizing these here
 * keeps the carve ({@link HydrologyProfileInprinter}), paint
 * ({@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfilePainter}), meander
 * ({@link Channel} /
 * {@link RiverNetwork}) and profile
 * ({@link RosgenProfile}) sides agreeing on one definition.
 */
public final class ChannelGeometry {

    private ChannelGeometry() {}

    /** Empirical depth-from-width law constants: {@code depth = max(1, (width / SCALE) ^ (1 / EXP))}. */
    private static final double DEPTH_WIDTH_SCALE = 1;

    private static final double DEPTH_WIDTH_EXP = 1.41;

    /** Half the channel width — the bed/water half-extent measured from the centreline (native px). */
    public static double bedHalfWidth(double width) {
        return width * 0.5;
    }

    /** Channel depth for the given width (native px), floored at 1. */
    public static double depthForWidth(double width) {
        return 1.5 * Math.max(2, Math.pow(width / DEPTH_WIDTH_SCALE, 1.0 / DEPTH_WIDTH_EXP));
    }

    /** The one knob calibrating narrow-deep against wide-shallow Rosgen types. */
    public static final double W_REF = 2.0;

    /** Prescribes the width-to-depth ratio the Rosgen classifier compares against {@code WD_NARROW}.
     *  Deliberately not derived from {@link #depthForWidth}, whose 1.0 floor would degenerate the ratio
     *  to plain width and classify nearly everything narrow-deep — see {@code config/README.md}. */
    public static double widthDepthRatio(double width) {
        return width / depthForWidth(width);
    }

    /** Floor keeping {@link #widthDepthRatio} positive and NaN-free against unchecked negative width. */
    private static final double MIN_RATIO_WIDTH = 0.05;

    /** Bed-overlap test driving meander crossing and merge detection. */
    public static boolean channelsOverlap(double distance, double widthA, double widthB) {
        return distance <= bedHalfWidth(widthA) + bedHalfWidth(widthB);
    }
}
