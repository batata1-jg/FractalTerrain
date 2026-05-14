package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import me.batata_1.fractalterrain.ml.models.ModelAssetManager;
import me.batata_1.fractalterrain.ml.models.PipelineModels;
import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.DynamicRegistryView;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

public class FractalTerrain implements ModInitializer {

    private static void addListenerForDynamic(
            DynamicRegistryView registryView, RegistryKey<? extends Registry<?>> key) {

        registryView.registerEntryAdded(key, (rawId, id, object) -> {
            LOGGER.info("Loaded entry of {}: {} = {}", key, id, object);
        });
    }

    @Override
    public void onInitialize() {
        DynamicRegistries.register(
                FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS,
                FractalTerrainChunkGenerator.Settings.CODEC);
        Registry.register(
                Registries.CHUNK_GENERATOR,
                Reference.identifier("chunk_generator"),
                FractalTerrainChunkGenerator.CODEC);
        Registry.register(
                Registries.BIOME_SOURCE, Reference.identifier("biome_source"), FractalTerrainBiomeSource.CODEC);


//        DynamicRegistrySetupCallback.EVENT.register(registryView -> {
//            LOGGER.info("isso rodaaa????????????????????????????????????????????????????????");
//            addListenerForDynamic(registryView, FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS);
//            addListenerForDynamic(registryView, FractalTerrainRegistryKeys.POST_PROCESSING_SETTINGS);
//            LOGGER.info("ele so n registra?");
//        });

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();

       // ServerLifecycleEvents.SERVER_STARTING.register(server -> ReliefProvider.clearStorage());

        ServerWorldEvents.LOAD.register(FractalTerrainInstance::setServer);

        ServerLifecycleEvents.SERVER_STOPPED.register(FractalTerrainInstance::freeServer);
    }
}
