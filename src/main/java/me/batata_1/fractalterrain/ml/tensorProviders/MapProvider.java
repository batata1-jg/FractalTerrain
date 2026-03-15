package me.batata_1.fractalterrain.ml.tensorProviders;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.math.CoordTranslator.toEntry;
import static me.batata_1.fractalterrain.world.ContinentalScaleMapProvider.*;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;
import java.nio.FloatBuffer;

public class MapProvider {

  public static OnnxTensor sampleMap(Pair<Integer, Integer> xz, long[] shape) throws OrtException {
    //        instance.getServer();
    // expects 5 channels
    if (shape[0] != 5) {
      RuntimeException e = new RuntimeException("shapes do not match");
      e.printStackTrace();
      throw e;
    }

    int size = 1;
    for (long l : shape) size *= (int) l;
    FloatBuffer noise = FloatBuffer.allocate(size);
    int of = -1;
    if (2 < shape.length) of += shape.length - 2;

    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < shape[of + 1]; j++) {
        for (int k = 0; k < shape[of + 2]; k++) {
          var coord = toEntry(Pair.of(j, k), xz, (int) (shape[1] / 2));
          int x = coord.getFirst();
          int z = coord.getSecond();
          double val =
              switch (i) {
                case 0 -> sampleElev(x, z) * elevSettings.amplitude();
                case 1 ->
                    sampleTemperature(x, z)
                        * tempSettings.amplitude()
                        * Math.max(sampleElev(x, z), 0);
                case 2 -> sampleThermalAmplitude(x, z) * tempSTDSettings.amplitude();
                case 3 -> samplePrecipitation(x, z) * precipSettings.amplitude();
                case 4 ->
                    samplePrecipitationAmplitude(x, z)
                        * precipSTDSettings.amplitude()
                        * samplePrecipitation(x, z);
                default ->
                    throw new IllegalStateException("Unexpected value in creating map: " + i);
              };

          noise.put((float) val);
        }
      }
    }
    noise.flip();

    return OnnxTensor.createTensor(ENV, noise, shape);
  }
}
