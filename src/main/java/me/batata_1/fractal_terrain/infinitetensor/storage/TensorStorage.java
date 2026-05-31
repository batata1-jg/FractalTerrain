package me.batata_1.fractal_terrain.infinitetensor.storage;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class TensorStorage {

    private static final Logger LOG = getLogger(TensorStorage.class);

    protected static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    protected final ConcurrentHashMap<List<Integer>, CompletableFuture<FloatTensor>> CACHE =
            new ConcurrentHashMap<>(16, 0.75f);
    protected final Set<List<Integer>> GENERATED_ENTRIES = Collections.synchronizedSet(new LinkedHashSet<>(16, 0.75f));

    protected final String PATH;
    protected final int rank;

    public TensorStorage(String path, int rank) {
        PATH = path;
        this.rank = rank;
        bootstrap();
    }

    public boolean inStorage(int[] index) {
        return GENERATED_ENTRIES.contains(toKey(index));
    }

    protected void serialize(String path, FloatTensor t) throws IOException {
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

    protected FloatTensor deserialize(String path) throws IOException, ClassNotFoundException {
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

    private static String NorP(int x) {
        if (x < 0) return "N";
        return "P";
    }

    // will not be a strong implementation because will be replaced by sqlite
    private static List<Integer> getKeyFromName(final String s, final int rank) {
        int curIndex = s.indexOf('_');
        String curString = s;

        if (curIndex == -1) {
            LOG.error("invalid in tiles dir (no '_')");
            return null;
        }

        final int[] ans = new int[rank];
        for (int id = 0; id < rank; id++) {
            curString = curString.substring(curIndex + 1);
            curIndex = curString.indexOf("_");
            if (curIndex == -1) curIndex = curString.length();
            int pos = Integer.parseInt(curString.substring(1, curIndex));
            int sign = curString.charAt(0) == 'N' ? -1 : 1;
            ans[id] = pos * sign;
        }
        return toKey(ans);
    }

    protected static String giveNameToKey(List<Integer> key) {
        StringBuilder ans = new StringBuilder();
        for (int id : key) {
            ans.append("_").append(NorP(id)).append(Math.abs(id));
        }
        return ans.append("_").toString();
    }

    private synchronized void bootstrap() {
        File file = new File(getEntryDir());
        if (!file.exists()) if (file.mkdirs()) LOG.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = file.list();
        if (createdTiles != null)
            for (String tile : createdTiles) {
                final List<Integer> key = getKeyFromName(tile, rank);
                if (key == null) {
                    LOG.error("invalid file, skipping");
                    continue;
                }
                GENERATED_ENTRIES.add(key);
            }
    }

    public FloatTensor getEntry(int[] index) {
        return getEntry(toKey(index));
    }

    public FloatTensor getEntry(List<Integer> key) {
        try {
            if (CACHE.containsKey(key)) return CACHE.get(key).get();
            return fetchEntry(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void addOrOverwriteEntry(CompletableFuture<FloatTensor> t, final int[] index) {
        final List<Integer> key = toKey(index);
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

    // xz inter coords
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

        LOG.error(
                "tile index = {} of path  {} not in storage/no ( GeneratedEntries = {} and CACHE = {} creation function found",
                key,
                getPath(),
                GENERATED_ENTRIES.contains(key),
                CACHE.containsKey(key));
        printCurrentEntrySet();
        printEntryMapHash();
        throw new RuntimeException();
    }

    public synchronized void printCurrentEntrySet() {
        LOG.info("Current Tiles: {}", GENERATED_ENTRIES);
    }

    public synchronized void printEntryMapHash() {
        LOG.info("FloatTensor Map: {}", CACHE);
    }

    @Nullable
    public synchronized String getPath() {
        return PATH;
    }

    // TODO:implement this
    public void evictIfNeeded(long cacheLimitBytes) {}

    protected static List<Integer> toKey(int[] index) {
        return Arrays.stream(index).boxed().toList();
    }
}
