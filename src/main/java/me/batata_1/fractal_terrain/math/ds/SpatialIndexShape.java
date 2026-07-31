package me.batata_1.fractal_terrain.math.ds;

/**
 * A 2-D shape, serving as a query region against a point index and as a stored entry in an
 * {@link ImmutableRTree} — one contract rather than two, so the same geometry serves both.
 *
 * <p>Box arguments are always {@code (lowerCorner, upperCorner)}, never {@code (corner, extent)}.
 *
 * <p>The concrete geometries are interfaces so payload-carrying records can implement them directly;
 * the nested records are ready-made for building queries.
 */
public interface SpatialIndexShape {

    /** True when this shape and the box {@code [lowerCorner, upperCorner]} are disjoint (node pruning). */
    boolean notIntersect(double[] lowerCorner, double[] upperCorner);

    /** True when this shape fully contains the box {@code [lowerCorner, upperCorner]} (node bulk-accept). */
    boolean contains(double[] lowerCorner, double[] upperCorner);

    /** True when this shape contains {@code queryPoint} — the {@link ImmutableRTree} stabbing primitive. */
    boolean containsPoint(double[] queryPoint);

    /** Inflated {@link #containsPoint}, so one prefetch can catch every shape reaching a whole chunk. */
    boolean containsPointInflated(double[] queryPoint, double inflateRadius);

    /** Writes the MBR into caller arrays, so a bulk load can reuse two scratch arrays. */
    void writeMbrInto(double[] mbrLowerCornerOut, double[] mbrUpperCornerOut);

    /** Leaf-scan convenience for the point-tree walks. */
    default boolean contains(SpatialIndexPoint point) {
        return containsPoint(point.getCoords());
    }

    /** Ready-made circle, for query construction or as a bare stored entry. */
    record Circle(double[] center, double radius) implements SpatialIndexCircle {
        @Override
        public double[] getCenter() {
            return center;
        }

        @Override
        public double getRadius() {
            return radius;
        }
    }

    /** Ready-made axis-aligned rectangle, for query construction or as a bare stored entry. */
    record Rectangle(double[] lowerCorner, double[] upperCorner) implements SpatialIndexRectangle {
        @Override
        public double[] getLowerCorner() {
            return lowerCorner;
        }

        @Override
        public double[] getUpperCorner() {
            return upperCorner;
        }
    }
}
