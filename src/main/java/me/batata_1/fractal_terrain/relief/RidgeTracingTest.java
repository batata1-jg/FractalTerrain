package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.Skeletonizer;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.math.DifferenceOfGaussians;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestOnly
public class RidgeTracingTest {

    private static final Logger LOG = LoggerFactory.getLogger(RidgeTracingTest.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/ridge_test";
    private static final String COARSE_TENSOR_DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/coarse";

    private static final double SIGMA1 = 0.7;
    private static final double SIGMA2 = 1.5;
    private static final double THRESHOLD = 0.5;
    private static final double FREQUENCY = 5.0;

    private static final int WINDOW_COARSE_PX = DifferenceOfGaussians.COARSE_TILE_SIZE;
    private static final int WINDOW_ORIGIN_CX = 0;
    private static final int WINDOW_ORIGIN_CZ = 0;
    private static final int PIXEL_SCALE = 8;
    private static final int IMG_PX = WINDOW_COARSE_PX * PIXEL_SCALE;

    public static void main(String[] args) throws Exception {
        Debug.DEBUG_LOGGER.info("RidgeTracingTest start; output dir = {}", DEBUG_PATH);
        cleanDir(DEBUG_PATH);
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        pipeline.updateInstance(0, DEBUG_PATH);
        final DifferenceOfGaussians dog =
                new DifferenceOfGaussians(DEBUG_PATH + "/dog_cache", SIGMA1, SIGMA2, THRESHOLD);
        final GlobalFillRocksPredicate predicate = new GlobalFillRocksPredicate(dog, FREQUENCY);

        //  dumpDogHeatmap(dog, "ridge_dog");
        //  dumpMask(dog, "ridge_mask");
        //   dumpSkeletonAndPolylines(dog, predicate, "ridge_skeleton", "ridge_splines_all");

        dumpSingleSampleQuery(dog, "ridge_query_single_point");

        // seedQuadTreeForWindow(predicate);
        //  dumpQueryField(predicate, "ridge_query");
        //  dumpCoarseElevTiles();

        LOG.info("RidgeTracingTest done. See {}", DEBUG_PATH);
    }

    private static void dumpCoarseElevTiles() {
        final File dir = new File(COARSE_TENSOR_DEBUG_PATH);
        dir.mkdirs();
        final int S = DifferenceOfGaussians.COARSE_TILE_SIZE;
        final Set<TileKey> keys = pipeline.getCoarse().getStorage().getCacheKeys();
        LOG.info("dumpCoarseElevTiles: {} cached DoG tile(s) → {}", keys.size(), COARSE_TENSOR_DEBUG_PATH);
        for (TileKey key : keys) {
            final int tx = key.get(FractalTerrainConfig.X);
            final int tz = key.get(FractalTerrainConfig.Z);
            final float[] elev = new float[S * S];
            final FloatTensor rawTl = pipeline.getCoarse().getStorage().getEntry(key);
            for (int i = 0; i < S; i++) {
                for (int j = 0; j < S; j++) {
                    final double w = rawTl.entryAt(new int[] {6, i, j});
                    final double et = w <= 1e-6 ? 0 : rawTl.entryAt(new int[] {0, i, j}) / w;
                    final double e = Math.max(0.0, et);
                    elev[i * S + j] = (float) (e);
                }
            }
            final FloatTensor tile = new FloatTensor(new int[] {S, S}, elev);
            final String name = "coarse_tile_tx" + tx + "_tz" + tz;
            Debug.tensor.see(tile, name, COARSE_TENSOR_DEBUG_PATH);
        }
    }

    private static void cleanDir(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (c.isFile()) c.delete();
                    else cleanDir(c.getAbsolutePath());
                }
            }
        }
        dir.mkdirs();
    }

    private static void splatCoarsePx(float[] data, int dcx, int dcz, float v) {
        final int x0 = dcx * PIXEL_SCALE;
        final int z0 = dcz * PIXEL_SCALE;
        for (int u = 0; u < PIXEL_SCALE; u++) {
            for (int w = 0; w < PIXEL_SCALE; w++) {
                data[(x0 + u) * IMG_PX + (z0 + w)] = v;
            }
        }
    }

    private static void dumpDogHeatmap(DifferenceOfGaussians dog, String name) {
        final float[] data = new float[IMG_PX * IMG_PX];
        for (int dcx = 0; dcx < WINDOW_COARSE_PX; dcx++) {
            for (int dcz = 0; dcz < WINDOW_COARSE_PX; dcz++) {
                final float v =
                        dog.getInfiniteTensor().getValue(new int[] {0, WINDOW_ORIGIN_CX + dcx, WINDOW_ORIGIN_CZ + dcz});
                splatCoarsePx(data, dcx, dcz, v);
            }
        }
        Debug.tensor.see(new FloatTensor(new int[] {IMG_PX, IMG_PX}, data), name, DEBUG_PATH);
    }

    private static void dumpMask(DifferenceOfGaussians dog, String name) {
        final float[] data = new float[IMG_PX * IMG_PX];
        for (int dcx = 0; dcx < WINDOW_COARSE_PX; dcx++) {
            for (int dcz = 0; dcz < WINDOW_COARSE_PX; dcz++) {
                final float v = dog.getThresholded(WINDOW_ORIGIN_CX + dcx, WINDOW_ORIGIN_CZ + dcz) ? 1f : 0f;
                splatCoarsePx(data, dcx, dcz, v);
            }
        }
        Debug.tensor.see(new FloatTensor(new int[] {IMG_PX, IMG_PX}, data), name, DEBUG_PATH);
    }

    private static void dumpSkeletonAndPolylines(
            DifferenceOfGaussians dog, GlobalFillRocksPredicate predicate, String skelName, String overlayName)
            throws IOException {
        final int Sc = DifferenceOfGaussians.COARSE_TILE_SIZE;
        final int txMin = Math.floorDiv(WINDOW_ORIGIN_CX, Sc);
        final int txMax = Math.floorDiv(WINDOW_ORIGIN_CX + WINDOW_COARSE_PX - 1, Sc);
        final int tzMin = Math.floorDiv(WINDOW_ORIGIN_CZ, Sc);
        final int tzMax = Math.floorDiv(WINDOW_ORIGIN_CZ + WINDOW_COARSE_PX - 1, Sc);

        final float[] skelGrid = new float[IMG_PX * IMG_PX];
        final QuadTree<QuadTreePoint> overlayTree = new QuadTree<>(new double[] {-1e9, -1e9}, new double[] {1e9, 1e9});

        int splineCounter = 0;
        for (int tx = txMin; tx <= txMax; tx++) {
            for (int tz = tzMin; tz <= tzMax; tz++) {
                final boolean[][] mask = new boolean[Sc][Sc];
                for (int di = 0; di < Sc; di++) {
                    for (int dj = 0; dj < Sc; dj++) {
                        mask[di][dj] = dog.getThresholded(tx * Sc + di, tz * Sc + dj);
                    }
                }
                final boolean[][] skel = Skeletonizer.zhangSuen(mask);

                for (int di = 0; di < Sc; di++) {
                    for (int dj = 0; dj < Sc; dj++) {
                        if (!skel[di][dj]) continue;
                        final int dcx = (tx * Sc + di) - WINDOW_ORIGIN_CX;
                        final int dcz = (tz * Sc + dj) - WINDOW_ORIGIN_CZ;
                        if (dcx < 0 || dcx >= WINDOW_COARSE_PX || dcz < 0 || dcz >= WINDOW_COARSE_PX) continue;
                        splatCoarsePx(skelGrid, dcx, dcz, 1f);
                    }
                }

                final List<List<int[]>> polylines = Skeletonizer.tracePolylines(skel);
                for (List<int[]> line : polylines) {
                    if (line.size() < GlobalFillRocksPredicate.MIN_POLYLINE_LEN) continue;
                    final ArrayList<double[]> globalPts = new ArrayList<>(line.size());
                    for (int[] p : line) {
                        globalPts.add(new double[] {tx * Sc + p[0], tz * Sc + p[1]});
                    }
                    try {
                        final QuinticHermiteSpline spline = QuinticHermiteSpline.createCatmullRom(globalPts);
                        final QuinticHermiteSpline resampled = spline.reSample(GlobalFillRocksPredicate.DX_COARSE_PX);

                        final ArrayList<double[]> scaled =
                                new ArrayList<>(resampled.points().size());
                        for (double[] pt : resampled.points()) {
                            scaled.add(new double[] {
                                (pt[0] - WINDOW_ORIGIN_CX) * PIXEL_SCALE, (pt[1] - WINDOW_ORIGIN_CZ) * PIXEL_SCALE
                            });
                        }
                        if (scaled.size() >= GlobalFillRocksPredicate.MIN_POLYLINE_LEN) {
                            final QuinticHermiteSpline scaledSpline = QuinticHermiteSpline.createCatmullRom(scaled);
                            Debug.spline.debugPath = DEBUG_PATH;
                            Debug.spline.see(scaledSpline, "ridge_spline_" + splineCounter);
                            splineCounter++;
                        }

                        for (double[] pt : resampled.points()) {
                            final double rx = pt[0] - WINDOW_ORIGIN_CX;
                            final double rz = pt[1] - WINDOW_ORIGIN_CZ;
                            if (rx >= 0 && rx < WINDOW_COARSE_PX && rz >= 0 && rz < WINDOW_COARSE_PX) {
                                overlayTree.insertPoint(new QuadTreePoint(new double[] {rx, rz}));
                            }
                        }
                    } catch (RuntimeException e) {
                        LOG.warn("skipped spline at tile ({},{}): {}", tx, tz, e.getMessage());
                    }
                }
            }
        }

        Debug.tensor.see(new FloatTensor(new int[] {IMG_PX, IMG_PX}, skelGrid), skelName, DEBUG_PATH);
        writeMaskWithSplineOverlay(dog, overlayTree, overlayName);
    }

    private static void writeMaskWithSplineOverlay(
            DifferenceOfGaussians dog, QuadTree<QuadTreePoint> overlayTree, String name) throws IOException {
        final int[] red = new int[IMG_PX * IMG_PX];
        final int[] green = new int[IMG_PX * IMG_PX];
        final int[] blue = new int[IMG_PX * IMG_PX];
        final double detectDist = 0.6;

        for (int dcx = 0; dcx < WINDOW_COARSE_PX; dcx++) {
            for (int dcz = 0; dcz < WINDOW_COARSE_PX; dcz++) {
                final boolean mask = dog.getThresholded(WINDOW_ORIGIN_CX + dcx, WINDOW_ORIGIN_CZ + dcz);
                final List<double[]> hits = overlayTree.getPointCoordsInBox(
                        new double[] {dcx - detectDist, dcz - detectDist},
                        new double[] {dcx + detectDist, dcz + detectDist});
                final int r = mask ? 120 : 0;
                final int g = hits.isEmpty() ? 0 : 255;
                final int x0 = dcx * PIXEL_SCALE;
                final int z0 = dcz * PIXEL_SCALE;
                for (int u = 0; u < PIXEL_SCALE; u++) {
                    for (int w = 0; w < PIXEL_SCALE; w++) {
                        final int idx = (x0 + u) * IMG_PX + (z0 + w);
                        red[idx] = r;
                        green[idx] = g;
                    }
                }
            }
        }
        BufferedImage img = new BufferedImage(IMG_PX, IMG_PX, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = img.getRaster();
        raster.setSamples(0, 0, IMG_PX, IMG_PX, 0, red);
        raster.setSamples(0, 0, IMG_PX, IMG_PX, 1, green);
        raster.setSamples(0, 0, IMG_PX, IMG_PX, 2, blue);
        File dir = new File(DEBUG_PATH);
        dir.mkdirs();
        File outputFile = new File(dir, name + ".png");
        ImageIO.write(img, "png", outputFile);
        LOG.info("wrote overlay to {}", outputFile.getPath());
    }

    private static void seedQuadTreeForWindow(GlobalFillRocksPredicate predicate) {
        final int Sc = DifferenceOfGaussians.COARSE_TILE_SIZE;
        final int radius = (int) Math.ceil(GlobalFillRocksPredicate.QUERY_RADIUS_COARSE_PX);
        final int tx0 = Math.floorDiv(WINDOW_ORIGIN_CX - radius, Sc);
        final int tx1 = Math.floorDiv(WINDOW_ORIGIN_CX + WINDOW_COARSE_PX + radius, Sc);
        final int tz0 = Math.floorDiv(WINDOW_ORIGIN_CZ - radius, Sc);
        final int tz1 = Math.floorDiv(WINDOW_ORIGIN_CZ + WINDOW_COARSE_PX + radius, Sc);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int tz = tz0; tz <= tz1; tz++) {
                predicate.placeRidgesTile(tx, tz);
            }
        }
    }

    /** Smoke test for {@link GlobalFillRocksPredicate#query}: seed a fresh predicate with one
     *  spline point at coarse-px (0, 0) and dump the resulting field. Expected: a disc of
     *  radius QUERY_RADIUS_COARSE_PX around the origin showing sin(freq * atan2(cx, cz));
     *  zeros outside that disc. */
    private static void dumpSingleSampleQuery(DifferenceOfGaussians dog, String name) {
        final GlobalFillRocksPredicate freshPredicate = new GlobalFillRocksPredicate(dog, FREQUENCY);
        freshPredicate.insertPoint(0.0, 0.0);
        freshPredicate.insertPoint(32.0, 32.0);
        dumpQueryField(freshPredicate, name);
    }

    private static void dumpQueryField(GlobalFillRocksPredicate predicate, String name) {
        final float[] data = new float[IMG_PX * IMG_PX];
        for (int dcx = 0; dcx < WINDOW_COARSE_PX; dcx++) {
            for (int dcz = 0; dcz < WINDOW_COARSE_PX; dcz++) {
                final float v = (float) predicate.query(
                        (float) ((WINDOW_ORIGIN_CX + dcx) * 1280), (float) ((WINDOW_ORIGIN_CZ + dcz) * 1280));
                splatCoarsePx(data, dcx, dcz, v);
            }
        }
        Debug.tensor.see(new FloatTensor(new int[] {IMG_PX, IMG_PX}, data), name, DEBUG_PATH);
    }
}
