package me.batata_1.fractal_terrain.hydrology.rosgen;

import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * The raster side of Rosgen classification: measures slope and entrenchment for one reach.
 *
 * <p><b>The elevation field must be raw decoded terrain, never a carved buffer.</b> The carve creates
 * the floodplain, so measuring on its output measures the carve's own tuning constants.
 *
 * <p>A transect strides across rows and gets no cache locality, so callers must transect once per reach
 * rather than per spline point — {@link ReachRosgenClassifier} owns that.
 */
public final class ReachMetricsSampler {

    private final float[] elev;
    private final int gridSize;

    /** @param elev raw decoded elevation — never a carved buffer; see the class doc */
    public ReachMetricsSampler(float[] elev, int gridSize) {
        if (elev.length != gridSize * gridSize) {
            throw new IllegalArgumentException("elev length " + elev.length + " != gridSize² " + (gridSize * gridSize));
        }
        this.elev = elev;
        this.gridSize = gridSize;
    }

    /** Bed slope over a reach, floored at 0. Beds are monotone non-increasing downstream, so an uphill
     *  reach means degenerate geometry, and flooring keeps it out of the steep type branches. */
    public double slope(List<double[]> pts, double arcLength, int fromIndex, int toIndex) {
        if (arcLength <= 0.0) return 0.0;
        final double drop = elevAt(pts.get(fromIndex)) - elevAt(pts.get(toIndex));
        return Math.max(0.0, drop / arcLength) / HydrologyTuning.RIVER_SLOPE_RESCALE;
    }

    public double elevAt(double[] sample) {
        return Interpolation.sampleBilinear(elev, sample[0], sample[1], gridSize);
    }

    /** Entrenchment ratio: the field method transcribed to a raster. A saturating transect returns
     *  {@code +inf}, which is the correct reading for a broad flat valley rather than a failure.
     *  Walk bound and step floor are tuned in {@code config/README.md}. */
    public double entrenchmentRatio(double[] point, double[] normal, double bedElev, double width) {
        final double bankfullWidth = Math.max(width, HydrologyTuning.MIN_WIDTH);
        final double depth = HydrologyTuning.DEPTH_MAX_FACTOR * ChannelGeometry.depth(bankfullWidth);
        final double floodProneStage = bedElev + 2.0 * depth;
        final double bankfullWidthFactor = Math.pow(bankfullWidth, 0.05) + HydrologyTuning.ENTRENTMENT_RATIO_BIAS;
        final double maxFloodPlainWidth = HydrologyTuning.ER_WALK_WIDTHS * bankfullWidthFactor;
        final double step = Math.clamp(
                bankfullWidth * HydrologyTuning.ER_STEP_WIDTH_FRACTION,
                HydrologyTuning.ER_STEP_MIN,
                maxFloodPlainWidth / HydrologyTuning.ER_MIN_STEPS_PER_SIDE);

        final double positive = halfWidth(point, normal, +1.0, floodProneStage, maxFloodPlainWidth, step);
        final double negative = halfWidth(point, normal, -1.0, floodProneStage, maxFloodPlainWidth, step);
        if (Double.isInfinite(positive) && Double.isInfinite(negative)) return Double.POSITIVE_INFINITY;

        final double positiveWidth = Double.isInfinite(positive) ? maxFloodPlainWidth : positive;
        final double negativeWidth = Double.isInfinite(negative) ? maxFloodPlainWidth : negative;
        return (positiveWidth + negativeWidth) / bankfullWidthFactor;
    }

    /** One side of the entrenchment transect; {@code +inf} when the walk never clears the stage. */
    private double halfWidth(
            double[] point,
            double[] normal,
            double side,
            double maxFloodProneAreaHeight,
            double maxFloodPlainWidth,
            double step) {
        for (double d = step; d <= maxFloodPlainWidth; d += step) {
            final double x = point[0] + side * d * normal[0];
            final double z = point[1] + side * d * normal[1];
            if (Interpolation.sampleBilinear(elev, x, z, gridSize) > maxFloodProneAreaHeight) return d;
        }
        return Double.POSITIVE_INFINITY;
    }
}
