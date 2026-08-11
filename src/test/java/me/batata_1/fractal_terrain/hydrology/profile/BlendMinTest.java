package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the carve blend, and for the invariant that a river only ever lowers ground. */
class BlendMinTest {

    private static final double RANGE = 4.0;

    @Test
    void isExactlyMinWhenInputsAreFartherApartThanTheRange() {
        // This is the property RosgenProfile.smoothMin lacks: terrain the river cannot reach must
        // come back bit-identical, not sunk by a constant.
        assertEquals(10.0, RosgenProfile.blendMin(10.0, 100.0, RANGE), 0.0);
        assertEquals(10.0, RosgenProfile.blendMin(100.0, 10.0, RANGE), 0.0);
        assertEquals(10.0, RosgenProfile.blendMin(10.0, 14.0, RANGE), 0.0);
    }

    @Test
    void neverExceedsTheHardMin() {
        for (double a = -10.0; a <= 10.0; a += 0.5) {
            for (double b = -10.0; b <= 10.0; b += 0.5) {
                assertTrue(RosgenProfile.blendMin(a, b, RANGE) <= Math.min(a, b) + 1e-12);
            }
        }
    }

    @Test
    void isContinuousAtTheRangeBoundary() {
        final double justInside = RosgenProfile.blendMin(10.0, 10.0 + RANGE - 1e-9, RANGE);
        final double justOutside = RosgenProfile.blendMin(10.0, 10.0 + RANGE + 1e-9, RANGE);
        assertEquals(justInside, justOutside, 1e-6);
    }

    @Test
    void isSymmetric() {
        assertEquals(RosgenProfile.blendMin(3.0, 5.0, RANGE), RosgenProfile.blendMin(5.0, 3.0, RANGE), 1e-12);
    }

    @Test
    void aRiverNeverRaisesTerrain() {
        // The invariant the whole composition exists to enforce, swept across the profile's zones.
        final NearestChannelSample sample = new NearestChannelSample(0.0, 6.0, 0.0, 50.0, RosgenType.C, 1);
        for (double ambient = 40.0; ambient <= 90.0; ambient += 1.0) {
            for (double dist = -40.0; dist <= 40.0; dist += 0.5) {
                final NearestChannelSample at = new NearestChannelSample(dist, 6.0, 0.0, 50.0, RosgenType.C, 1);
                assertTrue(
                        at.carveInto(ambient) <= ambient + 1e-9,
                        "carve raised terrain at dist=" + dist + " ambient=" + ambient);
            }
        }
        assertTrue(sample.carveInto(90.0) < 90.0, "a river directly underfoot must cut down");
    }

    @Test
    void terrainFarFromTheChannelIsReturnedUntouched() {
        // 100 relief pixels out the valley cone has climbed far above ambient, so min picks ambient
        // and blendMin must hand it back bit-identical rather than sinking it. Ambient is chosen
        // just above the bed so the cone clears it by far more than CARVE_BLEND_RANGE, without this
        // test needing to know RosgenProfile.C's exact floodPlainLength.
        final NearestChannelSample far = new NearestChannelSample(100.0, 6.0, 0.0, 50.0, RosgenType.C, 1);
        assertEquals(51.0, far.carveInto(51.0), 0.0);
    }
}
