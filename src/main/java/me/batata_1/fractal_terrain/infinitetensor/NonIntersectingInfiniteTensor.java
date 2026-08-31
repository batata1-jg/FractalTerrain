package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

public class NonIntersectingInfiniteTensor extends Storage<FloatTensor> {

    private final TensorWindow outWindow;
    private final Function<TileKey, FloatTensor> entryCreatingFunction;

    /** Soft cap on cached bytes; {@code Long.MAX_VALUE} disables eviction. */
    private final long cacheLimitBytes;

    /** Unbounded cache, the historical behaviour of every tensor here. */
    public NonIntersectingInfiniteTensor(String path, String name, int[] shape, Function<TileKey, FloatTensor> f) {
        this(path, name, shape, f, Long.MAX_VALUE);
    }

    public NonIntersectingInfiniteTensor(
            String path, String name, int[] shape, Function<TileKey, FloatTensor> f, long cacheLimitBytes) {
        super(path, name, shape.length, new FloatTensor(new int[] {1}));
        this.entryCreatingFunction = f;
        this.outWindow = new TensorWindow(shape);
        this.cacheLimitBytes = cacheLimitBytes;
    }

    /** Makes a cache miss recoverable by recomputing the tile, which is what turns {@code Storage}
     *  into a lazily-materialized infinite tensor. Runs on the calling thread, so a creating function
     *  that reads other tiles simply recurses. */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<FloatTensor> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final FloatTensor entry = entryCreatingFunction.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
        // The only insert path into Storage's accounting, so the budget holds for getValue-only readers
        // too. Runs after the promise settles on both branches: evicting first would drop the in-flight
        // promise from CACHE and let a racing reader start a duplicate compute.
        evictIfNeeded(cacheLimitBytes);
    }

    /** Bulk read of a pixel range, the path a chunk fill takes instead of four {@link #getValue} calls
     *  per pixel. {@code end} is exclusive; the result is freshly allocated and never cached, so the
     *  caller may read its backing array directly. */
    public FloatTensor getSlice(int[] start, int[] end) {
        final int[] outShape = new int[start.length];
        for (int d = 0; d < outShape.length; d++) outShape[d] = end[d] - start[d];
        final FloatTensor out = new FloatTensor(outShape);
        // No ensureComputed step: loadInto already recomputes a missing tile, so getEntry self-heals
        // per window, and both routes converge on Storage's single-flight.
        SliceGeometry.forEachIntersection(
                outWindow,
                InfiniteTensor.buildRange(start, end),
                (wi, dst, src) -> out.addFrom(getEntry(wi), dst, src));
        return out;
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }
}
