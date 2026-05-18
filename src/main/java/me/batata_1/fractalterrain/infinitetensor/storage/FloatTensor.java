package me.batata_1.fractalterrain.infinitetensor.storage;

import static me.batata_1.fractalterrain.debug.Debug.getLogger;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;
import java.nio.FloatBuffer;
import java.util.Arrays;
import org.slf4j.Logger;

public class FloatTensor {

    public static final Logger LOG = getLogger(FloatTensor.class);

    public final float[] data;
    public final int[] shape;
    private final long[] cProd;
    private final int[] strides;

    public long[] calcProd() {
        final int len = shape.length;
        final long[] c = new long[len];
        c[len - 1] = 1;
        for (int id = len - 2; id >= 0; id--) {
            c[id] = shape[id + 1] * c[id + 1];
        }
        return c;
    }

    public FloatTensor(OnnxTensor h) {
        data = h.getFloatBuffer().array();
        shape = Arrays.stream(h.getInfo().getShape()).mapToInt(i -> (int) i).toArray();
        this.strides = computeStrides(shape);
        cProd = calcProd();
    }

    public FloatTensor(int[] shape) {
        this.shape = shape.clone();
        int total = 1;
        for (int d : shape) total *= d;
        this.data = new float[total];
        this.strides = computeStrides(shape);
        cProd = calcProd();
    }

    public FloatTensor(int[] shape, float[] data) {
        this.shape = shape.clone();
        this.data = data;
        this.strides = computeStrides(shape);
        cProd = calcProd();
    }

    public FloatTensor(float[] en, int[] sh) {
        data = en;
        shape = sh;
        this.strides = computeStrides(shape);
        cProd = calcProd();
    }

    static int[] computeStrides(int[] shape) {
        int n = shape.length;
        int[] s = new int[n];
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

    public long byteSize() {
        return (long) data.length * Float.BYTES;
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

    public float entryAt(Pair<Integer, Integer> xz) {
        if (shape.length != 2) {
            LOG.error("cannot use pair because tensor is not 2D");
            throw new RuntimeException();
        }
        return entryAt(new long[] {xz.getFirst(), xz.getSecond()});
    }

    public float entryAt(long[] pos) {
        checkRank(pos.length);
        int idx = 0;
        for (int i = 0; i < shape.length; i++) idx += (int) (cProd[i] * pos[i]);
        if (idx >= data.length) throw new RuntimeException("outOfBOundsTensor: " + Arrays.toString(pos));
        return data[idx];
    }

    public int[] getShape() {
        return shape;
    }

    public long getSize() {
        return cProd[0] * shape[0];
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
        final long[] coords = new long[shape.length];
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
