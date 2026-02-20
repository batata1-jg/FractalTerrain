package me.batata_1.fractalterrain.ml;//package me.joaopalmeiras.terrenomod.ml;
import static me.batata_1.fractalterrain.ml.tensorProviders.MapProvider.sampleMap;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.DebugTensors.seeTensor;




import ai.onnxruntime.*;


import com.mojang.datafixers.util.Pair;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class teste {

    public static void inference(MinecraftServer server) throws OrtException, ExecutionException, InterruptedException, IOException {

        OnnxTensor t = sampleMap(Pair.of(0,0),new long[]{5,64,64});
        seeTensor(t,"elev",false,0);
        seeTensor(t,"temp",false,1);
        seeTensor(t,"tempampl",false,2);
        seeTensor(t,"precip",false,3);
        seeTensor(t,"precipampl",false,4);

    }




    public static void main(String[] args) throws OrtException {
        LOGGER.info("oiiiiiiii");

    }

}
