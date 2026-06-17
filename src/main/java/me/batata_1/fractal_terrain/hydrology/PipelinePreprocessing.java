package me.batata_1.fractal_terrain.hydrology;

import java.util.ArrayDeque;
import java.util.Arrays;
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
     * Decode a one-hot drainage bitfield into the D8 neighbour index it points at, or {@code -1} if
     * no bit is set (a sink / undrained cell).
     */
    public static int neighbor(float drainageDirection) {
        for (int i = 0; i < 8; i++) if ((((int) drainageDirection) & (1 << i)) != 0) return i;
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
    public static float[] computeFlow(final float[] drainageDirection, final int gridSize) {
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
    public static float[] computeDrainageDirection(final float[] elev, final float[] weight, final int gridSize) {
        final float[] drainageDirection = new float[gridSize * gridSize];
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
}
