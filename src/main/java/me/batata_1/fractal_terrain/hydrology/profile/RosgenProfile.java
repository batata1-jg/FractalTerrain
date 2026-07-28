package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;

/**
 * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type.
 *
 * <p>Two deltas are defined here, carved by two different stages:
 *
 * <ul>
 *   <li>{@link #riverInfluenceElevation} — the tile-level valley/floodplain SHELL. Carved once per tile
 *       by {@link HydrologyProfileCarver#carveRiverShells}, which picks the single <em>nearest</em>
 *       influencing unit per pixel and lerps the live buffer's elevation toward that unit's reference
 *       elevation: full pull inside {@link #floodPlainLength}, linearly released to no change at
 *       {@link #riverInfluence}.
 *   <li>{@link #riverAreaDelta} — the per-pixel bed TRENCH below the shell, within the bed half-width.
 *       <b>Not reached today</b>: its only caller is the commented-out body of
 *       {@link HydrologyProfile#computeForUnit}, so this method is currently dead code.
 * </ul>
 *
 * <p>The profile is also the authority for the two horizontal extents of the cross-section —
 * {@link #floodPlainLength} and {@link #riverInfluence} — so a river's floodplain half-extent and outer
 * influence radius can vary by Rosgen type. {@link FractalTerrainConfig#floodPlainLength} /
 * {@link FractalTerrainConfig#riverInfluence} are thin delegates to these (their width-only overloads
 * assume {@link RosgenType#A}).
 *
 * <p>The floodplain and blending zones are unions of per-unit radial discs; the smoothness of that
 * union relies on adjacent units' discs overlapping, which requires unit spacing {@code dx <=
 * width/2} (enforced in {@link me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork}).
 * Loosening that spacing risks scalloping or gaps in the floodplain corridor.
 *
 * <p><b>Only type A overrides anything</b> — A supplies its own {@link #floodPlainLength},
 * {@link #bedDelta}, {@link #floodPlainDelta} and {@link #unAffectedDistCalculator}; B, C and D inherit
 * every enum-level default unchanged. Per-type formulas land by overriding the relevant method in a
 * constant's body (e.g. {@code C { @Override public double riverInfluence(double width) { … } }}).
 */
public enum RosgenProfile {
    A {
        @Override
        public double floodPlainLength(double width) {
            return width;
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
        return width;
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
                FractalTerrainConfig.MAX_INFLUENCE_RADIUS, width * FractalTerrainConfig.INFLUENCE_BLEND_MULTIPLIER);
    }

    /**
     * The shell elevation at radial distance {@code radialDist} from a unit: {@code unitElev} (full pull
     * to the unit's reference elevation) out to {@link #floodPlainLength}, then linearly released back to
     * {@code curElev} at {@link #riverInfluence} and beyond. {@code curElev} is read from the live carve
     * buffer, so the result depends on the order units are visited; {@code carveRiverShells} sidesteps
     * that by applying only the single nearest unit per pixel.
     */
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

    /**
     * Bed-residual depth at a point {@code signedPerpDist} across / {@code alongDist} along the channel:
     * {@link #bedDelta} within the bed half-width, {@link #floodPlainDelta} out to
     * {@link #floodPlainLength}, {@code 0} beyond. Dead code today — see the class javadoc.
     */
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
