package me.batata_1.fractal_terrain;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.references.Reference;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractal_terrain.world.gen.chunk.FractalTerrainChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistryView;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.slf4j.Logger;

public class FractalTerrain implements ModInitializer {

    private static final Logger LOG = getLogger(FractalTerrain.class);

    private static void addListenerForDynamic(
            DynamicRegistryView registryView, RegistryKey<? extends Registry<?>> key) {

        registryView.registerEntryAdded(key, (rawId, id, object) -> {
            LOG.info("Loaded entry of {}: {} = {}", key, id, object);
        });
    }
    //TODO: usar FabricLoader.getInstance().isDevelopmentEnvironment();
    @Override
    public void onInitialize() {
        Registry.register(
                Registries.CHUNK_GENERATOR,
                Reference.identifier("chunk_generator"),
                FractalTerrainChunkGenerator.CODEC);
        Registry.register(
                Registries.BIOME_SOURCE, Reference.identifier("biome_source"), FractalTerrainBiomeSource.CODEC);

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        
        ServerWorldEvents.LOAD.register((MinecraftServer server, ServerWorld world) -> {
            final ChunkGenerator chunkGenerator =
                    server.getOverworld().getChunkManager().getChunkGenerator();
            BiomeSource source = server.getOverworld().getChunkManager().getChunkGenerator().getBiomeSource();
            LOG.info("biomas: {} ",source.getBiomes());
            if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
            if (world.getRegistryKey() != World.OVERWORLD) return;
            FractalTerrainInstance.init(server);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register((MinecraftServer server) -> {
            final ChunkGenerator chunkGenerator =
                    server.getOverworld().getChunkManager().getChunkGenerator();
            if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
            FractalTerrainInstance.close();
        });

    }
}
