package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.math.VectorOps;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the lattice carve. The geometry is chosen so every assertion lands on an exact LUT
 * entry: normal (1,0) makes perp == (x - cx), and resolution 1.0 with integer coords makes every
 * sampled perp an integer multiple of the LUT step, so linear interpolation is exact.
 */
class ComputeRiverGridTest {

    private static final int GRID = 16;
    private static final double RES = 1.0;
    private static final int POINTS = GRID * GRID;

    /** A straight knot whose normal points along +X, centred on the lattice. */
    private static RiverPrimitive knot(double cx, double elevation, RosgenType type, long ids) {
        return new RiverPrimitive(new double[] {cx, 8.0}, 5.0, type, new double[] {1.0, 0.0}, 0.0, 2.0, elevation, ids);
    }

    private static RiverInfluenceCarve.GridBuffers buffers() {
        final RiverInfluenceCarve.GridBuffers b = new RiverInfluenceCarve.GridBuffers();
        b.ensure(GRID, RiverInfluenceCarve.maxLutLen(GRID, RES));
        return b;
    }

    private static int idx(int row, int col) {
        return row * GRID + col;
    }

    @Test
    void carvesTheChannelCentreToTheProfileSurface() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        // perp == 0 and tang == 0 at (8, 8): the primitive owns the cell outright.
        final int centre = idx(8, 8);
        assertEquals(1.0f, b.acc[3 * centre + 2], 1e-6f, "weight at the centre");
        // RosgenProfile.delta returns a flat -10 inside marginLen (width 2 -> marginLen 1).
        final double[] point = {8.0, 8.0};
        final double signedPerpDist = VectorOps.dot(river.normal(), VectorOps.sub(point, river.coord()));
        assertEquals(river.h(signedPerpDist), b.acc[3 * centre], 1e-4f, "height at the centre");
    }

    @Test
    void leavesLatticePointsOutsideTheInfluenceUntouched() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        // influence is 5, so (0, 0) is out of range on both axes.
        final int corner = idx(0, 0);
        assertEquals(0.0f, b.acc[3 * corner + 2], "weight in the corner");
        assertEquals(0.0f, b.acc[3 * corner], "height in the corner");
    }

    @Test
    void theNearerPrimitiveWinsRegardlessOfItsElevation() {
        // Both reach (9, 8); B sits on it, A is one pixel away. B must own the cell even though it is
        // the higher primitive -- the merge is distance-driven, not elevation-driven.
        final RiverPrimitive a = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive b2 = knot(9.0, 200.0, RosgenType.A, 1L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(a, b2),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        final double[] point = {9.0, 8.0};
        final double signedPerpDist = VectorOps.dot(b2.normal(), VectorOps.sub(point, b2.coord()));
        assertEquals(b2.h(signedPerpDist), b.acc[3 * idx(9, 8)], 1e-4f);
    }

    @Test
    void theNearerPrimitiveWinsEvenWhenProcessedFirstWithTheLowerElevation() {
        // Deconfounds distance from list order and from elevation: nearer is FIRST and LOWER here, so a
        // last-wins or higher-wins implementation would report 190 (from farther) instead of 90 (nearer).
        final RiverPrimitive nearer = knot(9.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive farther = knot(8.0, 200.0, RosgenType.A, 1L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(nearer, farther),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        final double[] point = {9.0, 8.0};
        final double signedPerpDist = VectorOps.dot(nearer.normal(), VectorOps.sub(point, nearer.coord()));
        assertEquals(nearer.h(signedPerpDist), b.acc[3 * idx(9, 8)], 1e-4f);
    }

    @Test
    void reseedsSoASecondCallDoesNotCompound() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        final List<HydrologicalPrimitive> one = List.of(knot(8.0, 100.0, RosgenType.A, 0L));

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                one,
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);
        final float first = b.acc[3 * idx(8, 8)];
        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                one,
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(first, b.acc[3 * idx(8, 8)], "buffers are reused across calls and must be reseeded");
    }

    @Test
    void stopsAtTheFirstNonRiverPrimitiveAndReportsWhere() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologicalPrimitive source =
                new me.batata_1.fractal_terrain.hydrology.features.SourcePrimitive(new double[] {8.0, 8.0}, 2.0, 100.0);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        final int stop = RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river, source),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        // The return value bounds the RIVER run only; the source is carved by the radial pass that
        // runs after it, not skipped.
        assertEquals(1, stop, "the river run ends at index 1");
    }

    @Test
    void skipsAPrimitiveWithNoTangent() {
        // A null normal has no cross-section; carving it would NPE in the projection.
        final RiverPrimitive noNormal =
                new RiverPrimitive(new double[] {8.0, 8.0}, 5.0, RosgenType.A, null, 0.0, 2.0, 100.0, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(noNormal),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(0.0f, b.acc[3 * idx(8, 8) + 2]);
    }

    @Test
    void lutIsLongEnoughForAPrimitiveSpanningTheWholeDiagonal() {
        // A 45-degree normal maximises the reachable perp range; the LUT must not overflow.
        final RiverPrimitive diagonal = new RiverPrimitive(
                new double[] {8.0, 8.0},
                64.0,
                RosgenType.A,
                new double[] {Math.sqrt(0.5), Math.sqrt(0.5)},
                0.0,
                2.0,
                100.0,
                0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(diagonal),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertTrue(b.acc[3 * idx(8, 8) + 2] > 0.0f, "the diagonal primitive should still carve");
    }

    @Test
    void mergesTheWaterSurfaceWithoutNormalisingIt() {
        // waterLine(2.0) is -2, so the surface sits two below the primitive's own elevation. The water
        // lane blends toward a default of 0, which makes the raw accumulator the answer -- no divide.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(98.0f, b.acc[3 * idx(8, 8) + 1], 1e-4f, "water surface at the centre");
        assertEquals(0.0f, b.acc[3 * idx(0, 0) + 1], "water surface out of range");
    }

    @Test
    void stampsTheNearestPrimitivesFamilyAndRosgenType() {
        // RosgenType.C so the packed value is non-zero -- RIVER + A packs to 0L and would not
        // distinguish a real stamp from an unwritten cell.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.C, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        final long packed = b.typeMask[idx(8, 8)];
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(packed));
        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(packed));
        assertEquals(HydrologicalFeature.NONE, b.typeMask[idx(0, 0)], "nothing reached the corner");
    }

    @Test
    void anUnclassifiedReachStampsTheProfileItActuallyCarvedWith() {
        // A null rosgenType coalesces to A for the carve, so the mask must say A rather than "unknown".
        final RiverPrimitive river = knot(8.0, 100.0, null, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(RosgenType.A.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(8, 8)]));
    }

    @Test
    void theTypeMaskFollowsTheNearestPrimitiveNotTheFirst() {
        final RiverPrimitive a = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive c = knot(9.0, 100.0, RosgenType.C, 1L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(a, c),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(9, 8)]));
        assertEquals(RosgenType.A.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(7, 8)]));
    }

    // ---- Banded footprint coordinate ----

    private static double bandOf(double raw, double marginNorm, double floodPlainNorm) {
        final double bedSlope = marginNorm > 0 ? RiverInfluenceCarve.BED_EDGE / marginNorm : 0.0;
        final double floodPlainSlope = floodPlainNorm > marginNorm
                ? (RiverInfluenceCarve.FLOODPLAIN_EDGE - RiverInfluenceCarve.BED_EDGE) / (floodPlainNorm - marginNorm)
                : 0.0;
        final double outerSlope =
                floodPlainNorm < 1.0 ? (1.0 - RiverInfluenceCarve.FLOODPLAIN_EDGE) / (1.0 - floodPlainNorm) : 0.0;
        return RiverInfluenceCarve.band(raw, marginNorm, floodPlainNorm, bedSlope, floodPlainSlope, outerSlope);
    }

    @Test
    void bandPinsTheControlPointsToTheFixedBreakpoints() {
        final double margin = 0.2;
        final double floodPlain = 0.24;

        assertEquals(0.0, bandOf(0.0, margin, floodPlain), 1e-12, "the centreline");
        assertEquals(RiverInfluenceCarve.BED_EDGE, bandOf(margin, margin, floodPlain), 1e-12, "the bank");
        assertEquals(
                RiverInfluenceCarve.FLOODPLAIN_EDGE,
                bandOf(floodPlain, margin, floodPlain),
                1e-12,
                "the floodplain edge");
        assertEquals(1.0, bandOf(1.0, margin, floodPlain), 1e-12, "the influence rim");
    }

    @Test
    void bandIsMonotonicAcrossTheSweptRange() {
        final double margin = 0.2;
        final double floodPlain = 0.24;
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 1000; i++) {
            final double raw = i / 500.0;
            final double banded = bandOf(raw, margin, floodPlain);
            assertTrue(banded >= previous, "band fell at raw " + raw);
            previous = banded;
        }
    }

    @Test
    void bandIsFiniteWhenTheMarginAndFloodPlainCoincide() {
        // HydrologyProfile's default floodPlainLength returns width / 2, which is marginLen exactly;
        // RosgenProfile.DA inherits it, so the middle piece is empty for a real profile.
        final double coincident = 0.2;
        for (int i = 0; i <= 100; i++) {
            final double raw = i / 50.0;
            assertTrue(Double.isFinite(bandOf(raw, coincident, coincident)), "not finite at raw " + raw);
        }
        assertEquals(RiverInfluenceCarve.BED_EDGE, bandOf(coincident, coincident, coincident), 1e-12);
    }

    @Test
    void bandIsFiniteWhenTheFloodPlainReachesTheRim() {
        // A minimum-influence primitive can push floodPlainLength past its own rim; the clamp lands the
        // control point on exactly 1 and the outer piece becomes empty.
        for (int i = 0; i <= 100; i++) {
            final double raw = i / 50.0;
            assertTrue(Double.isFinite(bandOf(raw, 0.2, 1.0)), "not finite at raw " + raw);
        }
    }

    @Test
    void theCarveWritesTheBandedCoordinateIntoDist() {
        // dist starts at UNSET_MIN_DIST, so a lone primitive owns every cell it reaches outright and
        // dist lands on the banded value exactly, with no blend against a competitor. knot() gives
        // influenceLen 5, influenceWidth 7.5, marginLen 1 and (type A) floodPlainLen 1.2.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        // (8, 8) is the centreline: raw 0.
        assertEquals(0.0f, b.dist[idx(8, 8)], 1e-6f, "the centreline is the bottom of the bed band");
        // (8, 7) sits one pixel along the flow tangent, so raw == marginNorm exactly.
        assertEquals(
                (float) RiverInfluenceCarve.BED_EDGE,
                b.dist[idx(8, 7)],
                1e-6f,
                "one margin length out is the bed/floodplain boundary");
        // (8, 3) sits influenceLen along the flow tangent, so raw == 1 exactly.
        assertEquals(1.0f, b.dist[idx(8, 3)], 1e-6f, "the influence rim");
    }

    @Test
    void theBandedCoordinateIsIndependentOfPrimitiveWidth() {
        // The whole point of D1: a consumer classifies a point against BED_EDGE without knowing which
        // primitive claimed it. A ten-times-wider primitive still calls its own bank the bed edge, so
        // lattice cells that were floodplain for the narrow knot above are bed here.
        final RiverPrimitive wide = new RiverPrimitive(
                new double[] {8.0, 8.0}, 50.0, RosgenType.A, new double[] {1.0, 0.0}, 0.0, 20.0, 100.0, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(wide),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(0.0f, b.dist[idx(8, 8)], 1e-6f, "the centreline");
        assertTrue(
                b.dist[idx(8, 3)] < RiverInfluenceCarve.BED_EDGE, "five pixels out is still bed for a 20-wide channel");
    }

    @Test
    void unclaimedCellsKeepTheUnsetSeedRatherThanABandedValue() {
        // Task 2 publishes dist into a heightmap channel gated on RIVER_TYPE; an unclaimed cell must
        // stay recognisably unset rather than reading as a valid influence-band coordinate.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals((float) RiverInfluenceCarve.UNSET_MIN_DIST, b.dist[idx(0, 0)], "the corner is unclaimed");
        assertEquals(HydrologicalFeature.NONE, b.typeMask[idx(0, 0)], "and its type agrees");
    }
}
