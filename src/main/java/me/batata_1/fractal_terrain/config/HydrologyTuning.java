package me.batata_1.fractal_terrain.config;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.rosgen.RosgenProfile;

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
    public static final double MAX_MIGRATION = HydrologyTuning.DX;

    // ──────────────────────────────────────────────────────────────────────────
    // Flow accumulation (see Drainage.computeFlow)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Minimum accumulated flow for a cell to count as local river. Combined with the reach test and the
     * global-proximity exclusion to build the river mask. Local to this tracer rather than a
     * {@link HydrologyTuning} constant, so tuning it affects only the local network.
     */
    public static final float FLOW_THRESHOLD = 0.75f;

    // only generate sources for local rivers above this to prevent weird behavior in plains.
    public static final float GRAD_THRESHOLD = 10f;

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
    public static final double MIN_WIDTH = 0.4f;

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
     * Eccentricity at (or above) which the per-unit bed delta applies at full strength — the knob shaping
     * the elliptical footprint over which
     * {@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile#computeForUnit} fades a unit's
     * cross-section delta in. Dimensionless, in {@code (0, 1]}: {@code 1} confines full strength to the
     * unit's own cross-section line (everything else is faded), while smaller values widen the
     * full-strength ellipse along the channel — at the limit the delta would apply unfaded over the whole
     * floodplain disc. First-cut, untuned value pending visual calibration via {@code localRiverTest}.
     */
    public static final double MAX_ECCENTRICITY = 0.9;

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
    public static final double MAX_INFLUENCE_RADIUS = 64.0f;

    /**
     * Width of the border margin band {@code Meanders} keeps clear of the grid edge, as a multiple of
     * channel width. An independent margin factor (deliberately wider than {@link #riverInfluence}) so a
     * channel's whole carve band stays inside the grid.
     */
    public static final double MARGIN_INFLUENCE_FACTOR = 5.0;

    public static double maxInfluence(double width) {
        return Math.min(
                HydrologyTuning.MAX_INFLUENCE_RADIUS, width * (MAX_INFLUENCE_RADIUS/MAX_WIDTH));
    }


    public static double widthFromFlow(double rawFlow) {
        final double lawWidth = WIDTH_FLOW_SCALE * Math.sqrt(rawFlow);
        return Math.clamp(lawWidth, MIN_WIDTH, MAX_WIDTH);
    }

    public static double maxNativeWidth() {
        return MAX_WIDTH;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rosgen Level-I classification (see plans/rosgen-classification-plan.md)
    //
    // Slope bands: Rosgen's published values are real-world channel slopes. A
    // Minecraft-scale world is vertically exaggerated relative to its horizontal run
    // (a 150-block rise over 300 blocks is slope 0.5, five times S_AA), so copying the
    // literature directly classifies most of the world as Aa+. These are the literature
    // values as a starting point ONLY; recalibrate from the slope histogram dumped by
    // localRiverTest before judging any other threshold. The key tests slope first, so
    // slope miscalibration dominates every other error.
    // ──────────────────────────────────────────────────────────────────────────

    public static final double RIVER_SLOPE_RESCALE = 50;

    /** Slope at or above which a reach is {@code Aa+} (very steep, step/waterfall). Needs recalibration. */
    public static final double S_AA = 0.10;

    /** Slope at or above which a reach is {@code A} (steep, cascading step-pool). Needs recalibration. */
    public static final double S_A = 0.04;

    /**
     * Slope below which an anastomosing ({@code DA}) reach is plausible — essentially flat. Not a
     * published Rosgen figure: it is a gate this plan introduces so {@code DA} cannot claim a reach with
     * any real fall. First-cut, untuned value pending visual calibration via {@code localRiverTest}.
     */
    public static final double S_DA = 0.005;

    /** Entrenchment ratio below which a reach is entrenched ({@code F}/{@code G}). Rosgen: 1.0–1.4. */
    public static final double ER_ENTRENCHED = 1.4;

    /** Entrenchment ratio below which a reach is moderately entrenched ({@code B}). Rosgen: 1.41–2.2. */
    public static final double ER_SLIGHT = 2.2;

    /** Entrenchment ratio above which the flood-prone area is wide enough for {@code DA}. */
    public static final double ER_ANASTOMOSE = 4.0;

    /**
     * Width-to-depth ratio separating narrow-deep ({@code E G}) from wide-shallow ({@code C F}). Rosgen
     * publishes this boundary at {@code 12}, but the W/D it is compared against is prescribed by
     * {@link me.batata_1.fractal_terrain.hydrology.ChannelGeometry#widthDepthRatio} rather than measured,
     * so the pair only means what {@code W_REF} makes it mean. Calibrate {@code W_REF}, not this.
     */
    public static final double WD_NARROW = 12.0;

    /** Rosgen's published ER tolerance — the dead band that suppresses type flicker at a threshold. */
    public static final double ER_TOLERANCE = 0.2;

    /** Rosgen's published W/D tolerance — the dead band that suppresses type flicker at a threshold. */
    public static final double WD_TOLERANCE = 2.0;

    /**
     * Maximum bankfull depth as a multiple of mean bankfull depth, setting the flood-prone stage
     * ({@code bed + 2·dMax}). A rule of thumb rather than a sourced figure — calibrate visually.
     */
    public static final double DEPTH_MAX_FACTOR = 1;

    /**
     * Bed elevation (native px above sea level, which is {@code 0}) below which a reach counts as near
     * base level for the {@code DA} gate — deltas and tidal flats. First-cut, untuned.
     */
    public static final double DELTA_ELEV = 4.0;

    /**
     * Coefficient of the braiding threshold {@code S_braid = K_BRAID · width^-0.88}. Leopold &amp; Wolman
     * (1957) give {@code S = k·Q^-0.44}; with {@code W ∝ √flow ∝ √DA} this becomes a law in width.
     * Braiding is a style choice here, not a measurement — there is no sediment-transport model — so this
     * gates where braiding would be *plausible*. First-cut, untuned.
     */
    public static final double K_BRAID = 0.02;

    /** Exponent of the braiding threshold in width. Derived: {@code -0.44 / 0.50}. */
    public static final double BRAID_WIDTH_EXPONENT = -0.88;

    /**
     * Minimum native-px width for a {@code D} (braided) reach — braiding needs a large channel. Half the
     * width cap, chosen so {@code D} stays rare rather than from any published figure. First-cut,
     * untuned value pending visual calibration via {@code localRiverTest}.
     */
    public static final double BRAID_MIN_WIDTH = 8.0;

    /** Reach length as a multiple of bankfull width — Rosgen's own reach definition (20–30 widths). */
    public static final double REACH_WIDTHS = 20.0;

    /**
     * Hard cap (native px) on a reach window. {@code REACH_WIDTHS · MAX_WIDTH} is 320 px, over half a
     * 512 px tile, which would make a trunk river's reach span most of the tile. Shorter windows are
     * noisier, not wrong; the dead band absorbs the noise.
     */
    public static final double REACH_MAX_PX = 64.0;

    //bias towards lower ER, mostly affects streams with small widths.
    public static final double ENTRENTMENT_RATIO_BIAS = 1;

    /**
     * Entrenchment transect half-walk as a multiple of bankfull width. The key resolves ER only at 1.4,
     * 2.2 and 4.0, and {@code ER = floodProneWidth / bankfullWidth}, so {@code 2.05·width} per side
     * resolves ER up to 4.1 — everything the key needs. <b>Deliberately not
     * {@link #MAX_INFLUENCE_RADIUS}</b>: that is a carve constant, and a 128 px walk on a 514 px padded
     * buffer overruns the tile for any channel within 128 px of a border. {@code sampleBilinear} clamps
     * rather than failing, so an overrun silently returns the edge pixel repeated and reports
     * {@code ER = ∞}.
     */
    public static final double ER_WALK_WIDTHS = 5;

    /**
     * Entrenchment transect step (native px) as a fraction of bankfull width, floored at
     * {@link #ER_STEP_MIN}. Scaling with width keeps the step count roughly constant (~16 per side)
     * across the width range; ER is a ratio compared at four coarse thresholds, so sub-pixel precision
     * buys nothing.
     */
    public static final double ER_STEP_WIDTH_FRACTION = 0.125;

    /** Floor (native px) on the entrenchment transect step. */
    public static final double ER_STEP_MIN = 0.5;

    /**
     * Minimum entrenchment-transect samples per side. {@link #ER_STEP_MIN} keeps the step count low at
     * normal widths, but a reach at the {@link #MIN_WIDTH} clamp floor has a walk shorter than one step,
     * which would sample nothing and report every such reach as unconfined. Flooring the sample count
     * makes the step shrink with the walk instead. First-cut, untuned value pending visual calibration
     * via {@code localRiverTest}.
     */
    public static final double ER_MIN_STEPS_PER_SIDE = 1.0;


}
