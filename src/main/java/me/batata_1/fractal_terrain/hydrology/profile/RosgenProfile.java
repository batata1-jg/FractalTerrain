package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type. Given the
 * perpendicular distance from a query point to the channel centreline ({@code projectedDist}), it returns
 * an elevation <em>delta relative to the feature's reference elevation</em> (the carver adds this to
 * {@code min(unit.elevation, decodedElevation)}).
 *
 * <p>The profile is also the authority for the two horizontal extents of the cross-section —
 * {@link #floodPlainLength} and {@link #riverInfluence} — so a river's floodplain half-extent and outer
 * influence radius can vary by Rosgen type. {@link FractalTerrainConfig#floodPlainLength} /
 * {@link FractalTerrainConfig#riverInfluence} are thin delegates to these (their width-only overloads
 * assume {@link RosgenType#A}).
 *
 * <p>Symmetric cross-section, centre → far:
 *
 * <pre>
 *   elevInRiverBed | elevInFloodPlainArea | blendingRegion | decodedElev
 * </pre>
 *
 * Three zones, by {@code projectedDist}:
 *
 * <ol>
 *   <li><b>River bed</b> ({@code projectedDist ≤ width·0.5}) → {@link #bedElevation};</li>
 *   <li><b>Floodplain</b> (up to {@link #floodPlainLength}) → {@link #floodplainElevation} (a flat band for
 *       now, but kept a function for future noise);</li>
 *   <li><b>Blending region</b> (up to {@link #riverInfluence}) → {@link #blend}, the V/U valley curve that
 *       carries the floodplain delta toward {@code normalElevDelta}.</li>
 * </ol>
 *
 * <p><b>All four types currently share the same placeholder behaviour</b> — the cross-section deltas and
 * the {@link #floodPlainLength} / {@link #riverInfluence} laws are defined once at the enum level and
 * inherited by A–D. Per-type formulas land by overriding the relevant method in a constant's body (e.g.
 * {@code C { @Override public double riverInfluence(double width) { … } }}). The placeholder cross-section
 * (bed {@code -1}, floodplain {@code 0}, blend = lerp) produces a visible 1-deep channel; the carver's
 * per-id merge handles the smooth transition to decoded terrain until the real curves land.
 */
public enum RosgenProfile {
    A {

        @Override
        public double bedElevation(double projectedDist, double width, double floodPlainLength) {
            // TODO: user-supplied formula. Placeholder: river bed sits 1 below the reference (depth 1).
            return -1.0;
        }

        @Override
        public double floodplainElevation(double projectedDist, double width, double floodPlainLength) {
            // TODO: user-supplied formula. Placeholder: floodplain at the reference elevation.
            return 0.0;
        }

        @Override
        public double floodPlainLength(double width) {
            return 1 + 1.2 * width;
        }

        @Override
        public double riverInfluence(double width) {
            return Math.min(FractalTerrainConfig.MAX_INFLUENCE_RADIUS, floodPlainLength(width));
        }
    },
    B,
    C,
    D;

    // ---- Cross-section elevation deltas (shared placeholders; override per constant when formulas arrive) ----

    private static final Logger LOG = LoggerFactory.getLogger(RosgenProfile.class);

    /** River-bed elevation delta (relative to the reference elevation) within the bed half-width. */
    public double bedElevation(double projectedDist, double width, double floodPlainLength) {
        return -1.0;
    }

    /** Floodplain elevation delta (relative to the reference elevation) in the floodplain band. */
    public double floodplainElevation(double projectedDist, double width, double floodPlainLength) {
        return 0.0;
    }

    /**
     * V/U blend curve for the blending region: maps the floodplain delta toward {@code normalElevDelta}
     * (= {@code decodedElev − referenceElev}) as the normalized blend parameter {@code t} goes 0 → 1.
     */
    public double blend(double floodplainDelta, double normalElevDelta, double t) {
        return floodplainDelta * (1 - t) + normalElevDelta * t;
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

    /**
     * The full cross-section delta at {@code projectedDist}: dispatches to the bed / floodplain / blend
     * zone using this type's own {@link #floodPlainLength} and {@link #riverInfluence} extents.
     * {@code normalElevDelta} is {@code decodedElev − referenceElev}, used only in the blend zone.
     */
    public double elevationDelta(double projectedDist, double width, double normalElevDelta) {
        final double bedHalfWidth = width * 0.5;
        final double floodPlainLength = floodPlainLength(width);
        if (projectedDist <= bedHalfWidth) {
            return bedElevation(projectedDist, width, floodPlainLength);
        }
        if (projectedDist <= floodPlainLength) {
            return floodplainElevation(projectedDist, width, floodPlainLength);
        }
        final double maxInfluence = riverInfluence(width);
        final double blendSpan = Math.max(1e-9, maxInfluence - floodPlainLength);
        final double t = Math.min(1.0, (projectedDist - floodPlainLength) / blendSpan);
        final double floodplainDelta = floodplainElevation(floodPlainLength, width, floodPlainLength);
        return blend(floodplainDelta, normalElevDelta, t);
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
