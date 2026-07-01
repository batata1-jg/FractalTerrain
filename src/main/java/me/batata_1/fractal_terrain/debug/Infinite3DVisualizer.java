package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.CH;

import java.util.function.Function;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * When turned on in FractalTerrainConfig, projects anything into the minecraft world
 * using the chunk generator.
 * When using this, don't forget to disable the surface rule step and biome decoration step
 * */
public class Infinite3DVisualizer {

    private static final BlockState DEFAULT = Blocks.WHITE_CONCRETE.defaultBlockState();

    public enum DebugModes {
        RELIEF(0.2f, 1.0f, 5.0f, xz -> FractalTerrainInstance.getReliefProvider()
                .getElev(xz)),
        COARSE(1.0f, 1.0f, 256.0f, xz -> FractalTerrainInstance.getInfinite3DVisualizer()
                .getCoarse(xz)),

        DIST_SHORE(10.0f, 1.0f, 5.0f, xz ->
                (float) FractalTerrainInstance.getBiomeProvider().getDistShore(xz));
        final float scale;
        final float elevationBias;
        final Interpolation interp;

        DebugModes(
                final float elevationBias,
                final float resolution,
                final float baseScale,
                final Function<int[], Float> f) {
            this.elevationBias = elevationBias;
            this.scale = resolution * baseScale;
            this.interp = new Interpolation(this.scale, f);
        }

        public int sample(int x, int z) {
            return (int) (elevationBias * scale * interp.interpolateBilinear(x, z));
        }
    }

    /**
     * Paint modes for {@link #debugPaintController}. Each constant links to a painting method on this
     * visualizer that maps a block position to the {@link BlockState} it should be rendered with.
     * <ul>
     *   <li>{@link #RIVER_NET} — {@link #debugRiver}: global/local river + coast markers.</li>
     *   <li>{@link #PV} — {@link #debugPV}: peaks-and-valleys bands quantized from biome weirdness.</li>
     * </ul>
     */
    public enum DebugPaintModes {
        RIVER_NET(Infinite3DVisualizer::debugRiver),
        PV(Infinite3DVisualizer::debugPV);

        private final PaintFn fn;

        DebugPaintModes(final PaintFn fn) {
            this.fn = fn;
        }

        public BlockState paint(int x, int y, int z) {
            return fn.paint(FractalTerrainInstance.getInfinite3DVisualizer(), x, y, z);
        }

        @FunctionalInterface
        private interface PaintFn {
            BlockState paint(Infinite3DVisualizer viz, int x, int y, int z);
        }
    }

    private Float getCoarse(int[] xz) {
        xz[CH] = 0;
        return FractalTerrainInstance.pipeline.getCoarse().getSlice(xz, xz).data[0];
    }

    public Infinite3DVisualizer() {}

    public int debugElevController(int x, int z) {
        return FractalTerrainConfig.VIZ_H_CONTROL_MODE.sample(x, z);
    }

    public BlockState debugPaintController(int x, int y, int z) {
        return FractalTerrainConfig.VIZ_PAINT_CONTROL_MODE.paint(x, y, z);
    }

    // hydrology utils
    private final ThreadLocal<double[]> mutableCoordsXZ = ThreadLocal.withInitial(() -> new double[2]);
    private static final BlockState INSIDE_MARGIN_ROCK = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
    private static final BlockState COAST_MARKER_ROCK = Blocks.BLUE_CONCRETE.defaultBlockState();
    private static final BlockState RIVER_MARKER_ROCK = Blocks.GREEN_CONCRETE.defaultBlockState();

    public BlockState debugRiver(int xx, int y, int zz) {
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
        return DEFAULT;
    }

    // peaks-and-valleys (PV) painting
    private static final BlockState PV_VALLEYS = Blocks.PURPLE_CONCRETE.defaultBlockState();
    private static final BlockState PV_LOW = Blocks.BLUE_CONCRETE.defaultBlockState();
    private static final BlockState PV_MID = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
    private static final BlockState PV_HIGH = Blocks.LIME_CONCRETE.defaultBlockState();
    private static final BlockState PV_PEAKS = Blocks.YELLOW_CONCRETE.defaultBlockState();

    /**
     * Paints a peaks-and-valleys band derived from biome weirdness. The weirdness {@code w} at
     * {@code (x, z)} (scale-5 interpolation from {@link me.batata_1.fractal_terrain.world.biome.BiomeProvider})
     * is folded into {@code pv = 1 - |3·|w| - 2|} and quantized into five bands:
     * Valleys {@code [-1, -0.85)}, Low {@code [-0.85, -0.2)}, Mid {@code [-0.2, 0.2)},
     * High {@code [0.2, 0.7)}, Peaks {@code [0.7, 1]}.
     */
    public BlockState debugPV(int x, int y, int z) {
        final double weirdness = FractalTerrainInstance.getBiomeProvider().getWeirdness(x, z);
        final double pv = 1.0 - Math.abs(3.0 * Math.abs(weirdness) - 2.0);
        if (pv < -0.85) return PV_VALLEYS;
        if (pv < -0.2) return PV_LOW;
        if (pv < 0.2) return PV_MID;
        if (pv < 0.7) return PV_HIGH;
        return PV_PEAKS;
    }
}
