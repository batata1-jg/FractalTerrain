package me.batata_1.fractalterrain.math;

public class MaskedOps<T> {

    public static final MaskedOps<Double> DOUBLE = new MaskedOps<>();

    public double Add(T u, T v, final double mask) {
        return (((double)u)*mask + (1-mask)*((double)v));
    }

}
