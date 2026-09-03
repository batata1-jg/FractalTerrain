package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import org.jetbrains.annotations.NotNull;

/**
 * The head of a channel — the spring or seep a river starts at.
 *
 * <p>Cuts a cone rather than the confluence's bowl, so a headwater reads as a notch the channel
 * emerges from instead of a pool. Sized by the width of the single channel leaving it, which is the
 * narrowest in its network, so it is the smallest thing the radial pass carves.
 *
 * <p>Note this is a point of the river it heads, not an independent feature: the network still stamps
 * {@link HydrologicalFeature#SOURCE} on the first point of a channel that begins at a source node.
 */
public record SourcePrimitive(double[] coord, double width, double elevation, long seed) implements RadialPrimitive {

    static final SourcePrimitive PROTOTYPE = new SourcePrimitive(new double[] {0.0, 0.0}, 0, 0);

    public SourcePrimitive(double[] coord, double width, double elevation) {
        this(coord, width, elevation, computeHashCode(coord, width, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.SOURCE;
    }

    @Override
    public RadialProfile getRadialProfile() {
        return RadialProfile.SOURCE;
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord) + 2L * Double.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        PrimitiveCodec.putCoord(buf, coord);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new SourcePrimitive(coords, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcePrimitive other)) return false;
        return Arrays.equals(coord, other.coord)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(double[] coord, double width, double elevation) {
        int result = Objects.hash(width, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "Source[coord=" + Arrays.toString(coord) + ", width=" + width + ", elevation=" + elevation + "]";
    }
}
