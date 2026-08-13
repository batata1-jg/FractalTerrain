package me.batata_1.fractal_terrain.hydrology.profile;

import java.util.Arrays;
import java.util.List;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.LocalRiverProvider;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.math.VectorOps;
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.TestOnly;

/**
 * The elevation side of the hydrology profile — where rivers actually cut the terrain.
 *
 * <p>Split across two stages because they run at different times against different data: the tile-level
 * shell carve broad-brushes the valley during {@code LocalRiverProvider.buildTile}, and the per-pixel
 * refinement cuts the bed trench below it later, driven per chunk from {@code PopulateNoiseStep}.
 *
 * <p>All geometry is in the relief-pixel frame. The carving twin of {@code HydrologyProfilePainter}.
 */
public final class HydrologyProfileInprinter {

    private final LocalRiverProvider localRiver;

    public HydrologyProfileInprinter(LocalRiverProvider localRiver) {
        this.localRiver = localRiver;
    }

    public static double[] resolveRiverPrimitives(
            ChunkPos chunkPos,
            double scale,
            List<HydrologicalPrimitive> primitives,
            float[] ambientElevation,
            HydrologicalPrimitive.HydrologicalFeature[] riverType,
            float[] waterElev) {
        final int startX = chunkPos.getMinBlockX();
        final int startZ = chunkPos.getMinBlockZ();

        final double[] columns = new double[256 * 2];
        final double[] mutablePt = new double[2];

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                riverType[pos] = null;
                mutablePt[0] = (startX + dx) / scale;
                mutablePt[1] = (startZ + dz) / scale;

                final int nearestPrimitiveIndex =
                        HydrologyProfileInprinter.resolveNearestPrimitiveIndex(primitives, mutablePt);
                if (nearestPrimitiveIndex == -1) continue;
                if (!primitives.get(nearestPrimitiveIndex).containsPoint(mutablePt)) continue;

                final NearestChannelSample sample =
                        HydrologyProfileInprinter.sampleNearestChannel(primitives, nearestPrimitiveIndex, mutablePt);
                if (sample == null) continue;

                columns[pos * 2] = sample.carveInto(ambientElevation[pos]);
                columns[pos * 2 + 1] = 1;

                if (Math.abs(sample.signedPerpDist()) <= (sample.channelWidth() / 2) + 0.25) {
                    riverType[pos] = HydrologicalPrimitive.HydrologicalFeature.RIVER;
                    waterElev[pos] = (float) (HydrologicalPrimitive.waterLine(sample.channelWidth())
                            + sample.bedElevation());
                }
            }
        }
        return columns;
    }

    // -------------------------------------------------------------------------
    // Per-pixel refinement (zone-priority merge)
    // -------------------------------------------------------------------------

    /**
     * A chunk's influencing primitives, gathered once (see {@link #queryPrimitives}) so the per-block carve never
     * re-queries the spatial index. Produced by {@link #prefetchChunk} / {@link #queryPrimitives} and consumed
     * by {@link #resolveNearestPrimitiveIndex} / {@link #sampleNearestChannel}.
     */
    public record PrefetchedPrimitives(List<HydrologicalPrimitive> primitives) {}

    /** Amortizes the influence query across a whole chunk — one tree query per chunk rather than one
     *  per block. Feed the result to {@link #sampleNearestChannel}. */
    public List<HydrologicalPrimitive> prefetchChunk(double centerPixelX, double centerPixelZ, double chunkRadiusPx) {
        var primitives = localRiver.queryInfluence(new double[] {centerPixelX, centerPixelZ}, chunkRadiusPx);
        primitives.sort(HydrologicalPrimitive.comparator);
        return primitives;
    }

    /** Query the primitives influencing {@code pt} (relief-pixel frame, inflated by {@code extraRadius}). */
    private PrefetchedPrimitives queryPrimitives(double[] pt, double extraRadius) {
        return new PrefetchedPrimitives(localRiver.queryInfluence(pt, extraRadius));
    }

    /** Index into {@code primitives} of the river knot whose coordinate is nearest {@code point}, or
     *  -1 when none is. Relies on the comparator sorting rivers first, so the scan can stop early. */
    public static int resolveNearestPrimitiveIndex(List<HydrologicalPrimitive> primitives, double[] point) {
        int nearestIndex = -1;
        double nearestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < primitives.size(); i++) {
            if (!(primitives.get(i) instanceof RiverPrimitive river)) break;
            final double distSq = VectorOps.distanceSquared(point, river.coord());
            if (distSq >= nearestDistSq) continue;
            nearestIndex = i;
            nearestDistSq = distSq;
        }
        return nearestIndex;
    }

    /**
     * The signed perpendicular distance from {@code point} to the nearest channel, with that channel's
     * cross-section read at the foot point.
     *
     * <p>Measures against the two-segment polyline through the nearest knot rather than the quintic:
     * the primitives carry no velocity or acceleration, so the true curve cannot be rebuilt here.
     */
    public static NearestChannelSample sampleNearestChannel(
            List<HydrologicalPrimitive> primitives, int nearestPrimitiveIndex, double[] point) {
        if (nearestPrimitiveIndex < 0 || nearestPrimitiveIndex >= primitives.size()) return null;
        if (!(primitives.get(nearestPrimitiveIndex) instanceof RiverPrimitive nearestKnot)) return null;

        final double[] projection = new double[2];
        RiverPrimitive segStart = null;
        RiverPrimitive segEnd = null;
        double bestSegParam = 0.0;
        double bestDistSq = Double.MAX_VALUE;

        for (int offset = -1; offset <= 1; offset += 2) {
            final int neighbourIndex = nearestPrimitiveIndex + offset;
            if (neighbourIndex < 0 || neighbourIndex >= primitives.size()) continue;
            if (!(primitives.get(neighbourIndex) instanceof RiverPrimitive neighbour)) continue;
            if (!nearestKnot.isKnotAdjacentTo(neighbour)) continue;
            // Always orient the segment downstream, so segParam interpolates from the lower knot up.
            final boolean neighbourIsUpstream = neighbour.knotIndex() < nearestKnot.knotIndex();
            final RiverPrimitive start = neighbourIsUpstream ? neighbour : nearestKnot;
            final RiverPrimitive end = neighbourIsUpstream ? nearestKnot : neighbour;
            VectorOps.projectPointOntoSegment(point, start.coord(), end.coord(), projection);
            if (projection[1] >= bestDistSq) continue;
            bestDistSq = projection[1];
            bestSegParam = projection[0];
            segStart = start;
            segEnd = end;
        }

        if (segStart == null) return isolatedKnotSample(nearestKnot, point);
        return interpolatedSample(segStart, segEnd, bestSegParam, bestDistSq, point);
    }

    /** A knot with no knot-adjacent neighbour in range influences the point alone, so its tangent
     *  line is the correct answer — not a degraded one. */
    private static NearestChannelSample isolatedKnotSample(RiverPrimitive knot, double[] point) {
        return new NearestChannelSample(
                knot.d(point), knot.width(), knot.curvature(), knot.elevation(), knot.rosgenType(), knot.channelId());
    }

    /** Signs the distance with the interpolated normal so it agrees with {@link RiverPrimitive#d},
     *  which is the convention the asymmetric Rosgen profiles were tuned against. */
    private static NearestChannelSample interpolatedSample(
            RiverPrimitive segStart, RiverPrimitive segEnd, double segParam, double distSq, double[] point) {
        final double footX = lerp(segStart.coord()[0], segEnd.coord()[0], segParam);
        final double footZ = lerp(segStart.coord()[1], segEnd.coord()[1], segParam);
        final double[] lerpedNormal = {
            lerp(segStart.normal()[0], segEnd.normal()[0], segParam),
            lerp(segStart.normal()[1], segEnd.normal()[1], segParam)
        };
        // Near-opposite knot normals (a sharp bend) lerp toward the zero vector, which normalize()
        // would hand back as-is; that silently zeroes signedPerpDist. Fall back to segStart's normal
        // — it is already unit-length and, being the lower-index (upstream) knot, the natural default.
        final double[] footNormal =
                VectorOps.magnitude(lerpedNormal) < 1e-12 ? segStart.normal() : VectorOps.normalize(lerpedNormal);
        final double side = footNormal[0] * (point[0] - footX) + footNormal[1] * (point[1] - footZ);
        // A point exactly on the centreline has no bank to prefer; call it the positive bank rather
        // than let Math.signum(0.0) collapse the real distance to zero.
        final double sideSign = side == 0.0 ? 1.0 : Math.signum(side);
        return new NearestChannelSample(
                sideSign * Math.sqrt(distSq),
                lerp(segStart.width(), segEnd.width(), segParam),
                lerp(segStart.curvature(), segEnd.curvature(), segParam),
                lerp(segStart.elevation(), segEnd.elevation(), segParam),
                segParam < 0.5 ? segStart.rosgenType() : segEnd.rosgenType(),
                segStart.channelId());
    }

    private static double lerp(double from, double to, double t) {
        return from + t * (to - from);
    }

    // -------------------------------------------------------------------------
    // Tile-level shell pre-carve (moved from LocalRiverProvider)
    // -------------------------------------------------------------------------

    /** Slack around the tile grid for the primitive index bounds (primitives may overshoot the pad). */
    private static final double CARVE_INDEX_SLACK = 64.0;

    /** Carves the valley shell in place. Does not zone — the shell is one broad pull, applied before any
     *  primitive has a bed to distinguish. Compounds across calls on the same buffer, which
     *  {@code buildTile} relies on when it carves twice per tile. */
    public static void carveRiverShells(float[] elevation, HydrologicalPrimitive[] primitives, int paddedSize) {
        //       carveRiverShellsNearest(elevation, primitives, paddedSize);
        if (primitives.length == 0) return;
        final ImmutableRTree<HydrologicalPrimitive> index =
                new ImmutableRTree<>(Arrays.asList(primitives), HydrologicalPrimitive.PROTOTYPE);

        double weight = 0;
        double weightedElev = 0;
        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalPrimitive> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;
                weight = 0;
                weightedElev = 0;
                for (final HydrologicalPrimitive primitive : nearby) {
                    if (!primitive.containsPoint(pixel)) continue;
                    if (primitive instanceof RiverPrimitive river) {
                        final double deltaWeight = river.w(pixel);
                        weight += deltaWeight;
                        weightedElev += deltaWeight * river.h(pixel);
                    }
                }
                if (weight <= 1e-8) continue;
                final double elev = weightedElev / weight;
                weight = Math.clamp(weight, 0, 1);
                elevation[idx] = (float) ((1 - weight) * elevation[idx] + weight * elev);
            }
        }
    }

    @TestOnly
    public static void carveRiverShellsNearest(float[] elevation, HydrologicalPrimitive[] primitives, int paddedSize) {
        if (primitives.length == 0) return;
        final ImmutableRTree<HydrologicalPrimitive> index =
                new ImmutableRTree<>(Arrays.asList(primitives), HydrologicalPrimitive.PROTOTYPE);

        for (int pi = 0; pi < paddedSize; pi++) {
            for (int pj = 0; pj < paddedSize; pj++) {
                final int idx = pi * paddedSize + pj;
                final float ambient = elevation[idx];
                if (ambient < 0) continue;
                final double[] pixel = {pi, pj};
                final List<HydrologicalPrimitive> nearby = index.queryContaining(pixel);
                if (nearby.isEmpty()) continue;

                HydrologicalPrimitive nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (final HydrologicalPrimitive primitive : nearby) {
                    final double[] coord = primitive.coord();
                    final double dx = pixel[0] - coord[0];
                    final double dz = pixel[1] - coord[1];
                    final double radialDist = Math.hypot(dx, dz);

                    if (!primitive.containsPoint(pixel)) continue; // outside this primitive's influence
                    if (radialDist < nearestDist && primitive instanceof RiverPrimitive river) {
                        nearestDist = radialDist;
                        nearest = river;
                    }
                }

                if (nearest == null) continue;
                elevation[idx] = (float) nearest.getProfile().shellElevation(nearest, nearestDist, elevation[idx]);
            }
        }
    }
}
