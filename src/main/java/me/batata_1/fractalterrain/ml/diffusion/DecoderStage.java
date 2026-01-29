package me.batata_1.fractalterrain.ml.diffusion;

import ai.onnxruntime.OnnxTensor;
import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DecoderStage extends StorageInterface {

    protected DecoderStage() {
        super( new EntryStorage<>("decoder"),256*256*2, new long[]{2,256,256});
    }

    public static OnnxTensor[] run(
            Pair<Integer,Integer> xz,
            TileRegion[][] latents,
            int seed
    ) {
        List<List<Float>> h = new ArrayList<>();
        for(int i=0 ; i<256 ; i++) {
            h.add(new ArrayList<>());
            for(int j=0 ; j<256 ; j++) {
                h.get(i).add(0.0f);
            }
        }
        var t = new TileRegion(h, (byte) 1);
        var o = new TileRegion[2];
        Arrays.fill(o, t);
        var v = new TileRegion[4][2];
        Arrays.fill(v,o);
        return v;
    }

    @Override
    OnnxTensor[] runInference(int x, int z) {
        return new OnnxTensor[0];
    }
}
