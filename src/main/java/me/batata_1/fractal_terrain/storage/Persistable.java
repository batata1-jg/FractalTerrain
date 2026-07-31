package me.batata_1.fractal_terrain.storage;

/**
 * Contract for objects {@link Storage} can cache and optionally persist.
 *
 * <p>Splits format from IO: an implementation owns only the byte layout, while every file operation
 * stays in {@link Storage}.
 *
 * <p>The throwing defaults are the opt-out — a type that leaves them is treated as cache-only, which
 * {@link Storage} detects once by probing its prototype.
 */
public interface Persistable<T extends Persistable<T>> {

    /** Approximate in-memory footprint in bytes, used for {@link Storage}'s byte-budget eviction. */
    long byteSize();

    /** Payload bytes. Leaving the default marks the type cache-only. */
    default byte[] serialize() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
                "serialize not implemented for " + getClass().getName());
    }

    /** Rebuilds a payload; the receiver is only a prototype and its state is ignored. */
    default T deserialize(byte[] rawBytes) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
                "deserialize not implemented for " + getClass().getName());
    }

    /** Hook at the cache-write boundary, so a payload with mutable state can seal it before other
     *  threads can observe it. */
    default void freeze() {}
}
