package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.ArrayList;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.TestOnly;

/**
 * The Ikeda-Parker-Sawai meander migration model: it bends channels sideways into meanders, driven by
 * their own curvature rather than by the terrain.
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

    private static final double K = 0.0164; // aumentar esse faz curvar pra traz
    private static final double FRICTION = 0.011; // diminue a
    private static final double DT = 1;

    public Meanders(RiverNetwork network) {
        super(network);
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
        Debug.isNan(localRates);
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
        Debug.isNan(migRates);
        return migRates;
    }

    /** Meander migration (Ikeda-Parker-Sawai) at resample spacing {@code dx}. Endpoints stay pinned so
     *  graph node coordinates hold. */
    public void migrateMeanders(Channel ch, double dx) {
        final double[] migrationRates = computeMigrationRates(ch, dx);
        final double maxMigration = HydrologyTuning.maxMigration(dx);
        final int pointCount = ch.spline.points().size();
        ArrayList<double[]> migratedPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            if (i == 0 || i == pointCount - 1) {
                migratedPoints.add(ch.spline.points().get(i)); // pin node endpoints
                continue;
            }
            final double rate = Math.clamp(DT * migrationRates[i], -maxMigration, maxMigration);
            final double[] point = ch.spline.points().get(i);
            final double[] normal = ch.spline.normal(i);
            final double factor = -rate * borderDamping(point[0], point[1], ch.dischargeWidth());
            final double[] migratedPoint = {point[0] + normal[0] * factor, point[1] + normal[1] * factor};
            Debug.isNan(migratedPoint);
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
            final double[] migVector = VectorOps.scale(ch.spline.normal(i), -rate);
            double[] newPt = VectorOps.add(ch.spline.points().get(i), migVector);
            Debug.isNan(newPt);
            newPts[i] = newPt;
        }
        return newPts;
    }
}
