package me.batata_1.fractalterrain.registry;

import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractalterrain.world.gen.relief.ReliefProvider;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

public class FractalTerrainRegistryKeys {

    public static final RegistryKey<Registry<ReliefProvider.Settings>> POST_PROCESSING_SETTINGS =
            of("worldgen/relief");
    public static final RegistryKey<Registry<FractalTerrainChunkGenerator.Settings>>
            FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS = of("worldgen/gen_settings");

    private static <T> RegistryKey<Registry<T>> of(String id) {
        return RegistryKey.ofRegistry(Reference.identifier(id));
    }
}
