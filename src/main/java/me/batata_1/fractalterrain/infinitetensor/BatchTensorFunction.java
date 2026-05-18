package me.batata_1.fractalterrain.infinitetensor;

import java.util.List;
import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;

/**
 * Batched variant of TensorFunction.
 *
 * @param windowIndices the window indices for the batch
 * @param args          args.get(depIdx) is the list of dependency slices — one per window in the batch
 * @return list of output tensors, one per window in the batch
 */
@FunctionalInterface
public interface BatchTensorFunction {
    List<FloatTensor> apply(List<int[]> windowIndices, List<List<FloatTensor>> args);
}
