package me.batata_1.fractal_terrain.hydrology;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Queue;

/**
 * Flow-routing helpers shared by the river providers.
 *
 * <p>Both methods work on a square grid flattened in row-major order as {@code cellIndex = x *
 * gridSize + z}. Drainage uses the <b>D8</b> model: each cell points at exactly one of its eight
 * neighbours (the steepest downhill one). A direction is stored as a one-hot bitfield — bit {@code
 * i} set means "this cell drains toward neighbour {@code i}", where neighbour {@code i} sits at
 * offset {@code (NEIGHBOR_OFFSET_X[i], NEIGHBOR_OFFSET_Z[i])}. The first four offsets (indices 0..3)
 * are the diagonals (distance {@code √2}); the last four (4..7) are the cardinals (distance 1).
 */
public class PipelinePreprocessing {

    private static final Queue<Integer> sourceQueue = new ArrayDeque<>();

    /** D8 neighbour offsets. Indices 0..3 are diagonal (√2 away); 4..7 are cardinal (1 away). */
    public static final int[] NEIGHBOR_OFFSET_X = {-1, 1, -1, 1, 0, 0, -1, 1};

    public static final int[] NEIGHBOR_OFFSET_Z = {1, 1, -1, -1, -1, 1, 0, 0};

    /**
     * For each D8 direction index, the index of the opposite direction (offsets negated). Used to
     * convert an outgoing direction into the ingoing direction recorded on the entered neighbour.
     */
    public static final int[] OPPOSITE_DIRECTION = {3, 2, 1, 0, 5, 4, 7, 6};

    /**
     * Extra drainage-mask bit (above the 8 direction bits 0..7) marking a pixel that belongs to the
     * global river. Set externally (by {@code ReliefProvider}) — {@link #computeDrainageDirection}
     * never sets it. {@link #neighbor} only inspects bits 0..7, so this bit does not affect routing.
     */
    public static final int GLOBAL_RIVER_BIT = 1 << 8;

    /**
     * Decode a one-hot drainage bitfield into the D8 neighbour index it points at, or {@code -1} if
     * no direction bit (0..7) is set (a sink / undrained cell). Bits ≥ 8 (e.g.
     * {@link #GLOBAL_RIVER_BIT}) are ignored.
     */
    public static int neighbor(int drainageDirection) {
        for (int i = 0; i < 8; i++) if ((drainageDirection & (1 << i)) != 0) return i;
        return -1;
    }

    /**
     * Index of {@code cellIndex}'s neighbour in D8 direction {@code direction}, or {@code -1} when
     * that neighbour falls outside the {@code gridSize × gridSize} grid. A drainage field computed on
     * a larger (padded) grid and then cropped can point off the cropped grid at its border; routines
     * that route flow treat such an off-grid target as a sink (flow simply leaves the tile).
     */
    public static int neighborIndex(final int cellIndex, final int direction, final int gridSize) {
        final int x = cellIndex / gridSize;
        final int z = cellIndex % gridSize;
        final int neighborX = x + NEIGHBOR_OFFSET_X[direction];
        final int neighborZ = z + NEIGHBOR_OFFSET_Z[direction];
        if (neighborX < 0 || neighborZ < 0 || neighborX >= gridSize || neighborZ >= gridSize) return -1;
        return neighborX * gridSize + neighborZ;
    }

    /**
     * Topological flow accumulation over a D8 drainage field. Every cell starts with unit flow;
     * processing cells in upstream-to-downstream (Kahn topological) order, each cell's accumulated
     * flow is pushed to the neighbour it drains into. The result is {@code sqrt}-scaled so the
     * dynamic range stays manageable for downstream width/threshold use.
     *
     * @param drainageDirection one-hot D8 bitfield per cell (see {@link #computeDrainageDirection}).
     * @param gridSize side length of the square grid.
     * @return per-cell sqrt-scaled flow accumulation, indexed {@code x * gridSize + z}.
     */
    public static float[] computeFlow(final int[] drainageDirection, final int gridSize) {
        final int[] inDegree = new int[gridSize * gridSize];
        final float[] flow = new float[gridSize * gridSize];
        Arrays.fill(inDegree, 0);
        Arrays.fill(flow, 1);
        sourceQueue.clear();
        for (int cellIndex = 0; cellIndex < gridSize * gridSize; cellIndex++) {
            final int direction = neighbor(drainageDirection[cellIndex]);
            if (direction == -1) continue;
            final int downstream = neighborIndex(cellIndex, direction, gridSize);
            if (downstream == -1) continue;
            inDegree[downstream]++;
        }
        for (int cellIndex = 0; cellIndex < gridSize * gridSize; cellIndex++) {
            if (inDegree[cellIndex] == 0) sourceQueue.add(cellIndex);
        }
        while (!sourceQueue.isEmpty()) {
            final int current = sourceQueue.poll();
            final int direction = neighbor(drainageDirection[current]);
            if (direction == -1) continue;
            final int downstream = neighborIndex(current, direction, gridSize);
            if (downstream == -1) continue;
            if ((--inDegree[downstream]) == 0) sourceQueue.add(downstream);
            flow[downstream] += flow[current];
        }

        return flow;
    }

    /**
     * Compute a D8 steepest-descent drainage direction for every cell. For each cell the eight
     * in-bounds neighbours are examined; the cell drains toward the neighbour giving the most
     * negative slope (steepest downhill), with diagonal slopes scaled by {@code 1/√2} so distance is
     * accounted for. Elevations are weight-normalized first ({@code elev/weight}, guarding tiny
     * weights), matching the decoder's blend-weight convention. A cell with no downhill neighbour
     * stores {@code 0} (a sink).
     *
     * @param elev raw (un-normalized) elevation per cell.
     * @param weight per-cell blend weight; {@code elev} is divided by this (0 when weight ≈ 0).
     * @param gridSize side length of the square grid.
     * @return per-cell one-hot D8 drainage bitfield (bit {@code i} → neighbour {@code i}).
     */
    public static int[] computeDrainageDirection(final float[] elev, final float[] weight, final int gridSize) {
        final int[] drainageDirection = new int[gridSize * gridSize];
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                final int cellIndex = x * gridSize + z;
                final float cellElevation = (weight[cellIndex] > 1e-6f) ? (elev[cellIndex] / weight[cellIndex]) : 0f;
                float steepestSlope = 0;
                int steepestDirection = 0;
                for (int i = 0; i < 8; i++) {
                    if ((x == 0 && NEIGHBOR_OFFSET_X[i] == -1) || (z == 0 && NEIGHBOR_OFFSET_Z[i] == -1)) continue;
                    if ((x == (gridSize - 1) && NEIGHBOR_OFFSET_X[i] == 1)
                            || (z == (gridSize - 1) && NEIGHBOR_OFFSET_Z[i] == 1)) continue;
                    final int neighborIndex = gridSize * (x + NEIGHBOR_OFFSET_X[i]) + z + NEIGHBOR_OFFSET_Z[i];
                    final float neighborElevation =
                            (weight[neighborIndex] > 1e-6f) ? (elev[neighborIndex] / weight[neighborIndex]) : 0f;
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

    /**
     * Cardinal-only ("D4") steepest-descent drainage: identical to {@link #computeDrainageDirection}
     * but each cell may only drain toward one of its four <b>cardinal</b> neighbours (offset indices
     * 4..7); the diagonals (0..3) are never considered, so no {@code 1/√2} distance scaling is needed.
     * The output is the same one-hot bitfield convention, but only bits 4..7 can ever be set. Used by
     * {@code GlobalRiverProvider} so every river cell has a single, edge-aligned exit direction.
     *
     * @param elev raw (un-normalized) elevation per cell.
     * @param weight per-cell blend weight; {@code elev} is divided by this (0 when weight ≈ 0).
     * @param gridSize side length of the square grid.
     * @return per-cell one-hot D4 drainage bitfield (only bits 4..7 set; 0 for a sink).
     */
    public static int[] computeDrainageDirectionCardinal(final float[] elev, final float[] weight, final int gridSize) {
        final int[] drainageDirection = new int[gridSize * gridSize];
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                final int cellIndex = x * gridSize + z;
                final float cellElevation = (weight[cellIndex] > 1e-6f) ? (elev[cellIndex] / weight[cellIndex]) : 0f;
                float steepestSlope = 0;
                int steepestDirection = 0;
                for (int i = 4; i < 8; i++) {
                    if ((x == 0 && NEIGHBOR_OFFSET_X[i] == -1) || (z == 0 && NEIGHBOR_OFFSET_Z[i] == -1)) continue;
                    if ((x == (gridSize - 1) && NEIGHBOR_OFFSET_X[i] == 1)
                            || (z == (gridSize - 1) && NEIGHBOR_OFFSET_Z[i] == 1)) continue;
                    final int neighborIndex = gridSize * (x + NEIGHBOR_OFFSET_X[i]) + z + NEIGHBOR_OFFSET_Z[i];
                    final float neighborElevation =
                            (weight[neighborIndex] > 1e-6f) ? (elev[neighborIndex] / weight[neighborIndex]) : 0f;
                    final float slopeToNeighbor = neighborElevation - cellElevation;
                    if (slopeToNeighbor >= steepestSlope) continue;
                    steepestSlope = slopeToNeighbor;
                    steepestDirection = 1 << i;
                }
                drainageDirection[cellIndex] = steepestDirection;
            }
        }
        return drainageDirection;
    }

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
     * <p>The filled field is then blended back toward the input near the border so neighbouring tiles
     * stay seam-consistent: a cell {@code padding} px or more from the nearest border is fully filled,
     * a border cell keeps its original value, with a linear ramp in between. {@code padding <= 0}
     * returns the fully filled field.
     *
     * @param elevation per-cell elevation, indexed {@code x * gridSize + z}; not mutated.
     * @param gridSize side length of the square grid.
     * @param padding width (px) of the border transition band.
     * @return a new array: the (border-blended) depression-filled elevation.
     */
    public static float[] fillSinks(final float[] elevation, final int gridSize, final int padding) {
        final int cellCount = gridSize * gridSize;
        final float[] filled = new float[cellCount];
        final BitSet closed = new BitSet(cellCount);
        final long[] heap = new long[Math.max(16, cellCount)];
        final int[] heapSize = {0};

        // Seed: every border cell at its own elevation.
        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                if (x != 0 && z != 0 && x != gridSize - 1 && z != gridSize - 1) continue;
                final int cell = x * gridSize + z;
                filled[cell] = elevation[cell];
                closed.set(cell);
                heapSize[0] = heapPush(heap, heapSize[0], packEntry(filled[cell], cell));
            }
        }

        // Flood inward from the lowest spill level.
        while (heapSize[0] > 0) {
            final long top = heap[0];
            heapSize[0] = heapPop(heap, heapSize[0]);
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
                heapSize[0] = heapPush(heap, heapSize[0], packEntry(filled[n], n));
            }
        }

        // Border transition blend (in place into filled).
        if (padding > 0) {
            for (int x = 0; x < gridSize; x++) {
                for (int z = 0; z < gridSize; z++) {
                    final int distToBorder = Math.min(Math.min(x, z), Math.min(gridSize - 1 - x, gridSize - 1 - z));
                    if (distToBorder >= padding) continue;
                    final int idx = x * gridSize + z;
                    final float t = distToBorder / (float) padding;
                    filled[idx] = elevation[idx] + t * (filled[idx] - elevation[idx]);
                }
            }
        }
        return filled;
    }

    /**
     * Pack a flood entry into one {@code long}: an order-preserving 32-bit key of {@code level} in the
     * high bits, {@code cell} index in the low bits. Comparing entries with {@link Long#compareUnsigned}
     * therefore orders by elevation first, index second.
     */
    private static long packEntry(final float level, final int cell) {
        int bits = Float.floatToIntBits(level);
        bits ^= (bits >> 31) | Integer.MIN_VALUE; // negatives → flip all; positives → flip sign
        return ((bits & 0xffffffffL) << 32) | (cell & 0xffffffffL);
    }

    // TODO: most likely to break
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
