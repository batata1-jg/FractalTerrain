package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.world.biome.parameters.*;
import org.jetbrains.annotations.Nullable;

/**
 * Maps the diffusion model's coarse climate + relief channels onto the vanilla Overworld
 * <em>biome parameters</em>. See {@code worldgeneration101.md} ("Biomes → Overworld Biomes").
 *
 * <p>Minecraft selects an Overworld biome from six noise parameters:
 *
 * <ul>
 *   <li><b>continentalness</b> — ocean vs. inland (see {@link Continentalness}),
 *   <li><b>erosion</b> — flat vs. mountainous (see {@link ErosionLevel}),
 *   <li><b>temperature</b> — cold vs. hot (see {@link TemperatureLevel}),
 *   <li><b>humidity</b> / vegetation — dry vs. lush (see {@link HumidityLevel}),
 *   <li><b>weirdness</b> — ridges, folded into peaks-and-valleys (see {@link PeaksValleys}),
 *   <li><b>depth</b> — surface vs. cave; derived from block Y by {@code BiomeProvider}, not here.
 * </ul>
 *
 * <p>{@link #transform} reproduces the first five parameters (depth is purely vertical) for a
 * 512×512 tile, scaling them into the same roughly {@code [-1, 1]} space vanilla uses so the
 * existing {@code MultiNoiseBiomeSource} machinery can resolve a biome.
 *
 * <p><b>Responsibility:</b> thin public facade over the biome-parameter pipeline — {@link #transform}
 * forwards to {@link ClimateToBiomeTransformer}, which owns the actual per-parameter math.
 *
 * <p><b>Collaborators:</b> {@link ClimateToBiomeTransformer} (the delegate), which in turn uses
 * {@link ShoreDistanceCalculator} for the distance-to-shore upscaling and
 * {@link BiomeParameterClassifier} for the {@code is…(value)} band predicates (e.g.
 * {@link BiomeParameterClassifier#isCoast(float)}, {@link BiomeParameterClassifier#isShatteredErosion(float)});
 * the {@code world.biome.parameters} enums encode the wiki's published parameter ranges.
 *
 * <p><b>Invariants:</b> preserves the pre-split public signature of {@link #transform} exactly —
 * callers ({@code BiomeProvider}) are unaffected by the split.
 */
public class ClimateVariableTransform {

    public ClimateVariableTransform() {}

    /**
     * Maps the coarse climate array to the five horizontal vanilla biome parameters
     * (continentalness, erosion, temperature, humidity/vegetation, weirdness) for one 512×512 tile.
     * Depth — the sixth parameter — is vertical and supplied separately by {@code BiomeProvider}.
     *
     * <p>Delegates to {@link ClimateToBiomeTransformer#transform}; see there for the parameter docs.
     */
    public static float[] transform(
            int x0,
            int z0,
            float[] elev,
            float[] grad,
            float[] lowFreqGrad,
            float[] climate,
            float[] res,
            float[] vegPdf,
            int[] coarseDistShore,
            @Nullable float[] distShoreOut) {
        return ClimateToBiomeTransformer.transform(
                x0, z0, elev, grad, lowFreqGrad, climate, res, vegPdf, coarseDistShore, distShoreOut);
    }
}
