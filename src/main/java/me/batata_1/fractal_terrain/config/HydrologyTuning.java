package me.batata_1.fractal_terrain.config;

import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;

/**
 * Hydrology tuning: the river width/carve-profile law constants and the spline-resampling guards used
 * while tracing river geometry. Home for the width-from-flow law and the floodplain/influence-radius
 * helpers shared by the global and local river networks.
 */
public final class HydrologyTuning {

    private HydrologyTuning() {}

    // ──────────────────────────────────────────────────────────────────────────
    // Algorithm tuning
    // ──────────────────────────────────────────────────────────────────────────

    /** Hard cap on spline resampling iterations, guarding against runaway geometry. */
    public static final int MAX_SPLINE_LENGTH = (int) 1e4;
    /** Iteration cap for the spline arc-length binary search. */
    public static final int BINARY_SEARCH_MAX_STEPS = 20;

    // ──────────────────────────────────────────────────────────────────────────
    // Border / sampling constants consolidated from GlobalRiverProvider, LocalRiverProvider, and
    // Meanders (one home per concept; values unchanged from their prior per-class declarations).
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Halo width (coarse px) over which {@code GlobalRiverProvider}'s isolate ramp rises toward the tile
     * border; also that provider's padding halo (equal to the ramp width).
     */
    public static final int RAMP_WIDTH = 6;

    /** Sink-fill border-blend padding (native px) used by {@code LocalRiverProvider}'s tile carve. */
    public static final int FILL_PADDING = 64;

    /** Resample spacing (native px) for a freshly traced local channel, in {@code LocalRiverProvider}. */
    public static final double RESAMPLE_DIST = 2.0;

    /** Meander-simulation resample/migration step (native px), shared by {@code Meanders} and callers
     *  that need to reason about its point spacing (debug visualizers, tests). */
    public static final double DX = 1.5;

    /**
     * Width of the border margin band {@code Meanders} keeps clear of the grid edge, as a multiple of
     * channel width. An independent margin factor (deliberately wider than {@link #riverInfluence}) so a
     * channel's whole carve band stays inside the grid.
     */
    public static final double MARGIN_INFLUENCE_FACTOR = 5.0;

    // ──────────────────────────────────────────────────────────────────────────
    // Hydrology — river width & carve-profile tuning (all property-overridable).
    // ──────────────────────────────────────────────────────────────────────────

    /** Floor on every river width, in native pixels. */
    public static final double MIN_WIDTH = 0.2f;

    /** Scale on {@code sqrt(flow)} shared by the global and local networks (see {@link #widthFromFlow}). */
    public static final double WIDTH_FLOW_SCALE = 0.02f;

    /**
     * Cap on every river width <em>in the width-law frame</em> (the frame {@link #widthFromFlow}'s caller
     * works in). Local channels call {@code widthFromFlow} directly in native px, so they are capped at
     * {@code MAX_WIDTH} native px. Global rivers call it in the coarse frame and are rescaled by
     * {@link #GLOBAL_WIDTH_COORD_SCALE} afterwards ({@code GlobalRiverProvider.globalRiverWidth}), so their
     * native-px cap is {@code MAX_WIDTH * GLOBAL_WIDTH_COORD_SCALE} — see {@link #maxNativeWidth()}.
     */
    public static final double MAX_WIDTH = 16f;

    public static final double MAX_LOCAL_WIDTH = 6f;

    /**
     * Multiplier applied to <em>global</em>-river widths only, converting their coarse-px flow widths into
     * native px. (Local widths already come out in native px, so they use {@link #widthFromFlow} directly.)
     */
    public static final double GLOBAL_WIDTH_COORD_SCALE = 20f;

    /**
     * Floodplain half-extent (native px) = {@code FLOODPLAIN_BASE + FLOODPLAIN_WIDTH_FACTOR · width}. This
     * is the <em>flat</em> band carved at floodplain elevation; the blend to decoded terrain starts only
     * past it. Kept very tunable — a later plan adds noise to make this vary.
     */
    public static final double FLOODPLAIN_BASE = 0.6f;

    public static final double FLOODPLAIN_WIDTH_FACTOR = 1.0f;

    /** Width of the blend-to-decoded band beyond the floodplain (native px). */
    public static final double INFLUENCE_BLEND_MULTIPLIER = 2.2f;

    /**
     * Hard cap (native px) on any river's influence radius — also the radius the cross-tile unit query
     * uses. Bounds the per-pixel carve/paint work and the query span; rivers whose computed
     * {@link #riverInfluence} would exceed this are clamped to it.
     */
    public static final double MAX_INFLUENCE_RADIUS = 64.0f;

    /**
     * Max {@code |intended shell floor − current terrain|} a pixel may carve — applies <em>only</em> to the
     * tile-level pre-carve ({@code HydrologyProfileCarver.carveRiverShells}), not to the per-pixel
     * refinement merge. A pixel beyond this delta is <em>uncarvable</em> and skipped (so the tile carve
     * never gouges isolated holes or trenches); the hydrological units still record the intended shell
     * floor elevation.
     */
    public static final double MAX_CARVE_DELTA = 100;

    /**
     * Depth (native px) the tile-carved shell floor sits below a feature's reference (bank) elevation --
     * shallow, distinct from the much deeper per-pixel bed trench ({@link
     * me.batata_1.fractal_terrain.hydrology.ChannelGeometry#depthForWidth}).
     */
    public static final double FREEBOARD = 0.3f;

    /**
     * Fraction of the way from {@code width/2} to a river's {@code floodPlainLength} that the lens
     * sagitta {@link #d} sits -- keeps {@code d} strictly inside the required band {@code (width/2, fpl)}
     * for every representable width, since {@code floodPlainLength(width) > width/2} always.
     */
    private static final double D_FRACTION = 0.5;

    /**
     * The lens sagitta (native px) for the shell-carve mask: the along-channel half-extent of a single
     * unit's flat-floor footprint. Stays strictly inside the validity band {@code width/2 < d <
     * floodPlainLength} across the whole width range (narrowest local channel to the widest
     * native-rescaled global trunk) by construction -- see {@link #D_FRACTION}.
     */
    public static double d(double width, double floodPlainLength) {
        final double halfWidth = width * 0.5;
        return halfWidth + D_FRACTION * (floodPlainLength - halfWidth);
    }

    /**
     * Floodplain half-extent for a river of the given width and Rosgen type (native px). Delegates to the
     * type's {@link RosgenProfile#floodPlainLength} — the profile enum is the authority so extents can vary
     * by type; {@link #FLOODPLAIN_BASE} / {@link #FLOODPLAIN_WIDTH_FACTOR} back its shared placeholder law.
     */
    public static double floodPlainLength(double width, RosgenType type) {
        return RosgenProfile.of(type).floodPlainLength(width);
    }

    /** Floodplain half-extent for a typeless river (native px) — assumes {@link RosgenType#A}. */
    public static double floodPlainLength(double width) {
        return floodPlainLength(width, RosgenType.A);
    }

    /**
     * Outer influence radius for a river of the given width and Rosgen type (native px): floodplain + blend
     * band, clamped to {@link #MAX_INFLUENCE_RADIUS}. Beyond this radius a river no longer affects a pixel.
     * Delegates to the type's {@link RosgenProfile#riverInfluence} so the radius can vary by type.
     */
    public static double riverInfluence(double width, RosgenType type) {
        return RosgenProfile.of(type).riverInfluence(width);
    }

    /** Outer influence radius for a typeless river (native px) — assumes {@link RosgenType#A}. */
    public static double riverInfluence(double width) {
        return riverInfluence(width, RosgenType.A);
    }

    /**
     * The single river-width law shared by the global and local networks:
     * {@code clamp(WIDTH_FLOW_SCALE · log(max(1, rawFlow + 1)), MIN_WIDTH, MAX_WIDTH)}. Global callers
     * additionally multiply the result by {@link #GLOBAL_WIDTH_COORD_SCALE} to convert coarse-px flow into
     * native px — the {@link #MAX_WIDTH} cap applies <em>before</em> that rescale, so the global native-px
     * ceiling is {@link #maxNativeWidth()}.
     */
    public static double widthFromFlow(double rawFlow) {
        final double lawWidth = WIDTH_FLOW_SCALE * Math.log(Math.max(1.0, rawFlow + 1));
        return Math.clamp(lawWidth, MIN_WIDTH, MAX_WIDTH);
    }

    /**
     * The largest width (native px) any {@code HydrologicalUnit} can carry: the global-river cap after its
     * coarse→native rescale ({@link #MAX_WIDTH} · {@link #GLOBAL_WIDTH_COORD_SCALE}). Bounds the
     * channel-membership query radius ({@code HydrologyProfilePainter.insideChannel} queries
     * {@code maxNativeWidth()/2} around the point: any unit whose half-width disc could contain the point
     * must lie inside that radius).
     */
    public static double maxNativeWidth() {
        return MAX_WIDTH * GLOBAL_WIDTH_COORD_SCALE;
    }
}
