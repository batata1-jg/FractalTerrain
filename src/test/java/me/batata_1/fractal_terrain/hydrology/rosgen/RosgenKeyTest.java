package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * Table-driven contract test for the Rosgen Level-I decision key. The key is a total pure function of
 * five measured doubles, so every case here is an exact input/output pair with no fixture, raster or
 * graph involved. Each type gets at least one case that reaches it; the ordering cases pin the tests
 * that must fire before others.
 */
class RosgenKeyTest {

    /** slope, ER, W/D, width, bedElev — a reach comfortably inside the C envelope. */
    private static ReachMetrics baseC() {
        return new ReachMetrics(0.005, 6.0, 20.0, 10.0, 60.0);
    }

    private static ReachMetrics with(ReachMetrics m, Double slope, Double er, Double wd, Double w, Double z) {
        return new ReachMetrics(
                slope == null ? m.slope() : slope,
                er == null ? m.entrenchment() : er,
                wd == null ? m.widthDepth() : wd,
                w == null ? m.width() : w,
                z == null ? m.bedElev() : z);
    }

    @Test
    void steepestSlopeGivesAaRegardlessOfEverythingElse() {
        // Slope is tested first: a very steep reach is Aa+ even with a broad floodplain.
        assertEquals(RosgenType.Aa, RosgenKey.classify(with(baseC(), 0.20, 8.0, 30.0, null, null)));
    }

    @Test
    void steepSlopeGivesA() {
        assertEquals(RosgenType.A, RosgenKey.classify(with(baseC(), 0.06, 8.0, 30.0, null, null)));
    }

    @Test
    void entrenchedAndNarrowGivesG() {
        assertEquals(RosgenType.G, RosgenKey.classify(with(baseC(), 0.03, 1.2, 8.0, null, null)));
    }

    @Test
    void entrenchedAndWideGivesF() {
        assertEquals(RosgenType.F, RosgenKey.classify(with(baseC(), 0.03, 1.2, 20.0, null, null)));
    }

    @Test
    void moderatelyEntrenchedGivesB() {
        assertEquals(RosgenType.B, RosgenKey.classify(with(baseC(), 0.03, 1.8, 20.0, null, null)));
    }

    @Test
    void bAndGShareASlopeBandAndAreSeparatedByEntrenchmentOnly() {
        // Rosgen's published slope bands for B and G overlap exactly (0.02-0.039); ER is the
        // discriminator. Same slope and W/D, different ER, different type.
        final ReachMetrics g = with(baseC(), 0.03, 1.2, 8.0, null, null);
        final ReachMetrics b = with(baseC(), 0.03, 1.8, 8.0, null, null);
        assertEquals(RosgenType.G, RosgenKey.classify(g));
        assertEquals(RosgenType.B, RosgenKey.classify(b));
    }

    @Test
    void nearBaseLevelFlatAndVeryWideFloodProneGivesDA() {
        assertEquals(RosgenType.DA, RosgenKey.classify(with(baseC(), 0.001, 6.0, 20.0, 10.0, 2.0)));
    }

    @Test
    void daIsTestedBeforeDSoBraidingNeverStealsAnAnastomosingReach() {
        // Same reach, both gates open: DA must win.
        final ReachMetrics both = new ReachMetrics(0.001, 6.0, 20.0, 12.0, 2.0);
        assertEquals(RosgenType.DA, RosgenKey.classify(both));
    }

    @Test
    void wideChannelAboveTheBraidThresholdGivesD() {
        final double width = 12.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        // High above sea level, so the DA gate is shut.
        assertEquals(RosgenType.D, RosgenKey.classify(new ReachMetrics(slope, 6.0, 20.0, width, 80.0)));
    }

    @Test
    void narrowChannelAboveTheBraidThresholdIsNotD() {
        final double width = 2.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        assertEquals(RosgenType.E, RosgenKey.classify(new ReachMetrics(slope, 6.0, 8.0, width, 80.0)));
    }

    @Test
    void slightlyEntrenchedAndNarrowGivesE() {
        assertEquals(RosgenType.E, RosgenKey.classify(new ReachMetrics(0.001, 6.0, 8.0, 2.0, 80.0)));
    }

    @Test
    void slightlyEntrenchedAndWideGivesC() {
        assertEquals(RosgenType.C, RosgenKey.classify(new ReachMetrics(0.001, 6.0, 20.0, 2.0, 80.0)));
    }

    @Test
    void saturatedEntrenchmentIsHandledAsSlightlyEntrenched() {
        // A transect that never exceeds the flood-prone stage reports ER = +inf. That is the correct
        // semantic (a broad flat valley), not a failure, and must not throw or fall through.
        assertEquals(
                RosgenType.C, RosgenKey.classify(new ReachMetrics(0.001, Double.POSITIVE_INFINITY, 20.0, 2.0, 80.0)));
    }

    @Test
    void deadBandKeepsTheUpstreamTypeWhenEntrenchmentSitsOnAThreshold() {
        // ER 2.25 is within the published +/-0.2 of the 2.2 boundary, so a B neighbour holds.
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 2.25, 20.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.classify(onBoundary));
        assertEquals(RosgenType.B, RosgenKey.applyDeadBand(onBoundary, RosgenType.C, RosgenType.B));
    }

    @Test
    void deadBandKeepsTheUpstreamTypeWhenWidthDepthSitsOnAThreshold() {
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 6.0, 13.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.classify(onBoundary));
        assertEquals(RosgenType.E, RosgenKey.applyDeadBand(onBoundary, RosgenType.C, RosgenType.E));
    }

    @Test
    void deadBandDoesNotSuppressAChangeFarFromAnyThreshold() {
        final ReachMetrics clear = new ReachMetrics(0.001, 6.0, 20.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.applyDeadBand(clear, RosgenType.C, RosgenType.E));
    }

    @Test
    void deadBandPassesThroughWhenThereIsNoUpstreamNeighbour() {
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 2.25, 20.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.applyDeadBand(onBoundary, RosgenType.C, null));
    }
}
