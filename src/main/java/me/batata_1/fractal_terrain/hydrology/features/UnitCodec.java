package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Shared byte-level plumbing for {@link HydrologicalUnit} records — the pieces every feature type
 * repeats, factored out so a position-only record is a handful of one-line overrides.
 *
 * <p>All layouts are little-endian and length-prefixed, matching the format
 * {@link HydrologicalUnit#serialize()} wraps with a type tag. Nothing here writes or reads that tag.
 */
final class UnitCodec {

    private UnitCodec() {}

    /** Marks a {@code null} array where a length would otherwise be. */
    static final int NULL_LENGTH = -1;

    /** Byte cost of a length-prefixed coordinate array. */
    static long coordByteSize(double[] coord) {
        return Integer.BYTES + (long) (coord == null ? 0 : coord.length) * Double.BYTES;
    }

    /** Serialized form of a position-only unit: its length-prefixed coordinate array and nothing else. */
    static byte[] writeCoord(double[] coord) {
        final ByteBuffer buf = ByteBuffer.allocate((int) coordByteSize(coord)).order(ByteOrder.LITTLE_ENDIAN);
        putCoord(buf, coord);
        return buf.array();
    }

    /** Reads back a {@link #writeCoord} payload. */
    static double[] readCoord(byte[] rawBytes) {
        return getCoord(ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN));
    }

    /** Appends a length-prefixed coordinate array; a {@code null} array writes {@link #NULL_LENGTH}. */
    static void putCoord(ByteBuffer buf, double[] coord) {
        if (coord == null) {
            buf.putInt(NULL_LENGTH);
            return;
        }
        buf.putInt(coord.length);
        for (final double c : coord) buf.putDouble(c);
    }

    /** Reads a length-prefixed coordinate array written by {@link #putCoord}; may return {@code null}. */
    static double[] getCoord(ByteBuffer buf) {
        final int length = buf.getInt();
        if (length == NULL_LENGTH) return null;
        final double[] coord = new double[length];
        for (int i = 0; i < length; i++) coord[i] = buf.getDouble();
        return coord;
    }

    /**
     * Content equality for a position-only unit: same concrete record type and same coordinates. Records
     * compare {@code double[]} components by reference, so every unit record must route its
     * {@code equals} through a contents comparison like this one.
     */
    static boolean coordsEqual(HydrologicalUnit self, Object other, double[] coord) {
        if (self == other) return true;
        if (other == null || self.getClass() != other.getClass()) return false;
        return Arrays.equals(coord, ((HydrologicalUnit) other).getCoords());
    }

    /** The {@link #coordsEqual} counterpart: a hash over the coordinate contents. */
    static int coordsHash(double[] coord) {
        return Arrays.hashCode(coord);
    }
}
