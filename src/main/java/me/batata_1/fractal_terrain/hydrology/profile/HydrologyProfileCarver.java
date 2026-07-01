package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;

/**
 * The elevation side of the hydrology profile. Given a world position and its pre-carve (decoded)
 * elevation, it queries the per-tile river network ({@link LocalRiverProvider#queryInfluence}) and pulls
 * the terrain toward each nearby feature's channel bed / floodplain / blend profile (see
 * {@link RosgenProfile}), merging multiple features by distance.
 *
 * <p>This is the per-pixel <em>refinement</em> applied in {@code PopulateNoiseStep} on top of the
 * tile-precomputed carve; {@code RIVER_DIFFERENCE = carve(...) − preCarveElev}. It is the carving twin of
 * {@code HydrologyProfilePainter} and shares the {@link HydrologyProfile} iterate primitive.
 */
public final class HydrologyProfileCarver {

    private final LocalRiverProvider localRiver;

    public HydrologyProfileCarver(LocalRiverProvider localRiver) {
        this.localRiver = localRiver;
    }

    /** Convenience: resolve the live {@link LocalRiverProvider} from the singleton. */
    public HydrologyProfileCarver() {
        this(FractalTerrainInstance.getLocalRiverProvider());
    }

    /**
     * Refined elevation at world/block coordinates {@code (worldBlockX, worldBlockZ)} whose pre-carve
     * (decoded) elevation is {@code preCarveElev}. World coords are converted into the relief-pixel frame
     * (divide by {@link FractalTerrainConfig#GLOBAL_SCALE_CORRECTION}) that the unit query uses. Returns
     * {@code preCarveElev} unchanged when no feature influences the point.
     */
    public float carve(double worldBlockX, double worldBlockZ, double preCarveElev) {
        final double pixelX = worldBlockX / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final double pixelZ = worldBlockZ / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        return carveAtPixel(pixelX, pixelZ, preCarveElev);
    }

    /**
     * Carve at a point already in the relief-pixel frame. Each influencing feature (id-run) contributes a
     * target elevation from its {@link RosgenProfile}; contributions are merged as a distance-weighted
     * average (weight 1 at the channel, 0 at the feature's influence radius), so a lone feature reproduces
     * its profile exactly and overlapping features blend smoothly.
     */
    public float carveAtPixel(double pixelX, double pixelZ, double decodedElev) {
        final HydrologicalUnit[] units = localRiver.queryInfluence(new double[] {pixelX, pixelZ});
        if (units.length == 0) return (float) decodedElev;

        // [weightedElevationSum, weightTotal] — boxed so the lambda can accumulate.
        final double[] accumulator = {0.0, 0.0};
        final double[] queryPoint = {pixelX, pixelZ};
        HydrologyProfile.forEachIdRun(units, queryPoint, (run, start, end, nearestIdx, nearestDist) -> {
            final HydrologicalUnit nearest = run[nearestIdx];
            final double influence = FractalTerrainConfig.maxRiverInfluence(nearest.width());
            if (nearestDist >= influence) return;
            // Uncarvable: if this feature's intended bed is too far from the decoded terrain, skip it
            // entirely (its influence does not pull neighbours), so we never gouge holes or trenches.
            if (Math.abs(nearest.elevation() - decodedElev) > FractalTerrainConfig.MAX_CARVE_DELTA) return;

            final double featureElev = featureElevation(pixelX, pixelZ, nearest, decodedElev);
            final double weight = 1.0 - nearestDist / influence;
            accumulator[0] += featureElev * weight;
            accumulator[1] += weight;
        });

        if (accumulator[1] == 0.0) return (float) decodedElev;
        return (float) (accumulator[0] / accumulator[1]);
    }

    /**
     * Target elevation for a single feature: project the query point onto the line through the unit's
     * coordinate along its normal, take the perpendicular distance as the profile parameter, and add the
     * {@link RosgenProfile} delta to the reference elevation {@code min(unit.elevation, decodedElev)}.
     */
    private static double featureElevation(double pixelX, double pixelZ, HydrologicalUnit unit, double decodedElev) {
        final double unitX = unit.coord().get(0);
        final double unitZ = unit.coord().get(1);
        final double projectedDist;
        if (unit.normal() != null) {
            final double nx = unit.normal().get(0);
            final double nz = unit.normal().get(1);
            projectedDist = Math.abs((pixelX - unitX) * nx + (pixelZ - unitZ) * nz);
        } else {
            projectedDist = Math.hypot(pixelX - unitX, pixelZ - unitZ);
        }
        final double referenceElev = Math.min(unit.elevation(), decodedElev);
        final RosgenType type = unit.rosgenType() == null ? RosgenType.A : unit.rosgenType();
        final double floodPlainLength = FractalTerrainConfig.floodPlainLength(unit.width());
        final double delta = RosgenProfile.of(type)
                .elevationDelta(projectedDist, unit.width(), floodPlainLength, decodedElev - referenceElev);
        return referenceElev + delta;
    }
}
