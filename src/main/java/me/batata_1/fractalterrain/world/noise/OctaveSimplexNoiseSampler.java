package me.batata_1.fractalterrain.world.noise;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

public class OctaveSimplexNoiseSampler {

    private final long seedOffset;
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
             @Nullable Function<Double, Double> amplitudeDecay
    ) {
        this.seedOffset = seedOffset;
        this.numOctaves = numOctaves;
        periods = new double[numOctaves];
        amplitudes = new double[numOctaves];

        for(int i=0 ; i<numOctaves ; i++) {
            periods[i] = initPeriod*Math.pow(lacunarity,i);
            if(periodDecay != null) periods[i] *= periodDecay.apply((double) i);
            amplitudes[i] = initAmplitude*Math.pow(persistence,i);
            if(amplitudeDecay != null) amplitudes[i] *= amplitudeDecay.apply((double) i);
        }

//        LOGGER.info("printar amplitude: {}",amplitudes);

        double norm = 0;
        for(int i=0 ; i<numOctaves ; i++) norm += amplitudes[i];
        this.norm = norm;
    }

    public void initSampler(long seed) {
        sampler = new SimplexNoiseSampler(Random.create(seed + seedOffset));
    }

    public float sample(int x , int z) {
        double resp = 0;
        for(int i=0 ; i<numOctaves ; i++) {
            resp += sampler.sample(x/periods[i] + i ,z/periods[i] + i )*amplitudes[i];
        }
        return (float) (resp / norm);
    }

}
