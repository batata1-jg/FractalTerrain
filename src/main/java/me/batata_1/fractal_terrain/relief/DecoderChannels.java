package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;

import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;

/**
 * Shared decode of the diffusion decoder slice into weight-normalized base channels. Both
 * {@link ReliefProvider} (for the relief channels + the residual DoG) and
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider} (for the elevation/gradients it
 * traces and carves) need this; keeping it here, as a stateless static helper over the cached decoder
 * slice, avoids an instance dependency cycle between the two providers.
 */
public final class DecoderChannels {

    /** Relief base channel count (decoder channels 1..7, weight-normalized). */
    public static final int BASE_CHANNELS = DECODER_CHANNELS - 1; // 7
    /** Relief tile side in native px. */
    public static final int INNER = 512;

    private DecoderChannels() {}

    /** The pipeline's entry into hydrology: a haloed, weight-normalized decoder slice.
     *  Normalizing here means no downstream stage has to know about the blend weight. */
    public static float[][] decode(int tileX, int tileZ, int pad) {
        final int padded = INNER + 2 * pad;
        final FloatTensor slice = pipeline.getDecoderSlice(
                (tileX << 9) - pad, (tileZ << 9) - pad, ((tileX + 1) << 9) + pad, ((tileZ + 1) << 9) + pad);
        final int pixelCount = padded * padded;
        final float[][] base = new float[BASE_CHANNELS][pixelCount];
        for (int px = 0; px < pixelCount; px++) {
            final float weight = slice.get(px);
            final float inverse = (weight > 1e-6f) ? 1f / weight : 0f;
            for (int c = 1; c < DECODER_CHANNELS; c++) {
                base[c - 1][px] = slice.get(c * pixelCount + px) * inverse;
            }
        }
        return base;
    }
}
