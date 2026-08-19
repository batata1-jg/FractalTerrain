package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.slf4j.Logger;

public class PopulateNoiseStep {

    private static final BlockState DEFAULT_ROCK = Blocks.STONE.defaultBlockState();
    private static final Logger LOG = getLogger(PopulateNoiseStep.class);
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

    /** Columns in a chunk; the length unit of every per-column array below. */
    private static final int COLUMNS = 16 * 16;

    /** Stride of the (elevation, weight) pairs {@link #resolveRiverPrimitives} returns. */
    private static final int PAIR = 2;

    /** Offset of the weight within a pair; the elevation sits at offset 0. */
    private static final int WEIGHT = 1;

    /** The weight a resolved river column carries. The carve is a hard {@code Math.min} against
     *  ambient, so a river either owns its column or does not touch it — there is no partial blend yet.
     *  Expressing that as a weight anyway is what lets a second primitive family join the merge without
     *  restructuring the loop. */
    private static final double FULL_WEIGHT = 1.0;

    // Testing overrides: FULL_WEIGHT above stands in for a primitive's real influence weight, and
    // SMOOTH_STEP_DIVISOR below collapses the distance blend to a near-hard 0/1 selector. Restoring the
    // real blend needs HydrologyTuning.PRIMITIVE_BLEND_STRENGTH in SMOOTH_STEP_DIVISOR's place, and the
    // squared river.w(pt) result computed and used as the influence weight in place of FULL_WEIGHT.
    private static final double SMOOTH_STEP_DIVISOR = 0.1;

    /** Initial per-column "no primitive seen yet" distance, in relief-pixels. */
    private static final double UNSET_MIN_DIST = 64;

    private final NoiseGeneratorSettings settings;

    public PopulateNoiseStep(NoiseGeneratorSettings settings) {
        this.settings = settings;
    }

    public static double smoothStep(double r0, double r1, double x) {
        final double t = Math.clamp((x - r0) / (r1 - r0), 0, 1);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Cuts the river bed into the tile-carved shell, the last elevation pass before blocks are
     *  placed. The shell-to-bed delta lands in {@link Types#RIVER_DIFFERENCE}, which is what the
     *  water fill later reads. */
    public void fineGrainedPrimitivePass(ChunkPos chunkPos, FractalTerrainHeightmap heightmap) {
        final int seaLevel = settings.seaLevel();
        final int bottom = settings.noiseSettings().minY();
        final float[] interpolatedElevs = (float[]) heightmap.get(Types.ELEVATION);
        final float[] riverDifference = (float[]) heightmap.get(Types.RIVER_DIFFERENCE);
        final HydrologicalPrimitive.HydrologicalFeature[] riverType =
                (HydrologicalPrimitive.HydrologicalFeature[]) heightmap.get(Types.RIVER_TYPE);
        final float[] waterElev = (float[]) heightmap.get(Types.WATER_HEIGHT);
        final HydrologyProfileInprinter imprinter = FractalTerrainInstance.getHydrologyInprinter();
        // One influence query serves the whole chunk: prefetch every primitive that could reach any of the
        // 256 columns (chunk center + half-diagonal, both in the relief-pixel frame), then run the
        // flat merge per block against the prefetched array — 1 tree query per chunk instead of 256.
        final double scale = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final double chunkCenterPixelX = (chunkPos.getMinBlockX() + 8) / scale;
        final double chunkCenterPixelZ = (chunkPos.getMinBlockZ() + 8) / scale;
        final double chunkRadiusPx = (8.0 * Math.sqrt(2.0)) / scale;
        final List<HydrologicalPrimitive> primitives =
                imprinter.prefetchChunk(chunkCenterPixelX, chunkCenterPixelZ, chunkRadiusPx);

        final int startX = chunkPos.getMinBlockX();
        final int startZ = chunkPos.getMinBlockZ();

        // Scratch accumulators, one allocation per chunk (not per column/primitive): every primitive
        // carves into these in place, so `interpolatedElevs` stays untouched — and every column's
        // ambient reading stays the pre-pass value — until every primitive has been applied below.
        final double[] mergedElevation = new double[COLUMNS];
        final double[] smoothedMinDist = new double[COLUMNS];
        for (int pos = 0; pos < COLUMNS; pos++) {
            mergedElevation[pos] = interpolatedElevs[pos];
            smoothedMinDist[pos] = UNSET_MIN_DIST;
        }

        for (HydrologicalPrimitive primitive : primitives) {
            if (!(primitive instanceof RiverPrimitive river)) break;
            carveRiverColumns(river, interpolatedElevs, mergedElevation, smoothedMinDist, startX, startZ, scale);
        }

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                final float ambientElevation = interpolatedElevs[pos];
                riverDifference[pos] = (float) (mergedElevation[pos] - ambientElevation);
                interpolatedElevs[pos] = (float) (Math.max(bottom, mergedElevation[pos]) + seaLevel - 1);
            }
        }
    }

    /**
     * Carves one river primitive into the shared per-chunk accumulators, clipped to the columns its
     * footprint reaches. Primitive-outer so per-primitive invariants — profile, seed, and the
     * width-invariant {@link RosgenProfile#delta} extents — are computed once, not once per column.
     */
    private static void carveRiverColumns(
            RiverPrimitive river,
            float[] interpolatedElevs,
            double[] mergedElevation,
            double[] smoothedMinDist,
            int startX,
            int startZ,
            double scale) {
        final double[] normal = river.normal();
        // A null normal has no tangent — river.h() returns flat elevation and containsPoint would NPE.
        // Skipping here cannot change any non-crashing output.
        if (normal == null) return;
        final double nx = normal[0], nz = normal[1];
        final double cx = river.coord()[0], cz = river.coord()[1];
        final double influence = river.influence();

        // :PERF: conservative AABB clip; floor/ceil + clamp so a too-wide range is harmless while a
        // too-narrow one would silently drop carve — the exact containment test still runs per column.
        final double halfExtent = influence * (Math.abs(nx) + Math.abs(nz));
        final int dxMin = Math.clamp((long) Math.floor((cx - halfExtent) * scale - startX), 0, 15);
        final int dxMax = Math.clamp((long) Math.ceil((cx + halfExtent) * scale - startX), 0, 15);
        final int dzMin = Math.clamp((long) Math.floor((cz - halfExtent) * scale - startZ), 0, 15);
        final int dzMax = Math.clamp((long) Math.ceil((cz + halfExtent) * scale - startZ), 0, 15);
        if (dxMin > dxMax || dzMin > dzMax) return;

        final double width = river.width();
        final double curvature = river.curvature();
        final double elevation = river.elevation();
        final RosgenProfile profile = (RosgenProfile) river.getProfile();
        final long seed = 0;
        final double floodPlainLen = profile.floodPlainLength(width);
        final double marginLen = width / 2;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width);

        for (int dx = dxMin; dx <= dxMax; dx++) {
            final double px = (startX + dx) / scale;
            final double ddx = px - cx;
            for (int dz = dzMin; dz <= dzMax; dz++) {
                final int pos = (dx << 4) + dz;
                final double pz = (startZ + dz) / scale;
                final double ddz = pz - cz;

                // One frame: local +X is the channel tangent, local +Z the normal, so the containment
                // test and the cross-section share these two projections (see RiverPrimitive.getSinAngle).
                final double tang = ddx * nz - ddz * nx;
                final double perp = nx * ddx + nz * ddz;
                if (Math.abs(tang) > influence || Math.abs(perp) > influence) continue;

                final double influenceWeight = FULL_WEIGHT;

                final double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                final double t = Math.clamp(((smoothedMinDist[pos] - dist) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double weight = (t * t * (3.0 - 2.0 * t)) * influenceWeight;
                if (weight == 0) continue;

                final double height = elevation + profile.delta(seed, perp, floodPlainLen, marginLen, depth, curvature);

                blendDistance(smoothedMinDist, pos, dist, weight);
                blendElevation(mergedElevation, pos, height, interpolatedElevs[pos], weight);
            }
        }
    }

    /** Fixed distance-based blend for the running closest-primitive distance — the same function for
     *  every primitive family, never dispatched per primitive type. */
    private static void blendDistance(double[] smoothedMinDist, int pos, double dist, double weight) {
        smoothedMinDist[pos] = Interpolation.lerp(dist, smoothedMinDist[pos], weight);
    }

    /** Fixed distance-based blend for the merged elevation. The primitive supplies {@code height};
     *  capped at {@code ambientElevation} first so a primitive can only cut, never raise, the surface. */
    private static void blendElevation(
            double[] mergedElevation, int pos, double height, double ambientElevation, double weight) {
        mergedElevation[pos] = Interpolation.lerp(Math.min(height, ambientElevation), mergedElevation[pos], weight);
    }

    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }
}
