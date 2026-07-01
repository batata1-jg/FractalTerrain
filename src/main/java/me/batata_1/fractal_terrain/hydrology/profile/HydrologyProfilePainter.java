package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;

/**
 * The block/biome/vegetation side of the hydrology profile — the painting twin of
 * {@link HydrologyProfileCarver}. Where the carver lowers elevation, the painter decides what to place:
 * river water (from the {@link Types#RIVER_DIFFERENCE} the carver wrote), and (later) river-aware biome
 * parameters and a vegetation PDF. It shares the same {@link HydrologyProfile} iterate primitive and the
 * same per-tile river query.
 */
public final class HydrologyProfilePainter {

    @SuppressWarnings("unused") // retained for the upcoming biome / vegetation painting (paintBiome, vegPdf)
    private final LocalRiverProvider localRiver;

    public HydrologyProfilePainter(LocalRiverProvider localRiver) {
        this.localRiver = localRiver;
    }

    /** Convenience: resolve the live {@link LocalRiverProvider} from the singleton. */
    public HydrologyProfilePainter() {
        this(FractalTerrainInstance.getLocalRiverProvider());
    }

    /**
     * Top Y (inclusive) up to which river water should fill the column {@code (dx, dz)}, or
     * {@code reliefHeight} when no river water applies there. Reads {@link Types#RIVER_DIFFERENCE}: where the
     * carve lowered the terrain ({@code diff < 0}) the channel fills with water up to the pre-carve surface
     * ({@code reliefHeight − diff}). This mirrors the carver: negative carve delta → water in the carved
     * channel. The chunk filler turns the air between {@code reliefHeight} and this top into water.
     */
    public int riverWaterTop(FractalTerrainHeightmap heightmaps, int dx, int dz, int reliefHeight) {
        final float diff = heightmaps.get(Types.RIVER_DIFFERENCE, dx, dz);
        if (diff >= 0f) return reliefHeight;
        return Math.round(reliefHeight - diff);
    }
}
