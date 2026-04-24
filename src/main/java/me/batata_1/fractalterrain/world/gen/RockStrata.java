package me.batata_1.fractalterrain.world.gen;

import com.mojang.serialization.Codec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import me.batata_1.fractalterrain.math.Blur;
import me.batata_1.fractalterrain.math.MaskedOps;
import me.batata_1.fractalterrain.math.Spline;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.world.noise.OctaveSimplexNoiseSampler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.random.Random;

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

    public abstract BlockState getStrataBlock(final int layer);

    public int getCurLayer(double[] args, double y) {
        try {
            return (int) Math.floor((y - settings.layer_function().get().apply(args)) / settings.layerSpacing());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public double getSpacing() {
        return settings.layerSpacing();
    }

    protected double transform(final double[] args, final double y) {
        try {
            final double y0 = settings.layer_function().get().apply(args);
            final int curLayer = (int) Math.floor((y - y0) / settings.layerSpacing());
            return y0 + settings.layerSpacing() * curLayer;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract double sample(int x, int z, double y, double gradY, final double blurredY);

    public static final class AngledPlaneStrata extends RockStrata {

        private final OctaveSimplexNoiseSampler noiseSamplerX;
        private final OctaveSimplexNoiseSampler noiseSamplerZ;
        private final Blur blur = new Blur();
        private final BlockState[] layerBlockStates;
        private static final Spline spline =
                new Spline(new float[] {4, 16, 100}, new float[] {1, 0.75F, 0}, new float[] {0, 0, 0});

        // the greater fallOf, the s
        private AngledPlaneStrata(final double layerSpacing, final long seed, final BlockState[] strataMaterial) {
            super(new Settings(layerSpacing, -1, -1, new CompletableFuture<>()));
            noiseSamplerX = new OctaveSimplexNoiseSampler(seed, 1, 1, 2048, 0.75, 0.3, null, null);
            noiseSamplerZ = new OctaveSimplexNoiseSampler(seed + 1999, 1, 1, 2048, 0.75, 0.3, null, null);
            super.settings.layer_function.complete(doubles -> {
                final double theta = noiseSamplerX.sample(doubles[0], doubles[1]) * Math.PI / 4.0;
                final double phi = noiseSamplerZ.sample(doubles[0], doubles[1]) * Math.PI / 8.0;
                return Math.tan(phi) * (Math.sin(theta) * doubles[0] + Math.cos(theta) * doubles[1]);
            });
            blur.setF(doubles -> transform(new double[] {doubles[0], doubles[1]}, doubles[2]));
            final Random r = Random.create(seed);
            final BlockState[] layerBlockStates = new BlockState[1 << 8];
            for (int i = 0; i < layerBlockStates.length; i++) {
                layerBlockStates[i] = strataMaterial[Math.abs(r.nextInt() % strataMaterial.length)];
            }
            this.layerBlockStates = layerBlockStates;
        }

        public static AngledPlaneStrata create(double layerSpacing, long seedOff, BlockState[] m) {
            return new AngledPlaneStrata(layerSpacing, seedOff, m);
        }

        @Override
        public BlockState getStrataBlock(int layer) {
            return layerBlockStates[Math.abs(layer % layerBlockStates.length)];
        }

        @Override
        public double sample(final int x, final int z, final double y, final double gradY, final double blurredY) {
            final double blurredStrata = blur.entryAvgBlur3x3(new double[] {x, z, blurredY}) + y - blurredY;
            return MaskedOps.DOUBLE.Add(y, blurredStrata, spline.clamp01sample((float) gradY));
        }
    }
}
