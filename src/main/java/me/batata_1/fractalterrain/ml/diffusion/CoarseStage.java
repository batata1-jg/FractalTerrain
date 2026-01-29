package me.batata_1.fractalterrain.ml.diffusion;

import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CoarseStage {

    private static final ArrayList<EntryStorage<TileRegion>> coarseTiles = new ArrayList<>();

    public static void bootstrap() {
        for( int i=0 ; i<7 ; i++) {
            coarseTiles.add( new EntryStorage<>("coarse/" + i, 32, TileRegion.getRegionCodec()));
            coarseTiles.get(i).bootstrap();
        }
    }

    public static TileRegion[][] run(
            Pair<Integer,Integer> xz,
            float[][][] map,
            int seed
    ) {
        List<List<Float>> h = new ArrayList<>();
        for(int i=0 ; i<32 ; i++) {
            h.add(new ArrayList<>());
            for(int j=0 ; j<32 ; j++) {
                h.get(i).add(0.0f);
            }
        }
        var t = new TileRegion(h, (byte) 1);
        var o = new TileRegion[7];
        Arrays.fill(o, t);
        var v = new TileRegion[4][7];
        Arrays.fill(v,o);
        return v;
    }

    //[] -> C
    //inter coords
    public static TileRegion[] getCoarseTiles(Pair<Integer,Integer> xz) throws ExecutionException, InterruptedException {
        if(coarseTiles.get(0).existsEntry(xz)) {
            TileRegion[] t = new TileRegion[7];
            for(int i=0 ; i<t.length ; i++) t[i] = coarseTiles.get(i).getEntry(xz).get();
            if( t[0].isComplete() ) return t;
        }
        return RunNecessaryInference(xz);
    }

    private static TileRegion[] RunNecessaryInference(Pair<Integer,Integer> xz ) {

    }

}



