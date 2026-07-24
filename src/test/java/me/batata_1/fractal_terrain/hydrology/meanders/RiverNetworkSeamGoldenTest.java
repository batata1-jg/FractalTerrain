package me.batata_1.fractal_terrain.hydrology.meanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.AtomicView;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * Phase-1 round-trip golden for the canonical&harr;atomic seam ({@link RiverNetwork#viewAtomic()} /
 * {@link RiverNetwork#update}). Nothing in production calls the seam yet; this test is the Phase-1 gate.
 *
 * <p>Modelled on {@code MeandersGoldenTest.networkSignature} (order-independent additive checksum over
 * channel/node counts + spline points) and {@code LocalRiverGoldenTest.networkChecksum} (a structural
 * checksum over the same). The seam's {@link RiverNetwork#update} mutates the network <b>in place</b>, so
 * the signature is captured <b>before</b> the round trip and re-read afterwards:
 * {@code before = signature(net); net.update(net.viewAtomic()); assert before == signature(net)}.
 *
 * <p>The fixture is a genuine JUNCTION confluence in the <i>canonical</i> view — two SOURCE&rarr;JUNCTION
 * edges plus one JUNCTION&rarr;DRAIN edge — because the round-trip signature alone does not exercise
 * {@link RiverNetwork#update}'s load-bearing rule (preserve SOURCE/DRAIN canonical ids, re-assign only
 * JUNCTION-equivalents). Dedicated assertions check that rule directly: every SOURCE/DRAIN id survives the
 * in-place round trip, while the JUNCTION id is free to churn (and does) with the node count unchanged.
 *
 * <p><b>The signature is bit-exact, not quantized.</b> The round trip's measured max per-coordinate
 * drift on this fixture is <b>0.0</b> (a bijective before/after point matching): every incident channel's
 * shared-junction endpoint resolves to exactly {@code (250.0, 256.0)} — {@code reSample} reproduces this
 * straight-segment fixture's endpoints with zero FP error, so the full point multiset is bit-identical
 * before and after (an earlier "few-ULP drift" hypothesis was measured false). The checksum is therefore
 * an <b>exact</b> {@code doubleToLongBits} combine over every spline point, accumulated with commutative
 * long arithmetic (order-independent, exact mod 2^64). This is maximally sharp — any 1-ULP coordinate
 * change anywhere fails the gate — while the commutative, channel-id-free accumulation still tolerates the
 * channel-id reshuffle {@code update()} performs.
 */
class RiverNetworkSeamGoldenTest {

    private static final int GRID = 512;
    private static final double RESAMPLE_DIST = 2.0;

    // ---------------------------------------------------------------------------------------------
    // Fixture: a shared JUNCTION confluence (two SOURCE->JUNCTION edges + one JUNCTION->DRAIN edge)
    // ---------------------------------------------------------------------------------------------

    private static final double[] SOURCE_A = {100.0, 150.0};
    private static final double[] SOURCE_B = {400.0, 150.0};
    private static final double[] JUNCTION = {250.0, 256.0};
    private static final double[] DRAIN = {250.0, 400.0};

    /**
     * A canonical network with one real confluence: {@code sourceA -> junction}, {@code sourceB ->
     * junction}, {@code junction -> drain}. The junction is a JUNCTION endpoint of in-degree 2, out-degree
     * 1 — an in-degree&ge;2 structural node in the atomic view (unlike the crossing-edge fixtures in
     * {@code MeandersGoldenTest}, which never build a shared graph node).
     */
    private static RiverNetwork confluenceNetwork() {
        final List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(SOURCE_A[0], SOURCE_A[1], Endpoint.Type.SOURCE), // 0
                new NodeSpec(SOURCE_B[0], SOURCE_B[1], Endpoint.Type.SOURCE), // 1
                new NodeSpec(JUNCTION[0], JUNCTION[1], Endpoint.Type.JUNCTION), // 2
                new NodeSpec(DRAIN[0], DRAIN[1], Endpoint.Type.DRAIN)); // 3
        final List<EdgeSpec> edgeSpecs = List.of(
                new EdgeSpec(0, 2, segment(SOURCE_A, JUNCTION, 24), 8.0),
                new EdgeSpec(1, 2, segment(SOURCE_B, JUNCTION, 24), 8.0),
                new EdgeSpec(2, 3, segment(JUNCTION, DRAIN, 24), 12.0));
        return new RiverNetwork(GRID, nodeSpecs, edgeSpecs, false, 0, RESAMPLE_DIST);
    }

    /** A straight polyline of {@code n+1} points from {@code a} to {@code b} (endpoints exact). */
    private static ArrayList<double[]> segment(double[] a, double[] b, int n) {
        final ArrayList<double[]> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            final double t = (double) i / n;
            pts.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
        }
        return pts;
    }

    // ---------------------------------------------------------------------------------------------
    // 1. round trip preserves points + topology (the core Phase-1 gate)
    // ---------------------------------------------------------------------------------------------

    @Test
    void roundTripPreservesPointsAndTopology() {
        final RiverNetwork net = confluenceNetwork();
        assertHasJunctionConfluence(net);

        final String before = signature(net); // capture FIRST — update() mutates in place
        final AtomicView atomic = net.viewAtomic();
        final RiverNetwork same = net.update(atomic);

        assertTrue(same == net, "update() must mutate and return the same instance, not a fresh network");
        assertEquals(before, signature(net), "round trip changed points/topology");
    }

    // ---------------------------------------------------------------------------------------------
    // 2. SOURCE/DRAIN canonical ids survive; JUNCTION-equivalent ids are free to churn
    // ---------------------------------------------------------------------------------------------

    @Test
    void sourceAndDrainIdsSurviveJunctionIdsChurn() {
        final RiverNetwork net = confluenceNetwork();

        final Set<Integer> sourceIdsBefore = idsOfType(net, Endpoint.Type.SOURCE);
        final Set<Integer> drainIdsBefore = idsOfType(net, Endpoint.Type.DRAIN);
        final Set<Integer> junctionIdsBefore = idsOfType(net, Endpoint.Type.JUNCTION);
        final int nodeCountBefore = net.getNodes().size();

        net.update(net.viewAtomic());

        final Set<Integer> sourceIdsAfter = idsOfType(net, Endpoint.Type.SOURCE);
        final Set<Integer> drainIdsAfter = idsOfType(net, Endpoint.Type.DRAIN);
        final Set<Integer> junctionIdsAfter = idsOfType(net, Endpoint.Type.JUNCTION);

        // Load-bearing rule (Finding 1): every SOURCE and DRAIN keeps its canonical id.
        assertEquals(sourceIdsBefore, sourceIdsAfter, "a SOURCE canonical id was not preserved across update()");
        assertEquals(drainIdsBefore, drainIdsAfter, "a DRAIN canonical id was not preserved across update()");

        // Companion: JUNCTION-equivalent ids may be freely re-assigned, and here they actually change,
        // while the total node count and the JUNCTION count are unchanged.
        assertEquals(nodeCountBefore, net.getNodes().size(), "node count changed across update()");
        assertEquals(junctionIdsBefore.size(), junctionIdsAfter.size(), "JUNCTION count changed across update()");
        assertNotEquals(
                junctionIdsBefore,
                junctionIdsAfter,
                "JUNCTION ids should churn (be re-assigned past the max preserved SOURCE/DRAIN id)");
    }

    // ---------------------------------------------------------------------------------------------
    // 3. determinism across runs (mirrors the existing goldens)
    // ---------------------------------------------------------------------------------------------

    @Test
    void seamRoundTripIsDeterministicAcrossRuns() {
        String first = null;
        for (int run = 0; run < 5; run++) {
            final RiverNetwork net = confluenceNetwork();
            final String before = signature(net);
            net.update(net.viewAtomic());
            final String after = signature(net);
            assertEquals(before, after, "run " + run + ": round trip diverged from the pre-round-trip signature");
            if (first == null) first = after;
            else assertEquals(first, after, "run " + run + " diverged from run 0");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private static Set<Integer> idsOfType(RiverNetwork net, Endpoint.Type type) {
        final Set<Integer> ids = new HashSet<>();
        for (Endpoint n : net.getNodes()) if (n.type == type) ids.add(n.id);
        return ids;
    }

    private static void assertHasJunctionConfluence(RiverNetwork net) {
        final boolean confluence =
                net.getNodes().stream().anyMatch(n -> n.type == Endpoint.Type.JUNCTION && n.degree() >= 3);
        assertTrue(confluence, "fixture is missing a JUNCTION confluence (degree >= 3)");
    }

    /**
     * Bit-exact, order-independent signature over points + gross topology: channel/node/point counts plus
     * a commutative checksum over every channel's spline points via {@link Double#doubleToLongBits}. Long
     * accumulation is exact and associative/commutative (mod 2^64), so the checksum is invariant to the
     * order channels/points are visited — tolerating {@code update()}'s channel-id reshuffle — yet
     * sharp to a single ULP: any coordinate change fails the gate (measured round-trip drift is 0.0).
     */
    private static String signature(RiverNetwork net) {
        long checksum = 0L;
        int totalPoints = 0;
        for (Channel ch : net.getChannels()) {
            for (double[] pt : ch.spline.points()) {
                checksum += Double.doubleToLongBits(pt[0]) * 1_000_003L + Double.doubleToLongBits(pt[1]);
                totalPoints++;
            }
        }
        return String.format(
                "channels=%d nodes=%d points=%d checksum=%d",
                net.getChannelCount(), net.getNodes().size(), totalPoints, checksum);
    }
}
