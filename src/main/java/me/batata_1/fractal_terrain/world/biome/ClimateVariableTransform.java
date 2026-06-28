package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.math.spline.Spline;
import me.batata_1.fractal_terrain.noise.FastNoiseLite;
import me.batata_1.fractal_terrain.world.biome.parameters.*;

/**
 * Maps the diffusion model's coarse climate + relief channels onto the vanilla Overworld
 * <em>biome parameters</em>. See {@code worldgeneration101.md} ("Biomes → Overworld Biomes").
 *
 * <p>Minecraft selects an Overworld biome from six noise parameters:
 *
 * <ul>
 *   <li><b>continentalness</b> — ocean vs. inland (see {@link Continentalness}),
 *   <li><b>erosion</b> — flat vs. mountainous (see {@link ErosionLevel}),
 *   <li><b>temperature</b> — cold vs. hot (see {@link TemperatureLevel}),
 *   <li><b>humidity</b> / vegetation — dry vs. lush (see {@link HumidityLevel}),
 *   <li><b>weirdness</b> — ridges, folded into peaks-and-valleys (see {@link PeaksValleys}),
 *   <li><b>depth</b> — surface vs. cave; derived from block Y by {@code BiomeProvider}, not here.
 * </ul>
 *
 * <p>{@link #transform} reproduces the first five parameters (depth is purely vertical) for a
 * 512×512 tile, scaling them into the same roughly {@code [-1, 1]} space vanilla uses so the
 * existing {@code MultiNoiseBiomeSource} machinery can resolve a biome. The nested enums encode
 * the wiki's published parameter ranges; the {@code is…(value)} helpers classify an output value
 * against them (e.g. {@link #isCoast(float)}, {@link #isShatteredErosion(float)}).
 */
public class ClimateVariableTransform {

    // =========================================================================
    // Vanilla biome-parameter ranges (worldgeneration101.md → "Overworld Biomes")
    // =========================================================================

    /**
     * Internal °C climate temperature bands used by the temperature and vegetation models. These are
     * the raw-temperature bands, distinct from the vanilla {@link TemperatureLevel} output parameter.
     */
    private enum TempBand {
        FROZEN,
        COLD,
        COOL,
        TEMPERATE,
        WARM,
        HOT;

        static TempBand of(float tempC) {
            if (tempC < TEMP_FROZEN_MAX) return FROZEN;
            if (tempC < TEMP_COLD_MAX) return COLD;
            if (tempC < TEMP_COOL_MAX) return COOL;
            if (tempC < TEMP_TEMPERATE_MAX) return TEMPERATE;
            if (tempC < TEMP_WARM_MAX) return WARM;
            return HOT;
        }
    }

    // =========================================================================
    // Biome-parameter classification helpers (built on the enums above)
    // =========================================================================

    public static boolean isMushroomFields(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.MUSHROOM_FIELDS;
    }

    public static boolean isDeepOcean(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.DEEP_OCEAN;
    }

    public static boolean isOcean(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.OCEAN;
    }

    /** Coast band (−0.19 … −0.11): the ocean ↔ land transition where beaches generate. */
    public static boolean isCoast(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.COAST;
    }

    public static boolean isNearInland(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.NEAR_INLAND;
    }

    public static boolean isMidInland(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.MID_INLAND;
    }

    public static boolean isFarInland(float continentalness) {
        return Continentalness.of(continentalness) == Continentalness.FAR_INLAND;
    }

    /** True for any oceanic region (mushroom fields, deep ocean, ocean) — i.e. below the coast. */
    public static boolean isOceanic(float continentalness) {
        return continentalness < Continentalness.COAST.range.min();
    }

    /** True once past the coast (near-, mid- or far-inland). */
    public static boolean isInland(float continentalness) {
        return continentalness >= Continentalness.NEAR_INLAND.range.min();
    }

    /** Temperature level 0–4 of a temperature parameter value. */
    public static int temperatureLevel(float temperature) {
        return TemperatureLevel.of(temperature).ordinal();
    }

    /** Humidity (vegetation) level 0–4 of a humidity parameter value. */
    public static int humidityLevel(float humidity) {
        return HumidityLevel.of(humidity).ordinal();
    }

    /** Erosion level 5 (0.45 … 0.55): vanilla's shattered-terrain band. */
    public static boolean isShatteredErosion(float erosion) {
        return erosion > ErosionLevel.LEVEL_5.range.min() && erosion < ErosionLevel.LEVEL_5.range.max();
    }

    /** Valley peaks-and-valleys band — where vanilla carves rivers. */
    public static boolean isValley(float weirdness) {
        return PeaksValleys.of(weirdness) == PeaksValleys.VALLEYS;
    }

    // =========================================================================
    // Tile geometry
    // =========================================================================

    /** Side of a biome tile, in block pixels. */
    private static final int PIXELS = 1 << 9; // 512

    /** Pixels per channel in a flattened tile array ({@code PIXELS * PIXELS}). */
    private static final int TILE_SIZE = 1 << 18; // 512 × 512

    /** Number of biome-parameter output channels: continentalness, erosion, temperature, humidity, weirdness. */
    private static final int CHANNELS = 5;

    // Slot indices into the per-pixel mutable biome[] buffer (also the output tile channel order).
    private static final int CONTINENTALNESS = 0;
    private static final int EROSION = 1;
    private static final int TEMPERATURE = 2;
    private static final int VEGETATION = 3;
    private static final int WEIRDNESS = 4;

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
    private static final FastNoiseLite CONTINENTAL_NOISE, WEIRDENSS_NOISE;
    private static final FastNoiseLite TEMP_VAR_NOISE, VEG_VAR_NOISE;

    static {
        TEMP_NOISE = makeFnl(12345, 1f / 500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f / 128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f / 500f, 5, 2f, 0.5f);
        CONTINENTAL_NOISE = makeFnl(24567, 1f / 128f, 2, 2f, 0.5f);
        WEIRDENSS_NOISE = makeFnl(5467, 1f / 48f, 5, 2f, 0.5f);
        TEMP_VAR_NOISE = makeFnl(346, 1f / 256f, 2, 0.25f, 0.1f);
        VEG_VAR_NOISE = makeFnl(46754794, 1f / 256f, 2, 0.25f, 0.1f);
    }

    // Temperature noise = coarse·0.4 + fine·0.2 (°C-scale perturbation).
    private static final float TEMP_NOISE_COARSE_W = 0.4f;
    private static final float TEMP_NOISE_FINE_W = 0.2f;
    /** Normalizing divisor that maps the temperature noise back to roughly [-1, 1] for re-use as a weirdness/scale term. */
    private static final float TEMP_NOISE_NORM = 0.6f;

    // Precipitation noise applied multiplicatively: factor = 1 + 0.2·noise.
    private static final float PRECIP_NOISE_AMP = 0.2f;

    // Continental noise scaled down before being added to continentalness.
    private static final float CONTINENT_NOISE_AMP = 0.02f;

    // =========================================================================
    // Derived-climate constants (humidity / tree-cover model)
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

    // Temperature band edges (°C) used by the tree-cover and temperature models.
    private static final float TEMP_FROZEN_MAX = -5f;
    private static final float TEMP_COLD_MAX = 5f;
    private static final float TEMP_COOL_MAX = 12f;
    private static final float TEMP_TEMPERATE_MAX = 20f;
    private static final float TEMP_WARM_MAX = 26f;

    // =========================================================================
    // Continentalness constants
    // (the MEAN_* values are representative continentalness for each region above)
    // =========================================================================

    /** Land elevation rescale (m → continentalness units). */
    private static final float ELEV_CONT_SCALE = 2000f;
    /** Ocean elevation rescale and clamp window. */
    private static final float OCEAN_ELEV_SCALE = 89.4f;

    /** Mushroom-fields / deep-ocean boundary continentalness (wiki: −1.05). */
    private static final float MUSHROOM_THRESHOLD = -1.05f;
    /** Land continentalness is the mean of three terms (elevation, tempStd, precipCV). */
    private static final float CONT_TERM_COUNT = 3f;

    private static final float BIAS_COAST = 0.2f;
    private static final float CONT_NOISE_W_LAND = 0.2f;
    // tempStd → continentalness step function and its noise wobble.
    private static final int LOW_STD = 3, MID_STD = 5, HIGH_STD = 10;
    // Representative continentalness per region (Coast / Near-inland / Mid-inland / Far-inland).
    private static final float MEAN_COAST = -0.15f,
            MEAN_NEAR_INLAND = -0.04f,
            MEAN_MID_INLAND = 0.165f,
            MEAN_FAR_INLAND = 0.5f;
    private static final float CONT_TSTD_NOISE_W = 0.05f;
    private static final float CONT_TSTD_NOISE_DIV = 20f;
    // precip-CV → continentalness step function.
    private static final int LOW_PCV = 50, MID_PCV = 150;
    private static final float CONT_HSTD_ADD = 0.015f;
    private static final float CONT_HSTD_PCV_DIV = 200f;

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
    // Vegetation (humidity) constants
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
     * Maps the coarse climate array to the five horizontal vanilla biome parameters
     * (continentalness, erosion, temperature, humidity/vegetation, weirdness) for one 512×512 tile.
     * Depth — the sixth parameter — is vertical and supplied separately by {@code BiomeProvider}.
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

                float continentNoiseRaw = CONTINENTAL_NOISE.GetNoise(nx, ny);
                continentNoise[idx] = CONTINENT_NOISE_AMP * continentNoiseRaw;
                weirdnessNoise[idx] = WEIRDENSS_NOISE.GetNoise(nx, ny);

                vegetationVar[idx] = VEG_VAR_NOISE.GetNoise(nx, ny);
                temperatureVar[idx] = TEMP_VAR_NOISE.GetNoise(nx, ny);
            }
        }

        // Process each pixel into a shared mutable biome buffer (one slot per parameter), delegating
        // each parameter to its own method (continentalness, erosion, temperature, vegetation, weirdness).
        float[] biome = new float[CHANNELS];
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
                float tStd = tSeason / T_SEASON_TO_STD;

                // Shared terrain terms. Ocean pixels rescale elevation; both feed several parameters.
                boolean ocean = elevVal < 0;
                float rescaledElev = ocean ? elevVal / OCEAN_ELEV_SCALE : elevVal / ELEV_CONT_SCALE;
                float gradInfluence = Math.clamp(Math.abs(rescaledElev), 0, 1);
                float normPrecip = precip / PRECIP_NORM;
                TempBand band = TempBand.of(temp);

                // Per-pixel distance to shore, bilinearly upscaled from the coarse grid. r=X axis, c=Z axis;
                // +COARSE_CELL_HALF shifts a pixel onto its coarse-cell centre. Drives the coast override.
                double shoreGx = (r + COARSE_CELL_HALF) / (double) COARSE_CELL_PX;
                double shoreGz = (c + COARSE_CELL_HALF) / (double) COARSE_CELL_PX;
                float distShore = (float) Interpolation.sampleBilinear(distShoreGrid, shoreGx, shoreGz, DSHORE_GRID);

                computeContinentalness(
                        biome, ocean, rescaledElev, continentNoise[idx], tStd, pCV, tempNoise[idx], distShore);
                computeErosion(
                        biome,
                        elevVal,
                        rescaledElev,
                        slope,
                        lowFreqGrad[idx],
                        residual,
                        gradInfluence,
                        normPrecip,
                        temperatureVar[idx]);
                computeTemperature(biome, temp, tStd, precip, elevVal, tempNoise[idx], temperatureVar[idx], band);
                computeVegetation(biome, temp, tStd, pCV, precip, slope, normPrecip, vegetationVar[idx], band);
                computeWeirdness(biome, ocean, residual, weirdnessNoise[idx], elevVal, gradInfluence);

                // Cross-parameter post-process: may rewrite erosion/weirdness at the coast.
                stonyCliffaceCondition(biome);

                out[idx] = biome[CONTINENTALNESS];
                out[TILE_SIZE + idx] = biome[EROSION];
                out[2 * TILE_SIZE + idx] = biome[TEMPERATURE];
                out[3 * TILE_SIZE + idx] = biome[VEGETATION];
                out[4 * TILE_SIZE + idx] = biome[WEIRDNESS];
            }
        }
        return out;
    }

    /**
     * Continentalness: ocean depth vs. inland region (worldgeneration101.md "Continentalness").
     * Ocean pixels map elevation into [mushroom-fields edge, coast]; land pixels average an elevation
     * term with tempStd- and precipCV-driven region targets, then pull toward coast/inland by shore distance.
     */
    private static void computeContinentalness(
            float[] biome,
            boolean ocean,
            float rescaledElev,
            float continentNoise,
            float tStd,
            float pCV,
            float tempNoise,
            float distShore) {
        float continentalness;
        if (ocean) {
            continentalness = Math.clamp(rescaledElev, MUSHROOM_THRESHOLD + 0.1f, MEAN_COAST) + continentNoise;
        } else {
            float elevCont = Math.max(rescaledElev, 0) - BIAS_COAST;
            float tStdCont;
            if (tStd < LOW_STD) tStdCont = MEAN_COAST;
            else if (tStd < MID_STD) tStdCont = MEAN_NEAR_INLAND;
            else if (tStd < HIGH_STD) tStdCont = MEAN_MID_INLAND;
            else tStdCont = MEAN_FAR_INLAND;
            tStdCont += (tStd * CONT_TSTD_NOISE_W / CONT_TSTD_NOISE_DIV) * (tempNoise / TEMP_NOISE_NORM);
            float pCvCont;
            if (pCV < LOW_PCV) pCvCont = (MEAN_MID_INLAND + CONT_HSTD_ADD) * (pCV / CONT_HSTD_PCV_DIV) + MEAN_COAST;
            else if (pCV < MID_PCV) pCvCont = MEAN_MID_INLAND;
            else pCvCont = MEAN_FAR_INLAND;
            continentalness = (tStdCont + pCvCont + elevCont) / CONT_TERM_COUNT + continentNoise * CONT_NOISE_W_LAND;
            // Close to shore: pull toward the coast region so beaches resolve. Far from shore: pull inland.
            if (distShore < 0.7) continentalness = MEAN_COAST * 0.7f + continentalness * 0.3f;
            if (5 < distShore) continentalness = MEAN_FAR_INLAND * 0.5f + continentalness * 0.5f;
        }
        biome[CONTINENTALNESS] = continentalness;
    }

    /**
     * Erosion: flat (high) vs. mountainous (low), from compressed gradient magnitudes and elevation
     * (worldgeneration101.md "Erosion"). Erosion level 5 is jittered out of the shattered band and
     * near-sea-level pixels are forced flat.
     */
    private static void computeErosion(
            float[] biome,
            float elevVal,
            float rescaledElev,
            float slope,
            float slopeLowFreq,
            float residual,
            float gradInfluence,
            float normPrecip,
            float temperatureVar) {
        float resMag = Math.abs(residual / RES_MAG_SCALE);
        float lowFreqErosion = erosionSpline.sample((float) Math.pow(Math.abs(slopeLowFreq) + 1, GRAD_COMPRESSION));
        float slopeErosion = erosionSpline.sample((float) Math.pow(Math.abs(slope) + 1, GRAD_COMPRESSION));
        float resErosion = (float) Math.pow(Math.abs(residual * RES_E_SCALE) + 1, GRAD_COMPRESSION);
        float resErosionNorm = erosionSpline.sample(resErosion);
        float flatErosion = -Math.max(rescaledElev, 0);
        float veryFlatFactor = (slopeLowFreq > VERY_FLAT_SLOPE_MAX || resMag > VERY_FLAT_RESMAG_MAX)
                ? 0
                : Math.clamp(normPrecip + VERY_FLAT_PRECIP_OFFSET, 0, 1) * resErosion;
        float heightFactor = (elevVal < ELEV_PEAK_MIN)
                ? 0
                : (HEIGHT_FACTOR_BASE - rescaledElev) * (1 + HEIGHT_FACTOR_RES_W * Math.min(resMag, 1));

        float erosion = gradInfluence
                        * (EROSION_LOW_W * lowFreqErosion
                                + EROSION_RES_W * resErosionNorm
                                + EROSION_SLOPE_W * slopeErosion
                                + heightFactor)
                + (1 - gradInfluence) * flatErosion
                + veryFlatFactor;

        if(elevVal > 750) erosion = Math.max(erosion-0.3f,-1);

        // Erosion level 5 = shattered terrain: jitter it out of the band for now.
        if (isShatteredErosion(erosion)) erosion = 0.15f + temperatureVar * 0.12f;
        // Near sea level: force flat (high erosion).
        if (-1 <= elevVal && elevVal <= 3) erosion = 0.7f;
        // TODO: handle shattered biomes later

        biome[EROSION] = erosion;
    }

    /**
     * Temperature: blends a per-band target, a spline of the raw temperature, elevation cooling, and
     * noise (worldgeneration101.md "Temperature").
     */
    private static void computeTemperature(
            float[] biome,
            float temp,
            float tStd,
            float precip,
            float elevVal,
            float tempNoise,
            float temperatureVar,
            TempBand band) {
        float tStdTemp = (tStd / TEMP_TSTD_DIV) * TEMP_TSTD_W;
        if (band == TempBand.COOL || band == TempBand.COLD || band == TempBand.FROZEN) tStdTemp = -tStdTemp;
        float tempBand;
        if (band == TempBand.HOT && precip < HOT_PRECIP_MAX) tempBand = TEMP_T_HOT;
        else tempBand = TEMP_T_WARM; // if it is very humid, then jungle
        if (band == TempBand.WARM) tempBand = TEMP_T_WARM;
        if (band == TempBand.TEMPERATE) tempBand = TEMP_T_TEMPERATE;
        if (band == TempBand.COOL) tempBand = TEMP_T_COOL;
        if (band == TempBand.COLD) tempBand = TEMP_T_COLD;
        if (band == TempBand.FROZEN) tempBand = TEMP_T_FROZEN;
        float elevationFactor = TEMP_ELEV_W1 * TEMP_ELEV_W2 * Math.max(0, elevVal);
        float normTemp = temperatureSpline.sample(temp);

        float temperature = TEMP_BAND_W * tempBand
                + TEMP_NORM_W * normTemp
                - elevationFactor
                + tStdTemp
                + (tempNoise / TEMP_NOISE_NORM) * TEMP_NOISE_W;
        temperature += temperatureVar * 0.2f;
        biome[TEMPERATURE] = temperature;
    }

    /**
     * Vegetation (humidity): a precipitation term plus a tree-cover model (potential evapotranspiration
     * vs. precipitation, penalized by seasonality and growing season), thinned on steep slopes
     * (worldgeneration101.md "Humidity (Vegetation)").
     */
    private static void computeVegetation(
            float[] biome,
            float temp,
            float tStd,
            float pCV,
            float precip,
            float slope,
            float normPrecip,
            float vegetationVar,
            TempBand band) {
        // Tree-moisture model.
        float tEff = Math.max(0f, temp + TEFF_TSTD_W * tStd);
        float pet = Math.max(PET_BASE, PET_BASE + PET_LINEAR * tEff + PET_QUAD * tEff * tEff);
        float aridity = precip / Math.max(1f, pet);
        float seasonPenalty = 1f - SEASON_PENALTY_W * Math.min(1f, pCV / P_CV_NORM);
        float treeMoisture = aridity * seasonPenalty;

        float growingSeason = getGrowingSeason(tStd, temp);
        float gsFactor =
                Math.clamp((growingSeason - GROWING_SEASON_MIN) / (GROWING_SEASON_FULL - GROWING_SEASON_MIN), 0f, 1f);
        float effTreeMoisture = treeMoisture * gsFactor;

        // Slope-dependent bare threshold.
        float moistureFactor = Math.clamp((treeMoisture - BARE_MOIST_LO) / BARE_MOIST_SPAN, 0f, 1f);
        float bareThreshold = BARE_THRESHOLD_MIN + (BARE_THRESHOLD_MAX - BARE_THRESHOLD_MIN) * moistureFactor;

        // Tree coverage classification.
        boolean treesNone = effTreeMoisture < TREES_NONE_MAX;
        boolean tooArid = treeMoisture < ARID_MIN;
        boolean tooCold = growingSeason < GROWING_SEASON_MIN;
        boolean barren = tooArid || tooCold;
        boolean treesSparse = !treesNone && effTreeMoisture < TREES_SPARSE_MAX;
        boolean treesForest = !treesNone && effTreeMoisture >= TREES_SPARSE_MAX && effTreeMoisture < TREES_FOREST_MAX;
        boolean treesDense = !treesNone && effTreeMoisture >= TREES_FOREST_MAX && effTreeMoisture < TREES_DENSE_MAX;
        boolean treesRainforest = !treesNone && effTreeMoisture >= TREES_DENSE_MAX;

        // Slope overrides: thin trees on medium slopes, strip them on bare slopes.
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

        float precipVeg = VEG_PRECIP_POS * normPrecip - VEG_PRECIP_NEG * (1.0f - normPrecip);
        float treeVeg = 0;
        if (barren) treeVeg = VEG_TREE_BARREN;
        if (treesSparse) treeVeg = VEG_TREE_SPARSE;
        if (treesRainforest || treesForest || treesDense) {
            treeVeg = VEG_TREE_DENSE_SLOPE * effTreeMoisture - VEG_TREE_DENSE_OFFSET;
        }

        // add temperature penalty (less extreme temperature -> more humid)
        float temperatureGain = 0;
        if (band != TempBand.FROZEN && band != TempBand.HOT) temperatureGain = VEG_TEMP_GAIN;
        float vegetation = VEG_PRECIP_MIX * precipVeg
                + VEG_TREE_MIX * treeVeg
                + temperatureGain
                + Math.clamp(VEG_TSTD_OFFSET - tStd, 0, VEG_TSTD_CLAMP_MAX);
        vegetation += vegetationVar * 0.2f;
        biome[VEGETATION] = vegetation;
    }

    /**
     * Weirdness (ridges): residual-driven magnitude, peak-biased at altitude, with the sign coming from
     * noise (or forced negative over ocean). Valley results are pinned to a small positive value
     * (worldgeneration101.md "Weirdness (Ridges)").
     */
    private static void computeWeirdness(
            float[] biome, boolean ocean, float residual, float weirdnessNoise, float elevVal, float gradInfluence) {
        float resScaled = residual >= 1 ? residual / 2f : residual / 10f;
        float resWeird = WEIRD_RES_W * (WEIRD_RES_BASE + Math.clamp(resScaled, -1 + 0.05f, WEIRD_RES_CLAMP_HI));
        float weirdSign = ocean ? -1 : Math.signum(weirdnessNoise);
        float peakBias = (elevVal < ELEV_PEAK_MIN) ? 0 : (float) Math.pow(gradInfluence, 0.5f);
        if (600 < elevVal) peakBias = Math.min(0.75f, peakBias);
        if (700 < elevVal) peakBias = Math.min(0.95f,peakBias);
        float weirdness = (resWeird * (1 - peakBias) + peakBias * WEIRD_PEAK_VALUE) * weirdSign;
        if (isValley(weirdness)) weirdness = 0.06f;
        biome[WEIRDNESS] = weirdness;
    }

    /**
     * Cross-parameter coast tweak: at the coast, peaks/high ridges are flattened toward a stony-shore
     * look by dropping erosion to the level-1 midpoint and pinning weirdness. See
     * {@code worldgeneration101.md} ("Inland surface biome categories" → Stony Shore).
     */
    private static void stonyCliffaceCondition(float[] biome) {
        if (!isCoast(biome[CONTINENTALNESS])) return;
        PeaksValleys pv = PeaksValleys.of(biome[WEIRDNESS]);
        if (pv == PeaksValleys.PEAKS || pv == PeaksValleys.HIGH) {
            biome[EROSION] = ErosionLevel.LEVEL_1.range.mid();
            biome[WEIRDNESS] = 0.334f;
        }
    }

    /**
     * Length of the growing season in days, from seasonal temperature spread {@code tStd} and mean
     * {@code temp} (°C): the fraction of the year the temperature stays above the 5 °C tree threshold.
     */
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
