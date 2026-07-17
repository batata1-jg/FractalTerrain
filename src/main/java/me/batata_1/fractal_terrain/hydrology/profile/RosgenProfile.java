package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;

/**
 * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type.
 *
 * <p>Two independent deltas are carved by two different stages, and their sum is the intended trench
 * (cross-stage conservation):
 *
 * <ul>
 *   <li>{@link #riverInfluenceElevation} — the tile-level valley/floodplain SHELL (a flat floor at {@link #shellFloor},
 *       {@link HydrologyTuning#FREEBOARD} below the feature's reference/bank elevation), lens-masked so it
 *       reaches 0 (no change) at {@link #riverInfluence}. Carved once per tile by
 *       {@code HydrologyProfileCarver#carveRiverShells}, which computes the falloff lerp against a
 *       pristine (pre-carve) ambient snapshot rather than the live buffer, so the min-composite across
 *       the global and local passes is order-independent.
 *   <li>{@link #riverAreaDelta} — the per-pixel bed TRENCH cut below the already-carved shell, within
 *       the bed half-width only. Carved per block by {@code PopulateNoiseStep}.
 * </ul>
 *
 * <p>The profile is also the authority for the two horizontal extents of the cross-section —
 * {@link #floodPlainLength} and {@link #riverInfluence} — so a river's floodplain half-extent and outer
 * influence radius can vary by Rosgen type. {@link FractalTerrainConfig#floodPlainLength} /
 * {@link FractalTerrainConfig#riverInfluence} are thin delegates to these (their width-only overloads
 * assume {@link RosgenType#A}).
 *
 * <p>The lens mask ({@link #lensMask}) is the flat-floor footprint of a single unit: the intersection of
 * two circles of radius {@code r = fpl^2 / (2d) + d/2} (chord half-length {@code fpl}, sagitta {@code d}),
 * centred {@code +/-(r - d)} from the unit along its tangent. Inside the lens the mask is exactly {@code 1}
 * (out to {@code +/-fpl} cross-channel, {@code +/-d} along-channel); outside it the mask falls off and
 * reaches exactly {@code 0} at {@link #riverInfluence}. Consecutive units overlap along the channel (unit
 * spacing {@code dx <= width/2}) into a continuous flat floodplain corridor.
 *
 * <p>The floodplain and blending zones are unions of per-unit radial discs; the smoothness of that
 * union relies on adjacent units' discs overlapping, which requires unit spacing {@code dx <=
 * width/2} (enforced in {@link me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork}).
 * Loosening that spacing risks scalloping or gaps in the floodplain corridor.
 *
 * <p><b>All four types currently share the same behaviour</b> — the horizontal-extent laws
 * ({@link #floodPlainLength} / {@link #riverInfluence}) are defined once at the enum level and inherited by
 * A–D. Per-type formulas land by overriding the relevant method in a constant's body (e.g.
 * {@code C { @Override public double riverInfluence(double width) { … } }}).
 */
public enum RosgenProfile {
    A {
        @Override
        public double floodPlainLength(double width) {
            return 1 + 1.2 * width;
        }

        @Override
        protected double bedDelta(double signedPerpDist, double width) {
            return width * Math.sqrt(1 - signedPerpDist * signedPerpDist) / 2;
        }

        @Override
        protected double floodPlainDelta(double signedPerpDist, double width, double floodPlainLength) {
            if (width > 1) if (-0.75 < signedPerpDist && signedPerpDist < -0.25) return 1;
            return 0;
        }

        @Override
        protected double unAffectedDistCalculator(double width) {
            return 1.12 * width;
        }
    },
    B,
    C,
    D;

    public double unAffectedDist(double width) {
        return Math.clamp(unAffectedDistCalculator(width), width / 2, floodPlainLength(width));
    }

    protected double unAffectedDistCalculator(double width) {
        return width;
    }

    // ---- Horizontal extents (type-dependent; shared placeholder law, override per constant) ----
    /**
     * Floodplain half-extent (native px) for a river of the given width under this Rosgen type. Placeholder
     * law shared by all types: {@code FLOODPLAIN_BASE + FLOODPLAIN_WIDTH_FACTOR · width}. Override in a
     * constant's body to make a type's floodplain wider/narrower.
     */
    public double floodPlainLength(double width) {
        return FractalTerrainConfig.FLOODPLAIN_BASE + FractalTerrainConfig.FLOODPLAIN_WIDTH_FACTOR * width;
    }

    /**
     * Outer influence radius (native px) for a river of the given width under this Rosgen type: floodplain +
     * blend band, clamped to {@link FractalTerrainConfig#MAX_INFLUENCE_RADIUS}. Beyond this radius the river
     * no longer affects a pixel; it is also the unit's R-tree membership-circle radius
     * ({@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit#getRadius()}). Placeholder law shared by
     * all types; override per constant to widen/narrow a type's reach. Note it calls the (virtual)
     * {@link #floodPlainLength} so a type that overrides only its floodplain gets a consistent influence.
     */
    public double riverInfluence(double width) {
        return Math.min(
                FractalTerrainConfig.MAX_INFLUENCE_RADIUS,
                floodPlainLength(width) * FractalTerrainConfig.INFLUENCE_BLEND_MULTIPLIER);
    }

    public static double lensMask(double perpDist, double alongDist, double width, double floodPlainLength) {

        final double d = HydrologyTuning.d(width, floodPlainLength);
        final double r = (floodPlainLength * floodPlainLength) / (2.0 * d) + d / 2.0;
        final double offset = r - d;
        final double distToNearCenter = Math.hypot(alongDist - offset, perpDist);
        final double distToFarCenter = Math.hypot(alongDist + offset, perpDist);
        // Inside the intersection of both discs iff within r of the farther center.
        final double excess = Math.max(distToNearCenter, distToFarCenter) - r;
        if (excess <= 0.0) return 1.0;
        return 0;
    }

    public double riverInfluenceElevation(double radialDist, double width, double curElev, double unitElev) {
        double t = 0;
        final double riverInfluence = riverInfluence(width);
        final double floodPlainLength = floodPlainLength(width);
        if (floodPlainLength < radialDist) {
            t = (radialDist - floodPlainLength) / (riverInfluence - floodPlainLength);
        }
        if (riverInfluence < radialDist) t = 1;
        return (1 - t) * unitElev + t * curElev;
    }

    // ---- Bed (per-pixel residual trench, cut below the already-carved shell) ----

    public double riverAreaDelta(double signedPerpDist, double alongDist, double width) {
        final double floodPlainLen = floodPlainLength(width);
        if (Math.hypot(signedPerpDist, alongDist) > floodPlainLen) return 0;
        final double marginLen = width / 2;
        if (Math.abs(signedPerpDist) <= marginLen) return bedDelta(signedPerpDist / marginLen, width);
        return floodPlainDelta(
                signedPerpDist > 0
                        ? ((signedPerpDist - marginLen) / (floodPlainLen - marginLen))
                        : ((marginLen + signedPerpDist) / (marginLen - floodPlainLen)),
                width,
                floodPlainLen);
    }

    // range [-1,0] -> [-floodPlainLen,-marginLen] ;
    // range [0,1] -> [marginLen,floodPlainLen] ;
    protected double floodPlainDelta(double signedPerpDist, double width, double floodPlainLength) {
        return 0;
    }

    // should be between the range -1 and 1
    protected double bedDelta(double signedPerpDist, double width) {
        return -1;
    }

    /** The profile for a unit's Rosgen type. */
    public static RosgenProfile of(RosgenType type) {
        return switch (type) {
            case A -> A;
            case B -> B;
            case C -> C;
            case D -> D;
        };
    }
}
