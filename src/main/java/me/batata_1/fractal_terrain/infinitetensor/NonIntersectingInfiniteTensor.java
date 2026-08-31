package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

public class NonIntersectingInfiniteTensor extends Storage<FloatTensor> {

    private final TensorWindow outWindow;
    private final Function<TileKey, FloatTensor> entryCreatingFunction;

    public NonIntersectingInfiniteTensor(String path, String name, int[] shape, Function<TileKey, FloatTensor> f) {
        super(path, name, shape.length, new FloatTensor(new int[] {1}));
        this.entryCreatingFunction = f;
        this.outWindow = new TensorWindow(shape);
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
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }
}
