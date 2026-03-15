package me.batata_1.fractalterrain.ml.tensorProviders;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.math.CoordTranslator.toEntry;
import static net.minecraft.util.math.MathHelper.nextGaussian;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.google.common.hash.Hashing;
import com.mojang.datafixers.util.Pair;
import java.nio.FloatBuffer;
import net.minecraft.util.math.random.Random;

public class GaussianNoisePatchProvider {

  public static long wseed;

  public static void setSeed(long seed) {
    wseed = seed;
  }

  public static long getHash(long[] arr) {
    long hash = 0;
    for (long l : arr) {
      hash = Hashing.sha256().hashLong(hash + l).asLong();
    }
    return hash;
  }

  public static OnnxTensor sampleNoise(Pair<Integer, Integer> xz, long[] shape, long seed)
      throws OrtException {

    int size = 1;
    for (long l : shape) size *= (int) l;

    FloatBuffer noise = FloatBuffer.allocate(size);
    int of = 0;
    if (3 < shape.length) of += shape.length - 3;

    for (int i = 0; i < shape[of]; i++) {
      for (int j = 0; j < shape[of + 1]; j++) {
        for (int k = 0; k < shape[of + 2]; k++) {
          var p = toEntry(Pair.of(j, k), xz, (int) (shape[of + 1] / 2));
          float val =
              nextGaussian(
                  Random.create(getHash(new long[] {p.getFirst(), p.getSecond(), i, seed + wseed})),
                  0,
                  1);

          noise.put(val);
        }
      }
    }
    noise.flip();

    return OnnxTensor.createTensor(ENV, noise, shape);
  }
}
