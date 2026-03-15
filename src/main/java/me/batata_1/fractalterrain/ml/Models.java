package me.batata_1.fractalterrain.ml;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectFunction;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Optional;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.references.Reference.ModID;

public class Models {

    private static final HashMap<String,OrtSession> m = new HashMap<>();

    public static synchronized OrtSession getOrCreateModel(String path) {
        return m.computeIfAbsent(path, path1 -> fetchModel(path1,false));
    }

    public static synchronized OrtSession getOrCreateDirectModel(String path) {
        return m.computeIfAbsent(path, path1 -> fetchModel(path1,true));
    }

    private static synchronized OrtSession fetchModel(String path, boolean useDirect) {
        Identifier modelId = Identifier.of(ModID,path + ".onnx");
        assert modelId != null;
        LOGGER.info(modelId.toString());
        Optional<Resource> resource = FractalTerrainInstance.getServer().getResourceManager().getResource(modelId);
        if( resource.isEmpty() ) throw new RuntimeException("could not find model weights location");
        try (var opt = new OrtSession.SessionOptions()){

            opt.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL);
            if( useDirect ) opt.addDirectML(0);
            opt.setMemoryPatternOptimization(true);
            opt.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            InputStream inputStream = resource.get().getInputStream();
            byte[] modelArr = inputStream.readAllBytes();
            return ENV.createSession(modelArr, opt);
        } catch (IOException | OrtException e) {
            throw new RuntimeException(e);
        }
    }


}
