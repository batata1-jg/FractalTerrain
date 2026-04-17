package me.batata_1.fractalterrain.math;

import com.mojang.datafixers.util.Pair;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

public class Blur {
    private static final double[][] gauss_kernel_3x3 = {
        {1, 1, 1},
        {1, 1, 1},
        {1, 1, 1},
    };

    private static final int[] d5 = {-2, -1, 0, 1, 2};

    private final CompletableFuture<Function<double[],Double>> functionCompletableFuture = new CompletableFuture<>();
    public Blur() {}

    public void setF(Function<double[],Double> f) {
        this.functionCompletableFuture.complete(f);
    }

    public float entryAvgBlur3x3(double[] doubles) {
        try {
            double resp = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    doubles[0] += dx;
                    doubles[1] += dz;
                    resp += functionCompletableFuture.get().apply(doubles);
                    doubles[0] -= dx;
                    doubles[1] -= dz;
                }
            }
            return (float) (resp / 9);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error("future not completed?");
            throw new RuntimeException(e);
        }
    }

    public static double entryAvgBlur5x5(int x, int z, Function<Pair<Integer, Integer>, Double> f) {
        double resp = 0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                resp += f.apply(Pair.of(x + d5[i], z + d5[j]));
            }
        }
        return (resp / 25);
    }
}
