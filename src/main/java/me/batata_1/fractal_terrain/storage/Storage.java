package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.io.File;
import java.io.IOException;
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
 * Generic, lazily-populated tile cache keyed by integer coordinate tuples.
 *
 * <p>Payloads implement {@link Persistable}. When a payload's {@link Persistable#serialize} /
 * {@link Persistable#deserialize} are real, entries are persisted under {@link #PATH} and survive
 * eviction (re-read on demand); when they throw {@link UnsupportedOperationException}, the
 * {@code Storage} is <em>cache-only</em> and evicted entries are forgotten entirely. Passing a
 * {@code null} path to the constructor also forces <em>cache-only</em> behaviour: nothing touches the
 * filesystem and entries are never persisted regardless of the payload's serialize support.
 *
 * <p><b>Concurrency:</b> the read path ({@link #getEntry}) is lock-free for cache hits and uses
 * single-flight {@link ConcurrentHashMap#computeIfAbsent} on a miss, so any number of reader
 * threads may run concurrently. The only locked state is the eviction bookkeeping
 * ({@link #cachedEntryByteSizes} / {@link #totalCachedBytes}), guarded by {@link #evictionLock},
 * which readers never touch. See the class-level refactor plan for the lock-ordering argument.
 *
 * @param <T> the cached payload type.
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

    /** Serialize capability for this Storage: TRUE = disk-backed, FALSE = cache-only, null = unknown. */
    private volatile Boolean payloadIsSerializable = null;

    /**
     * @param path persistence directory, or {@code null} for a cache-only Storage that never persists
     *     and never touches the filesystem.
     */
    public Storage(@Nullable String path, String name, int rank, T deserializationPrototype) {
        if (path == null) {
            // Cache-only Storage: keep PATH null so the persistence paths short-circuit.
            payloadIsSerializable = false;
            PATH = null;
        } else {
            PATH = path + "/" + name;
        }
        this.rank = rank;
        this.deserializationPrototype = deserializationPrototype;
        if (!Boolean.FALSE.equals(payloadIsSerializable)) bootstrap();
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

        if (curIndex == -1) {
            LOG.error("invalid in tiles dir (no '_')");
            return null;
        }

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
        File file = new File(getEntryDir());
        if (!file.exists()) if (file.mkdirs()) LOG.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = file.list();
        if (createdTiles != null)
            for (String tile : createdTiles) {
                final TileKey key = getKeyFromName(tile, rank);
                if (key == null) {
                    LOG.error("invalid file, skipping");
                    continue;
                }
                GENERATED_ENTRIES.add(key);
                payloadIsSerializable = Boolean.TRUE; // on-disk tiles imply a real serializer
            }
    }

    public T getEntry(int[] index) {
        return getEntry(toKey(index));
    }

    public T getEntry(TileKey key) {
        try {
            return fetchEntry(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
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

    /**
     * Single-flight compute: returns the cached value if one already exists/is in flight, otherwise
     * atomically claims {@code index} and runs {@code compute} ON THE CALLING THREAD exactly once,
     * persisting/accounting the result. Losers of the claim block on the winner's future rather than
     * recomputing. On failure the claim is released so the key can be retried.
     */
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

    /**
     * Atomically claim {@code index} for computation. Returns a fresh promise the caller MUST settle
     * via {@link #fulfillClaim} or {@link #abandonClaim}; returns {@code null} when another thread
     * already owns or has produced the entry (the caller must NOT compute it).
     */
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

    /**
     * Single-flight fetch: returns the cached future on a hit, otherwise atomically installs a fresh
     * promise (so concurrent callers for the same key share it) and hands it to {@link #loadInto}.
     *
     * <p>The {@code putIfAbsent} winner populates the promise <em>after</em> the map operation
     * returns, so no CACHE bin lock is held across the (potentially expensive) load — keeping reads
     * lock-free and avoiding the {@code computeIfAbsent} "recursive update" pitfall.
     */
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

    /**
     * Populate the freshly-installed {@code promise} for an uncached {@code key} by reading the
     * persisted entry from disk. Runs synchronously on the calling thread. Throws
     * {@link EntryNotLoadableException} whenever it cannot produce the entry — cache-only storage
     * (null path or non-serializable payload), an unpersisted key, a missing tile file, or a
     * deserialization failure. Subclasses (e.g. {@code NonIntersectingInfiniteTensor}) override this
     * to catch that signal and recompute the entry on demand.
     */
    protected void loadInto(TileKey key, CompletableFuture<T> promise) throws EntryNotLoadableException {
        if (Boolean.FALSE.equals(payloadIsSerializable)) {
            // Cache-only Storage (null path or non-serializable payload): nothing persisted to load.
            throw new EntryNotLoadableException("cache-only storage has no persistent entry for " + key);
        }
        if (!GENERATED_ENTRIES.contains(key)) {
            throw new EntryNotLoadableException("tile " + key + " not in storage");
        }
        final File file = new File(tilePath(key) + ".ser");
        if (!file.exists()) {
            throw new EntryNotLoadableException("missing tile file " + file.getAbsolutePath() + " for " + key);
        }
        try {
            final T entry = deserializationPrototype.deserialize(tilePath(key));
            recordCachedEntry(key, entry.byteSize());
            promise.complete(entry);
        } catch (IOException | ClassNotFoundException e) {
            throw new EntryNotLoadableException("failed to deserialize tile " + key, e);
        }
    }

    /**
     * Mark {@code key} as logically existing, persist it when the payload supports serialization
     * (otherwise fall back to cache-only), and account its size for eviction. Returned for
     * {@code thenApply} chaining. Acquires only {@link #evictionLock} (never a CACHE bin lock).
     */
    protected T persistAndRecord(TileKey key, T entry) {
        GENERATED_ENTRIES.add(key);
        if (PATH == null) {
            // Cache-only Storage (no path): never persist, just account the size below.
            payloadIsSerializable = Boolean.FALSE;
        } else {
            try {
                entry.serialize(tilePath(key));
                payloadIsSerializable = Boolean.TRUE;
            } catch (UnsupportedOperationException e) {
                payloadIsSerializable = Boolean.FALSE; // cache-only payload — skip disk
            } catch (IOException e) {
                throw new RuntimeException(e);
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

    /**
     * Remove the eldest (earliest-inserted) key from the budget bookkeeping and return it. MUST be
     * called with {@link #evictionLock} held. Does NOT touch {@link #CACHE}/{@link #GENERATED_ENTRIES}
     * — the caller performs those removals after releasing the lock (see {@link #evictIfNeeded}).
     */
    private TileKey pollOldest() {
        Iterator<Map.Entry<TileKey, Long>> iterator =
                cachedEntryByteSizes.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        Map.Entry<TileKey, Long> eldest = iterator.next();
        iterator.remove();
        totalCachedBytes -= eldest.getValue();
        return eldest.getKey();
    }

    /**
     * Drop eldest cached entries until {@link #totalCachedBytes} is back within {@code cacheLimitBytes}.
     * Victims are selected under {@link #evictionLock}; the lock is released before mutating the
     * concurrent maps. For cache-only payloads {@link #GENERATED_ENTRIES} is purged BEFORE
     * {@link #CACHE} so a racing reader never sees an entry that exists logically but nowhere.
     */
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
        final boolean cacheOnly = Boolean.FALSE.equals(payloadIsSerializable);
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
