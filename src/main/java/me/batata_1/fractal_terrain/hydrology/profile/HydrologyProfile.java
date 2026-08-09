package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import org.jetbrains.annotations.Nullable;

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

    /** Marks a zone this profile does not define; any non-positive value reads the same way. */
    double NO_ZONE = -1.0;

    /** Outer radius of a zone, or {@link #NO_ZONE} if undefined. The default takes INFLUENCE from the
     *  primitive's own index radius, so carve reach and query reach cannot drift apart. An override must
     *  fall back to {@code super} for primitive types it does not recognise. */
    default double zoneRadius(HydrologicalPrimitive primitive, ZoneCategory category) {
        return category == ZoneCategory.INFLUENCE ? primitive.getRadius() : NO_ZONE;
    }

    /** The one zone this primitive claims a point for, or null if out of reach. The default picks the
     *  innermost defined zone; override when a profile's zones are not nested. */
    @Nullable
    default ZoneCategory categoryAt(HydrologicalPrimitive primitive, double radialDist) {
        for (final ZoneCategory category : ZoneCategory.BY_PRIORITY) {
            final double radius = zoneRadius(primitive, category);
            if (radius > 0 && radialDist < radius) return category;
        }
        return null;
    }

    /** Weight of this primitive in its zone's average, so nearer primitives dominate and a primitive fades out at its
     *  boundary rather than dropping out. Compared only within one zone, so any scale works. */
    default double zoneWeight(HydrologicalPrimitive primitive, ZoneCategory category, double radialDist) {
        final double radius = zoneRadius(primitive, category);
        if (radius <= 0) return 0;
        return Math.clamp(1 - radialDist / radius, 0, 1);
    }

    /** The valley pull this primitive exerts at tile-carve time, before any bed detail exists.
     *  Defaults to no pull, leaving the shell to whichever primitives do have a valley. */
    default double shellElevation(HydrologicalPrimitive primitive, double radialDist, double curElev) {
        return curElev;
    }
}
