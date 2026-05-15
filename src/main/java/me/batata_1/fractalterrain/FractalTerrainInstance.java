package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.debug.Debug.getLogger;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.debug.Debug.debug;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import me.batata_1.fractalterrain.ml.models.PipelineModels;
import me.batata_1.fractalterrain.ml.pipeline.WorldPipeline;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractalterrain.world.gen.ReliefProvider;
import me.batata_1.fractalterrain.noise.OctaveSimplexNoiseSampler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.slf4j.Logger;

public class FractalTerrainInstance {

    private static final Logger LOG = getLogger(FractalTerrainInstance.class);
    public static volatile FractalTerrainInstance instance = null;

    private final MinecraftServer curServer;
    private final Path worldPath;
    public static final WorldPipeline pipeline;
    static {
        PipelineModels.load();
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        pipeline = new WorldPipeline(0, models);
    }
    public final CompletableFuture<ReliefProvider> reliefSource = new CompletableFuture<>();

    private FractalTerrainInstance(MinecraftServer server) {
        this.curServer = server;
        this.worldPath = server.getSavePath(WorldSavePath.ROOT).normalize();
        reliefSource.complete(new ReliefProvider(worldPath + "/fractal_terrain"));
        final long seed = server
                .getSaveProperties()
                .getGeneratorOptions()
                .getSeed();
        pipeline.setSeed(seed);
        OctaveSimplexNoiseSampler.init(seed);
        LOG.info("fractal terrain instance created");
    }

    public static synchronized void init(MinecraftServer server) {
        if (instance != null) {
            LOG.warn("Already initialized");
            return;
        }
        instance = new FractalTerrainInstance(server);
    }



//    public static synchronized void setServer(MinecraftServer server, ServerWorld world) {
//        final ChunkGenerator chunkGenerator =
//                server.getOverworld().getChunkManager().getChunkGenerator();
//        if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
//        if (world.getRegistryKey() != World.OVERWORLD) return;
//        if (curServer != null || worldPath != null || reliefSource.isDone()) {
//            LOGGER.warn("Already initialized");
//            return;
//        }
//        LOGGER.info("fractalTerrain initializing");
//        curServer = server;
//        worldPath = server.getSavePath(WorldSavePath.ROOT).normalize();
//        reliefSource.complete(new ReliefProvider(worldPath + "/fractal_terrain"));
//        LOGGER.info("completed reliefSource");
//        final long seed = FractalTerrainInstance.getServer()
//                .getSaveProperties()
//                .getGeneratorOptions()
//                .getSeed();
//        pipeline.setSeed(seed);
//        OctaveSimplexNoiseSampler.init(seed);
//        debug();
//        LOGGER.info("init set size: {}", OctaveSimplexNoiseSampler.getInitSetSize());
//    }
//
//    public static synchronized void freeServer(MinecraftServer server) {
//        curServer = null;
//        worldPath = null;
//        try {
//            reliefSource.get().getStorage().clear();
//        } catch (InterruptedException | ExecutionException ignored) {
//        } finally {
//            reliefSource = new CompletableFuture<>();
//        }
//    }

    public static synchronized void close() {
        if (instance == null) return;
        try {
            instance.reliefSource.get().getStorage().clear();
        } catch (InterruptedException | ExecutionException ignored) {
        }
        instance = null;
        LOG.info("fractal terrain instance closed");
    }

    public static ReliefProvider getReliefProvider() {
        try {
            return instance.reliefSource.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static MinecraftServer getServer() {
        return instance.curServer;
    }


}
