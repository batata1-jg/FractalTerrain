package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Persistence and index geometry for the shed families. The cut step is the point: a reloaded oxbow
 * that forgot its age could never grow the aged profile the record exists to carry.
 */
class HistoricPrimitiveCodecTest {

    private static Stream<HistoricPrimitive> historicPrimitives() {
        return Stream.of(
                new OxbowLakePrimitive(new double[] {12.5, -40.25}, (byte) 7, 6.0, 30.0, 71.5),
                new AbandonedRiverPrimitive(new double[] {-3.0, 8.75}, (byte) 23, 1.25, 9.5, 130.0));
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void roundTripsThroughTheTypeTaggedPayload(HistoricPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.getClass(), reloaded.getClass(), "the type tag did not select the record");
        assertNotSame(original, reloaded);
        assertEquals(original, reloaded, "contents did not survive the round trip");
        assertEquals(original.hashCode(), reloaded.hashCode());
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void keepsTheStepItWasCutAt(HistoricPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.time(), reloaded.time());
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void reportsThePayloadSizeItActuallyWrites(HistoricPrimitive primitive) {
        assertEquals(primitive.byteSize(), primitive.serialize().length);
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void isIndexedAsADiscOfItsInfluence(HistoricPrimitive primitive) {
        assertEquals(primitive.influence(), primitive.getRadius(), 1e-12);
        assertArrayEquals(primitive.coord(), primitive.getCenter());
    }

    @Test
    void hasNoFootprintUntilItIsResolved() {
        // Minted mid-simulation with influence 0: indexing one before the resolve pass gives it a disc
        // that matches nothing, which is why that pass must run before collectPrimitives.
        final OxbowLakePrimitive unresolved = new OxbowLakePrimitive(new double[] {5.0, 5.0}, (byte) 2, 4.0, 0, 0);
        assertEquals(0.0, unresolved.getRadius(), 1e-12);

        final HistoricPrimitive resolved = unresolved.resolved(64.0, 20.0);

        assertEquals(20.0, resolved.getRadius(), 1e-12);
        assertEquals(64.0, resolved.elevation(), 1e-12);
        assertEquals(4.0, resolved.width(), 1e-12, "resolving must not disturb the width it was cut with");
        assertEquals((byte) 2, resolved.time(), "resolving must not disturb the cut step");
    }

    @Test
    void everyOtherFamilyReportsTheLiveNetworkStep() {
        final RiverPrimitive live =
                new RiverPrimitive(new double[] {0.0, 0.0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0);

        assertEquals((byte) 0, live.time());
    }

    @Test
    void sortsAfterEveryRiverPrimitive() {
        // computeRiverGrid's river loop stops at the first non-river entry; a shed family sorting before
        // RIVER would truncate the river run and silently drop carve.
        final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>(List.of(
                new OxbowLakePrimitive(new double[] {0.0, 0.0}, (byte) 1, 1.0, 1.0, 0.0),
                new RiverPrimitive(new double[] {0.0, 0.0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0)));
        primitives.sort(HydrologicalPrimitive.comparator);

        assertTrue(primitives.get(0) instanceof RiverPrimitive, "RIVER must sort first");
    }
}
