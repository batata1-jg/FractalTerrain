package me.batata_1.fractal_terrain.relief;

import me.batata_1.fractal_terrain.math.Interpolation;

public record ReliefAccessor(
        Interpolation reliefInterpolation,
        Interpolation reliefGradInterpolation,
        Interpolation reliefGradXInterpolation,
        Interpolation reliefGradYInterpolation,
        Interpolation reliefResInterpolation,
        Interpolation reliefBlurredInterpolation,
        RockStrata strata,
        Interpolation continentalness,
        Interpolation erosion,
        Interpolation temperature,
        Interpolation vegetation,
        Interpolation weirdness) {}
