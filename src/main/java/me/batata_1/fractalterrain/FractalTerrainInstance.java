package me.batata_1.fractalterrain;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Path;



public class FractalTerrainInstance {


    private final Path pathMundo;
    private final MinecraftServer curServer;

    public FractalTerrainInstance(MinecraftServer server) throws IOException {
        curServer = server;
        pathMundo = server.getSavePath(WorldSavePath.ROOT).normalize();

    }

    public MinecraftServer getServer() { return curServer;}

    public Path getTilesDir() { return Path.of(pathMundo + "/terrenomod"); }


}
