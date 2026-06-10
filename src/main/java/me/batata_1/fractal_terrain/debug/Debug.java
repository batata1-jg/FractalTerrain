package me.batata_1.fractal_terrain.debug;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
// import me.batata_1.fractalterrain.ml.tensorProviders.MapProvider;
import me.batata_1.fractal_terrain.noise.NoiseSampler;
import me.batata_1.fractal_terrain.noise.PhacelleNoiseSampler;
import me.batata_1.fractal_terrain.storage.FloatTensor;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
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
                .getSavePath(WorldSavePath.ROOT)
                .normalize();
        sampler.initSampler(FractalTerrainInstance.getServer().getOverworld().getSeed());
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

    public static void seePhacelleNormal(float freq, String name, int x, int z, int size) throws IOException {

        Path path = FractalTerrainInstance.getServer()
                .getSavePath(WorldSavePath.ROOT)
                .normalize();
        PhacelleNoiseSampler sampler = new PhacelleNoiseSampler(1, freq);
        sampler.initSampler(FractalTerrainInstance.getServer().getOverworld().getSeed());
        File outputFile = new File(path + "/" + name + ".png");
        DEBUG_LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                max = Math.max(max, sampler.sampleNormal(i, j));
                min = Math.min(min, sampler.sampleNormal(i, j));
            }
        DEBUG_LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, name);
        final float eps = 1e-5F;
        int[] arr = new int[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {

                float vi = (sampler.sampleNormal(i, j) - min) / (max - min + eps);
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

    public static void seePhacelle(float freq, String name, FloatTensor t, int size) throws IOException {

        Path path = FractalTerrainInstance.getServer()
                .getSavePath(WorldSavePath.ROOT)
                .normalize();
        PhacelleNoiseSampler sampler = new PhacelleNoiseSampler(1, freq);
        sampler.initSampler(FractalTerrainInstance.getServer().getOverworld().getSeed());
        File outputFile = new File(path + "/" + name + ".png");
        DEBUG_LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                max = Math.max(max, sampler.sampleNormal(i, j));
                min = Math.min(min, sampler.sampleNormal(i, j));
            }
        DEBUG_LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, name);
        final float eps = 1e-5F;
        int[] arr = new int[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {

                float vi = (sampler.sampleNormal(i, j) - min) / (max - min + eps);
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
                .getSavePath(WorldSavePath.ROOT)
                .normalize();
        name = name + "_p" + x + "_" + z + "q_";
        new File(basePath.toString(), name).mkdirs();
        DEBUG_LOGGER.info("seeTileTiff x={} z={} channels={} name={}", x, z, tile.getShape()[0], name);
        var onnxTile = tile.get();
        for (int ch = 0; ch < tile.getShape()[0]; ch++) {
            TiffConverter.toTiffChannel(onnxTile, ch, basePath + "/" + name + "/ch");
        }
    }

    public static synchronized void debug() {
        int x = -1;
        int z = -1;
        FractalTerrainInstance.pipeline.getDecoderSlice(x, z);
        FloatTensor fl = FractalTerrainInstance.getDecoderStorage().getEntry(new int[] {0, x, z});
        DEBUG_LOGGER.info("debug {}", fl.shape);
        seeTile(fl, x, z, "decoder_tile");
    }

    public static void debugMixin(MaterialRules.MaterialRuleContext context) {
        MaterialRules.MaterialCondition condition = MaterialRules.surface();
        DEBUG_LOGGER.info(" contidio: {} <- true", condition.apply(context).get());
    }

    public static void debugSurfaceBuilder(
            int x, int y, int surface_h, int z, MaterialRules.MaterialRuleContext materialRuleContext) {
        if (x == 0 && z == 0 && y == surface_h) {
            DEBUG_LOGGER.info(" surface_h = {} , coords {} {}", y, x, z);
            MaterialRules.MaterialCondition condition = MaterialRules.surface();
            DEBUG_LOGGER.info(
                    " contidion: {} <- true",
                    condition.apply(materialRuleContext).get());
        }
    }

    public static void debugCalls(int[] wi, String name) {
        if (!FractalTerrainConfig.DEBUG) return;
        DEBUG_LOGGER.info("{} is creating {}", name, wi);
    }
}
