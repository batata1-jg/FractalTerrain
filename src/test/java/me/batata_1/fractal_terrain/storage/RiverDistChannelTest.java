package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Payload shape of the banded-distance channel the bed carve publishes for the surface painter. */
class RiverDistChannelTest {

    @Test
    void allocatesOneFloatPerColumn() {
        // The channel is created per cached chunk heightmap, so its size is a memory budget rather than
        // an implementation detail: 256 floats against the 256 longs RIVER_TYPE already costs.
        final Object payload = Types.RIVER_DIST.creator().apply(new ChunkPos(0, 0));
        assertInstanceOf(float[].class, payload);
        assertEquals(256, ((float[]) payload).length);
    }

    @Test
    void readsRowMajorLikeEveryOtherFloatChannel() {
        // RIVER_TYPE overrides get() because it is a long[]; RIVER_DIST must not, so a caller can read
        // it either per block or as a raw array without knowing which.
        final float[] payload = (float[]) Types.RIVER_DIST.creator().apply(new ChunkPos(0, 0));
        payload[3 * 16 + 5] = 0.375f;
        assertEquals(0.375f, Types.RIVER_DIST.get(payload, 3, 5));
    }
}
