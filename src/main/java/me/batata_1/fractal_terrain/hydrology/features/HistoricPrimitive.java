package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A feature the network has shed — a cutoff meander or a captured channel — carrying the step it was cut
 * at and the channel it was cut from.
 *
 * <p>Public for the same reason {@link RadialPrimitive} is: the pass that resolves these lives outside
 * this package and must name {@link #resolved}. Minted mid-simulation, so elevation and influence arrive
 * only through that call — until it runs {@link #getRadius()} is 0, and a primitive indexed before then
 * has no footprint and silently matches nothing.
 */
public interface HistoricPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    /** Width of the channel this was cut out of; input to the cross-section it does not have yet. */
    double width();

    /** The bed the shed feature sits at; 0 until {@link #resolved}. */
    double elevation();

    /** Index radius, so a shed feature spans what its channel did; 0 until {@link #resolved}. */
    double influence();

    @Override
    default double[] getCenter() {
        return coord();
    }

    @Override
    default double getRadius() {
        return influence();
    }

    /** Skeleton: no cross-section of its own yet, so it blends as a plain influence disc. */
    @Override
    default HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    /** This primitive with its deferred quantities filled in. Abstract because only the record knows its
     *  own canonical constructor. */
    HistoricPrimitive resolved(double elevation, double influence);
}
