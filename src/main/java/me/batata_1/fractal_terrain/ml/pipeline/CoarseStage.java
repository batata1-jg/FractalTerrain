package me.batata_1.fractal_terrain.ml.pipeline;

import java.util.function.Supplier;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.infinitetensor.AdditiveInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.TensorWindow;
import me.batata_1.fractal_terrain.ml.models.OnnxModel;
import me.batata_1.fractal_terrain.ml.tensorProviders.GaussianNoisePatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The coarse diffusion stage, first of three. Runs 20-step DPM-Solver++ over native-pixel tiles to
 * produce {@code base_coarse_map}, the low-resolution elevation/climate tensor every later stage
 * conditions on.
 *
 * <p>Reads {@code coarseModel}, seed-derived noise from {@link GaussianNoisePatch}, and the current
 * {@link PipelineSession} snapshot exactly once per tile (MUST-1) — the synthetic map and noise must
 * come from the same reload generation.
 */
final class CoarseStage {

    private static final Logger LOG = LoggerFactory.getLogger(CoarseStage.class);

    static final int TILE_SIZE = 64;
    static final int TILE_STRIDE = 48;

    private static final float[] MODEL_MEANS = WorldPipelineModelConfig.coarseMeans();
    private static final float[] MODEL_STDS = WorldPipelineModelConfig.coarseStds();
    private static final float[] COND_SNR = WorldPipelineModelConfig.conditioningSnr();
    private static final int COARSE_POOLING = WorldPipelineModelConfig.coarsePooling();
    private static final float[] COND_VALS; // log(COND_SNR / 8)

    static {
        if (COARSE_POOLING != 1) {
            throw new IllegalStateException(
                    "coarse_pooling=" + COARSE_POOLING + " is not supported in the Java pipeline yet");
        }
        COND_VALS = new float[COND_SNR.length];
        for (int i = 0; i < COND_SNR.length; i++) COND_VALS[i] = (float) Math.log(COND_SNR[i] / 8.0);
    }

    private final OnnxModel coarseModel;
    private final Supplier<PipelineSession> sessionSupplier;
    private final long cacheLimitBytes;

    /** Reused across a tile's 20 diffusion steps to avoid ~3.6 MB/tile of per-step allocation churn. */
    private final ThreadLocal<float[]> coarseInputScratch =
            ThreadLocal.withInitial(() -> new float[11 * TILE_SIZE * TILE_SIZE]);

    private final AdditiveInfiniteTensor tensor;

    CoarseStage(OnnxModel coarseModel, Supplier<PipelineSession> sessionSupplier, long cacheLimitBytes) {
        this.coarseModel = coarseModel;
        this.sessionSupplier = sessionSupplier;
        this.cacheLimitBytes = cacheLimitBytes;
        this.tensor = build();
    }

    AdditiveInfiniteTensor tensor() {
        return tensor;
    }

    private AdditiveInfiniteTensor build() {
        int S = TILE_SIZE, ST = TILE_STRIDE;
        float[] ww = WorldPipeline.linearWeightWindow(S);
        TensorWindow outWin = new TensorWindow(new int[] {7, S, S}, new int[] {7, ST, ST});
        return new AdditiveInfiniteTensor(
                "base_coarse_map",
                new int[] {7, -1, -1},
                outWin,
                (wi, args) -> tile(wi, ww),
                null,
                -1,
                new AdditiveInfiniteTensor[] {},
                new TensorWindow[] {},
                cacheLimitBytes);
    }

    private FloatTensor tile(int[] wi, float[] ww) {
        int S = TILE_SIZE, ST = TILE_STRIDE;
        int i = wi[1], j = wi[2];
        int i1 = i * ST, j1 = j * ST;
        Debug.debugCalls(wi, "coarse");
        // Snapshot the reload-scoped session ONCE so the synthetic map and every seed-derived noise draw
        // below come from the same reload generation (MUST-1 — see PipelineSession).
        final PipelineSession s = sessionSupplier.get();
        final long seed = s.seed();
        // Synthetic map conditioning: channels [elev_sqrt, temp, tempStd, precip, precipStd]
        // Python call: synthetic_map_factory(j1, i1, j2, i2)
        // Coordinates are intentionally swapped
        float[][][] syn = s.syntheticMapFactory().sample(j1, i1, j1 + S, i1 + S);

        // Modify temp channel (index 1): where <= 20, scale the distance below 20 by 1.25x (exaggerates cold)
        for (int r = 0; r < S; r++)
            for (int c = 0; c < S; c++) {
                float v = syn[1][r][c];
                if (v <= 20f) syn[1][r][c] = (v - 20f) * 1.25f + 20f;
            }

        // Normalize with MODEL_MEANS/STDS indices [0,2,3,4,5]
        int[] meanIdx = {0, 2, 3, 4, 5};
        float[] condImg = new float[5 * S * S];
        for (int ch = 0; ch < 5; ch++) {
            float mean = MODEL_MEANS[meanIdx[ch]], std = MODEL_STDS[meanIdx[ch]];
            for (int px = 0; px < S * S; px++) condImg[ch * S * S + px] = (syn[ch][px / S][px % S] - mean) / std;
        }

        // Conditioning noise: Gaussian noise (5, S, S)
        float[] condNoise = WorldPipeline.flatten3D(GaussianNoisePatch.generate(seed, i1, j1, S, S, 5, S, S));

        // cond_img_mixed = cos(t_cond) * normalized + sin(t_cond) * noise
        float[] condMixed = new float[5 * S * S];
        for (int ch = 0; ch < 5; ch++) {
            float cosT = (float) Math.cos(Math.atan(COND_SNR[ch]));
            float sinT = (float) Math.sin(Math.atan(COND_SNR[ch]));
            for (int px = 0; px < S * S; px++) {
                condMixed[ch * S * S + px] = cosT * condImg[ch * S * S + px] + sinT * condNoise[ch * S * S + px];
            }
        }

        // Initial sample: (6, S, S) noise * sigma_max
        EDMScheduler sched = new EDMScheduler(20);
        float[] sample = WorldPipeline.flatten3D(GaussianNoisePatch.generate(seed + 1, i1, j1, S, S, 6, S, S));
        for (int k = 0; k < sample.length; k++) sample[k] *= sched.sigmas[0];

        // 20-step DPM-Solver++
        float[][] condInputs = new float[5][1];
        long[][] condShapes = new long[5][1];
        for (int ci = 0; ci < 5; ci++) {
            condInputs[ci] = new float[] {COND_VALS[ci]};
            condShapes[ci] = new long[] {1};
        }

        LOG.debug(
                "Coarse model called for chunk ({}, {}) tile pixels [{}, {}]-[{}, {}] (20 steps)",
                i,
                j,
                i1,
                j1,
                i1 + S,
                j1 + S);
        // Reused across all 20 steps. The conditioning half (channels 6..10 = condMixed) is constant
        // across steps, so copy it once; only the scaledIn half is refreshed per step.
        final float[] xIn = coarseInputScratch.get();
        System.arraycopy(condMixed, 0, xIn, 6 * S * S, 5 * S * S);
        for (int step = 0; step < 20; step++) {
            float sigma = sched.sigmas[step];
            float cnoise = EDMScheduler.trigflowPreconditionNoise(sigma);
            float[] scaledIn = EDMScheduler.preconditionInputs(sample, sigma);
            System.arraycopy(scaledIn, 0, xIn, 0, 6 * S * S);

            float[] modelOut =
                    coarseModel.runModel(xIn, new long[] {1, 11, S, S}, new float[] {cnoise}, condInputs, condShapes);
            sample = sched.step(modelOut, sample);
        }

        // Denormalize: sample / sigma_data → raw, then * STDS + MEANS
        float[] out = new float[6 * S * S];
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < S * S; px++)
                out[ch * S * S + px] =
                        (sample[ch * S * S + px] / EDMScheduler.SIGMA_DATA) * MODEL_STDS[ch] + MODEL_MEANS[ch];

        // ch1 = ch0 - ch1 (convert to p5)
        for (int px = 0; px < S * S; px++) out[S * S + px] = out[px] - out[S * S + px];

        // Output: (7, S, S) = [6 channels * weight | weight]
        FloatTensor result = new FloatTensor(new int[] {7, S, S});
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < S * S; px++) result.set(ch * S * S + px, out[ch * S * S + px] * ww[px]);
        result.writeFrom(ww, 0, 6 * S * S, S * S);
        return result;
    }
}
