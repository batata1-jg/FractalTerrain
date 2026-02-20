//package me.batata_1.fractalterrain.ml;
//
//import ai.onnxruntime.OnnxTensor;
//import ai.onnxruntime.OrtException;
//import ai.onnxruntime.OrtSession;
//import com.mojang.datafixers.util.Pair;
//
//import net.minecraft.resource.Resource;
//import net.minecraft.server.MinecraftServer;
//import net.minecraft.util.Identifier;
//import net.minecraft.util.math.random.Random;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.nio.FloatBuffer;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//import me.batata_1.fractalterrain.storage.Tile;
//import static me.batata_1.fractalterrain.references.Reference.ModID;
//import static me.batata_1.fractalterrain.references.Reference.LOGGER;
//
//import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;
//import static net.minecraft.util.math.MathHelper.nextGaussian;
//
//public class TileGenerator {
//
//
//    private static final long seed = 1;
//
//    private static final int NOISE_DIM = 512;
//    private static final long[] NOISE_TENSOR_SHAPE = {1,NOISE_DIM,1,1};
//
//    private static float[][] RunInference(MinecraftServer server , int hashCode ) throws IOException, OrtException {
//
//        try {
//            Optional<Resource> resource = server.getResourceManager().getResource(Identifier.of(ModID, "model/teste.onnx"));
//            InputStream stream = resource.orElseThrow().getInputStream();
//            byte[] modelArr = stream.readAllBytes();
//
//            var options = new OrtSession.SessionOptions();
//            options.addCPU(true);
//            //options.addCUDA(0);
//            var session = ENV.createSession(modelArr, options);
//
//            FloatBuffer bufferedNoise = FloatBuffer.allocate(NOISE_DIM);
//            for (int i = 0; i < NOISE_DIM; i++) {
//                bufferedNoise.put(nextGaussian(Random.create(server.getOverworld().getSeed() + hashCode), 0, 1));
//            }
//            bufferedNoise.flip();
//            OnnxTensor noise = OnnxTensor.createTensor(ENV, bufferedNoise, NOISE_TENSOR_SHAPE);
//            OnnxTensor alpha = OnnxTensor.createTensor(ENV, 1.0);
//
//            bufferedNoise.clear();
//
//            var inputs = Map.of("x", noise, "alpha", alpha);
//            try (var out = session.runInference(inputs)) {
//                OnnxTensor output = (OnnxTensor) out.get(0);
//
//                long[] shape = output.getInfo().getShape(); // [1, 2, 128, 128]
//                LOGGER.info("outTensorShape: {}", shape);
//                int c = (int) shape[1];
//                int h = (int) shape[2];
//                int w = (int) shape[3];
//
//                if (h != TILE_LENGTH || w != TILE_LENGTH) {
//                    LOGGER.error("output from model does not match with tile length");
//                    throw new RuntimeException();
//                }
//
//                FloatBuffer buffer = output.getFloatBuffer();
//                float[] OneDimData = new float[c * h * w];
//                buffer.get(OneDimData);
//
//                float[][] firstChannel = new float[h][w];
//
//                for (int i = 0; i < h; i++) {
//                    System.arraycopy(OneDimData, i * w, firstChannel[i], 0, w);
//                }
//                return firstChannel;
//            }
//        } catch (NoClassDefFoundError e) {
//            LOGGER.error("MORREUUU{}", e.getMessage());
//        }
//        return null;
//    }
//
//    private static Float UnNormalize(float f) {
//        return ((f+1)*MAX_ML_HEIGHT)/2;
//    }
//
//    public static Tile createTile(Pair<Integer,Integer> xz , MinecraftServer server ,  )  {
//        List<List<Float>> h = new ArrayList<>();
//        try {
//            float[][] fromInference = RunInference(server , xz.hashCode() );
//            for(int i=0 ; i<TILE_LENGTH ; i++) {
//                h.add(new ArrayList<>());
//                for(int j=0 ; j<TILE_LENGTH ; j++) {
//                    assert fromInference != null;
//                    h.get(i).add(UnNormalize(fromInference[i][j]));
//                }
//            }
//        } catch (IOException | OrtException e) {
//            throw new RuntimeException(e);
//        }
//        return new Tile(h);
//    }
//
//}
