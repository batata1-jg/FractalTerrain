package me.batata_1.fractal_terrain.hydrology.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HistoricPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.OxbowLakePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * The history deque: what a cutoff sheds, how long it is kept, and what frame it is emitted in.
 *
 * <p>Every production caller builds with history off, so without this gate nothing in the history path
 * is executed by any test at all.
 */
class RiverNetworkHistoryTest {

    private static final int GRID = 512;
    private static final double RESAMPLE_DIST = 2.0;
    private static final int MAX_SAVED_STATES = 3;

    /** Flow 100 gives width 4.0, so manageCutoffs searches a radius of sqrt(4) = 2.0. */
    private static final double FLOW = 100.0;

    /** Return-leg offset, well inside that 2.0 radius so the fold is found whatever the resample does. */
    private static final double HAIRPIN_GAP = 1.0;

    /** A channel that folds back over itself inside the cutoff search radius, so a cut is forced. */
    private static List<double[]> hairpin(double baseX, double baseZ) {
        final List<double[]> pts = new ArrayList<>();
        for (double x = baseX; x <= baseX + 100.0; x += 2.0) pts.add(new double[] {x, baseZ});
        for (double x = baseX + 100.0; x >= baseX + 10.0; x -= 2.0) pts.add(new double[] {x, baseZ + HAIRPIN_GAP});
        for (double z = baseZ + HAIRPIN_GAP; z <= baseZ + 100.0; z += 2.0) pts.add(new double[] {baseX + 10.0, z});
        return pts;
    }

    /** Two disjoint hairpins, so two cutoffs can be driven at two different steps. */
    private static RiverNetwork twoHairpins() {
        final List<double[]> a = hairpin(100.0, 100.0);
        final List<double[]> b = hairpin(300.0, 300.0);
        final double[] aHead = a.get(0);
        final double[] aTail = a.get(a.size() - 1);
        final double[] bHead = b.get(0);
        final double[] bTail = b.get(b.size() - 1);
        final List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(aHead[0], aHead[1], Endpoint.Type.SOURCE),
                new NodeSpec(aTail[0], aTail[1], Endpoint.Type.DRAIN),
                new NodeSpec(bHead[0], bHead[1], Endpoint.Type.SOURCE),
                new NodeSpec(bTail[0], bTail[1], Endpoint.Type.DRAIN));
        final List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, a, FLOW), new EdgeSpec(2, 3, b, FLOW));
        return new RiverNetwork(GRID, nodeSpecs, edgeSpecs, true, MAX_SAVED_STATES, RESAMPLE_DIST);
    }

    /** Reads the deque without changing it; remapHistory is the only window onto history. */
    private static List<HydrologicalPrimitive> history(RiverNetwork net) {
        final List<HydrologicalPrimitive> seen = new ArrayList<>();
        net.remapHistory(p -> {
            seen.add(p);
            return p;
        });
        return seen;
    }

    /** Types every point A; the classifier is not what these tests are about. */
    private static ChannelTyper flatTyper() {
        return new ChannelTyper() {
            @Override
            public void prepare(RiverNetwork network) {}

            @Override
            public RosgenType[] typesFor(Channel channel) {
                final RosgenType[] types = new RosgenType[channel.numPts()];
                Arrays.fill(types, RosgenType.A);
                return types;
            }
        };
    }

    @Test
    void aCutoffShedsOxbowsStampedWithTheStepAndTheChannelWidth() {
        final RiverNetwork net = twoHairpins();

        net.manageCutoffs(net.getChannels().get(0), 3);

        final List<HydrologicalPrimitive> shed = history(net);
        assertFalse(shed.isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");
        for (final HydrologicalPrimitive p : shed) {
            assertTrue(p instanceof OxbowLakePrimitive, "a cutoff sheds oxbows, not " + p.getClass());
            final OxbowLakePrimitive oxbow = (OxbowLakePrimitive) p;
            assertEquals((byte) 3, oxbow.time(), "the cut step is the primitive's age");
            assertTrue(oxbow.width() > 0, "width comes from the channel it was cut out of");
            assertEquals(0.0, oxbow.influence(), 1e-12, "influence is resolved later, not at mint");
            assertEquals(0.0, oxbow.elevation(), 1e-12, "elevation is resolved later, not at mint");
        }
    }

    @Test
    void dropsHistoryOlderThanTheStepWindow() {
        final RiverNetwork net = twoHairpins();
        final List<Channel> channels = net.getChannels();

        net.manageCutoffs(channels.get(0), 1);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the first hairpin produced no cutoff");

        // MAX_SAVED_STATES is 3, so at step 9 the step-1 entries are five steps past the window.
        net.manageCutoffs(channels.get(1), 9);

        final List<HydrologicalPrimitive> shed = history(net);
        assertFalse(shed.isEmpty(), "fixture is degenerate: the second hairpin produced no cutoff");
        for (final HydrologicalPrimitive p : shed) {
            assertEquals((byte) 9, p.time(), "everything outside the window must have been evicted");
        }
    }

    @Test
    void resolutionFillsTheDeferredElevationAndInfluence() {
        final RiverNetwork net = twoHairpins();
        net.manageCutoffs(net.getChannels().get(0), 2);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");

        net.remapHistory(p -> ((HistoricPrimitive) p).resolved(64.0, 12.0));

        for (final HydrologicalPrimitive p : history(net)) {
            final HistoricPrimitive resolved = (HistoricPrimitive) p;
            assertEquals(64.0, resolved.elevation(), 1e-12);
            assertEquals(12.0, resolved.getRadius(), 1e-12, "a resolved primitive finally has a footprint");
            assertEquals((byte) 2, p.time(), "resolving must not disturb the cut step");
        }
    }

    @Test
    void emissionShiftsShedPrimitivesIntoTheQueriedFrame() {
        final RiverNetwork net = twoHairpins();
        net.manageCutoffs(net.getChannels().get(0), 4);
        final List<HydrologicalPrimitive> shed = history(net);
        assertFalse(shed.isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");
        final HydrologicalPrimitive stored = shed.get(0);
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        stored.getType().addPrimitives(new double[] {10.0, 20.0}, out, stored);

        assertEquals(1, out.size());
        assertEquals(stored.coord()[0] - 10.0, out.get(0).coord()[0], 1e-12);
        assertEquals(stored.coord()[1] - 20.0, out.get(0).coord()[1], 1e-12);
        assertEquals(stored.time(), out.get(0).time(), "the shift must not disturb the cut step");
    }

    @Test
    void collectEmitsShedPrimitivesAlongsideTheLiveNetwork() {
        final RiverNetwork net = twoHairpins();
        net.manageCutoffs(net.getChannels().get(0), 6);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");
        // collectPrimitives reads a bed elevation per emitted river point; no assigner ran here.
        for (final Channel ch : net.getChannels()) ch.bedElevations = new double[ch.numPts()];

        final List<HydrologicalPrimitive> out =
                net.collectPrimitives(10.0, 20.0, id -> true, flatTyper(), (x, z, bed, w, normal, type) -> 8.0);

        assertTrue(
                out.stream().anyMatch(p -> p instanceof OxbowLakePrimitive),
                "history must reach the same list the carve collects through");
    }
}
