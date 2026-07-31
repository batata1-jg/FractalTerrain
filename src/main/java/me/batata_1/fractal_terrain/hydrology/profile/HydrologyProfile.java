package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import org.jetbrains.annotations.Nullable;

/**
 * How one kind of hydrological feature shapes terrain around it — the extension point every new feature
 * type plugs into.
 *
 * <p>The carve stages ask the profile, never the unit's concrete class, so adding a feature type needs
 * no carve-side change. The profile decides where/how much; the unit decides what, via {@link
 * HydrologicalUnit#carveFineGrained}, since the cross-section needs per-unit state.
 *
 * <p>Every default describes the minimum viable feature, so overrides touch only what actually changes.
 */
public interface HydrologyProfile {

    /** Marks a zone this profile does not define; any non-positive value reads the same way. */
    double NO_ZONE = -1.0;

    /** Outer radius of a zone, or {@link #NO_ZONE} if undefined. The default takes INFLUENCE from the
     *  unit's own index radius, so carve reach and query reach cannot drift apart. An override must
     *  fall back to {@code super} for unit types it does not recognise. */
    default double zoneRadius(HydrologicalUnit unit, ZoneCategory category) {
        return category == ZoneCategory.INFLUENCE ? unit.getRadius() : NO_ZONE;
    }

    /** The one zone this unit claims a point for, or null if out of reach. The default picks the
     *  innermost defined zone; override when a profile's zones are not nested. */
    @Nullable
    default ZoneCategory categoryAt(HydrologicalUnit unit, double radialDist) {
        for (final ZoneCategory category : ZoneCategory.BY_PRIORITY) {
            final double radius = zoneRadius(unit, category);
            if (radius > 0 && radialDist < radius) return category;
        }
        return null;
    }

    /** Weight of this unit in its zone's average, so nearer units dominate and a unit fades out at its
     *  boundary rather than dropping out. Compared only within one zone, so any scale works. */
    default double zoneWeight(HydrologicalUnit unit, ZoneCategory category, double radialDist) {
        final double radius = zoneRadius(unit, category);
        if (radius <= 0) return 0;
        return Math.clamp(1 - radialDist / radius, 0, 1);
    }

    /** The valley pull this unit exerts at tile-carve time, before any bed detail exists.
     *  Defaults to no pull, leaving the shell to whichever units do have a valley. */
    default double shellElevation(HydrologicalUnit unit, double radialDist, double curElev) {
        return curElev;
    }
}
