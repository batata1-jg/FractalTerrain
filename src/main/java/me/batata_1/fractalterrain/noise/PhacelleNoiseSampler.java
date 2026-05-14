package me.batata_1.fractalterrain.noise;

import static me.batata_1.fractalterrain.FractalTerrainInstance.reliefSource;

import java.util.concurrent.ExecutionException;
import me.batata_1.fractalterrain.math.Gradients;
import org.jetbrains.annotations.TestOnly;

public class PhacelleNoiseSampler extends VoronoiNoiseSampler {

    private static final double E2 = Math.exp(-2);
    private final float freq;

    public PhacelleNoiseSampler(long off, float freq) {
        // 512/freq
        // scale abaixo de 1 n funciona
        // fixar a freq e alterar o scale
        super(freq, off);
        this.freq = 32F;
    }

    @Override
    @TestOnly
    public float sample(final Number x, final Number z) {
        try {
            final double[] grads = Gradients.entryGradMagnitude(
                    x.intValue(), z.intValue(), 1, reliefSource.get().getStorage());

            return this.sample(x.floatValue(), z.floatValue(), (float) grads[0], (float) grads[1], (float) grads[2]);

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @TestOnly
    public float sampleNormal(final Number x, final Number z) {
        return this.sample(x.floatValue(), z.floatValue(), 1, 1, 1);
    }

    public float sampleD(final double x, final double z, final double gradX, final double gradY, final double grad) {
        return sample((float) x, (float) z, (float) gradX, (float) gradY, (float) grad);
    }

    public float sample(final float x, final float z, final float gradX, final float gradY, final float grad) {
        final double xi = x / scale;
        final double zi = z / scale;
        final int xCurSquare = (int) Math.floor(xi);
        final int zCurSquare = (int) Math.floor(zi);
        final float cosTheta = gradX / grad;
        final float sinTheta = gradY / grad;
        double val = 0;
        double avg = 0;
        for (byte dx = -1; dx <= 1; dx++) {
            for (byte dz = -1; dz <= 1; dz++) {
                final float dist = distPoint(xi, zi, xCurSquare + dx, zCurSquare + dz);
                final double weight = Math.exp(-dist * dist);
                val += weight
                        * Math.cos(freq * (-sinTheta * (xi - xCurSquare - dx) + cosTheta * (zi - zCurSquare - dz)));
                avg += weight;
            }
        }
        return (avg != 0 ? (float) (val / avg) : 0);
    }
}
