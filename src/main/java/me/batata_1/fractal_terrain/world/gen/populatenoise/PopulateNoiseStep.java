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
     * Second-pass ELEVATION override. Given the chunk's full set of heightmaps — the raw interpolated
     * relief elevation plus gradients, residual, and biome parameters (continentalness, erosion,
     * temperature, vegetation, weirdness) — produces the final per-block elevation for the chunk,
     * indexed {@code localX * 16 + localZ}.
     *
     * <p>This is where the terrain-shaping tweaks belong: biome-aware shaping, correcting for ocean
     * height, and clamping the lowest point to {@code bottomY}. {@link #settings} carries the sea level
     * and {@code minY} those tweaks need.
     *
     * <p>It also applies the per-pixel hydrology refinement: {@link HydrologyProfileCarver#carve} cuts the
     * bed residual trench below the already tile-carved valley/floodplain shell, and the carve delta
     * ({@code refined − shell}) is recorded into the {@link Types#RIVER_DIFFERENCE} heightmap so the
     * surface painter can place water where the river carved below the shell surface.
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
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                final float shellElev = interpolatedElevs[pos];
                final float refinedElev = chunkUnits.units().length == 0
                        ? shellElev
                        : carver.carvePrefetched(chunkUnits, (startX + dx) / scale, (startZ + dz) / scale, shellElev);
                riverDifference[pos] = refinedElev - shellElev; // trench vs. shell, not vs. original terrain
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
