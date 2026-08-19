package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the family/sub-type packing the carve writes into Types.RIVER_TYPE. */
class HydrologicalFeaturePackTest {

    @Test
    void roundTripsFamilyAndSubType() {
        final long packed = HydrologicalFeature.RIVER.pack(RosgenType.C.ordinal());
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(packed));
        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(packed));
    }

    @Test
    void roundTripsEveryFamilyAtSubTypeZero() {
        // ordinal 0 in both words packs to 0L, which must not be confused with NONE.
        for (final HydrologicalFeature feature : HydrologicalFeature.values()) {
            final long packed = feature.pack(0);
            assertEquals(feature, HydrologicalFeature.unpack(packed));
            assertEquals(0, HydrologicalFeature.unpackSub(packed));
        }
    }

    @Test
    void theEmptySentinelIsNotAValidFamily() {
        // A zero-filled buffer reads as RIVER + RosgenType.A, so -1L is what "untouched" must be.
        assertNull(HydrologicalFeature.unpack(HydrologicalFeature.NONE));
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(0L));
    }

    @Test
    void subTypeDoesNotBleedIntoTheFamilyWord() {
        // A negative sub-ordinal must stay in the low word rather than sign-extending over the family.
        final long packed = HydrologicalFeature.CONFLUENCE.pack(-1);
        assertEquals(HydrologicalFeature.CONFLUENCE, HydrologicalFeature.unpack(packed));
        assertEquals(-1, HydrologicalFeature.unpackSub(packed));
    }
}
