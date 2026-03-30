package me.batata_1.fractalterrain.math;

import com.mojang.datafixers.util.Pair;
import java.util.function.Function;
import net.minecraft.util.math.MathHelper;

public class Interpolation {

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

    // xz real coords
    public double interpolate(float x, float z) {

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

        return MathHelper.lerp2(deltaX, deltaZ, nodes[0], nodes[1], nodes[2], nodes[3]);
    }
}
