package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.getBiomeProvider;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.getReliefProvider;

import java.util.function.Function;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.world.level.ChunkPos;

/**
 * One chunk's worth of relief and climate heightmaps.
 *
 * <p>Exists to amortize sampling: each {@link Types} entry knows how to fill a whole chunk in one pass,
 * so the cache computes once per chunk instead of once per block.
 *
 * <p>{@link Types#ELEVATION} is only raw here — a second pass recomputes it from the full channel set
 * once every other heightmap is filled, since the final shaping needs them all.
 *
 * <p>The payload slot is {@code Object} so channels are not locked to {@code float[]}: a channel may
 * later carry a different element type, or a non-array structure entirely. Each {@link Types} constant
 * owns the interpretation of its own slot via {@link Types#get(Object, int, int)}.
 */
public record FractalTerrainHeightmap(Object[] data) {

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
        VEGETATION_PDF( pos -> fillBilinear(pos,getBiomeProvider()::getVegPdf)),
        CONTINENTALNESS(pos -> fillBilinear(pos, getBiomeProvider()::getContinentalness)),
        EROSION(getBiomeProvider()::fillErosion),
        TEMPERATURE(pos -> fillBilinear(pos, getBiomeProvider()::getTemperature)),
        HUMIDITY(pos -> fillBilinear(pos, getBiomeProvider()::getVegetation)),
        WEIRDNESS(getBiomeProvider()::fillWeirdness),
        // Special (like ELEVATION): zero-filled here, then populated by the second pass
        // (PopulateNoiseStep#fineGrainedPrimitivePass) as carve(x,z) − pre-carve elevation. Negative where the
        // river carved below the original terrain; the surface painter places water there.
        RIVER_DIFFERENCE(pos -> new float[1 << 8]),
        // :SCHEMA: packed by HydrologicalFeature.pack, family in the high word, sub-type in the low; recomputed per
        // chunk, not persisted, so the layout is free to change.
        RIVER_TYPE(pos -> new long[1 << 8]) {
            @Override
            public float get(Object payload, int localX, int localZ) {
                throw new UnsupportedOperationException("RIVER_TYPE is a long[]; read the raw payload");
            }
        },

        WATER_HEIGHT(pos -> new float[1 << 8]),
        ;

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
                            (dx + startingX) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION,
                            (dz + startingZ) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION,
                            mutableCoords,
                            mutableNodes,
                            f);
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
                            (dx + startingX) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION,
                            (dz + startingZ) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION,
                            mutableCoords,
                            mutableNodes,
                            f);
                }
            }
            return heights;
        }

        private final Function<ChunkPos, Object> creator;

        Types(Function<ChunkPos, Object> creator) {
            this.creator = creator;
        }

        /** Builds this heightmap's payload for chunk {@code key} (key = chunk coords). */
        public Function<ChunkPos, Object> creator() {
            return creator;
        }

        /**
         * Reads one block out of this channel's payload.
         *
         * <p>The default reads the payload as the row-major {@code float[256]} the fill helpers
         * produce; constants whose {@link #creator} returns something else override this instead of
         * making callers know the payload type.
         */
        public float get(Object payload, int localX, int localZ) {
            return ((float[]) payload)[localX * 16 + localZ];
        }
    }

    public float get(Types t, int localX, int localZ) {
        return t.get(data[t.ordinal()], localX, localZ);
    }

    /** Raw payload for callers that fill or scan a whole chunk; only valid for {@code float[]} channels. */
    public Object get(Types t) {
        return data[t.ordinal()];
    }
}
