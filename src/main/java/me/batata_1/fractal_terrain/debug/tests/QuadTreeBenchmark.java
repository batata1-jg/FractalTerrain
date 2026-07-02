package me.batata_1.fractal_terrain.debug.tests;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.List;
import java.util.Random;
import java.util.function.ToLongFunction;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query-throughput benchmark for the hydrology spatial index: builds one real
 * {@link LocalRiverProvider} unit tile (same boot path as {@link LocalRiverTest}), then measures
 * queries/second for uniformly random points over the 512×512 tile, for each query kind the carve/paint
 * path uses:
 *
 * <ol>
 *   <li>{@code ImmutableQuadTree.getPointsInCircle} at the insideMargin radius (0.5 px);</li>
 *   <li>… at the channel-membership radius ({@code maxNativeWidth()/2});</li>
 *   <li>… at the influence radius ({@code MAX_INFLUENCE_RADIUS}) — the heavy queryInfluence load;</li>
 *   <li>{@code ImmutableQuadTree.anyPointInCircle} with the insideChannel test (early exit);</li>
 *   <li>{@code LocalRiverProvider.queryInfluence} (cross-tile + re-stamping);</li>
 *   <li>{@code HydrologyProfileCarver.carveAtPixel} (query + flat weighted merge).</li>
 * </ol>
 *
 * Every measured call feeds a checksum that is logged at the end, so the JIT cannot dead-code-eliminate
 * the queries. Also dumps the tile's unit-tree visualization + stats via {@code Debug.units}. Run with
 * {@code ./gradlew quadTreeBenchmark}.
 */
@TestOnly
public class QuadTreeBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(QuadTreeBenchmark.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/quadtree_benchmark";

    private static final int GRID = 512;
    private static final int TILE_X = -1;
    private static final int TILE_Z = -1;

    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final long MEASURE_NANOS = 5_000_000_000L;

    /**
     * Provider-level query points stay this far from the tile border so the cross-tile span
     * (MAX_INFLUENCE_RADIUS) never forces a neighbouring tile to build mid-benchmark (each build runs
     * diffusion inference and would swamp the timings).
     */
    private static final double PROVIDER_MARGIN = FractalTerrainConfig.MAX_INFLUENCE_RADIUS + 2.0;

    public static void main(String[] args) {
        LOG.info("QuadTreeBenchmark start; output dir = {}", DEBUG_PATH);
        Meanders.DEBUG_STEPS = false;
        ModelAssetManager.ensureAssetsReady();
        PipelineModels.load();
        pipeline.updateInstance(420, DEBUG_PATH);

        final GlobalRiverProvider globalRivers = new GlobalRiverProvider(null);
        final LocalRiverProvider localRivers = new LocalRiverProvider(null);
        localRivers.setGlobalRiverProvider(globalRivers);
        final HydrologyProfileCarver carver = new HydrologyProfileCarver(localRivers);

        LOG.info("building unit tile ({},{})...", TILE_X, TILE_Z);
        final ImmutableQuadTree<HydrologicalUnit> tree = localRivers.getUnitTree(TILE_X, TILE_Z);
        LOG.info("unit tile built: {} points", tree.numPoints());

        // Snapshot for the human: the same imagery LocalRiverTest dumps.
        final List<HydrologicalUnit> allUnits =
                tree.getPointsInBox(new double[] {-GRID, -GRID}, new double[] {2.0 * GRID, 2.0 * GRID});
        Debug.units.see(allUnits, "benchmark_units_tx" + TILE_X + "_tz" + TILE_Z, GRID, 4);
        Debug.units.logStats(allUnits, "tile (" + TILE_X + "," + TILE_Z + ")");

        final double membershipRadius = FractalTerrainConfig.maxNativeWidth() / 2.0;
        final double influenceRadius = FractalTerrainConfig.MAX_INFLUENCE_RADIUS;
        // World-pixel origin of the benchmark tile (provider-level queries take world coords).
        final double worldOriginX = TILE_X * (double) GRID;
        final double worldOriginZ = TILE_Z * (double) GRID;

        // Tree-level queries: tile-local points anywhere in the tile.
        bench("getPointsInCircle r=0.5 (insideMargin)", tileLocalPoints(1), pt -> tree.getPointsInCircle(pt, 0.5)
                .size());
        bench(
                "getPointsInCircle r=" + membershipRadius + " (membership)",
                tileLocalPoints(2),
                pt -> tree.getPointsInCircle(pt, membershipRadius).size());
        bench(
                "getPointsInCircle r=" + influenceRadius + " (influence)",
                tileLocalPoints(3),
                pt -> tree.getPointsInCircle(pt, influenceRadius).size());
        bench(
                "anyPointInCircle r=" + membershipRadius + " (insideChannel test)",
                tileLocalPoints(4),
                pt -> tree.anyPointInCircle(
                                pt, membershipRadius, (u, distSq) -> distSq <= (u.width() * 0.5) * (u.width() * 0.5))
                        ? 1
                        : 0);

        // Provider-level queries: world points, kept PROVIDER_MARGIN clear of tile borders (see above).
        bench(
                "LocalRiverProvider.queryInfluence",
                worldInnerPoints(5, worldOriginX, worldOriginZ),
                pt -> localRivers.queryInfluence(pt).length);
        bench(
                "HydrologyProfileCarver.carveAtPixel",
                worldInnerPoints(6, worldOriginX, worldOriginZ),
                pt -> Float.floatToIntBits(carver.carveAtPixel(pt[0], pt[1], 100.0)));

        LOG.info("QuadTreeBenchmark done. See {}", DEBUG_PATH);
    }

    /** Deterministic uniform points in the tile-local frame [0, GRID)². One generator per benchmark. */
    private static PointSupplier tileLocalPoints(long seed) {
        final Random rng = new Random(seed);
        final double[] pt = new double[2];
        return () -> {
            pt[0] = rng.nextDouble() * GRID;
            pt[1] = rng.nextDouble() * GRID;
            return pt;
        };
    }

    /** Deterministic uniform world-frame points in the tile's inner region (PROVIDER_MARGIN from borders). */
    private static PointSupplier worldInnerPoints(long seed, double worldOriginX, double worldOriginZ) {
        final Random rng = new Random(seed);
        final double span = GRID - 2 * PROVIDER_MARGIN;
        final double[] pt = new double[2];
        return () -> {
            pt[0] = worldOriginX + PROVIDER_MARGIN + rng.nextDouble() * span;
            pt[1] = worldOriginZ + PROVIDER_MARGIN + rng.nextDouble() * span;
            return pt;
        };
    }

    /** Supplies the next query point (may return a reused array; the query must not retain it). */
    @FunctionalInterface
    private interface PointSupplier {
        double[] next();
    }

    /**
     * Wall-clock benchmark: run {@code query} on fresh points for {@link #WARMUP_NANOS} (discarded), then
     * for {@link #MEASURE_NANOS}, and log queries/second. The per-call long results accumulate into a
     * checksum that is logged too, so the JIT cannot eliminate the work.
     */
    private static void bench(String label, PointSupplier points, ToLongFunction<double[]> query) {
        long checksum = 0;
        final long warmupEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmupEnd) checksum += query.applyAsLong(points.next());

        long ops = 0;
        final long start = System.nanoTime();
        final long measureEnd = start + MEASURE_NANOS;
        long now;
        while ((now = System.nanoTime()) < measureEnd) {
            checksum += query.applyAsLong(points.next());
            ops++;
        }
        final double seconds = (now - start) / 1e9;
        LOG.info(
                "{}: {} queries/sec ({} ops in {}s, checksum {})",
                label,
                Math.round(ops / seconds),
                ops,
                String.format("%.2f", seconds),
                checksum);
    }
}
