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
}
