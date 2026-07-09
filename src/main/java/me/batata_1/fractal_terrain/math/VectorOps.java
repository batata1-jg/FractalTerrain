package me.batata_1.fractal_terrain.math;

public class VectorOps {

    public static double magnitude(double[] vec) {
        return Math.sqrt(dot(vec, vec));
    }

    public static double distance(double[] vec1, double[] vec2) {
        return Math.sqrt(distanceSquared(vec1, vec2));
    }

    public static double distanceSquared(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double resp = 0;
        for (int i = 0; i < vec1.length; i++) resp += (vec1[i] - vec2[i]) * (vec1[i] - vec2[i]);
        return resp;
    }

    public static double dot(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double resp = 0;
        for (int i = 0; i < vec1.length; i++) resp += vec1[i] * vec2[i];
        return resp;
    }

    public static double[] add(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = vec1[i] + vec2[i];
        return resp;
    }

    public static double[] sub(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = vec1[i] - vec2[i];
        return resp;
    }

    public static double[] normalize(double[] vec) {
        double len = 0;
        for (double v : vec) len += v * v;
        len = Math.sqrt(len);
        if (len < 1e-12) return new double[vec.length];
        double[] resp = new double[vec.length];
        for (int i = 0; i < vec.length; i++) resp[i] = vec[i] / len;
        return resp;
    }

    public static double[] scale(double[] vec, double scalar) {
        double[] resp = new double[vec.length];
        for (int i = 0; i < vec.length; i++) resp[i] = vec[i] * scalar;
        return resp;
    }

    //projects a onto b
    public static double[] project(double[] a, double[] b) {
        checkLengths(a, b);
        final double magB = magnitude(b);
        if(magB<1e-5) return new double[a.length];
        return scale(b,dot(a,b)/(magB*magB));
    }

    public static double[] div(double[] vec, double scalar) {
        if (scalar == 0) throw new RuntimeException("division by zero");
        double[] resp = new double[vec.length];
        for (int i = 0; i < vec.length; i++) resp[i] = vec[i] / scalar;
        return resp;
    }

    public static double[] min(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = Math.min(vec1[i], vec2[i]);
        return resp;
    }

    public static double[] max(double[] vec1, double[] vec2) {
        checkLengths(vec1, vec2);
        double[] resp = new double[vec1.length];
        for (int i = 0; i < vec1.length; i++) resp[i] = Math.max(vec1[i], vec2[i]);
        return resp;
    }

    /** Returns the z-component of the 3D cross product (vec1 × vec2) for 2D vectors. */
    public static double cross2D(double[] vec1, double[] vec2) {
        return vec1[0] * vec2[1] - vec1[1] * vec2[0];
    }

    private static void checkLengths(double[] vec1, double[] vec2) {
        if (vec1.length != vec2.length) throw new RuntimeException("vectors with different lengths");
    }
}
