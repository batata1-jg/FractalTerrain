package me.batata_1.fractal_terrain.infinitetensor;

import java.util.List;

/**
 * Function that computes a window of an InfiniteTensor.
 *
 * @param windowIndex the N-dimensional index of the window being computed
 * @param args        slices from each upstream dependency tensor, in the order declared
 * @return the computed FloatTensor with shape matching the output TensorWindow size
 */
@FunctionalInterface
public interface TensorFunction {
    FloatTensor apply(int[] windowIndex, List<FloatTensor> args);

    /**
     * Batched variant of TensorFunction.
     *
     * @param windowIndices the window indices for the batch
     * @param args          args.get(depIdx) is the list of dependency slices — one per window in the batch
     * @return list of output tensors, one per window in the batch
     */
    @FunctionalInterface
    interface BatchTensorFunction {
        List<FloatTensor> apply(List<int[]> windowIndices, List<List<FloatTensor>> args);
    }
}
