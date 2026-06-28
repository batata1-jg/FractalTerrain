package me.batata_1.fractal_terrain.math;

import java.util.function.Function;
import net.minecraft.util.Mth;

public class Interpolation {

    private static final Function<Double, Double> stepBilinear = x -> x;
    private static final Function<Double, Double> stepSmoothstep = x -> 3 * (x * x) - 2 * (x * x * x);

    private final float interpolation_scale;
    private final Function<int[], Float> f;

    public Interpolation(final float interpolationScale, final Function<int[], Float> f) {
        interpolation_scale = interpolationScale;
        this.f = f;
    }

    public double interpolateSmoothStep(float x, float z) {
        return interpolate(x, z, stepSmoothstep);
    }

    public double interpolateBilinear(float x, float z) {
        return interpolate(x, z, stepBilinear);
    }

    /**
     * Bilinear sample of a flat row-major {@code side×side} field at fractional {@code (px, pz)},
     * clamped to the field edges (indexing {@code field[x * side + z]}).
     */
    public static double sampleBilinear(float[] field, double px, double pz, int side) {
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        final double fx = px - x0;
        final double fz = pz - z0;
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        x0 = Math.clamp(x0, 0, side - 1);
        x1 = Math.clamp(x1, 0, side - 1);
        z0 = Math.clamp(z0, 0, side - 1);
        z1 = Math.clamp(z1, 0, side - 1);
        final double v0 = field[x0 * side + z0] * (1 - fz) + field[x0 * side + z1] * fz;
        final double v1 = field[x1 * side + z0] * (1 - fz) + field[x1 * side + z1] * fz;
        return v0 * (1 - fx) + v1 * fx;
    }

    // xz real coords
    private double interpolate(float x, float z, final Function<Double, Double> step) {

        x /= interpolation_scale;
        z /= interpolation_scale;

        final int[] xs = {(int) Math.floor(x), (int) Math.ceil(x)};
        final int[] zs = {(int) Math.floor(z), (int) Math.ceil(z)};

        final float[] nodes = new float[4];
        final int[] mutablePos = new int[] {0, 0, 0};
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                mutablePos[1] = xs[j];
                mutablePos[2] = zs[i];
                nodes[2 * i + j] = f.apply(mutablePos);
            }
        }

        final double deltaX = x - Math.floor(x);
        final double deltaZ = z - Math.floor(z);

        return Mth.lerp2(step.apply(deltaX), step.apply(deltaZ), nodes[0], nodes[1], nodes[2], nodes[3]);
    }

    public static double interpolateBilinear(
            float x, float z, int[] mutableNodePos, float[] mutableNodes, final Function<int[], Float> f) {
        return interpolate(x, z, mutableNodePos, mutableNodes, f, stepBilinear);
    }

    public static double interpolateSmoothStep(
            float x, float z, int[] mutableNodePos, float[] mutableNodes, final Function<int[], Float> f) {
        return interpolate(x, z, mutableNodePos, mutableNodes, f, stepSmoothstep);
    }

    private static double interpolate(
            float x,
            float z,
            int[] mutableNodePos,
            float[] mutableNodes,
            final Function<int[], Float> f,
            final Function<Double, Double> step) {

        final int[] xs = {(int) Math.floor(x), (int) Math.ceil(x)};
        final int[] zs = {(int) Math.floor(z), (int) Math.ceil(z)};

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                mutableNodePos[1] = xs[j];
                mutableNodePos[2] = zs[i];
                mutableNodes[2 * i + j] = f.apply(mutableNodePos);
            }
        }

        final double deltaX = x - Math.floor(x);
        final double deltaZ = z - Math.floor(z);

        return Mth.lerp2(
                step.apply(deltaX), step.apply(deltaZ), mutableNodes[0], mutableNodes[1], mutableNodes[2],
                mutableNodes[3]);
    }
}
