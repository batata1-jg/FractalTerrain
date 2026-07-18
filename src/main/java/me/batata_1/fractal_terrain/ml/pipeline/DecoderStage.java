package me.batata_1.fractal_terrain.ml.pipeline;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;

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
 * Responsibility: runs the single flow-matching decoder step plus the fuzed post-processing model,
 * producing the {@code init_residual_map} {@link AdditiveInfiniteTensor} ({@value #TILE_SIZE}x
 * {@value #TILE_SIZE} native-pixel tiles, {@code DECODER_CHANNELS} channels, {@code CH=0/X=1/Z=2}).
 *
 * <p>Collaborators: the {@code decoderModel} and {@code fuzedModel} ONNX models; the upstream latent
 * tensor (constructor-injected dependency, read via a compressed-resolution slice — no hidden coupling
 * with {@link LatentStage}); {@link WorldPipeline#LATENT_COMPRESSION}; the current {@link PipelineSession}
 * (seed and tau) obtained once per tile via {@code sessionSupplier}.
 *
 * <p>Invariants: output tensor is (DECODER_CHANNELS, S, S) with {@code CH=0/X=1/Z=2}; the tile-compute
 * reads the session snapshot exactly once (MUST-1); {@code decoderInputScratch} is a per-thread reusable
 * buffer, fully overwritten on every use.
 */
final class DecoderStage {

    private static final Logger LOG = LoggerFactory.getLogger(DecoderStage.class);

    static final int TILE_SIZE = 512;
    static final int TILE_STRIDE = 384;

    private final OnnxModel decoderModel;
    private final OnnxModel fuzedModel;
    private final Supplier<PipelineSession> sessionSupplier;
    private final long cacheLimitBytes;

    /** Per-thread reusable decoder model input (5 channels); fully overwritten on each decoder tile. */
    private final ThreadLocal<float[]> decoderInputScratch =
            ThreadLocal.withInitial(() -> new float[5 * TILE_SIZE * TILE_SIZE]);

    private final AdditiveInfiniteTensor tensor;

    DecoderStage(
            OnnxModel decoderModel,
            OnnxModel fuzedModel,
            Supplier<PipelineSession> sessionSupplier,
            long cacheLimitBytes,
            AdditiveInfiniteTensor latentsTensor) {
        this.decoderModel = decoderModel;
        this.fuzedModel = fuzedModel;
        this.sessionSupplier = sessionSupplier;
        this.cacheLimitBytes = cacheLimitBytes;
        this.tensor = build(latentsTensor);
    }

    AdditiveInfiniteTensor tensor() {
        return tensor;
    }

    private AdditiveInfiniteTensor build(AdditiveInfiniteTensor latentsTensor) {
        int S = TILE_SIZE, ST = TILE_STRIDE, lc = WorldPipeline.LATENT_COMPRESSION;
        TensorWindow outWin =
                new TensorWindow(new int[] {DECODER_CHANNELS, S, S}, new int[] {DECODER_CHANNELS, ST, ST});
        TensorWindow inpWin = new TensorWindow(new int[] {6, S / lc, S / lc}, new int[] {6, ST / lc, ST / lc});
        float[] ww = WorldPipeline.linearWeightWindow(S);
        float t = (float) Math.atan(EDMScheduler.SIGMA_MAX / EDMScheduler.SIGMA_DATA);

        return new AdditiveInfiniteTensor(
                "init_residual_map",
                new int[] {2, -1, -1},
                outWin,
                (wi, args) -> tile(wi, args.getFirst(), t, ww),
                null,
                -1,
                new AdditiveInfiniteTensor[] {latentsTensor},
                new TensorWindow[] {inpWin},
                cacheLimitBytes);
    }

    private FloatTensor tile(int[] wi, FloatTensor latentSlice, float t, float[] ww) {
        Debug.debugCalls(wi, "decoder");
        final int S = TILE_SIZE;
        final int ST = TILE_STRIDE;
        final int Slc = S / WorldPipeline.LATENT_COMPRESSION;
        final int i1 = wi[1] * ST, j1 = wi[2] * ST;
        final float cosT = (float) Math.cos(t), sinT = (float) Math.sin(t);
        // Snapshot the reload-scoped session once so the seed-derived noise draw and tau come from the same
        // reload generation (MUST-1 — see PipelineSession).
        final PipelineSession s = sessionSupplier.get();
        final long seed = s.seed();

        // Unnormalize latents channels 0..3 (4 channels)
        final float[] latFlat = new float[4 * Slc * Slc];
        for (int ch = 0; ch < 4; ch++)
            for (int px = 0; px < Slc * Slc; px++) {
                float w = latentSlice.get(5 * Slc * Slc + px);
                latFlat[ch * Slc * Slc + px] = (w > 1e-6f) ? latentSlice.get(ch * Slc * Slc + px) / w : 0f;
            }

        // Nearest-neighbor upsample (4, Slc, Slc) → (4, S, S)
        float[] upsampled = nearestUpsample(latFlat, 4, Slc, Slc, S, S);

        // One flow-matching step (sample starts at zero)
        float[] noise = WorldPipeline.flatten3D(GaussianNoisePatch.generate(seed + 5819, i1, j1, S, S, 1, S, S));
        float[] xT = new float[S * S];
        for (int k = 0; k < S * S; k++) xT[k] = sinT * noise[k] * EDMScheduler.SIGMA_DATA; // sample=0

        // model_in = concat([xT/sigma_data (1,S,S), upsampled (4,S,S)]) → (5,S,S).
        // Reuse the per-thread scratch buffer; fully overwritten below.
        final float[] modelIn = decoderInputScratch.get();
        for (int k = 0; k < S * S; k++) modelIn[k] = xT[k] / EDMScheduler.SIGMA_DATA;
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
            newSample[k] = (cosT * xT[k] - sinT * EDMScheduler.SIGMA_DATA * pred) / EDMScheduler.SIGMA_DATA;
        }

        // post-processing
        Object[][] inputs = new Object[3][3];
        inputs[0] = new Object[] {"residual_init", newSample, new long[] {1, 512, 512}};
        inputs[1] = new Object[] {"latents_init", latentSlice.dataUnsafe(), new long[] {6, 64, 64}};
        inputs[2] = new Object[] {"tau", s.tau(), new long[] {1}};

        final float[] data = fuzedModel.run(inputs);
        final float[] out = new float[S * S * DECODER_CHANNELS];
        System.arraycopy(data, 7 * S * S, out, 0, S * S);
        System.arraycopy(data, 0, out, S * S, 7 * S * S);

        return new FloatTensor(new int[] {DECODER_CHANNELS, S, S}, out);
    }

    private static float[] nearestUpsample(float[] src, int C, int sH, int sW, int dH, int dW) {
        float[] dst = new float[C * dH * dW];
        for (int c = 0; c < C; c++)
            for (int r = 0; r < dH; r++) {
                int sr = r * sH / dH;
                for (int col = 0; col < dW; col++)
                    dst[c * dH * dW + r * dW + col] = src[c * sH * sW + sr * sW + col * sW / dW];
            }
        return dst;
    }
}
