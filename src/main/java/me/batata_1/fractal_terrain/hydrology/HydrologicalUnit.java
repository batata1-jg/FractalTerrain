package me.batata_1.fractal_terrain.hydrology;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.storage.Persistable;

/**
 * A single point of a hydrological feature — a river (with its Rosgen channel type), an abandoned
 * river, or an oxbow lake. It is the queryable, persistable unit stored in
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider}'s tiled quadtree: it carries its
 * tile-local coordinate, the channel {@code normal} (unit perpendicular to the centreline at this
 * point, used to project query points across the channel), the local channel {@code width}, bed
 * {@code elevation}, the simulation {@code time} (meander step) at which it was recorded, and an
 * {@code id} grouping every point of the same feature.
 *
 * <p>The {@code id} groups the points that belong to one hydrological feature so the carve/paint
 * query can gather and merge them per feature. It is <em>not necessarily a channel id</em> — it is a
 * tile-unique feature key assigned at unit-assembly time (global rivers and the local network share a
 * single id space). {@code normal} may be {@code null} when no meaningful tangent exists.
 *
 * <p>{@code coord} and {@code normal} are primitive {@code double[]} (treated as immutable — never
 * mutate them after construction): {@link #getCoords()} returns the backing array directly, so tree
 * construction and every query point-scan pay no boxing or copying. Because records compare array
 * components by reference, {@link #equals} / {@link #hashCode} are overridden to compare contents.
 *
 * <p>{@link RosgenType} is inert for now: it is stored but no behaviour keys off it yet.
 */
public record HydrologicalUnit(
        HydrologicalFeature type,
        RosgenType rosgenType,
        double[] coord,
        double[] normal,
        double width,
        double elevation,
        int time,
        int id)
        implements QuadTreePoint, Persistable<HydrologicalUnit> {

    /** Dummy point that makes the unit tree serializable (probed once by Storage). */
    public static final HydrologicalUnit PROTOTYPE =
            new HydrologicalUnit(HydrologicalFeature.RIVER, null, new double[] {0.0, 0.0}, null, 0, 0, 0, 0);

    /** Kind of hydrological feature this point belongs to. */
    public enum HydrologicalFeature {
        RIVER,
        ABANDONED_RIVER,
        OXBOW_LAKE
    }

    /** Rosgen stream classification (A–D). Stored but unused for now. */
    public enum RosgenType {
        A,
        B,
        C,
        D
    }

    @Override
    public double[] getCoords() {
        return coord;
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HydrologicalUnit other)) return false;
        return type == other.type
                && rosgenType == other.rosgenType
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(normal, other.normal)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0
                && time == other.time
                && id == other.id;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, rosgenType, width, elevation, time, id);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }

    @Override
    public String toString() {
        return "HydrologicalUnit[type=" + type + ", rosgenType=" + rosgenType + ", coord="
                + Arrays.toString(coord) + ", normal=" + Arrays.toString(normal) + ", width=" + width
                + ", elevation=" + elevation + ", time=" + time + ", id=" + id + "]";
    }

    @Override
    public long byteSize() {
        // type + rosgen + coordCount + coord doubles + normalCount + normal doubles + width + elev + time + id
        final long normalDoubles = (normal == null ? 0L : (long) normal.length) * Double.BYTES;
        return 4L
                + 4L
                + 4L
                + (long) coord.length * Double.BYTES
                + 4L
                + normalDoubles
                + Double.BYTES
                + Double.BYTES
                + 4L
                + 4L;
    }

    @Override
    public byte[] serialize() {
        final int bytes = (int) byteSize();
        final ByteBuffer buf = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(type.ordinal());
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        buf.putInt(coord.length);
        for (double c : coord) buf.putDouble(c);
        // normal: count -1 marks a null normal, otherwise the doubles follow.
        buf.putInt(normal == null ? -1 : normal.length);
        if (normal != null) for (double c : normal) buf.putDouble(c);
        buf.putDouble(width);
        buf.putDouble(elevation);
        buf.putInt(time);
        buf.putInt(id);
        return buf.array();
    }

    @Override
    public HydrologicalUnit deserialize(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final HydrologicalFeature featureType = HydrologicalFeature.values()[buf.getInt()];
        final int rosgenOrdinal = buf.getInt();
        final RosgenType rosgen = rosgenOrdinal < 0 ? null : RosgenType.values()[rosgenOrdinal];
        final int coordCount = buf.getInt();
        final double[] coords = new double[coordCount];
        for (int i = 0; i < coordCount; i++) coords[i] = buf.getDouble();
        final int normalCount = buf.getInt();
        final double[] normalVec;
        if (normalCount < 0) {
            normalVec = null;
        } else {
            normalVec = new double[normalCount];
            for (int i = 0; i < normalCount; i++) normalVec[i] = buf.getDouble();
        }
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        final int t = buf.getInt();
        final int featureId = buf.getInt();
        return new HydrologicalUnit(featureType, rosgen, coords, normalVec, w, e, t, featureId);
    }
}
