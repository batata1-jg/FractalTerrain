package me.batata_1.fractal_terrain.infinitetensor;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.slf4j.Logger;

public class FloatTensor implements Persistable<FloatTensor> {

    public static final Logger LOG = getLogger(FloatTensor.class);

    public final float[] data;
    public final int[] shape;
    private final int[] strides;

    public FloatTensor(OnnxTensor h) {
        data = h.getFloatBuffer().array();
        shape = Arrays.stream(h.getInfo().getShape()).mapToInt(i -> (int) i).toArray();
        this.strides = computeStrides(shape);
    }

    public FloatTensor(int[] shape) {
        this.shape = shape.clone();
        int total = 1;
        for (int d : shape) total *= d;
        this.data = new float[total];
        this.strides = computeStrides(shape);
    }

    public FloatTensor(int[] shape, float[] data) {
        this.shape = shape.clone();
        this.data = data;
        this.strides = computeStrides(shape);
    }

    public FloatTensor(float[] en, int[] sh) {
        data = en;
        shape = sh;
        this.strides = computeStrides(shape);
    }

    static int[] computeStrides(int[] shape) {
        final int n = shape.length;
        final int[] s = new int[n];
        int stride = 1;
        for (int i = n - 1; i >= 0; i--) {
            s[i] = stride;
            stride *= shape[i];
        }
        return s;
    }

    public int ndim() {
        return shape.length;
    }

    @Override
    public long byteSize() {
        return (long) data.length * Float.BYTES;
    }

    /** Identifies the direct-binary tile format ("FTN1"); guards against stale legacy caches. */
    private static final int MAGIC = 0x46544E31;

    /**
     * Serialize this tensor to {@code path + ".ser"} in a flat little-endian binary layout:
     * {@code [magic, rank, shape..., dataLength, rawFloats...]}. Floats are bulk-written through a
     * {@link ByteBuffer} — no intermediate {@code float[]} copy and no Java object-serialization
     * overhead.
     */
    @Override
    public void serialize(String path) throws IOException {
        try (DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path + ".ser")))) {
            out.writeInt(MAGIC);
            out.writeInt(shape.length);
            for (int d : shape) out.writeInt(d);
            out.writeInt(data.length);
            final byte[] bytes = new byte[data.length * Float.BYTES];
            ByteBuffer.wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .put(data);
            out.write(bytes);
        }
    }

    /**
     * Read a tensor previously written by {@link #serialize(String)} from {@code path + ".ser"}.
     * Returns a fresh {@code FloatTensor}; the receiver is only a prototype and is not read. Reads
     * straight into the final {@code float[]} — no intermediate copy. A mismatched {@link #MAGIC}
     * (e.g. a legacy {@code ObjectOutputStream} tile) fails loudly so the cache can be regenerated.
     */
    @Override
    public FloatTensor deserialize(String path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path + ".ser")))) {
            final int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("incompatible FloatTensor tile format in " + path + ".ser (expected 0x"
                        + Integer.toHexString(MAGIC) + ", got 0x" + Integer.toHexString(magic)
                        + "); delete the fractal_terrain tile cache to regenerate");
            }
            final int sl = in.readInt();
            final int[] shape = new int[sl];
            for (int i = 0; i < sl; i++) shape[i] = in.readInt();
            final int el = in.readInt();
            final byte[] bytes = new byte[el * Float.BYTES];
            in.readFully(bytes);
            final float[] entries = new float[el];
            ByteBuffer.wrap(bytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .get(entries);
            return new FloatTensor(entries, shape);
        }
    }

    /**
     * Add values from src into this tensor at a sub-region.
     * dstRegion[d] = {start, stop}, srcRegion[d] = {start, stop}.
     * The region sizes must match in every dimension.
     */
    public void addFrom(FloatTensor src, int[][] dstRegion, int[][] srcRegion) {
        int n = shape.length;
        int[] count = new int[n];
        int total = 1;
        for (int d = 0; d < n; d++) {
            count[d] = dstRegion[d][1] - dstRegion[d][0];
            total *= count[d];
        }
        if (total == 0) return;

        // Compute strides for iterating over the count-shaped region
        int[] iterStrides = new int[n];
        iterStrides[n - 1] = 1;
        for (int d = n - 2; d >= 0; d--) {
            iterStrides[d] = iterStrides[d + 1] * count[d + 1];
        }

        for (int flat = 0; flat < total; flat++) {
            int dstFlat = 0, srcFlat = 0;
            for (int d = 0; d < n; d++) {
                int idx = (flat / iterStrides[d]) % count[d];
                dstFlat += (dstRegion[d][0] + idx) * strides[d];
                srcFlat += (srcRegion[d][0] + idx) * src.strides[d];
            }
            data[dstFlat] += src.data[srcFlat];
        }
    }

    public void copyFrom(FloatTensor src, int[][] dstRegion, int[][] srcRegion) {
        int n = shape.length;
        int[] count = new int[n];
        int total = 1;
        for (int d = 0; d < n; d++) {
            count[d] = dstRegion[d][1] - dstRegion[d][0];
            total *= count[d];
        }
        if (total == 0) return;

        // Compute strides for iterating over the count-shaped region
        int[] iterStrides = new int[n];
        iterStrides[n - 1] = 1;
        for (int d = n - 2; d >= 0; d--) {
            iterStrides[d] = iterStrides[d + 1] * count[d + 1];
        }

        for (int flat = 0; flat < total; flat++) {
            int dstFlat = 0, srcFlat = 0;
            for (int d = 0; d < n; d++) {
                int idx = (flat / iterStrides[d]) % count[d];
                dstFlat += (dstRegion[d][0] + idx) * strides[d];
                srcFlat += (srcRegion[d][0] + idx) * src.strides[d];
            }
            data[dstFlat] = src.data[srcFlat];
        }
    }

    /**
     * Extract a contiguous sub-region as a new zero-based tensor.
     * region[d] = {start, stop}.
     */
    public FloatTensor slice(int[][] region) {
        int n = shape.length;
        int[] newShape = new int[n];
        for (int d = 0; d < n; d++) {
            newShape[d] = region[d][1] - region[d][0];
        }
        FloatTensor result = new FloatTensor(newShape);
        int[][] dstRegion = new int[n][2];
        for (int d = 0; d < n; d++) {
            dstRegion[d][0] = 0;
            dstRegion[d][1] = newShape[d];
        }
        result.addFrom(this, dstRegion, region);
        return result;
    }

    @Override
    public String toString() {
        return "FloatTensor(shape=" + Arrays.toString(shape) + ")";
    }

    public float entryAt(int[] pos) {
        checkRank(pos.length);
        int idx = 0;
        for (int i = 0; i < shape.length; i++) idx += strides[i] * pos[i];
        if (idx >= data.length) throw new RuntimeException("outOfBOundsTensor: " + Arrays.toString(pos));
        return data[idx];
    }

    public int[] getShape() {
        return shape;
    }

    public int getSize() {
        return strides[0] * shape[0];
    }

    public long getLength() {
        return shape[shape.length - 1];
    }

    public OnnxTensor get() {
        try {
            return OnnxTensor.createTensor(
                    OrtEnvironment.getEnvironment(),
                    FloatBuffer.wrap(data),
                    Arrays.stream(shape).mapToLong(i -> (long) i).toArray());
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkRank(int len) {
        if (this.shape.length != len) {
            LOG.error("ranks do not match {} {}", this.shape.length, len);
            throw new RuntimeException();
        }
    }

    private void checkShapes(int[] arr) {
        if (!Arrays.equals(this.shape, arr)) {
            LOG.error("shapes do not match {} {}", this.shape, arr);
            throw new RuntimeException();
        }
    }

    public float[] getBand(int i, int ch) {
        final int[] coords = new int[shape.length];
        Arrays.fill(coords, 0);
        coords[i] = ch;
        final float[] resp = new float[(int) (shape[shape.length - 1] * shape[shape.length - 2])];
        for (int k = 0; k < shape[shape.length - 1]; k++) {
            for (int l = 0; l < shape[shape.length - 2]; l++) {
                coords[shape.length - 1] = k;
                coords[shape.length - 2] = l;
                resp[shape[shape.length - 2] * k + l] = entryAt(coords);
            }
        }
        return resp;
    }
}
