package me.batata_1.fractal_terrain.math;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.slf4j.Logger;

public class DifferenceOfGaussians {

    public static final int COARSE_TILE_SIZE = 64;

    private static final Logger LOG = getLogger(DifferenceOfGaussians.class);

    private final NonIntersectingInfiniteTensor DoGTensor;
    private final double sigma1;
    private final double sigma2;
    private final double threshold;
    private final int pad;

    public DifferenceOfGaussians(String path, double sigma1, double sigma2, double threshold) {
        this.sigma1 = sigma1;
        this.sigma2 = sigma2;
        this.threshold = threshold;
        this.pad = padFor(sigma1, sigma2);
        this.DoGTensor = new NonIntersectingInfiniteTensor(
                path, "dog_tensor", new int[] {1, COARSE_TILE_SIZE, COARSE_TILE_SIZE}, this::buildTile);
    }

    /** The single source of the pad size, so callers sizing their own padded slices cannot disagree
     *  with the constructor. */
    public static int padFor(double sigma1, double sigma2) {
        return (int) Math.ceil(3.0 * Math.max(sigma1, sigma2));
    }

    /** Stateless core for pipelines using the DoG as an intermediate: no normalization, clamping or
     *  cropping, so the caller keeps control of padding. Input must be square. */
    public static float[] run(float[] img, int W, int H, double sigma1, double sigma2) {
        final float[] lowSigmaBlur = Blur.gaussianSeparable(img, W, H, sigma1);
        final float[] highSigmaBlur = Blur.gaussianSeparable(img, W, H, sigma2);
        final float[] differenceOfGaussians = new float[img.length];
        for (int px = 0; px < img.length; px++) {
            differenceOfGaussians[px] = lowSigmaBlur[px] - highSigmaBlur[px];
        }
        return differenceOfGaussians;
    }

    protected FloatTensor buildTile(TileKey key) {
        final int tx = key.get(X);
        final int tz = key.get(Z);
        final int S = COARSE_TILE_SIZE;
        final int ci0 = tx * S - pad;
        final int cj0 = tz * S - pad;
        final int ci1 = (tx + 1) * S + pad;
        final int cj1 = (tz + 1) * S + pad;
        LOG.info("dog requesting slice: [{} {}] [{} {}]", ci0, cj0, ci1, cj1);
        final FloatTensor slice = pipeline.getCoarseSlice(ci0, cj0, ci1, cj1);
        final int cH = ci1 - ci0;
        final int cW = cj1 - cj0;
        final int N = cH * cW;

        final float[] elev = new float[N];
        for (int px = 0; px < N; px++) {
            final float w = slice.get(6 * N + px);
            final float eNorm = (w > 1e-6f) ? slice.get(px) / w : 0f;
            final float clamped = Math.max(0f, eNorm);
            elev[px] = clamped;
        }

        final float[] dogPad = run(elev, cW, cH, sigma1, sigma2);
        final float[] dog = new float[S * S];
        for (int row = 0; row < S; row++) {
            System.arraycopy(dogPad, (row + pad) * cW + pad, dog, row * S, S);
        }
        return new FloatTensor(new int[] {1, S, S}, dog);
    }

    /** True iff the raw DoG value at coarse-px (cx, cz) is ≥ threshold. Triggers the
     *  DoG tile compute on miss. No interpolation. */
    public boolean getThresholded(int cx, int cz) {
        return DoGTensor.getValue(new int[] {0, cx, cz}) >= threshold;
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return DoGTensor;
    }

    public double getThreshold() {
        return threshold;
    }
}
