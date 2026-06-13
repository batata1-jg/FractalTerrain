package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.io.File;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone smoke test for the full {@link GlobalRiverProvider} pipeline. Loads the models, builds
 * a provider, and for a few tiles dumps a PNG of every intermediate stage under
 * {@code <DEFAULT_DEBUG_PATH>/global_river/}. Run with {@code ./gradlew globalRiverTest}.
 */
@TestOnly
public class GlobalRiverTest {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalRiverTest.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/global_river";

    /** Tiles (tx, tz) to render. */
    private static final int[][] TILES = {{0, 0}};

    public static void main(String[] args) throws Exception {
        LOG.info("GlobalRiverTest start; output dir = {}", DEBUG_PATH);
        cleanDir(DEBUG_PATH);
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        pipeline.updateInstance(0, DEBUG_PATH);

        final GlobalRiverProvider provider = new GlobalRiverProvider(DEBUG_PATH);

        for (int[] tile : TILES) {
            dumpTile(provider, tile[0], tile[1]);
        }
        LOG.info("GlobalRiverTest done. See {}", DEBUG_PATH);
    }

    private static void dumpTile(GlobalRiverProvider provider, int tx, int tz) {
        final GlobalRiverProvider.Stages stages = provider.debugStages(tx, tz);
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";
        final int paddedSide = stages.paddedSide;
        final int fieldW = stages.fieldWidth;
        final int fieldH = stages.fieldHeight;

        seeFloat(stages.paddedElevation, paddedSide, paddedSide, prefix + "01_coarse_padded");
        seeFloat(stages.paddedBlur, paddedSide, paddedSide, prefix + "02_blurred");
        seeFloat(maskToFloat(stages.ridgeMask), paddedSide, paddedSide, prefix + "03_ridge_mask");
        seeFloat(maskToFloat(stages.valleyMask), paddedSide, paddedSide, prefix + "04_valley_mask");
        seeFloat(rasterizeTree(stages.ridgeTree, 0, 0, paddedSide), paddedSide, paddedSide, prefix + "05_ridge_tree");
        seeFloat(rasterizeTree(stages.valleyTree, 0, 0, paddedSide), paddedSide, paddedSide, prefix + "06_valley_tree");
        seeFloat(stages.fieldRaw, fieldW, fieldH, prefix + "07_field_raw");
        seeFloat(stages.fieldNorm, fieldW, fieldH, prefix + "08_field_fwidth");
        seeFloat(maskToFloat(stages.fieldMask), fieldW, fieldH, prefix + "09_field_mask");
        seeFloat(
                rasterizeTree(
                        stages.finalTree,
                        stages.tileOriginCx - stages.pad,
                        stages.tileOriginCz - stages.pad,
                        paddedSide),
                paddedSide,
                paddedSide,
                prefix + "10_final_tree");

        // Per-spline visualizations: confirm valley contours look like region borders and the
        // ridge/field splines are well-formed.
//        seeSplines(stages.ridgeSplines, prefix + "ridge_spline_");
//        seeSplines(stages.valleySplines, prefix + "valley_spline_");
//        seeSplines(stages.fieldSplines, prefix + "field_spline_");
    }

    /** Render each spline to its own PNG via {@link Debug#spline} (auto-bounds, frame-agnostic). */
    private static void seeSplines(List<QuinticHermiteSpline> splines, String prefix) {
        Debug.spline.debugPath = DEBUG_PATH;
        int n = 0;
        for (QuinticHermiteSpline spline : splines) {
            Debug.spline.see(spline, prefix + (n++));
        }
    }

    private static void seeFloat(float[] data, int width, int height, String name) {
        Debug.tensor.see(new FloatTensor(new int[] {height, width}, data), name, DEBUG_PATH);
    }

    private static float[] maskToFloat(boolean[][] mask) {
        final int height = mask.length;
        final int width = mask[0].length;
        final float[] out = new float[height * width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                out[row * width + col] = mask[row][col] ? 1f : 0f;
            }
        }
        return out;
    }

    /** Rasterize a tree's points (global coords) onto a {@code side x side} grid relative to an origin. */
    private static float[] rasterizeTree(QuadTree<QuadTreePoint> tree, int originX, int originZ, int side) {
        final float[] grid = new float[side * side];
        final List<double[]> points = tree.getPointCoordsInBox(
                new double[] {originX, originZ}, new double[] {originX + side, originZ + side});
        for (double[] point : points) {
            final int row = (int) Math.round(point[0] - originX);
            final int col = (int) Math.round(point[1] - originZ);
            if (row >= 0 && row < side && col >= 0 && col < side) {
                grid[row * side + col] = 1f;
            }
        }
        return grid;
    }

    private static void cleanDir(String path) {
        final File dir = new File(path);
        if (dir.exists()) {
            final File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isFile()) child.delete();
                    else cleanDir(child.getAbsolutePath());
                }
            }
        }
        dir.mkdirs();
    }
}
