package me.batata_1.fractalterrain.util;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.mojang.datafixers.util.Pair;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.storage.Tile;
import net.minecraft.util.WorldSavePath;

public class DebugTensors {

    public static OrtSession test_in;

    public static void readDebug() throws IOException, OrtException {
        test_in = Models.getOrCreateModel("models/latent_coisado");
    }

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

        LOGGER.info("tensor {} has bounds [{},{}]", name, min, max);
    }

    public static void seeTensor(OnnxTensor op, String name, boolean seeAvg) throws IOException {
        seeTensor(op, name, seeAvg, 0);
    }

    public static void seeTensor(OnnxTensor op, String name) throws IOException {
        seeTensor(op, name, true, 0);
    }

    public static void seeTensor(OnnxTensor op, String name, boolean seeAvg, int channel) throws IOException {

        Tile tl;

        if (op.getInfo().getShape().length > 3) {
            long[] a = op.getInfo().getShape();
            tl = new Tile(op.getFloatBuffer().array(), new long[] {a[1], a[2], a[3]});
        } else if (op.getInfo().getShape().length == 3) {
            tl = new Tile(op);
        } else {
            tl = new Tile(
                    op.getFloatBuffer().array(),
                    new long[] {1, op.getInfo().getShape()[0], op.getInfo().getShape()[1]});
        }

        float[] ftu = new float[(int) (tl.getShape()[1] * tl.getShape()[2])];

        LOGGER.info("first tensor sampleElev {} {}", tl.entryAt(new long[] {channel, 0, 0}), name);
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
        Tile t = new Tile(ftu, new long[] {tl.getShape()[1], tl.getShape()[2]});

        Path path = FractalTerrainInstance.getServer()
                .getSavePath(WorldSavePath.ROOT)
                .normalize();
        File outputFile = new File(path + "/" + name + ".png");
        LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < t.getShape()[0]; i++)
            for (int j = 0; j < t.getShape()[1]; j++) {
                max = Math.max(max, t.entryAt(Pair.of(i, j)));
                min = Math.min(min, t.entryAt(Pair.of(i, j)));
            }
        LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, name);
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
        ImageIO.write(outputImage, "png", outputFile);
        System.out.println("end");
    }
}
