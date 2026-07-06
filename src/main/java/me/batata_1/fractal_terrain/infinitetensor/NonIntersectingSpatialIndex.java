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
 * Lazily-computed, infinitely-tiled store whose per-tile payload is any {@link SpatialIndex}
 * (an {@code ImmutableRTree} for the hydrology unit tiles). Mirrors
 * {@link NonIntersectingInfiniteTensor}: non-overlapping tiles keyed by window index, each computed
 * on demand by {@code entryCreatingFunction} and cached in {@link Storage}.
 *
 * <p>The store is <b>index-agnostic</b>: it never runs a spatial query itself. Cross-tile queries go
 * through {@link #forEachTileWithin}, which hands the visitor each overlapping tile's world origin
 * plus the tile's <em>whole index</em> — the visitor runs whatever query the concrete index supports
 * (stab, circle, box, …) in the tile-local frame and merges results into one world frame itself.
 *
 * <p>Whether tiles persist to disk depends on the payload: {@code Storage}'s constructor probes
 * {@code prototypeIndex.serialize()} once — disk-backed when it succeeds, cache-only when it throws
 * {@link UnsupportedOperationException}. The caller supplies the prototype and <b>must seed it with
 * at least one real entry</b> so the probe exercises entry serialization (an empty index would
 * trivially "serialize" and misclassify a store whose entries cannot).
 */
public class NonIntersectingSpatialIndex<I extends SpatialIndex<?> & Persistable<I>> extends Storage<I> {

    /**
     * Compile-time query-stats toggle: when {@code true} every {@link #forEachTileWithin} call counts
     * calls, tiles visited, empty tiles skipped, and visitor early exits into the static
     * {@link LongAdder}s below (shared across all instances). Read them via {@link #statsString()} and
     * reset via {@link #resetStats()}. When {@code false} the JIT removes all counting, so production
     * queries pay nothing.
     */
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

    /**
     * @param prototypeIndex the deserialization prototype handed to {@link Storage}. Must be seeded
     *     with at least one real entry so the serializability probe exercises entry serialization —
     *     the store is index-type-agnostic and cannot seed one itself.
     * @param entryCreatingFunction computes a tile's index on demand (and on a disk-cache miss)
     */
    public NonIntersectingSpatialIndex(
            String path, String name, int[] shape, I prototypeIndex, Function<TileKey, I> entryCreatingFunction) {
        super(path, name, shape.length, prototypeIndex);
        this.entryCreatingFunction = entryCreatingFunction;
        this.outWindow = new TensorWindow(shape);
    }

    /**
     * Like {@link NonIntersectingInfiniteTensor#loadInto}: try the base disk-reload path first, and
     * when it cannot produce the entry (signalled by {@link EntryNotLoadableException} — cache-only,
     * unpersisted, or a corrupt/incompatible file, e.g. a legacy-format tile) recompute the tile from
     * {@code entryCreatingFunction} on the CALLING thread. Cleanup of a failed claim is handled by
     * {@code fetchEntry}.
     */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<I> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final I entry = entryCreatingFunction.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
    }

    /** Receives one overlapping tile's index during {@link #forEachTileWithin}. */
    public interface TileVisitor<IndexType> {
        /**
         * @param tileOriginX world X of the tile's local {@code (0,0)} corner — subtract from a world
         *     coordinate to get its tile-local coordinate (and add to tile-local results to restamp
         *     them into the world frame)
         * @param tileOriginZ world Z of the tile's local {@code (0,0)} corner
         * @param tileIndex the tile's whole spatial index; run any tile-local query on it
         * @return {@code true} to stop the walk (early exit), {@code false} to continue
         */
        boolean visit(int tileOriginX, int tileOriginZ, IndexType tileIndex);
    }

    /**
     * Visit every tile whose window overlaps the world-space circle ({@code worldCoords ± radius}),
     * handing {@code tileVisitor} the tile's world origin and its whole per-tile index. Tiles are
     * computed on demand; tiles with zero entries are skipped. Returns {@code true} iff a visitor
     * early-exited the walk.
     *
     * <p>{@code radius} only sizes the tile window — it must upper-bound how far outside a tile any
     * entry the visitor cares about can reach (e.g. the max shape influence radius plus any query
     * expansion); the actual spatial filtering is the visitor's query.
     */
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
