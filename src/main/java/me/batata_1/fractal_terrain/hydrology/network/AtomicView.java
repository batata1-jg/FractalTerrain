package me.batata_1.fractal_terrain.hydrology.network;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * Atomic (node) view of the network: parallel per-node data plus a directed adjacency where every
 * interior spline point is a first-class node. Built by {@link #viewAtomic()} / {@link #buildFromSpecs},
 * read by {@link #update}. Carries per-node position/role/canonicalId plus the flow inputs
 * {@code ownFlow} (per-cell constant / SOURCE seed) and {@code anchorFlow} (DRAIN-only ceiling+target);
 * the DERIVED per-node {@code flow} is populated by {@link #accumulateAndCorrectFlow} before
 * {@link #update} reads it. {@link #pointAtomicIds} (populated only by {@link #viewAtomic()}) maps each
 * canonical channel's spline points to their atomic ids, so crossing detection can re-stamp contacts.
 */
public final class AtomicView {
    private final List<double[]> position = new ObjectArrayList<>();
    /**
     * SOURCE / DRAIN / JUNCTION, or {@code null} for an interior spline point.
     */
    public final List<Endpoint.Type> role = new ObjectArrayList<>();
    /**
     * Valid only where {@link #role} is SOURCE or DRAIN — the canonical {@link Endpoint} id to preserve.
     */
    private final List<Integer> canonicalId = new ObjectArrayList<>();
    /**
     * CARRIED input: the per-cell constant {@link #FLOW_PER_CELL} (interior/junction) or the SOURCE seed.
     */
    private final List<Double> ownFlow = new ObjectArrayList<>();
    /**
     * CARRIED input, meaningful only where {@link #role} is DRAIN: the clamp ceiling + near-drain target.
     */
    public final List<Double> anchorFlow = new ObjectArrayList<>();
    /** Directed successors per atomic node. Mutate only via {@link #addDirectedEdge(int, int)}. */
    public final List<List<Integer>> adjacency = new ObjectArrayList<>();
    /**
     * canonical channelId -> atomic id per spline point (only populated by {@link #viewAtomic()}).
     */
    final Int2ObjectOpenHashMap<int[]> pointAtomicIds = new Int2ObjectOpenHashMap<>();

    public int size() {
        return position.size();
    }

    public Endpoint.Type role(int id) {
        return role.get(id);
    }

    public int canonicalId(int id) {
        return canonicalId.get(id);
    }

    public double ownFlow(int id) {
        return ownFlow.get(id);
    }

    public double anchorFlow(int id) {
        return anchorFlow.get(id);
    }

    /**
     * A fresh copy of the position of atomic node {@code id}.
     */
    public double[] pos(int id) {
        return position.get(id).clone();
    }

    /**
     * Append a new atomic node (position cloned) and return its atomic id.
     */
    public int addNode(double[] pos, Endpoint.Type role, int canonicalId, double ownFlow, double anchorFlow) {
        final int id = position.size();
        position.add(pos.clone());
        this.role.add(role);
        this.canonicalId.add(canonicalId);
        this.ownFlow.add(ownFlow);
        this.anchorFlow.add(anchorFlow);
        adjacency.add(new ObjectArrayList<>());
        return id;
    }

    /** The only sanctioned way to grow the adjacency; exists so callers cannot introduce a duplicate
     *  edge, which the single-outflow invariant would then read as a second outflow. */
    public void addDirectedEdge(int a, int b) {
        final List<Integer> out = adjacency.get(a);
        for (int existing : out) if (existing == b) return;
        out.add(b);
    }

    /** Whether the directed edge {@code a -> b} is currently present in the adjacency. */
    public boolean hasDirectedEdge(int a, int b) {
        return adjacency.get(a).contains(b);
    }

    /** Remove the directed edge {@code a -> b} from the adjacency if present (no-op otherwise). */
    private void removeDirectedEdge(int a, int b) {
        adjacency.get(a).remove((Integer) b);
    }

    // ---------------------------------------------------------------------------------------------
    // Flow accumulation + drain correction (populates the atomic view's DERIVED per-node flow)
    // ---------------------------------------------------------------------------------------------

    /** Derives the per-node flow that drives channel width, and smooths it toward each drain's anchor so
     *  width stays continuous into the joined river. Must run before {@link RiverNetwork#update} reads
     *  flow. Sums over the atomic adjacency, never {@link Endpoint#incoming} — see {@code README.md}. */
    public double[] accumulateAndCorrectFlow() {
        final int n = this.size();

        // predecessors u of each edge u -> node, each list sorted ascending by atomic id so every
        // confluence sum order is pinned (Determinism).
        final List<List<Integer>> predecessors = new ObjectArrayList<>(n);
        for (int v = 0; v < n; v++) predecessors.add(new ObjectArrayList<>());
        for (int u = 0; u < n; u++)
            for (int v : adjacency.get(u)) predecessors.get(v).add(u);
        for (List<Integer> preds : predecessors) Collections.sort(preds);

        final int[] remainingIn = new int[n];
        for (int v = 0; v < n; v++) remainingIn[v] = predecessors.get(v).size();

        final double[] totalFlow = new double[n];
        final IntSortedSet ready = new IntAVLTreeSet(); // ascending atomic id — deterministic frontier
        for (int v = 0; v < n; v++) if (remainingIn[v] == 0) ready.add(v);

        while (!ready.isEmpty()) {
            final int node = ready.firstInt();
            ready.remove(node);
            double sum = ownFlow.get(node);
            for (int tributary : predecessors.get(node)) sum += totalFlow[tributary];
            totalFlow[node] = sum;
            for (int next : adjacency.get(node)) if (--remainingIn[next] == 0) ready.add(next);
        }

        final double[] flow = totalFlow.clone();

        // Per drain (sorted): clamp the basin to the anchor ceiling, then lerp the last few mainstem
        // nodes up to the anchor. No basin-wide rescale (headwaters keep their small natural flow).
        for (int drain = 0; drain < n; drain++) {
            if (role.get(drain) != Endpoint.Type.DRAIN) continue;
            final double anchor = anchorFlow.get(drain);
            if (anchor == -1) continue; // does not require the drain to have a maximum flow;
            // 1. clamp every basin node to the anchor ceiling
            for (int node : basinOf(drain, predecessors)) flow[node] = Math.min(totalFlow[node], anchor);
            // 2. the drain reads its anchor exactly
            flow[drain] = anchor;

            // 3. smooth the drain jump along the mainstem, if it exceeds the step threshold
            int up = mainstemPredecessor(drain, predecessors, flow);
            if (up == -1 || anchor - flow[up] <= HydrologyTuning.DRAIN_FLOW_SMOOTH_STEP) continue;

            final List<Integer> chain = new ObjectArrayList<>();
            int node = up;
            while (node != -1
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

        return flow;
    }

    /** All atomic nodes upstream of {@code drain} (reverse-reachable via predecessors), including it. */
    private static List<Integer> basinOf(int drain, List<List<Integer>> predecessors) {
        final List<Integer> basin = new ObjectArrayList<>();
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
        int best = -1;
        for (int up : predecessors.get(node)) { // predecessors already ascending by id
            if (best == -1 || flow[up] > flow[best]) best = up;
        }
        return best;
    }

    // the edges itself can cross
    public record nodePrimitive(double[] coord, double radius, int id) implements SpatialIndexCircle {

        @Override
        public double[] getCenter() {
            return coord;
        }

        @Override
        public double getRadius() {
            return radius;
        }
    }

    /** canonicalId value for interior / crossing atomic nodes (mirrors {@code RiverNetwork.NONE}). */
    private static final int NO_CANONICAL_ID = -1;

    /** Geometric tolerance for the segment-crossing test (parallel guard + strict-interior margin). */
    private static final double CROSS_EPS = 1e-9;

    /** Planarizes the adjacency by re-routing crossing edges through a shared node at the intersection.
     *  Exists because meander migration lets channels drift across each other, and downstream carving
     *  assumes a planar network. */
    public void resolveCrossingEdges() {
        final int originalSize = size();
        if (originalSize < 2) return;

        // 1. Undirected position segments (dedup both directions of the same geometric edge), sorted for a
        //    deterministic crossing-node id assignment.
        final List<int[]> segments = new ObjectArrayList<>(); // {lo, hi}, lo < hi
        final LongSet seen = new LongOpenHashSet();
        for (int u = 0; u < originalSize; u++)
            for (int v : adjacency.get(u)) {
                if (u == v) continue;
                final int lo = Math.min(u, v), hi = Math.max(u, v);
                if (seen.add(((long) lo << 32) | (hi & 0xffffffffL))) segments.add(new int[] {lo, hi});
            }
        segments.sort(Comparator.<int[]>comparingInt(s -> s[0]).thenComparingInt(s -> s[1]));

        // node -> indices of segments incident to it (candidate lookup keyed on a shared / near node).
        final List<List<Integer>> incident = new ObjectArrayList<>(originalSize);
        for (int i = 0; i < originalSize; i++) incident.add(new ObjectArrayList<>());
        for (int si = 0; si < segments.size(); si++) {
            incident.get(segments.get(si)[0]).add(si);
            incident.get(segments.get(si)[1]).add(si);
        }

        // 2. Node R-tree; each node's proximity circle bounds how far a crossing partner's endpoint can sit.
        final double radius = HydrologyTuning.MAX_MIGRATION * 5;
        final List<nodePrimitive> primitives = new ObjectArrayList<>(originalSize);
        for (int i = 0; i < originalSize; i++) primitives.add(new nodePrimitive(pos(i), radius, i));
        final ImmutableRTree<nodePrimitive> index = new ImmutableRTree<>(primitives, null);

        // 3. Detect crossings; record the (t, crossingNodeId) split points per segment (t = param along lo->hi).
        final List<List<double[]>> splits = new ObjectArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) splits.add(new ObjectArrayList<>());
        final List<nodePrimitive> nearby = new ObjectArrayList<>();
        for (int si = 0; si < segments.size(); si++) {
            final int a = segments.get(si)[0], b = segments.get(si)[1];
            final double[] pa = pos(a), pb = pos(b);

            final IntSortedSet candidates = new IntAVLTreeSet(); // ascending -> deterministic
            nearby.clear();
            index.queryContaining(pa, nearby);
            index.queryContaining(pb, nearby);
            for (nodePrimitive nu : nearby) for (int sj : incident.get(nu.id())) if (sj > si) candidates.add(sj);

            for (int sj : candidates) {
                final int c = segments.get(sj)[0], d = segments.get(sj)[1];
                if (a == c || a == d || b == c || b == d) continue; // share an endpoint -> touch, not cross
                final double[] hit = segmentCrossing(pa, pb, pos(c), pos(d));
                if (hit == null) continue;
                final int xId = addNode(
                        new double[] {hit[2], hit[3]}, null, NO_CANONICAL_ID, HydrologyTuning.FLOW_PER_CELL_LOCAL, -1);
                splits.get(si).add(new double[] {hit[0], xId});
                splits.get(sj).add(new double[] {hit[1], xId});
            }
        }

        // 4. Re-route each crossed segment's directed edge(s) through its crossing nodes (ordered by param).
        for (int si = 0; si < segments.size(); si++) {
            final List<double[]> segSplits = splits.get(si);
            if (segSplits.isEmpty()) continue;
            segSplits.sort(Comparator.comparingDouble(s -> s[0])); // ascending t: lo -> hi
            final int a = segments.get(si)[0], b = segments.get(si)[1];

            final int[] chain = new int[segSplits.size() + 2]; // lo, crossing nodes..., hi
            chain[0] = a;
            for (int k = 0; k < segSplits.size(); k++)
                chain[k + 1] = (int) segSplits.get(k)[1];
            chain[chain.length - 1] = b;

            if (hasDirectedEdge(a, b)) { // lo -> hi: ascending t
                removeDirectedEdge(a, b);
                for (int k = 0; k + 1 < chain.length; k++) addDirectedEdge(chain[k], chain[k + 1]);
            }
            if (hasDirectedEdge(b, a)) { // hi -> lo: descending t
                removeDirectedEdge(b, a);
                for (int k = chain.length - 1; k - 1 >= 0; k--) addDirectedEdge(chain[k], chain[k - 1]);
            }
        }
    }

    /** Interior-only segment intersection for {@link #resolveCrossingEdges}; endpoint touches are not
     *  crossings, since channels legitimately share endpoints at confluences. */
    private static double[] segmentCrossing(double[] p1, double[] p2, double[] p3, double[] p4) {
        final double d1x = p2[0] - p1[0], d1y = p2[1] - p1[1];
        final double d2x = p4[0] - p3[0], d2y = p4[1] - p3[1];
        final double denom = d1x * d2y - d1y * d2x;
        if (Math.abs(denom) < CROSS_EPS) return null; // parallel or collinear
        final double rx = p3[0] - p1[0], ry = p3[1] - p1[1];
        final double t = (rx * d2y - ry * d2x) / denom; // param along p1->p2
        final double s = (rx * d1y - ry * d1x) / denom; // param along p3->p4
        if (t <= CROSS_EPS || t >= 1 - CROSS_EPS || s <= CROSS_EPS || s >= 1 - CROSS_EPS) return null;
        return new double[] {t, s, p1[0] + d1x * t, p1[1] + d1y * t};
    }
}
