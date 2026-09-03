package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import org.jetbrains.annotations.NotNull;

/**
 * The pool where two or more channels merge — one disc per {@code JUNCTION} endpoint of degree three
 * or more.
 *
 * <p>Sized by the widest channel meeting at the node, so a trunk's junction reads larger than a
 * headwater's. Carved by the radial pass of {@code RiverInfluenceCarve.computeRiverGrid} after every
 * river, which is what lets it deepen a bed the converging channels already cut rather than fight them.
 *
 * <p>Unrelated to the junction ray-set of the same name removed before {@code df7ca2e}; see
 * {@code ARCHITECTURE.md}'s superseded-designs paragraph.
 */
public record ConfluencePrimitive(double[] coord, double width, double elevation, long seed)
        implements RadialPrimitive {

    static final ConfluencePrimitive PROTOTYPE = new ConfluencePrimitive(new double[] {0.0, 0.0}, 0, 0);

    public ConfluencePrimitive(double[] coord, double width, double elevation) {
        this(coord, width, elevation, computeHashCode(coord, width, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.CONFLUENCE;
    }

    @Override
    public RadialProfile getRadialProfile() {
        return RadialProfile.CONFLUENCE;
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
        return new ConfluencePrimitive(coords, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfluencePrimitive other)) return false;
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
        return "Confluence[coord=" + Arrays.toString(coord) + ", width=" + width + ", elevation=" + elevation + "]";
    }
}
