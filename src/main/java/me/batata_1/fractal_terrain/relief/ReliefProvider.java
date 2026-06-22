package me.batata_1.fractal_terrain.relief;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.DECODER_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.RELIEF_CHANNELS;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.X;
import static me.batata_1.fractal_terrain.FractalTerrainConfig.Z;
import static me.batata_1.fractal_terrain.FractalTerrainInstance.pipeline;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing;
import me.batata_1.fractal_terrain.hydrology.RiverData;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Meanders;
import me.batata_1.fractal_terrain.hydrology.meanders.Node;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

//
//After building the elevations of every junction, recalculate the elevations all the channels in the following way: start with the elev of the start point (source/junction)
//then lerp max(decoded,junciton/drain/ channel endpoint elev) to junction/drain/ channel endpoint elev according to num spline points away.
//

/**
 * Builds final relief tiles ({@code [RELIEF_CHANNELS=8, 512, 512]}) by decoding the diffusion
 * residual and carving the global river network into the elevation.
 *
 * <p>Channel layout: {@code [0]} elev (carved) {@code [1]} blurredElev {@code [2]} gradX
 * {@code [3]} gradY {@code [4]} refinedGrad {@code [5]} lowFreqGrad {@code [6]} res
 * {@code [7]} riverData (D8 drainage in the low byte, plus the packed global-river id, per-pixel
 * spline position, and an id→width lookup table — see {@link RiverData}).
 *
 * <p>Per-tile pipeline: {@link #decodeBaseChannels} (padded 514×514) → trace the 2×2 owned coarse
 * cells' global-river arrows into straight splines and relax them down-gradient with a {@link Meanders}
 * sim ({@link #traceRiverSplines}) → carve the elevation toward the global river bed along those
 * splines → fill sinks ({@link PipelinePreprocessing#fillSinks}) so drainage has no trapped pits →
 * recompute the drainage direction on the filled (padded) elevation → rasterize each river's centreline
 * (tagging pixels with a 1-based id + spline position) and pack a per-tile id→width table → crop to the
 * inner 512×512. Channel 7 stores the packed {@link RiverData} int bit-preserving via
 * {@link Float#intBitsToFloat}. All tuning literals live grouped near the top.
 */
public class ReliefProvider {

    // ---- Tuning knobs (edit here during testing) ----------------------------
    /** Max distance (native px) from a river sample at which carving still applies. */
    private static final double MAX_CARVE_DIST = 64.0;
    /** Arc-length spacing (native px) used to densify river splines for the spatial index. */
    private static final double CARVE_SAMPLE_SPACING = 2.0;
    /** Arc-length spacing (native px) used to rasterize river centrelines into the riverData channel. */
    private static final double CENTERLINE_SAMPLE_SPACING = 0.5;
    /** Border transition width (px) for the sink-fill blend (keeps tile seams consistent). */
    private static final int FILL_PADDING = 64;

    // ---- River tracing / meander relaxation ---------------------------------
    /** Offset (padded px) of the perpendicular "gate" control point inside a cell from its edge point. */
    private static final double GATE_OFFSET = 32.0;
    /** Width (px) of the inward border-gradient band that confines relaxed channels to the tile. */
    private static final int BORDER_RAMP_WIDTH = 24;
    /** Magnitude of the inward border gradient (≫ terrain gradient + the migrate clamp). */
    private static final double BORDER_SLOPE = 1e3;
    /** Number of {@code migrateLowerGrad} relaxation steps run over the tile network. */
    private static final int RELAX_STEPS = 10;
    /** Minimum river width (px) assigned to a carved sample. */
    private static final double MIN_WIDTH = 1.0;

    // ---- Geometry -----------------------------------------------------------
    private static final int INNER = 512;
    private static final int PAD = 1;
    private static final int PADDED = INNER + 2 * PAD; // 514
    private static final int BASE_CHANNELS = DECODER_CHANNELS - 1; // 7 (relief ch0..6)
    /** Native px per coarse cell. */
    private static final int COARSE_PX = 256;
    /** Half a coarse cell — offset from a cell origin to its centre. */
    private static final int COARSE_HALF = COARSE_PX / 2;
    /** Distance (px) over which a polyline's bed lerps to the coarse bed near the tile edge (seam continuity). */
    private static final double BED_EDGE_BLEND = COARSE_HALF;

    private static final Logger LOG = getLogger(ReliefProvider.class);

    private final NonIntersectingInfiniteTensor final_tiles;

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path, "final_relief_tiles", new int[] {RELIEF_CHANNELS, INNER, INNER}, this::buildReliefTile);
    }

    @TestOnly
    public void setGlobalRiverProvider(GlobalRiverProvider provider) {
        this.globalRiverOverride = provider;
    }

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    public NonIntersectingInfiniteTensor getInfiniteTensor() {
        return final_tiles;
    }

    /**
     * The full computed relief tile {@code [RELIEF_CHANNELS, 512, 512]} covering native px
     * {@code [tileX<<9, (tileX+1)<<9) × [tileZ<<9, (tileZ+1)<<9)}. Used by {@code LocalRiverProvider}
     * to read the whole {@code elev}/{@code riverData} channels at once.
     */
    public FloatTensor getTile(int tileX, int tileZ) {
        return final_tiles.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Per-tile pipeline
    // -------------------------------------------------------------------------

    private FloatTensor buildReliefTile(TileKey key) {
        return computeTile(key.get(X), key.get(Z), null);
    }

    private FloatTensor computeTile(int x, int z, @Nullable Stages stages) {
        // 1. decode the 7 weight-normalized base channels at padded 514×514.
        final float[][] baseChannels = decodeBaseChannels(x, z);

        // 2-3. trace the global river arrows over this tile into straight splines and relax them
        //       down-gradient with a Meanders sim (control points + widths + bed elevations).
        final List<RiverPolyline> riverSplines = traceRiverSplines(x, z, baseChannels);

        // 4. carve the elevation (base channel 0) toward the global river bed along the splines.
        final float[] carvedElevation = baseChannels[0].clone();
        carveRiver(carvedElevation, riverSplines, stages);

        // 5a. fill sinks so drainage has no trapped interior pits (border-blended for seam consistency).
        final float[] filledElevation = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, FILL_PADDING);

        // 5b. drainage direction on the filled padded elevation (uniform weight = already normalized).
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainageDirection =
                PipelinePreprocessing.computeDrainageDirection(filledElevation, uniformWeight, PADDED);

        // 5c. rasterize each river's centreline: tag pixels with a 1-based id + spline position.
        final int[] riverId = new int[PADDED * PADDED];
        final int[] splinePosition = new int[PADDED * PADDED];
        rasterizeCenterlines(riverSplines, riverId, splinePosition);

        // 6. crop to the inner 512×512, packing channel 7 (drainage | id | spline position) and the
        //    per-tile id→width table; assemble the result tensor.
        final float[] entries = new float[RELIEF_CHANNELS * INNER * INNER];
        final int[] packedRiverData = new int[INNER * INNER];
        for (int ix = 0; ix < INNER; ix++) {
            for (int iz = 0; iz < INNER; iz++) {
                final int paddedIndex = (PAD + ix) * PADDED + (PAD + iz);
                final int innerIndex = ix * INNER + iz;
                entries[innerIndex] = filledElevation[paddedIndex];
                for (int ch = 1; ch < BASE_CHANNELS; ch++) {
                    entries[ch * INNER * INNER + innerIndex] = baseChannels[ch][paddedIndex];
                }
                final int id = riverId[paddedIndex];
                final int upper = (id != 0) ? splinePosition[paddedIndex] : 0;
                packedRiverData[innerIndex] = RiverData.packPixel(drainageDirection[paddedIndex], id, upper);
            }
        }
        writeWidthTable(packedRiverData, riverSplines);
        for (int i = 0; i < packedRiverData.length; i++) {
            // ch7 holds the packed riverData int, stored bit-preserving (see LocalRiverProvider).
            entries[7 * INNER * INNER + i] = Float.intBitsToFloat(packedRiverData[i]);
        }

        final FloatTensor result = new FloatTensor(entries, new int[] {RELIEF_CHANNELS, INNER, INNER});
        if (stages != null) {
            stages.baseChannels = baseChannels;
            stages.riverSplines = riverSplines;
            stages.carvedElevation = carvedElevation;
            stages.filledElevation = filledElevation;
            final float[] riverDataAsFloat = new float[drainageDirection.length];
            for (int i = 0; i < drainageDirection.length; i++) {
                riverDataAsFloat[i] = RiverData.packPixel(drainageDirection[i], riverId[i], 0);
            }
            stages.riverData = riverDataAsFloat;
            stages.result = result;
        }
        return result;
    }

    /**
     * Rasterize each river polyline's centreline into the padded grid: every distinct pixel the spline
     * passes through is tagged with its 1-based polyline id and an increasing position index along the
     * spline (0 at the upstream end). Fills {@code riverId} (0 = none) and {@code splinePosition} in
     * place. Ids align with {@link #writeWidthTable}'s table order (id {@code r+1} ↔ polyline {@code r}).
     * Later polylines win shared pixels (a global-river confluence — a localized artifact).
     */
    private void rasterizeCenterlines(List<RiverPolyline> riverSplines, int[] riverId, int[] splinePosition) {
        final int riverCount = Math.min(riverSplines.size(), RiverData.MAX_RIVER_ID);
        for (int r = 0; r < riverCount; r++) {
            final RiverPolyline polyline = riverSplines.get(r);
            if (polyline.controlPoints.size() < 2) continue;
            final int id = r + 1;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            int position = 0;
            int lastIndex = -1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CENTERLINE_SAMPLE_SPACING));
                for (int s = 0; s <= sampleCount; s++) {
                    final double[] p = spline.sample(i + (double) s / sampleCount);
                    final int xi = (int) Math.round(p[0]);
                    final int zi = (int) Math.round(p[1]);
                    if (xi < 0 || zi < 0 || xi >= PADDED || zi >= PADDED) continue;
                    final int idx = xi * PADDED + zi;
                    if (idx == lastIndex) continue; // same pixel as the previous sample
                    riverId[idx] = id;
                    splinePosition[idx] = position++ & RiverData.UPPER_MASK;
                    lastIndex = idx;
                }
            }
        }
    }

    /**
     * Overlay the per-tile id→width lookup table onto the upper halfword of the first non-global
     * pixels of {@code packedRiverData} (flattened order, skipping global pixels whose upper halfword
     * holds a spline position). Stream layout: slot 0 = id count, then 4 slots per id
     * (startWidth hi/lo, endWidth hi/lo). See {@link RiverData}.
     */
    private static void writeWidthTable(int[] packedRiverData, List<RiverPolyline> riverSplines) {
        final int riverCount = Math.min(riverSplines.size(), RiverData.MAX_RIVER_ID);
        final int slotCount = 1 + 4 * riverCount;
        final int[] slots = new int[slotCount];
        slots[0] = riverCount & RiverData.UPPER_MASK;
        for (int r = 0; r < riverCount; r++) {
            final RiverPolyline poly = riverSplines.get(r);
            final float startWidth = poly.widths.isEmpty()
                    ? (float) MIN_WIDTH
                    : poly.widths.getFirst().floatValue();
            final float endWidth = poly.widths.isEmpty()
                    ? (float) MIN_WIDTH
                    : poly.widths.getLast().floatValue();
            final int base = 1 + 4 * r;
            slots[base] = RiverData.widthHighHalf(startWidth);
            slots[base + 1] = RiverData.widthLowHalf(startWidth);
            slots[base + 2] = RiverData.widthHighHalf(endWidth);
            slots[base + 3] = RiverData.widthLowHalf(endWidth);
        }
        int slotCursor = 0;
        for (int i = 0; i < packedRiverData.length && slotCursor < slotCount; i++) {
            if (RiverData.riverId(packedRiverData[i]) != 0) continue; // skip global pixels
            packedRiverData[i] |= (slots[slotCursor] & RiverData.UPPER_MASK) << RiverData.UPPER_SHIFT;
            slotCursor++;
        }
    }

    /**
     * Fetch a +1-pixel-halo decoder slice ({@code [DECODER_CHANNELS=8, 514, 514]}) and weight-normalize
     * it into the 7 relief base channels at padded resolution. Decoder channel 0 is the blend weight;
     * channels 1..7 are the real outputs, each divided by the weight (guarded) and shifted down by one
     * so relief channel {@code c-1} = decoder channel {@code c} / weight.
     *
     * @return {@code baseChannels[c][paddedIndex]} for {@code c} in 0..6, each {@code 514*514} long.
     */
    public float[][] decodeBaseChannels(int x, int z) {
        final FloatTensor fineSlice =
                pipeline.getDecoderSlice((x << 9) - PAD, (z << 9) - PAD, ((x + 1) << 9) + PAD, ((z + 1) << 9) + PAD);
        final int pixelCount = PADDED * PADDED;
        final float[][] baseChannels = new float[BASE_CHANNELS][pixelCount];
        for (int px = 0; px < pixelCount; px++) {
            final float weight = fineSlice.data[px];
            final float inverse = (weight > 1e-6f) ? 1f / weight : 0f;
            for (int c = 1; c < DECODER_CHANNELS; c++) {
                baseChannels[c - 1][px] = fineSlice.data[c * pixelCount + px] * inverse;
            }
        }
        return baseChannels;
    }

    // -------------------------------------------------------------------------
    // River spline tracing (step 2-3)
    // -------------------------------------------------------------------------

    /**
     * One traced global-river branch: ordered control points (padded px) with per-vertex widths and
     * bed elevations (native-px scale, from {@code GlobalRiverProvider.getElevation}).
     */
    public static final class RiverPolyline {
        final ArrayList<double[]> controlPoints = new ArrayList<>();
        final ArrayList<Double> widths = new ArrayList<>();
        final ArrayList<Double> bedElevations = new ArrayList<>();
        QuinticHermiteSpline spline;
    }

    /** Canonical key for the internal edge shared by two adjacent owned cells. */
    private record EdgeKey(int lowerX, int lowerZ, int axis) {}

    /** Per-cell drainage info: outgoing cardinal dir, the cell it drains into, and its drain point (or null). */
    private record CellInfo(int ccx, int ccz, int outDirection, int dcx, int dcz, double[] drain) {}

    private List<RiverPolyline> traceRiverSplines(int tileX, int tileZ, float[][] baseChannels) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[] elev = baseChannels[0];

        // --- precompute {outDir, downstream, drain} for every river cell in the 4x4 area
        //     (inner 2x2 = this tile). The drain is the elevation-closest pixel on the outgoing edge
        //     (>= marginInfluence from tile corners), or null when that edge is outside the decoded slice.
        final Map<Long, CellInfo> cells = new HashMap<>();
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final int arrow = grp.getArrow(ccx, ccz);
                if (!GlobalRiverProvider.isRiver(arrow) && !GlobalRiverProvider.isCoast(arrow)) {
                    //                    LOG.info("drain of {} {} is invalid because not river",ccx,ccz);
                    continue;
                }
                int outDir = -1;
                final int outMask = GlobalRiverProvider.outgoingMask(arrow);
                for (int d = 4; d <= 7; d++)
                    if ((outMask & (1 << d)) != 0) {
                        outDir = d;
                        break;
                    }
                int dcx = ccx, dcz = ccz;
                double[] drain = null;
                if (outDir != -1) {
                    dcx = ccx + PipelinePreprocessing.NEIGHBOR_OFFSET_X[outDir];
                    dcz = ccz + PipelinePreprocessing.NEIGHBOR_OFFSET_Z[outDir];
                    drain = findDrain(
                            ccx,
                            ccz,
                            outDir,
                            tileX,
                            tileZ,
                            elev,
                            grp.getElevation(ccx, ccz),
                            marginInfluence(Math.max(grp.getWidth(ccx, ccz), MIN_WIDTH)));
                }
                //                LOG.info("drain of {} {} is {}",ccx,ccz,drain);
                cells.put(cellKey(ccx, ccz), new CellInfo(ccx, ccz, outDir, dcx, dcz, drain));
            }
        }

        final List<Meanders.NodeSpec> nodeSpecs = new ArrayList<>();
        final List<Meanders.EdgeSpec> edgeSpecs = new ArrayList<>();
        final Map<Long, Integer> centerIdx = new HashMap<>();
        final Map<EdgeKey, Integer> edgeNodeIdx = new HashMap<>();

        // --- owned (2x2) centres. No cardinal outflow -> DRAIN so the leaf-prune step keeps it.
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final CellInfo c = cells.get(cellKey(ccx, ccz));
                if (c == null) continue;
                final double cx = PAD + a * COARSE_PX + COARSE_HALF;
                final double cz = PAD + b * COARSE_PX + COARSE_HALF;
                final Node.NodeType type = (c.outDirection() != -1) ? Node.NodeType.JUNCTION : Node.NodeType.DRAIN;
                centerIdx.put(cellKey(ccx, ccz), addNode(nodeSpecs, cx, cz, type));
            }
        }

        // --- edges for owned cells: outflow centre->drain, inflows drain(N)->centre, source seed.
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final CellInfo c = cells.get(cellKey(ccx, ccz));
                if (c == null) continue;
                final int centre = centerIdx.get(cellKey(ccx, ccz));
                final double[] centreCoord = nodeCoord(nodeSpecs, centre);
                final double width = Math.max(grp.getWidth(ccx, ccz), MIN_WIDTH);

                // outflow: centre -> drain(C)
                if (c.drain() != null) {
                    final boolean downOwned =
                            isOwned(tileX, tileZ, c.dcx(), c.dcz()) && cells.containsKey(cellKey(c.dcx(), c.dcz()));
                    final int exitNode = downOwned
                            ? getOrCreateEdgeNode(nodeSpecs, edgeNodeIdx, ccx, ccz, c.dcx(), c.dcz(), c.drain())
                            : addNode(nodeSpecs, c.drain()[0], c.drain()[1], Node.NodeType.DRAIN);
                    final int ox = PipelinePreprocessing.NEIGHBOR_OFFSET_X[c.outDirection()];
                    final int oz = PipelinePreprocessing.NEIGHBOR_OFFSET_Z[c.outDirection()];
                    edgeSpecs.add(new Meanders.EdgeSpec(
                            centre, exitNode, pts(centreCoord, gateInside(c.drain(), ox, oz), c.drain()), width));
                }

                // inflows: cardinal neighbours whose drain leads into C
                for (int d = 4; d <= 7; d++) {
                    final int ox = PipelinePreprocessing.NEIGHBOR_OFFSET_X[d];
                    final int oz = PipelinePreprocessing.NEIGHBOR_OFFSET_Z[d];
                    final CellInfo n = cells.get(cellKey(ccx + ox, ccz + oz));
                    if (n == null || n.drain() == null) continue;
                    if (n.dcx() != ccx || n.dcz() != ccz) continue; // N does not drain into C
                    final boolean nOwned = isOwned(tileX, tileZ, n.ccx(), n.ccz());
                    final int entryNode = nOwned
                            ? getOrCreateEdgeNode(nodeSpecs, edgeNodeIdx, n.ccx(), n.ccz(), ccx, ccz, n.drain())
                            : addNode(nodeSpecs, n.drain()[0], n.drain()[1], Node.NodeType.SOURCE);
                    edgeSpecs.add(new Meanders.EdgeSpec(
                            entryNode,
                            centre,
                            pts(n.drain(), gateInside(n.drain(), ox, oz), centreCoord),
                            Math.max(grp.getWidth(n.ccx(), n.ccz()), MIN_WIDTH)));
                }

                // source seed: deterministic random point inside the cell, inset by marginInfluence.
                if (GlobalRiverProvider.isSource(grp.getArrow(ccx, ccz))) {
                    final double minX = PAD + a * COARSE_PX;
                    final double minZ = PAD + b * COARSE_PX;
                    final double[] seed = sourceSeed(ccx, ccz, minX, minZ, marginInfluence(width));
                    final int seedNode = addNode(nodeSpecs, seed[0], seed[1], Node.NodeType.SOURCE);
                    edgeSpecs.add(new Meanders.EdgeSpec(seedNode, centre, pts(seed, centreCoord), width));
                }
            }
        }

        if (edgeSpecs.isEmpty()) return new ArrayList<>();

        // gradient field: decoded terrain gradient + a large inward border push.
        final float[] gradX = baseChannels[2].clone();
        final float[] gradZ = baseChannels[3].clone();
        applyBorderPush(gradX, gradZ);

        final Meanders sim = new Meanders(PADDED, gradX, gradZ, nodeSpecs, edgeSpecs);
        sim.relaxLowerGrad(RELAX_STEPS);

        // extract: id-independent (captures/merges renumber channels) — sample width/bed by position.
        final List<RiverPolyline> result = new ArrayList<>();
        for (Channel ch : sim.getChannels()) {
            final ArrayList<double[]> cp = ch.spline.points();
            if (cp.size() < 2) continue;
            final RiverPolyline poly = new RiverPolyline();
            double runningBed = Double.POSITIVE_INFINITY;
            for (double[] p : cp) {
                poly.controlPoints.add(new double[] {p[0], p[1]});
                final int cellA = tileX * 2 + (int) Math.floor((p[0] - PAD) / COARSE_PX);
                final int cellB = tileZ * 2 + (int) Math.floor((p[1] - PAD) / COARSE_PX);
                final CellInfo ci = cells.get(cellKey(cellA, cellB));
                final double coarse = grp.getElevation(cellA, cellB);
                // at the cell's outgoing edge the river is exiting toward the downstream cell, so its bed
                // floor there is the downstream cell's coarse bed (terminal cells keep their own).
                final double downstreamCoarse =
                        (ci != null && ci.outDirection() != -1) ? grp.getElevation(ci.dcx(), ci.dcz()) : coarse;
                final double decoded = sampleBilinear(elev, p[0], p[1]);
                // follow the decoded terrain downstream, but never below the downstream coarse river bed.
                final double candidate = Math.max(decoded, downstreamCoarse);
                runningBed = Math.min(runningBed, candidate);
                // bed ramps from coarse(cell) at the entry to coarse(downstream) at the outgoing edge.
                final double cellMinX = PAD + (cellA - tileX * 2) * COARSE_PX;
                final double cellMinZ = PAD + (cellB - tileZ * 2) * COARSE_PX;
                final double s = outgoingProgress(p[0], p[1], cellMinX, cellMinZ, ci != null ? ci.outDirection() : -1);
                final double target = coarse + (downstreamCoarse - coarse) * s;
                // tile-edge continuity: lerp toward the position-dependent coarse target near the boundary.
                final double t = Math.clamp(distToTileBoundary(p[0], p[1]) / BED_EDGE_BLEND, 0.0, 1.0);
                final double bed = target + (runningBed - target) * t;
                poly.widths.add(Math.max(grp.getWidth(cellA, cellB), MIN_WIDTH));
                poly.bedElevations.add(bed);
            }
            poly.spline = QuinticHermiteSpline.createCatmullRom(poly.controlPoints);
            result.add(poly);
        }
        return result;
    }

    private static int addNode(List<Meanders.NodeSpec> nodes, double x, double z, Node.NodeType type) {
        final int idx = nodes.size();
        nodes.add(new Meanders.NodeSpec(x, z, type));
        return idx;
    }

    private static double[] nodeCoord(List<Meanders.NodeSpec> nodes, int idx) {
        final Meanders.NodeSpec ns = nodes.get(idx);
        return new double[] {ns.x(), ns.z()};
    }

    private static ArrayList<double[]> pts(double[]... ps) {
        final ArrayList<double[]> list = new ArrayList<>(ps.length);
        for (double[] p : ps) list.add(new double[] {p[0], p[1]});
        return list;
    }

    /**
     * A point {@link #GATE_OFFSET} px inside the owning cell from edge point {@code e}, along the
     * perpendicular (cardinal) axis given by the cell offset {@code (offsetX, offsetZ)} toward the
     * neighbour — so the spline's tangent at {@code e} is perpendicular to that edge.
     */
    private static double[] gateInside(double[] e, int offsetX, int offsetZ) {
        return new double[] {e[0] - offsetX * GATE_OFFSET, e[1] - offsetZ * GATE_OFFSET};
    }

    /** True when {@code (ccx, ccz)} is one of the tile's owned 2x2 coarse cells. */
    private static boolean isOwned(int tileX, int tileZ, int ccx, int ccz) {
        final int a = ccx - tileX * 2;
        final int b = ccz - tileZ * 2;
        return a >= 0 && a <= 1 && b >= 0 && b <= 1;
    }

    private static int getOrCreateEdgeNode(
            List<Meanders.NodeSpec> nodes,
            Map<EdgeKey, Integer> edgeNodeIdx,
            int c1x,
            int c1z,
            int c2x,
            int c2z,
            double[] coord) {
        final EdgeKey key = edgeKey(c1x, c1z, c2x, c2z);
        final Integer existing = edgeNodeIdx.get(key);
        if (existing != null) return existing;
        final int created = addNode(nodes, coord[0], coord[1], Node.NodeType.JUNCTION);
        edgeNodeIdx.put(key, created);
        return created;
    }

    private static EdgeKey edgeKey(int ax, int az, int bx, int bz) {
        final boolean aLower = (ax < bx) || (ax == bx && az <= bz);
        final int lx = aLower ? ax : bx;
        final int lz = aLower ? az : bz;
        final int axis = (ax != bx) ? 0 : 1; // adjacent cells differ in exactly one axis
        return new EdgeKey(lx, lz, axis);
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** Add a large inward gradient within {@link #BORDER_RAMP_WIDTH} of the padded border so −gradient confines channels to the tile. */
    private void applyBorderPush(float[] gradX, float[] gradZ) {
        for (int px = 0; px < PADDED; px++) {
            for (int pz = 0; pz < PADDED; pz++) {
                final int idx = px * PADDED + pz;
                if (px < BORDER_RAMP_WIDTH)
                    gradX[idx] -= (float) (BORDER_SLOPE * (1 - px / (double) BORDER_RAMP_WIDTH));
                else if (px >= PADDED - BORDER_RAMP_WIDTH)
                    gradX[idx] += (float) (BORDER_SLOPE * (1 - (PADDED - 1 - px) / (double) BORDER_RAMP_WIDTH));
                if (pz < BORDER_RAMP_WIDTH)
                    gradZ[idx] -= (float) (BORDER_SLOPE * (1 - pz / (double) BORDER_RAMP_WIDTH));
                else if (pz >= PADDED - BORDER_RAMP_WIDTH)
                    gradZ[idx] += (float) (BORDER_SLOPE * (1 - (PADDED - 1 - pz) / (double) BORDER_RAMP_WIDTH));
            }
        }
    }

    /**
     * The drain point (padded px) of coarse cell {@code (ccx, ccz)} on its outgoing edge toward cardinal
     * {@code dir} (4..7): the edge pixel whose decoded elevation ({@code elev}) is closest to the cell's
     * coarse bed {@code target}, restricted to pixels at least {@code marginInfl} from the owned-2x2
     * corners (so its carve band can't bleed past a tile corner). Returns {@code null} when the edge line
     * is outside the decoded slice {@code [0, PADDED)} or no valid pixel remains. Only the shared-edge
     * pixel line is read, so both tiles flanking the edge compute the same point (seam-deterministic).
     */
    private double[] findDrain(
            int ccx, int ccz, int dir, int tileX, int tileZ, float[] elev, double target, double marginInfl) {
        final int minXi = PAD + (ccx - tileX * 2) * COARSE_PX;
        final int minZi = PAD + (ccz - tileZ * 2) * COARSE_PX;
        final int maxXi = minXi + COARSE_PX;
        final int maxZi = minZi + COARSE_PX;

        int bestX = -1;
        int bestZ = -1;
        double bestDiff = Double.MAX_VALUE;
        if (dir == 4 || dir == 5) { // z-edge → iterate over x
            final int lineZ = (dir == 4) ? minZi : maxZi;
            if (lineZ < 0 || lineZ >= PADDED) return null;
            for (int xi = Math.max(0, minXi); xi < Math.min(PADDED, maxXi); xi++) {
                if (nearTileCorner(xi, lineZ, marginInfl)) continue;
                final double diff = Math.abs(elev[xi * PADDED + lineZ] - target);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestX = xi;
                    bestZ = lineZ;
                }
            }
        } else { // dir 6 or 7 → x-edge → iterate over z
            final int lineX = (dir == 6) ? minXi : maxXi;
            if (lineX < 0 || lineX >= PADDED) return null;
            for (int zi = Math.max(0, minZi); zi < Math.min(PADDED, maxZi); zi++) {
                if (nearTileCorner(lineX, zi, marginInfl)) continue;
                final double diff = Math.abs(elev[lineX * PADDED + zi] - target);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestX = lineX;
                    bestZ = zi;
                }
            }
        }
        if (bestX < 0) return null;
        return new double[] {bestX, bestZ};
    }

    /** True when padded-px {@code (px, pz)} is within {@code marginInfl} of an owned-2x2 corner. */
    private static boolean nearTileCorner(double px, double pz, double marginInfl) {
        final double lo = PAD;
        final double hi = PAD + INNER;
        final double r2 = marginInfl * marginInfl;
        for (final double cx : new double[] {lo, hi}) {
            for (final double cz : new double[] {lo, hi}) {
                final double dx = px - cx;
                final double dz = pz - cz;
                if (dx * dx + dz * dz < r2) return true;
            }
        }
        return false;
    }

    /**
     * Fractional progress (0..1) of padded-px {@code (px, pz)} across its coarse cell toward the outgoing
     * cardinal edge {@code outDir} (4..7): 0 at the entry side, 1 at the drain edge. {@code 0} when the
     * cell has no outgoing direction. {@code (cellMinX, cellMinZ)} is the cell origin in padded px.
     */
    private static double outgoingProgress(double px, double pz, double cellMinX, double cellMinZ, int outDir) {
        if (outDir < 0) return 0.0;
        final int ox = PipelinePreprocessing.NEIGHBOR_OFFSET_X[outDir];
        final int oz = PipelinePreprocessing.NEIGHBOR_OFFSET_Z[outDir];
        final double s = (ox != 0)
                ? (ox > 0 ? (px - cellMinX) : (cellMinX + COARSE_PX - px)) / COARSE_PX
                : (oz > 0 ? (pz - cellMinZ) : (cellMinZ + COARSE_PX - pz)) / COARSE_PX;
        return Math.clamp(s, 0.0, 1.0);
    }

    /** Min distance (padded px) from {@code (px, pz)} to the owned-2x2 boundary lines (0 at the edge). */
    private static double distToTileBoundary(double px, double pz) {
        final double lo = PAD;
        final double hi = PAD + INNER;
        return Math.min(Math.min(px - lo, hi - px), Math.min(pz - lo, hi - pz));
    }

    /**
     * A deterministic random seed point (padded px) for a source cell: uniform inside the cell square
     * inset by {@code marginInfl} on every side, keyed by the cell coords (so neighbouring tiles agree).
     */
    private static double[] sourceSeed(int ccx, int ccz, double minX, double minZ, double marginInfl) {
        final java.util.Random rng =
                new java.util.Random(((long) ccx * 0x9E3779B97F4A7C15L) ^ ((long) ccz * 0xC2B2AE3D27D4EB4FL));
        final double span = COARSE_PX - 2 * marginInfl;
        if (span <= 0) return new double[] {minX + COARSE_HALF, minZ + COARSE_HALF};
        final double rx = marginInfl + rng.nextDouble() * span;
        final double rz = marginInfl + rng.nextDouble() * span;
        return new double[] {minX + rx, minZ + rz};
    }

    /** Bilinear sample of a padded {@code PADDED×PADDED} field (row-major {@code x*PADDED + z}). */
    private static double sampleBilinear(float[] field, double px, double pz) {
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        final double fx = px - x0;
        final double fz = pz - z0;
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        x0 = Math.clamp(x0, 0, PADDED - 1);
        x1 = Math.clamp(x1, 0, PADDED - 1);
        z0 = Math.clamp(z0, 0, PADDED - 1);
        z1 = Math.clamp(z1, 0, PADDED - 1);
        final double v0 = field[x0 * PADDED + z0] * (1 - fz) + field[x0 * PADDED + z1] * fz;
        final double v1 = field[x1 * PADDED + z0] * (1 - fz) + field[x1 * PADDED + z1] * fz;
        return v0 * (1 - fx) + v1 * fx;
    }

    // -------------------------------------------------------------------------
    // River carving (step 4)
    // -------------------------------------------------------------------------

    /**
     * Radius (native px) out to which a river of the given width still lowers terrain (its carve band).
     * Shared by {@link #carveRiver} (where carving stops) and {@link #traceRiverSplines} (which keeps
     * drain/source/edge points at least this far from tile corners). Tune here for testing.
     */
    private static double marginInfluence(double width) {
        return 5 * width;
    }

    /** A densified river sample carrying its interpolated width + bed elevation, indexed in the carve QuadTree. */
    private static final class RiverSample extends QuadTreePoint {
        final double width;
        final double bedElev;

        RiverSample(double px, double pz, double width, double bedElev) {
            super(new double[] {px, pz});
            this.width = width;
            this.bedElev = bedElev;
        }
    }

    /**
     * Carve {@code elevation} (padded 514×514) toward the global river bed along the splines. Each
     * spline is densified into sample points (width and bed elevation lerped between adjacent control
     * points) and inserted into a QuadTree. Each pixel queries the nearest sample (within the
     * {@link #MAX_CARVE_DIST} query cap) and is pulled <b>down toward</b> the river-bed elevation there,
     * forming a tight bank: within {@code margin = width/2} the floor is {@code bedElev}; from
     * {@code margin} out to {@code marginInfluence = width + 1} the result lerps linearly from
     * {@code bedElev} back to the original elevation; beyond {@code marginInfluence} the pixel is left
     * untouched (never raised — only lowered). The global river itself is identified downstream not by
     * this carve band but by the rasterized centreline (see {@link #rasterizeCenterlines}).
     */
    private void carveRiver(float[] elevation, List<RiverPolyline> riverSplines, @Nullable Stages stages) {
        if (riverSplines.isEmpty()) return;

        final QuadTree<RiverSample> index = new QuadTree<>(
                new double[] {-COARSE_PX * 4, -COARSE_PX * 4},
                new double[] {PADDED + COARSE_PX * 4, PADDED + COARSE_PX * 4});
        for (RiverPolyline polyline : riverSplines) {
            if (polyline.controlPoints.size() < 2) continue;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                final double startWidth = polyline.widths.get(i);
                final double endWidth = polyline.widths.get(i + 1);
                final double startBed = polyline.bedElevations.get(i);
                final double endBed = polyline.bedElevations.get(i + 1);
                for (int s = 0; s <= sampleCount; s++) {
                    final double frac = (double) s / sampleCount;
                    final double[] point = spline.sample(i + frac);
                    final double width = startWidth + (endWidth - startWidth) * frac;
                    final double bed = startBed + (endBed - startBed) * frac;
                    index.insertPoint(new RiverSample(point[0], point[1], width, bed));
                }
            }
        }

        final float[] carveDepthField = (stages != null) ? new float[PADDED * PADDED] : null;
        for (int pi = 0; pi < PADDED; pi++) {
            for (int pj = 0; pj < PADDED; pj++) {
                final int idx = pi * PADDED + pj;
                if (elevation[idx] < 0) continue;
                final double[] pixel = {pi, pj};
                final List<RiverSample> nearby = index.getPointsInCircle(pixel, MAX_CARVE_DIST);
                if (nearby.isEmpty()) continue;
                double nearestDist = Double.MAX_VALUE;
                double width = 0;
                double bedElev = 0;
                for (RiverSample sample : nearby) {
                    final double dist = VectorOps.distance(pixel, sample.toArray());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        width = sample.width;
                        bedElev = sample.bedElev;
                    }
                }
                // MAX_CARVE_DIST is only the QuadTree query/safety cap now; the actual carve influence
                // ends at marginInfluence (rivers wider than ~2*MAX_CARVE_DIST clip at the cap).
                if (nearestDist >= MAX_CARVE_DIST) continue;

                final double margin = width * 0.5;
                final double marginInfluence = marginInfluence(width);

                // Beyond marginInfluence the terrain is untouched; carve only within [0, marginInfluence].
                if (nearestDist >= marginInfluence) continue;

                final float orig = elevation[idx];
                final double centreFloor = bedElev;
                final double frac = (nearestDist <= margin)
                        ? 0.0
                        : Math.min(1.0, (nearestDist - margin) / (marginInfluence - margin));
                final float carved = (float) (centreFloor + (orig - centreFloor) * frac);
                elevation[idx] = carved;
                if (carveDepthField != null) carveDepthField[idx] = orig - carved;
            }
        }
        if (stages != null) stages.carveDepthField = carveDepthField;
        index.clear();
    }

    // -------------------------------------------------------------------------
    // Pixel accessors
    // -------------------------------------------------------------------------

    public Float get_entry(final int[] mutableCoords, final int ch) {
        mutableCoords[FractalTerrainConfig.CH] = ch;
        return final_tiles.getValue(mutableCoords);
    }

    public Float getElev(int[] xz) {
        return get_entry(xz, 0);
    }

    public Float getBlurredElev(final int[] xz) {
        return get_entry(xz, 1);
    }

    public Float getGradX(final int[] xz) {
        return get_entry(xz, 2);
    }

    public Float getGradY(final int[] xz) {
        return get_entry(xz, 3);
    }

    public Float getRefinedGrad(final int[] xz) {
        return get_entry(xz, 4);
    }

    public Float getLowFreqGrad(final int[] xz) {
        return get_entry(xz, 5);
    }

    public Float getRes(final int[] xz) {
        return get_entry(xz, 6);
    }

    public Float getRiverData(final int[] xz) {
        return get_entry(xz, 7);
    }

    public double getContinentalElev(final int[] xz) {
        return 0;
    }

    public double getRawTemp(final int[] xz) {
        return 0;
    }

    public Float getRawTempSTD(final int[] xz) {
        return (float) 0;
    }

    public double getRawPrecip(final int[] xz) {
        return 0;
    }

    public Float getRawPrecipSTD(final int[] xz) {
        return (float) 0;
    }

    public int getRawGrad(final int[] xz) {
        return 0;
    }

    public double getBlurredGrad(final int[] xz) {
        return 0;
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int x, int z) {
        final Stages stages = new Stages();
        computeTile(x, z, stages);
        return stages;
    }

    /**
     * The densified river-spline sample points (padded px) traced for {@code stages}. Mirrors
     * {@link #carveRiver}'s sampling so a rasterization of these points matches what gets carved.
     * {@code RiverPolyline} is private, so callers (the debug tests) get plain {@code [x, z]} arrays.
     */
    @TestOnly
    public List<double[]> debugRiverSplinePoints(Stages stages) {
        final List<double[]> points = new ArrayList<>();
        final List<RiverPolyline> riverSplines = stages.riverSplines;
        if (riverSplines == null) return points;
        for (RiverPolyline polyline : riverSplines) {
            if (polyline.controlPoints.size() < 2) continue;
            final QuinticHermiteSpline spline = polyline.spline;
            final int segments = polyline.controlPoints.size() - 1;
            for (int i = 0; i < segments; i++) {
                final double segLength =
                        VectorOps.distance(polyline.controlPoints.get(i), polyline.controlPoints.get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                for (int s = 0; s <= sampleCount; s++) {
                    points.add(spline.sample(i + (double) s / sampleCount));
                }
            }
        }
        return points;
    }

    @TestOnly
    public static final class Stages {
        public float[][] baseChannels;
        public List<RiverPolyline> riverSplines;
        public float[] carveDepthField;
        public float[] carvedElevation;
        public float[] filledElevation;
        public float[] riverData;
        public FloatTensor result;
    }
}
