package me.batata_1.fractal_terrain.ml.pipeline;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;

import java.util.Objects;
import me.batata_1.fractal_terrain.infinitetensor.AdditiveInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.InfiniteTensor;
import me.batata_1.fractal_terrain.ml.models.ModelAssetManager;
import me.batata_1.fractal_terrain.ml.models.OnnxModel;
import me.batata_1.fractal_terrain.ml.models.PipelineModels;
import org.jetbrains.annotations.TestOnly;

/**
 * Java port of terrain_diffusion/inference/world_pipeline.py WorldPipeline.
 *
 * <p>Orchestrates the three diffusion stages plus climate derivation, each extracted into its own
 * collaborator: {@link CoarseStage} (20-step DPM-Solver++), {@link LatentStage} (2 flow-matching
 * steps), {@link DecoderStage} (1 flow-matching step + fuzed post-processing), and
 * {@link ClimateProvider} (windowed lapse-rate regression over the coarse tensor). All pixel
 * coordinates are native-resolution space, {@code CH=0/X=1/Z=2}.
 *
 * <p>This class owns the reload-scoped {@link PipelineSession} (MUST-1) and the model lifecycle; each
 * stage reads the current session once per tile/batch via a {@code Supplier<PipelineSession>} bound to
 * {@link #currentSession()}, so a reload cannot tear a multi-field read regardless of which stage is
 * reading it.
 */
public final class WorldPipeline implements AutoCloseable {

    static final int LATENT_COMPRESSION = WorldPipelineModelConfig.latentCompression();

    // Unreferenced in the current pipeline (kept from the pre-split WorldPipeline; not owned by any of
    // the four extracted stages).
    static final float LOWFREQ_MEAN = -31.4f;
    static final float LOWFREQ_STD = 38.6f;
    static final float RESIDUAL_MEAN = WorldPipelineModelConfig.residualMean();
    static final float RESIDUAL_STD = WorldPipelineModelConfig.residualStd();

    private final OnnxModel coarseModel;
    private final OnnxModel baseModel;
    private final OnnxModel decoderModel;
    private final OnnxModel fuzedModel;
    private final boolean ownModels;

    /**
     * The reload-scoped inputs {@code (seed, syntheticMapFactory, tau)} bundled behind one volatile
     * reference (MUST-1). {@link #updateInstance} swaps a whole new immutable {@link PipelineSession};
     * every tile closure (via each stage's session supplier) snapshots this once so a reload cannot tear
     * the multi-field read. See {@link PipelineSession}.
     */
    private volatile PipelineSession session;

    private final long cacheLimitBytes = 50L * 1024 * 1024;

    private final CoarseStage coarseStage;
    private final LatentStage latentStage;
    private final DecoderStage decoderStage;
    private final ClimateProvider climateProvider;

    final AdditiveInfiniteTensor coarse;
    final AdditiveInfiniteTensor latents;
    final AdditiveInfiniteTensor residual;

    /** Uses shared models from PipelineModels (e.g. from mod init). Does not close models on close(). Seed is 64-bit (Python: seed & 0xFFFFFFFFFFFFFFFF). */
    public WorldPipeline(long seed, PipelineModels models) {
        this.coarseModel = models.getCoarseModel();
        this.baseModel = models.getBaseModel();
        this.decoderModel = models.getDecoderModel();
        this.fuzedModel = models.getFuzedModel();
        this.ownModels = false;
        this.session = new PipelineSession(seed, new SyntheticMapFactory(seed), new float[] {1F});
        this.coarseStage = new CoarseStage(coarseModel, this::currentSession, cacheLimitBytes);
        this.coarse = coarseStage.tensor();
        this.latentStage = new LatentStage(baseModel, this::currentSession, cacheLimitBytes, coarse);
        this.latents = latentStage.tensor();
        this.decoderStage = new DecoderStage(decoderModel, fuzedModel, this::currentSession, cacheLimitBytes, latents);
        this.residual = decoderStage.tensor();
        this.climateProvider = new ClimateProvider(coarse);
    }

    /** Loads its own models (e.g. for tests). Caller must close. */
    @TestOnly
    public WorldPipeline(long seed) {
        ModelAssetManager.ensureAssetsReady();
        this.coarseModel = new OnnxModel(ModelAssetManager.resolveAssetPath("coarse_model.onnx"), "coarse");
        this.baseModel = new OnnxModel(ModelAssetManager.resolveAssetPath("base_model.onnx"), "base");
        this.decoderModel = new OnnxModel(ModelAssetManager.resolveAssetPath("decoder_model.onnx"), "decoder");
        this.fuzedModel = new OnnxModel(ModelAssetManager.resolveAssetPath("fuzed.onnx"), "fuzed");
        this.ownModels = true;
        this.session = new PipelineSession(seed, new SyntheticMapFactory(seed), new float[] {1F});
        this.coarseStage = new CoarseStage(coarseModel, this::currentSession, cacheLimitBytes);
        this.coarse = coarseStage.tensor();
        this.latentStage = new LatentStage(baseModel, this::currentSession, cacheLimitBytes, coarse);
        this.latents = latentStage.tensor();
        this.decoderStage = new DecoderStage(decoderModel, fuzedModel, this::currentSession, cacheLimitBytes, latents);
        this.residual = decoderStage.tensor();
        this.climateProvider = new ClimateProvider(coarse);
    }

    private PipelineSession currentSession() {
        return session;
    }

    /** Lightweight seed change (Python change_seed): update seed and synthetic map, clear tile caches. Models stay loaded. */
    public synchronized void updateInstance(final long newSeed, final String newPath) {
        final PipelineSession current = session;
        if (newSeed == current.seed() && Objects.equals(coarse.getCurrentPath(), newPath)) return;
        updateInfiniteTensors(newPath);
        // Build a fresh, internally consistent session (seed + its synthetic map together) and publish it
        // in a single volatile swap so in-flight workers snapshot either the whole old or whole new triple.
        // tau is not seed-scoped, so carry the current value forward.
        session = new PipelineSession(newSeed, new SyntheticMapFactory(newSeed), current.tau());
    }

    private synchronized void updateInfiniteTensors(String newPath) {
        coarse.updatePath(newPath);
        latents.updatePath(newPath);
        residual.updatePath(newPath);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Returns the current world seed. */
    public long getSeed() {
        return session.seed();
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
        return climateProvider.getClimate(x, z, elevFlat);
    }

    // =========================================================================
    // Shared static helpers (used by multiple stages)
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
