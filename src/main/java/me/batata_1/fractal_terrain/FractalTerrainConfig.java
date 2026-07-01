package me.batata_1.fractal_terrain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import me.batata_1.fractal_terrain.debug.Infinite3DVisualizer;
import net.fabricmc.loader.api.FabricLoader;

public record FractalTerrainConfig() {

    public static final float GLOBAL_SCALE_CORRECTION = 5f;

    // ──────────────────────────────────────────────────────────────────────────
    // Algorithm tuning
    // ──────────────────────────────────────────────────────────────────────────

    /** Hard cap on spline resampling iterations, guarding against runaway geometry. */
    public static final int MAX_SPLINE_LENGTH = (int) 1e4;
    /** Iteration cap for the spline arc-length binary search. */
    public static final int BINARY_SEARCH_MAX_STEPS = 20;

    // ──────────────────────────────────────────────────────────────────────────
    // Tensor layout (axis indices and per-stage channel counts)
    // ──────────────────────────────────────────────────────────────────────────

    /** Tensor axis indices for {@code [channel, x, z]} tile keys. */
    public static final int CH = 0;

    public static final int X = 1;
    public static final int Z = 2;

    /** Channel counts per pipeline stage. */
    public static final int DECODER_CHANNELS = 8;

    public static final int RELIEF_CHANNELS = 7;
    public static final int BIOME_CHANNELS = 6;
    public static final int GLOBAL_RIVER_CHANNELS = 3;

    // ──────────────────────────────────────────────────────────────────────────
    // Debug flags & logging
    // ──────────────────────────────────────────────────────────────────────────

    public static final String DEFAULT_DEBUG_PATH = "run/debug";
    public static final boolean DEBUG = false;
    public static final boolean TEST_INSTANCE = false;
    public static final boolean DEBUG_RIVER_NET = false;
    public static final boolean DEBUG_MANAGE_COLLISIONS = false;
    public static final boolean DEBUG_CROSSING_WINNER = false;

    /** Logs every distance-to-shore grid cell (coarse-px coordinate + value) as a biome tile is built. */
    public static final boolean DEBUG_DSHORE = false;

    // ──────────────────────────────────────────────────────────────────────────
    // 3D visualizer (debug terrain projection — see Infinite3DVisualizer)
    // ──────────────────────────────────────────────────────────────────────────

    public static final boolean DISABLE_3D_VISUALIZER = true;

    /**
     * Drives the elevation each visualizer column is raised to ({@link Infinite3DVisualizer#debugElevController}).
     * Available {@link Infinite3DVisualizer.DebugModes}:
     * <ul>
     *   <li>{@code RELIEF} — decoded relief elevation channel.</li>
     *   <li>{@code COARSE} — coarse-stage elevation channel.</li>
     * </ul>
     */
    public static final Infinite3DVisualizer.DebugModes VIZ_H_CONTROL_MODE = Infinite3DVisualizer.DebugModes.DIST_SHORE;

    /**
     * Drives the block painted at each visualizer position ({@link Infinite3DVisualizer#debugPaintController}).
     * Available {@link Infinite3DVisualizer.DebugPaintModes}:
     * <ul>
     *   <li>{@code RIVER_NET} — global/local river + coast markers.</li>
     *   <li>{@code PV} — peaks-and-valleys bands quantized from biome weirdness.</li>
     * </ul>
     */
    public static final Infinite3DVisualizer.DebugPaintModes VIZ_PAINT_CONTROL_MODE =
            Infinite3DVisualizer.DebugPaintModes.PV;

    /** Generation steps suppressed while the visualizer is active. */
    public static final boolean DISABLE_BIOME_DECORATION = true || !DISABLE_3D_VISUALIZER;

    public static final boolean DISABLE_SURFACE_STEP = false || !DISABLE_3D_VISUALIZER;
    public static final boolean TEST_HEIGHT_MAP = false;

    // ──────────────────────────────────────────────────────────────────────────
    // Property-file config (defaults backing the readers below)
    // ──────────────────────────────────────────────────────────────────────────

    private static final String FILE_NAME = "terrain-diffusion-mc.properties";
    private static final String RESOURCE_PATH = "/" + FILE_NAME;
    private static final Properties PROPERTIES = new Properties();
    private static final String DEFAULT_INFERENCE_DEVICE = "gpu";
    private static final boolean DEFAULT_OFFLOAD_MODELS = false;
    private static final boolean DEFAULT_VALIDATE_MODEL = false;
    private static final int DEFAULT_EXPLORER_PORT = 19801;

    static {
        loadDefaults();
        Path configPath = resolveConfigPath();
        if (configPath != null) {
            loadOverrides(configPath);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Hydrology — river width & carve-profile tuning (all property-overridable).
    // Declared after the static block so PROPERTIES is already populated.
    // ──────────────────────────────────────────────────────────────────────────

    /** Floor on every river width, in native pixels. */
    public static final double MIN_WIDTH = readDouble("hydrology.min_width", 1.0);

    /** Scale on {@code sqrt(flow)} shared by the global and local networks (see {@link #widthFromFlow}). */
    public static final double WIDTH_FLOW_SCALE = 0.2f;

    /**
     * Multiplier applied to <em>global</em>-river widths only, converting their coarse-px flow widths into
     * native px. (Local widths already come out in native px, so they use {@link #widthFromFlow} directly.)
     */
    public static final double GLOBAL_WIDTH_COORD_SCALE = 10f;

    /**
     * Floodplain half-extent (native px) = {@code FLOODPLAIN_BASE + FLOODPLAIN_WIDTH_FACTOR · width}. This
     * is the <em>flat</em> band carved at floodplain elevation; the blend to decoded terrain starts only
     * past it. Kept very tunable — a later plan adds noise to make this vary.
     */
    public static final double FLOODPLAIN_BASE = readDouble("hydrology.floodplain_base", 4.0);

    public static final double FLOODPLAIN_WIDTH_FACTOR = readDouble("hydrology.floodplain_width_factor", 2.0);

    /** Width of the blend-to-decoded band beyond the floodplain (native px). */
    public static final double INFLUENCE_BLEND_LEN = readDouble("hydrology.influence_blend_len", 16.0);

    /**
     * Hard cap (native px) on any river's influence radius — also the radius the cross-tile unit query
     * uses. Bounds the per-pixel carve/paint work and the query span; rivers whose computed
     * {@link #maxRiverInfluence} would exceed this are clamped to it.
     */
    public static final double MAX_INFLUENCE_RADIUS = readDouble("hydrology.max_influence_radius", 128.0);

    /**
     * Max {@code |intended bed − decoded|} a point may carve. Beyond this the point is treated as
     * <em>uncarvable</em> and excluded from the carve/merge entirely (so we never leave isolated holes or
     * trenches); its hydrological unit still records the intended bed elevation.
     */
    public static final double MAX_CARVE_DELTA = readDouble("hydrology.max_carve_delta", 48.0);

    /** Floodplain half-extent for a river of the given width (native px). */
    public static double floodPlainLength(double width) {
        return FLOODPLAIN_BASE + FLOODPLAIN_WIDTH_FACTOR * width;
    }

    /**
     * Outer influence radius for a river of the given width: floodplain + blend band (native px), clamped
     * to {@link #MAX_INFLUENCE_RADIUS}. Beyond this radius a river no longer affects a pixel.
     */
    public static double maxRiverInfluence(double width) {
        return Math.min(MAX_INFLUENCE_RADIUS, floodPlainLength(width) + INFLUENCE_BLEND_LEN);
    }

    /**
     * The single river-width law shared by the global and local networks:
     * {@code max(MIN_WIDTH, WIDTH_FLOW_SCALE · sqrt(max(0, rawFlow)))}. Global callers additionally
     * multiply the result by {@link #GLOBAL_WIDTH_COORD_SCALE} to convert coarse-px flow into native px.
     */
    public static double widthFromFlow(double rawFlow) {
        return Math.max(MIN_WIDTH, WIDTH_FLOW_SCALE * Math.log(Math.max(0.0, rawFlow)));
    }

    /** Inference device: "cpu", "gpu", or "auto" (try GPU then fall back to CPU). */
    public static String inferenceDevice() {
        return readString("inference.device", DEFAULT_INFERENCE_DEVICE);
    }

    /** Whether to offload inactive models from VRAM between pipeline stages. */
    public static boolean offloadModels() {
        return readBoolean("inference.offload_models", DEFAULT_OFFLOAD_MODELS);
    }

    /** TCP port for the local terrain explorer HTTP server. */
    public static int explorerPort() {
        return readInt("explorer.port", DEFAULT_EXPLORER_PORT);
    }

    /** Whether to validate SHA-256 for pre-existing local model files before use. */
    public static boolean validateModel() {
        return readBoolean("validate_model", DEFAULT_VALIDATE_MODEL);
    }

    private static void loadDefaults() {
        boolean loadedFromResource = false;
        try (InputStream in =
                me.batata_1.fractal_terrain.FractalTerrainConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                PROPERTIES.load(in);
                loadedFromResource = true;
            }
        } catch (IOException e) {
            System.err.println("Failed to load default config from resource: " + e.getMessage());
        }

        if (!loadedFromResource) {
            PROPERTIES.setProperty("inference.device", DEFAULT_INFERENCE_DEVICE);
            PROPERTIES.setProperty("validate_model", String.valueOf(DEFAULT_VALIDATE_MODEL));
        }
    }

    private static String readString(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value != null ? value.trim().toLowerCase() : defaultValue;
    }

    private static Path resolveConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        } catch (RuntimeException e) {
            System.err.println("Fabric Loader config directory unavailable: " + e.getMessage());
            return null;
        }
    }

    private static void loadOverrides(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    Properties overrides = new Properties();
                    overrides.load(in);
                    PROPERTIES.putAll(overrides);
                }
            } else {
                writeConfig(configPath);
            }
        } catch (IOException e) {
            System.err.println("Failed to read config file: " + e.getMessage());
        }
    }

    private static void writeConfig(Path configPath) {
        try (InputStream defaultConfigInputStream =
                me.batata_1.fractal_terrain.FractalTerrainConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (defaultConfigInputStream != null) {
                Files.copy(defaultConfigInputStream, configPath);
                return;
            }
            System.err.println("Default config resource not found: " + RESOURCE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to copy default config resource: " + e.getMessage());
        }
    }

    private static boolean readBoolean(String key, boolean defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private static int readInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Invalid int for " + key + ": " + value + ", using default " + defaultValue);
            return defaultValue;
        }
    }

    private static double readDouble(String key, double defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid double for " + key + ": " + value + ", using default " + defaultValue);
            return defaultValue;
        }
    }
}
