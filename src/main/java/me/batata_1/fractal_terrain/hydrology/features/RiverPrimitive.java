package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexRotatedRectangle;

/**
 * One sample of a flowing channel — the reference {@link HydrologicalPrimitive}, the only type with a full
 * cross-section.
 *
 * <p>{@code coord} and {@code normal} are handed by reference (no boxing); treat both as immutable. A
 * null {@code normal} means no tangent — the primitive averages into zones but cuts no cross-section.
 *
 * <p>A null {@code rosgenType} means never classified (spring/mouth, nothing to measure) and coalesces
 * to {@link RosgenType#A} so the primitive still carves.
 */
public record RiverPrimitive(
        double[] coord,
        double influence,
        RosgenType rosgenType,
        double[] normal,
        double curvature,
        double width,
        double elevation,
        long seed)
        implements SpatialIndexRotatedRectangle, HydrologicalPrimitive {

    static final RiverPrimitive PROTOTYPE = new RiverPrimitive(new double[] {0.0, 0.0}, 0, null, null, 0, 0, 0,0);

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.RIVER;
    }

    /** Channel-membership test driving {@code HydrologyProfilePainter.insideChannel}. */
    @Override
    public boolean channelContains(double distSqFromCentre) {
        final double bedHalfWidth = ChannelGeometry.bedHalfWidth(width);
        return distSqFromCentre <= bedHalfWidth * bedHalfWidth;
    }

    @Override
    public float waterLine() {
        return HydrologicalPrimitive.waterLine(width);
    }

    @Override
    public HydrologyProfile getProfile() {
        return RosgenProfile.of(RosgenType.orDefault(rosgenType));
    }

    @Override
    public double d(double[] pt) {
        final double nx = normal[0], nz = normal[1];
        final double dx = pt[0] - coord[0], dz = pt[1] - coord[1];
        return nx * dx + nz * dz;
    }

    public double h(double signedDist) {
        if (normal == null) return elevation;
        final RosgenProfile profile = (RosgenProfile) getProfile();
        return elevation + profile.delta(seed, signedDist, width, curvature);
    }

    @Override
    public double w(double[] pt) {
        final double dx = pt[0] - coord[0], dz = pt[1] - coord[1];
        final double nx = normal[0], nz = normal[1];
        final double length = getLength();
        final double width = getWidth();
        final double distanceWidth = d(pt);
        final double distanceLength = (dx * nz - dz * nx);
        if (Math.abs(distanceWidth) >= width || Math.abs(distanceLength) >= length) return 0;
        final double weightWidth = Math.pow(1 - (distanceWidth * distanceWidth) / (width * width), 2);
        final double weightLength = Math.pow(1 - (distanceLength * distanceLength) / (length * length), 2);
        return Math.pow(Math.clamp(smoothMin(weightLength, weightWidth, 0.02), 0, 1), 2);
    }

    public static double smoothMin(double a, double b, double lambda) {
        return (a + b - Math.sqrt((a - b) * (a - b) + lambda)) / 2;
    }

    @Override
    public long primitiveByteSize() {
        // rosgen tag + coord + normal + radius + width + elevation
        return Integer.BYTES
                + PrimitiveCodec.coordByteSize(coord)
                + PrimitiveCodec.coordByteSize(normal)
                + 4L * Double.BYTES
                + Long.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        // An unclassified reach stamps -1; every other value is a RosgenType ordinal.
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        PrimitiveCodec.putCoord(buf, coord);
        PrimitiveCodec.putCoord(buf, normal);
        buf.putDouble(curvature);
        buf.putDouble(influence);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int rosgenOrdinal = buf.getInt();
        final RosgenType rosgen = rosgenOrdinal < 0 ? null : RosgenType.values()[rosgenOrdinal];
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final double[] normalVec = PrimitiveCodec.getCoord(buf);
        final double curvature = buf.getDouble();
        final double r = buf.getDouble();
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new RiverPrimitive(coords, r, rosgen, normalVec, curvature, w, e, computeHashCode(rosgen,w,e,coords,normalVec));
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiverPrimitive other)) return false;
        return rosgenType == other.rosgenType
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(normal, other.normal)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(RosgenType rosgenType,double width, double elevation ,double[] coord ,double[] normal) {
        int result = Objects.hash(rosgenType, width, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }


    @Override
    public String toString() {
        return "River[coord=" + Arrays.toString(coord) + ", influence=" + influence + ", rosgenType=" + rosgenType
                + ", normal=" + Arrays.toString(normal) + ", width=" + width + ", elevation=" + elevation + "]";
    }

    @Override
    public double getAngle() {
        throw new IllegalStateException("River Primitive uses angle cosines and sines directly");
    }

    /** Local +X is the channel tangent {@code (nz, -nx)}, so {@link #getLength} runs along the flow. */
    public double getCosAngle() {
        return normal[1];
    }

    /** Negated: the tangent is {@code normal} rotated -90 degrees, and dropping the sign mirrors the
     *  footprint rather than rotating it, throwing every inherited box test off by twice the bearing. */
    public double getSinAngle() {
        return -normal[0];
    }

    @Override
    public double getLength() {
        return influence * 2;
    }

    /** refers to the width of the river primitive, NOT the accutal river width */
    @Override
    public double getWidth() {
        return influence * 2;
    }

    /** Rosgen stream classification (A–G); selects the primitive's {@link RosgenProfile}. */
    public enum RosgenType {
        A,
        Aa,
        B,
        C,
        D,
        DA,
        E,
        F,
        G;

        /** What an unclassified reach carves as. A domain rule, not a caller's choice — a reach the
         *  Rosgen key could not type still has to be given a profile, and A is the conservative one. */
        public static RosgenType orDefault(RosgenType type) {
            return type == null ? A : type;
        }
    }
}
