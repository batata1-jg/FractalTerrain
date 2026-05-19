package me.batata_1.fractalterrain.world.gen.relief;

import ai.onnxruntime.OnnxTensor;
import com.mojang.datafixers.util.Pair;

import java.nio.file.Path;
import java.util.concurrent.*;

import me.batata_1.fractalterrain.FractalTerrainInstance;

import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.util.Debug;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;

public class ReliefProvider {

    private final EntryStorage final_tiles;

    public ReliefProvider(Path pathSave) {
        final_tiles = new EntryStorage(pathSave.toString(),"final_tiles",512,xz->{
            Tile t = new Tile(FractalTerrainInstance.pipeline.getDecoderSice(xz.getFirst(),xz.getSecond()));
            Debug.seeFinal(t.get(),xz.getFirst(),xz.getSecond());
            return t;
        });
    }

    public float getElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 0);
    }

    public float getBlurredElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 1);
    }

    public float getGradX(Pair<Integer, Integer> xz) {
        return getValue(xz, 2);
    }

    public float getGradY(Pair<Integer, Integer> xz) {
        return getValue(xz, 3);
    }

    public float getRefinedGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 4);
    }

    public float getBlurredGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 5);
    }

    public float getRes(Pair<Integer, Integer> xz) {
        return getValue(xz, 6);
    }

    private float getValue(Pair<Integer, Integer> xz, int ch) {
        try {
            return final_tiles.getValue(xz, ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public EntryStorage getStorage() {
        return final_tiles;
    }

    public OnnxTensor getTilesAsTensor(int i, int j) {
        try {
            return final_tiles.getEntry(Pair.of(i, j)).get().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
