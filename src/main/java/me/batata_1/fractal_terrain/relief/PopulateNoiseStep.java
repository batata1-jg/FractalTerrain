package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.noise.PhacelleNoiseSampler;
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
    private static final BlockState COAST_MARKER_ROCK = Blocks.BLUE_CONCRETE.defaultBlockState();
    private static final BlockState RIVER_MARKER_ROCK = Blocks.GREEN_CONCRETE.defaultBlockState();
    private static final double MARKER_THRESHOLD = 0.8;
    private static final Logger LOG = getLogger(PopulateNoiseStep.class);

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
        //   final double interpolatedBlurredRelief = reliefBlurredInterpolation.interpolateBilinear(x, z);
        final double interpolatedRelief = reliefInterpolation.interpolateSmoothStep(x, z);
        //  final double interpolatedGrad = reliefGradInterpolation.interpolateSmoothStep(x, z);
        //  final double strata = this.strata.sample(x, z, interpolatedRelief, interpolatedGrad,
        // interpolatedBlurredRelief);
        return (int) interpolatedRelief - 1;
    }

    private static BlockState toRock(double v) {
        return (v >= MARKER_THRESHOLD) ? COAST_MARKER_ROCK : DEFAULT_ROCK;
    }

    public BlockState fillRocks(int x, int y, int z) {
        // final double v = fillRocksPredicate.query((float) x, (float) z);
        // return toRock(v);
        return DEFAULT_ROCK;
    }

    public BlockState placeRiver(BlockState state, int xx, int distFromSurface, int zz) {
        if (distFromSurface != 0) return state;
        final int coarseX = Math.floorDiv(xx, 256 * 5);
        final int coarseZ = Math.floorDiv(zz, 256 * 5);
        if (GlobalRiverProvider.isRiver(
                FractalTerrainInstance.getGlobalRiverProvider().getArrow(coarseX, coarseZ))) return RIVER_MARKER_ROCK;
        if (GlobalRiverProvider.isCoast(
                FractalTerrainInstance.getGlobalRiverProvider().getArrow(coarseX, coarseZ))) return COAST_MARKER_ROCK;
        return state;
    }
}
