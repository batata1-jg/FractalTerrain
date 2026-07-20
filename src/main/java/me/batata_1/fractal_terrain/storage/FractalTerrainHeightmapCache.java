package me.batata_1.fractal_terrain.storage;

import java.util.concurrent.ConcurrentHashMap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

/**
 * Per-chunk cache of {@link FractalTerrainHeightmap}s. Each chunk's full set of heightmaps is computed
 * once on first request and evicted automatically when that chunk reaches {@link ChunkStatus#FULL}
 * (i.e. once generation no longer needs it), keeping memory bounded to the in-flight generation
 * frontier rather than growing without limit.
 */
public final class FractalTerrainHeightmapCache {

    private final ConcurrentHashMap<ChunkPos, FractalTerrainHeightmap> CACHE = new ConcurrentHashMap<>();
    private final ServerChunkCache levelCache;

    public FractalTerrainHeightmapCache(ServerChunkCache levelCache) {
        this.levelCache = levelCache;
    }

    /** {@code x, z} are CHUNK coordinates. Computes all heightmaps for the chunk on first request. */
    public FractalTerrainHeightmap getOrCompute(ChunkPos pos) {
        final boolean[] computedHere = {false};
        final FractalTerrainHeightmap hmap = CACHE.computeIfAbsent(pos, k -> {
            computedHere[0] = true;
            return compute(k);
        });
        // Schedule eviction *after* computeIfAbsent returns, and only on the thread that actually
        // computed the entry. getChunkFuture can complete synchronously (chunks with no in-flight
        // holder, e.g. structure-locating getBaseHeight), so doing this inside the mapping function
        // would recursively update the map mid-computeIfAbsent.
        if (computedHere[0]) scheduleEviction(pos, hmap);
        return hmap;
    }

    private FractalTerrainHeightmap compute(ChunkPos pos) {
        final float[][] data = new float[Types.values().length][];
        for (Types t : Types.values()) data[t.ordinal()] = t.creator().apply(pos);
        final FractalTerrainHeightmap heightmap = new FractalTerrainHeightmap(data);
        // Second pass: the ELEVATION channel was filled with the raw interpolated relief elevation only.
        // Now that every other heightmap (gradients, residual, biome parameters) is available, recompute
        // ELEVATION from the full set — biome-aware shaping, ocean-height correction, bottomY clamp — and
        // overwrite it in place. (The record shares the same backing array, so the new values are visible.)
        // getPopulateNoiseStep().updateToFinalElev(pos, heightmap);
        return heightmap;
    }

    private void scheduleEviction(ChunkPos key, FractalTerrainHeightmap value) {
        levelCache
                .getChunkFuture(key.x, key.z, ChunkStatus.FULL, false)
                // whenComplete (not thenAccept) so eviction fires even on exceptional completion;
                // remove(key, value) only drops the exact instance we computed, never a newer one.
                .whenComplete((res, err) -> CACHE.remove(key, value));
    }

    public void clear() {
        CACHE.clear();
    }

    public int getSize() {
        return CACHE.size();
    }
}
