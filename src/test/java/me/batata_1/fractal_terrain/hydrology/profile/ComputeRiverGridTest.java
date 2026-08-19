package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
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

    private static HydrologyProfileInprinter.GridBuffers buffers() {
        final HydrologyProfileInprinter.GridBuffers b = new HydrologyProfileInprinter.GridBuffers();
        b.ensure(POINTS, HydrologyProfileInprinter.maxLutLen(GRID, RES));
        return b;
    }

    private static int idx(int row, int col) {
        return row * GRID + col;
    }

    @Test
    void carvesTheChannelCentreToTheProfileSurface() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        // perp == 0 and tang == 0 at (8, 8): the primitive owns the cell outright.
        final int centre = idx(8, 8);
        assertEquals(1.0f, b.acc[3 * centre + 2], 1e-6f, "weight at the centre");
        // RosgenProfile.delta returns a flat -10 inside marginLen (width 2 -> marginLen 1).
        assertEquals(90.0f, b.acc[3 * centre], 1e-4f, "height at the centre");
    }

    @Test
    void leavesLatticePointsOutsideTheInfluenceUntouched() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

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
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(a, b2), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(190.0f, b.acc[3 * idx(9, 8)], 1e-4f);
    }

    @Test
    void theNearerPrimitiveWinsEvenWhenProcessedFirstWithTheLowerElevation() {
        // Deconfounds distance from list order and from elevation: nearer is FIRST and LOWER here, so a
        // last-wins or higher-wins implementation would report 190 (from farther) instead of 90 (nearer).
        final RiverPrimitive nearer = knot(9.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive farther = knot(8.0, 200.0, RosgenType.A, 1L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(nearer, farther), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(90.0f, b.acc[3 * idx(9, 8)], 1e-4f);
    }

    @Test
    void reseedsSoASecondCallDoesNotCompound() {
        final HydrologyProfileInprinter.GridBuffers b = buffers();
        final List<HydrologicalPrimitive> one = List.of(knot(8.0, 100.0, RosgenType.A, 0L));

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, one, b.acc, b.typeMask, b.dist, b.lut);
        final float first = b.acc[3 * idx(8, 8)];
        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, one, b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(first, b.acc[3 * idx(8, 8)], "buffers are reused across calls and must be reseeded");
    }

    @Test
    void stopsAtTheFirstNonRiverPrimitiveAndReportsWhere() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologicalPrimitive source =
                new me.batata_1.fractal_terrain.hydrology.features.SourcePrimitive(new double[] {8.0, 8.0});
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        final int stop = HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river, source), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(1, stop, "the river run ends at index 1");
    }

    @Test
    void skipsAPrimitiveWithNoTangent() {
        // A null normal has no cross-section; carving it would NPE in the projection.
        final RiverPrimitive noNormal =
                new RiverPrimitive(new double[] {8.0, 8.0}, 5.0, RosgenType.A, null, 0.0, 2.0, 100.0, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(noNormal), b.acc, b.typeMask, b.dist, b.lut);

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
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(diagonal), b.acc, b.typeMask, b.dist, b.lut);

        assertTrue(b.acc[3 * idx(8, 8) + 2] > 0.0f, "the diagonal primitive should still carve");
    }

    @Test
    void mergesTheWaterSurfaceWithoutNormalisingIt() {
        // waterLine(2.0) is -2, so the surface sits two below the primitive's own elevation. The water
        // lane blends toward a default of 0, which makes the raw accumulator the answer -- no divide.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(98.0f, b.acc[3 * idx(8, 8) + 1], 1e-4f, "water surface at the centre");
        assertEquals(0.0f, b.acc[3 * idx(0, 0) + 1], "water surface out of range");
    }

    @Test
    void stampsTheNearestPrimitivesFamilyAndRosgenType() {
        // RosgenType.C so the packed value is non-zero -- RIVER + A packs to 0L and would not
        // distinguish a real stamp from an unwritten cell.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.C, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        final long packed = b.typeMask[idx(8, 8)];
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(packed));
        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(packed));
        assertEquals(HydrologicalFeature.NONE, b.typeMask[idx(0, 0)], "nothing reached the corner");
    }

    @Test
    void anUnclassifiedReachStampsTheProfileItActuallyCarvedWith() {
        // A null rosgenType coalesces to A for the carve, so the mask must say A rather than "unknown".
        final RiverPrimitive river = knot(8.0, 100.0, null, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(RosgenType.A.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(8, 8)]));
    }

    @Test
    void theTypeMaskFollowsTheNearestPrimitiveNotTheFirst() {
        final RiverPrimitive a = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive c = knot(9.0, 100.0, RosgenType.C, 1L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, List.of(a, c), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(9, 8)]));
        assertEquals(RosgenType.A.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(7, 8)]));
    }
}
