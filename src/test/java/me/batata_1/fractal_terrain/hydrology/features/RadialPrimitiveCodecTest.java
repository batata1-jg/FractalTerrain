package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.stream.Stream;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Persistence and index geometry for the radial family. Records compare {@code double[]} by reference,
 * so a primitive would otherwise be unequal to its own reloaded copy — which the round trip is here
 * to catch.
 */
class RadialPrimitiveCodecTest {

    /** Task 3 adds the SourcePrimitive entry; every test below is written to cover both. */
    private static Stream<RadialPrimitive> radialPrimitives() {
        return Stream.of(new ConfluencePrimitive(new double[] {12.5, -40.25}, 6.0, 71.5));
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void roundTripsThroughTheTypeTaggedPayload(RadialPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.getClass(), reloaded.getClass(), "the type tag did not select the record");
        assertNotSame(original, reloaded);
        assertEquals(original, reloaded, "contents did not survive the round trip");
        assertEquals(original.hashCode(), reloaded.hashCode());
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void reportsThePayloadSizeItActuallyWrites(RadialPrimitive primitive) {
        assertEquals(primitive.byteSize(), primitive.serialize().length);
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void isIndexedAsADiscOfItsOwnWidth(RadialPrimitive primitive) {
        assertEquals(primitive.width(), primitive.getRadius(), 1e-12);
        assertArrayEquals(primitive.coord(), primitive.getCenter());
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void containsThePointsInsideItsDiscAndNoOthers(RadialPrimitive primitive) {
        final double[] centre = primitive.coord();
        final double r = primitive.getRadius();

        assertTrue(primitive.containsPoint(new double[] {centre[0], centre[1]}));
        assertTrue(primitive.containsPoint(new double[] {centre[0] + r * 0.99, centre[1]}));
        assertFalse(primitive.containsPoint(new double[] {centre[0] + r * 1.01, centre[1]}));
        assertTrue(
                primitive.containsPointInflated(new double[] {centre[0] + r * 1.01, centre[1]}, r * 0.1),
                "an inflated stab must reach a disc the chunk prefetch would otherwise miss");
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void writesAnMbrThatBoundsItsDisc(RadialPrimitive primitive) {
        final double[] lower = new double[2];
        final double[] upper = new double[2];
        primitive.writeMbrInto(lower, upper);
        final double[] centre = primitive.coord();
        final double r = primitive.getRadius();

        assertArrayEquals(new double[] {centre[0] - r, centre[1] - r}, lower, 1e-12);
        assertArrayEquals(new double[] {centre[0] + r, centre[1] + r}, upper, 1e-12);
    }

    @Test
    void confluenceHoldsTheAppendedTypeTag() {
        // The ordinal is the on-disk tag. If this number changes, every cached primitive is
        // reinterpreted as a different feature.
        assertEquals(6, HydrologicalFeature.CONFLUENCE.ordinal());
        assertEquals(
                HydrologicalFeature.CONFLUENCE, new ConfluencePrimitive(new double[] {0.0, 0.0}, 1.0, 0.0).getType());
    }

    @Test
    void sortsAfterEveryRiverPrimitive() {
        // computeRiverGrid's river loop stops at the first non-river entry; a radial family sorting
        // before RIVER would truncate the river run and silently drop carve.
        final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>(List.of(
                new ConfluencePrimitive(new double[] {0.0, 0.0}, 1.0, 0.0),
                new RiverPrimitive(new double[] {0.0, 0.0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0)));
        primitives.sort(HydrologicalPrimitive.comparator);

        assertTrue(primitives.get(0) instanceof RiverPrimitive, "RIVER must sort first");
    }
}
