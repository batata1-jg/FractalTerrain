package me.batata_1.fractal_terrain.hydrology.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.junit.jupiter.api.Test;

/**
 * Gates {@link Centreline#normalAt}, the arc-length, graph-crossing replacement for a channel's own
 * (endpoint-extrapolating, too-short) spline tangent.
 *
 * <p>The headline fixture wedges a forced 2-point channel between two straight, offset-but-parallel
 * channels sharing its end nodes: the true through-direction is derived independently here from exact
 * arc-length reasoning along those straight arms (colinear points keep any Catmull-Rom fit exactly on
 * the line, so the derivation has no discretization error), not by re-running {@link Centreline} itself.
 */
class CentrelineTest {

    private static final int GRID = 512;
    private static final double RESAMPLE_DIST = 2.0;

    // ---------------------------------------------------------------------------------------------
    // 1. Headline fix: a short (forced 2-point) channel wedged between two longer, roughly collinear
    //    channels gets a normal from the true through-direction, not its own chord.
    // ---------------------------------------------------------------------------------------------

    private static final double A_Z = 190.0;
    private static final double B_Z = 192.5;
    private static final double[] J1 = {200.0, A_Z};
    private static final double[] J2 = {202.5, B_Z};

    @Test
    void wedgedShortChannelNormalMatchesTrueThroughDirectionNotItsOwnChord() {
        final RiverNetwork net = wedgedNetwork();
        final Endpoint j1 = findJunction(net, J1);
        final Endpoint j2 = findJunction(net, J2);
        final Channel mid = findChannel(net, j1.id, j2.id);

        // Force the wedged channel down to exactly 2 points (J1, J2) — a plain field assignment, the
        // same pattern Channel.reSample itself uses.
        mid.spline = QuinticHermiteSpline.createCatmullRom(new ArrayList<>(List.of(J1.clone(), J2.clone())));
        mid.flow = new double[] {0.01, 0.01};

        final Centreline centreline = new Centreline(net);
        final double[] actual = centreline.normalAt(mid, 0);

        // Independent expected value: L is the stencil half-length at this (floor-clamped) width; the
        // backward walk consumes it entirely along A's straight z=A_Z line, and the forward walk crosses
        // the whole wedge then continues along B's straight z=B_Z line for the remainder — both arc
        // lengths equal exact coordinate deltas because each arm is perfectly straight.
        final double l = Math.max(HydrologyTuning.TANGENT_WIDTHS * 0.6, HydrologyTuning.TANGENT_MIN_PX);
        final double jogLen = VectorOps.distance(J1, J2);
        final double remaining = l - jogLen;
        final double[] minus = {J1[0] - l, A_Z};
        final double[] plus = {J2[0] + remaining, B_Z};
        final double[] expected = VectorOps.perpendicular(VectorOps.normalize(VectorOps.sub(plus, minus)));

        assertEquals(expected[0], actual[0], 1e-6, "normal x-component off the true through-direction");
        assertEquals(expected[1], actual[1], 1e-6, "normal z-component off the true through-direction");

        // Would fail if the swap were reverted: the wedge's own 2-point chord (45 degrees, jog is
        // symmetric) is a materially different direction from the through-direction above.
        final double[] ownChordNormal = mid.spline.normal(0);
        final double gap = VectorOps.distance(actual, ownChordNormal);
        assertTrue(
                gap > 0.2,
                "expected the centreline normal to differ materially from the channel's own " + "chord normal, gap was "
                        + gap);
    }

    /** {@code SOURCE_A -> J1 -> [forced 2-pt wedge] -> J2 -> DRAIN}, with a low-flow tributary into each
     *  junction so {@code RiverNetwork.update} keeps J1/J2 as real structural (confluence) nodes rather
     *  than folding the whole path into one channel. */
    private static RiverNetwork wedgedNetwork() {
        final List<NodeSpec> nodes = List.of(
                new NodeSpec(100.0, A_Z, Endpoint.Type.SOURCE), // 0: A's source
                new NodeSpec(200.0, 140.0, Endpoint.Type.SOURCE), // 1: T1's source
                new NodeSpec(J1[0], J1[1], Endpoint.Type.JUNCTION), // 2: J1
                new NodeSpec(202.5, 145.0, Endpoint.Type.SOURCE), // 3: T2's source
                new NodeSpec(J2[0], J2[1], Endpoint.Type.JUNCTION), // 4: J2
                new NodeSpec(400.0, B_Z, Endpoint.Type.DRAIN)); // 5: drain
        final List<EdgeSpec> edges = List.of(
                new EdgeSpec(0, 2, segment(new double[] {100.0, A_Z}, J1, 40), 50.0), // A: high flow
                new EdgeSpec(1, 2, segment(new double[] {200.0, 140.0}, J1, 20), 1.0), // T1: low flow
                new EdgeSpec(2, 4, segment(J1, J2, 4), 20.0), // wedge, overwritten below
                new EdgeSpec(3, 4, segment(new double[] {202.5, 145.0}, J2, 20), 1.0), // T2: low flow
                new EdgeSpec(4, 5, segment(J2, new double[] {400.0, B_Z}, 60), 20.0)); // B
        return new RiverNetwork(GRID, nodes, edges, false, 0, RESAMPLE_DIST);
    }

    // ---------------------------------------------------------------------------------------------
    // 2. True source: a point with no upstream link still yields a unit-length normal from the
    //    one-sided stencil (magnitude only — direction is not meaningful with nothing upstream).
    // ---------------------------------------------------------------------------------------------

    @Test
    void trueSourcePointYieldsUnitNormalFromOneSidedStencil() {
        final List<NodeSpec> nodes = List.of(
                new NodeSpec(100.0, 256.0, Endpoint.Type.SOURCE), new NodeSpec(400.0, 256.0, Endpoint.Type.DRAIN));
        final List<EdgeSpec> edges = List.of(
                new EdgeSpec(0, 1, segment(new double[] {100.0, 256.0}, new double[] {400.0, 256.0}, 150), 4.0));
        final RiverNetwork net = new RiverNetwork(GRID, nodes, edges, false, 0, RESAMPLE_DIST);
        final Channel ch = net.getChannels().getFirst();

        final Centreline centreline = new Centreline(net);
        final double[] normal = centreline.normalAt(ch, 0);

        assertEquals(1.0, VectorOps.magnitude(normal), 1e-9, "one-sided stencil must still yield a unit normal");
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Determinism at a junction: two incoming arms with equal terminal flow must resolve to the
    //    same mainstem arm (the channelId tie-break) across repeated construction.
    // ---------------------------------------------------------------------------------------------

    @Test
    void equalFlowJunctionTieBreakIsDeterministicAcrossRepeatedConstruction() {
        double[] first = null;
        for (int run = 0; run < 5; run++) {
            final RiverNetwork net = tiedJunctionNetwork();
            final Endpoint junction = findJunction(net, new double[] {300.0, 300.0});
            final Channel out = net.getChannel(junction.outgoing);
            final Centreline centreline = new Centreline(net);
            final double[] normal = centreline.normalAt(out, 0);
            if (first == null) {
                first = normal;
            } else {
                assertEquals(first[0], normal[0], 1e-12, "run " + run + ": tie-break x diverged");
                assertEquals(first[1], normal[1], 1e-12, "run " + run + ": tie-break z diverged");
            }
        }
    }

    /** Two source arms of equal flow meeting at right angles, so a non-deterministic tie-break would
     *  visibly flip the resulting normal between runs. */
    private static RiverNetwork tiedJunctionNetwork() {
        final double[] junction = {300.0, 300.0};
        final List<NodeSpec> nodes = List.of(
                new NodeSpec(200.0, 300.0, Endpoint.Type.SOURCE), // 0: A1's source (approaches from the west)
                new NodeSpec(300.0, 200.0, Endpoint.Type.SOURCE), // 1: A2's source (approaches from the south)
                new NodeSpec(junction[0], junction[1], Endpoint.Type.JUNCTION), // 2
                new NodeSpec(500.0, 300.0, Endpoint.Type.DRAIN)); // 3
        final List<EdgeSpec> edges = List.of(
                new EdgeSpec(0, 2, segment(new double[] {200.0, 300.0}, junction, 50), 5.0),
                new EdgeSpec(1, 2, segment(new double[] {300.0, 200.0}, junction, 50), 5.0),
                new EdgeSpec(2, 3, segment(junction, new double[] {500.0, 300.0}, 100), 10.0));
        return new RiverNetwork(GRID, nodes, edges, false, 0, RESAMPLE_DIST);
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    /** A straight polyline of {@code n+1} points from {@code a} to {@code b} (endpoints exact). */
    private static ArrayList<double[]> segment(double[] a, double[] b, int n) {
        final ArrayList<double[]> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            final double t = (double) i / n;
            pts.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
        }
        return pts;
    }

    private static Endpoint findJunction(RiverNetwork net, double[] coord) {
        for (Endpoint en : net.getNodes()) {
            if (en.type == Endpoint.Type.JUNCTION && en.coord[0] == coord[0] && en.coord[1] == coord[1]) {
                return en;
            }
        }
        throw new IllegalStateException("no JUNCTION at " + java.util.Arrays.toString(coord));
    }

    private static Channel findChannel(RiverNetwork net, int startNodeId, int endNodeId) {
        for (Channel ch : net.getChannels()) {
            if (ch.startNodeId == startNodeId && ch.endNodeId == endNodeId) return ch;
        }
        throw new IllegalStateException("no channel " + startNodeId + " -> " + endNodeId);
    }
}
