package me.batata_1.fractalterrain.storage;

import static me.batata_1.fractalterrain.FractalTerrainInstance.ENV;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class Tile {

    protected float[] entries;
    protected long[] shape;
    protected long[] cProd;

    // TODO not serializing to correct function

    public void serialize(String path) throws IOException {
        //        LOGGER.info("serializando?");
        int el = this.entries.length;
        int sl = this.shape.length;
        float[] arr = new float[el + sl + 1];
        System.arraycopy(this.entries, 0, arr, 0, el);
        for (int i = el; i < (el + sl); i++) arr[i] = (float) this.shape[i - el];
        arr[el + sl] = (float) el;
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path + ".ser"));
        out.writeObject(arr);
    }

    public void deserialize(String path) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(path + ".ser"));
        float[] arr = (float[]) in.readObject();
        int slAddEl = arr.length - 1;
        int el = (int) arr[slAddEl];
        int sl = slAddEl - el;
        float[] entries = new float[el];
        System.arraycopy(arr, 0, entries, 0, el);
        long[] shape = new long[sl];
        for (int i = el; i < el + sl; i++) shape[i - el] = (long) arr[i];
        this.entries = entries;
        this.shape = shape;
        this.cProd = calcProd();
    }

    public long[] calcProd() {
        int len = shape.length;
        long[] c = new long[len];
        c[len - 1] = 1;
        for (int id = len - 2; id >= 0; id--) {
            c[id] = shape[id + 1] * c[id + 1];
        }
        return c;
    }

    public Tile() {}

    public Tile(OnnxTensor h) {
        entries = h.getFloatBuffer().array();
        shape = h.getInfo().getShape();
        cProd = calcProd();
    }

    public Tile(float[] en, long[] sh) {
        entries = en;
        shape = sh;
        cProd = calcProd();
    }

    public float entryAt(Pair<Integer, Integer> xz) {
        if (shape.length != 2) {
            LOGGER.error("cannot use pair because tensor is not 2D");
            throw new RuntimeException();
        }
        return entryAt(new long[] {xz.getFirst(), xz.getSecond()});
    }

    public float entryAt(long[] pos) {

        checkRank(pos.length);
        int idx = 0;
        for (int i = 0; i < shape.length; i++) idx += (int) (cProd[i] * pos[i]);
        return entries[idx];
    }

    public long[] getShape() {
        return shape;
    }

    public long getSize() {
        return cProd[0] * shape[0];
    }

    public long getLength() {
        return shape[shape.length - 1];
    }

    public OnnxTensor get() {
        try {
            return OnnxTensor.createTensor(ENV, FloatBuffer.wrap(entries), shape);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkRank(int len) {
        if (this.shape.length != len) {
            LOGGER.error("ranks do not match {} {}", this.shape.length, len);
            var e = new RuntimeException();
            e.printStackTrace();
            throw e;
        }
    }

    private void checkShapes(long[] arr) {
        if (!Arrays.equals(this.shape, arr)) {
            LOGGER.error("shapes do not match {} {}", this.shape, arr);
            var e = new RuntimeException();
            e.printStackTrace();
            throw e;
        }
    }

    public void add(Tile t) {
        checkShapes(t.shape);
        for (int i = 0; i < this.entries.length; i++) this.entries[i] += t.entries[i];
    }

    public void multScalar(float s) {
        for (int i = 0; i < this.entries.length; i++) this.entries[i] *= s;
    }
}
