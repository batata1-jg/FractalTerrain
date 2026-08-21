package me.batata_1.fractal_terrain.hydrology;

import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * Tile framing shared by the local-river split, so the four collaborating classes cannot drift apart on
 * frame conventions.
 *
 * <p>{@code PADDED = GRID + 2*PAD} gives a one-pixel halo for neighbour access at the tile border;
 * {@code COARSE_PX} is one global-river cell's native-px span.
 */
public final class HydrologyTileGeometry {

    private HydrologyTileGeometry() {}

    public static final int GRID = 512;
    public static final int PAD = 1;
    public static final int PADDED = GRID + 2 * PAD; // 514
    static final int COARSE_PX = 256;
    static final int COARSE_HALF = COARSE_PX / 2;

    public static double sampleBilinear(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, PADDED);
    }

    static double sampleLocal(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, GRID);
    }
}
