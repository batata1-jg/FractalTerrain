package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import me.batata_1.fractal_terrain.hydrology.carvers.RiverInfluenceCarve;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexRotatedRectangle;

/**
 * A primitive with a flow tangent and a Rosgen cross-section, cutting the rectangle/tangent shell shape
 * every implementor shares. {@link RiverPrimitive} and {@link OxbowLakePrimitive} both carve this shape: a
 * shed meander reads close enough to a live channel for the same cross-section math to apply to both.
 */
public interface RosgenCarvedPrimitive extends SpatialIndexRotatedRectangle, HydrologicalPrimitive {

    /** Unit cross-section normal; {@code null} means no tangent, so the carve skips this primitive. */
    double[] normal();

    /** Channel width at this point; the cross-section's overall scale. */
    double width();

    /** Signed curvature at this point; perturbs the cross-section per {@link RosgenProfile}. */
    double curvature();

    /** The elevation the cross-section is cut from. */
    double elevation();

    /** Rosgen classification; {@code null} coalesces to {@link RiverPrimitive.RosgenType#A}. */
    RiverPrimitive.RosgenType rosgenType();

    /** Cross-section seed, mixed into {@link RosgenProfile}'s per-point perturbation. */
    long seed();

    default HydrologyProfile getProfile() {
        return RosgenProfile.of(RiverPrimitive.RosgenType.orDefault(rosgenType()));
    }

    @Override
    default void carveInfluence(RiverInfluenceCarve.ShellGrid grid) {
        InfluenceCarver.carveRosgenInfluence(this, grid);
    }
}
