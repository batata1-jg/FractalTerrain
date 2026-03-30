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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import me.batata_1.fractalterrain.FractalTerrainInstance;

public class EntryStorage<T extends Tile> {

    private final ConcurrentHashMap<Pair<Integer, Integer>, CompletableFuture<T>> CACHE = new ConcurrentHashMap<>();
    private final Set<Pair<Integer, Integer>> GENERATED_ENTRIES =
            Collections.synchronizedSet(new LinkedHashSet<>(16, 0.75f));
    private final Supplier<T> empty_entry_maker;
    private final String PATH;
    private final int entry_len;

    public EntryStorage(String path, Supplier<T> s, int entryLen) {
        PATH = path;
        empty_entry_maker = s;
        entry_len = entryLen;
        bootstrap();
    }

    private Function<Pair<Integer, Integer>, T> entry_creating_function = null;

    public EntryStorage(String path, Supplier<T> s, int entryLen, Function<Pair<Integer, Integer>, T> f) {
        PATH = path;
        empty_entry_maker = s;
        entry_len = entryLen;
        entry_creating_function = f;
        bootstrap();
    }

    public String getEntryDir() {
        return FractalTerrainInstance.getDir() + "/" + PATH;
    }

    private synchronized void bootstrap() {
        File file = new File(getEntryDir());
        if (!file.exists()) if (file.mkdirs()) LOGGER.info("created tile dir in: {}", getEntryDir());
        String[] createdTiles = file.list();
        if (createdTiles != null)
            for (String tile : createdTiles) {
                var xz = interpretTileName(tile);
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
        var intra = toIntra(xz, entry_len);
        return intra.getFirst() == entry_len - 1
                || intra.getFirst() == 0
                || intra.getSecond() == entry_len - 1
                || intra.getSecond() == 0;
    }

    public float getReverseValue(int x, int z) throws ExecutionException, InterruptedException {
        T entry = getEntry(toInter(Pair.of(x, z), entry_len)).get();
        var intra = toIntra(Pair.of(x, z), entry_len);
        return entry.entryAt(new long[] {intra.getFirst(), entry_len - 1 - intra.getSecond()});
    }

    public float getValue(int x, int z) throws ExecutionException, InterruptedException {
        T entry = getEntry(toInter(Pair.of(x, z), entry_len)).get();
        var intra = toIntra(Pair.of(x, z), entry_len);
        return entry.entryAt(new long[] {intra.getFirst(), intra.getSecond()});
    }

    public float getValue(Pair<Integer, Integer> xz, int ch) throws ExecutionException, InterruptedException {
        T entry = getEntry(toInter(xz, entry_len)).get();
        var intra = toIntra(xz, entry_len);
        return entry.entryAt(new long[] {ch, intra.getFirst(), intra.getSecond()});
    }

    // xz inter coords
    public CompletableFuture<T> getEntry(Pair<Integer, Integer> xz) {
        if (CACHE.containsKey(xz)) return CACHE.get(xz);
        return fetchEntry(xz);
    }

    // xz inter coords
    public synchronized void addOrOverwriteEntry(CompletableFuture<T> t, Pair<Integer, Integer> xz) {

        CompletableFuture<T> ct = t.thenApply(entry -> {
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
    private synchronized CompletableFuture<T> fetchEntry(Pair<Integer, Integer> xz) {

        if (CACHE.containsKey(xz)) return CACHE.get(xz);

        if (GENERATED_ENTRIES.contains(xz)) {
            //            LOGGER.info("reading_tile");
            CompletableFuture<T> ct = CompletableFuture.supplyAsync(
                    () -> {
                        File file = new File(getEntryDir() + "/" + giveNameToTile(xz) + ".ser");
                        if (!file.exists()) {

                            LOGGER.error(
                                    "file {}, aka: {}-{} not exist",
                                    file.getAbsolutePath(),
                                    xz.getFirst(),
                                    xz.getSecond());
                            throw new RuntimeException();
                        }
                        try {

                            T t = empty_entry_maker.get();
                            t.deserialize(getEntryDir() + "/" + giveNameToTile(xz));
                            return t;
                        } catch (IOException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    EXECUTOR);
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
