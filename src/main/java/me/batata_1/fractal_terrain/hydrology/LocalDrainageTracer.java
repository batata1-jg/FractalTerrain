package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.config.DebugConfig.DEBUG_STEPS;
import static me.batata_1.fractal_terrain.config.HydrologyTuning.*;
import static me.batata_1.fractal_terrain.hydrology.Drainage.*;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.*;

import java.util.*;
import java.util.function.Predicate;
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

    public static void traceLocalNetwork(
            int[] drainage,
            float[] elev,
            float[] gradMag,
            RiverNetwork network,
            @Nullable RiverProvider.Stages stages) {
        final int cellCount = PADDED * PADDED;
        final AtomicView net = network.viewAtomic();
        final float[] flow =
                computeFlow(drainage, PADDED, HydrologyTuning.FLOW_INITIAL_LOCAL, HydrologyTuning.FLOW_PER_CELL_LOCAL);

        final Drainage.FlowGraph graph = Drainage.FlowGraph.of(drainage, PADDED);
        final int[] downstream = graph.downstream();
        final int[] inDegree = graph.inDegree();
        final Deque<Integer> sourceQueue = graph.sources();

        final int[] nodeIndex = new int[cellCount];
        Arrays.fill(nodeIndex, -1);

        final ImmutableRTree<GlobalRiverPrimitive> globalRiversPosition =
                new ImmutableRTree<>(getGlobalPrimitives(net), new GlobalRiverPrimitive(new double[] {0, 0}, 0, -1));

        boolean[] riverMask = null;
        if (stages != null) {
            riverMask = new boolean[cellCount];
            Arrays.fill(riverMask, false);
        }

        final QuadTree<CoordPoint> sources =
                new QuadTree<>(new double[] {0, 0}, new double[] {PADDED + 1, PADDED + 1}, 7);
        while (!sourceQueue.isEmpty()) {
            final int current = sourceQueue.poll();
            if (elev[current] < 0) continue;
            final int next = downstream[current];
            if (next == -1) continue;
            boolean isDrain = elev[next] < 0;
            if ((flow[next] >= FLOW_THRESHOLD && gradMag[next] >= GRAD_THRESHOLD) || nodeIndex[current] != -1) {
                if (stages != null) riverMask[current] = true;
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
            if ((--inDegree[next]) == 0) sourceQueue.add(next);
        }

        sources.clear();

        if (DEBUG_STEPS) {
            Debug.river.seeNetwork(net, 514, "afterTracingNet", "0");
        }

        network.manageCollisions(0, net);

        if (stages != null) {
            stages.flow = flow;
            stages.riverMask = riverMask;
        }
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
}
