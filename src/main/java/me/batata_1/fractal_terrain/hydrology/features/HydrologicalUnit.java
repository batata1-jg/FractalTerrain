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
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.storage.Persistable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single sample of a hydrological feature — a river reach, a waterfall, an oxbow lake, the source
 * terminating a channel — and the queryable, persistable unit stored in
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider}'s tiled {@code ImmutableRTree}. Each
 * feature type is a {@code record} implementing this interface, carrying whatever state that type needs
 * and nothing more: {@link RiverUnit} has a channel normal, width and bank elevation, while a
 * {@link WaterfallUnit} or a {@link SourceUnit} currently has only a position.
 *
 * <p>Every unit is indexed as an <b>influence circle</b> ({@link SpatialIndexCircle}): centre =
 * {@link #getCenter()}, radius = {@link #getRadius()} — so a point-stabbing query returns exactly the
 * units whose influence reaches the point. {@link #getCoords()} returns the same backing array with no
 * copy, so tree construction and every query point-scan pay no boxing.
 *
 * <p>What a unit <em>does</em> to the terrain is split in two, and neither half is dispatched on the
 * concrete record type by the carve stages:
 *
 * <ul>
 *   <li>{@link #getProfile()} answers <em>where</em> and <em>how much</em> — which {@link ZoneCategory}
 *       the unit claims a point for, and with what weight. See {@link HydrologyProfile}.</li>
 *   <li>{@link #carveFineGrained} answers <em>what</em> — the unit's own cross-section, computed from
 *       state only that record has.</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 *
 * <p>{@link Persistable} is implemented once, here, as a type tag plus a body: {@link #serialize()}
 * writes {@code getType().ordinal()} and appends whatever {@link #serializeUnit()} produced, and
 * {@link #deserialize} reads the tag back, looks up that feature's prototype in
 * {@link HydrologicalFeature}, and lets it rebuild the body via {@link #deserializeUnit}. So a record
 * never handles its own tag and a heterogeneous unit index round-trips through one {@code Storage}
 * payload type. A new feature type becomes persistable by implementing the two {@code …Unit} methods and
 * registering its prototype on its {@link HydrologicalFeature} constant.
 *
 * <p>Records compare array components by reference, so every implementation must override
 * {@link #equals} / {@link #hashCode} to compare {@code coord} contents — see
 * {@link UnitCodec#coordsEqual}.
 */
public interface HydrologicalUnit extends SpatialIndexPoint, SpatialIndexCircle, Persistable<HydrologicalUnit> {

    /**
     * The prototype {@code Storage} probes once to decide the unit index is persistable, and the receiver
     * every {@link #deserialize} call is made on. Any unit will do — {@link #deserialize} dispatches on
     * the tag in the bytes, not on the receiver — so this is simply the most common type.
     */
    HydrologicalUnit PROTOTYPE = RiverUnit.PROTOTYPE;

    /**
     * Placeholder influence radius (native px) for the feature types that carry only a position so far.
     * Small on purpose: until such a type has a profile deriving a reach from real state, it should
     * perturb barely more than the pixel it sits on rather than assert a radius nothing measured.
     */
    double DEFAULT_RADIUS = 2.0;
    Logger LOG = LoggerFactory.getLogger(HydrologicalUnit.class);

    /** Which kind of feature this unit is; the tag {@link #serialize()} writes. */
    HydrologicalFeature getType();

    /**
     * The kinds of hydrological feature, and the registry mapping each to the record implementing it.
     *
     * <p>A constant's {@link #ordinal()} is the on-disk type tag written by
     * {@link HydrologicalUnit#serialize()}, so <b>constants may only be appended</b> — reordering or
     * removing one silently reinterprets every unit already on disk.
     *
     * <p>A constant with no record yet is registered with a {@code null} prototype: it can be stamped on
     * geometry and carried through the pipeline, but {@link #prototype()} throws rather than guessing a
     * type, so an attempt to persist it fails loudly at the one place that can name what is missing.
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
                    final double[] coords = VectorOps.sub(ch.spline.sample(i),offset);
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
                units.add(new SourceUnit(VectorOps.sub(endpoint.coord,offset)));
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

        /**
         * Indirected through a {@link Supplier} because the constants name records that themselves
         * implement the enclosing interface: a direct instance would have to exist before this enum
         * finishes initialising.
         */
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

    /**
     * This unit's own cross-section at {@code pt} (relief-pixel frame), applied to the elevation already
     * carved there. Returning {@code elevAtPixel} unchanged means "no detail of my own" — the unit then
     * still blends its ambient elevation into whatever zone it claimed.
     */
    double carveFineGrained(double[] pt, double elevAtPixel);

    @Override
    default double getRadius() {
        return DEFAULT_RADIUS;
    }

    double[] coord();

    /**
     * Whether a query point at squared distance {@code distSqFromCentre} from this unit's centre lies
     * inside the feature's water span. Only features with a wetted channel can answer yes; the default is
     * no, so a feature type opts in by overriding.
     */
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
