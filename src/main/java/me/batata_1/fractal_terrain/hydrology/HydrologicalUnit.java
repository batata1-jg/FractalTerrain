package me.batata_1.fractal_terrain.hydrology;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
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
 * <p>{@link RosgenType} is inert for now: it is stored but no behaviour keys off it yet.
 */
public record HydrologicalUnit(
        HydrologicalFeature type,
        RosgenType rosgenType,
        List<Double> coord,
        List<Double> normal,
        double width,
        double elevation,
        int time,
        int id)
        implements QuadTreePoint, Persistable<HydrologicalUnit> {

    /** Dummy point that makes the unit tree serializable (probed once by Storage). */
    public static final HydrologicalUnit PROTOTYPE =
            new HydrologicalUnit(HydrologicalFeature.RIVER, null, List.of(0.0, 0.0), null, 0, 0, 0, 0);

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
        final double[] out = new double[coord.size()];
        for (int i = 0; i < out.length; i++) out[i] = coord.get(i);
        return out;
    }

    @Override
    public long byteSize() {
        // type + rosgen + coordCount + coord doubles + normalCount + normal doubles + width + elev + time + id
        final long normalDoubles = (normal == null ? 0L : (long) normal.size()) * Double.BYTES;
        return 4L
                + 4L
                + 4L
                + (long) coord.size() * Double.BYTES
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
        buf.putInt(coord.size());
        for (double c : coord) buf.putDouble(c);
        // normal: count -1 marks a null normal, otherwise the doubles follow.
        buf.putInt(normal == null ? -1 : normal.size());
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
        final List<Double> coords = new ArrayList<>(coordCount);
        for (int i = 0; i < coordCount; i++) coords.add(buf.getDouble());
        final int normalCount = buf.getInt();
        final List<Double> normalVec;
        if (normalCount < 0) {
            normalVec = null;
        } else {
            normalVec = new ArrayList<>(normalCount);
            for (int i = 0; i < normalCount; i++) normalVec.add(buf.getDouble());
        }
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        final int t = buf.getInt();
        final int featureId = buf.getInt();
        return new HydrologicalUnit(featureType, rosgen, coords, normalVec, w, e, t, featureId);
    }
}
