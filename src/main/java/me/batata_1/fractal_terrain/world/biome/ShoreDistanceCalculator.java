package me.batata_1.fractal_terrain.world.biome;

import me.batata_1.fractal_terrain.math.Interpolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upscales {@code BiomeProvider}'s coarse per-cell distance-to-shore grid into a smooth per-pixel
 * signal for one 512×512 biome tile.
 *
 * <p><b>Responsibility:</b> convert the integer coarse grid to a bilinear-sampleable float grid, sample
 * it per pixel, and (when enabled) log each grid cell for debugging.
 *
 * <p><b>Collaborators:</b> {@link Interpolation#sampleSmoothStep} does the bilinear sampling; called by
 * {@link ClimateToBiomeTransformer#transform} once per tile ({@link #toFloatGrid}, {@link #logDebugGrid})
 * and once per pixel ({@link #sample}).
 *
 * <p><b>Invariants:</b> stateless; the grid is {@link #DSHORE_GRID}×{@link #DSHORE_GRID} with a
 * 1-coarse-cell halo on every side (see {@code BiomeProvider.computeCoarseDistShore}) — the {@code +1}
 * offset in {@link #sample} skips onto the tile's owned cells and must stay in lockstep with that halo.
 */
public class ShoreDistanceCalculator {
    private static final Logger LOG = LoggerFactory.getLogger(ShoreDistanceCalculator.class);

    /** Native pixels spanned by one coarse cell. */
    private static final int COARSE_CELL_PX = 256;

    /** Half of {@link #COARSE_CELL_PX}; offsets a pixel to its coarse-cell centre for bilinear sampling. */
    private static final int COARSE_CELL_HALF = COARSE_CELL_PX / 2;

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
    // indices, a tile spans (TILE_SIZE/COARSE_CELL_PX) coarse cells, and the halo shifts the grid origin one
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
    // +COARSE_CELL_HALF shifts a pixel onto its coarse-cell centre. Drives the coast override.
    public static float sample(float[] distShoreGrid, int dx, int dz) {
        double shoreGx = 1 + (dx / (double) COARSE_CELL_PX);
        double shoreGz = 1 + (dz / (double) COARSE_CELL_PX);
        return (float) Interpolation.sampleSmoothStep(distShoreGrid, shoreGx, shoreGz, DSHORE_GRID);
    }
}
