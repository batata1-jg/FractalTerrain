package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Shared byte-level plumbing for {@link HydrologicalPrimitive} records — the pieces every feature type
 * repeats, factored out so a position-only record is a handful of one-line overrides.
 *
 * <p>All layouts are little-endian and length-prefixed, matching the format
 * {@link HydrologicalPrimitive#serialize()} wraps with a type tag. Nothing here writes or reads that tag.
 */
final class PrimitiveCodec {

    private PrimitiveCodec() {}

    /** Marks a {@code null} array where a length would otherwise be. */
    static final int NULL_LENGTH = -1;

    /** Byte cost of a length-prefixed coordinate array. */
    static long coordByteSize(double[] coord) {
        return Integer.BYTES + (long) (coord == null ? 0 : coord.length) * Double.BYTES;
    }

    /** Serialized form of a position-only primitive: its length-prefixed coordinate array and nothing else. */
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

    /** Content equality for a position-only primitive. Exists because records compare {@code double[]} by
     *  reference, which would make every primitive unequal to its own reloaded copy. */
    static boolean coordsEqual(HydrologicalPrimitive self, Object other, double[] coord) {
        if (self == other) return true;
        if (other == null || self.getClass() != other.getClass()) return false;
        return Arrays.equals(coord, ((HydrologicalPrimitive) other).coord());
    }

    /** The {@link #coordsEqual} counterpart: a hash over the coordinate contents. */
    static int coordsHash(double[] coord) {
        return Arrays.hashCode(coord);
    }

    /** A {@link #writeHistoric} payload read back whole, so a record's deserialize stays one statement. */
    record HistoricFields(double[] coord, byte time, double width, double influence, double elevation) {}

    /** Byte cost of a shed feature's body: the coordinate, the cut step, then width/influence/elevation. */
    static long historicByteSize(double[] coord) {
        return coordByteSize(coord) + Byte.BYTES + 3L * Double.BYTES;
    }

    // :SCHEMA: the shed families' body carries time/width/influence/elevation where the other
    // position-only families carry a coordinate alone; only these two are ever written with it, and
    // none has ever been written to a cached tile, so no existing payload can be misread.
    /** Serialized form of a shed feature. {@code seed} is derived from these, so it is never written. */
    static byte[] writeHistoric(double[] coord, byte time, double width, double influence, double elevation) {
        final ByteBuffer buf =
                ByteBuffer.allocate((int) historicByteSize(coord)).order(ByteOrder.LITTLE_ENDIAN);
        putCoord(buf, coord);
        buf.put(time);
        buf.putDouble(width);
        buf.putDouble(influence);
        buf.putDouble(elevation);
        return buf.array();
    }

    /** Reads back a {@link #writeHistoric} payload. */
    static HistoricFields readHistoric(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coord = getCoord(buf);
        final byte time = buf.get();
        final double width = buf.getDouble();
        final double influence = buf.getDouble();
        final double elevation = buf.getDouble();
        return new HistoricFields(coord, time, width, influence, elevation);
    }

    /** Content equality for a shed feature. Exists because records compare {@code double[]} by reference,
     *  which would make every primitive unequal to its own reloaded copy. */
    static boolean historicEquals(HistoricPrimitive self, Object other) {
        if (self == other) return true;
        if (other == null || self.getClass() != other.getClass()) return false;
        final HistoricPrimitive that = (HistoricPrimitive) other;
        return Arrays.equals(self.coord(), that.coord())
                && self.time() == that.time()
                && Double.compare(self.width(), that.width()) == 0
                && Double.compare(self.influence(), that.influence()) == 0
                && Double.compare(self.elevation(), that.elevation()) == 0;
    }

    /** The {@link #historicEquals} counterpart, cached in the record's {@code seed} component. */
    static long historicHash(double[] coord, byte time, double width, double influence, double elevation) {
        int result = Objects.hash(time, width, influence, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        return result;
    }
}
