package me.batata_1.fractal_terrain;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

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
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmapCache;
import me.batata_1.fractal_terrain.world.biome.BiomeProvider;
import me.batata_1.fractal_terrain.world.gen.populatenoise.PopulateNoiseStep;
import me.batata_1.fractal_terrain.world.gen.surfacebuilder.FractalTerrainSurfaceSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

/**
 * Thin static adapter over the current {@link GenerationContext} (keystone M-008).
 *
 * <p>The per-world provider graph now lives in {@link GenerationContext}; this class holds the current one
 * behind a {@code volatile CompletableFuture} and forwards its historical static getters to it, so the ~46
 * existing {@code FractalTerrainInstance.getX()} reach-throughs keep working unchanged while callers are
 * migrated package-by-package to hold an injected {@link GenerationContext} directly. The shared
 * {@link WorldPipeline} (loaded once per JVM) stays here since it outlives individual worlds.
 */
public class FractalTerrainInstance {

    private static final Logger LOG = getLogger(FractalTerrainInstance.class);
    public static volatile WorldPipeline pipeline;

    /**
     * The current world's context. {@code volatile}, and completed with a fully-constructed
     * {@link GenerationContext}; {@code get()} gives readers a happens-before edge with {@code complete()},
     * so a worker never observes a partially-built or null context. Reset to a fresh incomplete future on
     * {@link #close()} so a subsequent load re-publishes cleanly.
     */
    private static volatile CompletableFuture<GenerationContext> context = new CompletableFuture<>();

    /**
     * Loads the terrain-diffusion models and constructs the shared {@link WorldPipeline}, if not already
     * done. Safe to call repeatedly. Unlike the class-init-time loading this replaces, a load failure
     * (e.g. a missing datapack) surfaces here as a normal exception instead of an
     * {@code ExceptionInInitializerError} that would permanently poison this class.
     */
    public static synchronized void initPipeline() {
        if (pipeline != null) return;
        PipelineModels.load();
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        pipeline = new WorldPipeline(0, models);
    }

    public static synchronized void init(MinecraftServer server) {
        if (exists()) {
            LOG.warn("Already initialized");
            return;
        }
        initPipeline();
        context.complete(new GenerationContext(server, pipeline, LOG));
    }

    public static synchronized void close() {
        if (!exists()) return;
        current().clearCaches();
        context = new CompletableFuture<>();
        LOG.info("fractal terrain instance closed");
    }

    /** The current world's context, blocking until {@link #init} has published it. */
    private static GenerationContext current() {
        try {
            return context.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean exists() {
        return context.isDone();
    }

    public static PopulateNoiseStep getPopulateNoiseStep() {
        return current().getPopulateNoiseStep();
    }

    public static ReliefProvider getReliefProvider() {
        return current().getReliefProvider();
    }

    public static MinecraftServer getServer() {
        return current().getServer();
    }

    public static BiomeProvider getBiomeProvider() {
        return current().getBiomeProvider();
    }

    public static FractalTerrainSurfaceSystem getSurfaceBuilder() {
        return current().getSurfaceBuilder();
    }

    public static FractalTerrainHeightmapCache getHeightmapCache() {
        return current().getHeightmapCache();
    }

    public static RandomState getNoiseConfig() {
        return current().getNoiseConfig();
    }

    public static GlobalRiverProvider getGlobalRiverProvider() {
        return current().getGlobalRiverProvider();
    }

    public static LocalRiverProvider getLocalRiverProvider() {
        return current().getLocalRiverProvider();
    }

    public static HydrologyProfileCarver getHydrologyCarver() {
        return current().getHydrologyCarver();
    }

    public static HydrologyProfilePainter getHydrologyPainter() {
        return current().getHydrologyPainter();
    }

    /**
     * Debug aid: recompute {@code (tileX, tileZ)} through every provider that exposes {@code debugStages}
     * (global river, local river, relief) and dump a PNG of each intermediate stage under
     * {@code <DEFAULT_DEBUG_PATH>/instance/}, one subdirectory per provider. The tile pair is forwarded to
     * each provider in its own tile grid (see {@link InstanceStageDumper}).
     */
    @TestOnly
    public static void dumpDebugStages(int tileX, int tileZ) {
        final GenerationContext ctx = current();
        final String root = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/instance";
        LOG.info("dumping instance debug stages for tile ({},{}) to {}", tileX, tileZ, root);
        InstanceStageDumper.dump(
                root, tileX, tileZ, ctx.getGlobalRiverProvider(), ctx.getLocalRiverProvider(), ctx.getReliefProvider());
        LOG.info("instance debug stages dumped to {}", root);
    }

    @TestOnly
    public static Infinite3DVisualizer getInfinite3DVisualizer() {
        return current().getInfinite3DVisualizer();
    }
}
