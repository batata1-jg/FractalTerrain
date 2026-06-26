package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.relief.ReliefAccessor;
import me.batata_1.fractal_terrain.relief.RockStrata;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class PopulateNoiseStep {

    private static final BlockState[] rocks = new BlockState[] {
        Blocks.STONE.defaultBlockState(),
        Blocks.DIORITE.defaultBlockState(),
        Blocks.ANDESITE.defaultBlockState(),
        Blocks.GRANITE.defaultBlockState()
    };

    private static final BlockState DEFAULT_ROCK = Blocks.STONE.defaultBlockState();
    private static final Logger LOG = getLogger(PopulateNoiseStep.class);
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

    private final ReliefAccessor accessor;


    //TODO: fix this, the new interpolations are all wrong
    public PopulateNoiseStep(final float scale) {
        final float interpolationScale = scale * 5;
        this.accessor = new ReliefAccessor(
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getElev(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getRefinedGrad(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getRes(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getBlurredElev(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getGradX(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getGradY(xz)),
                RockStrata.AngledPlaneStrata.create(9, 8, rocks),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getElev(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getElev(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getElev(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getElev(xz)),
                new Interpolation(interpolationScale, xz -> FractalTerrainInstance.getReliefProvider()
                        .getElev(xz)));
    }

    public int getHeight(final int x, final int z) {
        //   final double interpolatedBlurredRelief = reliefBlurredInterpolation.interpolateBilinear(x, z);
        final double interpolatedRelief = accessor.reliefInterpolation().interpolateSmoothStep(x, z);
        //  final double interpolatedGrad = reliefGradInterpolation.interpolateSmoothStep(x, z);
        //  final double strata = this.strata.sample(x, z, interpolatedRelief, interpolatedGrad,
        // interpolatedBlurredRelief);
        return (int) interpolatedRelief - 1;
    }

    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }

    public ReliefAccessor getReliefAccessor() {
        return accessor;
    }
}
