package me.batata_1.fractal_terrain.hydrology.carvers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.AbandonedRiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.ConfluencePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.DeltaPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.hydrology.features.SourcePrimitive;
import org.junit.jupiter.api.Test;

/**
 * The three merge properties the radial pass depends on. Each is a case where the recurrence produces
 * a plausible-looking bowl in isolation and destroys the river carve where the two overlap.
 *
 * <p>Geometry mirrors {@code ComputeBedGridTest}: resolution 1.0 on integer coordinates, so every
 * sampled radius lands on an exact LUT entry and interpolation is exact.
 */
class RadialCarveTest {

    private static final int GRID = 16;
    private static final double RES = 1.0;
    private static final double CENTRE = 8.0;

    /** A straight river knot whose normal points along +X, centred on the lattice. */
    private static RiverPrimitive knot(double cx, double elevation) {
        return new RiverPrimitive(
                new double[] {cx, CENTRE}, 5.0, RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, elevation, 0L);
    }

    private static ConfluencePrimitive bowl(double width, double elevation) {
        return new ConfluencePrimitive(new double[] {CENTRE, CENTRE}, width, elevation);
    }

    private static SourcePrimitive cone(double width, double elevation) {
        return new SourcePrimitive(new double[] {CENTRE, CENTRE}, width, elevation);
    }

    /** Resolved (non-sentinel) values: production only ever mints one with {@code elevation = NaN},
     *  but this dispatch is exercised once it is. */
    private static AbandonedRiverPrimitive trace(double width, double elevation) {
        return new AbandonedRiverPrimitive(new double[] {CENTRE, CENTRE}, (byte) 0, width, elevation);
    }

    private static LatticeCarve.GridBuffers buffers() {
        final LatticeCarve.GridBuffers b = new LatticeCarve.GridBuffers();
        b.ensure(GRID, LatticeCarve.maxLutLen(GRID, RES));
        return b;
    }

    private static LatticeCarve.BedGrid grid(LatticeCarve.GridBuffers b, float[] elevs) {
        return new LatticeCarve.BedGrid(
                GRID, 0, 0, RES, b.acc, b.typeMask, b.dist, b.lut, b.perpRow, b.perpCol, b.tangRow, b.tangCol, elevs);
    }

    private static int idx(int row, int col) {
        return row * GRID + col;
    }

    private static double depthOf(double width) {
        return FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width);
    }

    private static void carve(LatticeCarve.GridBuffers b, List<HydrologicalPrimitive> primitives) {
        LatticeCarve.computeBedGrid(grid(b, null), primitives);
    }

    /** Second helper so the elevs-less {@link #carve} stays untouched for the tests that rely on it. */
    private static void carveWithElevs(
            LatticeCarve.GridBuffers b, List<HydrologicalPrimitive> primitives, float[] elevs) {
        LatticeCarve.computeBedGrid(grid(b, elevs), primitives);
    }

    /** One buffer holding two scales would leave BED_EDGE and FLOODPLAIN_EDGE meaning whichever family
     *  wrote last, so a disc is banded on the same breakpoints a channel is. */
    @Test
    void bandsTheDiscOnTheSameBreakpointsAsAChannel() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        assertEquals(0f, b.dist[idx(8, 8)], 1e-6f, "the disc centre is the floor of its bed");
        assertEquals((float) LatticeCarve.BED_EDGE, b.dist[idx(8, 10)], 1e-6f, "half the radius is the bank");
        assertEquals(
                (float) LatticeCarve.FLOODPLAIN_EDGE,
                b.dist[idx(8, 11)],
                1e-6f,
                "three quarters of the radius is the floodplain edge");
    }

    /** A bowl reaching ground no river touched carves to its own law: a null ambient field leaves
     *  nothing to cap against. */
    @Test
    void carvesToItsOwnLawWhereNoRiverReached() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertEquals(
                100.0 - depthOf(4.0),
                b.acc[3 * centre],
                1e-3,
                "an ungated min against the zero-filled acc would clamp the floor to 0");
        assertTrue(b.acc[3 * centre + 2] > 0, "the bowl must claim the cell it carved");
    }

    /** A bowl blends against ambient like every other family, so a rim above a river's bed pulls the
     *  merged surface up toward the bowl's own floor in proportion to its weight. */
    @Test
    void blendsAgainstAmbientRatherThanTheMergedRiverSurface() {
        final LatticeCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float riverBed = riverOnly.acc[3 * idx(8, 8)];

        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(knot(CENTRE, 100.0), bowl(4.0, 120.0)));

        assertTrue(
                b.acc[3 * idx(8, 8)] > riverBed,
                "the bowl's own floor sits above the river bed, and order alone decides the outcome");
    }

    /** A cell in the bowl's square footprint but outside its disc keeps the river's claim: the weight
     *  is assigned, not maxed, and the cell survives because w = 0 leaves dist[i] untouched, so the
     *  assignment reproduces the river's own claim. */
    @Test
    void keepsTheRiverWeightAtCellsOutsideItsDisc() {
        final LatticeCarve.GridBuffers b = buffers();
        // The knot at x = 4 reaches (4, 4); the bowl's AABB covers rows/cols 4..12 but its disc,
        // radius 4 about (8, 8), does not reach the corner at distance sqrt(32).
        carve(b, List.of(knot(4.0, 100.0), bowl(4.0, 100.0)));

        assertTrue(
                b.acc[3 * idx(4, 4) + 2] > 0,
                "assigning the weight lane instead of maxing it would zero the river's claim here");
    }

    /** The bowl publishes a water surface, or the recurrence drains it toward zero. */
    @Test
    void publishesItsOwnWaterSurface() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertEquals(
                100.0 + HydrologicalPrimitive.waterLine(4.0),
                b.acc[3 * centre + 1],
                1e-3,
                "water sits below the rim by the stepped waterLine offset");
    }

    /** A disc claims no type: it paints nothing either way, and not writing the tag is what keeps a
     *  river bed crossing the disc in its own surface materials. */
    @Test
    void claimsNoTypeOnTheCellsItCarves() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        assertEquals(
                HydrologicalPrimitive.HydrologicalFeature.NONE,
                b.typeMask[idx(8, 8)],
                "a cell only a disc reached must stay untagged");
    }

    /** The disc runs to width(), well past a channel's painted bed, so a bowl overlapping a river
     *  must leave the RIVER tag — and the surface painter's riverbed materials — in place. */
    @Test
    void leavesTheRiverTypeTagOnCellsTheRiverClaimed() {
        final LatticeCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final long riverTag = riverOnly.typeMask[idx(8, 8)];

        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(knot(CENTRE, 100.0), bowl(4.0, 100.0)));

        assertEquals(
                HydrologicalPrimitive.HydrologicalFeature.RIVER,
                HydrologicalPrimitive.HydrologicalFeature.unpack(riverTag),
                "fixture check: the river must claim the centre for this test to mean anything");
        assertEquals(riverTag, b.typeMask[idx(8, 8)], "the radial pass overwrote the river's tag");
    }

    /** D2's filter: a non-river, non-radial tail entry must leave every lane byte-identical. */
    @Test
    void ignoresANonRadialTailPrimitive() {
        final LatticeCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float[] accBefore = riverOnly.acc.clone();
        final long[] maskBefore = riverOnly.typeMask.clone();
        final float[] distBefore = riverOnly.dist.clone();

        // DELTA sorts between SOURCE and CONFLUENCE and implements no radial interface, so the second
        // pass must walk straight past it rather than treat the list tail as carveable.
        final LatticeCarve.GridBuffers withDelta = buffers();
        carve(withDelta, List.of(knot(CENTRE, 100.0), new DeltaPrimitive(new double[] {CENTRE, CENTRE})));

        assertArrayEquals(accBefore, withDelta.acc, "a delta in the tail perturbed the merged surface");
        assertArrayEquals(maskBefore, withDelta.typeMask, "a delta in the tail perturbed the type mask");
        assertArrayEquals(distBefore, withDelta.dist, "a delta in the tail perturbed the distance field");
    }

    /** One shared ranking buffer: the published distance is the winning primitive's, whichever family
     *  that is, which is what lets a rectangle and a disc rank against each other at all. */
    @Test
    void publishesTheWinningPrimitivesDistanceWhicheverFamilyWon() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(knot(4.0, 100.0), bowl(4.0, 100.0)));

        assertEquals(0f, b.dist[idx(8, 8)], 1e-6f, "the bowl centre is the nearest thing to that cell");
        assertTrue(b.dist[idx(4, 4)] < (float) LatticeCarve.UNSET_MIN_DIST, "the river still holds its own cells");
    }

    /** The source's cone gives up depth linearly, so half radius has given up half depth — where the
     *  bowl's parabola would have given up only a quarter. Exercises {@code SourcePrimitive} through
     *  the carve; every other radial test here carves a bowl. */
    @Test
    void carvesToTheSourceConeLawAtHalfRadius() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(cone(4.0, 100.0)));

        final int halfRadius = idx(8, 10);
        assertEquals(
                100.0 - 0.5 * depthOf(4.0),
                b.acc[3 * halfRadius],
                1e-3,
                "the cone gives up depth linearly: half depth at half radius");
    }

    /** {@code AbandonedRiverPrimitive} implements {@code RadialPrimitive}, so this bed-pass dispatch
     *  carves it exactly like a bowl or a cone once its deferred elevation is resolved — a deliberate
     *  consequence of the interface, not an oversight (see {@code profile/README.md}'s radial-pass
     *  note). It also overrides the abandoned-river family's shell carve to cut the same radial disc,
     *  but that path is not this test's concern. */
    @Test
    void carvesAnAbandonedRiverPrimitiveThroughTheRadialDispatch() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(trace(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertTrue(b.acc[3 * centre] < 100.0, "the bed-pass radial dispatch must cut the abandoned trace's centre");
    }

    /** No radial test above passes a non-null {@code elevs}, so the ambient-clamp branch is dead in
     *  test. An ambient below the bowl's own floor must pull the merged surface down to it. */
    @Test
    void clampsToAmbientElevationBelowTheBowlFloor() {
        final LatticeCarve.GridBuffers b = buffers();
        final float lowAmbient = (float) (100.0 - depthOf(4.0) - 10.0);
        final float[] elevs = new float[GRID * GRID];
        Arrays.fill(elevs, lowAmbient);

        carveWithElevs(b, List.of(bowl(4.0, 100.0)), elevs);

        assertEquals(
                lowAmbient,
                b.acc[3 * idx(8, 8)],
                1e-3,
                "the ambient clamp must pull the bowl's sampled floor down to the lower ambient");
    }

    /** Every test above runs at RES = 1.0, never the production tile resolution, so nothing pins
     *  {@link LatticeCarve#maxLutLen}'s radial-span bound. A {@code MAX_WIDTH} disc at
     *  {@code GRID_RESOLUTION} must stay inside the LUT it is sized against. */
    @Test
    void productionResolutionRadialDiscStaysWithinTheLut() {
        final double prodRes = 1.0 / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final LatticeCarve.GridBuffers b = new LatticeCarve.GridBuffers();
        b.ensure(GRID, LatticeCarve.maxLutLen(GRID, prodRes));
        final double centre = GRID / 2.0 * prodRes;
        final List<HydrologicalPrimitive> primitives =
                List.of(new ConfluencePrimitive(new double[] {centre, centre}, HydrologyTuning.MAX_WIDTH, 100.0));
        final LatticeCarve.BedGrid prodGrid = new LatticeCarve.BedGrid(
                GRID,
                0,
                0,
                prodRes,
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertDoesNotThrow(
                () -> LatticeCarve.computeBedGrid(prodGrid, primitives),
                "a MAX_WIDTH disc at production resolution must not overrun maxLutLen's table");

        assertTrue(b.acc[3 * idx(GRID / 2, GRID / 2) + 2] > 0, "the disc must claim the grid centre");
    }
}
