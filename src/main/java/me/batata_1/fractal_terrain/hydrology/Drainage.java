package me.batata_1.fractal_terrain.hydrology;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;

/**
 * Flow-routing math shared by the river providers: drainage direction, flow accumulation and
 * depression filling.
 *
 * <p>Everything here works on a square grid flattened in row-major order as {@code cellIndex = x *
 * gridSize + z}. Drainage uses the <b>D8</b> model: each cell points at exactly one of its eight
 * neighbours (the steepest downhill one). A direction is stored as a one-hot bitfield — bit {@code i}
 * set means "this cell drains toward neighbour {@code i}", where neighbour {@code i} sits at offset
 * {@code (NEIGHBOR_OFFSET_X[i], NEIGHBOR_OFFSET_Z[i])}. The first four offsets (indices 0..3) are the
 * diagonals (distance {@code √2}); the last four (4..7) are the cardinals (distance 1).
 *
 * <p>All methods are static and hold no state between calls, so concurrent tile builds may call them
 * freely.
 */
public class Drainage {

    /** D8 neighbour offsets. Indices 0..3 are diagonal (√2 away); 4..7 are cardinal (1 away). */
    public static final int[] NEIGHBOR_OFFSET_X = {-1, 1, -1, 1, 0, 0, -1, 1};

    public static final int[] NEIGHBOR_OFFSET_Z = {1, 1, -1, -1, -1, 1, 0, 0};

    /**
     * For each D8 direction index, the index of the opposite direction (offsets negated). Used to
     * convert an outgoing direction into the ingoing direction recorded on the entered neighbour.
     */
    public static final int[] OPPOSITE_DIRECTION = {3, 2, 1, 0, 5, 4, 7, 6};

    /** First offset index considered by a full D8 search — the diagonals are in play. */
    private static final int FIRST_DIRECTION_D8 = 0;

    /** First offset index considered by a cardinal-only search — skips the four diagonals. */
    private static final int FIRST_DIRECTION_CARDINAL = 4;

    // ---------------------------------------------------------------------------------------------
    // Direction bitfield decoding
    // ---------------------------------------------------------------------------------------------

    /**
     * Decode a one-hot drainage bitfield into the D8 neighbour index it points at, or {@code -1} if no
     * direction bit (0..7) is set (a sink / undrained cell). Bits ≥ 8 are ignored, so a field that
     * stuffs extra flags into the upper bytes can be decoded directly.
     */
    public static int directionOf(int drainageDirection) {
        for (int i = 0; i < 8; i++) if ((drainageDirection & (1 << i)) != 0) return i;
        return -1;
    }

    /**
     * Index of {@code cellIndex}'s neighbour in D8 direction {@code direction}, or {@code -1} when that
     * neighbour falls outside the {@code gridSize × gridSize} grid. A drainage field computed on a
     * larger (padded) grid and then cropped can point off the cropped grid at its border; routines that
     * route flow treat such an off-grid target as a sink (flow simply leaves the tile).
     */
    public static int neighborIndex(final int cellIndex, final int direction, final int gridSize) {
        final int x = cellIndex / gridSize;
        final int z = cellIndex % gridSize;
        final int neighborX = x + NEIGHBOR_OFFSET_X[direction];
        final int neighborZ = z + NEIGHBOR_OFFSET_Z[direction];
        if (neighborX < 0 || neighborZ < 0 || neighborX >= gridSize || neighborZ >= gridSize) return -1;
        return neighborX * gridSize + neighborZ;
    }

    // ---------------------------------------------------------------------------------------------
    // Routing topology
    // ---------------------------------------------------------------------------------------------

    /**
     * The routing topology of a D8 drainage field: where each cell sends its flow, and how many cells
     * send flow into it. Both {@link #computeFlow} and the local-network trace walk a grid in
     * upstream-to-downstream order, and both need exactly this pair of arrays to do it.
     *
     * @param downstream per-cell index of the cell it drains into, or {@code -1} for a sink (no
     *     direction bit set, or the target falls off the grid).
     * @param inDegree per-cell count of upstream contributors.
     */
    public record FlowGraph(int[] downstream, int[] inDegree) {

        /** Derive the routing topology of {@code drainageDirection} over a {@code gridSize} square. */
        public static FlowGraph of(final int[] drainageDirection, final int gridSize) {
            final int cellCount = gridSize * gridSize;
            final int[] downstream = new int[cellCount];
            final int[] inDegree = new int[cellCount];
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                downstream[cellIndex] = -1;
                final int direction = directionOf(drainageDirection[cellIndex]);
                if (direction == -1) continue;
                final int next = neighborIndex(cellIndex, direction, gridSize);
                if (next == -1) continue;
                downstream[cellIndex] = next;
                inDegree[next]++;
            }
            return new FlowGraph(downstream, inDegree);
        }

        /**
         * The headwater frontier: every cell with no upstream contributor, seeded in ascending cell
         * index so a Kahn walk over this queue visits confluences in a pinned, reproducible order.
         *
         * <p>The returned queue and {@link #inDegree} are both caller-owned scratch — a walk consumes
         * the queue and decrements the in-degrees as it goes, so call this once per traversal.
         */
        public Deque<Integer> sources() {
            final Deque<Integer> queue = new ArrayDeque<>();
            for (int cellIndex = 0; cellIndex < inDegree.length; cellIndex++) {
                if (inDegree[cellIndex] == 0) queue.add(cellIndex);
            }
            return queue;
        }
    }

    /**
     * Topological flow accumulation over a D8 drainage field. Every cell starts with {@code
     * initialFlow}; processing cells in upstream-to-downstream (Kahn topological) order, each cell's
     * accumulated flow is pushed to the neighbour it drains into, plus a {@code flowPerCell} gain
     * contributed by that routing step.
     *
     * <p>The two knobs are independent: {@code initialFlow} is the baseline every cell (including a
     * headwater source) carries before any upstream contribution, while {@code flowPerCell} is added
     * once per downstream hop, so it scales flow with channel <em>length</em> rather than with
     * catchment cell count. Passing {@code initialFlow = 1, flowPerCell = 0} reproduces the classic
     * "flow = number of upstream cells" accumulation.
     *
     * @param drainageDirection one-hot D8 bitfield per cell (see {@link #computeDrainageDirection}).
     * @param gridSize side length of the square grid.
     * @param initialFlow baseline flow every cell starts with.
     * @param flowPerCell extra flow added to the downstream cell at each routing step.
     * @return per-cell flow accumulation, indexed {@code x * gridSize + z}.
     */
    public static float[] computeFlow(
            final int[] drainageDirection, final int gridSize, final float initialFlow, final float flowPerCell) {
        final FlowGraph graph = FlowGraph.of(drainageDirection, gridSize);
        final int[] downstream = graph.downstream();
        final int[] inDegree = graph.inDegree();

        final float[] flow = new float[gridSize * gridSize];
        Arrays.fill(flow, initialFlow);

        final Deque<Integer> frontier = graph.sources();
        while (!frontier.isEmpty()) {
            final int current = frontier.poll();
            final int next = downstream[current];
            if (next == -1) continue;
            if ((--inDegree[next]) == 0) frontier.add(next);
            flow[next] += flow[current] + flowPerCell;
        }

        return flow;
    }

    // ---------------------------------------------------------------------------------------------
    // Drainage direction
    // ---------------------------------------------------------------------------------------------

    /**
     * Compute a D8 steepest-descent drainage direction for every cell. For each cell the eight
     * in-bounds neighbours are examined; the cell drains toward the neighbour giving the most negative
     * slope (steepest downhill), with diagonal slopes scaled by {@code 1/√2} so distance is accounted
     * for. Elevations are weight-normalized first ({@code elev/weight}, guarding tiny weights),
     * matching the decoder's blend-weight convention. A cell with no downhill neighbour stores {@code
     * 0} (a sink).
     *
     * @param elev raw (un-normalized) elevation per cell.
     * @param weight per-cell blend weight; {@code elev} is divided by this (0 when weight ≈ 0).
     * @param gridSize side length of the square grid.
     * @return per-cell one-hot D8 drainage bitfield (bit {@code i} → neighbour {@code i}).
     */
    public static int[] computeDrainageDirection(final float[] elev, final float[] weight, final int gridSize) {
        return steepestDescent(elev, weight, gridSize, FIRST_DIRECTION_D8);
    }

    /**
     * Cardinal-only ("D4") steepest-descent drainage: identical to {@link #computeDrainageDirection}
     * but each cell may only drain toward one of its four <b>cardinal</b> neighbours (offset indices
     * 4..7); the diagonals (0..3) are never considered, so no {@code 1/√2} distance scaling applies.
     * The output uses the same one-hot bitfield convention, but only bits 4..7 can ever be set. Used by
     * {@code GlobalRiverProvider} so every river cell has a single, edge-aligned exit direction.
     *
     * @param elev raw (un-normalized) elevation per cell.
     * @param weight per-cell blend weight; {@code elev} is divided by this (0 when weight ≈ 0).
     * @param gridSize side length of the square grid.
     * @return per-cell one-hot D4 drainage bitfield (only bits 4..7 set; 0 for a sink).
     */
    public static int[] computeDrainageDirectionCardinal(final float[] elev, final float[] weight, final int gridSize) {
        return steepestDescent(elev, weight, gridSize, FIRST_DIRECTION_CARDINAL);
    }

    /**
     * Steepest-descent drainage over the offset indices {@code firstDirection..7}. Diagonal candidates
     * (index &lt; 4) have their slope divided by {@code √2} to convert it to a per-unit-distance slope;
     * starting at {@link #FIRST_DIRECTION_CARDINAL} therefore skips both the diagonals and, with them,
     * any distance scaling.
     */
    private static int[] steepestDescent(
            final float[] elev, final float[] weight, final int gridSize, final int firstDirection) {
        final int[] drainageDirection = new int[gridSize * gridSize];
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                final int cellIndex = x * gridSize + z;
                final float cellElevation = normalized(elev, weight, cellIndex);
                float steepestSlope = 0;
                int steepestDirection = 0;
                for (int i = firstDirection; i < 8; i++) {
                    if ((x == 0 && NEIGHBOR_OFFSET_X[i] == -1) || (z == 0 && NEIGHBOR_OFFSET_Z[i] == -1)) continue;
                    if ((x == (gridSize - 1) && NEIGHBOR_OFFSET_X[i] == 1)
                            || (z == (gridSize - 1) && NEIGHBOR_OFFSET_Z[i] == 1)) continue;
                    final int neighborIndex = gridSize * (x + NEIGHBOR_OFFSET_X[i]) + z + NEIGHBOR_OFFSET_Z[i];
                    final float neighborElevation = normalized(elev, weight, neighborIndex);
                    final float slopeToNeighbor = (neighborElevation - cellElevation) / ((i < 4) ? 1.4142f : 1f);
                    if (slopeToNeighbor >= steepestSlope) continue;
                    steepestSlope = slopeToNeighbor;
                    steepestDirection = 1 << i;
                }
                drainageDirection[cellIndex] = steepestDirection;
            }
        }
        return drainageDirection;
    }

    /** Weight-normalized elevation at {@code cellIndex}, or {@code 0} where the weight is negligible. */
    private static float normalized(final float[] elev, final float[] weight, final int cellIndex) {
        return (weight[cellIndex] > 1e-6f) ? (elev[cellIndex] / weight[cellIndex]) : 0f;
    }

    // ---------------------------------------------------------------------------------------------
    // Depression filling
    // ---------------------------------------------------------------------------------------------

    /** Increment added at each flood step so filled depressions still descend toward the outlet. */
    private static final float FILL_EPSILON = 1e-4f;

    /**
     * Depression-fill (remove local minima from) {@code elevation} so every interior cell has a
     * downhill path to the grid border, using <b>Priority-Flood + ε</b> (Barnes, Lehman &amp; Mulla
     * 2014). Each cell is processed exactly once: a min-priority queue floods inward from the border,
     * and a cell entered from spill level {@code L} is raised to {@code max(elevation, L + ε)} — pits
     * fill to their spill level (plus a slight ε gradient so flats still drain) while hillside cells
     * keep their own elevation. {@code O(n log n)} time, {@code O(n)} memory.
     *
     * @param elevation per-cell elevation, indexed {@code x * gridSize + z}; not mutated.
     * @param gridSize side length of the square grid.
     * @param padding accepted for call-site symmetry with the other grid routines; unused.
     * @return a new array: the depression-filled elevation.
     */
    public static float[] fillSinks(final float[] elevation, final int gridSize, final int padding) {
        final int cellCount = gridSize * gridSize;
        final float[] filled = new float[cellCount];
        final BitSet closed = new BitSet(cellCount);
        final long[] heap = new long[Math.max(16, cellCount)];
        int heapSize = 0;

        // Seed: every border cell at its own elevation.
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                if (x != 0 && z != 0 && x != gridSize - 1 && z != gridSize - 1) continue;
                final int cell = x * gridSize + z;
                filled[cell] = elevation[cell];
                closed.set(cell);
                heapSize = heapPush(heap, heapSize, packEntry(filled[cell], cell));
            }
        }

        // Flood inward from the lowest spill level.
        while (heapSize > 0) {
            final long top = heap[0];
            heapSize = heapPop(heap, heapSize);
            final int cell = (int) (top & 0xffffffffL);
            final int x = cell / gridSize;
            final int z = cell % gridSize;
            for (int i = 0; i < 8; i++) {
                final int nx = x + NEIGHBOR_OFFSET_X[i];
                final int nz = z + NEIGHBOR_OFFSET_Z[i];
                if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) continue;
                final int n = nx * gridSize + nz;
                if (closed.get(n)) continue;
                closed.set(n);
                filled[n] = Math.max(elevation[n], filled[cell] + FILL_EPSILON);
                heapSize = heapPush(heap, heapSize, packEntry(filled[n], n));
            }
        }

        return filled;
    }

    /**
     * Pack a flood entry into one {@code long}: an order-preserving 32-bit key of {@code level} in the
     * high bits, {@code cell} index in the low bits. Comparing entries with {@link
     * Long#compareUnsigned} therefore orders by elevation first, index second.
     */
    private static long packEntry(final float level, final int cell) {
        int bits = Float.floatToIntBits(level);
        bits ^= (bits >> 31) | Integer.MIN_VALUE; // negatives → flip all; positives → flip sign
        return ((bits & 0xffffffffL) << 32) | (cell & 0xffffffffL);
    }

    /** Sift {@code entry} into the binary min-heap; returns the new size. */
    private static int heapPush(final long[] heap, final int size, final long entry) {
        int child = size;
        heap[child] = entry;
        while (child > 0) {
            final int parent = (child - 1) >>> 1;
            if (Long.compareUnsigned(heap[parent], heap[child]) <= 0) break;
            final long tmp = heap[parent];
            heap[parent] = heap[child];
            heap[child] = tmp;
            child = parent;
        }
        return size + 1;
    }

    /** Remove the min (root) from the binary min-heap; returns the new size. */
    private static int heapPop(final long[] heap, final int size) {
        final int newSize = size - 1;
        heap[0] = heap[newSize];
        int parent = 0;
        while (true) {
            final int left = 2 * parent + 1;
            final int right = left + 1;
            int smallest = parent;
            if (left < newSize && Long.compareUnsigned(heap[left], heap[smallest]) < 0) smallest = left;
            if (right < newSize && Long.compareUnsigned(heap[right], heap[smallest]) < 0) smallest = right;
            if (smallest == parent) break;
            final long tmp = heap[parent];
            heap[parent] = heap[smallest];
            heap[smallest] = tmp;
            parent = smallest;
        }
        return newSize;
    }
}
