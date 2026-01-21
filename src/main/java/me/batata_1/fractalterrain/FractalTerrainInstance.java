package me.batata_1.fractalterrain;

import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.Tile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;


import static me.batata_1.fractalterrain.util.FractalTerrainUtil.interpretTileName;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;


public class FractalTerrainInstance {

    private final HashMap<Pair<Integer,Integer>, Tile> tileMap = new HashMap<>();
    private final HashSet<Pair<Integer,Integer>> currentTiles = new HashSet<>();
    private final Path pathMundo;
    private final MinecraftServer curServer;

    public FractalTerrainInstance(MinecraftServer server) throws IOException {
        curServer = server;
        pathMundo = server.getSavePath(WorldSavePath.ROOT).normalize();
        File file = new File(pathMundo + "/terrenomod");
        if(!file.exists()) file.mkdirs();
        String[] createdTiles = file.list();
        if(createdTiles != null) for (String tile : createdTiles) {
            var xz = interpretTileName(tile);
            if( xz == null) {
                LOGGER.error("invalid file, skipping");
                continue;
            }
            currentTiles.add(xz);
        }
        LOGGER.info("listar os files: {}" , (Object) file.list());
    }

    public MinecraftServer getServer() { return curServer;}

    public Path getTilesDir() { return Path.of(pathMundo + "/terrenomod"); }

    public boolean tileMapContainsKey(Pair<Integer, Integer> xz) { return tileMap.containsKey(xz); }

    public Tile tileMapGet(Pair<Integer, Integer> xz) { return tileMap.get(xz); }

    public void tileMapPut(Pair<Integer,Integer> xz , Tile t) { tileMap.put(xz,t); }

    public boolean currentTilesContainsKey(Pair<Integer, Integer> xz) { return currentTiles.contains(xz);}

    public void currentTilesAdd(Pair<Integer, Integer> xz) { currentTiles.add(xz); }

    public String getCurrentTilesAsString() { return currentTiles.toString();}

    public String getTileMapAsString() { return tileMap.toString();}


}
