package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.Debug.debug;


import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.github.xandergos.terraindiffusionmc.pipeline.ModelAssetManager;
import com.github.xandergos.terraindiffusionmc.pipeline.PipelineModels;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipeline;

//import me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import me.batata_1.fractalterrain.world.gen.chunk.FractalTerrainChunkGenerator;

import me.batata_1.fractalterrain.world.gen.relief.ReliefProvider;
import me.batata_1.fractalterrain.world.noise.OctaveSimplexNoiseSampler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

public class FractalTerrainInstance {

    public static final WorldPipeline pipeline;
    static {
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        PipelineModels models = PipelineModels.getInstance();
        if( models == null) throw new IllegalStateException("models not loaded");
        pipeline = new WorldPipeline(0,models);
    }

    public static volatile FractalTerrainInstance INSTANCE;

    private final MinecraftServer server;
    private final Path pathMundo;
    public final CompletableFuture<ReliefProvider> reliefSource = new CompletableFuture<>();

    private FractalTerrainInstance(MinecraftServer server) {
        pathMundo = Path.of(server.getSavePath(WorldSavePath.ROOT).normalize() + "/fractal_terrain");
        this.server = server;
        LOGGER.info("completed reliefSource");
        final long seed = FractalTerrainInstance.getServer()
                .getSaveProperties()
                .getGeneratorOptions()
                .getSeed();
        pipeline.setSeed(seed);
        reliefSource.complete(new ReliefProvider(pathMundo));
//        GaussianNoisePatchProvider.setSeed(seed);
        ContinentalScaleMapProvider.initSamplers(seed);
        OctaveSimplexNoiseSampler.init(seed);
        debug();
        LOGGER.info("init set size: {}", OctaveSimplexNoiseSampler.getInitSetSize());
    }

    public static synchronized void setServer(MinecraftServer server, ServerWorld world) {
        final ChunkGenerator chunkGenerator =
                server.getOverworld().getChunkManager().getChunkGenerator();
        if (!(chunkGenerator instanceof FractalTerrainChunkGenerator)) return;
        if (world.getRegistryKey() != World.OVERWORLD) return;
        LOGGER.info("fractalTerrain initializing");
        if(INSTANCE != null) return;
        INSTANCE = new FractalTerrainInstance(server);
    }

    public static void freeServer(MinecraftServer minecraftServer) {
        try {
            INSTANCE.reliefSource.get().getStorage().close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static MinecraftServer getServer() {
        return INSTANCE.server;
    }
}
