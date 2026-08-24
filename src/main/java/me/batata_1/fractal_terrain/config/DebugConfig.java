package me.batata_1.fractal_terrain.config;

import me.batata_1.fractal_terrain.debug.Infinite3DVisualizer;

/**
 * Debug flags and logging config. Boolean flags that only guard cold/one-shot debug dumps are sourced
 * from {@code .properties} at load and cached in {@code final} fields (no per-check IO). Flags read on a
 * genuinely hot per-block/per-column/per-sample generation path stay compile-time {@code static final}
 * constants instead, since a runtime-loaded {@code final boolean} is not a compile-time constant and
 * loses {@code if(DEBUG)} dead-code elimination — see {@link #VIZ_H_CONTROL_MODE} / {@link
 * #VIZ_PAINT_CONTROL_MODE}.
 */
public final class DebugConfig {

    private DebugConfig() {}

    // ──────────────────────────────────────────────────────────────────────────
    // Debug flags & logging (cold/one-shot — property-overridable, read once at load)
    // ──────────────────────────────────────────────────────────────────────────

    public static final String DEFAULT_DEBUG_PATH = "run/debug";

    public static final boolean DEBUG = ModConfig.readBoolean("debug.enabled", false);
    public static final boolean TEST_INSTANCE = ModConfig.readBoolean("debug.test_instance", false);
    public static final boolean DEBUG_RIVER_NET = ModConfig.readBoolean("debug.river_net", false);
    public static final boolean DEBUG_MANAGE_COLLISIONS = ModConfig.readBoolean("debug.manage_collisions", false);
    public static final boolean DEBUG_CROSSING_WINNER = false;

    /** When true, every migration step dumps per-stage network PNGs into {@code step_<n>/} folders.
     *  Mutable, unlike the flags above: the debug harnesses set it per run, and it lives here rather
     *  than on a migration driver so {@code hydrology/} and {@code hydrology/network/} need not import
     *  upward from {@code hydrology/meanders/} to read it. */
    public static boolean DEBUG_STEPS = false;

    /** Logs every distance-to-shore grid cell (coarse-px coordinate + value) as a biome tile is built. */
    public static final boolean DEBUG_DSHORE = ModConfig.readBoolean("debug.dshore", false);

    // ──────────────────────────────────────────────────────────────────────────
    // 3D visualizer (debug terrain projection — see Infinite3DVisualizer)
    // ──────────────────────────────────────────────────────────────────────────

    /** Whether the 3D visualizer replaces normal chunk fill. */
    public static final boolean DISABLE_3D_VISUALIZER = false;

    /** Which field the visualizer raises columns to. Compile-time because it is read per column. */
    public static final Infinite3DVisualizer.DebugModes VIZ_H_CONTROL_MODE =
            Infinite3DVisualizer.DebugModes.RELIEF;

    /** Which field the visualizer colours by. Compile-time because it is read per block. */
    public static final Infinite3DVisualizer.DebugPaintModes VIZ_PAINT_CONTROL_MODE =
            Infinite3DVisualizer.DebugPaintModes.HYDRO_ZONES;

    /** Generation steps suppressed while the visualizer is active. */
    public static final boolean DISABLE_BIOME_DECORATION = true || !DISABLE_3D_VISUALIZER;

    public static final boolean DISABLE_SURFACE_STEP = false || !DISABLE_3D_VISUALIZER;
    public static final boolean TEST_HEIGHT_MAP = ModConfig.readBoolean("debug.test_height_map", false);
}
