package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import java.util.Arrays;
import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;
import me.batata_1.fractalterrain.world.gen.relief.ReliefProvider;
import me.batata_1.fractalterrain.world.gen.surfacebuilder.FractalTerrainMaterialRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
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
        DynamicRegistries.register(
                FractalTerrainRegistryKeys.POST_PROCESSING_SETTINGS, ReliefProvider.Settings.CODEC);
        Registry.register(
                Registries.CHUNK_GENERATOR,
                Reference.identifier("chunk_generator"),
                FractalTerrainChunkGenerator.CODEC);
        Registry.register(
                Registries.BIOME_SOURCE, Reference.identifier("biome_source"), FractalTerrainBiomeSource.CODEC);
        FractalTerrainMaterialRules.FractalTerrainMaterialRule.register(Registries.MATERIAL_RULE);
        FractalTerrainMaterialRules.FractalTerrainMaterialCondition.register(Registries.MATERIAL_CONDITION);
        FractalTerrainDensityFunctionTypes.register();

        LOGGER.info(Arrays.toString(DynamicRegistries.getDynamicRegistries().toArray()));

        DynamicRegistrySetupCallback.EVENT.register(registryView -> {
            LOGGER.info("isso rodaaa????????????????????????????????????????????????????????");
            addListenerForDynamic(registryView, FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS);
            addListenerForDynamic(registryView, FractalTerrainRegistryKeys.POST_PROCESSING_SETTINGS);
            LOGGER.info("ele so n registra?");
        });

        ServerWorldEvents.LOAD.register(FractalTerrainInstance::setServer);

        ServerLifecycleEvents.SERVER_STOPPED.register(FractalTerrainInstance::freeServer);
    }
}
