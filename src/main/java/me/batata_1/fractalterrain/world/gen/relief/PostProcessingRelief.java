package me.batata_1.fractalterrain.world.gen.relief;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider.sampleNoise;
import static me.batata_1.fractalterrain.util.DebugTensors.isNan;
import static me.batata_1.fractalterrain.util.MlUtil.*;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.ml.diffusion.Stages;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.StorageInterface;
import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.storage.TileRegion;
import me.batata_1.fractalterrain.util.DebugTensors;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;

public class PostProcessingRelief {

    private final DecodeAndFinish decodeAndFinish;
    private final CompletableFuture<OrtSession> average;
    private final CompletableFuture<OrtSession> take_coarse_grad;

    private final EntryStorage<Tile> final_tiles;
    private final EntryStorage<Tile> final_raw_tiles;

    public record Settings(float alpha, float beta, float gamma, float grad_blur, float tau)
            implements SettingsRegistry.Settings {

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.FLOAT.optionalFieldOf("alpha", 0F).forGetter(Settings::alpha),
                        Codec.FLOAT.optionalFieldOf("beta", 0.5F).forGetter(Settings::beta),
                        Codec.FLOAT.optionalFieldOf("gamma", 5F).forGetter(Settings::gamma),
                        Codec.FLOAT.optionalFieldOf("grad_blur", 0.1F).forGetter(Settings::grad_blur),
                        Codec.FLOAT.optionalFieldOf("tau", 2.0F).forGetter(Settings::tau))
                .apply(i, Settings::new));

        public static final Codec<RegistryEntry<Settings>> REGISTRY_CODEC =
                RegistryElementCodec.of(FractalTerrainRegistryKeys.POST_PROCESSING_SETTINGS, CODEC);
    }

    public PostProcessingRelief(Settings settings) {
        this.decodeAndFinish = new DecodeAndFinish(settings);
        take_coarse_grad = Models.getOrCreateModel("ml_util/take_coarse_grad");
        average = Models.getOrCreateModel("ml_util/average");
        final_raw_tiles = new EntryStorage<>("final_raw", Tile::new, 64, (xz) -> {
            try {
                assert average != null;
                assert take_coarse_grad != null;
                final OnnxTensor t = (OnnxTensor) take_coarse_grad
                        .get()
                        .run(Map.of("x", (OnnxTensor) average.get()
                                .run(Map.of("x", decodeAndFinish.getCoarseTilesAsTensor(xz.getFirst(), xz.getSecond())))
                                .get(0)))
                        .get(0);
                return new Tile(t);
            } catch (OrtException | IOException | ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        final_tiles = new EntryStorage<>("final", Tile::new, 512, (xz) -> {
            final int x = xz.getFirst() << 1;
            final int z = xz.getSecond() << 1;
            try {
                assert average != null;
                final OnnxTensor t = (OnnxTensor) average.get()
                        .run(Map.of("x", decodeAndFinish.getTilesAsTensor(x, z)))
                        .get(0);

                DebugTensors.seeFinal(t, x, z);
                return new Tile(t);
            } catch (ExecutionException | IOException | InterruptedException | OrtException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public float getElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 1);
    }

    public float getRefinedGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 2);
    }

    public float getBlurredGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 3);
    }

    public float getRes(Pair<Integer, Integer> xz) {
        return getValue(xz, 4);
    }

    private float getValue(Pair<Integer, Integer> xz, int ch) {
        try {
            return final_tiles.getValue(xz, ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // global coords -> correct to get coarse value
    private Pair<Integer, Integer> convertCoarse(Pair<Integer, Integer> xz) {
        return xz;
    }

    private float getCoarseValue(Pair<Integer, Integer> xz, int ch) {
        try {
            return final_raw_tiles.getValue(convertCoarse(xz), ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public float getContinentalElev(Pair<Integer, Integer> xz) {
        return getElev(xz);
    }

    public float getRawGrad(Pair<Integer, Integer> xz) {
        return (float) Math.tanh(getBlurredGrad(xz) / 1000.0);
    }

    public float getRawTemp(Pair<Integer, Integer> xz) {
        final double val = ContinentalScaleMapProvider.sampleTemperature(
                convertCoarse(xz).getFirst(), convertCoarse(xz).getSecond());
        return (float) val;
    }

    public float getRawTempSTD(Pair<Integer, Integer> xz) {
        return getCoarseValue(xz, 3);
    }

    public float getRawPrecip(Pair<Integer, Integer> xz) {
        return (float) ContinentalScaleMapProvider.samplePrecipitation(
                convertCoarse(xz).getFirst(), convertCoarse(xz).getSecond());
    }

    public float getRawPrecipSTD(Pair<Integer, Integer> xz) {
        return getCoarseValue(xz, 5);
    }

    public OnnxTensor getTilesAsTensor(int i, int j) {
        try {
            return final_tiles.getEntry(Pair.of(i, j)).get().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static class DecodeAndFinish extends StorageInterface {

        private final CompletableFuture<OrtSession> fuzed_finisher;
        private final CompletableFuture<OrtSession> decoder;
        private final Stages.LatentStage latent;
        private final Settings settings;

        public DecodeAndFinish(Settings settings) {
            super(new EntryStorage<>("decoder", TileRegion::new, 256), 256 * 256 * 2, new long[] {2, 256, 256});
            this.settings = settings;

            Stages.LatentStage.initModels();
            latent = new Stages.LatentStage();
            decoder = Models.getOrCreateDirectModel("models/run_decoder");
            fuzed_finisher = Models.getOrCreateModel("ml_util/fuzed");
        }

        @Override
        public OnnxTensor[] runInference(int x, int z)
                throws ExecutionException, OrtException, InterruptedException, IOException {

            final var xz = Pair.of(x, z);

            var inputs = Map.of(
                    "latents", latent.getTilesAsTensor(x, z),
                    "noise", sampleNoise(xz, new long[] {1, 512, 512}, 4));

            // seeTensor(latent.getTilesAsTensor(x,z),"latentTensor" + x + " " + z,true,  4);
            // seeTensor((OnnxTensor) decoder.run(inputs).get(0),"decoderTensor" + x + " " + z,false,  0);
            inputs = Map.of(
                    "residual_init", (OnnxTensor) decoder.get().run(inputs).get(0),
                    "latents_init", latent.getTilesAsTensor(x, z),
                    "alpha", OnnxTensor.createTensor(ENV, settings.alpha()),
                    "beta", OnnxTensor.createTensor(ENV, settings.beta()),
                    "gamma", OnnxTensor.createTensor(ENV, settings.gamma()),
                    "grad_blur", OnnxTensor.createTensor(ENV, settings.grad_blur()),
                    "tau", OnnxTensor.createTensor(ENV, settings.tau()));

            final var out = (OnnxTensor) fuzed_finisher.get().run(inputs).get(0);
            isNan(out);
            return slice(out);
        }

        public OnnxTensor getCoarseTilesAsTensor(int x, int z)
                throws IOException, ExecutionException, OrtException, InterruptedException {
            return latent.getCoarseModel().getTilesAsTensor(x, z);
        }
    }
}
