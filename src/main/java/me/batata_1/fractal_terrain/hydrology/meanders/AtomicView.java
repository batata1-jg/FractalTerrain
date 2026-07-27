package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final List<double[]> position = new ArrayList<>();
    /**
     * SOURCE / DRAIN / JUNCTION, or {@code null} for an interior spline point.
     */
    private final List<Endpoint.Type> role = new ArrayList<>();
    /**
     * Valid only where {@link #role} is SOURCE or DRAIN — the canonical {@link Endpoint} id to preserve.
     */
    private final List<Integer> canonicalId = new ArrayList<>();
    /**
     * CARRIED input: the per-cell constant {@link #FLOW_PER_CELL} (interior/junction) or the SOURCE seed.
     */
    private final List<Double> ownFlow = new ArrayList<>();
    /**
     * CARRIED input, meaningful only where {@link #role} is DRAIN: the clamp ceiling + near-drain target.
     */
    private final List<Double> anchorFlow = new ArrayList<>();
    /**
     * {@code adjacency.get(u)}: directed tree-successor edge(s) out of atomic node {@code u}.
     */
    final List<List<Integer>> adjacency = new ArrayList<>();
    /**
     * canonical channelId -> atomic id per spline point (only populated by {@link #viewAtomic()}).
     */
    final Map<Integer, int[]> pointAtomicIds = new HashMap<>();
    /**
     * DERIVED per-node flow; {@code null} until {@link #accumulateAndCorrectFlow} runs.
     */
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

    /**
     * The DERIVED per-node flow at {@code id}, falling back to {@code ownFlow} when not yet accumulated.
     */
    double flow(int id) {
        return (flow != null) ? flow[id] : ownFlow.get(id);
    }

    /**
     * A fresh copy of the position of atomic node {@code id}.
     */
    double[] pos(int id) {
        return position.get(id).clone();
    }

    /**
     * Append a new atomic node (position cloned) and return its atomic id.
     */
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
