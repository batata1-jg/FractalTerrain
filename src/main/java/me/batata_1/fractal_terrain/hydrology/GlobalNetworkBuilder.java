package me.batata_1.fractal_terrain.hydrology;

import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.COARSE_HALF;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.COARSE_PX;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PAD;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.PADDED;
import static me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry.sampleBilinear;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.meanders.*;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.providers.GlobalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifier;
import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * Step 1 of {@code LocalRiverProvider.buildTile}: turns {@link GlobalRiverProvider}'s coarse arrow field
 * into a relaxed per-tile graph, and hands back the boundary elevations the caller needs to assign beds.
 *
 * <p>Split out of the provider so the trace-and-relax core can be read and tested apart from the
 * dual-store cache plumbing around it.
 *
 * <p>Do not reorder the node/edge construction passes — later passes read state earlier ones populate.
 * Widths arrive already native-rescaled; re-scaling them here would double-apply.
 */
public final class GlobalNetworkBuilder {

    private GlobalNetworkBuilder() {}

    // ---- Global-river trace / meander relaxation ----------------------------
    private static final double GATE_JITTER = 24.0;
    private static final int MAX_RELAX_STEPS = 50;
    // Relaxation steps scale with the elevation of the tile's primary owned cell (2*tileCoords):
    // a baseline at sea level plus a per-elevation increment, so higher terrain relaxes more.
    private static final int MIN_RELAX_STEPS = 5;
    private static final double RELAX_STEPS_PER_ELEV = 0.2;

    private record EdgeKey(int lowerX, int lowerZ, int axis) {}

    private record CellInfo(int ccx, int ccz, int outDirection, int dcx, int dcz, double[] drain) {}

    /**
     * The relaxed {@link RiverNetwork} together with the boundary-elevation map {@link #build}
     * accumulated for it (source/drain node datum keyed by minted node id), for the caller to hand to
     * {@link ChannelElevationAssigner#assign}.
     *
     * <p>{@code rawElev} is the pre-carve elevation snapshot the Rosgen classifier must measure against;
     * it travels with the network because only {@link #build} runs early enough to take it.
     */
    public record Result(RiverNetwork network, int[] drainage, Map<Integer, Double> boundaryElevByNodeIdx, ChannelTyper typer) {}

    public static Result build(int tileX, int tileZ, float[][] base, GlobalRiverProvider grp) {
        final float[] elevCarvedGlobalOnly = base[0].clone();

        final Map<Long, CellInfo> cells = new HashMap<>();
        for (int a = -1; a <= 2; a++) {
            for (int b = -1; b <= 2; b++) {
                final int ccx = tileX * 2 + a;
                final int ccz = tileZ * 2 + b;
                final int arrow = grp.getArrow(ccx, ccz);
                final boolean carriesWater = GlobalRiverProvider.isRiver(arrow) || GlobalRiverProvider.isCoast(arrow);
                if (!carriesWater) continue;
                int outDir = -1;
                final int outMask = GlobalRiverProvider.outgoingMask(arrow);
                for (int d = 4; d <= 7; d++)
                    if ((outMask & (1 << d)) != 0) {
                        outDir = d;
                        break;
                    }
                int dcx = ccx, dcz = ccz;
                double[] drain;
                final double marginInfl =
                        HydrologyTuning.influence(Math.max(grp.getWidth(ccx, ccz), HydrologyTuning.MIN_WIDTH));
                if (outDir != -1) {
                    dcx = ccx + Drainage.NEIGHBOR_OFFSET_X[outDir];
                    dcz = ccz + Drainage.NEIGHBOR_OFFSET_Z[outDir];
                    drain = findDrain(ccx, ccz, outDir, tileX, tileZ, elevCarvedGlobalOnly, grp.getElevation(ccx, ccz), marginInfl);
                } else {
                    // no downstream arrow: the cell terminates at its lowest-elevation interior point.
                    drain = findLowestInCell(ccx, ccz, tileX, tileZ, elevCarvedGlobalOnly, marginInfl);
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
                if (type == Endpoint.Type.DRAIN) {
                    boundaryElevByNodeIdx.put(idx, 0.0);
                }
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
                final double width = Math.max(grp.getWidth(ccx, ccz), HydrologyTuning.MIN_WIDTH);

                // drains to another cell. Places the channel that connects the center to the drainage point
                if (c.outDirection() != -1 && c.drain() != null) {
                    final boolean downOwned =
                            isOwned(tileX, tileZ, c.dcx(), c.dcz()) && cells.containsKey(cellKey(c.dcx(), c.dcz()));
                    final int exitNode;
                    if (downOwned) {
                        exitNode = getOrCreateEdgeNode(nodeSpecs, edgeNodeIdx, ccx, ccz, c.dcx(), c.dcz(), c.drain());
                    } else {
                        exitNode = addNode(nodeSpecs, c.drain()[0], c.drain()[1], Endpoint.Type.DRAIN);
                        boundaryElevByNodeIdx.put(exitNode, Math.max(0, (double) grp.getElevation(c.dcx(), c.dcz())));
                    }
                    edgeSpecs.add(new RiverNetwork.EdgeSpec(
                            centre,
                            exitNode,
                            pts(centreCoord, gateInside(centreCoord, c.drain(), ccx, ccz), c.drain()),
                            grp.getFlow(ccx, ccz)));
                }

                // places the edges that connects sources/junctions in the boarders to the center of the cell
                for (int d = 4; d <= 7; d++) {
                    final int ox = Drainage.NEIGHBOR_OFFSET_X[d];
                    final int oz = Drainage.NEIGHBOR_OFFSET_Z[d];
                    final CellInfo n = cells.get(cellKey(ccx + ox, ccz + oz));
                    if (n == null || n.drain() == null) continue;
                    if (n.dcx() != ccx || n.dcz() != ccz) continue;
                    final boolean nOwned = isOwned(tileX, tileZ, n.ccx(), n.ccz());
                    final int entryNode;
                    if (nOwned) {
                        entryNode = getOrCreateEdgeNode(nodeSpecs, edgeNodeIdx, n.ccx(), n.ccz(), ccx, ccz, n.drain());
                    } else {
                        entryNode = addNode(nodeSpecs, n.drain()[0], n.drain()[1], Endpoint.Type.SOURCE);
                        boundaryElevByNodeIdx.put(entryNode, Math.max(0, (double) grp.getElevation(ccx, ccz)));
                    }
                    edgeSpecs.add(new RiverNetwork.EdgeSpec(
                            entryNode,
                            centre,
                            pts(n.drain(), gateInside(centreCoord, n.drain(), ccx, ccz), centreCoord),
                            grp.getFlow(n.ccx(), n.ccz())));
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
                            elevCarvedGlobalOnly,
                            grp);
                }
            }
        }

        ChannelTyper typer = new ReachRosgenClassifier(elevCarvedGlobalOnly,PADDED);

        final RiverNetwork network = new RiverNetwork(PADDED, nodeSpecs, edgeSpecs);
        if (edgeSpecs.isEmpty()) {
            return new Result(network,
                    Drainage.computeDrainageDirection(
                            Drainage.fillSinks(elevCarvedGlobalOnly,PADDED, HydrologyTuning.FILL_PADDING),PADDED
                    ),
                    boundaryElevByNodeIdx,
                    typer);
        }

        // Relaxation steps vary with the elevation of the tile's primary owned cell (2*tileCoords):
        // higher terrain gets more steps, capped at MAX_RELAX_STEPS.
        final CellInfo primaryCell = cells.get(cellKey(tileX * 2, tileZ * 2));
        final double primaryElev = (primaryCell != null) ? grp.getElevation(primaryCell.ccx(), primaryCell.ccz()) : 0.0;
        final int relaxSteps = MIN_RELAX_STEPS + (int) Math.round(Math.max(0.0, primaryElev) * RELAX_STEPS_PER_ELEV);

        new GradientNetworkRelaxation(network, base[2].clone(), base[3].clone())
                .relax(Math.min(relaxSteps, MAX_RELAX_STEPS), 5);
        clearBuildState(cells, nodeSpecs, edgeSpecs, centerIdx, edgeNodeIdx);

        ChannelElevationAssigner.assign(network,boundaryElevByNodeIdx, elevCarvedGlobalOnly);

        HydrologyProfileInprinter.carveRiverInfluence(elevCarvedGlobalOnly,collect(network,typer, elevCarvedGlobalOnly),PADDED);

        return new Result(network,
                Drainage.computeDrainageDirection(
                Drainage.fillSinks(elevCarvedGlobalOnly,PADDED, HydrologyTuning.FILL_PADDING),PADDED
        ), boundaryElevByNodeIdx, typer);
    }

    private static List<HydrologicalPrimitive> collect(RiverNetwork network,ChannelTyper typer, float[] elev) {
        var list = network.collectPrimitives(0,0,channelId -> true, typer,(x,z,bed,width) -> {
            double delta = Math.abs(Interpolation.sampleNearest(elev,x,z,PADDED) - bed);
            return HydrologyTuning.influence(width,delta);
        });
        list.sort(HydrologicalPrimitive.comparator);
        return list;
    };

    /** Drops the build scaffolding once the graph has copied it, so a tile build does not hold it for
     *  the lifetime of the returned {@link Result}. */
    private static void clearBuildState(
            Map<Long, CellInfo> cells,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            Map<Long, Integer> centerIdx,
            Map<EdgeKey, Integer> edgeNodeIdx) {
        cells.clear();
        nodeSpecs.clear();
        edgeSpecs.clear();
        centerIdx.clear();
        edgeNodeIdx.clear();
    }

    /** Seeds a headwater inside an owned cell, kept clear of the cell edges so its carve band cannot
     *  spill into the neighbouring cell. */
    private static void addSourceNode(
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
        final double downstreamBed = Math.max(
                0, (c.outDirection() != -1) ? grp.getElevation(c.dcx(), c.dcz()) : grp.getElevation(c.ccx(), c.ccz()));
        final double[] seed = sourceSeed(c.ccx(), c.ccz(), minX, minZ, HydrologyTuning.influence(width));
        final int seedNode = addNode(nodeSpecs, seed[0], seed[1], Endpoint.Type.SOURCE);
        boundaryElevByNodeIdx.put(seedNode, Math.max(sampleBilinear(elev, seed[0], seed[1]), downstreamBed));
        edgeSpecs.add(
                new RiverNetwork.EdgeSpec(seedNode, centre, pts(seed, centreCoord), grp.getFlow(c.ccx(), c.ccz())));
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

    /** Steers a channel between cell centre and edge drain. The offset is keyed on cell coordinates so
     *  both tiles flanking a seam compute the same gate. */
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

    /** Where a cell's river exits. On the tile's outer border the drain is a hand-off to the neighbour
     *  and may sit on the seam; an interior edge must stay clear of the whole border band. */
    private static double[] findDrain(
            int ccx, int ccz, int dir, int tileX, int tileZ, float[] elev, double target, double marginInfl) {
        final int minXi = PAD + (ccx - tileX * 2) * COARSE_PX;
        final int minZi = PAD + (ccz - tileZ * 2) * COARSE_PX;
        final boolean fixedZ = (dir == 4 || dir == 5); // edge is a constant-Z line
        final int line = fixedZ ? (dir == 4 ? minZi : minZi + COARSE_PX) : (dir == 6 ? minXi : minXi + COARSE_PX);
        if (line < 0 || line >= PADDED) return null;
        final boolean edgeOnSeam = (line == PAD || line == PAD + HydrologyTileGeometry.GRID);
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

    /** Lowest interior pixel of a cell, border band excluded so a drain never lands on a shared seam. */
    private static double[] findLowestInCell(int ccx, int ccz, int tileX, int tileZ, float[] elev, double marginInfl) {
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
        final double hi = PAD + HydrologyTileGeometry.GRID;
        return px - lo < marginInfl || hi - px < marginInfl || pz - lo < marginInfl || hi - pz < marginInfl;
    }

    private static boolean nearTileCorner(double px, double pz, double marginInfl) {
        final double lo = PAD;
        final double hi = PAD + HydrologyTileGeometry.GRID;
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
}
