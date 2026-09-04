package me.batata_1.fractal_terrain.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import me.batata_1.fractal_terrain.hydrology.Drainage;
import me.batata_1.fractal_terrain.hydrology.LocalDrainageTracer;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.providers.RiverProvider;
import org.junit.jupiter.api.Test;

/**
 * Proves the {@link HydrologyConfig} injected into {@link LocalDrainageTracer#traceLocalNetwork} is
 * actually read, not decorative: raising {@code getFlowThreshold()} through a custom config suppresses
 * every local channel the default {@link StaticHydrologyConfig} traces from the same fixture.
 */
class HydrologyConfigInjectionTest {

    private static final int GRID = RiverProvider.gridSizeForTest();

    /** Distance (rows) from the trunk to each ridge crest, mirroring {@code RiverGoldenTest}'s fixture. */
    private static final int RIDGE_OFFSET = 80;

    /** Symmetric double-ridge valley, copied from {@code RiverGoldenTest.syntheticElevation}: trunk at the
     *  center row, ridge crest at {@link #RIDGE_OFFSET}, falling to the border beyond. */
    private static float[] syntheticElevation(long seed) {
        final Random rng = new Random(seed);
        final double mid = GRID / 2.0;
        final float[] elevation = new float[GRID * GRID];
        for (int i = 0; i < GRID; i++) {
            final double a = Math.abs(i - mid);
            final double h;
            if (a <= RIDGE_OFFSET) {
                h = 2.0 + 58.0 * (a / RIDGE_OFFSET);
            } else {
                h = 60.0 - 40.0 * ((a - RIDGE_OFFSET) / (mid - RIDGE_OFFSET));
            }
            for (int j = 0; j < GRID; j++) {
                elevation[i * GRID + j] = (float) (h + (rng.nextDouble() - 0.5) * 3.0);
            }
        }
        return elevation;
    }

    /** Single SOURCE->DRAIN trunk along the valley floor, copied from
     *  {@code RiverGoldenTest.syntheticGlobalNetwork}. */
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

    /** Traces a fresh fixture with {@code config}; {@code gradMag} is uniformly above
     *  {@code GRAD_THRESHOLD} so the grad gate never suppresses a channel on its own — only the flow gate,
     *  which {@code config} controls, is under test. */
    private static RiverNetwork traceWith(HydrologyConfig config) {
        final float[] filled = Drainage.fillSinks(syntheticElevation(7), GRID, 0);
        final int[] drainage = Drainage.computeDrainageDirection(filled, GRID);
        final RiverNetwork network = syntheticGlobalNetwork();
        final float[] humidity = new float[GRID * GRID];
        final float[] gradMag = new float[GRID * GRID];
        Arrays.fill(gradMag, 1000f);
        LocalDrainageTracer.traceLocalNetwork(drainage, filled, humidity, gradMag, network, null, config);
        return network;
    }

    private static long localSourceCount(RiverNetwork network) {
        return network.getNodes().stream()
                        .filter(n -> n.type == Endpoint.Type.SOURCE)
                        .count()
                - 1; // minus the synthetic trunk's own source
    }

    @Test
    void injectedDefaultConfigTracesLocalChannels() {
        final RiverNetwork network = traceWith(StaticHydrologyConfig.INSTANCE);
        assertTrue(
                localSourceCount(network) > 0,
                "synthetic field produced no local channels with the default injected config — fixture is degenerate");
    }

    @Test
    void raisingFlowThresholdViaInjectedConfigSuppressesLocalChannels() {
        final long defaultCount = localSourceCount(traceWith(StaticHydrologyConfig.INSTANCE));
        assertTrue(defaultCount > 0, "fixture is degenerate — nothing to suppress");

        final HydrologyConfig unreachableFlowGate = new HydrologyConfig() {
            @Override
            public double getDx() {
                return StaticHydrologyConfig.INSTANCE.getDx();
            }

            @Override
            public double getResampleDist() {
                return StaticHydrologyConfig.INSTANCE.getResampleDist();
            }

            @Override
            public double getLocalAttachRadius() {
                return StaticHydrologyConfig.INSTANCE.getLocalAttachRadius();
            }

            @Override
            public double getMinWidth() {
                return StaticHydrologyConfig.INSTANCE.getMinWidth();
            }

            @Override
            public double getMaxWidth() {
                return StaticHydrologyConfig.INSTANCE.getMaxWidth();
            }

            @Override
            public double getWidthFlowScale() {
                return StaticHydrologyConfig.INSTANCE.getWidthFlowScale();
            }

            @Override
            public float getFlowThreshold() {
                return Float.MAX_VALUE;
            }

            @Override
            public float getGradThreshold() {
                return StaticHydrologyConfig.INSTANCE.getGradThreshold();
            }

            @Override
            public double getMinInfluenceRadius() {
                return StaticHydrologyConfig.INSTANCE.getMinInfluenceRadius();
            }

            @Override
            public double getMaxInfluenceRadius() {
                return StaticHydrologyConfig.INSTANCE.getMaxInfluenceRadius();
            }

            @Override
            public double getInfluenceDepthFactor() {
                return StaticHydrologyConfig.INSTANCE.getInfluenceDepthFactor();
            }

            @Override
            public float getFlowInitialLocal() {
                return StaticHydrologyConfig.INSTANCE.getFlowInitialLocal();
            }

            @Override
            public float getFlowInitialGlobal() {
                return StaticHydrologyConfig.INSTANCE.getFlowInitialGlobal();
            }

            @Override
            public float getFlowPerCellLocal() {
                return StaticHydrologyConfig.INSTANCE.getFlowPerCellLocal();
            }

            @Override
            public float getFlowPerCellGlobal() {
                return StaticHydrologyConfig.INSTANCE.getFlowPerCellGlobal();
            }
        };

        final long suppressedCount = localSourceCount(traceWith(unreachableFlowGate));
        assertEquals(
                0,
                suppressedCount,
                "raising FLOW_THRESHOLD via injected config suppressed all local channels — config path did not"
                        + " take effect");
    }
}
