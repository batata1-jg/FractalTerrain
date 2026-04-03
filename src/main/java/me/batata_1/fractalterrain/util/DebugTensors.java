package me.batata_1.fractalterrain.util;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.mojang.datafixers.util.Pair;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.ml.tensorProviders.MapProvider;
import me.batata_1.fractalterrain.storage.Tile;
import net.minecraft.util.WorldSavePath;

public class DebugTensors {

    public static CompletableFuture<OrtSession> test_in;

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

    public static void seeFinal(OnnxTensor t, int x, int z) {
        try {
            seeTensor(t,"final" + (x>>1) +" " +(z>>1),false,1);
            seeTensor(t,"final_res"+(x>>1) +" " +(z>>1),false,4 );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void debugModels() {
        assert false;
    }

    public static void debugLatentStageConstructor() {
        LOGGER.info("creating latent stage");
    }

    // this class is by ChatGPT (sorry for being lazy \_._._/ , didn't want to write this )
    public static class FloatTiffWriter {

        public static byte[] createFloatTiff(float[] pixels) throws IOException {
            int width = 512;
            int height = 512;

            if (pixels.length != width * height) {
                throw new IllegalArgumentException("Pixel array must be 512x512");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // ---------------------------------
            // 1. HEADER (8 bytes)
            // ---------------------------------
            out.write('I'); // Little endian
            out.write('I');
            writeShortLE(out, 42);

            int pixelDataSize = pixels.length * 4;
            int ifdOffset = 8 + pixelDataSize;
            writeIntLE(out, ifdOffset);

            // ---------------------------------
            // 2. PIXEL DATA (float -> bytes)
            // ---------------------------------
            for (float f : pixels) {
                writeFloatLE(out, f);
            }

            // ---------------------------------
            // 3. IFD
            // ---------------------------------
            ByteArrayOutputStream ifd = new ByteArrayOutputStream();

            int numEntries = 10;
            writeShortLE(ifd, numEntries);

            // --- Tags ---

            // ImageWidth (256)
            writeIFDEntry(ifd, 256, 4, 1, width);

            // ImageLength (257)
            writeIFDEntry(ifd, 257, 4, 1, height);

            // BitsPerSample (258) = 32 bits
            writeIFDEntry(ifd, 258, 3, 1, 32);

            // Compression (259) = none
            writeIFDEntry(ifd, 259, 3, 1, 1);

            // PhotometricInterpretation (262)
            // 1 = BlackIsZero
            writeIFDEntry(ifd, 262, 3, 1, 1);

            // StripOffsets (273)
            writeIFDEntry(ifd, 273, 4, 1, 8);

            // SamplesPerPixel (277)
            writeIFDEntry(ifd, 277, 3, 1, 1);

            // RowsPerStrip (278)
            writeIFDEntry(ifd, 278, 4, 1, height);

            // StripByteCounts (279)
            writeIFDEntry(ifd, 279, 4, 1, pixelDataSize);

            // SampleFormat (339) = 3 (IEEE float)
            writeIFDEntry(ifd, 339, 3, 1, 3);

            // Next IFD offset
            writeIntLE(ifd, 0);

            // Append IFD
            out.write(ifd.toByteArray());

            return out.toByteArray();
        }

        // ---------------------------------
        // IFD Entry writer
        // ---------------------------------
        private static void writeIFDEntry(ByteArrayOutputStream out, int tag, int type, int count, int value)
                throws IOException {
            writeShortLE(out, tag);
            writeShortLE(out, type);
            writeIntLE(out, count);

            if (type == 3 && count == 1) {
                writeShortLE(out, value);
                writeShortLE(out, 0);
            } else {
                writeIntLE(out, value);
            }
        }

        // ---------------------------------
        // Primitive writers (Little Endian)
        // ---------------------------------
        private static void writeShortLE(ByteArrayOutputStream out, int value) throws IOException {
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
        }

        private static void writeIntLE(ByteArrayOutputStream out, int value) throws IOException {
            out.write(value & 0xFF);
            out.write((value >> 8) & 0xFF);
            out.write((value >> 16) & 0xFF);
            out.write((value >> 24) & 0xFF);
        }

        private static void writeFloatLE(ByteArrayOutputStream out, float value) throws IOException {
            int intBits = Float.floatToIntBits(value);
            writeIntLE(out, intBits);
        }
    }

    public static void toTiff(OnnxTensor op, String name) {
        Tile tl = new Tile(op);
        for(int i=0 ; i<tl.getShape()[0] ; i++) {
            try(FileOutputStream fos = new FileOutputStream(name + "-" + i + ".tiff")) {
                float[] fl = tl.getBand(0,i);
                fos.write(FloatTiffWriter.createFloatTiff(fl));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void toTiffChannel(OnnxTensor op,int ch, String name) {
        Tile tl = new Tile(op);
        try(FileOutputStream fos = new FileOutputStream(name + "-" + ch + ".tiff")) {
            float[] fl = tl.getBand(0,ch);
            fos.write(FloatTiffWriter.createFloatTiff(fl));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static synchronized void debug() throws ExecutionException, InterruptedException {
        try {
            seeTensor(MapProvider.sampleMap(Pair.of(-32,-32),new long[]{5,64,64}),"feedElev",false,0);
        } catch (OrtException | IOException e) {
            throw new RuntimeException(e);
        }
        toTiffChannel(FractalTerrainInstance.reliefSource.get().getTilesAsTensor(0,0),0,"tensor");
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
