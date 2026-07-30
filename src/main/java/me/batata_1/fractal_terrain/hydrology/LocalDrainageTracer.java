package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.config.HydrologyTuning.*;
import static me.batata_1.fractal_terrain.hydrology.Drainage.*;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.*;
import static me.batata_1.fractal_terrain.hydrology.meanders.Meanders.DEBUG_STEPS;

import java.util.*;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.meanders.AtomicView;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import me.batata_1.fractal_terrain.math.ds.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsibility: the detailed <em>local</em> river network, traced directly from the per-tile drainage
 * field (flow accumulation → reach test → segment walk → channel build) and attached in place onto the
 * live {@link RiverNetwork} that already carries the global (Meanders-relaxed) channels, so a single
 * downstream bed-assignment/unit-collection pass serves both. This is the deterministic core exercised
 * headlessly by {@code LocalRiverGoldenTest} via {@link LocalRiverProvider#traceLocalNetworkForTest}.
 *
 * <p>Collaborators: {@link HydrologyTileGeometry} (tile geometry); {@link Drainage} (flow
 * accumulation, neighbour resolution); {@link Channel} / {@link RiverNetwork} (traced-segment geometry
 * and the graph it attaches to); consumed by {@code LocalRiverProvider.buildTile}.
 *
 * <p>Invariants: purely functional over its parameters plus the one {@link RiverNetwork} it mutates in
 * place — no state shared across tiles, so per-tile traces from different worker threads never
 * interact. A channel is only attached when its whole segment stays interior to the tile's TRUE boundary
 * ({@link #leavesTile} is {@link HydrologyTileGeometry#GRID}-based, not the padded frame — H5).
 * Proximity to a global channel is read from a point index built over {@link RiverNetwork#getChannels()}
 * on every call; it is a throwaway structure of its own, because the Meanders collision {@link QuadTree}
 * is cleared every simulation step and cannot be reused — see {@link #buildGlobalPointIndex}.
 */
final class LocalDrainageTracer {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDrainageTracer.class);

    private record GlobalRiverUnit(double[] pos, double width, int id) implements SpatialIndexCircle {

        @Override
        public double[] getCenter() {
            return pos;
        }

        @Override
        public double getRadius() {
            return width / 2;
        }
    }

    static void traceLocalNetwork(
            int[] drainage,
            float[] elev,
            float[] gradMag,
            RiverNetwork network,
            @Nullable LocalRiverProvider.Stages stages) {
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

        final ImmutableRTree<GlobalRiverUnit> globalRiversPosition =
                new ImmutableRTree<>(getGlobalUnits(net), new GlobalRiverUnit(new double[] {0, 0}, 0, -1));

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
                if (nodeIndex[current] == -1
                        && nodeIndex[next] == -1
                        && !sources.containsPointInCircle(curNodePos, 5.0)) {
                    nodeIndex[current] = net.addNode(curNodePos, Endpoint.Type.SOURCE, -1, flow[current], -1);
                    sources.insertPoint(new CoordPoint(curNodePos));
                }
                // check if reaches ocean.
                if (nodeIndex[next] == -1) {
                    final double[] nextNodePos =
                            new double[] {Math.floorDiv(next, PADDED) + 0.5, (next % PADDED) + 0.5};
                    nodeIndex[next] = net.addNode(
                            nextNodePos,
                            isDrain ? Endpoint.Type.DRAIN : null,
                            -1,
                            HydrologyTuning.FLOW_PER_CELL_LOCAL,
                            -1);
                    final List<GlobalRiverUnit> nearbyGlobal = globalRiversPosition.queryContaining(nextNodePos);
                    for (GlobalRiverUnit unit : nearbyGlobal) {
                        net.addDirectedEdge(unit.id(), nodeIndex[next]);
                        net.addDirectedEdge(nodeIndex[next], unit.id());
                    }
                }
                if (nodeIndex[current] != -1) net.addDirectedEdge(nodeIndex[current], nodeIndex[next]);
            }
            if ((--inDegree[next]) == 0 && !isDrain) sourceQueue.add(next);
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

    private static List<GlobalRiverUnit> getGlobalUnits(AtomicView net) {
        final double[] flow = net.accumulateAndCorrectFlow();
        final GlobalRiverUnit[] globalRivers = new GlobalRiverUnit[net.size()];
        for (int id = 0; id < net.size(); id++) {
            globalRivers[id] = new GlobalRiverUnit(net.pos(id), widthFromFlow(flow[id]), id);
        }
        return Arrays.stream(globalRivers).toList();
    }
}
