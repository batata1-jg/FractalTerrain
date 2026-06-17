package me.batata_1.fractal_terrain;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.ml.pipeline.WorldPipeline;
import me.batata_1.fractal_terrain.noise.OctaveSimplexNoiseSampler;
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import me.batata_1.fractal_terrain.world.biome.BiomeProvider;
import me.batata_1.fractal_terrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractal_terrain.world.gen.surfacebuilder.FractalTerrainSurfaceBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public class FractalTerrainInstance {

    private static final Logger LOG = getLogger(FractalTerrainInstance.class);
    public static final WorldPipeline pipeline;

    static {
        PipelineModels.load();
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        pipeline = new WorldPipeline(0, models);
    }

    private static volatile CompletableFuture<FractalTerrainInstance> instance = new CompletableFuture<>();

    private final MinecraftServer curServer;
    private final ReliefProvider reliefSource;
    private final BiomeProvider biomeProvider;
    private final GlobalRiverProvider globalRiverProvider;
    private final LocalRiverProvider localRiverProvider;
    private final FractalTerrainSurfaceBuilder surfaceBuilder;
    private final RandomState noiseConfig;

    private FractalTerrainInstance(MinecraftServer server) {
        this.curServer = server;
        final Path worldPath = server.getWorldPath(LevelResource.ROOT).normalize();
        this.reliefSource = new ReliefProvider(worldPath + "/fractal_terrain");
        this.biomeProvider = new BiomeProvider(worldPath + "/fractal_terrain");
        this.globalRiverProvider = new GlobalRiverProvider(worldPath + "/fractal_terrain");
        this.localRiverProvider = new LocalRiverProvider(null);
        final long seed = server.getWorldData().worldGenOptions().seed();
        final ServerLevel world = server.overworld();
        final RegistryAccess dynamicRegistryManager = world.registryAccess();
        final FractalTerrainChunkGenerator chunkGenerator =
                (FractalTerrainChunkGenerator) world.getChunkSource().getGenerator();
        this.noiseConfig = RandomState.create(
                chunkGenerator.getSettings().value(), dynamicRegistryManager.lookupOrThrow(Registries.NOISE), seed);
        pipeline.updateInstance(seed, worldPath + "/fractal_terrain");
        OctaveSimplexNoiseSampler.init(seed);
        // LOG.info("chunk Generator settings: {}", chunkGenerator.getSettings().value());
        RegistryAccess registryAccess = server.registryAccess();
        surfaceBuilder = new FractalTerrainSurfaceBuilder(
                this.noiseConfig,
                Blocks.STONE.defaultBlockState(),
                63,
                this.noiseConfig.random,
                chunkGenerator.getSettings().value().surfaceRule());

        LOG.info("fractal terrain instance created");
    }

    public static synchronized void init(MinecraftServer server) {
        if (exists()) {
            LOG.warn("Already initialized");
            return;
        }
        instance.complete(new FractalTerrainInstance(server));
    }

    public static synchronized void close() {
        if (!exists()) return;
        getInstance().biomeProvider.getInfiniteTensor().clear();
        getInstance().reliefSource.getInfiniteTensor().clear();
        getInstance().globalRiverProvider.getInfiniteTensor().clear();
        getInstance().localRiverProvider.getInfiniteTensor().clear();
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

    public static FractalTerrainSurfaceBuilder getSurfaceBuilder() {
        return getInstance().surfaceBuilder;
    }

    public static boolean exists() {
        return instance.isDone();
    }

    public static RandomState getNoiseConfig() {
        return getInstance().noiseConfig;
    }

    public static GlobalRiverProvider getGlobalRiverProvider() {
        return getInstance().globalRiverProvider;
    }

    public static LocalRiverProvider getLocalRiverProvider() {
        return getInstance().localRiverProvider;
    }
}
