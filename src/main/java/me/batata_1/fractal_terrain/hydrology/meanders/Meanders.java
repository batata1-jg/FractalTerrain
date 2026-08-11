package me.batata_1.fractal_terrain.hydrology.meanders;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
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
 *
 * <p>Every step takes a {@code dx} (native px): it is both the resample spacing and — through
 * {@link HydrologyTuning#maxMigration} — the cap on per-step displacement, so a caller coarsening the
 * geometry gets proportionally bolder migration and the two never fall out of scale with each other.
 */
public final class Meanders {

    /** A per-step migration rule; {@code dx} is the step's resample spacing (native px). */
    @FunctionalInterface
    private interface MigrationRule {
        void migrate(Channel ch, double dx);
    }

    private static final double TWO_THIRDS = 2.0 / 3.0;
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

    /** Pre-carve elevation the Rosgen classifier measures against; null makes {@link #collectPrimitives} refuse. */
    private final float[] elev;

    private final RiverNetwork network;

    private int currentStep = 0; // the step number being processed, for debug image folders

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
        this.network = new RiverNetwork(
                gridSize, nodeSpecs, edgeSpecs, savePreviousStates, maxSavedStates, HydrologyTuning.DX);
    }

    /** The mutated river network (for conversion / inspection). */
    public RiverNetwork getNetwork() {
        return network;
    }

    /** One simulation step using the Ikeda-Parker-Sawai meander migration, at resample spacing {@code dx}. */
    public void step(int i, double dx) {
        stepImpl(i, dx, this::migrateMeanders);
    }

    /** One step that only relaxes channels down the terrain gradient (no meandering). */
    public void relaxStep(int i, double dx) {
        stepImpl(i, dx, this::migrateLowerGrad);
    }

    /** Relax the whole network down-gradient for {@code steps} steps (cutoffs + collisions run). */
    public void relaxLowerGrad(int steps, double dx) {
        for (int i = 1; i <= steps; i++) {
            relaxStep(i, dx);
        }
    }

    public void simulate(int n) {
        simulate(n, HydrologyTuning.DX);
    }

    public void simulate(int n, double dx) {
        for (int i = 1; i <= n; i++) step(i, dx);
    }

    private void stepImpl(int i, double dx, MigrationRule migrate) {
        currentStep = i;
        network.beginStep();
        dumpNetwork("00_original");
        for (Channel ch : network.getChannels()) {
            ch.reSample(dx);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
            migrate.migrate(ch, dx);
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
            ch.reSample(dx);
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

    /** Pulls the channel toward the valley floor, so relaxed rivers follow the decoded terrain.
     *  {@code dx} is the step's resample spacing, which caps the displacement. */
    public void migrateLowerGrad(Channel ch, double dx) {
        final double maxMigration = HydrologyTuning.maxMigration(dx);
        final int pointCount = ch.spline.points().size();
        ArrayList<double[]> migratedPoints = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            final double[] point = ch.spline.points().get(i);
            final double[] gradient = sampleGradient(point[0], point[1]);
            final double[] displacementNormal = VectorOps.project(gradient, ch.spline.normal(i));

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

    /** The network's primitives, each carrying a Rosgen type. Classification happens here because this is
     *  the only object holding both the graph and the raster, and because the type sets the primitive's
     *  influence radius — which must exist before anything indexes these primitives. */
    public List<HydrologicalPrimitive> collectPrimitives(double offsetX, double offsetZ) {
        if (elev == null) {
            throw new IllegalStateException(
                    "collectPrimitives needs the raw elevation raster; construct Meanders with it");
        }
        return network.collectPrimitives(offsetX, offsetZ, new ReachRosgenClassifier(elev, gridSize));
    }
}
