package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    private static SurfaceMaterial[] scratch() {
        return new SurfaceMaterial[HydrologyTuning.MAX_RIVER_PAINT_DEPTH];
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
