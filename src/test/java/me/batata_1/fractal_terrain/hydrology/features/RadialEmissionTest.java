package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * What the network hands the radial family. The radius rule is the whole point: a bowl sized off a
 * channel that emitted no river primitives would carve into terrain nothing else touched.
 */
class RadialEmissionTest {

    private static final int GRID = 512;
    private static final double RESAMPLE_DIST = 4.0;

    private static final double[] SOURCE_A = {100.0, 150.0};
    private static final double[] SOURCE_B = {400.0, 150.0};
    private static final double[] JUNCTION = {250.0, 256.0};
    private static final double[] DRAIN = {250.0, 400.0};

    /** Two sources into one junction into a drain: the smallest graph with a real confluence. */
    private static RiverNetwork confluenceNetwork() {
        final List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(SOURCE_A[0], SOURCE_A[1], Endpoint.Type.SOURCE), // 0
                new NodeSpec(SOURCE_B[0], SOURCE_B[1], Endpoint.Type.SOURCE), // 1
                new NodeSpec(JUNCTION[0], JUNCTION[1], Endpoint.Type.JUNCTION), // 2
                new NodeSpec(DRAIN[0], DRAIN[1], Endpoint.Type.DRAIN)); // 3
        final List<EdgeSpec> edgeSpecs = List.of(
                new EdgeSpec(0, 2, segment(SOURCE_A, JUNCTION, 24), 8.0),
                new EdgeSpec(1, 2, segment(SOURCE_B, JUNCTION, 24), 8.0),
                new EdgeSpec(2, 3, segment(JUNCTION, DRAIN, 24), 12.0));
        return new RiverNetwork(GRID, nodeSpecs, edgeSpecs, false, 0, RESAMPLE_DIST);
    }

    private static ArrayList<double[]> segment(double[] a, double[] b, int n) {
        final ArrayList<double[]> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            final double t = (double) i / n;
            pts.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
        }
        return pts;
    }

    private static IntSet allChannelIds(RiverNetwork net) {
        final IntSet ids = new IntOpenHashSet();
        net.getChannels().forEach(ch -> ids.add(ch.channelId));
        return ids;
    }

    private static Endpoint nodeOfType(RiverNetwork net, Endpoint.Type type) {
        return net.getNodes().stream()
                .filter(n -> n.type == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixture has no " + type + " node"));
    }

    @Test
    void confluenceTakesTheWidestChannelMeetingAtTheJunction() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        junction.elevation = 64.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.CONFLUENCE.addPrimitives(new double[] {0.0, 0.0}, out, junction, net, allChannelIds(net));

        assertEquals(1, out.size(), "one disc per junction");
        final ConfluencePrimitive bowl = (ConfluencePrimitive) out.get(0);
        // The trunk carries both tributaries' flow, so it is the widest thing meeting here.
        final double trunkWidth = net.getChannel(junction.outgoing).widthAt(0);
        assertEquals(trunkWidth, bowl.width(), 1e-12);
        assertEquals(64.0, bowl.elevation(), 1e-12);
    }

    @Test
    void confluenceSkipsAJunctionWhoseChannelsDidNotEmit() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        junction.elevation = 64.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        // Only the outgoing trunk emitted: one incident channel is not two, so there is no confluence.
        final IntSet trunkOnly = new IntOpenHashSet();
        trunkOnly.add(junction.outgoing);

        HydrologicalFeature.CONFLUENCE.addPrimitives(new double[] {0.0, 0.0}, out, junction, net, trunkOnly);

        assertTrue(out.isEmpty(), "a bowl needs at least two emitting channels to be sized from");
    }

    @Test
    void confluenceSkipsAJunctionWithNoAssignedElevation() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        // Endpoint.elevation is NaN until ChannelElevationAssigner runs; a NaN rim carves a NaN bowl.
        junction.elevation = Double.NaN;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.CONFLUENCE.addPrimitives(new double[] {0.0, 0.0}, out, junction, net, allChannelIds(net));

        assertTrue(out.isEmpty(), "an unassigned rim must not reach the carve");
    }

    @Test
    void sourceTakesTheWidthOfTheChannelLeavingIt() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint source = nodeOfType(net, Endpoint.Type.SOURCE);
        source.elevation = 96.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.SOURCE.addPrimitives(new double[] {0.0, 0.0}, out, source, net, allChannelIds(net));

        assertEquals(1, out.size());
        final SourcePrimitive spring = (SourcePrimitive) out.get(0);
        assertEquals(net.getChannel(source.outgoing).widthAt(0), spring.width(), 1e-12);
        assertEquals(96.0, spring.elevation(), 1e-12);
        assertTrue(spring.width() >= HydrologyTuning.MIN_WIDTH, "width comes through widthFromFlow");
    }

    @Test
    void shiftsTheStoredCoordIntoTheQueriedFrame() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        junction.elevation = 64.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.CONFLUENCE.addPrimitives(new double[] {10.0, 20.0}, out, junction, net, allChannelIds(net));

        assertEquals(junction.coord[0] - 10.0, out.get(0).coord()[0], 1e-12);
        assertEquals(junction.coord[1] - 20.0, out.get(0).coord()[1], 1e-12);
    }
}
