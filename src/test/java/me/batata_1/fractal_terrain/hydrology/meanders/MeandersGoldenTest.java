package me.batata_1.fractal_terrain.hydrology.meanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * Headless JUnit conversion of {@code debug.tests.MeandersTest} (M-004): the graph-primitive
 * invariant checks (split/merge/collision capture/endpoint alignment) become plain assertions, and the
 * migration scenario ({@link #meandersInvariantsHoldAfterSimulation()}) additionally gets a golden
 * signature assertion so a silent numeric drift in the migration math fails the build even when the
 * broader invariants still hold. {@code Meanders}/{@code RiverNetwork} do no I/O and draw no randomness
 * (see {@link #meandersSimulationIsDeterministicAcrossRuns()}), so no canonicalization/tolerance was
 * needed to freeze the signature. {@code debug.tests.MeandersTest} is left untouched and keeps running
 * via the {@code meandersTest} Gradle task, including its PNG network dumps.
 */
class MeandersGoldenTest {

    private static final int GRID = 512;

    // -----------------------------------------------------------------------------------------
    // 1. split
    // -----------------------------------------------------------------------------------------
    @Test
    void split() {
        // invalid positions are no-ops
        Meanders sim = straightInstance(10);
        int count = sim.getChannelCount();
        int last = sim.getChannel(0).numPts() - 1;
        assertTrue(sim.split(0, 0, false) == -1, "split at 0 should no-op");
        assertTrue(sim.split(0, last, false) == -1, "split at last should no-op");
        assertTrue(sim.getChannelCount() == count, "channel count changed on no-op split");

        // redirect == false -> one new channel + one shared degree-2 junction
        int newId = sim.split(0, last / 2, false);
        assertTrue(newId != -1, "valid split returned -1");
        assertTrue(sim.getChannelCount() == count + 1, "split did not add a channel");
        Channel c0 = sim.getChannel(0);
        Channel cN = sim.getChannel(newId);
        assertTrue(c0.endNodeId == cN.startNodeId, "redirect=false halves should share a junction");
        Endpoint shared = sim.getNode(c0.endNodeId);
        assertTrue(
                shared.type == Endpoint.Type.JUNCTION && shared.degree() == 2,
                "shared node should be a degree-2 junction");

        // redirect == true -> two disconnected degree-1 junctions
        Meanders sim2 = straightInstance(10);
        int last2 = sim2.getChannel(0).numPts() - 1;
        int newId2 = sim2.split(0, last2 / 2, true);
        Channel a1 = sim2.getChannel(0);
        Channel a2 = sim2.getChannel(newId2);
        assertTrue(a1.endNodeId != a2.startNodeId, "redirect=true halves must NOT share a node");
        Endpoint j1 = sim2.getNode(a1.endNodeId);
        Endpoint j2 = sim2.getNode(a2.startNodeId);
        assertTrue(j1.type == Endpoint.Type.JUNCTION && j1.degree() == 1, "redirect=true upstream junction wrong");
        assertTrue(j2.type == Endpoint.Type.JUNCTION && j2.degree() == 1, "redirect=true downstream junction wrong");
    }

    // -----------------------------------------------------------------------------------------
    // 2. merge
    // -----------------------------------------------------------------------------------------
    @Test
    void merge() {
        Meanders sim = straightInstance(10);
        int last = sim.getChannel(0).numPts() - 1;
        int newId = sim.split(0, last / 2, false);
        int afterSplit = sim.getChannelCount();

        assertTrue(sim.merge(0), "pass-through junction should merge");
        assertTrue(sim.getChannelCount() == afterSplit - 1, "merge did not remove a channel");
        assertTrue(sim.getChannel(newId) == null, "downstream channel should be gone after merge");
        Channel merged = sim.getChannel(0);
        assertTrue(sim.getNode(merged.endNodeId).type == Endpoint.Type.DRAIN, "merged channel should end at the drain");

        // channel now ends at a drain (not a junction) -> merge is a no-op
        assertFalse(sim.merge(0), "merge into a drain should be a no-op");
    }

    // -----------------------------------------------------------------------------------------
    // 3. collision capture: narrow channel captured into wide one at a confluence
    // -----------------------------------------------------------------------------------------
    @Test
    void collisionCapture() {
        Meanders sim = crossingInstance();
        sim.manageCollisions();

        // a confluence junction (degree >= 3) was created
        boolean confluence =
                sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION && nd.degree() >= 3);
        assertTrue(confluence, "no confluence junction created by capture");

        // all degree-1 nodes are sources or drains; no degree-2 junction remains (merge ran)
        for (Endpoint nd : sim.getNodes()) {
            if (nd.degree() == 1) assertTrue(nd.isSourceOrDrain(), "leaf node " + nd.id + " is a junction");
            assertFalse(nd.type == Endpoint.Type.JUNCTION && nd.degree() == 2, "leftover degree-2 junction " + nd.id);
            // single-outflow invariant
            assertTrue(nd.outgoing == -1 || sim.getChannel(nd.outgoing) != null, "dangling outgoing on " + nd.id);
        }

        // the wide channel's source still drains to a drain
        assertTrue(reachesDrain(sim, 0), "wide channel source no longer reaches a drain");
        // the narrow channel's source still reaches a drain (captured into b)
        assertTrue(reachesDrain(sim, 2), "captured narrow source no longer reaches a drain");
    }

    // -----------------------------------------------------------------------------------------
    // 3b. same-contact-point capture: TWO narrow channels captured into one wide trunk at ONE point
    // -----------------------------------------------------------------------------------------
    @Test
    void sameContactPointCapture() {
        Meanders sim = sameContactPointInstance();
        sim.manageCollisions();

        // Both narrow channels contact the trunk at the SAME point -> they cluster with the trunk and
        // are captured into ONE junction collecting the trunk's upstream plus both tributaries
        // (degree >= 4). The old TreeMap-by-position grouping silently dropped one of the two crossings.
        boolean bigConfluence =
                sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION && nd.degree() >= 4);
        assertTrue(bigConfluence, "two same-point tributaries did not form a single >=degree-4 confluence");

        for (Endpoint nd : sim.getNodes()) {
            if (nd.degree() == 1) assertTrue(nd.isSourceOrDrain(), "leaf node " + nd.id + " is a junction");
            assertFalse(nd.type == Endpoint.Type.JUNCTION && nd.degree() == 2, "leftover degree-2 junction " + nd.id);
            assertTrue(nd.outgoing == -1 || sim.getChannel(nd.outgoing) != null, "dangling outgoing on " + nd.id);
        }

        // every source still reaches a drain -> no crossing was dropped
        assertTrue(reachesDrain(sim, 0), "trunk source no longer reaches a drain");
        assertTrue(reachesDrain(sim, 2), "narrow A source (captured) no longer reaches a drain");
        assertTrue(reachesDrain(sim, 4), "narrow B source (captured) no longer reaches a drain");
        assertEndpointsMatchNodes(sim, "same-contact-point capture");
    }

    // -----------------------------------------------------------------------------------------
    // 4. graph consistency: every channel's first/last point sits on its start/end node
    // -----------------------------------------------------------------------------------------
    @Test
    void nodeChannelEndpointsLineUp() {
        assertEndpointsMatchNodes(straightInstance(10), "construction");

        Meanders splitFalse = straightInstance(10);
        splitFalse.split(0, splitFalse.getChannel(0).numPts() / 2, false);
        assertEndpointsMatchNodes(splitFalse, "split redirect=false");

        Meanders splitTrue = straightInstance(10);
        splitTrue.split(0, splitTrue.getChannel(0).numPts() / 2, true);
        assertEndpointsMatchNodes(splitTrue, "split redirect=true");

        Meanders merged = straightInstance(10);
        merged.split(0, merged.getChannel(0).numPts() / 2, false);
        merged.merge(0);
        assertEndpointsMatchNodes(merged, "merge");

        Meanders captured = crossingInstance();
        captured.manageCollisions();
        assertEndpointsMatchNodes(captured, "collision capture");

        Meanders simulated = crossingInstance();
        simulated.simulate(6);
        assertEndpointsMatchNodes(simulated, "simulate with collision");
    }

    // -----------------------------------------------------------------------------------------
    // 5. meander-migration invariants + golden signature
    // -----------------------------------------------------------------------------------------

    /**
     * Ports {@code MeandersTest.testMeanders}'s invariant checks (finite points, pinned source, DX-
     * relative spacing, sinuosity growth) onto the two-channel sinusoidal scenario.
     */
    @Test
    void meandersInvariantsHoldAfterSimulation() {
        Meanders sim = newTwoChannelSinusoidalNetwork();
        ArrayList<double[]> before = sim.getChannelPts(0);
        double sBefore = sinuosity(before);
        double x0 = before.getFirst()[0], z0 = before.getFirst()[1];

        sim.simulate(100);

        ArrayList<double[]> result = sim.getChannelPts(0);
        int m = result.size();
        double sAfter = sinuosity(result);

        // 1. All points finite
        for (int i = 0; i < m; i++) {
            assertTrue(Double.isFinite(result.get(i)[0]) && Double.isFinite(result.get(i)[1]), "NaN/Inf at point " + i);
        }

        // 2. Source endpoint stationarity. Sources never move. The drain endpoint is not asserted
        // here: these two channels meander into each other and a capture restructures channel 0 (its
        // downstream becomes a new edge, and a captured channel's drain can be deleted). The exact
        // node/endpoint alignment is covered by nodeChannelEndpointsLineUp.
        double eps = 1e-9;
        assertTrue(
                Math.abs(result.get(0)[0] - x0) <= eps && Math.abs(result.get(0)[1] - z0) <= eps,
                "Source endpoint moved");

        // 3. Point spacing in [0.5*DX, 2*DX] (DX-relative). reSample appends getMaxT() after its
        // sampling loop, which can leave a single benign coincident point at the tail (a known baseline
        // artifact), so near-zero spacings are tolerated; the meaningful guarantee is that no gap
        // exceeds 2*DX.
        double lo = 0.5 * Meanders.DX, hi = 2.0 * Meanders.DX;
        for (int i = 1; i < m; i++) {
            double dx = result.get(i)[0] - result.get(i - 1)[0];
            double dz = result.get(i)[1] - result.get(i - 1)[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            assertTrue(d <= hi, String.format("Spacing %.3f exceeds %.2f at i=%d", d, hi, i));
            if (d > 1e-6) assertTrue(d >= lo, String.format("Spacing %.3f below %.2f at i=%d", d, lo, i));
        }

        // 4. Sinuosity growth
        assertTrue(sAfter > sBefore, String.format("Sinuosity did not grow: before=%.4f after=%.4f", sBefore, sAfter));
    }

    /**
     * Golden-signature gate: a canonical checksum over the whole network's channels/nodes after 100
     * simulation steps (see {@link #networkSignature}). Catches numeric drift in the migration math
     * that the invariant checks above would not (they only bound sinuosity growth and spacing, not the
     * exact resulting geometry).
     */
    @Test
    void meandersGoldenSignatureMatchesCapturedFixture() {
        Meanders sim = newTwoChannelSinusoidalNetwork();
        sim.simulate(100);
        assertEquals(GOLDEN_MEANDERS_SIGNATURE, networkSignature(sim));
    }

    /**
     * Determinism pre-check (M-004 step 3, run before the golden fixture above was frozen): rebuilds
     * and re-simulates the same scenario from scratch 5 times and asserts an identical signature every
     * time. {@code Meanders}/{@code RiverNetwork} draw no randomness and do no I/O, so this is expected
     * to be — and was confirmed — bit-identical run to run; no canonicalization or tolerance was needed.
     */
    @Test
    void meandersSimulationIsDeterministicAcrossRuns() {
        String first = null;
        for (int run = 0; run < 5; run++) {
            Meanders sim = newTwoChannelSinusoidalNetwork();
            sim.simulate(100);
            String signature = networkSignature(sim);
            if (first == null) first = signature;
            else assertEquals(first, signature, "run " + run + " diverged from run 0");
        }
    }

    /** Captured by running {@link #meandersGoldenSignatureMatchesCapturedFixture} once and logging it. */
    private static final String GOLDEN_MEANDERS_SIGNATURE = "channels=2 nodes=4 points=1591 checksum=401115734.478209";

    /**
     * Canonical, order-independent signature of a {@link Meanders} network's current state: channel and
     * node counts plus a checksum over every channel's spline points, so a shift in the migration math
     * changes the signature even if the broader per-scenario invariants still hold.
     */
    private static String networkSignature(Meanders sim) {
        List<Channel> channels = new ArrayList<>(sim.getChannels());
        channels.sort(Comparator.comparingInt(c -> c.channelId));
        double checksum = 0;
        int totalPoints = 0;
        for (Channel ch : channels) {
            for (double[] pt : ch.spline.points()) {
                checksum += pt[0] * 1000.0 + pt[1];
                totalPoints++;
            }
        }
        return String.format(
                "channels=%d nodes=%d points=%d checksum=%.6f",
                channels.size(), sim.getNodes().size(), totalPoints, checksum);
    }

    // -----------------------------------------------------------------------------------------
    // Scenario builders (ported verbatim from debug.tests.MeandersTest)
    // -----------------------------------------------------------------------------------------

    private static float[] zeroGrid() {
        return new float[GRID * GRID];
    }

    /** One source -> one drain edge from a list of points. */
    private static Meanders oneEdge(ArrayList<double[]> pts, double width) {
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, width));
        float[] g = zeroGrid();
        return new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);
    }

    private static Meanders straightInstance(double width) {
        ArrayList<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 60; i++) pts.add(new double[] {10.0 + i * 5.0, 100.0});
        return oneEdge(pts, width);
    }

    /** Wide channel b (horizontal) crossed by narrow channel a (vertical) at (250,256). */
    private static Meanders crossingInstance() {
        ArrayList<double[]> bPts = new ArrayList<>();
        for (int i = 0; i <= 60; i++) bPts.add(new double[] {100.0 + i * 5.0, 256.0});
        ArrayList<double[]> aPts = new ArrayList<>();
        for (int i = 0; i <= 60; i++) aPts.add(new double[] {250.0, 100.0 + i * 5.0});

        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(bPts.getFirst()[0], bPts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(bPts.getLast()[0], bPts.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(aPts.getFirst()[0], aPts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(aPts.getLast()[0], aPts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, bPts, 20.0), new EdgeSpec(2, 3, aPts, 5.0));
        float[] g = zeroGrid();
        return new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);
    }

    /** Wide trunk + two narrow channels (one vertical, one diagonal) all meeting exactly at (250,256). */
    private static Meanders sameContactPointInstance() {
        ArrayList<double[]> trunk = new ArrayList<>();
        for (int i = 0; i <= 60; i++) trunk.add(new double[] {100.0 + i * 5.0, 256.0}); // vertex 30 == (250,256)

        ArrayList<double[]> narrowA = new ArrayList<>();
        for (int i = 0; i <= 24; i++) narrowA.add(new double[] {250.0, 196.0 + i * 5.0}); // vertex 12 == (250,256)

        ArrayList<double[]> narrowB = new ArrayList<>();
        for (int i = 0; i <= 24; i++)
            narrowB.add(new double[] {190.0 + i * 5.0, 196.0 + i * 5.0}); // vertex 12 == (250,256)

        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(trunk.getFirst()[0], trunk.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(trunk.getLast()[0], trunk.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(narrowA.getFirst()[0], narrowA.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(narrowA.getLast()[0], narrowA.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(narrowB.getFirst()[0], narrowB.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(narrowB.getLast()[0], narrowB.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(
                new EdgeSpec(0, 1, trunk, 20.0), new EdgeSpec(2, 3, narrowA, 5.0), new EdgeSpec(4, 5, narrowB, 5.0));
        float[] g = zeroGrid();
        return new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);
    }

    /** Two parallel sinusoidal channels (100 pts each) that meander into each other over 100 steps. */
    private static Meanders newTwoChannelSinusoidalNetwork() {
        int n = 100;
        ArrayList<double[]> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts.add(new double[] {10.0 + i * 5.0, 200.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        double width = 10;
        ArrayList<double[]> pts1 = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts1.add(new double[] {10.0 + i * 5.0, 300.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(pts1.getFirst()[0], pts1.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts1.getLast()[0], pts1.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, width), new EdgeSpec(2, 3, pts1, width));
        float[] g = zeroGrid();
        return new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);
    }

    // -----------------------------------------------------------------------------------------
    // Assertion helpers
    // -----------------------------------------------------------------------------------------

    /** Every channel's first/last spline point must coincide with its start/end node coordinate. */
    private static void assertEndpointsMatchNodes(Meanders sim, String context) {
        double eps = 1e-9;
        for (Channel ch : sim.getChannels()) {
            Endpoint start = sim.getNode(ch.startNodeId);
            Endpoint end = sim.getNode(ch.endNodeId);
            assertTrue(start != null, context + ": channel " + ch.channelId + " has no start node");
            assertTrue(end != null, context + ": channel " + ch.channelId + " has no end node");
            double[] first = ch.spline.points().getFirst();
            double[] last = ch.spline.points().getLast();
            assertTrue(
                    pointDistance(first, start.coord) <= eps,
                    String.format(
                            "%s: channel %d first point %s != start node %d %s",
                            context, ch.channelId, fmt(first), start.id, fmt(start.coord)));
            assertTrue(
                    pointDistance(last, end.coord) <= eps,
                    String.format(
                            "%s: channel %d last point %s != end node %d %s",
                            context, ch.channelId, fmt(last), end.id, fmt(end.coord)));
        }
    }

    private static double pointDistance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static String fmt(double[] p) {
        return String.format("(%.3f,%.3f)", p[0], p[1]);
    }

    /** Follow outgoing edges downstream from the given source node id; true if a drain is reached. */
    private static boolean reachesDrain(Meanders sim, int sourceNodeId) {
        Endpoint n = sim.getNode(sourceNodeId);
        int guard = 0;
        while (n != null && guard++ < 100000) {
            if (n.type == Endpoint.Type.DRAIN) return true;
            if (n.outgoing == -1) return false;
            Channel ch = sim.getChannel(n.outgoing);
            if (ch == null) return false;
            n = sim.getNode(ch.endNodeId);
        }
        return false;
    }

    private static double sinuosity(ArrayList<double[]> pts) {
        double arcLen = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            double dx = pts.get(i)[0] - pts.get(i - 1)[0];
            double dz = pts.get(i)[1] - pts.get(i - 1)[1];
            arcLen += Math.sqrt(dx * dx + dz * dz);
        }
        double dx = pts.get(pts.size() - 1)[0] - pts.get(0)[0];
        double dz = pts.get(pts.size() - 1)[1] - pts.get(0)[1];
        double chord = Math.sqrt(dx * dx + dz * dz);
        return arcLen / Math.max(chord, 1e-9);
    }
}
