package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

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
     * <p>STUB: currently a pass-through that returns the raw interpolated ELEVATION unchanged.
     */
    public void updateToFinalElev(ChunkPos chunkPos, FractalTerrainHeightmap heightmap) {
        final int seaLevel = settings.seaLevel();
        final int bottom = settings.noiseSettings().minY();
        final float[] interpolatedElevs = heightmap.get(Types.ELEVATION);
        for(int pos=0 ; pos<16 ; pos++) {
            final float interpolatedElev = interpolatedElevs[pos];

            interpolatedElevs[pos] = Math.max(bottom,interpolatedElev) + seaLevel;
        }
    }

    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }
}
