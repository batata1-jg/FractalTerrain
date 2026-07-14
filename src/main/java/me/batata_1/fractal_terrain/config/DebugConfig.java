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
    public static final boolean DEBUG_CROSSING_WINNER = ModConfig.readBoolean("debug.crossing_winner", false);

    /** Logs every distance-to-shore grid cell (coarse-px coordinate + value) as a biome tile is built. */
    public static final boolean DEBUG_DSHORE = ModConfig.readBoolean("debug.dshore", false);

    // ──────────────────────────────────────────────────────────────────────────
    // 3D visualizer (debug terrain projection — see Infinite3DVisualizer)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Whether the 3D visualizer replaces normal chunk fill. Checked once per chunk fill, not per block, so
     * a runtime read costs nothing on the generation hot path.
     */
    public static final boolean DISABLE_3D_VISUALIZER = false;

    /**
     * Drives the elevation each visualizer column is raised to ({@link Infinite3DVisualizer#debugElevController}).
     * Sampled once per column inside the visualizer's per-block fill loop — genuinely hot, so this stays a
     * compile-time constant rather than a property-sourced field. Available
     * {@link Infinite3DVisualizer.DebugModes}:
     * <ul>
     *   <li>{@code RELIEF} — elevation after the carving step (carved+filled ch0 imported by ReliefProvider).</li>
     *   <li>{@code DECODED} — elevation before the carving step (raw decoded terrain, {@code DecoderChannels}
     *       {@code base[0]}), same vertical scale as {@code RELIEF} for direct before/after comparison.</li>
     *   <li>{@code COARSE} — coarse-stage elevation channel.</li>
     *   <li>{@code DIST_SHORE} — distance-to-shore field from the biome provider.</li>
     * </ul>
     */
    public static final Infinite3DVisualizer.DebugModes VIZ_H_CONTROL_MODE = Infinite3DVisualizer.DebugModes.RELIEF;

    /**
     * Drives the block painted at each visualizer position ({@link Infinite3DVisualizer#debugPaintController}).
     * Sampled once per block inside the visualizer's per-block fill loop — genuinely hot, so this stays a
     * compile-time constant rather than a property-sourced field. Available
     * {@link Infinite3DVisualizer.DebugPaintModes}:
     * <ul>
     *   <li>{@code RIVER_NET} — global/local river + coast markers.</li>
     *   <li>{@code PV} — peaks-and-valleys bands quantized from biome weirdness.</li>
     * </ul>
     */
    public static final Infinite3DVisualizer.DebugPaintModes VIZ_PAINT_CONTROL_MODE =
            Infinite3DVisualizer.DebugPaintModes.RIVER_NET;

    /** Generation steps suppressed while the visualizer is active. */
    public static final boolean DISABLE_BIOME_DECORATION = true || !DISABLE_3D_VISUALIZER;

    public static final boolean DISABLE_SURFACE_STEP = false || !DISABLE_3D_VISUALIZER;
    public static final boolean TEST_HEIGHT_MAP = ModConfig.readBoolean("debug.test_height_map", false);
}
