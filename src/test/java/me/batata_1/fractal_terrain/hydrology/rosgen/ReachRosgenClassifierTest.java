package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import org.junit.jupiter.api.Test;

/** Contract tests for reach segmentation and the graph-order classification walk. */
class ReachRosgenClassifierTest {

    private static final int SIDE = 512;

    /** A flat plain: every transect saturates, so entrenchment never selects an entrenched type. */
    private static float[] flat() {
        return new float[SIDE * SIDE];
    }

    /** A straight source-to-drain channel down the middle of the tile. */
    private static RiverNetwork straightNetwork() {
        final List<RiverNetwork.NodeSpec> nodes = List.of(
                new RiverNetwork.NodeSpec(100.0, 256.0, Endpoint.Type.SOURCE),
                new RiverNetwork.NodeSpec(400.0, 256.0, Endpoint.Type.DRAIN));
        final ArrayList<double[]> pts = new ArrayList<>();
        for (double x = 100.0; x <= 400.0; x += 2.0) pts.add(new double[] {x, 256.0});
        final List<RiverNetwork.EdgeSpec> edges = List.of(new RiverNetwork.EdgeSpec(0, 1, pts, 4.0));
        return new RiverNetwork(SIDE, nodes, edges, false, 0, 2.0);
    }

    @Test
    void everySplinePointReceivesAType() {
        // With no gap-filler in classifyChannel, a null entry means segment() left an index uncovered.
        // This is the coverage test for reach segmentation, not a formality.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);

        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            assertNotNull(types);
            assertEquals(ch.numPts(), types.length, "one type per spline point");
            for (RosgenType t : types) assertNotNull(t, "no null types");
        }
    }

    @Test
    void aChannelWithoutBedElevationsStillReceivesTypes() {
        // Removed features (oxbows, abandoned rivers) carry no bed elevations. Classification must
        // degrade rather than throw.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = null;

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);
        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            assertEquals(ch.numPts(), types.length);
            for (RosgenType t : types) assertNotNull(t);
        }
    }

    @Test
    void typesAreConstantWithinAReachSoAdjacentUnitsRarelyDisagree() {
        // Rosgen types a reach, not a point. Adjacent points inside one reach must share a type, so
        // the floodplain edge cannot scallop at unit spacing.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);

        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            int changes = 0;
            for (int i = 1; i < types.length; i++) if (types[i] != types[i - 1]) changes++;
            // Reaches are min(20*width, 64) px long, so this 300 px channel has roughly 19 of them --
            // but on a uniform field every reach measures the same and commits the same type, so the
            // count must stay far below the point count (~150). Anything approaching per-point variation
            // means reach segmentation is not being applied.
            assertTrue(changes <= 5, "expected at most 5 type changes along one channel, got " + changes);
        }
    }

    @Test
    void repeatedPrepareOnAnUnchangedNetworkIsDeterministic() {
        // prepare() must be idempotent on its own: it clears and rebuilds typesByChannelId, and the walk
        // order must not depend on HashMap iteration order.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);
        final RosgenType[] first = classifier.typesFor(net.getChannels().getFirst());
        classifier.prepare(net);
        final RosgenType[] second = classifier.typesFor(net.getChannels().getFirst());
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) assertEquals(first[i], second[i], "point " + i);
    }

    @Test
    void reclassifyingAfterAResampleIsDeterministic() {
        // The production sequence, which the idempotency test above does not reach: collectUnits runs
        // three times per tile and reSamples every channel before each classification pass.
        // QuinticHermiteSpline.reSampleWithTs refits a fresh Catmull-Rom through the resampled points
        // each time, so resampling an already-resampled spline is not obviously a fixed point. If it
        // drifts a reach's metrics across a threshold, the shell carved into the terrain
        // (LocalRiverProvider:249) and the type persisted to the index (:252) commit different types for
        // the same physical reach.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        resampleAll(net);
        classifier.prepare(net);
        final RosgenType[] first = classifier.typesFor(net.getChannels().getFirst());

        resampleAll(net);
        classifier.prepare(net);
        final RosgenType[] second = classifier.typesFor(net.getChannels().getFirst());

        assertEquals(first.length, second.length, "a second resample must not change the point count");
        for (int i = 0; i < first.length; i++) assertEquals(first[i], second[i], "point " + i);
    }

    /** Resamples at the spacing {@code collectUnits} uses, so the test walks the production path. */
    private static void resampleAll(RiverNetwork net) {
        for (Channel ch : net.getChannels()) {
            if (ch.isResampleable()) ch.reSample(Math.max(ch.intakeWidth() / 2.0, 0.5));
        }
    }

    private static double[] descendingBed(int n) {
        final double[] bed = new double[n];
        for (int i = 0; i < n; i++) bed[i] = 80.0 - i * 0.05;
        return bed;
    }
}
