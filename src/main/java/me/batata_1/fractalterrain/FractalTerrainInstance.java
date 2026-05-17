package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.debug.Debug.getLogger;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.debug.Debug.debug;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import me.batata_1.fractalterrain.ml.models.PipelineModels;
import me.batata_1.fractalterrain.ml.pipeline.WorldPipeline;
import me.batata_1.fractalterrain.world.biome.BiomeProvider;
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
    public static volatile CompletableFuture<FractalTerrainInstance> instance = new CompletableFuture<>();

    private final MinecraftServer curServer;
    private final ReliefProvider reliefSource;
    private final BiomeProvider biomeProvider;

    public static final WorldPipeline pipeline;
    static {
        PipelineModels.load();
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        pipeline = new WorldPipeline(0, models);
    }

    private FractalTerrainInstance(MinecraftServer server) {
        this.curServer = server;
        final Path worldPath = server.getSavePath(WorldSavePath.ROOT).normalize();
        this.reliefSource = new ReliefProvider(worldPath + "/fractal_terrain");
        this.biomeProvider = new BiomeProvider( worldPath + "/fractal_terrain");
        final long seed = server
                .getSaveProperties()
                .getGeneratorOptions()
                .getSeed();
        pipeline.setSeed(seed);
        OctaveSimplexNoiseSampler.init(seed);
        LOG.info("fractal terrain instance created");
    }

    public static synchronized void init(MinecraftServer server) {
        if (instance.isDone()) {
            LOG.warn("Already initialized");
            return;
        }
        instance.complete(new FractalTerrainInstance(server));
    }

    public static synchronized void close() {
        if (!instance.isDone()) return;
        getInstance().reliefSource.getStorage().clear();
        getInstance().biomeProvider.getStorage().clear();
        instance = new CompletableFuture<>();
        LOG.info("fractal terrain instance closed");
    }

    public static FractalTerrainInstance getInstance() {
        try {
            return instance.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static ReliefProvider getReliefProvider() {
        return getInstance().reliefSource;
    }

    public static MinecraftServer getServer() {
        return getInstance().curServer;
    }

    public static BiomeProvider getBiomeProvider() {
        return getInstance().biomeProvider;
    }
}
