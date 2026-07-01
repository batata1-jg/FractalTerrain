package me.batata_1.fractal_terrain;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.batata_1.fractal_terrain.debug.Infinite3DVisualizer;
import me.batata_1.fractal_terrain.debug.InstanceStageDumper;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfilePainter;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.ml.pipeline.WorldPipeline;
import me.batata_1.fractal_terrain.noise.OctaveSimplexNoiseSampler;
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmapCache;
import me.batata_1.fractal_terrain.world.biome.BiomeProvider;
import me.batata_1.fractal_terrain.world.gen.chunk.FractalTerrainChunkGenerator;
import me.batata_1.fractal_terrain.world.gen.populatenoise.PopulateNoiseStep;
import me.batata_1.fractal_terrain.world.gen.surfacebuilder.FractalTerrainSurfaceSystem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
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
    private final GlobalRiverProvider globalRiverProvider;
    private final LocalRiverProvider localRiverProvider;
    private final HydrologyProfileCarver hydrologyCarver;
    private final HydrologyProfilePainter hydrologyPainter;
    private final PopulateNoiseStep populateNoiseStep;
    private final FractalTerrainSurfaceSystem surfaceBuilder;
    private final FractalTerrainHeightmapCache heightmapCache;
    private final RandomState noiseConfig;
    private final Infinite3DVisualizer viz;

    private FractalTerrainInstance(MinecraftServer server) {
        this.curServer = server;
        final Path worldPath = server.getWorldPath(LevelResource.ROOT).normalize();
        // Build order mirrors the dependency graph: global → local → relief → biome.
        this.globalRiverProvider = new GlobalRiverProvider(worldPath + "/fractal_terrain");
        this.localRiverProvider = new LocalRiverProvider(worldPath + "/fractal_terrain");
        this.hydrologyCarver = new HydrologyProfileCarver(this.localRiverProvider);
        this.hydrologyPainter = new HydrologyProfilePainter(this.localRiverProvider);
        this.reliefSource = new ReliefProvider(worldPath + "/fractal_terrain");
        this.biomeProvider = new BiomeProvider(worldPath + "/fractal_terrain");
        final long seed = server.getWorldData().worldGenOptions().seed();
        final ServerLevel world = server.overworld();
        final RegistryAccess dynamicRegistryManager = world.registryAccess();
        final FractalTerrainChunkGenerator chunkGenerator =
                (FractalTerrainChunkGenerator) world.getChunkSource().getGenerator();
        this.populateNoiseStep =
                new PopulateNoiseStep(chunkGenerator.getSettings().value());
        this.heightmapCache = new FractalTerrainHeightmapCache(world.getChunkSource());
        this.noiseConfig = RandomState.create(
                chunkGenerator.getSettings().value(), dynamicRegistryManager.lookupOrThrow(Registries.NOISE), seed);
        pipeline.updateInstance(seed, worldPath + "/fractal_terrain");
        OctaveSimplexNoiseSampler.init(seed);
        // LOG.info("chunk Generator settings: {}", chunkGenerator.getSettings().value());

        RegistryAccess registryAccess = server.registryAccess();
        surfaceBuilder = new FractalTerrainSurfaceSystem(
                this.noiseConfig,
                Blocks.STONE.defaultBlockState(),
                63,
                this.noiseConfig.random,
                chunkGenerator.getSettings().value().surfaceRule());
        LOG.info("fractal terrain instance created");
        if (FractalTerrainConfig.DISABLE_3D_VISUALIZER) viz = null;
        else viz = new Infinite3DVisualizer();
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
        getInstance().localRiverProvider.clearCaches();
        getInstance().heightmapCache.clear();
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

    public static PopulateNoiseStep getPopulateNoiseStep() {
        return getInstance().populateNoiseStep;
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

    public static FractalTerrainSurfaceSystem getSurfaceBuilder() {
        return getInstance().surfaceBuilder;
    }

    public static FractalTerrainHeightmapCache getHeightmapCache() {
        return getInstance().heightmapCache;
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

    public static HydrologyProfileCarver getHydrologyCarver() {
        return getInstance().hydrologyCarver;
    }

    public static HydrologyProfilePainter getHydrologyPainter() {
        return getInstance().hydrologyPainter;
    }

    /**
     * Debug aid: recompute {@code (tileX, tileZ)} through every provider that exposes {@code debugStages}
     * (global river, local river, relief) and dump a PNG of each intermediate stage under
     * {@code <DEFAULT_DEBUG_PATH>/instance/}, one subdirectory per provider. The tile pair is forwarded to
     * each provider in its own tile grid (see {@link InstanceStageDumper}).
     */
    @TestOnly
    public static void dumpDebugStages(int tileX, int tileZ) {
        final FractalTerrainInstance inst = getInstance();
        final String root = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/instance";
        LOG.info("dumping instance debug stages for tile ({},{}) to {}", tileX, tileZ, root);
        InstanceStageDumper.dump(
                root, tileX, tileZ, inst.globalRiverProvider, inst.localRiverProvider, inst.reliefSource);
        LOG.info("instance debug stages dumped to {}", root);
    }

    @TestOnly
    public static Infinite3DVisualizer getInfinite3DVisualizer() {
        return getInstance().viz;
    }
}
