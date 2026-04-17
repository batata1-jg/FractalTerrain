package me.batata_1.fractalterrain.ml.diffusion;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.ml.tensorProviders.ConstantProvider.sampleAvgNull;
import static me.batata_1.fractalterrain.ml.tensorProviders.ConstantProvider.sampleConst;
import static me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider.sampleNoise;
import static me.batata_1.fractalterrain.ml.tensorProviders.MapProvider.sampleMap;
import static me.batata_1.fractalterrain.util.Debug.isNan;
import static me.batata_1.fractalterrain.util.MlUtil.slice;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.StorageInterface;
import me.batata_1.fractalterrain.storage.TileRegion;
import me.batata_1.fractalterrain.util.Debug;

public class Stages {

    public static class LatentStage extends StorageInterface {

        private static int startInstanceNum = 1;
        private static final float[] tSteps = new float[] {1.5645F, 0.6107F};
        private static volatile CompletableFuture<OrtSession> model;
        private static volatile CoarseStage coarseModel;

        public static synchronized void initModels() {
                model = Models.getOrCreateDirectModel("models/run_latent");
                coarseModel = new CoarseStage();
        }

        private final int latentStageInstanceNumber;
        private final LatentStage prev_state;

        public LatentStage() {
            super(new EntryStorage<>("latent/" + startInstanceNum, TileRegion::new, 32), 32 * 32 * 6, new long[] {
                6, 32, 32
            });
            Debug.debugLatentStageConstructor();
            latentStageInstanceNumber = startInstanceNum;
            if (startInstanceNum == 0) {
                prev_state = null;
                return;
            }
            startInstanceNum -= 1;
            prev_state = new LatentStage();
        }

        public CoarseStage getCoarseModel() {
            return coarseModel;
        }

        @Override
        public OnnxTensor[] runInference(final int x, final int z)
                throws OrtException, ExecutionException, InterruptedException, IOException {
            final FloatBuffer fl = FloatBuffer.allocate(4 * 4 * 7);
            for (int i = 0; i < 7; i++)
                for (int j = x; j < x + 4; j++)
                    for (int k = z; k < z + 4; k++) fl.put(coarseModel.getValue(Pair.of(j, k), i));
            fl.flip();
            final OnnxTensor coarse_tensor = OnnxTensor.createTensor(ENV, fl, new long[] {7, 4, 4});
            final OnnxTensor sample;
            if (latentStageInstanceNumber == 0) sample = sampleAvgNull(new long[] {6, 64, 64});
            else {
                assert prev_state != null;
                sample = prev_state.getTilesAsTensor(x, z);
            }
            isNan(sample);
            final var xz = Pair.of(x, z);
            final OnnxTensor noise = sampleNoise(xz, new long[] {1, 5, 64, 64}, 3);
            //        seeTensor(noise,"noise latent_t" + latentStageInstanceNumber + "__" + xz.getFirst()
            // + " " + xz.getSecond(), false);
            final var inputs = Map.of(
                    "sample", sample,
                    "noise", noise,
                    "cond_img", coarse_tensor,
                    "t_tensor", OnnxTensor.createTensor(ENV, tSteps[latentStageInstanceNumber]));
            final var out = (OnnxTensor) model.get().run(inputs).get(0);
            isNan(out);
            return slice(out);
        }
    }

    public static class CoarseStage extends StorageInterface {

        private final CompletableFuture<OrtSession> prep;
        private final CompletableFuture<OrtSession> first_order;
        private final CompletableFuture<OrtSession> second_order;
        private final CompletableFuture<OrtSession> output;
        private final float[] sigmas;

        protected CoarseStage() {
            super(new EntryStorage<>("coarse", TileRegion::new, 32), 32 * 32 * 7, new long[] {7, 32, 32});
            prep = Models.getOrCreateDirectModel("models/prepare_coarse");
            first_order = Models.getOrCreateModel("models/run_first_order_coarse");
            second_order = Models.getOrCreateModel("models/run_second_order_coarse");
            output = Models.getOrCreateDirectModel("models/out_coarse");
            sigmas = new float[] {
                8.0000e+01F,
                5.9658e+01F,
                4.3920e+01F,
                3.1884e+01F,
                2.2794e+01F,
                1.6022e+01F,
                1.1054e+01F,
                7.4689e+00F,
                4.9307e+00F,
                3.1708e+00F,
                1.9794e+00F,
                1.1943e+00F,
                6.9282e-01F,
                3.8386e-01F,
                2.0140e-01F,
                9.8973e-02F,
                4.4883e-02F,
                1.8401e-02F,
                6.6217e-03F,
                2.0000e-03F,
                0
            };
        }

        @Override
        public OnnxTensor[] runInference(final int x, final int z)
                throws OrtException, ExecutionException, InterruptedException {
            final var xz = Pair.of(x, z);
            var inputs = Map.of(
                    "synthetic_map", sampleMap(xz, new long[] {5, 64, 64}),
                    "cond_noise", sampleNoise(xz, new long[] {5, 64, 64}, 1),
                    "sample_noise", sampleNoise(xz, new long[] {6, 64, 64}, 2));

            var out = prep.get().run(inputs);
            final OnnxTensor img = (OnnxTensor) out.get(1);
            for (int i = 0; i < sigmas.length - 1; i++) {
                if (i == 0 || i == sigmas.length - 2) {
                    inputs = Map.of(
                            "sample", (OnnxTensor) out.get(0),
                            "prev_sample", sampleConst(0, new long[] {1, 6, 64, 64}),
                            "sigma_id", OnnxTensor.createTensor(ENV, sigmas[i]),
                            "sigma_post", OnnxTensor.createTensor(ENV, sigmas[i + 1]),
                            "cond_img", img);
                    out = first_order.get().run(inputs);
                    continue;
                }
                inputs = Map.of(
                        "sample",
                        (OnnxTensor) out.get(0),
                        "prev_sample",
                        (OnnxTensor) out.get(1),
                        "sigma_prev",
                        OnnxTensor.createTensor(ENV, sigmas[i - 1]),
                        "sigma_id",
                        OnnxTensor.createTensor(ENV, sigmas[i]),
                        "sigma_post",
                        OnnxTensor.createTensor(ENV, sigmas[i + 1]),
                        "cond_img",
                        img);
                out = second_order.get().run(inputs);
            }
            inputs = Map.of("sample", (OnnxTensor) out.get(0));
            final var finalOut = (OnnxTensor) output.get().run(inputs).get(0);
            isNan(finalOut);
            return slice(finalOut);
        }
    }
}
