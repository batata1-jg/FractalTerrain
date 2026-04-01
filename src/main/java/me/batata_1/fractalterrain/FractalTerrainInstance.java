package me.batata_1.fractalterrain;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.MlUtil.initUtil;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import java.nio.file.Path;

import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import me.batata_1.fractalterrain.world.gen.densityfunction.FractalTerrainDensityFunctionTypes;
import me.batata_1.fractalterrain.world.gen.relief.PostProcessingRelief;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

public class FractalTerrainInstance {

    public static volatile MinecraftServer curServer = null;
    public static volatile OrtEnvironment ENV = null;
    private static volatile Path pathMundo = null;
    public static volatile PostProcessingRelief post = null;

    public static synchronized void setServer(MinecraftServer server) {
        if (FractalTerrainDensityFunctionTypes.InterpolatedFromMap.isActive()) {
            if (curServer != null || pathMundo != null || ENV != null || post != null)
                throw new IllegalStateException("aready initialized");
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
            post = new PostProcessingRelief(FractalTerrainDensityFunctionTypes.RefinedElevation.setting);
            long seed = FractalTerrainInstance.getServer()
                    .getSaveProperties()
                    .getGeneratorOptions()
                    .getSeed();
            GaussianNoisePatchProvider.setSeed(seed);
            ContinentalScaleMapProvider.initSamplers(seed);
            Models.initialize();
        }
    }

    public static synchronized void freeServer(MinecraftServer server) {
        ENV = null;
        curServer = null;
        pathMundo = null;
        post = null;
    }

    public static MinecraftServer getServer() {
        return curServer;
    }

    public static Path getDir() {
        return Path.of(pathMundo + "/fractal_terrain");
    }
}
