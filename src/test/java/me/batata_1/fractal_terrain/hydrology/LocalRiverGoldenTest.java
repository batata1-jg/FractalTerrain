package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import org.junit.jupiter.api.Test;

/**
 * Headless golden gate for the deterministic local-network trace of {@link LocalRiverProvider} (M-005):
 * split out of the pipeline-coupled {@code debug.tests.LocalRiverTest} manual harness (which stays as a
 * PNG dumper). The unified trace attaches local segments directly onto a live {@link RiverNetwork}, so
 * this seam feeds a synthetic global network (a single central trunk channel) instead of a boolean mask,
 * runs the exact production {@link LocalRiverProvider#traceLocalNetworkForTest} path over it, and asserts
 * structural invariants rather than a frozen checksum: local output legitimately changes with the
 * uncalibrated {@code LOCAL_ATTACH_RADIUS} (DL-010), so a bit-exact checksum would demand re-baselining on
 * every calibration pass and cannot even compile against the new void/mask-free seam.
 */
class LocalRiverGoldenTest {

    private static final int GRID = LocalRiverProvider.gridSizeForTest();

    /** Distance (rows) from the trunk to each ridge crest (the watershed / source line). */
    private static final int RIDGE_OFFSET = 80;

    /** {@link RiverNetwork#insertSpecs} mints channel ids from 0 on a fresh network -- the synthetic
     *  trunk (the network's only constructor-supplied edge) is always channel 0. */
    private static final int TRUNK_CHANNEL_ID = 0;

    /**
     * Deterministic synthetic heightmap over the {@link #GRID} tile, shaped as a symmetric double-ridge
     * valley (a function of the row distance {@code a = |i - GRID/2|} plus seeded noise):
     * <ul>
     *   <li>a central valley at {@code i = GRID/2} (the global-river trunk, {@code +2}),
     *   <li>rising to a ridge crest at {@code a = RIDGE_OFFSET} ({@code +60}) — the interior source line,
     *   <li>then falling back toward the border ({@code +20}).
     * </ul>
     * The profile is piecewise-monotonic (no enclosed pits for {@code fillSinks} to flood), and the valley
     * connects to the tile border along its ends so it is never flooded either. Cells on the inner limb
     * drain to the trunk; cells on the outer limb drain to the border (land, so no outlet → no rivers).
     * The synthetic global network's trunk channel runs exactly along the valley floor, so channels walk
     * ridge → trunk, both interior, and survive {@code leavesTile}.
     */
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

    /** Steepest-descent drainage over {@code filled}, matching {@code LocalRiverProvider.buildTile}. */
    private static int[] drainageOf(float[] filled) {
        final float[] uniformWeight = new float[GRID * GRID];
        Arrays.fill(uniformWeight, 1f);
        return Drainage.computeDrainageDirection(filled, uniformWeight, GRID);
    }

    /**
     * A synthetic global network: a single SOURCE→DRAIN trunk channel running the length of the tile
     * along {@code z = GRID/2} (the valley floor {@link #syntheticElevation} carves), wide enough that
     * every ridge-side local segment's downstream end lands within {@code LOCAL_ATTACH_RADIUS} of some
     * trunk point. Built via {@link RiverNetwork}'s public constructor, exactly as {@code
     * GlobalNetworkBuilder} would build a real one, but with no Meanders relaxation applied (the trace
     * being tested reads the network's channel points directly, not a relaxed shape).
     */
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
        final LocalRiverProvider provider = new LocalRiverProvider(null);
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

    /**
     * Determinism pre-check (mirrors the M-004 pattern): the synthetic heightmap, drainage, and trace all
     * derive from a fixed seed and touch no other state, so 5 independent runs are expected to be — and
     * were confirmed — bit-identical; no canonicalization or tolerance was needed.
     */
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
