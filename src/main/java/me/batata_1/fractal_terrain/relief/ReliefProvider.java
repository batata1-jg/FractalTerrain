package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.*;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;

public class ReliefProvider {

    private static final double DOG_SIGMA1 = 1.0;
    private static final double DOG_SIGMA2 = 2.0;
    private static final double DOG_THRESHOLD = 0.0;
    private static final double QUERY_FREQUENCY = 1.0;

    private final NonIntersectingInfiniteTensor final_tiles;

    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(path + "/final_relief_tiles", new int[] {RELIEF_CHANNELS, 512, 512}, key -> {
            final int x = key.get(X);
            final int z = key.get(Z);
            final FloatTensor final_slice = pipeline.getDecoderSlice(x, z);
            final float[] entries = new float[RELIEF_CHANNELS << 18];
            // TODO: mover weight para canal 0
            for (int ch = 1; ch < 10; ch++) {
                for (int px = 0; px < (1 << 18); px++) {
                    final float w = final_slice.data[px];
                    entries[((ch - 1) << 18) + px] = (w > 1e-6f) ? final_slice.data[(ch << 18) + px] / w : 0f;
                }
            }


            final FloatTensor t = new FloatTensor(entries, new int[] {RELIEF_CHANNELS, 512, 512});
            //  Debug.seeTile(t, x, z, "final");
            return t;
        });
    }
    // ch7 -> adj
    // ch8 -> flow

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    public Float get_entry(final int[] mutableCoords, final int ch) {
        mutableCoords[CH] = ch;
        return final_tiles.getValue(mutableCoords);
    }

    public Float getElev(int[] xz) {
        return get_entry(xz, 0);
    }

    public Float getRefinedGrad(final int[] xz) {
        return get_entry(xz, 4);
    }

    public Float getGradX(final int[] xz) {
        return get_entry(xz, 2);
    }

    public Float getGradY(final int[] xz) {
        return get_entry(xz, 3);
    }

    public Float getRes(final int[] xz) {
        return get_entry(xz, 6);
    }

    public Float getBlurredElev(final int[] xz) {
        return get_entry(xz, 1);
    }

    public double getContinentalElev(final int[] xz) {
        return 0;
    }

    public double getRawTemp(final int[] xz) {
        return 0;
    }

    public Float getRawTempSTD(final int[] xz) {
        return (float) 0;
    }

    public double getRawPrecip(final int[] xz) {
        return 0;
    }

    public Float getRawPrecipSTD(final int[] xz) {
        return (float) 0;
    }

    public int getRawGrad(final int[] xz) {
        return 0;
    }

    public double getBlurredGrad(final int[] xz) {
        return 0;
    }
}
