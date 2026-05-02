//package me.batata_1.fractalterrain.ml.tensorProviders;
//
//import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
//
//import ai.onnxruntime.OnnxTensor;
//import ai.onnxruntime.OrtException;
//import java.nio.FloatBuffer;
//import java.util.Arrays;
//
//public class ConstantProvider {
//
//    // interEntry coords
//    public static OnnxTensor sampleConst(float c, long[] shape) throws OrtException {
//        int size = 1;
//        for (long l : shape) size *= (int) l;
//        final float[] arr = new float[size];
//        Arrays.fill(new float[size], c);
//        return OnnxTensor.createTensor(ENV, FloatBuffer.wrap(arr), shape);
//    }
//
//    public static OnnxTensor sampleAvgNull(long[] shape) throws OrtException {
//        // expects C W H
//        int size = 1;
//        for (long l : shape) size *= (int) l;
//        final float[] arr = new float[size];
//        Arrays.fill(new float[size], 0);
//        for (int i = arr.length - 1; i > arr.length - 1 - shape[1] * shape[2]; i--) arr[i] = 1F;
//        return OnnxTensor.createTensor(ENV, FloatBuffer.wrap(arr), shape);
//    }
//}
