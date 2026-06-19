package me.batata_1.fractal_terrain.ml.pipeline;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;

import java.util.*;
import java.util.stream.Collectors;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.infinitetensor.AdditiveInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.InfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.TensorWindow;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.OnnxModel;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import me.batata_1.fractal_terrain.ml.tensorProviders.GaussianNoisePatch;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java port of terrain_diffusion/inference/world_pipeline.py WorldPipeline.
 *
 * <p>Three stages: coarse (20-step DPM-Solver++), latent (2 flow-matching steps),
 * decoder (1 flow-matching step).  All pixel coordinates are native-resolution space.
 */
public final class WorldPipeline implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPipeline.class);

    static final int LATENT_COMPRESSION = WorldPipelineModelConfig.latentCompression();
    static final float SIGMA_DATA = EDMScheduler.SIGMA_DATA;

    static final int COARSE_TILE_SIZE = 64;
    static final int COARSE_TILE_STRIDE = 48;
    static final int LATENT_TILE_SIZE = 64;
    static final int LATENT_TILE_STRIDE = 32;
    static final int DECODER_TILE_SIZE = 512;
    static final int DECODER_TILE_STRIDE = 384;

    static final float[] MODEL_MEANS = WorldPipelineModelConfig.coarseMeans();
    static final float[] MODEL_STDS = WorldPipelineModelConfig.coarseStds();

    static final float[] COND_MEANS = {14.99f, 11.65f, 15.87f, 619.26f, 833.12f, 69.40f, 0.66f};
    static final float[] COND_STDS = {21.72f, 21.78f, 10.40f, 452.29f, 738.09f, 34.59f, 0.47f};

    static final float LOWFREQ_MEAN = -31.4f;
    static final float LOWFREQ_STD = 38.6f;
    static final float RESIDUAL_MEAN = WorldPipelineModelConfig.residualMean();
    static final float RESIDUAL_STD = WorldPipelineModelConfig.residualStd();

    static final float[] COND_SNR = WorldPipelineModelConfig.conditioningSnr();
    static final int COARSE_POOLING = WorldPipelineModelConfig.coarsePooling();
    static final float[] COND_VALS; // log(COND_SNR / 8)

    static {
        if (COARSE_POOLING != 1) {
            throw new IllegalStateException(
                    "coarse_pooling=" + COARSE_POOLING + " is not supported in the Java pipeline yet");
        }
        COND_VALS = new float[COND_SNR.length];
        for (int i = 0; i < COND_SNR.length; i++) COND_VALS[i] = (float) Math.log(COND_SNR[i] / 8.0);
    }

    // mp_concat scales for 6 tensors of sizes [16, 16, 4, 16, 5, 1] → 58 total
    static final int[] COND_DIMS = {16, 16, 4, 16, 5, 1};
    static final float[] MP_CONCAT_SCALES;
    static final float[] HISTOGRAM_RAW;

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

    private final OnnxModel coarseModel;
    private final OnnxModel baseModel;
    private final OnnxModel decoderModel;
    private final OnnxModel fuzedModel;
    private final boolean ownModels;
    private volatile SyntheticMapFactory syntheticMapFactory;
    private volatile long seed;
    private volatile float[] tau = new float[] {1F};
    private final long cacheLimitBytes = 50L * 1024 * 1024;

    /**
     * Per-thread reusable coarse model input (11 channels). Fully overwritten on every use, so it is
     * safe to reuse across the 20 diffusion steps of a tile and across tiles on the same worker
     * thread — replacing a 180 KB allocation per step (~3.6 MB/tile of pure churn).
     */
    private final ThreadLocal<float[]> coarseInputScratch =
            ThreadLocal.withInitial(() -> new float[11 * COARSE_TILE_SIZE * COARSE_TILE_SIZE]);

    /** Per-thread reusable decoder model input (5 channels); fully overwritten on each decoder tile. */
    private final ThreadLocal<float[]> decoderInputScratch =
            ThreadLocal.withInitial(() -> new float[5 * DECODER_TILE_SIZE * DECODER_TILE_SIZE]);

    final AdditiveInfiniteTensor coarse;
    final AdditiveInfiniteTensor latents;
    final AdditiveInfiniteTensor residual;

    /** Uses shared models from PipelineModels (e.g. from mod init). Does not close models on close(). Seed is 64-bit (Python: seed & 0xFFFFFFFFFFFFFFFF). */
    public WorldPipeline(long seed, PipelineModels models) {
        this.seed = seed;
        this.coarseModel = models.getCoarseModel();
        this.baseModel = models.getBaseModel();
        this.decoderModel = models.getDecoderModel();
        this.fuzedModel = models.getFuzedModel();
        this.ownModels = false;
        this.syntheticMapFactory = new SyntheticMapFactory(this.seed);
        this.coarse = buildCoarseStage();
        this.latents = buildLatentStage();
        this.residual = buildDecoderStage();
    }

    /** Loads its own models (e.g. for tests). Caller must close. */
    @TestOnly
    public WorldPipeline(long seed) {
        this.seed = seed;
        ModelAssetManager.ensureAssetsReady();
        this.coarseModel = new OnnxModel(ModelAssetManager.resolveAssetPath("coarse_model.onnx"), "coarse");
        this.baseModel = new OnnxModel(ModelAssetManager.resolveAssetPath("base_model.onnx"), "base");
        this.decoderModel = new OnnxModel(ModelAssetManager.resolveAssetPath("decoder_model.onnx"), "decoder");
        this.fuzedModel = new OnnxModel(ModelAssetManager.resolveAssetPath("fuzed.onnx"), "fuzed");
        this.ownModels = true;
        this.syntheticMapFactory = new SyntheticMapFactory(this.seed);
        this.coarse = buildCoarseStage();
        this.latents = buildLatentStage();
        this.residual = buildDecoderStage();
    }

    /** Lightweight seed change (Python change_seed): update seed and synthetic map, clear tile caches. Models stay loaded. */
    public synchronized void updateInstance(final long newSeed, final String newPath) {
        if (newSeed == this.seed && Objects.equals(coarse.getCurrentPath(), newPath)) return;
        updateInfiniteTensors(newPath);
        this.seed = newSeed;
        this.syntheticMapFactory = new SyntheticMapFactory(newSeed);
    }

    private synchronized void updateInfiniteTensors(String newPath) {
        coarse.updatePath(newPath);
        latents.updatePath(newPath);
        residual.updatePath(newPath);
    }

    // =========================================================================
    // Coarse Stage
    // =========================================================================

    private AdditiveInfiniteTensor buildCoarseStage() {
        int S = COARSE_TILE_SIZE, ST = COARSE_TILE_STRIDE;
        float[] ww = linearWeightWindow(S);
        TensorWindow outWin = new TensorWindow(new int[] {7, S, S}, new int[] {7, ST, ST});
        return new AdditiveInfiniteTensor(
                "base_coarse_map",
                new int[] {7, -1, -1},
                outWin,
                (wi, args) -> coarseTile(wi, ww),
                null,
                -1,
                new AdditiveInfiniteTensor[] {},
                new TensorWindow[] {},
                cacheLimitBytes);
    }

    private FloatTensor coarseTile(int[] wi, float[] ww) {
        int S = COARSE_TILE_SIZE, ST = COARSE_TILE_STRIDE;
        int i = wi[1], j = wi[2];
        int i1 = i * ST, j1 = j * ST;
        Debug.debugCalls(wi, "coarse");
        // Synthetic map conditioning: channels [elev_sqrt, temp, tempStd, precip, precipStd]
        // Python call: synthetic_map_factory(j1, i1, j2, i2)
        // Coordinates are intentionally swapped
        float[][][] syn = syntheticMapFactory.sample(j1, i1, j1 + S, i1 + S);

        // Modify temp channel (index 1): where <= 20, scale toward 20
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
        float[] condNoise = flatten3D(GaussianNoisePatch.generate(seed, i1, j1, S, S, 5, S, S));

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
        float[] sample = flatten3D(GaussianNoisePatch.generate(seed + 1, i1, j1, S, S, 6, S, S));
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
                out[ch * S * S + px] = (sample[ch * S * S + px] / SIGMA_DATA) * MODEL_STDS[ch] + MODEL_MEANS[ch];

        // ch1 = ch0 - ch1 (convert to p5)
        for (int px = 0; px < S * S; px++) out[S * S + px] = out[px] - out[S * S + px];

        // Output: (7, S, S) = [6 channels * weight | weight]
        FloatTensor result = new FloatTensor(new int[] {7, S, S});
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < S * S; px++) result.data[ch * S * S + px] = out[ch * S * S + px] * ww[px];
        System.arraycopy(ww, 0, result.data, 6 * S * S, S * S);
        return result;
    }

    // =========================================================================
    // Latent Stage
    // =========================================================================

    private AdditiveInfiniteTensor buildLatentStage() {
        int S = LATENT_TILE_SIZE, ST = LATENT_TILE_STRIDE;
        TensorWindow outWin = new TensorWindow(new int[] {6, S, S}, new int[] {6, ST, ST});
        TensorWindow coarseWin = new TensorWindow(new int[] {7, 4, 4}, new int[] {7, 1, 1}, new int[] {0, -1, -1});
        float[] ww = linearWeightWindow(S);
        float tInit = (float) Math.atan(EDMScheduler.SIGMA_MAX / SIGMA_DATA);

        final AdditiveInfiniteTensor initLatent = new AdditiveInfiniteTensor(
                "init_latent_map",
                new int[] {6, -1, -1},
                outWin,
                null,
                (wis, args) -> latentBatch(wis, null, args.getFirst(), tInit, 5819, ww),
                4,
                new AdditiveInfiniteTensor[] {coarse},
                new TensorWindow[] {coarseWin},
                cacheLimitBytes);

        float interT = (float) Math.atan(0.35f / SIGMA_DATA);
        return new AdditiveInfiniteTensor(
                "step_latent_map_0",
                new int[] {6, -1, -1},
                outWin,
                null,
                (wis, args) -> latentBatch(wis, args.getFirst(), args.get(1), interT, 5820, ww),
                4,
                new AdditiveInfiniteTensor[] {initLatent, coarse},
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
        int S = LATENT_TILE_SIZE, ST = LATENT_TILE_STRIDE;
        int batch = wis.size();
        float cosT = (float) Math.cos(t), sinT = (float) Math.sin(t);

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
                        float w = ps.data[5 * S * S + px];
                        sample[ch * S * S + px] = (w > 1e-6f) ? ps.data[ch * S * S + px] / w * SIGMA_DATA : 0f;
                    }
            }

            // z = noise * sigma_data; x_t = cos(t)*sample + sin(t)*z
            float[] noise = flatten3D(GaussianNoisePatch.generate(seed + seedOffset, i1, j1, S, S, 5, S, S));
            float[] xT = new float[5 * S * S];
            for (int k = 0; k < 5 * S * S; k++) {
                float z = noise[k] * SIGMA_DATA;
                xT[k] = cosT * sample[k] + sinT * z;
            }
            xTArr[b] = xT;

            // model_in = xT / sigma_data
            for (int k = 0; k < 5 * S * S; k++) modelInBatch[b * 5 * S * S + k] = xT[k] / SIGMA_DATA;
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
        List<FloatTensor> results = new ArrayList<>(batch);
        for (int b = 0; b < batch; b++) {
            float[] xT = xTArr[b];
            float[] newSample = new float[5 * S * S];
            for (int k = 0; k < 5 * S * S; k++) {
                float pred = -predBatch[b * 5 * S * S + k]; // base model output is negated
                newSample[k] = (cosT * xT[k] - sinT * SIGMA_DATA * pred) / SIGMA_DATA;
            }

            FloatTensor out = new FloatTensor(new int[] {6, S, S});
            for (int ch = 0; ch < 5; ch++)
                for (int px = 0; px < S * S; px++) out.data[ch * S * S + px] = newSample[ch * S * S + px] * ww[px];
            System.arraycopy(ww, 0, out.data, 5 * S * S, S * S);
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
                float w = coarseSlice.data[6 * N + px];
                condFlat[ch * N + px] = (w > 1e-6f) ? coarseSlice.data[ch * N + px] / w : 0f;
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

    // =========================================================================
    // Decoder Stage
    // =========================================================================

    private AdditiveInfiniteTensor buildDecoderStage() {
        int S = DECODER_TILE_SIZE, ST = DECODER_TILE_STRIDE, lc = LATENT_COMPRESSION;
        TensorWindow outWin =
                new TensorWindow(new int[] {DECODER_CHANNELS, S, S}, new int[] {DECODER_CHANNELS, ST, ST});
        TensorWindow inpWin = new TensorWindow(new int[] {6, S / lc, S / lc}, new int[] {6, ST / lc, ST / lc});
        float[] ww = linearWeightWindow(S);
        float t = (float) Math.atan(EDMScheduler.SIGMA_MAX / SIGMA_DATA);

        return new AdditiveInfiniteTensor(
                "init_residual_map",
                new int[] {2, -1, -1},
                outWin,
                (wi, args) -> decoderTile(wi, args.getFirst(), t, ww),
                null,
                -1,
                new AdditiveInfiniteTensor[] {latents},
                new TensorWindow[] {inpWin},
                cacheLimitBytes);
    }

    private FloatTensor decoderTile(int[] wi, FloatTensor latentSlice, float t, float[] ww) {
        Debug.debugCalls(wi, "decoder");
        final int S = DECODER_TILE_SIZE;
        final int ST = DECODER_TILE_STRIDE;
        final int Slc = S / LATENT_COMPRESSION;
        final int i1 = wi[1] * ST, j1 = wi[2] * ST;
        final float cosT = (float) Math.cos(t), sinT = (float) Math.sin(t);

        // Unnormalize latents channels 0..3 (4 channels)
        final float[] latFlat = new float[4 * Slc * Slc];
        for (int ch = 0; ch < 4; ch++)
            for (int px = 0; px < Slc * Slc; px++) {
                float w = latentSlice.data[5 * Slc * Slc + px];
                latFlat[ch * Slc * Slc + px] = (w > 1e-6f) ? latentSlice.data[ch * Slc * Slc + px] / w : 0f;
            }

        // Nearest-neighbor upsample (4, Slc, Slc) → (4, S, S)
        float[] upsampled = nearestUpsample(latFlat, 4, Slc, Slc, S, S);

        // One flow-matching step (sample starts at zero)
        float[] noise = flatten3D(GaussianNoisePatch.generate(seed + 5819, i1, j1, S, S, 1, S, S));
        float[] xT = new float[S * S];
        for (int k = 0; k < S * S; k++) xT[k] = sinT * noise[k] * SIGMA_DATA; // sample=0

        // model_in = concat([xT/sigma_data (1,S,S), upsampled (4,S,S)]) → (5,S,S).
        // Reuse the per-thread scratch buffer; fully overwritten below.
        final float[] modelIn = decoderInputScratch.get();
        for (int k = 0; k < S * S; k++) modelIn[k] = xT[k] / SIGMA_DATA;
        System.arraycopy(upsampled, 0, modelIn, S * S, 4 * S * S);

        LOG.debug(
                "Decoder model called for chunk ({}, {}) tile pixels [{}, {}]-[{}, {}]",
                wi[1],
                wi[2],
                i1,
                j1,
                i1 + S,
                j1 + S);
        float[] rawPred = decoderModel.runModel(modelIn, new long[] {1, 5, S, S}, new float[] {t}, null, null);

        // sample = cos(t)*xT - sin(t)*sigma_data*(-rawPred); then / sigma_data
        float[] newSample = new float[S * S];
        for (int k = 0; k < S * S; k++) {
            float pred = -rawPred[k]; // decoder model output is negated
            newSample[k] = (cosT * xT[k] - sinT * SIGMA_DATA * pred) / SIGMA_DATA;
        }

        // post proessing
        Object[][] inputs = new Object[3][3];
        inputs[0] = new Object[] {"residual_init", newSample, new long[] {1, 512, 512}};
        inputs[1] = new Object[] {"latents_init", latentSlice.data, new long[] {6, 64, 64}};
        inputs[2] = new Object[] {"tau", tau, new long[] {1}};

        final float[] data = fuzedModel.run(inputs);
        final float[] out = new float[S * S * DECODER_CHANNELS];
        System.arraycopy(data, 7 * S * S, out, 0, S * S);
        System.arraycopy(data, 0, out, S * S, 7 * S * S);

        return new FloatTensor(new int[] {DECODER_CHANNELS, S, S}, out);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Returns the current world seed. */
    public long getSeed() {
        return seed;
    }

    /**
     * Returns a coarse tensor slice with shape [7, ci1-ci0, cj1-cj0].
     * Coordinates are in coarse index units (1 unit = 256 native pixels).
     * me.batata_1.fractal_terrain.hydrology.meanders.Channel 6 is the blend weight; channels 0–5 are weighted sums.
     */
    public FloatTensor getCoarseSlice(int ci0, int cj0, int ci1, int cj1) {
        return coarse.getSlice(new int[] {0, ci0, cj0}, new int[] {7, ci1, cj1});
    }

    /**
     * Returns a decoder (residual) tensor slice with shape [DECODER_CHANNELS, i1-i0, j1-j0].
     * Coordinates are in native pixel units. Mirrors {@link #getCoarseSlice}: callers pass explicit
     * pixel bounds so they can request a padded/halo crop rather than an exact 512×512 tile.
     */
    public FloatTensor getDecoderSlice(int i0, int j0, int i1, int j1) {
        return residual.getSlice(new int[] {0, i0, j0}, new int[] {DECODER_CHANNELS, i1, j1});
    }

    public float[] getClimate(int x, int z, float[] elevFlat) {
        return computeClimate(x << 9, z << 9, (x + 1) << 9, (z + 1) << 9, elevFlat, 1 << 9, 1 << 9);
    }

    // =========================================================================
    // Climate
    // =========================================================================

    public static float[][][] localBaselineTemperature(float[][] T, float[][] e, int win, float fallbackThreshold) {
        int H = T.length, W = T[0].length;
        int outH = H - win + 1, outW = W - win + 1;
        float[][][] result = new float[2][outH][outW];

        float fallbackBeta = -0.0065f;
        float betaMin = -0.012f, betaMax = 0.0f;
        float eps = 1e-6f;

        for (int r = 0; r < outH; r++) {
            for (int c = 0; c < outW; c++) {
                // Compute windowed weighted averages (weight = land mask = e > 0)
                double muT = 0, muE = 0, muE2 = 0, muET = 0, sumW = 0;
                int n = win * win;
                for (int dr = 0; dr < win; dr++) {
                    for (int dc = 0; dc < win; dc++) {
                        float land = (e[r + dr][c + dc] > 0) ? 1.0f : 0.0f;
                        muT += T[r + dr][c + dc] * land;
                        muE += e[r + dr][c + dc] * land;
                        muE2 += e[r + dr][c + dc] * e[r + dr][c + dc] * land;
                        muET += e[r + dr][c + dc] * T[r + dr][c + dc] * land;
                        sumW += land;
                    }
                }
                double den = sumW + eps;
                muT /= den;
                muE /= den;
                muE2 /= den;
                muET /= den;
                double varE = muE2 - muE * muE;
                double covET = muET - muE * muT;
                double beta = (varE < 1.0 || sumW < fallbackThreshold * n) ? fallbackBeta : (covET / (varE + eps));
                beta = Math.clamp(beta, betaMin, betaMax);

                int pad = (win - 1) / 2;
                float Tc = T[r + pad][c + pad];
                float ec = e[r + pad][c + pad];
                result[0][r][c] = (float) (Tc - beta * ec);
                result[1][r][c] = (float) beta;
            }
        }
        return result;
    }

    private float[] computeClimate(int i1, int j1, int i2, int j2, float[] elevFlat, int H, int W) {
        int S = 32 * LATENT_COMPRESSION; // native pixels per coarse pixel in stride sense

        int ci1 = Math.floorDiv(i1, S);
        int cj1 = Math.floorDiv(j1, S);
        int ci2 = -Math.floorDiv(-i2, S);
        int cj2 = -Math.floorDiv(-j2, S);

        int win = 15, pad = (win - 1) / 2 + 1;

        FloatTensor coarseSlice =
                coarse.getSlice(new int[] {0, ci1 - pad, cj1 - pad}, new int[] {7, ci2 + pad, cj2 + pad});
        int cH = ci2 + pad - (ci1 - pad);
        int cW = cj2 + pad - (cj1 - pad);

        // Unnormalize all 6 coarse channels
        float[][] coarseMap = new float[6][cH * cW];
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < cH * cW; px++) {
                float w = coarseSlice.data[6 * cH * cW + px];
                coarseMap[ch][px] = (w > 1e-6f) ? coarseSlice.data[ch * cH * cW + px] / w : 0f;
            }

        // Coarse elevation (undo sqrt): max(0, v)^2  — ocean pixels clamp to 0, matching Python
        float[] coarseElev = new float[cH * cW];
        for (int px = 0; px < cH * cW; px++) {
            float v = Math.max(0f, coarseMap[0][px]);
            coarseElev[px] = v * v;
        }

        // Windowed lapse-rate regression
        float[][][] lbt = localBaselineTemperature(to2D(coarseMap[2], cH, cW), to2D(coarseElev, cH, cW), win, 0.02f);
        int lH = lbt[0].length, lW = lbt[0][0].length;

        // Central coarse (crop pad pixels from each side)
        int cenPad = win / 2;
        int cenH = cH - 2 * cenPad, cenW = cW - 2 * cenPad;
        float[][][] centralCoarse = new float[6][cenH][cenW];
        for (int ch = 0; ch < 6; ch++) {
            float[][] full = to2D(coarseMap[ch], cH, cW);
            centralCoarse[ch] = cropArray(full, cenPad, cenPad, cenH, cenW);
        }

        // Bilinear upsample to native resolution
        float[] climate = new float[5 * H * W];
        for (int r = 0; r < H; r++) {
            // fractional index into lbt/centralCoarse arrays (matches Python's u = (ii+0.5)/S - ci1 + 0.5)
            float cenGridY = (i1 + r + 0.5f) / S - ci1 + 0.5f;
            for (int c = 0; c < W; c++) {
                float cenGridX = (j1 + c + 0.5f) / S - cj1 + 0.5f;

                float tBase = bilinearSample2D(lbt[0], lH, lW, cenGridY, cenGridX);
                float beta = bilinearSample2D(lbt[1], lH, lW, cenGridY, cenGridX);
                float tempReal = tBase + beta * Math.max(0f, elevFlat[r * W + c]);

                climate[r * W + c] = tempReal;
                climate[H * W + r * W + c] = bilinearSample2D(centralCoarse[3], cenH, cenW, cenGridY, cenGridX);
                climate[2 * H * W + r * W + c] = bilinearSample2D(centralCoarse[4], cenH, cenW, cenGridY, cenGridX);
                climate[3 * H * W + r * W + c] = bilinearSample2D(centralCoarse[5], cenH, cenW, cenGridY, cenGridX);
                climate[4 * H * W + r * W + c] = beta;
            }
        }
        return climate;
    }

    // =========================================================================
    // Static helpers
    // =========================================================================

    static float[] linearWeightWindow(int size) {
        float[] w = new float[size * size];
        float mid = (size - 1) / 2.0f, eps = 1e-3f;
        for (int r = 0; r < size; r++) {
            float wy = 1f - (1f - eps) * Math.min(1f, Math.abs(r - mid) / mid);
            for (int c = 0; c < size; c++) {
                float wx = 1f - (1f - eps) * Math.min(1f, Math.abs(c - mid) / mid);
                w[r * size + c] = wy * wx;
            }
        }
        return w;
    }

    static float[] flatten3D(float[][][] arr) {
        int C = arr.length, H = arr[0].length, W = arr[0][0].length;
        float[] out = new float[C * H * W];
        for (int c = 0; c < C; c++)
            for (int r = 0; r < H; r++) System.arraycopy(arr[c][r], 0, out, c * H * W + r * W, W);
        return out;
    }

    static int appendScaled(float[] out, int off, float[] arr, float scale) {
        for (float v : arr) out[off++] = v * scale;
        return off;
    }

    static float[] nearestUpsample(float[] src, int C, int sH, int sW, int dH, int dW) {
        float[] dst = new float[C * dH * dW];
        for (int c = 0; c < C; c++)
            for (int r = 0; r < dH; r++) {
                int sr = r * sH / dH;
                for (int col = 0; col < dW; col++)
                    dst[c * dH * dW + r * dW + col] = src[c * sH * sW + sr * sW + col * sW / dW];
            }
        return dst;
    }

    static float[][] to2D(float[] flat, int H, int W) {
        float[][] a = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(flat, r * W, a[r], 0, W);
        return a;
    }

    static float[][] cropArray(float[][] src, int r0, int c0, int H, int W) {
        float[][] out = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(src[r + r0], c0, out[r], 0, W);
        return out;
    }

    static float bilinearSample2D(float[][] src, int H, int W, float gy, float gx) {
        float y = Math.clamp(gy, 0f, H - 1f);
        float x = Math.clamp(gx, 0f, W - 1f);
        int y0 = (int) y, y1 = Math.min(H - 1, y0 + 1);
        int x0 = (int) x, x1 = Math.min(W - 1, x0 + 1);
        float wy = y - y0, wx = x - x0;
        return (1 - wy) * (1 - wx) * src[y0][x0]
                + (1 - wy) * wx * src[y0][x1]
                + wy * (1 - wx) * src[y1][x0]
                + wy * wx * src[y1][x1];
    }

    @Override
    public void close() {
        if (ownModels) {
            coarseModel.close();
            baseModel.close();
            decoderModel.close();
            fuzedModel.close();
        }
    }

    @TestOnly
    public InfiniteTensor getDecoder() {
        return residual;
    }

    public InfiniteTensor getCoarse() {
        return coarse;
    }
}
