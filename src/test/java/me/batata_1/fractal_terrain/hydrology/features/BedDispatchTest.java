package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.carvers.LatticeCarve;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import org.junit.jupiter.api.Test;

/** What each family does when the bed pass reaches it, and that the cross-section hook fills the same
 *  table the profile does. */
class BedDispatchTest {

    private static final int GRID = 16;
    private static final double RES = 1.0;

    private static LatticeCarve.GridBuffers buffers() {
        final LatticeCarve.GridBuffers b = new LatticeCarve.GridBuffers();
        b.ensure(GRID, LatticeCarve.maxLutLen(GRID, RES));
        return b;
    }

    private static LatticeCarve.BedGrid grid(LatticeCarve.GridBuffers b) {
        return new LatticeCarve.BedGrid(
                GRID, 0, 0, RES, b.acc, b.typeMask, b.dist, b.lut, b.perpRow, b.perpCol, b.tangRow, b.tangCol, null);
    }

    @Test
    void aPositionOnlyPrimitiveCarvesNothing() {
        final LatticeCarve.GridBuffers b = buffers();
        LatticeCarve.computeBedGrid(grid(b), List.of());
        final float[] empty = b.acc.clone();
        final long[] emptyMask = b.typeMask.clone();

        LatticeCarve.computeBedGrid(
                grid(b),
                List.of(new WaterfallPrimitive(new double[] {8.0, 8.0}), new DeltaPrimitive(new double[] {8.0, 8.0})));

        assertArrayEquals(empty, b.acc, "a skeleton feature perturbed the merged surface");
        assertArrayEquals(emptyMask, b.typeMask, "a skeleton feature perturbed the type mask");
    }

    @Test
    void anOxbowRefusesTheBedPassRatherThanCarvingSilently() {
        final OxbowLakePrimitive oxbow = new OxbowLakePrimitive(
                new double[] {8.0, 8.0}, (byte) 3, 2.0, 5.0, 100.0, new double[] {1.0, 0.0}, 0.0, null);

        assertThrows(UnsupportedOperationException.class, () -> oxbow.carveBed(grid(buffers())));
    }

    @Test
    void theRiverLutHookFillsWhatTheProfileWouldHaveInline() {
        final RiverPrimitive river = new RiverPrimitive(
                new double[] {8.0, 8.0}, 5.0, RiverPrimitive.RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, 100.0);
        final int baseIdx = -4;
        final int n = 9;

        final float[] fromHook = new float[n];
        river.tabulateBedLut(fromHook, baseIdx, n, RES);

        final float[] fromProfile = new float[n];
        final RosgenProfile profile = (RosgenProfile) river.getProfile();
        profile.sampleCrossSection(
                fromProfile,
                n,
                RES,
                baseIdx,
                river.seed(),
                river.elevation(),
                profile.floodPlainLength(river.width()),
                river.width() / 2,
                FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(river.width()),
                river.curvature());

        assertArrayEquals(fromProfile, fromHook, "the hook must tabulate the profile's own cross-section");
    }
}
