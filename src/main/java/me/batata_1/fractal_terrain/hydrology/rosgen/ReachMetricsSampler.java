package me.batata_1.fractal_terrain.hydrology.rosgen;

import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * The raster side of Rosgen classification: measures along-channel slope and the entrenchment ratio for
 * one reach.
 *
 * <p><b>The elevation field must be the raw decoded terrain, never a carved buffer.</b>
 * {@code HydrologyProfileCarver.carveRiverShells} <em>creates</em> the floodplain and writes in place;
 * measuring entrenchment on its output measures {@code FLOODPLAIN_BASE} and
 * {@code FLOODPLAIN_WIDTH_FACTOR} — the carve's own tuning constants — instead of the terrain.
 *
 * <p>Cost note: a transect walks perpendicular to the channel, so consecutive samples stride a whole
 * row ({@code gridSize} floats) and get no spatial locality — each sample is close to a cache miss.
 * Callers must therefore transect once per <em>reach</em>, not once per spline point;
 * {@link ReachRosgenClassifier} is responsible for that and the step scales with width to keep the step
 * count roughly constant.
 */
public final class ReachMetricsSampler {

    private final float[] elev;
    private final int gridSize;

    /**
     * @param elev     raw decoded elevation, {@code gridSize²}, row-major {@code x * gridSize + z}
     * @param gridSize side of the (padded) square field
     */
    public ReachMetricsSampler(float[] elev, int gridSize) {
        if (elev.length != gridSize * gridSize) {
            throw new IllegalArgumentException("elev length " + elev.length + " != gridSize² " + (gridSize * gridSize));
        }
        this.elev = elev;
        this.gridSize = gridSize;
    }

    /**
     * Along-channel bed slope over {@code [fromIndex, toIndex]}: elevation drop divided by the arc length
     * spanning those points, floored at {@code 0}.
     *
     * <p>{@code ChannelElevationAssigner} propagates beds monotone non-increasing downstream, so an
     * uphill reach means degenerate geometry rather than real terrain; flooring at zero keeps such a
     * reach out of the {@code Aa+}/{@code A} branches rather than producing a nonsense negative slope.
     */
    public double slope(List<double[]> pts, double arcLength, int fromIndex, int toIndex) {
        if (arcLength <= 0.0) return 0.0;
        final double drop = elevAt(pts.get(fromIndex)) - elevAt(pts.get(toIndex));
        return Math.max(0.0, drop / arcLength) / HydrologyTuning.RIVER_SLOPE_RESCALE;
    }

    public double elevAt(double[] sample) {
        return Interpolation.sampleBilinear(elev, sample[0], sample[1], gridSize);
    }

    /**
     * Entrenchment ratio at one point: flood-prone width divided by bankfull width, where the flood-prone
     * width is measured at a stage of twice the maximum bankfull depth above the bed.
     *
     * <p>The field method transcribed to a raster. From the point, step outward along {@code ±normal}
     * until the sampled elevation exceeds the flood-prone stage; the two half-widths sum to the
     * flood-prone width. When <em>both</em> sides reach the walk bound without exceeding the stage the
     * result is {@code +inf} — the correct semantic for a broad flat valley (the slightly-entrenched
     * branch), not a failure. The bound is {@code ER_WALK_WIDTHS · width} per side, which resolves every
     * threshold the key tests. The step scales with width but is capped so each side always takes at
     * least {@code ER_MIN_STEPS_PER_SIDE} samples — otherwise a walk narrower than one step (a reach at
     * the {@link HydrologyTuning#MIN_WIDTH} clamp floor) samples nothing and reports {@code +inf}
     * unconditionally.
     *
     * @param point   reach centre in the network frame
     * @param normal  unit normal to the centreline at {@code point}
     * @param bedElev bed elevation at {@code point}
     * @param width   bankfull width, native px
     */
    public double entrenchmentRatio(double[] point, double[] normal, double bedElev, double width) {
        final double bankfullWidth = Math.max(width, HydrologyTuning.MIN_WIDTH);
        final double depth = HydrologyTuning.DEPTH_MAX_FACTOR * ChannelGeometry.depthForWidth(bankfullWidth);
        final double floodProneStage = bedElev + 2.0 * depth;
        final double maxFloodPlainWidth = HydrologyTuning.ER_WALK_WIDTHS * bankfullWidth;
        final double step = Math.clamp(
                bankfullWidth * HydrologyTuning.ER_STEP_WIDTH_FRACTION,
                HydrologyTuning.ER_STEP_MIN,
                maxFloodPlainWidth / HydrologyTuning.ER_MIN_STEPS_PER_SIDE);

        final double positive = halfWidth(point, normal, +1.0, floodProneStage, maxFloodPlainWidth, step);
        final double negative = halfWidth(point, normal, -1.0, floodProneStage, maxFloodPlainWidth, step);
        if (Double.isInfinite(positive) && Double.isInfinite(negative)) return Double.POSITIVE_INFINITY;

        final double positiveWidth = Double.isInfinite(positive) ? maxFloodPlainWidth : positive;
        final double negativeWidth = Double.isInfinite(negative) ? maxFloodPlainWidth : negative;
        return (positiveWidth + negativeWidth) / (bankfullWidth + HydrologyTuning.ENTRENTMENT_RATIO_BIAS);
    }

    /**
     * Distance from {@code point} along {@code side · normal} at which elevation first exceeds
     * {@code maxFloodProneAreaHeight}, or {@code +inf} when the walk reaches {@code maxFloodPlainWidth} without doing so.
     */
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
