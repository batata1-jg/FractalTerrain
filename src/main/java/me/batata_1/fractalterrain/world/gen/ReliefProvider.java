package me.batata_1.fractalterrain.world.gen;

import com.mojang.datafixers.util.Pair;
//import me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider;
import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;
import me.batata_1.fractalterrain.infinitetensor.storage.TensorStorage;
import me.batata_1.fractalterrain.debug.Debug;
//import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.FractalTerrainInstance.pipeline;

public class ReliefProvider {

    private final TensorStorage final_tiles;

    public ReliefProvider(String path) {
        final_tiles = new TensorStorage(path + "/final_relief_tiles",512, xz -> {
            int x = xz.getFirst();
            int z = xz.getSecond();

            FloatTensor final_slice = pipeline.getDecoderSlice(x,z);
            float[] entries = new float[7<<18];
            for (int ch = 0; ch < 4; ch++)
                for (int px = 0; px < (1<<18); px++) {
                    final float w = final_slice.data[(7<<18) + px];
                    entries[(ch<<18) + px] = (w > 1e-6f) ? final_slice.data[(ch<<18) + px] / w : 0f;
                }

            me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor t = new me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor(entries,new int[]{7,512,512});

            try {
                Debug.seeTensor(t.get(),"final" + x + " " + z ,false,0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return t;
        });
    }


    public TensorStorage getStorage() {
        return final_tiles;
    }

    public Float get_entry(Pair<Integer,Integer> xz,int ch) {
        try {
            return final_tiles.getValue(xz,ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Float getElev(Pair<Integer, Integer> xz) {
        return get_entry(xz,0);
    }

    public Float getRefinedGrad(Pair<Integer, Integer> xz) {
        return get_entry(xz,4);
    }

    public Float getGradX(Pair<Integer, Integer> xz) {
        return get_entry(xz,2);
    }

    public Float getGradY(Pair<Integer, Integer> xz) {
        return get_entry(xz,3);
    }

    public Float getRes(Pair<Integer, Integer> xz) {
        return get_entry(xz,6);
    }

    public Float getBlurredElev(Pair<Integer, Integer> xz) {
        return get_entry(xz,1);
    }

    public double getContinentalElev(Pair<Integer, Integer> xz) {
        return 0;
    }

    public double getRawTemp(Pair<Integer, Integer> xz) {
        return 0;
    }

    public Float getRawTempSTD(Pair<Integer, Integer> xz) {
        return (float) 0;
    }

    public double getRawPrecip(Pair<Integer, Integer> xz) {
        return 0;
    }

    public Float getRawPrecipSTD(Pair<Integer, Integer> xz) {
        return (float) 0;
    }

    public int getRawGrad(Pair<Integer, Integer> xz) {
        return 0;
    }

    public double getBlurredGrad(Pair<Integer, Integer> xz) {
        return 0;
    }
}
