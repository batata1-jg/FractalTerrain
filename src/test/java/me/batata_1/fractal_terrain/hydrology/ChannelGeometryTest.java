package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for the cross-section geometry laws. */
class ChannelGeometryTest {

    @Test
    void widthDepthRatioCrossesTwelveAtReferenceWidth() {
        // W_REF is defined as the width at which the narrow-deep / wide-shallow
        // boundary (W/D = 12) falls; this is the single calibration knob.
        assertEquals(12.0, ChannelGeometry.widthDepthRatio(ChannelGeometry.W_REF), 1e-9);
    }

    @Test
    void widthDepthRatioPinsTheExponent() {
        // widthDepthRatioCrossesTwelveAtReferenceWidth pins the coefficient only — at width == W_REF the
        // ratio is 1^anything == 12 regardless of the exponent. This pins WD_EXPONENT's magnitude too.
        assertEquals(12.0 * Math.pow(2.0, 0.278), ChannelGeometry.widthDepthRatio(8.0), 1e-9);
    }

    @Test
    void widthDepthRatioIsMonotoneIncreasing() {
        double previous = ChannelGeometry.widthDepthRatio(0.2);
        for (double w = 0.4; w <= 16.0; w += 0.2) {
            final double current = ChannelGeometry.widthDepthRatio(w);
            assertTrue(current > previous, "W/D must increase with width at w=" + w);
            previous = current;
        }
    }

    @Test
    void widthDepthRatioIsFiniteAtAndBelowTheWidthFloor() {
        assertTrue(Double.isFinite(ChannelGeometry.widthDepthRatio(0.0)));
        assertTrue(ChannelGeometry.widthDepthRatio(0.0) > 0.0);
    }

    @Test
    void depthForWidthRemainsUntouchedAcrossTheRepresentableRange() {
        // Guard: depthForWidth is floored at 1.0 for every representable width and
        // feeds the meander migration rate. widthDepthRatio must not have changed it.
        assertEquals(1.0, ChannelGeometry.depthForWidth(0.2), 1e-9);
        assertEquals(1.0, ChannelGeometry.depthForWidth(16.0), 1e-9);
    }
}
