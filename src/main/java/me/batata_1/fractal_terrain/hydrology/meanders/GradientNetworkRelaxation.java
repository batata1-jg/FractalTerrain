package me.batata_1.fractal_terrain.hydrology.meanders;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;

/**
 * The valley-seeking migration model: it slides channel points down the decoded terrain gradient so a
 * graph traced from coarse arrows settles into the relief the decoder actually produced.
 *
 * <p>The sibling of {@link Meanders} — same {@link ChannelMigrator} step sequence, same injected
 * network, opposite driver. Relaxation is terrain-driven and ignores curvature; meandering is
 * curvature-driven and only uses its own gradient raster to attenuate, not steer, the migration.
 *
 * <p>Unlike {@link Meanders#migrateMeanders}, endpoints are NOT pinned: a traced node's coarse-derived
 * position is itself an estimate worth relaxing, and {@code RiverNetwork.resolveEndpoints} re-seats the
 * graph's nodes after every step anyway.
 */
public final class GradientNetworkRelaxation extends ChannelMigrator {

    private final float[] gradX;
    private final float[] gradZ;

    public GradientNetworkRelaxation(RiverNetwork network, float[] gradX, float[] gradZ) {
        super(network);
        this.gradX = gradX;
        this.gradZ = gradZ;
    }

    /** Relaxes the whole network down-gradient for {@code steps} steps (cutoffs + collisions run). */
    public void relax(int steps, double dx) {
        run(steps, dx);
    }

    @Override
    protected void migrate(Channel ch, double dx) {
        migrateLowerGrad(ch, dx);
    }

    /** Pulls the channel toward the valley floor, so relaxed rivers follow the decoded terrain.
     *  {@code dx} is the step's resample spacing, which caps the displacement. */
    public void migrateLowerGrad(Channel ch, double dx) {
        final double maxMigration = HydrologyTuning.maxMigration(dx);
        final int pointCount = ch.spline.points().size();
        List<double[]> migratedPoints = new ObjectArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            final double[] point = ch.spline.points().get(i);
            final double[] gradient = sampleGradient(point[0], point[1]);

            double moveX = gradient[0] * -1;
            double moveZ = gradient[1] * -1;
            final double magnitude = Math.sqrt(moveX * moveX + moveZ * moveZ);
            if (magnitude > maxMigration) {
                final double clampFactor = maxMigration / magnitude;
                moveX = moveX * clampFactor;
                moveZ = moveZ * clampFactor;
            }

            final double dampFactor = borderDamping(point[0], point[1], ch.dischargeWidth());
            moveX = moveX * dampFactor;
            moveZ = moveZ * dampFactor;
            migratedPoints.add(new double[] {point[0] + moveX, point[1] + moveZ});
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(migratedPoints);
    }

    private double[] sampleGradient(double x, double z) {
        final int gridSize = network.getGridSize();
        final int xCell = Math.clamp((int) Math.round(x), 0, gridSize - 1);
        final int zCell = Math.clamp((int) Math.round(z), 0, gridSize - 1);
        final int cellIndex = xCell * gridSize + zCell;
        return new double[] {gradX[cellIndex], gradZ[cellIndex]};
    }
}
