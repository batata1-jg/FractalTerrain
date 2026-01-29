package me.batata_1.fractalterrain.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.giveNameToTile;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.interpretTileName;

public class EntryStorage<T extends Tile> {

    private static FractalTerrainInstance INSTANCE;

    private static final Executor EXECUTOR = Executors.newFixedThreadPool(10);

    private final String PATH;
    private final int TILE_SIZE;
    private final MapCodec<T> CODEC;

    public EntryStorage(String path, int size, MapCodec<T> codec ) {
        PATH = path;
        TILE_SIZE = size;
        CODEC = codec;
    }

    private final ConcurrentHashMap<Pair<Integer,Integer>,CompletableFuture<T>> CACHE = new ConcurrentHashMap<>();
    private final Set<Pair<Integer,Integer>> GENERATED_ENTRIES =
            Collections.synchronizedSet(new LinkedHashSet<>(16,0.75f) );

    public String getEntryDir() {
        return INSTANCE.getTilesDir() + PATH;
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

    //xz entry coords
    public CompletableFuture<T> getEntry(Pair<Integer,Integer> xz) {
        if(CACHE.containsKey(xz)) return CACHE.get(xz);
        return fetchEntry(xz);
    }

    //xz entry coords
    public synchronized void addOrOverwriteEntry(CompletableFuture<T> t , Pair<Integer,Integer> xz) {
        CompletableFuture<T> ct = t.thenApply(entry -> {
            LOGGER.info("adding tile");
            DataResult<JsonElement> result = CODEC.codec().encodeStart(JsonOps.INSTANCE,entry);
            File file = new File(getEntryDir() + "/" + giveNameToTile(xz) );

            try( FileWriter writer = new FileWriter(file) ) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(result.resultOrPartial(e -> LOGGER.error("n consequiu escrever")).orElseThrow(),writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return entry;
        });
        GENERATED_ENTRIES.add(xz);
        CACHE.put(xz,ct);
    }

    //xz entry coords
    public boolean existsEntry(Pair<Integer,Integer> xz) { return CACHE.containsKey(xz);}

    //xz entry coords
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
                try( FileReader reader = new FileReader(file)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if( element == null) throw new RuntimeException("element is null");
                    DataResult<T> result = CODEC.codec().parse(JsonOps.INSTANCE,element);
                    return result.resultOrPartial(e -> LOGGER.error("n consequiu ler")).orElseThrow();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } , EXECUTOR);
            CACHE.put(xz,ct);
            return CACHE.get(xz);
        }

        LOGGER.error("tile not in storage");
        return null;

    }

    public synchronized void printCurrentEntrySet() {
        LOGGER.info("Current Tiles: {}" , GENERATED_ENTRIES);
    }

    public synchronized void printEntryMapHash() {
        LOGGER.info("Tile Map: {}" , CACHE);
    }

    public int getEntryLength() {
        return TILE_SIZE;
    }

}
