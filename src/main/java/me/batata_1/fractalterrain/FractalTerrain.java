package me.batata_1.fractalterrain;

import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;
import me.batata_1.fractalterrain.world.gen.surfacebuilder.FractalTerrainMaterialRules;
import me.batata_1.fractalterrain.world.gen.surfacebuilder.FractalTerrainSurfaceRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.registry.Registries;

import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;


public class FractalTerrain implements ModInitializer {

    @Override
    public void onInitialize() {

        Registry.register(Registries.BIOME_SOURCE, Reference.identifier("biome_source"), FractalTerrainBiomeSource.CODEC);
        FractalTerrainMaterialRules.FractalTerrainMaterialRule.register(Registries.MATERIAL_RULE);
        FractalTerrainMaterialRules.FractalTerrainMaterialCondition.register(Registries.MATERIAL_CONDITION);
        FractalTerrainDensityFunctionTypes.register();

        ServerLifecycleEvents.SERVER_STARTING.register(FractalTerrainInstance::setServer);

        ServerLifecycleEvents.SERVER_STOPPED.register(FractalTerrainInstance::freeServer);

    }
}
