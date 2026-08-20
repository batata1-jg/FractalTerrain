package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import me.batata_1.fractal_terrain.math.ds.SpatialIndex;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Persistable;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

/**
 * Spatial-index counterpart to {@link NonIntersectingInfiniteTensor}: an infinitely-tiled store whose
 * per-tile payload is a {@link SpatialIndex} rather than a raster.
 *
 * <p>Index-agnostic by design — {@link #forEachTileWithin} hands the visitor each tile's origin and
 * whole index, so any query the concrete index supports works without this class knowing about it.
 *
 * <p>The prototype passed to the constructor must be seeded with a real entry: an empty one
 * serializes trivially and would misclassify entries that cannot.
 */
public class NonIntersectingSpatialIndex<I extends SpatialIndex<?> & Persistable<I>> extends Storage<I> {

    /** Turn on to count tile visits when diagnosing an over-broad query radius. */
    public static final boolean DEBUG_QUERY_STATS = false;

    private static final LongAdder STAT_FOR_EACH_TILE_CALLS = new LongAdder();
    private static final LongAdder STAT_TILES_VISITED = new LongAdder();
    private static final LongAdder STAT_TILES_SKIPPED_EMPTY = new LongAdder();
    private static final LongAdder STAT_VISITOR_EARLY_EXITS = new LongAdder();

    /** One line per {@link #DEBUG_QUERY_STATS} counter (all zeros when the toggle is off). */
    public static String statsString() {
        return "NonIntersectingSpatialIndex query stats:"
                + "\n  forEachTileWithin calls: " + STAT_FOR_EACH_TILE_CALLS.sum()
                + "\n  tiles visited:           " + STAT_TILES_VISITED.sum()
                + "\n  tiles skipped (empty):   " + STAT_TILES_SKIPPED_EMPTY.sum()
                + "\n  visitor early exits:     " + STAT_VISITOR_EARLY_EXITS.sum();
    }

    /** Zeroes every {@link #DEBUG_QUERY_STATS} counter. */
    public static void resetStats() {
        STAT_FOR_EACH_TILE_CALLS.reset();
        STAT_TILES_VISITED.reset();
        STAT_TILES_SKIPPED_EMPTY.reset();
        STAT_VISITOR_EARLY_EXITS.reset();
    }

    private final TensorWindow outWindow;
    private final Function<TileKey, I> entryCreatingFunction;

    /** Soft cap on cached bytes; {@code Long.MAX_VALUE} disables eviction. */
    private final long cacheLimitBytes;

    /** @param prototypeIndex must carry at least one real entry — see the class doc */
    public NonIntersectingSpatialIndex(
            String path,
            String name,
            int[] shape,
            I prototypeIndex,
            Function<TileKey, I> entryCreatingFunction,
            long cacheLimitBytes) {
        super(path, name, shape.length, prototypeIndex);
        this.entryCreatingFunction = entryCreatingFunction;
        this.outWindow = new TensorWindow(shape);
        this.cacheLimitBytes = cacheLimitBytes;
    }

    /** Makes a cache miss recoverable by recomputing, mirroring
     *  {@link NonIntersectingInfiniteTensor#loadInto}. Also the only place {@link #cacheLimitBytes} is
     *  enforced — a cache hit stays on {@link Storage}'s lock-free read path. */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<I> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final I entry = entryCreatingFunction.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
        // Runs after the promise is settled on both the disk-hit branch (inside super.loadInto) and the
        // recompute branch above: evicting before promise.complete would drop the in-flight promise from
        // CACHE and let a racing reader start a duplicate compute.
        evictIfNeeded(cacheLimitBytes);
    }

    /** Receives one overlapping tile's index during {@link #forEachTileWithin}. */
    public interface TileVisitor<IndexType> {
        /** Return {@code true} to stop the walk. Tile origins convert between world and tile-local
         *  frames; a store whose entries are already world-framed can ignore them. */
        boolean visit(int tileOriginX, int tileOriginZ, IndexType tileIndex);
    }

    /** The cross-tile query entry point, so callers never handle tile boundaries themselves.
     *  {@code radius} only sizes the tile window and must upper-bound how far outside a tile any
     *  entry of interest can reach; the real filtering is the visitor's own query. */
    public boolean forEachTileWithin(
            final double[] worldCoords, final double radius, final TileVisitor<I> tileVisitor) {
        if (DEBUG_QUERY_STATS) STAT_FOR_EACH_TILE_CALLS.increment();
        final int[][] pixelRange = {
            {(int) Math.floor(worldCoords[0] - radius), (int) Math.ceil(worldCoords[0] + radius) + 1},
            {(int) Math.floor(worldCoords[1] - radius), (int) Math.ceil(worldCoords[1] + radius) + 1}
        };
        final int[] lowestTile = outWindow.getLowestIntersection(pixelRange);
        final int[] highestTile = outWindow.getHighestIntersection(pixelRange);
        for (int tileX = lowestTile[0]; tileX <= highestTile[0]; tileX++) {
            for (int tileZ = lowestTile[1]; tileZ <= highestTile[1]; tileZ++) {
                if (DEBUG_QUERY_STATS) STAT_TILES_VISITED.increment();
                final int tileOriginX = tileX * outWindow.stride[0] + outWindow.offset[0];
                final int tileOriginZ = tileZ * outWindow.stride[1] + outWindow.offset[1];
                final I tileIndex = getEntry(new int[] {tileX, tileZ});
                if (tileIndex.numEntries() == 0) {
                    if (DEBUG_QUERY_STATS) STAT_TILES_SKIPPED_EMPTY.increment();
                    continue;
                }
                if (tileVisitor.visit(tileOriginX, tileOriginZ, tileIndex)) {
                    if (DEBUG_QUERY_STATS) STAT_VISITOR_EARLY_EXITS.increment();
                    return true;
                }
            }
        }
        return false;
    }
}
