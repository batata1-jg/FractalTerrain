package me.batata_1.fractal_terrain.hydrology.network;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * A vertex of the river-network graph held by {@link RiverNetwork}, sitting on a channel endpoint.
 *
 * <p>The network is a dendritic in-tree, so {@link #outgoing} is one channel id rather than a set;
 * that single-outflow rule is invariant K1, enforced by {@link RiverNetwork#assertSingleOutflow}.
 * The three node types differ in how they may be created and destroyed — a SOURCE never is, a DRAIN
 * only by stream capture, a JUNCTION freely — which is what the split/merge/prune paths rely on.
 */
public class Endpoint {

    public enum Type {
        SOURCE,
        DRAIN,
        JUNCTION;

        public boolean isSourceOrDrain() {
            return this == SOURCE || this == DRAIN;
        }
    }

    public final int id;
    public final Type type;
    public double[] coord;

    /** Boundary flow carried across the canonical/atomic seam: seed on SOURCE, anchor on DRAIN. */
    public double boundaryFlow = 0.0;

    /** Bed elevation filled by {@code ChannelElevationAssigner}; the carve reads {@link Channel#bedElevations} instead. */
    public double elevation = Double.NaN;

    /** channelIds whose endNodeId == this.id (many allowed). */
    public final IntSet incoming = new IntOpenHashSet();

    /** the single channelId whose startNodeId == this.id, or -1 if none. */
    public int outgoing = -1;

    public Endpoint(int id, Type type, double[] coord) {
        this.id = id;
        this.type = type;
        this.coord = coord;
    }

    public int degree() {
        return incoming.size() + (outgoing == -1 ? 0 : 1);
    }

    public boolean isSourceOrDrain() {
        return type.isSourceOrDrain();
    }

    @Override
    public String toString() {
        return "Endpoint{" + id + " " + type + " in=" + incoming + " out=" + outgoing + "}";
    }
}
