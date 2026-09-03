package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The radial shape laws. Both must reach zero exactly at the rim, or a bowl steps against the shell
 * it is cut into; the two are otherwise free to differ, and the midpoint test pins that they do.
 */
class RadialProfileTest {

    private static final double DEPTH = 10.0;

    @ParameterizedTest
    @EnumSource(RadialProfile.class)
    void isFlushAtTheRim(RadialProfile profile) {
        assertEquals(0.0, profile.radialDelta(1.0, DEPTH), 1e-12, profile + " steps at the rim");
    }

    @ParameterizedTest
    @EnumSource(RadialProfile.class)
    void bottomsOutAtTheFullDepthInTheCentre(RadialProfile profile) {
        assertEquals(-DEPTH, profile.radialDelta(0.0, DEPTH), 1e-12, profile + " floor is not the depth");
    }

    @ParameterizedTest
    @EnumSource(RadialProfile.class)
    void risesMonotonicallyTowardTheRim(RadialProfile profile) {
        double previous = profile.radialDelta(0.0, DEPTH);
        for (int i = 1; i <= 100; i++) {
            final double current = profile.radialDelta(i / 100.0, DEPTH);
            assertTrue(current >= previous, profile + " fell at r = " + (i / 100.0));
            previous = current;
        }
    }

    @Test
    void theParabolaHoldsItsFloorWiderThanTheCone() {
        // Half way out, the confluence bowl has given up a quarter of its depth and the source cone
        // half of it. This is the whole visual difference between the two families.
        assertEquals(-0.75 * DEPTH, RadialProfile.CONFLUENCE.radialDelta(0.5, DEPTH), 1e-12);
        assertEquals(-0.5 * DEPTH, RadialProfile.SOURCE.radialDelta(0.5, DEPTH), 1e-12);
    }

    @Test
    void tabulatesTheLawOntoTheLutWithTheElevationFoldedIn() {
        // step 1.0, baseIdx 0, radius 4 -> lut[i] is the surface at radius i.
        final float[] lut = new float[8];
        RadialProfile.CONFLUENCE.sampleRadialSection(lut, 5, 1.0, 0, 100.0, 1.0 / 4.0, DEPTH);

        assertEquals(90.0f, lut[0], 1e-4f, "centre is the rim minus the full depth");
        assertEquals(100.0f, lut[4], 1e-4f, "the rim is the elevation itself");
    }

    @Test
    void clampsBeyondTheRimInsteadOfOvershooting() {
        // The carve's AABB is square, so cells past the disc still index the LUT; they must read the
        // rim value rather than a law evaluated outside its domain.
        final float[] lut = new float[8];
        RadialProfile.SOURCE.sampleRadialSection(lut, 7, 1.0, 0, 100.0, 1.0 / 4.0, DEPTH);

        assertEquals(100.0f, lut[6], 1e-4f, "radius 6 on a radius-4 disc must clamp to the rim");
    }
}
