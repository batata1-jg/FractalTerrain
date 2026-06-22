package me.batata_1.fractal_terrain.math.ds;

import org.jetbrains.annotations.NotNull;

/**
 * A point stored in a {@link QuadTree}. The only thing an implementation must provide is its
 * coordinate array via {@link #getCoords()}; everything else (axis access, rank, comparison) is
 * derived by default.
 *
 * <p>Implementations are typically {@code record}s that carry extra payload alongside the
 * coordinates (e.g. {@link me.batata_1.fractal_terrain.hydrology.meanders.Channel.ChannelPt},
 * {@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit}). A point that must be persisted
 * additionally implements {@link me.batata_1.fractal_terrain.storage.Persistable}; that
 * responsibility is left to the implementation, not this interface.
 */
public interface QuadTreePoint extends Comparable<QuadTreePoint> {

    /**
     * The point's coordinates. May return the backing array for performance — callers must treat it
     * as read-only.
     */
    double[] getCoords();

    default double get(int axis) {
        return getCoords()[axis];
    }

    default int size() {
        return getCoords().length;
    }

    /** Read-only view of the coordinates (the backing array). */
    default double[] toArray() {
        return getCoords();
    }

    @Override
    default int compareTo(@NotNull QuadTreePoint pt) {
        if (pt.size() != this.size()) throw new IllegalArgumentException("points must match rank");
        for (int i = 0; i < this.size(); i++) {
            if (pt.get(i) < this.get(i)) return 1;
            if (pt.get(i) > this.get(i)) return -1;
        }
        return 0;
    }
}
