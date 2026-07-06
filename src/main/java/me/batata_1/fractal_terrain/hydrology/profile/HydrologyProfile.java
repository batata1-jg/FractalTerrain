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
     * distance is the cross-section parameter fed to the unit's {@link RosgenProfile}. The reference
     * elevation is {@code min(unit.elevation, decodedElev)} so a unit never lifts the terrain.
     */
    public static double computeForUnit(double pixelX, double pixelZ, HydrologicalUnit unit, double decodedElev) {
        final double[] unitCoord = unit.coord();
        final double projectedDist;
        if (unit.normal() != null) {
            final double[] n = unit.normal();
            projectedDist = Math.abs((pixelX - unitCoord[0]) * n[0] + (pixelZ - unitCoord[1]) * n[1]);
        } else {
            projectedDist = Math.hypot(pixelX - unitCoord[0], pixelZ - unitCoord[1]);
        }
        final double referenceElev = Math.min(unit.elevation(), decodedElev);
        final RosgenType type = unit.rosgenType() == null ? RosgenType.A : unit.rosgenType();
        final double delta =
                RosgenProfile.of(type).elevationDelta(projectedDist, unit.width(), decodedElev - referenceElev);
        return referenceElev + delta;
    }
}
