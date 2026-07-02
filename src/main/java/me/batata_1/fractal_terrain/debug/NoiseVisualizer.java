package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.noise.NoiseSampler;

/**
 * Renders a {@link NoiseSampler} (FastNoiseLite, octave Simplex, Voronoi, …) over a square window to a
 * normalised grayscale PNG. Mirrors {@link TensorVisualizer} / {@link SplineVisualizer}: it takes an
 * explicit {@code debugPath} and does <em>not</em> depend on a running server, so it works from the
 * standalone test harnesses too. (Distinct from {@link Debug#seeNoise} which is server-bound and reseeds
 * from the live world.)
 *
 * <p>The sampler is assumed already seeded (call {@link NoiseSampler#init(long)} or
 * {@link NoiseSampler#initSampler(long)} first); {@link #see(NoiseSampler, String, long, int, int, int)}
 * seeds it for you.
 */
public class NoiseVisualizer {

    public String debugPath;

    public NoiseVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    /** Seed {@code sampler} with {@code seed}, then render a {@code size×size} window anchored at the origin. */
    public void see(NoiseSampler sampler, String name, long seed, int x0, int z0, int size) {
        sampler.initSampler(seed);
        see(sampler, name, x0, z0, size);
    }

    /** Render an already-seeded sampler over the window {@code [x0, x0+size) × [z0, z0+size)}. */
    public void see(NoiseSampler sampler, String name, int x0, int z0, int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0, got " + size);

        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        final float[] values = new float[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                final float v = sampler.sample(x0 + i, z0 + j);
                values[i * size + j] = v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        DEBUG_LOGGER.info("noise '{}': window=[{},{})+{} value range=[{},{}]", name, x0, z0, size, min, max);

        final float span = (max - min) + 1e-5F;
        final int[] pixels = new int[size * size];
        for (int k = 0; k < pixels.length; k++) {
            pixels[k] = (int) (255f * (values[k] - min) / span);
        }

        final File dir = new File(debugPath);
        dir.mkdirs();
        final File outputFile = new File(dir, name + ".png");
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        final WritableRaster raster = image.getRaster();
        raster.setSamples(0, 0, size, size, 0, pixels);
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
