package me.batata_1.fractal_terrain.world.gen.chunk;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public class FractalTerrainChunkNoiseSampler extends NoiseChunk {

    public FractalTerrainChunkNoiseSampler(
            int horizontalCellCount,
            RandomState noiseConfig,
            int startBlockX,
            int startBlockZ,
            NoiseSettings generationShapeConfig,
            DensityFunctions.BeardifierOrMarker beardifying,
            NoiseGeneratorSettings chunkGeneratorSettings,
            Aquifer.FluidPicker fluidLevelSampler,
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
    public int preliminarySurfaceLevel(int blockX, int blockZ) {
        return 0;
    }
}
