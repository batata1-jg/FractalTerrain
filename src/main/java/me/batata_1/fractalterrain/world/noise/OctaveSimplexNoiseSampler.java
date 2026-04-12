package me.batata_1.fractalterrain.world.noise;

import java.awt.font.NumericShaper;
import java.util.*;
import java.util.function.Function;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class OctaveSimplexNoiseSampler extends NoiseSampler {

    private final int numOctaves;
    private SimplexNoiseSampler sampler = null;
    private final double[] amplitudes;
    private final double[] periods;
    private final double norm;

    public OctaveSimplexNoiseSampler(
            long seedOffset,
            int numOctaves,
            double initAmplitude,
            double initPeriod,
            double lacunarity,
            double persistence,
            @Nullable Function<Double, Double> periodDecay,
            @Nullable Function<Double, Double> amplitudeDecay) {
        super(seedOffset);
        INIT_SET.add(this);
        this.numOctaves = numOctaves;
        periods = new double[numOctaves];
        amplitudes = new double[numOctaves];

        for (int i = 0; i < numOctaves; i++) {
            periods[i] = initPeriod * Math.pow(lacunarity, i);
            if (periodDecay != null) periods[i] *= periodDecay.apply((double) i);
            amplitudes[i] = initAmplitude * Math.pow(persistence, i);
            if (amplitudeDecay != null) amplitudes[i] *= amplitudeDecay.apply((double) i);
        }

        //        LOGGER.info("printar amplitude: {}",amplitudes);

        double norm = 0;
        for (int i = 0; i < numOctaves; i++) norm += amplitudes[i];
        this.norm = norm;
    }

    @Override
    public synchronized void initSampler(long seed) {
        sampler = new SimplexNoiseSampler(Random.create(seed + seedOffset));
        INIT_SET.remove(this);
    }

    // always between -1 and 1
    @Override
    public float sample(Number x, Number z) {
        double resp = 0;
        for (int i = 0; i < numOctaves; i++) {
            resp += sampler.sample(
                            x.doubleValue() / periods[i] + i, z.doubleValue() / periods[i] + i)
                    * amplitudes[i];
        }
        return (float) (resp / norm);
    }
}
