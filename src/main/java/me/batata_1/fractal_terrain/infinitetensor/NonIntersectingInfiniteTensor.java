package me.batata_1.fractal_terrain.infinitetensor;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import com.google.common.base.Function;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.infinitetensor.storage.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.storage.TensorStorage;
import org.slf4j.Logger;

public class NonIntersectingInfiniteTensor extends TensorStorage {

    private static final Logger LOG = getLogger(NonIntersectingInfiniteTensor.class);

    private final TensorWindow outWindow;
    private final Function<List<Integer>, FloatTensor> entry_creating_function;

    public NonIntersectingInfiniteTensor(String path, int[] shape, Function<List<Integer>, FloatTensor> f) {
        super(path, shape.length);
        this.entry_creating_function = f;
        this.outWindow = new TensorWindow(shape);
    }

    protected synchronized CompletableFuture<FloatTensor> fetchEntry(List<Integer> key) {
        if (CACHE.containsKey(key)) return CACHE.get(key);
        if (GENERATED_ENTRIES.contains(key)) {
            final CompletableFuture<FloatTensor> ct = CompletableFuture.supplyAsync(
                    () -> {
                        final File file = new File(getEntryDir() + "/" + giveNameToKey(key) + ".ser");
                        if (!file.exists()) {
                            LOG.error("file {}, aka: {} not exist", file.getAbsolutePath(), key);
                            throw new RuntimeException();
                        }
                        try {
                            return deserialize(getEntryDir() + "/" + giveNameToKey(key));
                        } catch (IOException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    INFERENCE_EXECUTOR);
            CACHE.put(key, ct);
            return CACHE.get(key);
        }
        addOrOverwriteEntry(CompletableFuture.completedFuture(entry_creating_function.apply(key)), key);
        return CACHE.get(key);
    }

    protected synchronized void addOrOverwriteEntry(CompletableFuture<FloatTensor> t, final List<Integer> key) {
        final CompletableFuture<FloatTensor> ct = t.thenApply(entry -> {
            try {
                serialize(getEntryDir() + "/" + giveNameToKey(key), entry);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return entry;
        });
        GENERATED_ENTRIES.add(key);
        CACHE.put(key, ct);
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }
}
