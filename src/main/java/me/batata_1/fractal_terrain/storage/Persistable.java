package me.batata_1.fractal_terrain.storage;

/**
 * Contract for objects that {@link Storage} can cache and (optionally) persist to disk.
 *
 * <p>Serialization is pure: {@link #serialize()} returns the payload's raw bytes and
 * {@link #deserialize(byte[])} rebuilds a fresh instance from those bytes. The implementation owns
 * the byte <em>format</em> only; all file IO lives in {@link Storage}, which writes the returned
 * bytes to disk and feeds the read-back bytes to {@link #deserialize(byte[])}.
 *
 * <p>Both methods default to throwing {@link UnsupportedOperationException}. Types that provide a
 * real binary format must override both; types that leave the defaults in place are treated by
 * {@link Storage} as <em>cache-only</em> — their entries live only in the in-memory cache and are
 * never written to or read from disk. {@code Storage} detects this once, up front, by probing
 * {@link #serialize()} on its prototype.
 *
 * @param <T> the self type (so {@link #deserialize(byte[])} returns the concrete payload type).
 */
public interface Persistable<T extends Persistable<T>> {

    /** Approximate in-memory footprint in bytes, used for {@link Storage}'s byte-budget eviction. */
    long byteSize();

    /**
     * Serialize this payload to a freshly allocated byte array. The default marks the type as
     * non-serializable.
     *
     * @throws UnsupportedOperationException if the type has no real serialization (cache-only).
     */
    default byte[] serialize() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
                "serialize not implemented for " + getClass().getName());
    }

    /**
     * Rebuild a payload from bytes previously produced by {@link #serialize()}. The returned object
     * is a fresh instance; the receiver is only a prototype and its state is ignored. The default
     * marks the type as non-serializable.
     *
     * @throws UnsupportedOperationException if the type has no real serialization (cache-only).
     */
    default T deserialize(byte[] rawBytes) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
                "deserialize not implemented for " + getClass().getName());
    }

    /**
     * Called once by {@link Storage} at the cache-write boundary — immediately before a freshly
     * computed or disk-deserialized payload is published into the shared cache — so implementations
     * that hold mutable internal state can seal it against further mutation before it becomes visible
     * to other reader threads. The default is a no-op for payloads with no such contract.
     */
    default void freeze() {}
}
