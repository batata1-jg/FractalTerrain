package me.batata_1.fractal_terrain.math.ds;

/**
 * A circle {@link SpatialIndexShape}. Implementations (typically payload-carrying {@code record}s,
 * e.g. {@code HydrologicalUnit} with its influence radius) only provide {@link #getCenter()} and
 * {@link #getRadius()}; every geometric test is defaulted off those two — mirroring how
 * {@link SpatialIndexPoint} derives everything from {@code getCoords()}. All distance tests compare
 * squared distances, so no method takes a square root.
 */
public interface SpatialIndexCircle extends SpatialIndexShape {

    /** The circle's center. May return the backing array — callers must treat it as read-only. */
    double[] getCenter();

    double getRadius();

    /** Clamp-to-box nearest-point test: disjoint iff the box's closest point is farther than the radius. */
    @Override
    default boolean notIntersect(double[] lowerCorner, double[] upperCorner) {
        final double[] center = getCenter();
        final double radius = getRadius();
        final double clampedX = Math.clamp(center[0], lowerCorner[0], upperCorner[0]);
        final double clampedZ = Math.clamp(center[1], lowerCorner[1], upperCorner[1]);
        final double deltaX = center[0] - clampedX;
        final double deltaZ = center[1] - clampedZ;
        return deltaX * deltaX + deltaZ * deltaZ > radius * radius;
    }

    /** Farthest-corner test: the box is contained iff its farthest corner is within the radius. */
    @Override
    default boolean contains(double[] lowerCorner, double[] upperCorner) {
        final double[] center = getCenter();
        final double radius = getRadius();
        final double farthestDeltaX =
                Math.max(Math.abs(lowerCorner[0] - center[0]), Math.abs(upperCorner[0] - center[0]));
        final double farthestDeltaZ =
                Math.max(Math.abs(lowerCorner[1] - center[1]), Math.abs(upperCorner[1] - center[1]));
        return farthestDeltaX * farthestDeltaX + farthestDeltaZ * farthestDeltaZ <= radius * radius;
    }

    @Override
    default boolean containsPoint(double[] queryPoint) {
        final double[] center = getCenter();
        final double radius = getRadius();
        final double deltaX = queryPoint[0] - center[0];
        final double deltaZ = queryPoint[1] - center[1];
        return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
    }

    @Override
    default boolean containsPointInflated(double[] queryPoint, double inflateRadius) {
        final double[] center = getCenter();
        final double inflatedRadius = getRadius() + inflateRadius;
        final double deltaX = queryPoint[0] - center[0];
        final double deltaZ = queryPoint[1] - center[1];
        return deltaX * deltaX + deltaZ * deltaZ <= inflatedRadius * inflatedRadius;
    }

    @Override
    default void writeMbrInto(double[] mbrLowerCornerOut, double[] mbrUpperCornerOut) {
        final double[] center = getCenter();
        final double radius = getRadius();
        mbrLowerCornerOut[0] = center[0] - radius;
        mbrLowerCornerOut[1] = center[1] - radius;
        mbrUpperCornerOut[0] = center[0] + radius;
        mbrUpperCornerOut[1] = center[1] + radius;
    }
}
