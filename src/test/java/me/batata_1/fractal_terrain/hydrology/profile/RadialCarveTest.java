package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.ConfluencePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.DeltaPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * The three merge properties the radial pass depends on. Each is a case where the recurrence produces
 * a plausible-looking bowl in isolation and destroys the river carve where the two overlap.
 *
 * <p>Geometry mirrors {@code ComputeRiverGridTest}: resolution 1.0 on integer coordinates, so every
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

    private static RiverInfluenceCarve.GridBuffers buffers() {
        final RiverInfluenceCarve.GridBuffers b = new RiverInfluenceCarve.GridBuffers();
        b.ensure(GRID, RiverInfluenceCarve.maxLutLen(GRID, RES));
        return b;
    }

    private static int idx(int row, int col) {
        return row * GRID + col;
    }

    private static double depthOf(double width) {
        return FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width);
    }

    private static void carve(RiverInfluenceCarve.GridBuffers b, List<HydrologicalPrimitive> primitives) {
        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                primitives,
                b.acc,
                b.typeMask,
                b.dist,
                b.radialDist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);
    }

    /** D5: a bowl reaching ground no river touched carves to its own law, not toward the zero fill. */
    @Test
    void carvesToItsOwnLawWhereNoRiverReached() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertEquals(
                100.0 - depthOf(4.0),
                b.acc[3 * centre],
                1e-3,
                "an ungated min against the zero-filled acc would clamp the floor to 0");
        assertTrue(b.acc[3 * centre + 2] > 0, "the bowl must claim the cell it carved");
    }

    /** D4: a bowl whose rim sits above an already-carved river bed leaves that bed alone. */
    @Test
    void neverLiftsARiverBedItOverlaps() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        final RiverInfluenceCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float riverBed = riverOnly.acc[3 * idx(8, 8)];

        // Rim 20 above the river's, so the bowl floor still sits above the river bed it overlaps.
        carve(b, List.of(knot(CENTRE, 100.0), bowl(4.0, 120.0)));

        assertEquals(
                riverBed,
                b.acc[3 * idx(8, 8)],
                1e-3,
                "the first radial primitive takes weight 1, so without the clamp it overwrites the bed");
    }

    /** D6: a cell in the bowl's square footprint but outside its disc keeps the river's claim. */
    @Test
    void keepsTheRiverWeightAtCellsOutsideItsDisc() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
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
        final RiverInfluenceCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertEquals(
                100.0 + HydrologicalPrimitive.waterLine(4.0),
                b.acc[3 * centre + 1],
                1e-3,
                "water sits below the rim by the stepped waterLine offset");
    }

    /** The type mask names the family that won the cell, so the paint side can tell a pool from a bed. */
    @Test
    void stampsTheConfluenceFamilyOnTheCellsItWins() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        assertEquals(
                HydrologicalPrimitive.HydrologicalFeature.CONFLUENCE,
                HydrologicalPrimitive.HydrologicalFeature.unpack(b.typeMask[idx(8, 8)]));
    }

    /** D2's filter: a non-river, non-radial tail entry must leave every lane byte-identical. */
    @Test
    void ignoresANonRadialTailPrimitive() {
        final RiverInfluenceCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float[] accBefore = riverOnly.acc.clone();
        final long[] maskBefore = riverOnly.typeMask.clone();
        final float[] distBefore = riverOnly.dist.clone();

        // DELTA sorts between SOURCE and CONFLUENCE and implements no radial interface, so the second
        // pass must walk straight past it rather than treat the list tail as carveable.
        final RiverInfluenceCarve.GridBuffers withDelta = buffers();
        carve(withDelta, List.of(knot(CENTRE, 100.0), new DeltaPrimitive(new double[] {CENTRE, CENTRE})));

        assertArrayEquals(accBefore, withDelta.acc, "a delta in the tail perturbed the merged surface");
        assertArrayEquals(maskBefore, withDelta.typeMask, "a delta in the tail perturbed the type mask");
        assertArrayEquals(distBefore, withDelta.dist, "a delta in the tail perturbed the distance field");
    }

    /** The surface painter reads the river pass's banded dist after the carve returns, so the radial
     *  pass must rank on its own buffer and leave that one alone. */
    @Test
    void leavesTheRiverDistanceFieldUntouched() {
        final RiverInfluenceCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float[] distBefore = riverOnly.dist.clone();

        final RiverInfluenceCarve.GridBuffers withBowl = buffers();
        carve(withBowl, List.of(knot(CENTRE, 100.0), bowl(4.0, 100.0)));

        assertArrayEquals(distBefore, withBowl.dist, "the radial pass overwrote the painter's input");
    }
}
