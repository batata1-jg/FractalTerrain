package me.batata_1.fractalterrain.math;

import com.mojang.datafixers.util.Pair;

import java.util.function.Function;

public class Blur {
    private static final double[][] gauss_kernel_3x3 = {
            {1,1,1},
            {1,1,1},
            {1,1,1},
    };

    private static final int[] d3 = {-1,0,1};
    private static final int[] d5 = {-2,-1,0,1,2};

    public static float entryAvgBlur3x3(int x, int z, Function<Pair<Integer,Integer>,Double> f) {
        double resp = 0;
        for(int i=0 ; i<3 ; i++) {
            for(int j=0 ; j<3 ;j++) {
                resp += f.apply(Pair.of(x+d3[i],z+d3[j]));
            }
        }
        return (float) (resp/9);
    }

    public static double entryAvgBlur5x5(int x, int z, Function<Pair<Integer,Integer>,Double> f) {
        double resp = 0;
        for(int i=0 ; i<5 ; i++) {
            for(int j=0 ; j<5 ;j++) {
                resp += f.apply(Pair.of(x+d5[i],z+d5[j]));
            }
        }
        return (resp/25);
    }

}
