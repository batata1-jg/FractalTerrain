package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.world.biome.parameters.*;
import org.jetbrains.annotations.Nullable;

/**
 * Public facade over the biome-parameter pipeline; {@link ClimateToBiomeTransformer} owns the math.
 *
 * <p>Exists to keep one stable entry point for {@code BiomeProvider} while the per-parameter formulas
 * behind it are free to move.
 *
 * <p>Output is scaled into the roughly {@code [-1, 1]} space vanilla uses, so the stock
 * {@code MultiNoiseBiomeSource} can resolve a biome from it unchanged.
 */
public class ClimateVariableTransform {

    public ClimateVariableTransform() {}

    /** Delegates to {@link ClimateToBiomeTransformer#transform}; see there for parameter details. */
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
