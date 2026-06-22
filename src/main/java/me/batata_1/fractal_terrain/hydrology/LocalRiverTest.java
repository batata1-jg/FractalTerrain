package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.meanders.*;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone smoke test for {@link LocalRiverProvider} (the river-pipeline owner). Loads the models,
 * injects a {@link GlobalRiverProvider} directly (so the {@code FractalTerrainInstance} singleton is
 * never touched), and for a few tiles dumps PNGs of the flow accumulation, local river mask, carved
 * elevation, and traced channels under {@code <DEFAULT_DEBUG_PATH>/local_river/}. Run with
 * {@code ./gradlew localRiverTest}.
 */
@TestOnly
public class LocalRiverTest {

    private static final Logger LOG = LoggerFactory.getLogger(LocalRiverTest.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/local_river";

    private static final int GRID = 512;
    /** LocalRiverProvider's padded working resolution (GRID + 1-px halo per side). */
    private static final int PAD = 1;

    /** Tiles (tx, tz) to render. */
    private static final int[][] TILES = {{1, -3}, {1, -4}, {0, -3}, {0, -4}};

    public static void main(String[] args) {
        LOG.info("LocalRiverTest start; output dir = {}", DEBUG_PATH);
        Meanders.DEBUG_STEPS = true;
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        pipeline.updateInstance(420, DEBUG_PATH);

        final GlobalRiverProvider globalRivers = new GlobalRiverProvider(null);
        final LocalRiverProvider localRivers = new LocalRiverProvider(null);
        localRivers.setGlobalRiverProvider(globalRivers);

        for (int[] tile : TILES) {
            dumpTile(localRivers, globalRivers, tile[0], tile[1]);
        }
        LOG.info("LocalRiverTest done. See {}", DEBUG_PATH);
    }

    private static void dumpTile(LocalRiverProvider provider, GlobalRiverProvider globalRivers, int tx, int tz) {
        LOG.info("Dumping tile ({},{}) river={}", tx, tz, GlobalRiverProvider.isRiver(globalRivers.getArrow(tx, tz)));
        final String prefix = "tile_tx" + tx + "_tz" + tz + "_";

        final LocalRiverProvider.Stages stages = provider.debugStages(tx, tz);
        seeFloat(stages.flow, GRID, GRID, prefix + "01_flow");
        seeFloat(maskToFloat(stages.riverMask), GRID, GRID, prefix + "02_river_mask");
        seeFloat(stages.carvedElevation, GRID, GRID, prefix + "03_carved_elev");
        seeFloat(rasterizeChannels(stages.channels, PAD), GRID, GRID, prefix + "04_global_channels");
        seeFloat(rasterizeChannels(stages.localChannels, 0), GRID, GRID, prefix + "05_local_channels");
        LOG.info(
                "tile ({},{}): {} global, {} local channels",
                tx,
                tz,
                stages.channels.size(),
                stages.localChannels.size());
        checkMonotonicElevations(stages.network, tx, tz);
    }

    /**
     * Verify the global river network's bed elevations decrease downstream: walk each SOURCE→DRAIN path
     * (following the single {@link Endpoint#outgoing} edge of the dendritic in-tree) and assert the node
     * elevation sequence (source → junctions → drain) is monotonically non-increasing. Logs a warning for
     * every violating step and a per-tile pass/fail summary. {@code NaN} elevations (unassigned nodes) are
     * skipped rather than treated as failures.
     */
    private static boolean checkMonotonicElevations(RiverNetwork network, int tx, int tz) {
        if (network == null) {
            LOG.warn("tile ({},{}): no network captured — skipping monotonicity check", tx, tz);
            return false;
        }
        int sequences = 0;
        int violations = 0;
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.type != Endpoint.Type.SOURCE) continue;
            sequences++;
            final List<Endpoint> path = downstreamPath(network, endpoint);
            for (int i = 1; i < path.size(); i++) {
                final Endpoint prev = path.get(i - 1);
                final Endpoint cur = path.get(i);
                if (Double.isNaN(prev.elevation) || Double.isNaN(cur.elevation)) continue;
                if (cur.elevation > prev.elevation) {
                    violations++;
                    LOG.warn(
                            "tile ({},{}): elevation rises downstream at node {} ({}) {} -> node {} ({}) {}",
                            tx,
                            tz,
                            prev.id,
                            prev.type,
                            prev.elevation,
                            cur.id,
                            cur.type,
                            cur.elevation);
                }
            }
        }
        if (violations == 0) {
            LOG.info("tile ({},{}): monotonicity OK across {} source sequences", tx, tz, sequences);
        } else {
            LOG.warn(
                    "tile ({},{}): {} monotonicity violations across {} source sequences",
                    tx,
                    tz,
                    violations,
                    sequences);
        }
        return violations == 0;
    }

    /** Collect the node sequence from {@code source} downstream to its drain (following {@code outgoing}). */
    private static List<Endpoint> downstreamPath(RiverNetwork network, Endpoint source) {
        final List<Endpoint> path = new ArrayList<>();
        Endpoint current = source;
        for (int guard = 0; current != null && guard <= GRID * GRID; guard++) {
            path.add(current);
            if (current.outgoing == -1) break;
            final Channel channel = network.getChannel(current.outgoing);
            if (channel == null) break;
            current = network.getNode(channel.endNodeId);
        }
        return path;
    }

    private static void seeFloat(float[] data, int width, int height, String name) {
        Debug.tensor.see(new FloatTensor(new int[] {height, width}, data), name, DEBUG_PATH);
    }

    private static float[] maskToFloat(boolean[] mask) {
        final float[] out = new float[mask.length];
        for (int i = 0; i < mask.length; i++) out[i] = mask[i] ? 1f : 0f;
        return out;
    }

    /** Rasterize each channel's spline points onto the tile grid, subtracting {@code offset} (the pad). */
    private static float[] rasterizeChannels(List<Channel> channels, int offset) {
        final float[] grid = new float[GRID * GRID];
        if (channels == null) return grid;
        for (Channel channel : channels) {
            for (double[] point : channel.spline.points()) {
                final int x = (int) Math.round(point[0]) - offset;
                final int z = (int) Math.round(point[1]) - offset;
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
