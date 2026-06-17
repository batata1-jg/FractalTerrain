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
import me.batata_1.fractal_terrain.math.VectorOps;
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
        tiles = new NonIntersectingInfiniteQuadTree<>(
                path, "local_river", new int[] {1, GRID, GRID}, this::buildTile);
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

    private QuadTree<Channel.ChannelPt> buildTile( @Nullable TileKey key) {
        if(key==null) return null;
        return computeTile(key.get(X), key.get(Z), null);
    }


    //TODO: maybe rewrite this to be more efficient
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

        // 1. flow accumulation; 2. threshold into a river-cell mask.
        final float[] flow = computeFlow(drainageDirection, GRID);
        final boolean[] riverMask = new boolean[cellCount];
        for (int i = 0; i < cellCount; i++) riverMask[i] = flow[i] >= FLOW_THRESHOLD;

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

        // 3. trace each inter-junction segment into a Channel (split at confluences).
        final QuadTree<Channel.ChannelPt> tile = new QuadTree<>(
                new double[] {(double) tileX * GRID, (double) tileZ * GRID},
                new double[] {(double) (tileX + 1) * GRID, (double) (tileZ + 1) * GRID});
        final List<Channel> channels = new ArrayList<>();
        final byte[] conn = new byte[cellCount]; // reachesOutlet memo (0 unknown / 1 yes / 2 no / 3 visiting)
        int channelId = 0;
        for (int start = 0; start < cellCount; start++) {
            if (!riverMask[start]) continue;
            final boolean isSource = inDegree[start] == 0;
            final boolean isJunction = inDegree[start] >= 2;
            if (!isSource && !isJunction) continue; // mid-segment cell — covered by another walk
            final List<Integer> segmentCells = walkSegment(start, downstream, inDegree);
            if (segmentCells.size() < 2) continue;
            // Only global-river channels may leave the tile; every other channel must stay inside it.
            if (!isGlobalChannel(segmentCells, drainageDirection) && leavesTile(segmentCells)) continue;
            // Keep only channels whose drainage reaches a global-river cell or the sea.
            if (!reachesOutlet(segmentCells.getLast(), drainageDirection, elev, conn)) continue;
            final Channel channel = buildChannel(segmentCells, flow, tileX, tileZ, channelId++);
            if (channel == null) continue;
            channels.add(channel);
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
    private boolean isGlobalChannel(List<Integer> cells, int[] drainageDirection) {
        return (drainageDirection[cells.getFirst()] & PipelinePreprocessing.GLOBAL_RIVER_BIT) != 0;
    }

    /**
     * Trace the raw D8 drainage downstream from {@code startCell} and report whether it reaches an
     * outlet — a cell flagged {@link PipelinePreprocessing#GLOBAL_RIVER_BIT} (the global river) or a
     * sea cell ({@code elev < 0}). An interior sink, a path leaving the tile border, or a cycle counts
     * as not reaching an outlet. Results are memoized in {@code conn} (0 unknown / 1 yes / 2 no /
     * 3 visiting) and back-filled along the traced path, so the whole tile resolves in O(n).
     */
    private boolean reachesOutlet(int startCell, int[] drainageDirection, float[] elev, byte[] conn) {
        final List<Integer> path = new ArrayList<>();
        int cell = startCell;
        byte result;
        while (true) {
            if (conn[cell] != 0) {
                result = (conn[cell] == 1) ? (byte) 1 : (byte) 2; // 3 (visiting) ⇒ cycle ⇒ not connected
                break;
            }
            if ((drainageDirection[cell] & PipelinePreprocessing.GLOBAL_RIVER_BIT) != 0 || elev[cell] < 0) {
                conn[cell] = 1;
                result = 1;
                break;
            }
            conn[cell] = 3; // visiting
            path.add(cell);
            final int direction = neighbor(drainageDirection[cell]);
            if (direction == -1) {
                result = 2;
                break;
            }
            final int next = PipelinePreprocessing.neighborIndex(cell, direction, GRID);
            if (next == -1) { // exits the tile border — does not count as connected
                result = 2;
                break;
            }
            cell = next;
        }
        for (int c : path) conn[c] = result;
        return result == 1;
    }

    /** Build a resampled {@link Channel} from the segment's cell centres (in global native px). */
    private @Nullable Channel buildChannel(List<Integer> cells, float[] flow, int tileX, int tileZ, int channelId) {
        final ArrayList<double[]> points = new ArrayList<>(cells.size());
        float maxFlow = 0;
        for (int cell : cells) {
            final int x = cell / GRID;
            final int z = cell % GRID;
            points.add(new double[] {tileX * (double) GRID + x + 0.5, tileZ * (double) GRID + z + 0.5});
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
    public double nearestRiverDistance(double blockX, double blockZ) {
        final List<Channel.ChannelPt> hits = tiles.query(blockX, blockZ, QUERY_RADIUS);
        final double[] origin = {blockX, blockZ};
        double nearest = Double.MAX_VALUE;
        for (Channel.ChannelPt point : hits) {
            nearest = Math.min(nearest, VectorOps.distance(origin, point.toArray()));
        }
        return nearest;
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
