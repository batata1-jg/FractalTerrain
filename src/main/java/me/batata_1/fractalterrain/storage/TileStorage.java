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

import static me.batata_1.fractalterrain.ml.TileGenerator.createTile;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.giveNameToTile;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;



public class TileStorage {

    private static FractalTerrainInstance INSTANCE;

    public static void setInstance( FractalTerrainInstance i ) {
        INSTANCE = i;
    }

    public static FractalTerrainInstance getInstance() {return INSTANCE;}

    public static Tile getTile(Pair<Integer,Integer> xz) {
        if(INSTANCE.tileMapContainsKey(xz)) {
           // LOGGER.error("foi ai?");
            return INSTANCE.tileMapGet(xz);
        }
        if(INSTANCE.currentTilesContainsKey(xz)) {
            File file = new File(INSTANCE.getTilesDir() + "/" + giveNameToTile(xz));
            if( !file.exists() ) {
                LOGGER.error("file {}-{}.json does not exist",xz.getFirst(),xz.getSecond());
                throw new RuntimeException();
            }
            try( FileReader reader = new FileReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                assert element != null;
                DataResult<Tile> result = Tile.CODEC.codec().parse(JsonOps.INSTANCE,element);
                Tile t = result.resultOrPartial(e -> LOGGER.error("n consequiu ler")).orElseThrow();
                INSTANCE.tileMapPut(xz,t);
                return t;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static boolean existsTile(Pair<Integer,Integer> xz) { return INSTANCE.currentTilesContainsKey(xz);}

    public static Tile genTile(Pair<Integer,Integer> xz) {
        Tile generatedTile = createTile( xz , INSTANCE.getServer());
        DataResult<JsonElement> result = Tile.CODEC.codec().encodeStart(JsonOps.INSTANCE,generatedTile);
        File file = new File(INSTANCE.getTilesDir() + "/" + giveNameToTile(xz) );

        try( FileWriter writer = new FileWriter(file) ) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(result.resultOrPartial(e -> LOGGER.error("n consequiu escrever")).orElseThrow(),writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        INSTANCE.currentTilesAdd(xz);
        INSTANCE.tileMapPut(xz,generatedTile);
        return generatedTile;
    }

    public static void printCurrentTilesSet() {
        LOGGER.info("Current Tiles: {}" , INSTANCE.getCurrentTilesAsString());
    }

    public static void printTileMapHash() {
        LOGGER.info("Tile Map: {}" , INSTANCE.getTileMapAsString());
    }

}
