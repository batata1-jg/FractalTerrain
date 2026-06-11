package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import me.batata_1.fractal_terrain.hydrology.Skeletonizer;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
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

    private final QuadTree<QuadTreePoint> tree;
    private final DifferenceOfGaussians dog;
    private final double frequency;
    private final Skeletonizer skeletonizer = new Skeletonizer(MIN_POLYLINE_LEN, DX_COARSE_PX);
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

        final var splines = skeletonizer.trace(mask);
        LOG.info("doPlaceTile [{} {}] numSplines = {}", tx, tz, splines.size());
        final List<double[]> allSamples = new ArrayList<>();
        for (var spline : splines) {
            for (double[] pt : spline.points()) {
                allSamples.add(new double[] {cx0 + pt[0], cz0 + pt[1]});
            }
        }

        if (allSamples.isEmpty()) return;
        synchronized (treeLock) {
            for (double[] pt : allSamples) {
                tree.insertPoint(new QuadTreePoint(pt));
            }
        }
    }
}
