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

    /** Lattice geometry for a chunk: 16x16 blocks, one block being 1 / GLOBAL_SCALE_CORRECTION relief-pixels. */
    private static final int GRID_SIZE = 16;

    private static final double GRID_RESOLUTION = 1.0 / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;

    /** One instance of this class serves every chunk-generation thread, so the carve buffers cannot be fields. */
    private static final ThreadLocal<HydrologyProfileInprinter.GridBuffers> BUFFERS = ThreadLocal.withInitial(() -> {
        final HydrologyProfileInprinter.GridBuffers buffers = new HydrologyProfileInprinter.GridBuffers();
        buffers.ensure(COLUMNS, HydrologyProfileInprinter.maxLutLen(GRID_SIZE, GRID_RESOLUTION));
        return buffers;
    });

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
        final long[] riverType = (long[]) heightmap.get(Types.RIVER_TYPE);
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

        final HydrologyProfileInprinter.GridBuffers buffers = BUFFERS.get();
        final float[] acc = buffers.acc;
        HydrologyProfileInprinter.computeRiverGrid(
                chunkPos.getMinBlockX() / scale,
                chunkPos.getMinBlockZ() / scale,
                GRID_RESOLUTION,
                GRID_SIZE,
                primitives,
                acc,
                buffers.typeMask,
                buffers.dist,
                buffers.lut);

        for (int pos = 0; pos < COLUMNS; pos++) {
            final float ambient = interpolatedElevs[pos];
            final float weight = acc[3 * pos + 2];
            // acc[3 * pos] is already normalised, so the blend does not divide again. The min is what
            // keeps the carve cut-only; it is applied once here rather than per primitive.
            final double merged = weight > 0 ? acc[3 * pos] : ambient;
             //       weight > 0 ? (1 - weight) * ambient + weight * Math.min(acc[3 * pos], ambient) : ambient;
            riverDifference[pos] = (float) (merged - ambient);
            waterElev[pos] = weight > 0 ? (acc[3 * pos + 1] + seaLevel - 1) : 0f;
            riverType[pos] = buffers.typeMask[pos];
            interpolatedElevs[pos] = (float) (Math.max(bottom, merged) + seaLevel - 1);
        }
    }

    public BlockState fillRocks(int x, int y, int z) {
        if (y <= -128) return BEDROCK;
        if (y <= -64) return DEEPSLATE;
        return DEFAULT_ROCK;
    }
}
