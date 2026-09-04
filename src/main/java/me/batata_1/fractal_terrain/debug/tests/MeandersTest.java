package me.batata_1.fractal_terrain.debug.tests;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeandersTest {

    private static final Logger LOG = LoggerFactory.getLogger(MeandersTest.class);

    public static void main(String[] args) {
        testCrossingBecomesConfluence();
        testLosingBranchPruned();
        testDisjointChannelsUntouched();
        testNodeChannelEndpointsLineUp();
        testMeanders();
        LOG.info("ALL MEANDERS TESTS PASSED.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    /** {@code n + 1} evenly spaced points from {@code (x0,z0)} to {@code (x1,z1)}, inclusive. */
    private static List<double[]> straightRun(double x0, double z0, double x1, double z1, int n) {
        List<double[]> pts = new ObjectArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            pts.add(new double[] {x0 + (x1 - x0) * t, z0 + (z1 - z0) * t});
        }
        return pts;
    }

    /** Two source-to-drain channels ("b" then "a") over node ids 0=SOURCE b, 1=DRAIN b, 2=SOURCE a, 3=DRAIN a. */
    private static Meanders twoChannelNetwork(List<double[]> bPts, double bFlow, List<double[]> aPts, double aFlow) {
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(bPts.getFirst()[0], bPts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(bPts.getLast()[0], bPts.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(aPts.getFirst()[0], aPts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(aPts.getLast()[0], aPts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, bPts, bFlow), new EdgeSpec(2, 3, aPts, aFlow));
        return new Meanders(new RiverNetwork(HydrologyTileGeometry.GRID, nodeSpecs, edgeSpecs));
    }

    // -----------------------------------------------------------------------------------------
    // 1. Meander-migration invariants
    // -----------------------------------------------------------------------------------------
    private static void testMeanders() {
        int n = 100;
        List<double[]> pts = new ObjectArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts.add(new double[] {10.0 + i * 5.0, 200.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        double flow = 200;
        List<double[]> pts1 = new ObjectArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts1.add(new double[] {10.0 + i * 5.0, 300.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(pts1.getFirst()[0], pts1.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts1.getLast()[0], pts1.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, flow), new EdgeSpec(2, 3, pts1, flow));
        Meanders sim = new Meanders(new RiverNetwork(HydrologyTileGeometry.GRID, nodeSpecs, edgeSpecs));

        double x0 = pts.getFirst()[0], z0 = pts.getFirst()[1];

        List<double[]> before = sim.getChannelPts(0);
        double sBefore = sinuosity(before);
        LOG.info("Before: sinuosity={}  points={}", String.format("%.4f", sBefore), before.size());
        Debug.river.see(sim, "before");

        sim.simulate(100,10);

        List<double[]> result = sim.getChannelPts(0);
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
    // 2. crossing forced into a confluence
    // -----------------------------------------------------------------------------------------
    /** Wide channel b (horizontal) crossed by narrow channel a (vertical) at (250,256); both self-drain. */
    private static Meanders crossingInstance() {
        return twoChannelNetwork(
                straightRun(100.0, 256.0, 400.0, 256.0, 60), 200.0, straightRun(250.0, 100.0, 250.0, 400.0, 60), 200.0);
    }

    private static void testCrossingBecomesConfluence() {
        Meanders sim = crossingInstance();
        sim.manageCollisions();

        // Planarization inserts one shared atomic node at the geometric crossing, and invariant K1
        // (single outgoing edge per node) forces that shared node into a single confluence — two
        // rivers meeting at the same elevation genuinely do merge, so this is the physically correct
        // outcome, not a defect to route around.
        check(sim.getChannelCount() == 3, "crossing should split into 3 channels around the new junction");
        boolean junction = sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION);
        check(junction, "no JUNCTION was minted at the crossing");
        check(reachesDrain(sim, 0), "channel b source no longer reaches a drain");
        check(reachesDrain(sim, 2), "channel a source no longer reaches a drain");
        assertEndpointsMatchNodes(sim, "crossing becomes confluence");
        LOG.info("testCrossingBecomesConfluence passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 3. the losing branch of a merged crossing is pruned, without orphaning anything
    // -----------------------------------------------------------------------------------------
    private static void testLosingBranchPruned() {
        Meanders sim = crossingInstance();
        sim.manageCollisions();

        // The reverse-BFS capture picks the shorter hop path to the surviving drain, so channel b's
        // downstream half and its original drain (node 1) are orphaned and dropped.
        check(sim.getNode(1) == null, "losing branch's drain (node 1) was not pruned");
        long drains = sim.getNodes().stream()
                .filter(nd -> nd.type == Endpoint.Type.DRAIN)
                .count();
        check(drains == 1, "expected exactly one surviving DRAIN after the collision pass");

        Set<Integer> fed = new HashSet<>();
        for (Channel ch : sim.getChannels()) fed.add(ch.endNodeId);
        for (Channel ch : sim.getChannels()) {
            Endpoint start = sim.getNode(ch.startNodeId);
            check(
                    start.type == Endpoint.Type.SOURCE || fed.contains(start.id),
                    "channel " + ch.channelId + " starts at orphaned node " + start.id);
        }
        LOG.info("testLosingBranchPruned passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 4. two channels that never come close stay untouched by the collision pass
    // -----------------------------------------------------------------------------------------
    private static Meanders disjointInstance() {
        return twoChannelNetwork(
                straightRun(100.0, 256.0, 400.0, 256.0, 60), 20.0, straightRun(100.0, 60.0, 400.0, 60.0, 60), 3.0);
    }

    private static void testDisjointChannelsUntouched() {
        Meanders sim = disjointInstance();
        int trunkPtsBefore = sim.getChannel(0).numPts();
        int otherPtsBefore = sim.getChannel(1).numPts();

        sim.manageCollisions();

        check(sim.getChannelCount() == 2, "disjoint channels should not be merged");
        boolean junction = sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION);
        check(!junction, "a JUNCTION was minted for two channels that never cross");

        check(nodeUnchanged(sim, 0, Endpoint.Type.SOURCE, 100.0, 256.0), "node 0 changed");
        check(nodeUnchanged(sim, 1, Endpoint.Type.DRAIN, 400.0, 256.0), "node 1 changed");
        check(nodeUnchanged(sim, 2, Endpoint.Type.SOURCE, 100.0, 60.0), "node 2 changed");
        check(nodeUnchanged(sim, 3, Endpoint.Type.DRAIN, 400.0, 60.0), "node 3 changed");

        Channel trunk = sim.getChannel(sim.getNode(0).outgoing);
        Channel other = sim.getChannel(sim.getNode(2).outgoing);
        check(trunk.endNodeId == 1, "trunk is no longer wired to node 1");
        check(other.endNodeId == 3, "other channel is no longer wired to node 3");
        check(trunk.numPts() == trunkPtsBefore, "trunk point count changed");
        check(other.numPts() == otherPtsBefore, "other channel point count changed");

        assertEndpointsMatchNodes(sim, "disjoint channels");
        LOG.info("testDisjointChannelsUntouched passed.");
    }

    private static boolean nodeUnchanged(Meanders sim, int nodeId, Endpoint.Type type, double x, double z) {
        Endpoint n = sim.getNode(nodeId);
        return n != null && n.type == type && pointDistance(n.coord, new double[] {x, z}) <= 1e-9;
    }

    // -----------------------------------------------------------------------------------------
    // 5. graph consistency
    // -----------------------------------------------------------------------------------------
    private static void testNodeChannelEndpointsLineUp() {
        assertEndpointsMatchNodes(crossingInstance(), "construction");

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
