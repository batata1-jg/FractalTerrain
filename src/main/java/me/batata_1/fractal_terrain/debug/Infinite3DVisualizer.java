package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.CH;

import java.util.function.Function;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.relief.DecoderChannels;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When turned on in FractalTerrainConfig, projects anything into the minecraft world
 * using the chunk generator.
 * When using this, don't forget to disable the surface rule step and biome decoration step
 * */
@TestOnly
public class Infinite3DVisualizer {

    private static final BlockState DEFAULT = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final Logger LOG = LoggerFactory.getLogger(Infinite3DVisualizer.class);

    public enum DebugModes {
        /** Elevation <em>after</em> the carving step: the carved+filled ch0 imported by ReliefProvider. */
        RELIEF(0.2f, 1.0f, 5.0f, xz -> FractalTerrainInstance.getReliefProvider()
                .getElev(xz)),
        /**
         * Elevation <em>before</em> the carving step: the raw decoded terrain ({@link DecoderChannels}
         * {@code base[0]}), same vertical scale as {@link #RELIEF} so before/after are directly comparable.
         */
        DECODED(0.2f, 1.0f, 5.0f, xz -> FractalTerrainInstance.getInfinite3DVisualizer()
                .getDecodedElev(xz)),
        COARSE(1.0f, 1.0f, 256.0f, xz -> FractalTerrainInstance.getInfinite3DVisualizer()
                .getCoarse(xz)),
        POP_NOISE_RELIEF(1f,1f,1f,xz->0f) {
            @Override
            public int sample(int x, int z) {
                final FractalTerrainHeightmap heightmaps =
                        FractalTerrainInstance.getHeightmapCache().getOrCompute(new ChunkPos(x>>4, z>>4));
                final float[] reliefBaseHeight = heightmaps.get(FractalTerrainHeightmap.Types.ELEVATION);
                return (int) reliefBaseHeight[(x&15)*16 + (z&15)];
            }
        },

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
     *   <li>{@link #HYDRO_ZONES} — {@link #debugHydroZones}: per-block carve zone (bed / floodplain /
     *       blending), the deepest zone reached across every influencing hydrological unit.</li>
     * </ul>
     */
    public enum DebugPaintModes {
        RIVER_NET(Infinite3DVisualizer::debugRiver),
        PV(Infinite3DVisualizer::debugPV),
        HYDRO_ZONES(Infinite3DVisualizer::debugHydroZones);

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
        return FractalTerrainInstance.pipeline.getCoarse().getSlice(xz, xz).get(0);
    }

    // Pre-carve (decoded) elevation sampling. DecoderChannels.decode is a whole-tile decode, so we cache
    // the most recently decoded tile's ch0 per thread — a bilinear sample hits 4 neighbours that almost
    // always fall in the same tile, so this is ~1 decode per tile region rather than per block.
    private final ThreadLocal<long[]> decodedTileKey = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
    private final ThreadLocal<float[]> decodedTileElev = ThreadLocal.withInitial(() -> null);

    /**
     * Decoded (pre-carve) elevation at relief-pixel {@code (xz[1], xz[2])} — the {@link DecoderChannels}
     * {@code base[0]} that {@link DebugModes#DECODED} projects, before any river carving is applied.
     */
    private Float getDecodedElev(int[] xz) {
        final int px = xz[1];
        final int pz = xz[2];
        final int tileX = Math.floorDiv(px, DecoderChannels.INNER);
        final int tileZ = Math.floorDiv(pz, DecoderChannels.INNER);
        final long key = (((long) tileX) << 32) ^ (tileZ & 0xffffffffL);
        if (decodedTileElev.get() == null || decodedTileKey.get()[0] != key) {
            decodedTileElev.set(DecoderChannels.decode(tileX, tileZ, 0)[0]);
            decodedTileKey.get()[0] = key;
        }
        final int lx = px - tileX * DecoderChannels.INNER;
        final int lz = pz - tileZ * DecoderChannels.INNER;
        return decodedTileElev.get()[lx * DecoderChannels.INNER + lz];
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
        final double[] pixelPt = mutableCoordsXZ.get();
        pixelPt[0] = xx * 0.2; // block -> relief-pixel frame (÷ GLOBAL_SCALE_CORRECTION)
        pixelPt[1] = zz * 0.2;
        // Channel membership first, independent of the coarse-cell gate, so local channels outside
        // global river/coast cells also render their width band.
        if (FractalTerrainInstance.getHydrologyPainter().insideChannel(pixelPt)) return INSIDE_MARGIN_ROCK;
        final int coarseX = Math.floorDiv(xx, 256 * 5);
        final int coarseZ = Math.floorDiv(zz, 256 * 5);
        final int globalRiverData =
                FractalTerrainInstance.getGlobalRiverProvider().getArrow(coarseX, coarseZ);
        if (GlobalRiverProvider.isRiver(globalRiverData)) return RIVER_MARKER_ROCK;
        if (GlobalRiverProvider.isCoast(globalRiverData)) return COAST_MARKER_ROCK;
        return DEFAULT;
    }

    // hydrology carve-zone painting (bed / floodplain / blending)
    private static final BlockState BED_ZONE = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState FLOODPLAIN_ZONE = Blocks.ORANGE_CONCRETE.defaultBlockState();
    private static final BlockState BLENDING_ZONE = Blocks.PINK_CONCRETE.defaultBlockState();

    /**
     * Paints the carve zone at {@code (xx, zz)} as the deepest zone reached by any influencing
     * {@link HydrologicalUnit}, mirroring
     * {@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver#carvePrefetched}'s
     * min-composite (deepest covering channel wins) rather than a single nearest-unit seam:
     *
     * <ul>
     *   <li><b>bed</b> ({@code radialDist ≤ bedHalfWidth}) → red;</li>
     *   <li><b>floodplain</b> ({@code radialDist ≤ floodPlainLength}) → orange;</li>
     *   <li><b>blending</b> (out to the unit's influence radius) → pink.</li>
     * </ul>
     *
     * <p>{@code radialDist} is the plain Euclidean distance from the point to the unit's centre — the
     * same measure {@link HydrologicalUnit#channelContains} and {@code HydrologyProfileCarver#carvePrefetched}
     * use — not a perpendicular/along-channel decomposition, so this is an approximation near sharply
     * curved channels rather than an exact geometric preview.
     */
    public BlockState debugHydroZones(int xx, int y, int zz) {
        final double[] pt = mutableCoordsXZ.get();
        pt[0] = xx * 0.2; // block -> relief-pixel frame (÷ GLOBAL_SCALE_CORRECTION)
        pt[1] = zz * 0.2;
        //  LOG.info("[");
        final HydrologicalUnit[] units =
                FractalTerrainInstance.getLocalRiverProvider().queryInfluence(pt);
        // LOG.info("]");
        BlockState deepest = DEFAULT;
        for (final HydrologicalUnit unit : units) {
            final double du = pt[0] - unit.coord()[0];
            final double dv = pt[1] - unit.coord()[1];
            final double radialDist = Math.hypot(du, dv);
            if (radialDist >= unit.getRadius()) continue; // outside this unit's influence circle

            if (radialDist <= ChannelGeometry.bedHalfWidth(unit.width())) return BED_ZONE; // deepest possible

            if (deepest == BED_ZONE) continue;
            final RosgenProfile profile =
                    RosgenProfile.of(unit.rosgenType() == null ? HydrologicalUnit.RosgenType.A : unit.rosgenType());
            if (radialDist <= profile.floodPlainLength(unit.width())) {
                deepest = FLOODPLAIN_ZONE;
            } else if (deepest == DEFAULT) {
                deepest = BLENDING_ZONE;
            }
        }
        return deepest;
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
