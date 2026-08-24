package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import org.junit.jupiter.api.Test;

/**
 * Headless gate on the local-network trace, driving the production path over a synthetic global network
 * so no ONNX pipeline is needed.
 *
 * <p>Asserts structural invariants rather than a frozen checksum: local output legitimately shifts with
 * the uncalibrated {@code LOCAL_ATTACH_RADIUS}, so a checksum would demand re-baselining on every
 * calibration pass. {@code debug.tests.RiverTest} remains the PNG-dumping counterpart.
 */
class RiverGoldenTest {

    private static final int GRID = RiverProvider.gridSizeForTest();

    /** Distance (rows) from the trunk to each ridge crest (the watershed / source line). */
    private static final int RIDGE_OFFSET = 80;

    /** {@link RiverNetwork}'s constructor resets channel ids from 0, so the synthetic trunk is always channel 0. */
    private static final int TRUNK_CHANNEL_ID = 0;

    /** Symmetric double-ridge valley over {@link #GRID}: trunk at the center row, ridge crest at {@link
     *  #RIDGE_OFFSET}, falling to the border beyond — piecewise-monotonic so no interior pit needs
     *  {@code fillSinks}, and both limbs drain somewhere testable (trunk vs. border). */
    private static float[] syntheticElevation(long seed) {
        final Random rng = new Random(seed);
        final double mid = GRID / 2.0;
        final float[] elevation = new float[GRID * GRID];
        for (int i = 0; i < GRID; i++) {
            final double a = Math.abs(i - mid);
            final double h;
            if (a <= RIDGE_OFFSET) {
                h = 2.0 + 58.0 * (a / RIDGE_OFFSET); // trunk (+2) → ridge crest (+60)
            } else {
                h = 60.0 - 40.0 * ((a - RIDGE_OFFSET) / (mid - RIDGE_OFFSET)); // crest (+60) → border (+20)
            }
            for (int j = 0; j < GRID; j++) {
                elevation[i * GRID + j] = (float) (h + (rng.nextDouble() - 0.5) * 3.0); // tie-breaking noise
            }
        }
        return elevation;
    }

    /** Steepest-descent drainage over {@code filled}, matching {@code RiverProvider.buildTile}. */
    private static int[] drainageOf(float[] filled) {
        return Drainage.computeDrainageDirection(filled, GRID);
    }

    /** Single SOURCE→DRAIN trunk along the valley floor {@link #syntheticElevation} carves, wide enough
     *  that every ridge-side segment's downstream end lands within attach radius. Skips Meanders
     *  relaxation since the trace under test reads channel points directly. */
    private static RiverNetwork syntheticGlobalNetwork() {
        final int mid = GRID / 2;
        final ArrayList<double[]> trunkPts = new ArrayList<>();
        for (int j = 0; j <= GRID; j += 4) trunkPts.add(new double[] {mid, j});
        final List<RiverNetwork.NodeSpec> nodeSpecs = List.of(
                new RiverNetwork.NodeSpec(trunkPts.getFirst()[0], trunkPts.getFirst()[1], Endpoint.Type.SOURCE),
                new RiverNetwork.NodeSpec(trunkPts.getLast()[0], trunkPts.getLast()[1], Endpoint.Type.DRAIN));
        final List<RiverNetwork.EdgeSpec> edgeSpecs = List.of(new RiverNetwork.EdgeSpec(0, 1, trunkPts, 4.0));
        return new RiverNetwork(GRID, nodeSpecs, edgeSpecs, false, 0, 2.0);
    }

    /** Runs the production trace over a fresh synthetic network/elevation pair; returns the mutated network. */
    private static RiverNetwork trace(long seed) {
        final RiverProvider provider = new RiverProvider(null);
        // buildTile computes drainage on the sink-FILLED elevation and passes that same filled field to the
        // trace, so flow reaches outlets rather than stalling in interior depressions; mirror that here.
        final float[] filled = Drainage.fillSinks(syntheticElevation(seed), GRID, 0);
        final int[] drainage = drainageOf(filled);
        final RiverNetwork network = syntheticGlobalNetwork();
        provider.traceLocalNetworkForTest(drainage, filled, network);
        return network;
    }

    /** Follow outgoing edges downstream from {@code node}; the full node sequence source→…→drain. */
    private static List<Endpoint> downstreamPath(RiverNetwork network, Endpoint node) {
        final List<Endpoint> path = new ArrayList<>();
        Endpoint current = node;
        for (int guard = 0; current != null && guard <= GRID * GRID; guard++) {
            path.add(current);
            if (current.outgoing == -1) break;
            final Channel channel = network.getChannel(current.outgoing);
            if (channel == null) break;
            current = network.getNode(channel.endNodeId);
        }
        return path;
    }

    /** Converts a channel-endpoint coordinate from the padded/native graph frame back to the true
     *  {@code GRID} tile frame the local trace's own {@code leavesTile} check enforces against. */
    private static boolean interiorInGridFrame(double[] paddedPoint) {
        final double x = paddedPoint[0] - HydrologyTileGeometry.PAD;
        final double z = paddedPoint[1] - HydrologyTileGeometry.PAD;
        return x > 0 && z > 0 && x < GRID - 1 && z < GRID - 1;
    }

    @Test
    void localNetworkAttachesToGlobalTrunk() {
        final RiverNetwork network = trace(7);

        final long localSourceCount = network.getNodes().stream()
                        .filter(n -> n.type == Endpoint.Type.SOURCE)
                        .count()
                - 1; // minus the synthetic trunk's own source
        assertTrue(localSourceCount > 0, "synthetic field produced no local channels — fixture is degenerate");

        final boolean junctionMinted = network.getNodes().stream().anyMatch(n -> n.type == Endpoint.Type.JUNCTION);
        assertTrue(
                junctionMinted, "no local channel attached to the global trunk (expected a split()-minted JUNCTION)");
    }

    @Test
    void everySourceToDrainBedIsMonotoneNonIncreasing() {
        final RiverNetwork network = trace(7);
        // Assign bed elevations exactly as buildTile does, so the monotonicity guarantee (assign's
        // contract) can actually be checked. assign()'s clamp requires every terminal drain's floor stay
        // <= every upstream source's ceiling, so boundary data must itself be physically sensible
        // (sources high, drains low) -- a flat decoded-terrain sampler plus these two constants is enough
        // to exercise the propagation without violating that precondition.
        final Map<Integer, Double> boundaryElev = new HashMap<>();
        for (Endpoint n : network.getNodes()) {
            if (n.type == Endpoint.Type.SOURCE) boundaryElev.put(n.id, 100.0);
            else if (n.type == Endpoint.Type.DRAIN) boundaryElev.put(n.id, 0.0);
        }
        final float[] flatTerrain = new float[GRID * GRID];
        ChannelElevationAssigner.assign(network, boundaryElev, flatTerrain);

        int sequences = 0;
        for (Endpoint source : network.getNodes()) {
            if (source.type != Endpoint.Type.SOURCE) continue;
            sequences++;
            final List<Endpoint> path = downstreamPath(network, source);
            for (int i = 1; i < path.size(); i++) {
                final double prevElev = path.get(i - 1).elevation;
                final double curElev = path.get(i).elevation;
                if (Double.isNaN(prevElev) || Double.isNaN(curElev)) continue;
                assertTrue(
                        curElev <= prevElev + 1e-9,
                        "elevation rises downstream at node " + path.get(i - 1).id + " -> " + path.get(i).id);
            }
        }
        assertTrue(sequences > 0, "no source sequences to check — fixture is degenerate");
    }

    @Test
    void noLocalChannelCrossesTheTileEdge() {
        final RiverNetwork network = trace(7);
        boolean sawLocalChannel = false;
        for (Channel ch : network.getChannels()) {
            // The synthetic trunk itself legitimately runs the length of the tile (it IS the global
            // network); only the channels the local trace attached are held to the interior-only
            // guarantee it enforces via leavesTile before ever inserting a segment.
            if (ch.channelId == TRUNK_CHANNEL_ID) continue;
            final Endpoint start = network.getNode(ch.startNodeId);
            if (start == null || start.type != Endpoint.Type.SOURCE) continue; // a split()-grown global half
            sawLocalChannel = true;
            final double[] first = ch.spline.points().getFirst();
            final double[] last = ch.spline.points().getLast();
            assertTrue(
                    interiorInGridFrame(first), "local channel " + ch.channelId + " starts on/outside the tile edge");
            assertTrue(interiorInGridFrame(last), "local channel " + ch.channelId + " ends on/outside the tile edge");
        }
        assertTrue(sawLocalChannel, "synthetic field produced no local channels — fixture is degenerate");
    }

    /** Confirms the synthetic heightmap, drainage and trace derive from the seed alone: 5 runs are
     *  checked bit-identical, catching hidden nondeterminism the structural assertions alone would miss. */
    @Test
    void localNetworkIsDeterministicAcrossRuns() {
        Long first = null;
        for (int run = 0; run < 5; run++) {
            final long checksum = networkChecksum(trace(7));
            if (first == null) first = checksum;
            else assertEquals(first, checksum, "run " + run + " diverged from run 0");
        }
    }

    /** Order-independent-enough checksum over channel/node counts and every channel's spline points. */
    private static long networkChecksum(RiverNetwork network) {
        final List<Channel> channels = new ArrayList<>(network.getChannels());
        channels.sort((a, b) -> Integer.compare(a.channelId, b.channelId));
        long checksum = 1125899906842597L;
        checksum = 31 * checksum + channels.size();
        checksum = 31 * checksum + network.getNodes().size();
        for (final Channel channel : channels) {
            checksum = 31 * checksum + channel.channelId;
            for (final double[] point : channel.spline.points()) {
                checksum = 31 * checksum + Double.doubleToLongBits(point[0]);
                checksum = 31 * checksum + Double.doubleToLongBits(point[1]);
            }
        }
        return checksum;
    }
}
