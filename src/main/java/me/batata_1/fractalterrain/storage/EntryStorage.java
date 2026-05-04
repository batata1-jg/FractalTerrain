package me.batata_1.fractalterrain.storage;

import static me.batata_1.fractalterrain.math.CoordTranslator.toInter;
import static me.batata_1.fractalterrain.math.CoordTranslator.toIntra;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.mojang.datafixers.util.Pair;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import me.batata_1.fractalterrain.world.gen.relief.ReliefProvider;

public class EntryStorage {

    private static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<Pair<Integer, Integer>, CompletableFuture<Tile>> CACHE = new ConcurrentHashMap<>(16,0.75f);
    private final Set<Pair<Integer, Integer>> GENERATED_ENTRIES =
            Collections.synchronizedSet(new LinkedHashSet<>(16, 0.75f));
    private final Supplier<Tile> empty_entry_maker = Tile::new;

    private final String PATH;
    private final int entry_len;

    public EntryStorage(String path, int entryLen) {
        PATH = path;
        entry_len = entryLen;
        bootstrap();
    }

    private Function<Pair<Integer, Integer>, Tile> entry_creating_function = null;

    public EntryStorage(String path, int entryLen, Function<Pair<Integer, Integer>, Tile> f) {
        PATH = path;
        entry_len = entryLen;
        entry_creating_function = f;
        bootstrap();
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
        if (!file.exists()) if (file.mkdirs()) LOGGER.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = file.list();
        if (createdTiles != null)
            for (String tile : createdTiles) {
                final var xz = interpretTileName(tile);
                if (xz == null) {
                    LOGGER.error("invalid file, skipping");
                    continue;
                }
                GENERATED_ENTRIES.add(xz);
            }
        //        LOGGER.info("list os files: {}" , (Object) file.list());
    }

    // xz global coords

    public boolean inBorder(Pair<Integer, Integer> xz) {
        final var intra = toIntra(xz, entry_len);
        return intra.getFirst() == entry_len - 1
                || intra.getFirst() == 0
                || intra.getSecond() == entry_len - 1
                || intra.getSecond() == 0;
    }

    public float getReverseValue(int x, int z) throws ExecutionException, InterruptedException {
        final Tile entry = getEntry(toInter(Pair.of(x, z), entry_len)).get();
        final var intra = toIntra(Pair.of(x, z), entry_len);
        return entry.entryAt(new long[] {intra.getFirst(), entry_len - 1 - intra.getSecond()});
    }

    public float getValue(int x, int z) throws ExecutionException, InterruptedException {
        final Tile entry = getEntry(toInter(Pair.of(x, z), entry_len)).get();
        final var intra = toIntra(Pair.of(x, z), entry_len);
        return entry.entryAt(new long[] {intra.getFirst(), intra.getSecond()});
    }

    public float getValue(Pair<Integer, Integer> xz, int ch) throws ExecutionException, InterruptedException {
        final Tile entry = getEntry(toInter(xz, entry_len)).get();
        final var intra = toIntra(xz, entry_len);
        return entry.entryAt(new long[] {ch, intra.getFirst(), intra.getSecond()});
    }

    // xz inter coords
    public CompletableFuture<Tile> getEntry(Pair<Integer, Integer> xz) {
        if (CACHE.containsKey(xz)) return CACHE.get(xz);
        return fetchEntry(xz);
    }

    // xz inter coords
    public synchronized void addOrOverwriteEntry(CompletableFuture<Tile> t, Pair<Integer, Integer> xz) {

        final CompletableFuture<Tile> ct = t.thenApply(entry -> {
            try {
                entry.serialize(getEntryDir() + "/" + giveNameToTile(xz));
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
    private synchronized CompletableFuture<Tile> fetchEntry(Pair<Integer, Integer> xz) {

        if (CACHE.containsKey(xz)) return CACHE.get(xz);

        if (GENERATED_ENTRIES.contains(xz)) {
            //            LOGGER.info("reading_tile");
            final CompletableFuture<Tile> ct = CompletableFuture.supplyAsync(
                    () -> {
                        final File file = new File(getEntryDir() + "/" + giveNameToTile(xz) + ".ser");
                        if (!file.exists()) {

                            LOGGER.error(
                                    "file {}, aka: {}-{} not exist",
                                    file.getAbsolutePath(),
                                    xz.getFirst(),
                                    xz.getSecond());
                            throw new RuntimeException();
                        }
                        try {

                            final Tile t = empty_entry_maker.get();
                            t.deserialize(getEntryDir() + "/" + giveNameToTile(xz));
                            return t;
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

        LOGGER.error("tile not in storage/no creation function found");
        throw new RuntimeException();
    }

    public synchronized void printCurrentEntrySet() {
        LOGGER.info("Current Tiles: {}", GENERATED_ENTRIES);
    }

    public synchronized void printEntryMapHash() {
        LOGGER.info("Tile Map: {}", CACHE);
    }

    public synchronized String getPath() {
        return PATH;
    }

}
