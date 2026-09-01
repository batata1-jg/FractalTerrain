package me.batata_1.fractal_terrain.ml.pipeline;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.infinitetensor.AdditiveInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.TensorWindow;
import me.batata_1.fractal_terrain.ml.models.OnnxModel;
import me.batata_1.fractal_terrain.ml.tensorProviders.GaussianNoisePatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The latent stage, second of three. Runs 2 flow-matching steps that refine {@link CoarseStage}'s
 * output into {@code step_latent_map_0}, the tensor {@link DecoderStage} conditions on.
 *
 * <p>Conditions on a 4x4 slice of the coarse tensor — no hidden coupling with {@link CoarseStage}
 * beyond that slice. Reads the current {@link PipelineSession} seed once per batch (MUST-1).
 */
final class LatentStage {

    private static final Logger LOG = LoggerFactory.getLogger(LatentStage.class);

    static final int TILE_SIZE = 64;
    static final int TILE_STRIDE = 32;

    private static final float[] COND_MEANS = {14.99f, 11.65f, 15.87f, 619.26f, 833.12f, 69.40f, 0.66f};
    private static final float[] COND_STDS = {21.72f, 21.78f, 10.40f, 452.29f, 738.09f, 34.59f, 0.47f};

    // mp_concat scales for 6 tensors of sizes [16, 16, 4, 16, 5, 1] → 58 total
    private static final int[] COND_DIMS = {16, 16, 4, 16, 5, 1};
    private static final float[] MP_CONCAT_SCALES;
    private static final float[] HISTOGRAM_RAW;

    static {
        int sumN = 0;
        for (int n : COND_DIMS) sumN += n;
        int k = COND_DIMS.length;
        float C = (float) Math.sqrt((double) sumN * k);
        MP_CONCAT_SCALES = new float[k];
        for (int i = 0; i < k; i++) MP_CONCAT_SCALES[i] = C / (float) Math.sqrt(COND_DIMS[i]) / k;
        float[] configuredHistogramRaw = WorldPipelineModelConfig.histogramRaw();
        HISTOGRAM_RAW = configuredHistogramRaw != null ? configuredHistogramRaw : new float[] {0f, 0f, 0f, 0f, 0f};
    }

    private final OnnxModel baseModel;
    private final Supplier<PipelineSession> sessionSupplier;
    private final long cacheLimitBytes;

    private final AdditiveInfiniteTensor tensor;

    LatentStage(
            OnnxModel baseModel,
            Supplier<PipelineSession> sessionSupplier,
            long cacheLimitBytes,
            AdditiveInfiniteTensor coarseTensor) {
        this.baseModel = baseModel;
        this.sessionSupplier = sessionSupplier;
        this.cacheLimitBytes = cacheLimitBytes;
        this.tensor = build(coarseTensor);
    }

    AdditiveInfiniteTensor tensor() {
        return tensor;
    }

    private AdditiveInfiniteTensor build(AdditiveInfiniteTensor coarseTensor) {
        int S = TILE_SIZE, ST = TILE_STRIDE;
        TensorWindow outWin = new TensorWindow(new int[] {6, S, S}, new int[] {6, ST, ST});
        TensorWindow coarseWin = new TensorWindow(new int[] {7, 4, 4}, new int[] {7, 1, 1}, new int[] {0, -1, -1});
        float[] ww = WorldPipeline.linearWeightWindow(S);
        float tInit = (float) Math.atan(EDMScheduler.SIGMA_MAX / EDMScheduler.SIGMA_DATA);

        final AdditiveInfiniteTensor initLatent = new AdditiveInfiniteTensor(
                "init_latent_map",
                new int[] {6, -1, -1},
                outWin,
                null,
                (wis, args) -> latentBatch(wis, null, args.getFirst(), tInit, 5819, ww),
                4,
                new AdditiveInfiniteTensor[] {coarseTensor},
                new TensorWindow[] {coarseWin},
                cacheLimitBytes);

        float interT = (float) Math.atan(0.35f / EDMScheduler.SIGMA_DATA);
        return new AdditiveInfiniteTensor(
                "step_latent_map_0",
                new int[] {6, -1, -1},
                outWin,
                null,
                (wis, args) -> latentBatch(wis, args.getFirst(), args.get(1), interT, 5820, ww),
                4,
                new AdditiveInfiniteTensor[] {initLatent, coarseTensor},
                new TensorWindow[] {outWin, coarseWin},
                cacheLimitBytes);
    }

    private List<FloatTensor> latentBatch(
            List<int[]> wis,
            List<FloatTensor> prevSamples,
            List<FloatTensor> coarseSlices,
            float t,
            int seedOffset,
            float[] ww) {
        int S = TILE_SIZE, ST = TILE_STRIDE;
        int batch = wis.size();
        float cosT = (float) Math.cos(t), sinT = (float) Math.sin(t);
        final long seed = sessionSupplier.get().seed(); // snapshot once (MUST-1)

        // Intermediate storage: xT per batch element (needed for output step)
        float[][] xTArr = new float[batch][5 * S * S];

        float[] modelInBatch = new float[batch * 5 * S * S];
        float[] condInputBatch = new float[batch * 58];

        for (int b = 0; b < batch; b++) {
            int[] ctx = wis.get(b);
            Debug.debugCalls(ctx, "latent" + (prevSamples == null));
            int i1 = ctx[1] * ST, j1 = ctx[2] * ST;

            // Build conditioning from coarse slice (7, 4, 4)
            float[] cond58 = buildLatentConditioning(coarseSlices.get(b));
            System.arraycopy(cond58, 0, condInputBatch, b * 58, 58);

            // Build sample (unnormalized prev output or zeros)
            float[] sample = new float[5 * S * S];
            if (prevSamples != null) {
                FloatTensor ps = prevSamples.get(b);
                for (int ch = 0; ch < 5; ch++)
                    for (int px = 0; px < S * S; px++) {
                        float w = ps.get(5 * S * S + px);
                        sample[ch * S * S + px] =
                                (w > 1e-6f) ? ps.get(ch * S * S + px) / w * EDMScheduler.SIGMA_DATA : 0f;
                    }
            }

            // z = noise * sigma_data; x_t = cos(t)*sample + sin(t)*z
            float[] noise =
                    WorldPipeline.flatten3D(GaussianNoisePatch.generate(seed + seedOffset, i1, j1, S, S, 5, S, S));
            float[] xT = new float[5 * S * S];
            for (int k = 0; k < 5 * S * S; k++) {
                float z = noise[k] * EDMScheduler.SIGMA_DATA;
                xT[k] = cosT * sample[k] + sinT * z;
            }
            xTArr[b] = xT;

            // model_in = xT / sigma_data
            for (int k = 0; k < 5 * S * S; k++) modelInBatch[b * 5 * S * S + k] = xT[k] / EDMScheduler.SIGMA_DATA;
        }

        String chunkList = wis.stream().map(w -> "(" + w[1] + "," + w[2] + ")").collect(Collectors.joining(", "));
        LOG.debug("Base model called for {} chunks: {}", batch, chunkList);

        float[] noiseLabels = new float[batch];
        Arrays.fill(noiseLabels, t);

        float[] predBatch = baseModel.runModel(
                modelInBatch, new long[] {batch, 5, S, S}, noiseLabels, new float[][] {condInputBatch}, new long[][] {
                    {batch, 58}
                });

        // Build outputs: pred = -raw_model_out; sample = cos(t)*xT - sin(t)*sigma_data*pred
        List<FloatTensor> results = new ObjectArrayList<>(batch);
        for (int b = 0; b < batch; b++) {
            float[] xT = xTArr[b];
            float[] newSample = new float[5 * S * S];
            for (int k = 0; k < 5 * S * S; k++) {
                float pred = -predBatch[b * 5 * S * S + k]; // base model output is negated
                newSample[k] = (cosT * xT[k] - sinT * EDMScheduler.SIGMA_DATA * pred) / EDMScheduler.SIGMA_DATA;
            }

            FloatTensor out = new FloatTensor(new int[] {6, S, S});
            for (int ch = 0; ch < 5; ch++)
                for (int px = 0; px < S * S; px++) out.set(ch * S * S + px, newSample[ch * S * S + px] * ww[px]);
            out.writeFrom(ww, 0, 5 * S * S, S * S);
            results.add(out);
        }
        return results;
    }

    /** Build 58-dim conditioning vector from a (7,4,4) coarse tile slice. */
    private float[] buildLatentConditioning(FloatTensor coarseSlice) {
        int N = 4 * 4;
        // Unnormalize: cond[:-1] / cond[-1] for each pixel
        float[] condFlat = new float[6 * N];
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < N; px++) {
                float w = coarseSlice.get(6 * N + px);
                condFlat[ch * N + px] = (w > 1e-6f) ? coarseSlice.get(ch * N + px) / w : 0f;
            }

        // Append mask channel (all ones = (1 - mean) / std normalized)
        float[] condImg7 = new float[7 * N];
        System.arraycopy(condFlat, 0, condImg7, 0, 6 * N);
        float maskNorm = (1.0f - COND_MEANS[6]) / COND_STDS[6];
        for (int px = 0; px < N; px++) condImg7[6 * N + px] = maskNorm;

        // Normalize all 7 channels
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < N; px++) {
                float v = (condFlat[ch * N + px] - COND_MEANS[ch]) / COND_STDS[ch];
                condImg7[ch * N + px] = Float.isNaN(v) ? 0f : v;
            }

        // Extract components
        float[] meansCrop = new float[16];
        System.arraycopy(condImg7, 0, meansCrop, 0, 16);
        float[] p5Crop = new float[16];
        System.arraycopy(condImg7, 16, p5Crop, 0, 16);
        float[] maskCrop = new float[16];
        System.arraycopy(condImg7, 6 * 16, maskCrop, 0, 16);
        float[] climateMeans = new float[4];
        for (int ch = 0; ch < 4; ch++) {
            float sum = 0;
            for (int r = 1; r < 3; r++) for (int c = 1; c < 3; c++) sum += condImg7[(2 + ch) * 16 + r * 4 + c];
            climateMeans[ch] = sum / 4f;
            if (Float.isNaN(climateMeans[ch])) climateMeans[ch] = 0f;
        }

        float noiseLevelNorm = (0f - 0.5f) * (float) Math.sqrt(12.0);

        // mp_concat
        float[] out = new float[58];
        int off = 0;
        off = appendScaled(out, off, meansCrop, MP_CONCAT_SCALES[0]);
        off = appendScaled(out, off, p5Crop, MP_CONCAT_SCALES[1]);
        off = appendScaled(out, off, climateMeans, MP_CONCAT_SCALES[2]);
        off = appendScaled(out, off, maskCrop, MP_CONCAT_SCALES[3]);
        off = appendScaled(out, off, HISTOGRAM_RAW, MP_CONCAT_SCALES[4]);
        out[off] = noiseLevelNorm * MP_CONCAT_SCALES[5];
        return out;
    }

    private static int appendScaled(float[] out, int off, float[] arr, float scale) {
        for (float v : arr) out[off++] = v * scale;
        return off;
    }
}
