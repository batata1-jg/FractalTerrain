package me.batata_1.fractalterrain.world.gen;

import static me.batata_1.fractalterrain.FractalTerrainInstance.pipeline;

import com.mojang.datafixers.util.Pair;
// import me.batata_1.fractalterrain.ml.tensorProviders.GaussianNoisePatchProvider;
import me.batata_1.fractalterrain.debug.Debug;
// import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;
import me.batata_1.fractalterrain.infinitetensor.storage.TensorStorage;

public class ReliefProvider {

    private final TensorStorage final_tiles;

    public ReliefProvider(String path) {
        final_tiles = new TensorStorage(path + "/final_relief_tiles", 512, xz -> {
            int x = xz.getFirst();
            int z = xz.getSecond();

            FloatTensor final_slice = pipeline.getDecoderSlice(x, z);
            float[] entries = new float[7 << 18];
            for (int ch = 0; ch < 4; ch++)
                for (int px = 0; px < (1 << 18); px++) {
                    final float w = final_slice.data[(7 << 18) + px];
                    entries[(ch << 18) + px] = (w > 1e-6f) ? final_slice.data[(ch << 18) + px] / w : 0f;
                }

            FloatTensor t = new FloatTensor(entries, new int[] {7, 512, 512});
            Debug.tensor.see(t.get(), "final" + x + " " + z, false, 0);
            return t;
        });
    }

    public TensorStorage getStorage() {
        return final_tiles;
    }

    public Float get_entry(Pair<Integer, Integer> xz, int ch) {
        return final_tiles.getValue(xz, ch);
    }

    public Float getElev(Pair<Integer, Integer> xz) {
        return get_entry(xz, 0);
    }

    public Float getRefinedGrad(Pair<Integer, Integer> xz) {
        return get_entry(xz, 4);
    }

    public Float getGradX(Pair<Integer, Integer> xz) {
        return get_entry(xz, 2);
    }

    public Float getGradY(Pair<Integer, Integer> xz) {
        return get_entry(xz, 3);
    }

    public Float getRes(Pair<Integer, Integer> xz) {
        return get_entry(xz, 6);
    }

    public Float getBlurredElev(Pair<Integer, Integer> xz) {
        return get_entry(xz, 1);
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
