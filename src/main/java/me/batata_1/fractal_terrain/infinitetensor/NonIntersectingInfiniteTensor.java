package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

public class NonIntersectingInfiniteTensor extends Storage<FloatTensor> {

    private final TensorWindow outWindow;
    private final Function<TileKey, FloatTensor> entry_creating_function;

    public NonIntersectingInfiniteTensor(String path, String name, int[] shape, Function<TileKey, FloatTensor> f) {
        super(path, name, shape.length, new FloatTensor(new int[] {1}));
        this.entry_creating_function = f;
        this.outWindow = new TensorWindow(shape);
    }

    /**
     * Unlike the base storage, a miss here is recoverable. First try the base disk-reload path; if it
     * cannot produce the entry (cache-only, unpersisted, or a corrupt/missing file — signalled by
     * {@link EntryNotLoadableException}), (re)compute it from {@code entry_creating_function} and
     * persist/record it. Both the disk load and the compute run synchronously on the CALLING thread,
     * so a creating function that transitively reads other tiles simply recurses on the call stack.
     * Cleanup of a failed claim (e.g. if the compute itself throws) is handled by {@code fetchEntry}.
     */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<FloatTensor> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final FloatTensor entry = entry_creating_function.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }
}
