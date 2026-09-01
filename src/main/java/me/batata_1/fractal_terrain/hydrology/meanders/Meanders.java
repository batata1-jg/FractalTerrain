package me.batata_1.fractal_terrain.hydrology.meanders;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The Ikeda-Parker-Sawai meander migration model: it bends channels sideways into meanders. Curvature
 * still drives the direction and magnitude of the bend; the terrain gradient only attenuates it.
 *
 * <p>Only the migration rule lives here. The step sequence around it, and every structural change the
 * displacement provokes (cutoffs, stream capture, resampling), belong to {@link ChannelMigrator} and
 * {@link RiverNetwork} respectively. {@link GradientNetworkRelaxation} is the sibling model that
 * follows the terrain instead; both act on the one injected network.
 */
public final class Meanders extends ChannelMigrator {

    private static final double TWO_THIRDS = 2.0 / 3.0;
    private static final double OMEGA = -1.0;
    private static final double GAMMA = 2.5;

    private static final double K = 0.03; // aumentar esse faz curvar pra traz
    private static final double FRICTION = 0.011; // diminue a
    private static final double DT = 1;

    /** Exponential-decay reference for gradient attenuation; seeded from {@link HydrologyTuning}'s
     *  10f steep-gradient gate on this same raster and otherwise uncalibrated. */
    private static final double GRAD_REF = 1.0;

    /** Padded gradient-magnitude raster (decoder channel 4, {@code refinedGrad}), indexed like every
     *  other {@code network.getGridSize()} square raster; {@code null} disables attenuation. */
    private final float @Nullable [] gradMag;

    public Meanders(RiverNetwork network) {
        this(network, null);
    }

    public Meanders(RiverNetwork network, float[] gradMag) {
        super(network);
        this.gradMag = gradMag;
    }

    public void simulate(int n) {
        simulate(n, HydrologyTuning.DX);
    }

    public void simulate(int n, double dx) {
        run(n, dx);
    }

    @Override
    protected void migrate(Channel ch, double dx) {
        migrateMeanders(ch, dx);
    }

    private static double[] computeMigrationRates(Channel ch, double dx) {
        final double sinuosity = ch.computeSinuosity();
        final double[] localRates = ch.computeLocalRates();
        Debug.assertNoNaN(localRates);
        final double sigmaToTheMinus2over3 = Math.pow(sinuosity, -TWO_THIRDS);
        final double alpha = 2 * FRICTION / ch.depth();
        final double expTerm = Math.exp(-alpha * dx);
        double integralTerm = 0;
        final double[] migRates = new double[ch.spline.points().size()];
        for (int i = 0; i < ch.spline.points().size(); i++) {
            integralTerm += localRates[i] * K;
            final double migRate = OMEGA * localRates[i] + GAMMA * alpha * integralTerm;
            integralTerm *= expTerm;
            migRates[i] = migRate * sigmaToTheMinus2over3;
        }
        Debug.assertNoNaN(migRates);
        return migRates;
    }

    /** Meander migration (Ikeda-Parker-Sawai) at resample spacing {@code dx}. Endpoints stay pinned so
     *  graph node coordinates hold. */
    public void migrateMeanders(Channel ch, double dx) {
        final double[] migrationRates = computeMigrationRates(ch, dx);
        final double maxMigration = HydrologyTuning.maxMigration(dx);
        final int pointCount = ch.spline.points().size();
        List<double[]> migratedPoints = new ObjectArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            if (i == 0 || i == pointCount - 1) {
                migratedPoints.add(ch.spline.points().get(i)); // pin node endpoints
                continue;
            }
            final double rate = Math.clamp(DT * migrationRates[i], -maxMigration, maxMigration);
            final double[] point = ch.spline.points().get(i);
            final double[] normal = ch.spline.normal(i);
            final double gradScale = Math.exp(-sampleGradMag(point[0], point[1]) / GRAD_REF);
            final double factor = -rate * borderDamping(point[0], point[1], ch.dischargeWidth()) * gradScale;
            final double[] migratedPoint = {point[0] + normal[0] * factor, point[1] + normal[1] * factor};
            Debug.assertNoNaN(migratedPoint);
            migratedPoints.add(migratedPoint);
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(migratedPoints);
    }

    @TestOnly
    public double[][] computedMigVector(Channel ch, double dx) {
        final double[] migRates = computeMigrationRates(ch, dx);
        final double maxMigration = HydrologyTuning.maxMigration(dx);
        double[][] newPts = new double[ch.spline.getSize()][2];
        for (int i = 0; i < ch.spline.points().size(); i++) {
            final double rate = Math.clamp(DT * migRates[i], -maxMigration, maxMigration);
            final double[] point = ch.spline.points().get(i);
            final double gradScale = Math.exp(-sampleGradMag(point[0], point[1]) / GRAD_REF);
            final double[] migVector = VectorOps.scale(ch.spline.normal(i), -rate * gradScale);
            double[] newPt = VectorOps.add(point, migVector);
            Debug.assertNoNaN(newPt);
            newPts[i] = newPt;
        }
        return newPts;
    }

    /** Mirrors {@link GradientNetworkRelaxation#sampleGradient}'s clamping/indexing; {@code 0.0} with no
     *  raster keeps {@link #GRAD_REF} attenuation a no-op for callers that construct without one. */
    private double sampleGradMag(double x, double z) {
        if (gradMag == null) return 0.0;
        final int gridSize = network.getGridSize();
        final int xCell = Math.clamp((int) Math.round(x), 0, gridSize - 1);
        final int zCell = Math.clamp((int) Math.round(z), 0, gridSize - 1);
        return gradMag[xCell * gridSize + zCell];
    }
}
