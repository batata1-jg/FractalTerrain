package me.batata_1.fractal_terrain.infinitetensor;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.slf4j.Logger;

/**
 * Dense N-dimensional float tensor, the payload type behind every cached tile in the pipeline.
 *
 * <p>Freely mutable while its producer thread builds it, then sealed by {@link #freeze()} at the
 * cache-write boundary so worker threads can share it without copying.
 *
 * <p>The seal is partial by design: {@link #data} is public for bulk numeric interop, so a direct
 * {@code tensor.data[i] = x} is not caught. Only mutation routed through this class's methods throws.
 */
public class FloatTensor implements Persistable<FloatTensor> {

    public static final Logger LOG = getLogger(FloatTensor.class);

    public final float[] data;
    private final int[] shape;
    private final int[] strides;

    /** Set once by {@code Storage} at the cache-write boundary; never reset. */
    private volatile boolean frozen = false;

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

    /** Flat little-endian payload. Bulk-written through a {@link ByteBuffer} rather than Java object
     *  serialization, since tiles are written on the generation path. */
    @Override
    public byte[] serialize() {
        final int headerInts = 1 /* magic */ + 1 /* rank */ + shape.length + 1 /* dataLength */;
        final byte[] bytes = new byte[headerInts * Integer.BYTES + data.length * Float.BYTES];
        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.putInt(shape.length);
        for (int d : shape) buf.putInt(d);
        buf.putInt(data.length);
        buf.asFloatBuffer().put(data);
        return bytes;
    }

    /** Rebuilds a tensor; the receiver is only a prototype. A legacy tile fails loudly rather than
     *  being misread, so the cache can be regenerated. */
    @Override
    public FloatTensor deserialize(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new IllegalStateException("incompatible FloatTensor tile format (expected 0x"
                    + Integer.toHexString(MAGIC) + ", got 0x" + Integer.toHexString(magic)
                    + "); delete the fractal_terrain tile cache to regenerate");
        }
        final int sl = buf.getInt();
        final int[] shape = new int[sl];
        for (int i = 0; i < sl; i++) shape[i] = buf.getInt();
        final int el = buf.getInt();
        final float[] entries = new float[el];
        buf.asFloatBuffer().get(entries);
        return new FloatTensor(entries, shape);
    }

    /** Accumulates a sub-region, the operation windowed tensors compose overlapping tiles with. */
    public void addFrom(FloatTensor src, int[][] dstRegion, int[][] srcRegion) {
        checkMutable();
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
        checkMutable();
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

    /** Extracts a sub-region as a new zero-based tensor. */
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

    // -------------------------------------------------------------------------
    // Immutability contract: freeze + indexed/bulk accessors
    // -------------------------------------------------------------------------

    /** Seals the tensor at cache publication, so shared readers cannot observe a mutating tile. */
    @Override
    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    private void checkMutable() {
        if (frozen) {
            throw new IllegalStateException(
                    "cannot mutate " + this + ": already frozen (published to a Storage cache)");
        }
    }

    /** Flat indexed read: {@code data[flatIndex]}. No allocation; safe on the tensor read hot path. */
    public float get(int flatIndex) {
        return data[flatIndex];
    }

    /** Flat indexed write; throws once frozen. */
    public void set(int flatIndex, float value) {
        checkMutable();
        data[flatIndex] = value;
    }

    /** Bulk read into a caller's buffer; allowed even when frozen. */
    public void readInto(int srcPos, float[] dest, int destPos, int length) {
        System.arraycopy(data, srcPos, dest, destPos, length);
    }

    /** Bulk write; throws once frozen. */
    public void writeFrom(float[] src, int srcPos, int destPos, int length) {
        checkMutable();
        System.arraycopy(src, srcPos, data, destPos, length);
    }

    /** Allocating range copy; prefer {@link #readInto} on the hot path. */
    public float[] copyRange(int from, int to) {
        return Arrays.copyOfRange(data, from, to);
    }

    /** Escape hatch for bulk numeric interop that needs the array itself, such as an ONNX input.
     *  Throws once frozen, so a cached tensor's array can never escape. Do not mutate the result. */
    public float[] dataUnsafe() {
        checkMutable();
        return data;
    }

    /** Indexed shape read: {@code shape[dim]}. No allocation. */
    public int shape(int dim) {
        return shape[dim];
    }

    /** Defensive copy of the shape array; {@link #shape(int)} avoids the allocation for a single dim. */
    public int[] getShape() {
        return shape.clone();
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
