package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;

/**
 * Responsibility: the bottom-up bed-elevation assignment for a just-relaxed global-river
 * {@link RiverNetwork} — set source/drain node elevations from boundary conditions, propagate junction
 * elevations leaves→drains (Kahn), then recompute each channel's per-point bed as a lerp from its start
 * elevation toward its end elevation. Invoked once per tile at the end of
 * {@link GlobalNetworkBuilder#build}.
 *
 * <p>Collaborators: {@link HydrologyTileGeometry#sampleBilinear} (padded-frame terrain sampling);
 * consumes and mutates the {@link RiverNetwork} built by {@link GlobalNetworkBuilder} (writes
 * {@link Endpoint#elevation} and {@link Channel#bedElevations}).
 *
 * <p>Invariants: purely functional over its parameters — no shared mutable state across tiles. Each
 * channel's per-point elevation is floored at the bed of the terminal {@code DRAIN} it ultimately flows
 * into (not the local coarse cell), so a whole source→junction→drain path is monotone non-increasing
 * down to a single, path-consistent minimum; the drain bed of every node is resolved once up front by
 * {@link #resolveDrainElevations} rather than re-scanned per junction.
 */
final class ChannelElevationAssigner {

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
            final Endpoint startEndpoint = network.getNode(ch.startNodeId);
            final double startElev = (startEndpoint != null) ? startEndpoint.elevation : 0.0;
            double lastPointElev = Double.isNaN(startElev) ? Double.POSITIVE_INFINITY : startElev;
            final double terminalDrainElev = drainElevByNodeId.getOrDefault(ch.endNodeId, Double.NaN);
            final double drainFloor = Double.isNaN(terminalDrainElev) ? Double.NEGATIVE_INFINITY : terminalDrainElev;
            for (double[] p : ch.spline.points()) {
                lastPointElev = Math.clamp(sampleBilinear(decodedElev, p[0], p[1]), drainFloor, lastPointElev);
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
