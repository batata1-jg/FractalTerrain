package me.batata_1.fractal_terrain.hydrology;

import java.util.List;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;

/**
 * Rasterises two point sets (ridges and valleys) into a scalar "field-line" image.
 *
 * <p>For every output cell the net angle to all nearby ridge points (added) and valley points
 * (subtracted) is accumulated, then {@code sin(netAngle * frequency)} is written. Plain {@code sin}
 * produces lines that are crisp near the seed points but spread out farther away; a screen-space
 * derivative ({@code fwidth}) normalisation pass divides each value by the local gradient magnitude
 * so the traced lines stay uniformly thin across the whole image.
 *
 * <p>The output grid is sampled at TWICE the input resolution per axis (so the returned array is 4×
 * the input length); the higher resolution lets a downstream skeletonizer resolve more/finer lines.
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

    /**
     * Compute the field-line image for the given ridge/valley point sets. Returns a flat
     * {@code float[outputHeight * outputWidth]} (= {@code 4 * width * height}) in row-major order,
     * after the {@link #normalizeByFwidth} pass. The supplied trees must be in the same coordinate
     * frame that {@code (row*resolution, col*resolution)} addresses.
     */
    public float[] apply(QuadTree<QuadTreePoint> ridgePoints, QuadTree<QuadTreePoint> valleyPoints) {
        return normalizeByFwidth(applyRaw(ridgePoints, valleyPoints), outputWidth(), outputHeight());
    }

    /**
     * Raw {@code sin(netAngle * frequency)} field, BEFORE the fwidth normalization. Exposed so debug
     * harnesses can visualise the pre-normalization image; production callers use {@link #apply}.
     */
    public float[] applyRaw(QuadTree<QuadTreePoint> ridgePoints, QuadTree<QuadTreePoint> valleyPoints) {
        final int outH = outputHeight();
        final int outW = outputWidth();
        final float[] rawField = new float[outH * outW];

        for (int row = 0; row < outH; row++) {
            for (int col = 0; col < outW; col++) {
                final double sampleX = row * resolution;
                final double sampleZ = col * resolution;
                final double[] center = {sampleX, sampleZ};

                final List<QuadTreePoint> ridgeHits = ridgePoints.getPointsInCircle(center, queryRadius);
                final List<QuadTreePoint> valleyHits = valleyPoints.getPointsInCircle(center, queryRadius);

                if (ridgeHits.isEmpty() && valleyHits.isEmpty()) {
                    rawField[row * outW + col] = 0f;
                    continue;
                }

                double netAngle = 0.0;
                for (QuadTreePoint ridge : ridgeHits) {
                    netAngle += frequency*Math.atan2(sampleX - ridge.get(0), sampleZ - ridge.get(1));
                }
                for (QuadTreePoint valley : valleyHits) {
                    netAngle -= Math.atan2(sampleX - valley.get(0), sampleZ - valley.get(1));
                }
                rawField[row * outW + col] = (float) Math.sin(netAngle);
            }
        }
        return rawField;
    }

    /**
     * Divide each cell by its screen-space derivative magnitude
     * {@code fwidth ≈ |∂/∂row| + |∂/∂col|} (central differences inside, one-sided at borders),
     * clamped to {@link #FWIDTH_EPS}. Keeps the traced lines uniformly thin.
     */
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
