package me.batata_1.fractalterrain.ml.diffusion;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

public class CoarseStage {

    private static final EntryStorage<TileRegion> coarseTiles = new EntryStorage<>("coarse",32);

    public static void bootstrap() {
        coarseTiles.bootstrap();
    }

    public static OnnxTensor[] run(
            Pair<Integer,Integer> xz,
//            float[][][] map,
            int seed
    ) throws OrtException {
        float[] v = new float[32*32*7];
        Arrays.fill(v,0);
        OnnxTensor t = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(v), new long[]{7, 32, 32});
        OnnxTensor[] arr = new OnnxTensor[4];
        Arrays.fill(arr,t);
        return arr;
    }

    //[] -> C
    //inter coords
    public static OnnxTensor getCoarseTiles(Pair<Integer,Integer> xz) throws ExecutionException, InterruptedException {
        if(coarseTiles.existsEntry(xz)) {
            TileRegion t = coarseTiles.getEntry(xz).get();
            if( t.isComplete() ) return t.get();
        }
        return completeTiles(xz);
    }

    private static OnnxTensor completeTiles(Pair<Integer,Integer> xz ) throws ExecutionException, InterruptedException {
        TileRegion[] t = new TileRegion[7];
        int state = 0;
        if(coarseTiles.get(0).existsEntry(xz)) {
            for(int i=0 ; i<t.length ; i++) t[i] = coarseTiles.get(i).getEntry(xz).get();
            state = t[0].getState();
        }

        for( int i=0 ; i<4 ; i++) if( (state & (1<<i)) == 0 ) {
            t = runSingleInferenceStep(
                    xz.getFirst()+interX[i],
                    xz.getSecond()+interZ[i],
                    t);
        }
        return t;
    }

    private static synchronized TileRegion[] runSingleInferenceStep(int x, int z, TileRegion[] t) {
        TileRegion[][] batch = run(Pair.of(x,z),1);
        for(int i=0 ; i<4 ; i++) {
            for(int j=0 ; j<7 ; j++) {
                var interEntryCoords = Pair.of(x+dx[i],z+dz[i]);
                if(!coarseTiles.get(j).existsEntry(interEntryCoords))
                    coarseTiles.get(j).addOrOverwriteEntry(,interEntryCoords);
            }
        }
    }

}



