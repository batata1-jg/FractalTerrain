package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import ai.onnxruntime.OnnxTensor;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;

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

    public void see(OnnxTensor op, String name, String debugPath, boolean seeAvg) {
        see(op, name, debugPath, seeAvg, 0);
    }

    public void see(OnnxTensor op, String name, String debugPath) {
        see(op, name, debugPath, true, 0);
    }

    public void see(OnnxTensor op, String name, String debugPath, boolean seeAvg, int channel) {
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

        DEBUG_LOGGER.info("first tensor sampleElev {} {}", tl.entryAt(new int[] {channel, 0, 0}), name);
        printBounds(tl.get(), "cur seen tensor has bounds");
        for (int i = 0; i < tl.getShape()[1]; i++)
            for (int j = 0; j < tl.getShape()[2]; j++) {
                if (0 == tl.getShape()[0] - 1 || !seeAvg) {
                    ftu[(int) (j + tl.getShape()[1] * i)] = tl.entryAt(new int[] {channel, i, j});
                    continue;
                }

                ftu[(int) (j + tl.getShape()[1] * i)] =
                        tl.entryAt(new int[] {channel, i, j}) / tl.entryAt(new int[] {(tl.getShape()[0] - 1), i, j});
            }
        FloatTensor t = new FloatTensor(ftu, new int[] {tl.getShape()[1], tl.getShape()[2]});

        File dir = new File(debugPath);
        dir.mkdirs();
        File outputFile = new File(dir, name + ".png");
        DEBUG_LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < t.getShape()[0]; i++)
            for (int j = 0; j < t.getShape()[1]; j++) {
                max = Math.max(max, t.entryAt(new int[] {i, j}));
                min = Math.min(min, t.entryAt(new int[] {i, j}));
            }
        DEBUG_LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, name);
        final float eps = 1e-5F;
        int[] arr = new int[(int) t.getSize()];
        for (int i = 0; i < t.getShape()[0]; i++) {
            for (int j = 0; j < t.getShape()[1]; j++) {

                float vi = (t.entryAt(new int[] {i, j}) - min) / (max - min + eps);
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

    public void see(FloatTensor tl, String name, String debugPath) {
        final int[] sh = tl.getShape();
        final int H, W, ch;
        if (sh.length == 3) {
            ch = 0;
            H = sh[1];
            W = sh[2];
        } else if (sh.length == 2) {
            ch = -1;
            H = sh[0];
            W = sh[1];
        } else {
            throw new IllegalArgumentException("expected rank 2 or 3, got " + sh.length);
        }

        float max = -1e30f;
        float min = 1e30f;
        for (int i = 0; i < H; i++) {
            for (int j = 0; j < W; j++) {
                float v = (ch == -1) ? tl.entryAt(new int[] {i, j}) : tl.entryAt(new int[] {ch, i, j});
                if (v > max) max = v;
                if (v < min) min = v;
            }
        }
        final float eps = 1e-5f;
        final int[] arr = new int[H * W];
        for (int i = 0; i < H; i++) {
            for (int j = 0; j < W; j++) {
                float v = (ch == -1) ? tl.entryAt(new int[] {i, j}) : tl.entryAt(new int[] {ch, i, j});
                float vi = (v - min) / (max - min + eps);
                arr[j + i * W] = (int) (255f * vi);
            }
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = img.getRaster();
        raster.setSamples(0, 0, W, H, 0, arr);
        File dir = new File(debugPath);
        dir.mkdirs();
        File outputFile = new File(dir, name + ".png");
        DEBUG_LOGGER.info("FloatTensor see -> {} (min={}, max={})", outputFile.getPath(), min, max);
        try {
            ImageIO.write(img, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void see(OnnxTensor op, String path, int channel) {
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

        DEBUG_LOGGER.info("first tensor sampleElev {} {}", tl.entryAt(new int[] {channel, 0, 0}), path);
        printBounds(tl.get(), "cur seen tensor has bounds");
        for (int i = 0; i < tl.getShape()[1]; i++)
            for (int j = 0; j < tl.getShape()[2]; j++) {
                ftu[(int) (j + tl.getShape()[1] * i)] = tl.entryAt(new int[] {channel, i, j});
                continue;
            }
        FloatTensor t = new FloatTensor(ftu, new int[] {tl.getShape()[1], tl.getShape()[2]});

        File outputFile = new File(path + ".png");
        DEBUG_LOGGER.info("O caminho eh: {} , ", outputFile.getPath());
        float max = -1000000;
        float min = 1000000;
        for (int i = 0; i < t.getShape()[0]; i++)
            for (int j = 0; j < t.getShape()[1]; j++) {
                max = Math.max(max, t.entryAt(new int[] {i, j}));
                min = Math.min(min, t.entryAt(new int[] {i, j}));
            }
        DEBUG_LOGGER.info(" bounds of amplitude min are [{},{}] for {}", min, max, path);
        final float eps = 1e-5F;
        int[] arr = new int[(int) t.getSize()];
        for (int i = 0; i < t.getShape()[0]; i++) {
            for (int j = 0; j < t.getShape()[1]; j++) {

                float vi = (t.entryAt(new int[] {i, j}) - min) / (max - min + eps);
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
