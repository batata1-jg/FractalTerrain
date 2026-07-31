package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.batata_1.fractal_terrain.hydrology.features.RiverUnit;
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
        assertEquals(RiverUnit.RosgenType.Aa, RosgenKey.classify(with(baseC(), 0.20, 8.0, 30.0, null, null)));
    }

    @Test
    void steepSlopeGivesA() {
        assertEquals(RiverUnit.RosgenType.A, RosgenKey.classify(with(baseC(), 0.06, 8.0, 30.0, null, null)));
    }

    @Test
    void entrenchedAndNarrowGivesG() {
        assertEquals(RiverUnit.RosgenType.G, RosgenKey.classify(with(baseC(), 0.03, 1.2, 8.0, null, null)));
    }

    @Test
    void entrenchedAndWideGivesF() {
        assertEquals(RiverUnit.RosgenType.F, RosgenKey.classify(with(baseC(), 0.03, 1.2, 20.0, null, null)));
    }

    @Test
    void moderatelyEntrenchedGivesB() {
        assertEquals(RiverUnit.RosgenType.B, RosgenKey.classify(with(baseC(), 0.03, 1.8, 20.0, null, null)));
    }

    @Test
    void bAndGShareASlopeBandAndAreSeparatedByEntrenchmentOnly() {
        // Rosgen's published slope bands for B and G overlap exactly (0.02-0.039); ER is the
        // discriminator. Same slope and W/D, different ER, different type.
        final ReachMetrics g = with(baseC(), 0.03, 1.2, 8.0, null, null);
        final ReachMetrics b = with(baseC(), 0.03, 1.8, 8.0, null, null);
        assertEquals(RiverUnit.RosgenType.G, RosgenKey.classify(g));
        assertEquals(RiverUnit.RosgenType.B, RosgenKey.classify(b));
    }

    @Test
    void nearBaseLevelFlatAndVeryWideFloodProneGivesDA() {
        assertEquals(RiverUnit.RosgenType.DA, RosgenKey.classify(with(baseC(), 0.001, 6.0, 20.0, 10.0, 2.0)));
    }

    @Test
    void daIsTestedBeforeDSoBraidingNeverStealsAnAnastomosingReach() {
        // Both gates must be genuinely open: slope above braidThreshold(12.0) (~0.0022457) so the D
        // guard passes, but still below S_DA (0.005) so the DA guard also passes. Twice the threshold
        // (~0.00449) satisfies both while staying well under S_A (0.04), so the steep branches don't
        // fire first. With both branches viable, only their order in classify decides DA over D.
        final double width = 12.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        final ReachMetrics both = new ReachMetrics(slope, 6.0, 20.0, width, 2.0);
        assertEquals(RiverUnit.RosgenType.DA, RosgenKey.classify(both));
    }

    @Test
    void wideChannelAboveTheBraidThresholdGivesD() {
        final double width = 12.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        // High above sea level, so the DA gate is shut.
        assertEquals(RiverUnit.RosgenType.D, RosgenKey.classify(new ReachMetrics(slope, 6.0, 20.0, width, 80.0)));
    }

    @Test
    void narrowChannelAboveTheBraidThresholdIsNotD() {
        final double width = 2.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        assertEquals(RiverUnit.RosgenType.E, RosgenKey.classify(new ReachMetrics(slope, 6.0, 8.0, width, 80.0)));
    }

    @Test
    void slightlyEntrenchedAndNarrowGivesE() {
        assertEquals(RiverUnit.RosgenType.E, RosgenKey.classify(new ReachMetrics(0.001, 6.0, 8.0, 2.0, 80.0)));
    }

    @Test
    void slightlyEntrenchedAndWideGivesC() {
        assertEquals(RiverUnit.RosgenType.C, RosgenKey.classify(new ReachMetrics(0.001, 6.0, 20.0, 2.0, 80.0)));
    }

    @Test
    void saturatedEntrenchmentIsHandledAsSlightlyEntrenched() {
        // A transect that never exceeds the flood-prone stage reports ER = +inf. That is the correct
        // semantic (a broad flat valley), not a failure, and must not throw or fall through.
        assertEquals(
                RiverUnit.RosgenType.C,
                RosgenKey.classify(new ReachMetrics(0.001, Double.POSITIVE_INFINITY, 20.0, 2.0, 80.0)));
    }

    @Test
    void deadBandKeepsTheUpstreamTypeWhenEntrenchmentSitsOnAThreshold() {
        // ER 2.25 is within the published +/-0.2 of the 2.2 boundary, so a B neighbour holds.
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 2.25, 20.0, 2.0, 80.0);
        assertEquals(RiverUnit.RosgenType.C, RosgenKey.classify(onBoundary));
        assertEquals(
                RiverUnit.RosgenType.B,
                RosgenKey.applyDeadBand(onBoundary, RiverUnit.RosgenType.C, RiverUnit.RosgenType.B));
    }

    @Test
    void deadBandKeepsTheUpstreamTypeWhenWidthDepthSitsOnAThreshold() {
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 6.0, 13.0, 2.0, 80.0);
        assertEquals(RiverUnit.RosgenType.C, RosgenKey.classify(onBoundary));
        assertEquals(
                RiverUnit.RosgenType.E,
                RosgenKey.applyDeadBand(onBoundary, RiverUnit.RosgenType.C, RiverUnit.RosgenType.E));
    }

    @Test
    void deadBandDoesNotSuppressAChangeFarFromAnyThreshold() {
        final ReachMetrics clear = new ReachMetrics(0.001, 6.0, 20.0, 2.0, 80.0);
        assertEquals(
                RiverUnit.RosgenType.C, RosgenKey.applyDeadBand(clear, RiverUnit.RosgenType.C, RiverUnit.RosgenType.E));
    }

    @Test
    void deadBandPassesThroughWhenThereIsNoUpstreamNeighbour() {
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 2.25, 20.0, 2.0, 80.0);
        assertEquals(RiverUnit.RosgenType.C, RosgenKey.applyDeadBand(onBoundary, RiverUnit.RosgenType.C, null));
    }
}
