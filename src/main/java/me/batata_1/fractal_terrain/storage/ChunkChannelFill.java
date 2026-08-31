package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;

import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.world.level.ChunkPos;

/**
 * One tile channel's pixel window for a block rectangle, plus the upscale loops that read it.
 *
 * <p>Exists because a chunk spans only 3.2 tensor pixels: the whole working set for a channel is a
 * 4x4 or 5x5 window, so one slice replaces the 1024 cached lookups the four-corner per-pixel path
 * costs. Both {@code ReliefProvider} and {@code BiomeProvider} fill their heightmap channels through
 * here, and the biome density functions reuse {@link #open} for their own compositions.
 */
public final class ChunkChannelFill {

    private ChunkChannelFill() {}

    /**
     * A slice of one channel, addressed in global tensor-pixel coordinates.
     *
     * <p>Held as a raw array rather than a {@link FloatTensor} so the 256-sample loop indexes it
     * without a per-read accessor.
     */
    public record ChunkWindow(float[] data, int originX, int originZ, int rowStride) {}

    /**
     * Slices {@code channel} over the pixels a block rectangle needs; both bounds are inclusive.
     * {@code pixelScale} is the blocks-per-pixel divisor the caller will sample with — it must match,
     * or the window will not cover the corners the sampler reads.
     */
    public static ChunkWindow open(
            NonIntersectingInfiniteTensor tiles,
            int channel,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ,
            float pixelScale) {
        // floor/ceil, never floor/floor+1: on an exact pixel the two corners coincide, and reading one
        // pixel further would cross a tile boundary and materialise a whole tile for a zero weight.
        final int originX = (int) Math.floor(minBlockX / pixelScale);
        final int originZ = (int) Math.floor(minBlockZ / pixelScale);
        final int lastX = (int) Math.ceil(maxBlockX / pixelScale);
        final int lastZ = (int) Math.ceil(maxBlockZ / pixelScale);
        final FloatTensor slice =
                tiles.getSlice(new int[] {channel, originX, originZ}, new int[] {channel + 1, lastX + 1, lastZ + 1});
        // :PERF: raw backing array; getSlice allocates this tensor fresh and never publishes it to a
        // cache, so it is unfrozen and outside the freeze invariant infinitetensor/README.md states.
        return new ChunkWindow(slice.dataUnsafe(), originX, originZ, lastZ - originZ + 1);
    }

    /** {@link #open} at the heightmap's own scale, the one every relief/climate channel uses. */
    public static ChunkWindow open(
            NonIntersectingInfiniteTensor tiles,
            int channel,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ) {
        return open(tiles, channel, minBlockX, minBlockZ, maxBlockX, maxBlockZ, GLOBAL_SCALE_CORRECTION);
    }

    /** {@link #open} for a whole chunk. */
    public static ChunkWindow open(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos) {
        final int startX = pos.getMinBlockX();
        final int startZ = pos.getMinBlockZ();
        return open(tiles, channel, startX, startZ, startX + 15, startZ + 15);
    }

    /** Bilinear upscale of {@code channel} over one chunk, indexed {@code localX * 16 + localZ}. */
    public static float[] fillBilinear(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos) {
        final int startX = pos.getMinBlockX();
        final int startZ = pos.getMinBlockZ();
        final ChunkWindow window = open(tiles, channel, pos);
        final float[] data = window.data();
        final int originX = window.originX();
        final int originZ = window.originZ();
        final int rowStride = window.rowStride();
        final float[] out = new float[1 << 8];
        for (int dx = 0; dx < 16; dx++) {
            final float px = (dx + startX) / GLOBAL_SCALE_CORRECTION;
            for (int dz = 0; dz < 16; dz++) {
                final float pz = (dz + startZ) / GLOBAL_SCALE_CORRECTION;
                out[(dx << 4) + dz] =
                        (float) Interpolation.sampleWindowBilinear(data, px, pz, originX, originZ, rowStride);
            }
        }
        return out;
    }

    /** Smoothstep counterpart to {@link #fillBilinear}; the elevation channel uses this one. */
    public static float[] fillSmoothStep(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos) {
        final int startX = pos.getMinBlockX();
        final int startZ = pos.getMinBlockZ();
        final ChunkWindow window = open(tiles, channel, pos);
        final float[] data = window.data();
        final int originX = window.originX();
        final int originZ = window.originZ();
        final int rowStride = window.rowStride();
        final float[] out = new float[1 << 8];
        for (int dx = 0; dx < 16; dx++) {
            final float px = (dx + startX) / GLOBAL_SCALE_CORRECTION;
            for (int dz = 0; dz < 16; dz++) {
                final float pz = (dz + startZ) / GLOBAL_SCALE_CORRECTION;
                out[(dx << 4) + dz] =
                        (float) Interpolation.sampleWindowSmoothStep(data, px, pz, originX, originZ, rowStride);
            }
        }
        return out;
    }
}
