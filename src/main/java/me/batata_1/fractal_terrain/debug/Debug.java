package me.batata_1.fractal_terrain.debug;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
// import me.batata_1.fractalterrain.ml.tensorProviders.MapProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.noise.NoiseSampler;
import me.batata_1.fractal_terrain.storage.Persistable;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Debug {

    public static final Logger DEBUG_LOGGER = getLogger(Debug.class);
    public static final TensorVisualizer tensor = new TensorVisualizer();
    public static final RiverNetworkVisualizer river =
            new RiverNetworkVisualizer(FractalTerrainConfig.DEFAULT_DEBUG_PATH);
    public static final SplineVisualizer spline = new SplineVisualizer(FractalTerrainConfig.DEFAULT_DEBUG_PATH);

    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger("fractal_terrain/" + clazz.toString());
    }

    public static void isNan(double[] t) {
        for (double v : t)
            if (Double.isNaN(v)) throw new RuntimeException("this double[] is nan " + Arrays.toString(t));
    }

    public static void seeNoise(NoiseSampler sampler, String name, int x, int z, int size) throws IOException {

        Path path = FractalTerrainInstance.getServer()
                .getWorldPath(LevelResource.ROOT)
                .normalize();
        sampler.initSampler(FractalTerrainInstance.getServer().overworld().getSeed());
        File outputFile = new File(path + "/" + name + ".png");
        DEBUG_LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                max = Math.max(max, sampler.sample(i, j));
                min = Math.min(min, sampler.sample(i, j));
            }
        DEBUG_LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, name);
        final float eps = 1e-5F;
        int[] arr = new int[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {

                float vi = (sampler.sample(i, j) - min) / (max - min + eps);
                int v = (int) (255F * vi);

                arr[(int) (j + i * size)] = v;
            }
        }
        BufferedImage outputImage = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = outputImage.getRaster();
        raster.setSamples(0, 0, size, size, 0, arr);
        ImageIO.write(outputImage, "png", outputFile);
        System.out.println("end");
    }

    public static void seeTile(FloatTensor tile, int x, int z, String name) {
        Path basePath = Path.of(FractalTerrainConfig.DEFAULT_DEBUG_PATH);
        DEBUG_LOGGER.info("{}", basePath);
        name = name + "_p" + x + "_" + z + "q_";
        new File(basePath.toString(), name).mkdirs();
        DEBUG_LOGGER.info("seeTile x={} z={} channels={} name={}", x, z, tile.getShape()[0], name);
        var onnxTile = tile.get();
        for (int ch = 0; ch < tile.getShape()[0]; ch++) {
            tensor.see(onnxTile, name + "/ch_" + ch, basePath.toString(), false, ch);
            // tensor.see(tile,name + "/ch_" + ch,basePath.toString());
        }
    }

    public static void seeTileTiff(FloatTensor tile, int x, int z, String name) {
        Path basePath = FractalTerrainInstance.getServer()
                .getWorldPath(LevelResource.ROOT)
                .normalize();
        name = name + "_p" + x + "_" + z + "q_";
        new File(basePath.toString(), name).mkdirs();
        DEBUG_LOGGER.info("seeTileTiff x={} z={} channels={} name={}", x, z, tile.getShape()[0], name);
        var onnxTile = tile.get();
        for (int ch = 0; ch < tile.getShape()[0]; ch++) {
            TiffConverter.toTiffChannel(onnxTile, ch, basePath + "/" + name + "/ch");
        }
    }

    public static synchronized void debug() {}

    public static <T extends Persistable<T>> void dumpStorage(Storage<T> storage, String name1) {
        final String debugPath = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/" + name1;
        final File dir = new File(debugPath);
        dir.mkdirs();
        final int S = DifferenceOfGaussians.COARSE_TILE_SIZE;
        final Set<TileKey> keys = storage.getGeneratedKeys();
        DEBUG_LOGGER.info("dumpStorage: {} → {}", keys.size(), debugPath);
        for (TileKey key : keys) {
            final int tx = key.get(FractalTerrainConfig.X);
            final int tz = key.get(FractalTerrainConfig.Z);
            final float[] elev = new float[S * S];
            final FloatTensor rawTl = (FloatTensor) storage.getEntry(key);
            for (int i = 0; i < S; i++) {
                for (int j = 0; j < S; j++) {
                    final double w = rawTl.entryAt(new int[] {6, i, j});
                    // 2 == precip
                    final double et = w <= 1e-6 ? 0 : rawTl.entryAt(new int[] {2, i, j}) / w;
                    final double e = Math.max(0.0, et);
                    elev[i * S + j] = (float) (e);
                }
            }
            final FloatTensor tile = new FloatTensor(new int[] {S, S}, elev);
            final String name = "tx" + tx + "_tz" + tz;
            Debug.tensor.see(tile, name, debugPath);
        }
    }

    public static void debugMixin(SurfaceRules.Context context) {
        SurfaceRules.ConditionSource condition = SurfaceRules.abovePreliminarySurface();
        DEBUG_LOGGER.info(" contidio: {} <- true", condition.apply(context).test());
    }

    public static void debugSurfaceBuilder(
            int x, int y, int surface_h, int z, SurfaceRules.Context materialRuleContext) {
        if (x == 0 && z == 0 && y == surface_h) {
            DEBUG_LOGGER.info(" surface_h = {} , coords {} {}", y, x, z);
            SurfaceRules.ConditionSource condition = SurfaceRules.abovePreliminarySurface();
            DEBUG_LOGGER.info(
                    " contidion: {} <- true",
                    condition.apply(materialRuleContext).test());
        }
    }

    public static void debugCalls(int[] wi, String name) {
        if (!FractalTerrainConfig.DEBUG) return;
        DEBUG_LOGGER.info("{} is creating {}", name, wi);
    }

    public static <T> void printStream(Stream<T> stream, String name) {
        DEBUG_LOGGER.info("{}:", name);
        stream.peek(e -> DEBUG_LOGGER.info(" {}", e));
    }
}
