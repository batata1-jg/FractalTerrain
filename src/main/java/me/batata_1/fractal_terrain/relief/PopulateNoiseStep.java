package me.batata_1.fractal_terrain.relief;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.noise.PhacelleNoiseSampler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public class PopulateNoiseStep {

    private static final BlockState[] rocks = new BlockState[] {
        Blocks.STONE.getDefaultState(),
        Blocks.DIORITE.getDefaultState(),
        Blocks.ANDESITE.getDefaultState(),
        Blocks.GRANITE.getDefaultState()
    };
    private final Interpolation reliefInterpolation;
    private final Interpolation reliefGradInterpolation;
    private final Interpolation reliefGradXInterpolation;
    private final Interpolation reliefGradYInterpolation;
    private final Interpolation reliefResInterpolation;
    private final Interpolation reliefBlurredInterpolation;
    private final RockStrata strata;
    private final PhacelleNoiseSampler phacelleSampler;

    public PopulateNoiseStep(final float scale) {
        reliefInterpolation = new Interpolation(
                scale, xz -> FractalTerrainInstance.getReliefProvider().getElev(xz));
        reliefGradInterpolation = new Interpolation(
                scale, xz -> FractalTerrainInstance.getReliefProvider().getRefinedGrad(xz));
        reliefResInterpolation = new Interpolation(
                scale, xz -> FractalTerrainInstance.getReliefProvider().getRes(xz));
        reliefBlurredInterpolation = new Interpolation(
                scale, xz -> FractalTerrainInstance.getReliefProvider().getBlurredElev(xz));
        reliefGradXInterpolation = new Interpolation(
                scale, xz -> FractalTerrainInstance.getReliefProvider().getGradX(xz));
        reliefGradYInterpolation = new Interpolation(
                scale, xz -> FractalTerrainInstance.getReliefProvider().getGradY(xz));
        strata = RockStrata.AngledPlaneStrata.create(9, 8, rocks);
        phacelleSampler = new PhacelleNoiseSampler(5, 32F);
    }

    public int getHeight(final int x, final int z) {
        final double interpolatedBlurredRelief = reliefBlurredInterpolation.interpolateBilinear(x, z);
        final double interpolatedRelief = reliefInterpolation.interpolateSmoothStep(x, z);
        final double interpolatedGrad = reliefGradInterpolation.interpolateSmoothStep(x, z);
        final double strata = this.strata.sample(x, z, interpolatedRelief, interpolatedGrad, interpolatedBlurredRelief);
        return (int) interpolatedRelief;
    }

    // shouldn't depend on getHeight
    public BlockState fillRocks(int x, int y, int z) {

        return rocks[0];
    }
}
