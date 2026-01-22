package me.batata_1.fractalterrain.world;

import com.mojang.datafixers.util.Pair;

import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.storage.TileStorage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.util.CoordTranslator.*;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;


public class HeightProvider {

    private static float interpolate(float val0 , float val1 , float distToVal0) {
        return (val0*(INTERPOLATION_SCALE - distToVal0) + val1*distToVal0 ) / INTERPOLATION_SCALE;
    }

    //biLinear
    private static CompletableFuture<Tile> getOrCreateTile(Pair<Integer,Integer> xz) {
        var curTile = toCurTile(xz);
        if(TileStorage.existsTile(curTile)) return TileStorage.getTile(curTile);
        return TileStorage.genTile(curTile);
    }

    private static int getFromInterpolation(int x,int z) throws ExecutionException, InterruptedException {
        var curEntry = toCurEntry(toTileCoords(Pair.of(x,z)));
        var interpolationNode1 = backToNormalCoords(
                curEntry,
                toCurTile(Pair.of(x,z))
        );
        int distFromNode1x = Math.abs(interpolationNode1.getFirst() - x);
        int distFromNode1z = Math.abs(interpolationNode1.getSecond() - z);

        var interpolationNode2 = Pair.of( interpolationNode1.getFirst()                             , interpolationNode1.getSecond() + (int) INTERPOLATION_SCALE);
        var interpolationNode3 = Pair.of( interpolationNode1.getFirst() + (int) INTERPOLATION_SCALE , interpolationNode1.getSecond());
        var interpolationNode4 = Pair.of( interpolationNode1.getFirst() + (int) INTERPOLATION_SCALE , interpolationNode1.getSecond() + (int) INTERPOLATION_SCALE);
        int valNode1 = getOrCreateTile(interpolationNode1).get().entry(toCurEntry(toTileCoords(interpolationNode1)));
        int valNode2 = getOrCreateTile(interpolationNode2).get().entry(toCurEntry(toTileCoords(interpolationNode2)));
        int valNode3 = getOrCreateTile(interpolationNode3).get().entry(toCurEntry(toTileCoords(interpolationNode3)));
        int valNode4 = getOrCreateTile(interpolationNode4).get().entry(toCurEntry(toTileCoords(interpolationNode4)));


        float mixed12 = interpolate(valNode1,valNode2,distFromNode1z);
        float mixed34 = interpolate(valNode3,valNode4,distFromNode1z);

        return Math.round(interpolate(mixed12,mixed34,distFromNode1x));
    }

    public static int getElevation(int x , int z) {
        try {
            return getFromInterpolation(x,z);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
