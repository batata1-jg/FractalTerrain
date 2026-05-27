package me.batata_1.fractal_terrain.world.gen.chunk;

import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;

public class FractalTerrainChunkNoiseSampler extends ChunkNoiseSampler {

    public FractalTerrainChunkNoiseSampler(
            int horizontalCellCount,
            NoiseConfig noiseConfig,
            int startBlockX,
            int startBlockZ,
            GenerationShapeConfig generationShapeConfig,
            DensityFunctionTypes.Beardifying beardifying,
            ChunkGeneratorSettings chunkGeneratorSettings,
            AquiferSampler.FluidLevelSampler fluidLevelSampler,
            Blender blender) {
        super(
                horizontalCellCount,
                noiseConfig,
                startBlockX,
                startBlockZ,
                generationShapeConfig,
                beardifying,
                chunkGeneratorSettings,
                fluidLevelSampler,
                blender);
    }

    @Override
    public int estimateSurfaceHeight(int blockX, int blockZ) {
        return 0;
    }
}
