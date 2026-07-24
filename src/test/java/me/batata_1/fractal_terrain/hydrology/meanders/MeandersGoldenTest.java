package me.batata_1.fractal_terrain.hydrology.meanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * Headless JUnit gate for {@code Meanders}/{@code RiverNetwork} rewritten to the Phase-3 seam: all network
 * mutation now flows through {@code viewAtomic()}/{@code accumulateAndCorrectFlow}/{@code update}, and
 * collision handling ({@link RiverNetwork#manageCollisions}) is the deterministic two-mark DFS. The old
 * {@code split}/{@code merge} primitives and their tests are gone; the collision tests are written from
 * scratch to the new semantics:
 *
 * <ul>
 *   <li>two self-draining channels that merely cross are NOT merged (the DFS follows each stream to its
 *       own drain before considering any crossing);</li>
 *   <li>a dangling tributary (no drain of its own) that crosses a trunk IS captured into it via the
 *       promoted crossing edge — a confluence JUNCTION forms and the tributary reaches the trunk's drain;</li>
 *   <li>a dangling branch that reaches neither a drain nor a {@code streamMarked} node is pruned.</li>
 * </ul>
 *
 * {@code Meanders}/{@code RiverNetwork} do no I/O and draw no randomness (see
 * {@link #meandersSimulationIsDeterministicAcrossRuns()}), so the migration golden signature is frozen
 * bit-exact.
 */
class MeandersGoldenTest {

    private static final int GRID = 512;

    // -----------------------------------------------------------------------------------------
    // 1. crossing without capture: two self-draining channels are not merged
    // -----------------------------------------------------------------------------------------
    @Test
    void independentCrossingsAreNotMerged() {
        Meanders sim = crossingInstance();
        sim.manageCollisions();

        // both channels reach their own drain, so the DFS never uses the crossing edge -> no confluence.
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
    void danglingTributaryIsCapturedIntoTrunk() {
        Meanders sim = trunkInstance();
        addDanglingTributary(sim, tributaryPoints(256.0)); // ends on the trunk line -> crosses it
        sim.manageCollisions();

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
        // A tributary nowhere near the trunk: its dangling end has no crossing partner, so its DFS branch
        // reaches no drain. Its fresh SOURCE id is the next free node id after the trunk (0 SOURCE, 1 DRAIN).
        addDanglingTributary(sim, farPoints());
        final int tribSourceId = 2;
        assertTrue(
                sim.getNode(tribSourceId) != null && sim.getNode(tribSourceId).type == Endpoint.Type.SOURCE,
                "fixture precondition: tributary source id 2 should exist before the collision pass");

        sim.manageCollisions();

        assertNull(sim.getNode(tribSourceId), "unreachable dangling tributary source was not pruned");
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
        captured.manageCollisions();
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

    /**
     * Re-baselined for Phase 3: the two-mark DFS collision pass replaces the old split/merge stream-capture
     * (the two parallel channels now self-drain and are never merged), and every step re-derives flow and
     * folds the network back through the seam. Captured by running
     * {@link #meandersGoldenSignatureMatchesCapturedFixture} once. {@code networkSignature} formats with
     * {@code Locale.ROOT} for a portable decimal separator.
     */
    private static final String GOLDEN_MEANDERS_SIGNATURE = "channels=2 nodes=4 points=1656 checksum=438656243.300640";

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

    private static float[] zeroGrid() {
        return new float[GRID * GRID];
    }

    /** One source -> one drain edge from a list of points. */
    private static Meanders oneEdge(ArrayList<double[]> pts, double flow) {
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, flow));
        float[] g = zeroGrid();
        return new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);
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

    /** Add a standalone tributary (fresh SOURCE, dangling JUNCTION end) to be attached/pruned by the pass. */
    private static void addDanglingTributary(Meanders sim, ArrayList<double[]> pts) {
        final double[] flow = new double[pts.size()];
        Arrays.fill(flow, 3.0);
        sim.getNetwork().addLocalChannel(new Channel(pts, flow, 0), Endpoint.Type.JUNCTION);
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
        float[] g = zeroGrid();
        return new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);
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
