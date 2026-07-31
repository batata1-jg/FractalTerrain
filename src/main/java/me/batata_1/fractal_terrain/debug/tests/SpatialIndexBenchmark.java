package me.batata_1.fractal_terrain.debug.tests;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToLongFunction;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query-throughput benchmark for the hydrology spatial indexes: builds one real
 * {@link LocalRiverProvider} unit tile (same boot path as {@link LocalRiverTest}), indexes the same
 * units in <b>both</b> structures — the production {@link ImmutableRTree} of influence circles and an
 * {@link ImmutableQuadTree} of unit-center points replicating the pre-R-tree query path — then
 * measures queries/second for uniformly random points over the 512×512 tile, pairing each legacy
 * query with its R-tree replacement:
 *
 * <ol>
 *   <li><b>influence query</b> — legacy: {@code getPointsInCircle(MAX_INFLUENCE_RADIUS)} + per-unit
 *       reach filter ({@code distSq ≤ riverInfluence(width)²}); R-tree: one
 *       {@link ImmutableRTree#queryContaining} stab;</li>
 *   <li><b>insideChannel existence test</b> — legacy: {@code anyPointInCircle} at
 *       {@code maxNativeWidth()/2}; R-tree: {@link ImmutableRTree#anyContaining} with the
 *       {@code width/2} filter;</li>
 *   <li><b>provider level</b> — {@code LocalRiverProvider.queryInfluence} (cross-tile + re-stamping)
 *       and {@code HydrologyProfileCarver.carveAtPixel} (query + flat weighted merge), both now on the
 *       R-tree path.</li>
 * </ol>
 *
 * Before timing anything it cross-checks correctness: over {@value #CROSS_CHECK_POINTS} seeded random
 * points, the legacy path and the R-tree stab must return exactly the same unit sets (throws on the
 * first mismatch). Every measured call feeds a checksum that is logged at the end, so the JIT cannot
 * dead-code-eliminate the queries. Also dumps the tile's unit visualization + stats via
 * {@code Debug.units}. Run with {@code ./gradlew spatialIndexBenchmark}.
 */
@TestOnly
public class SpatialIndexBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(SpatialIndexBenchmark.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/spatial_index_benchmark";

    private static final int GRID = 512;
    private static final int TILE_X = -2;
    private static final int TILE_Z = -2;

    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final long MEASURE_NANOS = 5_000_000_000L;

    private static final int CROSS_CHECK_POINTS = 10_000;

    /**
     * Provider-level query points stay this far from the tile border so the cross-tile span
     * (MAX_INFLUENCE_RADIUS) never forces a neighbouring tile to build mid-benchmark (each build runs
     * diffusion inference and would swamp the timings).
     */
    private static final double PROVIDER_MARGIN = HydrologyTuning.MAX_INFLUENCE_RADIUS + 2.0;

    /**
     * Quadtree adapter for the legacy path: {@link HydrologicalUnit} no longer implements
     * {@link SpatialIndexPoint} (it is an influence circle now), so the benchmark wraps each unit in a
     * point record carrying the unit's center.
     */
    private record UnitPoint(HydrologicalUnit unit) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return unit.coord();
        }
    }

    public static void main(String[] args) {
        LOG.info("SpatialIndexBenchmark start; output dir = {}", DEBUG_PATH);
        Meanders.DEBUG_STEPS = false;
        ModelAssetManager.ensureAssetsReady();
        FractalTerrainInstance.initPipeline();
        pipeline.updateInstance(420, DEBUG_PATH);

        final GlobalRiverProvider globalRivers = new GlobalRiverProvider(null);
        final LocalRiverProvider localRivers = new LocalRiverProvider(null);
        localRivers.setGlobalRiverProvider(globalRivers);
        final HydrologyProfileCarver carver = new HydrologyProfileCarver(localRivers);

        LOG.info("building unit tile ({},{})...", TILE_X, TILE_Z);
        final ImmutableRTree<HydrologicalUnit> unitRTree = localRivers.getUnitTree(TILE_X, TILE_Z);
        final List<HydrologicalUnit> allUnits = unitRTree.getAllEntries();
        LOG.info("unit tile built: {} units", unitRTree.numEntries());

        // World-pixel origin of the benchmark tile. The provider indexes units in the WORLD frame, so
        // this is not just for the provider-level benchmarks below: EVERY structure and query point here
        // lives in that frame, including the index-level ones that used to be tile-local.
        final double worldOriginX = TILE_X * (double) GRID;
        final double worldOriginZ = TILE_Z * (double) GRID;

        // The legacy structure over the same units: a point quadtree + the per-unit reach re-test. Its
        // bounds must span the tile's WORLD extent, or every unit falls outside the root square.
        final List<UnitPoint> unitPoints = new ArrayList<>(allUnits.size());
        for (final HydrologicalUnit unit : allUnits) unitPoints.add(new UnitPoint(unit));
        final ImmutableQuadTree<UnitPoint> unitQuadTree = new ImmutableQuadTree<>(
                new double[] {worldOriginX - 16, worldOriginZ - 16},
                new double[] {worldOriginX + GRID + 16, worldOriginZ + GRID + 16},
                unitPoints);

        // Snapshot for the human: the same imagery LocalRiverTest dumps (world coords → tile canvas).
        Debug.units.see(allUnits, "benchmark_units_tx" + TILE_X + "_tz" + TILE_Z, GRID, 4, worldOriginX, worldOriginZ);
        Debug.units.logStats(allUnits, "tile (" + TILE_X + "," + TILE_Z + ")");

        crossCheckInfluenceQueries(allUnits, unitQuadTree, unitRTree, worldOriginX, worldOriginZ);

        final double membershipRadius = HydrologyTuning.maxNativeWidth() / 2.0;

        // ---- influence query: legacy quadtree+filter vs one R-tree stab -------------------------
        final List<UnitPoint> legacyQueryBuffer = new ArrayList<>(256);
        final double legacyInfluenceOpsPerSec = bench(
                "quadtree influence query (circle r=" + HydrologyTuning.MAX_INFLUENCE_RADIUS + " + reach filter)",
                worldTilePoints(1, worldOriginX, worldOriginZ),
                pt -> legacyInfluenceQuery(unitQuadTree, pt, legacyQueryBuffer));
        final List<HydrologicalUnit> stabQueryBuffer = new ArrayList<>(256);
        final double rtreeInfluenceOpsPerSec = bench(
                "rtree influence query (queryContaining stab)", worldTilePoints(1, worldOriginX, worldOriginZ), pt -> {
                    stabQueryBuffer.clear();
                    return unitRTree.queryContaining(pt, stabQueryBuffer).size();
                });

        // ---- insideChannel existence test: legacy anyPointInCircle vs R-tree anyContaining ------
        final double legacyMembershipOpsPerSec = bench(
                "quadtree anyPointInCircle r=" + membershipRadius + " (insideChannel test)",
                worldTilePoints(2, worldOriginX, worldOriginZ),
                pt -> unitQuadTree.anyPointInCircle(
                                pt,
                                membershipRadius,
                                (unitPoint, distSq) -> distSq
                                        <= (unitPoint.unit().width() * 0.5)
                                                * (unitPoint.unit().width() * 0.5))
                        ? 1
                        : 0);
        final double rtreeMembershipOpsPerSec = bench(
                "rtree anyContaining (insideChannel test)",
                worldTilePoints(2, worldOriginX, worldOriginZ),
                pt -> unitRTree.anyContaining(pt, unit -> {
                            final double deltaX = unit.coord()[0] - pt[0];
                            final double deltaZ = unit.coord()[1] - pt[1];
                            return deltaX * deltaX + deltaZ * deltaZ <= (unit.width() * 0.5) * (unit.width() * 0.5);
                        })
                        ? 1
                        : 0);

        // ---- provider-level queries: world points, PROVIDER_MARGIN clear of borders (see above) ----
        bench(
                "LocalRiverProvider.queryInfluence",
                worldInnerPoints(5, worldOriginX, worldOriginZ),
                pt -> localRivers.queryInfluence(pt).length);
        bench(
                "HydrologyProfileCarver.carveAtPixel",
                worldInnerPoints(6, worldOriginX, worldOriginZ),
                pt -> Float.floatToIntBits(carver.carveAtPixel(pt, 100.0)));

        LOG.info(
                "throughput ratio (rtree/quadtree): influence query {}x, insideChannel test {}x",
                String.format("%.2f", rtreeInfluenceOpsPerSec / legacyInfluenceOpsPerSec),
                String.format("%.2f", rtreeMembershipOpsPerSec / legacyMembershipOpsPerSec));
        LOG.info("SpatialIndexBenchmark done. See {}", DEBUG_PATH);
    }

    /**
     * The pre-R-tree influence query, replicated exactly: collect every unit-center point within
     * {@code MAX_INFLUENCE_RADIUS}, then keep only those whose own {@code riverInfluence(width)} disc
     * actually covers the query point. Returns the kept count.
     */
    private static long legacyInfluenceQuery(
            ImmutableQuadTree<UnitPoint> unitQuadTree, double[] pt, List<UnitPoint> buffer) {
        buffer.clear();
        unitQuadTree.getPointsInCircle(pt, HydrologyTuning.MAX_INFLUENCE_RADIUS, buffer);
        long keptCount = 0;
        for (final UnitPoint unitPoint : buffer) {
            final HydrologicalUnit unit = unitPoint.unit();
            final double deltaX = unit.coord()[0] - pt[0];
            final double deltaZ = unit.coord()[1] - pt[1];
            final double reach = unit.getRadius();
            if (deltaX * deltaX + deltaZ * deltaZ <= reach * reach) keptCount++;
        }
        return keptCount;
    }

    /**
     * Correctness gate run before any timing, over {@link #CROSS_CHECK_POINTS} seeded random points drawn
     * from the tile's world extent (the frame the index stores). Ground truth is a <b>brute-force linear
     * scan</b> of every unit ({@code distSq ≤ riverInfluence(width)²}); the R-tree stab must match it
     * exactly (throws on the first mismatch).
     * The legacy quadtree path is checked against the same truth but only <b>warns</b> on deviation —
     * {@code ImmutableQuadTree.findSection} tiles on a 0-anchored grid while node squares use an
     * arbitrary origin, a known pre-existing boundary misclassification that can drop a few points, and
     * it must not mask an R-tree bug (nor fail the benchmark for an old one).
     */
    private static void crossCheckInfluenceQueries(
            List<HydrologicalUnit> allUnits,
            ImmutableQuadTree<UnitPoint> unitQuadTree,
            ImmutableRTree<HydrologicalUnit> unitRTree,
            double worldOriginX,
            double worldOriginZ) {
        final Random rng = new Random(42);
        final double[] pt = new double[2];
        final List<UnitPoint> legacyBuffer = new ArrayList<>(256);
        final List<HydrologicalUnit> stabBuffer = new ArrayList<>(256);
        int quadTreeDeviations = 0;
        for (int i = 0; i < CROSS_CHECK_POINTS; i++) {
            pt[0] = worldOriginX + rng.nextDouble() * GRID;
            pt[1] = worldOriginZ + rng.nextDouble() * GRID;

            final Set<HydrologicalUnit> bruteForceHits = new HashSet<>();
            for (final HydrologicalUnit unit : allUnits) {
                final double deltaX = unit.coord()[0] - pt[0];
                final double deltaZ = unit.coord()[1] - pt[1];
                final double reach = unit.getRadius();
                if (deltaX * deltaX + deltaZ * deltaZ <= reach * reach) bruteForceHits.add(unit);
            }

            stabBuffer.clear();
            final Set<HydrologicalUnit> stabHits = new HashSet<>(unitRTree.queryContaining(pt, stabBuffer));
            if (!bruteForceHits.equals(stabHits)) {
                final Set<HydrologicalUnit> bruteForceOnly = new HashSet<>(bruteForceHits);
                bruteForceOnly.removeAll(stabHits);
                final Set<HydrologicalUnit> stabOnly = new HashSet<>(stabHits);
                stabOnly.removeAll(bruteForceHits);
                LOG.error(
                        "R-tree stab mismatch at ({}, {}): brute force {} units, rtree {} units; missing {}, extra {}",
                        pt[0],
                        pt[1],
                        bruteForceHits.size(),
                        stabHits.size(),
                        bruteForceOnly,
                        stabOnly);
                throw new IllegalStateException("R-tree stab disagrees with brute force — see log");
            }

            legacyBuffer.clear();
            unitQuadTree.getPointsInCircle(pt, HydrologyTuning.MAX_INFLUENCE_RADIUS, legacyBuffer);
            final Set<HydrologicalUnit> legacyHits = new HashSet<>();
            for (final UnitPoint unitPoint : legacyBuffer) {
                final HydrologicalUnit unit = unitPoint.unit();
                final double deltaX = unit.coord()[0] - pt[0];
                final double deltaZ = unit.coord()[1] - pt[1];
                final double reach = unit.getRadius();
                if (deltaX * deltaX + deltaZ * deltaZ <= reach * reach) legacyHits.add(unit);
            }
            if (!legacyHits.equals(bruteForceHits)) quadTreeDeviations++;
        }
        if (quadTreeDeviations > 0)
            LOG.warn(
                    "legacy quadtree path deviated from brute force on {} of {} points (known findSection"
                            + " boundary misclassification) — its throughput numbers below stand, but the"
                            + " R-tree is the correct one",
                    quadTreeDeviations,
                    CROSS_CHECK_POINTS);
        LOG.info("cross-check passed: R-tree stab matches brute force on {} points", CROSS_CHECK_POINTS);
    }

    /**
     * Deterministic uniform world-frame points spanning the benchmark tile — {@code [origin, origin+GRID)}
     * per axis. One generator per benchmark. Used for the index-level benchmarks, which stab the tile's
     * own R-tree directly; that index stores world coords, so its query points are world points too.
     */
    private static PointSupplier worldTilePoints(long seed, double worldOriginX, double worldOriginZ) {
        final Random rng = new Random(seed);
        final double[] pt = new double[2];
        return () -> {
            pt[0] = worldOriginX + rng.nextDouble() * GRID;
            pt[1] = worldOriginZ + rng.nextDouble() * GRID;
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
     * for {@link #MEASURE_NANOS}, log queries/second, and return it (for the ratio summary). The per-call
     * long results accumulate into a checksum that is logged too, so the JIT cannot eliminate the work.
     */
    private static double bench(String label, PointSupplier points, ToLongFunction<double[]> query) {
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
        final double opsPerSecond = ops / seconds;
        LOG.info(
                "{}: {} queries/sec ({} ops in {}s, checksum {})",
                label,
                Math.round(opsPerSecond),
                ops,
                String.format("%.2f", seconds),
                checksum);
        return opsPerSecond;
    }
}
