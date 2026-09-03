package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * The cross-section of a feature with no flow direction — what a junction pool cuts, and what a spring
 * cuts, as a function of distance from its centre alone.
 *
 * <p>The radial twin of {@link RosgenProfile}: same split, where the enum constant owns the shape and
 * the carve owns the walk. A radial primitive routes here rather than to a Rosgen type because a bowl
 * has no tangent to take a cross-section across.
 */
public enum RadialProfile implements HydrologyProfile {

    /** Converging flow scours a rounded floor, so the parabola holds its depth well out toward the rim. */
    CONFLUENCE {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * (1 - normalizedRadius * normalizedRadius);
        }
    },

    /** A spring cuts a notch rather than a pool, so the cone gives up depth linearly from a point. */
    SOURCE {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * (1 - normalizedRadius);
        }
    };

    /**
     * Tabulates this profile into {@code lut}, where entry {@code i} is the surface at radius
     * {@code (baseIdx + i) * step}. Runs once per primitive per grid, so the carve's per-cell loop
     * reads an array instead of evaluating the law.
     */
    public void sampleRadialSection(
            float[] lut, int n, double step, int baseIdx, double elevation, double invRadius, double depth) {
        for (int i = 0; i < n; i++) {
            // The carve's footprint is a square AABB, so entries past the rim are indexed and must
            // read the rim rather than an extrapolated law.
            final double r = Math.clamp((baseIdx + i) * step * invRadius, 0, 1);
            lut[i] = (float) (elevation + radialDelta(r, depth));
        }
    }

    /** The signed offset below the rim at a radius normalised to {@code [0, 1]}; zero at the rim. */
    protected abstract double radialDelta(double normalizedRadius, double depth);
}
