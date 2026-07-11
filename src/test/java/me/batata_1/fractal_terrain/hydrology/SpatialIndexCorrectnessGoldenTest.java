package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import org.junit.jupiter.api.Test;

/**
 * Headless correctness gate for the hydrology spatial indexes, split out of the CORRECTNESS portion of
 * {@code debug.tests.SpatialIndexBenchmark} (M-004) — the wall-clock throughput benchmark itself stays
 * in that class as a manual harness.
 *
 * <p>{@code SpatialIndexBenchmark} builds its unit set from a real {@code LocalRiverProvider} tile,
 * which reads elevation via {@code GlobalRiverProvider} off {@code FractalTerrainInstance.pipeline} —
 * the ONNX diffusion pipeline (~1 GB weights + GPU). That is not headless/CI-runnable, so this test
 * exercises the same cross-check ({@link ImmutableRTree} stab vs. a brute-force linear scan) over a
 * synthetic, seeded {@link HydrologicalUnit} set instead: the index structures' correctness is a
 * property of the shapes they are given, independent of whether those shapes came from real terrain.
 */
class SpatialIndexCorrectnessGoldenTest {

    private static final int GRID = 512;
    private static final int UNIT_COUNT = 800;
    private static final int CROSS_CHECK_POINTS = 2000;

    /** Quadtree adapter mirroring {@code SpatialIndexBenchmark.UnitPoint}. */
    private record UnitPoint(HydrologicalUnit unit) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return unit.coord();
        }
    }

    /** Deterministic synthetic unit set: seeded uniform centers/widths over the {@link #GRID} tile. */
    private static List<HydrologicalUnit> syntheticUnits(long seed) {
        final Random rng = new Random(seed);
        final List<HydrologicalUnit> units = new ArrayList<>(UNIT_COUNT);
        for (int i = 0; i < UNIT_COUNT; i++) {
            final double x = rng.nextDouble() * GRID;
            final double z = rng.nextDouble() * GRID;
            final double width = 1.0 + rng.nextDouble() * (FractalTerrainConfig.maxNativeWidth() - 1.0);
            units.add(new HydrologicalUnit(
                    HydrologicalFeature.RIVER, RosgenType.A, new double[] {x, z}, null, width, 0.0, 0, i));
        }
        return units;
    }

    /**
     * Brute-force vs. R-tree stab over {@link #CROSS_CHECK_POINTS} seeded points (throws via the
     * assertion on the first mismatch, mirroring {@code SpatialIndexBenchmark}'s
     * {@code crossCheckInfluenceQueries}), plus a hit-count checksum: a change to the reach/width
     * formula that keeps the R-tree and brute force internally consistent with each other (so the
     * per-point assertion alone would not catch it) still moves the checksum.
     */
    private static long crossCheckAndChecksum(List<HydrologicalUnit> units) {
        final ImmutableRTree<HydrologicalUnit> unitRTree = new ImmutableRTree<>(units, null);

        final Random rng = new Random(42);
        final double[] pt = new double[2];
        final List<HydrologicalUnit> stabBuffer = new ArrayList<>(256);
        long checksum = 0;
        for (int i = 0; i < CROSS_CHECK_POINTS; i++) {
            pt[0] = rng.nextDouble() * GRID;
            pt[1] = rng.nextDouble() * GRID;

            final Set<HydrologicalUnit> bruteForceHits = new HashSet<>();
            for (final HydrologicalUnit unit : units) {
                final double deltaX = unit.coord()[0] - pt[0];
                final double deltaZ = unit.coord()[1] - pt[1];
                final double reach = unit.getRadius();
                if (deltaX * deltaX + deltaZ * deltaZ <= reach * reach) bruteForceHits.add(unit);
            }

            stabBuffer.clear();
            final Set<HydrologicalUnit> stabHits = new HashSet<>(unitRTree.queryContaining(pt, stabBuffer));
            assertEquals(
                    bruteForceHits,
                    stabHits,
                    "R-tree stab disagrees with brute force at (" + pt[0] + "," + pt[1] + ")");

            checksum += bruteForceHits.size();
        }
        return checksum;
    }

    /** The legacy {@link ImmutableQuadTree} path, retained to confirm it still builds/queries over the
     *  synthetic set (mirrors {@code SpatialIndexBenchmark}'s legacy path; not asserted against brute
     *  force here — it carries a known {@code findSection} boundary misclassification, documented on
     *  the benchmark). */
    private static ImmutableQuadTree<UnitPoint> buildLegacyQuadTree(List<HydrologicalUnit> units) {
        final List<UnitPoint> unitPoints = new ArrayList<>(units.size());
        for (final HydrologicalUnit unit : units) unitPoints.add(new UnitPoint(unit));
        return new ImmutableQuadTree<>(new double[] {-16, -16}, new double[] {GRID + 16, GRID + 16}, unitPoints);
    }

    @Test
    void rtreeMatchesBruteForceAcrossSyntheticUnits() {
        final List<HydrologicalUnit> units = syntheticUnits(7);
        buildLegacyQuadTree(units); // exercised for parity with the manual benchmark; not asserted here
        final long checksum = crossCheckAndChecksum(units);
        assertEquals(GOLDEN_CHECKSUM, checksum, "hit-set-size checksum drifted from the captured golden");
    }

    /**
     * Determinism pre-check (M-004 step 3, run before the golden fixture above was frozen): the
     * synthetic unit generation, tree build, and cross-check all derive from fixed {@link Random} seeds
     * and touch no other state, so 5 independent runs are expected to be — and were confirmed —
     * bit-identical; no canonicalization or tolerance was needed.
     */
    @Test
    void correctnessCheckIsDeterministicAcrossRuns() {
        Long first = null;
        for (int run = 0; run < 5; run++) {
            final long checksum = crossCheckAndChecksum(syntheticUnits(7));
            if (first == null) first = checksum;
            else assertEquals(first, checksum, "run " + run + " diverged from run 0");
        }
    }

    /** Captured by running {@link #rtreeMatchesBruteForceAcrossSyntheticUnits} once and logging it. */
    private static final long GOLDEN_CHECKSUM = 13814L;
}
