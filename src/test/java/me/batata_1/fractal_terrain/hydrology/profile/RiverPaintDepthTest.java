package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The paint contract every profile answers: a bounded material column, keyed by the banded coordinate. */
class RiverPaintDepthTest {

    /** The bands a profile can be asked about, plus both boundaries and a point past the rim. */
    private static final float[] SWEPT_BANDS = {
        0.0f,
        (float) RiverInfluenceCarve.BED_EDGE * 0.5f,
        (float) RiverInfluenceCarve.BED_EDGE,
        (float) RiverInfluenceCarve.BED_EDGE + 1e-4f,
        (float) RiverInfluenceCarve.FLOODPLAIN_EDGE,
        (float) RiverInfluenceCarve.FLOODPLAIN_EDGE + 1e-4f,
        1.0f
    };

    /** Squarely inside the bed band, away from either boundary swept above. */
    private static final float BED_BAND = 0.0f;

    /** Squarely inside the floodplain band, away from either boundary swept above. */
    private static final float FLOOD_PLAIN_BAND =
            (float) ((RiverInfluenceCarve.BED_EDGE + RiverInfluenceCarve.FLOODPLAIN_EDGE) / 2);

    private static SurfaceMaterial[] scratch() {
        return new SurfaceMaterial[HydrologyTuning.MAX_RIVER_PAINT_DEPTH];
    }

    /** One constant's expected bed and floodplain columns, matched against what {@code riverPaintDepth}
     *  actually returns — the check a reviewer otherwise has to do by hand, constant by constant. */
    private static Stream<Arguments> columnFixtures() {
        return Stream.of(
                Arguments.of(
                        RosgenProfile.A,
                        new SurfaceMaterial[] {SurfaceMaterial.COBBLE, SurfaceMaterial.COBBLE, SurfaceMaterial.GRAVEL},
                        new SurfaceMaterial[] {}),
                Arguments.of(
                        RosgenProfile.Aa,
                        new SurfaceMaterial[] {SurfaceMaterial.COBBLE, SurfaceMaterial.COBBLE, SurfaceMaterial.COBBLE},
                        new SurfaceMaterial[] {SurfaceMaterial.COBBLE, SurfaceMaterial.GRAVEL}),
                Arguments.of(
                        RosgenProfile.B,
                        new SurfaceMaterial[] {SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL, SurfaceMaterial.COBBLE},
                        new SurfaceMaterial[] {}),
                Arguments.of(
                        RosgenProfile.C,
                        new SurfaceMaterial[] {SurfaceMaterial.SAND, SurfaceMaterial.SAND, SurfaceMaterial.GRAVEL},
                        new SurfaceMaterial[] {SurfaceMaterial.DEFER, SurfaceMaterial.SILT, SurfaceMaterial.SILT}),
                Arguments.of(
                        RosgenProfile.D,
                        new SurfaceMaterial[] {SurfaceMaterial.GRAVEL, SurfaceMaterial.SAND, SurfaceMaterial.GRAVEL},
                        new SurfaceMaterial[] {SurfaceMaterial.SAND, SurfaceMaterial.SAND}),
                Arguments.of(
                        RosgenProfile.DA,
                        new SurfaceMaterial[] {SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL},
                        new SurfaceMaterial[] {}),
                Arguments.of(
                        RosgenProfile.E,
                        new SurfaceMaterial[] {SurfaceMaterial.SILT, SurfaceMaterial.CLAY, SurfaceMaterial.CLAY},
                        new SurfaceMaterial[] {SurfaceMaterial.DEFER, SurfaceMaterial.SILT}),
                Arguments.of(
                        RosgenProfile.F,
                        new SurfaceMaterial[] {SurfaceMaterial.SAND, SurfaceMaterial.SILT, SurfaceMaterial.SILT},
                        new SurfaceMaterial[] {SurfaceMaterial.DEFER, SurfaceMaterial.SILT}),
                Arguments.of(
                        RosgenProfile.G,
                        new SurfaceMaterial[] {SurfaceMaterial.COBBLE, SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL},
                        new SurfaceMaterial[] {}));
    }

    private static void assertColumn(RosgenProfile profile, float dist, SurfaceMaterial[] expected, String band) {
        final SurfaceMaterial[] out = scratch();
        final int depth = profile.riverPaintDepth(0, dist, out);
        assertEquals(expected.length, depth, profile + " " + band + " depth");
        assertArrayEquals(expected, Arrays.copyOf(out, depth), profile + " " + band + " column");
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void neverPaintsDeeperThanTheScratchBufferHolds(RosgenProfile profile) {
        // MAX_RIVER_PAINT_DEPTH is the contract bound the surface builder sizes its buffer to; a profile
        // returning more would overrun it, which is a programming error rather than a runtime condition.
        final SurfaceMaterial[] out = scratch();
        for (float dist : SWEPT_BANDS) {
            final int depth = profile.riverPaintDepth(0, dist, out);
            assertTrue(depth >= 0, profile + " returned a negative depth at " + dist);
            assertTrue(depth <= HydrologyTuning.MAX_RIVER_PAINT_DEPTH, profile + " returned " + depth);
        }
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void fillsEveryEntryItClaims(RosgenProfile profile) {
        final SurfaceMaterial[] out = scratch();
        for (float dist : SWEPT_BANDS) {
            Arrays.fill(out, null);
            final int depth = profile.riverPaintDepth(0, dist, out);
            for (int d = 0; d < depth; d++) {
                assertNotNull(out[d], profile + " left entry " + d + " unwritten at " + dist);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void writesNothingPastTheDepthItReturns(RosgenProfile profile) {
        // The buffer is reused across all 256 columns of a chunk, so a profile must not leave a previous
        // column's material where the next column's loop can read it.
        final SurfaceMaterial[] out = scratch();
        Arrays.fill(out, SurfaceMaterial.MUD);
        final int depth = profile.riverPaintDepth(0, 0.0f, out);
        for (int d = depth; d < out.length; d++) {
            assertSame(SurfaceMaterial.MUD, out[d], profile + " overwrote entry " + d);
        }
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void paintsNothingOutsideTheFloodPlain(RosgenProfile profile) {
        // The influence band is the outer blend; leaving it to the vanilla rules is what keeps a river's
        // valley looking like the biome it runs through.
        assertEquals(
                0,
                profile.riverPaintDepth(0, (float) RiverInfluenceCarve.FLOODPLAIN_EDGE + 1e-4f, scratch()),
                profile + " painted past the floodplain");
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void paintsSomethingInTheBed(RosgenProfile profile) {
        // Without this every profile could satisfy the bounds above by painting nothing at all.
        assertTrue(profile.riverPaintDepth(0, 0.0f, scratch()) > 0, profile + " left its own bed unpainted");
    }

    @ParameterizedTest
    @MethodSource("columnFixtures")
    void eachConstantPaintsItsOwnMaterialColumns(
            RosgenProfile profile, SurfaceMaterial[] bed, SurfaceMaterial[] floodPlain) {
        assertColumn(profile, BED_BAND, bed, "bed");
        assertColumn(profile, FLOOD_PLAIN_BAND, floodPlain, "floodplain");
    }

    @Test
    void aTypeAHeadwaterTrenchComesUpCobble() {
        final SurfaceMaterial[] out = scratch();
        RosgenProfile.A.riverPaintDepth(0, BED_BAND, out);
        assertEquals(SurfaceMaterial.COBBLE, out[0]);
    }

    @Test
    void aTypeCLowlandMeanderComesUpSand() {
        final SurfaceMaterial[] out = scratch();
        RosgenProfile.C.riverPaintDepth(0, BED_BAND, out);
        assertEquals(SurfaceMaterial.SAND, out[0]);
    }

    @Test
    void aTypeCFloodplainDefersToTheBiomeWithSiltBeneath() {
        final SurfaceMaterial[] out = scratch();
        RosgenProfile.C.riverPaintDepth(0, FLOOD_PLAIN_BAND, out);
        assertEquals(SurfaceMaterial.DEFER, out[0]);
        assertEquals(SurfaceMaterial.SILT, out[1]);
    }

    @Test
    void anUnprofiledFeatureTypePaintsNothing() {
        assertEquals(0, DefaultProfile.INSTANCE.riverPaintDepth(0, 0.0f, scratch()));
    }

    @Test
    void aRiverTagResolvesToItsRosgenProfile() {
        // The surface path holds a packed tag and never a primitive instance, so tag-to-profile
        // resolution has to live on the family enum.
        for (RosgenType type : RosgenType.values()) {
            assertSame(
                    RosgenProfile.of(type),
                    HydrologicalFeature.RIVER.profileFor(type.ordinal()),
                    "RIVER sub-type " + type);
        }
    }

    @Test
    void aFeatureTypeWithNoProfileFallsBackToTheDefault() {
        assertSame(DefaultProfile.INSTANCE, HydrologicalFeature.SOURCE.profileFor(0));
        assertSame(DefaultProfile.INSTANCE, HydrologicalFeature.WATERFALL.profileFor(0));
    }

    @Test
    void rosgenTypesResolveByOrdinalWithoutCopyingTheEnum() {
        for (RosgenType type : RosgenType.values()) {
            assertSame(type, RosgenType.byOrdinal(type.ordinal()));
        }
    }
}
