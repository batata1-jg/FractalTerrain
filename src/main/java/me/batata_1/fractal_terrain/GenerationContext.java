package me.batata_1.fractal_terrain;

import java.nio.file.Path;
import me.batata_1.fractal_terrain.debug.Infinite3DVisualizer;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfilePainter;
import me.batata_1.fractal_terrain.ml.pipeline.WorldPipeline;
import me.batata_1.fractal_terrain.noise.NoiseSampler;
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
import org.slf4j.Logger;

/**
 * Per-world provider graph, wired in dependency order for one loaded server world.
 *
 * <p>Injectable seam from the singleton-to-DI refactor (M-008); {@link FractalTerrainInstance} holds
 * this behind a future and forwards its old static getters here while callers migrate to holding a
 * context directly.
 *
 * <p>Build order mirrors the dependency graph and must be preserved: {@code global → local → relief
 * → biome}.
 */
public final class GenerationContext {

    private final MinecraftServer server;
    private final ReliefProvider reliefProvider;
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

    GenerationContext(MinecraftServer server, WorldPipeline pipeline, Logger log) {
        this.server = server;
        final Path worldPath = server.getWorldPath(LevelResource.ROOT).normalize();
        // Build order mirrors the dependency graph: global → local → relief → biome.
        this.globalRiverProvider = new GlobalRiverProvider(worldPath + "/fractal_terrain");
        this.localRiverProvider = new LocalRiverProvider(worldPath + "/fractal_terrain");
        this.hydrologyCarver = new HydrologyProfileCarver(this.localRiverProvider);
        this.hydrologyPainter = new HydrologyProfilePainter(this.localRiverProvider);
        this.reliefProvider = new ReliefProvider(worldPath + "/fractal_terrain");
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
        NoiseSampler.init(seed);

        this.surfaceBuilder = new FractalTerrainSurfaceSystem(
                this.noiseConfig,
                Blocks.STONE.defaultBlockState(),
                63,
                this.noiseConfig.random,
                chunkGenerator.getSettings().value().surfaceRule());
        log.info("fractal terrain generation context created");
        this.viz = FractalTerrainConfig.DISABLE_3D_VISUALIZER ? null : new Infinite3DVisualizer();
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ReliefProvider getReliefProvider() {
        return reliefProvider;
    }

    public BiomeProvider getBiomeProvider() {
        return biomeProvider;
    }

    public GlobalRiverProvider getGlobalRiverProvider() {
        return globalRiverProvider;
    }

    public LocalRiverProvider getLocalRiverProvider() {
        return localRiverProvider;
    }

    public HydrologyProfileCarver getHydrologyCarver() {
        return hydrologyCarver;
    }

    public HydrologyProfilePainter getHydrologyPainter() {
        return hydrologyPainter;
    }

    public PopulateNoiseStep getPopulateNoiseStep() {
        return populateNoiseStep;
    }

    public FractalTerrainSurfaceSystem getSurfaceBuilder() {
        return surfaceBuilder;
    }

    public FractalTerrainHeightmapCache getHeightmapCache() {
        return heightmapCache;
    }

    public RandomState getNoiseConfig() {
        return noiseConfig;
    }

    public Infinite3DVisualizer getInfinite3DVisualizer() {
        return viz;
    }

    /** Clears every per-world cache this context owns (used on world unload). */
    void clearCaches() {
        biomeProvider.getInfiniteTensor().clear();
        reliefProvider.getInfiniteTensor().clear();
        globalRiverProvider.getInfiniteTensor().clear();
        localRiverProvider.clearCaches();
        heightmapCache.clear();
    }
}
