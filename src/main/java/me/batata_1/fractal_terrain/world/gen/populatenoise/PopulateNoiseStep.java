package me.batata_1.fractal_terrain.world.gen.populatenoise;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfileInprinter;
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

        final double[] riverWeightedElevations =
                HydrologyProfileInprinter.resolveRiverPrimitives(chunkPos, scale,  primitives, interpolatedElevs, riverType, waterElev);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                final float ambientElevation = interpolatedElevs[pos];
                // Weighted merge of every primitive family that claims this column. Rivers are the only
                // contributor today; a non-river family adds its own (elevation, weight) pair here.
                final double riverWeight = riverWeightedElevations[pos * PAIR + WEIGHT];
                final float mergedElevation = (riverWeight <= 0.0)
                        ? ambientElevation
                        : (float) ((1.0 - riverWeight) * ambientElevation + riverWeight * riverWeightedElevations[pos * PAIR]);

                riverDifference[pos] = mergedElevation - ambientElevation;
                interpolatedElevs[pos] = Math.max(bottom, mergedElevation) + seaLevel - 1;
            }
        }
    }


    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }
}
