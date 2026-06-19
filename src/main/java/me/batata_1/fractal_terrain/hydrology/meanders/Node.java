package me.batata_1.fractal_terrain.hydrology.meanders;

import java.util.HashSet;
import java.util.Set;

/**
 * A vertex of the river-network graph held by {@link Meanders}. Nodes sit exactly on channel
 * endpoints. The network is a dendritic in-tree: every node has at most ONE outgoing edge
 * (multiple upstreams may flow in, but only one channel flows out), so {@link #outgoing} is a
 * single channel id rather than a set.
 *
 * <ul>
 *   <li>SOURCE — 1 outgoing, 0 incoming. Created only at construction, never destroyed.</li>
 *   <li>DRAIN — 0 outgoing, &ge;1 incoming. Never created; deleted only when a capture orphans it.</li>
 *   <li>JUNCTION — &ge;1 incoming, exactly 1 outgoing. Created/destroyed by split/merge/prune.</li>
 * </ul>
 */
public class Node {

    public enum NodeType {
        SOURCE,
        DRAIN,
        JUNCTION
    }

    public final int id;
    public final NodeType type;
    public double[] coord;

    /** channelIds whose endNodeId == this.id (many allowed). */
    public final Set<Integer> incoming = new HashSet<>();

    /** the single channelId whose startNodeId == this.id, or -1 if none. */
    public int outgoing = -1;

    public Node(int id, NodeType type, double[] coord) {
        this.id = id;
        this.type = type;
        this.coord = coord;
    }

    public int degree() {
        return incoming.size() + (outgoing == -1 ? 0 : 1);
    }

    public boolean isSourceOrDrain() {
        return type == NodeType.SOURCE || type == NodeType.DRAIN;
    }

    @Override
    public String toString() {
        return "Node{" + id + " " + type + " in=" + incoming + " out=" + outgoing + "}";
    }
}
