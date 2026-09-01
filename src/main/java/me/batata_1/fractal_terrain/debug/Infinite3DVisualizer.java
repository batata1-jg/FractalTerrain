package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.CH;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.function.Function;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.hydrology.providers.GlobalRiverProvider;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.relief.DecoderChannels;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmapCacheAccessor;
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
        /** Elevation before carving (raw {@link DecoderChannels} {@code base[0]}), same scale as
         *  {@link #RELIEF} so before/after compare directly. */
        DECODED(0.2f, 1.0f, 5.0f, xz -> FractalTerrainInstance.getInfinite3DVisualizer()
                .getDecodedElev(xz)),
        COARSE(1.0f, 1.0f, 256.0f, xz -> FractalTerrainInstance.getInfinite3DVisualizer()
                .getCoarse(xz)),
        POP_NOISE_RELIEF(1f, 1f, 1f, xz -> 0f) {
            @Override
            public int sample(int x, int z) {
                final FractalTerrainHeightmap heightmaps = FractalTerrainHeightmapCacheAccessor.get(x >> 4, z >> 4);
                final float[] reliefBaseHeight = (float[]) heightmaps.get(FractalTerrainHeightmap.Types.ELEVATION);
                return (int) reliefBaseHeight[(x & 15) * 16 + (z & 15)];
            }
        },
        SINGLE_PRIMITIVE(0, 0, 0, xz -> 0f) {

            private static final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>();
            private static final ImmutableRTree<HydrologicalPrimitive> tree;
            private static final int BASE_ELEV = 80;

            static {
                final double width = 7;
                primitives.add(new RiverPrimitive(
                        new double[] {256, 256},
                        HydrologyTuning.influence(width),
                        RiverPrimitive.RosgenType.C,
                        new double[] {0, 1},
                        0.4,
                        width,
                        64,
                        0));
                primitives.add(new RiverPrimitive(
                        new double[] {256 + HydrologyTuning.influence(width), 256, +HydrologyTuning.influence(width)},
                        HydrologyTuning.influence(width),
                        RiverPrimitive.RosgenType.C,
                        new double[] {1, 0},
                        0.4,
                        width,
                        64,
                        0));
                tree = new ImmutableRTree<>(primitives, HydrologicalPrimitive.PROTOTYPE);
            }

            @Override
            public int sample(int x, int z) {
                x %= 512 * 5;
                z %= 512 * 5;
                if (x < 0) x += 512 * 5;
                if (z < 0) z += 512 * 5;
                final double[] pt = {x / 5.0, z / 5.0};
                var primitives = tree.queryContaining(pt);
                if (primitives.isEmpty()) return BASE_ELEV;
                double weight = 0;
                double weightedElev = 0;
                double distance = 10;
                return BASE_ELEV;
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
     *       blending), the deepest zone reached across every influencing hydrological primitive.</li>
     * </ul>
     */
    public enum DebugPaintModes {
        EMPTY((var a, var b, var c, var d) -> DEFAULT),
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

    /** Decoded (pre-carve) elevation at relief-pixel {@code (xz[1], xz[2])} — what {@link DebugModes#DECODED}
     *  projects, before river carving. */
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
    private static final BlockState FLOODPLAIN_ZONE = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState BLENDING_ZONE = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();

    /** Bed-zone block per {@link RiverPrimitive.RosgenType} ordinal; frozen order matches primitive serialization. */
    private static final BlockState[] BED_ZONE_BY_ROSGEN = {
        Blocks.RED_CONCRETE.defaultBlockState(), // A   steep entrenched
        Blocks.PINK_CONCRETE.defaultBlockState(), // Aa  very steep
        Blocks.ORANGE_CONCRETE.defaultBlockState(), // B   moderately entrenched
        Blocks.YELLOW_CONCRETE.defaultBlockState(), // C   meandering, broad floodplain
        Blocks.LIME_CONCRETE.defaultBlockState(), // D   braided
        Blocks.GREEN_CONCRETE.defaultBlockState(), // DA  anastomosing
        Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), // E   narrow, deep, highly sinuous
        Blocks.BLUE_CONCRETE.defaultBlockState(), // F   entrenched meandering
        Blocks.PURPLE_CONCRETE.defaultBlockState(), // G   entrenched gully
    };

    private static final BlockState NOT_RIVER = Blocks.BLACK_CONCRETE.defaultBlockState();

    /** Preview of what {@code RiverInfluenceCarve} will carve at {@code (xx, zz)}: composites the
     *  deepest zone across covering {@link RiverPrimitive}s, but returns black on any non-river primitive in range. */
    public BlockState debugHydroZones(int xx, int y, int zz) {
        final double[] pt = mutableCoordsXZ.get();
        pt[0] = xx * 0.2; // block -> relief-pixel frame (÷ GLOBAL_SCALE_CORRECTION)
        pt[1] = zz * 0.2;
        //  LOG.info("[");
        final HydrologicalPrimitive[] primitives =
                FractalTerrainInstance.getRiverProvider().queryInfluence(pt).toArray(new HydrologicalPrimitive[0]);
        // LOG.info("]");

        BlockState deepest = DEFAULT;
        for (final HydrologicalPrimitive primitive : primitives) {
            final double du = pt[0] - primitive.coord()[0];
            final double dv = pt[1] - primitive.coord()[1];
            final double radialDist = Math.hypot(du, dv);
            if (!primitive.containsPoint(pt)) continue; // outside this primitive's influence circle

            if (!(primitive instanceof RiverPrimitive riverPrimitive)) continue;
            // An unclassified reach paints as A, matching how the carve coalesces a null type.
            final RiverPrimitive.RosgenType type = RiverPrimitive.RosgenType.orDefault(riverPrimitive.rosgenType());

            // Bed is the deepest possible zone: no later primitive can beat it, so paint and stop.
            if (radialDist <= ChannelGeometry.bedHalfWidth(riverPrimitive.width()))
                return BED_ZONE_BY_ROSGEN[type.ordinal()];

            final RosgenProfile profile = RosgenProfile.of(type);
            if (radialDist <= profile.floodPlainLength(riverPrimitive.width())) {
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

    /** Paints a peaks-and-valleys band derived from biome weirdness — quick visual check of the PV
     *  classification before it drives biome/feature placement. */
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
