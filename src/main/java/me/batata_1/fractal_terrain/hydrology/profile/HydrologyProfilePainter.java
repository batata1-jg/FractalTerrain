package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;

/**
 * The block/biome/vegetation side of the hydrology profile — the painting twin of
 * {@link HydrologyProfileCarver}. Where the carver lowers elevation, the painter decides what to place:
 * river water (from the {@link Types#RIVER_DIFFERENCE} the carver wrote), channel membership
 * ({@link #insideChannel}), and (later) river-aware biome parameters and a vegetation PDF. It shares the
 * same {@link HydrologyProfile} core and the same per-tile river query.
 */
public final class HydrologyProfilePainter {

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

    /**
     * True when {@code pixelPt} (relief-pixel frame) lies inside some channel's water span — i.e. within
     * {@code unit.width()/2} of any {@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit}. The
     * query region radius is {@link FractalTerrainConfig#maxNativeWidth()}{@code /2}: unit widths are
     * capped at {@code MAX_WIDTH} (local channels, native px) and
     * {@code MAX_WIDTH · GLOBAL_WIDTH_COORD_SCALE} (global rivers, rescaled after the width law), so any
     * unit whose half-width disc could contain the point must lie inside that radius. Correct across tile
     * borders ({@code anyValueWithin} visits every overlapping tile) and allocation-free (early-exit
     * traversal, no result list).
     */
    public boolean insideChannel(double[] pixelPt) {
        final double queryRadius = FractalTerrainConfig.maxNativeWidth() / 2.0;
        return localRiver.anyUnitWithin(
                pixelPt, queryRadius, (unit, distSq) -> distSq <= (unit.width() * 0.5) * (unit.width() * 0.5));
    }
}
