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
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.providers.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.SpatialHashGrid;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
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

    /** Zero-radius adapter mirroring {@code RiverNetwork.CrossingPoint}, so a {@link Channel.ChannelPt}
     *  can be stored in an {@link ImmutableRTree} stab query for this benchmark's own comparison. */
    private record ChannelPointCircle(Channel.ChannelPt pt) implements SpatialIndexCircle {
        @Override
        public double[] getCenter() {
            return pt.toArray();
        }

        @Override
        public double getRadius() {
            return 0.0;
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

        // ---- RiverNetwork spatial-index migration (2026-09-03 design): detectCrossings -> ImmutableRTree,
        // detectAndApplyCutoffs -> SpatialHashGrid, both against one real tile's channel set ------------
        LOG.info("building RiverNetwork debug stages for the crossing/cutoff benchmark...");
        final RiverProvider.Stages riverNetworkStages = localRivers.debugStages(TILE_X, TILE_Z);
        benchDetectCrossingsCandidateGeneration(riverNetworkStages.network);
        benchManageCutoffsOpMix(riverNetworkStages.network);

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

    /** D1's regression gate: the R-tree candidate set must exactly match the QuadTree baseline
     *  {@code detectCrossings} used to build, over real query radii from a real tile's channels. Then
     *  queries/sec for each, at a representative radius. */
    private static void benchDetectCrossingsCandidateGeneration(RiverNetwork network) {
        final List<Channel.ChannelPt> channelPts = new ObjectArrayList<>();
        double maxHalf = 0.0;
        for (final Channel ch : network.getChannels()) {
            for (final Channel.ChannelPt pt : ch.getChannelAsPts()) channelPts.add(pt);
            for (int i = 0; i < ch.numPts(); i++)
                maxHalf = Math.max(maxHalf, ChannelGeometry.bedHalfWidth(ch.widthAt(i)));
        }
        if (channelPts.isEmpty()) {
            LOG.warn("detectCrossings benchmark skipped: tile has no channel points");
            return;
        }
        LOG.info("detectCrossings benchmark: {} channels, {} points", network.getChannelCount(), channelPts.size());

        final QuadTree<Channel.ChannelPt> quadTree = new QuadTree<>(new double[] {-1e3, -1e3}, new double[] {1e3, 1e3});
        for (final Channel.ChannelPt pt : channelPts) quadTree.insertPoint(pt);

        final List<ChannelPointCircle> circles = new ObjectArrayList<>(channelPts.size());
        for (final Channel.ChannelPt pt : channelPts) circles.add(new ChannelPointCircle(pt));
        final ImmutableRTree<ChannelPointCircle> rtree = new ImmutableRTree<>(circles, null);

        final Random rng = new Random(7);
        final List<ChannelPointCircle> stabBuffer = new ObjectArrayList<>(64);
        int mismatches = 0;
        for (int i = 0; i < CROSS_CHECK_POINTS; i++) {
            final Channel.ChannelPt query = channelPts.get(rng.nextInt(channelPts.size()));
            final double radius = ChannelGeometry.bedHalfWidth(query.width()) + maxHalf;

            final Set<Channel.ChannelPt> quadHits = new HashSet<>(quadTree.getPointsInCircle(query.toArray(), radius));

            stabBuffer.clear();
            rtree.queryContaining(query.toArray(), radius, stabBuffer);
            final Set<Channel.ChannelPt> stabHits = new HashSet<>();
            for (final ChannelPointCircle circle : stabBuffer) stabHits.add(circle.pt());

            if (!quadHits.equals(stabHits)) mismatches++;
        }
        if (mismatches > 0)
            throw new IllegalStateException("detectCrossings R-tree candidate set disagreed with the QuadTree"
                    + " baseline on " + mismatches + " of " + CROSS_CHECK_POINTS + " points");
        LOG.info(
                "detectCrossings cross-check passed: R-tree candidates match QuadTree on {} points",
                CROSS_CHECK_POINTS);

        final double benchRadius = maxHalf * 2;
        final Random quadRng = new Random(8);
        bench(
                "detectCrossings quadtree candidate query",
                () -> channelPts.get(quadRng.nextInt(channelPts.size())).toArray(),
                pt -> quadTree.getPointsInCircle(pt, benchRadius).size());

        final Random rtreeRng = new Random(9);
        final List<ChannelPointCircle> queryBuffer = new ObjectArrayList<>(64);
        bench(
                "detectCrossings rtree candidate query",
                () -> channelPts.get(rtreeRng.nextInt(channelPts.size())).toArray(),
                pt -> {
                    queryBuffer.clear();
                    return rtree.queryContaining(pt, benchRadius, queryBuffer).size();
                });
    }

    /** Minimal view over {@code detectAndApplyCutoffs}'s four operations, so the same walk exercises the
     *  QuadTree baseline and the SpatialHashGrid replacement without duplicating the algorithm. */
    private interface CutoffIndex {
        void insert(Channel.ChannelPt pt);

        void remove(Channel.ChannelPt pt);

        boolean contains(Channel.ChannelPt pt);

        List<Channel.ChannelPt> closeTo(Channel.ChannelPt pt, double radius);
    }

    private record QuadTreeCutoffIndex(QuadTree<Channel.ChannelPt> tree) implements CutoffIndex {
        @Override
        public void insert(Channel.ChannelPt pt) {
            tree.insertPoint(pt);
        }

        @Override
        public void remove(Channel.ChannelPt pt) {
            tree.removePoint(pt);
        }

        @Override
        public boolean contains(Channel.ChannelPt pt) {
            return tree.containsPoint(pt);
        }

        @Override
        public List<Channel.ChannelPt> closeTo(Channel.ChannelPt pt, double radius) {
            return tree.getPointsInCircle(pt.toArray(), radius);
        }
    }

    private record HashGridCutoffIndex(SpatialHashGrid<Channel.ChannelPt> grid) implements CutoffIndex {
        @Override
        public void insert(Channel.ChannelPt pt) {
            grid.insertPoint(pt);
        }

        @Override
        public void remove(Channel.ChannelPt pt) {
            grid.removePoint(pt);
        }

        @Override
        public boolean contains(Channel.ChannelPt pt) {
            return grid.containsPoint(pt);
        }

        @Override
        public List<Channel.ChannelPt> closeTo(Channel.ChannelPt pt, double radius) {
            return grid.getPointsInCircle(pt.toArray(), radius);
        }
    }

    /** Replicates {@code RiverNetwork.detectAndApplyCutoffs}'s walk exactly (insert the channel, walk id
     *  order, query-then-cut), parameterized over {@link CutoffIndex} so the same algorithm exercises
     *  either structure. Returns the surviving indexes {@code detectAndApplyCutoffs} would keep. */
    private static List<Integer> runCutoffWalk(CutoffIndex index, Channel.ChannelPt[] pts, int channelId) {
        for (Channel.ChannelPt pt : pts) index.insert(pt);
        final List<Integer> keptIndexes = new ObjectArrayList<>();
        for (int id = 0; id < pts.length - 1; id++) {
            if (!index.contains(pts[id])) continue;
            keptIndexes.add(id);
            final List<Channel.ChannelPt> close = index.closeTo(pts[id], Math.sqrt(pts[id].width()));
            close.sort(null);
            for (Channel.ChannelPt cpt : close) {
                if (cpt.index() <= id + 1 || cpt.channelId() != channelId) continue;
                for (int i = id; i < cpt.index(); i++) index.remove(pts[i]);
            }
        }
        keptIndexes.add(pts.length - 1);
        return keptIndexes;
    }

    /** D2's regression gate + throughput comparison: same detectAndApplyCutoffs walk, QuadTree baseline vs
     *  SpatialHashGrid replacement, over the tile's largest channel (most insert/remove/query traffic). */
    private static void benchManageCutoffsOpMix(RiverNetwork network) {
        Channel largest = null;
        for (final Channel ch : network.getChannels())
            if (largest == null || ch.numPts() > largest.numPts()) largest = ch;
        if (largest == null) {
            LOG.warn("detectAndApplyCutoffs benchmark skipped: tile has no channels");
            return;
        }
        final Channel.ChannelPt[] pts = largest.getChannelAsPts();
        final int channelId = largest.channelId;
        final double cellSize = Math.ceil(Math.sqrt(HydrologyTuning.maxNativeWidth()));

        final List<Integer> quadKept = runCutoffWalk(
                new QuadTreeCutoffIndex(new QuadTree<>(new double[] {-1e3, -1e3}, new double[] {1e3, 1e3})),
                pts,
                channelId);
        final List<Integer> hashKept =
                runCutoffWalk(new HashGridCutoffIndex(new SpatialHashGrid<>(cellSize)), pts, channelId);
        if (!quadKept.equals(hashKept))
            throw new IllegalStateException("detectAndApplyCutoffs SpatialHashGrid walk diverged from the QuadTree"
                    + " baseline: quadtree kept " + quadKept + ", hashgrid kept " + hashKept);
        LOG.info(
                "detectAndApplyCutoffs cross-check passed: SpatialHashGrid kept indexes match QuadTree ({} points)",
                pts.length);

        benchOp(
                "detectAndApplyCutoffs quadtree op-mix (channel " + channelId + ", " + pts.length + " points)",
                () -> runCutoffWalk(
                        new QuadTreeCutoffIndex(new QuadTree<>(new double[] {-1e3, -1e3}, new double[] {1e3, 1e3})),
                        pts,
                        channelId));
        benchOp(
                "detectAndApplyCutoffs hashgrid op-mix (channel " + channelId + ", " + pts.length + " points)",
                () -> runCutoffWalk(new HashGridCutoffIndex(new SpatialHashGrid<>(cellSize)), pts, channelId));
    }

    /** {@link #bench} for a niladic op-mix pass (insert/remove/query all inside one call) instead of a
     *  single query point — used by the detectAndApplyCutoffs benchmark, which rebuilds its structure per
     *  call exactly as {@code RiverNetwork.detectAndApplyCutoffs} does. */
    private static void benchOp(String label, Runnable op) {
        final long warmupEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmupEnd) op.run();

        long ops = 0;
        final long start = System.nanoTime();
        final long measureEnd = start + MEASURE_NANOS;
        long now;
        while ((now = System.nanoTime()) < measureEnd) {
            op.run();
            ops++;
        }
        final double seconds = (now - start) / 1e9;
        LOG.info(
                "{}: {} ops/sec ({} ops in {}s)",
                label,
                Math.round(ops / seconds),
                ops,
                String.format("%.2f", seconds));
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
