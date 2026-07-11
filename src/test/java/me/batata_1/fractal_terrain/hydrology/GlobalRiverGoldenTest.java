package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import org.junit.jupiter.api.Test;

/**
 * Headless golden gate for the deterministic {@link GlobalRiverProvider} network build (M-004), split out
 * of the pipeline-coupled {@code debug.tests.GlobalRiverTest} manual harness (which stays as a PNG dumper).
 *
 * <p>{@code GlobalRiverTest} sources its per-tile elevation from {@code FractalTerrainInstance.pipeline}
 * — the ONNX diffusion model (~1 GB weights + GPU), which is not headless/CI-runnable. But the whole
 * per-tile river computation (threshold → border ramp → sink fill → D4 steepest-descent drainage → per-
 * source walk → flow-width → downstream-monotone bed elevation → packed result tile) is a pure function
 * of the padded elevation grid, the single pipeline-sourced input. This test exercises that exact
 * production path (via {@link GlobalRiverProvider#computeTileForTest}) over a synthetic, seeded elevation
 * field instead: the network math's correctness is a property of the heightmap it is given, independent of
 * whether that heightmap came from real terrain.
 */
class GlobalRiverGoldenTest {

    private static final int SIDE = GlobalRiverProvider.paddedSideForTest();

    /**
     * Deterministic synthetic heightmap: a below-sea base plus a handful of seeded Gaussian ridges, so the
     * field has ridge seeds (elevation ≥ the source threshold), interior valleys, and coastline (elevation
     * ≤ 0) — enough structure to drive sources → descent → coast/border outlets through every stage.
     */
    private static float[] syntheticElevation(long seed) {
        final Random rng = new Random(seed);
        final int bumps = 10;
        final double[] cx = new double[bumps];
        final double[] cz = new double[bumps];
        final double[] amp = new double[bumps];
        final double[] sigma = new double[bumps];
        for (int b = 0; b < bumps; b++) {
            cx[b] = rng.nextDouble() * SIDE;
            cz[b] = rng.nextDouble() * SIDE;
            amp[b] = 30.0 + rng.nextDouble() * 30.0; // peaks well above the ridge threshold
            sigma[b] = 6.0 + rng.nextDouble() * 8.0;
        }
        final float[] elevation = new float[SIDE * SIDE];
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                double h = -8.0; // base below sea level → coastline near the low ground
                for (int b = 0; b < bumps; b++) {
                    final double dx = i - cx[b];
                    final double dz = j - cz[b];
                    h += amp[b] * Math.exp(-(dx * dx + dz * dz) / (2.0 * sigma[b] * sigma[b]));
                }
                elevation[i * SIDE + j] = (float) h;
            }
        }
        return elevation;
    }

    /** Bit-exact checksum over all three packed channels (arrows/widths/bed elevation) of the result tile. */
    private static long tileChecksum(FloatTensor tile) {
        long checksum = 1125899906842597L;
        final int size = tile.getSize();
        for (int i = 0; i < size; i++) {
            checksum = 31 * checksum + Float.floatToIntBits(tile.get(i));
        }
        return checksum;
    }

    private static long buildAndChecksum(long seed) {
        final GlobalRiverProvider provider = new GlobalRiverProvider(null);
        final FloatTensor tile = provider.computeTileForTest(syntheticElevation(seed));
        return tileChecksum(tile);
    }

    /** The tile must actually contain rivers, so the golden isn't a trivial all-zero checksum. */
    private static void assertHasRivers(FloatTensor tile) {
        final int pixelsPerChannel = 64 * 64;
        int riverPixels = 0;
        for (int i = 0; i < pixelsPerChannel; i++) {
            if (GlobalRiverProvider.isRiver(Float.floatToIntBits(tile.get(i)))) riverPixels++;
        }
        assertTrue(riverPixels > 0, "synthetic field produced no river pixels — fixture is degenerate");
    }

    @Test
    void globalNetworkMatchesGolden() {
        final GlobalRiverProvider provider = new GlobalRiverProvider(null);
        final FloatTensor tile = provider.computeTileForTest(syntheticElevation(7));
        assertHasRivers(tile);
        assertEquals(
                GOLDEN_CHECKSUM, tileChecksum(tile), "global-river tile checksum drifted from the captured golden");
    }

    /**
     * Determinism pre-check (M-004 step 3, run before the golden above was frozen): the synthetic
     * heightmap, the network build, and the packing all derive from a fixed seed and touch no other state,
     * so 5 independent runs are expected to be — and were confirmed — bit-identical; no canonicalization or
     * tolerance was needed.
     */
    @Test
    void globalNetworkIsDeterministicAcrossRuns() {
        Long first = null;
        for (int run = 0; run < 5; run++) {
            final long checksum = buildAndChecksum(7);
            if (first == null) first = checksum;
            else assertEquals(first, checksum, "run " + run + " diverged from run 0");
        }
    }

    /** Captured by running {@link #globalNetworkMatchesGolden} once and logging it. */
    private static final long GOLDEN_CHECKSUM = -992928566221604883L;
}
