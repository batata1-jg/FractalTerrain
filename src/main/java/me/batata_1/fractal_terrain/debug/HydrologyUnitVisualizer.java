package me.batata_1.fractal_terrain.debug;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.math.ds.SpatialIndex;

/**
 * Renders the {@link HydrologicalUnit} set of one relief tile (e.g. the {@code ImmutableRTree} built
 * by {@code LocalRiverProvider}) to an upscaled color PNG, and logs summary statistics. Mirrors
 * {@link TensorVisualizer} / {@link NoiseVisualizer}: explicit {@code debugPath}, no running-server
 * dependency, so it works from the standalone harnesses.
 *
 * <p><b>Reading the image:</b> each {@link HydrologicalFeature} type has its own base hue (RIVER =
 * blue, ABANDONED_RIVER = orange, OXBOW_LAKE = magenta); different feature {@code id}s of the same
 * type get different brightness shades (golden-ratio hash, so consecutive ids are clearly distinct).
 * Every unit first paints its <em>width girth</em> — a translucent filled disc of radius
 * {@code width/2} (in tile px, scaled by {@code upscale}) — then its centre point in the full-strength
 * color, so channel centrelines stay visible on top of the girth tubes. Correct widths appear as
 * continuous tubes: the producers resample at {@code dx ≤ width/2}, so consecutive discs must overlap —
 * gaps or sudden girth jumps indicate a width/taper bug.
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

    /**
     * Render {@code units} (tile-local relief-pixel coords) onto a {@code (gridSize·upscale)²} RGB PNG at
     * {@code {debugPath}/{name}.png}. Units outside {@code [0, gridSize)} are clipped per pixel, not
     * dropped, so girths of near-border units still show their in-tile part.
     */
    public void see(List<HydrologicalUnit> units, String name, int gridSize, int upscale) {
        if (gridSize <= 0 || upscale <= 0)
            throw new IllegalArgumentException("gridSize and upscale must be > 0, got " + gridSize + ", " + upscale);
        final int side = gridSize * upscale;
        final BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);

        // Pass 1 — width girths: translucent filled disc of radius width/2 (tile px) per unit.
        for (final HydrologicalUnit unit : units) {
            final int rgb = colorFor(unit).getRGB();
            final double centerX = unit.coord()[0] * upscale;
            final double centerZ = unit.coord()[1] * upscale;
            final double radius = unit.width() * 0.5 * upscale;
            blendDisc(image, centerX, centerZ, radius, rgb);
        }

        // Pass 2 — unit points: solid squares in the full-strength color, on top of the girths.
        final int pointHalf = Math.max(1, upscale / 2) / 2; // point square side = max(1, upscale/2)
        for (final HydrologicalUnit unit : units) {
            final int rgb = colorFor(unit).getRGB();
            final int px = (int) Math.round(unit.coord()[0] * upscale);
            final int pz = (int) Math.round(unit.coord()[1] * upscale);
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
            throw new RuntimeException(e);
        }
        DEBUG_LOGGER.info(
                "unit tree '{}': {} units rendered to {} ({}x{} px)", name, units.size(), outputFile, side, side);
    }

    /**
     * Log summary statistics for {@code units}: total points, distinct feature ids, per-type feature/point
     * counts, width min/mean/max, elevation min/max, and points-per-feature min/mean/max.
     */
    public void logStats(List<HydrologicalUnit> units, String label) {
        if (units.isEmpty()) {
            DEBUG_LOGGER.info("unit stats [{}]: empty", label);
            return;
        }
        final Map<Integer, Integer> pointsPerFeature = new HashMap<>();
        final Map<HydrologicalFeature, Integer> pointsPerType = new HashMap<>();
        final Map<HydrologicalFeature, Map<Integer, Boolean>> featuresPerType = new HashMap<>();
        double widthMin = Double.POSITIVE_INFINITY, widthMax = Double.NEGATIVE_INFINITY, widthSum = 0;
        double elevMin = Double.POSITIVE_INFINITY, elevMax = Double.NEGATIVE_INFINITY;
        for (final HydrologicalUnit unit : units) {
            pointsPerFeature.merge(unit.id(), 1, Integer::sum);
            pointsPerType.merge(unit.type(), 1, Integer::sum);
            featuresPerType.computeIfAbsent(unit.type(), t -> new HashMap<>()).put(unit.id(), Boolean.TRUE);
            widthMin = Math.min(widthMin, unit.width());
            widthMax = Math.max(widthMax, unit.width());
            widthSum += unit.width();
            elevMin = Math.min(elevMin, unit.elevation());
            elevMax = Math.max(elevMax, unit.elevation());
        }
        int perFeatureMin = Integer.MAX_VALUE, perFeatureMax = 0;
        for (final int n : pointsPerFeature.values()) {
            perFeatureMin = Math.min(perFeatureMin, n);
            perFeatureMax = Math.max(perFeatureMax, n);
        }
        DEBUG_LOGGER.info(
                "unit stats [{}]: {} points across {} features; width min/mean/max = {}/{}/{}; elevation min/max = {}/{}; points-per-feature min/mean/max = {}/{}/{}",
                label,
                units.size(),
                pointsPerFeature.size(),
                widthMin,
                widthSum / units.size(),
                widthMax,
                elevMin,
                elevMax,
                perFeatureMin,
                (double) units.size() / pointsPerFeature.size(),
                perFeatureMax);
        for (final Map.Entry<HydrologicalFeature, Integer> e : pointsPerType.entrySet()) {
            DEBUG_LOGGER.info(
                    "unit stats [{}]:   {} -> {} features, {} points",
                    label,
                    e.getKey(),
                    featuresPerType.get(e.getKey()).size(),
                    e.getValue());
        }
    }

    /**
     * A unit's render color: base hue by {@link HydrologicalFeature} type, brightness varied per feature
     * {@code id} via a golden-ratio hash so consecutive ids land far apart in [0.55, 1.0).
     */
    private static Color colorFor(HydrologicalUnit unit) {
        final float hue =
                switch (unit.type()) {
                    case RIVER -> 0.60f; // blue
                    case ABANDONED_RIVER -> 0.08f; // orange
                    case OXBOW_LAKE -> 0.85f; // magenta
                };
        final double idHash = (unit.id() * 0.6180339887498949) % 1.0;
        final float brightness = (float) (0.55 + 0.45 * idHash);
        return Color.getHSBColor(hue, 0.9f, brightness);
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
