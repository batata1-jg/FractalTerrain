package me.batata_1.fractalterrain.infinitetensor.storage;

import static me.batata_1.fractalterrain.debug.Debug.getLogger;
import static me.batata_1.fractalterrain.math.CoordTranslator.toInter;
import static me.batata_1.fractalterrain.math.CoordTranslator.toIntra;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

import com.google.common.base.Function;
import com.mojang.datafixers.util.Pair;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;

public class TensorStorage {

    private static final Logger LOG = getLogger(TensorStorage.class);

    private static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<Pair<Integer, Integer>, CompletableFuture<FloatTensor>> CACHE =
            new ConcurrentHashMap<>(16, 0.75f);
    private final Set<Pair<Integer, Integer>> GENERATED_ENTRIES =
            Collections.synchronizedSet(new LinkedHashSet<>(16, 0.75f));

    private final String PATH;
    private final int entry_len;

    public TensorStorage(String path, int entryLen) {
        PATH = path;
        entry_len = entryLen;
        bootstrap();
    }

    private Function<Pair<Integer, Integer>, FloatTensor> entry_creating_function = null;

    public TensorStorage(String path, int entryLen, Function<Pair<Integer, Integer>, FloatTensor> f) {
        PATH = path;
        entry_len = entryLen;
        entry_creating_function = f;
        bootstrap();
    }

    private void serialize(String path, FloatTensor t) throws IOException {
        final int el = t.data.length;
        final int sl = t.shape.length;
        final float[] arr = new float[el + sl + 1];
        System.arraycopy(t.data, 0, arr, 0, el);
        for (int i = el; i < (el + sl); i++) arr[i] = (float) t.shape[i - el];
        arr[el + sl] = (float) el;
        final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path + ".ser"));
        out.writeObject(arr);
        out.close();
    }

    private FloatTensor deserialize(String path) throws IOException, ClassNotFoundException {
        final ObjectInputStream in = new ObjectInputStream(new FileInputStream(path + ".ser"));
        final float[] arr = (float[]) in.readObject();
        in.close();
        final int slAddEl = arr.length - 1;
        final int el = (int) arr[slAddEl];
        final int sl = slAddEl - el;
        final float[] entries = new float[el];
        System.arraycopy(arr, 0, entries, 0, el);
        final int[] shape = new int[sl];
        for (int i = el; i < el + sl; i++) shape[i - el] = (int) arr[i];
        return new FloatTensor(entries, shape);
    }

    public String getEntryDir() {
        return PATH;
    }

    public synchronized void clear() {
        GENERATED_ENTRIES.clear();
        CACHE.clear();
    }

    private synchronized void bootstrap() {
        File file = new File(getEntryDir());
        if (!file.exists()) if (file.mkdirs()) LOG.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = file.list();
        if (createdTiles != null)
            for (String tile : createdTiles) {
                final var xz = interpretTileName(tile);
                if (xz == null) {
                    LOG.error("invalid file, skipping");
                    continue;
                }
                GENERATED_ENTRIES.add(xz);
            }
    }

    // xz global coords

    public boolean inBorder(Pair<Integer, Integer> xz) {
        final var intra = toIntra(xz, entry_len);
        return intra.getFirst() == entry_len - 1
                || intra.getFirst() == 0
                || intra.getSecond() == entry_len - 1
                || intra.getSecond() == 0;
    }

    public float getReverseValue(int x, int z) {
        final FloatTensor entry = getEntry(toInter(Pair.of(x, z), entry_len));
        final var intra = toIntra(Pair.of(x, z), entry_len);
        return entry.entryAt(new long[] {intra.getFirst(), entry_len - 1 - intra.getSecond()});
    }

    public float getValue(int x, int z) {
        final FloatTensor entry = getEntry(toInter(Pair.of(x, z), entry_len));
        final var intra = toIntra(Pair.of(x, z), entry_len);
        return entry.entryAt(new long[] {intra.getFirst(), intra.getSecond()});
    }

    public float getValue(Pair<Integer, Integer> xz, int ch) {
        final FloatTensor entry = getEntry(toInter(xz, entry_len));
        final var intra = toIntra(xz, entry_len);
        return entry.entryAt(new long[] {ch, intra.getFirst(), intra.getSecond()});
    }

    // xz inter coords
    public FloatTensor getEntry(Pair<Integer, Integer> xz) {
        try {
            if (CACHE.containsKey(xz)) return CACHE.get(xz).get();
            return fetchEntry(xz).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    // xz inter coords
    public synchronized void addOrOverwriteEntry(CompletableFuture<FloatTensor> t, Pair<Integer, Integer> xz) {

        final CompletableFuture<FloatTensor> ct = t.thenApply(entry -> {
            try {
                serialize(getEntryDir() + "/" + giveNameToTile(xz), entry);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return entry;
        });

        GENERATED_ENTRIES.add(xz);
        CACHE.put(xz, ct);
    }

    // xz inter coords
    public boolean existsEntry(Pair<Integer, Integer> xz) {
        return GENERATED_ENTRIES.contains(xz);
    }

    // xz inter coords
    private synchronized CompletableFuture<FloatTensor> fetchEntry(Pair<Integer, Integer> xz) {

        if (CACHE.containsKey(xz)) return CACHE.get(xz);

        if (GENERATED_ENTRIES.contains(xz)) {
            final CompletableFuture<FloatTensor> ct = CompletableFuture.supplyAsync(
                    () -> {
                        final File file = new File(getEntryDir() + "/" + giveNameToTile(xz) + ".ser");
                        if (!file.exists()) {
                            LOG.error(
                                    "file {}, aka: {}-{} not exist",
                                    file.getAbsolutePath(),
                                    xz.getFirst(),
                                    xz.getSecond());
                            throw new RuntimeException();
                        }
                        try {
                            return deserialize(getEntryDir() + "/" + giveNameToTile(xz));
                        } catch (IOException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    INFERENCE_EXECUTOR);
            CACHE.put(xz, ct);
            return CACHE.get(xz);
        }

        if (entry_creating_function != null) {
            addOrOverwriteEntry(CompletableFuture.completedFuture(entry_creating_function.apply(xz)), xz);
            return CACHE.get(xz);
        }

        LOG.error("tile not in storage/no creation function found");
        throw new RuntimeException();
    }

    public synchronized void printCurrentEntrySet() {
        LOG.info("Current Tiles: {}", GENERATED_ENTRIES);
    }

    public synchronized void printEntryMapHash() {
        LOG.info("FloatTensor Map: {}", CACHE);
    }

    public synchronized String getPath() {
        return PATH;
    }
}
