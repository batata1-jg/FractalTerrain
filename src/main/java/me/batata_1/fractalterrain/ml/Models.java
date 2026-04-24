package me.batata_1.fractalterrain.ml;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.references.Reference.ModID;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

public class Models {

    private static final ConcurrentHashMap<String, CompletableFuture<OrtSession>> MODEL_CACHE =
            new ConcurrentHashMap<>();

    public static synchronized void initialize() {
        for (final var e : MODEL_CACHE.entrySet()) {
            try {
                e.getValue().complete(fetchModel(e.getKey()));
            } catch (OrtException | IOException ex) {
                LOGGER.warn("could not load model {} with dml, falling back to cpu", e.getKey());
                try {
                    e.getValue()
                            .complete(fetchModel(
                                    e.getKey().substring(0, e.getKey().indexOf("&dml")) + "&cpu"));
                } catch (OrtException | IOException exc) {
                    throw new RuntimeException(exc);
                }
            }
        }
    }

    public static synchronized CompletableFuture<OrtSession> getOrCreateModel(String path) {
        return MODEL_CACHE.computeIfAbsent(path + "&cpu", key -> new CompletableFuture<>());
    }

    public static synchronized CompletableFuture<OrtSession> getOrCreateDirectModel(String path) {
        return MODEL_CACHE.computeIfAbsent(path + "&dml", key -> new CompletableFuture<>());
    }

    private static synchronized OrtSession fetchModel(String key) throws OrtException, IOException {
        String path = key.substring(0, key.indexOf("&"));
        Identifier modelId = Identifier.of(ModID, path + ".onnx");
        assert modelId != null;
        LOGGER.info(modelId.toString());
        Optional<Resource> resource =
                FractalTerrainInstance.getServer().getResourceManager().getResource(modelId);
        if (resource.isEmpty()) throw new RuntimeException("could not find model weights location");
        try (final var opt = new OrtSession.SessionOptions()) {
            opt.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL);
            if (key.contains("&dml")) opt.addDirectML(0);
            opt.setMemoryPatternOptimization(true);
            opt.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            InputStream inputStream = resource.get().getInputStream();
            final byte[] modelArr = inputStream.readAllBytes();
            return ENV.createSession(modelArr, opt);
        }
    }
}
