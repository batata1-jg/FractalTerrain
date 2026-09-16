package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.stream.Stream;

import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Persistence and rectangle geometry shared by every {@link RosgenCarvedPrimitive}. */
class RosgenCarvedPrimitiveCodecTest {

    private static Stream<RosgenCarvedPrimitive> primitives() {
        return Stream.of(
                new RiverPrimitive(
                        new double[] {12.5, -40.25},
                        5.0,
                        RiverPrimitive.RosgenType.B,
                        new double[] {0.6, 0.8},
                        0.1,
                        6.0,
                        71.5),
                new OxbowLakePrimitive(
                        new double[] {-3.0, 8.75}, (byte) 23, 1.25, 9.5, 130.0, new double[] {1.0, 0.0}, 0.0, null));
    }

    @ParameterizedTest
    @MethodSource("primitives")
    void roundTripsThroughTheTypeTaggedPayload(RosgenCarvedPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.getClass(), reloaded.getClass(), "the type tag did not select the record");
        assertNotSame(original, reloaded);
        assertEquals(original, reloaded, "contents did not survive the round trip");
        assertEquals(original.hashCode(), reloaded.hashCode());
    }

    @ParameterizedTest
    @MethodSource("primitives")
    void reportsThePayloadSizeItActuallyWrites(RosgenCarvedPrimitive primitive) {
        assertEquals(primitive.byteSize(), primitive.serialize().length);
    }

    @ParameterizedTest
    @MethodSource("primitives")
    void carvesTheShellAsARosgenCrossSection(RosgenCarvedPrimitive primitive) {
        assertEquals(
                InfluenceCarver.ROSGEN, primitive.getInfluenceCarver());
    }
}
