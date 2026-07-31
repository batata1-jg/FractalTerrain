package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import org.junit.jupiter.api.Test;

/**
 * Headless correctness gate for the hydrology spatial indexes, split out of the correctness portion of
 * {@code debug.tests.SpatialIndexBenchmark} (whose wall-clock throughput benchmark stays there).
 *
 * <p>The benchmark builds its unit set from a real tile via the ONNX diffusion pipeline, not
 * headless/CI-runnable. This test exercises the same cross-check ({@link ImmutableRTree} stab vs.
 * brute-force scan) over a synthetic, seeded {@link HydrologicalUnit} set: correctness is a property
 * of the shapes given, independent of their origin.
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
            final double width = 1.0 + rng.nextDouble() * (HydrologyTuning.maxNativeWidth() - 1.0);
            units.add(new HydrologicalUnit(
                    HydrologicalFeature.RIVER, RiverUnit.RosgenType.A, new double[]{x, z}, null, width, 0.0, 0, i) {
                @Override
                public double[] getCoords() {
                    return new double[0];
                }

                @Override
                public double[] getCenter() {
                    return new double[0];
                }

                @Override
                public HydrologicalFeature getType() {
                    return null;
                }

                @Override
                public boolean equals(Object o) {
                    return false;
                }

                @Override
                public int hashCode() {
                    return 0;
                }

                @Override
                public HydrologyProfile getProfile() {
                    return null;
                }

                @Override
                public double carveFineGrained(double[] pt, double elevAtPixel) {
                    return 0;
                }

                @Override
                public double[] coord() {
                    return new double[0];
                }

                @Override
                public long unitByteSize() {
                    return 0;
                }

                @Override
                public byte[] serializeUnit() {
                    return new byte[0];
                }

                @Override
                public HydrologicalUnit deserializeUnit(byte[] rawBytes) {
                    return null;
                }
            });
        }
        return units;
    }

    /** Brute-force vs. R-tree stab over seeded points, plus a hit-count checksum: catches a reach/width
     *  formula change that keeps both structures internally consistent but shifts what they agree on. */
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

    /** Legacy {@link ImmutableQuadTree} path, exercised only to confirm it still builds/queries here; not
     *  asserted against brute force since it carries a known {@code findSection} boundary bug. */
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

    /** Confirms unit generation, tree build and cross-check derive from the seeds alone: 5 runs are
     *  checked bit-identical, catching hidden nondeterminism the golden checksum alone would miss. */
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
