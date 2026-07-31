package me.batata_1.fractal_terrain.math.ds;

import org.jetbrains.annotations.NotNull;

/**
 * A point stored in a point-based spatial index.
 *
 * <p>Kept to a single required method so payload-carrying records can be indexed directly, without a
 * wrapper allocation per point. Everything else is derived by default.
 *
 * <p>Persistence is deliberately not part of this contract — a point that needs it implements
 * {@link me.batata_1.fractal_terrain.storage.Persistable} separately.
 */
public interface SpatialIndexPoint extends Comparable<SpatialIndexPoint> {

    /** The point's coordinates. May be the backing array; callers must treat it as read-only. */
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
    default int compareTo(@NotNull SpatialIndexPoint pt) {
        if (pt.size() != this.size()) throw new IllegalArgumentException("points must match rank");
        for (int i = 0; i < this.size(); i++) {
            if (pt.get(i) < this.get(i)) return 1;
            if (pt.get(i) > this.get(i)) return -1;
        }
        return 0;
    }
}
