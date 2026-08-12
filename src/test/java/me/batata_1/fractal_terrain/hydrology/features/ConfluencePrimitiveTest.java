package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import org.junit.jupiter.api.Test;

/** Unit tests for the angular-bracket blend, radial elevation lerp, falloff and persistence of {@link ConfluencePrimitive}. */
class ConfluencePrimitiveTest {

    @Test
    void bracketPicksAngularNeighborsNotNearestTwo() {
        // If bracket selection fell back to array order or to nearest-by-magnitude instead of the
        // signed wrapped angle, a query near the 0/10 pair would wrongly blend those two arms instead
        // of the pair that actually straddles it (10 and 180).
        final double[] angles = {0.0, Math.toRadians(10), Math.toRadians(180)};
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                100.0,
                100.0,
                angles,
                new double[] {0.0, 0.0, 0.0},
                new double[] {10.0, 10.0, 10.0},
                new double[] {1000.0, 2000.0, -500.0},
                new RosgenType[] {RosgenType.A, RosgenType.A, RosgenType.A});

        final double[] pt = {0.0, 50.0};
        final double dx = pt[0] - primitive.coord()[0];
        final double dz = pt[1] - primitive.coord()[1];
        final double r = Math.sqrt(dx * dx + dz * dz);
        final double theta = Math.atan2(dz, dx);

        // True angular bracket: arm 1 (10 deg) is the closest positive-side neighbor and arm 2
        // (180 deg) is the only negative-side candidate.
        final double dArm1 = r * Math.sin(theta - angles[1]);
        final double dArm2 = r * Math.sin(theta - angles[2]);
        final double elevArm1 = 100.0 + (2000.0 - 100.0) * (r / 100.0);
        final double elevArm2 = 100.0 + (-500.0 - 100.0) * (r / 100.0);
        final double hArm1 = elevArm1 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm1, 10.0, 0.0);
        final double hArm2 = elevArm2 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm2, 10.0, 0.0);
        final double expected =
                (Math.abs(dArm2) * hArm1 + Math.abs(dArm1) * hArm2) / (Math.abs(dArm1) + Math.abs(dArm2));

        assertEquals(expected, primitive.h(pt), 1e-9);

        // Sanity: blending the wrong (0 deg, 10 deg) pair lands far from the true answer, so these
        // rim values actually discriminate between the two candidate brackets.
        final double dArm0 = r * Math.sin(theta - angles[0]);
        final double elevArm0 = 100.0 + (1000.0 - 100.0) * (r / 100.0);
        final double hArm0 = elevArm0 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm0, 10.0, 0.0);
        final double wrongBlend =
                (Math.abs(dArm1) * hArm0 + Math.abs(dArm0) * hArm1) / (Math.abs(dArm0) + Math.abs(dArm1));
        assertTrue(Math.abs(expected - wrongBlend) > 100.0);
    }

    @Test
    void bracketWrapsPastPiWhenEveryArmIsOnOneSide() {
        // A bearing inside a gap wider than pi puts every arm on the same signed side. The far
        // bracket is then only reachable by wrapping past +-pi; a search that gives up and reuses the
        // near arm would drop the blend entirely and stamp one arm's cross-section over the gap.
        final double[] angles = {0.0, Math.toRadians(30), Math.toRadians(60)};
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                100.0,
                100.0,
                angles,
                new double[] {0.0, 0.0, 0.0},
                new double[] {10.0, 10.0, 10.0},
                new double[] {0.0, 5000.0, 200.0},
                new RosgenType[] {RosgenType.A, RosgenType.A, RosgenType.A});

        // Bearing 120 deg sits in the 60->360 deg gap, so its true neighbours are arm 2 (60 deg)
        // and arm 0 (0 deg == 360 deg). Radius == influence, so the elevation lerp lands on the rims.
        final double[] pt = {100.0 * Math.cos(Math.toRadians(120)), 100.0 * Math.sin(Math.toRadians(120))};
        final double r = Math.hypot(pt[0], pt[1]);
        final double theta = Math.atan2(pt[1], pt[0]);

        final double dArm0 = r * Math.sin(theta - angles[0]);
        final double dArm2 = r * Math.sin(theta - angles[2]);
        final double hArm0 = 0.0 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm0, 10.0, 0.0);
        final double hArm2 = 200.0 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm2, 10.0, 0.0);
        final double expected =
                (Math.abs(dArm2) * hArm0 + Math.abs(dArm0) * hArm2) / (Math.abs(dArm0) + Math.abs(dArm2));

        assertEquals(expected, primitive.h(pt), 1e-9);
        // Collapsing onto the near arm alone would land 100 away, so this case discriminates.
        assertTrue(Math.abs(expected - hArm2) > 50.0);
    }

    @Test
    void bracketWrapsAcrossPiSeam() {
        // A bracket search that compares raw angle values instead of wrapping the difference would
        // treat 170 deg and -170 deg as far apart, when they are actually 20 deg apart across the seam.
        final double[] angles = {Math.toRadians(170), Math.toRadians(-170)};
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                100.0,
                50.0,
                angles,
                new double[] {0.0, 0.0},
                new double[] {8.0, 8.0},
                new double[] {300.0, -100.0},
                new RosgenType[] {RosgenType.A, RosgenType.A});

        final double[] pt = {-40.0, 0.0};
        final double dx = pt[0] - primitive.coord()[0];
        final double dz = pt[1] - primitive.coord()[1];
        final double r = Math.sqrt(dx * dx + dz * dz);
        final double theta = Math.atan2(dz, dx);

        // theta - angles[0] is already +10 deg, within (-PI, PI]. theta - angles[1] is +350 deg,
        // which wraps to -10 deg across the seam -- the true bracket, not a seam fallback.
        final double dArm0 = r * Math.sin(theta - angles[0]);
        final double wrappedDeltaArm1 = (theta - angles[1]) - 2 * Math.PI;
        final double dArm1 = r * Math.sin(wrappedDeltaArm1);
        final double elevArm0 = 50.0 + (300.0 - 50.0) * (r / 100.0);
        final double elevArm1 = 50.0 + (-100.0 - 50.0) * (r / 100.0);
        final double hArm0 = elevArm0 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm0, 8.0, 0.0);
        final double hArm1 = elevArm1 + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), dArm1, 8.0, 0.0);
        final double expected =
                (Math.abs(dArm1) * hArm0 + Math.abs(dArm0) * hArm1) / (Math.abs(dArm0) + Math.abs(dArm1));

        assertEquals(expected, primitive.h(pt), 1e-9);
    }

    @Test
    void blendCollapsesOntoOwnArmProfileWhenOnItsRay() {
        // A point exactly on one arm's ray has zero perpendicular distance to that arm, so the blend
        // weight on the other bracket arm must go to zero -- otherwise the confluence surface would
        // show a seam discontinuity exactly along each arm's centerline.
        final double[] angles = {0.0, Math.toRadians(50), Math.toRadians(220)};
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                100.0,
                40.0,
                angles,
                new double[] {0.3, -0.2, 0.1},
                new double[] {6.0, 12.0, 9.0},
                new double[] {90.0, -9999.0, 9999.0},
                new RosgenType[] {RosgenType.A, RosgenType.B, RosgenType.A});

        final double r = 25.0;
        final double[] pt = {r * Math.cos(angles[0]), r * Math.sin(angles[0])};

        final double expectedElev = 40.0 + (90.0 - 40.0) * (r / 100.0);
        final double expected =
                expectedElev + RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), 0.0, 6.0, 0.3);

        assertEquals(expected, primitive.h(pt), 1e-9);
    }

    @Test
    void radialElevationLerpHitsJunctionAtCentreAndRimAtInfluence() {
        // The shell's radial blend must anchor exactly at the two ends of its range: the junction
        // elevation right at the confluence center, and each arm's own rim right at the influence
        // edge -- otherwise the confluence patch would not stitch seamlessly onto the channel it
        // interrupts.
        final double[] angles = {0.0, Math.toRadians(120), Math.toRadians(240)};
        final double width = 10.0, curvature = 0.0;
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                50.0,
                100.0,
                angles,
                new double[] {curvature, curvature, curvature},
                new double[] {width, width, width},
                new double[] {180.0, 20.0, 65.0},
                new RosgenType[] {RosgenType.A, RosgenType.A, RosgenType.A});

        // Near the centre, off any arm's ray: both bracket arms' perpendicular distance is tiny,
        // well inside the channel margin, so the profile delta each contributes is the same shared
        // constant and the remaining blended term isolates the radial elevation lerp itself.
        final double tinyR = 1e-11;
        final double[] nearCentre = {tinyR * Math.cos(Math.toRadians(45)), tinyR * Math.sin(Math.toRadians(45))};
        final double deltaWithinMargin =
                RosgenProfile.of(RosgenType.A).delta(primitive.hashCode(), 0.0, width, curvature);
        assertEquals(100.0, primitive.h(nearCentre) - deltaWithinMargin, 1e-9);

        // On an arm's ray at r == influence: the point collapses onto that arm alone, so its
        // elevation term is exactly that arm's rim.
        final double[] onArm1AtInfluence = {50.0 * Math.cos(angles[1]), 50.0 * Math.sin(angles[1])};
        assertEquals(20.0, primitive.h(onArm1AtInfluence) - deltaWithinMargin, 1e-9);
    }

    @Test
    void wFalloffIsOneAtCentreZeroBeyondInfluenceAndMonotonic() {
        // w gates how strongly a confluence primitive pulls the carve at a pixel; a falloff that
        // doesn't reach exactly 1 at the centre or exactly 0 at the influence edge would leave a
        // seam where the confluence patch meets the surrounding channel/valley blend.
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                20.0,
                0.0,
                new double[] {0.0, Math.PI},
                new double[] {0.0, 0.0},
                new double[] {5.0, 5.0},
                new double[] {0.0, 0.0},
                new RosgenType[] {RosgenType.A, RosgenType.A});

        assertEquals(1.0, primitive.w(new double[] {0.0, 0.0}), 1e-9);
        assertEquals(0.0, primitive.w(new double[] {20.0, 0.0}), 1e-9);
        assertEquals(0.0, primitive.w(new double[] {30.0, 0.0}), 1e-9);

        final double r = 12.0;
        final double u = (r * r) / (20.0 * 20.0);
        final double expectedInterior = (1 - u) * (1 - u);
        assertEquals(expectedInterior, primitive.w(new double[] {r, 0.0}), 1e-9);

        double previous = 1.0;
        for (double radius = 0.0; radius <= 25.0; radius += 1.0) {
            final double current = primitive.w(new double[] {radius, 0.0});
            assertTrue(current <= previous + 1e-12, "w must not increase as radius grows: " + radius);
            previous = current;
        }
    }

    @Test
    void serializationRoundTripPreservesArrayContentsAndNullRosgenType() {
        // The primitive index persists every feature through PrimitiveCodec; a length/order mistake
        // here would corrupt a cached tile silently, only surfacing as a wrong carve on the next load.
        final ConfluencePrimitive primitive = new ConfluencePrimitive(
                new double[] {12.5, -7.25},
                33.0,
                77.5,
                new double[] {0.1, 1.2, 2.3},
                new double[] {-0.5, 0.0, 0.75},
                new double[] {4.0, 5.5, 6.25},
                new double[] {10.0, 20.0, 30.0},
                new RosgenType[] {RosgenType.B, null, RosgenType.C});

        final byte[] body = primitive.serializePrimitive();
        assertEquals(primitive.primitiveByteSize(), (long) body.length);

        final ConfluencePrimitive roundTripped = (ConfluencePrimitive) primitive.deserializePrimitive(body);
        assertEquals(primitive, roundTripped);
        assertArrayEquals(primitive.coord(), roundTripped.coord());
        assertArrayEquals(primitive.angles(), roundTripped.angles());
        assertArrayEquals(primitive.curvatures(), roundTripped.curvatures());
        assertArrayEquals(primitive.widths(), roundTripped.widths());
        assertArrayEquals(primitive.rimElevations(), roundTripped.rimElevations());
        assertArrayEquals(primitive.rosgenTypes(), roundTripped.rosgenTypes());
        assertNull(roundTripped.rosgenTypes()[1]);

        final byte[] full = primitive.serialize();
        final HydrologicalPrimitive fullRoundTripped = primitive.deserialize(full);
        assertEquals(primitive, fullRoundTripped);
    }

    @Test
    void equalsAndHashCodeCompareContentsNotReferences() {
        // Records compare array fields by reference; without a content-based override, two
        // primitives rebuilt from the same serialized bytes (a fresh array each time) would never
        // register as equal, breaking any cache or dedup keyed on the primitive.
        final ConfluencePrimitive a = new ConfluencePrimitive(
                new double[] {1.0, 2.0},
                10.0,
                5.0,
                new double[] {0.0, 1.0},
                new double[] {0.1, 0.2},
                new double[] {3.0, 4.0},
                new double[] {6.0, 7.0},
                new RosgenType[] {RosgenType.A, RosgenType.B});
        final ConfluencePrimitive b = new ConfluencePrimitive(
                new double[] {1.0, 2.0},
                10.0,
                5.0,
                new double[] {0.0, 1.0},
                new double[] {0.1, 0.2},
                new double[] {3.0, 4.0},
                new double[] {6.0, 7.0},
                new RosgenType[] {RosgenType.A, RosgenType.B});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        final ConfluencePrimitive changed = new ConfluencePrimitive(
                a.coord(),
                a.influence(),
                a.junctionElevation(),
                a.angles(),
                a.curvatures(),
                a.widths(),
                new double[] {6.0, 999.0},
                a.rosgenTypes());
        assertNotEquals(a, changed);
    }

    @Test
    void degenerateArmCountsDoNotThrow() {
        // Confluences with one incident arm (a dead-end capture) or zero arms (an orphaned junction
        // node mid-network-edit) must still be safe to query, since the spatial index does not filter
        // by arm count before calling h/w/d.
        final ConfluencePrimitive single = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                10.0,
                5.0,
                new double[] {0.0},
                new double[] {0.0},
                new double[] {2.0},
                new double[] {8.0},
                new RosgenType[] {RosgenType.A});
        assertDoesNotThrow(() -> single.h(new double[] {1.0, 1.0}));
        assertDoesNotThrow(() -> single.w(new double[] {1.0, 1.0}));
        assertDoesNotThrow(() -> single.d(new double[] {1.0, 1.0}));

        final ConfluencePrimitive empty = new ConfluencePrimitive(
                new double[] {0.0, 0.0},
                10.0,
                5.0,
                new double[0],
                new double[0],
                new double[0],
                new double[0],
                new RosgenType[0]);
        assertDoesNotThrow(() -> empty.h(new double[] {1.0, 1.0}));
        assertDoesNotThrow(() -> empty.w(new double[] {1.0, 1.0}));
        assertDoesNotThrow(() -> empty.d(new double[] {1.0, 1.0}));
        assertEquals(5.0, empty.h(new double[] {1.0, 1.0}), 1e-9);
    }
}
