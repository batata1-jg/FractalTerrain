package me.batata_1.fractalterrain.world.biome;

import me.batata_1.fractalterrain.FractalTerrainInstance;
import me.batata_1.fractalterrain.debug.Debug;
import me.batata_1.fractalterrain.infinitetensor.storage.FloatTensor;
import me.batata_1.fractalterrain.infinitetensor.storage.TensorStorage;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.FractalTerrainInstance.pipeline;

public class BiomeProvider {

    private final TensorStorage final_tiles;


    public BiomeProvider(String path) {
        final_tiles = new TensorStorage(path + "/final_biome_tiles",512,xz -> {
            int x = xz.getFirst();
            int z = xz.getSecond();
            FloatTensor reliefTensor =  FractalTerrainInstance.getReliefProvider().getStorage().getEntry(xz);

            final float[] elev = Arrays.copyOfRange(reliefTensor.data,0,1<<18);
            final float[] grad = Arrays.copyOfRange(reliefTensor.data,4<<18,5<<18);
            final float[] lowFreqGrad = Arrays.copyOfRange(reliefTensor.data,5<<18,6<<18);
            final float[] climate = pipeline.getClimate(x,z,elev);
            final float[] res = Arrays.copyOfRange(climate,6<<18,7<<18);
            final float[] biomeVariables = ClimateVariableTransform.transform(x,z,elev,grad,lowFreqGrad,climate,res);

            FloatTensor t = new FloatTensor(biomeVariables,new int[]{5,512,512});

            try {
                Debug.seeTensor(t.get(),"final" + x + " " + z ,false,0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return t;
        });

    }



}
