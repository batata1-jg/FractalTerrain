package me.batata_1.fractal_terrain.storage;

import java.io.IOException;

/**
 * Contract for objects that {@link Storage} can cache and (optionally) persist to disk.
 *
 * <p>Both {@link #serialize(String)} and {@link #deserialize(String)} default to throwing
 * {@link UnsupportedOperationException}. Implementations that provide a real on-disk format must
 * override both; types that leave the defaults in place are treated by {@link Storage} as
 * <em>cache-only</em> — their entries live only in the in-memory cache and are never written to or
 * read from disk.
 *
 * @param <T> the self type (so {@link #deserialize(String)} returns the concrete payload type).
 */
public interface Persistable<T extends Persistable<T>> {

    /** Approximate in-memory footprint in bytes, used for {@link Storage}'s byte-budget eviction. */
    long byteSize();

    /**
     * Write this payload to {@code path}. The default marks the type as non-serializable.
     *
     * @throws UnsupportedOperationException if the type has no real serialization (cache-only).
     */
    default void serialize(String path) throws IOException {
        throw new UnsupportedOperationException(
                "serialize not implemented for " + getClass().getName());
    }

    /**
     * Read a payload previously written by {@link #serialize(String)} from {@code path}. The
     * returned object is a fresh instance; the receiver is only a prototype and its state is
     * ignored. The default marks the type as non-serializable.
     *
     * @throws UnsupportedOperationException if the type has no real serialization (cache-only).
     */
    default T deserialize(String path) throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException(
                "deserialize not implemented for " + getClass().getName());
    }
}
