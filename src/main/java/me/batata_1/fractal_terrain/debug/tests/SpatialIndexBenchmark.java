package me.batata_1.fractal_terrain.debug.tests;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToLongFunction;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.DebugConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.providers.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queries/sec benchmark comparing the production {@link ImmutableRTree} against the legacy
 * {@link ImmutableQuadTree}-based query path it replaced, over one real {@link RiverProvider}
 * primitive tile — covering the influence query, the {@code insideChannel} existence test, and the
 * provider-level query/carve calls.
 *
 * <p>Cross-checks correctness against a brute-force scan before timing anything (throws on mismatch),
 * since a benchmark of a broken index is worthless. Also dumps the tile's primitive visualization. Run with
 * {@code ./gradlew spatialIndexBenchmark}.
 */
@TestOnly
public class SpatialIndexBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(SpatialIndexBenchmark.class);

    private static final String DEBUG_PATH = FractalTerrainConfig.DEFAULT_DEBUG_PATH + "/spatial_index_benchmark";

    private static final int TILE_X = -2;
    private static final int TILE_Z = -2;

    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final long MEASURE_NANOS = 5_000_000_000L;

    private static final int CROSS_CHECK_POINTS = 10_000;

    /** Keeps provider-level query points clear of MAX_INFLUENCE_RADIUS so a run never triggers a neighbour-tile build mid-timing. */
    private static final double PROVIDER_MARGIN = HydrologyTuning.MAX_INFLUENCE_RADIUS + 2.0;

    /** Quadtree adapter: {@link HydrologicalPrimitive} is an influence circle rather than a
     *  {@link SpatialIndexPoint}, so the point-index side of the comparison wraps each primitive's centre. */
    private record PrimitivePoint(HydrologicalPrimitive primitive) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return primitive.coord();
        }
    }

    public static void main(String[] args) {
        LOG.info("SpatialIndexBenchmark start; output dir = {}", DEBUG_PATH);
        DebugConfig.DEBUG_STEPS = false;
        ModelAssetManager.ensureAssetsReady();
        FractalTerrainInstance.initPipeline();
        pipeline.updateInstance(420, DEBUG_PATH);

        final GlobalRiverProvider globalRivers = new GlobalRiverProvider(null);
        final RiverProvider localRivers = new RiverProvider(null);
        localRivers.setGlobalRiverProvider(globalRivers);
        final HydrologyProfileInprinter carver = new HydrologyProfileInprinter(localRivers);

        LOG.info("building primitive tile ({},{})...", TILE_X, TILE_Z);
        final ImmutableRTree<HydrologicalPrimitive> primitiveRTree = localRivers.getPrimitiveTree(TILE_X, TILE_Z);
        final List<HydrologicalPrimitive> allPrimitives = primitiveRTree.getAllEntries();
        LOG.info("primitive tile built: {} primitives", primitiveRTree.numEntries());

        // World-pixel origin of the benchmark tile. The provider indexes primitives in the WORLD frame, so
        // this is not just for the provider-level benchmarks below: EVERY structure and query point here
        // lives in that frame, including the index-level ones that used to be tile-local.
        final double worldOriginX = TILE_X * (double) HydrologyTileGeometry.GRID;
        final double worldOriginZ = TILE_Z * (double) HydrologyTileGeometry.GRID;

        // The legacy structure over the same primitives: a point quadtree + the per-primitive reach re-test. Its
        // bounds must span the tile's WORLD extent, or every primitive falls outside the root square.
        final List<PrimitivePoint> primitivePoints = new ObjectArrayList<>(allPrimitives.size());
        for (final HydrologicalPrimitive primitive : allPrimitives) primitivePoints.add(new PrimitivePoint(primitive));
        final ImmutableQuadTree<PrimitivePoint> primitiveQuadTree = new ImmutableQuadTree<>(
                new double[] {worldOriginX - 16, worldOriginZ - 16},
                new double[] {
                    worldOriginX + HydrologyTileGeometry.GRID + 16, worldOriginZ + HydrologyTileGeometry.GRID + 16
                },
                primitivePoints);

        // Snapshot for the human: the same imagery LocalRiverTest dumps (world coords → tile canvas).
        Debug.primitives.see(
                allPrimitives,
                "benchmark_units_tx" + TILE_X + "_tz" + TILE_Z,
                HydrologyTileGeometry.GRID,
                4,
                worldOriginX,
                worldOriginZ);
        Debug.primitives.logStats(allPrimitives, "tile (" + TILE_X + "," + TILE_Z + ")");

        crossCheckInfluenceQueries(allPrimitives, primitiveQuadTree, primitiveRTree, worldOriginX, worldOriginZ);

        final double membershipRadius = HydrologyTuning.maxNativeWidth() / 2.0;

        // ---- influence query: legacy quadtree+filter vs one R-tree stab -------------------------
        final List<PrimitivePoint> legacyQueryBuffer = new ObjectArrayList<>(256);
        final double legacyInfluenceOpsPerSec = bench(
                "quadtree influence query (circle r=" + HydrologyTuning.MAX_INFLUENCE_RADIUS + " + reach filter)",
                worldTilePoints(1, worldOriginX, worldOriginZ),
                pt -> legacyInfluenceQuery(primitiveQuadTree, pt, legacyQueryBuffer));
        final List<HydrologicalPrimitive> stabQueryBuffer = new ObjectArrayList<>(256);
        final double rtreeInfluenceOpsPerSec = bench(
                "rtree influence query (queryContaining stab)", worldTilePoints(1, worldOriginX, worldOriginZ), pt -> {
                    stabQueryBuffer.clear();
                    return primitiveRTree.queryContaining(pt, stabQueryBuffer).size();
                });

        // ---- insideChannel existence test: legacy anyPointInCircle vs R-tree anyContaining ------
        final double legacyMembershipOpsPerSec = bench(
                "quadtree anyPointInCircle r=" + membershipRadius + " (insideChannel test)",
                worldTilePoints(2, worldOriginX, worldOriginZ),
                pt -> primitiveQuadTree.anyPointInCircle(
                                pt, membershipRadius, (primitivePoint, distSq) -> primitivePoint
                                        .primitive()
                                        .channelContains(distSq))
                        ? 1
                        : 0);
        final double rtreeMembershipOpsPerSec = bench(
                "rtree anyContaining (insideChannel test)",
                worldTilePoints(2, worldOriginX, worldOriginZ),
                pt -> primitiveRTree.anyContaining(pt, primitive -> {
                            final double deltaX = primitive.coord()[0] - pt[0];
                            final double deltaZ = primitive.coord()[1] - pt[1];
                            return primitive.channelContains(deltaX * deltaX + deltaZ * deltaZ);
                        })
                        ? 1
                        : 0);

        // ---- provider-level queries: world points, PROVIDER_MARGIN clear of borders (see above) ----
        bench(
                "RiverProvider.queryInfluence",
                worldInnerPoints(5, worldOriginX, worldOriginZ),
                pt -> localRivers.queryInfluence(pt).toArray().length);

        LOG.info(
                "throughput ratio (rtree/quadtree): influence query {}x, insideChannel test {}x",
                String.format("%.2f", rtreeInfluenceOpsPerSec / legacyInfluenceOpsPerSec),
                String.format("%.2f", rtreeMembershipOpsPerSec / legacyMembershipOpsPerSec));
        LOG.info("SpatialIndexBenchmark done. See {}", DEBUG_PATH);
    }

    /** The pre-R-tree influence query, replicated exactly: quadtree circle scan then per-primitive
     *  {@code riverInfluence(width)} filter — the baseline the R-tree replaces. */
    private static long legacyInfluenceQuery(
            ImmutableQuadTree<PrimitivePoint> primitiveQuadTree, double[] pt, List<PrimitivePoint> buffer) {
        buffer.clear();
        primitiveQuadTree.getPointsInCircle(pt, HydrologyTuning.MAX_INFLUENCE_RADIUS, buffer);
        long keptCount = 0;
        for (final PrimitivePoint primitivePoint : buffer) {
            final HydrologicalPrimitive primitive = primitivePoint.primitive();
            if (primitive.containsPoint(pt)) keptCount++;
        }
        return keptCount;
    }

    /** Correctness gate before any timing: brute-force scan is ground truth; the R-tree must match
     *  exactly (throws on mismatch), while the legacy quadtree only warns — {@code findSection}'s known
     *  0-anchored-grid boundary bug can drop points and must not fail the benchmark for an old bug. */
    private static void crossCheckInfluenceQueries(
            List<HydrologicalPrimitive> allPrimitives,
            ImmutableQuadTree<PrimitivePoint> primitiveQuadTree,
            ImmutableRTree<HydrologicalPrimitive> primitiveRTree,
            double worldOriginX,
            double worldOriginZ) {
        final Random rng = new Random(42);
        final double[] pt = new double[2];
        final List<PrimitivePoint> legacyBuffer = new ObjectArrayList<>(256);
        final List<HydrologicalPrimitive> stabBuffer = new ObjectArrayList<>(256);
        int quadTreeDeviations = 0;
        for (int i = 0; i < CROSS_CHECK_POINTS; i++) {
            pt[0] = worldOriginX + rng.nextDouble() * HydrologyTileGeometry.GRID;
            pt[1] = worldOriginZ + rng.nextDouble() * HydrologyTileGeometry.GRID;

            final Set<HydrologicalPrimitive> bruteForceHits = new HashSet<>();
            for (final HydrologicalPrimitive primitive : allPrimitives) {
                if (primitive.containsPoint(pt)) bruteForceHits.add(primitive);
            }

            stabBuffer.clear();
            final Set<HydrologicalPrimitive> stabHits = new HashSet<>(primitiveRTree.queryContaining(pt, stabBuffer));
            if (!bruteForceHits.equals(stabHits)) {
                final Set<HydrologicalPrimitive> bruteForceOnly = new HashSet<>(bruteForceHits);
                bruteForceOnly.removeAll(stabHits);
                final Set<HydrologicalPrimitive> stabOnly = new HashSet<>(stabHits);
                stabOnly.removeAll(bruteForceHits);
                LOG.error(
                        "R-tree stab mismatch at ({}, {}): brute force {} primitives, rtree {} primitives; missing {}, extra {}",
                        pt[0],
                        pt[1],
                        bruteForceHits.size(),
                        stabHits.size(),
                        bruteForceOnly,
                        stabOnly);
                throw new IllegalStateException("R-tree stab disagrees with brute force — see log");
            }

            legacyBuffer.clear();
            primitiveQuadTree.getPointsInCircle(pt, HydrologyTuning.MAX_INFLUENCE_RADIUS, legacyBuffer);
            final Set<HydrologicalPrimitive> legacyHits = new HashSet<>();
            for (final PrimitivePoint primitivePoint : legacyBuffer) {
                final HydrologicalPrimitive primitive = primitivePoint.primitive();
                if (primitive.containsPoint(pt)) legacyHits.add(primitive);
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

    /** Deterministic uniform world-frame points spanning the tile — used by the index-level benchmarks,
     *  which stab the tile's own R-tree directly in its native (world) coordinate frame. */
    private static PointSupplier worldTilePoints(long seed, double worldOriginX, double worldOriginZ) {
        final Random rng = new Random(seed);
        final double[] pt = new double[2];
        return () -> {
            pt[0] = worldOriginX + rng.nextDouble() * HydrologyTileGeometry.GRID;
            pt[1] = worldOriginZ + rng.nextDouble() * HydrologyTileGeometry.GRID;
            return pt;
        };
    }

    /** Deterministic uniform world-frame points in the tile's inner region (PROVIDER_MARGIN from borders). */
    private static PointSupplier worldInnerPoints(long seed, double worldOriginX, double worldOriginZ) {
        final Random rng = new Random(seed);
        final double span = HydrologyTileGeometry.GRID - 2 * PROVIDER_MARGIN;
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

    /** Runs {@code query} for {@link #WARMUP_NANOS} then {@link #MEASURE_NANOS}, logging queries/sec.
     *  Results feed a checksum so the JIT cannot dead-code-eliminate the timed calls. */
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
