package me.batata_1.fractal_terrain.debug.tests;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.network.AtomicView;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manual reproduction of {@code MeandersGoldenTest}'s stream-capture fixtures, so the DFS->BFS rework of
 * {@code RiverNetwork.manageCollisions} can be observed without a compiling JUnit suite.
 *
 * <p>Prints facts rather than asserting: every scenario runs to completion regardless of outcome, and a
 * final summary reports which scenarios matched their expected result.
 */
public class CaptureSelectionTest {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureSelectionTest.class);

    private static final double TRIBUTARY_FLOW = 3.0;

    private static boolean allPassed = true;

    public static void main(String[] args) {
        runCrossingInstance();
        runTrunkWithFarTributary();
        runSinusoidalSimulation();
        LOG.info(allPassed ? "ALL SCENARIOS MATCHED EXPECTATIONS." : "SOME SCENARIOS DID NOT MATCH EXPECTATIONS.");
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 1: two self-draining channels crossing should NOT be merged
    // -----------------------------------------------------------------------------------------

    private static void runCrossingInstance() {
        LOG.info("=== Scenario 1: crossingInstance (independent crossing) ===");
        Meanders sim = crossingInstance();
        final RiverNetwork network = sim.getNetwork();

        // Diagnostic block: same call sim.manageCollisions() would make internally (currentStep starts at
        // 0), but done by hand so we keep a reference to the AtomicView and can inspect it both sides of
        // the pass. manageCollisions mutates its `atomic` argument in place, so this reference is valid
        // after the call too.
        final AtomicView atomic = network.viewAtomic();
        LOG.info("[S1-DIAG] atomic view size right after viewAtomic(): {}", atomic.size());
        dumpAtomicNodes(atomic, "[S1-DIAG][pre-collision]");

        network.manageCollisions(0, atomic);

        LOG.info(
                "[S1-DIAG] atomic view size after manageCollisions() returns (same AtomicView object; reflects "
                        + "any nodes resolveCrossingEdges added during planarization): {}",
                atomic.size());
        dumpChannelsAndNodes(sim, "[S1-DIAG][post-collision]");

        int channelCount = sim.getChannelCount();
        int nodeCount = sim.getNodes().size();
        long sourceCount = countByType(sim, Endpoint.Type.SOURCE);
        long drainCount = countByType(sim, Endpoint.Type.DRAIN);
        long junctionCount = countByType(sim, Endpoint.Type.JUNCTION);
        boolean bothReach = reachesDrain(sim, 0) && reachesDrain(sim, 2);
        boolean singleOutflow = singleOutflowHolds(sim);

        LOG.info(
                "channels={} nodes={} SOURCE={} DRAIN={} JUNCTION={} bothSourcesReachDrain={} singleOutflow={}",
                channelCount,
                nodeCount,
                sourceCount,
                drainCount,
                junctionCount,
                bothReach,
                singleOutflow);

        boolean expected = channelCount == 2 && junctionCount == 0 && bothReach;
        verdict("crossingInstance NOT merged: 2 channels, 0 JUNCTION nodes, both sources reach a drain", expected);
    }

    /** [S1-DIAG] Dumps every atomic node's id, position and role, confirming (or falsifying) the assumed
     *  channel-b=[0..60] / channel-a=[61..121] atomic-id layout without touching package-private state. */
    private static void dumpAtomicNodes(AtomicView atomic, String label) {
        LOG.info("{} dumping {} atomic nodes:", label, atomic.size());
        for (int id = 0; id < atomic.size(); id++) {
            double[] pos = atomic.pos(id);
            LOG.info("{}   atomic[{}] pos=({}, {}) role={}", label, id, pos[0], pos[1], atomic.role(id));
        }
    }

    /** [S1-DIAG] Dumps every surviving channel (endpoints + first/last point) and every surviving node
     *  (type + coord), localizing exactly where a JUNCTION was minted, if any. */
    private static void dumpChannelsAndNodes(Meanders sim, String label) {
        List<Channel> channels = new ObjectArrayList<>(sim.getChannels());
        channels.sort(Comparator.comparingInt(c -> c.channelId));
        LOG.info("{} dumping {} channels:", label, channels.size());
        for (Channel ch : channels) {
            Endpoint start = sim.getNode(ch.startNodeId);
            Endpoint end = sim.getNode(ch.endNodeId);
            double[] first = ch.spline.points().getFirst();
            double[] last = ch.spline.points().getLast();
            LOG.info(
                    "{}   channel[{}] start=node{}({}) end=node{}({}) firstPt=({}, {}) lastPt=({}, {})",
                    label,
                    ch.channelId,
                    start.id,
                    start.type,
                    end.id,
                    end.type,
                    first[0],
                    first[1],
                    last[0],
                    last[1]);
        }
        List<Endpoint> nodes = new ObjectArrayList<>(sim.getNodes());
        nodes.sort(Comparator.comparingInt(n -> n.id));
        LOG.info("{} dumping {} nodes:", label, nodes.size());
        for (Endpoint nd : nodes) {
            LOG.info("{}   node[{}] type={} coord=({}, {})", label, nd.id, nd.type, nd.coord[0], nd.coord[1]);
        }
    }

    /** Wide channel b (horizontal) crossed by narrow channel a (vertical) at (250,256); both self-drain. */
    private static Meanders crossingInstance() {
        List<double[]> bPts = new ObjectArrayList<>();
        for (int i = 0; i <= 60; i++) bPts.add(new double[] {100.0 + i * 5.0, 256.0});
        List<double[]> aPts = new ObjectArrayList<>();
        for (int i = 0; i <= 60; i++) aPts.add(new double[] {250.0, 100.0 + i * 5.0});

        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(bPts.getFirst()[0], bPts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(bPts.getLast()[0], bPts.getLast()[1], Endpoint.Type.DRAIN),
                new NodeSpec(aPts.getFirst()[0], aPts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(aPts.getLast()[0], aPts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, bPts, 20.0), new EdgeSpec(2, 3, aPts, 5.0));
        return new Meanders(new RiverNetwork(HydrologyTileGeometry.GRID, nodeSpecs, edgeSpecs));
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 2: a dangling tributary nowhere near the trunk is pruned
    // -----------------------------------------------------------------------------------------

    private static void runTrunkWithFarTributary() {
        LOG.info("=== Scenario 2: trunkInstance + farPoints() dangling tributary (prune) ===");
        Meanders sim = trunkInstance();
        final AtomicView atomic = sim.getNetwork().viewAtomic();
        final int tribSourceId = addDanglingTributary(atomic, farPoints());
        LOG.info("fixture precondition: tributary atomic id {} role={}", tribSourceId, atomic.role(tribSourceId));

        sim.getNetwork().manageCollisions(0, atomic);

        int channelCount = sim.getChannelCount();
        int nodeCount = sim.getNodes().size();
        long sourceCount = countByType(sim, Endpoint.Type.SOURCE);
        long drainCount = countByType(sim, Endpoint.Type.DRAIN);
        long junctionCount = countByType(sim, Endpoint.Type.JUNCTION);
        boolean trunkReaches = reachesDrain(sim, 0);
        boolean singleOutflow = singleOutflowHolds(sim);

        LOG.info(
                "channels={} nodes={} SOURCE={} DRAIN={} JUNCTION={} trunkSourceReachesDrain={} singleOutflow={}",
                channelCount,
                nodeCount,
                sourceCount,
                drainCount,
                junctionCount,
                trunkReaches,
                singleOutflow);

        boolean expected = channelCount == 1 && sourceCount == 1 && trunkReaches;
        verdict("unreachable tributary pruned: 1 channel, 1 SOURCE, trunk source reaches a drain", expected);
    }

    /** One source -> one drain edge from a list of points. */
    private static Meanders oneEdge(List<double[]> pts, double flow) {
        List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE),
                new NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN));
        List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, pts, flow));
        return new Meanders(new RiverNetwork(HydrologyTileGeometry.GRID, nodeSpecs, edgeSpecs));
    }

    /** A single wide horizontal trunk SOURCE(0) -> DRAIN(1) along z = 256, x in [100, 400]. */
    private static Meanders trunkInstance() {
        List<double[]> pts = new ObjectArrayList<>();
        for (int i = 0; i <= 60; i++) pts.add(new double[] {100.0 + i * 5.0, 256.0});
        return oneEdge(pts, 20.0);
    }

    /** A short polyline far from the trunk (never crosses it). */
    private static List<double[]> farPoints() {
        List<double[]> pts = new ObjectArrayList<>();
        for (int i = 0; i <= 20; i++) pts.add(new double[] {50.0, 40.0 + i * 2.0});
        return pts;
    }

    /** Adds a dangling tributary directly through the atomic view, mirroring how {@code LocalDrainageTracer}
     *  attaches a traced segment; the dangling end is what {@code manageCollisions} must resolve. */
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

    // -----------------------------------------------------------------------------------------
    // Scenario 3: two parallel sinusoids simulated 100 steps, real captures exercised
    // -----------------------------------------------------------------------------------------

    private static void runSinusoidalSimulation() {
        LOG.info("=== Scenario 3: newTwoChannelSinusoidalNetwork simulated 100 steps ===");
        Meanders sim = newTwoChannelSinusoidalNetwork();
        sim.simulate(100);

        String actual = networkSignature(sim);
        boolean matches = GOLDEN_MEANDERS_SIGNATURE.equals(actual);

        LOG.info("frozen  : {}", GOLDEN_MEANDERS_SIGNATURE);
        LOG.info("computed: {}", actual);
        verdict("signature matches the frozen golden fixture", matches);
    }

    /** Captured by the JUnit golden test; re-baseline only on an intended capture-behaviour change. */
    private static final String GOLDEN_MEANDERS_SIGNATURE = "channels=2 nodes=4 points=1656 checksum=438656243.300640";

    /** Two parallel sinusoidal channels (100 pts each) that meander into each other over 100 steps. */
    private static Meanders newTwoChannelSinusoidalNetwork() {
        int n = 100;
        List<double[]> pts = new ObjectArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pts.add(new double[] {10.0 + i * 5.0, 200.0 + 5.0 * Math.sin(2.0 * Math.PI * i / (n - 1))});
        }
        double flow = 10;
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
        return new Meanders(new RiverNetwork(HydrologyTileGeometry.GRID, nodeSpecs, edgeSpecs));
    }

    private static String networkSignature(Meanders sim) {
        List<Channel> channels = new ObjectArrayList<>(sim.getChannels());
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
    // Boolean fact helpers (print PASS/FAIL, never throw — this is a harness, not a test)
    // -----------------------------------------------------------------------------------------

    private static long countByType(Meanders sim, Endpoint.Type type) {
        return sim.getNodes().stream().filter(n -> n.type == type).count();
    }

    /** Every node's outgoing edge, if any, references a live channel (single-outflow structural sanity). */
    private static boolean singleOutflowHolds(Meanders sim) {
        for (Endpoint nd : sim.getNodes()) {
            if (nd.type == Endpoint.Type.DRAIN) {
                if (nd.outgoing != -1) return false;
            } else if (nd.outgoing == -1 || sim.getChannel(nd.outgoing) == null) {
                return false;
            }
        }
        return true;
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

    private static void verdict(String expectation, boolean matched) {
        if (!matched) allPassed = false;
        LOG.info("verdict [{}]: {}", matched ? "PASS" : "FAIL", expectation);
    }
}
