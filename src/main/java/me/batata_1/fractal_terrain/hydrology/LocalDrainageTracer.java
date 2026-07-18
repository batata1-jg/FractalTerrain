package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.config.HydrologyTuning.widthFromFlow;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.*;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.computeFlow;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.neighbor;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import org.jetbrains.annotations.Nullable;

/**
 * Responsibility: the detailed <em>local</em> river network, traced directly from the per-tile drainage
 * field (flow accumulation → reach test → segment walk → channel build) and attached in place onto the
 * live {@link RiverNetwork} that already carries the global (Meanders-relaxed) channels, so a single
 * downstream bed-assignment/unit-collection pass serves both. This is the deterministic core exercised
 * headlessly by {@code LocalRiverGoldenTest} via {@link LocalRiverProvider#traceLocalNetworkForTest}.
 *
 * <p>Collaborators: {@link HydrologyTileGeometry} (tile geometry); {@link PipelinePreprocessing} (flow
 * accumulation, neighbour resolution); {@link Channel} / {@link RiverNetwork} (traced-segment geometry
 * and the graph it attaches to); consumed by {@code LocalRiverProvider.buildTile}.
 *
 * <p>Invariants: purely functional over its parameters plus the one {@link RiverNetwork} it mutates in
 * place — no state shared across tiles, so per-tile traces from different worker threads never
 * interact. A channel is only attached when its whole segment stays interior to the tile's TRUE boundary
 * ({@link #leavesTile}, still {@link HydrologyTileGeometry#GRID}-based, not the padded frame — H5). No
 * per-pixel global boolean mask is built anywhere: proximity to a global channel is read from a point
 * index freshly built over {@link RiverNetwork#getChannels()} every call (the transient Meanders
 * collision {@link QuadTree} is cleared every simulation step and cannot be reused, so this index is its
 * own, separate, throwaway structure) — see {@link #buildGlobalPointIndex}.
 */
final class LocalDrainageTracer {

    private LocalDrainageTracer() {}

    private static final float FLOW_THRESHOLD = 40f;

    /** Half-extent of the fresh global-channel point index's bounds (channels never approach this). */
    private static final double POINT_INDEX_EXTENT = 1e9;

    /**
     * Traces the local drainage network and mutates {@code network} in place: flow accumulation → reach
     * test → segment walk, as before, but every surviving interior segment is now attached directly to
     * {@code network} as a graph edge instead of being returned in a detached list. Proximity to a global
     * channel — read from a point index freshly built over {@code network.getChannels()} (see
     * {@link #buildGlobalPointIndex}) — replaces both the walk-termination exclusion and the reach-seed
     * adjacency the removed pixel mask used to provide ({@link HydrologyTuning#LOCAL_ATTACH_RADIUS} gates
     * both, plus the junction-attachment split below).
     *
     * <p>Each surviving segment's upstream ridge cell becomes a SOURCE node; its downstream end either
     * splits the nearest global channel (minting a JUNCTION) when a global-channel point lies within
     * {@code LOCAL_ATTACH_RADIUS} of it, or terminates at a coast DRAIN ({@code elev < 0}). A segment
     * whose downstream end finds neither is DROPPED rather than force-attached (DL-013/DL-015: the
     * continuous attach-time distance check is authoritative over the grid reach seed at the radius
     * boundary). If the walk yields no surviving segments, or the graph carries no channels to index
     * against (every segment then only attaches via a coast drain), this method is simply a no-op /
     * coast-only pass — {@link #buildGlobalPointIndex} and {@link QuadTree#getPointsInCircle} both
     * tolerate an empty channel list without error.
     *
     * <p>Returns nothing — see {@code LocalRiverProvider.buildTile} for how the caller augments the
     * boundary-elevation map (reading the nodes this method minted) and runs the single
     * assign/collectUnits pass over the now-unified graph.
     */
    static void traceLocalNetwork(
            int[] drainage, float[] elev, RiverNetwork network, @Nullable LocalRiverProvider.Stages stages) {
        final int cellCount = PADDED * PADDED;
        final float[] flow = computeFlow(drainage, PADDED);
        final QuadTree<Channel.ChannelPt> globalIndex = buildGlobalPointIndex(network.getChannels());
        final boolean[] reaches = computeReaches(drainage, elev, globalIndex);
        final boolean[] riverMask = new boolean[cellCount];
        for (int cell = 0; cell < cellCount; cell++) {
            riverMask[cell] =
                    flow[cell] >= FLOW_THRESHOLD && reaches[cell] && !nearGlobal(globalIndex, cell / PADDED, cell % PADDED);
        }

        final int[] downstream = new int[cellCount];
        final int[] inDegree = new int[cellCount];
        for (int cell = 0; cell < cellCount; cell++) {
            downstream[cell] = -1;
            if (!riverMask[cell]) continue;
            final int direction = neighbor(drainage[cell]);
            if (direction == -1) continue;
            final int next = PipelinePreprocessing.neighborIndex(cell, direction, PADDED);
            if (next == -1 || !riverMask[next]) continue;
            downstream[cell] = next;
            inDegree[next]++;
        }

        int localChannelId = 0;
        for (int start = 0; start < cellCount; start++) {
            if (!riverMask[start]) continue;
            final boolean isSource = inDegree[start] == 0;
            final boolean isJunction = inDegree[start] >= 2;
            if (!isSource && !isJunction) continue;
            final List<Integer> segmentCells = walkSegment(start, downstream, inDegree);
            if (segmentCells.size() < 2 || leavesTile(segmentCells)) continue;
            final Channel channel = buildLocalChannel(segmentCells, flow, localChannelId++);
            if (channel == null) continue; // degenerate geometry: skip (buildLocalChannel already logs nothing)
            attachSegment(network, globalIndex, channel, segmentCells.getLast(), elev);
        }

        if (stages != null) {
            stages.flow = flow;
            stages.riverMask = riverMask;
        }
    }

    /**
     * Attaches one traced local {@code channel} (still in the un-padded {@code GRID} frame) to
     * {@code network} as a SOURCE -> (JUNCTION split | coast DRAIN) edge. {@code globalIndex} (built in
     * the same {@code GRID} frame) locates the nearest global-channel point within {@link
     * HydrologyTuning#LOCAL_ATTACH_RADIUS} of the segment's downstream end; when found, the nearest
     * candidate's exact channel/index is split (DL-015: this continuous distance check is authoritative,
     * so a candidate the split bounds-check rejects — e.g. it turns out to sit exactly on a channel
     * endpoint, or a prior attach in this same pass already split past it — is a clean DROP, never a
     * force-attach). Otherwise the segment attaches to a coast DRAIN when its downstream cell is below
     * sea level, or is dropped (DL-013: no dangling, datum-less node).
     */
    private static void attachSegment(
            RiverNetwork network,
            QuadTree<Channel.ChannelPt> globalIndex,
            Channel channel,
            int downstreamCell,
            float[] elev) {
        final ArrayList<double[]> pts = channel.spline.points();
        final double[] downstreamPt = pts.getLast();
        final Channel.ChannelPt nearestGlobal =
                nearestWithin(globalIndex, downstreamPt[0], downstreamPt[1], HydrologyTuning.LOCAL_ATTACH_RADIUS);

        final RiverNetwork.NodeSpec sourceSpec =
                new RiverNetwork.NodeSpec(pts.getFirst()[0], pts.getFirst()[1], Endpoint.Type.SOURCE);

        if (nearestGlobal != null) {
            final Channel globalChannel = network.getChannel(nearestGlobal.channelId());
            if (globalChannel == null) return; // stale candidate (already restructured this pass): drop.
            final int downstreamChannelId = network.split(nearestGlobal.channelId(), nearestGlobal.index(), false);
            if (downstreamChannelId == -1) return; // DL-015: split rejected the candidate; drop, don't force.
            // Snap the attaching edge's last point onto the exact junction coordinate split() just minted,
            // so the new edge's endpoint coincides with the node it terminates at (no confluence gap).
            pts.set(
                    pts.size() - 1,
                    network.getNode(globalChannel.endNodeId).coord.clone());
            network.attachSourceToExistingNode(
                    sourceSpec,
                    globalChannel.endNodeId,
                    pts,
                    channel.startWidth,
                    channel.endWidth,
                    HydrologyTuning.RESAMPLE_DIST);
            return;
        }

        if (elev[downstreamCell] >= 0) return; // DL-013: no global channel in reach, no coast -> drop.
        final RiverNetwork.NodeSpec drainSpec =
                new RiverNetwork.NodeSpec(pts.getLast()[0], pts.getLast()[1], Endpoint.Type.DRAIN);
        network.attachSourceToNewDrain(
                sourceSpec, drainSpec, pts, channel.startWidth, channel.endWidth, HydrologyTuning.RESAMPLE_DIST);
    }


    /**
     * A fresh point index over every point of every channel in {@code channels}, shifted into the
     * un-padded {@code GRID} frame (subtracting {@link HydrologyTileGeometry#PAD}) that the local trace
     * itself works in — mirrors the coordinate convention the removed {@code rasterizeGlobalMask} used.
     * Tolerates an empty {@code channels} list (an empty tree; every query below just returns no
     * candidates, so every non-coast segment is dropped rather than dereferencing a missing point).
     */
    private static QuadTree<Channel.ChannelPt> buildGlobalPointIndex(List<Channel> channels) {
        final QuadTree<Channel.ChannelPt> index = new QuadTree<>(
                new double[] {-POINT_INDEX_EXTENT, -POINT_INDEX_EXTENT},
                new double[] {POINT_INDEX_EXTENT, POINT_INDEX_EXTENT});
        for (Channel ch : channels) {
            final List<double[]> pts = ch.spline.points();
            for (int i = 0; i < pts.size(); i++) {
                final double[] p = pts.get(i);
                index.insertPoint(new Channel.ChannelPt(new double[] {p[0] - PAD, p[1] - PAD}, i, ch.channelId));
            }
        }
        return index;
    }

    /** Whether any global-channel point lies within {@link HydrologyTuning#LOCAL_ATTACH_RADIUS} of (x, z). */
    private static boolean nearGlobal(QuadTree<Channel.ChannelPt> index, int x, int z) {
        return !index.getPointsInCircle(new double[] {x, z}, HydrologyTuning.LOCAL_ATTACH_RADIUS)
                .isEmpty();
    }

    /** The nearest global-channel point within {@code radius} of {@code (x, z)}, or {@code null}. */
    private static @Nullable Channel.ChannelPt nearestWithin(
            QuadTree<Channel.ChannelPt> index, double x, double z, double radius) {
        Channel.ChannelPt nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Channel.ChannelPt candidate : index.getPointsInCircle(new double[] {x, z}, radius)) {
            final double dx = candidate.coords()[0] - x;
            final double dz = candidate.coords()[1] - z;
            final double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static List<Integer> walkSegment(int start, int[] downstream, int[] inDegree) {
        final List<Integer> cells = new ArrayList<>();
        cells.add(start);
        int current = start;
        for (int step = 0; step < GRID * GRID; step++) {
            final int next = downstream[current];
            if (next == -1) break;
            cells.add(next);
            if (inDegree[next] >= 2) break;
            current = next;
        }
        return cells;
    }

    private static boolean leavesTile(List<Integer> cells) {
        return onBorder(cells.getFirst()) || onBorder(cells.getLast());
    }

    private static boolean onBorder(int cell) {
        final int x = cell / GRID;
        final int z = cell % GRID;
        return x == 0 || z == 0 || x == GRID - 1 || z == GRID - 1;
    }

    private static boolean[] computeReaches(int[] drainage, float[] elev, QuadTree<Channel.ChannelPt> globalIndex) {
        final int n = drainage.length;
        final int[] down = new int[n];
        for (int c = 0; c < n; c++) {
            final int dir = neighbor(drainage[c]);
            down[c] = (dir == -1) ? -1 : PipelinePreprocessing.neighborIndex(c, dir, GRID);
        }
        final int[] head = new int[n + 1];
        for (int c = 0; c < n; c++) if (down[c] != -1) head[down[c] + 1]++;
        for (int c = 0; c < n; c++) head[c + 1] += head[c];
        final int[] upstream = new int[head[n]];
        final int[] cursor = head.clone();
        for (int c = 0; c < n; c++) if (down[c] != -1) upstream[cursor[down[c]]++] = c;

        final boolean[] reaches = new boolean[n];
        final int[] queue = new int[n];
        int qHead = 0;
        int qTail = 0;
        for (int c = 0; c < n; c++) {
            if (elev[c] < 0 || nearGlobal(globalIndex, c / GRID, c % GRID)) {
                reaches[c] = true;
                queue[qTail++] = c;
            }
        }
        while (qHead < qTail) {
            final int d = queue[qHead++];
            for (int k = head[d]; k < head[d + 1]; k++) {
                final int u = upstream[k];
                if (!reaches[u]) {
                    reaches[u] = true;
                    queue[qTail++] = u;
                }
            }
        }
        return reaches;
    }

    private static @Nullable Channel buildLocalChannel(List<Integer> cells, float[] flow, int channelId) {
        final ArrayList<double[]> points = new ArrayList<>(cells.size());
        float maxFlow = 0;
        for (int cell : cells) {
            points.add(new double[] {(double) cell / GRID + 0.5, cell % GRID + 0.5});
            maxFlow = Math.max(maxFlow, flow[cell]);
        }
        final double startWidth = widthFromFlow(flow[cells.getFirst()]);
        final double endWidth = widthFromFlow(flow[cells.getLast()]);
        final Channel channel = new Channel(widthFromFlow(maxFlow), points, channelId);
        channel.setWidthProfile(startWidth, endWidth);
        if (!channel.isResampleable()) return null; // degenerate geometry: skip this channel
        try {
            channel.reSample(HydrologyTuning.RESAMPLE_DIST);
        } catch (RuntimeException runaway) {
            // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); drop the channel.
            return null;
        }
        return channel;
    }
}
