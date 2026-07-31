package me.batata_1.fractal_terrain.hydrology.meanders;

import static me.batata_1.fractal_terrain.config.HydrologyTuning.MAX_MIGRATION;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifier;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

/**
 * The meander <b>simulation driver</b>. {@code Meanders} owns the Ikeda-Parker-Sawai migration model
 * and the per-step orchestration; the river network itself — channels, nodes, and all structural
 * topology (split/merge/prune, cutoffs, stream-capture collisions, resampling) — lives in
 * {@link RiverNetwork}, which this class mutates.
 *
 * <p>Each step resamples every channel, migrates its interior points, then asks the network to resolve
 * self-intersection cutoffs and stream-capture collisions. {@link #relaxLowerGrad} runs the same loop
 * with a valley-seeking migration instead of meandering (and a network constructed without history).
 */
public final class Meanders {

    private static final double TWO_THIRDS = 2.0 / 3.0;
    private static final double INF = 1e9;
    private static final double OMEGA = -1.0;
    private static final double GAMMA = 2.5;

    private static final double K = 0.0164; // aumentar esse faz curvar pra traz
    private static final double FRICTION = 0.011; // diminue a
    private static final double DT = 1;

    /** When true, step()/manageCollisions() dump per-stage network PNGs into step_&lt;n&gt;/ folders. */
    public static boolean DEBUG_STEPS = false;

    private static final Logger LOG = getLogger(Meanders.class);

    private final int gridSize;
    private final float[] gradX;
    private final float[] gradZ;

    /** Pre-carve elevation the Rosgen classifier measures against; null makes {@link #collectUnits} refuse. */
    private final float[] elev;

    private final RiverNetwork network;

    private int currentStep = 0; // the step number being processed, for debug image folders
    private final double maxMigrationMagnetude;

    public Meanders(
            int gridSize,
            float[] gradX,
            float[] gradZ,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs) {
        this(gridSize, gradX, gradZ, null, nodeSpecs, edgeSpecs, false, 0);
    }

    /** The production construction path; builds a network that keeps no history. */
    public Meanders(
            int gridSize,
            float[] gradX,
            float[] gradZ,
            float[] elev,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs) {
        this(gridSize, gradX, gradZ, elev, nodeSpecs, edgeSpecs, false, 0);
    }

    public Meanders(
            int gridSize,
            float[] gradX,
            float[] gradZ,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            boolean savePreviousStates,
            int maxSavedStates) {
        this(gridSize, gradX, gradZ, null, nodeSpecs, edgeSpecs, savePreviousStates, maxSavedStates);
    }

    public Meanders(
            int gridSize,
            float[] gradX,
            float[] gradZ,
            float[] elev,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            boolean savePreviousStates,
            int maxSavedStates) {
        if (elev != null && elev.length != gridSize * gridSize) {
            throw new IllegalArgumentException("elev length " + elev.length + " != gridSize² " + (gridSize * gridSize));
        }
        this.gridSize = gridSize;
        this.gradX = gradX;
        this.gradZ = gradZ;
        this.elev = elev;
        this.maxMigrationMagnetude = INF;
        this.network = new RiverNetwork(
                gridSize, nodeSpecs, edgeSpecs, savePreviousStates, maxSavedStates, HydrologyTuning.DX);
    }

    /** The mutated river network (for conversion / inspection). */
    public RiverNetwork getNetwork() {
        return network;
    }

    /** One simulation step using the Ikeda-Parker-Sawai meander migration. */
    public void step(int i) {
        stepImpl(i, this::migrateMeanders);
    }

    /** One step that only relaxes channels down the terrain gradient (no meandering). */
    public void relaxStep(int i) {
        stepImpl(i, this::migrateLowerGrad);
    }

    /** Relax the whole network down-gradient for {@code steps} steps (cutoffs + collisions run). */
    public void relaxLowerGrad(int steps) {
        for (int i = 1; i <= steps; i++) {
            relaxStep(i);
        }
    }

    public void simulate(int n) {
        for (int i = 1; i <= n; i++) step(i);
    }

    private void stepImpl(int i, Consumer<Channel> migrate) {
        currentStep = i;
        network.beginStep();
        dumpNetwork("00_original");
        for (Channel ch : network.getChannels()) {
            ch.reSample(HydrologyTuning.DX);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
            migrate.accept(ch);
        }

        network.resolveEndpoints();

        for (Channel ch : network.getChannels()) {
            ch.reSample(Math.sqrt(ch.intakeWidth())); // intake: finest spacing, gap-free discs
            network.manageCutoffs(ch, i);
        }
        dumpNetwork("01_migrated");

        network.manageCollisions(i, network.viewAtomic());
        dumpNetwork("04_managed");
        for (Channel ch : network.getChannels()) {
            ch.reSample(HydrologyTuning.DX);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
        }
        dumpNetwork("05_final");
        network.recordState(i);
    }

    /** Dumps the whole network into {@code step_<currentStep>/<name>.png} when {@link #DEBUG_STEPS}. */
    private void dumpNetwork(String name) {
        if (!DEBUG_STEPS) return;
        Debug.river.seeNetwork(this, "step_" + currentStep, name);
    }

    public int getGridSize() {
        return gridSize;
    }

    // ---------------------------------------------------------------------------------------------
    // Migration
    // ---------------------------------------------------------------------------------------------

    private static double[] computeMigrationRates(Channel ch) {
        final double sinuosity = ch.computeSinuosity();
        final double[] localRates = ch.computeLocalRates();
        Debug.isNan(localRates);
        final double sigmaToTheMinus2over3 = Math.pow(sinuosity, -TWO_THIRDS);
        final double alpha = 2 * FRICTION / ch.depth();
        final double expTerm = Math.exp(-alpha * HydrologyTuning.DX);
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

    /** Meander migration (Ikeda-Parker-Sawai). Endpoints stay pinned so graph node coordinates hold. */
    public void migrateMeanders(Channel ch) {
        final double[] migrationRates = computeMigrationRates(ch);
        final int pointCount = ch.spline.points().size();
        ArrayList<double[]> migratedPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            if (i == 0 || i == pointCount - 1) {
                migratedPoints.add(ch.spline.points().get(i)); // pin node endpoints
                continue;
            }
            final double rate = Math.clamp(DT * migrationRates[i], -maxMigrationMagnetude, maxMigrationMagnetude);
            final double[] point = ch.spline.points().get(i);
            final double[] migrationVector = VectorOps.scale(
                    ch.spline.normal(i), -rate * borderDamping(point[0], point[1], ch.dischargeWidth()));
            double[] migratedPoint = VectorOps.add(point, migrationVector);
            Debug.isNan(migratedPoint);
            migratedPoints.add(migratedPoint);
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(migratedPoints);
    }

    /** Pulls the channel toward the valley floor, so relaxed rivers follow the decoded terrain. */
    public void migrateLowerGrad(Channel ch) {
        final int pointCount = ch.spline.points().size();
        ArrayList<double[]> migratedPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            final double[] point = ch.spline.points().get(i);
            final double[] gradient = sampleGradient(point[0], point[1]);
            double[] displacementNormal = VectorOps.project(gradient, ch.spline.normal(i));

            double[] displacement =
                    VectorOps.add(VectorOps.scale(displacementNormal, -1), VectorOps.scale(gradient, -1));
            //  double[] displacement = VectorOps.scale(gradient, -1);
            final double magnitude = VectorOps.magnitude(displacement);
            if (magnitude > MAX_MIGRATION) displacement = VectorOps.scale(displacement, MAX_MIGRATION / magnitude);

            displacement = VectorOps.scale(displacement, borderDamping(point[0], point[1], ch.dischargeWidth()));
            migratedPoints.add(VectorOps.add(point, displacement));
        }
        ch.spline = QuinticHermiteSpline.createCatmullRom(migratedPoints);
    }

    /** Caps the damping margin so a wide trunk cannot zero the whole field. Uncalibrated; under-damps near centre. */
    private static final double MAX_MARGIN_FRACTION = 0.4;

    /** Fades migration to zero near the grid border, keeping a channel's whole carve band inside the
     *  grid. Wider channels are confined further in. Endpoints are pinned by callers, so only they may
     *  sit near the border. */
    private double borderDamping(double x, double z, double width) {
        final double margin = Math.min(HydrologyTuning.MARGIN_INFLUENCE_FACTOR * width, MAX_MARGIN_FRACTION * gridSize);
        if (margin <= 0) return 1.0;
        final double distToBorder = Math.min(Math.min(x, z), Math.min(gridSize - 1 - x, gridSize - 1 - z));
        // Inner padding [0, margin]: hard-zero. Beyond it, ramp 0→1 over the next margin.
        return Math.clamp((distToBorder - margin) / margin, 0.0, 1.0);
    }

    private double[] sampleGradient(double x, double z) {
        final int xCell = Math.clamp((int) Math.round(x), 0, gridSize - 1);
        final int zCell = Math.clamp((int) Math.round(z), 0, gridSize - 1);
        final int cellIndex = xCell * gridSize + zCell;
        return new double[] {gradX[cellIndex], gradZ[cellIndex]};
    }

    @TestOnly
    public double[][] computedMigVector(Channel ch) {
        final double[] migRates = computeMigrationRates(ch);
        double[][] newPts = new double[ch.spline.getSize()][2];
        for (int i = 0; i < ch.spline.points().size(); i++) {
            final double rate = Math.clamp(DT * migRates[i], -maxMigrationMagnetude, maxMigrationMagnetude);
            final double[] migVector = VectorOps.scale(ch.spline.normal(i), -rate);
            double[] newPt = VectorOps.add(ch.spline.points().get(i), migVector);
            Debug.isNan(newPt);
            newPts[i] = newPt;
        }
        return newPts;
    }

    // ---------------------------------------------------------------------------------------------
    // Graph delegation (forwards to the RiverNetwork; convenience for tests/debug)
    // ---------------------------------------------------------------------------------------------

    @TestOnly
    public void manageCollisions() {
        network.manageCollisions(currentStep, network.viewAtomic());
    }

    public List<Channel> getChannels() {
        return network.getChannels();
    }

    public ArrayList<double[]> getChannelPts(int channelId) {
        return network.getChannelPts(channelId);
    }

    public int getChannelCount() {
        return network.getChannelCount();
    }

    public Channel getChannel(int id) {
        return network.getChannel(id);
    }

    public Collection<Endpoint> getNodes() {
        return network.getNodes();
    }

    public Endpoint getNode(int id) {
        return network.getNode(id);
    }

    /** The network's units, each carrying a Rosgen type. Classification happens here because this is
     *  the only object holding both the graph and the raster, and because the type sets the unit's
     *  influence radius — which must exist before anything indexes these units. */
    public List<HydrologicalUnit> collectUnits(double offsetX, double offsetZ) {
        if (elev == null) {
            throw new IllegalStateException("collectUnits needs the raw elevation raster; construct Meanders with it");
        }
        return network.collectUnits(offsetX, offsetZ, new ReachRosgenClassifier(elev, gridSize));
    }
}
