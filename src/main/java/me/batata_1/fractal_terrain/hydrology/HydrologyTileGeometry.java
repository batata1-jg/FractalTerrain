package me.batata_1.fractal_terrain.hydrology;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.InfluenceSampler;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * Tile framing shared by the local-river split, so the four collaborating classes cannot drift apart on
 * frame conventions.
 *
 * <p>{@code PADDED = GRID + 2*PAD} gives a one-pixel halo for neighbour access at the tile border;
 * {@code COARSE_PX} is one global-river cell's native-px span.
 */
public final class HydrologyTileGeometry {

    private HydrologyTileGeometry() {}

    public static final int GRID = 512;
    public static final int PAD = 1;
    public static final int PADDED = GRID + 2 * PAD; // 514
    static final int COARSE_PX = 256;
    static final int COARSE_HALF = COARSE_PX / 2;

    public static double sampleBilinear(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, PADDED);
    }

    static double sampleLocal(float[] field, double px, double pz) {
        return Interpolation.sampleBilinear(field, px, pz, GRID);
    }

    /**
     * The shared {@link InfluenceSampler} every tile-pipeline collect site queries against {@code elev},
     * clamped so a primitive's rotated footprint fits inside the padded tile instead of running off the
     * grid and getting silently truncated by {@code RiverInfluenceCarve}'s AABB clip.
     */
    public static InfluenceSampler influenceSampler(float[] elev) {
        return (x, z, bedElev, width, normal, type) -> {
            final double raw = HydrologyTuning.influence(
                    width, Math.abs(Interpolation.sampleNearest(elev, x, z, PADDED) - bedElev));
            final double edge = Math.min(Math.min(x, z), Math.min(PADDED - 1 - x, PADDED - 1 - z));
            final double axisSpan = Math.abs(normal[0]) + Math.abs(normal[1]);
            // RiverPrimitive.getLength()/getWidth() both return influence*2, so carvePrimitiveInfluence's
            // half-extents (influenceLen*|nz| + influenceWidth*|nx| and influenceLen*|nx| + influenceWidth*|nz|)
            // collapse to influence*(|nx|+|nz|) — containment against the nearest tile edge is one division.
            // A degenerate (zero) normal makes that division meaningless, so it falls back to the unclamped raw
            // radius instead.
            final double bounded = axisSpan > 0 ? Math.min(raw, edge / axisSpan) : raw;
            return Math.max(
                    Math.max(bounded, HydrologyTuning.MIN_INFLUENCE_RADIUS),
                    RosgenProfile.of(type).floodPlainLength(width) + 1);
        };
    }
}
