package me.batata_1.fractal_terrain.debug.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeandersTest {

    private static final Logger LOG = LoggerFactory.getLogger(MeandersTest.class);

    private static final int GRID = 512;

    public static void main(String[] args) {
        testIndependentCrossing();
        testDanglingTributaryCapture();
        testUnreachableBranchPruned();
        testNodeChannelEndpointsLineUp();
        testMeanders();
        LOG.info("ALL MEANDERS TESTS PASSED.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

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

    private static Meanders trunkInstance() {
        ArrayList<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= 60; i++) pts.add(new double[] {100.0 + i * 5.0, 256.0});
        return oneEdge(pts, 20.0);
    }

    private static void addDanglingTributary(Meanders sim, ArrayList<double[]> pts) {
        final double[] flow = new double[pts.size()];
        Arrays.fill(flow, 3.0);
        // sim.getNetwork().addLocalChannel(new Channel(pts, flow, 0), Endpoint.Type.JUNCTION);
    }

    // -----------------------------------------------------------------------------------------
    // 1. Meander-migration invariants
    // -----------------------------------------------------------------------------------------
    private static void testMeanders() {
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
        Meanders sim = new Meanders(GRID, g, g, nodeSpecs, edgeSpecs);

        double x0 = pts.getFirst()[0], z0 = pts.getFirst()[1];

        ArrayList<double[]> before = sim.getChannelPts(0);
        double sBefore = sinuosity(before);
        LOG.info("Before: sinuosity={}  points={}", String.format("%.4f", sBefore), before.size());
        Debug.river.see(sim, "before");

        sim.simulate(100);

        ArrayList<double[]> result = sim.getChannelPts(0);
        int m = result.size();
        double sAfter = sinuosity(result);
        LOG.info("After:  sinuosity={}  points={}", String.format("%.4f", sAfter), m);
        Debug.river.see(sim, "after");

        for (int i = 0; i < m; i++) {
            check(Double.isFinite(result.get(i)[0]) && Double.isFinite(result.get(i)[1]), "NaN/Inf at point " + i);
        }

        double eps = 1e-9;
        check(
                Math.abs(result.get(0)[0] - x0) <= eps && Math.abs(result.get(0)[1] - z0) <= eps,
                "Source endpoint moved");

        double lo = 0.5 * HydrologyTuning.DX, hi = 2.0 * HydrologyTuning.DX;
        for (int i = 1; i < m; i++) {
            double dx = result.get(i)[0] - result.get(i - 1)[0];
            double dz = result.get(i)[1] - result.get(i - 1)[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            check(d <= hi, String.format("Spacing %.3f exceeds %.2f at i=%d", d, hi, i));
            if (d > 1e-6) check(d >= lo, String.format("Spacing %.3f below %.2f at i=%d", d, lo, i));
        }

        check(sAfter > sBefore, String.format("Sinuosity did not grow: before=%.4f after=%.4f", sBefore, sAfter));
        LOG.info("testMeanders: invariants passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 2. crossing without capture
    // -----------------------------------------------------------------------------------------
    /** Wide channel b (horizontal) crossed by narrow channel a (vertical) at (250,256); both self-drain. */
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

    private static void testIndependentCrossing() {
        Meanders sim = crossingInstance();
        sim.manageCollisions();

        check(sim.getChannelCount() == 2, "self-draining crossing channels should not be merged");
        boolean junction = sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION);
        check(!junction, "a JUNCTION was minted for two self-draining crossing channels");
        check(reachesDrain(sim, 0), "channel b source no longer reaches a drain");
        check(reachesDrain(sim, 2), "channel a source no longer reaches a drain");
        assertEndpointsMatchNodes(sim, "independent crossing");
        LOG.info("testIndependentCrossing passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 3. capture: a dangling tributary crossing a trunk is promoted into it
    // -----------------------------------------------------------------------------------------
    private static void testDanglingTributaryCapture() {
        Meanders sim = trunkInstance();
        ArrayList<double[]> tribPts = new ArrayList<>();
        for (int i = 0; i <= 39; i++) tribPts.add(new double[] {250.0, 100.0 + (256.0 - 100.0) * i / 39.0});
        addDanglingTributary(sim, tribPts);
        sim.manageCollisions();

        boolean confluence = sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION);
        check(confluence, "dangling tributary was not captured into the trunk (no JUNCTION minted)");
        long sources = sim.getNodes().stream()
                .filter(nd -> nd.type == Endpoint.Type.SOURCE)
                .count();
        check(sources == 2, "expected the trunk source plus the captured tributary source");
        for (Endpoint nd : sim.getNodes())
            if (nd.type == Endpoint.Type.SOURCE) check(reachesDrain(sim, nd.id), "a source no longer reaches a drain");
        assertEndpointsMatchNodes(sim, "tributary capture");
        LOG.info("testDanglingTributaryCapture passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 4. prune: a dangling branch reaching no drain is dropped
    // -----------------------------------------------------------------------------------------
    private static void testUnreachableBranchPruned() {
        Meanders sim = trunkInstance();
        ArrayList<double[]> farPts = new ArrayList<>();
        for (int i = 0; i <= 20; i++) farPts.add(new double[] {50.0, 40.0 + i * 2.0});
        addDanglingTributary(sim, farPts);
        final int tribSourceId = 2;
        check(sim.getNode(tribSourceId) != null, "fixture precondition: tributary source id 2 should exist");

        sim.manageCollisions();

        check(sim.getNode(tribSourceId) == null, "unreachable dangling tributary source was not pruned");
        check(sim.getChannelCount() == 1, "only the trunk channel should remain after pruning");
        check(reachesDrain(sim, 0), "trunk source no longer reaches a drain");
        LOG.info("testUnreachableBranchPruned passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 5. graph consistency
    // -----------------------------------------------------------------------------------------
    private static void testNodeChannelEndpointsLineUp() {
        assertEndpointsMatchNodes(trunkInstance(), "construction");

        Meanders captured = crossingInstance();
        captured.manageCollisions();
        assertEndpointsMatchNodes(captured, "collision pass");

        Meanders simulated = crossingInstance();
        simulated.simulate(6);
        assertEndpointsMatchNodes(simulated, "simulate with collision");

        LOG.info("testNodeChannelEndpointsLineUp passed.");
    }

    private static void assertEndpointsMatchNodes(Meanders sim, String context) {
        double eps = 1e-9;
        for (Channel ch : sim.getChannels()) {
            Endpoint start = sim.getNode(ch.startNodeId);
            Endpoint end = sim.getNode(ch.endNodeId);
            check(start != null, context + ": channel " + ch.channelId + " has no start node");
            check(end != null, context + ": channel " + ch.channelId + " has no end node");
            double[] first = ch.spline.points().getFirst();
            double[] last = ch.spline.points().getLast();
            check(
                    pointDistance(first, start.coord) <= eps,
                    String.format(
                            "%s: channel %d first point %s != start node %d %s",
                            context, ch.channelId, fmt(first), start.id, fmt(start.coord)));
            check(
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
