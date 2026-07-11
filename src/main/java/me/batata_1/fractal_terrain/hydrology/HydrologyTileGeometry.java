package me.batata_1.fractal_terrain.hydrology;

import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * Responsibility: the per-tile grid geometry shared by the local-river split ({@link LocalRiverProvider}
 * and its collaborators {@link GlobalNetworkBuilder}, {@link ChannelElevationAssigner},
 * {@link LocalDrainageTracer}) — the padded/unpadded tile dimensions and the two bilinear-sample helpers
 * keyed off them, so the same tile framing is used everywhere instead of being re-declared per class.
 *
 * <p>Collaborators: consumed by every class in the local-river split; owns no mutable state.
 *
 * <p>Invariants: {@code PADDED = GRID + 2*PAD} (a 1px halo per side, for neighbour access at the tile
 * border); {@code COARSE_PX} is the native-px span of one coarse (global-river) cell within a tile, so
 * {@code COARSE_HALF} is its midpoint offset. These are pure geometry constants — changing them changes
 * tile framing only, not algorithm behavior.
 */
final class HydrologyTileGeometry {

    private HydrologyTileGeometry() {}

    static final int GRID = 512;
    static final int PAD = 1;
    static final int PADDED = GRID + 2 * PAD; // 514
    static final int COARSE_PX = 256;
    static final int COARSE_HALF = COARSE_PX / 2;

    /** Bilinear sample of a {@code PADDED×PADDED} field (the padded/native tile frame). */
    static double sampleBilinear(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, PADDED);
    }

    /** Bilinear sample of a {@code GRID×GRID} field (the cropped/local tile frame). */
    static double sampleLocal(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, GRID);
    }
}
