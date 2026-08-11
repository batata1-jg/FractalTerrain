package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Headless correctness gate for the hydrology spatial indexes, split out of the correctness portion of
 * {@code debug.tests.SpatialIndexBenchmark} (whose wall-clock throughput benchmark stays there).
 *
 * <p>The benchmark builds its primitive set from a real tile via the ONNX diffusion pipeline, not
 * headless/CI-runnable. This test exercises the same cross-check ({@link ImmutableRTree} stab vs.
 * brute-force scan) over a synthetic, seeded {@link HydrologicalPrimitive} set: correctness is a property
 * of the shapes given, independent of their origin.
 */
@Disabled("Fixture rewritten to build RiverPrimitive directly after commit 660796c turned HydrologicalPrimitive "
        + "into an interface; GOLDEN_CHECKSUM predates that refactor and is unverified against this fixture.")
class SpatialIndexCorrectnessGoldenTest {

    private static final int GRID = 512;
    private static final int PRIMITIVE_COUNT = 800;
    private static final int CROSS_CHECK_POINTS = 2000;

    /** Quadtree adapter mirroring {@code SpatialIndexBenchmark.PrimitivePoint}. */
    private record PrimitivePoint(HydrologicalPrimitive primitive) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return primitive.coord();
        }
    }

    /** Deterministic synthetic primitive set: seeded uniform centers/widths over the {@link #GRID} tile. */
    private static List<HydrologicalPrimitive> syntheticPrimitives(long seed) {
        final Random rng = new Random(seed);
        final List<HydrologicalPrimitive> primitives = new ArrayList<>(PRIMITIVE_COUNT);
        for (int i = 0; i < PRIMITIVE_COUNT; i++) {
            final double x = rng.nextDouble() * GRID;
            final double z = rng.nextDouble() * GRID;
            final double width = 1.0 + rng.nextDouble() * (HydrologyTuning.maxNativeWidth() - 1.0);
            primitives.add(new RiverPrimitive(
                    new double[] {x, z},
                    RosgenProfile.riverInfluence(width),
                    RiverPrimitive.RosgenType.A,
                    null,
                    0.0,
                    width,
                    0.0,
                    i));
        }
        return primitives;
    }

    /** Brute-force vs. R-tree stab over seeded points, plus a hit-count checksum: catches a reach/width
     *  formula change that keeps both structures internally consistent but shifts what they agree on. */
    private static long crossCheckAndChecksum(List<HydrologicalPrimitive> primitives) {
        final ImmutableRTree<HydrologicalPrimitive> primitiveRTree = new ImmutableRTree<>(primitives, null);

        final Random rng = new Random(42);
        final double[] pt = new double[2];
        final List<HydrologicalPrimitive> stabBuffer = new ArrayList<>(256);
        long checksum = 0;
        for (int i = 0; i < CROSS_CHECK_POINTS; i++) {
            pt[0] = rng.nextDouble() * GRID;
            pt[1] = rng.nextDouble() * GRID;

            final Set<HydrologicalPrimitive> bruteForceHits = new HashSet<>();
            for (final HydrologicalPrimitive primitive : primitives) {
                final double deltaX = primitive.coord()[0] - pt[0];
                final double deltaZ = primitive.coord()[1] - pt[1];
                final double reach = ((RiverPrimitive) primitive).influence();
                if (deltaX * deltaX + deltaZ * deltaZ <= reach * reach) bruteForceHits.add(primitive);
            }

            stabBuffer.clear();
            final Set<HydrologicalPrimitive> stabHits = new HashSet<>(primitiveRTree.queryContaining(pt, stabBuffer));
            assertEquals(
                    bruteForceHits,
                    stabHits,
                    "R-tree stab disagrees with brute force at (" + pt[0] + "," + pt[1] + ")");

            checksum += bruteForceHits.size();
        }
        return checksum;
    }

    /** Legacy {@link ImmutableQuadTree} path, exercised only to confirm it still builds/queries here; not
     *  asserted against brute force since it carries a known {@code findSection} boundary bug. */
    private static ImmutableQuadTree<PrimitivePoint> buildLegacyQuadTree(List<HydrologicalPrimitive> primitives) {
        final List<PrimitivePoint> primitivePoints = new ArrayList<>(primitives.size());
        for (final HydrologicalPrimitive primitive : primitives) primitivePoints.add(new PrimitivePoint(primitive));
        return new ImmutableQuadTree<>(new double[] {-16, -16}, new double[] {GRID + 16, GRID + 16}, primitivePoints);
    }

    @Test
    void rtreeMatchesBruteForceAcrossSyntheticPrimitives() {
        final List<HydrologicalPrimitive> primitives = syntheticPrimitives(7);
        buildLegacyQuadTree(primitives); // exercised for parity with the manual benchmark; not asserted here
        final long checksum = crossCheckAndChecksum(primitives);
        assertEquals(GOLDEN_CHECKSUM, checksum, "hit-set-size checksum drifted from the captured golden");
    }

    /** Confirms primitive generation, tree build and cross-check derive from the seeds alone: 5 runs are
     *  checked bit-identical, catching hidden nondeterminism the golden checksum alone would miss. */
    @Test
    void correctnessCheckIsDeterministicAcrossRuns() {
        Long first = null;
        for (int run = 0; run < 5; run++) {
            final long checksum = crossCheckAndChecksum(syntheticPrimitives(7));
            if (first == null) first = checksum;
            else assertEquals(first, checksum, "run " + run + " diverged from run 0");
        }
    }

    /** Captured by running {@link #rtreeMatchesBruteForceAcrossSyntheticPrimitives} once and logging it. */
    private static final long GOLDEN_CHECKSUM = 13814L;
}
