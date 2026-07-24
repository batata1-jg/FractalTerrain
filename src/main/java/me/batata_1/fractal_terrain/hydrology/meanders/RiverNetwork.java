package me.batata_1.fractal_terrain.hydrology.meanders;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * <p>This class is the container the {@link Meanders} simulation mutates: it holds the channels/nodes,
 * the working spatial index, and all the structural primitives — {@link #split}/{@link #merge}, leaf
 * pruning, self-intersection cutoffs ({@link #manageCutoffs}) and stream-capture collision resolution
 * ({@link #manageCollisions}). {@code Meanders} contributes only the migration model and step
 * orchestration.
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
    /** contiguous index gap (on the same channel pair) below which contacts are one crossing. */
    private static final int CLUSTER_GAP = 3;
    /** Floor on the resample spacing used when converting features to {@link HydrologicalUnit}s. */
    private static final double MIN_CONVERT_SPACING = 0.5;

    private static final Logger LOG = getLogger(RiverNetwork.class);

    private final int gridSize;
    private final QuadTree<Channel.ChannelPt> quadTree =
            new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});

    // graph storage: stable channel/node ids (sparse after split/merge/prune)
    private final Map<Integer, Channel> channels = new HashMap<>();
    private final Map<Integer, Endpoint> nodes = new HashMap<>();
    private int nextChannelId = 0;
    private int nextNodeId = 0;

    // history (only populated when savePreviousStates is true)
    private final boolean savePreviousStates;
    private final int maxSavedStates;
    private final ArrayDeque<List<ArrayList<double[]>>> previousStates = new ArrayDeque<>();
    private final List<RemovedPath> removedPaths = new ArrayList<>();

    /** A vertex of the supplied initial network. */
    public record NodeSpec(double x, double z, Endpoint.Type type) {}

    /**
     * A directed edge of the supplied initial network; {@code pts} include the two endpoints. {@code flow}
     * is the (per-cell) raw flow-accumulation value for the edge — width is derived from it downstream via
     * {@link HydrologyTuning#widthFromFlow}. The channel built from this spec carries a constant per-point
     * {@code flow[]} = this value (see {@link #mintChannel}); real per-point flow is assigned to the
     * unified network later by {@code accumulateAndCorrectFlow}.
     */
    public record EdgeSpec(int startNodeIdx, int endNodeIdx, ArrayList<double[]> pts, double flow) {}

    private record Crossing(int channelIdA, int posA, int channelIdB, int posB, double widthA, double widthB) {}

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

        insertSpecs(nodeSpecs, edgeSpecs, resampleDist);
    }

    /**
     * Mints Endpoints and Channels for a batch of {@link NodeSpec}/{@link EdgeSpec} entries into the
     * live graph, resampling each new channel at {@code resampleDist} and wiring outgoing/incoming
     * edges while enforcing the single-outflow invariant (a node may have at most one outgoing edge).
     * {@link EdgeSpec#startNodeIdx()}/{@link EdgeSpec#endNodeIdx()} are indices into the supplied
     * {@code nodeSpecs} list, not node ids — this method mints a fresh node id per spec (so it composes
     * safely with a graph that already has nodes) and returns the mapping from each supplied node-spec
     * index to its minted node id, so a caller (e.g. the local drainage tracer) can reference the
     * freshly minted nodes afterwards. The constructor delegates its own node/edge construction to this
     * method.
     */
    public Map<Integer, Integer> insertSpecs(List<NodeSpec> nodeSpecs, List<EdgeSpec> edgeSpecs, double resampleDist) {
        final Map<Integer, Integer> specIndexToNodeId = new HashMap<>();
        for (int i = 0; i < nodeSpecs.size(); i++) {
            NodeSpec ns = nodeSpecs.get(i);
            final int nodeId = nextNodeId++;
            nodes.put(nodeId, new Endpoint(nodeId, ns.type(), new double[] {ns.x(), ns.z()}));
            specIndexToNodeId.put(i, nodeId);
        }

        for (EdgeSpec es : edgeSpecs) {
            final int startNodeId = specIndexToNodeId.get(es.startNodeIdx());
            final int endNodeId = specIndexToNodeId.get(es.endNodeIdx());
            mintChannel(startNodeId, endNodeId, es.pts(), constantFlow(es.pts().size(), es.flow()), resampleDist);
        }
        return specIndexToNodeId;
    }

    /** A per-point flow array of {@code n} entries all equal to {@code flow} (the constant-flow edge case). */
    private static double[] constantFlow(int n, double flow) {
        final double[] f = new double[n];
        Arrays.fill(f, flow);
        return f;
    }

    /**
     * Mints a new SOURCE-typed node plus a single edge from it to an EXISTING node id already in the
     * graph -- the local drainage tracer's "attach to a global channel" case, where {@code
     * existingEndNodeId} is a JUNCTION minted by {@link #split} and only the upstream endpoint is new.
     * Resamples the channel at {@code resampleDist} and applies the given start/end width taper (mirrors
     * {@link #insertSpecs}'s wiring/single-outflow enforcement via the shared {@link #mintChannel}).
     * Returns the minted node's id.
     */
    public int attachSourceToExistingNode(
            NodeSpec sourceSpec, int existingEndNodeId, ArrayList<double[]> pts, double[] flow, double resampleDist) {
        final int sourceNodeId = nextNodeId++;
        nodes.put(
                sourceNodeId,
                new Endpoint(sourceNodeId, sourceSpec.type(), new double[] {sourceSpec.x(), sourceSpec.z()}));
        mintChannel(sourceNodeId, existingEndNodeId, pts, flow, resampleDist);
        return sourceNodeId;
    }

    /**
     * Mints a new SOURCE and a new DRAIN node plus the single edge between them -- the local drainage
     * tracer's "coast-draining" attach case, where both endpoints are new. Mirrors {@link
     * #attachSourceToExistingNode}'s width-taper/resample handling for the case where no global channel
     * is within reach; the general N-node/M-edge batch case remains {@link #insertSpecs}. Returns
     * {@code {sourceNodeId, drainNodeId}}.
     */
    public int[] attachSourceToNewDrain(
            NodeSpec sourceSpec, NodeSpec drainSpec, ArrayList<double[]> pts, double[] flow, double resampleDist) {
        final int sourceNodeId = nextNodeId++;
        nodes.put(
                sourceNodeId,
                new Endpoint(sourceNodeId, sourceSpec.type(), new double[] {sourceSpec.x(), sourceSpec.z()}));
        final int drainNodeId = nextNodeId++;
        nodes.put(
                drainNodeId, new Endpoint(drainNodeId, drainSpec.type(), new double[] {drainSpec.x(), drainSpec.z()}));
        mintChannel(sourceNodeId, drainNodeId, pts, flow, resampleDist);
        return new int[] {sourceNodeId, drainNodeId};
    }

    /**
     * Shared edge-minting core behind {@link #insertSpecs}, {@link #attachSourceToExistingNode} and
     * {@link #attachSourceToNewDrain}: builds the {@link Channel} from the per-point {@code flow} array
     * (aligned to {@code pts}), resamples at {@code resampleDist} ({@link Channel#reSample} blends the flow
     * array along with the geometry; a no-op on {@link Channel#bedElevations}, which is always null at
     * insertion time -- H2), wires the directed edge between the two given node ids, enforces the
     * single-outflow invariant, and seeds/anchors the boundary nodes' {@link Endpoint#sourceFlow}. Returns
     * the minted channel id.
     */
    private int mintChannel(int startNodeId, int endNodeId, List<double[]> pts, double[] flow, double resampleDist) {
        final int id = nextChannelId++;
        final Channel ch = new Channel(new ArrayList<>(pts), flow, id);
        ch.reSample(resampleDist);
        ch.startNodeId = startNodeId;
        ch.endNodeId = endNodeId;
        channels.put(id, ch);
        final Endpoint start = nodes.get(startNodeId);
        final Endpoint end = nodes.get(endNodeId);
        if (start.outgoing != -1) {
            throw new IllegalArgumentException("node " + start.id + " would have >1 outgoing edge");
        }
        start.outgoing = id;
        end.incoming.add(id);
        // Seed/anchor the boundary nodes so the canonical<->atomic seam (viewAtomic/accumulate) carries a
        // meaningful ownFlow (SOURCE seed) / anchorFlow (DRAIN anchor). Uses the edge's endpoint flow — for
        // the constant-flow global path this is the edge's flow value; deleted in Phase 3 with mintChannel.
        if (start.type == Endpoint.Type.SOURCE) start.sourceFlow = flow[0];
        if (end.type == Endpoint.Type.DRAIN) end.sourceFlow = flow[flow.length - 1];
        return id;
    }

    // ---------------------------------------------------------------------------------------------
    // The two views + the seam (Phase 1)
    //
    // The canonical (channel) view is the field storage above (channels/nodes maps + id counters).
    // The atomic (node) view ({@link AtomicView}) is a List<List<Integer>> adjacency over per-node
    // data where every interior spline point is a first-class node. {@link #viewAtomic()} converts
    // canonical -> atomic; {@link #update} folds an atomic view back into `this` IN PLACE.
    //
    // NOTHING in production calls these yet (they land behavior-neutral in Phase 1). Phase 1 carries
    // only position + topology + role/canonicalId; the flow arrays (ownFlow/anchorFlow/derived flow)
    // referenced by the plan's pseudocode arrive in Phase 2 and are omitted here.
    // ---------------------------------------------------------------------------------------------

    /** Sentinel for "no canonical id" (interior/JUNCTION-equivalent atomic nodes). */
    private static final int NONE = -1;

    /** The per-cell own-flow constant carried by interior/junction atomic nodes (see {@link #viewAtomic()}). */
    private static final double FLOW_PER_CELL = HydrologyTuning.FLOW_PER_CELL_LOCAL;

    /**
     * Atomic (node) view of the network: parallel per-node data plus a directed adjacency where every
     * interior spline point is a first-class node. Built once by {@link #viewAtomic()} (growing via
     * {@link #addNode}), read by {@link #update}. Carries per-node position/role/canonicalId plus the
     * flow inputs {@code ownFlow} (the per-cell constant / SOURCE seed) and {@code anchorFlow}
     * (DRAIN-only ceiling+target); the DERIVED per-node {@code flow} is populated by
     * {@link #accumulateAndCorrectFlow} before {@link #update} reads it.
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
     * Canonical -> atomic. Iterates channels in sorted-by-id order (mirrors {@link #detectCrossings}'s
     * {@code Collections.sort(channelIds)}) so atomic-id assignment is reproducible and HashMap
     * iteration order never leaks in. Each channel's interior points become fresh atomic nodes; the two
     * endpoints collapse keyed on the canonical {@link Endpoint} node id (the first channel touching a
     * node adds it, later channels reuse it), so channels sharing a graph node share one atomic node.
     * SOURCE/DRAIN provenance (and their canonical id) is carried so {@link #update} can preserve their
     * ids. Velocity/acceleration are not carried — {@code createCatmullRom} re-infers them on rebuild.
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
     * single-outflow (K1) guard {@code mintChannel} used. {@code bedElevations} are not preserved.
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

        // inlined K1 guard (mintChannel:203-205): the start endpoint must not already own an outgoing edge
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
    // split / merge — single-step graph primitives
    // ---------------------------------------------------------------------------------------------

    public int split(int id, int pos, boolean redirect) {
        Channel channel = channels.get(id);
        if (channel == null) return -1;
        final int lastIndex = channel.numPts() - 1;
        if (pos <= 0 || pos >= lastIndex) return -1;

        final int downstreamNodeId = channel.endNodeId;
        final double[] splitCoord = channel.spline.points().get(pos).clone();

        ArrayList<double[]> downstreamPoints = new ArrayList<>();
        for (int i = pos; i <= lastIndex; i++)
            downstreamPoints.add(channel.spline.points().get(i));
        final int downstreamChannelId = nextChannelId++;
        Channel downstreamChannel = new Channel(channel.dischargeWidth(), downstreamPoints, downstreamChannelId);
        downstreamChannel.endNodeId = downstreamNodeId;
        channels.put(downstreamChannelId, downstreamChannel);

        ArrayList<Integer> upstreamIndexes = new ArrayList<>();
        for (int i = 0; i <= pos; i++) upstreamIndexes.add(i);
        channel.keepOnly(upstreamIndexes);

        Endpoint downstreamEndpoint = nodes.get(downstreamNodeId);
        downstreamEndpoint.incoming.remove(id);
        downstreamEndpoint.incoming.add(downstreamChannelId);

        if (!redirect) {
            Endpoint junction = newNode(splitCoord);
            channel.endNodeId = junction.id;
            downstreamChannel.startNodeId = junction.id;
            junction.incoming.add(id);
            junction.outgoing = downstreamChannelId;
        } else {
            Endpoint upstreamJunction = newNode(splitCoord);
            Endpoint downstreamJunction = newNode(splitCoord.clone());
            channel.endNodeId = upstreamJunction.id;
            upstreamJunction.incoming.add(id);
            downstreamChannel.startNodeId = downstreamJunction.id;
            downstreamJunction.outgoing = downstreamChannelId;
        }
        return downstreamChannelId;
    }

    public boolean merge(int id) {
        Channel channel = channels.get(id);
        if (channel == null) return false;
        Endpoint junction = nodes.get(channel.endNodeId);
        if (junction == null || junction.type != Endpoint.Type.JUNCTION) return false;
        if (junction.outgoing == -1) return false;
        if (!(junction.incoming.size() == 1 && junction.incoming.contains(id))) return false;

        final int downstreamChannelId = junction.outgoing;
        Channel downstreamChannel = channels.get(downstreamChannelId);
        final int upstreamNodeId = channel.startNodeId;
        final int downstreamNodeId = downstreamChannel.endNodeId;

        ArrayList<double[]> mergedPoints = new ArrayList<>(channel.spline.points());
        List<double[]> downstreamPoints = downstreamChannel.spline.points();
        for (int i = 1; i < downstreamPoints.size(); i++) mergedPoints.add(downstreamPoints.get(i));

        Channel mergedChannel =
                new Channel(Math.max(channel.dischargeWidth(), downstreamChannel.dischargeWidth()), mergedPoints, id);
        mergedChannel.startNodeId = upstreamNodeId;
        mergedChannel.endNodeId = downstreamNodeId;
        channels.put(id, mergedChannel);
        channels.remove(downstreamChannelId);

        Endpoint downstreamEndpoint = nodes.get(downstreamNodeId);
        downstreamEndpoint.incoming.remove(downstreamChannelId);
        downstreamEndpoint.incoming.add(id);

        nodes.remove(junction.id);
        return true;
    }

    private Endpoint newNode(double[] coord) {
        final int nodeId = nextNodeId++;
        Endpoint endpoint = new Endpoint(nodeId, Endpoint.Type.JUNCTION, coord);
        nodes.put(nodeId, endpoint);
        return endpoint;
    }

    // ---------------------------------------------------------------------------------------------
    // Collisions (stream capture)
    // ---------------------------------------------------------------------------------------------

    public void manageCollisions(int step) {
        List<Crossing> crossings = detectCrossings();
        if (FractalTerrainConfig.DEBUG_MANAGE_COLLISIONS) {
            LOG.info("crossing list: {}", Arrays.toString(crossings.toArray()));
        }
        segmentAndResolve(crossings);
        pruneDanglingJunctionLeaves(step);
        deleteOrphanDrains();
        mergePassFromSources();
    }

    private void deleteOrphanDrains() {
        List<Integer> orphanDrainIds = new ArrayList<>();
        for (Endpoint endpoint : nodes.values())
            if (endpoint.type == Endpoint.Type.DRAIN && endpoint.degree() == 0) orphanDrainIds.add(endpoint.id);
        for (int drainId : orphanDrainIds) nodes.remove(drainId);
    }

    private List<Crossing> detectCrossings() {
        quadTree.clear();
        List<Integer> channelIds = new ArrayList<>(channels.keySet());
        Collections.sort(channelIds);
        for (int channelId : channelIds) insertChannel(channels.get(channelId));

        List<Crossing> crossings = new ArrayList<>();
        for (int channelAId : channelIds) {
            Channel channelA = channels.get(channelAId);
            final int pointCountA = channelA.numPts();
            final double widthA = channelA.dischargeWidth();
            final double queryRadius = Math.max(widthA, 1.0);
            Map<Integer, List<double[]>> contactsByPartner = new HashMap<>();
            for (int posA = 1; posA < pointCountA - 1; posA++) {
                final double[] pointA = channelA.spline.points().get(posA);
                List<Channel.ChannelPt> nearbyPoints = quadTree.getPointsInCircle(pointA, queryRadius);
                nearbyPoints.sort(null);

                for (Channel.ChannelPt nearbyPoint : nearbyPoints) {
                    final int channelBId = nearbyPoint.channelId();
                    if (channelBId <= channelAId) continue;
                    Channel channelB = channels.get(channelBId);
                    final int posB = nearbyPoint.index();
                    if (posB <= 0 || posB >= channelB.numPts() - 1) continue;

                    if (nearSharedNode(channelA, channelB, pointA)) continue;
                    final double distance = VectorOps.distance(pointA, nearbyPoint.toArray());
                    if (!ChannelGeometry.channelsOverlap(distance, widthA, channelB.dischargeWidth())) continue;
                    contactsByPartner
                            .computeIfAbsent(channelBId, partner -> new ArrayList<>())
                            .add(new double[] {posA, posB, distance});
                }
            }
            for (Map.Entry<Integer, List<double[]>> entry : contactsByPartner.entrySet()) {
                Channel channelB = channels.get(entry.getKey());
                List<double[]> contactList = entry.getValue();
                contactList.sort(Comparator.comparingDouble(contact -> contact[0]));
                int firstClusterEnd = 1;
                while (firstClusterEnd < contactList.size()
                        && contactList.get(firstClusterEnd)[0] - contactList.get(firstClusterEnd - 1)[0] <= CLUSTER_GAP)
                    firstClusterEnd++;
                double[] closest = contactList.getFirst();
                for (int scan = 1; scan < firstClusterEnd; scan++)
                    if (contactList.get(scan)[2] < closest[2]) closest = contactList.get(scan);
                crossings.add(new Crossing(
                        channelAId,
                        (int) closest[0],
                        channelB.channelId,
                        (int) closest[1],
                        widthA,
                        channelB.dischargeWidth()));
            }
        }
        return crossings;
    }

    private boolean nearSharedNode(Channel channelA, Channel channelB, double[] contactPoint) {
        final double radius = channelA.dischargeWidth() + channelB.dischargeWidth();
        final double radiusSq = radius * radius;
        for (int nodeA : new int[] {channelA.startNodeId, channelA.endNodeId})
            for (int nodeB : new int[] {channelB.startNodeId, channelB.endNodeId})
                if (nodeA != -1 && nodeA == nodeB) {
                    Endpoint shared = nodes.get(nodeA);
                    if (shared != null && VectorOps.distanceSquared(contactPoint, shared.coord) <= radiusSq)
                        return true;
                }
        return false;
    }

    /**
     * Total order over channels used to pick the trunk of a contact cluster: the strongest channel
     * wins. Precedence: an already-downstream channel beats its upstream, then wider beats narrower,
     * then lower id wins (so the order is strict for distinct ids). Returns {@code >0} when
     * {@code idA} is stronger, {@code <0} when {@code idB} is stronger, {@code 0} only when equal.
     */
    private int compareChannels(int idA, int idB) {
        if (idA == idB) return 0;
        if (reachesDownstream(idA, idB)) return -1; // idB is downstream of idA -> idB stronger
        if (reachesDownstream(idB, idA)) return 1; // idA is downstream of idB -> idA stronger
        final double widthA = channels.get(idA).dischargeWidth();
        final double widthB = channels.get(idB).dischargeWidth();
        if (widthA != widthB) return widthA > widthB ? 1 : -1;
        return idA < idB ? 1 : -1; // lower id wins
    }

    private boolean reachesDownstream(int ancestorId, int descendantId) {
        Channel ancestor = channels.get(ancestorId);
        if (ancestor == null) return false;
        int nodeId = ancestor.endNodeId;
        for (int guard = 0; guard <= channels.size() && nodeId != -1; guard++) {
            Endpoint endpoint = nodes.get(nodeId);
            if (endpoint == null || endpoint.outgoing == -1) return false;
            if (endpoint.outgoing == descendantId) return true;
            Channel next = channels.get(endpoint.outgoing);
            if (next == null) return false;
            nodeId = next.endNodeId;
        }
        return false;
    }

    /** A planned split of a channel: which contact cluster it belongs to and whether it is the trunk. */
    private record PlanEntry(long clusterRoot, boolean isTrunk) {}

    /** A captured channel's upstream junction (created by a {@code redirect=true} split) and its cluster. */
    private record Capture(int upstreamJunctionId, long clusterRoot) {}

    /** Packs a {@code (channelId, position)} contact endpoint into a single union-find key. */
    private static long endpointKey(int channelId, int position) {
        return ((long) channelId << 32) | (position & 0xffffffffL);
    }

    private static long ufFind(Map<Long, Long> parent, long key) {
        long root = key;
        long step = parent.getOrDefault(root, root);
        while (step != root) {
            root = step;
            step = parent.getOrDefault(root, root);
        }
        long cursor = key; // path compression
        while (cursor != root) {
            long next = parent.getOrDefault(cursor, cursor);
            parent.put(cursor, root);
            cursor = next;
        }
        return root;
    }

    private static void ufUnion(Map<Long, Long> parent, long a, long b) {
        long rootA = ufFind(parent, a);
        long rootB = ufFind(parent, b);
        if (rootA != rootB) parent.put(rootA, rootB);
    }

    /**
     * Stream-capture resolution. Channels that contact each other at one physical point form a
     * <em>cluster</em> (union-find over {@code (channelId, position)} endpoints); the cluster's
     * strongest channel under {@link #compareChannels} is the trunk and flows through, while every
     * other member is captured into the trunk's junction. This single rule covers a lone crossing,
     * the multi-tributary confluence, and the mixed win/lose case uniformly — all junctions are built
     * by {@link #split}.
     */
    private void segmentAndResolve(List<Crossing> crossings) {
        if (crossings.isEmpty()) return;

        // 1. Cluster contacts that meet at the same physical point.
        Map<Long, Long> parent = new HashMap<>();
        for (Crossing crossing : crossings)
            ufUnion(
                    parent,
                    endpointKey(crossing.channelIdA(), crossing.posA()),
                    endpointKey(crossing.channelIdB(), crossing.posB()));

        // all of the channels that cross into another channel
        Map<Long, Map<Integer, Integer>> clusters = new HashMap<>(); // root -> (channelId -> position)
        for (Crossing crossing : crossings) {
            long root = ufFind(parent, endpointKey(crossing.channelIdA(), crossing.posA()));
            Map<Integer, Integer> members = clusters.computeIfAbsent(root, k -> new HashMap<>());
            members.putIfAbsent(crossing.channelIdA(), crossing.posA());
            members.putIfAbsent(crossing.channelIdB(), crossing.posB());
        }

        // 2. Per cluster: the trunk is the strongest channel; everyone else is captured into it.
        Map<Integer, TreeMap<Integer, PlanEntry>> splitPlan = new HashMap<>(); // channelId -> (position -> plan)
        for (Map.Entry<Long, Map<Integer, Integer>> entry : clusters.entrySet()) {
            final long root = entry.getKey();
            final Map<Integer, Integer> members = entry.getValue();
            int trunk = -1;
            for (int channelId : members.keySet())
                if (trunk == -1 || compareChannels(channelId, trunk) > 0) trunk = channelId;
            if (FractalTerrainConfig.DEBUG_CROSSING_WINNER) LOG.info("cluster {} -> trunk {}", members.keySet(), trunk);
            for (Map.Entry<Integer, Integer> member : members.entrySet())
                splitPlan
                        .computeIfAbsent(member.getKey(), k -> new TreeMap<>())
                        .put(member.getValue(), new PlanEntry(root, member.getKey() == trunk));
        }

        // 3. Split each channel at its planned positions, DESCENDING so positions stay valid.
        Map<Long, Integer> trunkJunction = new HashMap<>();
        List<Capture> captures = new ArrayList<>();
        for (Map.Entry<Integer, TreeMap<Integer, PlanEntry>> entry : splitPlan.entrySet()) {
            final int channelId = entry.getKey();
            for (Map.Entry<Integer, PlanEntry> planned :
                    entry.getValue().descendingMap().entrySet()) {
                final PlanEntry plan = planned.getValue();
                if (split(channelId, planned.getKey(), !plan.isTrunk()) == -1) continue;
                final int junctionId = channels.get(channelId).endNodeId;
                if (plan.isTrunk()) trunkJunction.put(plan.clusterRoot(), junctionId);
                else captures.add(new Capture(junctionId, plan.clusterRoot()));
            }
        }

        // 4. Redirect every captured upstream segment into its cluster trunk's junction.
        for (Capture capture : captures) {
            final Integer trunkJunctionId = trunkJunction.get(capture.clusterRoot());
            if (trunkJunctionId == null) continue; // trunk was not a through-junction (rare cross-cluster)
            Endpoint capturedJunction = nodes.get(capture.upstreamJunctionId());
            if (capturedJunction == null || capturedJunction.incoming.isEmpty()) continue;
            final int tributaryId = capturedJunction.incoming.iterator().next();
            Channel tributary = channels.get(tributaryId);
            Endpoint trunkEndpoint = nodes.get(trunkJunctionId);
            tributary.endNodeId = trunkJunctionId;
            trunkEndpoint.incoming.add(tributaryId);
            ArrayList<double[]> tributaryPoints = tributary.spline.points();
            tributaryPoints.set(tributaryPoints.size() - 1, trunkEndpoint.coord.clone());
            tributary.spline = QuinticHermiteSpline.createCatmullRom(tributaryPoints);
            nodes.remove(capture.upstreamJunctionId());
        }
    }

    private void pruneDanglingJunctionLeaves(int step) {
        ArrayDeque<Integer> leafQueue = new ArrayDeque<>();
        for (Endpoint endpoint : nodes.values())
            if (endpoint.type == Endpoint.Type.JUNCTION && endpoint.degree() == 1) leafQueue.add(endpoint.id);
        while (!leafQueue.isEmpty()) {
            final int nodeId = leafQueue.poll();
            Endpoint endpoint = nodes.get(nodeId);
            if (endpoint == null || endpoint.type != Endpoint.Type.JUNCTION || endpoint.degree() != 1) continue;

            final boolean incidentIsOutgoing = endpoint.outgoing != -1;
            final int channelId = incidentIsOutgoing
                    ? endpoint.outgoing
                    : endpoint.incoming.iterator().next();
            Channel channel = channels.get(channelId);
            final int otherNodeId = incidentIsOutgoing ? channel.endNodeId : channel.startNodeId;
            Endpoint otherEndpoint = nodes.get(otherNodeId);
            if (otherEndpoint != null) {
                if (otherEndpoint.outgoing == channelId) otherEndpoint.outgoing = -1;
                otherEndpoint.incoming.remove(channelId);
                if (otherEndpoint.type == Endpoint.Type.JUNCTION && otherEndpoint.degree() == 1)
                    leafQueue.add(otherEndpoint.id);
            }
            if (savePreviousStates && channel.numPts() >= 2) {
                removedPaths.add(new RemovedPath(
                        HydrologicalFeature.ABANDONED_RIVER,
                        new ArrayList<>(channel.spline.points()),
                        channel.dischargeWidth(),
                        step));
            }
            channels.remove(channelId);
            nodes.remove(nodeId);
        }
    }

    private void mergePassFromSources() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Endpoint source : new ArrayList<>(nodes.values())) {
                if (source.type != Endpoint.Type.SOURCE) continue;
                int channelId = source.outgoing;
                while (channelId != -1) {
                    Channel channel = channels.get(channelId);
                    if (channel == null) break;
                    if (merge(channelId)) {
                        changed = true;
                        continue;
                    }
                    Endpoint downstreamEndpoint = nodes.get(channel.endNodeId);
                    if (downstreamEndpoint == null) break;
                    channelId = downstreamEndpoint.outgoing;
                }
            }
        }
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
