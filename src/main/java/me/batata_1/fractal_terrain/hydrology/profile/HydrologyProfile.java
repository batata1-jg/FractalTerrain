package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;

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

    /**
     * The materials this profile paints down a claimed column, tabulated into {@code out}, surface
     * first. Tabulated rather than answered per block because the caller's 16x16 column loop sits below
     * the hot/cold line and cannot afford a virtual call per block.
     *
     * @param subType the family-specific classification from {@code HydrologicalFeature.unpackSub}
     * @param dist the banded footprint coordinate from {@code RiverInfluenceCarve.band}
     * @param out caller-owned scratch, at least {@code HydrologyTuning.MAX_RIVER_PAINT_DEPTH} long;
     *     implementations must not retain it
     * @return how many entries were filled; zero leaves the column to the vanilla surface rules
     */
    default int riverPaintDepth(int subType, float dist, SurfaceMaterial[] out) {
        return 0;
    }
}
