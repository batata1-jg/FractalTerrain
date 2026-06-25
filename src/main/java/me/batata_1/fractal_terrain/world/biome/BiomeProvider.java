package me.batata_1.fractal_terrain.world.biome;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.*;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.NotNull;

public class BiomeProvider {

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

    /** Biome-tile channel holding weirdness (matches the sampler wiring below: {@code T H C E D W}). */
    private static final int WEIRDNESS_CHANNEL = 4;

    private final NonIntersectingInfiniteTensor final_tiles;
    public final Climate.Sampler sampler;

    /** Scale-5 bilinear sampler over the weirdness channel; backs {@link #getWeirdness(int, int)}. */
    private final Interpolation weirdnessInterpolation;

    private enum BiomeChannels{
        TEMPERATURE(2),
        HUMIDITY(3),
        CONTINENTALNESS(0),
        EROSION(1),
        DEPTH(-1),
        WEIRDNESS(4);
        final int val;
        private BiomeChannels(int val) {this.val = val;}
    }


    public BiomeProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path, "final_biome_tiles", new int[] {BIOME_CHANNELS, 512, 512}, key -> {
                    int x = key.get(X);
                    int z = key.get(Z);
                    FloatTensor reliefTensor = FractalTerrainInstance.getReliefProvider()
                            .getInfiniteTensor()
                            .getEntry(key);

                    final float[] elev = Arrays.copyOfRange(reliefTensor.data, 0, 1 << 18);
                    final float[] grad = Arrays.copyOfRange(reliefTensor.data, 4 << 18, 5 << 18);
                    final float[] lowFreqGrad = Arrays.copyOfRange(reliefTensor.data, 5 << 18, 6 << 18);
                    final float[] res = Arrays.copyOfRange(reliefTensor.data, 6 << 18, 7 << 18);
                    final float[] vegPdf = new float[512 * 512];
                    final float[] climate = pipeline.getClimate(x, z, elev);
                    final int[] coarseDistShore = computeCoarseDistShore(x, z);
                    final float[] biomeVariables = ClimateVariableTransform.transform(
                            x, z, elev, grad, lowFreqGrad, climate, res, vegPdf, coarseDistShore);

                    // Channels 0..4 from the climate transform; channel 5 = river-humidity PDF (reserved,
                    // not read by the sampler yet).
                    final float[] entries = new float[BIOME_CHANNELS * TILE_PIXELS];
                    System.arraycopy(biomeVariables, 0, entries, 0, 5 * TILE_PIXELS);
                    System.arraycopy(vegPdf, 0, entries, 5 * TILE_PIXELS, TILE_PIXELS);

                    FloatTensor t = new FloatTensor(entries, new int[] {BIOME_CHANNELS, 512, 512});

                  //  Debug.seeTile(t, x, z, "final_biomes");
                    return t;
                });
        // T H C E D W SpawnTarget
        final float scale = 1;
        sampler = new Climate.Sampler(
                new BiomeProviderDensity(scale, BiomeChannels.TEMPERATURE.val),
                new BiomeProviderDensity(scale, BiomeChannels.HUMIDITY.val),
                new BiomeProviderDensity(scale, BiomeChannels.CONTINENTALNESS.val),
                new ErosionDensity(scale, BiomeChannels.EROSION.val),
                DensityFunctions.yClampedGradient(-64, 63, -1, 0),
                new BiomeProviderDensity(scale, BiomeChannels.WEIRDNESS.val),
                List.of());

        weirdnessInterpolation = new Interpolation(scale * 5, mutablePos -> {
            mutablePos[CH] = WEIRDNESS_CHANNEL;
            return final_tiles.getValue(mutablePos);
        });
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

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

        // Elevation slice must cover each grid cell's full search window: grid spans [tileC-1, tileC+3),
        // each cell reaches ±SHORE_SEARCH_RADIUS ⇒ slice spans [tileC-1-R, tileC+3+R) = sliceSide cells.
        final int sliceSide = DSHORE_GRID + 2 * SHORE_SEARCH_RADIUS;
        final int ci0 = tileCx - 1 - SHORE_SEARCH_RADIUS;
        final int cj0 = tileCz - 1 - SHORE_SEARCH_RADIUS;
        final FloatTensor slice = pipeline.getCoarseSlice(ci0, cj0, ci0 + sliceSide, cj0 + sliceSide);

        // Weight-normalize coarse elevation (channel 0 / channel 6); ocean ⇒ value < 0.
        final int cells = sliceSide * sliceSide;
        final float[] elev = new float[cells];
        for (int px = 0; px < cells; px++) {
            final float w = slice.data[6 * cells + px];
            elev[px] = (w > COARSE_WEIGHT_EPS) ? slice.data[px] / w : 0f;
        }

        // Offset from the slice origin to the grid origin (the slice adds R cells of search halo).
        final int gridOffset = SHORE_SEARCH_RADIUS;
        final int[] distShore = new int[DSHORE_GRID * DSHORE_GRID];
        for (int gx = 0; gx < DSHORE_GRID; gx++) {
            for (int gz = 0; gz < DSHORE_GRID; gz++) {
                final int si = gx + gridOffset;
                final int sj = gz + gridOffset;
                int dist;
                if (elev[si * sliceSide + sj] < 0f) {
                    dist = SHORE_DIST_OCEAN;
                } else {
                    int minManhattan = Integer.MAX_VALUE;
                    for (int dx = -SHORE_SEARCH_RADIUS; dx <= SHORE_SEARCH_RADIUS; dx++) {
                        for (int dz = -SHORE_SEARCH_RADIUS; dz <= SHORE_SEARCH_RADIUS; dz++) {
                            if (elev[(si + dx) * sliceSide + (sj + dz)] < 0f) {
                                minManhattan = Math.min(minManhattan, Math.abs(dx) + Math.abs(dz));
                            }
                        }
                    }
                    // Adjacent ocean (Manhattan 1) ⇒ shore 0; corner ocean (Manhattan 8) ⇒ 7.
                    dist = (minManhattan == Integer.MAX_VALUE) ? SHORE_DIST_NO_OCEAN : minManhattan - 1;
                }
                distShore[gx * DSHORE_GRID + gz] = dist;
            }
        }
        return distShore;
    }

    /** Bilinearly-interpolated weirdness at block {@code (x, z)} (scale-5 sampling of channel 4). */
    public double getWeirdness(int x, int z) {
        return weirdnessInterpolation.interpolateBilinear(x, z);
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

    private static class BiomeProviderDensity implements DensityFunction.SimpleFunction {

        private final Interpolation interpolation;

        public BiomeProviderDensity(final float scale, final int ch) {
            interpolation = new Interpolation(scale * 5, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                final int x = pos.blockX();
                final int z = pos.blockZ();
                densities[i] = interpolation.interpolateBilinear(x, z);
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


    //TODO: handle E5
    private static class ErosionDensity implements DensityFunction.SimpleFunction {

        private final Interpolation interpolation;

        public ErosionDensity(final float scale, final int ch) {
            interpolation = new Interpolation(scale * 5, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                final int x = pos.blockX();
                final int z = pos.blockZ();
                densities[i] = interpolation.interpolateBilinear(x, z);
                if(0.44<densities[i]&&densities[i]<0.55) densities[i] += 0.15;
            }
        }

        @Override
        public double compute(FunctionContext pos) {
            double densities = interpolation.interpolateBilinear(pos.blockX(), pos.blockZ());
            if(0.44<densities&&densities<0.55) densities += 0.15;
            return densities;
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
}
