package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import org.junit.jupiter.api.Test;

/** Unit tests for the packed river-type layer the bed carve writes. */
class RiverTypeLayerTest {

    @Test
    void allocatesOneLongPerColumn() {
        // The creator ignores its ChunkPos argument, so this needs no world.
        final Object payload = Types.RIVER_TYPE.creator().apply(null);
        assertInstanceOf(long[].class, payload);
        assertEquals(256, ((long[]) payload).length);
    }

    @Test
    void refusesTheFloatColumnAccessor() {
        // Types.get casts to float[]; letting RIVER_TYPE through it would be a silent ClassCastException
        // at some unrelated call site instead of a named failure here.
        final Object payload = Types.RIVER_TYPE.creator().apply(null);
        assertThrows(UnsupportedOperationException.class, () -> Types.RIVER_TYPE.get(payload, 0, 0));
    }
}
