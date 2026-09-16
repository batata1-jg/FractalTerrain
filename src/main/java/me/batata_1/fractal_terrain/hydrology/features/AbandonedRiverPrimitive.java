package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import org.jetbrains.annotations.NotNull;

/**
 * A former channel the river has since migrated out of — carved radially, like a confluence pool
 * left to silt in, rather than reusing river-style banding: unlike {@link OxbowLakePrimitive}, it is
 * minted with no {@link me.batata_1.fractal_terrain.hydrology.network.Channel} in scope (see
 * {@code RiverNetwork}'s eviction path), so no flow tangent is available to band against.
 */
public record AbandonedRiverPrimitive(double[] coord, byte time, double width, double elevation, long seed)
        implements RadialPrimitive {

    static final AbandonedRiverPrimitive PROTOTYPE =
            new AbandonedRiverPrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0);

    public AbandonedRiverPrimitive(double[] coord, byte time, double width, double elevation) {
        this(coord, time, width, elevation, computeHashCode(coord, time, width, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.ABANDONED_RIVER;
    }

    @Override
    public RadialProfile getRadialProfile() {
        return RadialProfile.ABANDONED_RIVER;
    }

    @Override
    public InfluenceCarver getInfluenceCarver() {
        return InfluenceCarver.RADIAL;
    }

    /** This primitive with its deferred elevation filled in — unknowable at the cut, resolved later
     *  once the network's bed-elevation pass runs. Radius is not deferred: width, and so
     *  {@link RadialPrimitive#getRadius()}, is already known when the trace is cut. */
    public AbandonedRiverPrimitive resolved(double elevation) {
        return new AbandonedRiverPrimitive(coord, time, width, elevation);
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord) + Byte.BYTES + 2L * Double.BYTES;
    }

    // :SCHEMA: this record's serialized layout; no AbandonedRiverPrimitive payload of this format has
    // ever been written to a cached tile, so a format change here needs no migration.
    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        PrimitiveCodec.putCoord(buf, coord);
        buf.put(time);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final byte t = buf.get();
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new AbandonedRiverPrimitive(coords, t, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbandonedRiverPrimitive other)) return false;
        return time == other.time
                && Arrays.equals(coord, other.coord)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(double[] coord, byte time, double width, double elevation) {
        int result = Objects.hash(time, width, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "Abandoned[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", elevation="
                + elevation + "]";
    }
}
