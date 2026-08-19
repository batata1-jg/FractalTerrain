package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Generic lazily-populated tile cache, the persistence layer under every provider in the pipeline.
 *
 * <p>Disk-backed or cache-only is decided once from the payload's {@link Persistable} support and the
 * constructor path, so callers never branch on it.
 *
 * <p>Chunk generation reads this from many worker threads at once, which shapes the design: hits are
 * lock-free, misses single-flight without holding a map bin lock, and eviction bookkeeping is the only
 * locked state. See {@code README.md} for the cache-write/freeze boundary.
 */
public class Storage<T extends Persistable<T>> {

    private static final Logger LOG = getLogger(Storage.class);

    protected final ConcurrentHashMap<TileKey, CompletableFuture<T>> CACHE = new ConcurrentHashMap<>(16, 0.75f);

    /** Keys that logically exist (disk-backed for serializable payloads, in-cache for cache-only). */
    protected final Set<TileKey> GENERATED_ENTRIES = ConcurrentHashMap.newKeySet();

    protected final String PATH;
    protected final int rank;

    /** Used only to invoke {@link Persistable#deserialize}; its own state is never read. */
    protected final T deserializationPrototype;

    /** Guards {@link #cachedEntryByteSizes} + {@link #totalCachedBytes}. Readers never acquire this. */
    private final Object evictionLock = new Object();

    /** Cached keys mapped to byte size, in INSERTION ORDER (eldest entry iterates first). */
    private final LinkedHashMap<TileKey, Long> cachedEntryByteSizes = new LinkedHashMap<>();

    /** Running sum of {@link #cachedEntryByteSizes} values. */
    private long totalCachedBytes = 0;

    /** Serialize capability, decided once in the constructor: true = disk-backed, false = cache-only. */
    private final boolean payloadIsSerializable;

    /** @param path persistence directory, or {@code null} for a cache-only Storage */
    public Storage(@Nullable String path, String name, int rank, T deserializationPrototype) {
        this.rank = rank;
        this.deserializationPrototype = deserializationPrototype;
        if (path == null) {
            // Cache-only Storage: keep PATH null so the persistence paths short-circuit.
            PATH = null;
            payloadIsSerializable = false;
            return;
        }
        PATH = path + "/" + name;
        // Probe the payload once: if the prototype cannot serialize, this Storage is cache-only and
        // skips bootstrap entirely (nothing to load, nothing to validate on disk).
        payloadIsSerializable = probeSerializable(deserializationPrototype);
        if (payloadIsSerializable) bootstrap();
    }

    /** True iff {@code prototype.serialize()} succeeds (vs. throwing {@link UnsupportedOperationException}). */
    private static boolean probeSerializable(Persistable<?> prototype) {
        try {
            prototype.serialize();
            return true;
        } catch (UnsupportedOperationException e) {
            LOG.info("{} not serializable", prototype.getClass().getSimpleName());
            return false;
        }
    }

    private static void writeBytes(String path, byte[] bytes) throws IOException {
        Files.write(Path.of(path), bytes);
    }

    private static byte[] readBytes(String path) throws IOException {
        return Files.readAllBytes(Path.of(path));
    }

    public boolean inStorage(int[] index) {
        return GENERATED_ENTRIES.contains(toKey(index));
    }

    public String getEntryDir() {
        return PATH;
    }

    public Set<TileKey> getCachedKeys() {
        return CACHE.keySet();
    }

    public Set<TileKey> getGeneratedKeys() {
        return GENERATED_ENTRIES;
    }

    public void clear() {
        GENERATED_ENTRIES.clear();
        CACHE.clear();
        synchronized (evictionLock) {
            cachedEntryByteSizes.clear();
            totalCachedBytes = 0;
        }
    }

    private static String NorP(int x) {
        if (x < 0) return "N";
        return "P";
    }

    // will not be a strong implementation because will be replaced by sqlite
    private static TileKey getKeyFromName(final String s, final int rank) {
        int curIndex = s.indexOf('_');
        String curString = s;

        if (curIndex == -1) return null;

        final int[] ans = new int[rank];
        for (int id = 0; id < rank; id++) {
            curString = curString.substring(curIndex + 1);
            curIndex = curString.indexOf("_");
            if (curIndex == -1) curIndex = curString.length();
            int pos = Integer.parseInt(curString.substring(1, curIndex));
            int sign = curString.charAt(0) == 'N' ? -1 : 1;
            ans[id] = pos * sign;
        }
        return toKey(ans);
    }

    protected static String giveNameToKey(TileKey key) {
        StringBuilder ans = new StringBuilder();
        for (int d = 0; d < key.rank(); d++) {
            int id = key.get(d);
            ans.append("_").append(NorP(id)).append(Math.abs(id));
        }
        return ans.append("_").toString();
    }

    /** Absolute path stem (without the ".ser" suffix) for the tile at {@code key}. */
    protected String tilePath(TileKey key) {
        return getEntryDir() + "/" + giveNameToKey(key);
    }

    private void bootstrap() {
        File dir = new File(getEntryDir());
        if (!dir.exists()) if (dir.mkdirs()) LOG.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = dir.list();
        if (createdTiles == null) return;
        for (String tile : createdTiles) {
            final TileKey key = validatedTileKey(tile);
            if (key == null) {
                // Unconventional file in the tile dir: warn and delete it so the cache stays clean.
                final File bad = new File(dir, tile);
                LOG.warn("deleting non-conforming tile cache file '{}' in {}", tile, getEntryDir());
                if (bad.isFile() && !bad.delete()) LOG.warn("failed to delete {}", bad.getAbsolutePath());
                continue;
            }
            GENERATED_ENTRIES.add(key);
        }
    }

    /** Parse a tile filename to its key, or {@code null} if it does not match the {@code .ser} convention. */
    private TileKey validatedTileKey(String tile) {
        if (!tile.endsWith(".ser")) return null;
        try {
            return getKeyFromName(tile, rank);
        } catch (RuntimeException e) {
            return null; // malformed coordinate token
        }
    }

    public T getEntry(int[] index) {
        return getEntry(toKey(index));
    }

    public T getEntry(TileKey key) {
        try {
            return fetchEntry(key).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e + " at key:" + key.toString());
        } catch (ExecutionException e) {
            throw new RuntimeException(e + " at key:" + key.toString());
        }
    }

    public void addOrOverwriteEntry(CompletableFuture<T> t, final int[] index) {
        final TileKey key = toKey(index);
        CACHE.put(key, t.thenApply(entry -> persistAndRecord(key, entry)));
    }

    // -------------------------------------------------------------------------
    // Single-flight COMPUTE path
    //
    // Mirrors the single-flight LOAD path ({@link #fetchEntry}) but for freshly-computed entries.
    // The atomic {@code putIfAbsent} claim — not any external check-then-act — is what guarantees a
    // given key is computed at most once even when many worker threads race for overlapping slices.
    // -------------------------------------------------------------------------

    /** Single-flight compute, so an expensive tile build never runs twice concurrently.
     *  Runs {@code compute} on the calling thread; losers block on the winner. */
    public T getOrCompute(final int[] index, final Supplier<T> compute) {
        final CompletableFuture<T> claim = claimForCompute(index);
        if (claim == null) return getEntry(toKey(index)); // someone else owns it — await their result
        try {
            final T value = persistAndRecord(toKey(index), compute.get());
            claim.complete(value);
            return value;
        } catch (RuntimeException | Error t) {
            abandonClaim(index, claim, t);
            throw t;
        }
    }

    /** Claims a key so one build can cross-fill a sibling store. The returned promise MUST be settled;
     *  {@code null} means another thread owns it and the caller must not compute. */
    public CompletableFuture<T> claimForCompute(final int[] index) {
        final TileKey key = toKey(index);
        if (CACHE.get(key) != null) return null;
        final CompletableFuture<T> promise = new CompletableFuture<>();
        return CACHE.putIfAbsent(key, promise) == null ? promise : null;
    }

    /** The in-flight/cached future for {@code index}, or {@code null} if none is installed. */
    public CompletableFuture<T> peekFuture(final int[] index) {
        return CACHE.get(toKey(index));
    }

    /** Settle a claim from {@link #claimForCompute}: persist, size-account, and complete the promise. */
    public void fulfillClaim(final int[] index, final CompletableFuture<T> promise, final T value) {
        promise.complete(persistAndRecord(toKey(index), value));
    }

    /** Release a claimed-but-unsettled promise so the key can be retried, propagating {@code cause}. */
    public void abandonClaim(final int[] index, final CompletableFuture<T> promise, final Throwable cause) {
        CACHE.remove(toKey(index), promise);
        promise.completeExceptionally(cause);
    }

    /** Single-flight fetch. Deliberately not {@code computeIfAbsent}, which would hold a bin lock
     *  across the load and can deadlock on recursive update. */
    protected CompletableFuture<T> fetchEntry(TileKey key) {
        final CompletableFuture<T> existing = CACHE.get(key);
        if (existing != null) return existing;
        final CompletableFuture<T> promise = new CompletableFuture<>();
        final CompletableFuture<T> prev = CACHE.putIfAbsent(key, promise);
        if (prev != null) return prev; // another thread is already loading this key
        try {
            loadInto(key, promise);
        } catch (Throwable ex) {
            // Terminal failure: the entry could neither be loaded nor (for subclasses) recomputed.
            // Drop our claim so the key can be retried, and surface the cause to all awaiters.
            LOG.error("failed to load tile {} of path {}", key, getPath(), ex);
            CACHE.remove(key, promise);
            promise.completeExceptionally(ex);
        }
        return promise;
    }

    /** Loads a key from disk. Throws {@link EntryNotLoadableException} for every not-on-disk reason
     *  alike, which is the signal subclasses override to recompute instead. */
    protected void loadInto(TileKey key, CompletableFuture<T> promise) throws EntryNotLoadableException {
        if (!payloadIsSerializable) {
            // Cache-only Storage (null path or non-serializable payload): nothing persisted to load.
            throw new EntryNotLoadableException("cache-only storage has no persistent entry for " + key);
        }
        if (!GENERATED_ENTRIES.contains(key)) {
            throw new EntryNotLoadableException("tile " + key + " not in storage");
        }
        final String tileFile = tilePath(key) + ".ser";
        if (!new File(tileFile).exists()) {
            throw new EntryNotLoadableException("missing tile file " + tileFile + " for " + key);
        }
        try {
            final T entry = deserializationPrototype.deserialize(readBytes(tileFile));
            entry.freeze(); // cache-write boundary: freeze before publishing to other reader threads
            recordCachedEntry(key, entry.byteSize());
            promise.complete(entry);
        } catch (IOException | IllegalStateException e) {
            throw new EntryNotLoadableException("failed to deserialize tile " + key, e);
        }
    }

    /** The publication point: records, persists and accounts a new entry. */
    protected T persistAndRecord(TileKey key, T entry) {
        entry.freeze(); // cache-write boundary: freeze before publishing to other reader threads
        GENERATED_ENTRIES.add(key);
        if (payloadIsSerializable) {
            try {
                writeBytes(tilePath(key) + ".ser", entry.serialize());
            } catch (IOException e) {
                throw new UncheckedIOException("failed to persist tile " + key, e);
            }
        }
        recordCachedEntry(key, entry.byteSize());
        return entry;
    }

    /** Insert/refresh a cached key so it counts toward the budget and becomes the NEWEST entry. */
    private void recordCachedEntry(TileKey key, long byteSize) {
        synchronized (evictionLock) {
            Long previousSize = cachedEntryByteSizes.remove(key); // remove first so re-put re-orders to newest
            if (previousSize != null) totalCachedBytes -= previousSize;
            cachedEntryByteSizes.put(key, byteSize);
            totalCachedBytes += byteSize;
        }
    }

    /** Picks an eviction victim. Call with {@link #evictionLock} held; the caller does the removal
     *  after releasing it. */
    private TileKey pollOldest() {
        Iterator<Map.Entry<TileKey, Long>> iterator =
                cachedEntryByteSizes.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        Map.Entry<TileKey, Long> eldest = iterator.next();
        iterator.remove();
        totalCachedBytes -= eldest.getValue();
        return eldest.getKey();
    }

    /** Trims the cache to its byte budget. Purge order matters for cache-only payloads, or a racing
     *  reader sees an entry that exists logically but nowhere. */
    public void evictIfNeeded(long cacheLimitBytes) {
        final List<TileKey> victims = new ArrayList<>();
        synchronized (evictionLock) {
            while (totalCachedBytes > cacheLimitBytes) {
                TileKey evictedKey = pollOldest();
                if (evictedKey == null) break; // nothing left to evict
                victims.add(evictedKey);
            }
        }
        if (victims.isEmpty()) return;
        final boolean cacheOnly = !payloadIsSerializable;
        for (TileKey evictedKey : victims) {
            if (cacheOnly) GENERATED_ENTRIES.remove(evictedKey); // forget BEFORE dropping the cache copy
            CACHE.remove(evictedKey);
        }
    }

    public void printCurrentEntrySet() {
        LOG.info("Current Tiles: {}", GENERATED_ENTRIES);
    }

    public void printEntryMapHash() {
        LOG.info("Entry Map: {}", CACHE);
    }

    @Nullable
    public String getPath() {
        return PATH;
    }

    protected static TileKey toKey(int[] index) {
        return new TileKey(index);
    }
}
