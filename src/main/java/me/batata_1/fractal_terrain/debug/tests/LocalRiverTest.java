package me.batata_1.fractal_terrain.debug.tests;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.Drainage;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.meanders.*;
import me.batata_1.fractal_terrain.hydrology.rosgen.ReachMetricsSampler;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone smoke test for {@link LocalRiverProvider} (the river-pipeline owner). Loads the models via
 * {@code FractalTerrainInstance.initPipeline()}, but constructs its own {@link GlobalRiverProvider} and
 * {@link LocalRiverProvider} directly rather than going through {@code FractalTerrainInstance}'s provider
 * accessors, and for a few tiles dumps PNGs of the flow accumulation, local river mask, carved elevation,
 * and traced channels under {@code <DEFAULT_DEBUG_PATH>/local_river/}. Run with
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
    private static final int[][] TILES = {{-2, -2}
        // {0, -1},
        //    {-1,-1}
        //   {-1,-1},{0,-1},{0,-2}
        //         {-1, -1}, {-1, -2}, {-2, -1} , {-2,-2}
    };

    public static void main(String[] args) {
        LOG.info("LocalRiverTest start; output dir = {}", DEBUG_PATH);
        Meanders.DEBUG_STEPS = false;
        ModelAssetManager.ensureAssetsReady();
        FractalTerrainInstance.initPipeline();
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

        // Dump the global-river cell profile first: it reads only GlobalRiverProvider, so it survives
        // even if the Meanders build inside debugStages below throws.
        globalRiverNetworkCellProfile(globalRivers, tx, tz);

        final LocalRiverProvider.Stages stages;
        try {
            stages = provider.debugStages(tx, tz);
        } catch (RuntimeException e) {
            LOG.warn("tile ({},{}): debugStages failed ({}); cell profile above still dumped", tx, tz, e.toString(), e);
            return;
        }
        seeFloat(stages.flow, GRID + 2, GRID + 2, prefix + "01_flow");
        seeFloat(maskToFloat(stages.riverMask), GRID + 2, GRID + 2, prefix + "02_river_mask");
        seeFloat(stages.carvedElevation, GRID, GRID, prefix + "03_carved_elev");
        // Both lists are channels of the SAME unified graph (see LocalRiverProvider.Stages), and the local
        // trace shifts its segments by +PAD into the padded graph frame on insertion (DL-005), so both
        // need the same PAD offset subtracted for tile-local pixel coords.
        seeFloat(rasterizeChannels(stages.channels, PAD), GRID, GRID, prefix + "04_global_channels");
        seeFloat(rasterizeChannels(stages.localChannels, PAD), GRID, GRID, prefix + "05_local_channels");
        LOG.info(
                "tile ({},{}): {} global, {} local channels",
                tx,
                tz,
                stages.channels.size(),
                stages.localChannels.size());
        checkMonotonicElevations(stages.network, tx, tz);
        ReachMetricsSampler sampler = new ReachMetricsSampler(stages.rawElevation, GRID);
        dumpSlopeHistogram(stages.network, sampler);
        dumpUnitTree(stages, tx, tz, prefix);
    }

    /**
     * Prints the along-channel reach-slope distribution. Rosgen's published slope bands (0.02 / 0.04 /
     * 0.10) are real-world channel slopes; this world's relief is vertically exaggerated relative to its
     * horizontal run, so copying them classifies most of the world as Aa+. Place S_A and S_AA at
     * percentiles of this distribution instead -- matching the shape matters more than the numbers.
     *
     * <p>One slope per channel, measured end to end, not one per {@code ReachRosgenClassifier} reach.
     * Channels whose {@code bedElevations} are absent or mis-sized against the spline are skipped:
     * {@code Channel.keepOnly} slices the spline without slicing {@code bedElevations}, so a stale array
     * would index out of bounds.
     */
    private static void dumpSlopeHistogram(RiverNetwork network, ReachMetricsSampler sampler) {
        // throw new NotImplementedException();
        if (network == null) return;
        final List<Double> slopes = new ArrayList<>();
        for (Channel ch : network.getChannels()) {
            if (ch.bedElevations == null || ch.numPts() < 2) continue;
            if (ch.bedElevations.length != ch.numPts()) continue;
            final List<double[]> pts = ch.spline.points();
            double arc = 0.0;
            for (int i = 1; i < pts.size(); i++) arc += VectorOps.distance(pts.get(i - 1), pts.get(i));
            slopes.add(sampler.slope(pts, arc, 0, ch.numPts() - 1));
        }
        if (slopes.isEmpty()) return;
        Collections.sort(slopes);
        for (double p : new double[] {0.50, 0.75, 0.90, 0.95, 0.99}) {
            final int idx = Math.min(slopes.size() - 1, (int) (p * slopes.size()));
            System.out.printf("slope p%02d = %.6f%n", (int) (p * 100), slopes.get(idx));
        }
    }

    /**
     * Render the tile's built {@code HydrologicalUnit} R-tree (type-colored points, id shades, width
     * girths — see {@link me.batata_1.fractal_terrain.debug.HydrologyUnitVisualizer}) and log its stats.
     * Reads the index {@code debugStages} captured, so the tile is not rebuilt.
     */
    private static void dumpUnitTree(LocalRiverProvider.Stages stages, int tx, int tz, String prefix) {
        if (stages.unitTree == null) {
            LOG.warn("tile ({},{}): no unit tree captured — skipping unit dump", tx, tz);
            return;
        }
        final var savedPath = Debug.units.debugPath;
        Debug.units.debugPath = DEBUG_PATH;
        try {
            final List<HydrologicalUnit> units = stages.unitTree.getAllEntries();
            Debug.units.see(units, prefix + "06_units", GRID, 4);
            Debug.units.seeByRosgenType(units, prefix + "07_rosgen", GRID, 4);
            Debug.units.logStats(units, "tile (" + tx + "," + tz + ")");
        } catch (RuntimeException e) {
            LOG.warn("tile ({},{}): unit-tree dump failed ({})", tx, tz, e.toString(), e);
        } finally {
            Debug.units.debugPath = savedPath;
        }
    }

    /** Cardinal direction labels for arrow bits 4..7 (see {@code Drainage.NEIGHBOR_OFFSET_*}). */
    private static final String[] DIRECTION_NAME = {"", "", "", "", "-Z", "+Z", "-X", "+X"};

    /**
     * Log the 4×4 grid of global-river cells covering relief tile {@code (tx, tz)} — the same window
     * {@code LocalRiverProvider.buildGlobalNetwork} reads (relative offsets {@code a,b ∈ [-1,2]}, so the
     * inner 2×2 are the tile's owned cells). For each cell logs its coarse-cell coordinates, the
     * source/sink/coast/river flags, width, bed elevation, and the downstream cell its outgoing arrow
     * points to. Reads only {@link GlobalRiverProvider}, so it runs independently of the (possibly
     * failing) {@code Meanders} build.
     */
    private static void globalRiverNetworkCellProfile(GlobalRiverProvider grp, int tx, int tz) {
        LOG.info(
                "global-river cell profile for relief tile ({},{}) [coarse cells {}..{} x {}..{}]:",
                tx,
                tz,
                tx * 2 - 1,
                tx * 2 + 2,
                tz * 2 - 1,
                tz * 2 + 2);
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) {
                final int ccx = tx * 2 + a;
                final int ccz = tz * 2 + b;
                final int arrow = grp.getArrow(ccx, ccz);
                final boolean owned = a >= 0 && a <= 1 && b >= 0 && b <= 1;
                LOG.info(
                        "  cell ({},{}){} arrow=0x{} river={} source={} sink={} coast={} width={} elev={} {}",
                        ccx,
                        ccz,
                        owned ? " [owned]" : "",
                        Integer.toHexString(arrow),
                        GlobalRiverProvider.isRiver(arrow),
                        GlobalRiverProvider.isSource(arrow),
                        GlobalRiverProvider.isSink(arrow),
                        GlobalRiverProvider.isCoast(arrow),
                        grp.getWidth(ccx, ccz),
                        grp.getElevation(ccx, ccz),
                        downstreamCellLabel(ccx, ccz, arrow));
            }
        }
    }

    /** Resolve a cell's outgoing cardinal arrow to a "points to (dcx,dcz)" label, or "(no outgoing)". */
    private static String downstreamCellLabel(int ccx, int ccz, int arrow) {
        final int outMask = GlobalRiverProvider.outgoingMask(arrow);
        for (int d = 4; d <= 7; d++) {
            if ((outMask & (1 << d)) != 0) {
                final int dcx = ccx + Drainage.NEIGHBOR_OFFSET_X[d];
                final int dcz = ccz + Drainage.NEIGHBOR_OFFSET_Z[d];
                return "-> points to (" + dcx + "," + dcz + ") dir=" + DIRECTION_NAME[d];
            }
        }
        return "-> (no outgoing)";
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
