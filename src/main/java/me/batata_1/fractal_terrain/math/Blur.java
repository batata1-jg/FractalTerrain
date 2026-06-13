package me.batata_1.fractal_terrain.math;

import static me.batata_1.fractal_terrain.references.Reference.LOGGER;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class Blur {
    private static final double[][] gauss_kernel_3x3 = {
        {1, 1, 1},
        {1, 1, 1},
        {1, 1, 1},
    };

    private static final int[] d5 = {-2, -1, 0, 1, 2};

    public static final double MAX_SIGMA = 4.0;
    public static final int MAX_HALF = (int) Math.ceil(3.0 * MAX_SIGMA);
    public static final double[] KERNEL;

    static {
        final int len = 2 * MAX_HALF + 1;
        KERNEL = new double[len];
        final double inv2s2 = 1.0 / (2.0 * MAX_SIGMA * MAX_SIGMA);
        double sum = 0;
        for (int i = 0; i < len; i++) {
            double d = i - MAX_HALF;
            KERNEL[i] = Math.exp(-d * d * inv2s2);
            sum += KERNEL[i];
        }
        for (int i = 0; i < len; i++) KERNEL[i] /= sum;
    }

    private final CompletableFuture<Function<double[], Double>> functionCompletableFuture = new CompletableFuture<>();

    public Blur() {}

    public void setF(Function<double[], Double> f) {
        this.functionCompletableFuture.complete(f);
    }

    public float entryAvgBlur3x3(double[] doubles) {
        try {
            double resp = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    doubles[0] += dx;
                    doubles[1] += dz;
                    resp += functionCompletableFuture.get().apply(doubles);
                    doubles[0] -= dx;
                    doubles[1] -= dz;
                }
            }
            return (float) (resp / 9);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("future not completed?");
            throw new RuntimeException(e);
        }
    }

    private static double[] kernelForSigma(double sigma) {
        if (sigma > MAX_SIGMA) {
            throw new IllegalArgumentException("sigma " + sigma + " exceeds MAX_SIGMA " + MAX_SIGMA);
        }
        final int half = (int) Math.ceil(3.0 * sigma);
        final int len = 2 * half + 1;
        final int start = MAX_HALF - half;
        final double[] k = new double[len];
        double sum = 0;
        for (int i = 0; i < len; i++) {
            k[i] = KERNEL[start + i];
            sum += k[i];
        }
        for (int i = 0; i < len; i++) k[i] /= sum;
        return k;
    }

    public static float[] gaussianSeparable(float[] src, int W, int H, double sigma) {
        final double[] kern = kernelForSigma(sigma);
        final int half = (kern.length - 1) / 2;

        final float[] tmp = new float[W * H];
        final float[] dst = new float[W * H];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double acc = 0;
                for (int dx = -half; dx <= half; dx++) {
                    int xx = Math.clamp(x + dx, 0, W - 1);
                    acc += kern[dx + half] * src[y * W + xx];
                }
                tmp[y * W + x] = (float) acc;
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double acc = 0;
                for (int dy = -half; dy <= half; dy++) {
                    int yy = Math.clamp(y + dy, 0, H - 1);
                    acc += kern[dy + half] * tmp[yy * W + x];
                }
                dst[y * W + x] = (float) acc;
            }
        }
        return dst;
    }

    /**
     * Halo width (in pixels) a Blur needs on every side so the cropped result
     * is free of border artifacts: {@code ceil(3 * sigma)}.
     */
    public static int padFor(double sigma) {
        return (int) Math.ceil(3.0 * sigma);
    }
}
