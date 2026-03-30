package me.batata_1.fractalterrain.registry;

import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGeneratorSettings;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class FractalTerrainRegistryKeys extends RegistryKeys {

    public static final RegistryKey<Registry<FractalTerrainChunkGeneratorSettings>> FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS = of("worldgen/frac_chunk_gen_settings");

    private static <T> RegistryKey<Registry<T>> of(String id) {
        return RegistryKey.ofRegistry(new Identifier(id));
    }
}
