package me.batata_1.fractal_terrain.math.ds;

/**
 * An axis-aligned rectangle {@link SpatialIndexShape}. Implementations (typically payload-carrying
 * {@code record}s) only provide {@link #getLowerCorner()} and {@link #getUpperCorner()}; every
 * geometric test is defaulted off those two — mirroring how {@link SpatialIndexPoint} derives
 * everything from {@code getCoords()}.
 */
public interface SpatialIndexRectangle extends SpatialIndexShape {

    /** The rectangle's lower (min-X, min-Z) corner. May return the backing array — treat as read-only. */
    double[] getLowerCorner();

    /** The rectangle's upper (max-X, max-Z) corner. May return the backing array — treat as read-only. */
    double[] getUpperCorner();

    /** Interval-overlap test per axis. */
    @Override
    default boolean intersects(double[] lowerCorner, double[] upperCorner) {
        final double[] thisLower = getLowerCorner();
        final double[] thisUpper = getUpperCorner();
        return thisUpper[0] >= lowerCorner[0]
                && upperCorner[0] >= thisLower[0]
                && thisUpper[1] >= lowerCorner[1]
                && upperCorner[1] >= thisLower[1];
    }

    /** The box is contained iff both its corners lie within this rectangle's intervals. */
    @Override
    default boolean contains(double[] lowerCorner, double[] upperCorner) {
        final double[] thisLower = getLowerCorner();
        final double[] thisUpper = getUpperCorner();
        return thisLower[0] <= lowerCorner[0]
                && upperCorner[0] <= thisUpper[0]
                && thisLower[1] <= lowerCorner[1]
                && upperCorner[1] <= thisUpper[1];
    }

    @Override
    default boolean containsPoint(double[] queryPoint) {
        final double[] thisLower = getLowerCorner();
        final double[] thisUpper = getUpperCorner();
        return thisLower[0] <= queryPoint[0]
                && queryPoint[0] <= thisUpper[0]
                && thisLower[1] <= queryPoint[1]
                && queryPoint[1] <= thisUpper[1];
    }

    /** Clamp-to-rectangle nearest-point test: within {@code inflateRadius} of the rectangle. */
    @Override
    default boolean containsPointInflated(double[] queryPoint, double inflateRadius) {
        final double[] thisLower = getLowerCorner();
        final double[] thisUpper = getUpperCorner();
        final double clampedX = Math.clamp(queryPoint[0], thisLower[0], thisUpper[0]);
        final double clampedZ = Math.clamp(queryPoint[1], thisLower[1], thisUpper[1]);
        final double deltaX = queryPoint[0] - clampedX;
        final double deltaZ = queryPoint[1] - clampedZ;
        return deltaX * deltaX + deltaZ * deltaZ <= inflateRadius * inflateRadius;
    }

    @Override
    default void writeMbrInto(double[] mbrLowerCornerOut, double[] mbrUpperCornerOut) {
        final double[] thisLower = getLowerCorner();
        final double[] thisUpper = getUpperCorner();
        mbrLowerCornerOut[0] = thisLower[0];
        mbrLowerCornerOut[1] = thisLower[1];
        mbrUpperCornerOut[0] = thisUpper[0];
        mbrUpperCornerOut[1] = thisUpper[1];
    }
}
