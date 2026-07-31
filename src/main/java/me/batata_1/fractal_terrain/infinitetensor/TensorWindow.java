package me.batata_1.fractal_terrain.infinitetensor;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.Arrays;
import org.slf4j.Logger;

/**
 * Defines the sliding window layout for an InfiniteTensor.
 *
 * For window index w[], the covered pixel range in dimension d is:
 *   [w[d] * stride[d] + offset[d],  w[d] * stride[d] + offset[d] + size[d])
 *
 * Windows may overlap (stride < size) or have gaps (stride > size).
 * Overlapping windows are summed during slice accumulation.
 */
public class TensorWindow {
    private static final Logger LOG = getLogger(TensorWindow.class);
    public final int[] size;
    public final int[] stride;
    public final int[] offset;

    public TensorWindow(int[] size, int[] stride, int[] offset) {
        this.size = size.clone();
        this.stride = stride.clone();
        this.offset = offset.clone();
    }

    /** Non-overlapping windows starting at zero. */
    public TensorWindow(int[] size) {
        this.size = size.clone();
        this.stride = size.clone();
        this.offset = new int[size.length];
        Arrays.fill(this.offset, 0);
    }

    /** Overlapping windows with given stride, starting at zero. */
    public TensorWindow(int[] size, int[] stride) {
        this.size = size.clone();
        this.stride = stride.clone();
        this.offset = new int[size.length];
    }

    public int ndim() {
        return size.length;
    }

    /** Pixel-space bounds of a window. */
    public int[][] getBounds(int[] windowIndex) {
        int n = size.length;
        int[][] bounds = new int[n][2];
        for (int i = 0; i < n; i++) {
            bounds[i][0] = windowIndex[i] * stride[i] + offset[i];
            bounds[i][1] = windowIndex[i] * stride[i] + offset[i] + size[i];
        }
        return bounds;
    }

    /** Lower window bound of a pixel range, so a read touches no window it does not need. */
    public int[] getLowestIntersection(int[][] pixelRange) {
        int n = size.length;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            int p = pixelRange[i][0];
            int numerator = p - offset[i] - size[i] + 1;
            if (numerator >= 0) {
                // ceiling division
                result[i] = (numerator + stride[i] - 1) / stride[i];
            } else {
                // ceiling for negative: -(floor(-num / stride))
                result[i] = -((-numerator) / stride[i]);
            }
        }
        return result;
    }

    /** Upper window bound of a pixel range; pairs with {@link #getLowestIntersection}. */
    public int[] getHighestIntersection(int[][] pixelRange) {
        int n = size.length;
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            int p = pixelRange[i][1] - 1;
            result[i] = Math.floorDiv(p - offset[i], stride[i]);
        }
        return result;
    }

    // TODO: tentar otimizar isso pra ter menos memoria (tirar esse new)
    public int[] getSinglePixelIntersection(final int[] coords) {
        final int n = size.length;
        final int[] res = new int[n];
        for (int i = 0; i < n; i++) {
            res[i] = Math.floorDiv(coords[i] - offset[i], stride[i]);
        }
        return res;
    }

    public int[] getSinglePixelIntersection(final double[] coords) {
        final int n = size.length;
        final int[] res = new int[n];
        for (int i = 0; i < n; i++) {
            res[i] = Math.floorDiv((int) (coords[i] - offset[i]), stride[i]);
        }
        return res;
    }

    public int[] getPerWindowCoord(final int[] coords) {
        for (int i = 0; i < coords.length; i++) {
            coords[i] %= stride[i];
            if (coords[i] < 0) coords[i] = (coords[i] + stride[i]) % stride[i];
        }
        return coords;
    }

    public double[] getPerWindowCoord(final double[] coords) {
        for (int i = 0; i < coords.length; i++) {
            if (coords[i] < 0) coords[i] += stride[i] * Math.ceil(-coords[i] / stride[i]);
            else coords[i] -= Math.floor(coords[i] / stride[i]) * stride[i];
            if (coords[i] < 0 || coords[i] > stride[i]) {
                coords[i] = 0;
                LOG.error("doublePerWindowsCoord failed at coord[{}] = {}", i, coords[i]);
            }
        }
        return coords;
    }
}
