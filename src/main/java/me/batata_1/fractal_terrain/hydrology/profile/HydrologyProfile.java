package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;

/**
 * Shared primitives for the two sides of the same coin — {@link HydrologyProfileCarver} (elevation) and
 * {@code HydrologyProfilePainter} (blocks/biomes/vegetation). Both consume the flat
 * {@link HydrologicalUnit}[] returned by
 * {@link me.batata_1.fractal_terrain.hydrology.LocalRiverProvider#queryInfluence} the same way: every
 * unit contributes a per-unit value ({@link #computeForUnit} for elevation), and the caller merges the
 * contributions in one flat distance-weighted average (weight 1 at the unit, 0 at that unit's own
 * {@link FractalTerrainConfig#riverInfluence influence} radius).
 */
public final class HydrologyProfile {

    private HydrologyProfile() {}

    /**
     * The elevation this single unit would carve the point {@code (pixelX, pixelZ)} to, if it were the
     * only unit in the world (relief-pixel frame throughout). Projects the point onto the line through
     * the unit's coordinate along the unit's channel-perpendicular normal; the absolute projected
     * distance is the cross-section parameter fed to the unit's {@link RosgenProfile}.
     *
     * <p>The reference elevation the cross-section (bed / floodplain deltas) is built on is
     * {@code min(unit.elevation, decodedElevAtUnit)} — anchored on the decoded pre-carve elevation at the
     * <em>unit's own coordinate</em>, not the query pixel's. That keeps the bed at a consistent depth
     * along the channel instead of tracking whatever terrain the pixel happens to sit on (the {@code min}
     * still ensures a unit never lifts the terrain). The query pixel's decoded elevation
     * ({@code decodedElevAtPixel}) is used only as the outer blend target: it is the {@code decodedRelative}
     * fed to {@link RosgenProfile#elevationDelta}, so at the influence edge the carve seams back to the
     * real terrain at the pixel.
     */
    public static double computeForUnit(
            double pixelX, double pixelZ, HydrologicalUnit unit, double decodedElevAtPixel, double decodedElevAtUnit) {
        final double[] unitCoord = unit.coord();
        final double projectedDist;
        if (unit.normal() != null) {
            final double[] n = unit.normal();
            projectedDist = Math.abs((pixelX - unitCoord[0]) * n[0] + (pixelZ - unitCoord[1]) * n[1]);
        } else {
            projectedDist = Math.hypot(pixelX - unitCoord[0], pixelZ - unitCoord[1]);
        }

        final double referenceElev = decodedElevAtUnit;
        //Math.min(unit.elevation() + 62, decodedElevAtUnit);
        final RosgenType type = unit.rosgenType() == null ? RosgenType.A : unit.rosgenType();
        final double delta =
                RosgenProfile.of(type).elevationDelta(projectedDist, unit.width(), decodedElevAtPixel - referenceElev);
        return referenceElev + delta;
    }
}
