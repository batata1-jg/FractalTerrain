package me.batata_1.fractalterrain.world.gen;

import com.mojang.serialization.Codec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.world.noise.OctaveSimplexNoiseSampler;

public abstract class RockStrata {

    public record Settings(
            double layerSpacing,
            int maxNumLayers,
            int layerOffset,
            CompletableFuture<Function<double[], Double>> layer_function)
            implements SettingsRegistry.Settings {
        public static final Codec<Settings> CODEC = null;
    }

    protected final Settings settings;

    public RockStrata(Settings settings) {
        this.settings = settings;
    }

    private static int binarySearchLayer(
            double x, double z, double y, int p, int q, Function<double[], Double> f_layer) {
        while (p != q) {
            int m = -((-p - q) >> 1);
            if (f_layer.apply(new double[] {x, z, m}) <= y) p = m;
            else q = m - 1;
        }
        return p;
    }

    public int getCurLayer(double[] args, double y) throws ExecutionException, InterruptedException {
        return (int) Math.floor((y - settings.layer_function().get().apply(args)) / settings.layerSpacing());
    }

    protected double transform(double[] args, double y) throws ExecutionException, InterruptedException {
        double y0 = settings.layer_function().get().apply(args);
        int curLayer = (int) Math.floor((y - y0) / settings.layerSpacing());
        return y0 + settings.layerSpacing() * curLayer;
    }

    public abstract double transformY(double x, double z, double y);

    public static final class AngledPlaneStrata extends RockStrata {

        private final OctaveSimplexNoiseSampler noiseSamplerX;
        private final OctaveSimplexNoiseSampler noiseSamplerZ;

        private AngledPlaneStrata(double layerSpacing, long seed) {
            super(new Settings(layerSpacing, -1, -1, new CompletableFuture<>()));
            noiseSamplerX = new OctaveSimplexNoiseSampler(seed, 1, 1, 2048, 0.75, 0.3, null, null);
            noiseSamplerZ = new OctaveSimplexNoiseSampler(seed + 1999, 1, 1, 2048, 0.75, 0.3, null, null);
            super.settings.layer_function.complete(doubles -> {
                double theta = noiseSamplerX.sample(doubles[0], doubles[1]) * Math.PI / 8.0;
                double phi = noiseSamplerZ.sample(doubles[0], doubles[1]) * Math.PI / 8.0;
                return Math.tan(phi) * (Math.sin(theta) * doubles[0] + Math.cos(theta) * doubles[1]);
            });
            //            super.settings.layer_function.complete(doubles -> 0.25*doubles[0] + -0.5*doubles[1]);
        }

        public static AngledPlaneStrata create(double layerSpacing, long seedOff) {
            return new AngledPlaneStrata(layerSpacing, seedOff);
        }

        @Override
        public double transformY(double x, double z, double y) {
            try {
                int curLayer = getCurLayer(new double[] {x, z}, y);
                return super.transform(new double[] {x, z}, y);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
