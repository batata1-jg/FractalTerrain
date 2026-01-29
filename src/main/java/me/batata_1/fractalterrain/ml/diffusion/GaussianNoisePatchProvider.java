package me.batata_1.fractalterrain.ml.diffusion;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.util.CoordTranslator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.io.BufferedInputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.CoordTranslator.flattenedCoords;
import static me.batata_1.fractalterrain.util.CoordTranslator.toEntry;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.ENV;
import static net.minecraft.util.math.MathHelper.nextGaussian;

public class GaussianNoisePatchProvider {

    //bootstrap dps

    public static MinecraftServer server;
    //interEntry coords
    public static OnnxTensor sample(Pair<Integer,Integer> xz, long[] shape) throws OrtException {

        FloatBuffer noise = FloatBuffer.allocate((int) (shape[0]*shape[1]*shape[2]));

        long hashSeed;
        for (int i = 0; i < shape[0] ;i++) {
            for(int j=0 ; j< shape[1] ; j++) {
                for(int k=0; k< shape[2] ; k++) {
                    // pode quebrar aqui?
                    //Hashing.sha256().hashLong(seed).asLong()
                    var p = toEntry(Pair.of(j,k),xz, (int) (shape[1]/2));
                    hashSeed = Arrays.hashCode(new int[]{p.getFirst(), p.getSecond(),k, (int) server.getOverworld().getSeed()});
                    noise.put(nextGaussian(Random.create(hashSeed), 0, 1));
                }
            }
        }
        noise.flip();

        return OnnxTensor.createTensor(ENV,noise,shape);
    }


}
