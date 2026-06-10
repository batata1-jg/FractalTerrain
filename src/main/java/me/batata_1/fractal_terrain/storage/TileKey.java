package me.batata_1.fractal_terrain.storage;

import java.util.Arrays;

/**
 * Immutable integer-tuple key for {@link Storage} tiles.
 *
 * <p>Replaces the former {@code List<Integer>} keys, which boxed every coordinate and allocated an
 * {@code ArrayList} on <em>every</em> cache access (e.g. ~24 lookups per generated block). A
 * {@code TileKey} is a single object wrapping one {@code int[]} plus a precomputed hash — no
 * per-element boxing.
 *
 * <p>The backing array is defensively copied on construction, so callers may hand in (and keep
 * mutating) a reused scratch array — this is what makes the non-cloning window iteration in
 * {@code InfiniteTensor#iterateWindows} safe. {@code final} fields give safe publication when used
 * as a {@link java.util.concurrent.ConcurrentHashMap} key across threads.
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
