package me.batata_1.fractal_terrain.math.ds;

/**
 * A 2-D shape playing either (or both) of two roles in the spatial-index family:
 *
 * <ul>
 *   <li><b>Query region</b> against a point index ({@link QuadTree}, {@link ImmutableQuadTree}) —
 *       the tree walks prune with {@link #notIntersect} and bulk-accept with
 *       {@link #contains(double[], double[])};
 *   <li><b>Stored entry</b> of an {@link ImmutableRTree} — the stabbing query tests candidates with
 *       {@link #containsPoint} / {@link #containsPointInflated} and the bulk load reads each entry's
 *       bounding box via {@link #writeMbrInto}.
 * </ul>
 *
 * <p>Box arguments are always {@code (lowerCorner, upperCorner)} pairs — <b>not</b>
 * {@code (corner, extent)}. Like {@link SpatialIndexPoint}, the concrete geometries are interfaces
 * meant to be implemented by payload-carrying records: {@link SpatialIndexCircle} and
 * {@link SpatialIndexRectangle} default every method off their few geometric accessors, and the
 * nested {@link Circle} / {@link Rectangle} records are the ready-made implementations for query
 * construction.
 */
public interface SpatialIndexShape {

    /** True when this shape and the box {@code [lowerCorner, upperCorner]} are disjoint (node pruning). */
    boolean notIntersect(double[] lowerCorner, double[] upperCorner);

    /** True when this shape fully contains the box {@code [lowerCorner, upperCorner]} (node bulk-accept). */
    boolean contains(double[] lowerCorner, double[] upperCorner);

    /** True when this shape contains {@code queryPoint} — the {@link ImmutableRTree} stabbing primitive. */
    boolean containsPoint(double[] queryPoint);

    /**
     * {@link #containsPoint} with this shape's boundary expanded outward by {@code inflateRadius}:
     * true when {@code queryPoint} lies within {@code inflateRadius} of the shape. Serves radius-expanded
     * stabbing queries (e.g. a chunk-prefetch that must catch every shape reaching anywhere in the chunk).
     */
    boolean containsPointInflated(double[] queryPoint, double inflateRadius);

    /**
     * Writes this shape's minimum bounding rectangle into the caller's arrays (lower corner into
     * {@code mbrLowerCornerOut}, upper corner into {@code mbrUpperCornerOut}) — allocation-free so bulk
     * loads can reuse two scratch arrays.
     */
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
