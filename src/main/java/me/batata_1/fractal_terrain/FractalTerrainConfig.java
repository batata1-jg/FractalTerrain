package me.batata_1.fractal_terrain;

import me.batata_1.fractal_terrain.config.DebugConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.config.ModConfig;
import me.batata_1.fractal_terrain.config.TensorLayout;
import me.batata_1.fractal_terrain.debug.Infinite3DVisualizer;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;

/**
 * Thin delegating facade over the {@code config} package: re-exports {@link TensorLayout}, {@link
 * DebugConfig}, {@link HydrologyTuning}, and {@link ModConfig} under their original names so existing
 * callers keep compiling unchanged. New code should reference the owning class in {@code config}
 * directly.
 */
public record FractalTerrainConfig() {

    public static final float GLOBAL_SCALE_CORRECTION = ModConfig.GLOBAL_SCALE_CORRECTION;

    // ──────────────────────────────────────────────────────────────────────────
    // Algorithm tuning — see HydrologyTuning
    // ──────────────────────────────────────────────────────────────────────────

    public static final int MAX_SPLINE_LENGTH = HydrologyTuning.MAX_SPLINE_LENGTH;
    public static final int BINARY_SEARCH_MAX_STEPS = HydrologyTuning.BINARY_SEARCH_MAX_STEPS;

    // ──────────────────────────────────────────────────────────────────────────
    // Tensor layout — see TensorLayout
    // ──────────────────────────────────────────────────────────────────────────

    public static final int CH = TensorLayout.CH;
    public static final int X = TensorLayout.X;
    public static final int Z = TensorLayout.Z;

    public static final int DECODER_CHANNELS = TensorLayout.DECODER_CHANNELS;
    public static final int RELIEF_CHANNELS = TensorLayout.RELIEF_CHANNELS;
    public static final int BIOME_CHANNELS = TensorLayout.BIOME_CHANNELS;
    public static final int GLOBAL_RIVER_CHANNELS = TensorLayout.GLOBAL_RIVER_CHANNELS;

    // ──────────────────────────────────────────────────────────────────────────
    // Debug flags & logging — see DebugConfig
    // ──────────────────────────────────────────────────────────────────────────

    public static final String DEFAULT_DEBUG_PATH = DebugConfig.DEFAULT_DEBUG_PATH;
    public static final boolean DEBUG = DebugConfig.DEBUG;
    public static final boolean TEST_INSTANCE = DebugConfig.TEST_INSTANCE;
    public static final boolean DEBUG_RIVER_NET = DebugConfig.DEBUG_RIVER_NET;
    public static final boolean DEBUG_MANAGE_COLLISIONS = DebugConfig.DEBUG_MANAGE_COLLISIONS;
    public static final boolean DEBUG_CROSSING_WINNER = DebugConfig.DEBUG_CROSSING_WINNER;
    public static final boolean DEBUG_DSHORE = DebugConfig.DEBUG_DSHORE;

    // ──────────────────────────────────────────────────────────────────────────
    // 3D visualizer — see DebugConfig
    // ──────────────────────────────────────────────────────────────────────────

    public static final boolean DISABLE_3D_VISUALIZER = DebugConfig.DISABLE_3D_VISUALIZER;
    public static final Infinite3DVisualizer.DebugModes VIZ_H_CONTROL_MODE = DebugConfig.VIZ_H_CONTROL_MODE;
    public static final Infinite3DVisualizer.DebugPaintModes VIZ_PAINT_CONTROL_MODE =
            DebugConfig.VIZ_PAINT_CONTROL_MODE;
    public static final boolean DISABLE_BIOME_DECORATION = DebugConfig.DISABLE_BIOME_DECORATION;
    public static final boolean DISABLE_SURFACE_STEP = DebugConfig.DISABLE_SURFACE_STEP;
    public static final boolean TEST_HEIGHT_MAP = DebugConfig.TEST_HEIGHT_MAP;

    // ──────────────────────────────────────────────────────────────────────────
    // Hydrology — river width & carve-profile tuning — see HydrologyTuning
    // ──────────────────────────────────────────────────────────────────────────

    public static final double MIN_WIDTH = HydrologyTuning.MIN_WIDTH;
    public static final double WIDTH_FLOW_SCALE = HydrologyTuning.WIDTH_FLOW_SCALE;
    public static final double MAX_WIDTH = HydrologyTuning.MAX_WIDTH;
    public static final double MAX_LOCAL_WIDTH = HydrologyTuning.MAX_LOCAL_WIDTH;
    public static final double FLOODPLAIN_BASE = HydrologyTuning.FLOODPLAIN_BASE;
    public static final double FLOODPLAIN_WIDTH_FACTOR = HydrologyTuning.FLOODPLAIN_WIDTH_FACTOR;
    public static final double INFLUENCE_BLEND_MULTIPLIER = HydrologyTuning.INFLUENCE_BLEND_MULTIPLIER;
    public static final double MAX_INFLUENCE_RADIUS = HydrologyTuning.MAX_INFLUENCE_RADIUS;
    public static final double MAX_CARVE_DELTA = HydrologyTuning.MAX_CARVE_DELTA;

    /** @see HydrologyTuning#floodPlainLength(double, RosgenType) */
    public static double floodPlainLength(double width, RosgenType type) {
        return HydrologyTuning.floodPlainLength(width, type);
    }

    /** @see HydrologyTuning#floodPlainLength(double) */
    public static double floodPlainLength(double width) {
        return HydrologyTuning.floodPlainLength(width);
    }

    /** @see HydrologyTuning#riverInfluence(double, RosgenType) */
    public static double riverInfluence(double width, RosgenType type) {
        return HydrologyTuning.riverInfluence(width, type);
    }

    /** @see HydrologyTuning#riverInfluence(double) */
    public static double riverInfluence(double width) {
        return HydrologyTuning.riverInfluence(width);
    }

    /** @see HydrologyTuning#maxNativeWidth() */
    public static double maxNativeWidth() {
        return HydrologyTuning.maxNativeWidth();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Property-file config — see ModConfig
    // ──────────────────────────────────────────────────────────────────────────

    /** @see ModConfig#inferenceDevice() */
    public static String inferenceDevice() {
        return ModConfig.inferenceDevice();
    }

    /** @see ModConfig#offloadModels() */
    public static boolean offloadModels() {
        return ModConfig.offloadModels();
    }

    /** @see ModConfig#explorerPort() */
    public static int explorerPort() {
        return ModConfig.explorerPort();
    }

    /** @see ModConfig#validateModel() */
    public static boolean validateModel() {
        return ModConfig.validateModel();
    }
}
