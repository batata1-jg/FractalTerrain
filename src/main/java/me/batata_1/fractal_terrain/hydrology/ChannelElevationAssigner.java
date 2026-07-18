package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bed-elevation assignment for a relaxed {@link RiverNetwork}, in three phases:
 *
 * <ol>
 *   <li>Seed every {@code SOURCE}/{@code DRAIN} node's elevation from the boundary map (missing entries
 *       default to {@code 0.0}).</li>
 *   <li>Propagate junction elevations leaves→drains in Kahn order: each channel walks its spline points
 *       taking a running terrain sample clamped into {@code [drainElev, previousPoint]}, and a junction
 *       adopts the minimum arriving elevation once all its incoming channels have reported.</li>
 *   <li>Recompute every channel's per-point bed: a terrain sample floored at the channel's end elevation,
 *       blended toward that end elevation by the along-channel fraction, then forced monotone
 *       non-increasing against the previous point.</li>
 * </ol>
 *
 * <p>Operates on whatever network and boundary map it is given and assumes nothing about the network
 * being global-only.  invokes it <b>twice</b> per tile — once on the
 * global-only graph and again after the local network is attached.
 *
 * <p>Collaborators: {@link HydrologyTileGeometry#sampleBilinear} (padded-frame terrain sampling);
 * consumes and mutates the {@link RiverNetwork} handed to it (writes {@link Endpoint#elevation} and
 * {@link Channel#bedElevations}).
 *
 * <p>Invariants: purely functional over its parameters — no shared mutable state across tiles. Phase 2
 * floors each path at the bed of the terminal {@code DRAIN} it ultimately flows into (not the local
 * coarse cell), so a whole source→junction→drain path descends to a single path-consistent minimum; the
 * drain bed of every node is resolved once up front by {@link #resolveDrainElevations} rather than
 * re-scanned per junction.
 *
 * <p>Phase 2 throws {@link IllegalArgumentException} on a missing start node, a {@code NaN} start
 * elevation, or a node whose drain elevation is unresolvable — i.e. broken topology fails loudly here
 * rather than silently producing {@code NaN} beds.
 */
final class ChannelElevationAssigner {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelElevationAssigner.class);

    private ChannelElevationAssigner() {}

    static void assign(RiverNetwork network, Map<Integer, Double> boundaryElevByNodeIdx, float[] decodedElev) {
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.type == Endpoint.Type.SOURCE || endpoint.type == Endpoint.Type.DRAIN) {
                endpoint.elevation = boundaryElevByNodeIdx.getOrDefault(endpoint.id, 0.0);
            }
        }

        final Map<Integer, Double> drainElevByNodeId = resolveDrainElevations(network);

        final Map<Integer, Integer> pendingIncoming = new HashMap<>();
        final Map<Integer, Double> junctionElev = new HashMap<>();
        final ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.type == Endpoint.Type.JUNCTION) {
                pendingIncoming.put(endpoint.id, endpoint.incoming.size());
                junctionElev.put(endpoint.id, Double.POSITIVE_INFINITY);
            } else if (endpoint.type == Endpoint.Type.SOURCE && endpoint.outgoing != -1) {
                ready.add(endpoint.outgoing);
            }
        }

        // calculate the correct elevations for the junctions, and sources
        while (!ready.isEmpty()) {
            final Channel ch = network.getChannel(ready.poll());
            if (ch == null) continue;
            final Endpoint startPoint = network.getNode(ch.startNodeId);
            if (startPoint == null) throw new IllegalArgumentException("startPoint is null");
            final double startElev = startPoint.elevation;
            if (Double.isNaN(startElev)) throw new IllegalArgumentException("startElev is NaN");
            double lastPointElev = startElev;
            final double endPointElev = drainElevByNodeId.getOrDefault(ch.endNodeId, Double.NaN);
            if (Double.isNaN(endPointElev)) throw new IllegalArgumentException("endPointElev is NaN");
            for (double[] p : ch.spline.points()) {
                //                LOG.info("[{},{}]", endPointElev,lastPointElev);
                lastPointElev = Math.clamp(sampleBilinear(decodedElev, p[0], p[1]), endPointElev, lastPointElev);
            }
            final Endpoint endEndpoint = network.getNode(ch.endNodeId);
            if (endEndpoint == null) continue;
            if (endEndpoint.type == Endpoint.Type.JUNCTION) {
                junctionElev.merge(endEndpoint.id, lastPointElev, Math::min);
                final int remaining = pendingIncoming.merge(endEndpoint.id, -1, Integer::sum);
                if (remaining == 0) {
                    endEndpoint.elevation = junctionElev.get(endEndpoint.id);
                    if (endEndpoint.outgoing != -1) ready.add(endEndpoint.outgoing);
                }
            }
        }

        for (Channel ch : network.getChannels()) {
            final Endpoint startEndpoint = network.getNode(ch.startNodeId);
            final Endpoint endEndpoint = network.getNode(ch.endNodeId);
            final double startElev =
                    (startEndpoint != null && !Double.isNaN(startEndpoint.elevation)) ? startEndpoint.elevation : 0.0;
            final double endElev =
                    (endEndpoint != null && !Double.isNaN(endEndpoint.elevation)) ? endEndpoint.elevation : startElev;
            final List<double[]> pts = ch.spline.points();
            final int n = pts.size();
            final double[] bed = new double[n];
            bed[0] = startElev;
            for (int i = 1; i < n; i++) {
                final double frac = (double) i / (n - 1);
                final double candidate = Math.max(sampleBilinear(decodedElev, pts.get(i)[0], pts.get(i)[1]), endElev);
                bed[i] = Math.min(bed[i - 1], candidate + (endElev - candidate) * frac);
            }
            ch.bedElevations = bed;
        }
    }

    /**
     * For every node, the bed elevation of the unique downstream {@code DRAIN} it ultimately flows into.
     * The network is a single-outflow dendritic in-tree, so following {@link Endpoint#outgoing} edges from
     * any node reaches exactly one drain. Each downstream walk memoizes every node it touches (including
     * the drain itself), so the whole network is resolved in O(nodes) total instead of re-walking per
     * junction. Unreachable nodes (broken topology) map to {@code NaN}.
     */
    private static Map<Integer, Double> resolveDrainElevations(RiverNetwork network) {
        final Map<Integer, Double> drainElevByNodeId = new HashMap<>();
        final ArrayDeque<Integer> pathToDrain = new ArrayDeque<>();
        for (Endpoint start : network.getNodes()) {
            if (drainElevByNodeId.containsKey(start.id)) continue;
            pathToDrain.clear();
            int currentId = start.id;
            double drainElev = Double.NaN;
            while (currentId != -1) {
                final Double memoized = drainElevByNodeId.get(currentId);
                if (memoized != null) {
                    drainElev = memoized;
                    break;
                }
                final Endpoint current = network.getNode(currentId);
                if (current == null) break;
                if (current.type == Endpoint.Type.DRAIN) {
                    drainElev = current.elevation;
                    drainElevByNodeId.put(currentId, drainElev);
                    break;
                }
                pathToDrain.push(currentId);
                final Channel outgoing = network.getChannel(current.outgoing);
                currentId = (outgoing == null) ? -1 : outgoing.endNodeId;
            }
            for (int nodeId : pathToDrain) drainElevByNodeId.put(nodeId, drainElev);
        }
        return drainElevByNodeId;
    }
}
