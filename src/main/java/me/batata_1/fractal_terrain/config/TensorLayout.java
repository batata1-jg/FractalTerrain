package me.batata_1.fractal_terrain.config;

/**
 * ONNX tensor-layout invariants: the {@code [channel, x, z]} axis indices and the per-pipeline-stage
 * channel counts. These are fixed by the model I/O contract — values must never change.
 */
public final class TensorLayout {

    private TensorLayout() {}

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
}
