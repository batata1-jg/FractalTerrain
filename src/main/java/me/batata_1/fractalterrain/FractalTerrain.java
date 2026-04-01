package me.batata_1.fractalterrain;

import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;
import me.batata_1.fractalterrain.world.gen.relief.PostProcessingRelief;
import me.batata_1.fractalterrain.world.gen.surfacebuilder.FractalTerrainMaterialRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class FractalTerrain implements ModInitializer {

    @Override
    public void onInitialize() {



        Registry.register(Registries.CHUNK_GENERATOR , Reference.identifier("chunk_generator"), FractalTerrainChunkGenerator.CODEC);
        Registry.register(
                Registries.BIOME_SOURCE, Reference.identifier("biome_source"), FractalTerrainBiomeSource.CODEC);
        FractalTerrainMaterialRules.FractalTerrainMaterialRule.register(Registries.MATERIAL_RULE);
        FractalTerrainMaterialRules.FractalTerrainMaterialCondition.register(Registries.MATERIAL_CONDITION);
        FractalTerrainDensityFunctionTypes.register();

        DynamicRegistries.register(FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS,FractalTerrainChunkGenerator.Settings.CODEC);
        DynamicRegistries.register(FractalTerrainRegistryKeys.POST_PROCESSING_SETTINGS, PostProcessingRelief.Settings.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(FractalTerrainInstance::setServer);

        ServerLifecycleEvents.SERVER_STOPPED.register(FractalTerrainInstance::freeServer);
    }
}
