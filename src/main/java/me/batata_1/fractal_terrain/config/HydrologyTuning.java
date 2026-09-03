package me.batata_1.fractal_terrain.config;

/**
 * Tuning constants for river width, carve profile, and Rosgen classification.
 *
 * <p>One home for values the global and local river networks must agree on — split across the two
 * providers they would drift. Most are first-cut and uncalibrated; see {@code README.md} for which ones,
 * what miscalibration looks like on screen, and how to recalibrate.
 */
public final class HydrologyTuning {

    public static final double PRIMITIVE_BLEND_STRENGTH = 0.05;
    public static final double INFLUENCE_BLEND_STRENGH = 0.01;

    private HydrologyTuning() {}

    // ──────────────────────────────────────────────────────────────────────────
    // Algorithm tuning
    // ──────────────────────────────────────────────────────────────────────────

    /** Hard cap on spline resampling iterations, guarding against runaway geometry. */
    public static final int MAX_SPLINE_LENGTH = (int) 1e4;
    /** Iteration cap for the spline arc-length binary search. */
    public static final int BINARY_SEARCH_MAX_STEPS = 20;

    // ──────────────────────────────────────────────────────────────────────────
    // Border / sampling constants consolidated from GlobalRiverProvider, RiverProvider, and
    // Meanders (one home per concept; values unchanged from their prior per-class declarations).
    // ──────────────────────────────────────────────────────────────────────────

    /** Coarse-px halo for {@code GlobalRiverProvider}'s isolate ramp, and that provider's padding. */
    public static final int RAMP_WIDTH = 6;

    /** Sink-fill border-blend padding (native px) used by {@code RiverProvider}'s tile carve. */
    public static final int FILL_PADDING = 64;

    /** Resample spacing (native px) for a freshly traced local channel, in {@code LocalDrainageTracer}. */
    public static final double RESAMPLE_DIST = 2.0;

    /** Radius at which a local river is taken to meet a global channel. Uncalibrated — see README. */
    public static final double LOCAL_ATTACH_RADIUS = 4.0;

    /** Default meander resample/migration step (native px); debug visualizers and tests reason about it too. */
    public static final double DX = 1.5;

    /** Max per-step displacement of a migration run resampled at {@code dx}: caps how far a point may
     *  travel relative to its neighbours, so one step cannot fold the polyline over itself. */
    public static double maxMigration(double dx) {
        return dx * 1.5;
    }

    /** Max per-step displacement at the default {@link #DX}; the network's collision search radius
     *  (see {@code AtomicView}) is derived from it. */
    public static final double MAX_MIGRATION = maxMigration(DX);

    /** {@code Centreline.normalAt}'s stencil half-length as a multiple of the point's own width.
     *  Uncalibrated by design — long enough to beat 45-degree lattice quantization, short enough not to
     *  smooth away real meander curvature. */
    public static final double TANGENT_WIDTHS = 1.0;

    /** Floor (native px) on {@code Centreline.normalAt}'s stencil half-length, for a headwater's minimum width. */
    public static final double TANGENT_MIN_PX = 2.0;

    // ──────────────────────────────────────────────────────────────────────────
    // Flow accumulation (see Drainage.computeFlow)
    // ──────────────────────────────────────────────────────────────────────────

    /** Accumulated-flow floor for a cell to count as local river; one of three river-mask gates. */
    public static final float FLOW_THRESHOLD = 0.75f;

    // only generate sources for local rivers above this to prevent weird behavior in plains.
    public static final float GRAD_THRESHOLD = 10f;

    public static final float FLOW_INITIAL_GLOBAL = 0.1f;

    public static final float FLOW_INITIAL_LOCAL = 0.02f;

    public static final float FLOW_PER_CELL_GLOBAL = 15f;

    public static final float FLOW_PER_CELL_LOCAL = 0.01f;

    /** Flow jump triggering the near-drain ramp that keeps width continuous where a tributary joins. */
    public static final double DRAIN_FLOW_SMOOTH_STEP = 10;

    public static final int DRAIN_FLOW_SMOOTH_MAX_NODES = 20;


    public static float[] flowFromHumidity(float[] humdity,boolean isGlobal) {
        float[] res = humdity.clone();
        for(int px=0; px<humdity.length ; px++) {
            res[px] /= 1000.0f;
            if(isGlobal) res[px] *= FLOW_PER_CELL_GLOBAL;
            else res[px] *= FLOW_PER_CELL_LOCAL;
        }
        return res;
    }

    public static double widthFromFlow(double rawFlow) {
        final double lawWidth = WIDTH_FLOW_SCALE * Math.sqrt(rawFlow);
        return Math.clamp(lawWidth, MIN_WIDTH, MAX_WIDTH);
    }
    // ──────────────────────────────────────────────────────────────────────────
    // Hydrology — river width & carve-profile tuning (all property-overridable).
    // ──────────────────────────────────────────────────────────────────────────

    /** Floor on every river width, in native pixels. */
    public static final double MIN_WIDTH = 0.6f;

    /** Scale on {@code sqrt(flow)} shared by the global and local networks (see {@link #widthFromFlow}). */
    public static final double WIDTH_FLOW_SCALE = 0.4f;

    public static final double MAX_WIDTH = 16f;

    /** Hard cap on influence radius, bounding both per-pixel carve work and the cross-tile query span. */
    public static final double MAX_INFLUENCE_RADIUS = 64.0f;

    /** Floor on influence radius, load-bearing rather than cosmetic: at zero,
     *  {@code RiverInfluenceCarve} divides by the influence half-width and the R-tree indexes the
     *  primitive under a zero-area rectangle. */
    public static final double MIN_INFLUENCE_RADIUS = 2.0;

    /** Scale taking a channel's {@code depth × width} to its influence radius. Uncalibrated — see README. */
    public static final double INFLUENCE_DEPTH_FACTOR = 2.0;

    /** Border margin kept clear, wider than the influence radius — see README. */
    public static final double MARGIN_INFLUENCE_FACTOR = 5.0;

    public static double influence(double width) {
        return Math.clamp(width * 5, MIN_INFLUENCE_RADIUS, MAX_INFLUENCE_RADIUS);
    }

    /** Influence radius for a channel point whose bed sits {@code deltaElev} below the surface, so a deeply
     *  incised channel pulls in more terrain than its width alone implies. Callers with no bed elevation
     *  to hand keep using {@link #influence(double)}. */
    public static double influence(double width, double deltaElev) {
        return Math.clamp(
                INFLUENCE_DEPTH_FACTOR * Math.sqrt((deltaElev + 1)) * width,
                MIN_INFLUENCE_RADIUS,
                MAX_INFLUENCE_RADIUS);
    }



    public static double maxNativeWidth() {
        return MAX_WIDTH;
    }

    /** Width threshold below which {@code HydrologicalPrimitive.waterLine} returns {@link #WATER_LINE_OFFSET_NARROW}. */
    public static final double WATER_LINE_WIDTH_NARROW = 1.5;

    /** Width threshold below which {@code HydrologicalPrimitive.waterLine} returns {@link #WATER_LINE_OFFSET_MEDIUM}. */
    public static final double WATER_LINE_WIDTH_MEDIUM = 2.5;

    /** Water-surface offset (native px) below the bank for a channel narrower than {@link #WATER_LINE_WIDTH_NARROW}. */
    public static final float WATER_LINE_OFFSET_NARROW = -1;

    /** Water-surface offset (native px) below the bank for a channel narrower than {@link #WATER_LINE_WIDTH_MEDIUM}. */
    public static final float WATER_LINE_OFFSET_MEDIUM = -2;

    /** Water-surface offset (native px) below the bank for any channel at or above {@link #WATER_LINE_WIDTH_MEDIUM}. */
    public static final float WATER_LINE_OFFSET_WIDE = -3;

    // ──────────────────────────────────────────────────────────────────────────
    // Rosgen Level-I classification (see hydrology/rosgen/ — ReachRosgenClassifier, RosgenKey, RosgenProfile)
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

    /** Flatness gate keeping {@code DA} off any reach with real fall. Not a published Rosgen figure. */
    public static final double S_DA = 0.005;

    /** Entrenchment ratio below which a reach is entrenched ({@code F}/{@code G}). Rosgen: 1.0–1.4. */
    public static final double ER_ENTRENCHED = 1.6;

    /** Entrenchment ratio below which a reach is moderately entrenched ({@code B}). Rosgen: 1.41–2.2. */
    public static final double ER_SLIGHT = 2.7;

    /** Entrenchment ratio above which the flood-prone area is wide enough for {@code DA}. */
    public static final double ER_ANASTOMOSE = 4.0;

    /** Splits narrow-deep ({@code E G}) from wide-shallow ({@code C F}); calibrate {@code W_REF}, not this. */
    public static final double WD_NARROW = 1.0;

    /** Sets the flood-prone stage as a multiple of mean bankfull depth. Rule of thumb, not sourced. */
    public static final double DEPTH_MAX_FACTOR = 1;

    /** Bed elevation below which a reach counts as near base level for the {@code DA} gate. Uncalibrated. */
    public static final double DELTA_ELEV = 4.0;

    /** Coefficient of the braiding threshold; an authored gate, not a measurement. See README. */
    public static final double K_BRAID = 0.02;

    /** Exponent of the braiding threshold in width. Derived: {@code -0.44 / 0.50}. */
    public static final double BRAID_WIDTH_EXPONENT = -0.88;

    /** Width floor for a braided {@code D} reach, set to keep {@code D} rare. Uncalibrated — see README. */
    public static final double BRAID_MIN_WIDTH = 4.0;

    /** Reach length as a multiple of bankfull width — Rosgen's own reach definition (20–30 widths). */
    public static final double REACH_WIDTHS = 20.0;

    /** Caps a reach window so a trunk river's reach cannot span most of a tile. */
    public static final double REACH_MAX_PX = 64.0;

    // bias towards lower ER, mostly affects streams with small widths.
    public static final double ENTRENTMENT_RATIO_BIAS = 10;

    /** Entrenchment transect half-walk; never substitute {@link #MAX_INFLUENCE_RADIUS} — see README. */
    public static final double ER_WALK_WIDTHS = 7;

    /** Transect step as a fraction of width, keeping the sample count roughly constant across widths. */
    public static final double ER_STEP_WIDTH_FRACTION = 0.125;

    /** Floor (native px) on the entrenchment transect step. */
    public static final double ER_STEP_MIN = 0.25;

    /** Sample-count floor, so a minimum-width reach still gets sampled instead of reading as unconfined. */
    public static final double ER_MIN_STEPS_PER_SIDE = 1.0;
}
