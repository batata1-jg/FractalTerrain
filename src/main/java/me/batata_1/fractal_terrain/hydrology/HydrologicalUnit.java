package me.batata_1.fractal_terrain.hydrology;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.jetbrains.annotations.NotNull;

/**
 * A single sample of a hydrological feature — a river (with its Rosgen channel type), an abandoned
 * river, or an oxbow lake. It is the queryable, persistable unit stored in
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider}'s tiled {@code ImmutableRTree},
 * where it lives as an <b>influence circle</b> ({@link SpatialIndexCircle}): center = its tile-local
 * {@code coord}, radius = its own {@link FractalTerrainConfig#riverInfluence riverInfluence(width)} —
 * so a point-stabbing query returns exactly the units whose influence reaches the point. It carries
 * the channel {@code normal} (unit perpendicular to the centreline at this point, used to project
 * query points across the channel), the local channel {@code width}, the reference (bank) {@code
 * elevation} the tile-level shell carve lerps toward (see
 * {@link RosgenProfile#riverInfluenceElevation}), the simulation {@code time} (meander step) at which
 * it was recorded, and an {@code id} grouping every point of the same feature.
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
 * <p>{@link RosgenType} selects the {@link RosgenProfile} used by {@link #getRadius()} and by the
 * tile-level shell carve. A {@code null} rosgenType is treated as {@link RosgenType#A} at every such
 * site. Because only {@code A} overrides any profile method today, the choice is currently observable
 * only as "A vs. not-A".
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
        implements SpatialIndexPoint, SpatialIndexCircle, Persistable<HydrologicalUnit> {

    /** Dummy unit that makes the unit index serializable (probed once by Storage). */
    public static final HydrologicalUnit PROTOTYPE =
            new HydrologicalUnit(HydrologicalFeature.RIVER, null, new double[] {0.0, 0.0}, null, 0, 0, 0, 0);

    /** {@link SpatialIndexPoint} coordinate accessor — same backing array as {@link #getCenter()}, no copy. */
    @Override
    public double[] getCoords() {
        return coord;
    }

    /** Kind of hydrological feature this point belongs to. */
    public enum HydrologicalFeature {
        RIVER,
        ABANDONED_RIVER,
        OXBOW_LAKE
    }

    /** Rosgen stream classification (A–D); selects the unit's {@link RosgenProfile}. */
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

    @Override
    public double[] getCenter() {
        return coord;
    }

    /**
     * The unit's influence radius — <b>derived</b> from {@code width} via
     * {@link FractalTerrainConfig#riverInfluence}, never serialized (the on-disk unit layout is
     * unchanged). Sound for the channel-membership test ({@code HydrologyProfilePainter.insideChannel}
     * filters stab hits by {@code width/2}) because {@code riverInfluence(w) > w/2} for every
     * representable width; if widths ever exceed 128 native px, change this to
     * {@code max(riverInfluence(width), width * 0.5)} and keep the carve blend divisor on
     * {@code riverInfluence}.
     */
    @Override
    public double getRadius() {
        return RosgenProfile.of(rosgenType == null ? RosgenType.A : rosgenType).riverInfluence(width);
    }

    /**
     * Whether a query point at squared distance {@code distSqFromCentre} from this unit's centre lies
     * inside the channel's water span — i.e. within this unit's bed half-width
     * ({@link ChannelGeometry#bedHalfWidth}). The channel-membership test used by
     * {@link me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfilePainter#insideChannel}.
     */
    public boolean channelContains(double distSqFromCentre) {
        final double bedHalfWidth = ChannelGeometry.bedHalfWidth(width);
        return distSqFromCentre <= bedHalfWidth * bedHalfWidth;
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o
                instanceof
                HydrologicalUnit(
                        HydrologicalFeature type1,
                        RosgenType rosgenType1,
                        double[] coord1,
                        double[] normal1,
                        double width1,
                        double elevation1,
                        int time1,
                        int id1))) return false;
        return type == type1
                && rosgenType == rosgenType1
                && Arrays.equals(coord, coord1)
                && Arrays.equals(normal, normal1)
                && Double.compare(width, width1) == 0
                && Double.compare(elevation, elevation1) == 0
                && time == time1
                && id == id1;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, rosgenType, width, elevation, time, id);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }

    @Override
    public @NotNull String toString() {
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
