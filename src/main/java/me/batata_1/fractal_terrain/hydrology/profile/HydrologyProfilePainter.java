package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
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

    /**
     * Top Y (inclusive) up to which river water should fill the column {@code (dx, dz)}, or
     * {@code reliefHeight} when no river water applies there. Reads {@link Types#RIVER_DIFFERENCE} — the
     * bed-residual delta cut below the tile-carved shell, not below the original decoded terrain — and
     * where that delta is negative fills to {@code reliefHeight − diff}. The chunk filler turns the air
     * between {@code reliefHeight} and this top into water.
     */
    public int riverWaterTop(FractalTerrainHeightmap heightmaps, int dx, int dz, int reliefHeight) {
        final float diff = heightmaps.get(Types.RIVER_DIFFERENCE, dx, dz);
        if (diff >= 0f) return reliefHeight;
        return Math.round(reliefHeight - diff);
    }

    /**
     * True when {@code pixelPt} (relief-pixel frame) lies inside some channel's water span — i.e. within
     * {@code unit.width()/2} of any {@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit}.
     * Candidates come from the R-tree stabbing query, which returns the units whose <em>influence</em>
     * circle contains the point — a sound superset of the channel discs, because
     * {@code riverInfluence(w) > w/2} for every representable width — and the {@code width/2} test
     * filters them. The tile-visit radius is {@link HydrologyTuning#maxNativeWidth()}{@code /2} —
     * now including the native-rescale factor, so it correctly covers the widest global trunk's half-width
     * disc — meaning any unit whose half-width disc could contain the point must lie inside that radius.
     * Correct across tile borders ({@code anyInfluencingUnit} visits every overlapping tile) and
     * allocation-free (early-exit traversal, no result list).
     */
    public boolean insideChannel(double[] pixelPt) {
        final double tileVisitRadius = HydrologyTuning.maxNativeWidth() / 2.0;
        return localRiver.anyInfluencingUnit(pixelPt, tileVisitRadius, HydrologicalUnit::channelContains);
    }
}
