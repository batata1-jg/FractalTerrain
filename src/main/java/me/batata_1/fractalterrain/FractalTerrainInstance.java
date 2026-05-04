package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.Debug.debug;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipeline;
import me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractalterrain.world.gen.relief.ReliefProvider;
import me.batata_1.fractalterrain.world.noise.OctaveSimplexNoiseSampler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class FractalTerrainInstance {

    public static volatile MinecraftServer curServer = null;
    private static volatile Path pathMundo = null;
    public static final WorldPipeline pipeline;
    static {
        PipelineModels.load();
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        pipeline = new WorldPipeline(0, models);
    }
    public static volatile CompletableFuture<ReliefProvider> reliefSource = new CompletableFuture<>();
    public FractalTerrainInstance INSTANCE;


    public static synchronized void setServer(MinecraftServer server, ServerWorld world) {
        final ChunkGenerator chunkGenerator =
                server.getOverworld().getChunkManager().getChunkGenerator();
//        if (chunkGenerator instanceof NoiseChunkGenerator) {
//            LOGGER.info(((NoiseChunkGenerator) chunkGenerator)
//                    .getSettings()
//                    .getClass()
//                    .toString());
//        }
        if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
        if (world.getRegistryKey() != World.OVERWORLD) return;
        if (curServer != null || pathMundo != null || reliefSource.isDone()) {
            LOGGER.warn("Already initialized");
            return;
        }
        LOGGER.info("fractalTerrain initializing");
        curServer = server;
        pathMundo = server.getSavePath(WorldSavePath.ROOT).normalize();
        reliefSource.complete(new ReliefProvider(pathMundo + "/fractal_terrain"));
        LOGGER.info("completed reliefSource");
        final long seed = FractalTerrainInstance.getServer()
                .getSaveProperties()
                .getGeneratorOptions()
                .getSeed();
        GaussianNoisePatchProvider.setSeed(seed);
        pipeline.setSeed(seed);
        ContinentalScaleMapProvider.initSamplers(seed);
        OctaveSimplexNoiseSampler.init(seed);
        debug();
        LOGGER.info("init set size: {}", OctaveSimplexNoiseSampler.getInitSetSize());
    }

    public static synchronized void freeServer(MinecraftServer server) {
        curServer = null;
        pathMundo = null;
        try {
            reliefSource.get().getStorage().clear();
        } catch (InterruptedException | ExecutionException ignored) {
        } finally {
            reliefSource = new CompletableFuture<>();
        }
    }

    public static MinecraftServer getServer() {
        return curServer;
    }

    public static Path getDir() {
        return Path.of(pathMundo + "/fractal_terrain");
    }

}
