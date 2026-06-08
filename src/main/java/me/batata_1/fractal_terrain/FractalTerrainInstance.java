package me.batata_1.fractal_terrain;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import me.batata_1.fractal_terrain.hydrology.RiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.storage.TensorStorage;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.ml.pipeline.WorldPipeline;
import me.batata_1.fractal_terrain.noise.OctaveSimplexNoiseSampler;
import me.batata_1.fractal_terrain.terrablender.InitTerrablender;
import me.batata_1.fractal_terrain.world.biome.BiomeProvider;
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import me.batata_1.fractal_terrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractal_terrain.world.gen.surfacebuilder.FractalTerrainSurfaceBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.TestOnly;
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
    private final RiverProvider riverProvider;
    private final FractalTerrainSurfaceBuilder surfaceBuilder;
    private final NoiseConfig noiseConfig;

    private FractalTerrainInstance(MinecraftServer server) {
        this.curServer = server;
        final Path worldPath = server.getSavePath(WorldSavePath.ROOT).normalize();
        this.reliefSource = new ReliefProvider(worldPath + "/fractal_terrain");
        this.biomeProvider = new BiomeProvider(worldPath + "/fractal_terrain");
        this.riverProvider = new RiverProvider();
        final long seed = server.getSaveProperties().getGeneratorOptions().getSeed();
        final ServerWorld world = server.getOverworld();
        final DynamicRegistryManager dynamicRegistryManager = world.getRegistryManager();
        final FractalTerrainChunkGenerator chunkGenerator =
                (FractalTerrainChunkGenerator) world.getChunkManager().getChunkGenerator();
        this.noiseConfig = NoiseConfig.create(
                chunkGenerator.getSettings().value(),
                dynamicRegistryManager.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS),
                seed);
        pipeline.updateInstance(seed, worldPath + "/fractal_terrain");
        OctaveSimplexNoiseSampler.init(seed);
        // LOG.info("chunk Generator settings: {}", chunkGenerator.getSettings().value());
        DynamicRegistryManager registryAccess = server.getRegistryManager();
        InitTerrablender.initializeBlenderBiomes(
                registryAccess,
                DimensionOptions.OVERWORLD,
                chunkGenerator.getSettings().value(),
                chunkGenerator.getBiomeSource(),
                seed);
        surfaceBuilder = new FractalTerrainSurfaceBuilder(
                this.noiseConfig,
                Blocks.STONE.getDefaultState(),
                63,
                this.noiseConfig.randomDeriver,
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

    public static NoiseConfig getNoiseConfig() {
        return getInstance().noiseConfig;
    }

    @TestOnly
    public static TensorStorage getDecoderStorage() {
        return pipeline.getDecoder().getStorage();
    }

}
