package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import org.junit.jupiter.api.Test;

/**
 * Headless golden gate for the deterministic local-network trace of {@link LocalRiverProvider} (M-004),
 * split out of the pipeline-coupled {@code debug.tests.LocalRiverTest} manual harness (which stays as a
 * PNG dumper).
 *
 * <p>{@code LocalRiverTest} builds a whole tile, whose two inputs — the ONNX-decoded terrain
 * ({@code DecoderChannels.decode}) and a {@link GlobalRiverProvider} whose arrows derive from the coarse
 * diffusion elevation — both originate in the ONNX pipeline (~1 GB weights + GPU), so it is not
 * headless/CI-runnable. The provider's <em>unique</em> deterministic core, however, is the local-network
 * trace ({@link LocalRiverProvider#traceLocalNetworkForTest flow accumulation → reach test → segment walk
 * → channel build}), a pure function of a drainage field, an elevation grid, and a global-river mask. This
 * test exercises that exact production path over a synthetic, seeded elevation field draining into a
 * central global-river trunk band (the mask), computing its drainage the same way {@code buildTile} does
 * via {@link PipelinePreprocessing#computeDrainageDirection}.
 */
class LocalRiverGoldenTest {

    private static final int GRID = LocalRiverProvider.gridSizeForTest();

    /** Half-width (rows) of the central global-river trunk band. */
    private static final int TRUNK_HALF = 2;
    /** Distance (rows) from the trunk to each ridge crest (the watershed / source line). */
    private static final int RIDGE_OFFSET = 80;

    /**
     * Deterministic synthetic heightmap over the {@link #GRID} tile, shaped as a symmetric double-ridge
     * valley (a function of the row distance {@code a = |i - GRID/2|} plus seeded noise):
     * <ul>
     *   <li>a central valley at {@code i = GRID/2} (the global-river trunk, {@code +2}),
     *   <li>rising to a ridge crest at {@code a = RIDGE_OFFSET} ({@code +60}) — the interior source line,
     *   <li>then falling back toward the border ({@code +20}).
     * </ul>
     * The profile is piecewise-monotonic (no enclosed pits for {@code fillSinks} to flood), and the valley
     * connects to the tile border along its ends so it is never flooded either. Cells on the inner limb
     * drain to the trunk; cells on the outer limb drain to the border (land, so no outlet → no rivers).
     * Outlets are supplied by {@link #trunkMask()} exactly as production local rivers drain into the global
     * network, so channels run ridge → trunk, both interior, and survive {@code leavesTile}.
     */
    private static float[] syntheticElevation(long seed) {
        final Random rng = new Random(seed);
        final double mid = GRID / 2.0;
        final float[] elevation = new float[GRID * GRID];
        for (int i = 0; i < GRID; i++) {
            final double a = Math.abs(i - mid);
            final double h;
            if (a <= RIDGE_OFFSET) {
                h = 2.0 + 58.0 * (a / RIDGE_OFFSET); // trunk (+2) → ridge crest (+60)
            } else {
                h = 60.0 - 40.0 * ((a - RIDGE_OFFSET) / (mid - RIDGE_OFFSET)); // crest (+60) → border (+20)
            }
            for (int j = 0; j < GRID; j++) {
                elevation[i * GRID + j] = (float) (h + (rng.nextDouble() - 0.5) * 3.0); // tie-breaking noise
            }
        }
        return elevation;
    }

    /** The central global-river trunk band (rows within {@link #TRUNK_HALF} of {@code GRID/2}): the outlet
     *  the reach test seeds from, and the cells the local network drains toward but excludes. */
    private static boolean[] trunkMask() {
        final int mid = GRID / 2;
        final boolean[] mask = new boolean[GRID * GRID];
        for (int i = Math.max(0, mid - TRUNK_HALF); i <= mid + TRUNK_HALF && i < GRID; i++) {
            for (int j = 0; j < GRID; j++) mask[i * GRID + j] = true;
        }
        return mask;
    }

    /** Steepest-descent drainage over {@code filled}, matching {@code LocalRiverProvider.buildTile}. */
    private static int[] drainageOf(float[] filled) {
        final float[] uniformWeight = new float[GRID * GRID];
        Arrays.fill(uniformWeight, 1f);
        return PipelinePreprocessing.computeDrainageDirection(filled, uniformWeight, GRID);
    }

    /** Bit-exact checksum over the traced channel geometry (count + every resampled spline coordinate). */
    private static long channelChecksum(List<Channel> channels) {
        long checksum = 1125899906842597L;
        checksum = 31 * checksum + channels.size();
        for (final Channel channel : channels) {
            checksum = 31 * checksum + channel.channelId;
            for (final double[] point : channel.spline.points()) {
                checksum = 31 * checksum + Double.doubleToLongBits(point[0]);
                checksum = 31 * checksum + Double.doubleToLongBits(point[1]);
            }
        }
        return checksum;
    }

    private static List<Channel> trace(long seed) {
        final LocalRiverProvider provider = new LocalRiverProvider(null);
        // buildTile computes drainage on the sink-FILLED elevation and passes that same filled field to the
        // trace, so flow reaches outlets rather than stalling in interior depressions; mirror that here.
        final float[] filled = PipelinePreprocessing.fillSinks(syntheticElevation(seed), GRID, 0);
        final int[] drainage = drainageOf(filled);
        return provider.traceLocalNetworkForTest(drainage, filled, trunkMask());
    }

    @Test
    void localNetworkMatchesGolden() {
        final List<Channel> channels = trace(7);
        assertTrue(channels.size() > 0, "synthetic field produced no local channels — fixture is degenerate");
        assertEquals(
                GOLDEN_CHECKSUM,
                channelChecksum(channels),
                "local-network channel checksum drifted from the captured golden");
    }

    /**
     * Determinism pre-check (M-004 step 3, run before the golden above was frozen): the synthetic
     * heightmap, drainage, and trace all derive from a fixed seed and touch no other state, so 5
     * independent runs are expected to be — and were confirmed — bit-identical; no canonicalization or
     * tolerance was needed.
     */
    @Test
    void localNetworkIsDeterministicAcrossRuns() {
        Long first = null;
        for (int run = 0; run < 5; run++) {
            final long checksum = channelChecksum(trace(7));
            if (first == null) first = checksum;
            else assertEquals(first, checksum, "run " + run + " diverged from run 0");
        }
    }

    /** Captured by running {@link #localNetworkMatchesGolden} once and logging it. */
    private static final long GOLDEN_CHECKSUM = 181642854564706469L;
}
