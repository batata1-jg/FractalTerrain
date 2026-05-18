package me.batata_1.fractalterrain.debug;

import ai.onnxruntime.OnnxTensor;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;

public class TiffConverter {

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
        FloatTensor tl = new FloatTensor(op);
        for (int i = 0; i < tl.getShape()[0]; i++) {
            try (FileOutputStream fos = new FileOutputStream(name + "-" + i + ".tiff")) {
                float[] fl = tl.getBand(0, i);
                fos.write(FloatTiffWriter.createFloatTiff(fl));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void toTiffChannel(OnnxTensor op, int ch, String name) {
        FloatTensor tl = new FloatTensor(op);
        try (FileOutputStream fos = new FileOutputStream(name + "-" + ch + ".tiff")) {
            float[] fl = tl.getBand(0, ch);
            fos.write(FloatTiffWriter.createFloatTiff(fl));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
