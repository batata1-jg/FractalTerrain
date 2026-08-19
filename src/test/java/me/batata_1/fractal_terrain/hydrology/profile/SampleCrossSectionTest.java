package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the cross-section LUT the grid carve tabulates once per primitive. */
class SampleCrossSectionTest {

    private static final long SEED = 0L;
    private static final double ELEVATION = 100.0;
    private static final double FLOOD_PLAIN_LEN = 1.2;
    private static final double MARGIN_LEN = 1.0;
    private static final double DEPTH = 3.0;
    private static final double CURVATURE = 0.0;

    @Test
    void everyEntryMatchesDeltaAtItsAnchoredPerpDistance() {
        final RosgenProfile profile = RosgenProfile.A;
        final int baseIdx = -5;
        final int n = 12;
        final double step = 1.0;
        final float[] lut = new float[n];

        profile.sampleCrossSection(
                lut, n, step, baseIdx, SEED, ELEVATION, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);

        for (int i = 0; i < n; i++) {
            final double perp = (baseIdx + i) * step;
            final double expected =
                    ELEVATION + profile.delta(SEED, perp, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);
            assertEquals((float) expected, lut[i], "entry " + i + " at perp " + perp);
        }
    }

    @Test
    void writesNothingPastN() {
        // The buffer is a reused, oversized scratch array; the carve must not depend on its tail.
        final float[] lut = new float[16];
        java.util.Arrays.fill(lut, Float.NaN);

        RosgenProfile.C.sampleCrossSection(
                lut, 4, 1.0, 0, SEED, ELEVATION, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);

        for (int i = 4; i < lut.length; i++) {
            assertEquals(Float.NaN, lut[i], "entry " + i + " was overwritten");
        }
    }

    @Test
    void aNegativeBaseIdxSamplesTheNegativeSideOfTheChannel() {
        // baseIdx is floor(perpMin / step) and is normally negative; index 0 is the far bank, not the centre.
        final float[] lut = new float[3];
        RosgenProfile.A.sampleCrossSection(
                lut, 3, 1.0, -1, SEED, ELEVATION, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);

        final double centre =
                ELEVATION + RosgenProfile.A.delta(SEED, 0.0, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);
        assertEquals((float) centre, lut[1], "index 1 should be perp 0");
    }
}
