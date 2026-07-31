package me.batata_1.fractal_terrain.math;

import java.util.List;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;

/**
 * Rasterises ridge and valley point sets into a scalar field-line image, upstream of skeletonization.
 *
 * <p>The fwidth normalization pass exists because plain {@code sin} gives lines that are crisp near
 * the seeds and spread out far from them; dividing by the local gradient keeps them uniformly thin.
 *
 * <p>Output is upsampled per axis so a downstream skeletonizer can resolve finer lines.
 */
public class FieldLinePlacer {

    /** Upsample factor per axis: the output grid is {@code (UPSAMPLE*W) x (UPSAMPLE*H)}. */
    public static final int UPSAMPLE = 8;

    private static final double FWIDTH_EPS = 1e-6;

    private final int width;
    private final int height;
    private final double resolution;
    private final double queryRadius;

    @SuppressWarnings("unused") // accepted now; wired into the field shaping later by the user
    private final double lineThickness;

    private final double frequency;

    public FieldLinePlacer(
            int width, int height, double resolution, double queryRadius, double lineThickness, double frequency) {
        this.width = width;
        this.height = height;
        this.resolution = resolution;
        this.queryRadius = queryRadius;
        this.lineThickness = lineThickness;
        this.frequency = frequency;
    }

    /** Output grid width (in cells): {@code UPSAMPLE * width}. */
    public int outputWidth() {
        return UPSAMPLE * width;
    }

    /** Output grid height (in cells): {@code UPSAMPLE * height}. */
    public int outputHeight() {
        return UPSAMPLE * height;
    }

    /** The production entry point: the raw field plus normalization. Trees must be in the frame that
     *  {@code (row*resolution, col*resolution)} addresses. */
    public float[] apply(
            ImmutableQuadTree<SpatialIndexPoint> ridgePoints, ImmutableQuadTree<SpatialIndexPoint> valleyPoints) {
        return normalizeByFwidth(applyRaw(ridgePoints, valleyPoints), outputWidth(), outputHeight());
    }

    /** Pre-normalization field, exposed for debug visualisation; production uses {@link #apply}. */
    public float[] applyRaw(
            ImmutableQuadTree<SpatialIndexPoint> ridgePoints, ImmutableQuadTree<SpatialIndexPoint> valleyPoints) {
        final int outH = outputHeight();
        final int outW = outputWidth();
        final float[] rawField = new float[outH * outW];

        for (int row = 0; row < outH; row++) {
            for (int col = 0; col < outW; col++) {
                final double sampleX = row * resolution;
                final double sampleZ = col * resolution;
                final double[] center = {sampleX, sampleZ};

                final List<SpatialIndexPoint> ridgeHits = ridgePoints.getPointsInCircle(center, queryRadius);
                final List<SpatialIndexPoint> valleyHits = valleyPoints.getPointsInCircle(center, queryRadius);

                if (ridgeHits.isEmpty() && valleyHits.isEmpty()) {
                    rawField[row * outW + col] = 0f;
                    continue;
                }

                double netAngle = 0.0;
                for (SpatialIndexPoint ridge : ridgeHits) {
                    netAngle += frequency * Math.atan2(sampleX - ridge.get(0), sampleZ - ridge.get(1));
                }
                for (SpatialIndexPoint valley : valleyHits) {
                    netAngle -= Math.atan2(sampleX - valley.get(0), sampleZ - valley.get(1));
                }
                rawField[row * outW + col] = (float) Math.sin(netAngle);
            }
        }
        return rawField;
    }

    /** Divides out the local gradient so line thickness stops varying with distance from the seeds. */
    public static float[] normalizeByFwidth(float[] rawField, int outW, int outH) {
        final float[] out = new float[rawField.length];
        for (int row = 0; row < outH; row++) {
            for (int col = 0; col < outW; col++) {
                final int idx = row * outW + col;

                final float up = rawField[Math.max(row - 1, 0) * outW + col];
                final float down = rawField[Math.min(row + 1, outH - 1) * outW + col];
                final float left = rawField[row * outW + Math.max(col - 1, 0)];
                final float right = rawField[row * outW + Math.min(col + 1, outW - 1)];

                final double dRow = (down - up) * 0.5;
                final double dCol = (right - left) * 0.5;
                final double fwidth = Math.max(Math.abs(dRow) + Math.abs(dCol), FWIDTH_EPS);

                out[idx] = (float) (rawField[idx] / fwidth);
            }
        }
        return out;
    }
}
