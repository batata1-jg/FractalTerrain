package me.batata_1.fractalterrain.util;

import ai.onnxruntime.*;
import java.util.Map;
import me.batata_1.fractalterrain.ml.Models;

public class MlUtil {

    private static OrtSession aveager;
    private static OrtSession slicer2d;
    private static OrtSession slicer;
    private static OrtSession merger;

    public static void initUtil() {

        slicer2d = Models.getOrCreateModel("ml_util/split2d");

        slicer = Models.getOrCreateModel("ml_util/split");
        merger = Models.getOrCreateModel("ml_util/merge");
    }

    public static OnnxTensor[] slice(OnnxTensor t) throws OrtException {
        var out = slicer.run(Map.of("x", t));
        OnnxTensor[] outTensor = new OnnxTensor[4];
        for (int i = 0; i < 4; i++) {
            outTensor[i] = (OnnxTensor) out.get(i);
        }
        return outTensor;
    }

    public static OnnxTensor[] slice2d(OnnxTensor t) throws OrtException {
        var out = slicer2d.run(Map.of("x", t));
        OnnxTensor[] outTensor = new OnnxTensor[4];
        for (int i = 0; i < 4; i++) {
            outTensor[i] = (OnnxTensor) out.get(i);
        }
        return outTensor;
    }

    public static OnnxTensor merge(OnnxTensor[] t) throws OrtException {
        var in = Map.of(
                "a", t[2],
                "b", t[0],
                "c", t[3],
                "d", t[1]);
        return (OnnxTensor) merger.run(in).get(0);
    }
}
