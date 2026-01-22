package me.batata_1.fractalterrain.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import me.batata_1.fractalterrain.FractalTerrainInstance;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static me.batata_1.fractalterrain.ml.TileGenerator.createTile;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.giveNameToTile;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.interpretTileName;

public class TileStorage {

    private static FractalTerrainInstance INSTANCE;

    private static final int MAX_CACHE_SIZE = 512;
    private static final Map<Pair<Integer,Integer>, CompletableFuture<Tile>> CACHE =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Pair<Integer,Integer>, CompletableFuture<Tile>> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            });
    private static final Set<Pair<Integer,Integer>> GENERATED_TILES =
            java.util.Collections.synchronizedSet(new LinkedHashSet<>(16,0.75f) );

    public static synchronized void bootstrap() {
        File file = new File(String.valueOf(INSTANCE.getTilesDir()));
        if(!file.exists()) if(file.mkdirs())
            LOGGER.info("created tile dir in: {}", INSTANCE.getTilesDir());
        String[] createdTiles = file.list();
        if(createdTiles != null) for (String tile : createdTiles) {
            var xz = interpretTileName(tile);
            if( xz == null) {
                LOGGER.error("invalid file, skipping");
                continue;
            }
            GENERATED_TILES.add(xz);
        }
        LOGGER.info("list os files: {}" , (Object) file.list());
    }

    public static void setInstance( FractalTerrainInstance i ) {
        INSTANCE = i;
    }

    public static FractalTerrainInstance getInstance() {return INSTANCE;}

    public static synchronized CompletableFuture<Tile> getTile(Pair<Integer,Integer> xz) {

        if(CACHE.containsKey(xz)) return CACHE.get(xz);

        if(GENERATED_TILES.contains(xz)) {
            CompletableFuture<Tile> ct = CompletableFuture.supplyAsync(() -> {
                File file = new File(INSTANCE.getTilesDir() + "/" + giveNameToTile(xz));
                if( !file.exists() ) {
                    LOGGER.error("file {}-{}.json does not exist",xz.getFirst(),xz.getSecond());
                    throw new RuntimeException();
                }
                try( FileReader reader = new FileReader(file)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if( element == null) throw new RuntimeException("element is null");
                    DataResult<Tile> result = Tile.CODEC.codec().parse(JsonOps.INSTANCE,element);
                    return result.resultOrPartial(e -> LOGGER.error("n consequiu ler")).orElseThrow();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            CACHE.put(xz,ct);
            return CACHE.get(xz);
        }

        return null;
    }

    public static synchronized boolean existsTile(Pair<Integer,Integer> xz) { return GENERATED_TILES.contains(xz);}

    public static synchronized CompletableFuture<Tile> genTile(Pair<Integer,Integer> xz) {

        CompletableFuture<Tile> ct = CompletableFuture.supplyAsync(() -> {
            Tile generatedTile = createTile( xz , INSTANCE.getServer());
            DataResult<JsonElement> result = Tile.CODEC.codec().encodeStart(JsonOps.INSTANCE,generatedTile);
            File file = new File(INSTANCE.getTilesDir() + "/" + giveNameToTile(xz) );

            try( FileWriter writer = new FileWriter(file) ) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(result.resultOrPartial(e -> LOGGER.error("n consequiu escrever")).orElseThrow(),writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return generatedTile;
        });

        GENERATED_TILES.add(xz);
        CACHE.put(xz,ct);
        return CACHE.get(xz);
    }

    public static synchronized void printCurrentTilesSet() {
        LOGGER.info("Current Tiles: {}" , GENERATED_TILES);
    }

    public static synchronized void printTileMapHash() {
        LOGGER.info("Tile Map: {}" , CACHE);
    }

}
