package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Supplier;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One sample of a hydrological feature, and the unit the whole carve pipeline queries against.
 *
 * <p>Exists so heterogeneous feature types share one index and one persistence payload: every unit is
 * indexed as an influence circle, and the type tag lets one {@code Storage} payload round-trip them all.
 *
 * <p>Carve behavior is split so the carve stages never switch on the concrete record type: {@link
 * #getProfile()} answers where and how much, {@link #carveFineGrained} answers what. Implementations
 * must override {@code equals}/{@code hashCode} — see {@link UnitCodec#coordsEqual}.
 */
public interface HydrologicalUnit extends SpatialIndexPoint, SpatialIndexCircle, Persistable<HydrologicalUnit> {

    /** Probe {@code Storage} uses to decide the index is persistable; any unit type would serve. */
    HydrologicalUnit PROTOTYPE = RiverUnit.PROTOTYPE;

    /** Deliberately small, so a feature with no profile yet barely perturbs the terrain. */
    double DEFAULT_RADIUS = 2.0;

    Logger LOG = LoggerFactory.getLogger(HydrologicalUnit.class);

    /** Which kind of feature this unit is; the tag {@link #serialize()} writes. */
    HydrologicalFeature getType();

    /**
     * Feature kinds and the registry mapping each to its record.
     *
     * <p><b>Append only.</b> A constant's ordinal is the on-disk type tag, so reordering or removing one
     * silently reinterprets every unit already cached.
     *
     * <p>A {@code null} prototype means the type can flow through the pipeline but not be persisted;
     * {@link #prototype()} throws rather than guessing, failing where the gap can be named.
     */
    enum HydrologicalFeature {
        RIVER(() -> RiverUnit.PROTOTYPE) {
            @Override
            public void addUnits(double[] offset, List<HydrologicalUnit> out, Object... args) {
                ChannelTyper typer = (ChannelTyper) args[0];
                Channel ch = (Channel) args[1];
                RiverUnit.RosgenType[] types = typer.typesFor(ch);
                for (int i = 0; i < ch.numPts(); i++) {
                    final double width = ch.widthAt(i);
                    final RosgenProfile profile = RosgenProfile.of(types[i]);
                    final double[] coords = VectorOps.sub(ch.spline.sample(i), offset);
                    out.add(new RiverUnit(
                            coords,
                            profile.riverInfluence(width),
                            types[i],
                            ch.spline.normal(i),
                            width,
                            ch.bedElev(i)));
                }
            }
        },
        ABANDONED_RIVER(() -> AbandonedRiverUnit.PROTOTYPE) {
            @Override
            public void addUnits(double[] offset, List<HydrologicalUnit> units, Object... args) {}
        },
        OXBOW_LAKE(() -> OxbowLakeUnit.PROTOTYPE) {
            @Override
            public void addUnits(double[] offset, List<HydrologicalUnit> units, Object... args) {}
        },
        SOURCE(() -> SourceUnit.PROTOTYPE) {
            @Override
            public void addUnits(double[] offset, List<HydrologicalUnit> units, Object... args) {
                Endpoint endpoint = (Endpoint) args[0];
                units.add(new SourceUnit(VectorOps.sub(endpoint.coord, offset)));
            }
        },
        WATERFALL(() -> WaterfallUnit.PROTOTYPE) {
            @Override
            public void addUnits(double[] offset, List<HydrologicalUnit> units, Object... args) {}
        },
        DELTA(() -> DeltaUnit.PROTOTYPE) {
            @Override
            public void addUnits(double[] offset, List<HydrologicalUnit> units, Object... args) {}
        };

        /** {@code values()} without the defensive copy; indexed by the on-disk type tag. */
        private static final HydrologicalFeature[] VALUES = values();

        /** A supplier, because a direct instance would need to exist before this enum initialises. */
        private final @Nullable Supplier<HydrologicalUnit> prototypeSupplier;

        HydrologicalFeature(@Nullable Supplier<HydrologicalUnit> prototypeSupplier) {
            this.prototypeSupplier = prototypeSupplier;
        }

        /** The feature for an on-disk type tag. */
        static HydrologicalFeature fromTag(int tag) {
            if (tag < 0 || tag >= VALUES.length) {
                throw new IllegalArgumentException("unknown hydrological feature tag " + tag);
            }
            return VALUES[tag];
        }

        /** The prototype of the record implementing this feature. */
        public HydrologicalUnit prototype() {
            if (prototypeSupplier == null) {
                throw new IllegalStateException("no HydrologicalUnit record implements " + name() + " yet");
            }
            return prototypeSupplier.get();
        }

        public abstract void addUnits(double[] offset, List<HydrologicalUnit> units, Object... args);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    boolean equals(Object o);

    @Override
    int hashCode();

    /** The profile deciding this unit's carve zones and shell pull. */
    HydrologyProfile getProfile();

    /** The unit's own cross-section, layered onto what the shell carve already cut. Returning
     *  {@code elevAtPixel} unchanged means the unit adds no detail of its own. */
    double carveFineGrained(double[] pt, double elevAtPixel);

    @Override
    default double getRadius() {
        return DEFAULT_RADIUS;
    }

    double[] coord();

    /** Whether the point is inside open water. Defaults to no, so a wetted type opts in. */
    default boolean channelContains(double distSqFromCentre) {
        return false;
    }

    @Override
    default long byteSize() {
        return Integer.BYTES + unitByteSize();
    }

    /** Type tag + {@link #serializeUnit()} body. See the class javadoc. */
    @Override
    default byte[] serialize() throws UnsupportedOperationException {
        final byte[] body = serializeUnit();
        final ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(getType().ordinal());
        buf.put(body);
        return buf.array();
    }

    /** Reads the type tag, then rebuilds the body on that feature's prototype. See the class javadoc. */
    @Override
    default HydrologicalUnit deserialize(byte[] rawBytes) throws UnsupportedOperationException {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final HydrologicalFeature type = HydrologicalFeature.fromTag(buf.getInt());
        final byte[] body = new byte[rawBytes.length - Integer.BYTES];
        buf.get(body);
        return type.prototype().deserializeUnit(body);
    }

    long unitByteSize();

    /** This unit's payload, without the type tag {@link #serialize()} prepends. */
    byte[] serializeUnit();

    /** Rebuild a unit of this record's type from a {@link #serializeUnit()} body (no type tag). */
    HydrologicalUnit deserializeUnit(byte[] rawBytes);
}
