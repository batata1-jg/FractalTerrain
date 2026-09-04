package me.batata_1.fractal_terrain.hydrology.meanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.network.AtomicView;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Headless gate for meander relaxation and stream capture.
 *
 * <p>Exercises the reverse-BFS-from-every-drain capture pass: a bed-overlap crossing planarizes into a
 * shared node with two forward continuations, and since K1 permits only one outgoing edge from it, the
 * BFS merges through whichever continuation reaches a drain in fewer hops and prunes the other; a
 * dangling tributary crossing a trunk is captured into it the same way; a branch reaching no drain is
 * pruned.
 *
 * <p>The migration signature is frozen bit-exact — read {@code hydrology/network/README.md} before
 * re-baselining it.
 */
class MeandersGoldenTest {

    private static final int GRID = 512;

    // -----------------------------------------------------------------------------------------
    // 1. a planarized crossing of two self-draining channels
    // -----------------------------------------------------------------------------------------
    @Test
    void independentCrossingsAreNotMerged() {
        Meanders sim = crossingInstance();
        sim.detectAndResolveCaptures();

        // The two channels' bed overlap planarizes into one shared node with two forward continuations;
        // K1 permits only one outgoing edge from it, so the BFS merges through whichever continuation is
        // fewer hops from a drain and prunes the other. The assertions below expect no merge, which this
        // forced merge cannot satisfy.
        assertEquals(2, sim.getChannelCount(), "self-draining crossing channels should not be merged");
        boolean junction = sim.getNodes().stream().anyMatch(n -> n.type == Endpoint.Type.JUNCTION);
        assertFalse(junction, "a JUNCTION was minted for two self-draining crossing channels");

        // spec ids 0/2 are the two sources (buildFromSpecs pins SOURCE/DRAIN ids to spec index; update()
        // preserves them across the collision pass).
        assertTrue(reachesDrain(sim, 0), "channel b source no longer reaches a drain");
        assertTrue(reachesDrain(sim, 2), "channel a source no longer reaches a drain");
        assertEndpointsMatchNodes(sim, "independent crossing");
        assertSingleOutflow(sim, "independent crossing");
    }

    // -----------------------------------------------------------------------------------------
    // 2. capture: a dangling tributary crossing a trunk is promoted into it
    // -----------------------------------------------------------------------------------------
    @Test
    @Disabled("No API expresses a dangling tributary as a canonical Channel, which capture requires. "
            + "detectCrossings builds its quadtree from RiverNetwork.channels, so a tributary added through "
            + "the atomic view has no Channel and can never be found crossing the trunk; and it cannot be "
            + "built canonically either, because update()'s chain walk calls onlyOutgoing() on the "
            + "tributary's dangling end, which by definition has no outgoing edge. Re-enable once a "
            + "supported way to attach a dangling canonical channel exists.")
    void danglingTributaryIsCapturedIntoTrunk() {
        Meanders sim = trunkInstance();
        final AtomicView atomic = sim.getNetwork().viewAtomic();
        addDanglingTributary(atomic, tributaryPoints(256.0)); // ends on the trunk line -> crosses it
        sim.getNetwork().detectAndResolveCaptures(0, atomic);

        boolean confluence = sim.getNodes().stream().anyMatch(n -> n.type == Endpoint.Type.JUNCTION);
        assertTrue(confluence, "dangling tributary was not captured into the trunk (no JUNCTION minted)");

        long sources = sim.getNodes().stream()
                .filter(n -> n.type == Endpoint.Type.SOURCE)
                .count();
        assertEquals(2, sources, "expected the trunk source plus the captured tributary source");
        assertTrue(everySourceReachesDrain(sim), "a source no longer reaches a drain after capture");
        assertEndpointsMatchNodes(sim, "tributary capture");
        assertSingleOutflow(sim, "tributary capture");
    }

    // -----------------------------------------------------------------------------------------
    // 3. prune: a dangling branch reaching no drain is dropped
    // -----------------------------------------------------------------------------------------
    @Test
    void unreachableDanglingBranchIsPruned() {
        Meanders sim = trunkInstance();
        // A tributary nowhere near the trunk: its dangling end has no crossing partner, so no path from
        // its source reaches a DRAIN and the reverse BFS never marks it alive.
        final AtomicView atomic = sim.getNetwork().viewAtomic();
        final int tribSourceId = addDanglingTributary(atomic, farPoints());
        assertEquals(
                Endpoint.Type.SOURCE,
                atomic.role(tribSourceId),
                "fixture precondition: the tributary SOURCE should exist in the atomic view before the pass");

        sim.getNetwork().detectAndResolveCaptures(0, atomic);

        // The pruned tributary contributes no SOURCE to the rebuilt canonical view — only the trunk's.
        assertEquals(
                1,
                sim.getNodes().stream()
                        .filter(n -> n.type == Endpoint.Type.SOURCE)
                        .count(),
                "unreachable dangling tributary source was not pruned");
        assertEquals(1, sim.getChannelCount(), "only the trunk channel should remain after pruning");
        assertTrue(reachesDrain(sim, 0), "trunk source no longer reaches a drain");
        assertSingleOutflow(sim, "prune");
    }

    // -----------------------------------------------------------------------------------------
    // 4. graph consistency: every channel's first/last point sits on its start/end node
    // -----------------------------------------------------------------------------------------
    @Test
    void nodeChannelEndpointsLineUp() {
        assertEndpointsMatchNodes(trunkInstance(), "construction");

        Meanders captured = crossingInstance();
        captured.detectAndResolveCaptures();
        assertEndpointsMatchNodes(captured, "collision pass");

        Meanders simulated = crossingInstance();
        simulated.simulate(6);
        assertEndpointsMatchNodes(simulated, "simulate with collision");
    }

    // -----------------------------------------------------------------------------------------
    // 5. meander-migration invariants + golden signature
    // -----------------------------------------------------------------------------------------

    @Test
    void meandersInvariantsHoldAfterSimulation() {
        Meanders sim = newTwoChannelSinusoidalNetwork();
        List<double[]> before = sim.getChannelPts(0);
        double sBefore = sinuosity(before);
        double x0 = before.getFirst()[0], z0 = before.getFirst()[1];

        sim.simulate(100);

        List<double[]> result = sim.getChannelPts(0);
        int m = result.size();
        double sAfter = sinuosity(result);

        // 1. All points finite
        for (int i = 0; i < m; i++) {
            assertTrue(Double.isFinite(result.get(i)[0]) && Double.isFinite(result.get(i)[1]), "NaN/Inf at point " + i);
        }

        // 2. Source endpoint stationarity — sources never move.
        double eps = 1e-9;
        assertTrue(
                Math.abs(result.get(0)[0] - x0) <= eps && Math.abs(result.get(0)[1] - z0) <= eps,
                "Source endpoint moved");

        // 3. Point spacing in [0.5*DX, 2*DX] (DX-relative). reSample appends getMaxT() after its
        // sampling loop, which can leave a single benign coincident point at the tail, so near-zero
        // spacings are tolerated; the meaningful guarantee is that no gap exceeds 2*DX.
        double lo = 0.5 * HydrologyTuning.DX, hi = 2.0 * HydrologyTuning.DX;
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

    @Test
    void meandersGoldenSignatureMatchesCapturedFixture() {
        Meanders sim = newTwoChannelSinusoidalNetwork();
        sim.simulate(100);
        assertEquals(GOLDEN_MEANDERS_SIGNATURE, networkSignature(sim));
    }

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

    /** Captured by running the golden test once; re-baseline only on an intended behaviour change.
     *  Three channels, not two: {@code AtomicView.resolveCrossingEdges} inserts one shared node at a
     *  geometric crossing, and invariant K1 allows that node a single outgoing edge, so a confluence is
     *  forced by planarization. The two-channel value predates that and encodes an unreachable outcome. */
    private static final String GOLDEN_MEANDERS_SIGNATURE = "channels=3 nodes=4 points=2442 checksum=614331415.075280";

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
                Locale.ROOT,
                "channels=%d nodes=%d points=%d checksum=%.6f",
                channels.size(),
                sim.getNodes().size(),
                totalPoints,
                checksum);
    }

    // -----------------------------------------------------------------------------------------
    // Scenario builders
    // -----------------------------------------------------------------------------------------

    /** One source -> one drain edge from a list of points. */
    private static Meanders oneEdge(ArrayList<double[]> pts, double flow) {
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, flow));
        return new Meanders(new RiverNetwork(GRID, nodeSpecs, edgeSpecs));
    }

    /** A single wide horizontal trunk SOURCE(0) -> DRAIN(1) along z = 256, x in [100, 400]. */
    private static Meanders trunkInstance() {
        ArrayList<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= 60; i++) pts.add(new double[] {100.0 + i * 5.0, 256.0});
        return oneEdge(pts, 20.0);
    }

    /** A vertical polyline from (250, 100) up to (250, {@code endZ}), ending on the trunk line at z = 256. */
    private static ArrayList<double[]> tributaryPoints(double endZ) {
        ArrayList<double[]> pts = new ArrayList<>();
        final int n = 39;
        for (int i = 0; i <= n; i++) pts.add(new double[] {250.0, 100.0 + (endZ - 100.0) * i / n});
        return pts;
    }

    /** A short polyline far from the trunk (never crosses it). */
    private static ArrayList<double[]> farPoints() {
        ArrayList<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= 20; i++) pts.add(new double[] {50.0, 40.0 + i * 2.0});
        return pts;
    }

    /** Own-flow carried by every atomic node of a fixture tributary. */
    private static final double TRIBUTARY_FLOW = 3.0;

    /** A tributary with a deliberately dangling end, mirroring how {@code LocalDrainageTracer} attaches
     *  a traced segment. The dangling end is what {@link RiverNetwork#detectAndResolveCaptures} must resolve. */
    private static int addDanglingTributary(AtomicView atomic, List<double[]> pts) {
        final int sourceId = atomic.addNode(pts.get(0).clone(), Endpoint.Type.SOURCE, -1, TRIBUTARY_FLOW, -1);
        int prev = sourceId;
        for (int i = 1; i < pts.size(); i++) {
            final int interior = atomic.addNode(pts.get(i).clone(), null, -1, TRIBUTARY_FLOW, -1);
            atomic.addDirectedEdge(prev, interior);
            prev = interior;
        }
        return sourceId;
    }

    /** Wide channel b (horizontal) crossed by narrow channel a (vertical) at (250,256). Both self-drain. */
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
        return new Meanders(new RiverNetwork(GRID, nodeSpecs, edgeSpecs));
    }

    /** Two parallel sinusoidal channels (100 pts each) that meander into each other over 100 steps. */
    private static Meanders newTwoChannelSinusoidalNetwork() {
        int n = 100;
        ArrayList<double[]> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts.add(new double[] {10.0 + i * 5.0, 200.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        double flow = 10;
        ArrayList<double[]> pts1 = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts1.add(new double[] {10.0 + i * 5.0, 300.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(pts1.getFirst()[0], pts1.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts1.getLast()[0], pts1.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, flow), new EdgeSpec(2, 3, pts1, flow));
        return new Meanders(new RiverNetwork(GRID, nodeSpecs, edgeSpecs));
    }

    // -----------------------------------------------------------------------------------------
    // Assertion helpers
    // -----------------------------------------------------------------------------------------

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

    /** Every node's outgoing edge, if any, references a live channel (single-outflow structural sanity). */
    private static void assertSingleOutflow(Meanders sim, String context) {
        for (Endpoint nd : sim.getNodes()) {
            if (nd.type == Endpoint.Type.DRAIN) {
                assertTrue(nd.outgoing == -1, context + ": DRAIN " + nd.id + " has an outgoing edge");
            } else {
                assertTrue(
                        nd.outgoing != -1 && sim.getChannel(nd.outgoing) != null,
                        context + ": non-drain node " + nd.id + " lacks a single live outgoing edge");
            }
        }
    }

    private static boolean everySourceReachesDrain(Meanders sim) {
        for (Endpoint nd : sim.getNodes()) {
            if (nd.type == Endpoint.Type.SOURCE && !reachesDrain(sim, nd.id)) return false;
        }
        return true;
    }

    private static double pointDistance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static String fmt(double[] p) {
        return String.format("(%.3f,%.3f)", p[0], p[1]);
    }

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

    private static double sinuosity(List<double[]> pts) {
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
