package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.FractalTerrainInstance.getBiomeProvider;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.getReliefProvider;

import java.util.function.Function;
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
     * The heightmap kinds. Each carries the provider call that fills a whole chunk's channel in one
     * pass, so the cache computes once per chunk rather than once per block.
     *
     * <p>Relief channels (elevation, gradients, residual) come from {@code ReliefProvider}, the climate
     * channels from {@code BiomeProvider}; each provider takes one tensor slice over the chunk's ~5x5
     * pixel window and upscales it. Each entry resolves its provider per call rather than capturing one,
     * because a world reload replaces the {@code GenerationContext} the providers live in.
     */
    public enum Types {
        ELEVATION(pos -> getReliefProvider().fillElev(pos)),
        REFINED_GRAD(pos -> getReliefProvider().fillRefinedGrad(pos)),
        // TODO:create fill veg pdf
        VEGETATION_PDF(pos -> new float[1 << 18]),
        CONTINENTALNESS(pos -> getBiomeProvider().fillContinentalness(pos)),
        EROSION(pos -> getBiomeProvider().fillErosion(pos)),
        TEMPERATURE(pos -> getBiomeProvider().fillTemperature(pos)),
        HUMIDITY(pos -> getBiomeProvider().fillVegetation(pos)),
        WEIRDNESS(pos -> getBiomeProvider().fillWeirdness(pos)),
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
