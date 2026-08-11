package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the channel/knot packing in RiverPrimitive.ids and the adjacency guard built on it. */
class RiverPrimitiveIdsTest {

    /** Mirrors the packing in HydrologicalFeature.RIVER.addPrimitives. */
    private static RiverPrimitive knot(int channelId, int knotIndex) {
        return new RiverPrimitive(
                new double[] {0.0, 0.0},
                1.0,
                RosgenType.A,
                new double[] {0.0, -1.0},
                0.0,
                2.0,
                0.0,
                knotIndex | (((long) channelId) << 32));
    }

    @Test
    void unpacksChannelAndKnotFromTheSameLong() {
        final RiverPrimitive primitive = knot(7, 42);
        assertEquals(7, primitive.channelId());
        assertEquals(42, primitive.knotIndex());
    }

    @Test
    void consecutiveKnotsOfOneChannelAreAdjacent() {
        assertTrue(knot(3, 10).isKnotAdjacentTo(knot(3, 11)));
        assertTrue(knot(3, 11).isKnotAdjacentTo(knot(3, 10)));
    }

    @Test
    void aKnotIsNotAdjacentToItself() {
        assertFalse(knot(3, 10).isKnotAdjacentTo(knot(3, 10)));
    }

    @Test
    void knotsOfDifferentChannelsAreNeverAdjacent() {
        // Two rivers running close: their primitives interleave in the spatial query result.
        assertFalse(knot(3, 10).isKnotAdjacentTo(knot(4, 11)));
    }

    @Test
    void nonConsecutiveKnotsOfOneChannelAreNotAdjacent() {
        // The meander-loopback case: one channel enters the chunk twice, so knots 10 and 57
        // can land in neighbouring slots of the sorted prefetch list.
        assertFalse(knot(3, 10).isKnotAdjacentTo(knot(3, 57)));
    }

    @Test
    void packingSurvivesAChannelIdWithTheHighBitSet() {
        // knotIndex must not be sign-extended out of the low word.
        final RiverPrimitive primitive = knot(Integer.MAX_VALUE, 5);
        assertEquals(Integer.MAX_VALUE, primitive.channelId());
        assertEquals(5, primitive.knotIndex());
    }
}
