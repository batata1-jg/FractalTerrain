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
 * (one {@code QuadTree} per 512×512 relief tile). Rivers that touch the tile border are dropped —
 * they continue into the neighbouring tile, which traces its own matching segment.
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
    private @Nullable ReliefProvider reliefOverride;

    public LocalRiverProvider(String path) {
        tiles = new NonIntersectingInfiniteQuadTree<>(path, new int[] {1, GRID, GRID}, key -> key != null ? buildTile(key) : null);
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

    private QuadTree<Channel.ChannelPt> buildTile(TileKey key) {
        return computeTile(key.get(X), key.get(Z), null);
    }

    private QuadTree<Channel.ChannelPt> computeTile(int tileX, int tileZ, @Nullable Stages stages) {
        final ReliefProvider relief = reliefProvider();
        final FloatTensor reliefTile = relief.getTile(tileX, tileZ);
        final int cellCount = GRID * GRID;

        // ch7 = drainageDirection (D8 one-hot bitfield), ch0 = carved elevation (unused for now).
        final float[] drainageDirection = new float[cellCount];
        System.arraycopy(reliefTile.data, 7 * cellCount, drainageDirection, 0, cellCount);

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
        int channelId = 0;
        for (int start = 0; start < cellCount; start++) {
            if (!riverMask[start]) continue;
            final boolean isSource = inDegree[start] == 0;
            final boolean isJunction = inDegree[start] >= 2;
            if (!isSource && !isJunction) continue; // mid-segment cell — covered by another walk
            final List<Integer> segmentCells = walkSegment(start, downstream, inDegree);
            if (segmentCells.size() < 2) continue;
            if (leavesTile(segmentCells)) continue; // continues into a neighbour tile
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
