package me.batata_1.fractalterrain.math;

import com.mojang.datafixers.util.Pair;
import java.util.function.Function;
import net.minecraft.util.math.MathHelper;

public class Interpolation {

    private static final Function<Double,Double> stepBilinear = x -> x;
    private static final Function<Double,Double> stepSmoothstep = x -> 3 * (x*x) - 2 * (x*x*x);


    private static float smoothStep(double delta) {
        return (float) (3 * (delta * delta) - 2 * (delta * delta * delta));
    }

    private final float interpolation_scale;
    private Function<Pair<Integer, Integer>, Float> f;

    public Interpolation(float interpolationScale) {
        interpolation_scale = interpolationScale;
    }

    public void setF(Function<Pair<Integer, Integer>, Float> f) {
        this.f = f;
    }

    public double interpolateSmoothStep(float x , float z) {
        return interpolate(x,z,stepSmoothstep);
    }

    public double interpolateBilinear(float x, float z) {
        return interpolate(x,z,stepBilinear);
    }

    // xz real coords
    private double interpolate(float x, float z, Function<Double, Double> step) {

        x /= interpolation_scale * 5;
        z /= interpolation_scale * 5;

        int[] xs = {(int) Math.floor(x), (int) Math.ceil(x)};
        int[] zs = {(int) Math.floor(z), (int) Math.ceil(z)};

        float[] nodes = new float[4];

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                nodes[2 * i + j] = f.apply(Pair.of(xs[j], zs[i]));
            }
        }

        double deltaX = x - Math.floor(x);
        double deltaZ = z - Math.floor(z);

        return MathHelper.lerp2(step.apply(deltaX), step.apply(deltaZ), nodes[0], nodes[1], nodes[2], nodes[3]);
    }
}
