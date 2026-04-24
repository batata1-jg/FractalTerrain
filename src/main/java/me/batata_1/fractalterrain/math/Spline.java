package me.batata_1.fractalterrain.math;

import java.util.Arrays;

public class Spline {

    private final float[] a, b;
    private final float[] x, y;
    private final float k0, kn;
    private final int numPts;

    public Spline(float[] xs, float[] ys, float[] ks) {
        if (xs.length != ys.length || xs.length != ks.length)
            throw new IllegalArgumentException("spline xs and ys len differ");
        if (xs.length < 2) throw new IllegalArgumentException("must have at least 2 pts");
        numPts = xs.length;
        k0 = ks[0];
        kn = ks[ks.length - 1];
        x = xs;
        y = ys;
        for (int i = 0; i < numPts - 1; i++)
            if (xs[i + 1] - xs[i] <= 0) throw new IllegalArgumentException("xs not strictly increasing");
        final float[] aa = new float[numPts - 1];
        final float[] bb = new float[numPts - 1];
        for (int i = 0; i < numPts - 1; i++) {
            aa[i] = ks[i] * (xs[i + 1] - xs[i]) - (ys[i + 1] - ys[i]);
            bb[i] = -ks[i + 1] * (xs[i + 1] - xs[i]) + (ys[i + 1] - ys[i]);
        }
        a = aa;
        b = bb;
    }

    public float clamp01sample(final float xq) {
        return Math.clamp(sample(xq), 0.0F, 1.0F);
    }

    public float sample(final float xq) {
        if (xq <= x[0]) return k0 * xq + y[0];
        if (x[numPts - 1] <= xq) return kn * xq + y[numPts - 1];
        int i = Arrays.binarySearch(x, xq);
        if (i < 0) i = -(i + 1) - 1;
        if (i < 0 || x.length <= i) throw new IllegalStateException("wrong:" + i + " xq:" + xq);
        final float t = (xq - x[i]) / (x[i + 1] - x[i]);
        return (1 - t) * y[i] + t * y[i + 1] + t * (t - 1) * ((1 - t) * a[i] + t * b[i]);
    }
}
