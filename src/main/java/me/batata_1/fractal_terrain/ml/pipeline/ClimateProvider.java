package me.batata_1.fractal_terrain.ml.pipeline;

import me.batata_1.fractal_terrain.infinitetensor.AdditiveInfiniteTensor;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;

/**
 * Responsibility: derives native-resolution climate conditioning (real temperature, coarse temperature
 * std, precipitation, precipitation-std, lapse-rate beta) from the coarse tensor via a windowed
 * lapse-rate regression plus bilinear upsampling.
 *
 * <p>Collaborators: only the upstream coarse tensor (constructor-injected dependency) and
 * {@link WorldPipeline#LATENT_COMPRESSION}. Unlike {@link CoarseStage}/{@link LatentStage}/
 * {@link DecoderStage}, this stage never reads {@link PipelineSession} — climate derivation has no
 * seed- or tau-dependent noise draw, so it crosses no reload-scoped state.
 *
 * <p>Invariants: input/output cross only the coarse tensor's {@code CH=0/X=1/Z=2} tensor boundary; the
 * 5-channel output layout is, in order, {@code [tempReal, tempStd, precip, precipStd, beta]}.
 */
final class ClimateProvider {

    private final AdditiveInfiniteTensor coarseTensor;

    ClimateProvider(AdditiveInfiniteTensor coarseTensor) {
        this.coarseTensor = coarseTensor;
    }

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

    public float[] getClimate(int x, int z, float[] elevFlat) {
        return computeClimate(x << 9, z << 9, (x + 1) << 9, (z + 1) << 9, elevFlat, 1 << 9, 1 << 9);
    }

    private float[] computeClimate(int i1, int j1, int i2, int j2, float[] elevFlat, int H, int W) {
        int S = 32 * WorldPipeline.LATENT_COMPRESSION; // native pixels per coarse pixel in stride sense

        int ci1 = Math.floorDiv(i1, S);
        int cj1 = Math.floorDiv(j1, S);
        int ci2 = -Math.floorDiv(-i2, S);
        int cj2 = -Math.floorDiv(-j2, S);

        int win = 15, pad = (win - 1) / 2 + 1;

        FloatTensor coarseSlice =
                coarseTensor.getSlice(new int[] {0, ci1 - pad, cj1 - pad}, new int[] {7, ci2 + pad, cj2 + pad});
        int cH = ci2 + pad - (ci1 - pad);
        int cW = cj2 + pad - (cj1 - pad);

        // Unnormalize all 6 coarse channels
        float[][] coarseMap = new float[6][cH * cW];
        for (int ch = 0; ch < 6; ch++)
            for (int px = 0; px < cH * cW; px++) {
                float w = coarseSlice.get(6 * cH * cW + px);
                coarseMap[ch][px] = (w > 1e-6f) ? coarseSlice.get(ch * cH * cW + px) / w : 0f;
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

    private static float[][] to2D(float[] flat, int H, int W) {
        float[][] a = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(flat, r * W, a[r], 0, W);
        return a;
    }

    private static float[][] cropArray(float[][] src, int r0, int c0, int H, int W) {
        float[][] out = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(src[r + r0], c0, out[r], 0, W);
        return out;
    }

    private static float bilinearSample2D(float[][] src, int H, int W, float gy, float gx) {
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
}
