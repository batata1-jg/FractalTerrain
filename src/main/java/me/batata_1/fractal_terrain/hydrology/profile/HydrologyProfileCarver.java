package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexPoint;
import me.batata_1.fractal_terrain.math.spline.QuinticHermiteSpline;
import me.batata_1.fractal_terrain.relief.ReliefProvider;

/**
 * The elevation side of the hydrology profile. Two carve stages live here:
 *
 * <ul>
 *   <li><b>Per-pixel refinement</b> ({@link #carve}, {@link #carveAtPixel}, {@link #carvePrefetched}) —
 *       queries the per-tile river network ({@link LocalRiverProvider#queryInfluence}) and merges every
 *       influencing unit's {@link HydrologyProfile#computeForUnit} elevation in one flat
 *       distance-weighted average. Applied in {@code PopulateNoiseStep} on top of the tile pre-carve;
 *       {@code RIVER_DIFFERENCE = carve(...) − preCarveElev}.</li>
 *   <li><b>Tile-level pre-carve</b> ({@link #carveGlobalRivers}, static) — the ch0 carve run once per
 *       relief tile by {@code LocalRiverProvider.buildTile}, pulling the decoded elevation toward the
 *       global-river channel beds.</li>
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
    // Per-pixel refinement (flat distance-weighted merge over all units)
    // -------------------------------------------------------------------------

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
     * A chunk's influencing units paired with each unit's own decoded pre-carve elevation
     * ({@code decodedElevAtUnit[i]} is the elevation for {@code units[i]}). The decoded elevations are
     * sampled once per unit (see {@link #queryUnits}) so the per-block merge never re-queries the
     * relief provider. Produced by {@link #prefetchChunk} / {@link #queryUnits} and consumed by
     * {@link #carvePrefetched}.
     */
    public record PrefetchedUnits(HydrologicalUnit[] units, double[] decodedElevAtUnit) {}

    /**
     * Carve at a point already in the relief-pixel frame: query the influencing units, then merge via
     * {@link #carvePrefetched}.
     */
    public float carveAtPixel(double pixelX, double pixelZ, double decodedElevAtPixel) {
        final PrefetchedUnits prefetched = queryUnits(new double[] {pixelX, pixelZ}, 0.0);
        return carvePrefetched(prefetched, pixelX, pixelZ, decodedElevAtPixel);
    }

    /**
     * One cross-tile influence query serving a whole chunk of carve calls: gathers every unit that could
     * influence <em>any</em> point within {@code chunkRadiusPx} of {@code (centerPixelX, centerPixelZ)}
     * (a unit is kept when the center lies within {@code maxRiverInfluence(unit.width()) +
     * chunkRadiusPx}), and samples each unit's decoded pre-carve elevation once. Feed the result to
     * {@link #carvePrefetched} for each block — one tree query per chunk instead of one per block. All
     * arguments are in the relief-pixel frame.
     */
    public PrefetchedUnits prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        return queryUnits(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
    }

    /**
     * Query the units influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}) and
     * sample each unit's decoded pre-carve elevation at its own coordinate — the same smoothstep over the
     * carved+filled ch0 that produces the pixel's own pre-carve elevation ({@code Types.ELEVATION}).
     */
    private PrefetchedUnits queryUnits(double[] pt, double extraRadius) {
        final HydrologicalUnit[] units = localRiver.queryInfluence(pt, extraRadius);
        final double[] decodedElevAtUnit = new double[units.length];
        final ReliefProvider relief = FractalTerrainInstance.getReliefProvider();
        final int[] nodePos = new int[3]; // [CH, x, z]; reused across units
        final float[] nodes = new float[4]; // 2x2 interpolation stencil; reused across units
        for (int i = 0; i < units.length; i++) {
            final double[] coord = units[i].coord();
            decodedElevAtUnit[i] = Interpolation.interpolateSmoothStep(
                    (float) coord[0], (float) coord[1], nodePos, nodes, relief::getElev);
        }
        return new PrefetchedUnits(units, decodedElevAtUnit);
    }

    /**
     * The merge core, over an already-gathered {@link PrefetchedUnits} (world relief-pixel coords, e.g.
     * from {@link #prefetchChunk}). <b>Every</b> influencing unit contributes its
     * {@link HydrologyProfile#computeForUnit} elevation, weighted linearly by distance: weight 1 when the
     * point sits on the unit, 0 at that unit's own
     * {@link FractalTerrainConfig#riverInfluence influence} radius. Contributions from all units of
     * all features are merged in one flat weighted average — a lone straight channel averages many
     * near-identical contributions (≈ its exact profile), and river intersections blend smoothly because
     * every channel's units participate. Each unit's cross-section is anchored on its own decoded
     * elevation ({@code decodedElevAtUnit[i]}); {@code decodedElevAtPixel} is only the outer blend target.
     * Returns {@code decodedElevAtPixel} unchanged when no unit reaches the point.
     */
    public float carvePrefetched(PrefetchedUnits prefetched, double pixelX, double pixelZ, double decodedElevAtPixel) {
        final HydrologicalUnit[] units = prefetched.units();
        final double[] decodedElevAtUnit = prefetched.decodedElevAtUnit();
        double weightedElevSum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < units.length; i++) {
            final HydrologicalUnit unit = units[i];
            final double influenceRadius = unit.getRadius(); // = riverInfluence(unit.width()), the circle's own radius
            final double[] unitCoord = unit.coord();
            final double dist = Math.hypot(pixelX - unitCoord[0], pixelZ - unitCoord[1]);
            if (dist >= influenceRadius) continue; // outside this unit's reach
            final double weight = 1.0 - dist / influenceRadius; // 1 at the unit, 0 at the influence edge
            weightedElevSum +=
                    HydrologyProfile.computeForUnit(pixelX, pixelZ, unit, decodedElevAtPixel, decodedElevAtUnit[i])
                            * weight;
            weightSum += weight;
        }

        if (weightSum == 0.0) return (float) decodedElevAtPixel;
        return (float) (weightedElevSum / weightSum);
    }

    // -------------------------------------------------------------------------
    // Tile-level global-river pre-carve (moved from LocalRiverProvider)
    // -------------------------------------------------------------------------

    /** Densification interval along the channel splines (native px). */
    private static final double CARVE_SAMPLE_SPACING = 2.0;

    /** Slack around the tile grid for the sample index bounds (channels may overshoot the pad). */
    private static final double CARVE_INDEX_SLACK = 1024.0;

    /** A densified river sample carrying its interpolated width + bed elevation. */
    private record RiverSample(double[] coords, double width, double bedElev) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return coords;
        }
    }

    /**
     * Tile-level global-river carve (the ch0 pre-carve, distinct from the per-pixel refinement above):
     * densify each channel spline into {@link RiverSample}s every {@link #CARVE_SAMPLE_SPACING} px, index
     * them in an {@link ImmutableQuadTree}, and pull every grid pixel within
     * {@link FractalTerrainConfig#riverInfluence riverInfluence(width)} of its nearest sample
     * toward that sample's bed (full bed inside {@code width/2}, linear blend back to the original
     * terrain at {@code riverInfluence}). {@link FractalTerrainConfig#MAX_CARVE_DELTA} lives
     * <em>here only</em>: a pixel whose {@code |original − bed|} exceeds it is uncarvable and skipped, so
     * isolated deep beds never gouge holes or trenches. Static so {@code LocalRiverProvider.buildTile}
     * and future callers share one implementation.
     *
     * @param elevation flattened row-major {@code paddedSize × paddedSize} grid, carved in place
     * @param channels global channels with {@code bedElevations} assigned
     * @param paddedSize grid side length ({@code LocalRiverProvider} passes {@code PADDED} = 514)
     */
    public static void carveGlobalRivers(float[] elevation, List<Channel> channels, int paddedSize) {
        if (channels.isEmpty()) return;
        final List<RiverSample> samples = new ArrayList<>();
        for (Channel ch : channels) {
            if (ch.bedElevations == null || ch.numPts() < 2) continue;
            final QuinticHermiteSpline spline = ch.spline;
            final int segments = ch.numPts() - 1;
            final double[] pointWidths = pointWidths(ch);
            for (int i = 0; i < segments; i++) {
                final double segLength = VectorOps.distance(
                        spline.points().get(i), spline.points().get(i + 1));
                final int sampleCount = Math.max(1, (int) Math.ceil(segLength / CARVE_SAMPLE_SPACING));
                final double startWidth = pointWidths[i];
                final double endWidth = pointWidths[i + 1];
                final double startBed = ch.bedElevations[i];
                final double endBed = ch.bedElevations[i + 1];
                for (int s = 0; s <= sampleCount; s++) {
                    final double frac = (double) s / sampleCount;
                    final double[] point = spline.sample(i + frac);
                    final double width = startWidth + (endWidth - startWidth) * frac;
                    final double bed = startBed + (endBed - startBed) * frac;
                    samples.add(new RiverSample(new double[] {point[0], point[1]}, width, bed));
                }
            }
        }
        final ImmutableQuadTree<RiverSample> index = new ImmutableQuadTree<>(
                new double[] {-CARVE_INDEX_SLACK, -CARVE_INDEX_SLACK},
                new double[] {paddedSize + CARVE_INDEX_SLACK, paddedSize + CARVE_INDEX_SLACK},
                samples);

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                if (elevation[idx] < 0) continue;
                final double[] pixel = {pi, pj};
                final List<RiverSample> nearby =
                        index.getPointsInCircle(pixel, FractalTerrainConfig.MAX_INFLUENCE_RADIUS);
                if (nearby.isEmpty()) continue;
                double nearestDist = Double.MAX_VALUE;
                double width = 0;
                double bedElev = 0;
                for (RiverSample sample : nearby) {
                    final double dist = VectorOps.distance(pixel, sample.coords());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        width = sample.width();
                        bedElev = sample.bedElev();
                    }
                }
                if (nearestDist >= FractalTerrainConfig.MAX_INFLUENCE_RADIUS) continue;
                // Uncarvable pixel: the intended bed is too far below/above the current terrain —
                // carving would gouge an isolated hole or trench. MAX_CARVE_DELTA applies ONLY here.
                if (Math.abs(elevation[idx] - bedElev) > FractalTerrainConfig.MAX_CARVE_DELTA) continue;
                final double bedHalfWidth = ChannelGeometry.bedHalfWidth(width);
                final double riverInfluenceRadius = FractalTerrainConfig.riverInfluence(width);
                if (nearestDist >= riverInfluenceRadius) continue;
                final float orig = elevation[idx];
                final double frac = (nearestDist <= bedHalfWidth)
                        ? 0.0
                        : Math.min(1.0, (nearestDist - bedHalfWidth) / (riverInfluenceRadius - bedHalfWidth));
                elevation[idx] = (float) (bedElev + (orig - bedElev) * frac);
            }
        }
    }

    /** Per-point widths lerped along the channel's start→end taper. */
    private static double[] pointWidths(Channel ch) {
        final int n = ch.numPts();
        final double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            final double frac = (n == 1) ? 0.0 : (double) i / (n - 1);
            w[i] = ch.startWidth + (ch.endWidth - ch.startWidth) * frac;
        }
        return w;
    }
}
