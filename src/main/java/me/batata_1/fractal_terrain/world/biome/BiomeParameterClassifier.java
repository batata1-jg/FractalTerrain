package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.world.biome.parameters.Continentalness;
import me.batata_1.fractal_terrain.world.biome.parameters.ErosionLevel;
import me.batata_1.fractal_terrain.world.biome.parameters.PeaksValleys;

/**
 * Classifies vanilla biome-parameter values (continentalness, erosion, weirdness) against the
 * wiki-published bands in {@code world.biome.parameters}.
 *
 * <p><b>Responsibility:</b> pure {@code is…(value)} predicates over a single biome-parameter value —
 * no tile geometry, no noise, no cross-parameter mutation.
 *
 * <p><b>Collaborators:</b> {@link Continentalness}, {@link ErosionLevel}, {@link PeaksValleys} (the
 * enums whose bands back each predicate); called by {@link ClimateToBiomeTransformer} while building a
 * tile's biome parameters.
 *
 * <p><b>Invariants:</b> stateless and side-effect free; classification boundaries must stay exactly as
 * published in {@code worldgeneration101.md} — do not adjust a threshold without updating the source
 * enum's range.
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
