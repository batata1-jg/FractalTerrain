package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Pins the window samplers to {@code interpolate}, the per-pixel path they replace. Equality is exact,
 * not approximate: both compute the same corners with the same double expressions.
 */
class InterpolationWindowSampleTest {

    private static final float SCALE = 5f;
    private static final int ORIGIN_X = -3;
    private static final int ORIGIN_Z = 7;
    private static final int W = 12;
    private static final int H = 12;

    /** Irregular enough that a transposed or off-by-one index cannot coincide with a correct read. */
    private static float cell(int gx, int gz) {
        return (float) (Math.sin(gx * 0.7) * 13.0 + Math.cos(gz * 1.3) * 7.0 + gx * 0.25 - gz * 0.5);
    }

    private static float[] window() {
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = cell(ORIGIN_X + ix, ORIGIN_Z + iz);
        }
        return d;
    }

    /** The legacy per-pixel corner reader, over the same field. */
    private static final Function<int[], Float> POINT_SOURCE = p -> cell(p[1], p[2]);

    @Test
    void bilinearMatchesTheLegacyPerPixelPath() {
        final float[] d = window();
        for (int blockX = -14; blockX <= 40; blockX++) {
            for (int blockZ = 36; blockZ <= 90; blockZ++) {
                final float px = blockX / SCALE;
                final float pz = blockZ / SCALE;
                final double legacy = Interpolation.interpolateBilinear(px, pz, new int[3], new float[4], POINT_SOURCE);
                assertEquals(
                        legacy,
                        Interpolation.sampleWindowBilinear(d, px, pz, ORIGIN_X, ORIGIN_Z, H),
                        0.0,
                        "block (" + blockX + "," + blockZ + ")");
            }
        }
    }

    @Test
    void smoothStepMatchesTheLegacyPerPixelPath() {
        final float[] d = window();
        for (int blockX = -14; blockX <= 40; blockX++) {
            for (int blockZ = 36; blockZ <= 90; blockZ++) {
                final float px = blockX / SCALE;
                final float pz = blockZ / SCALE;
                final double legacy =
                        Interpolation.interpolateSmoothStep(px, pz, new int[3], new float[4], POINT_SOURCE);
                assertEquals(
                        legacy,
                        Interpolation.sampleWindowSmoothStep(d, px, pz, ORIGIN_X, ORIGIN_Z, H),
                        0.0,
                        "block (" + blockX + "," + blockZ + ")");
            }
        }
    }

    @Test
    void anExactPixelReadsOneColumnAndOneRow() {
        // blockX 15 / 5 == 3.0 exactly: floor == ceil, so the sampler must not reach px 4.
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = (ORIGIN_X + ix == 4) ? Float.NaN : 1f;
        }
        assertEquals(1.0, Interpolation.sampleWindowBilinear(d, 15 / SCALE, 40 / SCALE, ORIGIN_X, ORIGIN_Z, H), 0.0);
    }
}
