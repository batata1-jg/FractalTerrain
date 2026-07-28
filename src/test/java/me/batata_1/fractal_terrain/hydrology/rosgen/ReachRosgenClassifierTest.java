package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
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

    /**
     * A two-confluence chain: {@code A} (main headwater) and {@code T1} (a small tributary) join at
     * junction {@code J1} into {@code B}, which then joins tributary {@code T2} at junction {@code J2}
     * into {@code C}, which reaches the drain. {@code RiverNetwork.update} numbers channels by ascending
     * atomic-node id, and {@code buildFromSpecs} assigns atomic ids to node specs in list order before any
     * edge's interior points, so with the node specs listed source-to-drain the channel ids come out
     * {@code A=0, T1=1, B=2, T2=3, C=4} -- every non-drain-adjacent channel (A, T1, B, T2) holds a lower id
     * than the channel immediately downstream of it. Only {@code C} is drain-adjacent.
     *
     * <p>This is the layout that made {@code orderDownstreamFirst}'s old sequencing wrong: the drain seed
     * frontier held only {@code C}, and the unreached-channel pass then enqueued every other channel by id
     * ({@code A, T1, B, T2}) before the BFS drained, so {@code A} and {@code T1} were emitted before
     * {@code B} -- the channel they actually flow into -- even though {@code C} (seeded first) happened to
     * come out ahead of everything. A single confluence directly on the drain does not expose this: only a
     * confluence at least one hop upstream of the drain does.
     */
    private static RiverNetwork chainedConfluenceNetwork() {
        final List<RiverNetwork.NodeSpec> nodes = List.of(
                new RiverNetwork.NodeSpec(50.0, 150.0, Endpoint.Type.SOURCE), // 0: A's source
                new RiverNetwork.NodeSpec(50.0, 220.0, Endpoint.Type.SOURCE), // 1: T1's source
                new RiverNetwork.NodeSpec(150.0, 185.0, Endpoint.Type.JUNCTION), // 2: J1
                new RiverNetwork.NodeSpec(150.0, 320.0, Endpoint.Type.SOURCE), // 3: T2's source
                new RiverNetwork.NodeSpec(300.0, 256.0, Endpoint.Type.JUNCTION), // 4: J2
                new RiverNetwork.NodeSpec(450.0, 256.0, Endpoint.Type.DRAIN)); // 5: drain
        final List<RiverNetwork.EdgeSpec> edges = List.of(
                new RiverNetwork.EdgeSpec(0, 2, line(50.0, 150.0, 150.0, 185.0), 4.0), // A
                new RiverNetwork.EdgeSpec(1, 2, line(50.0, 220.0, 150.0, 185.0), 4.0), // T1
                new RiverNetwork.EdgeSpec(2, 4, line(150.0, 185.0, 300.0, 256.0), 4.0), // B
                new RiverNetwork.EdgeSpec(3, 4, line(150.0, 320.0, 300.0, 256.0), 4.0), // T2
                new RiverNetwork.EdgeSpec(4, 5, line(300.0, 256.0, 450.0, 256.0), 4.0)); // C
        return new RiverNetwork(SIDE, nodes, edges, false, 0, 2.0);
    }

    /** A straight de facto rectangular-valley channel wide enough for a coarse, easy-to-shape transect. */
    private static RiverNetwork wideStraightNetwork() {
        final List<RiverNetwork.NodeSpec> nodes = List.of(
                new RiverNetwork.NodeSpec(100.0, 256.0, Endpoint.Type.SOURCE),
                new RiverNetwork.NodeSpec(400.0, 256.0, Endpoint.Type.DRAIN));
        final ArrayList<double[]> pts = new ArrayList<>();
        for (double x = 100.0; x <= 400.0; x += 2.0) pts.add(new double[] {x, 256.0});
        // flow=2000 clamps width to HydrologyTuning.MAX_WIDTH (16 px): a 2.05*16 px transect walk in
        // 2 px steps, coarse enough to shape a valley wall by hand.
        final List<RiverNetwork.EdgeSpec> edges = List.of(new RiverNetwork.EdgeSpec(0, 1, pts, 2000.0));
        return new RiverNetwork(SIDE, nodes, edges, false, 0, 2.0);
    }

    /** Straight-line points from {@code (x0,z0)} to {@code (x1,z1)}, spaced roughly 2 px apart. */
    private static ArrayList<double[]> line(double x0, double z0, double x1, double z1) {
        final double dist = Math.hypot(x1 - x0, z1 - z0);
        final int steps = Math.max(2, (int) Math.round(dist / 2.0));
        final ArrayList<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            final double t = (double) i / steps;
            pts.add(new double[] {x0 + (x1 - x0) * t, z0 + (z1 - z0) * t});
        }
        return pts;
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
        //
        // The fixture must be adversarial to be worth anything: a flat, wall-less elevation field makes
        // every transect saturate to the same +inf entrenchment everywhere, so it would pass even with
        // reach segmentation deleted outright (every point would independently classify the same way).
        // Here the valley pinches to a narrow, clearly entrenched width every other 8 px band along the
        // channel and opens to an unconfined valley in between -- a period far shorter than one reach
        // (64 px at this channel's clamped 16 px width) -- so a per-point classifier would flip type
        // roughly every 4 px while a reach-segmented one, sampling one transect per ~64 px reach, must not.
        final int pinchPeriod = 8;
        final double narrowHalfWidth = 6.0;
        final float wallHeight = 1000f;
        final float[] elev = alternatingValleyField(pinchPeriod, narrowHalfWidth, wallHeight);

        final RiverNetwork net = wideStraightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        // Prove the fixture is actually adversarial: sample entrenchment directly, 4 px apart (half the
        // pinch period), at a point guaranteed to fall in a pinch band and one guaranteed to fall in an
        // open band, and confirm the raw values land on opposite sides of a key threshold rather than
        // both saturating together.
        final ReachMetricsSampler rawSampler = new ReachMetricsSampler(elev, SIDE);
        final double[] normal = {0.0, 1.0};
        final double erPinch = rawSampler.entrenchmentRatio(new double[] {100.0, 256.0}, normal, 80.0, 16.0);
        final double erOpen = rawSampler.entrenchmentRatio(new double[] {104.0, 256.0}, normal, 80.0, 16.0);
        assertTrue(
                erPinch < HydrologyTuning.ER_ENTRENCHED,
                "pinch point must be entrenched (ER < " + HydrologyTuning.ER_ENTRENCHED + "), got " + erPinch);
        assertTrue(
                erOpen > HydrologyTuning.ER_SLIGHT,
                "open point must be unconfined (ER > " + HydrologyTuning.ER_SLIGHT + "), got " + erOpen);

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(elev, SIDE);
        classifier.prepare(net);

        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            int changes = 0;
            for (int i = 1; i < types.length; i++) if (types[i] != types[i - 1]) changes++;
            // ~300 px of channel / 64 px reaches is ~5 reaches, so at most 4 boundaries can change type --
            // regardless of how many times the adversarial valley itself pinches and opens in between.
            assertTrue(changes <= 5, "expected at most 5 type changes along one channel, got " + changes);
        }
    }

    /**
     * Row-major {@code x*SIDE+z} elevation field: a valley wall exists only when {@code (x/pinchPeriod)}
     * is even, at {@code |z-256| >= narrowHalfWidth}. In the alternating "open" bands the field is flat at
     * 0 everywhere, so a transect there never exceeds any plausible flood-prone stage and saturates.
     */
    private static float[] alternatingValleyField(int pinchPeriod, double narrowHalfWidth, float wallHeight) {
        final float[] elev = new float[SIDE * SIDE];
        for (int x = 0; x < SIDE; x++) {
            if ((x / pinchPeriod) % 2 != 0) continue; // open band: leave this column at 0
            for (int z = 0; z < SIDE; z++) {
                if (Math.abs(z - 256.0) >= narrowHalfWidth) elev[x * SIDE + z] = wallHeight;
            }
        }
        return elev;
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

    @Test
    void headwaterChannelsAreOrderedBeforeTheTrunkTheyFeed() throws Exception {
        // orderDownstreamFirst's ordering contract, exercised directly: every channel must appear after
        // the channel it flows into. A single straight channel (as every other test in this class uses)
        // cannot exercise this at all -- it is the reason the old sequencing bug shipped green. The
        // chained-confluence network below reproduces the id layout (a confluence's inflows holding lower
        // ids than the channel immediately downstream of it) that made the bug observable.
        final RiverNetwork net = chainedConfluenceNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final Method orderMethod =
                ReachRosgenClassifier.class.getDeclaredMethod("orderDownstreamFirst", RiverNetwork.class);
        orderMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<Channel> order = (List<Channel>) orderMethod.invoke(null, net);

        assertEquals(net.getChannels().size(), order.size(), "every channel must appear exactly once");
        final Map<Integer, Integer> position = new HashMap<>();
        for (int i = 0; i < order.size(); i++) position.put(order.get(i).channelId, i);

        for (Channel ch : net.getChannels()) {
            final Endpoint end = net.getNode(ch.endNodeId);
            if (end == null || end.outgoing == -1) continue; // flows straight into a drain
            assertTrue(
                    position.get(ch.channelId) > position.get(end.outgoing),
                    "channel " + ch.channelId + " must be walked after its downstream neighbour "
                            + end.outgoing + "; order was "
                            + order.stream().map(c -> c.channelId).toList());
        }
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
