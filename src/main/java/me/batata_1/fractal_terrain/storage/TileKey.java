package me.batata_1.fractal_terrain.storage;

import java.util.Arrays;

/**
 * Immutable integer-tuple key for {@link Storage} tiles.
 *
 * <p>Exists to keep cache lookups allocation-free: the boxed {@code List<Integer>} keys it replaced
 * allocated on every access, and there are roughly two dozen lookups per generated block.
 *
 * <p>The backing array is copied on construction, which is what lets callers pass a reused scratch
 * array — {@code InfiniteTensor#iterateWindows} depends on that.
 */
public final class TileKey {

    private final int[] idx;
    private final int hash;

    public TileKey(int[] idx) {
        this.idx = idx.clone();
        this.hash = Arrays.hashCode(this.idx);
    }

    /** Number of coordinates (the tensor rank). */
    public int rank() {
        return idx.length;
    }

    /** Coordinate at axis {@code d}. */
    public int get(int d) {
        return idx[d];
    }

    /** A fresh copy of the backing coordinates. */
    public int[] toIntArray() {
        return idx.clone();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TileKey k && Arrays.equals(idx, k.idx));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return Arrays.toString(idx);
    }
}
