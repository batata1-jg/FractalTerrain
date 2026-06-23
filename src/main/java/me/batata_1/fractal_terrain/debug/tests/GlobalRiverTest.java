package me.batata_1.fractal_terrain.debug.tests;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.io.File;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
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
    private static final int[][] TILES = {{-1, -1}};

    public static void main(String[] args) throws Exception {
        LOG.info("GlobalRiverTest start; output dir = {}", DEBUG_PATH);
        cleanDir(DEBUG_PATH);
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        pipeline.updateInstance(420, DEBUG_PATH);

        final GlobalRiverProvider provider = new GlobalRiverProvider(null);

        for (int[] tile : TILES) {
            dumpTile(provider, tile[0], tile[1]);
        }
        LOG.info("GlobalRiverTest done. See {}", DEBUG_PATH);
    }

    private static void dumpTile(GlobalRiverProvider provider, int tx, int tz) {
        final GlobalRiverProvider.Stages stages = provider.debugStages(tx, tz);
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";
        final int paddedSide = stages.paddedSide;

        seeFloat(stages.rawElevation, paddedSide, paddedSide, prefix + "01_raw_elevation");
        seeFloat(stages.rampedElevation, paddedSide, paddedSide, prefix + "02_ramped_elevation");
        seeFloat(maskToFloat(stages.upperMask), paddedSide, paddedSide, prefix + "03_upper_mask");
        seeFloat(maskToFloat(stages.lowerMask), paddedSide, paddedSide, prefix + "04_lower_mask");
        seeFloat(maskToFloat(stages.ridgeMask), paddedSide, paddedSide, prefix + "05_ridge_mask");
        seeFloat(maskToFloat(stages.coastMask), paddedSide, paddedSide, prefix + "06_coast_mask");
        seeFloat(rasterizePaths(stages.descentPaths, paddedSide), paddedSide, paddedSide, prefix + "07_descent_paths");
        seeFloat(arrowPresence(stages.arrows), paddedSide, paddedSide, prefix + "08_arrows_padded");
        seeFloat(stages.widths, paddedSide, paddedSide, prefix + "09_widths_padded");
        seeFloat(stages.riverElevation, paddedSide, paddedSide, prefix + "09b_river_elev_padded");
        seeFloat(channel(stages.tile, 0), TILE_SIZE, TILE_SIZE, prefix + "10_tile_arrows");
        seeFloat(channel(stages.tile, 1), TILE_SIZE, TILE_SIZE, prefix + "11_tile_widths");
        seeFloat(channel(stages.tile, 2), TILE_SIZE, TILE_SIZE, prefix + "12_tile_river_elev");
    }

    private static final int TILE_SIZE = 64;

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

    /** Rasterize every per-source descent path's pixels onto a {@code side x side} grid. */
    private static float[] rasterizePaths(List<List<int[]>> paths, int side) {
        final float[] grid = new float[side * side];
        if (paths == null) return grid;
        for (List<int[]> path : paths) {
            for (int[] pixel : path) grid[pixel[0] * side + pixel[1]] = 1f;
        }
        return grid;
    }

    /** 1 where a pixel carries any arrow bit, 0 otherwise. */
    private static float[] arrowPresence(int[] arrows) {
        final float[] out = new float[arrows.length];
        for (int i = 0; i < arrows.length; i++) out[i] = GlobalRiverProvider.isRiver(arrows[i]) ? 1f : 0f;
        return out;
    }

    private static float[] coastPresence(int[] arrows) {
        final float[] out = new float[arrows.length];
        for (int i = 0; i < arrows.length; i++) out[i] = GlobalRiverProvider.isCoast(arrows[i]) ? 1f : 0f;
        return out;
    }

    /** Extract a single 64×64 channel from the {@code [3,64,64]} result tile. */
    private static float[] channel(FloatTensor tile, int ch) {
        final int pixels = TILE_SIZE * TILE_SIZE;
        final float[] out = new float[pixels];
        System.arraycopy(tile.data, ch * pixels, out, 0, pixels);
        return out;
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
