package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import ai.onnxruntime.OnnxTensor;
import com.mojang.datafixers.util.Pair;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.infinitetensor.storage.FloatTensor;
import net.minecraft.util.WorldSavePath;

public class TensorVisualizer {

    public static void isNan(OnnxTensor t) {
        float[] arr = t.getFloatBuffer().array();
        for (float v : arr) if (Float.isNaN(v)) throw new RuntimeException("this tensor is nan");
    }

    public static void printBounds(OnnxTensor t, String name) {
        float[] arr = t.getFloatBuffer().array();
        float max = -1e9F;
        float min = 1e9F;
        for (float v : arr) {
            max = Math.max(max, v);
            min = Math.min(min, v);
        }

        DEBUG_LOGGER.info("tensor {} has bounds [{},{}]", name, min, max);
    }

    public void see(OnnxTensor op, String name, boolean seeAvg) {
        see(op, name, seeAvg, 0);
    }

    public void see(OnnxTensor op, String name) {
        see(op, name, true, 0);
    }

    public void see(OnnxTensor op, String name, boolean seeAvg, int channel) {
        FloatTensor tl;
        if (op.getInfo().getShape().length > 3) {
            int[] a = Arrays.stream(op.getInfo().getShape())
                    .mapToInt(i -> (int) i)
                    .toArray();
            tl = new FloatTensor(op.getFloatBuffer().array(), new int[] {a[1], a[2], a[3]});
        } else if (op.getInfo().getShape().length == 3) {
            tl = new FloatTensor(op);
        } else {
            tl = new FloatTensor(op.getFloatBuffer().array(), new int[] {
                1, (int) op.getInfo().getShape()[0], (int) op.getInfo().getShape()[1]
            });
        }

        float[] ftu = new float[(int) (tl.getShape()[1] * tl.getShape()[2])];

        DEBUG_LOGGER.info("first tensor sampleElev {} {}", tl.entryAt(new long[] {channel, 0, 0}), name);
        printBounds(tl.get(), "cur seen tensor has bounds");
        for (int i = 0; i < tl.getShape()[1]; i++)
            for (int j = 0; j < tl.getShape()[2]; j++) {
                if (0 == tl.getShape()[0] - 1 || !seeAvg) {
                    ftu[(int) (j + tl.getShape()[1] * i)] = tl.entryAt(new long[] {channel, i, j});
                    continue;
                }

                ftu[(int) (j + tl.getShape()[1] * i)] =
                        tl.entryAt(new long[] {channel, i, j}) / tl.entryAt(new long[] {(tl.getShape()[0] - 1), i, j});
            }
        FloatTensor t = new FloatTensor(ftu, new int[] {tl.getShape()[1], tl.getShape()[2]});

        Path path = FractalTerrainInstance.getServer()
                .getSavePath(WorldSavePath.ROOT)
                .normalize();
        File outputFile = new File(path + "/" + name + ".png");
        DEBUG_LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < t.getShape()[0]; i++)
            for (int j = 0; j < t.getShape()[1]; j++) {
                max = Math.max(max, t.entryAt(Pair.of(i, j)));
                min = Math.min(min, t.entryAt(Pair.of(i, j)));
            }
        DEBUG_LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, name);
        final float eps = 1e-5F;
        int[] arr = new int[(int) t.getSize()];
        for (int i = 0; i < t.getShape()[0]; i++) {
            for (int j = 0; j < t.getShape()[1]; j++) {

                float vi = (t.entryAt(Pair.of(i, j)) - min) / (max - min + eps);
                int v = (int) (255F * vi);

                arr[(int) (j + i * t.getShape()[0])] = v;
            }
        }
        BufferedImage outputImage =
                new BufferedImage((int) t.getShape()[0], (int) t.getShape()[1], BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = outputImage.getRaster();
        raster.setSamples(0, 0, (int) t.getShape()[0], (int) t.getShape()[1], 0, arr);
        try {
            ImageIO.write(outputImage, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("end");
    }
}
