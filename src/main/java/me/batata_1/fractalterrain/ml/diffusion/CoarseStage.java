package me.batata_1.fractalterrain.ml.diffusion;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

public class CoarseStage extends StorageInterface{

    protected CoarseStage() {
        super(new EntryStorage<>("coarse"), 32*32*7, new long[]{7,32,32});
    }

    public static OnnxTensor[] run(
            Pair<Integer,Integer> xz,
            float[][][] map,
            int seed
    ) throws OrtException {
        float[] v = new float[32*32*7];
        Arrays.fill(v,0);
        OnnxTensor t = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(v), new long[]{7, 32, 32});
        OnnxTensor[] arr = new OnnxTensor[4];
        Arrays.fill(arr,t);
        return arr;
    }


    @Override
    public OnnxTensor[] runInference(int x , int z) {

        return null;
    }
}



