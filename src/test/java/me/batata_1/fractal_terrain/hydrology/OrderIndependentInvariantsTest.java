package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import me.batata_1.fractal_terrain.hydrology.providers.GlobalRiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import org.junit.jupiter.api.Test;

/**
 * Order-independent raster invariants over the global-river arrow field, standing in for a frozen
 * checksum that a deliberate hash-iteration-order or packing change would break for the wrong reason.
 *
 * <p>Each check is true of any correctly-built field, regardless of how {@link GlobalRiverProvider}
 * walked its sources to build it: every pixel carries at most one outgoing direction, every outgoing
 * link resolves to a river or coast pixel (or leaves the crop), every walk terminates without cycling,
 * and width is only ever set where an arrow exists. {@link GlobalRiverGoldenTest} already covers
 * run-to-run determinism and a checksum over a non-degenerate field; this class shares that fixture.
 */
class OrderIndependentInvariantsTest {

    private static final int SIDE = GlobalRiverProvider.paddedSideForTest();
    private static final int TILE = 64;
    private static final int PIXELS_PER_CHANNEL = TILE * TILE;

    /** Bails a runaway walk before it can hang the suite; mirrors {@code GlobalRiverProvider}'s own
     *  private {@code MAX_WALK_STEPS}, restated here since the field is not visible to tests. */
    private static final int MAX_WALK_STEPS = 4 * 64 * 64;

    /** Synthetic heightmap: sea-level base plus seeded Gaussian ridges, giving ridge sources, valleys and
     *  coastline enough to exercise every stage from sources to coast/border outlets. */
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

    private static FloatTensor buildTile() {
        final GlobalRiverProvider provider = new GlobalRiverProvider(null);
        return provider.computeTileForTest(syntheticElevation(7));
    }

    private static int arrowAt(FloatTensor tile, int x, int z) {
        return Float.floatToIntBits(tile.get(x * TILE + z));
    }

    private static float widthAt(FloatTensor tile, int x, int z) {
        return tile.get(PIXELS_PER_CHANNEL + x * TILE + z);
    }

    /** A packing bug that sets two direction bits for one pixel shows up here: the field models a single
     *  D8 step per cell, so more than one outgoing bit is a corrupt arrow, not a branch. */
    @Test
    void everyRiverPixelHasAtMostOneOutgoingDirection() {
        final FloatTensor tile = buildTile();
        for (int x = 0; x < TILE; x++) {
            for (int z = 0; z < TILE; z++) {
                final int arrow = arrowAt(tile, x, z);
                final int mask = GlobalRiverProvider.outgoingMask(arrow);
                assertTrue(
                        Integer.bitCount(mask) <= 1,
                        "pixel (" + x + "," + z + ") has outgoing mask " + mask + " with more than one bit set");
            }
        }
    }

    /** A dangling or mis-targeted arrow shows up here: following one outgoing bit must land on more
     *  river (the walk continues) or coast (the walk's natural end), never on dead ground. Targets that
     *  leave the 64×64 crop are a legitimate exit — the field is a crop of a larger padded grid — and are
     *  skipped rather than failed. */
    @Test
    void everyOutgoingArrowLandsOnRiverOrCoast() {
        final FloatTensor tile = buildTile();
        int checked = 0;
        for (int x = 0; x < TILE; x++) {
            for (int z = 0; z < TILE; z++) {
                final int arrow = arrowAt(tile, x, z);
                if (!GlobalRiverProvider.isRiver(arrow)) continue;
                final int mask = GlobalRiverProvider.outgoingMask(arrow);
                if (mask == 0) continue;
                final int d = Integer.numberOfTrailingZeros(mask);
                final int nx = x + Drainage.NEIGHBOR_OFFSET_X[d];
                final int nz = z + Drainage.NEIGHBOR_OFFSET_Z[d];
                if (nx < 0 || nz < 0 || nx >= TILE || nz >= TILE) continue;
                final int targetArrow = arrowAt(tile, nx, nz);
                assertTrue(
                        GlobalRiverProvider.isRiver(targetArrow) || GlobalRiverProvider.isCoast(targetArrow),
                        "pixel (" + x + "," + z + ") outgoing arrow lands on (" + nx + "," + nz
                                + "), which is neither river nor coast");
                checked++;
            }
        }
        assertTrue(checked > 1500, "only " + checked + " links checked (measured 3559) — fixture regressed");
    }

    /** A cycle in the arrow graph — two pixels each pointing at the other — would otherwise hang
     *  {@code ReliefProvider} at world-gen time; this walks every river pixel downstream and fails loudly
     *  before that, rather than looping forever. */
    @Test
    void everyRiverPixelTerminatesWithoutCycling() {
        final FloatTensor tile = buildTile();
        for (int sx = 0; sx < TILE; sx++) {
            for (int sz = 0; sz < TILE; sz++) {
                if (!GlobalRiverProvider.isRiver(arrowAt(tile, sx, sz))) continue;
                int x = sx;
                int z = sz;
                boolean terminated = false;
                for (int step = 0; step < MAX_WALK_STEPS; step++) {
                    final int arrow = arrowAt(tile, x, z);
                    final int mask = GlobalRiverProvider.outgoingMask(arrow);
                    if (mask == 0) {
                        terminated = true;
                        break;
                    }
                    final int d = Integer.numberOfTrailingZeros(mask);
                    final int nx = x + Drainage.NEIGHBOR_OFFSET_X[d];
                    final int nz = z + Drainage.NEIGHBOR_OFFSET_Z[d];
                    if (nx < 0 || nz < 0 || nx >= TILE || nz >= TILE) {
                        terminated = true;
                        break;
                    }
                    final int targetArrow = arrowAt(tile, nx, nz);
                    if (GlobalRiverProvider.isCoast(targetArrow) || GlobalRiverProvider.isSink(targetArrow)) {
                        terminated = true;
                        break;
                    }
                    x = nx;
                    z = nz;
                }
                if (!terminated) {
                    fail("walk from river pixel (" + sx + "," + sz + ") did not terminate within " + MAX_WALK_STEPS
                            + " steps — likely a cycle");
                }
            }
        }
    }

    /** Width is derived from flow accumulation only where the arrow packer actually visited the pixel;
     *  an unvisited pixel keeping a stale nonzero width would silently widen a river that was never
     *  traced there. */
    @Test
    void widthIsZeroWhereThereIsNoArrow() {
        final FloatTensor tile = buildTile();
        for (int x = 0; x < TILE; x++) {
            for (int z = 0; z < TILE; z++) {
                final int arrow = arrowAt(tile, x, z);
                if (arrow != 0) continue;
                assertEquals(0f, widthAt(tile, x, z), "pixel (" + x + "," + z + ") has no arrow but nonzero width");
            }
        }
    }

    /** Pins a floor on river and source pixel counts so a future change that empties the field fails
     *  here, loudly, rather than making the other four invariants pass vacuously over zero pixels. */
    @Test
    void fieldIsNonDegenerate() {
        final FloatTensor tile = buildTile();
        int riverPixels = 0;
        int sourcePixels = 0;
        for (int i = 0; i < PIXELS_PER_CHANNEL; i++) {
            final int arrow = Float.floatToIntBits(tile.get(i));
            if (GlobalRiverProvider.isRiver(arrow)) riverPixels++;
            if (GlobalRiverProvider.isSource(arrow)) sourcePixels++;
        }
        assertTrue(riverPixels > 1500, "only " + riverPixels + " river pixels (measured 3595) — field is degenerate");
        assertTrue(
                sourcePixels > 1200, "only " + sourcePixels + " source pixels (measured 2745) — field is degenerate");
    }
}
