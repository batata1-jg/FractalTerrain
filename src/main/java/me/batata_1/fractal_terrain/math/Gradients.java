package me.batata_1.fractal_terrain.math;

public class Gradients {

    private static final int[] d = {-1, 0, 1};
    private static final double[][] kernel_x = {
        {-1, 0, 1},
        {-2, 0, 2},
        {-1, 0, 1}
    };
    private static final double[][] kernel_y = {
        {-1, -2, -1},
        {0, 0, 0},
        {1, 2, 1}
    };

    private static double singleGradX(float x, int i, int j) {
        return x * kernel_x[i][j];
    }

    private static double singleGradY(float x, int i, int j) {
        return x * kernel_y[i][j];
    }
}
