package me.batata_1.fractal_terrain.hydrology.network;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.math.VectorOps;

/**
 * Cross-section normals from an arc-length stencil that crosses channel boundaries by following the
 * graph's links, instead of from a single channel's own spline.
 *
 * <p>{@link me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline#tangent} extrapolates badly at a
 * channel's first/last point and its +/-0.5 stencil is too short to beat pixel-grid quantization on a
 * headwater; walking across links gives a longer, endpoint-safe stencil. Holds no per-point cache, so
 * nothing needs invalidating when {@link Channel#reSample} runs.
 */
public final class Centreline {

    /** Guards against a corrupted graph, not an expected walk length — the K1 single-outflow invariant
     *  makes the in-tree walk terminate well before this many channel boundaries. */
    private static final int MAX_HOPS = 8;

    private final RiverNetwork network;

    public Centreline(RiverNetwork network) {
        this.network = network;
    }

    /** Unit cross-section normal at point {@code i} of {@code ch}, from a stencil that walks
     *  {@code +/-l} arc length across channel boundaries rather than sampling {@code ch}'s own spline. */
    public double[] normalAt(Channel ch, int i) {
        final double l = Math.max(HydrologyTuning.TANGENT_WIDTHS * ch.widthAt(i), HydrologyTuning.TANGENT_MIN_PX);
        return VectorOps.perpendicular(VectorOps.normalize(VectorOps.sub(walk(ch, i, +l), walk(ch, i, -l))));
    }

    /** Point reached at arc length {@code s} from {@code (ch, i)}, hopping into the linked channel at an
     *  end. Falls back to a one-sided stencil at a true source/drain, and to whatever point was reached
     *  if {@link #MAX_HOPS} is exhausted first. */
    private double[] walk(Channel ch, int i, double s) {
        final int step = s > 0 ? +1 : -1;
        double remaining = Math.abs(s);
        double[] here = ch.spline.points().get(i);
        for (int hops = 0; remaining > 0 && hops < MAX_HOPS; ) {
            final int next = i + step;
            if (next < 0 || next >= ch.numPts()) {
                final Channel link = step > 0 ? downstreamOf(ch) : mainStemUpstreamOf(ch);
                if (link == null) return here; // true source / drain: one-sided stencil, still a unit normal
                ch = link;
                i = step > 0 ? 0 : link.numPts() - 1; // shared node: coincides with `here`, so d == 0
                hops++;
                continue;
            }
            final double[] there = ch.spline.points().get(next);
            final double d = VectorOps.distance(here, there);
            if (d >= remaining) {
                final double u = remaining / d;
                return new double[] {here[0] + (there[0] - here[0]) * u, here[1] + (there[1] - here[1]) * u};
            }
            remaining -= d;
            here = there;
            i = next;
        }
        return here;
    }

    /** The channel {@code ch} feeds into, or {@code null} at a true drain / dangling end. */
    private Channel downstreamOf(Channel ch) {
        final Endpoint end = network.getNode(ch.endNodeId);
        if (end == null || end.outgoing == -1) return null;
        final Channel link = network.getChannel(end.outgoing);
        return (link == null || link.numPts() < 2) ? null : link;
    }

    /** The incoming arm with the largest terminal flow, the trunk continuation upstream of {@code ch};
     *  {@code null} at a true source. Ties break on the lowest {@code channelId} so the result does not
     *  depend on {@link Endpoint#incoming}'s hash-set iteration order. */
    private Channel mainStemUpstreamOf(Channel ch) {
        final Endpoint start = network.getNode(ch.startNodeId);
        if (start == null) return null;
        Channel best = null;
        double bestFlow = Double.NEGATIVE_INFINITY;
        for (int armId : start.incoming) {
            final Channel arm = network.getChannel(armId);
            if (arm == null || arm.numPts() < 2) continue;
            final double armFlow = arm.flow[arm.flow.length - 1];
            if (best == null || armFlow > bestFlow || (armFlow == bestFlow && arm.channelId < best.channelId)) {
                best = arm;
                bestFlow = armFlow;
            }
        }
        return best;
    }
}
