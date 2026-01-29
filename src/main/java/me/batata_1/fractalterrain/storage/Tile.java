package me.batata_1.fractalterrain.storage;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;

import java.io.*;
import java.nio.FloatBuffer;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.ENV;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;

public class Tile {

    protected final float[] entries;
    protected final long[] shape;
    protected final long[] cProd;

    public static void serialize(String path, Tile t) throws IOException {
        int el = t.entries.length;
        int sl = t.shape.length;
        float[] arr = new float[el+sl+1];
        System.arraycopy(t.entries, 0, arr, 0, el);
        for( int i=el ; i<(el+sl) ; i++)
            arr[i] = (float) t.shape[i-el];
        arr[el+sl] = (float) el;
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path + ".ser"));
        out.writeObject(arr);
    }

    public static <T extends Tile> T deserialize(String path) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream( new FileInputStream(path + ".ser"));
        float[] arr = (float[]) in.readObject();
        int slAddEl = arr.length -1;
        int el = (int) arr[slAddEl];
        int sl = slAddEl - el;
        float[] entries = new float[el];
        System.arraycopy(arr, 0, entries, 0, el);
        long[] shape = new long[sl];
        for(int i=el ; i<el+sl ; i++) shape[i-el] = (long) arr[i];
        return (T) new Tile(entries,shape);
    }

    public long[] calcProd() {
        int len = shape.length;
        long[] c = new long[len];
        c[len-1] = 1;
        for(int id=0 ; id<len-1 ; id++) {
            c[len-2 - id] = shape[id]*c[len-1 -id];
        }
        return c;
    }

    public Tile( OnnxTensor h ) {
        entries = h.getFloatBuffer().array();
        shape = h.getInfo().getShape();
        cProd = calcProd();
    }

    public Tile(float[] en , long[] sh) {
        entries = en;
        shape = sh;
        cProd = calcProd();
    }

    public float entryAt(Pair<Integer,Integer> xz) {
        if( shape.length != 2 ) {
            LOGGER.error("cannot use pair because tensor is not 2D");
            throw new RuntimeException();
        }
        return entryAt(new int[]{xz.getFirst(), xz.getSecond()});
    }

    public float entryAt(int[] pos) {
        if( shape.length != pos.length ) {
            LOGGER.error("shape and pos dont match");
            throw new RuntimeException();
        }
        int idx=0;
        for(int i=0 ; i<shape.length ; i++)
            idx += (int) (cProd[i]*pos[i]);
        return entries[idx];
    }

    public OnnxTensor get() {
        try {
            return OnnxTensor.createTensor(ENV, FloatBuffer.wrap(entries),shape);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

}
