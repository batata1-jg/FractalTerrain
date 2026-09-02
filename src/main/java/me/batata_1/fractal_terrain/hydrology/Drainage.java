package me.batata_1.fractal_terrain.hydrology;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Flow-routing math shared by the global and local river providers: drainage direction, flow
 * accumulation and depression filling.
 *
 * <p>Lives apart from either provider because both need the same routing and must agree on it —
 * the global network traces over it and the local network derives its whole channel set from it.
 *
 * <p>Directions are one-hot D8 bitfields over a row-major square grid; offsets 0..3 are the diagonals
 * and 4..7 the cardinals, an ordering the D4 variants depend on.
 */
public class Drainage {

    /** D8 neighbour offsets. Indices 0..3 are diagonal (√2 away); 4..7 are cardinal (1 away). */
    public static final int[] NEIGHBOR_OFFSET_X = {-1, 1, -1, 1, 0, 0, -1, 1};

    public static final int[] NEIGHBOR_OFFSET_Z = {1, 1, -1, -1, -1, 1, 0, 0};

    /** Turns an outgoing direction into the ingoing one, so upstream can be derived, never stored. */
    public static final int[] OPPOSITE_DIRECTION = {3, 2, 1, 0, 5, 4, 7, 6};

    /** First offset index considered by a full D8 search — the diagonals are in play. */
    private static final int FIRST_DIRECTION_D8 = 0;

    /** First offset index considered by a cardinal-only search — skips the four diagonals. */
    private static final int FIRST_DIRECTION_CARDINAL = 4;

    // ---------------------------------------------------------------------------------------------
    // Direction bitfield decoding
    // ---------------------------------------------------------------------------------------------

    /** Decodes a drainage bitfield to a neighbour index, or {@code -1} for a sink. Ignores bits ≥ 8 so
     *  callers packing extra flags above the direction bits can decode in place. */
    public static int directionOf(int drainageDirection) {
        for (int i = 0; i < 8; i++) if ((drainageDirection & (1 << i)) != 0) return i;
        return -1;
    }

    /** Neighbour index, or {@code -1} when it falls off the grid. Off-grid is not an error: a cropped
     *  padded field points outward at its border, and flow legitimately leaves the tile there. */
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
            if (drainageDirection.length != cellCount)
                throw new IllegalArgumentException("drainageDirection arrays must have the same length");
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

        /** The headwater frontier a topological walk starts from. Queue and in-degrees are caller-owned
         *  scratch consumed by the walk, so call this once per traversal. */
        public IntArrayFIFOQueue sources() {
            final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
            for (int cellIndex = 0; cellIndex < inDegree.length; cellIndex++) {
                if (inDegree[cellIndex] == 0) queue.enqueue(cellIndex);
            }
            return queue;
        }
    }

    /** Flow accumulation, the quantity every channel width is ultimately derived from.
     *  {@code initialFlow} scales flow with catchment area and {@code flowPerCell} with channel length;
     *  they are separate knobs so river size can be tuned along either axis. */
    public static float[] computeFlow(
            final int[] drainageDirection, final int gridSize, final float initialFlow, final float flowPerCell) {
        final FlowGraph graph = FlowGraph.of(drainageDirection, gridSize);
        final int[] downstream = graph.downstream();
        final int[] inDegree = graph.inDegree();

        final float[] flow = new float[gridSize * gridSize];
        Arrays.fill(flow, initialFlow);

        final IntArrayFIFOQueue frontier = graph.sources();
        while (!frontier.isEmpty()) {
            final int current = frontier.dequeueInt();
            final int next = downstream[current];
            if (next == -1) continue;
            if ((--inDegree[next]) == 0) frontier.enqueue(next);
            flow[next] += flow[current] + flowPerCell;
        }

        return flow;
    }

    // ---------------------------------------------------------------------------------------------
    // Drainage direction
    // ---------------------------------------------------------------------------------------------

    /** The default routing, used by the local network where diagonal channels are wanted. */
    public static int[] computeDrainageDirection(final float[] elev, final int gridSize) {
        return steepestDescent(elev, gridSize, FIRST_DIRECTION_D8);
    }

    /** Cardinal-only routing for {@code GlobalRiverProvider}, which needs every river cell to have a
     *  single edge-aligned exit so arrows stay consistent across tile borders. */
    public static int[] computeDrainageDirectionCardinal(final float[] elev, final int gridSize) {
        return steepestDescent(elev, gridSize, FIRST_DIRECTION_CARDINAL);
    }

    /** Shared body of the D8 and D4 variants; the start index is what selects between them. */
    private static int[] steepestDescent(final float[] elev, final int gridSize, final int firstDirection) {
        final int[] drainageDirection = new int[gridSize * gridSize];
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                final int cellIndex = x * gridSize + z;
                final float cellElevation = elev[cellIndex];
                float steepestSlope = 0;
                int steepestDirection = 0;
                for (int i = firstDirection; i < 8; i++) {
                    if ((x == 0 && NEIGHBOR_OFFSET_X[i] == -1) || (z == 0 && NEIGHBOR_OFFSET_Z[i] == -1)) continue;
                    if ((x == (gridSize - 1) && NEIGHBOR_OFFSET_X[i] == 1)
                            || (z == (gridSize - 1) && NEIGHBOR_OFFSET_Z[i] == 1)) continue;
                    final int neighborIndex = gridSize * (x + NEIGHBOR_OFFSET_X[i]) + z + NEIGHBOR_OFFSET_Z[i];
                    final float neighborElevation = elev[neighborIndex];
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

    // ---------------------------------------------------------------------------------------------
    // Depression filling
    // ---------------------------------------------------------------------------------------------

    /** Increment added at each flood step so filled depressions still descend toward the outlet. */
    private static final float FILL_EPSILON = 1e-4f;

    /** Removes local minima so every interior cell has a downhill path out, which is what lets the
     *  steepest-descent walks terminate. Priority-Flood + ε (Barnes, Lehman & Mulla 2014).
     *  {@code padding} is unused, kept for call-site symmetry. */
    public static float[] fillSinks(final float[] elevation, final int gridSize, final int padding) {
        final int cellCount = gridSize * gridSize;
        final float[] filled = new float[cellCount];
        final BitSet closed = new BitSet(cellCount);
        final long[] heap = new long[Math.max(16, cellCount)];
        int heapSize = 0;

        // Seed: every border cell at its own elevation.
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                final boolean isBorder = x == 0 || z == 0 || x == gridSize - 1 || z == gridSize - 1;
                if (!isBorder) continue;
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

    /** Packs level and cell into one long so the flood queue can hold primitives, and so ties break on
     *  cell index rather than on queue order. */
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
