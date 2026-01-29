package me.batata_1.fractalterrain.storage;


import com.mojang.datafixers.util.Pair;

import me.batata_1.fractalterrain.FractalTerrainInstance;


import java.io.File;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

public class EntryStorage<T extends Tile> {

    private static FractalTerrainInstance INSTANCE;

    private final String PATH;

    public EntryStorage(String path) {
        PATH = path;
    }

    private final ConcurrentHashMap<Pair<Integer,Integer>,CompletableFuture<T>> CACHE = new ConcurrentHashMap<>();
    private final Set<Pair<Integer,Integer>> GENERATED_ENTRIES =
            Collections.synchronizedSet(new LinkedHashSet<>(16,0.75f) );

    public String getEntryDir() {
        return INSTANCE.getTilesDir() + "/"+ PATH;
    }

    public synchronized void bootstrap() {
        File file = new File(getEntryDir());
        if(!file.exists()) if(file.mkdirs())
            LOGGER.info("created tile dir in: {}", INSTANCE.getTilesDir());
        String[] createdTiles = file.list();
        if(createdTiles != null) for (String tile : createdTiles) {
            var xz = interpretTileName(tile);
            if( xz == null) {
                LOGGER.error("invalid file, skipping");
                continue;
            }
            GENERATED_ENTRIES.add(xz);
        }
        LOGGER.info("list os files: {}" , (Object) file.list());
    }

    public static void setInstance( FractalTerrainInstance i ) {
        INSTANCE = i;
    }

    public static FractalTerrainInstance getInstance() {return INSTANCE;}

    //xz entryAt coords
    public CompletableFuture<T> getEntry(Pair<Integer,Integer> xz) {
        if(CACHE.containsKey(xz)) return CACHE.get(xz);
        return fetchEntry(xz);
    }

    //xz entryAt coords
    public synchronized void addOrOverwriteEntry(CompletableFuture<T> t , Pair<Integer,Integer> xz) {
        CompletableFuture<T> ct = t.thenApply(entry -> {

            try {
                T.serialize(getEntryDir() + "/" + giveNameToTile(xz),entry);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return entry;
        });
        GENERATED_ENTRIES.add(xz);
        CACHE.put(xz,ct);
    }

    //xz entryAt coords
    public boolean existsEntry(Pair<Integer,Integer> xz) { return CACHE.containsKey(xz);}

    //xz entryAt coords
    private synchronized CompletableFuture<T> fetchEntry(Pair<Integer,Integer> xz) {

        if(CACHE.containsKey(xz)) return CACHE.get(xz);

        if(GENERATED_ENTRIES.contains(xz)) {
            LOGGER.info("reading_tile");
            CompletableFuture<T> ct = CompletableFuture.supplyAsync(() -> {
                File file = new File(getEntryDir() + "/" + giveNameToTile(xz));
                if( !file.exists() ) {
                    LOGGER.error("file {}-{}.json does not exist",xz.getFirst(),xz.getSecond());
                    throw new RuntimeException();
                }
                try {
                    return T.deserialize(getEntryDir() + "/" + giveNameToTile(xz));
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            } , EXECUTOR);
            CACHE.put(xz,ct);
            return CACHE.get(xz);
        }

        LOGGER.error("tile not in storage");
        throw new RuntimeException();

    }

    public synchronized void printCurrentEntrySet() {
        LOGGER.info("Current Tiles: {}" , GENERATED_ENTRIES);
    }

    public synchronized void printEntryMapHash() {
        LOGGER.info("Tile Map: {}" , CACHE);
    }


}
