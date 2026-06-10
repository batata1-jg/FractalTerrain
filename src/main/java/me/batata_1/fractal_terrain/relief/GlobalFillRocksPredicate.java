package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

public class GlobalFillRocksPredicate {

    private static final double INF = 1e9;

    public static final int TILE_COARSE_PX = DifferenceOfGaussians.COARSE_TILE_SIZE;
    public static final int MIN_POLYLINE_LEN = 4;
    private static final Logger LOG = getLogger(GlobalFillRocksPredicate.class);

    public static double DX_COARSE_PX = 4.0;
    public static double QUERY_RADIUS_COARSE_PX = 96.0;

    private static final double BLOCK_TO_COARSE = 256.0 * 5.0;

    private static final int[] NEIGH_DI = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final int[] NEIGH_DJ = {0, 1, 1, 1, 0, -1, -1, -1};

    private final QuadTree<QuadTreePoint> tree;
    private final DifferenceOfGaussians dog;
    private final double frequency;
    private final ConcurrentHashMap<List<Integer>, Boolean> placedTiles = new ConcurrentHashMap<>();
    private final Object treeLock = new Object();

    public GlobalFillRocksPredicate(DifferenceOfGaussians dog, double frequency) {
        this.dog = dog;
        this.frequency = frequency;
        this.tree = new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});
    }

    public void placeRidgesTile(int tx, int tz) {
        final List<Integer> key = List.of(tx, tz);
        placedTiles.computeIfAbsent(key, k -> {
            doPlaceRidgesTile(tx, tz);
            return Boolean.TRUE;
        });
    }

    public void ensureTilesForChunk(int chunkStartX, int chunkStartZ) {
        final double minCx = chunkStartX / BLOCK_TO_COARSE - QUERY_RADIUS_COARSE_PX;
        final double maxCx = (chunkStartX + 15) / BLOCK_TO_COARSE + QUERY_RADIUS_COARSE_PX;
        final double minCz = chunkStartZ / BLOCK_TO_COARSE - QUERY_RADIUS_COARSE_PX;
        final double maxCz = (chunkStartZ + 15) / BLOCK_TO_COARSE + QUERY_RADIUS_COARSE_PX;
        final int tx0 = (int) Math.floor(minCx / TILE_COARSE_PX);
        final int tx1 = (int) Math.floor(maxCx / TILE_COARSE_PX);
        final int tz0 = (int) Math.floor(minCz / TILE_COARSE_PX);
        final int tz1 = (int) Math.floor(maxCz / TILE_COARSE_PX);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                placeRidgesTile(tx, tz);
            }
        }
    }

    /** Insert a raw coarse-px point into the QuadTree. For tests / debugging only —
     *  production paths go through {@link #placeRidgesTile}. */
    @TestOnly
    public void insertPoint(double cx, double cz) {
        synchronized (treeLock) {
            tree.insertPoint(new QuadTreePoint(new double[] {cx, cz}));
        }
    }

    private double distancePenaltyFn(double x, double dist) {
        x = x / Math.PI - 0.5;
        final double flX = x - Math.floor(x);
        final double clX = Math.ceil(x) - x;
        double fl = Math.floor(x);
        if ((int) (fl) % 2 == 0) {
            return Math.PI * (fl - Math.pow(clX, dist) + 1.5);
        }
        return Math.PI * (fl + Math.pow(flX, dist) + 0.5);
    }

    /** x, z are Minecraft block coords. Converts to coarse-px before querying the tree. */
    public double query(float x, float z) {
        final double cx = x / BLOCK_TO_COARSE;
        final double cz = z / BLOCK_TO_COARSE;
        final double[] center = {cx, cz};
        final List<QuadTreePoint> hits;
        hits = tree.getPointsInCircle(center, QUERY_RADIUS_COARSE_PX);
        if (hits.isEmpty()) return 0.0;
        double netAngle = 0;
        for (QuadTreePoint pt : hits) {
            final double xp = pt.get(0);
            final double zp = pt.get(1);
            final double theta = Math.atan2(cx - xp, cz - zp);
            netAngle += theta;
        }
        return Math.sin(netAngle * frequency);
    }

    private void doPlaceRidgesTile(int tx, int tz) {
        final int S = TILE_COARSE_PX;
        final int cx0 = tx * S;
        final int cz0 = tz * S;

        final boolean[][] mask = new boolean[S][S];
        for (int di = 0; di < S; di++) {
            for (int dj = 0; dj < S; dj++) {
                mask[di][dj] = dog.getThresholded(cx0 + di, cz0 + dj);
            }
        }

        final boolean[][] skel = zhangSuen(mask);
        final List<List<int[]>> polylines = tracePolylines(skel);
        LOG.info("doPlaceTile [{} {}] numPolulines = {}", tx, tz, polylines.size());
        final List<double[]> allSamples = new ArrayList<>();
        for (List<int[]> line : polylines) {
            if (line.size() < MIN_POLYLINE_LEN) continue;
            final ArrayList<double[]> globalPts = new ArrayList<>(line.size());
            for (int[] p : line) {
                globalPts.add(new double[] {cx0 + p[0], cz0 + p[1]});
            }
            try {
                final QuinticHermiteSpline spline = QuinticHermiteSpline.createCatmullRom(globalPts);
                final QuinticHermiteSpline resampled = spline.reSample(DX_COARSE_PX);
                allSamples.addAll(resampled.points());
            } catch (RuntimeException e) {
                // Degenerate spline (e.g., MAX_SPLINE_LENGTH exceeded). Skip this polyline.
            }
        }

        if (allSamples.isEmpty()) return;
        synchronized (treeLock) {
            for (double[] pt : allSamples) {
                tree.insertPoint(new QuadTreePoint(pt));
            }
        }
    }

    static boolean[][] zhangSuen(boolean[][] input) {
        final int H = input.length;
        final int W = input[0].length;
        final boolean[][] img = new boolean[H][W];
        for (int i = 0; i < H; i++) System.arraycopy(input[i], 0, img[i], 0, W);

        final List<int[]> toRemove = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            toRemove.clear();
            for (int i = 1; i < H - 1; i++) {
                for (int j = 1; j < W - 1; j++) {
                    if (img[i][j] && shouldRemove(img, i, j, true)) {
                        toRemove.add(new int[] {i, j});
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                for (int[] p : toRemove) img[p[0]][p[1]] = false;
                changed = true;
            }

            toRemove.clear();
            for (int i = 1; i < H - 1; i++) {
                for (int j = 1; j < W - 1; j++) {
                    if (img[i][j] && shouldRemove(img, i, j, false)) {
                        toRemove.add(new int[] {i, j});
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                for (int[] p : toRemove) img[p[0]][p[1]] = false;
                changed = true;
            }
        }
        return img;
    }

    private static boolean shouldRemove(boolean[][] img, int i, int j, boolean subpass1) {
        final boolean north = img[i - 1][j];
        final boolean northEast = img[i - 1][j + 1];
        final boolean east = img[i][j + 1];
        final boolean southEast = img[i + 1][j + 1];
        final boolean south = img[i + 1][j];
        final boolean southWest = img[i + 1][j - 1];
        final boolean west = img[i][j - 1];
        final boolean northWest = img[i - 1][j - 1];

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

        if (subpass1) {
            return !(north && east && south) && !(east && south && west);
        } else {
            return !(north && east && west) && !(north && south && west);
        }
    }

    static List<List<int[]>> tracePolylines(boolean[][] skel) {
        final int H = skel.length;
        final int W = skel[0].length;
        final int[][] n8 = new int[H][W];
        for (int i = 0; i < H; i++) {
            for (int j = 0; j < W; j++) {
                if (!skel[i][j]) continue;
                int cnt = 0;
                for (int k = 0; k < 8; k++) {
                    final int ni = i + NEIGH_DI[k];
                    final int nj = j + NEIGH_DJ[k];
                    if (ni >= 0 && ni < H && nj >= 0 && nj < W && skel[ni][nj]) cnt++;
                }
                n8[i][j] = cnt;
            }
        }

        final boolean[][] visited = new boolean[H][W];
        final List<List<int[]>> polylines = new ArrayList<>();

        final List<int[]> nodes = new ArrayList<>();
        for (int i = 0; i < H; i++) {
            for (int j = 0; j < W; j++) {
                if (skel[i][j] && (n8[i][j] == 1 || n8[i][j] >= 3)) {
                    nodes.add(new int[] {i, j});
                }
            }
        }

        for (int[] node : nodes) {
            for (int k = 0; k < 8; k++) {
                final int ni = node[0] + NEIGH_DI[k];
                final int nj = node[1] + NEIGH_DJ[k];
                if (ni < 0 || ni >= H || nj < 0 || nj >= W) continue;
                if (!skel[ni][nj] || visited[ni][nj]) continue;

                final List<int[]> line = new ArrayList<>();
                line.add(new int[] {node[0], node[1]});
                int ci = ni, cj = nj;
                while (true) {
                    line.add(new int[] {ci, cj});
                    visited[ci][cj] = true;
                    if (n8[ci][cj] != 2) break;
                    int nextI = -1, nextJ = -1;
                    for (int m = 0; m < 8; m++) {
                        final int ti = ci + NEIGH_DI[m];
                        final int tj = cj + NEIGH_DJ[m];
                        if (ti < 0 || ti >= H || tj < 0 || tj >= W) continue;
                        if (!skel[ti][tj]) continue;
                        if (visited[ti][tj]) continue;
                        if (ti == node[0] && tj == node[1]) continue;
                        nextI = ti;
                        nextJ = tj;
                        break;
                    }
                    if (nextI < 0) break;
                    ci = nextI;
                    cj = nextJ;
                }
                polylines.add(line);
            }
        }

        for (int i = 0; i < H; i++) {
            for (int j = 0; j < W; j++) {
                if (!skel[i][j] || visited[i][j]) continue;
                if (n8[i][j] != 2) continue;
                final List<int[]> loop = new ArrayList<>();
                loop.add(new int[] {i, j});
                visited[i][j] = true;
                int ci = i, cj = j;
                while (true) {
                    int nextI = -1, nextJ = -1;
                    for (int m = 0; m < 8; m++) {
                        final int ti = ci + NEIGH_DI[m];
                        final int tj = cj + NEIGH_DJ[m];
                        if (ti < 0 || ti >= H || tj < 0 || tj >= W) continue;
                        if (!skel[ti][tj]) continue;
                        if (visited[ti][tj]) continue;
                        nextI = ti;
                        nextJ = tj;
                        break;
                    }
                    if (nextI < 0) break;
                    loop.add(new int[] {nextI, nextJ});
                    visited[nextI][nextJ] = true;
                    ci = nextI;
                    cj = nextJ;
                }
                loop.add(new int[] {i, j});
                polylines.add(loop);
            }
        }
        return polylines;
    }
}
