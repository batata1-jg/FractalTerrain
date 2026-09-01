package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for the cross-section geometry laws. */
class ChannelGeometryTest {

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
        assertTrue(ChannelGeometry.widthDepthRatio(0.0) >= 0.0);
    }
}
