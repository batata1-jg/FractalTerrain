package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.network.Centreline;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexShape;
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
 * <p>Carve behavior is split so the carve never switches on the concrete record type: {@link
 * #getProfile()} answers what cross-section to cut, and the geometry it is cut along comes off the
 * record's own accessors. Implementations must override {@code equals}/{@code hashCode} — see
 * {@link PrimitiveCodec#coordsEqual}.
 */
public interface HydrologicalPrimitive extends SpatialIndexShape, Persistable<HydrologicalPrimitive> {

    /** Probe {@code Storage} uses to decide the index is persistable; any primitive type would serve. */
    HydrologicalPrimitive PROTOTYPE = new RiverPrimitive(new double[] {0.0, 0.0}, 0, null, null, 0, 0, 0, 0);

    /** Deliberately small, so a feature with no profile yet barely perturbs the terrain. */
    double DEFAULT_RADIUS = 2.0;

    Comparator<HydrologicalPrimitive> comparator = (p1, p2) -> {
        if (p1.getType().ordinal() < p2.getType().ordinal()) return -1;
        if (p1.getType().ordinal() > p2.getType().ordinal()) return 1;
        final RiverPrimitive r1 = asRiver(p1);
        final RiverPrimitive r2 = asRiver(p2);
        if (r1 != null && r2 != null) {
            if (r1.influence() > r2.influence()) return -1;
            if (r1.influence() < r2.influence()) return 1;
        }
        return 0;
    };

    /** The {@link RiverPrimitive} a primitive carves as, or {@code null} for every other family. The one
     *  place {@link #comparator} and the lattice carve agree on what counts as a river. */
    static @Nullable RiverPrimitive asRiver(HydrologicalPrimitive primitive) {
        if (primitive instanceof RiverPrimitive river) return river;
        return null;
    }

    Logger LOG = LoggerFactory.getLogger(HydrologicalPrimitive.class);

    /** Which kind of feature this primitive is; the tag {@link #serialize()} writes. */
    HydrologicalFeature getType();

    long primitiveByteSize();

    /** This primitive's payload, without the type tag {@link #serialize()} prepends. */
    byte[] serializePrimitive();

    default float waterLine() {
        return -1;
    }

    /** Water surface offset below the bank, stepped by channel size. Static because the carve reads
     *  it at an interpolated width, not at any one primitive's. */
    static float waterLine(double channelWidth) {
        if (channelWidth <= HydrologyTuning.WATER_LINE_WIDTH_NARROW) return HydrologyTuning.WATER_LINE_OFFSET_NARROW;
        if (channelWidth <= HydrologyTuning.WATER_LINE_WIDTH_MEDIUM) return HydrologyTuning.WATER_LINE_OFFSET_MEDIUM;
        return HydrologyTuning.WATER_LINE_OFFSET_WIDE;
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    boolean equals(Object o);

    @Override
    int hashCode();

    /** The profile deciding this primitive's carve zones and shell pull. */
    HydrologyProfile getProfile();

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

    /** Rebuild a primitive of this record's type from a {@link #serializePrimitive()} body (no type tag). */
    HydrologicalPrimitive deserializePrimitive(byte[] rawBytes);

    /**
     * Influence radius for a channel point, in whatever frame the caller emits primitives from.
     *
     * <p>Nested here so the grid stride behind the sample stays with the provider that owns the grid, and
     * this package keeps needing no frame of its own. {@code normal} is read-only: implementations must
     * not retain or mutate it.
     */
    @FunctionalInterface
    interface InfluenceSampler {
        double at(double x, double z, double bedElev, double width, double[] normal, RiverPrimitive.RosgenType type);
    }

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
                Centreline centreline = (Centreline) args[2];
                InfluenceSampler influence = (InfluenceSampler) args[3];
                RiverPrimitive.RosgenType[] types = typer.typesFor(ch);
                for (int i = 0; i < ch.numPts(); i++) {
                    final double width = ch.widthAt(i);
                    final double[] normal = centreline.normalAt(ch, i);
                    // The sampler reads its own grid, which the offset has not been applied to; only the
                    // stored coord is shifted into the frame the index is queried in.
                    final double[] splinePt = ch.spline.sample(i);
                    final double bedElevation = ch.bedElev(i);
                    out.add(new RiverPrimitive(
                            VectorOps.sub(splinePt, offset),
                            influence.at(splinePt[0], splinePt[1], bedElevation, width, normal, types[i]),
                            types[i],
                            normal,
                            ch.spline.curvature(i),
                            width,
                            bedElevation));
                }
            }

            @Override
            public HydrologyProfile profileFor(int sub) {
                return RosgenProfile.of(RiverPrimitive.RosgenType.byOrdinal(sub));
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
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        },
        WATERFALL(() -> WaterfallPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        },
        DELTA(() -> DeltaPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        },
        // :SCHEMA: appended, never reordered; the ordinal is the on-disk type tag, so moving a
        // constant reinterprets every primitive already cached.
        CONFLUENCE(() -> ConfluencePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        };
        /** {@code values()} without the defensive copy; indexed by the on-disk type tag. */
        private static final HydrologicalFeature[] VALUES = values();

        /** What a lattice cell holds when no primitive reached it. Not {@code 0L} — that is a valid
         *  packed value ({@code RIVER} + {@code RosgenType.A}), so a zero-filled buffer would read as river. */
        public static final long NONE = -1L;

        public long pack(int subOrdinal) {
            return (((long) ordinal()) << 32) | (subOrdinal & 0xFFFFFFFFL);
        }

        /** The family in a {@link #pack}ed cell, or {@code null} for {@link #NONE}. */
        @Nullable
        public static HydrologicalFeature unpack(long packed) {
            final int ordinal = (int) (packed >>> 32);
            return ordinal < 0 || ordinal >= VALUES.length ? null : VALUES[ordinal];
        }

        /** The family-specific sub-classification in a {@link #pack}ed cell. */
        public static int unpackSub(long packed) {
            return (int) packed;
        }

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

        /** The profile a packed cell's sub-classification carves and paints with. Exists because the
         *  surface path holds a packed tag and never a primitive instance. */
        public HydrologyProfile profileFor(int sub) {
            return DefaultProfile.INSTANCE;
        }

        public abstract void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args);
    }
}
