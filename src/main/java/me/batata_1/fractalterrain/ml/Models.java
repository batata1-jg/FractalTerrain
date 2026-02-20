package me.batata_1.fractalterrain.ml;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
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
    public static final OrtSession.SessionOptions DEFAULT_OPTIONS;

    static {
        try (var opt = new OrtSession.SessionOptions()) {
            opt.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL);
            opt.setMemoryPatternOptimization(true);
            opt.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            DEFAULT_OPTIONS = opt;

        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized OrtSession getOrCreateModel(String path) {
        return m.computeIfAbsent(path, Models::fetchModel);
    }

    private static synchronized OrtSession fetchModel(String path ) {
        Identifier modelId = Identifier.of(ModID,path + ".onnx");
        assert modelId != null;
        LOGGER.info(modelId.toString());
        Optional<Resource> resource = FractalTerrainInstance.getServer().getResourceManager().getResource(modelId);
        if( resource.isEmpty() ) throw new RuntimeException("could not find model weights location");
        try (var opt = new OrtSession.SessionOptions()){

            opt.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL);
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
