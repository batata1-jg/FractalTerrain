package me.batata_1.fractal_terrain.world.biome;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.*;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.NotNull;

public class BiomeProvider {

    /** Native-px e-folding distance of the river-humidity falloff (tunable). */
    private static final double HUMIDITY_FALLOFF = 32.0;

    private static final int TILE_PIXELS = 1 << 18; // 512 × 512

    private final NonIntersectingInfiniteTensor final_tiles;
    public final Climate.Sampler sampler;

    public BiomeProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path + "/final_biome_tiles", new int[] {BIOME_CHANNELS, 512, 512}, key -> {
                    int x = key.get(X);
                    int z = key.get(Z);
                    FloatTensor reliefTensor = FractalTerrainInstance.getReliefProvider()
                            .getInfiniteTensor()
                            .getEntry(key);

                    final float[] elev = Arrays.copyOfRange(reliefTensor.data, 0, 1 << 18);
                    final float[] grad = Arrays.copyOfRange(reliefTensor.data, 4 << 18, 5 << 18);
                    final float[] lowFreqGrad = Arrays.copyOfRange(reliefTensor.data, 5 << 18, 6 << 18);
                    final float[] res = Arrays.copyOfRange(reliefTensor.data, 6 << 18, 7 << 18);
                    final float[] vegPdf = new float[512*512];
                    final float[] climate = pipeline.getClimate(x, z, elev);
                    final float[] biomeVariables =
                            ClimateVariableTransform.transform(x, z, elev, grad, lowFreqGrad, climate, res, vegPdf);

                    // Channels 0..4 from the climate transform; channel 5 = river-humidity PDF (reserved,
                    // not read by the sampler yet).
                    final float[] entries = new float[BIOME_CHANNELS * TILE_PIXELS];
                    System.arraycopy(biomeVariables, 0, entries, 0, 5 * TILE_PIXELS);
                    System.arraycopy(vegPdf, 0, entries, 5 * TILE_PIXELS, TILE_PIXELS);

                    FloatTensor t = new FloatTensor(entries, new int[] {BIOME_CHANNELS, 512, 512});

                    Debug.seeTile(t, x, z, "final_biomes");
                    return t;
                });
        // T H C E D W SpawnTarget
        final float scale = 1;
        sampler = new Climate.Sampler(
                new BiomeProviderDensity(scale, 2),
                new BiomeProviderDensity(scale, 3),
                new BiomeProviderDensity(scale, 0),
                new BiomeProviderDensity(scale, 1),
                DensityFunctions.yClampedGradient(-64, 63, -1, 0),
                new BiomeProviderDensity(scale, 4),
                List.of());
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    /**
     * River-humidity PDF for biome tile {@code (tileX, tileZ)}: for each of the 512×512 pixels, the
     * nearest local-river distance is mapped through an exponential falloff (closer ⇒ more humid).
     * Pixels with no river within the query radius get 0. Indexed {@code ix*512 + iz}, matching the
     * relief/biome flat layout.
     */
    private static float[] riverHumidity(int tileX, int tileZ) {
        final LocalRiverProvider localRivers = FractalTerrainInstance.getLocalRiverProvider();
        final float[] vegPdf = new float[TILE_PIXELS];
        final int blockOriginX = tileX << 9;
        final int blockOriginZ = tileZ << 9;
        for (int ix = 0; ix < 512; ix++) {
            for (int iz = 0; iz < 512; iz++) {
                final double dist = localRivers.nearestRiverDistance(blockOriginX + ix, blockOriginZ + iz);
                vegPdf[ix * 512 + iz] = (dist == Double.MAX_VALUE) ? 0f : (float) Math.exp(-dist / HUMIDITY_FALLOFF);
            }
        }
        return vegPdf;
    }

    private static class BiomeProviderDensity implements DensityFunction.SimpleFunction {

        private final Interpolation interpolation;

        public BiomeProviderDensity(final float scale, final int ch) {
            interpolation = new Interpolation(scale, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                final int x = pos.blockX();
                final int z = pos.blockZ();
                densities[i] = interpolation.interpolateBilinear(x, z);
            }
        }

        @Override
        public double compute(FunctionContext pos) {
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
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return null;
        }
    }
}
