package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.QuadTreePoint;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

/**
 * Lazily-computed, infinitely-tiled store whose per-tile payload is a {@link QuadTree} (instead of
 * a {@code FloatTensor}). Mirrors {@link NonIntersectingInfiniteTensor}: non-overlapping tiles keyed
 * by window index, each computed on demand by {@code entryCreatingFunction} and cached in
 * {@link Storage}. Since {@link QuadTree} leaves {@code serialize}/{@code deserialize} as the
 * throwing defaults, the store is <em>cache-only</em> (tiles are recomputed, never read from disk).
 *
 * <p>The {@code getValue} of the tensor variant is replaced by {@link #query}: a single-tile lookup
 * that finds the owning tile for a coordinate and runs a spatial query on its {@code QuadTree},
 * bounded to that tile's logical window.
 */
public class NonIntersectingInfiniteQuadTree<T extends QuadTreePoint> extends Storage<QuadTree<T>> {

    private final TensorWindow outWindow;
    private final Function<TileKey, QuadTree<T>> entryCreatingFunction;

    public NonIntersectingInfiniteQuadTree(String path, int[] shape, Function<TileKey, QuadTree<T>> f) {
        super(path, shape.length, new QuadTree<>(new double[] {0, 0}, new double[] {0, 0}));
        this.entryCreatingFunction = f;
        this.outWindow = new TensorWindow(shape);
    }

    /**
     * Like {@link NonIntersectingInfiniteTensor#loadInto}: try the base disk-reload path first, and
     * when it cannot produce the entry (signalled by {@link EntryNotLoadableException} — the common
     * case here, since this store is cache-only) recompute the tile from {@code entryCreatingFunction}
     * on the CALLING thread. Cleanup of a failed claim is handled by {@code fetchEntry}.
     */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<QuadTree<T>> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final QuadTree<T> entry = entryCreatingFunction.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
    }

    /**
     * Single-tile spatial query: find the tile that owns {@code (queryX, queryZ)}, (re)compute it if
     * needed, and return the points within {@code radius}. Results are restricted to the owning
     * tile's logical window bounds, so points lying in a neighbouring tile's halo are never returned
     * — this is the "symbolic crop": the final tile may physically hold halo points, but {@code query}
     * never looks beyond the requested window.
     */
    public List<T> query(double queryX, double queryZ, double radius) {
        final int[] coords = {0, (int) Math.floor(queryX), (int) Math.floor(queryZ)};
        final int[] tileIndex = outWindow.getSinglePixelIntersection(coords);
        final QuadTree<T> tile = getEntry(tileIndex);

        // Shape is {1, size, size}: dim 0 is a dummy leading axis, X is dim 1 and Z is dim 2 (matching
        // the {0, x, z} coords above), so the spatial crop reads windowBounds[1] and windowBounds[2].
        final int[][] windowBounds = outWindow.getBounds(tileIndex);
        final double windowMinX = windowBounds[1][0];
        final double windowMaxX = windowBounds[1][1];
        final double windowMinZ = windowBounds[2][0];
        final double windowMaxZ = windowBounds[2][1];

        final List<T> hits = tile.getPointsInCircle(new double[] {queryX, queryZ}, radius);
        hits.removeIf(point -> point.get(0) < windowMinX
                || point.get(0) >= windowMaxX
                || point.get(1) < windowMinZ
                || point.get(1) >= windowMaxZ);
        return hits;
    }

    /** The (re)computed {@link QuadTree} for the tile owning {@code (queryX, queryZ)}. */
    public QuadTree<T> getTile(double queryX, double queryZ) {
        final int[] coords = {0, (int) Math.floor(queryX), (int) Math.floor(queryZ)};
        return getEntry(outWindow.getSinglePixelIntersection(coords));
    }
}
