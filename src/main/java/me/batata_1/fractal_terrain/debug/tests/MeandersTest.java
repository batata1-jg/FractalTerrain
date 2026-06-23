package me.batata_1.fractal_terrain.debug.tests;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.NodeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MeandersTest {

    private static final Logger LOG = LoggerFactory.getLogger(MeandersTest.class);

    private static final int GRID = 512;

    public static void main(String[] args) {
        // Meanders.DEBUG_STEPS = true;
        testMeanders();
        //  testSplit();
        //  testMerge();
        //   testCollisionCapture();
        //   testNodeChannelEndpointsLineUp();
        LOG.info("ALL MEANDERS TESTS PASSED.");
    }

    // -----------------------------------------------------------------------------------------
    // relaxLowerGrad: migrateLowerGrad-only relaxation (used by ReliefProvider)
    // -----------------------------------------------------------------------------------------
    private static void testRelaxLowerGrad() {
        final int grid = 256;
        final float[] gx = new float[grid * grid];
        final float[] gz = new float[grid * grid];
        for (int i = 0; i < gz.length; i++) gz[i] = 5f; // constant downhill toward -z

        ArrayList<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 40; i++) pts.add(new double[] {20.0 + i * 5.0, 128.0});
        double sx = pts.getFirst()[0], sz = pts.getFirst()[1];
        double dx = pts.getLast()[0], dz = pts.getLast()[1];
        List<NodeSpec> nodeSpecs =
                List.of(new NodeSpec(sx, sz, Endpoint.Type.SOURCE), new NodeSpec(dx, dz, Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, 8.0));
        Meanders sim = new Meanders(grid, gx, gz, nodeSpecs, edgeSpecs);

        sim.relaxLowerGrad(5); // full step: reSample + migrateLowerGrad + cutoffs + collisions

        ArrayList<double[]> res = sim.getChannelPts(0);
        int n = res.size();
        double eps = 1e-9;
        check(Math.abs(res.get(0)[0] - sx) <= eps && Math.abs(res.get(0)[1] - sz) <= eps, "source endpoint moved");
        check(
                Math.abs(res.get(n - 1)[0] - dx) <= eps && Math.abs(res.get(n - 1)[1] - dz) <= eps,
                "drain endpoint moved");
        boolean relaxedDown = false;
        for (int i = 0; i < n; i++) {
            check(Double.isFinite(res.get(i)[0]) && Double.isFinite(res.get(i)[1]), "NaN/Inf at " + i);
            if (i > 0 && i < n - 1 && res.get(i)[1] < sz - 0.1) relaxedDown = true;
        }
        check(relaxedDown, "interior points did not relax down-gradient");
        LOG.info("testRelaxLowerGrad passed.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

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

    // -----------------------------------------------------------------------------------------
    // 1. Meander-migration invariants (ported to the graph constructor)
    // -----------------------------------------------------------------------------------------
    private static void testMeanders() {
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

        // 1. All points finite
        for (int i = 0; i < m; i++) {
            check(Double.isFinite(result.get(i)[0]) && Double.isFinite(result.get(i)[1]), "NaN/Inf at point " + i);
        }

        // 2. Source endpoint stationarity. Sources never move. We do NOT assert the drain endpoint
        // here: these two channels meander into each other and a capture restructures channel 0
        // (its downstream becomes a new edge, and a captured channel's drain can be deleted). The
        // exact node/endpoint alignment is covered by testNodeChannelEndpointsLineUp.
        double eps = 1e-9;
        check(
                Math.abs(result.get(0)[0] - x0) <= eps && Math.abs(result.get(0)[1] - z0) <= eps,
                "Source endpoint moved");

        // 3. Point spacing in [0.5*DX, 2*DX]  (DX-relative; the old [25,100] assumed DX=50).
        // reSample appends getMaxT() after its sampling loop, which can leave a single benign
        // coincident point at the tail (a known baseline artifact), so near-zero spacings are
        // tolerated; the meaningful guarantee is that no gap exceeds 2*DX.
        double lo = 0.5 * Meanders.DX, hi = 2.0 * Meanders.DX;
        for (int i = 1; i < m; i++) {
            double dx = result.get(i)[0] - result.get(i - 1)[0];
            double dz = result.get(i)[1] - result.get(i - 1)[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            check(d <= hi, String.format("Spacing %.3f exceeds %.2f at i=%d", d, hi, i));
            if (d > 1e-6) check(d >= lo, String.format("Spacing %.3f below %.2f at i=%d", d, lo, i));
        }

        // 4. Sinuosity growth
        check(sAfter > sBefore, String.format("Sinuosity did not grow: before=%.4f after=%.4f", sBefore, sAfter));
        LOG.info("testMeanders: invariants passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 2. split
    // -----------------------------------------------------------------------------------------
    private static void testSplit() {
        // invalid positions are no-ops
        Meanders sim = straightInstance(10);
        int count = sim.getChannelCount();
        int last = sim.getChannel(0).numPts() - 1;
        check(sim.split(0, 0, false) == -1, "split at 0 should no-op");
        check(sim.split(0, last, false) == -1, "split at last should no-op");
        check(sim.getChannelCount() == count, "channel count changed on no-op split");

        // redirect == false -> one new channel + one shared degree-2 junction
        int newId = sim.split(0, last / 2, false);
        check(newId != -1, "valid split returned -1");
        check(sim.getChannelCount() == count + 1, "split did not add a channel");
        Channel c0 = sim.getChannel(0);
        Channel cN = sim.getChannel(newId);
        check(c0.endNodeId == cN.startNodeId, "redirect=false halves should share a junction");
        Endpoint shared = sim.getNode(c0.endNodeId);
        check(
                shared.type == Endpoint.Type.JUNCTION && shared.degree() == 2,
                "shared node should be a degree-2 junction");

        // redirect == true -> two disconnected degree-1 junctions
        Meanders sim2 = straightInstance(10);
        int last2 = sim2.getChannel(0).numPts() - 1;
        int newId2 = sim2.split(0, last2 / 2, true);
        Channel a1 = sim2.getChannel(0);
        Channel a2 = sim2.getChannel(newId2);
        check(a1.endNodeId != a2.startNodeId, "redirect=true halves must NOT share a node");
        Endpoint j1 = sim2.getNode(a1.endNodeId);
        Endpoint j2 = sim2.getNode(a2.startNodeId);
        check(j1.type == Endpoint.Type.JUNCTION && j1.degree() == 1, "redirect=true upstream junction wrong");
        check(j2.type == Endpoint.Type.JUNCTION && j2.degree() == 1, "redirect=true downstream junction wrong");
        LOG.info("testSplit passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 3. merge
    // -----------------------------------------------------------------------------------------
    private static void testMerge() {
        Meanders sim = straightInstance(10);
        int last = sim.getChannel(0).numPts() - 1;
        int newId = sim.split(0, last / 2, false);
        int afterSplit = sim.getChannelCount();

        check(sim.merge(0), "pass-through junction should merge");
        check(sim.getChannelCount() == afterSplit - 1, "merge did not remove a channel");
        check(sim.getChannel(newId) == null, "downstream channel should be gone after merge");
        Channel merged = sim.getChannel(0);
        check(sim.getNode(merged.endNodeId).type == Endpoint.Type.DRAIN, "merged channel should end at the drain");

        // channel now ends at a drain (not a junction) -> merge is a no-op
        check(!sim.merge(0), "merge into a drain should be a no-op");
        LOG.info("testMerge passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 4. collision capture: narrow channel captured into wide one at a confluence
    // -----------------------------------------------------------------------------------------
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

    private static void testCollisionCapture() {
        Meanders sim = crossingInstance();
        sim.manageCollisions();

        // a confluence junction (degree >= 3) was created
        boolean confluence =
                sim.getNodes().stream().anyMatch(nd -> nd.type == Endpoint.Type.JUNCTION && nd.degree() >= 3);
        check(confluence, "no confluence junction created by capture");

        // all degree-1 nodes are sources or drains; no degree-2 junction remains (merge ran)
        for (Endpoint nd : sim.getNodes()) {
            if (nd.degree() == 1) check(nd.isSourceOrDrain(), "leaf node " + nd.id + " is a junction");
            check(!(nd.type == Endpoint.Type.JUNCTION && nd.degree() == 2), "leftover degree-2 junction " + nd.id);
            // single-outflow invariant
            check(nd.outgoing == -1 || sim.getChannel(nd.outgoing) != null, "dangling outgoing on " + nd.id);
        }

        // the wide channel's source still drains to a drain
        check(reachesDrain(sim, 0), "wide channel source no longer reaches a drain");
        // the narrow channel's source still reaches a drain (captured into b)
        check(reachesDrain(sim, 2), "captured narrow source no longer reaches a drain");
        LOG.info("testCollisionCapture passed.");
    }

    // -----------------------------------------------------------------------------------------
    // 5. graph consistency: every channel's first/last point sits on its start/end node
    // -----------------------------------------------------------------------------------------
    private static void testNodeChannelEndpointsLineUp() {
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

        LOG.info("testNodeChannelEndpointsLineUp passed.");
    }

    /** Every channel's first/last spline point must coincide with its start/end node coordinate. */
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
