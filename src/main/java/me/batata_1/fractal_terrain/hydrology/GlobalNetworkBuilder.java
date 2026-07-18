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
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.meanders.*;

/**
 * Responsibility: build the per-tile <em>global</em>-river subgraph — the 2×2 owned coarse cells plus
 * their one-cell halo, read off {@link GlobalRiverProvider}'s arrow field — into {@link RiverNetwork}
 * node/edge specs, then relax it down-gradient with a {@link Meanders} simulation, returning the relaxed
 * network together with the boundary-elevation map it accumulated for the caller to assign bed
 * elevations. This is the deterministic core unique to {@code LocalRiverProvider.buildTile}'s step 1
 * ("global rivers: trace + relax"), extracted unchanged.
 *
 * <p>Collaborators: {@link ChannelElevationAssigner} (bed-elevation propagation, invoked by the caller
 * after {@link #build} returns); {@link HydrologyTileGeometry} (shared tile/coarse-cell geometry);
 * {@link Meanders} / {@link RiverNetwork} (the graph structure being built and relaxed);
 * {@link GlobalRiverProvider} (the coarse arrow/width/elevation source).
 *
 * <p>Invariants: purely functional over its parameters — no shared mutable state, so per-tile builds
 * from different worker threads never interact. The owned-cell topology (2×2 centres, drains,
 * junctions, sources) and gate-jitter/relax-step constants are unchanged from the original
 * {@code LocalRiverProvider.buildGlobalNetwork}; do not reorder the node/edge construction passes — later
 * passes rely on {@code centerIdx}/{@code edgeNodeIdx} populated by earlier ones.
 *
 * <p>{@link GlobalRiverProvider#getWidth} already returns native-rescaled widths (coarse-px flow width x
 * {@code GLOBAL_WIDTH_COORD_SCALE}); every {@code EdgeSpec}/margin/seed computation below consumes that
 * value directly, so the {@link Meanders} relax step and the border-confinement margin both operate in
 * the same native-px frame as the local network -- do not re-scale it again here.
 */
final class GlobalNetworkBuilder {

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
     * The relaxed {@link Meanders} network together with the boundary-elevation map {@link #build}
     * accumulated for it (source/drain node datum keyed by minted node id), for the caller to hand to
     * {@link ChannelElevationAssigner#assign}.
     */
    record Result(Meanders network, Map<Integer, Double> boundaryElevByNodeIdx) {}

    static Result build(int tileX, int tileZ, float[][] base, GlobalRiverProvider grp) {
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
                final double marginInfl = FractalTerrainConfig.riverInfluence(
                        Math.max(grp.getWidth(ccx, ccz), FractalTerrainConfig.MIN_WIDTH));
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
                if (type == Endpoint.Type.DRAIN)
                    boundaryElevByNodeIdx.put(idx, Math.max(0, (double) grp.getElevation(ccx, ccz)));
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
                final double width = Math.max(grp.getWidth(ccx, ccz), FractalTerrainConfig.MIN_WIDTH);

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
                        boundaryElevByNodeIdx.put(entryNode, Math.max(0, (double) grp.getElevation(ccx, ccz)));
                    }
                    edgeSpecs.add(new RiverNetwork.EdgeSpec(
                            entryNode,
                            centre,
                            pts(n.drain(), gateInside(centreCoord, n.drain(), ccx, ccz), centreCoord),
                            Math.max(grp.getWidth(n.ccx(), n.ccz()), FractalTerrainConfig.MIN_WIDTH)));
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
            clearBuildState(cells, nodeSpecs, edgeSpecs, centerIdx, edgeNodeIdx);
            return new Result(empty, boundaryElevByNodeIdx);
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
        clearBuildState(cells, nodeSpecs, edgeSpecs, centerIdx, edgeNodeIdx);
        return new Result(sim, boundaryElevByNodeIdx);
    }

    /**
     * Release the transient build scaffolding accumulated by {@link #build}. Everything here has already
     * been consumed (the {@link Meanders}/{@link RiverNetwork} copies the node/edge specs), so clearing
     * them frees the references before the (returned) {@link Result} is handed back. The
     * {@code boundaryElevByNodeIdx} map is not cleared here — it is part of the returned {@link Result}
     * and is the caller's to consume.
     */
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

    /**
     * Create the SOURCE node for an owned cell flagged {@code isSource}: place a deterministic interior
     * seed ({@link #sourceSeed}, kept {@code riverInfluence(width)} clear of the cell edges), record its
     * boundary bed elevation (the decoded terrain at the seed, floored at the downstream coarse bed), and
     * wire a seed→centre edge.
     */
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
        final double[] seed = sourceSeed(c.ccx(), c.ccz(), minX, minZ, FractalTerrainConfig.riverInfluence(width));
        final int seedNode = addNode(nodeSpecs, seed[0], seed[1], Endpoint.Type.SOURCE);
        final double downstreamBed = Math.max(
                0, (c.outDirection() != -1) ? grp.getElevation(c.dcx(), c.dcz()) : grp.getElevation(c.ccx(), c.ccz()));
        boundaryElevByNodeIdx.put(seedNode, Math.max(sampleBilinear(elev, seed[0], seed[1]), downstreamBed));
        edgeSpecs.add(new RiverNetwork.EdgeSpec(seedNode, centre, pts(seed, centreCoord), width));
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

    /**
     * The lowest-elevation interior pixel of the cell, or {@code null} if none qualify. Pixels within
     * {@code marginInfl} of the tile border are skipped so the lastPointElev drain never lands on the outer
     * band (keeping it clear of the seam shared with the neighbouring tile).
     */
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
