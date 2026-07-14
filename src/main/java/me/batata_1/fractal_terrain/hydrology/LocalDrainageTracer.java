package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.GRID;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PAD;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleLocal;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.computeFlow;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.neighbor;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.jetbrains.annotations.Nullable;

/**
 * Responsibility: the detailed <em>local</em> river network, traced directly from the per-tile drainage
 * field (flow accumulation → reach test → segment walk → channel build), excluding pixels already
 * claimed by the global-river trace. This is the deterministic core exercised headlessly by
 * {@code LocalRiverGoldenTest} via {@link LocalRiverProvider#traceLocalNetworkForTest}.
 *
 * <p>Collaborators: {@link HydrologyTileGeometry} (tile geometry + local-frame sampling);
 * {@link PipelinePreprocessing} (flow accumulation, neighbour resolution); {@link Channel} /
 * {@link QuinticHermiteSpline} (traced-segment geometry); consumed by
 * {@code LocalRiverProvider.buildTile} (steps 5–7).
 *
 * <p>Invariants: purely functional over its parameters — no shared mutable state, so per-tile traces
 * from different worker threads never interact. A channel is only emitted when its whole segment stays
 * interior to the tile ({@link #leavesTile}); this is unchanged from the original
 * {@code LocalRiverProvider.traceLocalNetwork}.
 */
final class LocalDrainageTracer {

    private LocalDrainageTracer() {}

    private static final float FLOW_THRESHOLD = 40f;

    static boolean[] rasterizeGlobalMask(List<Channel> channels) {
        final boolean[] mask = new boolean[GRID * GRID];
        for (Channel ch : channels) {
            if (ch.numPts() < 2) continue;
            final QuinticHermiteSpline spline = ch.spline;
            final int segments = ch.numPts() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength = VectorOps.distance(
                        spline.points().get(i), spline.points().get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / 0.5));
                for (int s = 0; s <= sampleCount; s++) {
                    final double[] p = spline.sample(i + (double) s / sampleCount);
                    final int xi = (int) Math.round(p[0] - PAD);
                    final int zi = (int) Math.round(p[1] - PAD);
                    if (xi < 0 || zi < 0 || xi >= GRID || zi >= GRID) continue;
                    mask[xi * GRID + zi] = true;
                }
            }
        }
        return mask;
    }

    static List<Channel> traceLocalNetwork(
            int[] drainage, float[] elev, boolean[] globalMask, @Nullable LocalRiverProvider.Stages stages) {
        final int cellCount = GRID * GRID;
        final float[] flow = computeFlow(drainage, GRID);
        final boolean[] reaches = computeReaches(drainage, elev, globalMask);
        final boolean[] riverMask = new boolean[cellCount];
        for (int i = 0; i < cellCount; i++) riverMask[i] = flow[i] >= FLOW_THRESHOLD && reaches[i] && !globalMask[i];

        final int[] downstream = new int[cellCount];
        final int[] inDegree = new int[cellCount];
        for (int cell = 0; cell < cellCount; cell++) {
            downstream[cell] = -1;
            if (!riverMask[cell]) continue;
            final int direction = neighbor(drainage[cell]);
            if (direction == -1) continue;
            final int next = PipelinePreprocessing.neighborIndex(cell, direction, GRID);
            if (next == -1 || !riverMask[next]) continue;
            downstream[cell] = next;
            inDegree[next]++;
        }

        final List<Channel> channels = new ArrayList<>();
        int channelId = 0;
        for (int start = 0; start < cellCount; start++) {
            if (!riverMask[start]) continue;
            final boolean isSource = inDegree[start] == 0;
            final boolean isJunction = inDegree[start] >= 2;
            if (!isSource && !isJunction) continue;
            final List<Integer> segmentCells = walkSegment(start, downstream, inDegree);
            if (segmentCells.size() < 2 || leavesTile(segmentCells)) continue;
            final Channel channel = buildLocalChannel(segmentCells, flow, channelId++);
            if (channel != null) channels.add(channel);
        }
        if (stages != null) {
            stages.flow = flow;
            stages.riverMask = riverMask;
        }
        return channels;
    }

    private static List<Integer> walkSegment(int start, int[] downstream, int[] inDegree) {
        final List<Integer> cells = new ArrayList<>();
        cells.add(start);
        int current = start;
        for (int step = 0; step < GRID * GRID; step++) {
            final int next = downstream[current];
            if (next == -1) break;
            cells.add(next);
            if (inDegree[next] >= 2) break;
            current = next;
        }
        return cells;
    }

    private static boolean leavesTile(List<Integer> cells) {
        return onBorder(cells.getFirst()) || onBorder(cells.getLast());
    }

    private static boolean onBorder(int cell) {
        final int x = cell / GRID;
        final int z = cell % GRID;
        return x == 0 || z == 0 || x == GRID - 1 || z == GRID - 1;
    }

    private static boolean[] computeReaches(int[] drainage, float[] elev, boolean[] globalMask) {
        final int n = drainage.length;
        final int[] down = new int[n];
        for (int c = 0; c < n; c++) {
            final int dir = neighbor(drainage[c]);
            down[c] = (dir == -1) ? -1 : PipelinePreprocessing.neighborIndex(c, dir, GRID);
        }
        final int[] head = new int[n + 1];
        for (int c = 0; c < n; c++) if (down[c] != -1) head[down[c] + 1]++;
        for (int c = 0; c < n; c++) head[c + 1] += head[c];
        final int[] upstream = new int[head[n]];
        final int[] cursor = head.clone();
        for (int c = 0; c < n; c++) if (down[c] != -1) upstream[cursor[down[c]]++] = c;

        final boolean[] reaches = new boolean[n];
        final int[] queue = new int[n];
        int qHead = 0;
        int qTail = 0;
        for (int c = 0; c < n; c++) {
            if (globalMask[c] || elev[c] < 0) {
                reaches[c] = true;
                queue[qTail++] = c;
            }
        }
        while (qHead < qTail) {
            final int d = queue[qHead++];
            for (int k = head[d]; k < head[d + 1]; k++) {
                final int u = upstream[k];
                if (!reaches[u]) {
                    reaches[u] = true;
                    queue[qTail++] = u;
                }
            }
        }
        return reaches;
    }

    private static @Nullable Channel buildLocalChannel(List<Integer> cells, float[] flow, int channelId) {
        final ArrayList<double[]> points = new ArrayList<>(cells.size());
        float maxFlow = 0;
        for (int cell : cells) {
            points.add(new double[] {(double) cell / GRID + 0.5, cell % GRID + 0.5});
            maxFlow = Math.max(maxFlow, flow[cell]);
        }
        final double startWidth = FractalTerrainConfig.widthFromFlow(flow[cells.getFirst()]);
        final double endWidth = FractalTerrainConfig.widthFromFlow(flow[cells.getLast()]);
        final Channel channel = new Channel(FractalTerrainConfig.widthFromFlow(maxFlow), points, channelId);
        channel.setWidthProfile(startWidth, endWidth);
        if (!channel.isResampleable()) return null; // degenerate geometry: skip this channel
        try {
            channel.reSample(HydrologyTuning.RESAMPLE_DIST);
        } catch (RuntimeException runaway) {
            // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); drop the channel.
            return null;
        }
        return channel;
    }

    /**
     * Resample a local channel at {@code dx = width/2} and add its points as RIVER
     * {@link HydrologicalUnit}s, stamping each with the channel normal and a shared feature id pulled from
     * {@code nextFeatureId} (one id per channel, advancing the counter).
     */
    static void addLocalChannelUnits(List<HydrologicalUnit> out, Channel ch, float[] elev, int[] nextFeatureId) {
        // Spacing must be <= half the NARROWEST width along the start->end taper, so consecutive
        // units' width/2 discs always overlap (gap-free membership test + girth rendering).
        final double narrowestWidth = Math.min(ch.startWidth, ch.endWidth);
        final double dx = Math.max(narrowestWidth / 2.0, 0.5);
        if (!ch.spline.isResampleable()) return; // degenerate geometry: nothing to add
        final QuinticHermiteSpline resampled;
        try {
            resampled = ch.spline.reSample(dx);
        } catch (RuntimeException runaway) {
            // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); add no units.
            return;
        }
        final List<double[]> pts = resampled.points();
        final int n = pts.size();
        // Sample each point's shell reference off the already-globally-carved buffer, then force it
        // monotone non-increasing downstream (index 0..n-1 is upstream..downstream) so a local channel
        // never floats above its own upstream point.
        final double[] reference = new double[n];
        for (int i = 0; i < n; i++) {
            final double[] p = pts.get(i);
            final double sampled = sampleLocal(elev, p[0], p[1]);
            reference[i] = (i == 0) ? sampled : Math.min(reference[i - 1], sampled);
        }
        final int featureId = nextFeatureId[0]++;
        for (int i = 0; i < n; i++) {
            final double[] p = pts.get(i);
            final double frac = (n <= 1) ? 0.0 : (double) i / (n - 1);
            final double w = ch.startWidth + (ch.endWidth - ch.startWidth) * frac;
            final double[] nrm = resampled.normal(i);
            out.add(new HydrologicalUnit(
                    HydrologicalFeature.RIVER,
                    HydrologicalUnit.RosgenType.A,
                    new double[] {p[0], p[1]},
                    new double[] {nrm[0], nrm[1]},
                    w,
                    reference[i],
                    0,
                    featureId));
        }
    }
}
