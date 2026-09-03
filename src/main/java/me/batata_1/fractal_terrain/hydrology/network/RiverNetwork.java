package me.batata_1.fractal_terrain.hydrology.network;

import static me.batata_1.fractal_terrain.config.DebugConfig.DEBUG_CROSSING_WINNER;
import static me.batata_1.fractal_terrain.config.DebugConfig.DEBUG_STEPS;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.AbandonedRiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.InfluenceSampler;
import me.batata_1.fractal_terrain.hydrology.features.OxbowLakePrimitive;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The river-network graph, owning every topology and geometry mutation the hydrology pipeline performs.
 *
 * <p>All mutation funnels through one validated seam between the canonical channel view and the atomic
 * node view ({@link AtomicView}), so topology rules are checked in exactly one place. Stream-capture
 * collision handling lives here in {@link #manageCollisions}, never split into its own resolver.
 *
 * <p>{@link #collectPrimitives} is the pipeline's exit: it packages the network into {@link HydrologicalPrimitive}s
 * for the caller to freeze into a spatial index. See {@code README.md} for the view seam in detail.
 */
public final class RiverNetwork {

    private static final double INF = 1e3;
    /** Floor on the resample spacing used when converting features to {@link HydrologicalPrimitive}s. */
    private static final double MIN_CONVERT_SPACING = 0.5;

    private static final Logger LOG = getLogger(RiverNetwork.class);

    private final int gridSize;
    private final QuadTree<Channel.ChannelPt> quadTree =
            new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});

    // graph storage: stable channel/node ids (sparse after prune/rewire)
    private final Int2ObjectOpenHashMap<Channel> channels = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Endpoint> nodes = new Int2ObjectOpenHashMap<>();
    private int nextChannelId = 0;
    private int nextNodeId = 0;

    // history (only populated when saveHistory is true)
    private final boolean saveHistory;
    private final int maxSavedStates;

    /** Oxbow and abandoned-channel primitives already shed, oldest first; drained by {@link #collectPrimitives}. */
    private final ArrayDeque<HydrologicalPrimitive> lastStates = new ArrayDeque<>();

    // for each endpoint, if it is source/drain, update the channels to match it. If it is junction, take the average of
    // the channels end positions.
    public void resolveEndpoints() {
        for (Endpoint endpoint : nodes.values()) {
            if (endpoint.type == Endpoint.Type.DRAIN) {
                final double[] realCoord = endpoint.coord;
                for (int id : endpoint.incoming) {
                    final Channel ch = channels.get(id);
                    ch.spline.points().set(ch.spline.getSize() - 1, realCoord);
                }
                continue;
            }
            if (endpoint.type == Endpoint.Type.SOURCE) {
                final double[] realCoord = endpoint.coord;
                final Channel ch = channels.get(endpoint.outgoing);
                ch.spline.points().set(0, realCoord);
                continue;
            }

            int avgCount = 1;
            double[] avgSum = new double[2];

            for (int id : endpoint.incoming) {
                final Channel ch = channels.get(id);
                avgSum = VectorOps.add(avgSum, ch.spline.points().getLast());
                avgCount++;
            }
            avgSum = VectorOps.add(
                    avgSum, channels.get(endpoint.outgoing).spline.points().getFirst());

            avgSum = VectorOps.div(avgSum, avgCount);

            for (int id : endpoint.incoming) {
                final Channel ch = channels.get(id);
                ch.spline.points().set(ch.spline.getSize() - 1, avgSum);
            }
            channels.get(endpoint.outgoing).spline.points().set(0, avgSum);
        }
    }

    /**
     * A vertex of the supplied initial network — an atomic node spec (position + role). Consumed only as
     * inert input to {@link #buildFromSpecs}, which folds these plus the {@link EdgeSpec}s into an
     * {@link AtomicView} and calls {@link #update}.
     */
    public record NodeSpec(double x, double z, Endpoint.Type type) {}

    /**
     * A directed crossingEdge of the supplied initial network — an atomic crossingEdge spec; {@code pts} include the two
     * endpoints. {@code flow} is the (per-cell) raw flow-accumulation value for the crossingEdge: a SOURCE start
     * carries it as its seed {@code ownFlow}, a DRAIN end as its {@code anchorFlow};
     * Width is derived downstream via {@link HydrologyTuning#widthFromFlow}.
     */
    public record EdgeSpec(int startNodeIdx, int endNodeIdx, List<double[]> pts, double flow) {}

    /** The production construction path: a graph that keeps no history, resampled at the default spacing. */
    public RiverNetwork(int gridSize, List<NodeSpec> nodeSpecs, List<EdgeSpec> edgeSpecs) {
        this(gridSize, nodeSpecs, edgeSpecs, false, 0, HydrologyTuning.DX);
    }

    public RiverNetwork(
            int gridSize,
            List<NodeSpec> nodeSpecs,
            List<EdgeSpec> edgeSpecs,
            boolean saveHistory,
            int maxSavedStates,
            double resampleDist) {

        this.gridSize = gridSize;
        this.saveHistory = saveHistory;
        this.maxSavedStates = maxSavedStates;
        if (FractalTerrainConfig.DEBUG_RIVER_NET)
            Debug.river.seeNetwork(gridSize, nodeSpecs, edgeSpecs, "river_network", "_");

        buildFromSpecs(nodeSpecs, edgeSpecs, resampleDist);
    }

    // ---------------------------------------------------------------------------------------------
    // Construction: everything enters through the atomic view (no split/mint primitives)
    // ---------------------------------------------------------------------------------------------

    /** Bulk-builds the graph from specs through the same seam every other mutation uses.
     *  SOURCE/DRAIN ids are pinned to their spec index, so a caller's boundary-elevation map keyed on
     *  that index stays valid across the build. */
    private void buildFromSpecs(List<NodeSpec> nodeSpecs, List<EdgeSpec> edgeSpecs, double resampleDist) {
        final AtomicView atomic = new AtomicView();

        // Boundary flow inputs, derived from the edges touching each node spec.
        final double[] sourceSeed = new double[nodeSpecs.size()];
        final double[] drainAnchor = new double[nodeSpecs.size()];
        for (EdgeSpec es : edgeSpecs) {
            if (nodeSpecs.get(es.startNodeIdx()).type() == Endpoint.Type.SOURCE)
                sourceSeed[es.startNodeIdx()] = es.flow();
            if (nodeSpecs.get(es.endNodeIdx()).type() == Endpoint.Type.DRAIN)
                drainAnchor[es.endNodeIdx()] = Math.max(drainAnchor[es.endNodeIdx()], es.flow());
        }

        // One atomic node per node spec (endpoints collapse here). SOURCE/DRAIN carry their spec index as
        // the canonical id to preserve; interior/junction carry NONE.
        final int[] specNodeAtomicId = new int[nodeSpecs.size()];
        for (int i = 0; i < nodeSpecs.size(); i++) {
            final NodeSpec ns = nodeSpecs.get(i);
            final boolean boundary = ns.type().isSourceOrDrain();
            final double ownFlow = (ns.type() == Endpoint.Type.SOURCE) ? sourceSeed[i] : FLOW_PER_CELL;
            final double anchor = (ns.type() == Endpoint.Type.DRAIN) ? drainAnchor[i] : -1;
            specNodeAtomicId[i] =
                    atomic.addNode(new double[] {ns.x(), ns.z()}, ns.type(), boundary ? i : NONE, ownFlow, anchor);
        }

        for (EdgeSpec es : edgeSpecs) {
            final List<double[]> pts = resamplePts(es.pts(), es.flow(), resampleDist);
            final int last = pts.size() - 1;
            int prev = specNodeAtomicId[es.startNodeIdx()];
            for (int i = 1; i < last; i++) {
                final int interior = atomic.addNode(pts.get(i), null, NONE, FLOW_PER_CELL, -1);
                atomic.addDirectedEdge(prev, interior);
                prev = interior;
            }
            atomic.addDirectedEdge(prev, specNodeAtomicId[es.endNodeIdx()]);
        }

        update(atomic);
    }

    /** Resample an crossingEdge's raw endpoint-inclusive polyline at {@code resampleDist} (endpoints preserved). */
    private static List<double[]> resamplePts(List<double[]> pts, double flow, double resampleDist) {
        final double[] tmpFlow = new double[pts.size()];
        Arrays.fill(tmpFlow, flow);
        final Channel tmp = new Channel(new ObjectArrayList<>(pts), tmpFlow, 0);
        if (tmp.isResampleable()) {
            try {
                tmp.reSample(resampleDist);
            } catch (RuntimeException runaway) {
                // pathological geometry: fall back to the raw points rather than fail construction
            }
        }
        return tmp.spline.points();
    }

    // ---------------------------------------------------------------------------------------------
    // The two views + the seam
    //
    // The canonical (channel) view is the field storage above (channels/nodes maps + id counters).
    // The atomic (node) view ({@link AtomicView}) is a List<List<Integer>> adjacency over per-node
    // data where every interior spline point is a first-class node. {@link #viewAtomic()} converts
    // canonical -> atomic; {@link #update} folds an atomic view back into `this` IN PLACE.
    // ---------------------------------------------------------------------------------------------

    /** Sentinel for "no canonical id" (interior/JUNCTION-equivalent atomic nodes) and "unset crossingEdge". */
    private static final int NONE = -1;

    /** The per-cell own-flow constant carried by interior/junction atomic nodes (see {@link #viewAtomic()}). */
    private static final double FLOW_PER_CELL = HydrologyTuning.FLOW_PER_CELL_LOCAL;

    /** Half of the mutation seam: exposes every spline point as a graph node so per-point algorithms
     *  (flow, planarization, capture) can work uniformly. {@link #update} folds the result back. */
    public AtomicView viewAtomic() {
        final AtomicView atomic = new AtomicView();
        final Int2IntOpenHashMap endpointToAtomicId = new Int2IntOpenHashMap(); // canonical Endpoint id -> atomic id
        endpointToAtomicId.defaultReturnValue(-1);

        final List<Integer> channelIds = new ObjectArrayList<>(channels.keySet());
        Collections.sort(channelIds);

        for (int channelId : channelIds) {
            final Channel ch = channels.get(channelId);
            final List<double[]> pts = ch.spline.points();
            final int last = pts.size() - 1;
            final int[] atomicIdOfPoint = new int[pts.size()];

            for (int i = 0; i <= last; i++) {
                if (i != 0 && i != last) {
                    // interior point: always a fresh atomic node, keyed on nothing but its position in
                    // this channel (no epsilon collapse). Carries the per-cell ownFlow constant; the
                    // derived flow is populated later by accumulateAndCorrectFlow, not carried here.
                    atomicIdOfPoint[i] = atomic.addNode(pts.get(i), null, NONE, FLOW_PER_CELL, -1);
                    continue;
                }
                // endpoint point: collapse keyed on the canonical Endpoint node id.
                final int endpointNodeId = (i == 0) ? ch.startNodeId : ch.endNodeId;
                final int existing = endpointToAtomicId.get(endpointNodeId);
                if (existing != -1) {
                    atomicIdOfPoint[i] = existing;
                    continue;
                }
                final Endpoint ep = nodes.get(endpointNodeId);
                final boolean boundary = ep.isSourceOrDrain();
                // ownFlow: SOURCE carries its captured seed (ep.boundaryFlow); DRAIN/JUNCTION carry the
                // per-cell constant. anchorFlow: DRAIN carries its anchor (ep.boundaryFlow), else unused.
                final double ownFlow = (ep.type == Endpoint.Type.SOURCE) ? ch.flow[0] : FLOW_PER_CELL;
                final double anchorFlow = (ep.type == Endpoint.Type.DRAIN) ? ch.flow[ch.flow.length - 1] : -1;
                final int atomicId =
                        atomic.addNode(pts.get(i), ep.type, boundary ? endpointNodeId : NONE, ownFlow, anchorFlow);
                endpointToAtomicId.put(endpointNodeId, atomicId);
                atomicIdOfPoint[i] = atomicId;
            }

            for (int i = 0; i < last; i++) {
                atomic.addDirectedEdge(atomicIdOfPoint[i], atomicIdOfPoint[i + 1]); // directed i -> i+1
            }
            atomic.pointAtomicIds.put(channelId, atomicIdOfPoint);
        }
        return atomic;
    }

    /** Enforces invariant K1, the hard precondition of {@link #update}. Throws rather than asserts, so
     *  the guard holds without {@code -ea}. */
    void assertSingleOutflow(AtomicView atomic) {
        for (int id = 0; id < atomic.size(); id++) {
            final int outdeg = atomic.adjacency.get(id).size();
            final Endpoint.Type role = atomic.role(id);
            if (role == Endpoint.Type.DRAIN) {
                if (outdeg != 0)
                    throw new IllegalStateException("DRAIN node " + id + " must have no outgoing crossingEdge");
            } else {
                // SOURCE / JUNCTION / interior all require exactly one outgoing crossingEdge
                if (outdeg != 1)
                    throw new IllegalStateException((role == null ? "interior" : role.toString()) + " node " + id
                            + " must have exactly one outgoing crossingEdge, had " + outdeg);
            }
        }
    }

    /** The other half of the seam, folding the atomic view back in place. Preserves SOURCE/DRAIN ids
     *  because boundary-elevation maps key on them; re-assigns every channel id, which is why the
     *  local/global channel distinction cannot survive a collision pass. */
    public RiverNetwork update(AtomicView atomic) {

        final double[] flow = atomic.accumulateAndCorrectFlow();
        assertSingleOutflow(atomic); // K1 — before any mutation

        final int n = atomic.size();
        final int[] indegree = new int[n];
        for (int u = 0; u < n; u++) for (int v : atomic.adjacency.get(u)) indegree[v]++;

        // structural nodes = source / drain / confluence (in-degree >= 2); sorted for a deterministic
        // emission order independent of HashMap/HashSet iteration.
        final IntSortedSet structural = new IntAVLTreeSet();
        for (int id = 0; id < n; id++) {
            final Endpoint.Type role = atomic.role(id);
            if ((role != null && role.isSourceOrDrain()) || indegree[id] >= 2) {
                structural.add(id);
            }
        }

        // Preserve the canonical id of every SOURCE/DRAIN atomic node; find the max so fresh ids start
        // past it. JUNCTION-equivalents stay NONE for now and get a fresh id below.
        final int[] canonicalIdOf = new int[n];
        Arrays.fill(canonicalIdOf, NONE);
        int maxPreserved = -1;
        for (int id : structural) {
            final Endpoint.Type role = atomic.role(id);
            if (role != null && role.isSourceOrDrain()) {
                canonicalIdOf[id] = atomic.canonicalId(id);
                maxPreserved = Math.max(maxPreserved, atomic.canonicalId(id));
            }
        }

        // IN-PLACE reset: wipe the canonical view; set the node counter PAST every preserved id.
        this.channels.clear();
        this.nodes.clear();
        this.quadTree.clear();
        this.nextChannelId = 0;
        this.nextNodeId = maxPreserved + 1;

        // Assign fresh, deterministic (sorted) ids to JUNCTION-equivalent structural nodes so each
        // structural atomic node maps to exactly one canonical id (a confluence is shared across chains).
        for (int id : structural) {
            if (canonicalIdOf[id] == NONE) canonicalIdOf[id] = nextNodeId++;
        }

        for (int start : structural) { // sorted — deterministic emission order
            if (atomic.role(start) == Endpoint.Type.DRAIN) continue; // drains only terminate a chain
            int cur = onlyOutgoing(atomic, start);
            final List<Integer> chain = new ObjectArrayList<>();
            chain.add(start);
            while (!structural.contains(cur)) {
                chain.add(cur);
                cur = onlyOutgoing(atomic, cur); // interior node: single outgoing crossingEdge (K1)
            }
            chain.add(cur); // cur: the next structural node (confluence or drain)
            emitChannel(atomic, flow, chain, canonicalIdOf);
        }
        return this;
    }

    /** The single directed tree-successor of {@code id} (K1 guarantees exactly one for non-drain nodes). */
    private static int onlyOutgoing(AtomicView atomic, int id) {
        return atomic.adjacency.get(id).getFirst();
    }

    /** Emits one chain as a wired-in {@link Channel}; the per-chain step of {@link #update}. */
    private void emitChannel(AtomicView atomic, double[] accumulatedFlow, List<Integer> chain, int[] canonicalIdOf) {
        final List<double[]> points = new ObjectArrayList<>(chain.size());
        final double[] flow = new double[chain.size()];
        for (int i = 0; i < chain.size(); i++) {
            points.add(atomic.pos(chain.get(i)));
            flow[i] = accumulatedFlow[chain.get(i)]; // DERIVED flow (populated by accumulateAndCorrectFlow)
        }

        final int startAtomic = chain.getFirst();
        final int endAtomic = chain.getLast();
        final Endpoint start = ensureNode(atomic, startAtomic, canonicalIdOf[startAtomic], points.getFirst());
        final Endpoint end = ensureNode(atomic, endAtomic, canonicalIdOf[endAtomic], points.getLast());

        // inlined K1 guard: the start endpoint must not already own an outgoing crossingEdge (single-outflow)
        if (start.outgoing != NONE) {
            throw new IllegalStateException("node " + start.id + " would have >1 outgoing crossingEdge");
        }

        final int id = nextChannelId++;
        final Channel ch = new Channel(points, flow, id);
        ch.startNodeId = start.id;
        ch.endNodeId = end.id;
        channels.put(id, ch);
        start.outgoing = id;
        end.incoming.add(id);
        insertChannelInQuadTree(ch); // kept QuadTree helper (also used by manageCutoffs)
        // bedElevations intentionally NOT preserved — the seam is bed-elevation-agnostic
    }

    /** Gets or creates the {@link Endpoint} for a structural node, shared across chains so a confluence
     *  is one vertex rather than one per incident channel. */
    private Endpoint ensureNode(AtomicView atomic, int atomicId, int canonicalId, double[] pos) {
        final Endpoint existing = nodes.get(canonicalId);
        if (existing != null) return existing;
        final Endpoint.Type role = atomic.role(atomicId);
        final Endpoint.Type type = (role != null && role.isSourceOrDrain()) ? role : Endpoint.Type.JUNCTION;
        final Endpoint ep = new Endpoint(canonicalId, type, pos.clone());
        // Restore the carried seed/anchor so a subsequent viewAtomic() reads the same ownFlow/anchorFlow
        // (keeps the seam round trip idempotent for flow, mirroring the SOURCE/DRAIN id preservation).
        if (type == Endpoint.Type.SOURCE) ep.boundaryFlow = atomic.ownFlow(atomicId);
        else if (type == Endpoint.Type.DRAIN) ep.boundaryFlow = atomic.anchorFlow(atomicId);
        nodes.put(canonicalId, ep);
        return ep;
    }

    // ---------------------------------------------------------------------------------------------
    // Collisions (stream capture) — a from-scratch orient-and-prune over the atomic view
    // ---------------------------------------------------------------------------------------------

    /** Stream capture: re-orients the network so a drifted-together crossing merges into one channel and
     *  prunes what no longer reaches a drain. Orientation is an O(V+E) reverse BFS from every drain: the
     *  shortest hop-count path decides which continuation survives a merge. */
    public void manageCollisions(int step, AtomicView atomic) {

        // step 1: undirected crossing edges, added to the adjacency in both directions, then planarized.
        if (DEBUG_STEPS) {
            Debug.river.seeNetwork(atomic, 514, "step_" + step, "baseAtomicView");
        }
        List<int[]> crossings = detectCrossings(atomic);
        if (DEBUG_CROSSING_WINNER) LOG.info("crossings at step {} : {}", step, crossings);
        for (int[] crossingEdge : crossings) {
            atomic.addDirectedEdge(crossingEdge[0], crossingEdge[1]);
            atomic.addDirectedEdge(crossingEdge[1], crossingEdge[0]);
        }

        atomic.resolveCrossingEdges();

        // step 2: layered multi-source reverse BFS from every DRAIN — reachability and orientation
        // together (see reverseBfsCapture for the invariants this establishes by construction).
        final ReachTree reach = reverseBfsCapture(atomic);

        // step 3: record each unreached dangling sub-path as an abandoned river (history only).
        if (saveHistory) recordAbandoned(atomic, reach.alive(), step);

        // step 4: build the oriented compact view, derive flow, fold back in place.
        final AtomicView oriented = buildOriented(atomic, reach.alive(), reach.outgoing());
        if (DEBUG_STEPS) {
            Debug.river.seeNetwork(oriented, 514, "step_" + step, "orientedAtomicView");
        }
        update(oriented);
    }

    /** {@link #reverseBfsCapture}'s result: which atomic nodes survive, and each survivor's downstream successor. */
    private record ReachTree(boolean[] alive, int[] outgoing) {}

    /** Layered multi-source reverse BFS from every DRAIN over {@code adjReversed} (the reversal — no
     *  adjacency is ever physically flipped), producing a shortest-hop capture tree: {@code parent[]} is
     *  {@code outgoing[]} by construction. See {@code README.md} "Stream capture" for the invariants this establishes. */
    private static ReachTree reverseBfsCapture(AtomicView atomic) {
        final int n = atomic.size();
        final List<List<Integer>> adjReversed = new ObjectArrayList<>(n);
        for (int v = 0; v < n; v++) adjReversed.add(new ObjectArrayList<>());
        for (int u = 0; u < n; u++)
            for (int v : atomic.adjacency.get(u)) adjReversed.get(v).add(u);

        final int[] dist = new int[n];
        Arrays.fill(dist, NONE);
        final int[] parent = new int[n];
        Arrays.fill(parent, NONE);

        List<Integer> layer = new ObjectArrayList<>();
        for (int id = 0; id < n; id++) if (atomic.role(id) == Endpoint.Type.DRAIN) layer.add(id);
        for (int id : layer) dist[id] = 0;

        // Each node's distance is set at discovery (never re-set once found), so the result is
        // independent of dequeue order and the whole pass stays O(V+E).
        int d = 0;
        while (!layer.isEmpty()) {
            final List<Integer> next = new ObjectArrayList<>();
            for (int p : layer) {
                if (atomic.role(p) == Endpoint.Type.SOURCE) continue; // absorbing: never expanded
                for (int u : adjReversed.get(p)) {
                    if (dist[u] != NONE) continue; // already settled in an earlier layer
                    dist[u] = d + 1;
                    next.add(u);
                }
            }
            Collections.sort(next); // ascending atomic id — deterministic, independent of dequeue order
            for (int u : next) {
                parent[u] = pickStraightestParent(atomic, u, dist, d, parent);
            }
            layer = next;
            d++;
        }

        // Phase 2: mark forward from every reached SOURCE along parent[] (K1 guarantees a single path),
        // stopping at an already-marked node or NONE — the O(V) sweep the class javadoc requires.
        final boolean[] markedFromSource = new boolean[n];
        for (int id = 0; id < n; id++) {
            if (dist[id] == NONE || atomic.role(id) != Endpoint.Type.SOURCE) continue;
            int cur = id;
            while (cur != NONE && !markedFromSource[cur]) {
                markedFromSource[cur] = true;
                cur = parent[cur];
            }
        }

        final boolean[] alive = new boolean[n];
        for (int id = 0; id < n; id++) alive[id] = dist[id] != NONE && markedFromSource[id];
        return new ReachTree(alive, parent);
    }

    /** Straightest-continuation tie-break for {@link #reverseBfsCapture}, over {@code u}'s own forward
     *  adjacency filtered to same-layer, non-SOURCE candidates. |cross2D| of the hop into a candidate
     *  against its downstream tangent is sin(turn angle), so the least-deflected candidate wins, keeping
     *  a captured channel flowing the way it was already headed. A layer-0 DRAIN candidate has no tangent
     *  yet, so it falls back to nearest-by-distance; ties beyond that fall to the lowest atomic id. */
    private static int pickStraightestParent(AtomicView atomic, int u, int[] dist, int layerDist, int[] parent) {
        final double[] here = atomic.pos(u);
        int best = NONE;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int p : atomic.adjacency.get(u)) {
            if (dist[p] != layerDist || atomic.role(p) == Endpoint.Type.SOURCE) continue;
            final double[] there = atomic.pos(p);
            final double score;
            if (parent[p] == NONE) {
                score = VectorOps.distanceSquared(here, there);
            } else {
                final double[] hop = VectorOps.normalize(VectorOps.sub(there, here));
                final double[] tangent = VectorOps.normalize(VectorOps.sub(atomic.pos(parent[p]), there));
                score = Math.abs(VectorOps.cross2D(hop, tangent));
            }
            if (score < bestScore || (score == bestScore && p < best)) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** The pruned, oriented atomic view the collision pass promotes crossings into. Roles, canonical ids
     *  and flow inputs carry through so {@link #update} keeps SOURCE/DRAIN ids and  re-derives flow over the new topology. */
    private static AtomicView buildOriented(AtomicView atomic, boolean[] alive, int[] outgoing) {
        final int n = atomic.size();
        final AtomicView oriented = new AtomicView();
        final int[] newId = new int[n];
        Arrays.fill(newId, NONE);
        for (int old = 0; old < n; old++) {
            if (!alive[old]) continue;
            newId[old] = oriented.addNode(
                    atomic.pos(old),
                    atomic.role(old),
                    atomic.canonicalId(old),
                    atomic.ownFlow(old),
                    atomic.anchorFlow(old));
        }
        for (int old = 0; old < n; old++) {
            if (!alive[old] || atomic.role(old) == Endpoint.Type.DRAIN) continue;
            final int out = outgoing[old];
            if (out != NONE && newId[out] != NONE) oriented.addDirectedEdge(newId[old], newId[out]);
        }
        return oriented;
    }

    /** Mints each pruned sub-path the collision pass drops as {@link HydrologicalFeature#ABANDONED_RIVER}
     *  history, so a captured channel survives as a trace rather than vanishing. */
    private void recordAbandoned(AtomicView atomic, boolean[] alive, int step) {
        final int n = atomic.size();
        final boolean[] hasUnmarkedPred = new boolean[n];
        for (int u = 0; u < n; u++)
            if (!alive[u]) for (int v : atomic.adjacency.get(u)) if (!alive[v]) hasUnmarkedPred[v] = true;

        for (int head = 0; head < n; head++) {
            if (alive[head] || hasUnmarkedPred[head]) continue;
            final List<double[]> pts = new ObjectArrayList<>();
            double maxOwn = 0.0;
            int cur = head;
            while (cur != NONE && !alive[cur]) {
                pts.add(atomic.pos(cur));
                maxOwn = Math.max(maxOwn, atomic.ownFlow(cur));
                cur = atomic.adjacency.get(cur).isEmpty()
                        ? NONE
                        : atomic.adjacency.get(cur).get(0);
            }
            if (pts.size() < 10) continue;
            // One width for the whole path: ownFlow is the per-cell constant on interior atomic nodes,
            // so a per-point widthFromFlow would give a uniform hairline instead of the channel's size.
            final double width = HydrologyTuning.widthFromFlow(maxOwn);
            for (double[] p : pts) {
                lastStates.addLast(new AbandonedRiverPrimitive(p.clone(), (byte) step, width, 0, 0));
            }
        }
        evictOlderThan(step);
    }

    /** Finds one crossing edge per overlapping channel pair (closest overlapping points, tested via
     *  {@link ChannelGeometry#channelsOverlap}), feeding the collision/orientation pass below. */
    private List<int[]> detectCrossings(AtomicView atomic) {
        quadTree.clear();
        final List<Integer> channelIds = new ObjectArrayList<>(channels.keySet());
        Collections.sort(channelIds);
        for (int channelId : channelIds) insertChannelInQuadTree(channels.get(channelId));

        double maxHalf = 0.0;
        for (int channelId : channelIds) {
            final Channel c = channels.get(channelId);
            for (int i = 0; i < c.numPts(); i++)
                maxHalf = Math.max(maxHalf, ChannelGeometry.bedHalfWidth(c.widthAt(i)));
        }

        final List<int[]> edges = new ObjectArrayList<>();
        for (int channelAId : channelIds) {
            final Channel channelA = channels.get(channelAId);
            final int[] aAtomic = atomic.pointAtomicIds.get(channelAId);
            final Int2ObjectOpenHashMap<double[]> bestByPartner =
                    new Int2ObjectOpenHashMap<>(); // partnerId -> {atomA, atomB, dist}
            for (int posA = 0; posA < channelA.numPts(); posA++) {
                final double[] pointA = channelA.spline.points().get(posA);
                final double halfA = ChannelGeometry.bedHalfWidth(channelA.widthAt(posA));
                final List<Channel.ChannelPt> nearby = quadTree.getPointsInCircle(pointA, halfA + maxHalf);
                nearby.sort(null);
                for (Channel.ChannelPt np : nearby) {
                    final int channelBId = np.channelId();
                    if (channelBId <= channelAId) continue;
                    final Channel channelB = channels.get(channelBId);
                    final int posB = np.index();
                    final double distance = VectorOps.distance(pointA, np.toArray());
                    if (!ChannelGeometry.channelsOverlap(distance, channelA.widthAt(posA), channelB.widthAt(posB)))
                        continue;
                    final int atomA = aAtomic[posA];
                    final int atomB = atomic.pointAtomicIds.get(channelBId)[posB];
                    if (atomA == atomB) continue; // shared confluence node
                    if (isTreeAdjacent(atomic, atomA, atomB)) continue; // already directly connected
                    final double[] cur = bestByPartner.get(channelBId);
                    if (cur == null || distance < cur[2])
                        bestByPartner.put(channelBId, new double[] {atomA, atomB, distance});
                }
            }
            for (double[] c : bestByPartner.values()) edges.add(new int[] {(int) c[0], (int) c[1]});
        }
        return edges;
    }

    /** Whether {@code a}/{@code b} are directly connected by a directed tree crossingEdge (either direction). */
    private static boolean isTreeAdjacent(AtomicView atomic, int a, int b) {
        return atomic.adjacency.get(a).contains(b) || atomic.adjacency.get(b).contains(a);
    }

    // ---------------------------------------------------------------------------------------------
    // Step hooks used by the ChannelMigrator models
    // ---------------------------------------------------------------------------------------------

    /** Clears the working spatial index at the start of a step. */
    public void beginStep() {
        quadTree.clear();
    }

    public int getGridSize() {
        return gridSize;
    }

    // ---------------------------------------------------------------------------------------------
    // Cutoffs (per-channel self-intersection removal)
    // ---------------------------------------------------------------------------------------------

    private List<Channel.ChannelPt> getPtsCloseTo(Channel.ChannelPt pt) {
        // Retained-path read (manageCutoffs): derived width of the CURRENT point.
        return quadTree.getPointsInCircle(
                pt.toArray(), Math.sqrt(channels.get(pt.channelId()).widthAt(pt.index())));
    }

    public void manageCutoffs(Channel ch, int step) {
        if (ch.spline.checkNaN()) {
            throw new RuntimeException("cannot cut becuse spline is NaN");
        }
        quadTree.clear();
        insertChannelInQuadTree(ch);
        List<Integer> newPathIndexes = new ObjectArrayList<>();

        for (int id = 0; id < ch.numPts() - 1; id++) {
            if (!quadTree.containsPoint(ch.pt(id))) continue;
            newPathIndexes.add(id);
            List<Channel.ChannelPt> ptList = getPtsCloseTo(ch.pt(id));
            ptList.sort(null);
            for (Channel.ChannelPt cpt : ptList) {
                if (cpt.index() <= id + 1 || cpt.channelId() != ch.channelId) continue;
                cutRiverSection(id, cpt.index(), ch);
            }
        }
        newPathIndexes.add(ch.numPts() - 1);
        if (saveHistory) recordRemovedComplement(ch, newPathIndexes, step);
        ch.keepOnly(newPathIndexes);
    }

    private void insertChannelInQuadTree(Channel ch) {
        Channel.ChannelPt[] pts = ch.getChannelAsPts();
        for (Channel.ChannelPt pt : pts) {
            quadTree.insertPoint(pt);
        }
    }

    private void cutRiverSection(int from, int to, Channel ch) {
        for (int i = from; i < to; i++) quadTree.removePoint(ch.pt(i));
    }

    /** Records the points of {@code ch} NOT in {@code keptIndexes} as the oxbow the cutoff left behind. */
    private void recordRemovedComplement(Channel ch, List<Integer> keptIndexes, int step) {
        final boolean[] kept = new boolean[ch.numPts()];
        for (int idx : keptIndexes) if (idx >= 0 && idx < kept.length) kept[idx] = true;
        int removedCount = 0;
        for (int i = 0; i < ch.numPts(); i++) if (!kept[i]) removedCount++;
        if (removedCount < 2) return; // a single stray point is not a loop

        for (int i = 0; i < ch.numPts(); i++) {
            if (kept[i]) continue;
            // Elevation and influence stay 0 here: neither is knowable at the cut, and both are filled
            // in later through remapHistory.
            lastStates.addLast(
                    new OxbowLakePrimitive(ch.spline.points().get(i).clone(), (byte) step, ch.widthAt(i), 0, 0));
        }
        evictOlderThan(step);
    }

    // ---------------------------------------------------------------------------------------------
    // History
    // ---------------------------------------------------------------------------------------------

    /** Drops history older than the step window; runs after every mint, and only ever inspects the head
     *  because steps are non-decreasing across mints. */
    private void evictOlderThan(int step) {
        while (!lastStates.isEmpty() && step - lastStates.peekFirst().time() > maxSavedStates) {
            lastStates.removeFirst();
        }
    }

    /** Rewrites every stored history primitive, preserving deque order. Exists because elevation and
     *  influence are known only long after the cutoff that minted the primitive, and must be filled in
     *  before {@link #collectPrimitives} copies them into the index. */
    public void remapHistory(UnaryOperator<HydrologicalPrimitive> resolver) {
        final List<HydrologicalPrimitive> staged = new ObjectArrayList<>(lastStates);
        lastStates.clear();
        for (HydrologicalPrimitive p : staged) lastStates.addLast(resolver.apply(p));
    }

    // ---------------------------------------------------------------------------------------------
    // Conversion to the queryable, persistable primitive tree
    // ---------------------------------------------------------------------------------------------

    public List<HydrologicalPrimitive> collectPrimitives(
            double offsetX,
            double offsetZ,
            IntPredicate channelIdFilter,
            @Nullable ChannelTyper typer,
            InfluenceSampler surface) {
        final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>();
        final double[] offset = new double[] {offsetX, offsetZ};
        // Phase 1: resample every emitting channel. Types depend on neighbouring channels, so every
        // channel must hold its final geometry before any of them is classified.
        final List<Channel> emitting = new ObjectArrayList<>();
        for (Channel ch : channels.values()) {
            if (!channelIdFilter.test(ch.channelId)) continue;
            if (!ch.isResampleable()) continue; // degenerate geometry (too few points or NaN): skip
            // Spacing must be <= half the NARROWEST (intake) derived width, so consecutive primitives'
            // width/2 discs always overlap (gap-free membership test + girth rendering).
            final double dx = Math.max(ch.intakeWidth(), MIN_CONVERT_SPACING);
            try {
                ch.reSample(dx);
            } catch (RuntimeException runaway) {
                // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); add no primitives.
                continue;
            }
            emitting.add(ch);
        }
        final IntSet emittingIds = new IntOpenHashSet(emitting.size());
        for (final Channel ch : emitting) emittingIds.add(ch.channelId);

        // Phase 2: one classification pass over the whole graph.
        if (typer != null) typer.prepare(this);
        final Centreline centreline = new Centreline(this);

        for (Endpoint en : nodes.values()) {
            if (en.type == Endpoint.Type.SOURCE) {
                HydrologicalFeature.SOURCE.addPrimitives(offset, primitives, en, this, emittingIds);
            }

            // Degree three or more: two channels arriving is what makes a junction a confluence.
            if (en.type == Endpoint.Type.JUNCTION && en.incoming.size() >= 2) {
                HydrologicalFeature.CONFLUENCE.addPrimitives(offset, primitives, en, this, emittingIds);
            }

            // TODO: fix this, not all drains are deltas
            if (en.type == Endpoint.Type.DRAIN) HydrologicalFeature.DELTA.addPrimitives(offset, primitives, en);
        }

        for (Channel ch : emitting) {
            HydrologicalFeature.RIVER.addPrimitives(offset, primitives, typer, ch, centreline, surface);
        }

        // Shed features were minted in network frame at the step that cut them; addPrimitives shifts each
        // into the frame this collect emits in.
        for (HydrologicalPrimitive shed : lastStates) {
            shed.getType().addPrimitives(offset, primitives, shed);
        }
        return primitives;
    }

    // ---------------------------------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------------------------------

    public List<Channel> getChannels() {
        return new ObjectArrayList<>(channels.values());
    }

    public List<double[]> getChannelPts(int channelId) {
        return channels.get(channelId).spline.points();
    }

    public int getChannelCount() {
        return channels.size();
    }

    public Channel getChannel(int id) {
        return channels.get(id);
    }

    public Collection<Endpoint> getNodes() {
        return nodes.values();
    }

    public Endpoint getNode(int id) {
        return nodes.get(id);
    }
}
