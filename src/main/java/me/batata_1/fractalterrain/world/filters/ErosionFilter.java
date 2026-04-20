package me.batata_1.fractalterrain.world.filters;

import me.batata_1.fractalterrain.world.noise.VoronoiNoiseSampler;

public class ErosionFilter extends Filter {

    VoronoiNoiseSampler sampler;
    protected ErosionFilter(long seedOffset) {
        super();
    }



    public double sample(double x, double z, double y, double gradX, double gradZ) {
        
        return 0;
    }

    @Override
    public double sample(double x, double z, double y) {
        return 0;
    }
}
