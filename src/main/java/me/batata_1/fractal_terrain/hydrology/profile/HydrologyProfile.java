package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How one kind of hydrological feature shapes terrain around it — the extension point every new feature
 * type plugs into.
 *
 * <p>The carve stages ask the profile, never the primitive's concrete class, so adding a feature type needs
 * no carve-side change. The profile decides where/how much; the primitive decides what, via {@link
 * HydrologicalPrimitive#h}, since the cross-section needs per-primitive state.
 *
 * <p>Every default describes the minimum viable feature, so overrides touch only what actually changes.
 */
public interface HydrologyProfile {


    /** The valley pull this primitive exerts at tile-carve time, before any bed detail exists.
     *  Defaults to no pull, leaving the shell to whichever primitives do have a valley. */
    default double shellElevation(HydrologicalPrimitive primitive, double radialDist, double curElev) {
        return curElev;
    }


}
