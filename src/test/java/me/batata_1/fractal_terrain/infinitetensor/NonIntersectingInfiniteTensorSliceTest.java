package me.batata_1.fractal_terrain.infinitetensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code getSlice} to the per-pixel {@code getValue} it replaces, and pins the cache budget to
 * the insert path. Runs on a synthetic in-memory tensor ({@code path == null}), so no model, no ONNX
 * and no world are involved.
 */
class NonIntersectingInfiniteTensorSliceTest {

    private static final int CHANNELS = 3;
    private static final int SIDE = 8;

    /** Distinct per (channel, global x, global z) so a mis-indexed read cannot coincide with a right one. */
    private static float expected(int ch, int gx, int gz) {
        return ch * 1_000_003f + gx * 31f + gz;
    }

    private final Set<TileKey> built = new LinkedHashSet<>();

    private NonIntersectingInfiniteTensor tensor(long cacheLimitBytes) {
        return new NonIntersectingInfiniteTensor(
                null,
                "synthetic",
                new int[] {CHANNELS, SIDE, SIDE},
                key -> {
                    built.add(key);
                    final int tileX = key.get(1);
                    final int tileZ = key.get(2);
                    final float[] entries = new float[CHANNELS * SIDE * SIDE];
                    for (int ch = 0; ch < CHANNELS; ch++) {
                        for (int ix = 0; ix < SIDE; ix++) {
                            for (int iz = 0; iz < SIDE; iz++) {
                                entries[(ch * SIDE + ix) * SIDE + iz] =
                                        expected(ch, tileX * SIDE + ix, tileZ * SIDE + iz);
                            }
                        }
                    }
                    return new FloatTensor(entries, new int[] {CHANNELS, SIDE, SIDE});
                },
                cacheLimitBytes);
    }

    private void assertSliceMatchesGetValue(int ch, int x0, int z0, int x1, int z1) {
        final NonIntersectingInfiniteTensor t = tensor(Long.MAX_VALUE);
        final FloatTensor slice = t.getSlice(new int[] {ch, x0, z0}, new int[] {ch + 1, x1, z1});
        final int rowStride = z1 - z0;
        for (int gx = x0; gx < x1; gx++) {
            for (int gz = z0; gz < z1; gz++) {
                assertEquals(
                        t.getValue(new int[] {ch, gx, gz}),
                        slice.get((gx - x0) * rowStride + (gz - z0)),
                        "at (" + ch + "," + gx + "," + gz + ")");
            }
        }
    }

    @Test
    void sliceInsideOneTileMatchesGetValue() {
        assertSliceMatchesGetValue(1, 2, 3, 6, 7);
    }

    @Test
    void sliceAcrossATileBoundaryMatchesGetValue() {
        assertSliceMatchesGetValue(2, 6, 6, 11, 11);
    }

    @Test
    void sliceAtNegativeCoordinatesMatchesGetValue() {
        assertSliceMatchesGetValue(0, -10, -3, -5, 2);
    }

    @Test
    void sliceTouchesOnlyTheTilesItOverlaps() {
        final NonIntersectingInfiniteTensor t = tensor(Long.MAX_VALUE);
        built.clear();
        // 8..15 is exactly tile 1 on both axes: a floor/floor+1 sampler would also drag in tile 2.
        t.getSlice(new int[] {0, 8, 8}, new int[] {1, 16, 16});
        assertEquals(1, built.size(), "built " + built);
    }

    @Test
    void cacheLimitEvictsOnInsertSoGetValueReadersAreBoundedToo() {
        final long tileBytes = (long) CHANNELS * SIDE * SIDE * Float.BYTES;
        final NonIntersectingInfiniteTensor t = tensor(2 * tileBytes);
        for (int tileX = 0; tileX < 6; tileX++) {
            t.getValue(new int[] {0, tileX * SIDE, 0});
        }
        built.clear();
        // Tile 0 is long evicted under a 2-tile budget, so reading it again rebuilds it.
        t.getValue(new int[] {0, 0, 0});
        assertEquals(1, built.size(), "expected tile 0 to be rebuilt, built " + built);
    }
}
