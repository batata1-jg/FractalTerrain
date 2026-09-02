package me.batata_1.fractal_terrain.hydrology.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.junit.jupiter.api.Test;

/**
 * The eviction policy {@code RiverProvider.buildTile} drives its tile cache with.
 *
 * <p>fastutil has no {@code accessOrder} flag: a plain {@code get} compiles, passes every other test,
 * and silently degrades the cache to insertion-order eviction. Nothing fails — the cache just gets
 * worse. Asserting recency directly is the only thing that catches it.
 */
class RecentTileCachePolicyTest {

    /** Mirrors {@code RiverProvider.RECENT_TILE_CAPACITY}. */
    private static final int CAPACITY = 4;

    /** The read half of the production path: {@code getAndMoveToFirst}, not {@code get}. */
    private static String read(Long2ObjectLinkedOpenHashMap<String> cache, long key) {
        return cache.getAndMoveToFirst(key);
    }

    /** The write half: newest to the head, then evict from the tail until within budget. */
    private static void write(Long2ObjectLinkedOpenHashMap<String> cache, long key, String value) {
        cache.putAndMoveToFirst(key, value);
        while (cache.size() > CAPACITY) cache.removeLast();
    }

    @Test
    void aTouchedEntrySurvivesEvictionAndAnUntouchedOneDoesNot() {
        final Long2ObjectLinkedOpenHashMap<String> cache = new Long2ObjectLinkedOpenHashMap<>(CAPACITY);
        for (long k = 0; k < CAPACITY; k++) write(cache, k, "tile" + k);
        assertEquals(CAPACITY, cache.size());

        assertEquals("tile0", read(cache, 0));

        write(cache, 99, "tile99");
        assertEquals(CAPACITY, cache.size());

        assertTrue(cache.containsKey(0), "the touched entry was evicted — recency is not refreshing on read");
        assertFalse(cache.containsKey(1), "the least-recently-used entry survived — eviction is not from the tail");
        assertTrue(cache.containsKey(99), "the newest entry was evicted immediately");
    }

    @Test
    void aMissLeavesTheCacheUntouched() {
        final Long2ObjectLinkedOpenHashMap<String> cache = new Long2ObjectLinkedOpenHashMap<>(CAPACITY);
        for (long k = 0; k < CAPACITY; k++) write(cache, k, "tile" + k);

        assertNull(read(cache, 12345), "an object-valued fastutil map must still return null on a miss");
        assertEquals(CAPACITY, cache.size(), "a miss changed the cache size");
        assertTrue(cache.containsKey(0), "a miss reordered or evicted an entry");
    }
}
