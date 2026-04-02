package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.MlUtil.initUtil;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;
import me.batata_1.fractalterrain.world.gen.relief.PostProcessingRelief;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

public class FractalTerrainInstance {

    public static volatile MinecraftServer curServer = null;
    public static volatile OrtEnvironment ENV = null;
    private static volatile Path pathMundo = null;
    public static volatile CompletableFuture<PostProcessingRelief> reliefSource = new CompletableFuture<>();

    public static synchronized void setServer(MinecraftServer server,ServerWorld serverWorld) {

        ChunkGenerator chunkGenerator = server.getOverworld().getChunkManager().getChunkGenerator();
        if(chunkGenerator instanceof NoiseChunkGenerator) {
            LOGGER.info(((NoiseChunkGenerator) chunkGenerator).getSettings().getClass().toString());
        }
        if(!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
        LOGGER.info("fractalTerrain initializing");
        if (curServer != null || pathMundo != null || ENV != null || reliefSource.isDone()) {
            LOGGER.warn("Already initialized");
            return;
        }
        curServer = server;
        pathMundo = server.getSavePath(WorldSavePath.ROOT).normalize();
        try (OrtEnvironment.ThreadingOptions opts = new OrtEnvironment.ThreadingOptions()) {
            opts.setGlobalInterOpNumThreads(
                    (Runtime.getRuntime().availableProcessors() >> 2) == 0
                            ? 1
                            : (Runtime.getRuntime().availableProcessors() >> 2));
            ENV = OrtEnvironment.getEnvironment();
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("session providers {}", OrtEnvironment.getAvailableProviders());
        initUtil();

        PostProcessingRelief.Settings post_config = ( chunkGenerator instanceof FractalTerrainChunkGenerator) ? ((FractalTerrainChunkGenerator) chunkGenerator).getSettings().value().postConfig().value() : FractalTerrainDensityFunctionTypes.RefinedElevation.post_config;

        reliefSource.complete(new PostProcessingRelief(post_config));
        LOGGER.info("completed reliefSource");
        long seed = FractalTerrainInstance.getServer()
                .getSaveProperties()
                .getGeneratorOptions()
                .getSeed();
        GaussianNoisePatchProvider.setSeed(seed);
        ContinentalScaleMapProvider.initSamplers(seed);
        Models.initialize();
    }

    public static synchronized void freeServer(MinecraftServer server) {
        ENV = null;
        curServer = null;
        pathMundo = null;
        reliefSource = new CompletableFuture<>();
    }

    public static MinecraftServer getServer() {
        return curServer;
    }

    public static Path getDir() {
        return Path.of(pathMundo + "/fractal_terrain");
    }

}
