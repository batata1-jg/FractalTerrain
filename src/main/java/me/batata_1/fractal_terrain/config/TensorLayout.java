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

    /**
     * Global-river tile channels: 0 = packed arrow bitfield, 1 = width, 2 = bed elevation, 3 = raw
     * flow accumulation. Channel 3 (flow) is persisted so the local network can derive width from flow
     * without the lossy {@code widthFromFlow} inversion (see {@code GlobalNetworkBuilder}); width stays
     * channel 1 (still consumed by the relief carve). Bumped 3&rarr;4 in Phase 2 — on-disk cached global
     * tiles from the old 3-channel layout are incompatible and must be regenerated.
     */
    public static final int GLOBAL_RIVER_CHANNELS = 4;
}
