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
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit;
import me.batata_1.fractal_terrain.hydrology.features.RiverUnit.RosgenType;
import me.batata_1.fractal_terrain.math.ds.SpatialIndex;

/**
 * Renders a tile's {@link HydrologicalUnit} set to an upscaled color PNG and logs summary stats.
 * Same explicit-{@code debugPath}, no-server pattern as {@link TensorVisualizer} / {@link NoiseVisualizer}.
 *
 * <p>A {@link RiverUnit} paints a translucent width-girth disc under its centre point; gaps or
 * sudden girth jumps in the render flag a width/taper bug in the unit producer.
 */
public class HydrologyUnitVisualizer {

    /** Alpha for the width-girth discs (blended over whatever is already in the pixel). */
    private static final double GIRTH_ALPHA = 0.35;

    public String debugPath;

    public HydrologyUnitVisualizer(String debugPath) {
        this.debugPath = debugPath;
    }

    /** Collect every unit of {@code index} and render it (works for any {@link SpatialIndex} payload). */
    public void see(SpatialIndex<HydrologicalUnit> index, String name, int gridSize, int upscale) {
        see(index.getAllEntries(), name, gridSize, upscale);
    }

    /** Renders {@code units} (tile-local coords) to a PNG; near-border units are clipped per pixel,
     *  not dropped, so a girth crossing the tile edge still shows its in-tile part. */
    public void see(List<HydrologicalUnit> units, String name, int gridSize, int upscale) {
        see(units, name, gridSize, upscale, 0, 0);
    }

    /** {@link #see(List, String, int, int)} for units in <b>world</b> relief-pixel coords — the frame
     *  {@code LocalRiverProvider} publishes. {@code (originX, originZ)} shifts them into canvas frame. */
    public void see(
            List<HydrologicalUnit> units, String name, int gridSize, int upscale, double originX, double originZ) {
        render(units, name, gridSize, upscale, originX, originZ, HydrologyUnitVisualizer::colorFor);
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

    /** Rendered for a unit with no Rosgen type: a source, a drain, an oxbow, or an unclassified reach. */
    private static final Color UNCLASSIFIED = new Color(0xFFFFFF);

    /** Colour for a unit's Rosgen type. Renders {@link #UNCLASSIFIED} instead of coalescing to A like
     *  downstream carving does — this dump shows what was measured, not what will be carved. */
    private static Color rosgenColor(HydrologicalUnit unit) {
        if (!(unit instanceof RiverUnit riverUnit) || riverUnit.rosgenType() == null) return UNCLASSIFIED;
        return ROSGEN_PALETTE[riverUnit.rosgenType().ordinal()];
    }

    /** As {@link #see(List, String, int, int)} but colours by Rosgen type: the classification's visual
     *  regression check — expect long runs of one colour, changing at valley transitions. */
    public void seeByRosgenType(List<HydrologicalUnit> units, String name, int gridSize, int upscale) {
        seeByRosgenType(units, name, gridSize, upscale, 0, 0);
    }

    /** {@link #seeByRosgenType(List, String, int, int)} with the world→canvas origin of {@link #see}. */
    public void seeByRosgenType(
            List<HydrologicalUnit> units, String name, int gridSize, int upscale, double originX, double originZ) {
        render(units, name, gridSize, upscale, originX, originZ, HydrologyUnitVisualizer::rosgenColor);
    }

    /** Renders {@code units} with {@code palette} choosing colour, so a unit's girth disc and its centre
     *  point never show different colours after independent lookups in the two render passes. */
    private void render(
            List<HydrologicalUnit> units,
            String name,
            int gridSize,
            int upscale,
            double originX,
            double originZ,
            Function<HydrologicalUnit, Color> palette) {
        if (gridSize <= 0 || upscale <= 0)
            throw new IllegalArgumentException("gridSize and upscale must be > 0, got " + gridSize + ", " + upscale);
        final int side = gridSize * upscale;
        final BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);

        // Pass 1 — width girths: translucent filled disc of radius width/2 (tile px). Only a River has a
        // width; the position-only feature types contribute their centre point in pass 2 and nothing here.
        for (final HydrologicalUnit unit : units) {
            if (!(unit instanceof RiverUnit riverUnit)) continue;
            final int rgb = palette.apply(unit).getRGB();
            final double centerX = (unit.coord()[0] - originX) * upscale;
            final double centerZ = (unit.coord()[1] - originZ) * upscale;
            final double radius = riverUnit.width() * 0.5 * upscale;
            blendDisc(image, centerX, centerZ, radius, rgb);
        }

        // Pass 2 — unit points: solid squares in the full-strength color, on top of the girths.
        final int pointHalf = Math.max(1, upscale / 2) / 2; // point square side = max(1, upscale/2)
        for (final HydrologicalUnit unit : units) {
            final int rgb = palette.apply(unit).getRGB();
            final int px = (int) Math.round((unit.coord()[0] - originX) * upscale);
            final int pz = (int) Math.round((unit.coord()[1] - originZ) * upscale);
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
                "unit tree '{}': {} units rendered to {} ({}x{} px)", name, units.size(), outputFile, side, side);
    }

    /** Logs per-type point counts and, over the {@link RiverUnit} units, width/elevation min-mean-max
     *  — a numeric sanity check alongside the visual dump, without opening a PNG. */
    public void logStats(List<HydrologicalUnit> units, String label) {
        if (units.isEmpty()) {
            DEBUG_LOGGER.info("unit stats [{}]: empty", label);
            return;
        }
        final Map<HydrologicalFeature, Integer> pointsPerType = new HashMap<>();
        int riverCount = 0;
        double widthMin = Double.POSITIVE_INFINITY, widthMax = Double.NEGATIVE_INFINITY, widthSum = 0;
        double elevMin = Double.POSITIVE_INFINITY, elevMax = Double.NEGATIVE_INFINITY;
        for (final HydrologicalUnit unit : units) {
            pointsPerType.merge(unit.getType(), 1, Integer::sum);
            if (!(unit instanceof RiverUnit riverUnit)) continue;
            riverCount++;
            widthMin = Math.min(widthMin, riverUnit.width());
            widthMax = Math.max(widthMax, riverUnit.width());
            widthSum += riverUnit.width();
            elevMin = Math.min(elevMin, riverUnit.elevation());
            elevMax = Math.max(elevMax, riverUnit.elevation());
        }
        if (riverCount == 0) {
            DEBUG_LOGGER.info("unit stats [{}]: {} points, none of them rivers", label, units.size());
        } else {
            DEBUG_LOGGER.info(
                    "unit stats [{}]: {} points ({} river); width min/mean/max = {}/{}/{}; elevation min/max = {}/{}",
                    label,
                    units.size(),
                    riverCount,
                    widthMin,
                    widthSum / riverCount,
                    widthMax,
                    elevMin,
                    elevMax);
        }
        for (final Map.Entry<HydrologicalFeature, Integer> e : pointsPerType.entrySet()) {
            DEBUG_LOGGER.info("unit stats [{}]:   {} -> {} points", label, e.getKey(), e.getValue());
        }
    }

    /** A unit's render color: one full-brightness hue per {@link HydrologicalFeature} type. */
    private static Color colorFor(HydrologicalUnit unit) {
        final float hue =
                switch (unit.getType()) {
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
