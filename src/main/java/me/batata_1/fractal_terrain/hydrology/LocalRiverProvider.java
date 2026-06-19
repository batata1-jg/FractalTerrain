package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.computeFlow;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.neighbor;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteQuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Detailed (local) river network derived per relief tile from the relief {@code drainageDirection}
 * field. Flow accumulation over the D8 drainage gives each cell a discharge; cells above a flow
 * threshold form the river mask, which is traced — splitting at confluences — into one
 * {@link Channel} per inter-junction segment, widthed from the local flow. Each channel's points are
 * inserted (in global native-px) into a per-tile {@link QuadTree}; the network anchors to the global
 * river because the relief carve already biases drainage toward it.
 *
 * <p>Storage is a cache-only {@link NonIntersectingInfiniteQuadTree} of {@link Channel.ChannelPt}
 * (one {@code QuadTree} per 512×512 relief tile). A traced segment is kept only when its drainage
 * reaches an outlet — a global-river cell (relief ch7's {@link PipelinePreprocessing#GLOBAL_RIVER_BIT})
 * or the sea ({@code elev < 0}). Border rule: only global-river channels may leave the tile (they are
 * consistent across tiles via the global network); every other channel touching the border is dropped,
 * since the neighbouring tile traces its own matching segment.
 */
public class LocalRiverProvider {

    // ---- Tuning knobs (edit here during testing) ----------------------------
    /** sqrt-scaled flow accumulation at/above which a cell counts as a river cell. */
    private static final float FLOW_THRESHOLD = 40f;
    /** River width (native px) per unit of sqrt-scaled flow. */
    private static final double WIDTH_SCALE = 0.15;
    /** Floor on a traced channel's width. */
    private static final double MIN_WIDTH = 1.0;
    /** Arc-length spacing (native px) the traced channel splines are resampled to. */
    private static final double RESAMPLE_DIST = 2.0;
    /** Radius (native px) the nearest-river query searches. */
    private static final double QUERY_RADIUS = 64.0;

    // ---- Geometry -----------------------------------------------------------
    /** Relief tile side in native px (= grid side for flow routing). */
    private static final int GRID = 512;

    private final NonIntersectingInfiniteQuadTree<Channel.ChannelPt> tiles;

    /** Test-only override for the relief source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable ReliefProvider reliefOverride;

    public LocalRiverProvider(String path) {
        tiles = new NonIntersectingInfiniteQuadTree<>(path, "local_river", new int[] {GRID, GRID}, this::buildTile);
    }

    @TestOnly
    public void setReliefProvider(ReliefProvider provider) {
        this.reliefOverride = provider;
    }

    private ReliefProvider reliefProvider() {
        return (reliefOverride != null) ? reliefOverride : FractalTerrainInstance.getReliefProvider();
    }

    public NonIntersectingInfiniteQuadTree<Channel.ChannelPt> getInfiniteTensor() {
        return tiles;
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    private QuadTree<Channel.ChannelPt> buildTile(@Nullable TileKey key) {
        if (key == null) return null;
        return computeTile(key.get(X - 1), key.get(Z - 1), null);
    }

    // TODO: maybe rewrite this to be more efficient
    private QuadTree<Channel.ChannelPt> computeTile(int tileX, int tileZ, @Nullable Stages stages) {
        final ReliefProvider relief = reliefProvider();
        final FloatTensor reliefTile = relief.getTile(tileX, tileZ);
        final int cellCount = GRID * GRID;

        // ch7 = drainageDirection (D8 one-hot bitfield + GLOBAL_RIVER_BIT), stored bit-preserving;
        // ch0 = carved+filled elevation (used for the sea test in the connectivity filter).
        final int[] drainageDirection = new int[cellCount];
        final float[] elev = new float[cellCount];
        for (int i = 0; i < cellCount; i++) {
            drainageDirection[i] = Float.floatToIntBits(reliefTile.data[7 * cellCount + i]);
            elev[i] = reliefTile.data[i];
        }

        // 1. flow accumulation; 2. bottom-up outlet connectivity; 3. threshold + prune into a
        //    river-cell mask. Folding `reaches` into the mask prunes whole subtrees that never drain
        //    to an outlet before any of them is traced (top-down), and never splits a confluence
        //    (reaches is downstream-closed upward: if a cell reaches an outlet, so does every cell
        //    draining into it).
        final float[] flow = computeFlow(drainageDirection, GRID);
        final boolean[] reaches = computeReaches(drainageDirection, elev);
        final boolean[] riverMask = new boolean[cellCount];
        for (int i = 0; i < cellCount; i++) riverMask[i] = flow[i] >= FLOW_THRESHOLD && reaches[i];

        // Downstream pointer and in-degree restricted to river cells.
        final int[] downstream = new int[cellCount];
        final int[] inDegree = new int[cellCount];
        for (int cell = 0; cell < cellCount; cell++) {
            downstream[cell] = -1;
            if (!riverMask[cell]) continue;
            final int direction = neighbor(drainageDirection[cell]);
            if (direction == -1) continue;
            final int next = PipelinePreprocessing.neighborIndex(cell, direction, GRID);
            if (next == -1) continue;
            if (!riverMask[next]) continue;
            downstream[cell] = next;
            inDegree[next]++;
        }

        // 4. trace each inter-junction segment into a Channel (split at confluences). The river mask is
        //    already pruned to outlet-reaching cells, so no per-segment outlet check is needed.
        final QuadTree<Channel.ChannelPt> tile =
                new QuadTree<>(new double[] {-16, -16}, new double[] {GRID + 16, GRID + 16});
        final List<Channel> channels = new ArrayList<>();
        int channelId = 0;
        for (int start = 0; start < cellCount; start++) {
            if (!riverMask[start]) continue;
            final boolean isSource = inDegree[start] == 0;
            final boolean isJunction = inDegree[start] >= 2;
            if (!isSource && !isJunction) continue; // mid-segment cell — covered by another walk
            final List<Integer> segmentCells = walkSegment(start, downstream, inDegree);
            if (segmentCells.size() < 2) continue;
            // Only global-river channels may leave the tile; every other channel must stay inside it.
            if (!isGlobalChannel(start, drainageDirection) && leavesTile(segmentCells)) continue;
            final Channel channel = buildChannel(segmentCells, flow, tileX, tileZ, channelId++);
            if (channel == null) continue;
            channels.add(channel);
        }

        for (Channel channel : channels) {
            for (Channel.ChannelPt point : channel.getChannelAsPts()) tile.insertPoint(point);
        }

        if (stages != null) {
            stages.flow = flow;
            stages.riverMask = riverMask;
            stages.channels = channels;
        }
        return tile;
    }

    /**
     * Walk downstream from {@code start} appending cell indices until the segment ends: a confluence
     * (in-degree ≥ 2, included as the terminal vertex), a cell with no in-network downstream, or a
     * dead-stop guard against pathological loops.
     */
    private List<Integer> walkSegment(int start, int[] downstream, int[] inDegree) {
        final List<Integer> cells = new ArrayList<>();
        cells.add(start);
        int current = start;
        for (int step = 0; step < GRID * GRID; step++) {
            final int next = downstream[current];
            if (next == -1) break;
            cells.add(next);
            if (inDegree[next] >= 2) break; // reached the next junction
            current = next;
        }
        return cells;
    }

    /** True if the first or last cell of the segment lies on the tile border. */
    private boolean leavesTile(List<Integer> cells) {
        return onBorder(cells.getFirst()) || onBorder(cells.getLast());
    }

    private boolean onBorder(int cell) {
        final int x = cell / GRID;
        final int z = cell % GRID;
        return x == 0 || z == 0 || x == GRID - 1 || z == GRID - 1;
    }

    /**
     * Whether the segment lies on the global river, judged by its first (upstream) cell carrying
     * {@link PipelinePreprocessing#GLOBAL_RIVER_BIT}: a global-river trunk segment's head is itself a
     * river cell, while a local tributary's source is not.
     */
    private boolean isGlobalChannel(int startNode, int[] drainageDirection) {
        return (drainageDirection[startNode] & PipelinePreprocessing.GLOBAL_RIVER_BIT) != 0;
    }

    /**
     * Bottom-up outlet connectivity over the whole tile: {@code reaches[c]} is true when cell {@code c}
     * drains (transitively, along the raw D8 drainage) into an outlet — a cell flagged
     * {@link PipelinePreprocessing#GLOBAL_RIVER_BIT} (the global river) or a sea cell ({@code elev < 0}).
     * Computed once by seeding at the outlet cells and reverse-propagating upstream (CSR reverse
     * adjacency + BFS), in O(n). Cells that stall in an interior sink, exit the tile border, or sit on a
     * cycle are simply never reached → false, matching the old per-segment downstream walk.
     */
    private static boolean[] computeReaches(int[] drainageDirection, float[] elev) {
        final int n = drainageDirection.length;

        // Per-cell raw D8 downstream pointer (or -1 for a sink / off-grid border exit).
        final int[] down = new int[n];
        for (int c = 0; c < n; c++) {
            final int dir = neighbor(drainageDirection[c]);
            down[c] = (dir == -1) ? -1 : PipelinePreprocessing.neighborIndex(c, dir, GRID);
        }

        // Reverse adjacency in CSR form: head[d]..head[d+1] indexes the cells that drain into d.
        final int[] head = new int[n + 1];
        for (int c = 0; c < n; c++) if (down[c] != -1) head[down[c] + 1]++;
        for (int c = 0; c < n; c++) head[c + 1] += head[c];
        final int[] upstream = new int[head[n]];
        final int[] cursor = head.clone();
        for (int c = 0; c < n; c++) if (down[c] != -1) upstream[cursor[down[c]]++] = c;

        // Seed BFS at outlet cells, propagate upstream.
        final boolean[] reaches = new boolean[n];
        final int[] queue = new int[n];
        int qHead = 0;
        int qTail = 0;
        for (int c = 0; c < n; c++) {
            if ((drainageDirection[c] & PipelinePreprocessing.GLOBAL_RIVER_BIT) != 0 || elev[c] < 0) {
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

    /** Build a resampled {@link Channel} from the segment's cell centres (in global native px). */
    private @Nullable Channel buildChannel(List<Integer> cells, float[] flow, int tileX, int tileZ, int channelId) {
        final ArrayList<double[]> points = new ArrayList<>(cells.size());
        float maxFlow = 0;
        for (int cell : cells) {
            final int x = cell / GRID;
            final int z = cell % GRID;
            points.add(new double[] {x + 0.5, z + 0.5});
            maxFlow = Math.max(maxFlow, flow[cell]);
        }
        final double width = Math.max(MIN_WIDTH, WIDTH_SCALE * maxFlow);
        final Channel channel = new Channel(width, points, channelId);
        try {
            channel.reSample(RESAMPLE_DIST);
        } catch (RuntimeException degenerate) {
            return null;
        }
        return channel;
    }

    // -------------------------------------------------------------------------
    // Query API (consumed by BiomeProvider)
    // -------------------------------------------------------------------------

    /**
     * Distance (native px) from {@code (blockX, blockZ)} to the nearest local-river sample, or
     * {@link Double#MAX_VALUE} when no river lies within {@link #QUERY_RADIUS}.
     */
    public boolean insideMargin(double[] coords) {
        List<Channel.ChannelPt> hits = tiles.getValuesWithin(coords, QUERY_RADIUS);
        return !hits.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int tileX, int tileZ) {
        final Stages stages = new Stages();
        computeTile(tileX, tileZ, stages);
        return stages;
    }

    @TestOnly
    public static final class Stages {
        public float[] flow;
        public boolean[] riverMask;
        public List<Channel> channels;
    }
}
