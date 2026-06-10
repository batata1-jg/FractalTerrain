package me.batata_1.fractal_terrain.world.biome;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.*;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import org.jetbrains.annotations.NotNull;

public class BiomeProvider {

    private final NonIntersectingInfiniteTensor final_tiles;
    public final MultiNoiseUtil.MultiNoiseSampler sampler;

    public BiomeProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(path + "/final_biome_tiles", new int[] {5, 512, 512}, key -> {
            int x = key.get(X);
            int z = key.get(Z);
            FloatTensor reliefTensor = FractalTerrainInstance.getReliefProvider()
                    .getInfiniteTensor()
                    .getEntry(key);

            final float[] elev = Arrays.copyOfRange(reliefTensor.data, 0, 1 << 18);
            final float[] grad = Arrays.copyOfRange(reliefTensor.data, 4 << 18, 5 << 18);
            final float[] lowFreqGrad = Arrays.copyOfRange(reliefTensor.data, 5 << 18, 6 << 18);
            final float[] res = Arrays.copyOfRange(reliefTensor.data, 6 << 18, 7 << 18);
            final float[] climate = pipeline.getClimate(x, z, elev);
            final float[] biomeVariables =
                    ClimateVariableTransform.transform(x, z, elev, grad, lowFreqGrad, climate, res);

            FloatTensor t = new FloatTensor(biomeVariables, new int[] {5, 512, 512});

            Debug.seeTileTiff(t, x, z, "final_biomes");
            return t;
        });
        // T H C E D W SpawnTarget
        final float scale = 1;
        sampler = new MultiNoiseUtil.MultiNoiseSampler(
                new BiomeProviderDensity(scale, 2),
                new BiomeProviderDensity(scale, 3),
                new BiomeProviderDensity(scale, 0),
                new BiomeProviderDensity(scale, 1),
                DensityFunctionTypes.yClampedGradient(-64, 63, -1, 0),
                new BiomeProviderDensity(scale, 4),
                List.of());
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    private static class BiomeProviderDensity implements DensityFunction.Base {

        private final Interpolation interpolation;

        public BiomeProviderDensity(final float scale, final int ch) {
            interpolation = new Interpolation(scale, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        @Override
        public void fill(double[] densities, @NotNull EachApplier applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final NoisePos pos = applier.at(i);
                final int x = pos.blockX();
                final int z = pos.blockZ();
                densities[i] = interpolation.interpolateBilinear(x, z);
            }
        }

        @Override
        public double sample(NoisePos pos) {
            return interpolation.interpolateBilinear(pos.blockX(), pos.blockZ());
        }

        @Override
        public double minValue() {
            return 0;
        }

        @Override
        public double maxValue() {
            return 0;
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return null;
        }
    }
}
