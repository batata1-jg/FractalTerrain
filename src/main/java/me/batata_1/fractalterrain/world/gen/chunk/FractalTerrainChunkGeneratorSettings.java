package me.batata_1.fractalterrain.world.gen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;

public record FractalTerrainChunkGeneratorSettings(
        FractalTerrainDensityFunctionTypes.RefinedElevation baseTerrain
) {

    public static final Codec<FractalTerrainChunkGeneratorSettings> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    FractalTerrainDensityFunctionTypes.RefinedElevation.CODEC.fieldOf("base_terrain").forGetter(FractalTerrainChunkGeneratorSettings::baseTerrain)
            ).apply(instance,FractalTerrainChunkGeneratorSettings::new)
    );

    public static final Codec<RegistryEntry<FractalTerrainChunkGeneratorSettings>> REGISTRY_CODEC = RegistryElementCodec.of(FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS,CODEC);

}
