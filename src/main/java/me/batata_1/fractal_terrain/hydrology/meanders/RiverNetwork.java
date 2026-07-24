package me.batata_1.fractal_terrain.hydrology.meanders;

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
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.HydrologicalFeature;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import org.slf4j.Logger;

/**
 * The river-network graph and all topology/geometry it owns: a directed dendritic in-tree whose edges
 * are {@link Channel}s (keyed by {@code channelId}) and whose vertices are typed {@link Endpoint}s sitting
 * on channel endpoints (single-outflow invariant — see {@link Endpoint}).
 *
 * <p>All network mutation is collapsed onto a single validated seam: an explicit conversion between the
 * <b>canonical (channel) view</b> (the {@code channels}/{@code nodes} maps) and an <b>atomic (node) view</b>
 * ({@link AtomicView} — a {@code List<List<Integer>>} adjacency where every interior spline point is a
 * first-class node). {@link #viewAtomic()} builds the atomic view; {@link #accumulateAndCorrectFlow}
 * derives per-point flow over it; {@link #update} folds it back into {@code this} in place. Collision
 * handling ({@link #manageCollisions}) is a from-scratch orient-and-prune over the atomic view (a
 * deterministic two-mark DFS), and {@code Channel} stores <b>flow</b> (additive at confluences), with width
 * derived via {@link HydrologyTuning#widthFromFlow}.
 *
 * <p>When {@code savePreviousStates} is enabled it additionally records, per step, the paths removed by
 * collisions (as {@link HydrologicalFeature#ABANDONED_RIVER}) and by cutoffs (as
 * {@link HydrologicalFeature#OXBOW_LAKE}), plus bounded network snapshots. The relaxation phase runs
 * with recording disabled, so it produces no history.
 *
 * <p>{@link #collectUnits} packages the current network (plus recorded removed features) into a list of
 * {@link HydrologicalUnit}s, resampling every feature at {@code dx = max(width/2, MIN_CONVERT_SPACING)}
 * so wider features carry proportionally fewer points; the caller freezes the list into its spatial
 * index of choice.
 */
public final class RiverNetwork {

    private static final double INF = 1e9;
    /** Floor on the resample spacing used when converting features to {@link HydrologicalUnit}s. */
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

    /**
     * A vertex of the supplied initial network — an atomic node spec (position + role). Consumed only as
     * inert input to {@link #buildFromSpecs}, which folds these plus the {@link EdgeSpec}s into an
     * {@link AtomicView} and calls {@link #update}.
     */
    public record NodeSpec(double x, double z, Endpoint.Type type) {}

    /**
     * A directed edge of the supplied initial network — an atomic edge spec; {@code pts} include the two
     * endpoints. {@code flow} is the (per-cell) raw flow-accumulation value for the edge: a SOURCE start
     * carries it as its seed {@code ownFlow}, a DRAIN end as its {@code anchorFlow}; interior/junction nodes
     * carry the per-cell constant, and real per-point flow is derived by {@link #accumulateAndCorrectFlow}.
     * Width is derived downstream via {@link HydrologyTuning#widthFromFlow}.
     */
    public record EdgeSpec(int startNodeIdx, int endNodeIdx, ArrayList<double[]> pts, double flow) {}

    /** A geometry removed from the active network, retained for {@link #collectUnits}. */
    private record RemovedPath(HydrologicalFeature type, ArrayList<double[]> pts, double width, int time) {}

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

    /**
     * Fold a batch of {@link NodeSpec}/{@link EdgeSpec} entries into {@code this} through the seam: build an
     * {@link AtomicView} directly (interior edge points become fresh atomic nodes; the two endpoints collapse
     * on their shared node-spec index so a confluence is one atomic node), seed each boundary node's flow
     * input (SOURCE seed / DRAIN anchor), derive per-point flow with {@link #accumulateAndCorrectFlow}, then
     * {@link #update} emits {@link Channel}s/{@link Endpoint}s into {@code this}. SOURCE/DRAIN canonical ids
     * are pinned to their node-spec index (so a boundary-elevation map keyed on the spec index stays valid —
     * the historic {@code specIndex == nodeId} contract); JUNCTION-equivalents get fresh ids.
     */
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
            final double anchor = (ns.type() == Endpoint.Type.DRAIN) ? drainAnchor[i] : 0.0;
            specNodeAtomicId[i] =
                    atomic.addNode(new double[] {ns.x(), ns.z()}, ns.type(), boundary ? i : NONE, ownFlow, anchor);
        }

        for (EdgeSpec es : edgeSpecs) {
            final List<double[]> pts = resamplePts(es.pts(), es.flow(), resampleDist);
            final int last = pts.size() - 1;
            int prev = specNodeAtomicId[es.startNodeIdx()];
            for (int i = 1; i < last; i++) {
                final int interior = atomic.addNode(pts.get(i), null, NONE, FLOW_PER_CELL, 0.0);
                atomic.adjacency.get(prev).add(interior);
                prev = interior;
            }
            atomic.adjacency.get(prev).add(specNodeAtomicId[es.endNodeIdx()]);
        }

        accumulateAndCorrectFlow(atomic);
        update(atomic);
    }

    /** Resample an edge's raw endpoint-inclusive polyline at {@code resampleDist} (endpoints preserved). */
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

    /**
     * Add a standalone traced-local channel (already resampled) as a fresh SOURCE -> ({@code endType})
     * edge, without resolving any confluence — the atomic collision pass ({@link #manageCollisions}) is what
     * attaches it (via an undirected crossing edge) or demotes it. {@code endType} is DRAIN for a coast
     * outlet or JUNCTION for a dangling end awaiting a crossing attach. The dangling JUNCTION end has zero
     * outgoing edges until the collision pass reorients it — a transient state {@link #update} would reject,
     * so no {@link #update}/K1 check may run between this and {@link #manageCollisions}.
     */
    public void addLocalChannel(Channel channel, Endpoint.Type endType) {
        final int startId = nextNodeId++;
        final int endId = nextNodeId++;
        final double[] first = channel.spline.points().getFirst();
        final double[] last = channel.spline.points().getLast();
        final Endpoint start = new Endpoint(startId, Endpoint.Type.SOURCE, first.clone());
        final Endpoint end = new Endpoint(endId, endType, last.clone());
        start.sourceFlow = channel.flow[0]; // SOURCE seed = traced headwater flow
        if (endType == Endpoint.Type.DRAIN) end.sourceFlow = channel.flow[channel.flow.length - 1]; // coast anchor
        nodes.put(startId, start);
        nodes.put(endId, end);
        final int id = nextChannelId++;
        // rebuild the channel under a fresh id (Channel.channelId is final) preserving points + flow
        final Channel wired = new Channel(new ArrayList<>(channel.spline.points()), channel.flow, id);
        wired.startNodeId = startId;
        wired.endNodeId = endId;
        channels.put(id, wired);
        start.outgoing = id;
        end.incoming.add(id);
    }

    // ---------------------------------------------------------------------------------------------
    // The two views + the seam
    //
    // The canonical (channel) view is the field storage above (channels/nodes maps + id counters).
    // The atomic (node) view ({@link AtomicView}) is a List<List<Integer>> adjacency over per-node
    // data where every interior spline point is a first-class node. {@link #viewAtomic()} converts
    // canonical -> atomic; {@link #update} folds an atomic view back into `this` IN PLACE.
    // ---------------------------------------------------------------------------------------------

    /** Sentinel for "no canonical id" (interior/JUNCTION-equivalent atomic nodes) and "unset edge". */
    private static final int NONE = -1;

    /** The per-cell own-flow constant carried by interior/junction atomic nodes (see {@link #viewAtomic()}). */
    private static final double FLOW_PER_CELL = HydrologyTuning.FLOW_PER_CELL_LOCAL;

    /**
     * Atomic (node) view of the network: parallel per-node data plus a directed adjacency where every
     * interior spline point is a first-class node. Built by {@link #viewAtomic()} / {@link #buildFromSpecs},
     * read by {@link #update}. Carries per-node position/role/canonicalId plus the flow inputs
     * {@code ownFlow} (per-cell constant / SOURCE seed) and {@code anchorFlow} (DRAIN-only ceiling+target);
     * the DERIVED per-node {@code flow} is populated by {@link #accumulateAndCorrectFlow} before
     * {@link #update} reads it. {@link #pointAtomicIds} (populated only by {@link #viewAtomic()}) maps each
     * canonical channel's spline points to their atomic ids, so crossing detection can re-stamp contacts.
     */
    public static final class AtomicView {
        private final List<double[]> position = new ArrayList<>();
        /** SOURCE / DRAIN / JUNCTION, or {@code null} for an interior spline point. */
        private final List<Endpoint.Type> role = new ArrayList<>();
        /** Valid only where {@link #role} is SOURCE or DRAIN — the canonical {@link Endpoint} id to preserve. */
        private final List<Integer> canonicalId = new ArrayList<>();
        /** CARRIED input: the per-cell constant {@link #FLOW_PER_CELL} (interior/junction) or the SOURCE seed. */
        private final List<Double> ownFlow = new ArrayList<>();
        /** CARRIED input, meaningful only where {@link #role} is DRAIN: the clamp ceiling + near-drain target. */
        private final List<Double> anchorFlow = new ArrayList<>();
        /** {@code adjacency.get(u)}: directed tree-successor edge(s) out of atomic node {@code u}. */
        final List<List<Integer>> adjacency = new ArrayList<>();
        /** canonical channelId -> atomic id per spline point (only populated by {@link #viewAtomic()}). */
        final Map<Integer, int[]> pointAtomicIds = new HashMap<>();
        /** DERIVED per-node flow; {@code null} until {@link #accumulateAndCorrectFlow} runs. */
        double[] flow;

        int size() {
            return position.size();
        }

        Endpoint.Type role(int id) {
            return role.get(id);
        }

        int canonicalId(int id) {
            return canonicalId.get(id);
        }

        double ownFlow(int id) {
            return ownFlow.get(id);
        }

        double anchorFlow(int id) {
            return anchorFlow.get(id);
        }

        /** The DERIVED per-node flow at {@code id}, falling back to {@code ownFlow} when not yet accumulated. */
        double flow(int id) {
            return (flow != null) ? flow[id] : ownFlow.get(id);
        }

        /** A fresh copy of the position of atomic node {@code id}. */
        double[] pos(int id) {
            return position.get(id).clone();
        }

        /** Append a new atomic node (position cloned) and return its atomic id. */
        int addNode(double[] pos, Endpoint.Type role, int canonicalId, double ownFlow, double anchorFlow) {
            final int id = position.size();
            position.add(pos.clone());
            this.role.add(role);
            this.canonicalId.add(canonicalId);
            this.ownFlow.add(ownFlow);
            this.anchorFlow.add(anchorFlow);
            adjacency.add(new ArrayList<>());
            return id;
        }
    }

    /**
     * Canonical -> atomic. Iterates channels in sorted-by-id order (so atomic-id assignment is reproducible
     * and HashMap iteration order never leaks in). Each channel's interior points become fresh atomic nodes;
     * the two endpoints collapse keyed on the canonical {@link Endpoint} node id (the first channel touching
     * a node adds it, later channels reuse it), so channels sharing a graph node share one atomic node.
     * SOURCE/DRAIN provenance (and their canonical id) is carried so {@link #update} can preserve their ids.
     * Velocity/acceleration are not carried — {@code createCatmullRom} re-infers them on rebuild.
     */
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
                    atomicIdOfPoint[i] = atomic.addNode(pts.get(i), null, NONE, FLOW_PER_CELL, 0.0);
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
                final double ownFlow = (ep.type == Endpoint.Type.SOURCE) ? ep.sourceFlow : FLOW_PER_CELL;
                final double anchorFlow = (ep.type == Endpoint.Type.DRAIN) ? ep.sourceFlow : 0.0;
                final int atomicId =
                        atomic.addNode(pts.get(i), ep.type, boundary ? endpointNodeId : NONE, ownFlow, anchorFlow);
                endpointToAtomicId.put(endpointNodeId, atomicId);
                atomicIdOfPoint[i] = atomicId;
            }

            for (int i = 0; i < last; i++) {
                atomic.adjacency.get(atomicIdOfPoint[i]).add(atomicIdOfPoint[i + 1]); // directed i -> i+1
            }
            atomic.pointAtomicIds.put(channelId, atomicIdOfPoint);
        }
        return atomic;
    }

    /**
     * Single-outflow check (invariant K1), modelled on {@link Endpoint#degree()} + {@link Endpoint.Type}:
     * SOURCE exactly 1 outgoing, DRAIN 0 outgoing, JUNCTION/interior exactly 1 outgoing. Counts only the
     * directed tree-successor slot ({@code adjacency.get(id).size()}). Throws (rather than {@code assert})
     * so the guard holds regardless of whether {@code -ea} is enabled — the plan pins this as a hard
     * precondition of {@link #update}.
     */
    void assertSingleOutflow(AtomicView atomic) {
        for (int id = 0; id < atomic.size(); id++) {
            final int outdeg = atomic.adjacency.get(id).size();
            final Endpoint.Type role = atomic.role(id);
            if (role == Endpoint.Type.DRAIN) {
                if (outdeg != 0) throw new IllegalStateException("DRAIN node " + id + " must have no outgoing edge");
            } else {
                // SOURCE / JUNCTION / interior all require exactly one outgoing edge
                if (outdeg != 1)
                    throw new IllegalStateException((role == null ? "interior" : role.toString()) + " node " + id
                            + " must have exactly one outgoing edge, had " + outdeg);
            }
        }
    }

    /**
     * Atomic -> canonical, IN PLACE. Mutates {@code this}: clears {@code channels}/{@code nodes}/
     * {@code quadTree}, resets the id counters (node counter set PAST every preserved id so a fresh
     * JUNCTION-equivalent id can never collide with a preserved SOURCE/DRAIN id), then re-emits maximal
     * directed chains between structural nodes (source / drain / in-degree &ge; 2 confluence) as
     * {@link Channel}s wired directly into {@code this}. The canonical id of every atomic node whose
     * provenance is SOURCE or DRAIN is preserved (boundary-elevation lookups key on these); every
     * JUNCTION-equivalent structural node gets a fresh id. {@code bedElevations} are intentionally not
     * preserved. Does NOT allocate a new {@code RiverNetwork} — returns {@code this} for chaining.
     */
    public RiverNetwork update(AtomicView atomic) {
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
                cur = onlyOutgoing(atomic, cur); // interior node: single outgoing edge (K1)
            }
            chain.add(cur); // cur: the next structural node (confluence or drain)
            emitChannel(atomic, chain, canonicalIdOf);
        }
        return this;
    }

    /** The single directed tree-successor of {@code id} (K1 guarantees exactly one for non-drain nodes). */
    private static int onlyOutgoing(AtomicView atomic, int id) {
        return atomic.adjacency.get(id).get(0);
    }

    /**
     * Builds a {@link Channel} from {@code chain} and wires it directly into {@code this}, reusing the
     * kept {@link #insertChannel} QuadTree helper. The start/end canonical ids come from
     * {@code canonicalIdOf} (preserved SOURCE/DRAIN, or fresh JUNCTION-equivalent). Applies the inlined
     * single-outflow (K1) guard. {@code bedElevations} are not preserved.
     */
    private void emitChannel(AtomicView atomic, List<Integer> chain, int[] canonicalIdOf) {
        final ArrayList<double[]> points = new ArrayList<>(chain.size());
        final double[] flow = new double[chain.size()];
        for (int i = 0; i < chain.size(); i++) {
            points.add(atomic.pos(chain.get(i)));
            flow[i] = atomic.flow(chain.get(i)); // DERIVED flow (populated by accumulateAndCorrectFlow)
        }

        final int startAtomic = chain.get(0);
        final int endAtomic = chain.get(chain.size() - 1);
        final Endpoint start = ensureNode(atomic, startAtomic, canonicalIdOf[startAtomic], points.getFirst());
        final Endpoint end = ensureNode(atomic, endAtomic, canonicalIdOf[endAtomic], points.getLast());

        // inlined K1 guard: the start endpoint must not already own an outgoing edge (single-outflow)
        if (start.outgoing != NONE) {
            throw new IllegalStateException("node " + start.id + " would have >1 outgoing edge");
        }

        final int id = nextChannelId++;
        final Channel ch = new Channel(points, flow, id);
        ch.startNodeId = start.id;
        ch.endNodeId = end.id;
        channels.put(id, ch);
        start.outgoing = id;
        end.incoming.add(id);
        insertChannel(ch); // kept QuadTree helper (also used by manageCutoffs)
        // bedElevations intentionally NOT preserved — the seam is bed-elevation-agnostic
    }

    /**
     * Fetches (or lazily creates) the {@link Endpoint} for a structural atomic node in {@code this}. A
     * SOURCE/DRAIN atomic node keeps its type; every other structural node becomes a JUNCTION. Endpoints
     * are shared across chains (a confluence is the end of one chain and the start of others).
     */
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
    // Flow accumulation + drain correction (populates the atomic view's DERIVED per-node flow)
    // ---------------------------------------------------------------------------------------------

    /**
     * Populate {@code atomic.flow[]} (the DERIVED per-node flow that drives width) from the carried inputs
     * {@code ownFlow[]} (per-cell constant / SOURCE seed) and {@code anchorFlow[]} (DRAIN-only). Reverse-
     * topological accumulation over the ATOMIC adjacency (never {@link Endpoint#incoming}, a HashSet), so
     * the confluence sum order is pinned by ascending atomic id and the {@code doubleToLongBits} goldens
     * stay bit-stable. Then per drain (sorted): clamp every basin node to the anchor ceiling, set the drain
     * to its anchor exactly, and lerp the last &le; {@link HydrologyTuning#DRAIN_FLOW_SMOOTH_MAX_NODES}
     * mainstem nodes up to the anchor when the jump exceeds {@link HydrologyTuning#DRAIN_FLOW_SMOOTH_STEP}
     * — NO basin-wide rescale. Call this before {@link #update} reads the derived flow.
     */
    public void accumulateAndCorrectFlow(AtomicView atomic) {
        final int n = atomic.size();

        // predecessors u of each edge u -> node, each list sorted ascending by atomic id so every
        // confluence sum order is pinned (Determinism).
        final List<List<Integer>> predecessors = new ArrayList<>(n);
        for (int v = 0; v < n; v++) predecessors.add(new ArrayList<>());
        for (int u = 0; u < n; u++)
            for (int v : atomic.adjacency.get(u)) predecessors.get(v).add(u);
        for (List<Integer> preds : predecessors) Collections.sort(preds);

        final int[] remainingIn = new int[n];
        for (int v = 0; v < n; v++) remainingIn[v] = predecessors.get(v).size();

        final double[] totalFlow = new double[n];
        final TreeSet<Integer> ready = new TreeSet<>(); // ascending atomic id — deterministic frontier
        for (int v = 0; v < n; v++) if (remainingIn[v] == 0) ready.add(v);

        while (!ready.isEmpty()) {
            final int node = ready.pollFirst();
            double sum = atomic.ownFlow(node);
            for (int tributary : predecessors.get(node)) sum += totalFlow[tributary];
            totalFlow[node] = sum;
            for (int next : atomic.adjacency.get(node)) if (--remainingIn[next] == 0) ready.add(next);
        }

        final double[] flow = totalFlow.clone();

        // Per drain (sorted): clamp the basin to the anchor ceiling, then lerp the last few mainstem
        // nodes up to the anchor. No basin-wide rescale (headwaters keep their small natural flow).
        for (int drain = 0; drain < n; drain++) {
            if (atomic.role(drain) != Endpoint.Type.DRAIN) continue;
            final double anchor = atomic.anchorFlow(drain);

            // 1. clamp every basin node to the anchor ceiling
            for (int node : basinOf(drain, predecessors)) flow[node] = Math.min(totalFlow[node], anchor);
            // 2. the drain reads its anchor exactly
            flow[drain] = anchor;

            // 3. smooth the drain jump along the mainstem, if it exceeds the step threshold
            int up = mainstemPredecessor(drain, predecessors, flow);
            if (up == NONE || anchor - flow[up] <= HydrologyTuning.DRAIN_FLOW_SMOOTH_STEP) continue;

            final List<Integer> chain = new ArrayList<>();
            int node = up;
            while (node != NONE
                    && chain.size() < HydrologyTuning.DRAIN_FLOW_SMOOTH_MAX_NODES
                    && anchor - flow[node] > HydrologyTuning.DRAIN_FLOW_SMOOTH_STEP) {
                chain.add(node);
                node = mainstemPredecessor(node, predecessors, flow);
            }

            // 4. ramp anchor (at the drain) down to the far-end node's natural flow across the chain,
            //    only raising (max) so a tributary already carrying more is never lowered.
            final int span = chain.size();
            final double farFlow = flow[chain.get(span - 1)];
            for (int k = 0; k < span; k++) { // k = 0 is the drain-adjacent node
                final double t = (double) (k + 1) / (span + 1); // 0 -> drain (anchor), 1 -> far end (farFlow)
                final double ramped = anchor + (farFlow - anchor) * t;
                final int cn = chain.get(k);
                flow[cn] = Math.max(flow[cn], ramped);
            }
        }

        atomic.flow = flow;
    }

    /** All atomic nodes upstream of {@code drain} (reverse-reachable via predecessors), including it. */
    private static List<Integer> basinOf(int drain, List<List<Integer>> predecessors) {
        final List<Integer> basin = new ArrayList<>();
        final ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(drain);
        while (!stack.isEmpty()) {
            final int node = stack.pop();
            basin.add(node);
            for (int up : predecessors.get(node)) stack.push(up);
        }
        return basin;
    }

    /** The highest-flow predecessor of {@code node} (tie-broken by ascending atomic id), or {@link #NONE}. */
    private static int mainstemPredecessor(int node, List<List<Integer>> predecessors, double[] flow) {
        int best = NONE;
        for (int up : predecessors.get(node)) { // predecessors already ascending by id
            if (best == NONE || flow[up] > flow[best]) best = up;
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------------
    // Collisions (stream capture) — a from-scratch orient-and-prune over the atomic view
    // ---------------------------------------------------------------------------------------------

    /**
     * Rebuild the network's topology from scratch over the atomic view:
     *
     * <ol>
     *   <li>Detect crossings between channels (bed-overlap on per-point {@link Channel#widthAt}); connect the
     *       crossing atomic-node pairs with undirected edges.</li>
     *   <li>Deterministic two-mark DFS from each source (sorted source id, pinned adjacency: directed
     *       tree-successor first, then undirected crossing partners ascending by atomic id). Promotion — of
     *       the not-yet-{@code streamMarked} stack suffix — happens only on reaching a DRAIN or an
     *       already-{@code streamMarked} node; each node's single outgoing edge is set exactly once, at the
     *       instant it becomes {@code streamMarked}. Acyclic + single-outflow by construction.</li>
     *   <li>Build the oriented view from the promoted edges (kept = promoted edges only).</li>
     *   <li>Prune unmarked nodes — each dangling unmarked sub-path becomes an {@link
     *       HydrologicalFeature#ABANDONED_RIVER} (recorded only when {@code savePreviousStates}).</li>
     *   <li>{@link #accumulateAndCorrectFlow} over the oriented view, then {@link #update} folds it back in
     *       place.</li>
     * </ol>
     */
    public void manageCollisions(int step) {
        final AtomicView atomic = viewAtomic();
        final int n = atomic.size();

        // step 1: undirected crossing edges + the pinned per-node adjacency (tree successor + partners).
        final int[] treeSuccessor = new int[n];
        for (int u = 0; u < n; u++)
            treeSuccessor[u] = atomic.adjacency.get(u).isEmpty()
                    ? NONE
                    : atomic.adjacency.get(u).get(0);
        final List<List<Integer>> crossingPartners = new ArrayList<>(n);
        for (int u = 0; u < n; u++) crossingPartners.add(new ArrayList<>());
        for (int[] edge : detectCrossings(atomic)) {
            crossingPartners.get(edge[0]).add(edge[1]);
            crossingPartners.get(edge[1]).add(edge[0]);
        }
        for (List<Integer> partners : crossingPartners) {
            Collections.sort(partners); // ascending atomic id — pinned adjacency order (Determinism)
        }

        // step 2: deterministic two-mark DFS.
        final boolean[] visited = new boolean[n];
        final boolean[] streamMarked = new boolean[n];
        final int[] outgoing = new int[n];
        Arrays.fill(outgoing, NONE);
        for (int sourceId : sortedSourceIds(atomic)) {
            if (!visited[sourceId]) {
                dfsVisit(
                        atomic,
                        treeSuccessor,
                        crossingPartners,
                        sourceId,
                        new ArrayDeque<>(),
                        visited,
                        streamMarked,
                        outgoing);
            }
        }

        // alive = streamMarked, plus any node referenced as a promoted successor (reached drains).
        final boolean[] alive = streamMarked.clone();
        for (int u = 0; u < n; u++) if (streamMarked[u] && outgoing[u] != NONE) alive[outgoing[u]] = true;

        // step 4: record each unmarked dangling sub-path as an abandoned river (history only).
        if (savePreviousStates) recordAbandoned(atomic, alive, step);

        // step 3 + 5: build the oriented compact view, derive flow, fold back in place.
        final AtomicView oriented = buildOriented(atomic, alive, outgoing);
        accumulateAndCorrectFlow(oriented);
        update(oriented);
    }

    /** SOURCE atomic ids in ascending order (the mandatory DFS start order — trunk selection depends on it). */
    private static List<Integer> sortedSourceIds(AtomicView atomic) {
        final List<Integer> sources = new ArrayList<>();
        for (int id = 0; id < atomic.size(); id++) if (atomic.role(id) == Endpoint.Type.SOURCE) sources.add(id);
        return sources; // ids are appended in ascending order already
    }

    /**
     * Returns true iff {@code node}'s branch reached a drain / {@code streamMarked} terminus (promoted, or
     * was already a terminus on entry). {@code stack} is the current path from the originating source.
     */
    private boolean dfsVisit(
            AtomicView atomic,
            int[] treeSuccessor,
            List<List<Integer>> crossingPartners,
            int node,
            Deque<Integer> stack,
            boolean[] visited,
            boolean[] streamMarked,
            int[] outgoing) {
        if (streamMarked[node] || atomic.role(node) == Endpoint.Type.DRAIN) return true; // already a terminus
        if (visited[node]) return false; // on-stack ancestor or exhausted branch — NOT promoted, NOT marked

        visited[node] = true;
        stack.push(node);

        // pinned per-node adjacency order: directed tree-successor first (if present), then crossing
        // partners ascending by atomic id.
        final List<Integer> neighbors = new ArrayList<>();
        if (treeSuccessor[node] != NONE) neighbors.add(treeSuccessor[node]);
        neighbors.addAll(crossingPartners.get(node));

        for (int next : neighbors) {
            if (streamMarked[next] || atomic.role(next) == Endpoint.Type.DRAIN) {
                // Promotion happens ONLY here: reaching a DRAIN or an already-streamMarked node. Every node
                // on `stack` is not-yet-streamMarked (the entry guard never pushes a streamMarked node), so
                // the whole stack is the promoted suffix.
                promoteSuffix(stack, next, streamMarked, outgoing);
                stack.pop();
                return true;
            }
            if (!visited[next]
                    && dfsVisit(
                            atomic, treeSuccessor, crossingPartners, next, stack, visited, streamMarked, outgoing)) {
                stack.pop(); // promotion already happened for this whole stack, deeper in the recursion
                return true;
            }
            // else: `next` is visited-but-unmarked (dead branch / ancestor) — try the next neighbor
        }

        stack.pop();
        return false; // exhausted — node stays merely visited, unpromoted; pruned in step 4
    }

    /**
     * Wires each node on {@code stack} (top-of-stack first) to its single outgoing edge toward
     * {@code terminus}, then streamMarks it. Each node's outgoing edge is set EXACTLY ONCE, at the instant it
     * transitions unmarked -> streamMarked; an already-streamMarked node is never on the stack.
     */
    private static void promoteSuffix(Deque<Integer> stack, int terminus, boolean[] streamMarked, int[] outgoing) {
        int next = terminus;
        for (int node : stack) { // ArrayDeque iterates head (top of stack) first
            outgoing[node] = next;
            streamMarked[node] = true;
            next = node;
        }
    }

    /**
     * Build the oriented, pruned atomic view: only {@code alive} nodes, re-indexed ascending, with each
     * kept node's single promoted outgoing edge. Drains and pruned nodes get an empty adjacency. Roles /
     * canonical ids / flow inputs are carried through, so {@link #update} preserves SOURCE/DRAIN ids and
     * {@link #accumulateAndCorrectFlow} re-derives flow over the oriented topology.
     */
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
            if (out != NONE && newId[out] != NONE)
                oriented.adjacency.get(newId[old]).add(newId[out]);
        }
        return oriented;
    }

    /**
     * Record each maximal unmarked (pruned) directed sub-path as an {@link HydrologicalFeature#ABANDONED_RIVER}
     * removed feature. Walks the ORIGINAL directed adjacency; a run head is an unmarked node with no unmarked
     * directed predecessor.
     */
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

    /**
     * Detect channel crossings: one undirected atomic-node-pair edge per overlapping channel pair (the
     * closest overlapping point pair between them), bed-overlap tested via {@link ChannelGeometry#channelsOverlap}
     * on per-point {@link Channel#widthAt}. Deterministic: channels iterated in sorted id order; the chosen
     * pair per partner is the globally closest (independent of query order); shared confluence nodes (same
     * atomic id) are skipped.
     */
    private List<int[]> detectCrossings(AtomicView atomic) {
        quadTree.clear();
        final List<Integer> channelIds = new ArrayList<>(channels.keySet());
        Collections.sort(channelIds);
        for (int channelId : channelIds) insertChannel(channels.get(channelId));

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

    /** Whether {@code a}/{@code b} are directly connected by a directed tree edge (either direction). */
    private static boolean isTreeAdjacent(AtomicView atomic, int a, int b) {
        return atomic.adjacency.get(a).contains(b) || atomic.adjacency.get(b).contains(a);
    }

    // ---------------------------------------------------------------------------------------------
    // Step hooks used by Meanders
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
        insertChannel(ch);
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

    private void insertChannel(Channel ch) {
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
        // Retained-path read: derived width of the FIRST removed spline point (serves OXBOW_LAKE units).
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
    // Conversion to the queryable, persistable unit tree
    // ---------------------------------------------------------------------------------------------

    /**
     * Collect the network's {@link HydrologicalUnit}s (active channels of type
     * {@link HydrologicalFeature#RIVER} plus recorded removed features — oxbow lakes / abandoned
     * rivers) in one pass over the unified graph — global and local channels alike, since local
     * segments are first-class graph members — as a mutable list the caller freezes into a single
     * spatial index. Each feature is first resampled at {@code dx = max(width/2, MIN_CONVERT_SPACING)};
     * emitted coordinates are the network coordinate minus {@code (offsetX, offsetZ)} (e.g. to drop a
     * halo pad). Per-point bed elevation is read directly from the already-assigned
     * {@link Channel#bedElevations} — this pass never invents or re-derives it from decoded terrain;
     * removed paths (oxbows/abandoned rivers) carry no {@code bedElevations}, so their elevation falls
     * back to sampling decoded terrain at each point.
     *
     * <p>Channels whose geometry is degenerate ({@code !isResampleable()}) or that overrun the spline
     * length cap during resampling are skipped silently, contributing no units.
     *
     * <p>{@code nextFeatureId} is a single-element mutable counter threaded by the caller so every unit
     * this one pass emits — global and local alike — shares one tile-unique
     * {@link HydrologicalUnit#id() id} space — every point of one feature gets the same id, and the
     * counter advances once per feature.
     */
    public List<HydrologicalUnit> collectUnits(int time, double offsetX, double offsetZ, int[] nextFeatureId) {
        return collectUnits(time, offsetX, offsetZ, nextFeatureId, channelId -> true);
    }

    /**
     * As {@link #collectUnits(int, double, double, int[])}, but a graph channel's RIVER units are only
     * emitted when {@code channelIdFilter} accepts its {@link Channel#channelId}; recorded removed
     * features (oxbow/abandoned) are always emitted regardless (the local drainage tracer never produces
     * removed paths, so this only ever filters live graph channels).
     *
     * <p><b>No caller passes a real filter today.</b> The only invocation is the unfiltered overload
     * above delegating with {@code channelId -> true}. This exists to let a caller carve or index one
     * subgraph (e.g. global-only channels) out of the unified network, but {@code LocalRiverProvider}
     * currently collects unfiltered for every purpose.
     */
    public List<HydrologicalUnit> collectUnits(
            int time, double offsetX, double offsetZ, int[] nextFeatureId, IntPredicate channelIdFilter) {
        final List<HydrologicalUnit> units = new ArrayList<>();
        for (Channel ch : channels.values()) {
            if (!channelIdFilter.test(ch.channelId)) continue;
            if (!ch.isResampleable()) continue; // degenerate geometry (too few points or NaN): skip
            // Spacing must be <= half the NARROWEST (intake) derived width, so consecutive units'
            // width/2 discs always overlap (gap-free membership test + girth rendering).
            final double dx = Math.max(ch.intakeWidth() / 2.0, MIN_CONVERT_SPACING);
            try {
                ch.reSample(dx);
            } catch (RuntimeException runaway) {
                // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); add no units.
                continue;
            }
            addFeatureUnits(
                    units,
                    ch.spline,
                    ch.bedElevations,
                    ch.flow, // per-point flow -> per-point derived width (natural taper)
                    0.0,
                    HydrologicalFeature.RIVER,
                    time,
                    offsetX,
                    offsetZ,
                    nextFeatureId);
        }
        for (RemovedPath rp : removedPaths) {
            final QuinticHermiteSpline spline = QuinticHermiteSpline.createCatmullRom(rp.pts());
            if (!spline.isResampleable()) continue; // degenerate geometry (too few points or NaN): skip
            final double dx = Math.max(rp.width() / 2.0, MIN_CONVERT_SPACING);
            final QuinticHermiteSpline resampled;
            try {
                resampled = spline.reSample(dx);
            } catch (RuntimeException runaway) {
                // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); add no units.
                continue;
            }
            // No bedElevations for oxbow/abandoned removed paths: fall back to decoded terrain. Removed
            // paths carry only a scalar width (no per-point flow), so pass a null flow + that fallback.
            addFeatureUnits(
                    units, resampled, null, null, rp.width(), rp.type(), rp.time(), offsetX, offsetZ, nextFeatureId);
        }
        return units;
    }

    private static void addFeatureUnits(
            List<HydrologicalUnit> out,
            QuinticHermiteSpline spline,
            double[] bedElevations,
            double[] flow,
            double fallbackWidth,
            HydrologicalFeature type,
            int time,
            double offsetX,
            double offsetZ,
            int[] nextFeatureId) {
        final List<double[]> pts = spline.points();
        final int n = pts.size();
        final int featureId = nextFeatureId[0]++;
        for (int i = 0; i < n; i++) {
            final double[] p = pts.get(i);
            // Per-point derived width (natural taper) when flow is present; else the scalar fallback.
            final double w = (flow != null) ? HydrologyTuning.widthFromFlow(flow[i]) : fallbackWidth;
            final double bed = (bedElevations != null) ? bedElevations[i] : 0;
            final double[] nrm = spline.normal(i);
            out.add(new HydrologicalUnit(
                    type,
                    // TODO: change this to the correct type
                    HydrologicalUnit.RosgenType.A,
                    new double[] {p[0] - offsetX, p[1] - offsetZ},
                    new double[] {nrm[0], nrm[1]},
                    w,
                    bed,
                    time,
                    featureId));
        }
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
