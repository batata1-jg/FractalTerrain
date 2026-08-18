package me.batata_1.fractal_terrain.hydrology.rosgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.hydrology.network.Centreline;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.math.VectorOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Rosgen key over the whole graph, segmenting channels into reaches and measuring each.
 *
 * <p>Walks downstream-first so the dead band carries across junctions. {@code RiverNetwork.update} splits
 * a trunk at every confluence, so a per-channel walk would reset the band at each one and scallop the
 * floodplain edge the band exists to prevent.
 *
 * <p>Classifies per reach, not per point: transects are cache-hostile and a detailed tile emits tens of
 * thousands of points, while Rosgen's own reach definition is ~20 channel widths.
 */
public final class ReachRosgenClassifier implements ChannelTyper {

    private static final Logger LOG = LoggerFactory.getLogger(ReachRosgenClassifier.class);
    private final ReachMetricsSampler sampler;
    private final Map<Integer, RosgenType[]> typesByChannelId = new HashMap<>();
    private Centreline centreline;

    /** @param elev <b>raw</b> decoded elevation — never a carved buffer */
    public ReachRosgenClassifier(float[] elev, int gridSize) {
        this.sampler = new ReachMetricsSampler(elev, gridSize);
    }

    @Override
    public void prepare(RiverNetwork network) {
        this.centreline = new Centreline(network);
        typesByChannelId.clear();
        for (Channel ch : orderDownstreamFirst(network)) {
            typesByChannelId.put(ch.channelId, classifyChannel(ch, seedFor(ch, network)));
        }
    }

    @Override
    public RosgenType[] typesFor(Channel channel) {
        final RosgenType[] cached = typesByChannelId.get(channel.channelId);
        if (cached != null && cached.length == channel.numPts()) return cached;
        // A channel resampled after prepare(), or one absent from the prepared network: classify it
        // standalone rather than returning a mis-sized array.
        return classifyChannel(channel, null);
    }

    /** Orders channels so each comes after the one it flows into, which {@link #seedFor} requires.
     *  Dangling components with no reachable drain are rooted at their lowest id rather than their true
     *  outlet, so the dead band resets at those boundaries — see {@code README.md}. */
    private static List<Channel> orderDownstreamFirst(RiverNetwork network) {
        final List<Channel> order = new ArrayList<>();
        final ArrayDeque<Channel> frontier = new ArrayDeque<>();
        final Map<Integer, Boolean> seen = new HashMap<>();

        for (Endpoint node : network.getNodes()) {
            if (node.type != Endpoint.Type.DRAIN) continue;
            for (int incomingId : node.incoming) {
                final Channel ch = network.getChannel(incomingId);
                if (ch != null && seen.putIfAbsent(incomingId, Boolean.TRUE) == null) frontier.add(ch);
            }
        }
        drainFrontier(network, frontier, seen, order);

        // Any channel not reachable from a drain (a dangling branch) still needs a type. Sorted by id:
        // getChannels() is a view over a HashMap, and unlike the drain-rooted expansion above — where a
        // channel is always polled after the channel it flows into, whatever order its siblings arrive in
        // — a dangling branch can be classified before its own downstream neighbour, so this order
        // reaches the output through seedFor. RiverNetwork.viewAtomic and detectCrossings sort for the
        // same reason.
        final List<Channel> remaining = new ArrayList<>(network.getChannels());
        remaining.sort(Comparator.comparingInt(ch -> ch.channelId));
        for (Channel ch : remaining) {
            if (seen.putIfAbsent(ch.channelId, Boolean.TRUE) != null) continue;
            frontier.add(ch);
            drainFrontier(network, frontier, seen, order);
        }
        return order;
    }

    /** Drains one BFS frontier to exhaustion. Run per root rather than once overall, so an injected
     *  dangling root cannot interleave with the drain-rooted walk. */
    private static void drainFrontier(
            RiverNetwork network, ArrayDeque<Channel> frontier, Map<Integer, Boolean> seen, List<Channel> order) {
        while (!frontier.isEmpty()) {
            final Channel ch = frontier.poll();
            order.add(ch);
            final Endpoint start = network.getNode(ch.startNodeId);
            if (start == null) continue;
            for (int incomingId : start.incoming) {
                final Channel upstream = network.getChannel(incomingId);
                if (upstream != null && seen.putIfAbsent(incomingId, Boolean.TRUE) == null) frontier.add(upstream);
            }
        }
    }

    /** The downstream neighbour's committed type, or null at a drain; the dead band's carry-in. */
    private RosgenType seedFor(Channel ch, RiverNetwork network) {
        final Endpoint end = network.getNode(ch.endNodeId);
        if (end == null || end.outgoing == -1) return null;
        final RosgenType[] downstream = typesByChannelId.get(end.outgoing);
        return (downstream == null || downstream.length == 0) ? null : downstream[0];
    }

    /** One type per spline point, walked downstream-to-upstream so the dead band flows the same
     *  direction as the graph walk. */
    private RosgenType[] classifyChannel(Channel ch, RosgenType seed) {
        final int n = ch.numPts();
        final RosgenType[] types = new RosgenType[n];
        if (n == 0) return types;

        final List<int[]> reaches = segment(ch);
        RosgenType previous = seed;
        for (int r = reaches.size() - 1; r >= 0; r--) {
            final int[] reach = reaches.get(r);
            final ReachMetrics metrics = measure(ch, reach[0], reach[1]);
            final RosgenType committed = RosgenKey.applyDeadBand(metrics, RosgenKey.classify(metrics), previous);
            for (int i = reach[0]; i <= reach[1]; i++) types[i] = committed;
            previous = committed;
        }
        // Any point no reach covered stays null. segment() is meant to cover every index, so a null here
        // is a segmentation gap; leaving it null keeps it visible (white in the type PNG, and never
        // counted as an A during calibration) rather than fabricating a type the terrain never produced.
        return types;
    }

    /** Inclusive {@code [from, to]} index pairs, cut at the reach length in arc length. */
    private static List<int[]> segment(Channel ch) {
        final List<int[]> reaches = new ArrayList<>();
        final List<double[]> pts = ch.spline.points();
        final int n = pts.size();
        if (n < 2) {
            reaches.add(new int[] {0, 0});
            return reaches;
        }
        int from = 0;
        double accumulated = 0.0;
        for (int i = 1; i < n; i++) {
            accumulated += VectorOps.distance(pts.get(i - 1), pts.get(i));
            if (accumulated >= reachLength(ch.widthAt(i))) {
                reaches.add(new int[] {from, i});
                from = i;
                accumulated = 0.0;
            }
        }
        if (from < n - 1) reaches.add(new int[] {from, n - 1});
        else if (reaches.isEmpty()) reaches.add(new int[] {0, n - 1});
        return reaches;
    }

    /** Reach length (native px): Rosgen's 20 channel widths, capped so it cannot span a whole tile. */
    private static double reachLength(double width) {
        return Math.min(HydrologyTuning.REACH_WIDTHS * width, HydrologyTuning.REACH_MAX_PX);
    }

    /** Measure one reach at its midpoint. One transect per reach — see the class javadoc. */
    private ReachMetrics measure(Channel ch, int from, int to) {
        final List<double[]> pts = ch.spline.points();
        if (from > to || from < 0 || to >= pts.size())
            throw new IllegalArgumentException("channel reach is bigger than itself");
        final double mid = (from + to) / 2.0;
        final double width = ch.widthAt((int) mid);

        double arcLength = 0.0;
        for (int i = from + 1; i <= to; i++) arcLength += VectorOps.distance(pts.get(i - 1), pts.get(i));

        final double bedElev = sampler.elevAt(ch.spline.sample(mid));
        final double slope = sampler.slope(pts, arcLength, from, to);

        // centreline.normalAt never returns null: VectorOps.normalize returns a zero vector, not null, when
        // the tangent degenerates (duplicate consecutive spline points), and perpendicular preserves that. A
        // zero normal makes the transect resample the same pixel at every step, so it must be caught
        // here rather than walked.
        // TODO: 1.0 is a placeholder. ER = 1 means fully entrenched, which sends a degenerate reach to
        // F/G — visible types, deliberately, so the case shows up in the type PNG instead of hiding in
        // the C/E majority. Once the type mix is calibrated (P1), decide whether a degenerate reach
        // should instead inherit its downstream neighbour's type or be dropped from classification.
        double[] normal = centreline.normalAt(ch, (int) mid);
        if (isDegenerate(normal)) {
            LOG.warn("degenerate");
        }
        final double entrenchment = isDegenerate(normal)
                ? DEGENERATE_ENTRENCHMENT
                : sampler.entrenchmentRatio(pts.get((int) mid), normal, bedElev, width);

        return new ReachMetrics(slope, entrenchment, ChannelGeometry.widthDepthRatio(width), width, bedElev);
    }

    /** Entrenchment reported for a reach whose centreline tangent degenerates. See the TODO in {@link #measure}. */
    private static final double DEGENERATE_ENTRENCHMENT = 1.0;

    /** Squared length below which a normal counts as degenerate rather than a unit vector. */
    private static final double DEGENERATE_NORMAL_EPS_SQ = 1e-12;

    /** Whether the centreline tangent degenerated, leaving a zero-length normal instead of a unit one. */
    private static boolean isDegenerate(double[] normal) {
        return normal[0] * normal[0] + normal[1] * normal[1] < DEGENERATE_NORMAL_EPS_SQ;
    }
}
