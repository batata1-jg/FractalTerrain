package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
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
    private static final BlockState COAST_MARKER_ROCK = Blocks.BLUE_CONCRETE.defaultBlockState();
    private static final BlockState RIVER_MARKER_ROCK = Blocks.GREEN_CONCRETE.defaultBlockState();
    private static final double MARKER_THRESHOLD = 0.8;
    private static final Logger LOG = getLogger(PopulateNoiseStep.class);
    private static final BlockState INSIDE_MARGIN_ROCK = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();

    private final ReliefAccessor accessor;

    public PopulateNoiseStep(final float scale) {
        this.accessor = new ReliefAccessor(
                new Interpolation(
                        scale, xz -> FractalTerrainInstance.getReliefProvider().getElev(xz)),
                new Interpolation(
                        scale, xz -> FractalTerrainInstance.getReliefProvider().getRefinedGrad(xz)),
                new Interpolation(
                        scale, xz -> FractalTerrainInstance.getReliefProvider().getRes(xz)),
                new Interpolation(
                        scale, xz -> FractalTerrainInstance.getReliefProvider().getBlurredElev(xz)),
                new Interpolation(
                        scale, xz -> FractalTerrainInstance.getReliefProvider().getGradX(xz)),
                new Interpolation(
                        scale, xz -> FractalTerrainInstance.getReliefProvider().getGradY(xz)),
                RockStrata.AngledPlaneStrata.create(9, 8, rocks));
    }

    public int getHeight(final int x, final int z) {
        //   final double interpolatedBlurredRelief = reliefBlurredInterpolation.interpolateBilinear(x, z);
        final double interpolatedRelief = accessor.reliefInterpolation().interpolateSmoothStep(x, z);
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

    private final ThreadLocal<double[]> mutableCoordsXZ = ThreadLocal.withInitial(() -> new double[2]);

    public BlockState placeRiver(BlockState state, int xx, int distFromSurface, int zz) {
        if (distFromSurface != 0) return state;
        final int coarseX = Math.floorDiv(xx, 256 * 5);
        final int coarseZ = Math.floorDiv(zz, 256 * 5);
        mutableCoordsXZ.get()[0] = xx * 0.2;
        mutableCoordsXZ.get()[1] = zz * 0.2;
        final int globalRiverData =
                FractalTerrainInstance.getGlobalRiverProvider().getArrow(coarseX, coarseZ);
        if (GlobalRiverProvider.isRiver(globalRiverData) || GlobalRiverProvider.isCoast(globalRiverData)) {
            boolean f = FractalTerrainInstance.getLocalRiverProvider().insideMargin(mutableCoordsXZ.get());
            if (f) return INSIDE_MARGIN_ROCK;
            if (GlobalRiverProvider.isRiver(globalRiverData)) return RIVER_MARKER_ROCK;
            return COAST_MARKER_ROCK;
        }
        return state;
    }

    public ReliefAccessor getReliefAccessor() {
        return accessor;
    }
}
