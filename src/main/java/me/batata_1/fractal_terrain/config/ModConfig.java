package me.batata_1.fractal_terrain.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code .properties} load/parse machinery (defaults resource + config-dir overrides) plus the
 * remaining global scalar config that isn't tensor layout, debug, or hydrology tuning. Other {@code
 * config} classes read through {@link #readBoolean}/{@link #readInt}/{@link #readDouble}/{@link
 * #readString} once this class's static initializer has populated {@link #PROPERTIES}.
 */
public final class ModConfig {

    private ModConfig() {}

    public static final float GLOBAL_SCALE_CORRECTION = 5f;

    // ──────────────────────────────────────────────────────────────────────────
    // Property-file config (defaults backing the readers below)
    // ──────────────────────────────────────────────────────────────────────────

    private static final Logger LOG = LoggerFactory.getLogger(ModConfig.class);
    private static final String FILE_NAME = "terrain-diffusion-mc.properties";
    private static final String RESOURCE_PATH = "/" + FILE_NAME;
    private static final Properties PROPERTIES = new Properties();
    private static final String DEFAULT_INFERENCE_DEVICE = "gpu";

    /**
     * Last-resort fallback used only if the bundled {@code terrain-diffusion-mc.properties} resource
     * fails to load entirely. The operative default normally comes from that resource's {@code
     * inference.offload_models} value. Note the resource-load-failure branch in {@link
     * #loadDefaults()} does not set an explicit {@code inference.offload_models} property, so this
     * constant IS reached in that branch (unlike {@link #DEFAULT_VALIDATE_MODEL}).
     */
    private static final boolean DEFAULT_OFFLOAD_MODELS = false;

    /**
     * Last-resort fallback used only if the bundled {@code terrain-diffusion-mc.properties} resource
     * fails to load entirely. The operative default normally comes from that resource's {@code
     * validate_model} value. Note the resource-load-failure branch in {@link #loadDefaults()} sets an
     * explicit {@code validate_model} property equal to this constant, so this value is consulted
     * directly in that branch rather than via {@link #readBoolean}'s default parameter.
     */
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
        try (InputStream in = ModConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                PROPERTIES.load(in);
                loadedFromResource = true;
            }
        } catch (IOException e) {
            LOG.warn("Failed to load default config from resource; falling back to built-in defaults", e);
        }

        if (!loadedFromResource) {
            PROPERTIES.setProperty("inference.device", DEFAULT_INFERENCE_DEVICE);
            PROPERTIES.setProperty("validate_model", String.valueOf(DEFAULT_VALIDATE_MODEL));
        }
    }

    static String readString(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value != null ? value.trim().toLowerCase() : defaultValue;
    }

    private static Path resolveConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        } catch (RuntimeException e) {
            LOG.warn("Fabric Loader config directory unavailable; using built-in defaults", e);
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
            LOG.warn("Failed to read config file; using built-in defaults", e);
        }
    }

    private static void writeConfig(Path configPath) {
        try (InputStream defaultConfigInputStream = ModConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (defaultConfigInputStream != null) {
                Files.copy(defaultConfigInputStream, configPath);
                return;
            }
            LOG.error("Default config resource not found: {}", RESOURCE_PATH);
        } catch (IOException e) {
            LOG.warn("Failed to copy default config resource", e);
        }
    }

    static boolean readBoolean(String key, boolean defaultValue) {
        String value = PROPERTIES.getProperty(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    static int readInt(String key, int defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid int for {}: {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    static double readDouble(String key, double defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid double for {}: {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
