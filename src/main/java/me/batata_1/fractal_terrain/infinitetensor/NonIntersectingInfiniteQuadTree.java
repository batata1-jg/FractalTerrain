package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import me.batata_1.fractal_terrain.math.ds.ImmutableQuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

/**
 * Lazily-computed, infinitely-tiled store whose per-tile payload is an {@link ImmutableQuadTree}
 * (instead of a {@code FloatTensor}). Mirrors {@link NonIntersectingInfiniteTensor}: non-overlapping
 * tiles keyed by window index, each computed on demand by {@code entryCreatingFunction} and cached in
 * {@link Storage}.
 *
 * <p>Whether tiles persist to disk depends on the point type: the store is disk-backed when the
 * supplied {@code pointPrototype} is {@link me.batata_1.fractal_terrain.storage.Persistable} (e.g.
 * {@link me.batata_1.fractal_terrain.hydrology.HydrologicalUnit}) and cache-only otherwise (e.g.
 * a bare coordinate point). {@code Storage}'s constructor decides this once by probing the prototype
 * tile — which is why the prototype is seeded with one dummy point, so the probe exercises point
 * serialization.
 *
 * <p>The {@code getValue} of the tensor variant is replaced by {@link #getValuesWithin}: a
 * single-tile lookup that finds the owning tile for a coordinate and runs a spatial query on its
 * {@code ImmutableQuadTree}, bounded to that tile's logical window.
 */
public class NonIntersectingInfiniteQuadTree<T extends QuadTreePoint> extends Storage<ImmutableQuadTree<T>> {

    /**
     * Compile-time query-stats toggle: when {@code true} every query method counts calls, tiles visited,
     * tiles with hits, points delivered, and early exits into the static {@link LongAdder}s below (shared
     * across all instances). Read them via {@link #statsString()} and reset via {@link #resetStats()}.
     * When {@code false} the JIT removes all counting, so production queries pay nothing.
     */
    public static final boolean DEBUG_QUERY_STATS = false;

    private static final LongAdder STAT_GET_VALUES_CALLS = new LongAdder();
    private static final LongAdder STAT_FOR_EACH_TILE_CALLS = new LongAdder();
    private static final LongAdder STAT_ANY_VALUE_CALLS = new LongAdder();
    private static final LongAdder STAT_TILES_VISITED = new LongAdder();
    private static final LongAdder STAT_TILES_WITH_HITS = new LongAdder();
    private static final LongAdder STAT_POINTS_DELIVERED = new LongAdder();
    private static final LongAdder STAT_ANY_VALUE_EARLY_EXITS = new LongAdder();

    /** One line per {@link #DEBUG_QUERY_STATS} counter (all zeros when the toggle is off). */
    public static String statsString() {
        return "NonIntersectingInfiniteQuadTree query stats:"
                + "\n  getValuesWithin calls:   " + STAT_GET_VALUES_CALLS.sum()
                + "\n  forEachTileWithin calls: " + STAT_FOR_EACH_TILE_CALLS.sum()
                + "\n  anyValueWithin calls:    " + STAT_ANY_VALUE_CALLS.sum()
                + "\n  tiles visited:           " + STAT_TILES_VISITED.sum()
                + "\n  tiles with hits:         " + STAT_TILES_WITH_HITS.sum()
                + "\n  points delivered:        " + STAT_POINTS_DELIVERED.sum()
                + "\n  anyValue early exits:    " + STAT_ANY_VALUE_EARLY_EXITS.sum();
    }

    /** Zeroes every {@link #DEBUG_QUERY_STATS} counter. */
    public static void resetStats() {
        STAT_GET_VALUES_CALLS.reset();
        STAT_FOR_EACH_TILE_CALLS.reset();
        STAT_ANY_VALUE_CALLS.reset();
        STAT_TILES_VISITED.reset();
        STAT_TILES_WITH_HITS.reset();
        STAT_POINTS_DELIVERED.reset();
        STAT_ANY_VALUE_EARLY_EXITS.reset();
    }

    private final TensorWindow outWindow;
    private final Function<TileKey, ImmutableQuadTree<T>> entryCreatingFunction;

    public NonIntersectingInfiniteQuadTree(
            String path, String name, int[] shape, T pointPrototype, Function<TileKey, ImmutableQuadTree<T>> f) {
        super(path, name, shape.length, prototypeTree(pointPrototype));
        this.entryCreatingFunction = f;
        this.outWindow = new TensorWindow(shape);
    }

    /**
     * The deserialization prototype tree: it carries the {@code pointPrototype} (used to rebuild
     * stored point chunks) and is seeded with that one point so {@code Storage}'s serializability
     * probe actually attempts to serialize a point.
     */
    private static <T extends QuadTreePoint> ImmutableQuadTree<T> prototypeTree(T pointPrototype) {
        return new ImmutableQuadTree<>(
                new double[] {0, 0}, new double[] {1, 1}, List.of(pointPrototype), pointPrototype);
    }

    /**
     * Like {@link NonIntersectingInfiniteTensor#loadInto}: try the base disk-reload path first, and
     * when it cannot produce the entry (signalled by {@link EntryNotLoadableException} — cache-only,
     * unpersisted, or a corrupt/missing file) recompute the tile from {@code entryCreatingFunction}
     * on the CALLING thread. Cleanup of a failed claim is handled by {@code fetchEntry}.
     */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<ImmutableQuadTree<T>> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final ImmutableQuadTree<T> entry = entryCreatingFunction.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
    }

    /** The (re)computed {@link ImmutableQuadTree} for the tile owning {@code (queryX, queryZ)}. */
    public List<T> getValuesWithin(final double[] coords, final double radius) {
        if (DEBUG_QUERY_STATS) STAT_GET_VALUES_CALLS.increment();
        final ImmutableQuadTree<T> entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        final List<T> result = entry.getPointsInCircle(outWindow.getPerWindowCoord(coords), radius);
        if (DEBUG_QUERY_STATS) STAT_POINTS_DELIVERED.add(result.size());
        return result;
    }

    /** Receives one tile's contribution to a {@link #forEachTileWithin} query. */
    public interface TileVisitor<T extends QuadTreePoint> {
        /**
         * @param originX world X of the tile's local {@code (0,0)} corner — add to a point's tile-local
         *     coord to get its world coord
         * @param originZ world Z of the tile's local {@code (0,0)} corner
         * @param tileLocalPoints the points this tile returned for the circle query (tile-local coords)
         */
        void visit(int originX, int originZ, List<T> tileLocalPoints);
    }

    /**
     * Visit every tile whose window overlaps the world-space circle ({@code coords ± radius}). Unlike
     * {@link #getValuesWithin} (single owning tile, tile-local results), this spans tile borders: for each
     * overlapping tile the {@code visitor} receives the tile's world origin and the tile-local points from
     * a circle query centred at {@code coords − origin}, so a caller can translate the points into one
     * world frame and merge them. Tiles are computed on demand, exactly like {@link #getValuesWithin}.
     *
     * <p>The list handed to the visitor is <b>one reused buffer</b> (cleared between tiles) — the visitor
     * must copy out anything it needs and must not retain the list beyond the visit.
     */
    public void forEachTileWithin(final double[] coords, final double radius, final TileVisitor<T> visitor) {
        if (DEBUG_QUERY_STATS) STAT_FOR_EACH_TILE_CALLS.increment();
        final int[][] pixelRange = {
            {(int) Math.floor(coords[0] - radius), (int) Math.ceil(coords[0] + radius) + 1},
            {(int) Math.floor(coords[1] - radius), (int) Math.ceil(coords[1] + radius) + 1}
        };
        final int[] lo = outWindow.getLowestIntersection(pixelRange);
        final int[] hi = outWindow.getHighestIntersection(pixelRange);
        final double[] localCenter = new double[2];
        final List<T> local = new ArrayList<>(64); // reused across tiles; cleared per tile
        for (int tileX = lo[0]; tileX <= hi[0]; tileX++) {
            for (int tileZ = lo[1]; tileZ <= hi[1]; tileZ++) {
                if (DEBUG_QUERY_STATS) STAT_TILES_VISITED.increment();
                final int originX = tileX * outWindow.stride[0] + outWindow.offset[0];
                final int originZ = tileZ * outWindow.stride[1] + outWindow.offset[1];
                final ImmutableQuadTree<T> entry = getEntry(new int[] {tileX, tileZ});
                localCenter[0] = coords[0] - originX;
                localCenter[1] = coords[1] - originZ;
                local.clear();
                entry.getPointsInCircle(localCenter, radius, local);
                if (!local.isEmpty()) {
                    if (DEBUG_QUERY_STATS) {
                        STAT_TILES_WITH_HITS.increment();
                        STAT_POINTS_DELIVERED.add(local.size());
                    }
                    visitor.visit(originX, originZ, local);
                }
            }
        }
    }

    /**
     * Cross-tile twin of {@link #forEachTileWithin} that stops at the first accepted point: true iff some
     * point within {@code radius} of {@code coords} (world frame) passes {@code test}. The test runs in
     * the tile-local frame, but distances are translation-invariant, so the {@code distSqToCenter} it
     * receives equals the world-frame squared distance — no coordinate re-stamping is needed. Allocates
     * no result list (delegates to {@link ImmutableQuadTree#anyPointInCircle} per tile).
     */
    public boolean anyValueWithin(
            final double[] coords, final double radius, final ImmutableQuadTree.PointTest<T> test) {
        if (DEBUG_QUERY_STATS) STAT_ANY_VALUE_CALLS.increment();
        final int[][] pixelRange = {
            {(int) Math.floor(coords[0] - radius), (int) Math.ceil(coords[0] + radius) + 1},
            {(int) Math.floor(coords[1] - radius), (int) Math.ceil(coords[1] + radius) + 1}
        };
        final int[] lo = outWindow.getLowestIntersection(pixelRange);
        final int[] hi = outWindow.getHighestIntersection(pixelRange);
        final double[] localCenter = new double[2];
        for (int tileX = lo[0]; tileX <= hi[0]; tileX++) {
            for (int tileZ = lo[1]; tileZ <= hi[1]; tileZ++) {
                if (DEBUG_QUERY_STATS) STAT_TILES_VISITED.increment();
                final int originX = tileX * outWindow.stride[0] + outWindow.offset[0];
                final int originZ = tileZ * outWindow.stride[1] + outWindow.offset[1];
                final ImmutableQuadTree<T> entry = getEntry(new int[] {tileX, tileZ});
                localCenter[0] = coords[0] - originX;
                localCenter[1] = coords[1] - originZ;
                if (entry.anyPointInCircle(localCenter, radius, test)) {
                    if (DEBUG_QUERY_STATS) STAT_ANY_VALUE_EARLY_EXITS.increment();
                    return true;
                }
            }
        }
        return false;
    }
}
