//package me.batata_1.fractalterrain.world.biome;
//
//import me.batata_1.fractalterrain.debug.Debug;
//import me.batata_1.fractalterrain.infinitetensor.FloatTensor;
//import me.batata_1.fractalterrain.infinitetensor.storage.TensorStorage;
//import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;
//
//import java.io.IOException;
//
//import static me.batata_1.fractalterrain.FractalTerrainInstance.pipeline;
//
//public class BiomeProvider {
//
//    private final TensorStorage final_tiles;
//
//
//    public BiomeProvider(String path) {
//        final_tiles = new TensorStorage(path + "/final_biome_tiles",512,xz -> {
//            int x = xz.getFirst();
//            int z = xz.getSecond();
//
//            float[] climate = pipeline.getClimate(x,z,);
//            float[] entries = new float[7<<18];
//            for (int ch = 0; ch < 4; ch++)
//                for (int px = 0; px < (1<<18); px++) {
//                    final float w = final_slice.data[(7<<18) + px];
//                    entries[(ch<<18) + px] = (w > 1e-6f) ? final_slice.data[(ch<<18) + px] / w : 0f;
//                }
//
//            float[] biomeVariables = ClimateVariableTransform.transform(x,z,);
//
//            FloatTensor t = new FloatTensor(biomeVariables,new long[]{5,512,512});
//
//            try {
//                Debug.seeTensor(t.get(),"final" + x + " " + z ,false,0);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            return t;
//        });
//    }
//}
