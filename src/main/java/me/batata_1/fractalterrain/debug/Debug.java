package me.batata_1.fractalterrain.debug;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import me.batata_1.fractalterrain.FractalTerrainInstance;
// import me.batata_1.fractalterrain.ml.tensorProviders.MapProvider;
import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;
import me.batata_1.fractalterrain.noise.NoiseSampler;
import me.batata_1.fractalterrain.noise.PhacelleNoiseSampler;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Debug {

    public static final Logger DEBUG_LOGGER = getLogger(Debug.class);
    public static final TensorVisualizer tensor = new TensorVisualizer();

    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger("fractal_terrain/" + clazz.toString());
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

    public static synchronized void debug() {
        //        try {

        //            see(MapProvider.sampleMap(Pair.of(-32, -32), new long[] {5, 64, 64}), "feedElev", false, 0);

        //            toTiffChannel(FractalTerrainInstance.reliefSource.get().getTilesAsTensor(0, 0), 4, "tensor");

        //            VoronoiNoiseSampler s = new VoronoiNoiseSampler(64, 1);
        //
        //            seeNoise(s, "voronoi", 0, 0, 512);
        //            see(FractalTerrainInstance.reliefSource.get().getTilesAsTensor(0, 0), "analysie", false, 0);
        //            see(FractalTerrainInstance.reliefSource.get().getTilesAsTensor(0, 0), "grad", false, 4);
        //            see(FractalTerrainInstance.reliefSource.get().getTilesAsTensor(0, 0), "gradY", false, 3);
        //            see(FractalTerrainInstance.reliefSource.get().getTilesAsTensor(0, 0), "gradX", false, 2);
        //            seeNoise(new PhacelleNoiseSampler(5, 3), "phacelle", 0, 0, 512);
        //
        //            for(float freq=1F ; freq<=512F ; freq *= 2F ) {
        //                seePhacelleNormal(freq,"phacelleNormal" + freq,0,0,512);
        //                seeNoise(new PhacelleNoiseSampler(5,freq), "phacelle"+freq,0,0,512);
        //            //            }
        //        } catch (OrtException | IOException e) {
        //            throw new RuntimeException(e);
        //        }

        //        for(int i=-4 ; i<4 ; i++) {
        //            for(int j=-4 ; j<4 ; j++) {
        //                toTiffChannel(FractalTerrainInstance.reliefSource.getTilesAsTensor(i,j),0,
        //                        i+ "-" + j +"tensor" );
        //                toTiffChannel(FractalTerrainInstance.reliefSource.getTilesAsTensor(i,j),4,
        //                        i+ "-" + j +"tensor" );
        //            }
        //        }
    }

}
