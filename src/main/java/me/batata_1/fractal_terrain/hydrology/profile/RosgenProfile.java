package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;

/**
 * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type. Given the
 * perpendicular distance from a query point to the channel centreline ({@code projectedDist}), it returns
 * an elevation <em>delta relative to the feature's reference elevation</em> (the carver adds this to
 * {@code min(unit.elevation, decodedElevation)}).
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
 *   <li><b>Floodplain</b> (up to {@code floodPlainLength}) → {@link #floodplainElevation} (a flat band for
 *       now, but kept a function for future noise);</li>
 *   <li><b>Blending region</b> (up to {@link FractalTerrainConfig#riverInfluence}) → {@link #blend},
 *       the V/U valley curve that carries the floodplain delta toward {@code decodedRelative}.</li>
 * </ol>
 *
 * <p><b>Type A is the only type wired for now</b>, and its three functions are placeholders pending the
 * user-supplied formulas: bed {@code -1} (depth 1), floodplain {@code 0}, blend = identity. They produce a
 * visible 1-deep channel; the carver's per-id merge handles the smooth transition to decoded terrain until
 * the real blend curve lands.
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
        public double blend(double floodplainDelta, double decodedRelative, double t) {
            // TODO: user-supplied V/U curve. Placeholder: identity (no transition curve applied).
            return floodplainDelta;
        }
    };

    /** River-bed elevation delta (relative to the reference elevation) within the bed half-width. */
    public abstract double bedElevation(double projectedDist, double width, double floodPlainLength);

    /** Floodplain elevation delta (relative to the reference elevation) in the floodplain band. */
    public abstract double floodplainElevation(double projectedDist, double width, double floodPlainLength);

    /**
     * V/U blend curve for the blending region: maps the floodplain delta toward {@code decodedRelative}
     * (= {@code decodedElev − referenceElev}) as the normalized blend parameter {@code t} goes 0 → 1.
     */
    public abstract double blend(double floodplainDelta, double decodedRelative, double t);

    /**
     * The full cross-section delta at {@code projectedDist}: dispatches to the bed / floodplain / blend
     * zone. {@code decodedRelative} is {@code decodedElev − referenceElev}, used only in the blend zone.
     */
    public double elevationDelta(double projectedDist, double width, double floodPlainLength, double decodedRelative) {
        final double bedHalfWidth = width * 0.5;
        if (projectedDist <= bedHalfWidth) {
            return bedElevation(projectedDist, width, floodPlainLength);
        }
        if (projectedDist <= floodPlainLength) {
            return floodplainElevation(projectedDist, width, floodPlainLength);
        }
        final double maxInfluence = FractalTerrainConfig.riverInfluence(width);
        final double blendSpan = Math.max(1e-9, maxInfluence - floodPlainLength);
        final double t = Math.min(1.0, (projectedDist - floodPlainLength) / blendSpan);
        final double floodplainDelta = floodplainElevation(floodPlainLength, width, floodPlainLength);
        return blend(floodplainDelta, decodedRelative, t);
    }

    /** The profile for a unit's Rosgen type (defaults to {@link #A}; only A is wired for now). */
    public static RosgenProfile of(RosgenType type) {
        return A;
    }
}
