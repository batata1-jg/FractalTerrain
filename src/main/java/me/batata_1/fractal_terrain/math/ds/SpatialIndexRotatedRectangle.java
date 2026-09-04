package me.batata_1.fractal_terrain.math.ds;

/**
 * An oriented (rotated) rectangle {@link SpatialIndexShape}. Implementations (typically
 * payload-carrying {@code record}s) only provide {@link #coord()}, {@link #getAngle()},
 * {@link #getLength()} and {@link #getWidth()}; every geometric test is defaulted off those —
 * mirroring how {@link SpatialIndexCircle} derives everything from center and radius.
 *
 * <p>Exists so a shape that is long and thin along an arbitrary bearing indexes by its true
 * footprint rather than by an axis-aligned box of several times the area.
 *
 * <p>Point tests run in the rectangle's local frame — rotating the query by {@code -angle} about the
 * center reduces them to the axis-aligned case; box tests use the separating-axis theorem over the
 * two world axes and the two local axes.
 */
public interface SpatialIndexRotatedRectangle extends SpatialIndexShape {

    /** The rectangle's center. May return the backing array — callers must treat it as read-only. */
    double[] coord();

    /** Rotation of the local +X axis, radians, counter-clockwise in the X/Z plane. */
    double getAngle();

    /** Full extent along the local +X axis — the direction {@link #getAngle()} points. */
    double getLength();

    /** Full extent across the local +Z axis, perpendicular to {@link #getLength()}. */
    double getWidth();

    /** Override alongside {@link #getSinAngle()} to cache the rotation when queries run hot. */
    default double getCosAngle() {
        return Math.cos(getAngle());
    }

    /** Override alongside {@link #getCosAngle()} to cache the rotation when queries run hot. */
    default double getSinAngle() {
        return Math.sin(getAngle());
    }

    /** Separating-axis test over the two world axes and the two local axes. */
    @Override
    default boolean intersects(double[] lowerCorner, double[] upperCorner) {
        final double[] center = coord();
        final double cosAngle = getCosAngle();
        final double sinAngle = getSinAngle();
        final double halfLength = getLength() * 0.5;
        final double halfWidth = getWidth() * 0.5;
        final double absCos = Math.abs(cosAngle);
        final double absSin = Math.abs(sinAngle);

        final double worldRadiusX = halfLength * absCos + halfWidth * absSin;
        if (center[0] + worldRadiusX < lowerCorner[0] || upperCorner[0] < center[0] - worldRadiusX) {
            return false;
        }
        final double worldRadiusZ = halfLength * absSin + halfWidth * absCos;
        if (center[1] + worldRadiusZ < lowerCorner[1] || upperCorner[1] < center[1] - worldRadiusZ) {
            return false;
        }

        final double boxHalfX = (upperCorner[0] - lowerCorner[0]) * 0.5;
        final double boxHalfZ = (upperCorner[1] - lowerCorner[1]) * 0.5;
        final double deltaX = (lowerCorner[0] + upperCorner[0]) * 0.5 - center[0];
        final double deltaZ = (lowerCorner[1] + upperCorner[1]) * 0.5 - center[1];
        final double localX = deltaX * cosAngle + deltaZ * sinAngle;
        if (Math.abs(localX) > halfLength + boxHalfX * absCos + boxHalfZ * absSin) {
            return false;
        }
        final double localZ = deltaZ * cosAngle - deltaX * sinAngle;
        return Math.abs(localZ) <= halfWidth + boxHalfX * absSin + boxHalfZ * absCos;
    }

    /** Convex, so containing all four box corners is the whole test. */
    @Override
    default boolean contains(double[] lowerCorner, double[] upperCorner) {
        final double[] center = coord();
        final double cosAngle = getCosAngle();
        final double sinAngle = getSinAngle();
        final double halfLength = getLength() * 0.5;
        final double halfWidth = getWidth() * 0.5;
        return containsLocal(
                        lowerCorner[0] - center[0],
                        lowerCorner[1] - center[1],
                        cosAngle,
                        sinAngle,
                        halfLength,
                        halfWidth)
                && containsLocal(
                        upperCorner[0] - center[0],
                        lowerCorner[1] - center[1],
                        cosAngle,
                        sinAngle,
                        halfLength,
                        halfWidth)
                && containsLocal(
                        lowerCorner[0] - center[0],
                        upperCorner[1] - center[1],
                        cosAngle,
                        sinAngle,
                        halfLength,
                        halfWidth)
                && containsLocal(
                        upperCorner[0] - center[0],
                        upperCorner[1] - center[1],
                        cosAngle,
                        sinAngle,
                        halfLength,
                        halfWidth);
    }

    @Override
    default boolean containsPoint(double[] queryPoint) {
        final double[] center = coord();
        return containsLocal(
                queryPoint[0] - center[0],
                queryPoint[1] - center[1],
                getCosAngle(),
                getSinAngle(),
                getLength() * 0.5,
                getWidth() * 0.5);
    }

    /** Clamp-to-rectangle nearest-point test in the local frame; rotation preserves the distance. */
    @Override
    default boolean containsPointInflated(double[] queryPoint, double inflateRadius) {
        final double[] center = coord();
        final double cosAngle = getCosAngle();
        final double sinAngle = getSinAngle();
        final double deltaX = queryPoint[0] - center[0];
        final double deltaZ = queryPoint[1] - center[1];
        final double localX = deltaX * cosAngle + deltaZ * sinAngle;
        final double localZ = deltaZ * cosAngle - deltaX * sinAngle;
        final double halfLength = getLength() * 0.5;
        final double halfWidth = getWidth() * 0.5;
        final double outsideX = localX - Math.clamp(localX, -halfLength, halfLength);
        final double outsideZ = localZ - Math.clamp(localZ, -halfWidth, halfWidth);
        return outsideX * outsideX + outsideZ * outsideZ <= inflateRadius * inflateRadius;
    }

    @Override
    default void writeMbrInto(double[] mbrLowerCornerOut, double[] mbrUpperCornerOut) {
        final double[] center = coord();
        final double absCos = Math.abs(getCosAngle());
        final double absSin = Math.abs(getSinAngle());
        final double halfLength = getLength() * 0.5;
        final double halfWidth = getWidth() * 0.5;
        final double worldRadiusX = halfLength * absCos + halfWidth * absSin;
        final double worldRadiusZ = halfLength * absSin + halfWidth * absCos;
        mbrLowerCornerOut[0] = center[0] - worldRadiusX;
        mbrLowerCornerOut[1] = center[1] - worldRadiusZ;
        mbrUpperCornerOut[0] = center[0] + worldRadiusX;
        mbrUpperCornerOut[1] = center[1] + worldRadiusZ;
    }

    /** Takes the rotation pre-resolved so a four-corner test pays for one {@code sin}/{@code cos}. */
    private static boolean containsLocal(
            double deltaX, double deltaZ, double cosAngle, double sinAngle, double halfLength, double halfWidth) {
        final double localX = deltaX * cosAngle + deltaZ * sinAngle;
        final double localZ = deltaZ * cosAngle - deltaX * sinAngle;
        return Math.abs(localX) <= halfLength && Math.abs(localZ) <= halfWidth;
    }
}
