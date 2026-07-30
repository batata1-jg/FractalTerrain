package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.slf4j.Logger;

public class PopulateNoiseStep {

    private static final BlockState DEFAULT_ROCK = Blocks.STONE.defaultBlockState();
    private static final Logger LOG = getLogger(PopulateNoiseStep.class);
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private final NoiseGeneratorSettings settings;

    public PopulateNoiseStep(NoiseGeneratorSettings settings) {
        this.settings = settings;
    }

    /**
     * Second-pass ELEVATION override, indexed {@code localX * 16 + localZ}. For every column, reads the
     * tile-carved shell elevation ({@code baseElev}) out of the {@link Types#ELEVATION} heightmap, prefetches
     * the hydrological units that could influence the chunk once, then refines each column with
     * {@link HydrologyProfileCarver#carvePrefetched} to get {@code refinedElev} — the per-pixel bed-residual
     * trench cut into the shell. The delta ({@code refinedElev - baseElev}) is written to
     * {@link Types#RIVER_DIFFERENCE}, and {@link Types#ELEVATION} is overwritten with
     * {@code Math.max(bottom, refinedElev) + seaLevel - 1}.
     */
    public void updateToFinalElev(ChunkPos chunkPos, FractalTerrainHeightmap heightmap) {
        final int seaLevel = settings.seaLevel();
        final int bottom = settings.noiseSettings().minY();
        final float[] interpolatedElevs = heightmap.get(Types.ELEVATION);
        final float[] riverDifference = heightmap.get(Types.RIVER_DIFFERENCE);
        final HydrologyProfileCarver carver = FractalTerrainInstance.getHydrologyCarver();
        final int startX = chunkPos.getMinBlockX();
        final int startZ = chunkPos.getMinBlockZ();
        // One influence query serves the whole chunk: prefetch every unit that could reach any of the
        // 256 columns (chunk center + half-diagonal, both in the relief-pixel frame), then run the
        // flat merge per block against the prefetched array — 1 tree query per chunk instead of 256.
        final double scale = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final double chunkCenterPixelX = (startX + 8) / scale;
        final double chunkCenterPixelZ = (startZ + 8) / scale;
        final double chunkRadiusPx = (8.0 * Math.sqrt(2.0)) / scale;
        final HydrologyProfileCarver.PrefetchedUnits chunkUnits =
                carver.prefetchChunk(chunkCenterPixelX, chunkCenterPixelZ, chunkRadiusPx);
        final double[] mutablePt = new double[2];
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                final float baseElev = interpolatedElevs[pos];
                mutablePt[0] = (startX + dx) / scale;
                mutablePt[1] = (startZ + dz) / scale;
                final float refinedElev = chunkUnits.units().length == 0
                        ? baseElev
                        : carver.carvePrefetched(chunkUnits, mutablePt, baseElev);
                riverDifference[pos] = refinedElev - baseElev; // trench vs. shell, not vs. original terrain
                interpolatedElevs[pos] = Math.max(bottom, refinedElev) + seaLevel - 1;
            }
        }
    }

    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }
}
