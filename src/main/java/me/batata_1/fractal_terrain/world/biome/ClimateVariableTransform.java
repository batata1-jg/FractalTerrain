package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.math.spline.Spline;
import me.batata_1.fractal_terrain.noise.FastNoiseLite;

public class ClimateVariableTransform {

    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;
    private static final FastNoiseLite SNOW_NOISE, SNOW_NOISE_FINE;
    private static final FastNoiseLite CONTINENTAL_NOISE, WEIRDENSS_NOISE;

    static {
        TEMP_NOISE = makeFnl(12345, 1f / 500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f / 128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f / 500f, 5, 2f, 0.5f);
        SNOW_NOISE = makeFnl(12345, 1f / 500f, 3, 2f, 0.5f);
        SNOW_NOISE_FINE = makeFnl(54321, 1f / 128f, 2, 2f, 0.5f);
        CONTINENTAL_NOISE = makeFnl(24567, 1f / 128f, 2, 2f, 0.5f);
        WEIRDENSS_NOISE = makeFnl(5467, 1f / 48f, 5, 2f, 0.5f);
    }

    public ClimateVariableTransform() {}

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

    // 0->no erosion 1->high erosion
    private static final Spline erosionSpline =
            new Spline(new float[] {0, 1}, new float[] {-0.9f, 0.775f}, new float[] {0, 0});
    private static final Spline temperatureSpline = new Spline(
            new float[] {-20, 0, 8.5f, 16, 23, 32},
            new float[] {-0.8f, -0.45f, -0.3f, 0.025f, 0.375f, 0.775f},
            new float[] {0, 0, 0, 0, 0, 0});
    // continentalness erosion temperature vegetation weirdness<-(PV)
    public static float[] transform(
            int x0, int z0, float[] elev, float[] grad, float[] lowFreqGrad, float[] climate, float[] res) {
        float[] out = new float[5 << 18];
        for (int i = 0; i < (5 << 18); i++) out[i] = -1;

        if (climate == null || climate.length < (1 << 20)) {
            return out;
        }

        // Generate Perlin noise perturbations
        float[] tempNoise = new float[1 << 18];
        float[] precipNoiseFact = new float[1 << 18];
        float[] snowNoise = new float[1 << 18];
        float[] continentNoise = new float[1 << 18];
        float[] weirdnessNoise = new float[1 << 18];

        //   Debug.tensor.see(new FloatTensor(climate,new int[]{4,512,512}),2,"precip",
        // FractalTerrainConfig.DEFAULT_DEBUG_PATH);

        for (int r = 0; r < (1 << 9); r++) {
            for (int c = 0; c < (1 << 9); c++) {
                int idx = r * (1 << 9) + c;
                float nx = z0 + c, ny = x0 + r;
                float tnc = TEMP_NOISE.GetNoise(nx, ny);
                float tnf = TEMP_NOISE_FINE.GetNoise(nx, ny);
                tempNoise[idx] = 0.4f * tnc + 0.2f * tnf;

                float pn = PRECIP_NOISE.GetNoise(nx, ny);
                precipNoiseFact[idx] = 1.0f + 0.2f * pn;

                float snc = SNOW_NOISE.GetNoise(nx, ny);
                float snf = SNOW_NOISE_FINE.GetNoise(nx, ny);
                snowNoise[idx] = 3.0f * snc + 2.0f * snf;

                float cnf = CONTINENTAL_NOISE.GetNoise(nx, ny);
                continentNoise[idx] = 0.02f * cnf;
                weirdnessNoise[idx] = WEIRDENSS_NOISE.GetNoise(nx, ny);
            }
        }

        // Compute slope from padded elevation using Sobel (divide by pixelSizeM for ratio)

        // Process per-pixel
        for (int r = 0; r < (1 << 9); r++) {
            for (int c = 0; c < (1 << 9); c++) {
                int idx = r * (1 << 9) + c;
                float elevVal = elev[idx];
                float slope = grad[idx];
                float residual = res[idx];

                // Climate channels: [0]=temp, [1]=t_season, [2]=precip, [3]=p_cv
                float temp = climate[idx] + tempNoise[idx];
                float tSeason = climate[(1 << 18) + idx];
                float precip = Math.max(0f, climate[(2 << 18) + idx]) * precipNoiseFact[idx];
                float pCV = climate[(3 << 18) + idx];

                // Derived climate variables
                float tStd = tSeason / 100f;
                float tEff = Math.max(0f, temp + 0.5f * tStd);
                float pet = Math.max(250f, 250f + 25f * tEff + 0.7f * tEff * tEff);
                float aridity = precip / Math.max(1f, pet);
                float seasonPenalty = 1f - 0.35f * Math.min(1f, pCV / 100f);
                float treeMoisture = aridity * seasonPenalty;

                // Growing season
                float growingSeason = getGrowingSeason(tStd, temp);

                float gsFactor = Math.clamp((growingSeason - 60f) / (150f - 60f), 0f, 1f);
                float effTreeMoisture = treeMoisture * gsFactor;

                // Slope-dependent bare threshold
                float moistureFactor = Math.clamp((treeMoisture - 0.35f) / 0.45f, 0f, 1f);
                float bareThreshold = 0.7f + (1.19f - 0.7f) * moistureFactor;

                // Tree coverage classification
                boolean treesNone = effTreeMoisture < 0.2f;
                boolean tooArid = treeMoisture < 0.05f;
                boolean tooCold = growingSeason < 60f;
                boolean barren = tooArid || tooCold;
                boolean treesSparse = !treesNone && effTreeMoisture < 0.5f;
                boolean treesForest = !treesNone && effTreeMoisture >= 0.5f && effTreeMoisture < 0.8f;
                boolean treesDense = !treesNone && effTreeMoisture >= 0.8f && effTreeMoisture < 1.3f;
                boolean treesRainforest = !treesNone && effTreeMoisture >= 1.3f;

                // Slope overrides
                boolean slopeMedium = slope >= 0.62f && slope < bareThreshold;
                boolean slopeBare = slope >= bareThreshold;
                if (slopeMedium) {
                    if (treesForest || treesDense || treesRainforest) {
                        treesSparse = true;
                    }
                    treesForest = treesForest && false;
                    treesDense = false;
                    treesRainforest = false;
                }
                if (slopeBare) {
                    treesSparse = false;
                    treesForest = false;
                    treesDense = false;
                    treesRainforest = false;
                }

                // Snow classification

                // Elevation/temp bands
                boolean frozen = temp < -5f;
                boolean cold = temp >= -5f && temp < 5f;
                boolean cool = temp >= 5f && temp < 12f;
                boolean temperate = temp >= 12f && temp < 20f;
                boolean warm = temp >= 20f && temp < 26f;
                boolean hot = temp >= 26f;

                float continentalness;
                float erosion;
                float temperature;
                float vegetation;
                float weirdness;

                float rescaledElev = elevVal / 2000f;

                if (elevVal < 0) {
                    rescaledElev = elevVal / 89.4f;
                    continentalness = Math.clamp(rescaledElev, -1.04F, -0.11F) + continentNoise[idx];
                    // if(rescaledElev<)
                } else {
                    float tStdCont;
                    float hStdCont;
                    float eCont = Math.max(rescaledElev, 0) - 0.2f;
                    if (tStd < 3) tStdCont = -0.15F;
                    else if (tStd < 5) tStdCont = -0.04F;
                    else if (tStd < 10) tStdCont = 0.165F;
                    else tStdCont = 0.5F;
                    tStdCont += (tStd * 0.05F / 20F) * (tempNoise[idx] / 0.6F);
                    if (pCV < 50) {
                        hStdCont = (0.165F + 0.015F) * (pCV / 200F) - 0.15F;
                    } else if (pCV < 150) hStdCont = 0.165F;
                    else hStdCont = 0.5F;
                    continentalness = (tStdCont + hStdCont + eCont) / 3F + continentNoise[idx] * 0.2F;
                }

                float slopeLowFreq = lowFreqGrad[idx];
                float normPrecip = precip / 2000F;
                double gradCompression = -1;
                float gradInfluence = Math.clamp(Math.abs(rescaledElev), 0, 1);
                float resMag = Math.abs(residual / 15f);
                float lowE = (float) Math.pow(Math.abs(slopeLowFreq) + 1, gradCompression);
                float slopeE = (float) Math.pow(Math.abs(slope) + 1, gradCompression);
                float resE = (float) Math.pow(Math.abs(residual * 2f) + 1, gradCompression);
                lowE = erosionSpline.sample(lowE);
                slopeE = erosionSpline.sample(slopeE);
                float resENorm = erosionSpline.sample(resE);
                float eE = -Math.max(rescaledElev, 0);
                float veryFlatFactor =
                        (slopeLowFreq > 10 || resMag > 0.4) ? 0 : Math.clamp(normPrecip + 0.2f, 0, 1) * resE;
                float heightFactor = (elevVal < 475) ? 0 : (0.2f - rescaledElev) * (1 + 2 * Math.min(resMag, 1));

                erosion = gradInfluence * (0.5f * lowE + 0.3f * resENorm + 0.2f * slopeE + heightFactor)
                        + (1 - gradInfluence) * eE
                        + veryFlatFactor;

                float tT = 0;
                float tStdT = (tStd / 20F) * 0.1F;
                if (cool || cold || frozen) tStdT = -tStdT;
                if (hot && precip < 1000) {
                    tT = 0.775F;
                } else {
                    // if it is very humid, then jungle
                    tT = 0.375F;
                }
                if (warm) tT = 0.375F;
                if (temperate) tT = 0.025F;
                if (cool) tT = -0.3F;
                if (cold) tT = -0.45F;
                if (frozen) tT = -0.8F;
                float elevationFactor = 0.03f * 0.0325f * Math.max(0, elevVal);
                float normTemp = temperatureSpline.sample(temp);

                temperature = 0.5f * tT + 0.5f * normTemp - elevationFactor + tStdT + (tempNoise[idx] / 0.6F) * 0.02F;

                float precipV = 0.65f * normPrecip - 0.675f * (1.0f - normPrecip);
                float treeV = 0;
                if (barren) treeV = -0.675F;
                if (treesSparse) treeV = -0.225F;
                if (treesRainforest || treesForest || treesDense) {
                    treeV = 2.5F * effTreeMoisture - 1.25F;
                }

                // add temperature penalty (less extreme temerature -> more humid)
                float temperatureGain = 0;
                if (!frozen && !hot) temperatureGain = 0.07f;
                vegetation = 0.3F * precipV + 0.7F * treeV + temperatureGain + Math.clamp(0.6f - tStd, 0, 0.45f);
                float resW = 0.333f * (1 + Math.clamp(residual / 8F, -1, 1));
                float wNoise = Math.signum(weirdnessNoise[idx]);
                weirdness = resW * wNoise;

                out[idx] = continentalness;
                out[(1 << 18) + idx] = erosion;
                out[(2 << 18) + idx] = temperature;
                out[(3 << 18) + idx] = vegetation;
                out[(4 << 18) + idx] = weirdness;
            }
        }
        return out;
    }

    private static float getGrowingSeason(float tStd, float temp) {
        float amplitude = tStd * 1.414f;
        float growingSeason;
        if (amplitude < 0.1f) {
            growingSeason = temp > 5f ? 365f : 0f;
        } else {
            float x = (5f - temp) / amplitude;
            if (x <= -1f) growingSeason = 365f;
            else if (x >= 1f) growingSeason = 0f;
            else growingSeason = 365f * (0.5f - (float) Math.asin(Math.clamp(x, -1f, 1f)) / (float) Math.PI);
        }
        return growingSeason;
    }
}
