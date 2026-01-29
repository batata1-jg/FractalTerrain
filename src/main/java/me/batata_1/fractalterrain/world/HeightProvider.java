package me.batata_1.fractalterrain.world;

import com.mojang.datafixers.util.Pair;

import me.batata_1.fractalterrain.ml.diffusion.CoarseStage;
import me.batata_1.fractalterrain.ml.diffusion.DecoderStage;
import me.batata_1.fractalterrain.ml.diffusion.LatentStage;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.util.CoordTranslator.*;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

public class HeightProvider {

    public static final EntryStorage<Tile> finalTiles = new EntryStorage<>("final_tiles", 512,Tile.getCodec());

    public static void bootstrapTileStorages() {
        finalTiles.bootstrap();
        CoarseStage.bootstrap();
        LatentStage.bootstrap();
        DecoderStage.bootstrap();
    }

    private static float interpolate(float val0 , float val1 , float distToVal0) {
//        return MathHelper.lerp(distToVal0,val0,val1);
        return (val0*(INTERPOLATION_SCALE - distToVal0) + val1*distToVal0 ) / INTERPOLATION_SCALE;
    }

    private static CompletableFuture<Tile> getFinalTile(Pair<Integer,Integer> xz) {
        return finalTiles.getEntry(toCurTile(xz));
    }

    private static ArrayList<Pair<Integer,Integer>> getInterpolationNodes(Pair<Integer,Integer> xz) {
        ArrayList<Pair<Integer,Integer>> r = new ArrayList<>();
        var curEntry = toCurEntry(toTileCoords(xz));
        var interpolationNode1 = backToNormalCoords(
                curEntry,
                toCurTile(xz)
        );
        r.add(interpolationNode1);
        r.add(Pair.of( interpolationNode1.getFirst()                             , interpolationNode1.getSecond() + (int) INTERPOLATION_SCALE));
        r.add(Pair.of( interpolationNode1.getFirst() + (int) INTERPOLATION_SCALE , interpolationNode1.getSecond()));
        r.add(Pair.of( interpolationNode1.getFirst() + (int) INTERPOLATION_SCALE , interpolationNode1.getSecond() + (int) INTERPOLATION_SCALE));
        return r;
    }

    private static int bilinearlyInterpolate(Float[] valNode, int distX ,int distZ ) {

        float mixed12 = interpolate(valNode[0],valNode[1],distZ);
        float mixed34 = interpolate(valNode[2],valNode[3],distZ);

        return Math.round(interpolate(mixed12,mixed34,distX));
    }

    //xz real coords
    private static int getInitialElev(int x,int z) {
        var interpNodes = getInterpolationNodes(Pair.of(x,z));


        int distX = Math.abs(interpNodes.get(0).getFirst() - x);
        int distZ = Math.abs(interpNodes.get(0).getSecond() - z);
        Float[] nodeVals = new Float[4];
        for(int i=0 ; i<4 ; i++) {
            try {
                nodeVals[i] = getFinalTile(interpNodes.get(i)).get().entry(toCurEntry(toTileCoords(interpNodes.get(i))));
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return bilinearlyInterpolate(nodeVals,distX,distZ);
    }

    public static int getElevation(int x , int z) {


        int init = getInitialElev(x,z);

        return init;
    }

}
