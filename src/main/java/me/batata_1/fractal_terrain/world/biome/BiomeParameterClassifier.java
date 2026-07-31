package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.world.biome.parameters.Continentalness;
import me.batata_1.fractal_terrain.world.biome.parameters.ErosionLevel;
import me.batata_1.fractal_terrain.world.biome.parameters.PeaksValleys;

/**
 * Band predicates over a single vanilla biome-parameter value, consulted mid-computation by
 * {@link ClimateToBiomeTransformer}.
 *
 * <p>Kept free of tile geometry and noise so the wiki-published band boundaries live in exactly one
 * place and can be checked against the source enums.
 *
 * <p>Never adjust a threshold here without updating the owning enum's range.
 */
public class BiomeParameterClassifier {

    private BiomeParameterClassifier() {}

    public static boolean isMushroomFields(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.MUSHROOM_FIELDS;
    }

    public static boolean isDeepOcean(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.DEEP_OCEAN;
    }

    public static boolean isOcean(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.OCEAN;
    }

    /** Coast band (−0.19 … −0.11): the ocean ↔ land transition where beaches generate. */
    public static boolean isCoast(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.COAST;
    }

    public static boolean isNearInland(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.NEAR_INLAND;
    }

    public static boolean isMidInland(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.MID_INLAND;
    }

    public static boolean isFarInland(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.FAR_INLAND;
    }

    /** True for any oceanic region (mushroom fields, deep ocean, ocean) — i.e. below the coast. */
    public static boolean isOceanic(float continentalness) {
        return continentalness < Continentalness.COAST.range.min();
    }

    /** True once past the coast (near-, mid- or far-inland). */
    public static boolean isInland(float continentalness) {
        return continentalness >= Continentalness.NEAR_INLAND.range.min();
    }

    /** Erosion level 5 (0.45 … 0.55): vanilla's shattered-terrain band. */
    public static boolean isShatteredErosion(float erosion) {
        return erosion > ErosionLevel.LEVEL_5.range.min() && erosion < ErosionLevel.LEVEL_5.range.max();
    }

    /** Valley peaks-and-valleys band — where vanilla carves rivers. */
    public static boolean isValley(float weirdness) {
        return PeaksValleys.of(weirdness) == PeaksValleys.VALLEYS;
    }
}
