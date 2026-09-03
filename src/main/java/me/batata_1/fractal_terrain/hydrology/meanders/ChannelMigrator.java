package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.Collection;
import java.util.List;
import me.batata_1.fractal_terrain.config.DebugConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.TestOnly;

/**
 * The per-step simulation driver shared by every point-migration model. A subclass supplies only
 * {@link #migrate}; the resample → migrate → resolve-endpoints → cutoffs → collisions sequence lives
 * here, so two models driven over one graph cannot fall out of step with each other.
 *
 * <p>The {@link RiverNetwork} is injected, not constructed: a model is one rule applied to a graph
 * someone else owns, so several can be driven over the SAME instance in sequence — impossible while one
 * of them owns it privately. Only {@link GradientNetworkRelaxation} has a pipeline caller today.
 *
 * <p>Every step takes a {@code dx} (native px): it is both the resample spacing and — through
 * {@link HydrologyTuning#maxMigration} — the cap on per-step displacement, so a caller coarsening the
 * geometry gets proportionally bolder migration and the two never fall out of scale with each other.
 */
public abstract class ChannelMigrator {

    /** Caps the damping margin so a wide trunk cannot zero the whole field. Uncalibrated; under-damps near centre. */
    private static final double MAX_MARGIN_FRACTION = 0.4;

    protected final RiverNetwork network;

    private int currentStep = 0; // the step number being processed, for debug image folders

    protected ChannelMigrator(RiverNetwork network) {
        this.network = network;
    }

    /** Displaces {@code ch}'s points by this model's rule, at resample spacing {@code dx} (native px). */
    protected abstract void migrate(Channel ch, double dx);

    /** The mutated river network (for conversion / inspection). */
    public RiverNetwork getNetwork() {
        return network;
    }

    public int getGridSize() {
        return network.getGridSize();
    }

    /** Runs {@code steps} migration steps at resample spacing {@code dx}; cutoffs and collisions run each step. */
    public void run(int steps, double dx) {
        for (int i = 1; i <= steps; i++) {
            step(i, dx);
        }
    }

    /** One simulation step: resample every channel, migrate it, then let the network resolve the
     *  self-intersection cutoffs and stream-capture collisions the displacement created. */
    public void step(int i, double dx) {
        currentStep = i;
        network.beginStep();
        dumpNetwork("00_original");
        for (Channel ch : network.getChannels()) {
            final double localDx = Math.clamp(ch.intakeWidth()/2,1.5,dx);
            ch.reSample(localDx);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
            migrate(ch, localDx);
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
            final double localDx = Math.clamp(ch.intakeWidth()/2,1.5,dx);
            ch.reSample(localDx);
            ch.spline = QuinticHermiteSpline.createCatmullRom(ch.spline.points());
        }
        dumpNetwork("05_final");
        network.recordState(i);
    }

    /** Dumps the whole network into {@code step_<currentStep>/<name>.png} when
     *  {@link DebugConfig#DEBUG_STEPS}. */
    private void dumpNetwork(String name) {
        if (!DebugConfig.DEBUG_STEPS) return;
        Debug.river.seeNetwork(network, "step_" + currentStep, name);
    }

    /** Fades migration to zero near the grid border, keeping a channel's whole carve band inside the
     *  grid. Wider channels are confined further in. Endpoints are pinned by callers, so only they may
     *  sit near the border. */
    protected double borderDamping(double x, double z, double width) {
        final int gridSize = network.getGridSize();
        final double margin = Math.min(HydrologyTuning.MARGIN_INFLUENCE_FACTOR * width, MAX_MARGIN_FRACTION * gridSize);
        if (margin <= 0) return 1.0;
        final double distToBorder = Math.min(Math.min(x, z), Math.min(gridSize - 1 - x, gridSize - 1 - z));
        // Inner padding [0, margin]: hard-zero. Beyond it, ramp 0→1 over the next margin.
        return Math.clamp((distToBorder - margin) / margin, 0.0, 1.0);
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

    public List<double[]> getChannelPts(int channelId) {
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
}
