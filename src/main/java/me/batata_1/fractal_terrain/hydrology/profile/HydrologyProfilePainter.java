package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;

/**
 * The block/biome/vegetation side of the hydrology profile — the painting twin of
 * {@link HydrologyProfileInprinter}. Where the carver lowers elevation, the painter decides what to place:
 * river water (from the {@link Types#RIVER_DIFFERENCE} the carver wrote), channel membership
 * ({@link #insideChannel}), and (later) river-aware biome parameters and a vegetation PDF. It shares the
 * same {@link HydrologyProfile} core and the same per-tile river query.
 */
public final class HydrologyProfilePainter {

    private final RiverProvider riverProvider;

    public HydrologyProfilePainter(RiverProvider riverProvider) {
        this.riverProvider = riverProvider;
    }

    /** Water surface height for a column; the chunk filler floods up to it. Measured against the
     *  tile-carved shell, not the original decoded terrain. */
    public int riverWaterTop(FractalTerrainHeightmap heightmaps, int dx, int dz, int reliefHeight) {
        final float diff = heightmaps.get(Types.RIVER_DIFFERENCE, dx, dz);
        if (diff >= 0f) return reliefHeight;
        return Math.round(reliefHeight - diff);
    }

    /** Whether the point is in open water. Filters the influence query's results, which are a sound
     *  superset since influence always exceeds half-width. */
    public boolean insideChannel(double[] pixelPt) {
        final double tileVisitRadius = HydrologyTuning.maxNativeWidth() / 2.0;
        return riverProvider.anyInfluencingPrimitive(pixelPt, tileVisitRadius, HydrologicalPrimitive::channelContains);
    }
}
