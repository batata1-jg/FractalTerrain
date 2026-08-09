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
 * One sample of a hydrological feature, and the primitive the whole carve pipeline queries against.
 *
 * <p>Exists so heterogeneous feature types share one index and one persistence payload: every primitive is
 * indexed as an influence circle, and the type tag lets one {@code Storage} payload round-trip them all.
 *
 * <p>Carve behavior is split so the carve stages never switch on the concrete record type: {@link
 * #getProfile()} answers where and how much, {@link #h} answers what. Implementations
 * must override {@code equals}/{@code hashCode} — see {@link PrimitiveCodec#coordsEqual}.
 */
public interface HydrologicalPrimitive
        extends SpatialIndexPoint, SpatialIndexCircle, Persistable<HydrologicalPrimitive> {

    /** Probe {@code Storage} uses to decide the index is persistable; any primitive type would serve. */
    HydrologicalPrimitive PROTOTYPE = RiverPrimitive.PROTOTYPE;

    /** Deliberately small, so a feature with no profile yet barely perturbs the terrain. */
    double DEFAULT_RADIUS = 2.0;

    Logger LOG = LoggerFactory.getLogger(HydrologicalPrimitive.class);

    /** Which kind of feature this primitive is; the tag {@link #serialize()} writes. */
    HydrologicalFeature getType();

    default float waterLine() {
        return -1;
    }
    ;

    /**
     * Feature kinds and the registry mapping each to its record.
     *
     * <p><b>Append only.</b> A constant's ordinal is the on-disk type tag, so reordering or removing one
     * silently reinterprets every primitive already cached.
     *
     * <p>A {@code null} prototype means the type can flow through the pipeline but not be persisted;
     * {@link #prototype()} throws rather than guessing, failing where the gap can be named.
     */
    enum HydrologicalFeature {
        RIVER(() -> RiverPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> out, Object... args) {
                ChannelTyper typer = (ChannelTyper) args[0];
                Channel ch = (Channel) args[1];
                RiverPrimitive.RosgenType[] types = typer.typesFor(ch);
                for (int i = 0; i < ch.numPts(); i++) {
                    final double width = ch.widthAt(i);
                    final RosgenProfile profile = RosgenProfile.of(types[i]);
                    final double[] coords = VectorOps.sub(ch.spline.sample(i), offset);
                    out.add(new RiverPrimitive(
                            coords,
                            profile.riverInfluence(width),
                            types[i],
                            ch.spline.normal(i),
                            ch.spline.curvature(i),
                            width,
                            ch.bedElev(i)));
                }
            }
        },
        ABANDONED_RIVER(() -> AbandonedRiverPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        },
        OXBOW_LAKE(() -> OxbowLakePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        },
        SOURCE(() -> SourcePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                Endpoint endpoint = (Endpoint) args[0];
                primitives.add(new SourcePrimitive(VectorOps.sub(endpoint.coord, offset)));
            }
        },
        WATERFALL(() -> WaterfallPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        },
        DELTA(() -> DeltaPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        };

        /** {@code values()} without the defensive copy; indexed by the on-disk type tag. */
        private static final HydrologicalFeature[] VALUES = values();

        /** A supplier, because a direct instance would need to exist before this enum initialises. */
        private final @Nullable Supplier<HydrologicalPrimitive> prototypeSupplier;

        HydrologicalFeature(@Nullable Supplier<HydrologicalPrimitive> prototypeSupplier) {
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
        public HydrologicalPrimitive prototype() {
            if (prototypeSupplier == null) {
                throw new IllegalStateException("no HydrologicalPrimitive record implements " + name() + " yet");
            }
            return prototypeSupplier.get();
        }

        public abstract void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    boolean equals(Object o);

    @Override
    int hashCode();

    /** The profile deciding this primitive's carve zones and shell pull. */
    HydrologyProfile getProfile();

    /** The primitive's own cross-section, layered onto what the shell carve already cut. Returning
     *  {@code elevAtPixel} unchanged means the primitive adds no detail of its own. */
    default double h(double[] pt, Object... args) {
        return 0;
    }

    default double w(double[] pt, Object... args) {
            final double r = getRadius();
            final double d = d(pt);
            if(d<1) return 1;
//            if(d>=r) return 0;
            return Math.pow(Math.max(0,r+1-Math.abs(d)) /((r+1)*d) ,2);
    }

    default double d(double[] pt) {
        return Math.hypot(pt[0]-coord()[0], pt[1]-coord()[1]);
    }

    @Override
    default double getRadius() {
        return DEFAULT_RADIUS;
    }

    default double influence() {
        return getRadius();
    }

    double[] coord();

    /** Whether the point is inside open water. Defaults to no, so a wetted type opts in. */
    default boolean channelContains(double distSqFromCentre) {
        return false;
    }

    @Override
    default long byteSize() {
        return Integer.BYTES + primitiveByteSize();
    }

    /** Type tag + {@link #serializePrimitive()} body. See the class javadoc. */
    @Override
    default byte[] serialize() throws UnsupportedOperationException {
        final byte[] body = serializePrimitive();
        final ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(getType().ordinal());
        buf.put(body);
        return buf.array();
    }

    /** Reads the type tag, then rebuilds the body on that feature's prototype. See the class javadoc. */
    @Override
    default HydrologicalPrimitive deserialize(byte[] rawBytes) throws UnsupportedOperationException {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final HydrologicalFeature type = HydrologicalFeature.fromTag(buf.getInt());
        final byte[] body = new byte[rawBytes.length - Integer.BYTES];
        buf.get(body);
        return type.prototype().deserializePrimitive(body);
    }

    long primitiveByteSize();

    /** This primitive's payload, without the type tag {@link #serialize()} prepends. */
    byte[] serializePrimitive();

    /** Rebuild a primitive of this record's type from a {@link #serializePrimitive()} body (no type tag). */
    HydrologicalPrimitive deserializePrimitive(byte[] rawBytes);
}
