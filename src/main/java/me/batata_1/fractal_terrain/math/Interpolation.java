package me.batata_1.fractal_terrain.math;

import java.util.function.Function;
import net.minecraft.util.Mth;

public class Interpolation {

    private static final Function<Double, Double> stepBilinear = x -> x;
    public static final Function<Double, Double> stepSmoothstep = x -> 3 * (x * x) - 2 * (x * x * x);

    private final float interpolationScale;
    private final Function<int[], Float> f;

    public static double lerp(double start, double end, double weight) {
        return start * weight + end * (1 - weight);
    }

    public Interpolation(final float interpolationScale, final Function<int[], Float> f) {
        this.interpolationScale = interpolationScale;
        this.f = f;
    }

    public double interpolateSmoothStep(float x, float z) {
        return interpolate(x, z, stepSmoothstep);
    }

    public double interpolateBilinear(float x, float z) {
        return interpolate(x, z, stepBilinear);
    }

    /** Bilinear sample, edge-clamped so an out-of-range coordinate reads the border rather than
     *  throwing. */
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

    /** Nearest-neighbour sample, edge-clamped like {@link #sampleBilinear}. Serves callers reading a
     *  field that must not be averaged across its neighbours. */
    public static double sampleNearest(float[] field, double px, double pz, int side) {
        final int x = Math.clamp((int) Math.floor(px), 0, side - 1);
        final int z = Math.clamp((int) Math.floor(pz), 0, side - 1);
        return field[x * side + z];
    }

    public static double sampleSmoothStep(float[] field, double px, double pz, int side) {
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        final double fx = stepSmoothstep.apply(px - x0);
        final double fz = stepSmoothstep.apply(pz - z0);
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

    /** Bilinear sample of a pre-sliced window, allocation-free. Replaces four tensor lookups per pixel
     *  on the chunk-fill path; {@code px}/{@code pz} are global pixel coords, the window origin is
     *  subtracted only at the index. Unclamped — the caller sizes the window to cover floor..ceil. */
    public static double sampleWindowBilinear(
            float[] data, float px, float pz, int originX, int originZ, int rowStride) {
        final int colLo = ((int) Math.floor(px) - originX) * rowStride;
        final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
        final int rowLo = (int) Math.floor(pz) - originZ;
        final int rowHi = (int) Math.ceil(pz) - originZ;
        final double deltaX = px - Math.floor(px);
        final double deltaZ = pz - Math.floor(pz);
        return Mth.lerp2(
                deltaX, deltaZ, data[colLo + rowLo], data[colHi + rowLo], data[colLo + rowHi], data[colHi + rowHi]);
    }

    /** Smoothstep counterpart to {@link #sampleWindowBilinear}; the elevation channel uses this one. */
    public static double sampleWindowSmoothStep(
            float[] data, float px, float pz, int originX, int originZ, int rowStride) {
        final int colLo = ((int) Math.floor(px) - originX) * rowStride;
        final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
        final int rowLo = (int) Math.floor(pz) - originZ;
        final int rowHi = (int) Math.ceil(pz) - originZ;
        final double deltaX = smoothStep(px - Math.floor(px));
        final double deltaZ = smoothStep(pz - Math.floor(pz));
        return Mth.lerp2(
                deltaX, deltaZ, data[colLo + rowLo], data[colHi + rowLo], data[colLo + rowHi], data[colHi + rowHi]);
    }

    /** Bilinear sample with {@code Math.abs} applied to each corner before the lerp. The transform is
     *  per-corner, not on the result: applied afterwards, a cell straddling zero would cancel to near
     *  zero instead of averaging its corners' magnitudes. */
    public static double sampleWindowAbs(float[] data, float px, float pz, int originX, int originZ, int rowStride) {
        final int colLo = ((int) Math.floor(px) - originX) * rowStride;
        final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
        final int rowLo = (int) Math.floor(pz) - originZ;
        final int rowHi = (int) Math.ceil(pz) - originZ;
        return Mth.lerp2(
                px - Math.floor(px),
                pz - Math.floor(pz),
                Math.abs(data[colLo + rowLo]),
                Math.abs(data[colHi + rowLo]),
                Math.abs(data[colLo + rowHi]),
                Math.abs(data[colHi + rowHi]));
    }

    /** {@code Math.signum} counterpart to {@link #sampleWindowAbs}; the pair reproduces weirdness's
     *  smooth-magnitude-times-scattering-sign split from one window instead of two tensor walks. */
    public static double sampleWindowSignum(float[] data, float px, float pz, int originX, int originZ, int rowStride) {
        final int colLo = ((int) Math.floor(px) - originX) * rowStride;
        final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
        final int rowLo = (int) Math.floor(pz) - originZ;
        final int rowHi = (int) Math.ceil(pz) - originZ;
        return Mth.lerp2(
                px - Math.floor(px),
                pz - Math.floor(pz),
                Math.signum(data[colLo + rowLo]),
                Math.signum(data[colHi + rowLo]),
                Math.signum(data[colLo + rowHi]),
                Math.signum(data[colHi + rowHi]));
    }

    /** Unboxed twin of {@link #stepSmoothstep}; the expression is duplicated verbatim so the two cannot
     *  drift and the window samplers stay bit-identical to the per-pixel path. */
    private static double smoothStep(double x) {
        return 3 * (x * x) - 2 * (x * x * x);
    }

    // xz real coords
    private double interpolate(float x, float z, final Function<Double, Double> step) {

        x /= interpolationScale;
        z /= interpolationScale;

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
                step.apply(deltaX),
                step.apply(deltaZ),
                mutableNodes[0],
                mutableNodes[1],
                mutableNodes[2],
                mutableNodes[3]);
    }
}
