package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.math.spline.Spline;
import me.batata_1.fractal_terrain.noise.FastNoiseLite;

public class ClimateVariableTransform {

    // =========================================================================
    // Tile geometry
    // =========================================================================

    /** Side of a biome tile, in block pixels. */
    private static final int PIXELS = 1 << 9; // 512

    /** Pixels per channel in a flattened tile array ({@code PIXELS * PIXELS}). */
    private static final int TILE_SIZE = 1 << 18; // 512 × 512

    /** Number of climate output channels: continentalness, erosion, temperature, vegetation, weirdness. */
    private static final int CHANNELS = 5;

    // Input climate-array channel offsets (each TILE_SIZE apart): [0]=temp, [1]=t_season, [2]=precip, [3]=p_cv.
    private static final int CLIMATE_TEMP = 0;
    private static final int CLIMATE_T_SEASON = 1;
    private static final int CLIMATE_PRECIP = 2;
    private static final int CLIMATE_P_CV = 3;

    // =========================================================================
    // Distance-to-shore upscaling (see BiomeProvider.computeCoarseDistShore)
    // =========================================================================

    /** Native pixels spanned by one coarse cell. */
    private static final int COARSE_CELL_PX = 256;

    /** Half of {@link #COARSE_CELL_PX}; offsets a pixel to its coarse-cell centre for bilinear sampling. */
    private static final int COARSE_CELL_HALF = COARSE_CELL_PX / 2;

    /** Side of the per-coarse-cell distance grid passed in (2 owned cells + 1-cell halo each side). */
    private static final int DSHORE_GRID = 4;

    // =========================================================================
    // Perlin-noise perturbation fields and their mixing weights
    // =========================================================================

    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;
    private static final FastNoiseLite SNOW_NOISE, SNOW_NOISE_FINE;
    private static final FastNoiseLite CONTINENTAL_NOISE, WEIRDENSS_NOISE;
    private static final FastNoiseLite TEMP_VAR_NOISE,VEG_VAR_NOISE;

    static {
        TEMP_NOISE = makeFnl(12345, 1f / 500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f / 128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f / 500f, 5, 2f, 0.5f);
        SNOW_NOISE = makeFnl(12345, 1f / 500f, 3, 2f, 0.5f);
        SNOW_NOISE_FINE = makeFnl(54321, 1f / 128f, 2, 2f, 0.5f);
        CONTINENTAL_NOISE = makeFnl(24567, 1f / 128f, 2, 2f, 0.5f);
        WEIRDENSS_NOISE = makeFnl(5467, 1f / 48f, 5, 2f, 0.5f);
        TEMP_VAR_NOISE = makeFnl(346,1f/256f,2,0.5f,0.5f);
        VEG_VAR_NOISE = makeFnl(46754794,1f/256f,2,0.5f,0.5f);
    }

    // Temperature noise = coarse·0.4 + fine·0.2 (°C-scale perturbation).
    private static final float TEMP_NOISE_COARSE_W = 0.4f;
    private static final float TEMP_NOISE_FINE_W = 0.2f;
    /** Normalizing divisor that maps the temperature noise back to roughly [-1, 1] for re-use as a weirdness/scale term. */
    private static final float TEMP_NOISE_NORM = 0.6f;

    // Precipitation noise applied multiplicatively: factor = 1 + 0.2·noise.
    private static final float PRECIP_NOISE_AMP = 0.2f;

    // Snow noise = coarse·3 + fine·2.
    private static final float SNOW_NOISE_COARSE_W = 3.0f;
    private static final float SNOW_NOISE_FINE_W = 2.0f;

    // Continental noise scaled down before being added to continentalness.
    private static final float CONTINENT_NOISE_AMP = 0.02f;

    // =========================================================================
    // Derived-climate constants
    // =========================================================================

    /** Converts the raw seasonal-temperature channel to a standard deviation. */
    private static final float T_SEASON_TO_STD = 100f;
    /** Weight of temperature std when forming the "effective" temperature for PET. */
    private static final float TEFF_TSTD_W = 0.5f;
    /** Potential-evapotranspiration model: base + linear·tEff + quad·tEff². */
    private static final float PET_BASE = 250f;

    private static final float PET_LINEAR = 25f;
    private static final float PET_QUAD = 0.7f;
    /** Seasonality penalty on tree moisture: 1 - 0.35·min(1, pCV/100). */
    private static final float SEASON_PENALTY_W = 0.35f;

    private static final float P_CV_NORM = 100f;

    // Growing-season → tree-cover ramp: below MIN no trees, full effect by FULL days.
    private static final float GROWING_SEASON_MIN = 60f;
    private static final float GROWING_SEASON_FULL = 150f;

    // Slope bare-threshold ramp over tree moisture: (treeMoisture - 0.35)/0.45 mapped into [MIN, MAX].
    private static final float BARE_MOIST_LO = 0.35f;
    private static final float BARE_MOIST_SPAN = 0.45f;
    private static final float BARE_THRESHOLD_MIN = 0.7f;
    private static final float BARE_THRESHOLD_MAX = 1.19f;

    // Tree-coverage classification thresholds on effective tree moisture.
    private static final float TREES_NONE_MAX = 0.2f;
    private static final float ARID_MIN = 0.05f;
    private static final float TREES_SPARSE_MAX = 0.5f;
    private static final float TREES_FOREST_MAX = 0.8f;
    private static final float TREES_DENSE_MAX = 1.3f;

    /** Minimum slope (after which medium-slope tree thinning applies). */
    private static final float SLOPE_MEDIUM_MIN = 0.62f;

    // Temperature band edges (°C).
    private static final float TEMP_FROZEN_MAX = -5f;
    private static final float TEMP_COLD_MAX = 5f;
    private static final float TEMP_COOL_MAX = 12f;
    private static final float TEMP_TEMPERATE_MAX = 20f;
    private static final float TEMP_WARM_MAX = 26f;

    // =========================================================================
    // Continentalness constants
    // =========================================================================

    /** Land elevation rescale (m → continentalness units). */
    private static final float ELEV_CONT_SCALE = 2000f;
    /** Ocean elevation rescale and clamp window. */
    private static final float OCEAN_ELEV_SCALE = 89.4f;

    private static final float MUSHROOM_THRESHOLD = -1.05f;
    /** Land continentalness is the mean of three terms (elevation, tempStd, precipCV). */
    private static final float CONT_TERM_COUNT = 3f;

    private static final float BIAS_COAST = 0.2f;
    private static final float CONT_NOISE_W_LAND = 0.2f;
    // tempStd → continentalness step function and its noise wobble.
    private static final int LOW_STD = 3, MID_STD = 5, HIGH_STD = 10;
    private static final float MEAN_COAST = -0.15f, MEAN_NEAR_INLAND = -0.04f, MEAN_MID_INLAND = 0.165f, MEAN_FAR_INLAND = 0.5f;
    private static final float CONT_TSTD_NOISE_W = 0.05f;
    private static final float CONT_TSTD_NOISE_DIV = 20f;
    // precip-CV → continentalness step function.
    private static final int LOW_PCV = 50, MID_PCV = 150;
    private static final float CONT_HSTD_BASE = 0.165f, CONT_HSTD_ADD = 0.015f, CONT_HSTD_HIGH = 0.5f;
    private static final float CONT_HSTD_PCV_DIV = 200f, CONT_HSTD_SUB = 0.15f;

    // =========================================================================
    // Erosion constants
    // =========================================================================

    private static final float PRECIP_NORM = 2000f;
    /** Exponent compressing gradient magnitudes before the erosion spline (|g|+1)^-1. */
    private static final double GRAD_COMPRESSION = -1;

    private static final float RES_MAG_SCALE = 15f;
    private static final float RES_E_SCALE = 2f;
    // Erosion mix weights over the compressed low-freq/residual/slope gradients.
    private static final float EROSION_LOW_W = 0.5f;
    private static final float EROSION_RES_W = 0.3f;
    private static final float EROSION_SLOPE_W = 0.2f;
    // "Very flat" boost gating and offset.
    private static final float VERY_FLAT_SLOPE_MAX = 10f;
    private static final float VERY_FLAT_RESMAG_MAX = 0.4f;
    private static final float VERY_FLAT_PRECIP_OFFSET = 0.2f;
    // High-elevation steepening term.
    private static final float HEIGHT_FACTOR_BASE = 0.2f;
    private static final float HEIGHT_FACTOR_RES_W = 2f;

    /** Elevation (m) above which terrain is treated as "peak" (steepening + weirdness peak bias). */
    private static final int ELEV_PEAK_MIN = 475;

    // =========================================================================
    // Temperature constants
    // =========================================================================

    private static final float TEMP_TSTD_DIV = 20f;
    private static final float TEMP_TSTD_W = 0.1f;
    private static final float HOT_PRECIP_MAX = 1000f;
    // Per-band target temperature value.
    private static final float TEMP_T_HOT = 0.775f;
    private static final float TEMP_T_WARM = 0.375f;
    private static final float TEMP_T_TEMPERATE = 0.025f;
    private static final float TEMP_T_COOL = -0.3f;
    private static final float TEMP_T_COLD = -0.45f;
    private static final float TEMP_T_FROZEN = -0.8f;
    // Final temperature mix.
    private static final float TEMP_BAND_W = 0.5f;
    private static final float TEMP_NORM_W = 0.5f;
    private static final float TEMP_ELEV_W1 = 0.03f;
    private static final float TEMP_ELEV_W2 = 0.0325f;
    private static final float TEMP_NOISE_W = 0.02f;

    // =========================================================================
    // Vegetation constants
    // =========================================================================

    private static final float VEG_PRECIP_POS = 0.65f;
    private static final float VEG_PRECIP_NEG = 0.675f;
    private static final float VEG_TREE_BARREN = -0.675f;
    private static final float VEG_TREE_SPARSE = -0.225f;
    private static final float VEG_TREE_DENSE_SLOPE = 2.5f;
    private static final float VEG_TREE_DENSE_OFFSET = 1.25f;
    private static final float VEG_TEMP_GAIN = 0.07f;
    private static final float VEG_PRECIP_MIX = 0.3f;
    private static final float VEG_TREE_MIX = 0.7f;
    private static final float VEG_TSTD_OFFSET = 0.6f;
    private static final float VEG_TSTD_CLAMP_MAX = 0.45f;

    // =========================================================================
    // Weirdness constants
    // =========================================================================

    private static final float WEIRD_RES_W = 0.333f;
    private static final float WEIRD_RES_BASE = 1.07f;
    private static final float WEIRD_RES_DIV = 1f;
    private static final float WEIRD_RES_CLAMP_HI = 1 - 0.07f;
    private static final float WEIRD_PEAK_VALUE = 0.666f;

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

    /**
     * Maps the coarse climate array to the five vanilla biome parameters
     * (continentalness, erosion, temperature, vegetation, weirdness) for one 512×512 tile.
     *
     * @param coarseDistShore per-coarse-cell distance-to-shore grid, row-major {@code [Xcell*DSHORE_GRID + Zcell]},
     *     {@link #DSHORE_GRID}×{@link #DSHORE_GRID}. Its origin is one coarse cell before the tile origin (the
     *     bilinear halo); values are {@code -1} (ocean), {@code 0..7} (increasing land distance) or {@code 8}
     *     (no ocean within the search window). Bilinearly upscaled here to the per-pixel {@code distShore}.
     */
    public static float[] transform(
            int x0,
            int z0,
            float[] elev,
            float[] grad,
            float[] lowFreqGrad,
            float[] climate,
            float[] res,
            float[] vegPdf,
            int[] coarseDistShore) {
        float[] out = new float[CHANNELS * TILE_SIZE];
        for (int i = 0; i < (CHANNELS * TILE_SIZE); i++) out[i] = -1;

        if (climate == null || climate.length < (4 * TILE_SIZE)) {
            return out;
        }

        // Float view of the integer distance-to-shore grid, for bilinear sampling.
        final float[] distShoreGrid = new float[coarseDistShore.length];
        for (int i = 0; i < coarseDistShore.length; i++) distShoreGrid[i] = coarseDistShore[i];

        // Generate Perlin noise perturbations
        float[] tempNoise = new float[TILE_SIZE];
        float[] precipNoiseFact = new float[TILE_SIZE];
        float[] snowNoise = new float[TILE_SIZE];
        float[] continentNoise = new float[TILE_SIZE];
        float[] weirdnessNoise = new float[TILE_SIZE];
        float[] vegetationVar = new float[TILE_SIZE];
        float[] temperatureVar = new float[TILE_SIZE];


        for (int r = 0; r < PIXELS; r++) {
            for (int c = 0; c < PIXELS; c++) {
                int idx = r * PIXELS + c;
                float nx = z0 + c, ny = x0 + r;

                float tempNoiseCoarse = TEMP_NOISE.GetNoise(nx, ny);
                float tempNoiseFine = TEMP_NOISE_FINE.GetNoise(nx, ny);
                tempNoise[idx] = TEMP_NOISE_COARSE_W * tempNoiseCoarse + TEMP_NOISE_FINE_W * tempNoiseFine;

                float precipNoise = PRECIP_NOISE.GetNoise(nx, ny);
                precipNoiseFact[idx] = 1.0f + PRECIP_NOISE_AMP * precipNoise;

                float snowNoiseCoarse = SNOW_NOISE.GetNoise(nx, ny);
                float snowNoiseFine = SNOW_NOISE_FINE.GetNoise(nx, ny);
                snowNoise[idx] = SNOW_NOISE_COARSE_W * snowNoiseCoarse + SNOW_NOISE_FINE_W * snowNoiseFine;

                float continentNoiseRaw = CONTINENTAL_NOISE.GetNoise(nx, ny);
                continentNoise[idx] = CONTINENT_NOISE_AMP * continentNoiseRaw;
                weirdnessNoise[idx] = WEIRDENSS_NOISE.GetNoise(nx, ny);

                vegetationVar[idx] = VEG_VAR_NOISE.GetNoise(nx,ny);
                temperatureVar[idx] = TEMP_VAR_NOISE.GetNoise(nx,ny);
            }
        }

        // Process per-pixel
        for (int r = 0; r < PIXELS; r++) {
            for (int c = 0; c < PIXELS; c++) {
                int idx = r * PIXELS + c;
                float elevVal = elev[idx];
                float slope = grad[idx];
                float residual = res[idx];

                // Climate channels: [0]=temp, [1]=t_season, [2]=precip, [3]=p_cv
                float temp = climate[CLIMATE_TEMP * TILE_SIZE + idx] + tempNoise[idx];
                float tSeason = climate[CLIMATE_T_SEASON * TILE_SIZE + idx];
                float precip = Math.max(0f, climate[CLIMATE_PRECIP * TILE_SIZE + idx]) * precipNoiseFact[idx];
                float pCV = climate[CLIMATE_P_CV * TILE_SIZE + idx];

                // Derived climate variables
                float tStd = tSeason / T_SEASON_TO_STD;
                float tEff = Math.max(0f, temp + TEFF_TSTD_W * tStd);
                float pet = Math.max(PET_BASE, PET_BASE + PET_LINEAR * tEff + PET_QUAD * tEff * tEff);
                float aridity = precip / Math.max(1f, pet);
                float seasonPenalty = 1f - SEASON_PENALTY_W * Math.min(1f, pCV / P_CV_NORM);
                float treeMoisture = aridity * seasonPenalty;

                // Growing season
                float growingSeason = getGrowingSeason(tStd, temp);

                float gsFactor = Math.clamp(
                        (growingSeason - GROWING_SEASON_MIN) / (GROWING_SEASON_FULL - GROWING_SEASON_MIN), 0f, 1f);
                float effTreeMoisture = treeMoisture * gsFactor;

                // Slope-dependent bare threshold
                float moistureFactor = Math.clamp((treeMoisture - BARE_MOIST_LO) / BARE_MOIST_SPAN, 0f, 1f);
                float bareThreshold = BARE_THRESHOLD_MIN + (BARE_THRESHOLD_MAX - BARE_THRESHOLD_MIN) * moistureFactor;

                // Tree coverage classification
                boolean treesNone = effTreeMoisture < TREES_NONE_MAX;
                boolean tooArid = treeMoisture < ARID_MIN;
                boolean tooCold = growingSeason < GROWING_SEASON_MIN;
                boolean barren = tooArid || tooCold;
                boolean treesSparse = !treesNone && effTreeMoisture < TREES_SPARSE_MAX;
                boolean treesForest =
                        !treesNone && effTreeMoisture >= TREES_SPARSE_MAX && effTreeMoisture < TREES_FOREST_MAX;
                boolean treesDense =
                        !treesNone && effTreeMoisture >= TREES_FOREST_MAX && effTreeMoisture < TREES_DENSE_MAX;
                boolean treesRainforest = !treesNone && effTreeMoisture >= TREES_DENSE_MAX;

                // Slope overrides
                boolean slopeMedium = slope >= SLOPE_MEDIUM_MIN && slope < bareThreshold;
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

                // Temperature bands
                boolean frozen = temp < TEMP_FROZEN_MAX;
                boolean cold = temp >= TEMP_FROZEN_MAX && temp < TEMP_COLD_MAX;
                boolean cool = temp >= TEMP_COLD_MAX && temp < TEMP_COOL_MAX;
                boolean temperate = temp >= TEMP_COOL_MAX && temp < TEMP_TEMPERATE_MAX;
                boolean warm = temp >= TEMP_TEMPERATE_MAX && temp < TEMP_WARM_MAX;
                boolean hot = temp >= TEMP_WARM_MAX;

                float continentalness;
                float erosion;
                float temperature;
                float vegetation;
                float weirdness;

                float rescaledElev = elevVal / ELEV_CONT_SCALE;
                float resScaled = residual >= 1 ? residual / 2f : residual / 10f;
                // Per-pixel distance to shore, bilinearly upscaled from the coarse grid. r=X axis, c=Z axis;
                // +COARSE_CELL_HALF shifts a pixel onto its coarse-cell centre, +1 (implicit via the halo grid)
                // lands the owned cells at grid index 1. Exposed for continentalness tuning; not yet consumed.
                double shoreGx = (r + COARSE_CELL_HALF) / (double) COARSE_CELL_PX;
                double shoreGz = (c + COARSE_CELL_HALF) / (double) COARSE_CELL_PX;
                float distShore = (float) Interpolation.sampleBilinear(distShoreGrid, shoreGx, shoreGz, DSHORE_GRID);
                boolean beachWeirdness = false;
                if (elevVal < 0) {
                    beachWeirdness = true;
                    rescaledElev = elevVal / OCEAN_ELEV_SCALE;
                    continentalness = Math.clamp(rescaledElev, MUSHROOM_THRESHOLD+0.1f, MEAN_COAST) + continentNoise[idx];
                } else {
                    float tStdCont;
                    float pCvCont;
                    float elevCont = Math.max(rescaledElev, 0) - BIAS_COAST;
                    if (tStd < LOW_STD) tStdCont = MEAN_COAST;
                    else if (tStd < MID_STD) tStdCont = MEAN_NEAR_INLAND;
                    else if (tStd < HIGH_STD) tStdCont = MEAN_MID_INLAND;
                    else tStdCont = MEAN_FAR_INLAND;
                    tStdCont += (tStd * CONT_TSTD_NOISE_W / CONT_TSTD_NOISE_DIV) * (tempNoise[idx] / TEMP_NOISE_NORM);
                    if (pCV < LOW_PCV) {
                        pCvCont = (MEAN_MID_INLAND + CONT_HSTD_ADD) * (pCV / CONT_HSTD_PCV_DIV) + MEAN_COAST;
                    } else if (pCV < MID_PCV) pCvCont = MEAN_MID_INLAND;
                    else pCvCont = MEAN_FAR_INLAND;
                    continentalness =
                            (tStdCont + pCvCont + elevCont) / CONT_TERM_COUNT + continentNoise[idx] * CONT_NOISE_W_LAND;
                    if(distShore<0.5) continentalness = MEAN_COAST*0.7f + continentalness*0.3f;
                }
                //is in shore, override
                // --- Erosion ---
                float slopeLowFreq = lowFreqGrad[idx];
                float normPrecip = precip / PRECIP_NORM;
                // higher elev areas are more steep
                float gradInfluence = Math.clamp(Math.abs(rescaledElev), 0, 1);
                float resMag = Math.abs(residual / RES_MAG_SCALE);
                float lowFreqErosion = (float) Math.pow(Math.abs(slopeLowFreq) + 1, GRAD_COMPRESSION);
                float slopeErosion = (float) Math.pow(Math.abs(slope) + 1, GRAD_COMPRESSION);
                float resErosion = (float) Math.pow(Math.abs(residual * RES_E_SCALE) + 1, GRAD_COMPRESSION);
                lowFreqErosion = erosionSpline.sample(lowFreqErosion);
                slopeErosion = erosionSpline.sample(slopeErosion);
                float resErosionNorm = erosionSpline.sample(resErosion);
                float flatErosion = -Math.max(rescaledElev, 0);
                float veryFlatFactor = (slopeLowFreq > VERY_FLAT_SLOPE_MAX || resMag > VERY_FLAT_RESMAG_MAX)
                        ? 0
                        : Math.clamp(normPrecip + VERY_FLAT_PRECIP_OFFSET, 0, 1) * resErosion;
                float heightFactor = (elevVal < ELEV_PEAK_MIN)
                        ? 0
                        : (HEIGHT_FACTOR_BASE - rescaledElev) * (1 + HEIGHT_FACTOR_RES_W * Math.min(resMag, 1));

                erosion = gradInfluence
                                * (EROSION_LOW_W * lowFreqErosion
                                        + EROSION_RES_W * resErosionNorm
                                        + EROSION_SLOPE_W * slopeErosion
                                        + heightFactor)
                        + (1 - gradInfluence) * flatErosion
                        + veryFlatFactor;

                if(0.45f<erosion&&erosion<0.55f) erosion = 0.15f + temperatureVar[idx]*0.12f;
                if(-1<=elevVal&&elevVal<=3) erosion = 0.7f;
                if(distShore<0.5) ;
                //TODO: handle shattered biomes later

                // --- Temperature ---
                float tempBand = 0;
                float tStdTemp = (tStd / TEMP_TSTD_DIV) * TEMP_TSTD_W;
                if (cool || cold || frozen) tStdTemp = -tStdTemp;
                if (hot && precip < HOT_PRECIP_MAX) {
                    tempBand = TEMP_T_HOT;
                } else {
                    // if it is very humid, then jungle
                    tempBand = TEMP_T_WARM;
                }
                if (warm) tempBand = TEMP_T_WARM;
                if (temperate) tempBand = TEMP_T_TEMPERATE;
                if (cool) tempBand = TEMP_T_COOL;
                if (cold) tempBand = TEMP_T_COLD;
                if (frozen) tempBand = TEMP_T_FROZEN;
                float elevationFactor = TEMP_ELEV_W1 * TEMP_ELEV_W2 * Math.max(0, elevVal);
                float normTemp = temperatureSpline.sample(temp);

                temperature = TEMP_BAND_W * tempBand
                        + TEMP_NORM_W * normTemp
                        - elevationFactor
                        + tStdTemp
                        + (tempNoise[idx] / TEMP_NOISE_NORM) * TEMP_NOISE_W;
                temperature += temperatureVar[idx]*0.2f;
                // --- Vegetation ---
                float precipVeg = VEG_PRECIP_POS * normPrecip - VEG_PRECIP_NEG * (1.0f - normPrecip);
                float treeVeg = 0;
                if (barren) treeVeg = VEG_TREE_BARREN;
                if (treesSparse) treeVeg = VEG_TREE_SPARSE;
                if (treesRainforest || treesForest || treesDense) {
                    treeVeg = VEG_TREE_DENSE_SLOPE * effTreeMoisture - VEG_TREE_DENSE_OFFSET;
                }

                // add temperature penalty (less extreme temperature -> more humid)
                float temperatureGain = 0;
                if (!frozen && !hot) temperatureGain = VEG_TEMP_GAIN;
                vegetation = VEG_PRECIP_MIX * precipVeg
                        + VEG_TREE_MIX * treeVeg
                        + temperatureGain
                        + Math.clamp(VEG_TSTD_OFFSET - tStd, 0, VEG_TSTD_CLAMP_MAX);
                vegetation += vegetationVar[idx]*0.2f;
                // --- Weirdness ---
                float resWeird =
                        WEIRD_RES_W * (WEIRD_RES_BASE + Math.clamp(resScaled, -1 + 0.05f, WEIRD_RES_CLAMP_HI));
                float weirdSign = beachWeirdness ? -1 : Math.signum(weirdnessNoise[idx]);
                float peakBias = (elevVal < ELEV_PEAK_MIN) ? 0 : gradInfluence;
                weirdness = (resWeird * (1 - peakBias) + peakBias * WEIRD_PEAK_VALUE) * weirdSign;

                stonyCliffaceCondition(weirdness,erosion,continentalness,resScaled);

                out[idx] = continentalness;
                out[TILE_SIZE + idx] = erosion;
                out[2 * TILE_SIZE + idx] = temperature;
                out[3 * TILE_SIZE + idx] = vegetation;
                out[4 * TILE_SIZE + idx] = weirdness;
            }
        }
        return out;
    }

    private static boolean isCoast(float continentalness) {
        return -0.19f<=continentalness&&continentalness<=-0.11f;
    }

    private static void stonyCliffaceCondition(float weirdness, float erosion, float continentalness, float resScaled) {
        if(isCoast(continentalness)) {

        }
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
