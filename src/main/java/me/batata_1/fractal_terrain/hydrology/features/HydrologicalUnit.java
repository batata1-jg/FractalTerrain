package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.storage.Persistable;

/**
 * A single sample of a hydrological feature — a river reach (with its Rosgen channel type), the
 * {@code SOURCE} or {@code DRAIN} point terminating a channel, an abandoned river, or an oxbow lake. It
 * is the queryable, persistable unit stored in
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
 * <p>{@link River.RosgenType} selects the {@link RosgenProfile} used by {@link #getRadius()} and by the
 * tile-level shell carve. A {@code null} rosgenType means <em>this unit is not a river reach</em> — a
 * {@code SOURCE}, a {@code DRAIN}, or a removed feature — because Rosgen classifies reaches and there is
 * nothing to measure a spring or a river mouth over. Consumers coalesce {@code null} to
 * {@link River.RosgenType#A} at every such site, so a typeless unit still carves. Because only {@code A}
 * overrides any profile method today, the choice is currently observable only as "A vs. not-A".
 */

public interface HydrologicalUnit extends SpatialIndexPoint , SpatialIndexCircle, Persistable<HydrologicalUnit> {

    /** Dummy unit that makes the unit index serializable (probed once by Storage). */
    HydrologicalUnit getPrototype();


    /** Kind of hydrological feature this point belongs to. */
    enum HydrologicalFeature {
        RIVER,
        ABANDONED_RIVER,
        OXBOW_LAKE,
        SOURCE,
        DRAIN,
        WATERFALL
    }


    // Records compare array components by reference; these compare contents instead.
    @Override
    boolean equals(Object o);

    @Override
    int hashCode();

    HydrologyProfile getProfile();

    double carveFineGrained(double[] pt, double elevAtPixel);

    @Override
    default byte[] serialize() throws UnsupportedOperationException {
        // the first byte will be the enum type and then will delegate to serialize unit
        return null;
    }

    @Override
    default HydrologicalUnit deserialize(byte[] rawBytes) throws UnsupportedOperationException {
        return null;
    }

    byte[] serializeUnit();

    HydrologicalUnit deserializeUnit(byte[] rawBytes);

}
