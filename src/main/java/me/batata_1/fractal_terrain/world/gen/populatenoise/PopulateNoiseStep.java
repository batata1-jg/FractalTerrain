package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.Arrays;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileCarver;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
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
    private final NoiseGeneratorSettings settings;

    public PopulateNoiseStep(NoiseGeneratorSettings settings) {
        this.settings = settings;
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
        final HydrologyProfileCarver carver = FractalTerrainInstance.getHydrologyCarver();
        final int startX = chunkPos.getMinBlockX();
        final int startZ = chunkPos.getMinBlockZ();
        // One influence query serves the whole chunk: prefetch every primitive that could reach any of the
        // 256 columns (chunk center + half-diagonal, both in the relief-pixel frame), then run the
        // flat merge per block against the prefetched array — 1 tree query per chunk instead of 256.
        final double scale = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final double chunkCenterPixelX = (startX + 8) / scale;
        final double chunkCenterPixelZ = (startZ + 8) / scale;
        final double chunkRadiusPx = (8.0 * Math.sqrt(2.0)) / scale;
        final HydrologyProfileCarver.PrefetchedPrimitives chunkPrimitives =
                carver.prefetchChunk(chunkCenterPixelX, chunkCenterPixelZ, chunkRadiusPx);
        final HydrologicalPrimitive[] primitives = chunkPrimitives.primitives();
        final double[] mutablePt = new double[2];

        float refinedElev = 0;
        final double[] zoneSums = new double[ZoneCategory.COUNT];
        final double[] zoneWeights = new double[ZoneCategory.COUNT];
        double nearestDist;
        HydrologicalPrimitive nearestPrimitive;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                final float baseElev = interpolatedElevs[pos];
                mutablePt[0] = (startX + dx) / scale;
                mutablePt[1] = (startZ + dz) / scale;
                nearestDist = 1e9;
                nearestPrimitive = null;
                for (final HydrologicalPrimitive primitive : primitives) {
                    final double[] primitiveCoord = primitive.coord();
                    final double radialDist =
                            Math.hypot(mutablePt[0] - primitiveCoord[0], mutablePt[1] - primitiveCoord[1]);
                    final HydrologyProfile profile = primitive.getProfile();
                    if (primitive.influence() < radialDist) continue; // out of this primitive's reach
                    final ZoneCategory zone = profile.categoryAt(primitive, radialDist);
                    if (zone == null) continue;
                    final double weight = profile.zoneWeight(primitive, zone, radialDist);
                    if (weight <= 0) continue;
                    if (radialDist < nearestDist) {
                        nearestDist = radialDist;
                        nearestPrimitive = primitive;
                    }
                    final int slot = zone.ordinal();
                    zoneSums[slot] += weight * primitive.h(mutablePt);
                    zoneWeights[slot] += weight;
                }
                boolean intersects = false;
                for (final ZoneCategory zone : ZoneCategory.BY_PRIORITY) {
                    final int slot = zone.ordinal();
                    if (zoneWeights[slot] > 1e-6) {
                        refinedElev = (float) (zoneSums[slot] / zoneWeights[slot]);
                        intersects = true;
                        break;
                    }
                }
                if (!intersects) refinedElev = baseElev;
                riverDifference[pos] = refinedElev - baseElev;
                interpolatedElevs[pos] = Math.max(bottom, refinedElev) + seaLevel - 1;
                if (nearestPrimitive instanceof RiverPrimitive river) {
                    if (nearestDist > (river.width() / 2) + 0.25) continue;
                    riverType[pos] = river.getType();
                    waterElev[pos] =
                            (float) (nearestPrimitive.waterLine() + Math.max(bottom, river.elevation()) + seaLevel - 1);
                } else {
                    riverType[pos] = null;
                }
            }
        }
    }

    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }
}
