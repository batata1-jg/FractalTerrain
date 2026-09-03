package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A feature the carve cuts radially rather than along a flow tangent — a junction pool or a spring.
 *
 * <p>The type {@code RiverInfluenceCarve}'s second pass dispatches on, which is why it is public where
 * {@link PositionOnlyPrimitive} is not: the carve lives in {@code hydrology.profile} and must name it.
 * Everything the pass needs is here, so the pass never switches on a concrete record type — the shape
 * comes from {@link RadialProfile}, the extents from {@link #width()}, the rim from {@link #elevation()}.
 */
public interface RadialPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    /** The largest channel width meeting at this node; the disc radius and the depth law's input. */
    double width();

    /** The rim the bowl is cut down from, taken from the node's assigned bed elevation. */
    double elevation();

    RadialProfile getRadialProfile();

    @Override
    default double[] getCenter() {
        return coord();
    }

    @Override
    default double getRadius() {
        return width();
    }

    @Override
    default HydrologyProfile getProfile() {
        return getRadialProfile();
    }
}
