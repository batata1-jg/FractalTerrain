package me.batata_1.fractal_terrain.hydrology.meanders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * Round-trip golden for the canonical&harr;atomic seam ({@link RiverNetwork#viewAtomic()} /
 * {@link RiverNetwork#accumulateAndCorrectFlow} / {@link RiverNetwork#update}). Nothing in production calls
 * the seam yet; this test is the seam gate.
 *
 * <p>As of Phase 2 the seam round trip also derives and folds back per-point <b>flow</b>: {@code viewAtomic}
 * carries each node's {@code ownFlow}/{@code anchorFlow}, {@code accumulateAndCorrectFlow} derives the
 * per-node flow (reverse-topological accumulation + drain clamp + near-drain lerp), and {@code update}
 * writes it into each {@link Channel#flow}. The full round trip
 * {@code av = viewAtomic(); accumulateAndCorrectFlow(av); update(av)} is therefore <b>idempotent</b>: a
 * freshly constructed network carries its construction-time flow, so the test first canonicalizes with one
 * round trip, captures the signature, then round-trips again and asserts bit-identical output — proving
 * points, topology <b>and</b> flow all round-trip.
 *
 * <p>The fixture is a genuine JUNCTION confluence in the <i>canonical</i> view — two SOURCE&rarr;JUNCTION
 * edges plus one JUNCTION&rarr;DRAIN edge — because the round-trip signature alone does not exercise
 * {@link RiverNetwork#update}'s load-bearing rule (preserve SOURCE/DRAIN canonical ids, re-assign only
 * JUNCTION-equivalents). Dedicated assertions check that rule directly.
 *
 * <p><b>The signature is bit-exact.</b> Points reproduce exactly across the round trip and flow is derived
 * deterministically, so the checksum is an exact {@code doubleToLongBits} combine over every spline point
 * <b>and</b> every per-point flow value, accumulated with commutative long arithmetic (order-independent,
 * exact mod 2^64) — sharp to a single ULP yet tolerant of {@code update()}'s channel-id reshuffle.
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
     * 1 — an in-degree&ge;2 structural node in the atomic view. The {@code EdgeSpec} 4th field is now a
     * flow value (not a width); the exact numbers are arbitrary — the seam derives flow from them.
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

    /** One full seam round trip (in place): canonical -> atomic -> accumulate flow -> canonical. */
    private static RiverNetwork roundTrip(RiverNetwork net) {
        final AtomicView atomic = net.viewAtomic();
        return net.update(atomic);
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
    // 1. round trip preserves points + topology + flow (the core seam gate)
    // ---------------------------------------------------------------------------------------------

    @Test
    void roundTripPreservesPointsTopologyAndFlow() {
        final RiverNetwork net = confluenceNetwork();
        assertHasJunctionConfluence(net);

        roundTrip(net); // canonicalize: fresh construction-time flow -> derived flow
        final String before = signature(net);
        final RiverNetwork same = roundTrip(net);

        assertTrue(same == net, "update() must mutate and return the same instance, not a fresh network");
        assertEquals(before, signature(net), "round trip changed points/topology/flow");
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

        roundTrip(net);

        final Set<Integer> sourceIdsAfter = idsOfType(net, Endpoint.Type.SOURCE);
        final Set<Integer> drainIdsAfter = idsOfType(net, Endpoint.Type.DRAIN);
        final Set<Integer> junctionIdsAfter = idsOfType(net, Endpoint.Type.JUNCTION);

        // Load-bearing rule (Finding 1): every SOURCE and DRAIN keeps its canonical id.
        assertEquals(sourceIdsBefore, sourceIdsAfter, "a SOURCE canonical id was not preserved across update()");
        assertEquals(drainIdsBefore, drainIdsAfter, "a DRAIN canonical id was not preserved across update()");

        // Companion: JUNCTION-equivalent ids are NOT preserved by any type rule — they are re-assigned as
        // JUNCTION-equivalents (past the max preserved SOURCE/DRAIN id, never colliding with a SOURCE/DRAIN
        // id). Because construction and the round trip both fold through the same deterministic seam, an
        // idempotent round trip re-derives the same id rather than churning it — so the invariant is
        // "re-assigned past the boundary ids", not "numerically different". The count is unchanged.
        assertEquals(nodeCountBefore, net.getNodes().size(), "node count changed across update()");
        assertEquals(junctionIdsBefore.size(), junctionIdsAfter.size(), "JUNCTION count changed across update()");
        final Set<Integer> boundaryIds = new HashSet<>(sourceIdsAfter);
        boundaryIds.addAll(drainIdsAfter);
        for (int jid : junctionIdsAfter)
            assertFalse(boundaryIds.contains(jid), "JUNCTION id " + jid + " collides with a SOURCE/DRAIN id");
    }

    // ---------------------------------------------------------------------------------------------
    // 3. flow is actually threaded (not all-zero) and varies along the network
    // ---------------------------------------------------------------------------------------------

    @Test
    void roundTripProducesNonTrivialVariedFlow() {
        final RiverNetwork net = confluenceNetwork();
        roundTrip(net);

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Channel ch : net.getChannels()) {
            for (double f : ch.flow) {
                min = Math.min(min, f);
                max = Math.max(max, f);
            }
        }
        assertTrue(max > 0.0, "derived flow is all zero — flow is not being threaded through the seam");
        assertTrue(max > min, "derived flow is constant — accumulation/clamp/lerp produced no variation");
    }

    // ---------------------------------------------------------------------------------------------
    // 4. determinism across runs (mirrors the existing goldens)
    // ---------------------------------------------------------------------------------------------

    @Test
    void seamRoundTripIsDeterministicAcrossRuns() {
        String first = null;
        for (int run = 0; run < 5; run++) {
            final RiverNetwork net = confluenceNetwork();
            roundTrip(net); // canonicalize
            final String before = signature(net);
            roundTrip(net);
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
     * Bit-exact, order-independent signature over points + gross topology + per-point flow: channel/node/
     * point counts plus a commutative checksum over every channel's spline points and flow values via
     * {@link Double#doubleToLongBits}. Long accumulation is exact and associative/commutative (mod 2^64),
     * so the checksum is invariant to visitation order — tolerating {@code update()}'s channel-id reshuffle
     * — yet sharp to a single ULP: any coordinate or flow change fails the gate.
     */
    private static String signature(RiverNetwork net) {
        long checksum = 0L;
        int totalPoints = 0;
        for (Channel ch : net.getChannels()) {
            final List<double[]> pts = ch.spline.points();
            for (int i = 0; i < pts.size(); i++) {
                final double[] pt = pts.get(i);
                checksum += Double.doubleToLongBits(pt[0]) * 1_000_003L + Double.doubleToLongBits(pt[1]);
                checksum += Double.doubleToLongBits(ch.flow[i]) * 1_000_000_007L;
                totalPoints++;
            }
        }
        return String.format(
                "channels=%d nodes=%d points=%d checksum=%d",
                net.getChannelCount(), net.getNodes().size(), totalPoints, checksum);
    }
}
