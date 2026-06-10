package me.batata_1.fractal_terrain.infinitetensor;

import me.batata_1.fractal_terrain.storage.FloatTensor;

public class AdditiveInfiniteTensor extends InfiniteTensor {

    public AdditiveInfiniteTensor(
            String id,
            int[] shape,
            TensorWindow outputWindow,
            TensorFunction function,
            TensorFunction.BatchTensorFunction batchFunction,
            int batchSize,
            InfiniteTensor[] deps,
            TensorWindow[] depWindows,
            long cacheLimitBytes) {
        super(id, shape, outputWindow, function, batchFunction, batchSize, deps, depWindows, cacheLimitBytes);
    }

    @Override
    protected void updateOutput(FloatTensor output, FloatTensor src, int[][] dstRegion, int[][] srcRegion) {
        output.addFrom(src, dstRegion, srcRegion);
    }
}
