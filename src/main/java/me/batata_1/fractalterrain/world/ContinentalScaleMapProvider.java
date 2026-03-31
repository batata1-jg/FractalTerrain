package me.batata_1.fractalterrain.world;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import me.batata_1.fractalterrain.world.noise.OctaveSimplexNoiseSampler;

public class ContinentalScaleMapProvider {

    public record Settings(float min, float max, double amplitude, float offset, OctaveSimplexNoiseSampler sampler) {}

    public static void initSamplers(long seed) {
        LOGGER.info("seed:{}", seed);
        elevSettings.sampler.initSampler(seed);
        tempSettings.sampler.initSampler(seed);
        tempSTDSettings.sampler.initSampler(seed);
        precipSettings.sampler.initSampler(seed);
        precipSTDSettings.sampler.initSampler(seed);
    }

    // sampler returns [-1,1]

    private static double sample(int x, int z, Settings s) {
        double maxNorm = s.max() / s.amplitude();
        double minNorm = s.min() / s.amplitude();
        double offsetNorm = s.offset() / s.amplitude();
        return Math.clamp(s.sampler().sample(x,z) + offsetNorm,minNorm,maxNorm);
    }

    public static final Settings elevSettings =
            new Settings(-64, 120, 90, 30, new OctaveSimplexNoiseSampler(1, 8, 1.0, 60.0, 0.7, 0.6, null, Math::sin));

    public static double sampleElev(int x, int z) {




        return sample(x, z, elevSettings);
    }

    public static final Settings tempSettings =
            new Settings(-40, 30, 50, 10, new OctaveSimplexNoiseSampler(2, 5, 1.3, 90.0, 0.6, 0.7, null, null));

    public static double sampleTemperature(int x, int z) {
        return sample(x, z, tempSettings);
    }

    public static final Settings tempSTDSettings =
            new Settings(0, 200, 100, 100, new OctaveSimplexNoiseSampler(3, 8, 1.0, 80.0, 0.5, 0.6, null, null));

    public static double sampleThermalAmplitude(int x, int z) {
        return sample(x, z, tempSTDSettings);
    }

    public static final Settings precipSettings =
            new Settings(0, 1800, 2200, -200, new OctaveSimplexNoiseSampler(4, 14, 1.4, 80.0, 0.6, 0.4, null, null));

    public static double samplePrecipitation(int x, int z) {
        return sample(x, z, precipSettings);
    }

    public static final Settings precipSTDSettings =
            new Settings(0, 20, 10, 10, new OctaveSimplexNoiseSampler(5, 12, 1.0, 100.0, 0.5, 0.7, null, null));

    public static double samplePrecipitationAmplitude(int x, int z) {
        return sample(x, z, precipSTDSettings);
    }
}
