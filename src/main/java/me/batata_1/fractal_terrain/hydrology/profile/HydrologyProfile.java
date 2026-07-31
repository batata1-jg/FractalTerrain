package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalUnit;
import org.jetbrains.annotations.Nullable;

/**
 * How one kind of hydrological feature shapes the terrain around it — the extension point every new
 * feature type plugs into. A unit hands out its profile via {@link HydrologicalUnit#getProfile()}, and
 * the two carve stages ask that profile, never the unit's concrete class, what the feature does:
 *
 * <ul>
 *   <li><b>Zoning</b> ({@link #zoneRadius}, {@link #categoryAt}, {@link #zoneWeight}) — which
 *       {@link ZoneCategory} the unit claims a point for and how strongly, driving the per-block merge in
 *       {@link HydrologyProfileCarver#carvePrefetched}.</li>
 *   <li><b>Shell elevation</b> ({@link #shellElevation}) — the tile-level valley pull applied by
 *       {@link HydrologyProfileCarver#carveRiverShells}.</li>
 * </ul>
 *
 * <p>The fine cross-section itself is not here: it is per-unit state (a normal, a width, a bed
 * elevation), so it lives on the unit as {@link HydrologicalUnit#carveFineGrained}. The profile decides
 * <em>where</em> and <em>how much</em>; the unit decides <em>what</em>.
 *
 * <p>Every default on this interface describes the minimum viable feature: a single {@link
 * ZoneCategory#INFLUENCE} disc of radius {@link HydrologicalUnit#getRadius()}, linear falloff, no shell
 * pull. A new profile only overrides what it actually changes — see {@link RosgenProfile} for the river
 * case and {@link DefaultProfile} for a type that keeps every default.
 */
public interface HydrologyProfile {

    /**
     * Returned by {@link #zoneRadius} for a zone this profile does not define. Any non-positive value
     * reads as absent; this constant names the intent.
     */
    double NO_ZONE = -1.0;

    /**
     * Outer radius (native px) of {@code category} around {@code unit}, or {@link #NO_ZONE} when this
     * profile has no such zone.
     *
     * <p>The default defines only {@link ZoneCategory#INFLUENCE}, at the unit's own {@link
     * HydrologicalUnit#getRadius()} — the same circle the R-tree indexes the unit by, so a unit's carve
     * reach and its query reach cannot drift apart.
     *
     * <p>An override may consult {@code unit}'s concrete type for the state it needs (a river's width,
     * say) and must fall back to {@code HydrologyProfile.super.zoneRadius} for units it does not
     * recognise — a profile is selected by the unit, but nothing stops a shared profile serving several
     * unit types.
     */
    default double zoneRadius(HydrologicalUnit unit, ZoneCategory category) {
        return category == ZoneCategory.INFLUENCE ? unit.getRadius() : NO_ZONE;
    }

    /**
     * The single zone {@code unit} claims a point at radial distance {@code radialDist} for, or
     * {@code null} when the point is out of reach and the unit contributes nothing.
     *
     * <p>The default walks {@link ZoneCategory#BY_PRIORITY} and returns the first defined zone whose
     * radius contains the distance — i.e. for the usual nested layout (bed ⊂ floodplain ⊂ influence) the
     * innermost, most specific zone. Override for a profile whose zones are not nested, e.g. an annular
     * plunge-pool rim that starts at a non-zero radius.
     */
    @Nullable
    default ZoneCategory categoryAt(HydrologicalUnit unit, double radialDist) {
        for (final ZoneCategory category : ZoneCategory.BY_PRIORITY) {
            final double radius = zoneRadius(unit, category);
            if (radius > 0 && radialDist < radius) return category;
        }
        return null;
    }

    /**
     * How much weight {@code unit}'s contribution carries in {@code category}'s average at {@code
     * radialDist}: {@code 1} at the unit's centre falling linearly to {@code 0} at the zone's outer
     * radius, so nearer units dominate and a unit fades out smoothly at its zone boundary rather than
     * dropping out.
     *
     * <p>Weights are only ever compared within one zone, so an override is free to use any non-negative
     * scale it likes; a weight of {@code 0} is equivalent to not contributing at all.
     */
    default double zoneWeight(HydrologicalUnit unit, ZoneCategory category, double radialDist) {
        final double radius = zoneRadius(unit, category);
        if (radius <= 0) return 0;
        return Math.clamp(1 - radialDist / radius, 0, 1);
    }

    /**
     * The tile-level shell elevation {@code unit} pulls a pixel at {@code radialDist} toward, given the
     * live buffer's current elevation {@code curElev}. Applied once per tile by
     * {@link HydrologyProfileCarver#carveRiverShells}, before any bed detail exists.
     *
     * <p>The default is no pull at all ({@code curElev}) — a feature with no valley of its own leaves the
     * shell to whichever units do have one. {@link RosgenProfile#riverInfluenceElevation} is the river
     * override.
     */
    default double shellElevation(HydrologicalUnit unit, double radialDist, double curElev) {
        return curElev;
    }
}
