package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives every channel point a bed elevation, so the carve has a target to cut toward.
 *
 * <p>Network-agnostic by design: {@code GlobalNetworkBuilder.build} and {@code LocalNetworkBuilder.build}
 * each run it once — the former on the global-only graph, the latter on the unified one — and it must
 * behave identically both times.
 *
 * <p>Each path is floored at the bed of the terminal DRAIN it reaches rather than at the local coarse
 * cell, so a whole source→junction→drain path descends to one consistent minimum. Broken topology
 * throws here rather than silently yielding NaN beds downstream.
 */
public final class ChannelElevationAssigner {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelElevationAssigner.class);

    private ChannelElevationAssigner() {}

    public static void assign(RiverNetwork network, Int2DoubleMap boundaryElevByNodeIdx, float[] decodedElev) {
        assignBoundaryElevations(network, boundaryElevByNodeIdx);
        final Int2DoubleMap drainElevByNodeId = resolveDrainElevations(network);
        computeJunctionElevations(network, drainElevByNodeId, decodedElev);
        interpolateBedElevations(network, decodedElev);
    }

    /** Seeds SOURCE/DRAIN elevations from the tile boundary; the phases below propagate from these. */
    private static void assignBoundaryElevations(RiverNetwork network, Int2DoubleMap boundaryElevByNodeIdx) {
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.isSourceOrDrain()) {
                endpoint.elevation = boundaryElevByNodeIdx.getOrDefault(endpoint.id, 0.0);
            }
        }
    }

    /** Propagates elevation downstream from each SOURCE to every JUNCTION once its incoming channels have
     *  all resolved, floored along the way at the DEM sample and at the reach's terminal drain elevation.
     *  Writes land on {@link Endpoint#elevation} in place; {@link #interpolateBedElevations} reads them back. */
    private static void computeJunctionElevations(
            RiverNetwork network, Int2DoubleMap drainElevByNodeId, float[] decodedElev) {
        final Int2IntOpenHashMap pendingIncoming = new Int2IntOpenHashMap();
        pendingIncoming.defaultReturnValue(-1);
        final Int2DoubleOpenHashMap junctionElev = new Int2DoubleOpenHashMap();
        junctionElev.defaultReturnValue(Double.NaN);
        final IntArrayFIFOQueue ready = new IntArrayFIFOQueue();
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.type == Endpoint.Type.JUNCTION) {
                pendingIncoming.put(endpoint.id, endpoint.incoming.size());
                junctionElev.put(endpoint.id, Double.POSITIVE_INFINITY);
            } else if (endpoint.type == Endpoint.Type.SOURCE && endpoint.hasOutgoing()) {
                ready.enqueue(endpoint.outgoing);
            }
        }

        // calculate the correct elevations for the junctions, and sources
        while (!ready.isEmpty()) {
            final Channel ch = network.getChannel(ready.dequeueInt());
            if (ch == null) throw new IllegalStateException("channel is null");
            final Endpoint startPoint = network.getNode(ch.startNodeId);
            if (startPoint == null) throw new IllegalStateException("startPoint is null");
            final double endPointElev = drainElevByNodeId.getOrDefault(ch.endNodeId, Double.NaN);
            if (Double.isNaN(endPointElev)) throw new IllegalStateException("endPointElev is NaN");
            final double startElev = Math.max(startPoint.elevation, endPointElev);
            if (Double.isNaN(startElev)) throw new IllegalStateException("startElev is NaN");
            double lastPointElev = startElev;
            for (double[] p : ch.spline.points()) {
                lastPointElev = Math.clamp(sampleBilinear(decodedElev, p[0], p[1]), endPointElev, lastPointElev);
            }
            final Endpoint endEndpoint = network.getNode(ch.endNodeId);
            if (endEndpoint == null) throw new IllegalStateException("endEndpoint is null");
            if (endEndpoint.type == Endpoint.Type.JUNCTION) {
                junctionElev.mergeDouble(endEndpoint.id, lastPointElev, Math::min);
                final int remaining = pendingIncoming.mergeInt(endEndpoint.id, -1, Integer::sum);
                if (remaining == 0) {
                    endEndpoint.elevation = junctionElev.get(endEndpoint.id);
                    if (endEndpoint.hasOutgoing()) ready.enqueue(endEndpoint.outgoing);
                }
            }
        }
    }

    /** Interpolates each channel's per-point bed between its resolved endpoints, floored at the DEM sample
     *  so the bed never rises above terrain the carve will cut into. */
    private static void interpolateBedElevations(RiverNetwork network, float[] decodedElev) {
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

    /** Resolves every node's terminal drain up front, so the elevation pass never re-walks downstream
     *  per junction. Unreachable nodes map to NaN, which the caller treats as broken topology. */
    private static Int2DoubleMap resolveDrainElevations(RiverNetwork network) {
        final Int2DoubleOpenHashMap drainElevByNodeId = new Int2DoubleOpenHashMap();
        drainElevByNodeId.defaultReturnValue(Double.NaN);
        final IntArrayList pathToDrain = new IntArrayList();
        for (Endpoint start : network.getNodes()) {
            if (drainElevByNodeId.containsKey(start.id)) continue;
            pathToDrain.clear();
            int currentId = start.id;
            double drainElev = Double.NaN;
            while (currentId != -1) {
                if (drainElevByNodeId.containsKey(currentId)) {
                    drainElev = drainElevByNodeId.get(currentId);
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
