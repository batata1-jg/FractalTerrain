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
 * Dense N-dimensional float tensor.
 *
 * <p><b>Immutability contract:</b> {@link #data}/{@link #shape} are {@code private final} and never
 * exposed as a mutable reference. Instances are freely mutable while under construction by their
 * producer thread; once a tensor is published into a {@link me.batata_1.fractal_terrain.storage.Storage}
 * cache, {@link #freeze()} is called at that cache-write boundary (see {@code Storage#persistAndRecord}
 * / {@code Storage#loadInto}), and every mutating method ({@link #set}, {@link #writeFrom},
 * {@link #dataUnsafe()}, {@link #addFrom}, {@link #copyFrom}) throws {@link IllegalStateException}
 * thereafter. Reads ({@link #get(int)}, {@link #readInto}, {@link #copyRange}, {@link #entryAt}) never
 * allocate and remain available regardless of frozen state, so cached tensors stay safe to read
 * concurrently from any worker thread without per-read copies.
 */
public class FloatTensor implements Persistable<FloatTensor> {

    public static final Logger LOG = getLogger(FloatTensor.class);

    public final float[] data;
    private final int[] shape;
    private final int[] strides;

    /**
     * Set once by {@link me.batata_1.fractal_terrain.storage.Storage} at the cache-write boundary.
     * Never reset. {@code volatile} so the freeze is visible to any thread that observes this tensor
     * through the cache's safe-publication path.
     */
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

    /**
     * Serialize this tensor to a flat little-endian byte array:
     * {@code [magic, rank, shape..., dataLength, rawFloats...]}. Floats are bulk-written through a
     * {@link ByteBuffer} — no Java object-serialization overhead. File IO is {@link
     * me.batata_1.fractal_terrain.storage.Storage}'s responsibility.
     */
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

    /**
     * Rebuild a tensor from bytes previously produced by {@link #serialize()}. Returns a fresh
     * {@code FloatTensor}; the receiver is only a prototype and is not read. A mismatched {@link
     * #MAGIC} (e.g. a legacy tile) fails loudly so the cache can be regenerated.
     */
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

    /**
     * Add values from src into this tensor at a sub-region.
     * dstRegion[d] = {start, stop}, srcRegion[d] = {start, stop}.
     * The region sizes must match in every dimension.
     */
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

    // -------------------------------------------------------------------------
    // Immutability contract: freeze + indexed/bulk accessors
    // -------------------------------------------------------------------------

    /**
     * Freezes this tensor: every subsequent mutation attempt throws {@link IllegalStateException}.
     * Called exactly once by {@link me.batata_1.fractal_terrain.storage.Storage} at the moment this
     * tensor is published into the shared cache. Idempotent — safe to call more than once.
     */
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

    /**
     * Flat indexed write: {@code data[flatIndex] = value}. Throws {@link IllegalStateException} once
     * this tensor is frozen.
     */
    public void set(int flatIndex, float value) {
        checkMutable();
        data[flatIndex] = value;
    }

    /**
     * Bulk read of {@code [srcPos, srcPos+length)} into {@code dest}, mirroring
     * {@link System#arraycopy}. Read-only: always allowed, regardless of frozen state.
     */
    public void readInto(int srcPos, float[] dest, int destPos, int length) {
        System.arraycopy(data, srcPos, dest, destPos, length);
    }

    /**
     * Bulk write of {@code src[srcPos, srcPos+length)} into this tensor at {@code destPos}, mirroring
     * {@link System#arraycopy}. Throws {@link IllegalStateException} once this tensor is frozen.
     */
    public void writeFrom(float[] src, int srcPos, int destPos, int length) {
        checkMutable();
        System.arraycopy(src, srcPos, data, destPos, length);
    }

    /**
     * Read-only copy of {@code data[from:to]} as a freshly allocated array. Always allowed. Prefer
     * {@link #get(int)}/{@link #readInto} on the hot path — this allocates.
     */
    public float[] copyRange(int from, int to) {
        return Arrays.copyOfRange(data, from, to);
    }

    /**
     * Direct access to the backing array, for bulk numeric interop (e.g. constructing an
     * {@link OnnxTensor} input) that needs the array reference itself rather than an indexed/ranged
     * copy. Throws {@link IllegalStateException} once this tensor is frozen, so a cached tensor's
     * backing array can never escape through this method. Callers must not mutate the returned array.
     */
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
