package me.batata_1.fractal_terrain.noise.filters;

import me.batata_1.fractal_terrain.noise.VoronoiNoiseSampler;

public class ErosionFilter extends Filter {

    VoronoiNoiseSampler sampler;

    protected ErosionFilter(long seedOffset) {
        super();
    }

    @Override
    public double sample(double x, double z, double y) {
        return 0;
    }
}
