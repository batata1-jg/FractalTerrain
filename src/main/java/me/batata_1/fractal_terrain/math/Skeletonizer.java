package me.batata_1.fractal_terrain.math;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

/**
 * Turns a binary mask into a list of smooth splines tracing its 1-pixel skeleton.
 *
 * <p>The pipeline is: {@link #zhangSuen} thinning → {@link #tracePolylines} (endpoint/junction
 * seeded walks plus closed loops) → Catmull-Rom fit + arc-length resampling. This is the
 * canonical home for the logic that used to live inside
 * {@code relief.GlobalFillRocksPredicate}; that class now delegates here.
 *
 * <p>Output spline points are in the SAME coordinate frame as the input mask (mask-relative,
 * {@code (row, col)}); callers add any tile/pad origin themselves.
 */
public class Skeletonizer {

    /** 8-neighbourhood offsets (clockwise), shared by the thinning and tracing passes. */
    private static final int[] NEIGH_DI = {-1, -1, 0, 1, 1, 1, 0, -1};

    private static final int[] NEIGH_DJ = {0, 1, 1, 1, 0, -1, -1, -1};

    private final int minPolylineLength;
    private final double resampleSpacing;

    /**
     * @param minPolylineLength polylines with fewer points than this are discarded before fitting.
     * @param resampleSpacing arc-length spacing used to resample each fitted spline.
     */
    public Skeletonizer(int minPolylineLength, double resampleSpacing) {
        this.minPolylineLength = minPolylineLength;
        this.resampleSpacing = resampleSpacing;
    }

    /**
     * Thin {@code mask}, trace its skeleton into polylines, and fit + resample each long-enough
     * polyline into a {@link QuinticHermiteSpline}. Degenerate splines (e.g. runaway resampling)
     * are skipped. Points are mask-relative.
     */
    public List<QuinticHermiteSpline> trace(boolean[][] mask) {
        final boolean[][] skeleton = zhangSuen(mask);
        final List<List<int[]>> polylines = tracePolylines(skeleton);
        final List<QuinticHermiteSpline> splines = new ArrayList<>();
        for (List<int[]> polyline : polylines) {
            if (polyline.size() < minPolylineLength) continue;
            final ArrayList<double[]> polylinePoints = new ArrayList<>(polyline.size());
            for (int[] pixel : polyline) {
                polylinePoints.add(new double[] {pixel[0], pixel[1]});
            }
            try {
                final QuinticHermiteSpline fitted = QuinticHermiteSpline.createCatmullRom(polylinePoints);
                splines.add(fitted.reSample(resampleSpacing));
            } catch (RuntimeException degenerate) {
                // Degenerate spline (e.g. MAX_SPLINE_LENGTH exceeded). Skip this polyline.
            }
        }
        return splines;
    }

    // -------------------------------------------------------------------------
    // Zhang–Suen thinning
    // -------------------------------------------------------------------------

    /** Zhang–Suen thinning to a 1-pixel-wide skeleton. Returns a fresh array; input is untouched. */
    public static boolean[][] zhangSuen(boolean[][] input) {
        final int height = input.length;
        final int width = input[0].length;
        final boolean[][] image = new boolean[height][width];
        for (int row = 0; row < height; row++) System.arraycopy(input[row], 0, image[row], 0, width);

        final List<int[]> toRemove = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            toRemove.clear();
            for (int row = 1; row < height - 1; row++) {
                for (int col = 1; col < width - 1; col++) {
                    if (image[row][col] && shouldRemove(image, row, col, true)) {
                        toRemove.add(new int[] {row, col});
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                for (int[] pixel : toRemove) image[pixel[0]][pixel[1]] = false;
                changed = true;
            }

            toRemove.clear();
            for (int row = 1; row < height - 1; row++) {
                for (int col = 1; col < width - 1; col++) {
                    if (image[row][col] && shouldRemove(image, row, col, false)) {
                        toRemove.add(new int[] {row, col});
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                for (int[] pixel : toRemove) image[pixel[0]][pixel[1]] = false;
                changed = true;
            }
        }
        return image;
    }

    private static boolean shouldRemove(boolean[][] image, int row, int col, boolean firstSubpass) {
        final boolean north = image[row - 1][col];
        final boolean northEast = image[row - 1][col + 1];
        final boolean east = image[row][col + 1];
        final boolean southEast = image[row + 1][col + 1];
        final boolean south = image[row + 1][col];
        final boolean southWest = image[row + 1][col - 1];
        final boolean west = image[row][col - 1];
        final boolean northWest = image[row - 1][col - 1];

        final int liveNeighborCount = (north ? 1 : 0)
                + (northEast ? 1 : 0)
                + (east ? 1 : 0)
                + (southEast ? 1 : 0)
                + (south ? 1 : 0)
                + (southWest ? 1 : 0)
                + (west ? 1 : 0)
                + (northWest ? 1 : 0);
        if (liveNeighborCount < 2 || liveNeighborCount > 6) return false;

        int zeroToOneTransitions = 0;
        if (!north && northEast) zeroToOneTransitions++;
        if (!northEast && east) zeroToOneTransitions++;
        if (!east && southEast) zeroToOneTransitions++;
        if (!southEast && south) zeroToOneTransitions++;
        if (!south && southWest) zeroToOneTransitions++;
        if (!southWest && west) zeroToOneTransitions++;
        if (!west && northWest) zeroToOneTransitions++;
        if (!northWest && north) zeroToOneTransitions++;
        if (zeroToOneTransitions != 1) return false;

        if (firstSubpass) {
            return !(north && east && south) && !(east && south && west);
        } else {
            return !(north && east && west) && !(north && south && west);
        }
    }

    // -------------------------------------------------------------------------
    // Polyline tracing
    // -------------------------------------------------------------------------

    /**
     * Convert a 1-pixel skeleton into polylines. Endpoints/junctions (8-neighbour count != 2) seed
     * walks along degree-2 chains; any remaining unvisited degree-2 pixels form closed loops.
     */
    public static List<List<int[]>> tracePolylines(boolean[][] skeleton) {
        final int height = skeleton.length;
        final int width = skeleton[0].length;
        final int[][] neighborCount = new int[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!skeleton[row][col]) continue;
                int count = 0;
                for (int dir = 0; dir < 8; dir++) {
                    final int nRow = row + NEIGH_DI[dir];
                    final int nCol = col + NEIGH_DJ[dir];
                    if (nRow >= 0 && nRow < height && nCol >= 0 && nCol < width && skeleton[nRow][nCol]) count++;
                }
                neighborCount[row][col] = count;
            }
        }

        final boolean[][] visited = new boolean[height][width];
        final List<List<int[]>> polylines = new ArrayList<>();

        final List<int[]> nodes = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (skeleton[row][col] && (neighborCount[row][col] == 1 || neighborCount[row][col] >= 3)) {
                    nodes.add(new int[] {row, col});
                }
            }
        }

        for (int[] node : nodes) {
            for (int dir = 0; dir < 8; dir++) {
                final int startRow = node[0] + NEIGH_DI[dir];
                final int startCol = node[1] + NEIGH_DJ[dir];
                if (startRow < 0 || startRow >= height || startCol < 0 || startCol >= width) continue;
                if (!skeleton[startRow][startCol] || visited[startRow][startCol]) continue;

                final List<int[]> line = new ArrayList<>();
                line.add(new int[] {node[0], node[1]});
                int curRow = startRow, curCol = startCol;
                while (true) {
                    line.add(new int[] {curRow, curCol});
                    visited[curRow][curCol] = true;
                    if (neighborCount[curRow][curCol] != 2) break;
                    int nextRow = -1, nextCol = -1;
                    for (int dir2 = 0; dir2 < 8; dir2++) {
                        final int tRow = curRow + NEIGH_DI[dir2];
                        final int tCol = curCol + NEIGH_DJ[dir2];
                        if (tRow < 0 || tRow >= height || tCol < 0 || tCol >= width) continue;
                        if (!skeleton[tRow][tCol]) continue;
                        if (visited[tRow][tCol]) continue;
                        if (tRow == node[0] && tCol == node[1]) continue;
                        nextRow = tRow;
                        nextCol = tCol;
                        break;
                    }
                    if (nextRow < 0) break;
                    curRow = nextRow;
                    curCol = nextCol;
                }
                polylines.add(line);
            }
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!skeleton[row][col] || visited[row][col]) continue;
                if (neighborCount[row][col] != 2) continue;
                final List<int[]> loop = new ArrayList<>();
                loop.add(new int[] {row, col});
                visited[row][col] = true;
                int curRow = row, curCol = col;
                while (true) {
                    int nextRow = -1, nextCol = -1;
                    for (int dir = 0; dir < 8; dir++) {
                        final int tRow = curRow + NEIGH_DI[dir];
                        final int tCol = curCol + NEIGH_DJ[dir];
                        if (tRow < 0 || tRow >= height || tCol < 0 || tCol >= width) continue;
                        if (!skeleton[tRow][tCol]) continue;
                        if (visited[tRow][tCol]) continue;
                        nextRow = tRow;
                        nextCol = tCol;
                        break;
                    }
                    if (nextRow < 0) break;
                    loop.add(new int[] {nextRow, nextCol});
                    visited[nextRow][nextCol] = true;
                    curRow = nextRow;
                    curCol = nextCol;
                }
                loop.add(new int[] {row, col});
                polylines.add(loop);
            }
        }
        return polylines;
    }
}
