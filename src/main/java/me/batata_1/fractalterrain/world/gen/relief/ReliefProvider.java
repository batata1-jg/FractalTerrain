package me.batata_1.fractalterrain.world.gen.relief;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.util.Debug;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReliefProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ReliefProvider.class);

    private static final float NATIVE_RESOLUTION = WorldPipelineModelConfig.nativeResolution();

    private static final FastNoiseLite ELEV_NOISE_COARSE = makeFnl(99999, 1f/24f, 3, 2f, 0.5f);
    private static final FastNoiseLite ELEV_NOISE_FINE   = makeFnl(88888, 1f/6f,  2, 2f, 0.6f);

    private static FastNoiseLite makeFnl(int seed, float freq, int oct, float lac, float gain) {
        FastNoiseLite fnl = new FastNoiseLite(seed);
        fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        fnl.SetFrequency(freq);
        fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
        fnl.SetFractalOctaves(oct);
        fnl.SetFractalLacunarity(lac);
        fnl.SetFractalGain(gain);
        return fnl;
    }

    private static final int MAX_CACHE_SIZE = 64;
    private static final Object CACHE_LOCK = new Object();
    private static final Map<String, LocalTerrainProvider.HeightmapData> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, Future<LocalTerrainProvider.HeightmapData>> PENDING = new ConcurrentHashMap<>();
    /** Single thread for pipeline.get() so MemoryTileStore is not accessed concurrently. */
    private static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    private static volatile ReliefProvider INSTANCE;
    private static long instanceSeed;

    private final WorldPipeline pipeline;

    private ReliefProvider(long seed, PipelineModels models) {
        this.pipeline = new WorldPipeline(seed, models);
    }

    /** Seed is 64-bit world seed. Creates provider once; later worlds only update seed and clear caches (lightweight). */
    public static synchronized void init(long seed) {
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        if (INSTANCE == null) {
            INSTANCE = new ReliefProvider(seed, models);
            instanceSeed = seed;
        } else if (instanceSeed != seed) {
            INSTANCE.pipeline.setSeed(seed);
            instanceSeed = seed;
            synchronized (CACHE_LOCK) { CACHE.clear(); }
            PENDING.clear();
        }
    }

    public static synchronized ReliefProvider getInstance() {
        if (INSTANCE == null) {
            PipelineModels.awaitLoad();
            PipelineModels models = PipelineModels.getInstance();
            if (models == null) throw new IllegalStateException("PipelineModels failed to load");
            INSTANCE = new ReliefProvider(0L, models);
            instanceSeed = 0L;
        }
        return INSTANCE;
    }

    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            CACHE.clear();
        }
        PENDING.clear();
    }

    // =========================================================================
    // Explorer API — all pipeline calls routed through INFERENCE_EXECUTOR
    // =========================================================================

    /** Returns the current world seed used by the pipeline. */
    public static long getSeed() {
        return instanceSeed;
    }

    /**
     * Run elevation and climate inference on the inference thread.
     *
     * @return float[2]: [0] = elev (H*W), [1] = climate (5*H*W, or null)
     */
    public static float[][] getPipelineData(int i1, int j1, int i2, int j2, boolean withClimate) throws Exception {
        return submitToInferenceThread(() -> getInstance().pipeline.get(i1, j1, i2, j2, withClimate));
    }

    /**
     * Fetch a coarse tensor slice on the inference thread.
     * Coordinates are in coarse index units (1 unit = 256 native pixels).
     *
     * @return FloatTensor with shape [7, ci1-ci0, cj1-cj0]
     */
    public static FloatTensor getPipelineCoarse(int ci0, int cj0, int ci1, int cj1) throws Exception {
        return submitToInferenceThread(() -> getInstance().pipeline.getCoarseSlice(ci0, cj0, ci1, cj1));
    }

    /**
     * Change the world seed used by the pipeline and clear all caches.
     * Note: this also affects terrain generation for new Minecraft chunks.
     */
    public static void changeSeedFromExplorer(long newSeed) throws Exception {
        submitToInferenceThread(() -> {
            LocalTerrainProvider provider = getInstance();
            provider.pipeline.setSeed(newSeed);
            instanceSeed = newSeed;
            synchronized (CACHE_LOCK) { CACHE.clear(); }
            PENDING.clear();
            return null;
        });
    }

    /** Change to a random new seed; returns the new seed value. */
    public static long generateRandomSeedFromExplorer() throws Exception {
        long newSeed = new Random().nextLong();
        changeSeedFromExplorer(newSeed);
        return newSeed;
    }

    private static <T> T submitToInferenceThread(Callable<T> task) throws Exception {
        return INFERENCE_EXECUTOR.submit(task).get();
    }

    /**
     * Fetch heightmap for a block-coordinate region (i=Z, j=X).
     * Coordinates are in block space; scale from config determines blocks per native pixel.
     * Blocks the calling thread until the tile is ready (one tile can take 10–30+ seconds).
     * If the caller is the server or a chunk worker, the game will stall until this returns.
     */
    public LocalTerrainProvider.HeightmapData fetchHeightmap(int i1, int j1, int i2, int j2) {
        String key = i1 + "," + j1 + "," + i2 + "," + j2;
        synchronized (CACHE_LOCK) {
            LocalTerrainProvider.HeightmapData cached = CACHE.get(key);
            if (cached != null) return cached;
        }

        int scale = WorldScaleManager.getCurrentScale();
        FutureTask<LocalTerrainProvider.HeightmapData> task = new FutureTask<>(() -> {
            long computedWindowCountBefore = pipeline.getTotalComputedWindowCount();
            LocalTerrainProvider.HeightmapData data = scale <= 1
                    ? handle1x(i1, j1, i2, j2)
                    : handleUpsampled(i1, j1, i2, j2, scale);
            long computedWindowCountAfter = pipeline.getTotalComputedWindowCount();

            long newlyComputedWindowCount = computedWindowCountAfter - computedWindowCountBefore;
            if (newlyComputedWindowCount > 0) {
                int regionWidth = j2 - j1;
                int regionHeight = i2 - i1;
                LOG.info(
                        "Terrain Diffusion finished generating region {}x{} ({} newly computed windows)",
                        regionWidth, regionHeight, newlyComputedWindowCount);
            }
            synchronized (CACHE_LOCK) {
                CACHE.put(key, data);
                evictLruTo(MAX_CACHE_SIZE);
            }
            PENDING.remove(key);
            return data;
        });
        Future<LocalTerrainProvider.HeightmapData> existing = PENDING.putIfAbsent(key, task);
        FutureTask<LocalTerrainProvider.HeightmapData> toRun = (existing == null) ? task : (FutureTask<LocalTerrainProvider.HeightmapData>) existing;
        if (existing == null) {
            int regionWidth = j2 - j1;
            int regionHeight = i2 - i1;
            LOG.info(
                    "Terrain Diffusion uncached region requested: ({}, {})-({}, {}) size {}x{}",
                    j1, i1, j2, i2, regionWidth, regionHeight);
            INFERENCE_EXECUTOR.submit(toRun);
        }
        try {
            return toRun.get();
        } catch (Exception e) {
            PENDING.remove(key);
            throw new RuntimeException("Terrain tile failed: " + key, e);
        }
    }

    private static void evictLruTo(int maxSize) {
        Iterator<Map.Entry<String, LocalTerrainProvider.HeightmapData>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > maxSize && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    // =========================================================================
    // Scale == 1: block coords == native pixel coords
    // =========================================================================

    private LocalTerrainProvider.HeightmapData handle1x(int i1, int j1, int i2, int j2) {
        int H = i2 - i1, W = j2 - j1;

        float[] elevPadded = pipeline.get(i1 - 1, j1 - 1, i2 + 1, j2 + 1, false)[0];
        float[][] out = pipeline.get(i1, j1, i2, j2, true);
        float[] elevFlat = out[0];
        float[] climate  = out[1];

        short[] biomeFlat = BiomeClassifier.classify(elevFlat, climate, i1, j1, elevPadded, H, W, NATIVE_RESOLUTION);
        return buildHeightmapData(elevFlat, biomeFlat, H, W);
    }

    // =========================================================================
    // Scale > 1: pipeline at native res → bilinear upsample to block res
    // =========================================================================

    private LocalTerrainProvider.HeightmapData handleUpsampled(int i1, int j1, int i2, int j2, int scale) {
        int H = i2 - i1, W = j2 - j1;
        float pixelSizeM = NATIVE_RESOLUTION / scale;

        // Convert block coords to native pixel coords
        int i1n = Math.floorDiv(i1, scale);
        int j1n = Math.floorDiv(j1, scale);
        int i2n = -Math.floorDiv(-i2, scale);
        int j2n = -Math.floorDiv(-j2, scale);

        // 2-pixel native padding (1 for bilinear + 1 for slope)
        int i1p = i1n - 2, j1p = j1n - 2;
        int i2p = i2n + 2, j2p = j2n + 2;
        int nH = i2p - i1p, nW = j2p - j1p;

        float[][] out = pipeline.get(i1p, j1p, i2p, j2p, true);
        float[] elevNativeFlat    = out[0];
        float[] climateNativeFlat = out[1];

        // Bilinear upsample elevation: (nH, nW) → (nH*scale, nW*scale)
        float[][] elevNative2D = to2D(elevNativeFlat, nH, nW);
        float[][] elevUp = LaplacianUtils.bilinearResize(elevNative2D, nH * scale, nW * scale);

        // Crop offsets in the upsampled array
        int padUp   = 2 * scale;
        int offsetI = i1 - i1n * scale;
        int offsetJ = j1 - j1n * scale;
        int cropI1  = padUp + offsetI;
        int cropJ1  = padUp + offsetJ;

        float[] elevSmooth = cropFlat(elevUp, cropI1,     cropJ1,     H,   W,   nH * scale, nW * scale);
        float[] elevPadded = cropFlat(elevUp, cropI1 - 1, cropJ1 - 1, H+2, W+2, nH * scale, nW * scale);

        // Upsample climate (4, nH, nW) → (4, H, W)
        float[] climate = upsampleClimate(climateNativeFlat, nH, nW, cropI1, cropJ1, H, W, scale, nH * scale, nW * scale);

        float[] elevOut = addElevationNoise(elevSmooth, elevPadded, i1, j1, H, W, pixelSizeM);

        short[] biomeFlat = BiomeClassifier.classify(elevSmooth, climate, i1, j1, elevPadded, H, W, pixelSizeM);
        return buildHeightmapData(elevOut, biomeFlat, H, W);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private float[] addElevationNoise(float[] elevSmooth, float[] elevPadded,
                                      int i1, int j1, int H, int W, float pixelSizeM) {
        float[] slopeGradient = sobelGradient(elevPadded, H + 2, W + 2, H, W);
        float[] elevOut = elevSmooth.clone();
        float normFactor = 40f * pixelSizeM / NATIVE_RESOLUTION;
        float ampC = 100f * pixelSizeM / NATIVE_RESOLUTION;
        float ampF = 70f  * pixelSizeM / NATIVE_RESOLUTION;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float e = elevSmooth[idx];
                if (e < 0f) continue;

                float grad = slopeGradient[idx];
                float sf = Math.min(1f, grad / normFactor);
                sf = sf * sf * (float) Math.sqrt(sf);

                float nx = j1 + c, ny = i1 + r;
                elevOut[idx] = e
                        + ELEV_NOISE_COARSE.GetNoise(nx, ny) * ampC * sf
                        + ELEV_NOISE_FINE.GetNoise(nx, ny)   * ampF * sf;
            }
        }
        return elevOut;
    }

    private static float[] sobelGradient(float[] padded, int pH, int pW, int H, int W) {
        final float[] SOBEL_X = {-1,0,1, -2,0,2, -1,0,1};
        final float[] SOBEL_Y = {-1,-2,-1, 0,0,0, 1,2,1};
        float[] result = new float[H * W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float dx = 0, dy = 0;
                for (int k = 0; k < 9; k++) {
                    float v = padded[(r + k/3) * pW + (c + k%3)];
                    dx += v * SOBEL_X[k];
                    dy += v * SOBEL_Y[k];
                }
                dx /= 8f; dy /= 8f;
                result[r * W + c] = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        return result;
    }

    private static float[] upsampleClimate(float[] climNative, int nH, int nW,
                                           int cropI1, int cropJ1, int H, int W,
                                           int scale, int upH, int upW) {
        if (climNative == null) return null;
        float[] result = new float[4 * H * W];
        for (int ch = 0; ch < 4; ch++) {
            float[][] chNative = new float[nH][nW];
            for (int r = 0; r < nH; r++)
                System.arraycopy(climNative, ch * nH * nW + r * nW, chNative[r], 0, nW);
            float[][] chUp = LaplacianUtils.bilinearResize(chNative, upH, upW);
            for (int r = 0; r < H; r++)
                for (int c = 0; c < W; c++)
                    result[ch * H * W + r * W + c] = chUp[cropI1 + r][cropJ1 + c];
        }
        return result;
    }

    private static float[] cropFlat(float[][] src, int r0, int c0, int H, int W, int srcH, int srcW) {
        float[] out = new float[H * W];
        for (int r = 0; r < H; r++) {
            int sr = Math.max(0, Math.min(srcH - 1, r0 + r));
            for (int c = 0; c < W; c++)
                out[r * W + c] = src[sr][Math.max(0, Math.min(srcW - 1, c0 + c))];
        }
        return out;
    }

    private static float[][] to2D(float[] flat, int H, int W) {
        float[][] a = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(flat, r * W, a[r], 0, W);
        return a;
    }

    private static LocalTerrainProvider.HeightmapData buildHeightmapData(float[] elevFlat, short[] biomeFlat, int H, int W) {
        short[][] heightmap = new short[H][W];
        short[][] biomeIds  = new short[H][W];
        for (int r = 0; r < H; r++)
            for (int c = 0; c < W; c++) {
                float e = elevFlat[r * W + c];
                heightmap[r][c] = (short) Math.max(-32768, Math.min(32767, (int) Math.floor(e)));
                biomeIds[r][c]  = biomeFlat[r * W + c];
            }
        return new LocalTerrainProvider.HeightmapData(heightmap, biomeIds, W, H);
    }


    private final CompletableFuture<OrtSession> average;
    private final CompletableFuture<OrtSession> take_coarse_grad;

    private final EntryStorage<Tile> final_tiles;
    private final EntryStorage<Tile> final_raw_tiles;

    public record Settings(float tau) implements SettingsRegistry.Settings {

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(
                i -> i.group(Codec.FLOAT.optionalFieldOf("tau", 2.0F).forGetter(Settings::tau))
                        .apply(i, Settings::new));

        public static final Codec<RegistryEntry<Settings>> REGISTRY_CODEC =
                RegistryElementCodec.of(FractalTerrainRegistryKeys.POST_PROCESSING_SETTINGS, CODEC);
    }

//    public ReliefProvider(Settings settings) {
//        take_coarse_grad = Models.getOrCreateModel("ml_util/take_coarse_grad");
//        average = Models.getOrCreateModel("ml_util/average");
//        final_raw_tiles = new EntryStorage<>("final_raw", Tile::new, 64, (xz) -> {
//            try {
//                assert average != null;
//                assert take_coarse_grad != null;
//                final OnnxTensor t = (OnnxTensor) take_coarse_grad
//                        .get()
//                        .run(Map.of("x", (OnnxTensor) average.get()
//                                .run(Map.of("x", decodeAndFinish.getCoarseTilesAsTensor(xz.getFirst(), xz.getSecond())))
//                                .get(0)))
//                        .get(0);
//                return new Tile(t);
//            } catch (OrtException | IOException | ExecutionException | InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        final_tiles = new EntryStorage<>("final", Tile::new, 512, (xz) -> {
//            final int x = xz.getFirst() << 1;
//            final int z = xz.getSecond() << 1;
//            try {
//                assert average != null;
//                final OnnxTensor t = (OnnxTensor) average.get()
//                        .run(Map.of("x", decodeAndFinish.getTilesAsTensor(x, z)))
//                        .get(0);
//
//                Debug.seeFinal(t, x, z);
//                return new Tile(t);
//            } catch (ExecutionException | IOException | InterruptedException | OrtException e) {
//                throw new RuntimeException(e);
//            }
//        });
//    }

    public float getElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 0);
    }

    public float getBlurredElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 1);
    }

    public float getGradX(Pair<Integer, Integer> xz) {
        return getValue(xz, 2);
    }

    public float getGradY(Pair<Integer, Integer> xz) {
        return getValue(xz, 3);
    }

    public float getRefinedGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 4);
    }

    public float getBlurredGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 5);
    }

    public float getRes(Pair<Integer, Integer> xz) {
        return getValue(xz, 6);
    }

    private float getValue(Pair<Integer, Integer> xz, int ch) {
        try {
            return final_tiles.getValue(xz, ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public EntryStorage<Tile> getStorage() {
        return final_tiles;
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

}
