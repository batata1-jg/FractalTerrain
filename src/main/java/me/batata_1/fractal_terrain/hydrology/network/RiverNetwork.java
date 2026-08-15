package me.batata_1.fractal_terrain.hydrology.network;

import static me.batata_1.fractal_terrain.config.DebugConfig.DEBUG_CROSSING_WINNER;
import static me.batata_1.fractal_terrain.config.DebugConfig.DEBUG_STEPS;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
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
    private final Map<Integer, Channel> channels = new HashMap<>();
    private final Map<Integer, Endpoint> nodes = new HashMap<>();
    private int nextChannelId = 0;
    private int nextNodeId = 0;

    // history (only populated when savePreviousStates is true)
    private final boolean savePreviousStates;
    private final int maxSavedStates;
    private final ArrayDeque<List<ArrayList<double[]>>> previousStates = new ArrayDeque<>();
    private final List<RemovedPath> removedPaths = new ArrayList<>();

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
    public record EdgeSpec(int startNodeIdx, int endNodeIdx, ArrayList<double[]> pts, double flow) {}

    /** A geometry removed from the active network, retained for {@link #collectPrimitives}. */
    private record RemovedPath(HydrologicalFeature type, ArrayList<double[]> pts, double width, int time) {}

    /** The production construction path: a graph that keeps no history, resampled at the default spacing. */
    public RiverNetwork(int gridSize, List<NodeSpec> nodeSpecs, List<EdgeSpec> edgeSpecs) {
        this(gridSize, nodeSpecs, edgeSpecs, false, 0, HydrologyTuning.DX);
    }

    public RiverNetwork(
            int gridSize,
            List<NodeSpec> nodeSpecs,
            List<EdgeSpec> edgeSpecs,
            boolean savePreviousStates,
            int maxSavedStates,
            double resampleDist) {

        this.gridSize = gridSize;
        this.savePreviousStates = savePreviousStates;
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
            final boolean boundary = ns.type() == Endpoint.Type.SOURCE || ns.type() == Endpoint.Type.DRAIN;
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
        final Channel tmp = new Channel(new ArrayList<>(pts), tmpFlow, 0);
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

    /** Sentinel for "this frame was not entered from anywhere" — identity-compared, never read as a vector. */
    private static final double[] NO_TANGENT = new double[2];

    /** The per-cell own-flow constant carried by interior/junction atomic nodes (see {@link #viewAtomic()}). */
    private static final double FLOW_PER_CELL = HydrologyTuning.FLOW_PER_CELL_LOCAL;

    /** Half of the mutation seam: exposes every spline point as a graph node so per-point algorithms
     *  (flow, planarization, capture) can work uniformly. {@link #update} folds the result back. */
    public AtomicView viewAtomic() {
        final AtomicView atomic = new AtomicView();
        final Map<Integer, Integer> endpointToAtomicId = new HashMap<>(); // canonical Endpoint id -> atomic id

        final List<Integer> channelIds = new ArrayList<>(channels.keySet());
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
                final Integer existing = endpointToAtomicId.get(endpointNodeId);
                if (existing != null) {
                    atomicIdOfPoint[i] = existing;
                    continue;
                }
                final Endpoint ep = nodes.get(endpointNodeId);
                final boolean boundary = ep.type == Endpoint.Type.SOURCE || ep.type == Endpoint.Type.DRAIN;
                // ownFlow: SOURCE carries its captured seed (ep.sourceFlow); DRAIN/JUNCTION carry the
                // per-cell constant. anchorFlow: DRAIN carries its anchor (ep.sourceFlow), else unused.
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
        final TreeSet<Integer> structural = new TreeSet<>();
        for (int id = 0; id < n; id++) {
            final Endpoint.Type role = atomic.role(id);
            if (role == Endpoint.Type.SOURCE || role == Endpoint.Type.DRAIN || indegree[id] >= 2) {
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
            if (role == Endpoint.Type.SOURCE || role == Endpoint.Type.DRAIN) {
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
            final List<Integer> chain = new ArrayList<>();
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
        final ArrayList<double[]> points = new ArrayList<>(chain.size());
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
        final Endpoint.Type type =
                (role == Endpoint.Type.SOURCE || role == Endpoint.Type.DRAIN) ? role : Endpoint.Type.JUNCTION;
        final Endpoint ep = new Endpoint(canonicalId, type, pos.clone());
        // Restore the carried seed/anchor so a subsequent viewAtomic() reads the same ownFlow/anchorFlow
        // (keeps the seam round trip idempotent for flow, mirroring the SOURCE/DRAIN id preservation).
        if (type == Endpoint.Type.SOURCE) ep.sourceFlow = atomic.ownFlow(atomicId);
        else if (type == Endpoint.Type.DRAIN) ep.sourceFlow = atomic.anchorFlow(atomicId);
        nodes.put(canonicalId, ep);
        return ep;
    }

    // ---------------------------------------------------------------------------------------------
    // Collisions (stream capture) — a from-scratch orient-and-prune over the atomic view
    // ---------------------------------------------------------------------------------------------

    /** Stream capture: where channels have drifted into each other, re-orients the network so one
     *  captures the other and prunes what no longer reaches a drain. Rebuilds topology from scratch
     *  rather than patching it, which is what keeps the result acyclic and single-outflow. */
    public void manageCollisions(int step, AtomicView atomic) {

        // step 1: undirected crossing edges + the pinned per-node adjacency (tree successor + partners).
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
        final int n = atomic.size();

        // step 2: deterministic two-mark DFS.
        final int[] visited = new int[n];
        Arrays.fill(visited, -1);
        final boolean[] foundDrain = new boolean[n];
        Arrays.fill(foundDrain, false);
        final boolean[] streamMarked = new boolean[n];
        Arrays.fill(streamMarked, false);
        final int[] outgoing = new int[n];
        Arrays.fill(outgoing, NONE);
        final ArrayDeque<Integer> dfsStack = new ArrayDeque<>();
        for (int sourceId : sortedSourceIds(atomic)) {
            if (visited[sourceId] == -1 || foundDrain[visited[sourceId]]) {
                dfsVisit(atomic, sourceId, sourceId, dfsStack, visited, foundDrain, streamMarked, outgoing);
            }
            dfsStack.clear();
        }

        // alive = streamMarked, plus any node referenced as a promoted successor (reached drains).
        final boolean[] alive = streamMarked.clone();
        for (int u = 0; u < n; u++) if (streamMarked[u] && outgoing[u] != NONE) alive[outgoing[u]] = true;

        // step 4: record each unmarked dangling sub-path as an abandoned river (history only).
        if (savePreviousStates) recordAbandoned(atomic, alive, step);

        // step 3 + 5: build the oriented compact view, derive flow, fold back in place.
        final AtomicView oriented = buildOriented(atomic, alive, outgoing);
        if (DEBUG_STEPS) {
            Debug.river.seeNetwork(oriented, 514, "step_" + step, "orientedAtomicView");
        }
        update(oriented);
    }

    /** SOURCE atomic ids in ascending order (the mandatory DFS start order — trunk selection depends on it). */
    private static List<Integer> sortedSourceIds(AtomicView atomic) {
        final List<Integer> sources = new ArrayList<>();
        for (int id = 0; id < atomic.size(); id++) if (atomic.role(id) == Endpoint.Type.SOURCE) sources.add(id);
        return sources; // ids are appended in ascending order already
    }

    /** Iterative capture DFS from {@code root}; true means this branch reached a terminus and was promoted.
     *  {@code stack} alone carries the frames: a child is stamped {@code visited[..] == sourceId} before being
     *  pushed, so a resumed frame rescans and skips it — no per-frame cursor needed, and recursion depth can no
     *  longer overflow the JVM stack. */
    private boolean dfsVisit(
            AtomicView atomic,
            int root,
            int sourceId,
            Deque<Integer> stack,
            int[] visited,
            boolean[] foundDrain,
            boolean[] streamMarked,
            int[] outgoing) {
        if (streamMarked[root] || atomic.role(root) == Endpoint.Type.DRAIN) return true; // already a terminus
        if (visited[root] == sourceId) return false; // on-stack ancestor or exhausted branch — NOT promoted, NOT marked
        if (visited[root] != -1 && !foundDrain[visited[root]]) return false;

        visited[root] = sourceId;
        stack.push(root);
        // The hop that entered each stacked node, so a frame can pick the straightest continuation of it.
        final Deque<double[]> tangents = new ArrayDeque<>();
        tangents.push(NO_TANGENT); // the root was not entered from anywhere

        while (!stack.isEmpty()) {
            final int node = stack.peek();

            // pinned per-node adjacency order: directed tree-successor first (if present), then crossing
            // partners ascending by atomic id. Both scans below walk it, so ties stay deterministic.
            final List<Integer> neighbors = atomic.adjacency.get(node);
            int descend = NONE;

            // Terminus first: any DRAIN or already-streamMarked neighbor outranks descending, so a terminus
            // sitting later in the adjacency order can never be deferred behind an earlier live branch.
            for (int next : neighbors) {
                if (streamMarked[next] || atomic.role(next) == Endpoint.Type.DRAIN) {
                    // Promotion happens ONLY here: reaching a DRAIN or an already-streamMarked node. Every node
                    // on `stack` is not-yet-streamMarked (the entry guard never pushes a streamMarked node), so
                    // the whole stack is the promoted suffix.
                    promoteSuffix(stack, next, streamMarked, outgoing);
                    foundDrain[sourceId] = true;
                    stack.clear(); // recursion unwound every frame on its way out; clearing matches that
                    return true;
                }
            }

            // Otherwise descend into the straightest continuation: |cross| of the candidate tangent against the
            // hop that entered `node` is sin(turn angle), so the least-deflected partner wins and captured
            // channels keep flowing the way they were already headed. The root has no entering hop, so it falls
            // back to the nearest neighbor.
            final double[] here = atomic.pos(node);
            final double[] prevTangent = tangents.peek();
            double[] descendTangent = null;
            double best = Double.POSITIVE_INFINITY;
            for (int next : neighbors) {
                // the callee's own entry guards, inlined: reaching here, only the visited/foundDrain one can fire.
                // `visited[next] == sourceId` also covers children this frame already descended into and exhausted.
                if (visited[next] == sourceId || (visited[next] != -1 && !foundDrain[visited[next]])) continue;
                final double[] there = atomic.pos(next);
                final double[] tangent = VectorOps.normalize(VectorOps.sub(there, here));
                final double score = prevTangent == NO_TANGENT
                        ? VectorOps.distanceSquared(here, there)
                        : Math.abs(VectorOps.cross2D(tangent, prevTangent));
                if (score < best) { // strict, so equally aligned partners fall back to adjacency order
                    best = score;
                    descend = next;
                    descendTangent = tangent;
                }
            }

            if (descend == NONE) {
                stack.pop(); // exhausted — node stays merely visited, unpromoted; pruned in step 4
                tangents.pop();
            } else {
                visited[descend] = sourceId;
                stack.push(descend);
                tangents.push(descendTangent);
            }
        }
        return false;
    }

    /** Promotes {@code stack} into the oriented tree toward {@code terminus} — the sole place outgoing
     *  edges are assigned, preserving the single-outflow invariant established during DFS orientation. */
    private static void promoteSuffix(Deque<Integer> stack, int terminus, boolean[] streamMarked, int[] outgoing) {
        int next = terminus;
        streamMarked[next] = true; // ensures terminus is streamMarked
        for (int node : stack) { // ArrayDeque iterates head (top of stack) first
            outgoing[node] = next;
            streamMarked[node] = true;
            next = node;
        }
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

    /** Records each pruned sub-path the collision pass drops, staged for {@link #collectPrimitives} to emit
     *  as an {@link HydrologicalFeature#ABANDONED_RIVER} entry. */
    private void recordAbandoned(AtomicView atomic, boolean[] alive, int step) {
        final int n = atomic.size();
        final boolean[] hasUnmarkedPred = new boolean[n];
        for (int u = 0; u < n; u++)
            if (!alive[u]) for (int v : atomic.adjacency.get(u)) if (!alive[v]) hasUnmarkedPred[v] = true;

        for (int head = 0; head < n; head++) {
            if (alive[head] || hasUnmarkedPred[head]) continue;
            final ArrayList<double[]> pts = new ArrayList<>();
            double maxOwn = 0.0;
            int cur = head;
            while (cur != NONE && !alive[cur]) {
                pts.add(atomic.pos(cur));
                maxOwn = Math.max(maxOwn, atomic.ownFlow(cur));
                cur = atomic.adjacency.get(cur).isEmpty()
                        ? NONE
                        : atomic.adjacency.get(cur).get(0);
            }
            if (pts.size() >= 2)
                removedPaths.add(new RemovedPath(
                        HydrologicalFeature.ABANDONED_RIVER, pts, HydrologyTuning.widthFromFlow(maxOwn), step));
        }
    }

    /** Finds one crossing edge per overlapping channel pair (closest overlapping points, tested via
     *  {@link ChannelGeometry#channelsOverlap}), feeding the collision/orientation pass below. */
    private List<int[]> detectCrossings(AtomicView atomic) {
        quadTree.clear();
        final List<Integer> channelIds = new ArrayList<>(channels.keySet());
        Collections.sort(channelIds);
        for (int channelId : channelIds) insertChannelInQuadTree(channels.get(channelId));

        double maxHalf = 0.0;
        for (int channelId : channelIds) {
            final Channel c = channels.get(channelId);
            for (int i = 0; i < c.numPts(); i++)
                maxHalf = Math.max(maxHalf, ChannelGeometry.bedHalfWidth(c.widthAt(i)));
        }

        final List<int[]> edges = new ArrayList<>();
        for (int channelAId : channelIds) {
            final Channel channelA = channels.get(channelAId);
            final int[] aAtomic = atomic.pointAtomicIds.get(channelAId);
            final Map<Integer, double[]> bestByPartner = new HashMap<>(); // partnerId -> {atomA, atomB, dist}
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
        ArrayList<Integer> newPathIndexes = new ArrayList<>();

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
        if (savePreviousStates) recordRemovedComplement(ch, newPathIndexes, HydrologicalFeature.OXBOW_LAKE, step);
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

    /** Record the points of {@code ch} NOT in {@code keptIndexes} as a removed feature (oxbow loop). */
    private void recordRemovedComplement(
            Channel ch, ArrayList<Integer> keptIndexes, HydrologicalFeature type, int step) {
        final boolean[] kept = new boolean[ch.numPts()];
        for (int idx : keptIndexes) if (idx >= 0 && idx < kept.length) kept[idx] = true;
        final ArrayList<double[]> removed = new ArrayList<>();
        int firstRemovedIndex = -1;
        for (int i = 0; i < ch.numPts(); i++)
            if (!kept[i]) {
                if (firstRemovedIndex == -1) firstRemovedIndex = i;
                removed.add(ch.spline.points().get(i).clone());
            }
        // Retained-path read: derived width of the FIRST removed spline point (serves OXBOW_LAKE primitives).
        if (removed.size() >= 2) removedPaths.add(new RemovedPath(type, removed, ch.widthAt(firstRemovedIndex), step));
    }

    // ---------------------------------------------------------------------------------------------
    // History
    // ---------------------------------------------------------------------------------------------

    /** Snapshot the current channel geometry (bounded to {@code maxSavedStates}); no-op if disabled. */
    public void recordState(int step) {
        if (!savePreviousStates) return;
        final List<ArrayList<double[]>> snapshot = new ArrayList<>(channels.size());
        for (Channel ch : channels.values()) {
            final ArrayList<double[]> copy = new ArrayList<>(ch.numPts());
            for (double[] p : ch.spline.points()) copy.add(p.clone());
            snapshot.add(copy);
        }
        previousStates.addLast(snapshot);
        while (previousStates.size() > maxSavedStates) previousStates.removeFirst();
    }

    // ---------------------------------------------------------------------------------------------
    // Conversion to the queryable, persistable primitive tree
    // ---------------------------------------------------------------------------------------------

    /** {@link #collectPrimitives(double, double, IntPredicate, ChannelTyper)} untyped: every primitive's {@link
     *  RiverPrimitive#rosgenType() rosgenType} is null, which callers coalesce to {@link RosgenType#A}. */
    public List<HydrologicalPrimitive> collectPrimitives(double offsetX, double offsetZ) {
        return collectPrimitives(offsetX, offsetZ, channelId -> true, null);
    }

    /** {@link #collectPrimitives(double, double, IntPredicate, ChannelTyper)} over every channel. */
    public List<HydrologicalPrimitive> collectPrimitives(double offsetX, double offsetZ, @Nullable ChannelTyper typer) {
        return collectPrimitives(offsetX, offsetZ, channelId -> true, typer);
    }

    public List<HydrologicalPrimitive> collectPrimitives(
            double offsetX, double offsetZ, IntPredicate channelIdFilter, @Nullable ChannelTyper typer) {
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        final double[] offset = new double[] {offsetX, offsetZ};
        // Phase 1: resample every emitting channel. Types depend on neighbouring channels, so every
        // channel must hold its final geometry before any of them is classified.
        final List<Channel> emitting = new ArrayList<>();
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

        // Phase 2: one classification pass over the whole graph.
        if (typer != null) typer.prepare(this);

        for (Endpoint en : nodes.values()) {
            if (en.type == Endpoint.Type.SOURCE) HydrologicalFeature.SOURCE.addPrimitives(offset, primitives, en);
            if (en.type == Endpoint.Type.JUNCTION)
                HydrologicalFeature.CONFLUENCE.addPrimitives(offset, primitives, en, this, typer);
            // TODO: fix this, not all drains are deltas
            if (en.type == Endpoint.Type.DRAIN) HydrologicalFeature.DELTA.addPrimitives(offset, primitives, en);
        }

        for (Channel ch : emitting) {
            HydrologicalFeature.RIVER.addPrimitives(offset, primitives, typer, ch);
        }

        for (RemovedPath rp : removedPaths) {
            HydrologicalFeature.ABANDONED_RIVER.addPrimitives(offset, primitives, rp);
        }
        return primitives;
    }

    // ---------------------------------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------------------------------

    public List<Channel> getChannels() {
        return new ArrayList<>(channels.values());
    }

    public ArrayList<double[]> getChannelPts(int channelId) {
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
