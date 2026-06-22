package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.io.File;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.relief.ReliefProvider;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone smoke test for {@link LocalRiverProvider}. Loads the models, builds the relief → local
 * river chain (with the cross-provider references injected directly so the {@code FractalTerrainInstance}
 * singleton is never touched), and for a few tiles dumps PNGs of the flow accumulation, river mask,
 * and traced channels under {@code <DEFAULT_DEBUG_PATH>/local_river/}. Run with
 * {@code ./gradlew localRiverTest}.
 */
@TestOnly
public class LocalRiverTest {

    private static final Logger LOG = LoggerFactory.getLogger(LocalRiverTest.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/local_river";

    private static final int GRID = 512;
    /** ReliefProvider's padded working resolution (GRID + 1-px halo per side). */
    private static final int RELIEF_PADDED = GRID + 2;

    /** Radius (padded px) around each spline sample point that is lit when rasterizing rivers. */
    private static final double RIVER_SPLINE_RADIUS = 10;

    /** Tiles (tx, tz) to render. */
    private static final int[][] TILES =  {{1, -3}};
//            {{1, -3}, {1, -4}, {2, -3}, {2, -4}};

    public static void main(String[] args) throws Exception {
        LOG.info("LocalRiverTest start; output dir = {}", DEBUG_PATH);
        // cleanDir(DEBUG_PATH);
        Meanders.DEBUG_STEPS = true;
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        pipeline.updateInstance(420, DEBUG_PATH);

        final GlobalRiverProvider globalRivers = new GlobalRiverProvider(null);
        final ReliefProvider relief = new ReliefProvider(DEBUG_PATH);
        relief.setGlobalRiverProvider(globalRivers);
        final LocalRiverProvider localRivers = new LocalRiverProvider(null);
        localRivers.setReliefProvider(relief);

        float[] gb = new float[64 * 64];
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                int x = i - 32;
                int y = j - 32;
                gb[i * 64 + j] = GlobalRiverProvider.isCoast(globalRivers.getArrow(x, y)) ? 1 : 0;
            }
        }
        seeFloat(gb, 64, 64, "globalsss");
        for (int[] tile : TILES) {
            dumpTile(localRivers, relief, globalRivers, tile[0], tile[1]);
        }
        LOG.info("LocalRiverTest done. See {}", DEBUG_PATH);
    }

    private static void dumpTile(
            LocalRiverProvider provider, ReliefProvider relief, GlobalRiverProvider globalRivers, int tx, int tz) {
        LOG.info("Dumping {} {} {} is river:", GlobalRiverProvider.isRiver(globalRivers.getArrow(tx, tz)), tx, tz);
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";

        dumpReliefStages(relief, tx, tz, prefix);

        final LocalRiverProvider.Stages stages = provider.debugStages(tx, tz);
        seeFloat(stages.flow, GRID, GRID, prefix + "11_flow");
        seeFloat(maskToFloat(stages.riverMask), GRID, GRID, prefix + "12_river_mask");
        seeFloat(rasterizeChannels(stages.channels, tx, tz), GRID, GRID, prefix + "13_channels");
        LOG.info("tile ({},{}): {} channels", tx, tz, stages.channels.size());
    }

    /** Render the ReliefProvider intermediate stages for this tile (padded 514×514 + cropped result). */
    private static void dumpReliefStages(ReliefProvider relief, int tx, int tz, String prefix) {
        final ReliefProvider.Stages stages = relief.debugStages(tx, tz);
        seeFloat(stages.baseChannels[0], RELIEF_PADDED, RELIEF_PADDED, prefix + "01_relief_base_elev");
        seeFloat(stages.carvedElevation, RELIEF_PADDED, RELIEF_PADDED, prefix + "02_relief_carved_elev");
        if (stages.carveDepthField != null) {
            seeFloat(stages.carveDepthField, RELIEF_PADDED, RELIEF_PADDED, prefix + "03_relief_carve_depth");
        }
        seeFloat(stages.riverData, RELIEF_PADDED, RELIEF_PADDED, prefix + "04_relief_river_data");
        seeFloat(channel(stages.result, 0), GRID, GRID, prefix + "05_relief_result_elev");
        seeFloat(channel(stages.result, 7), GRID, GRID, prefix + "06_relief_result_drainage_dir");
        seeFloat(
                rasterizeRiverSplines(relief.debugRiverSplinePoints(stages)),
                RELIEF_PADDED,
                RELIEF_PADDED,
                prefix + "07_relief_river_splines");
    }

    /**
     * Rasterize the traced river-spline sample points (padded px) by inserting them into a
     * {@link QuadTree} and lighting up every pixel within {@link #RIVER_SPLINE_RADIUS} of a sample.
     */
    private static float[] rasterizeRiverSplines(List<double[]> points) {
        final QuadTree<QuadTreePoint> tree =
                new QuadTree<>(new double[] {-1, -1}, new double[] {RELIEF_PADDED + 1, RELIEF_PADDED + 1});
        for (double[] point : points) tree.insertPoint(new QuadTreePoint(point));

        final float[] mask = new float[RELIEF_PADDED * RELIEF_PADDED];
        for (int pi = 0; pi < RELIEF_PADDED; pi++) {
            for (int pj = 0; pj < RELIEF_PADDED; pj++) {
                if (!tree.getPointsInCircle(new double[] {pi, pj}, RIVER_SPLINE_RADIUS)
                        .isEmpty()) {
                    mask[pi * RELIEF_PADDED + pj] = 1f;
                }
            }
        }
        return mask;
    }

    /** Extract a single GRID×GRID channel from a {@code [channels, GRID, GRID]} result tensor. */
    private static float[] channel(FloatTensor tensor, int ch) {
        final int pixels = GRID * GRID;
        final float[] out = new float[pixels];
        System.arraycopy(tensor.data, ch * pixels, out, 0, pixels);
        return out;
    }

    private static void seeFloat(float[] data, int width, int height, String name) {
        Debug.tensor.see(new FloatTensor(new int[] {height, width}, data), name, DEBUG_PATH);
    }

    private static float[] maskToFloat(boolean[] mask) {
        final float[] out = new float[mask.length];
        for (int i = 0; i < mask.length; i++) out[i] = mask[i] ? 1f : 0f;
        return out;
    }

    /** Rasterize every traced channel's spline points onto the tile grid (global px → tile-local). */
    private static float[] rasterizeChannels(List<Channel> channels, int tx, int tz) {
        final float[] grid = new float[GRID * GRID];
        final int originX = tx * GRID;
        final int originZ = tz * GRID;
        for (Channel channel : channels) {
            for (double[] point : channel.spline.points()) {
                final int x = (int) Math.round(point[0]) - originX;
                final int z = (int) Math.round(point[1]) - originZ;
                if (x >= 0 && x < GRID && z >= 0 && z < GRID) grid[x * GRID + z] = 1f;
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
