package me.batata_1.fractal_terrain.storage;

/**
 * Thrown by {@link Storage#loadInto} when it cannot produce the entry from persistent storage
 * (cache-only/null path, non-serializable payload, key not persisted, file missing, or
 * deserialization failed). Subclasses with an entry-creating supplier catch this and recompute
 * the entry instead.
 */
public class EntryNotLoadableException extends Exception {

    public EntryNotLoadableException(String message) {
        super(message);
    }

    public EntryNotLoadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
