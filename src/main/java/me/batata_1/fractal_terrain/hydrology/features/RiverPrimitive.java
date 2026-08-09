package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import org.jetbrains.annotations.Nullable;

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
        double radius,
        RosgenType rosgenType,
        double @Nullable [] normal,
        double curvature,
        double width,
        double elevation)
        implements HydrologicalPrimitive {

    static final RiverPrimitive PROTOTYPE = new RiverPrimitive(new double[] {0.0, 0.0}, 0, null, null, 0, 0, 0);

    @Override
    public double[] getCoords() {
        return coord;
    }

    @Override
    public double[] getCenter() {
        return coord;
    }

    @Override
    public double getRadius() {
        return radius;
    }

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
        if (width <= 1.5) return -1;
        if (width <= 2.5) return -2;
        return -3;
    }

    @Override
    public HydrologyProfile getProfile() {
        return RosgenProfile.of(rosgenType == null ? RosgenType.A : rosgenType);
    }

    /** Cuts the primitive's cross-section, faded over an elliptical footprint so full strength lands across
     *  the floodplain but tapers along the channel. Neighbouring primitives' ellipses cover the stretch
     *  between them, which is why the {@code dx <= width/2} spacing matters. */
    @Override
    public double d(double[] pt) {
        if (normal == null) return radius;
        final double nx = normal[0], nz = normal[1];
        if (Math.abs(nx) < 1e-6 || Math.abs(nz) < 1e-6) return radius;
        final double dx = pt[0] - coord[0], dz = pt[1] - coord[1];
        return nx * dx + nz * dz;
    }

    @Override
    public double h(double[] pt, Object... args) {
        if (normal == null) return elevation;
        final RosgenProfile profile = (RosgenProfile) getProfile();
        return elevation + profile.delta(hashCode(),d(pt),width,curvature);
    }

    @Override
    public long primitiveByteSize() {
        // rosgen tag + coord + normal + radius + width + elevation
        return Integer.BYTES
                + PrimitiveCodec.coordByteSize(coord)
                + PrimitiveCodec.coordByteSize(normal)
                + 4L * Double.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        // An unclassified reach stamps -1; every other value is a RosgenType ordinal.
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        PrimitiveCodec.putCoord(buf, coord);
        PrimitiveCodec.putCoord(buf, normal);
        buf.putDouble(curvature);
        buf.putDouble(radius);
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
        return new RiverPrimitive(coords, r, rosgen, normalVec, curvature, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiverPrimitive other)) return false;
        return rosgenType == other.rosgenType
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(normal, other.normal)
                && Double.compare(radius, other.radius) == 0
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(rosgenType, radius, width, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }

    @Override
    public String toString() {
        return "River[coord=" + Arrays.toString(coord) + ", radius=" + radius + ", rosgenType=" + rosgenType
                + ", normal=" + Arrays.toString(normal) + ", width=" + width + ", elevation=" + elevation + "]";
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
        G
    }
}
