package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.hydrology.HydrologyTileGeometry;
import me.batata_1.fractal_terrain.math.Interpolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upscales the coarse distance-to-shore grid into the smooth per-pixel signal coastal biomes read.
 *
 * <p>Smoothing here rather than at the coarse stage is what keeps beach and stony-shore bands from
 * landing on visible coarse-cell steps.
 *
 * <p>The {@code +1} offset in {@link #sample} skips the one-cell halo and must stay in lockstep with
 * {@code BiomeProvider.computeCoarseDistShore}.
 */
public class ShoreDistanceCalculator {
    private static final Logger LOG = LoggerFactory.getLogger(ShoreDistanceCalculator.class);

    /** Side of the per-coarse-cell distance grid passed in (2 owned cells + 1-cell halo each side). */
    private static final int DSHORE_GRID = 4;

    private ShoreDistanceCalculator() {}

    /** Float view of the integer distance-to-shore grid, for bilinear sampling. */
    public static float[] toFloatGrid(int[] coarseDistShore) {
        final float[] distShoreGrid = new float[coarseDistShore.length];
        for (int i = 0; i < coarseDistShore.length; i++) distShoreGrid[i] = coarseDistShore[i];
        return distShoreGrid;
    }

    // Debug: log each dshore grid cell's coarse-pixel coordinate and value. The grid is row-major
    // [Xcell*DSHORE_GRID + Zcell] over the tile's owned coarse cells plus a 1-cell halo. x0/z0 are tile
    // indices, a tile spans (TILE_SIZE/HydrologyTileGeometry.COARSE_PX) coarse cells, and the halo shifts the grid
    // origin one
    // coarse cell earlier: coarsePx = tileIndex*coarseCellsPerTile - 1 + gridIndex.
    public static void logDebugGrid(int x0, int z0, int[] coarseDistShore) {
        final int originCx = (x0 << 1);
        final int originCz = (z0 << 1);
        for (int gx = 0; gx < DSHORE_GRID; gx++) {
            for (int gz = 0; gz < DSHORE_GRID; gz++) {
                LOG.info(
                        "dshore cell coarsePx=({}, {}) value={}",
                        originCx + gx - 1,
                        originCz + gz - 1,
                        coarseDistShore[gx * DSHORE_GRID + gz]);
            }
        }
    }

    // Per-pixel distance to shore, bilinearly upscaled from the coarse grid. dx=X axis, dz=Z axis;
    // +HydrologyTileGeometry.COARSE_HALF shifts a pixel onto its coarse-cell centre. Drives the coast override.
    public static float sample(float[] distShoreGrid, int dx, int dz) {
        double shoreGx = 1 + (dx / (double) HydrologyTileGeometry.COARSE_PX);
        double shoreGz = 1 + (dz / (double) HydrologyTileGeometry.COARSE_PX);
        return (float) Interpolation.sampleSmoothStep(distShoreGrid, shoreGx, shoreGz, DSHORE_GRID);
    }
}
