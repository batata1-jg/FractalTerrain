package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import org.jetbrains.annotations.NotNull;

/**
 * A meander loop cut off from its channel, carved with the same rectangle/tangent cross-section as
 * a live river — a shed loop reads close enough in shape to still be a channel.
 *
 * <p>{@link ZoneCategory#LAKE_BED} is reserved below {@link ZoneCategory#BED} for the standing-water
 * classification this record does not carry yet — {@code ZoneCategory} itself is not live (see its
 * own javadoc), so nothing currently reads that reservation.
 */
public record OxbowLakePrimitive(
        double[] coord,
        byte time,
        double width,
        double influence,
        double elevation,
        double[] normal,
        double curvature,
        RiverPrimitive.RosgenType rosgenType,
        long seed)
        implements RosgenCarvedPrimitive {

    static final OxbowLakePrimitive PROTOTYPE =
            new OxbowLakePrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0, 0, null, 0, null);

    public OxbowLakePrimitive(
            double[] coord,
            byte time,
            double width,
            double influence,
            double elevation,
            double[] normal,
            double curvature,
            RiverPrimitive.RosgenType rosgenType) {
        this(
                coord,
                time,
                width,
                influence,
                elevation,
                normal,
                curvature,
                rosgenType,
                computeHashCode(coord, time, width, influence, elevation, normal));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.OXBOW_LAKE;
    }

    /** This primitive with its deferred elevation and influence filled in — unknowable at the cut,
     *  resolved later once the network's bed-elevation pass runs. Not part of a shared interface:
     *  nothing outside tests calls it polymorphically. */
    public OxbowLakePrimitive resolved(double elevation, double influence) {
        return new OxbowLakePrimitive(coord, time, width, influence, elevation, normal, curvature, rosgenType);
    }

    @Override
    public double getAngle() {
        throw new IllegalStateException("OxbowLakePrimitive uses angle cosines and sines directly");
    }

    /** Local +X is the flow tangent {@code (nz, -nx)}, mirroring {@link RiverPrimitive}. */
    @Override
    public double getCosAngle() {
        return normal[1];
    }

    @Override
    public double getSinAngle() {
        return -normal[0];
    }

    @Override
    public double getLength() {
        return influence * 2;
    }

    @Override
    public double getWidth() {
        return influence * 3;
    }

    @Override
    public long primitiveByteSize() {
        return Integer.BYTES // rosgen tag
                + PrimitiveCodec.coordByteSize(coord)
                + Byte.BYTES // time
                + 3L * Double.BYTES // width, influence, elevation
                + PrimitiveCodec.coordByteSize(normal)
                + Double.BYTES; // curvature
    }

    // :SCHEMA: this record's serialized layout; no OxbowLakePrimitive payload of this format has ever
    // been written to a cached tile, so a format change here needs no migration.
    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        PrimitiveCodec.putCoord(buf, coord);
        buf.put(time);
        buf.putDouble(width);
        buf.putDouble(influence);
        buf.putDouble(elevation);
        PrimitiveCodec.putCoord(buf, normal);
        buf.putDouble(curvature);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int rosgenOrdinal = buf.getInt();
        final RiverPrimitive.RosgenType rosgen =
                rosgenOrdinal < 0 ? null : RiverPrimitive.RosgenType.values()[rosgenOrdinal];
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final byte t = buf.get();
        final double w = buf.getDouble();
        final double inf = buf.getDouble();
        final double e = buf.getDouble();
        final double[] normalVec = PrimitiveCodec.getCoord(buf);
        final double curv = buf.getDouble();
        return new OxbowLakePrimitive(coords, t, w, inf, e, normalVec, curv, rosgen);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OxbowLakePrimitive other)) return false;
        return time == other.time
                && rosgenType == other.rosgenType
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(normal, other.normal)
                && Double.compare(width, other.width) == 0
                && Double.compare(influence, other.influence) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(
            double[] coord, byte time, double width, double influence, double elevation, double[] normal) {
        int result = Objects.hash(time, width, influence, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "Oxbow[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", influence="
                + influence + ", elevation=" + elevation + ", normal=" + Arrays.toString(normal) + "]";
    }
}
