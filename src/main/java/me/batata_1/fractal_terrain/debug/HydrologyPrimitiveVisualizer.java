package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.math.ds.SpatialIndex;

/**
 * Renders a tile's {@link HydrologicalPrimitive} set to an upscaled color PNG and logs summary stats.
 * Same explicit-{@code debugPath}, no-server pattern as {@link TensorVisualizer} / {@link NoiseVisualizer}.
 *
 * <p>A {@link RiverPrimitive} paints a translucent width-girth disc under its centre point; gaps or
 * sudden girth jumps in the render flag a width/taper bug in the primitive producer.
 */
public class HydrologyPrimitiveVisualizer {

    /** Alpha for the width-girth discs (blended over whatever is already in the pixel). */
    private static final double GIRTH_ALPHA = 0.35;

    public String debugPath;

    public HydrologyPrimitiveVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    /** Collect every primitive of {@code index} and render it (works for any {@link SpatialIndex} payload). */
    public void see(SpatialIndex<HydrologicalPrimitive> index, String name, int gridSize, int upscale) {
        see(index.getAllEntries(), name, gridSize, upscale);
    }

    /** Renders {@code primitives} (tile-local coords) to a PNG; near-border primitives are clipped per pixel,
     *  not dropped, so a girth crossing the tile edge still shows its in-tile part. */
    public void see(List<HydrologicalPrimitive> primitives, String name, int gridSize, int upscale) {
        see(primitives, name, gridSize, upscale, 0, 0);
    }

    /** {@link #see(List, String, int, int)} for primitives in <b>world</b> relief-pixel coords — the frame
     *  {@code LocalRiverProvider} publishes. {@code (originX, originZ)} shifts them into canvas frame. */
    public void see(
            List<HydrologicalPrimitive> primitives,
            String name,
            int gridSize,
            int upscale,
            double originX,
            double originZ) {
        render(primitives, name, gridSize, upscale, originX, originZ, HydrologyPrimitiveVisualizer::colorFor);
    }

    /** Fixed Rosgen palette indexed by {@link RosgenType#ordinal()}; stable so dumps stay comparable across runs. */
    private static final Color[] ROSGEN_PALETTE = {
        new Color(0xFF0059), // A   steep entrenched
        new Color(0xFF7DDE), // Aa  very steep
        new Color(0xFF6A00), // B   moderately entrenched
        new Color(0xFFD500), // C   meandering, broad floodplain
        new Color(0x38FF19), // D   braided
        new Color(0x318323), // DA  anastomosing
        new Color(0x00B2FF), // E   narrow, deep, highly sinuous
        new Color(0x0055FF), // F   entrenched meandering
        new Color(0x7B00FF), // G   entrenched gully
    };

    /** Rendered for a primitive with no Rosgen type: a source, a drain, an oxbow, or an unclassified reach. */
    private static final Color UNCLASSIFIED = new Color(0xFFFFFF);

    /** Colour for a primitive's Rosgen type. Renders {@link #UNCLASSIFIED} instead of coalescing to A like
     *  downstream carving does — this dump shows what was measured, not what will be carved. */
    private static Color rosgenColor(HydrologicalPrimitive primitive) {
        if (!(primitive instanceof RiverPrimitive riverPrimitive) || riverPrimitive.rosgenType() == null)
            return UNCLASSIFIED;
        return ROSGEN_PALETTE[riverPrimitive.rosgenType().ordinal()];
    }

    /** As {@link #see(List, String, int, int)} but colours by Rosgen type: the classification's visual
     *  regression check — expect long runs of one colour, changing at valley transitions. */
    public void seeByRosgenType(List<HydrologicalPrimitive> primitives, String name, int gridSize, int upscale) {
        seeByRosgenType(primitives, name, gridSize, upscale, 0, 0);
    }

    /** {@link #seeByRosgenType(List, String, int, int)} with the world→canvas origin of {@link #see}. */
    public void seeByRosgenType(
            List<HydrologicalPrimitive> primitives,
            String name,
            int gridSize,
            int upscale,
            double originX,
            double originZ) {
        render(primitives, name, gridSize, upscale, originX, originZ, HydrologyPrimitiveVisualizer::rosgenColor);
    }

    /** Renders {@code primitives} with {@code palette} choosing colour, so a primitive's girth disc and its centre
     *  point never show different colours after independent lookups in the two render passes. */
    private void render(
            List<HydrologicalPrimitive> primitives,
            String name,
            int gridSize,
            int upscale,
            double originX,
            double originZ,
            Function<HydrologicalPrimitive, Color> palette) {
        if (gridSize <= 0 || upscale <= 0)
            throw new IllegalArgumentException("gridSize and upscale must be > 0, got " + gridSize + ", " + upscale);
        final int side = gridSize * upscale;
        final BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);

        // Pass 1 — width girths: translucent filled disc of radius width/2 (tile px). Only a River has a
        // width; the position-only feature types contribute their centre point in pass 2 and nothing here.
        for (final HydrologicalPrimitive primitive : primitives) {
            if (!(primitive instanceof RiverPrimitive riverPrimitive)) continue;
            final int rgb = palette.apply(primitive).getRGB();
            final double centerX = (primitive.coord()[0] - originX) * upscale;
            final double centerZ = (primitive.coord()[1] - originZ) * upscale;
            final double radius = riverPrimitive.width() * 0.5 * upscale;
            blendDisc(image, centerX, centerZ, radius, rgb);
        }

        // Pass 2 — primitive points: solid squares in the full-strength color, on top of the girths.
        final int pointHalf = Math.max(1, upscale / 2) / 2; // point square side = max(1, upscale/2)
        for (final HydrologicalPrimitive primitive : primitives) {
            final int rgb = palette.apply(primitive).getRGB();
            final int px = (int) Math.round((primitive.coord()[0] - originX) * upscale);
            final int pz = (int) Math.round((primitive.coord()[1] - originZ) * upscale);
            for (int x = px - pointHalf; x <= px + pointHalf; x++) {
                for (int z = pz - pointHalf; z <= pz + pointHalf; z++) {
                    if (x >= 0 && x < side && z >= 0 && z < side) image.setRGB(x, z, rgb);
                }
            }
        }

        final File dir = new File(debugPath);
        dir.mkdirs();
        final File outputFile = new File(dir, name + ".png");
        try {
            ImageIO.write(image, "png", outputFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DEBUG_LOGGER.info(
                "primitive tree '{}': {} primitives rendered to {} ({}x{} px)",
                name,
                primitives.size(),
                outputFile,
                side,
                side);
    }

    /** Logs per-type point counts and, over the {@link RiverPrimitive} primitives, width/elevation min-mean-max
     *  — a numeric sanity check alongside the visual dump, without opening a PNG. */
    public void logStats(List<HydrologicalPrimitive> primitives, String label) {
        if (primitives.isEmpty()) {
            DEBUG_LOGGER.info("primitive stats [{}]: empty", label);
            return;
        }
        final Map<HydrologicalFeature, Integer> pointsPerType = new HashMap<>();
        int riverCount = 0;
        double widthMin = Double.POSITIVE_INFINITY, widthMax = Double.NEGATIVE_INFINITY, widthSum = 0;
        double elevMin = Double.POSITIVE_INFINITY, elevMax = Double.NEGATIVE_INFINITY;
        for (final HydrologicalPrimitive primitive : primitives) {
            pointsPerType.merge(primitive.getType(), 1, Integer::sum);
            if (!(primitive instanceof RiverPrimitive riverPrimitive)) continue;
            riverCount++;
            widthMin = Math.min(widthMin, riverPrimitive.width());
            widthMax = Math.max(widthMax, riverPrimitive.width());
            widthSum += riverPrimitive.width();
            elevMin = Math.min(elevMin, riverPrimitive.elevation());
            elevMax = Math.max(elevMax, riverPrimitive.elevation());
        }
        if (riverCount == 0) {
            DEBUG_LOGGER.info("primitive stats [{}]: {} points, none of them rivers", label, primitives.size());
        } else {
            DEBUG_LOGGER.info(
                    "primitive stats [{}]: {} points ({} river); width min/mean/max = {}/{}/{}; elevation min/max = {}/{}",
                    label,
                    primitives.size(),
                    riverCount,
                    widthMin,
                    widthSum / riverCount,
                    widthMax,
                    elevMin,
                    elevMax);
        }
        for (final Map.Entry<HydrologicalFeature, Integer> e : pointsPerType.entrySet()) {
            DEBUG_LOGGER.info("primitive stats [{}]:   {} -> {} points", label, e.getKey(), e.getValue());
        }
    }

    /** A primitive's render color: one full-brightness hue per {@link HydrologicalFeature} type. */
    private static Color colorFor(HydrologicalPrimitive primitive) {
        final float hue =
                switch (primitive.getType()) {
                    case RIVER -> 0.60f; // blue
                    case ABANDONED_RIVER -> 0.08f; // orange
                    case OXBOW_LAKE -> 0.85f; // magenta
                    case SOURCE -> 0.33f; // green
                    case DELTA -> 0.00f; // red
                    default -> 0.60f;
                };
        return Color.getHSBColor(hue, 0.9f, 1);
    }

    /** Alpha-blend a filled disc of {@code rgb} at {@link #GIRTH_ALPHA} over the image (manual RGB lerp). */
    private static void blendDisc(BufferedImage image, double centerX, double centerZ, double radius, int rgb) {
        if (radius <= 0) return;
        final int side = image.getWidth();
        final int x0 = Math.max(0, (int) Math.floor(centerX - radius));
        final int x1 = Math.min(side - 1, (int) Math.ceil(centerX + radius));
        final int z0 = Math.max(0, (int) Math.floor(centerZ - radius));
        final int z1 = Math.min(side - 1, (int) Math.ceil(centerZ + radius));
        final double radiusSq = radius * radius;
        final int srcR = (rgb >> 16) & 0xFF;
        final int srcG = (rgb >> 8) & 0xFF;
        final int srcB = rgb & 0xFF;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                final double dx = x - centerX;
                final double dz = z - centerZ;
                if (dx * dx + dz * dz > radiusSq) continue;
                final int dst = image.getRGB(x, z);
                final int r = (int) (((dst >> 16) & 0xFF) * (1 - GIRTH_ALPHA) + srcR * GIRTH_ALPHA);
                final int g = (int) (((dst >> 8) & 0xFF) * (1 - GIRTH_ALPHA) + srcG * GIRTH_ALPHA);
                final int b = (int) ((dst & 0xFF) * (1 - GIRTH_ALPHA) + srcB * GIRTH_ALPHA);
                image.setRGB(x, z, (r << 16) | (g << 8) | b);
            }
        }
    }
}
