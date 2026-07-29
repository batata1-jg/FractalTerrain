package me.batata_1.fractal_terrain.hydrology;

import me.batata_1.fractal_terrain.hydrology.rosgen.RosgenProfile;

/**
 * The single authority for channel cross-section geometry shared across the hydrology pipeline: the bed
 * half-width, the empirical depth-from-width law, and the channel-overlap test. Centralizing these here
 * keeps the carve ({@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver}), paint
 * ({@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfilePainter}), meander
 * ({@link me.batata_1.fractal_terrain.hydrology.meanders.Channel} /
 * {@link me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork}) and profile
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
        return Math.max(0.5, Math.pow(width / DEPTH_WIDTH_SCALE, 1.0 / DEPTH_WIDTH_EXP));
    }

    /**
     * Native-px width at which the width-to-depth ratio crosses {@code 12} — Rosgen's boundary between
     * the narrow-deep types ({@code E G A}) and the wide-shallow ones ({@code C F B}). The single
     * calibration knob for the {@code E}&harr;{@code C} and {@code G}&harr;{@code F} splits: lower it and
     * rivers start looking wide and shallow sooner. First-cut, untuned value pending visual calibration
     * via {@code localRiverTest}.
     */
    public static final double W_REF = 4.0;

    /** W/D at {@link #W_REF} — Rosgen's narrow-deep / wide-shallow boundary. */
    private static final double WD_AT_REF = 12.0;

    /**
     * Exponent of the width-to-depth law. Hydraulic geometry gives {@code W/D ∝ DA^0.139}
     * (Bieger et al. 2015, nationwide); this project's width law is {@code W = 0.4·√flow}, i.e.
     * {@code W ∝ DA^0.50}, so {@code W/D ∝ W^(0.139/0.50)}. Applying a nationwide drainage-area exponent
     * to this project's synthetic width law is itself an unvalidated cross-domain analogy — first-cut,
     * untuned value pending visual calibration via {@code localRiverTest}.
     */
    private static final double WD_EXPONENT = 0.278;

    /**
     * Dimensionless width-to-depth ratio for a channel of the given native-px width:
     * {@code 12 · (width / W_REF)^0.278}, monotone non-decreasing on {@code [0, ∞)} and strictly
     * increasing above {@link #MIN_RATIO_WIDTH}, calibrated so {@code W_REF} maps to {@code 12}. Used by
     * the Rosgen classifier to pick narrow-deep types over wide-shallow ones.
     *
     * <p><b>Deliberately not derived from {@link #depthForWidth}.</b> That law is floored at {@code 1.0}
     * across the whole representable width range ({@code [0.2, 16]} px; the expression only exceeds 1
     * above {@link #DEPTH_WIDTH_SCALE} px), so {@code width / depthForWidth(width)} degenerates to
     * {@code width} — which would put the {@code W/D = 12} boundary at 12 px against a 16 px cap and
     * classify nearly every channel as narrow-deep. {@code depthForWidth} additionally feeds the meander
     * migration rate, so it is not safe to re-floor.
     */
    public static double widthDepthRatio(double width) {
        return WD_AT_REF * Math.pow(Math.max(width, MIN_RATIO_WIDTH) / W_REF, WD_EXPONENT);
    }

    /**
     * Floor guarding {@link #widthDepthRatio} against non-positive results: keeps the ratio strictly
     * positive at {@code width == 0} (a bare {@code pow} would return exactly {@code 0}) and avoids
     * {@code NaN} for negative width, since {@code Math.pow} with a negative base and fractional exponent
     * is {@code NaN} and nothing upstream guarantees a non-negative argument to this public method.
     */
    private static final double MIN_RATIO_WIDTH = 0.05;

    /**
     * Whether two channels of widths {@code widthA} / {@code widthB} whose centrelines are {@code distance}
     * apart overlap — i.e. their bed half-widths meet. Used by the meander crossing/merge detection.
     */
    public static boolean channelsOverlap(double distance, double widthA, double widthB) {
        return distance <= bedHalfWidth(widthA) + bedHalfWidth(widthB);
    }
}
