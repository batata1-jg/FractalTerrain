package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins {@code sampleWindowAbs}/{@code sampleWindowSignum} to the two {@link Interpolation}s the
 * weirdness density used to walk separately.
 *
 * <p>Weirdness is the one channel whose transform is corner-wise: {@code abs} and {@code signum} apply
 * to each of the four nodes <em>before</em> the lerp, not to the interpolated result. A sampler that
 * transformed afterwards would agree almost everywhere and diverge exactly where the sign flips, so
 * equality here is exact rather than approximate.
 */
class InterpolationSignedWindowTest {

    private static final float SCALE = 5f;
    private static final int ORIGIN_X = -3;
    private static final int ORIGIN_Z = 7;

    /** Sized from the sample loop below: it needs global pixels x -3..8 and z 7..18. */
    private static final int W = 12;

    private static final int H = 12;

    /** Crosses zero on both axes, so the sign interpolation actually flips inside the sampled range. */
    private static float cell(int gx, int gz) {
        return (float) (Math.sin(gx * 0.7) * 13.0 + Math.cos(gz * 1.3) * 7.0);
    }

    private static float[] window() {
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = cell(ORIGIN_X + ix, ORIGIN_Z + iz);
        }
        return d;
    }

    /** The per-point path: magnitude and sign interpolated by two independent {@link Interpolation}s. */
    private static final Interpolation MAGNITUDE = new Interpolation(SCALE, p -> Math.abs(cell(p[1], p[2])));

    private static final Interpolation SIGN = new Interpolation(SCALE, p -> Math.signum(cell(p[1], p[2])));

    private static double legacy(int blockX, int blockZ) {
        if (SIGN.interpolateBilinear(blockX, blockZ) >= 0) return MAGNITUDE.interpolateBilinear(blockX, blockZ);
        return -MAGNITUDE.interpolateBilinear(blockX, blockZ);
    }

    private static double fresh(float[] d, int blockX, int blockZ) {
        final float px = blockX / SCALE;
        final float pz = blockZ / SCALE;
        final double magnitude = Interpolation.sampleWindowAbs(d, px, pz, ORIGIN_X, ORIGIN_Z, H);
        final double sign = Interpolation.sampleWindowSignum(d, px, pz, ORIGIN_X, ORIGIN_Z, H);
        return (sign >= 0) ? magnitude : -magnitude;
    }

    @Test
    void windowSamplersMatchTheTwoInterpolationPath() {
        final float[] d = window();
        for (int blockX = -14; blockX <= 40; blockX++) {
            for (int blockZ = 36; blockZ <= 90; blockZ++) {
                assertEquals(
                        legacy(blockX, blockZ), fresh(d, blockX, blockZ), 0.0, "block (" + blockX + "," + blockZ + ")");
            }
        }
    }

    @Test
    void theSampledRangeContainsASignFlip() {
        // Guards the test above: with no flip it would pass even for a sampler that ignored the sign.
        boolean positive = false;
        boolean negative = false;
        for (int blockX = -14; blockX <= 40; blockX++) {
            for (int blockZ = 36; blockZ <= 90; blockZ++) {
                if (legacy(blockX, blockZ) >= 0) positive = true;
                else negative = true;
            }
        }
        assertTrue(positive && negative, "expected both signs in the sampled range");
    }

    @Test
    void absIsAppliedPerCornerNotToTheResult() {
        // Corners straddling zero: transforming after the lerp averages +/-4 to 0, while the corner-wise
        // path averages the magnitudes to 4. This is the difference the whole split exists for.
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = ((ix + iz) % 2 == 0) ? 4f : -4f;
        }
        // Mid-cell, so all four corners carry weight.
        assertEquals(4.0, Interpolation.sampleWindowAbs(d, 0.5f, 10.5f, ORIGIN_X, ORIGIN_Z, H), 0.0);
    }

    @Test
    void anExactPixelReadsOneColumnAndOneRow() {
        // 15 / 5 == 3.0 exactly: floor == ceil, so neither sampler may reach px 4.
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = (ORIGIN_X + ix == 4) ? Float.NaN : 1f;
        }
        assertEquals(1.0, Interpolation.sampleWindowAbs(d, 15 / SCALE, 40 / SCALE, ORIGIN_X, ORIGIN_Z, H), 0.0);
        assertEquals(1.0, Interpolation.sampleWindowSignum(d, 15 / SCALE, 40 / SCALE, ORIGIN_X, ORIGIN_Z, H), 0.0);
    }
}
