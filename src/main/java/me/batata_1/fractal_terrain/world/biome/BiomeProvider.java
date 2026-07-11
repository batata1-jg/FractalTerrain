package me.batata_1.fractal_terrain.world.biome;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.*;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Builds the per-tile vanilla biome-parameter field consumed by {@code FractalTerrainBiomeSource}.
 * See {@code worldgeneration101.md} ("Biomes → Overworld Biomes").
 *
 * <p>Vanilla resolves an Overworld biome from six parameters — continentalness, erosion, temperature,
 * humidity (vegetation), weirdness, and depth. The first five are horizontal: {@link ClimateVariableTransform}
 * derives them from the diffusion model's climate + relief channels into a cached 512×512 tile (one channel
 * each, see {@link BiomeChannels}). Depth is vertical and supplied directly from block Y by a
 * {@code yClampedGradient} density function (wiki: depth ≈ 0 at the surface, rising downward), so it is not
 * stored in a tile channel.
 *
 * <p>{@link #sampler} wires these channels into a {@link Climate.Sampler} in vanilla's
 * {@code temperature, humidity, continentalness, erosion, depth, weirdness} order.
 *
 * <p>Class layout: constants → fields → channel enum → constructor → private helpers → per-chunk
 * {@code fill*} producers → nested {@link DensityFunction} implementations → public accessors (last).
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

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final NonIntersectingInfiniteTensor final_tiles;
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
        final_tiles = new NonIntersectingInfiniteTensor(
                path, "final_biome_tiles", new int[] {TILE_CHANNELS, 512, 512}, key -> {
                    int x = key.get(X);
                    int z = key.get(Z);
                    FloatTensor reliefTensor = FractalTerrainInstance.getReliefProvider()
                            .getInfiniteTensor()
                            .getEntry(key);

                    final float[] elev = reliefTensor.copyRange(0, 1 << 18);
                    final float[] grad = reliefTensor.copyRange(4 << 18, 5 << 18);
                    final float[] lowFreqGrad = reliefTensor.copyRange(5 << 18, 6 << 18);
                    final float[] res = reliefTensor.copyRange(6 << 18, 7 << 18);
                    final float[] vegPdf = new float[TILE_PIXELS];
                    final float[] climate = pipeline.getClimate(x, z, elev);
                    final int[] coarseDistShore = computeCoarseDistShore(x, z);
                    // While the visualizer runs, also capture the per-pixel dist-to-shore for the debug channel.
                    final float[] distShoreDebug = DEBUG_DSHORE_CHANNEL_ON ? new float[TILE_PIXELS] : null;
                    final float[] biomeVariables = ClimateVariableTransform.transform(
                            x, z, elev, grad, lowFreqGrad, climate, res, vegPdf, coarseDistShore, distShoreDebug);

                    // Channel layout: [0..4] biome params, [5] river-humidity PDF (reserved, unused by the
                    // sampler), [6] debug dist-to-shore (only while the visualizer runs).
                    final float[] entries = new float[TILE_CHANNELS * TILE_PIXELS];
                    System.arraycopy(biomeVariables, 0, entries, 0, PARAM_CHANNELS * TILE_PIXELS);
                    System.arraycopy(vegPdf, 0, entries, PARAM_CHANNELS * TILE_PIXELS, TILE_PIXELS);
                    if (distShoreDebug != null) {
                        System.arraycopy(distShoreDebug, 0, entries, DEBUG_DSHORE_CHANNEL * TILE_PIXELS, TILE_PIXELS);
                    }
                    // FloatTensor t = new FloatTensor(entries, new int[] {TILE_CHANNELS, 512, 512});
                    //  ;; Debug.seeTile(t, x, z, "final_biomes");
                    return new FloatTensor(entries, new int[] {TILE_CHANNELS, 512, 512});
                });
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
            return final_tiles.getValue(mutablePos);
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Per-coarse-cell distance-to-shore for biome tile {@code (tileX, tileZ)}, as a {@link #DSHORE_GRID}×
     * {@link #DSHORE_GRID} grid (row-major {@code [Xcell*DSHORE_GRID + Zcell]}). The grid spans the tile's
     * {@link #COARSE_CELLS_PER_TILE}×{@link #COARSE_CELLS_PER_TILE} owned coarse cells plus a 1-cell halo on
     * every side, so {@code ClimateVariableTransform} can bilinearly upscale it without falling off the edge.
     *
     * <p>Each value is the Manhattan distance (in coarse cells) from that cell to the nearest ocean cell
     * within a 9×9 window, biased so a cell adjacent to ocean reads {@code 0} (shore): {@code -1} when the
     * cell itself is ocean, {@code 0..7} for increasing land distance, and {@link #SHORE_DIST_NO_OCEAN}
     * when no ocean lies within the window.
     */
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

    /**
     * Distance-to-shore for one coarse cell (1 coarse px) at coarse-pixel {@code (cellCx, cellCz)}. Pulls its
     * own {@code (2·SHORE_SEARCH_RADIUS+1)²} coarse slice centred on the cell — {@code ci0}/{@code cj0} are
     * just {@code cell − SHORE_SEARCH_RADIUS}, with no halo (∓1) correction, since the caller already encodes
     * the halo in the cell coordinate it passes — and returns the Manhattan distance (in coarse cells) to the
     * nearest ocean cell within that 9×9 window, biased so a cell adjacent to ocean reads {@code 0}:
     * {@link #SHORE_DIST_OCEAN} when the cell itself is ocean, {@code 0..7} for increasing land distance, and
     * {@link #SHORE_DIST_NO_OCEAN} when no ocean lies within the window.
     */
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

    /**
     * River-humidity PDF for biome tile {@code (tileX, tileZ)}: for each of the 512×512 pixels, the
     * nearest local-river distance is mapped through an exponential falloff (closer ⇒ more humid).
     * Pixels with no river within the query radius get 0. Indexed {@code ix*512 + iz}, matching the
     * relief/biome flat layout.
     */
    private static float[] riverHumidity(int tileX, int tileZ) {
        final LocalRiverProvider localRivers = FractalTerrainInstance.getLocalRiverProvider();
        final float[] vegPdf = new float[TILE_PIXELS];
        final int blockOriginX = tileX << 9;
        final int blockOriginZ = tileZ << 9;
        for (int ix = 0; ix < 512; ix++) {
            for (int iz = 0; iz < 512; iz++) {
                // final double dist = localRivers.nearestRiverDistance(blockOriginX + ix, blockOriginZ + iz);
                // vegPdf[ix * 512 + iz] = (dist == Double.MAX_VALUE) ? 0f : (float) Math.exp(-dist / HUMIDITY_FALLOFF);
            }
        }
        return vegPdf;
    }

    // -------------------------------------------------------------------------
    // Per-chunk channel producers (consumed by FractalTerrainHeightmap)
    // -------------------------------------------------------------------------

    /** Erosion for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z} (see {@link ErosionDensity}). */
    public float[] fillErosion(ChunkPos pos) {
        final float[] res = new float[1 << 8];
        ((ErosionDensity) erosionDensity).fillArray(res, pos);
        return res;
    }

    /** Weirdness for the 16×16 blocks of {@code pos}, indexed {@code x*16 + z} (see {@link WeirdnessDensity}). */
    public float[] fillWeirdness(ChunkPos pos) {
        final float[] res = new float[1 << 8];
        ((WeirdnessDensity) weirdnessDensity).fillArray(res, pos);
        return res;
    }

    // -------------------------------------------------------------------------
    // Nested density functions
    // -------------------------------------------------------------------------

    /** Plain bilinear interpolation of a stored biome channel. */
    private static class BiomeProviderDensity implements DensityFunction.SimpleFunction {

        private final Interpolation interpolation;

        public BiomeProviderDensity(final float scale, final int ch) {
            interpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                densities[i] = interpolation.interpolateBilinear(pos.blockX(), pos.blockZ());
            }
        }

        @Override
        public double compute(FunctionContext pos) {
            return interpolation.interpolateBilinear(pos.blockX(), pos.blockZ());
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
    }

    /**
     * Erosion density that nudges values out of vanilla's shattered band (erosion level 5,
     * ≈0.45–0.55) until shattered biomes are handled. See {@code worldgeneration101.md}
     * ("Shattered biomes") and {@link ClimateVariableTransform#isShatteredErosion}.
     */
    private static class ErosionDensity implements DensityFunction.SimpleFunction {

        // Shattered (erosion level 5) avoidance band and the push applied to escape it.
        private static final double SHATTERED_LO = 0.44, SHATTERED_HI = 0.55, SHATTERED_PUSH = 0.15;

        private final Interpolation interpolation;

        public ErosionDensity(final float scale, final int ch) {
            interpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        private double sample(int x, int z) {
            double res = interpolation.interpolateBilinear(x, z);
            if (SHATTERED_LO < res && res < SHATTERED_HI) res += SHATTERED_PUSH;
            return res;
        }

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                densities[i] = sample(pos.blockX(), pos.blockZ());
            }
        }

        public void fillArray(float[] erosionMap, ChunkPos pos) {
            final int startX = pos.getMinBlockX();
            final int startZ = pos.getMinBlockZ();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    erosionMap[x * 16 + z] = (float) sample(startX + x, startZ + z);
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
    }

    /**
     * Weirdness density built from <b>two</b> interpolations of the same channel that are combined:
     *
     * <ul>
     *   <li>{@link #valueInterpolation} — the magnitude {@code |weirdness|}, which carries the value;
     *   <li>{@link #signInterpolation} — the {@link Math#signum sign} of the weirdness, which picks the
     *       peaks-or-valleys side.
     * </ul>
     *
     * <p>{@link #sample} returns {@code value × sign}. The terrain reads only the magnitude of weirdness
     * (PV = {@code 1 − |3·|weirdness| − 2|}), so keeping the magnitude smooth preserves the overall
     * landscape shape, while the sign — which only biome selection cares about — scatters the world into
     * more weirdness-folded biome bands.
     */
    private static class WeirdnessDensity implements DensityFunction.SimpleFunction {

        private final Interpolation valueInterpolation;
        private final Interpolation signInterpolation;

        public WeirdnessDensity(final float scale, final int ch) {
            valueInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return Math.abs(
                        FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos));
            });
            signInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return Math.signum(
                        FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos));
            });
        }

        /** Smooth magnitude × rapidly-flipping sign (see the class javadoc). */
        private double sample(int x, int z) {
            if (signInterpolation.interpolateBilinear(x, z) >= 0) return valueInterpolation.interpolateBilinear(x, z);
            return -valueInterpolation.interpolateBilinear(x, z);
        }

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                densities[i] = sample(pos.blockX(), pos.blockZ());
            }
        }

        public void fillArray(float[] weirdnessMap, ChunkPos pos) {
            final int startX = pos.getMinBlockX();
            final int startZ = pos.getMinBlockZ();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    weirdnessMap[x * 16 + z] = (float) sample(startX + x, startZ + z);
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
    }

    // -------------------------------------------------------------------------
    // Accessors (kept last)
    // -------------------------------------------------------------------------

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    /**
     * Raw biome-tile channel read at scaled-grid {@code xz} (reads {@code xz[X]}/{@code xz[Z]}, clobbers
     * {@code xz[CH]}), matching the relief getter contract. These back the climate {@code
     * FractalTerrainHeightmap.Types} entries: the heightmap supplies its own bilinear interpolation around
     * these point samples (just like the relief getters), so no interpolation happens here.
     */
    private Float biomeChannel(final int[] xz, final int ch) {
        xz[CH] = ch;
        return final_tiles.getValue(xz);
    }

    /** Continentalness ({@link BiomeChannels#CONTINENTALNESS}) at scaled-grid {@code xz}. */
    public Float getContinentalness(final int[] xz) {
        return biomeChannel(xz, BiomeChannels.CONTINENTALNESS.channel);
    }

    /** Temperature ({@link BiomeChannels#TEMPERATURE}) at scaled-grid {@code xz}. */
    public Float getTemperature(final int[] xz) {
        return biomeChannel(xz, BiomeChannels.TEMPERATURE.channel);
    }

    /** Vegetation/humidity ({@link BiomeChannels#HUMIDITY}) at scaled-grid {@code xz}. */
    public Float getVegetation(final int[] xz) {
        return biomeChannel(xz, BiomeChannels.HUMIDITY.channel);
    }

    /** Bilinearly-interpolated weirdness at block {@code (x, z)} (scale-5 sampling of channel 4). */
    @TestOnly
    public double getWeirdness(int x, int z) {
        return weirdnessInterpolation.interpolateBilinear(x, z);
    }

    /**
     * Per-pixel distance-to-shore at scaled-grid {@code xz} (reads {@code xz[X]}/{@code xz[Z]}, clobbers
     * {@code xz[CH]}), backing the {@code DIST_SHORE} 3D visualizer
     * ({@link me.batata_1.fractal_terrain.debug.Infinite3DVisualizer}). Reads the {@link #DEBUG_DSHORE_CHANNEL}
     * each tile appends while the visualizer runs — the per-pixel signal {@link ClimateVariableTransform#transform}
     * bilinearly upscales from the coarse grid, i.e. what actually drives the coast biomes. Only meaningful
     * while the visualizer is enabled ({@link #DEBUG_DSHORE_CHANNEL_ON}).
     */
    @TestOnly
    public Float getDistShore(int[] xz) {
        return biomeChannel(xz, DEBUG_DSHORE_CHANNEL);
    }
}
