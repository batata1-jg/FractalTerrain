package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.getBiomeProvider;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.getReliefProvider;

import java.util.function.Function;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.world.level.ChunkPos;

/**
 * One chunk's worth of relief/climate heightmaps, each a flat {@code float[256]} indexed
 * {@code localX * 16 + localZ} (matching the rest of the generator's chunk-local layout).
 *
 * <p>Each {@link Types} entry names a heightmap kind and bundles the per-pixel source channel getter
 * with the {@link Interpolation} sampler that turns that channel into a per-block value. The enum
 * composes these into a {@code Function<ChunkPos, float[]>} that fills a whole chunk in one pass, so
 * {@link FractalTerrainHeightmapCache} only has to ask each type for its array. Values are produced
 * through the same {@link Interpolation} machinery (at the same {@code SCALE}) the old
 * {@code ReliefAccessor} used, so they match the previous code exactly — only the <em>when/where</em>
 * (compute-once-per-chunk, cached) changed.
 *
 * <p>{@link Types#ELEVATION} is special: the {@code creator} only produces the <em>raw</em>
 * smoothstep-interpolated relief elevation. After every heightmap is filled,
 * {@link FractalTerrainHeightmapCache} runs a second pass
 * ({@link me.batata_1.fractal_terrain.world.gen.populatenoise.PopulateNoiseStep#finalElev}) that
 * recomputes ELEVATION from the full set of channels (gradients, residual, biome parameters), applying
 * the final terrain tweaks (biome-aware shaping, ocean-height correction, bottomY clamp).
 */
public record FractalTerrainHeightmap(float[][] data) {

    /**
     * The heightmap kinds. Each carries its per-pixel source channel (a {@code Function<int[], Float>}
     * over {@code [ch, x, z]}, matching the relief/biome getter contract) composed into the
     * chunk-filling {@link #creator}.
     *
     * <p>Relief channels (elevation, gradients, residual) read the {@code ReliefProvider} getters; the
     * climate channels (continentalness…weirdness) read the matching {@code BiomeProvider} getters. The
     * climate/gradient channels are point-sampled and bilinearly upscaled via {@link #fillBilinear};
     * {@link #ELEVATION} uses smoothstep ({@link #fillSmoothStep}) to match the legacy {@code getHeight}.
     */
    public enum Types {
        ELEVATION(pos -> fillSmoothStep(pos, getReliefProvider()::getElev)),
        REFINED_GRAD(pos -> fillBilinear(pos, getReliefProvider()::getRefinedGrad)),
        RES(pos -> fillBilinear(pos, getReliefProvider()::getRes)),
        BLURRED_ELEV(pos -> fillBilinear(pos, getReliefProvider()::getBlurredElev)),
        GRAD_X(pos -> fillBilinear(pos, getReliefProvider()::getGradX)),
        GRAD_Y(pos -> fillBilinear(pos, getReliefProvider()::getGradY)),
        CONTINENTALNESS(pos -> fillBilinear(pos, getBiomeProvider()::getContinentalness)),
        EROSION(getBiomeProvider()::fillErosion),
        TEMPERATURE(pos -> fillBilinear(pos, getBiomeProvider()::getTemperature)),
        VEGETATION(pos -> fillBilinear(pos, getBiomeProvider()::getVegetation)),
        WEIRDNESS(getBiomeProvider()::fillWeirdness);

        private static float[] fillBilinear(ChunkPos chunkPos, Function<int[], Float> f) {
            final float[] heights = new float[1 << 8];
            final int startingX = chunkPos.getMinBlockX();
            final int startingZ = chunkPos.getMinBlockZ();
            final int[] mutableCoords = new int[3];
            final float[] mutableNodes = new float[4];
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    mutableCoords[0] = dx + startingX;
                    mutableCoords[1] = dz + startingZ;
                    heights[(dx << 4) + dz] = (float) Interpolation.interpolateBilinear(
                            (dx + startingX) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION, (dz + startingZ) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION, mutableCoords, mutableNodes, f);
                }
            }
            return heights;
        }

        private static float[] fillSmoothStep(ChunkPos chunkPos, Function<int[], Float> f) {
            final float[] heights = new float[1 << 8];
            final int startingX = chunkPos.getMinBlockX();
            final int startingZ = chunkPos.getMinBlockZ();
            final int[] mutableCoords = new int[3];
            final float[] mutableNodes = new float[4];
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    heights[(dx << 4) + dz] = (float) Interpolation.interpolateSmoothStep(
                            (dx + startingX) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION, (dz + startingZ) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION, mutableCoords, mutableNodes, f);
                }
            }
            return heights;
        }

        private final Function<ChunkPos, float[]> creator;

        Types(Function<ChunkPos, float[]> creator) {
            this.creator = creator;
        }

        /** Builds this heightmap's {@code float[256]} for chunk {@code key} (key = chunk coords). */
        public Function<ChunkPos, float[]> creator() {
            return creator;
        }
    }

    public float get(Types t, int localX, int localZ) {
        return data[t.ordinal()][localX * 16 + localZ];
    }

    public float[] get(Types t) {
        return data[t.ordinal()];
    }
}
