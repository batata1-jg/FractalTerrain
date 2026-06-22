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
 * world coordinate plus the local channel {@code width}, bed {@code elevation}, and the simulation
 * {@code time} (meander step) at which it was recorded.
 *
 * <p>It deliberately carries no channel id or in-channel index — features are resampled before
 * conversion, so per-point identity is not needed downstream.
 *
 * <p>{@link RosgenType} is inert for now: it is stored but no behaviour keys off it yet.
 */
public record HydrologicalUnit(
        HydrologicalFeature type, RosgenType rosgenType, List<Double> coord, double width, double elevation, int time)
        implements QuadTreePoint, Persistable<HydrologicalUnit> {

    /** Dummy point that makes the unit tree serializable (probed once by Storage). */
    public static final HydrologicalUnit PROTOTYPE =
            new HydrologicalUnit(HydrologicalFeature.RIVER, null, List.of(0.0, 0.0), 0, 0, 0);

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
        // type ordinal + rosgen ordinal + coord count + (coord doubles) + width + elevation + time
        return 4L + 4L + 4L + (long) coord.size() * Double.BYTES + Double.BYTES + Double.BYTES + 4L;
    }

    @Override
    public byte[] serialize() {
        final int bytes = (int) byteSize();
        final ByteBuffer buf = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(type.ordinal());
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        buf.putInt(coord.size());
        for (double c : coord) buf.putDouble(c);
        buf.putDouble(width);
        buf.putDouble(elevation);
        buf.putInt(time);
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
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        final int t = buf.getInt();
        return new HydrologicalUnit(featureType, rosgen, coords, w, e, t);
    }
}
