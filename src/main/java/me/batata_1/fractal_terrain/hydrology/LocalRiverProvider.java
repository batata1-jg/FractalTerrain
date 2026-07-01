package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.computeFlow;
import static me.batata_1.fractal_terrain.hydrology.PipelinePreprocessing.neighbor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.meanders.*;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteQuadTree;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.relief.DecoderChannels;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Owner of the whole per-tile river pipeline. From the decoded terrain ({@link DecoderChannels}) and the
 * global river network ({@link GlobalRiverProvider}) it computes, per 512×512 relief tile, two
 * artifacts produced by a single build:
 *
 * <ul>
 *   <li>a serializable {@link NonIntersectingInfiniteQuadTree} of {@link HydrologicalUnit}s — the
 *       queryable river network (global rivers traced + relaxed by a {@link Meanders} sim, plus the
 *       detailed local network traced from drainage); and</li>
 *   <li>a {@link NonIntersectingInfiniteTensor} of {@code [1,512,512]} carved+filled elevation tiles,
 *       imported by {@code ReliefProvider} as its elevation channel.</li>
 * </ul>
 *
 * <p>The two stores are populated together: whichever is requested first runs {@link #buildTile} and
 * cross-fills the other via the storage claim API (single-flight guarantees one build per tile).
 */
public class LocalRiverProvider {

    // ---- Global-river trace / meander relaxation ----------------------------
    private static final double GATE_JITTER = 24.0;
    private static final int MAX_RELAX_STEPS = 50;
    // Relaxation steps scale with the elevation of the tile's primary owned cell (2*tileCoords):
    // a baseline at sea level plus a per-elevation increment, so higher terrain relaxes more.
    private static final int MIN_RELAX_STEPS = 5;
    private static final double RELAX_STEPS_PER_ELEV = 0.2;
    private static final double MIN_WIDTH = FractalTerrainConfig.MIN_WIDTH;

    // ---- Carving / sink filling ---------------------------------------------
    private static final double MAX_CARVE_DIST = 64.0;
    private static final double CARVE_SAMPLE_SPACING = 2.0;
    private static final int FILL_PADDING = 64;

    // ---- Local network ------------------------------------------------------
    private static final float FLOW_THRESHOLD = 40f;
    private static final double RESAMPLE_DIST = 2.0;
    private static final double QUERY_RADIUS = 1.0;

    // ---- Geometry -----------------------------------------------------------
    private static final int GRID = 512;
    private static final int PAD = 1;
    private static final int PADDED = GRID + 2 * PAD; // 514
    private static final int COARSE_PX = 256;
    private static final int COARSE_HALF = COARSE_PX / 2;

    private final NonIntersectingInfiniteQuadTree<HydrologicalUnit> units;
    private final NonIntersectingInfiniteTensor carved;
    private final ConcurrentHashMap<TileKey, TileResult> pending = new ConcurrentHashMap<>();

    /** Test-only override for the global river source; {@code null} → use the singleton. */
    @TestOnly
    private @Nullable GlobalRiverProvider globalRiverOverride;

    /** Both per-tile artifacts produced by one {@link #buildTile}. */
    private record TileResult(ImmutableQuadTree<HydrologicalUnit> units, FloatTensor carved) {}

    public LocalRiverProvider(String path) {
        units = new NonIntersectingInfiniteQuadTree<>(
                path, "local_river_units", new int[] {GRID, GRID}, HydrologicalUnit.PROTOTYPE, this::buildUnitsTile);
        carved = new NonIntersectingInfiniteTensor(
                path, "local_carved_elev", new int[] {1, GRID, GRID}, this::buildCarvedTile);
    }

    @TestOnly
    public void setGlobalRiverProvider(GlobalRiverProvider provider) {
        this.globalRiverOverride = provider;
    }

    private GlobalRiverProvider globalRiverProvider() {
        return (globalRiverOverride != null) ? globalRiverOverride : FractalTerrainInstance.getGlobalRiverProvider();
    }

    /** Clears both per-tile caches (the unit tree and the carved-elevation tensor). */
    public void clearCaches() {
        units.clear();
        carved.clear();
        pending.clear();
    }

    /** The carved+filled elevation tile {@code [1,512,512]} for {@code (tileX, tileZ)} (imported by ReliefProvider). */
    public FloatTensor getCarvedElev(int tileX, int tileZ) {
        return carved.getEntry(new int[] {0, tileX, tileZ});
    }

    // -------------------------------------------------------------------------
    // Dual-store build (single compute, both stores filled)
    // -------------------------------------------------------------------------

    private TileResult buildOnce(int tileX, int tileZ) {
        return pending.computeIfAbsent(new TileKey(new int[] {tileX, tileZ}), k -> buildTile(tileX, tileZ, null));
    }

    private ImmutableQuadTree<HydrologicalUnit> buildUnitsTile(TileKey key) {
        final int tileX = key.get(0);
        final int tileZ = key.get(1);
        final TileResult result = buildOnce(tileX, tileZ);
        final int[] carvedKey = {0, tileX, tileZ};
        final CompletableFuture<FloatTensor> claim = carved.claimForCompute(carvedKey);
        if (claim != null) carved.fulfillClaim(carvedKey, claim, result.carved());
        pending.remove(new TileKey(new int[] {tileX, tileZ}));
        return result.units();
    }

    private FloatTensor buildCarvedTile(TileKey key) {
        final int tileX = key.get(1);
        final int tileZ = key.get(2);
        final TileResult result = buildOnce(tileX, tileZ);
        final int[] unitsKey = {tileX, tileZ};
        final CompletableFuture<ImmutableQuadTree<HydrologicalUnit>> claim = units.claimForCompute(unitsKey);
        if (claim != null) units.fulfillClaim(unitsKey, claim, result.units());
        pending.remove(new TileKey(new int[] {tileX, tileZ}));
        return result.carved();
    }

    // -------------------------------------------------------------------------
    // Core pipeline
    // -------------------------------------------------------------------------

    private TileResult buildTile(int tileX, int tileZ, @Nullable Stages stages) {
        final GlobalRiverProvider grp = globalRiverProvider();
        final float[][] base = DecoderChannels.decode(tileX, tileZ, PAD); // padded 514, channels 0..6

        // 1. global rivers: trace + relax + assign bed elevations.
        final Meanders sim = buildGlobalNetwork(tileX, tileZ, base, grp);

        // 2. carve the decoded elevation toward the global river beds.
        final float[] carvedElevation = base[0].clone();
        carveGlobalRivers(carvedElevation, sim.getChannels());

        // 3. fill sinks (border-blended), then crop to the inner 512 carved tile.
        final float[] filled = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, FILL_PADDING);
        final FloatTensor carvedTile = cropToTile(filled);

        // 4. drainage on the filled elevation; crop everything to the inner 512 grid.
        final float[] uniformWeight = new float[PADDED * PADDED];
        Arrays.fill(uniformWeight, 1f);
        final int[] drainagePadded = PipelinePreprocessing.computeDrainageDirection(filled, uniformWeight, PADDED);
        final int[] drainage = new int[GRID * GRID];
        final float[] elev = new float[GRID * GRID];
        for (int ix = 0; ix < GRID; ix++) {
            for (int iz = 0; iz < GRID; iz++) {
                final int padded = (PAD + ix) * PADDED + (PAD + iz);
                drainage[ix * GRID + iz] = drainagePadded[padded];
                elev[ix * GRID + iz] = filled[padded];
            }
        }

        // 5. mark the global-river pixels so the local network excludes them.
        final boolean[] globalMask = rasterizeGlobalMask(sim.getChannels());

        final List<Channel> localChannels = traceLocalNetwork(drainage, elev, globalMask, stages);

        // 7. assemble the unit tree: global network units + local channel units (tile-local coords).
        //    A shared feature-id counter gives global rivers and the local network one tile-unique id space.
        final RiverNetwork.ElevationSampler decodedSampler = (x, z) -> sampleBilinear(base[0], x, z);
        final int[] nextFeatureId = {0};
        final List<HydrologicalUnit> unitPoints =
                sim.getNetwork().collectUnits(0, decodedSampler, PAD, PAD, nextFeatureId);
        for (Channel ch : localChannels) addLocalChannelUnits(unitPoints, ch, elev, nextFeatureId);
        final ImmutableQuadTree<HydrologicalUnit> tree = new ImmutableQuadTree<>(
                new double[] {-16, -16}, new double[] {GRID + 16, GRID + 16}, unitPoints, HydrologicalUnit.PROTOTYPE);

        if (stages != null) {
            stages.channels = new ArrayList<>(sim.getChannels());
            stages.localChannels = localChannels;
            stages.carvedElevation = carvedTile.data;
            stages.network = sim.getNetwork();
        }
        return new TileResult(tree, carvedTile);
    }

    private FloatTensor cropToTile(float[] padded) {
        final float[] data = new float[GRID * GRID];
        for (int ix = 0; ix < GRID; ix++) {
            for (int iz = 0; iz < GRID; iz++) {
                data[ix * GRID + iz] = padded[(PAD + ix) * PADDED + (PAD + iz)];
            }
        }
        return new FloatTensor(new int[] {1, GRID, GRID}, data);
    }

    // -------------------------------------------------------------------------
    // Global-river network: trace + relax + bed assignment
    // -------------------------------------------------------------------------

    private record EdgeKey(int lowerX, int lowerZ, int axis) {}

    private record CellInfo(int ccx, int ccz, int outDirection, int dcx, int dcz, double[] drain) {}

    private Meanders buildGlobalNetwork(int tileX, int tileZ, float[][] base, GlobalRiverProvider grp) {
        final float[] elev = base[0];

        final Map<Long, CellInfo> cells = new HashMap<>();
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final int arrow = grp.getArrow(ccx, ccz);
                if (!GlobalRiverProvider.isRiver(arrow) && !GlobalRiverProvider.isCoast(arrow)) continue;
                int outDir = -1;
                final int outMask = GlobalRiverProvider.outgoingMask(arrow);
                for (int d = 4; d <= 7; d++)
                    if ((outMask & (1 << d)) != 0) {
                        outDir = d;
                        break;
                    }
                int dcx = ccx, dcz = ccz;
                double[] drain = null;
                final double marginInfl = marginInfluence(Math.max(grp.getWidth(ccx, ccz), MIN_WIDTH));
                if (outDir != -1) {
                    dcx = ccx + PipelinePreprocessing.NEIGHBOR_OFFSET_X[outDir];
                    dcz = ccz + PipelinePreprocessing.NEIGHBOR_OFFSET_Z[outDir];
                    drain = findDrain(ccx, ccz, outDir, tileX, tileZ, elev, grp.getElevation(ccx, ccz), marginInfl);
                } else {
                    // no downstream arrow: the cell terminates at its lowest-elevation interior point.
                    drain = findLowestInCell(ccx, ccz, tileX, tileZ, elev, marginInfl);
                }
                cells.put(cellKey(ccx, ccz), new CellInfo(ccx, ccz, outDir, dcx, dcz, drain));
            }
        }

        final List<RiverNetwork.NodeSpec> nodeSpecs = new ArrayList<>();
        final List<RiverNetwork.EdgeSpec> edgeSpecs = new ArrayList<>();
        final Map<Long, Integer> centerIdx = new HashMap<>();
        final Map<EdgeKey, Integer> edgeNodeIdx = new HashMap<>();
        final Map<Integer, Double> boundaryElevByNodeIdx = new HashMap<>();

        // owned (2x2) centres.
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final CellInfo c = cells.get(cellKey(ccx, ccz));
                if (c == null) continue;
                final Endpoint.Type type = (c.outDirection() != -1) ? Endpoint.Type.JUNCTION : Endpoint.Type.DRAIN;
                // a lastPointElev cell drains to its lowest-elevation interior point; otherwise the hub sits at the
                // centre.
                final double cx = (type == Endpoint.Type.DRAIN && c.drain() != null)
                        ? c.drain()[0]
                        : PAD + a * COARSE_PX + COARSE_HALF;
                final double cz = (type == Endpoint.Type.DRAIN && c.drain() != null)
                        ? c.drain()[1]
                        : PAD + b * COARSE_PX + COARSE_HALF;
                final int idx = addNode(nodeSpecs, cx, cz, type);
                centerIdx.put(cellKey(ccx, ccz), idx);
                if (type == Endpoint.Type.DRAIN) boundaryElevByNodeIdx.put(idx, (double) grp.getElevation(ccx, ccz));
            }
        }

        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final CellInfo c = cells.get(cellKey(ccx, ccz));
                if (c == null) continue;
                final int centre = centerIdx.get(cellKey(ccx, ccz));
                final double[] centreCoord = nodeCoord(nodeSpecs, centre);
                final double width = Math.max(grp.getWidth(ccx, ccz), MIN_WIDTH);

                // drains to another cell. Places the channel that connects the center to the drainage point
                if (c.outDirection() != -1 && c.drain() != null) {
                    final boolean downOwned =
                            isOwned(tileX, tileZ, c.dcx(), c.dcz()) && cells.containsKey(cellKey(c.dcx(), c.dcz()));
                    final int exitNode;
                    if (downOwned) {
                        exitNode = getOrCreateEdgeNode(nodeSpecs, edgeNodeIdx, ccx, ccz, c.dcx(), c.dcz(), c.drain());
                    } else {
                        exitNode = addNode(nodeSpecs, c.drain()[0], c.drain()[1], Endpoint.Type.DRAIN);
                        boundaryElevByNodeIdx.put(exitNode, (double) grp.getElevation(c.dcx(), c.dcz()));
                    }
                    edgeSpecs.add(new RiverNetwork.EdgeSpec(
                            centre,
                            exitNode,
                            pts(centreCoord, gateInside(centreCoord, c.drain(), ccx, ccz), c.drain()),
                            width));
                }

                // places the edges that connects sources/junctions in the boarders to the center of the cell
                for (int d = 4; d <= 7; d++) {
                    final int ox = PipelinePreprocessing.NEIGHBOR_OFFSET_X[d];
                    final int oz = PipelinePreprocessing.NEIGHBOR_OFFSET_Z[d];
                    final CellInfo n = cells.get(cellKey(ccx + ox, ccz + oz));
                    if (n == null || n.drain() == null) continue;
                    if (n.dcx() != ccx || n.dcz() != ccz) continue;
                    final boolean nOwned = isOwned(tileX, tileZ, n.ccx(), n.ccz());
                    final int entryNode;
                    if (nOwned) {
                        entryNode = getOrCreateEdgeNode(nodeSpecs, edgeNodeIdx, n.ccx(), n.ccz(), ccx, ccz, n.drain());
                    } else {
                        entryNode = addNode(nodeSpecs, n.drain()[0], n.drain()[1], Endpoint.Type.SOURCE);
                        boundaryElevByNodeIdx.put(entryNode, (double) grp.getElevation(ccx, ccz));
                    }
                    edgeSpecs.add(new RiverNetwork.EdgeSpec(
                            entryNode,
                            centre,
                            pts(n.drain(), gateInside(centreCoord, n.drain(), ccx, ccz), centreCoord),
                            Math.max(grp.getWidth(n.ccx(), n.ccz()), MIN_WIDTH)));
                }

                if (GlobalRiverProvider.isSource(grp.getArrow(ccx, ccz))) {
                    addSourceNode(
                            nodeSpecs,
                            edgeSpecs,
                            boundaryElevByNodeIdx,
                            c,
                            centre,
                            centreCoord,
                            a,
                            b,
                            width,
                            elev,
                            grp);
                }
            }
        }

        if (edgeSpecs.isEmpty()) {
            final Meanders empty =
                    new Meanders(PADDED, new float[PADDED * PADDED], new float[PADDED * PADDED], nodeSpecs, edgeSpecs);
            clearBuildState(cells, nodeSpecs, edgeSpecs, centerIdx, edgeNodeIdx, boundaryElevByNodeIdx);
            return empty;
        }

        // Border confinement is now handled by the Meanders migration (per-channel, width-scaled).
        final float[] gradX = base[2].clone();
        final float[] gradZ = base[3].clone();

        // Relaxation steps vary with the elevation of the tile's primary owned cell (2*tileCoords):
        // higher terrain gets more steps, capped at MAX_RELAX_STEPS.
        final CellInfo primaryCell = cells.get(cellKey(tileX * 2, tileZ * 2));
        final double primaryElev = (primaryCell != null) ? grp.getElevation(primaryCell.ccx(), primaryCell.ccz()) : 0.0;
        final int relaxSteps = MIN_RELAX_STEPS + (int) Math.round(Math.max(0.0, primaryElev) * RELAX_STEPS_PER_ELEV);

        final Meanders sim = new Meanders(PADDED, gradX, gradZ, nodeSpecs, edgeSpecs);
        sim.relaxLowerGrad(Math.min(relaxSteps, MAX_RELAX_STEPS));
        assignBedElevations(sim.getNetwork(), boundaryElevByNodeIdx, base[0]);
        clearBuildState(cells, nodeSpecs, edgeSpecs, centerIdx, edgeNodeIdx, boundaryElevByNodeIdx);
        return sim;
    }

    /**
     * Release the transient build scaffolding accumulated by {@link #buildGlobalNetwork}. Everything here
     * has already been consumed (the {@link Meanders}/{@link RiverNetwork} copies the node/edge specs and
     * {@code assignBedElevations} drains the boundary-elevation map), so clearing them frees the references
     * before the (returned) sim is handed back.
     */
    private static void clearBuildState(
            Map<Long, CellInfo> cells,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            Map<Long, Integer> centerIdx,
            Map<EdgeKey, Integer> edgeNodeIdx,
            Map<Integer, Double> boundaryElevByNodeIdx) {
        cells.clear();
        nodeSpecs.clear();
        edgeSpecs.clear();
        centerIdx.clear();
        edgeNodeIdx.clear();
        boundaryElevByNodeIdx.clear();
    }

    /**
     * Create the SOURCE node for an owned cell flagged {@code isSource}: place a deterministic interior
     * seed ({@link #sourceSeed}, kept {@code marginInfluence(width)} clear of the cell edges), record its
     * boundary bed elevation (the decoded terrain at the seed, floored at the downstream coarse bed), and
     * wire a seed→centre edge.
     */
    private void addSourceNode(
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            Map<Integer, Double> boundaryElevByNodeIdx,
            CellInfo c,
            int centre,
            double[] centreCoord,
            int a,
            int b,
            double width,
            float[] elev,
            GlobalRiverProvider grp) {
        final double minX = PAD + a * COARSE_PX;
        final double minZ = PAD + b * COARSE_PX;
        final double[] seed = sourceSeed(c.ccx(), c.ccz(), minX, minZ, marginInfluence(width));
        final int seedNode = addNode(nodeSpecs, seed[0], seed[1], Endpoint.Type.SOURCE);
        final double downstreamBed =
                (c.outDirection() != -1) ? grp.getElevation(c.dcx(), c.dcz()) : grp.getElevation(c.ccx(), c.ccz());
        boundaryElevByNodeIdx.put(seedNode, Math.max(sampleBilinear(elev, seed[0], seed[1]), downstreamBed));
        edgeSpecs.add(new RiverNetwork.EdgeSpec(seedNode, centre, pts(seed, centreCoord), width));
    }

    /**
     * Bottom-up bed assignment: set source/drain node elevations from boundary conditions, propagate
     * junction elevations leaves→drains (Kahn) as the min lastPointElev of the incoming channels, then
     * recompute each channel's per-point bed as a lerp from its start elevation toward its end
     * elevation, floored each step at {@code max(decoded, endElev)}. Writes {@link Endpoint#elevation} and
     * {@link Channel#bedElevations}.
     *
     * <p>Each channel's per-point elevation is floored at the bed of the terminal {@code DRAIN} it
     * ultimately flows into (not the local coarse cell), so a whole source→junction→drain path is
     * monotone non-increasing down to a single, path-consistent minimum. The drain bed of every node is
     * resolved once up front by {@link #resolveDrainElevations} rather than re-scanned per junction.
     */
    private void assignBedElevations(
            RiverNetwork network, Map<Integer, Double> boundaryElevByNodeIdx, float[] decodedElev) {
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.type == Endpoint.Type.SOURCE || endpoint.type == Endpoint.Type.DRAIN) {
                endpoint.elevation = boundaryElevByNodeIdx.getOrDefault(endpoint.id, 0.0);
            }
        }

        final Map<Integer, Double> drainElevByNodeId = resolveDrainElevations(network);

        final Map<Integer, Integer> pendingIncoming = new HashMap<>();
        final Map<Integer, Double> junctionElev = new HashMap<>();
        final ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (Endpoint endpoint : network.getNodes()) {
            if (endpoint.type == Endpoint.Type.JUNCTION) {
                pendingIncoming.put(endpoint.id, endpoint.incoming.size());
                junctionElev.put(endpoint.id, Double.POSITIVE_INFINITY);
            } else if (endpoint.type == Endpoint.Type.SOURCE && endpoint.outgoing != -1) {
                ready.add(endpoint.outgoing);
            }
        }

        // calculate the correct elevations for the junctions, and sources
        while (!ready.isEmpty()) {
            final Channel ch = network.getChannel(ready.poll());
            if (ch == null) continue;
            final Endpoint startEndpoint = network.getNode(ch.startNodeId);
            final double startElev = (startEndpoint != null) ? startEndpoint.elevation : 0.0;
            double lastPointElev = Double.isNaN(startElev) ? Double.POSITIVE_INFINITY : startElev;
            final double terminalDrainElev = drainElevByNodeId.getOrDefault(ch.endNodeId, Double.NaN);
            final double drainFloor = Double.isNaN(terminalDrainElev) ? Double.NEGATIVE_INFINITY : terminalDrainElev;
            for (double[] p : ch.spline.points()) {
                lastPointElev = Math.clamp(sampleBilinear(decodedElev, p[0], p[1]), drainFloor, lastPointElev);
            }
            final Endpoint endEndpoint = network.getNode(ch.endNodeId);
            if (endEndpoint == null) continue;
            if (endEndpoint.type == Endpoint.Type.JUNCTION) {
                junctionElev.merge(endEndpoint.id, lastPointElev, Math::min);
                final int remaining = pendingIncoming.merge(endEndpoint.id, -1, Integer::sum);
                if (remaining == 0) {
                    endEndpoint.elevation = junctionElev.get(endEndpoint.id);
                    if (endEndpoint.outgoing != -1) ready.add(endEndpoint.outgoing);
                }
            }
        }

        for (Channel ch : network.getChannels()) {
            final Endpoint startEndpoint = network.getNode(ch.startNodeId);
            final Endpoint endEndpoint = network.getNode(ch.endNodeId);
            final double startElev =
                    (startEndpoint != null && !Double.isNaN(startEndpoint.elevation)) ? startEndpoint.elevation : 0.0;
            final double endElev =
                    (endEndpoint != null && !Double.isNaN(endEndpoint.elevation)) ? endEndpoint.elevation : startElev;
            final List<double[]> pts = ch.spline.points();
            final int n = pts.size();
            final double[] bed = new double[n];
            bed[0] = startElev;
            for (int i = 1; i < n; i++) {
                final double frac = (double) i / (n - 1);
                final double candidate = Math.max(sampleBilinear(decodedElev, pts.get(i)[0], pts.get(i)[1]), endElev);
                bed[i] = Math.min(bed[i - 1], candidate + (endElev - candidate) * frac);
            }
            ch.bedElevations = bed;
        }
    }

    /**
     * For every node, the bed elevation of the unique downstream {@code DRAIN} it ultimately flows into.
     * The network is a single-outflow dendritic in-tree, so following {@link Endpoint#outgoing} edges from
     * any node reaches exactly one drain. Each downstream walk memoizes every node it touches (including
     * the drain itself), so the whole network is resolved in O(nodes) total instead of re-walking per
     * junction. Unreachable nodes (broken topology) map to {@code NaN}.
     */
    private static Map<Integer, Double> resolveDrainElevations(RiverNetwork network) {
        final Map<Integer, Double> drainElevByNodeId = new HashMap<>();
        final ArrayDeque<Integer> pathToDrain = new ArrayDeque<>();
        for (Endpoint start : network.getNodes()) {
            if (drainElevByNodeId.containsKey(start.id)) continue;
            pathToDrain.clear();
            int currentId = start.id;
            double drainElev = Double.NaN;
            while (currentId != -1) {
                final Double memoized = drainElevByNodeId.get(currentId);
                if (memoized != null) {
                    drainElev = memoized;
                    break;
                }
                final Endpoint current = network.getNode(currentId);
                if (current == null) break;
                if (current.type == Endpoint.Type.DRAIN) {
                    drainElev = current.elevation;
                    drainElevByNodeId.put(currentId, drainElev);
                    break;
                }
                pathToDrain.push(currentId);
                final Channel outgoing = network.getChannel(current.outgoing);
                currentId = (outgoing == null) ? -1 : outgoing.endNodeId;
            }
            for (int nodeId : pathToDrain) drainElevByNodeId.put(nodeId, drainElev);
        }
        return drainElevByNodeId;
    }

    // -------------------------------------------------------------------------
    // Carving
    // -------------------------------------------------------------------------

    /** A densified river sample carrying its interpolated width + bed elevation. */
    private record RiverSample(double[] coords, double width, double bedElev) implements QuadTreePoint {
        @Override
        public double[] getCoords() {
            return coords;
        }
    }

    private void carveGlobalRivers(float[] elevation, List<Channel> channels) {
        if (channels.isEmpty()) return;
        final List<RiverSample> samples = new ArrayList<>();
        for (Channel ch : channels) {
            if (ch.bedElevations == null || ch.numPts() < 2) continue;
            final QuinticHermiteSpline spline = ch.spline;
            final int segments = ch.numPts() - 1;
            final double[] pointWidths = pointWidths(ch);
            for (int i = 0; i < segments; i++) {
                final double segLength = VectorOps.distance(
                        spline.points().get(i), spline.points().get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                final double startWidth = pointWidths[i];
                final double endWidth = pointWidths[i + 1];
                final double startBed = ch.bedElevations[i];
                final double endBed = ch.bedElevations[i + 1];
                for (int s = 0; s <= sampleCount; s++) {
                    final double frac = (double) s / sampleCount;
                    final double[] point = spline.sample(i + frac);
                    final double width = startWidth + (endWidth - startWidth) * frac;
                    final double bed = startBed + (endBed - startBed) * frac;
                    samples.add(new RiverSample(new double[] {point[0], point[1]}, width, bed));
                }
            }
        }
        final ImmutableQuadTree<RiverSample> index = new ImmutableQuadTree<>(
                new double[] {-COARSE_PX * 4, -COARSE_PX * 4},
                new double[] {PADDED + COARSE_PX * 4, PADDED + COARSE_PX * 4},
                samples);

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
                    final double dist = VectorOps.distance(pixel, sample.coords());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        width = sample.width();
                        bedElev = sample.bedElev();
                    }
                }
                if (nearestDist >= MAX_CARVE_DIST) continue;
                final double margin = width * 0.5;
                final double marginInfluence = marginInfluence(width);
                if (nearestDist >= marginInfluence) continue;
                final float orig = elevation[idx];
                final double frac = (nearestDist <= margin)
                        ? 0.0
                        : Math.min(1.0, (nearestDist - margin) / (marginInfluence - margin));
                elevation[idx] = (float) (bedElev + (orig - bedElev) * frac);
            }
        }
    }

    private static double[] pointWidths(Channel ch) {
        final int n = ch.numPts();
        final double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            final double frac = (n == 1) ? 0.0 : (double) i / (n - 1);
            w[i] = ch.startWidth + (ch.endWidth - ch.startWidth) * frac;
        }
        return w;
    }

    // -------------------------------------------------------------------------
    // Local network (drainage-derived)
    // -------------------------------------------------------------------------

    private boolean[] rasterizeGlobalMask(List<Channel> channels) {
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

    private List<Channel> traceLocalNetwork(
            int[] drainage, float[] elev, boolean[] globalMask, @Nullable Stages stages) {
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

    private List<Integer> walkSegment(int start, int[] downstream, int[] inDegree) {
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

    private boolean leavesTile(List<Integer> cells) {
        return onBorder(cells.getFirst()) || onBorder(cells.getLast());
    }

    private boolean onBorder(int cell) {
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

    private @Nullable Channel buildLocalChannel(List<Integer> cells, float[] flow, int channelId) {
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
        try {
            channel.reSample(RESAMPLE_DIST);
        } catch (RuntimeException degenerate) {
            return null;
        }
        return channel;
    }

    /**
     * Resample a local channel at {@code dx = width/2} and add its points as RIVER
     * {@link HydrologicalUnit}s, stamping each with the channel normal and a shared feature id pulled from
     * {@code nextFeatureId} (one id per channel, advancing the counter).
     */
    private void addLocalChannelUnits(List<HydrologicalUnit> out, Channel ch, float[] elev, int[] nextFeatureId) {
        final double dx = Math.max(ch.width / 2.0, 0.5);
        final QuinticHermiteSpline resampled;
        try {
            resampled = ch.spline.reSample(dx);
        } catch (RuntimeException degenerate) {
            return;
        }
        final List<double[]> pts = resampled.points();
        final int n = pts.size();
        final int featureId = nextFeatureId[0]++;
        for (int i = 0; i < n; i++) {
            final double[] p = pts.get(i);
            final double frac = (n <= 1) ? 0.0 : (double) i / (n - 1);
            final double w = ch.startWidth + (ch.endWidth - ch.startWidth) * frac;
            final double bed = sampleLocal(elev, p[0], p[1]);
            final double[] nrm = resampled.normal(i);
            out.add(new HydrologicalUnit(
                    HydrologicalFeature.RIVER,
                    null,
                    List.of(p[0], p[1]),
                    List.of(nrm[0], nrm[1]),
                    w,
                    bed,
                    0,
                    featureId));
        }
    }

    // -------------------------------------------------------------------------
    // Geometry helpers (ported from the former ReliefProvider river path)
    // -------------------------------------------------------------------------

    private static int addNode(List<RiverNetwork.NodeSpec> nodes, double x, double z, Endpoint.Type type) {
        final int idx = nodes.size();
        nodes.add(new RiverNetwork.NodeSpec(x, z, type));
        return idx;
    }

    private static double[] nodeCoord(List<RiverNetwork.NodeSpec> nodes, int idx) {
        final RiverNetwork.NodeSpec ns = nodes.get(idx);
        return new double[] {ns.x(), ns.z()};
    }

    private static ArrayList<double[]> pts(double[]... ps) {
        final ArrayList<double[]> list = new ArrayList<>(ps.length);
        for (double[] p : ps) list.add(new double[] {p[0], p[1]});
        return list;
    }

    /**
     * Gate point steering a channel between the cell centre and an edge drain point. Returns the midpoint between
     * {@code centre} and the edge {@code side} point, displaced by a small deterministic offset keyed on the cell
     * coordinates (so the jitter is stable across rebuilds and consistent for both flanking tiles).
     */
    private static double[] gateInside(double[] centre, double[] side, int ccx, int ccz) {
        final double mx = 0.5 * (centre[0] + side[0]);
        final double mz = 0.5 * (centre[1] + side[1]);
        final long h = cellHash(ccx, ccz);
        final double angle = (h & 0xFFFFL) / 65536.0 * 2.0 * Math.PI;
        final double mag = ((h >>> 16) & 0xFFFFL) / 65536.0 * GATE_JITTER;
        return new double[] {mx + Math.cos(angle) * mag, mz + Math.sin(angle) * mag};
    }

    /** Deterministic 64-bit hash of a cell coordinate pair (SplitMix64 finalizer). */
    private static long cellHash(int ccx, int ccz) {
        long z = (((long) ccx) << 32) ^ (ccz & 0xFFFFFFFFL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static boolean isOwned(int tileX, int tileZ, int ccx, int ccz) {
        final int a = ccx - tileX * 2;
        final int b = ccz - tileZ * 2;
        return a >= 0 && a <= 1 && b >= 0 && b <= 1;
    }

    private static int getOrCreateEdgeNode(
            List<RiverNetwork.NodeSpec> nodes,
            Map<EdgeKey, Integer> edgeNodeIdx,
            int c1x,
            int c1z,
            int c2x,
            int c2z,
            double[] coord) {
        final EdgeKey key = edgeKey(c1x, c1z, c2x, c2z);
        final Integer existing = edgeNodeIdx.get(key);
        if (existing != null) return existing;
        final int created = addNode(nodes, coord[0], coord[1], Endpoint.Type.JUNCTION);
        edgeNodeIdx.put(key, created);
        return created;
    }

    private static EdgeKey edgeKey(int ax, int az, int bx, int bz) {
        final boolean aLower = (ax < bx) || (ax == bx && az <= bz);
        final int lx = aLower ? ax : bx;
        final int lz = aLower ? az : bz;
        final int axis = (ax != bx) ? 0 : 1;
        return new EdgeKey(lx, lz, axis);
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /**
     * The exit point on the cell's cardinal-arrow edge: the edge pixel whose decoded elevation is closest
     * to {@code target} (the cell's coarse bed). The exit edge is a constant-Z line (dir 4/5) or a
     * constant-X line (dir 6/7) on the cell boundary. When that line lies on the tile's outer border the
     * drain is a hand-off to the neighbour tile and may sit on the seam (only corners are avoided, via
     * {@link #nearTileCorner}); when the line is interior to the tile the drain must stay clear of the
     * whole border band ({@link #nearTileBorder}).
     */
    private double[] findDrain(
            int ccx, int ccz, int dir, int tileX, int tileZ, float[] elev, double target, double marginInfl) {
        final int minXi = PAD + (ccx - tileX * 2) * COARSE_PX;
        final int minZi = PAD + (ccz - tileZ * 2) * COARSE_PX;
        final boolean fixedZ = (dir == 4 || dir == 5); // edge is a constant-Z line
        final int line = fixedZ ? (dir == 4 ? minZi : minZi + COARSE_PX) : (dir == 6 ? minXi : minXi + COARSE_PX);
        if (line < 0 || line >= PADDED) return null;
        final boolean edgeOnSeam = (line == PAD || line == PAD + GRID);
        final int from = Math.max(0, fixedZ ? minXi : minZi);
        final int to = Math.min(PADDED, (fixedZ ? minXi : minZi) + COARSE_PX);

        int bestX = -1;
        int bestZ = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int t = from; t < to; t++) {
            final int xi = fixedZ ? t : line;
            final int zi = fixedZ ? line : t;
            if (edgeOnSeam ? nearTileCorner(xi, zi, marginInfl) : nearTileBorder(xi, zi, marginInfl)) continue;
            final double diff = Math.abs(elev[xi * PADDED + zi] - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestX = xi;
                bestZ = zi;
            }
        }
        if (bestX < 0) return null;
        return new double[] {bestX, bestZ};
    }

    /**
     * The lowest-elevation interior pixel of the cell, or {@code null} if none qualify. Pixels within
     * {@code marginInfl} of the tile border are skipped so the lastPointElev drain never lands on the outer
     * band (keeping it clear of the seam shared with the neighbouring tile).
     */
    private double[] findLowestInCell(int ccx, int ccz, int tileX, int tileZ, float[] elev, double marginInfl) {
        final int minXi = PAD + (ccx - tileX * 2) * COARSE_PX;
        final int minZi = PAD + (ccz - tileZ * 2) * COARSE_PX;
        final int maxXi = minXi + COARSE_PX;
        final int maxZi = minZi + COARSE_PX;

        int bestX = -1;
        int bestZ = -1;
        double bestElev = Double.MAX_VALUE;
        for (int xi = Math.max(0, minXi); xi < Math.min(PADDED, maxXi); xi++) {
            for (int zi = Math.max(0, minZi); zi < Math.min(PADDED, maxZi); zi++) {
                if (nearTileBorder(xi, zi, marginInfl)) continue;
                final double e = elev[xi * PADDED + zi];
                if (e < bestElev) {
                    bestElev = e;
                    bestX = xi;
                    bestZ = zi;
                }
            }
        }
        if (bestX < 0) return null;
        return new double[] {bestX, bestZ};
    }

    /** True when {@code (px, pz)} sits within {@code marginInfl} of any of the four tile-interior edges. */
    private static boolean nearTileBorder(double px, double pz, double marginInfl) {
        final double lo = PAD;
        final double hi = PAD + GRID;
        return px - lo < marginInfl || hi - px < marginInfl || pz - lo < marginInfl || hi - pz < marginInfl;
    }

    private static boolean nearTileCorner(double px, double pz, double marginInfl) {
        final double lo = PAD;
        final double hi = PAD + GRID;
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

    private static double[] sourceSeed(int ccx, int ccz, double minX, double minZ, double marginInfl) {
        final Random rng = new Random(((long) ccx * 0x9E3779B97F4A7C15L) ^ ((long) ccz * 0xC2B2AE3D27D4EB4FL));
        final double span = COARSE_PX - 2 * marginInfl;
        if (span <= 0) return new double[] {minX + COARSE_HALF, minZ + COARSE_HALF};
        final double rx = marginInfl + rng.nextDouble() * span;
        final double rz = marginInfl + rng.nextDouble() * span;
        return new double[] {minX + rx, minZ + rz};
    }

    private static double marginInfluence(double width) {
        return 5 * width;
    }

    private static double sampleBilinear(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, PADDED);
    }

    private static double sampleLocal(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, GRID);
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /** True when a hydrological feature lies within {@link #QUERY_RADIUS} of {@code coords}. */
    public boolean insideMargin(double[] coords) {
        return !units.getValuesWithin(coords, QUERY_RADIUS).isEmpty();
    }

    /**
     * Gather every hydrological unit whose feature influences {@code pt} (a point in the relief-pixel
     * frame), re-stamped into world coords and returned as a flat array <b>sorted by
     * {@link HydrologicalUnit#id() id}</b> — the universal iterate primitive both
     * {@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver} and the painter consume
     * (walk equal-id runs via
     * {@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile#forEachIdRun}).
     *
     * <p>A unit is kept when {@code pt} lies within that unit's own
     * {@link FractalTerrainConfig#maxRiverInfluence influence} radius. The query spans tile borders (a
     * river within influence may live in a neighbouring tile); stored coords are tile-local, so each is
     * translated to the common world frame by adding its owning tile's origin before the distance test.
     */
    public HydrologicalUnit[] queryInfluence(double[] pt) {
        final List<HydrologicalUnit> hits = new ArrayList<>();
        units.forEachTileWithin(pt, FractalTerrainConfig.MAX_INFLUENCE_RADIUS, (originX, originZ, local) -> {
            for (HydrologicalUnit u : local) {
                final double worldX = u.coord().get(0) + originX;
                final double worldZ = u.coord().get(1) + originZ;
                final double influence = FractalTerrainConfig.maxRiverInfluence(u.width());
                final double dx = worldX - pt[0];
                final double dz = worldZ - pt[1];
                if (dx * dx + dz * dz > influence * influence) continue;
                hits.add(new HydrologicalUnit(
                        u.type(),
                        u.rosgenType(),
                        List.of(worldX, worldZ),
                        u.normal(),
                        u.width(),
                        u.elevation(),
                        u.time(),
                        u.id()));
            }
        });
        hits.sort(Comparator.comparingInt(HydrologicalUnit::id));
        return hits.toArray(new HydrologicalUnit[0]);
    }

    // -------------------------------------------------------------------------
    // Debug access
    // -------------------------------------------------------------------------

    @TestOnly
    public Stages debugStages(int tileX, int tileZ) {
        final Stages stages = new Stages();
        buildTile(tileX, tileZ, stages);
        return stages;
    }

    @TestOnly
    public static final class Stages {
        public float[] flow;
        public boolean[] riverMask;
        public float[] carvedElevation;
        public List<Channel> channels;
        public List<Channel> localChannels;
        public RiverNetwork network;
    }
}
