package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Generic, lazily-populated tile cache keyed by integer coordinate tuples.
 *
 * <p>Payloads implement {@link Persistable}. When a payload's {@link Persistable#serialize} /
 * {@link Persistable#deserialize} are real, entries are persisted under {@link #PATH} and survive
 * eviction (re-read on demand); when they throw {@link UnsupportedOperationException}, the
 * {@code Storage} is <em>cache-only</em> and evicted entries are forgotten entirely.
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

    protected static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    protected final ConcurrentHashMap<List<Integer>, CompletableFuture<T>> CACHE = new ConcurrentHashMap<>(16, 0.75f);

    /** Keys that logically exist (disk-backed for serializable payloads, in-cache for cache-only). */
    protected final Set<List<Integer>> GENERATED_ENTRIES = ConcurrentHashMap.newKeySet();

    protected final String PATH;
    protected final int rank;

    /** Used only to invoke {@link Persistable#deserialize}; its own state is never read. */
    protected final T deserializationPrototype;

    /** Guards {@link #cachedEntryByteSizes} + {@link #totalCachedBytes}. Readers never acquire this. */
    private final Object evictionLock = new Object();

    /** Cached keys mapped to byte size, in INSERTION ORDER (eldest entry iterates first). */
    private final LinkedHashMap<List<Integer>, Long> cachedEntryByteSizes = new LinkedHashMap<>();

    /** Running sum of {@link #cachedEntryByteSizes} values. */
    private long totalCachedBytes = 0;

    /** Serialize capability for this Storage: TRUE = disk-backed, FALSE = cache-only, null = unknown. */
    private volatile Boolean payloadIsSerializable = null;

    public Storage(String path, int rank, T deserializationPrototype) {
        LOG.info("creating storage {}",path);
        PATH = path;
        this.rank = rank;
        this.deserializationPrototype = deserializationPrototype;
        bootstrap();
    }

    public boolean inStorage(int[] index) {
        return GENERATED_ENTRIES.contains(toKey(index));
    }

    public String getEntryDir() {
        return PATH;
    }

    public Set<List<Integer>> getCacheKeys() {
        return CACHE.keySet();
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
    private static List<Integer> getKeyFromName(final String s, final int rank) {
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

    protected static String giveNameToKey(List<Integer> key) {
        StringBuilder ans = new StringBuilder();
        for (int id : key) {
            ans.append("_").append(NorP(id)).append(Math.abs(id));
        }
        return ans.append("_").toString();
    }

    /** Absolute path stem (without the ".ser" suffix) for the tile at {@code key}. */
    protected String tilePath(List<Integer> key) {
        return getEntryDir() + "/" + giveNameToKey(key);
    }

    private void bootstrap() {
        File file = new File(getEntryDir());
        if (!file.exists()) if (file.mkdirs()) LOG.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = file.list();
        if (createdTiles != null)
            for (String tile : createdTiles) {
                final List<Integer> key = getKeyFromName(tile, rank);
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

    public T getEntry(List<Integer> key) {
        try {
            return fetchEntry(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void addOrOverwriteEntry(CompletableFuture<T> t, final int[] index) {
        final List<Integer> key = toKey(index);
        CACHE.put(key, t.thenApply(entry -> persistAndRecord(key, entry)));
    }

    /**
     * Single-flight fetch: returns the cached future on a hit, otherwise atomically installs a fresh
     * promise (so concurrent callers for the same key share it) and hands it to {@link #loadInto}.
     *
     * <p>The {@code putIfAbsent} winner populates the promise <em>after</em> the map operation
     * returns, so no CACHE bin lock is held across the (potentially expensive) load — keeping reads
     * lock-free and avoiding the {@code computeIfAbsent} "recursive update" pitfall.
     */
    protected CompletableFuture<T> fetchEntry(List<Integer> key) {
        final CompletableFuture<T> existing = CACHE.get(key);
        if (existing != null) return existing;
        final CompletableFuture<T> promise = new CompletableFuture<>();
        final CompletableFuture<T> prev = CACHE.putIfAbsent(key, promise);
        if (prev != null) return prev; // another thread is already loading this key
        loadInto(key, promise);
        return promise;
    }

    /**
     * Populate the freshly-installed {@code promise} for an uncached {@code key}. The base
     * implementation can only reload a disk-backed entry (asynchronously on {@link #INFERENCE_EXECUTOR});
     * subclasses (e.g. {@code NonIntersectingInfiniteTensor}) override this to recompute on demand.
     * On failure the promise's mapping is removed so the key can be retried.
     */
    protected void loadInto(List<Integer> key, CompletableFuture<T> promise) {
        if (!(GENERATED_ENTRIES.contains(key) && !Boolean.FALSE.equals(payloadIsSerializable))) {
            LOG.error(
                    "tile index = {} of path {} not in storage (GENERATED_ENTRIES={}, CACHE={})",
                    key,
                    getPath(),
                    GENERATED_ENTRIES.contains(key),
                    CACHE.containsKey(key));
            printCurrentEntrySet();
            printEntryMapHash();
            CACHE.remove(key, promise);
            promise.completeExceptionally(new RuntimeException("tile " + key + " not in storage"));
            return;
        }
        INFERENCE_EXECUTOR.execute(() -> {
            try {
                final File file = new File(tilePath(key) + ".ser");
                if (!file.exists()) {
                    LOG.error("file {}, aka: {} not exist", file.getAbsolutePath(), key);
                    throw new RuntimeException("missing tile file for " + key);
                }
                final T entry = deserializationPrototype.deserialize(tilePath(key));
                recordCachedEntry(key, entry.byteSize());
                promise.complete(entry);
            } catch (Throwable ex) {
                CACHE.remove(key, promise);
                promise.completeExceptionally(ex);
            }
        });
    }

    /**
     * Mark {@code key} as logically existing, persist it when the payload supports serialization
     * (otherwise fall back to cache-only), and account its size for eviction. Returned for
     * {@code thenApply} chaining. Acquires only {@link #evictionLock} (never a CACHE bin lock).
     */
    protected T persistAndRecord(List<Integer> key, T entry) {
        GENERATED_ENTRIES.add(key);
        try {
            entry.serialize(tilePath(key));
            payloadIsSerializable = Boolean.TRUE;
        } catch (UnsupportedOperationException e) {
            payloadIsSerializable = Boolean.FALSE; // cache-only payload — skip disk
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        recordCachedEntry(key, entry.byteSize());
        return entry;
    }

    /** Insert/refresh a cached key so it counts toward the budget and becomes the NEWEST entry. */
    private void recordCachedEntry(List<Integer> key, long byteSize) {
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
    private List<Integer> pollOldest() {
        Iterator<Map.Entry<List<Integer>, Long>> iterator =
                cachedEntryByteSizes.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        Map.Entry<List<Integer>, Long> eldest = iterator.next();
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
        final List<List<Integer>> victims = new ArrayList<>();
        synchronized (evictionLock) {
            while (totalCachedBytes > cacheLimitBytes) {
                List<Integer> evictedKey = pollOldest();
                if (evictedKey == null) break; // nothing left to evict
                victims.add(evictedKey);
            }
        }
        if (victims.isEmpty()) return;
        final boolean cacheOnly = Boolean.FALSE.equals(payloadIsSerializable);
        for (List<Integer> evictedKey : victims) {
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

    protected static List<Integer> toKey(int[] index) {
        return Arrays.stream(index).boxed().toList();
    }
}
