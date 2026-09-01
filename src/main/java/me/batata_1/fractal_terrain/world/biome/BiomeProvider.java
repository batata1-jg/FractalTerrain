package me.batata_1.fractal_terrain.world.biome;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.*;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import com.google.common.base.Function;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.storage.ChunkChannelFill;
import me.batata_1.fractal_terrain.storage.TileKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Builds the per-tile vanilla biome-parameter field {@code FractalTerrainBiomeSource} resolves biomes
 * from — the bridge between the diffusion model's output and vanilla's biome machinery.
 *
 * <p>Producing vanilla's own six parameters, rather than picking biomes directly, is what lets the
 * existing {@code MultiNoiseBiomeSource} and every datapack biome keep working unchanged.
 *
 * <p>Five parameters are horizontal and cached per tile; depth is vertical and comes straight from
 * block Y, so it is never stored in a channel.
 */
public class BiomeProvider {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Native-px e-folding distance of the river-humidity falloff (tunable). */
    private static final double HUMIDITY_FALLOFF = 32.0;

    private static final int TILE_PIXELS = 1 << 18; // 512 × 512

    // --- coarse distance-to-shore -------------------------------------------------
    // A biome tile owns a 2×2 block of coarse cells (1 coarse cell = 256 native px).
    // For each cell we measure the Manhattan distance to the nearest ocean cell within
    // a 9×9 window, then bilinearly upscale the per-cell value to a smooth per-pixel
    // signal inside ClimateVariableTransform. The grid carries a 1-cell halo on every
    // side so the bilinear sampler has neighbours for the tile's edge pixels.

    /** Coarse cells spanned by a biome tile per axis (512 px / 256 px-per-cell). */
    private static final int COARSE_CELLS_PER_TILE = 2;

    /** Half-extent of the shore search window (9×9 ⇒ radius 4); also the max Manhattan reach. */
    private static final int SHORE_SEARCH_RADIUS = 4;

    /** Distance assigned to an ocean cell (normalized coarse elevation &lt; 0). */
    private static final int SHORE_DIST_OCEAN = -1;

    /** Distance assigned when no ocean cell is found within the search window. */
    private static final int SHORE_DIST_NO_OCEAN = 8;

    /** Side of the upscale grid: the 2×2 owned cells plus a 1-cell bilinear halo each side. */
    private static final int DSHORE_GRID = COARSE_CELLS_PER_TILE + 2;

    /** Minimum blend weight before a coarse pixel is treated as present (matches the pipeline). */
    private static final float COARSE_WEIGHT_EPS = 1e-6f;

    // --- tile channel layout ------------------------------------------------------
    // Each stored tile is [0..4] biome params, [5] river-humidity PDF (reserved), and — only while the 3D
    // visualizer runs — [6] a debug distance-to-shore field. The tensor is declared with TILE_CHANNELS so
    // every channel (including the debug one) is addressable; getDistShore reads DEBUG_DSHORE_CHANNEL.

    /** Biome-parameter channels {@link ClimateVariableTransform#transform} returns (continentalness…weirdness). */
    private static final int PARAM_CHANNELS = 5;

    /** Whether each tile carries the extra debug distance-to-shore channel (only while the visualizer runs). */
    private static final boolean DEBUG_DSHORE_CHANNEL_ON = !DISABLE_3D_VISUALIZER;

    /** Index of the appended debug distance-to-shore channel (after the {@link FractalTerrainConfig#BIOME_CHANNELS} real ones). */
    private static final int DEBUG_DSHORE_CHANNEL = BIOME_CHANNELS;

    /** Channels stored per tile: the real {@link FractalTerrainConfig#BIOME_CHANNELS}, plus the debug channel when active. */
    private static final int TILE_CHANNELS = DEBUG_DSHORE_CHANNEL_ON ? BIOME_CHANNELS + 1 : BIOME_CHANNELS;

    /** Cached biome tiles. Same 8-tile reasoning as {@code ReliefProvider}: Storage's accounting is
     *  FIFO, so the budget must sit well above the 1-4 tile working set. Sized off TILE_CHANNELS, so it
     *  already covers the larger tile the visualizer's debug channel produces. */
    private static final int MAX_CACHED_TILES = 8;

    private static final long CACHE_LIMIT_BYTES = (long) MAX_CACHED_TILES * TILE_CHANNELS * TILE_PIXELS * Float.BYTES;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final NonIntersectingInfiniteTensor finalTiles;
    public final Climate.Sampler sampler;

    private final DensityFunction erosionDensity;
    private final DensityFunction weirdnessDensity;

    /** Scale-5 bilinear sampler over the weirdness channel; backs {@link #getWeirdness(int, int)}. */
    @TestOnly
    private final Interpolation weirdnessInterpolation;



    // -------------------------------------------------------------------------
    // Channel → density wiring
    // -------------------------------------------------------------------------

    /** Produces the {@link DensityFunction} that turns a stored tile channel into per-block values. */
    @FunctionalInterface
    private interface DensityFactory {
        DensityFunction create(float scale, int channel);
    }

    /**
     * Tile-channel index plus per-channel density creator for each vanilla biome parameter, matching the
     * layout written by {@link ClimateVariableTransform#transform}. Each constant owns the strategy that
     * turns its channel into a {@link DensityFunction}: most channels interpolate bilinearly
     * ({@link BiomeProviderDensity}), but {@link #EROSION} needs a different creator
     * ({@link ErosionDensity}) because interpolating across the negative→positive erosion range would
     * sweep values through the shattered band (level 5, ≈0.45–0.55), and {@link #WEIRDNESS} uses
     * {@link WeirdnessDensity} (value × rapidly-flipping sign). {@link #DEPTH} is vertical (from block Y),
     * has no tile channel (sentinel {@code -1}), and its creator returns a y-gradient.
     */
    private enum BiomeChannels {
        CONTINENTALNESS(0, BiomeProviderDensity::new),
        EROSION(1, ErosionDensity::new),
        TEMPERATURE(2, BiomeProviderDensity::new),
        HUMIDITY(3, BiomeProviderDensity::new),
        WEIRDNESS(4, WeirdnessDensity::new),
        DEPTH(-1, (scale, ch) -> DensityFunctions.yClampedGradient(-64, 63, -1, 0));

        final int channel;
        final DensityFactory factory;

        BiomeChannels(int channel, DensityFactory factory) {
            this.channel = channel;
            this.factory = factory;
        }

        DensityFunction density(float scale) {
            return factory.create(scale, channel);
        }
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public BiomeProvider(String path) {
        finalTiles = new NonIntersectingInfiniteTensor(
                path, "final_biome_tiles", new int[] {TILE_CHANNELS, 512, 512}, buildTile(), CACHE_LIMIT_BYTES);
        // Vanilla Climate.Sampler order: temperature, humidity, continentalness, erosion, depth, weirdness.
        // Each channel produces its own density via its creator (DEPTH's is the vertical y-gradient).
        final float scale = 1;
        erosionDensity = BiomeChannels.EROSION.density(scale);
        weirdnessDensity = BiomeChannels.WEIRDNESS.density(scale);
        sampler = new Climate.Sampler(
                BiomeChannels.TEMPERATURE.density(scale),
                BiomeChannels.HUMIDITY.density(scale),
                BiomeChannels.CONTINENTALNESS.density(scale),
                erosionDensity,
                BiomeChannels.DEPTH.density(scale),
                weirdnessDensity,
                List.of());

        weirdnessInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
            mutablePos[CH] = BiomeChannels.WEIRDNESS.channel;
            return finalTiles.getValue(mutablePos);
        });
    }

    private static @NotNull Function<TileKey, FloatTensor> buildTile() {
        return key -> {
            int x = key.get(X);
            int z = key.get(Z);
            FloatTensor reliefTensor = FractalTerrainInstance.getReliefProvider()
                    .getInfiniteTensor()
                    .getEntry(key);

            final float[] elev = reliefTensor.copyRange(0, 1 << 18);
            final float[] grad = reliefTensor.copyRange(4 << 18, 5 << 18);
            final float[] lowFreqGrad = reliefTensor.copyRange(5 << 18, 6 << 18);
            final float[] res = reliefTensor.copyRange(6 << 18, 7 << 18);
            final float[] climate = pipeline.getClimate(x, z, elev);
            final int[] coarseDistShore = computeCoarseDistShore(x, z);
            // While the visualizer runs, also capture the per-pixel dist-to-shore for the debug channel.
            final float[] distShoreDebug = DEBUG_DSHORE_CHANNEL_ON ? new float[TILE_PIXELS] : null;
            var humidityFromRiver = humidityFromBodiesOfWater(key);
            final float[] biomeVariables = ClimateToBiomeTransformer.transform(
                    x, z, elev, grad, lowFreqGrad, climate, res, humidityFromRiver, coarseDistShore, distShoreDebug);

            // Channel layout: [0..4] biome params, [5] river-humidity PDF (reserved, unused by the
            // sampler), [6] debug dist-to-shore (only while the visualizer runs).
            final float[] entries = new float[TILE_CHANNELS * TILE_PIXELS];
            System.arraycopy(biomeVariables, 0, entries, 0, (PARAM_CHANNELS+1) * TILE_PIXELS);
            if (distShoreDebug != null) {
                System.arraycopy(distShoreDebug, 0, entries, DEBUG_DSHORE_CHANNEL * TILE_PIXELS, TILE_PIXELS);
            }
            // FloatTensor t = new FloatTensor(entries, new int[] {TILE_CHANNELS, 512, 512});
            //  ;; Debug.seeTile(t, x, z, "final_biomes");
            return new FloatTensor(entries, new int[] {TILE_CHANNELS, 512, 512});
        };
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Coarse distance-to-shore, the signal driving every coastal biome. Carries a one-cell halo so
     *  the upscale can interpolate without falling off the tile edge. */
    private static int[] computeCoarseDistShore(int tileX, int tileZ) {
        // Coarse-cell origin of the tile (1 coarse cell = 256 px ⇒ tileIndex<<9 px = tileIndex*2 cells).
        final int tileCx = tileX * COARSE_CELLS_PER_TILE;
        final int tileCz = tileZ * COARSE_CELLS_PER_TILE;

        // Fill the grid one cell at a time. Grid cell (gx, gz) is the coarse cell at coarse-px
        // (tileCx - 1 + gx, tileCz - 1 + gz): the -1 puts grid index 0 on the halo ring, so the 2×2 owned
        // cells land at grid indices 1..2 (matching the +1 halo-skip in ClimateVariableTransform's bilinear).
        final int[] distShore = new int[DSHORE_GRID * DSHORE_GRID];
        for (int gx = 0; gx < DSHORE_GRID; gx++) {
            for (int gz = 0; gz < DSHORE_GRID; gz++) {
                distShore[gx * DSHORE_GRID + gz] = calculateCell(tileCx - 1 + gx, tileCz - 1 + gz);
            }
        }
        return distShore;
    }

    /** Per-cell half of {@link #computeCoarseDistShore}. The caller already encodes the halo in the
     *  coordinate passed, so this applies no halo correction of its own. */
    private static int calculateCell(int cellCx, int cellCz) {
        final int sliceSide = 2 * SHORE_SEARCH_RADIUS + 1;
        final int ci0 = cellCx - SHORE_SEARCH_RADIUS;
        final int cj0 = cellCz - SHORE_SEARCH_RADIUS;
        final FloatTensor slice = pipeline.getCoarseSlice(ci0, cj0, ci0 + sliceSide, cj0 + sliceSide);

        // Weight-normalize coarse elevation (channel 0 / channel 6); ocean ⇒ value < 0.
        final int cells = sliceSide * sliceSide;
        final float[] elev = new float[cells];
        for (int px = 0; px < cells; px++) {
            final float w = slice.get(6 * cells + px);
            elev[px] = (w > COARSE_WEIGHT_EPS) ? slice.get(px) / w : 0f;
        }

        // The target cell sits at the slice centre.
        final int si = SHORE_SEARCH_RADIUS;
        final int sj = SHORE_SEARCH_RADIUS;
        if (elev[si * sliceSide + sj] < 0f) return SHORE_DIST_OCEAN;
        int minManhattan = Integer.MAX_VALUE;
        for (int dx = -SHORE_SEARCH_RADIUS; dx <= SHORE_SEARCH_RADIUS; dx++) {
            for (int dz = -SHORE_SEARCH_RADIUS; dz <= SHORE_SEARCH_RADIUS; dz++) {
                if (elev[(si + dx) * sliceSide + (sj + dz)] < 0f) {
                    minManhattan = Math.min(minManhattan, Math.abs(dx) + Math.abs(dz));
                }
            }
        }
        // Adjacent ocean (Manhattan 1) ⇒ shore 0; corner ocean (Manhattan 8) ⇒ 7.
        return (minManhattan == Integer.MAX_VALUE) ? SHORE_DIST_NO_OCEAN : minManhattan - 1;
    }

    /** Dead: no call site, and the body is commented out, so it returns all zeros.
     *  Intended to raise humidity near rivers. */
    private static float[] humidityFromBodiesOfWater(TileKey key) {
        final var primitives = FractalTerrainInstance.getRiverProvider().getPrimitivesInTile(key);
        primitives.sort(HydrologicalPrimitive.comparator);
        final var riverH = new float[TILE_PIXELS];
        for (int ix = 0; ix < 512; ix++) {
            for (int iz = 0; iz < 512; iz++) {
                // final double dist = localRivers.nearestRiverDistance(blockOriginX + ix, blockOriginZ + iz);
                // vegPdf[ix * 512 + iz] = (dist == Double.MAX_VALUE) ? 0f : (float) Math.exp(-dist / HUMIDITY_FALLOFF);
            }
        }
        return new float[TILE_PIXELS];
    }

    // -------------------------------------------------------------------------
    // Per-chunk channel producers (consumed by FractalTerrainHeightmap)
    // -------------------------------------------------------------------------

    /** Erosion for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z} (see {@link ErosionDensity}). */
    public float[] fillErosion(ChunkPos pos) {
        final float[] res = new float[1 << 8];
        ((ChannelDensity) erosionDensity).fillArray(res, pos);
        return res;
    }

    /** Weirdness for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z} (see {@link WeirdnessDensity}). */
    public float[] fillWeirdness(ChunkPos pos) {
        final float[] res = new float[1 << 8];
        ((ChannelDensity) weirdnessDensity).fillArray(res, pos);
        return res;
    }

    /** Continentalness for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillContinentalness(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(finalTiles, BiomeChannels.CONTINENTALNESS.channel, pos);
    }

    /** Temperature for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillTemperature(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(finalTiles, BiomeChannels.TEMPERATURE.channel, pos);
    }

    /** Vegetation/humidity for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillVegetation(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(finalTiles, BiomeChannels.HUMIDITY.channel, pos);
    }

    // -------------------------------------------------------------------------
    // Nested density functions
    // -------------------------------------------------------------------------

    /**
     * Shared body of the three tile-channel densities.
     *
     * <p>Holds the two ways to read one channel — a pre-sliced chunk window for the batch and chunk
     * fills, and the per-point {@link Interpolation} that still backs {@code compute} — so each
     * subclass supplies only its own composition (a shattered-band nudge, a magnitude-times-sign
     * product) rather than a fourth copy of the fill loops.
     */
    private abstract static class ChannelDensity implements DensityFunction.SimpleFunction {

        protected ChannelDensity(final int channel, final float pixelScale) {
            this.channel = channel;
            this.pixelScale = pixelScale;
        }

        /** Bounds the batch's block rectangle, slices it once, then samples. Falls back to per-point
         *  reads for a batch too wide to slice, rather than allocating an unbounded window. */
        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            // One pass over forIndex, never two: NoiseChunk's anonymous ContextProvider also advances
            // interpolationCounter, which is CacheOnce's generation stamp, so asking a second time would
            // invalidate sibling caches that are still live. The positions are kept rather than re-read.
            final int[] blocks = new int[densities.length * 2];
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                final int x = pos.blockX();
                final int z = pos.blockZ();
                blocks[2 * i] = x;
                blocks[2 * i + 1] = z;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }

            if (maxX - minX >= MAX_BATCH_SPAN_BLOCKS || maxZ - minZ >= MAX_BATCH_SPAN_BLOCKS) {
                for (int i = 0; i < densities.length; i++) {
                    densities[i] = sample(blocks[2 * i], blocks[2 * i + 1]);
                }
                return;
            }

            final ChunkChannelFill.ChunkWindow window = open(minX, minZ, maxX, maxZ);
            for (int i = 0; i < densities.length; i++) {
                densities[i] = sample(window, blocks[2 * i], blocks[2 * i + 1]);
            }
        }

        /** The 16x16 chunk form, backing {@code fillErosion}/{@code fillWeirdness}. */
        public void fillArray(float[] out, ChunkPos pos) {
            final int startX = pos.getMinBlockX();
            final int startZ = pos.getMinBlockZ();
            final ChunkChannelFill.ChunkWindow window = open(startX, startZ, startX + 15, startZ + 15);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    out[x * 16 + z] = (float) sample(window, startX + x, startZ + z);
                }
            }
        }

        @Override
        public double compute(FunctionContext pos) {
            return sample(pos.blockX(), pos.blockZ());
        }

        @Override
        public double minValue() {
            return 0;
        }

        @Override
        public double maxValue() {
            return 0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return null;
        }

        /** This density's value at a block, reading a window already sliced over it. */
        protected abstract double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ);

        /** This density's value at a single block, via the per-point tensor path. */
        protected abstract double sample(int blockX, int blockZ);

        /** Widest block span the batch path will slice before falling back to per-point reads. A vanilla
         *  {@code fillArray} batch is cell-sized, so this only guards a pathological caller. */
        private static final int MAX_BATCH_SPAN_BLOCKS = 64;

        /** Tile channel this density reads; the window slices it directly. */
        protected final int channel;

        /** Blocks per tensor pixel, the same divisor {@link Interpolation} applies internally. Threaded
         *  into the window path rather than assumed, so the two cannot silently disagree. */
        protected final float pixelScale;

        /** Bilinear read of {@link #channel} out of an open window; the subclasses compose on top. */
        protected double read(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            return Interpolation.sampleWindowBilinear(
                    window.data(),
                    blockX / pixelScale,
                    blockZ / pixelScale,
                    window.originX(),
                    window.originZ(),
                    window.rowStride());
        }

        private ChunkChannelFill.ChunkWindow open(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
            return ChunkChannelFill.open(
                    FractalTerrainInstance.getBiomeProvider().finalTiles,
                    channel,
                    minBlockX,
                    minBlockZ,
                    maxBlockX,
                    maxBlockZ,
                    pixelScale);
        }
    }

    /** Plain bilinear interpolation of a stored biome channel. */
    private static class BiomeProviderDensity extends ChannelDensity {

        public BiomeProviderDensity(final float scale, final int ch) {
            super(ch, scale * GLOBAL_SCALE_CORRECTION);
            interpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().finalTiles.getValue(mutablePos);
            });
        }

        @Override
        protected double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            return read(window, blockX, blockZ);
        }

        @Override
        protected double sample(int blockX, int blockZ) {
            return interpolation.interpolateBilinear(blockX, blockZ);
        }

        private final Interpolation interpolation;
    }

    /**
     * Erosion density that nudges values out of vanilla's shattered band (erosion level 5,
     * ≈0.45–0.55) until shattered biomes are handled. See {@code worldgeneration101.md}
     * ("Shattered biomes") and {@link BiomeParameterClassifier#isShatteredErosion}.
     */
    private static class ErosionDensity extends ChannelDensity {

        public ErosionDensity(final float scale, final int ch) {
            super(ch, scale * GLOBAL_SCALE_CORRECTION);
            interpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().finalTiles.getValue(mutablePos);
            });
        }

        @Override
        protected double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            return nudge(read(window, blockX, blockZ));
        }

        @Override
        protected double sample(int blockX, int blockZ) {
            return nudge(interpolation.interpolateBilinear(blockX, blockZ));
        }

        // Shattered (erosion level 5) avoidance band and the push applied to escape it.
        private static final double SHATTERED_LO = 0.44, SHATTERED_HI = 0.55, SHATTERED_PUSH = 0.15;

        private final Interpolation interpolation;

        private static double nudge(double res) {
            if (SHATTERED_LO < res && res < SHATTERED_HI) res += SHATTERED_PUSH;
            return res;
        }
    }

    /**
     * Weirdness, interpolated as magnitude and sign separately.
     *
     * <p>Split because the two are read by different consumers: terrain uses only the magnitude, so
     * keeping that smooth preserves landscape shape, while the sign is biome-selection-only and can
     * scatter freely — which is what folds the world into more weirdness bands.
     */
    private static class WeirdnessDensity extends ChannelDensity {

        public WeirdnessDensity(final float scale, final int ch) {
            super(ch, scale * GLOBAL_SCALE_CORRECTION);
            valueInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return Math.abs(
                        FractalTerrainInstance.getBiomeProvider().finalTiles.getValue(mutablePos));
            });
            signInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return Math.signum(
                        FractalTerrainInstance.getBiomeProvider().finalTiles.getValue(mutablePos));
            });
        }

        /** Both interpolations read the same channel, so one window serves the magnitude and the sign —
         *  the 2048-lookup half of the per-chunk cost collapses to a single slice here. */
        @Override
        protected double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            final float px = blockX / pixelScale;
            final float pz = blockZ / pixelScale;
            final float[] data = window.data();
            final int originX = window.originX();
            final int originZ = window.originZ();
            final int rowStride = window.rowStride();
            // Corner-wise abs/signum before the lerp, matching what the two Interpolations do inside
            // their own per-corner source functions.
            final double magnitude = Interpolation.sampleWindowAbs(data, px, pz, originX, originZ, rowStride);
            final double sign = Interpolation.sampleWindowSignum(data, px, pz, originX, originZ, rowStride);
            return (sign >= 0) ? magnitude : -magnitude;
        }

        @Override
        protected double sample(int blockX, int blockZ) {
            if (signInterpolation.interpolateBilinear(blockX, blockZ) >= 0) {
                return valueInterpolation.interpolateBilinear(blockX, blockZ);
            }
            return -valueInterpolation.interpolateBilinear(blockX, blockZ);
        }

        private final Interpolation valueInterpolation;
        private final Interpolation signInterpolation;
    }

    // -------------------------------------------------------------------------
    // Accessors (kept last)
    // -------------------------------------------------------------------------

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return finalTiles;
    }

    /** Raw channel read backing the climate heightmap types. Deliberately uninterpolated — the
     *  heightmap supplies its own, as it does for the relief getters. Clobbers {@code xz[CH]}. */
    private Float biomeChannel(final int[] xz, final int ch) {
        xz[CH] = ch;
        return finalTiles.getValue(xz);
    }

    public Float getVegPdf(int[] xz) {
        xz[CH] = 5;
        return finalTiles.getValue(xz);}

    /** Bilinearly-interpolated weirdness at block {@code (x, z)} (scale-5 sampling of channel 4). */
    @TestOnly
    public double getWeirdness(int x, int z) {
        return weirdnessInterpolation.interpolateBilinear(x, z);
    }

    /** Debug read of the upscaled distance-to-shore, so the visualizer shows what actually drives the
     *  coast biomes. Meaningful only while {@link #DEBUG_DSHORE_CHANNEL_ON}. */
    @TestOnly
    public Float getDistShore(int[] xz) {
        return biomeChannel(xz, DEBUG_DSHORE_CHANNEL);
    }
}
