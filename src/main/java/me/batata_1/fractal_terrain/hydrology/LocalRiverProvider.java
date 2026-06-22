package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.computeFlow;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.neighbor;

import java.util.ArrayList;
import java.util.Comparator;
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
 * Detailed (local) river network derived per relief tile from the relief {@code riverData} field
 * (relief ch7; see {@link RiverData}). Flow accumulation over the D8 drainage (the low byte) gives
 * each cell a discharge; cells above a flow threshold — <b>excluding global-river pixels</b> — form
 * the local river mask, traced (splitting at confluences) into one {@link Channel} per inter-junction
 * segment, widthed from the local flow (start→end taper). The global rivers themselves are
 * reconstructed separately from {@code riverData} (grouping pixels by id, ordered by spline position,
 * widths from the packed id→width table) and <b>merged</b> in. Every channel's points are inserted
 * (in tile-local px) into a per-tile {@link QuadTree}.
 *
 * <p>Storage is a cache-only {@link NonIntersectingInfiniteQuadTree} of {@link Channel.ChannelPt}
 * (one {@code QuadTree} per 512×512 relief tile). A local segment is kept only when its drainage
 * reaches an outlet — a global-river cell (id ≠ 0) or the sea ({@code elev < 0}). Border rule: local
 * channels touching the tile border are dropped (the neighbouring tile traces its own matching
 * segment); global rivers are allowed to leave (they are consistent across tiles).
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
    private static final double QUERY_RADIUS = 1.0;

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

        // ch7 = riverData (D8 drainage in the low byte + packed global-river id / spline position /
        //   width table; see RiverData), stored bit-preserving; ch0 = carved+filled elevation (used
        //   for the sea test in the connectivity filter).
        final int[] riverData = new int[cellCount];
        final float[] elev = new float[cellCount];
        for (int i = 0; i < cellCount; i++) {
            riverData[i] = Float.floatToIntBits(reliefTile.data[7 * cellCount + i]);
            elev[i] = reliefTile.data[i];
        }

        // 1. flow accumulation; 2. bottom-up outlet connectivity; 3. threshold + prune into a local
        //    river-cell mask, EXCLUDING global-river pixels (those are reconstructed separately, so a
        //    local tributary terminates where it meets the global river). Folding `reaches` into the
        //    mask prunes whole subtrees that never drain to an outlet before any of them is traced.
        final float[] flow = computeFlow(riverData, GRID);
        final boolean[] reaches = computeReaches(riverData, elev);
        final boolean[] riverMask = new boolean[cellCount];
        for (int i = 0; i < cellCount; i++)
            riverMask[i] = flow[i] >= FLOW_THRESHOLD && reaches[i] && !RiverData.isGlobalRiver(riverData[i]);

        // Downstream pointer and in-degree restricted to local river cells.
        final int[] downstream = new int[cellCount];
        final int[] inDegree = new int[cellCount];
        for (int cell = 0; cell < cellCount; cell++) {
            downstream[cell] = -1;
            if (!riverMask[cell]) continue;
            final int direction = neighbor(riverData[cell]);
            if (direction == -1) continue;
            final int next = PipelinePreprocessing.neighborIndex(cell, direction, GRID);
            if (next == -1) continue;
            if (!riverMask[next]) continue;
            downstream[cell] = next;
            inDegree[next]++;
        }

        // 4. trace each inter-junction local segment into a Channel (split at confluences). The river
        //    mask is already pruned to outlet-reaching cells, so no per-segment outlet check is needed.
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
            // Local channels must stay inside the tile; the neighbour tile traces its own border segment.
            if (leavesTile(segmentCells)) continue;
            final Channel channel = buildChannel(segmentCells, flow, channelId++);
            if (channel == null) continue;
            channels.add(channel);
        }

        // 5. reconstruct the global rivers from riverData and merge them in.
        channels.addAll(reconstructGlobalChannels(riverData, channelId));

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
     * Bottom-up outlet connectivity over the whole tile: {@code reaches[c]} is true when cell {@code c}
     * drains (transitively, along the raw D8 drainage) into an outlet — a global-river cell
     * ({@link RiverData#isGlobalRiver}) or a sea cell ({@code elev < 0}). Computed once by seeding at
     * the outlet cells and reverse-propagating upstream (CSR reverse adjacency + BFS), in O(n). Cells
     * that stall in an interior sink, exit the tile border, or sit on a cycle are simply never reached
     * → false, matching the old per-segment downstream walk.
     */
    private static boolean[] computeReaches(int[] riverData, float[] elev) {
        final int n = riverData.length;

        // Per-cell raw D8 downstream pointer (or -1 for a sink / off-grid border exit).
        final int[] down = new int[n];
        for (int c = 0; c < n; c++) {
            final int dir = neighbor(riverData[c]);
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
            if (RiverData.isGlobalRiver(riverData[c]) || elev[c] < 0) {
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

    /**
     * Build a resampled {@link Channel} from the segment's cell centres (tile-local px). The width
     * tapers from the upstream cell's flow to the downstream cell's flow so it aligns with the
     * channels it meets at confluences (and, downstream, with the global river).
     */
    private @Nullable Channel buildChannel(List<Integer> cells, float[] flow, int channelId) {
        final ArrayList<double[]> points = new ArrayList<>(cells.size());
        float maxFlow = 0;
        for (int cell : cells) {
            final int x = cell / GRID;
            final int z = cell % GRID;
            points.add(new double[] {x + 0.5, z + 0.5});
            maxFlow = Math.max(maxFlow, flow[cell]);
        }
        final double startWidth = Math.max(MIN_WIDTH, WIDTH_SCALE * flow[cells.getFirst()]);
        final double endWidth = Math.max(MIN_WIDTH, WIDTH_SCALE * flow[cells.getLast()]);
        final Channel channel = new Channel(Math.max(MIN_WIDTH, WIDTH_SCALE * maxFlow), points, channelId);
        channel.setWidthProfile(startWidth, endWidth);
        try {
            channel.reSample(RESAMPLE_DIST);
        } catch (RuntimeException degenerate) {
            return null;
        }
        return channel;
    }

    // -------------------------------------------------------------------------
    // Global river reconstruction (from the packed riverData)
    // -------------------------------------------------------------------------

    /**
     * Rebuild each global river as its own {@link Channel} from {@code riverData}: group global pixels
     * by id, order each id's pixels by their packed spline position, and taper the width from the
     * id→width table's {@code startWidth} to {@code endWidth}. Channel ids continue from
     * {@code firstChannelId} so they don't collide with the local channels.
     */
    private List<Channel> reconstructGlobalChannels(int[] riverData, int firstChannelId) {
        final int riverCount = readGlobalRiverCount(riverData);
        if (riverCount == 0) return new ArrayList<>();

        final float[] startWidth = new float[riverCount + 1]; // 1-based by id
        final float[] endWidth = new float[riverCount + 1];
        readWidthTable(riverData, riverCount, startWidth, endWidth);

        // Collect {splinePosition, cell} per id.
        final List<ArrayList<int[]>> pixelsById = new ArrayList<>(riverCount + 1);
        for (int id = 0; id <= riverCount; id++) pixelsById.add(new ArrayList<>());
        for (int cell = 0; cell < riverData.length; cell++) {
            final int id = RiverData.riverId(riverData[cell]);
            if (id == 0 || id > riverCount) continue;
            pixelsById.get(id).add(new int[] {RiverData.splinePosition(riverData[cell]), cell});
        }

        final List<Channel> channels = new ArrayList<>();
        int channelId = firstChannelId;
        for (int id = 1; id <= riverCount; id++) {
            final ArrayList<int[]> pixels = pixelsById.get(id);
            if (pixels.size() < 2) continue;
            pixels.sort(Comparator.comparingInt(p -> p[0]));
            final ArrayList<double[]> points = new ArrayList<>(pixels.size());
            for (int[] p : pixels) {
                final int cell = p[1];
                points.add(new double[] {cell / GRID + 0.5, cell % GRID + 0.5});
            }
            final double start = Math.max(MIN_WIDTH, startWidth[id]);
            final double end = Math.max(MIN_WIDTH, endWidth[id]);
            final Channel channel = new Channel(Math.max(start, end), points, channelId++);
            channel.setWidthProfile(start, end);
            try {
                channel.reSample(RESAMPLE_DIST);
            } catch (RuntimeException degenerate) {
                continue;
            }
            channels.add(channel);
        }
        return channels;
    }

    /** Read the global-river id count from the first non-global pixel's table slot (0 if none). */
    private static int readGlobalRiverCount(int[] riverData) {
        for (int riverDatum : riverData) {
            if (RiverData.riverId(riverDatum) != 0) continue;
            return RiverData.upperHalf(riverDatum) & RiverData.MAX_RIVER_ID;
        }
        return 0;
    }

    /**
     * Read the id→width table: the upper halfwords of the first {@code 1 + 4*riverCount} non-global
     * pixels (slot 0 = count, then start hi/lo, end hi/lo per id) into the 1-based
     * {@code startWidth}/{@code endWidth} arrays.
     */
    private static void readWidthTable(int[] riverData, int riverCount, float[] startWidth, float[] endWidth) {
        final int slotCount = 1 + 4 * riverCount;
        final int[] slots = new int[slotCount];
        int cursor = 0;
        for (int i = 0; i < riverData.length && cursor < slotCount; i++) {
            if (RiverData.riverId(riverData[i]) != 0) continue;
            slots[cursor++] = RiverData.upperHalf(riverData[i]);
        }
        for (int r = 0; r < riverCount; r++) {
            final int base = 1 + 4 * r;
            startWidth[r + 1] = RiverData.widthFromSlots(slots[base], slots[base + 1]);
            endWidth[r + 1] = RiverData.widthFromSlots(slots[base + 2], slots[base + 3]);
        }
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
