package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

public class NonIntersectingInfiniteTensor extends Storage<FloatTensor> {

    private final TensorWindow outWindow;
    private final Function<TileKey, FloatTensor> entry_creating_function;

    public NonIntersectingInfiniteTensor(String path, int[] shape, Function<TileKey, FloatTensor> f) {
        super(path, shape.length, new FloatTensor(new int[] {1}));
        this.entry_creating_function = f;
        this.outWindow = new TensorWindow(shape);
    }

    /**
     * Unlike the base storage, a miss here is recoverable: the entry is (re)computed from
     * {@code entry_creating_function} and persisted/recorded. The compute runs on the CALLING thread
     * (not {@link #INFERENCE_EXECUTOR}) because the creating function transitively reads other tiles
     * whose disk loads are scheduled on that single-thread executor — running here would self-deadlock.
     */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<FloatTensor> promise) {
        // Disk-backed entry from a previous session: reuse the base async reload path.
        if (GENERATED_ENTRIES.contains(key) && new File(tilePath(key) + ".ser").exists()) {
            super.loadInto(key, promise);
            return;
        }
        try {
            final FloatTensor entry = entry_creating_function.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        } catch (Throwable ex) {
            CACHE.remove(key, promise);
            promise.completeExceptionally(ex);
        }
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }
}
