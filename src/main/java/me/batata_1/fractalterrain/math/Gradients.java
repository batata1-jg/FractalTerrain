package me.batata_1.fractalterrain.math;

import com.mojang.datafixers.util.Pair;
import java.util.concurrent.ExecutionException;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.Tile;

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

    public static <T extends Tile> double[] entryGradMagnitude(
            final int x, final int z, final int ch, final EntryStorage s) {
        double respX = 0;
        double respY = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                try {
                    float entry = s.getValue(Pair.of(x + d[i], z + d[j]), ch);
                    respX += singleGradX(entry, i, j);
                    respY += singleGradY(entry, i, j);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return new double[] {respX, respY, Math.sqrt(respX * respX + respY * respY)};
    }
}
