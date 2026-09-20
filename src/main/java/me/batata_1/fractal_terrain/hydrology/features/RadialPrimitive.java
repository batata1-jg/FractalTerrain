package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.carvers.BedCarver;
import me.batata_1.fractal_terrain.hydrology.carvers.LatticeCarve;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A feature the carve cuts radially rather than along a flow tangent — a junction pool or a spring.
 *
 * <p>The type {@code LatticeCarve}'s second pass dispatches on, which is why it is public where
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

    default HydrologyProfile getProfile() {
        return getRadialProfile();
    }

    /** A bowl contributes no shell influence: the shell is the valley a flow tangent cuts, and a disc
     *  has none. {@link AbandonedRiverPrimitive} is the one radial family that overrides this. */
    @Override
    default void carveInfluence(LatticeCarve.ShellGrid grid) {}

    @Override
    default void carveBed(LatticeCarve.BedGrid grid) {
        final double radius = getRadius();
        if (radius <= 0) return;
        // Deferred: elevation is NaN-sentinelled until RiverNetwork.remapHistory resolves it, which no
        // production caller does -- carving the sentinel would cut every cell it reaches to NaN.
        if (Double.isNaN(elevation())) return;
        BedCarver.carveRadial(this, grid, coord()[0], coord()[1], radius, (float)
                (elevation() + HydrologicalPrimitive.waterLine(width())));
    }

    @Override
    default void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution) {
        getRadialProfile()
                .sampleRadialSection(
                        lut,
                        n,
                        resolution,
                        baseIdx,
                        elevation(),
                        1.0 / getRadius(),
                        FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width()));
    }
}
