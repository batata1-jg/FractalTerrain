package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

public class IntLongChunkPosConversionTest {

    @Test
    void chunkPosToLongBackToChunkPosNoChangeFirstQuadrant() {
        var beforePos = new ChunkPos(13567847, 23823);
        var afterPos = FractalTerrainHeightmapCacheAccessor.LongToChunkPos(
                FractalTerrainHeightmapCacheAccessor.ChunkPosToLong(beforePos));
        assertEquals(beforePos.x, afterPos.x);
        assertEquals(beforePos.z, afterPos.z);
    }

    @Test
    void chunkPosToLongBackToChunkPosNoChangeSecondQuadrant() {
        var beforePos = new ChunkPos(-13567847, 23823);
        var afterPos = FractalTerrainHeightmapCacheAccessor.LongToChunkPos(
                FractalTerrainHeightmapCacheAccessor.ChunkPosToLong(beforePos));
        assertEquals(beforePos.x, afterPos.x);
        assertEquals(beforePos.z, afterPos.z);
    }

    @Test
    void chunkPosToLongBackToChunkPosNoChangeThirdQuadrant() {
        var beforePos = new ChunkPos(-13567847, -23823);
        var afterPos = FractalTerrainHeightmapCacheAccessor.LongToChunkPos(
                FractalTerrainHeightmapCacheAccessor.ChunkPosToLong(beforePos));
        assertEquals(beforePos.x, afterPos.x);
        assertEquals(beforePos.z, afterPos.z);
    }

    @Test
    void chunkPosToLongBackToChunkPosNoChangeFourthQuadrant() {
        var beforePos = new ChunkPos(13567847, -23823);
        var afterPos = FractalTerrainHeightmapCacheAccessor.LongToChunkPos(
                FractalTerrainHeightmapCacheAccessor.ChunkPosToLong(beforePos));
        assertEquals(beforePos.x, afterPos.x);
        assertEquals(beforePos.z, afterPos.z);
    }
}
