package me.batata_1.fractal_terrain;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.references.Reference;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractal_terrain.world.gen.chunk.FractalTerrainChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistryView;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.slf4j.Logger;

public class FractalTerrain implements ModInitializer {

    private static final Logger LOG = getLogger(FractalTerrain.class);

    private static void addListenerForDynamic(
            DynamicRegistryView registryView, ResourceKey<? extends Registry<?>> key) {

        registryView.registerEntryAdded(key, (rawId, id, object) -> {
            LOG.info("Loaded entry of {}: {} = {}", key, id, object);
        });
    }
    // TODO: usar FabricLoader.getInstance().isDevelopmentEnvironment();
    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                Reference.identifier("chunk_generator"),
                FractalTerrainChunkGenerator.CODEC);
        Registry.register(
                BuiltInRegistries.BIOME_SOURCE, Reference.identifier("biome_source"), FractalTerrainBiomeSource.CODEC);

        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        ServerWorldEvents.LOAD.register((MinecraftServer server, ServerLevel world) -> {
            final ChunkGenerator chunkGenerator =
                    server.overworld().getChunkSource().getGenerator();
            BiomeSource source =
                    server.overworld().getChunkSource().getGenerator().getBiomeSource();
            LOG.info("biomas: {} ", source.possibleBiomes());
            if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
            if (world.dimension() != Level.OVERWORLD) return;
            FractalTerrainInstance.init(server);
            if (FractalTerrainConfig.TEST_INSTANCE) FractalTerrainInstance.dumpDebugStages(-1, -1);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register((MinecraftServer server) -> {
            final ChunkGenerator chunkGenerator =
                    server.overworld().getChunkSource().getGenerator();
            if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
            FractalTerrainInstance.close();
        });

        ServerTickEvents.START_SERVER_TICK.register((server) -> {
            if (FractalTerrainConfig.TEST_HEIGHT_MAP) {
                int sz = FractalTerrainInstance.getHeightmapCache().getSize();
                LOG.info("cur cache sz = {}", sz);
            }
        });
    }
}
