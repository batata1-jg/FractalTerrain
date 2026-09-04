package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.config.DebugConfig.DEBUG_STEPS;
import static me.batata_1.fractal_terrain.config.HydrologyTuning.*;
import static me.batata_1.fractal_terrain.hydrology.Drainage.*;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.*;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.util.*;
import java.util.function.Predicate;
import me.batata_1.fractal_terrain.config.HydrologyConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.network.AtomicView;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.math.ds.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traces the detailed local river network off the tile's drainage field and attaches it onto the graph
 * that already carries the global channels.
 *
 * <p>Attaches in place rather than building a parallel network, so one downstream bed-assignment and
 * primitive-collection pass serves global and local channels alike.
 *
 * <p>A channel is kept only if it stays inside the tile's true boundary or would seam against its
 * neighbour. Global proximity uses a throwaway index since the RiverNetwork quadtree is cleared each step.
 */
public final class LocalDrainageTracer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDrainageTracer.class);
    private static final Predicate<GlobalRiverPrimitive> acceptanceTest = globalRiverPrimitive -> true;

    private record GlobalRiverPrimitive(double[] pos, double width, int id) implements SpatialIndexCircle {

        @Override
        public double[] getCenter() {
            return pos;
        }

        @Override
        public double getRadius() {
            return width / 2;
        }
    }

    /** Mutable per-trace scratch {@link #traceAndAttach} walks in place; groups Phase A's outputs into
     *  one parameter instead of five. */
    private record FlowGraphState(
            float[] flow, int[] downstream, int[] inDegree, IntArrayFIFOQueue sourceQueue, int[] nodeIndex) {}

    /** The global-primitive spatial index plus the optional debug river mask Phase B builds together. */
    private record GlobalIndex(ImmutableRTree<GlobalRiverPrimitive> tree, boolean @Nullable [] riverMask) {}

    public static void traceLocalNetwork(
            int[] drainage,
            float[] elev,
            float[] humidity,
            float[] gradMag,
            RiverNetwork network,
            @Nullable RiverProvider.Stages stages,
            HydrologyConfig config) {
        final AtomicView net = network.viewAtomic();
        final FlowGraphState state = computeFlowGraphState(drainage, humidity, config);
        final GlobalIndex globalIndex = indexGlobalPrimitives(net, stages);
        traceAndAttach(elev, gradMag, net, state, globalIndex.tree(), globalIndex.riverMask(), config);
        finalizeTrace(network, net, stages, state.flow(), globalIndex.riverMask());
    }

    /** Computes the flow field and DEM-drainage topology (downstream links, in-degree, source queue) the
     *  walk in {@link #traceAndAttach} consumes, plus a fresh per-cell node-index scratch array. */
    private static FlowGraphState computeFlowGraphState(int[] drainage, float[] humidity, HydrologyConfig config) {
        final float[] flow =
                computeFlow(drainage, PADDED, config.getFlowInitialLocal(), flowFromHumidity(humidity, false));

        final Drainage.FlowGraph graph = Drainage.FlowGraph.of(drainage, PADDED);
        final int[] downstream = graph.downstream();
        final int[] inDegree = graph.inDegree();
        final IntArrayFIFOQueue sourceQueue = graph.sources();

        final int[] nodeIndex = new int[PADDED * PADDED];
        Arrays.fill(nodeIndex, -1);

        return new FlowGraphState(flow, downstream, inDegree, sourceQueue, nodeIndex);
    }

    /** Builds the read-only spatial index of already-traced global channels the walk attaches local
     *  segments to, plus the optional per-cell debug mask a live {@code stages} wants populated. */
    private static GlobalIndex indexGlobalPrimitives(AtomicView net, @Nullable RiverProvider.Stages stages) {
        final ImmutableRTree<GlobalRiverPrimitive> globalRiversPosition =
                new ImmutableRTree<>(getGlobalPrimitives(net), new GlobalRiverPrimitive(new double[] {0, 0}, 0, -1));

        boolean[] riverMask = null;
        if (stages != null) {
            riverMask = new boolean[PADDED * PADDED];
            Arrays.fill(riverMask, false);
        }
        return new GlobalIndex(globalRiversPosition, riverMask);
    }

    private static List<GlobalRiverPrimitive> getGlobalPrimitives(AtomicView net) {
        final double[] flow = net.accumulateAndCorrectFlow();
        final GlobalRiverPrimitive[] globalRivers = new GlobalRiverPrimitive[net.size()];
        for (int id = 0; id < net.size(); id++) {
            globalRivers[id] =
                    new GlobalRiverPrimitive(net.pos(id), Math.min(3, influence(widthFromFlow(flow[id]))), id);
        }
        return Arrays.stream(globalRivers).toList();
    }

    /** Walks the DEM drainage graph from every source cell, minting local SOURCE/JUNCTION/DRAIN nodes where
     *  flow crosses the channel threshold and wiring each new node to any global primitive already covering
     *  its position. */
    private static void traceAndAttach(
            float[] elev,
            float[] gradMag,
            AtomicView net,
            FlowGraphState state,
            ImmutableRTree<GlobalRiverPrimitive> globalRiversPosition,
            @Nullable boolean[] riverMask,
            HydrologyConfig config) {
        final float[] flow = state.flow();
        final int[] downstream = state.downstream();
        final int[] inDegree = state.inDegree();
        final IntArrayFIFOQueue sourceQueue = state.sourceQueue();
        final int[] nodeIndex = state.nodeIndex();

        final QuadTree<CoordPoint> sources =
                new QuadTree<>(new double[] {0, 0}, new double[] {PADDED + 1, PADDED + 1}, 7);
        while (!sourceQueue.isEmpty()) {
            final int current = sourceQueue.dequeueInt();
            if (elev[current] < 0) continue;
            final int next = downstream[current];
            if (next == -1) continue;
            boolean isDrain = elev[next] < 0;
            if ((flow[next] >= config.getFlowThreshold() && gradMag[next] >= config.getGradThreshold())
                    || nodeIndex[current] != -1) {
                if (riverMask != null) riverMask[current] = true;
                // create the source if there isnt a source nearby

                final double[] curNodePos =
                        new double[] {Math.floorDiv(current, PADDED) + 0.5, (current % PADDED) + 0.5};
                if (nodeIndex[current] == -1 && nodeIndex[next] == -1 && !sources.containsPointInCircle(curNodePos, 3.0)
                //   && !globalRiversPosition.anyContaining(curNodePos, acceptanceTest)
                ) {
                    nodeIndex[current] = net.addNode(curNodePos, Endpoint.Type.SOURCE, -1, flow[current], -1);
                    sources.insertPoint(new CoordPoint(curNodePos));
                }

                if (nodeIndex[next] == -1) {
                    final double[] nextNodePos =
                            new double[] {Math.floorDiv(next, PADDED) + 0.5, (next % PADDED) + 0.5};
                    nodeIndex[next] = net.addNode(
                            nextNodePos,
                            isDrain ? Endpoint.Type.DRAIN : null,
                            -1,
                            HydrologyTuning.FLOW_PER_CELL_LOCAL,
                            -1);
                    final List<GlobalRiverPrimitive> nearbyGlobal = globalRiversPosition.queryContaining(nextNodePos);
                    for (GlobalRiverPrimitive primitive : nearbyGlobal) {
                        net.addDirectedEdge(primitive.id(), nodeIndex[next]);
                        net.addDirectedEdge(nodeIndex[next], primitive.id());
                    }
                    if ((!nearbyGlobal.isEmpty()) || isDrain) inDegree[next] = -1;
                }
                if (nodeIndex[current] != -1) net.addDirectedEdge(nodeIndex[current], nodeIndex[next]);
            }
            if ((--inDegree[next]) == 0) sourceQueue.enqueue(next);
        }

        sources.clear();
    }

    /** Debug-dumps the traced view, folds any capture from the walk back via
     *  {@link RiverNetwork#detectAndResolveCaptures}, and hands flow/debug-mask outputs to {@code stages}. */
    private static void finalizeTrace(
            RiverNetwork network,
            AtomicView net,
            @Nullable RiverProvider.Stages stages,
            float[] flow,
            @Nullable boolean[] riverMask) {
        if (DEBUG_STEPS) {
            Debug.river.seeNetwork(net, 514, "afterTracingNet", "0");
        }

        network.detectAndResolveCaptures(0, net);

        if (stages != null) {
            stages.flow = flow;
            stages.riverMask = riverMask;
        }
    }
}
