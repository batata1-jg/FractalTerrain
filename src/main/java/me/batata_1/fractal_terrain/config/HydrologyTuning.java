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

    /** Resample spacing (native px) for a freshly traced local channel, in {@code LocalDrainageTracer}. */
    public static final double RESAMPLE_DIST = 2.0;

    /**
     * Native-px proximity radius at which a local river is considered to meet a global channel: gates
     * the local drainage tracer's reach-seed adjacency, its walk-termination exclusion, and its
     * junction-attachment split -- all three now read this one radius instead of the removed per-pixel
     * global mask. First-cut, untuned value pending visual calibration via {@code localRiverTest},
     * mirroring {@code Meanders}'s {@code MAX_MARGIN_FRACTION} first-cut pattern: too small yields
     * parallel double rivers (the local walk runs alongside the global channel instead of joining it);
     * too large truncates local detail (interior tributaries get excluded/terminated well before they
     * would naturally reach the global channel).
     */
    public static final double LOCAL_ATTACH_RADIUS = 4.0;

    /** Meander-simulation resample/migration step (native px), shared by {@code Meanders} and callers
     *  that need to reason about its point spacing (debug visualizers, tests). */
    public static final double DX = 1.5;
    /** max per-step displacement for the valley-seeking migration. */
    public static final double MAX_MIGRATION = 2 * HydrologyTuning.DX;
    /**
     * Width of the border margin band {@code Meanders} keeps clear of the grid edge, as a multiple of
     * channel width. An independent margin factor (deliberately wider than {@link #riverInfluence}) so a
     * channel's whole carve band stays inside the grid.
     */
    public static final double MARGIN_INFLUENCE_FACTOR = 5.0;

    // ──────────────────────────────────────────────────────────────────────────
    // Flow accumulation (see Drainage.computeFlow)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Minimum accumulated flow for a cell to count as local river. Combined with the reach test and the
     * global-proximity exclusion to build the river mask. Local to this tracer rather than a
     * {@link HydrologyTuning} constant, so tuning it affects only the local network.
     */
    public static final float FLOW_THRESHOLD = 0.5f;

    //only generate sources for local rivers above this to prevent weird behavior in plains.
    public static final float GRAD_THRESHOLD = 1f;

    public static final float FLOW_INITIAL_GLOBAL = 0.4f;

    public static final float FLOW_INITIAL_LOCAL = 0.002f;

    public static final float FLOW_PER_CELL_GLOBAL = 2f;

    public static final float FLOW_PER_CELL_LOCAL = 0.001f;

    /**
     * Near-drain flow smoothing (see {@code RiverNetwork.accumulateAndCorrectFlow}). A drain reads its
     * anchor flow exactly, which can be far larger than the natural accumulated flow of the mainstem just
     * upstream; to keep width continuous into the joined river, the last few mainstem nodes before a drain
     * are ramped up toward the anchor. {@code DRAIN_FLOW_SMOOTH_STEP} is the minimum drain-to-upstream flow
     * jump that triggers the ramp (and the per-step ceiling below which the natural profile is deemed to
     * have caught up); {@code DRAIN_FLOW_SMOOTH_MAX_NODES} caps how many mainstem nodes the ramp spans.
     */
    public static final double DRAIN_FLOW_SMOOTH_STEP = 10;

    public static final int DRAIN_FLOW_SMOOTH_MAX_NODES = 20;

    // ──────────────────────────────────────────────────────────────────────────
    // Hydrology — river width & carve-profile tuning (all property-overridable).
    // ──────────────────────────────────────────────────────────────────────────

    /** Floor on every river width, in native pixels. */
    public static final double MIN_WIDTH = 0.2f;

    /** Scale on {@code sqrt(flow)} shared by the global and local networks (see {@link #widthFromFlow}). */
    public static final double WIDTH_FLOW_SCALE = 0.4f;

    public static final double MAX_WIDTH = 16f;

    /**
     * Currently unused: no live code reads this, not even through the {@code FractalTerrainConfig} facade
     * re-export.
     */
    public static final double MAX_LOCAL_WIDTH = 6f;

    /**
     * Floodplain half-extent (native px) = {@code FLOODPLAIN_BASE + FLOODPLAIN_WIDTH_FACTOR · width}. This
     * is the <em>flat</em> band carved at floodplain elevation; the blend to decoded terrain starts only
     * past it. Kept very tunable — a later plan adds noise to make this vary.
     */
    public static final double FLOODPLAIN_BASE = 0.6f;

    public static final double FLOODPLAIN_WIDTH_FACTOR = 1.0f;

    /**
     * Dimensionless multiplier on {@link #floodPlainLength} used to derive a river's outer influence
     * radius (see {@code RosgenProfile#riverInfluence}): {@code riverInfluence = floodPlainLength ·
     * INFLUENCE_BLEND_MULTIPLIER}, clamped to {@link #MAX_INFLUENCE_RADIUS}. Not itself a native-px
     * width -- the blend band's actual width is {@code riverInfluence − floodPlainLength}.
     */
    public static final double INFLUENCE_BLEND_MULTIPLIER = 2f;

    /**
     * Hard cap (native px) on any river's influence radius — also the radius the cross-tile unit query
     * uses. Bounds the per-pixel carve/paint work and the query span; rivers whose computed
     * {@link #riverInfluence} would exceed this are clamped to it.
     */
    public static final double MAX_INFLUENCE_RADIUS = 128.0f;

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

    public static double widthFromFlow(double rawFlow) {
        final double lawWidth = WIDTH_FLOW_SCALE * Math.sqrt(rawFlow);
        return Math.clamp(lawWidth, MIN_WIDTH, MAX_WIDTH);
    }

    public static double maxNativeWidth() {
        return MAX_WIDTH;
    }
}
