package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Analytic tests for the raster side of classification. Every fixture is a synthetic field whose
 * expected answer is derived by hand, so a failure means the sampler is wrong rather than that a
 * captured expectation went stale.
 */
class ReachMetricsSamplerTest {

    private static final int SIDE = 128;

    /** Symmetric V-valley along z, giving a known entrenchment ratio to measure against. */
    private static float[] vValley(double gradient) {
        final float[] elev = new float[SIDE * SIDE];
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                elev[x * SIDE + z] = (float) (Math.abs(x - SIDE / 2.0) * gradient);
            }
        }
        return elev;
    }

    @Test
    void transectOnAVValleyRecoversTheAnalyticFloodProneWidth() {
        // Flood-prone stage sits at bed + 2*dMax. With bed = 0 and a gradient of 1.0 per px, the
        // stage elevation equals the horizontal distance at which the walk stops, so the flood-prone
        // half-width equals the stage and the full width is twice it.
        final double gradient = 1.0;
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(gradient), SIDE);
        final double width = 4.0;
        final double[] point = {SIDE / 2.0, SIDE / 2.0};
        final double[] normal = {1.0, 0.0}; // across the valley
        final double er = sampler.entrenchmentRatio(point, normal, 0.0, width);

        final double stage = 2.0
                * me.batata_1.fractal_terrain.config.HydrologyTuning.DEPTH_MAX_FACTOR
                * me.batata_1.fractal_terrain.hydrology.ChannelGeometry.depthForWidth(width);
        final double expected = (2.0 * stage / gradient) / width;
        // One transect step of tolerance: the walk stops at the first sample above the stage.
        final double step = Math.max(
                me.batata_1.fractal_terrain.config.HydrologyTuning.ER_STEP_MIN,
                width * me.batata_1.fractal_terrain.config.HydrologyTuning.ER_STEP_WIDTH_FRACTION);
        assertEquals(expected, er, 2.0 * step / width);
    }

    @Test
    void transectOnAFlatFieldSaturatesToInfinity() {
        // A perfectly flat plain never exceeds the flood-prone stage, so the walk runs to its bound.
        // That is the correct semantic for a broad flat valley, not a failure.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(new float[SIDE * SIDE], SIDE);
        final double er = sampler.entrenchmentRatio(new double[] {64.0, 64.0}, new double[] {1.0, 0.0}, 0.0, 4.0);
        assertTrue(Double.isInfinite(er), "a saturated transect reports ER = +inf, got " + er);
    }

    @Test
    void narrowGorgeGivesAnEntrenchedRatio() {
        // A steep gorge: the walk terminates almost immediately, so ER lands under the entrenched
        // threshold.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(50.0), SIDE);
        final double er = sampler.entrenchmentRatio(new double[] {64.0, 64.0}, new double[] {1.0, 0.0}, 0.0, 4.0);
        assertTrue(
                er < me.batata_1.fractal_terrain.config.HydrologyTuning.ER_ENTRENCHED,
                "a gorge must classify as entrenched, got ER = " + er);
    }

    @Test
    void narrowestChannelStillSamplesTheTerrain() {
        // At the MIN_WIDTH clamp floor, maxWalk (ER_WALK_WIDTHS * width) is shorter than one
        // ER_STEP_MIN-floored step, so the old step formula made the walk loop run zero iterations and
        // report every such reach as unconfined (+inf), regardless of terrain. The fixed step shrinks
        // to guarantee at least ER_MIN_STEPS_PER_SIDE samples per side, so a real gorge at this width
        // must still classify as entrenched.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(50.0), SIDE);
        final double er = sampler.entrenchmentRatio(
                new double[] {64.0, 64.0},
                new double[] {1.0, 0.0},
                0.0,
                me.batata_1.fractal_terrain.config.HydrologyTuning.MIN_WIDTH);
        assertTrue(Double.isFinite(er), "a narrow-width transect must sample the terrain, got ER = " + er);
        assertTrue(
                er < me.batata_1.fractal_terrain.config.HydrologyTuning.ER_ENTRENCHED,
                "a gorge at MIN_WIDTH must classify as entrenched, got ER = " + er);
    }

    @Test
    void transectNeverReadsOutsideTheBuffer() {
        // A channel sitting on the very edge of the buffer must not overrun. sampleBilinear clamps,
        // so this asserts the walk terminates and returns a usable number rather than looping.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(1.0), SIDE);
        final double er = sampler.entrenchmentRatio(new double[] {0.0, 0.0}, new double[] {1.0, 0.0}, 0.0, 16.0);
        assertTrue(er >= 0.0, "edge transect must return a non-negative ratio, got " + er);
    }

    //    @Test
    //    void slopeOnAConstantGradientBedIsThatGradient() {
    //        final double[] bed = new double[21];
    //        for (int i = 0; i < bed.length; i++) bed[i] = 100.0 - i * 0.5; // drops 0.5 per point
    //        // 20 intervals of 0.5 elevation over an arc length of 40 px -> slope 0.25.
    //        assertEquals(0.25, ReachMetricsSampler.slope(bed, 40.0, 0, 20), 1e-9);
    //    }

    //    @Test
    //    void slopeIsNeverNegative() {
    //        // ChannelElevationAssigner forces beds monotone non-increasing downstream, but a degenerate
    //        // reach must still not produce a negative slope that would skip the Aa+/A tests.
    //        final double[] bed = {10.0, 20.0};
    //        assertEquals(0.0, ReachMetricsSampler.slope(bed, 5.0, 0, 1), 1e-9);
    //    }
    //
    //    @Test
    //    void slopeOnAZeroLengthReachIsZero() {
    //        final double[] bed = {10.0, 5.0};
    //        assertEquals(0.0, ReachMetricsSampler.slope(bed, 0.0, 0, 1), 1e-9);
    //    }
}
