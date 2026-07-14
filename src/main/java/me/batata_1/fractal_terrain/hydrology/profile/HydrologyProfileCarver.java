package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;

/**
 * The elevation side of the hydrology profile. Two carve stages live here:
 *
 * <ul>
 *   <li><b>Per-pixel refinement</b> ({@link #carve}, {@link #carveAtPixel}, {@link #carvePrefetched}) —
 *       queries the per-tile river network ({@link LocalRiverProvider#queryInfluence}) and takes the
 *       minimum (deepest) {@link HydrologyProfile#computeForUnit} elevation over every influencing unit —
 *       the bed residual cut below the already-carved shell. Applied in {@code PopulateNoiseStep} on top
 *       of the tile shell carve; {@code RIVER_DIFFERENCE = carve(...) − shellElev}.</li>
 *   <li><b>Tile-level shell pre-carve</b> ({@link #carveRiverShells}, static) — the valley/floodplain
 *       shell carve run once per relief tile (both global and local passes) by
 *       {@code LocalRiverProvider.buildTile}, pulling the decoded elevation toward each unit's shell
 *       floor (bank + freeboard).</li>
 * </ul>
 *
 * All geometry is in the relief-pixel frame; only {@link #carve} converts from world/block coordinates
 * (divide by {@link FractalTerrainConfig#GLOBAL_SCALE_CORRECTION}). This is the carving twin of
 * {@code HydrologyProfilePainter}.
 */
public final class HydrologyProfileCarver {

    private final LocalRiverProvider localRiver;

    public HydrologyProfileCarver(LocalRiverProvider localRiver) {
        this.localRiver = localRiver;
    }

    // -------------------------------------------------------------------------
    // Per-pixel refinement (nearest influencing unit)
    // -------------------------------------------------------------------------

    /**
     * Refined elevation at world/block coordinates {@code (worldBlockX, worldBlockZ)} whose already-carved
     * shell elevation is {@code shellElevAtPixel}. World coords are converted into the relief-pixel frame
     * (divide by {@link FractalTerrainConfig#GLOBAL_SCALE_CORRECTION}) that the unit query uses. Returns
     * {@code shellElevAtPixel} unchanged when no feature influences the point.
     */
    public float carve(double worldBlockX, double worldBlockZ, double shellElevAtPixel) {
        final double pixelX = worldBlockX / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final double pixelZ = worldBlockZ / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        return carveAtPixel(pixelX, pixelZ, shellElevAtPixel);
    }

    /**
     * A chunk's influencing units, gathered once (see {@link #queryUnits}) so the per-block merge never
     * re-queries the spatial index. Produced by {@link #prefetchChunk} / {@link #queryUnits} and consumed
     * by {@link #carvePrefetched}.
     */
    public record PrefetchedUnits(HydrologicalUnit[] units) {}

    /**
     * Carve at a point already in the relief-pixel frame: query the influencing units, then merge via
     * {@link #carvePrefetched}.
     */
    public float carveAtPixel(double pixelX, double pixelZ, double shellElevAtPixel) {
        final PrefetchedUnits prefetched = queryUnits(new double[] {pixelX, pixelZ}, 0.0);
        return carvePrefetched(prefetched, pixelX, pixelZ, shellElevAtPixel);
    }

    /**
     * One cross-tile influence query serving a whole chunk of carve calls: gathers every unit that could
     * influence <em>any</em> point within {@code chunkRadiusPx} of {@code (centerPixelX, centerPixelZ)}
     * (a unit is kept when the center lies within {@code maxRiverInfluence(unit.width()) +
     * chunkRadiusPx}). Feed the result to {@link #carvePrefetched} for each block — one tree query per
     * chunk instead of one per block. All arguments are in the relief-pixel frame.
     */
    public PrefetchedUnits prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        return queryUnits(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
    }

    /** Query the units influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedUnits queryUnits(double[] pt, double extraRadius) {
        return new PrefetchedUnits(localRiver.queryInfluence(pt, extraRadius));
    }

    /**
     * The merge core, over an already-gathered {@link PrefetchedUnits} (world relief-pixel coords, e.g.
     * from {@link #prefetchChunk}). The point's elevation is the <b>minimum</b> (deepest) of
     * {@link HydrologyProfile#computeForUnit} over every prefetched unit that reaches the point (radial
     * distance below that unit's own {@link FractalTerrainConfig#riverInfluence influence} radius) — since
     * {@code computeForUnit} returns {@code shellElevAtPixel + bedResidualDelta}, this is a min-composite
     * bed carve consistent with the shell kernel's own min-composite: the deepest covering channel wins at
     * a confluence, with no additive double-deepening (both channels share the same
     * {@code shellElevAtPixel} anchor) and no abrupt mid-channel Voronoi seam between overlapping bed
     * half-widths. {@code shellElevAtPixel} is the fallback returned unchanged when no unit reaches the
     * point.
     */
    public float carvePrefetched(PrefetchedUnits prefetched, double pixelX, double pixelZ, double shellElevAtPixel) {
        final HydrologicalUnit[] units = prefetched.units();
        double target = shellElevAtPixel;
        boolean anyInfluence = false;
        for (final HydrologicalUnit unit : units) {
            final double influenceRadius = unit.getRadius(); // = riverInfluence(unit.width()), the circle's own radius
            final double[] unitCoord = unit.coord();
            final double dist = Math.hypot(pixelX - unitCoord[0], pixelZ - unitCoord[1]);
            if (dist >= influenceRadius) continue; // outside this unit's reach
            anyInfluence = true;
            final double unitElev = HydrologyProfile.computeForUnit(pixelX, pixelZ, unit, shellElevAtPixel);
            target = Math.min(target, unitElev);
        }

        if (!anyInfluence) return (float) shellElevAtPixel;
        return (float) target;
    }

    // -------------------------------------------------------------------------
    // Tile-level shell pre-carve (moved from LocalRiverProvider)
    // -------------------------------------------------------------------------

    /** Slack around the tile grid for the unit index bounds (units may overshoot the pad). */
    private static final double CARVE_INDEX_SLACK = 1024.0;

    /**
     * Tile-level valley/floodplain shell carve (distinct from the per-pixel bed-residual refinement
     * above): indexes {@code units} in an {@link ImmutableQuadTree} and, for every grid pixel, composes
     * every nearby unit's {@link RosgenProfile#riverInfluenceElevation} via {@code min()} of an absolute floor target
     * (never a relative subtract) — so global-then-local carves on the same buffer never double-deepen a
     * confluence, regardless of call order. Each unit's {@code shellDelta} is computed against
     * {@code ambientSnapshot} — the pristine, pre-carve original terrain — rather than the live buffer, so
     * both the global and local passes derive their falloff lerp from the same original terrain and the
     * min-composite is genuinely order-independent near confluences. The lens mask inside
     * {@code shellDelta} forces the delta to 0 at each unit's own {@code riverInfluence}, so units outside
     * a pixel's radius simply don't contribute. {@link FractalTerrainConfig#MAX_CARVE_DELTA} is evaluated
     * against the live (pre-this-call) buffer value: a pixel whose composed target strays too far from its
     * current elevation is uncarvable and left unchanged, so isolated deep shells never gouge holes or
     * trenches. Static so both the global and local {@code LocalRiverProvider.buildTile} passes share one
     * implementation.
     *
     * @param elevation flattened row-major {@code paddedSize × paddedSize} grid, carved in place
     * @param ambientSnapshot pristine (pre-carve) original terrain, same layout as {@code elevation}, used
     *     only to compute each unit's shell-delta falloff — never mutated
     * @param units hydrological units (global or local) to carve into the grid
     * @param paddedSize grid side length ({@code LocalRiverProvider} passes {@code PADDED} = 514)
     */
    public static void carveRiverShells(
            float[] elevation, HydrologicalUnit[] units, int paddedSize) {
        if (units.length == 0) return;
        final ImmutableQuadTree<HydrologicalUnit> index = new ImmutableQuadTree<>(
                new double[] {-CARVE_INDEX_SLACK, -CARVE_INDEX_SLACK},
                new double[] {paddedSize + CARVE_INDEX_SLACK, paddedSize + CARVE_INDEX_SLACK},
                Arrays.asList(units));

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalUnit> nearby =
                        index.getPointsInCircle(pixel, FractalTerrainConfig.MAX_INFLUENCE_RADIUS);
                if (nearby.isEmpty()) continue;

                final double curElev = elevation[idx];
                double target = ambient;
                for (HydrologicalUnit unit : nearby) {
                    final double[] coord = unit.coord();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);
                    if (radialDist >= unit.getRadius()) continue; // outside this unit's influence

                    final double perpDist;
                    final double alongDist;
                    if (unit.normal() != null) {
                        final double[] n = unit.normal();
                        perpDist = Math.abs(dx * n[0] + dz * n[1]);
                        alongDist = Math.sqrt(Math.max(0.0, radialDist * radialDist - perpDist * perpDist));
                    } else {
                        perpDist = radialDist;
                        alongDist = 0.0;
                    }

                    final RosgenType type = unit.rosgenType() == null ? RosgenType.A : unit.rosgenType();
                    final RosgenProfile profile = RosgenProfile.of(type);
                    final double elev = profile.riverInfluenceElevation(radialDist, unit.width(), curElev, unit.elevation());
                    target = Math.min(target, elev);
                }

                elevation[idx] = (float) target;
            }
        }
    }

}
