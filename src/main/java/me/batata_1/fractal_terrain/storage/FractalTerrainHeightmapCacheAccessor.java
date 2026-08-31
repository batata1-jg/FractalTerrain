package me.batata_1.fractal_terrain.storage;

import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import net.minecraft.world.level.ChunkPos;

public class FractalTerrainHeightmapCacheAccessor {

    private static final int MAX_CACHE_ENTRIES = 4;

    private static final ThreadLocal<Long2ObjectLinkedOpenHashMap<FractalTerrainHeightmap>> LOCAL_CACHE = ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(MAX_CACHE_ENTRIES));

    public static long IntsToLong(int x , int z) {
        return (x&first32mask) | ((long)z << 32);
    }
    public static long ChunkPosToLong(ChunkPos pos) {
        return IntsToLong(pos.x,pos.z);
    }

    private static final long first32mask = 0xffffffffL;
    public static ChunkPos LongToChunkPos(long pos) {
        return new ChunkPos((int) pos, (int) (pos >>> 32));
    }

    private static FractalTerrainHeightmap HmapFetcher(long pos) {
        return FractalTerrainInstance.getHeightmapCache().getOrCompute(LongToChunkPos(pos));
    }

    public static FractalTerrainHeightmap get(long pos) {
        var hMap = LOCAL_CACHE.get().computeIfAbsent(pos,FractalTerrainHeightmapCacheAccessor::HmapFetcher);
        evictIfNeeded();
        return hMap;
    }

    public static FractalTerrainHeightmap get(int x , int z) {
        return get(IntsToLong(x,z));
    }

    public static FractalTerrainHeightmap get(ChunkPos pos) {
        return get(ChunkPosToLong(pos));
    }

    public static void evictIfNeeded() {
        if(LOCAL_CACHE.get().size()>MAX_CACHE_ENTRIES) LOCAL_CACHE.get().removeFirst();
    }

}
