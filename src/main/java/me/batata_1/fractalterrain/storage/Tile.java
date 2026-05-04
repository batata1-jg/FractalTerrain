package me.batata_1.fractalterrain.storage;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.mojang.datafixers.util.Pair;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class Tile {

    protected float[] entries;
    protected long[] shape;
    protected long[] cProd;

    public void serialize(String path) throws IOException {
        final int el = this.entries.length;
        final int sl = this.shape.length;
        final float[] arr = new float[el + sl + 1];
        System.arraycopy(this.entries, 0, arr, 0, el);
        for (int i = el; i < (el + sl); i++) arr[i] = (float) this.shape[i - el];
        arr[el + sl] = (float) el;
        final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path + ".ser"));
        out.writeObject(arr);
        out.close();
    }

    public void deserialize(String path) throws IOException, ClassNotFoundException  {
        final ObjectInputStream in = new ObjectInputStream(new FileInputStream(path + ".ser"));
        final float[] arr = (float[]) in.readObject();
        in.close();
        final int slAddEl = arr.length - 1;
        final int el = (int) arr[slAddEl];
        final int sl = slAddEl - el;
        final float[] entries = new float[el];
        System.arraycopy(arr, 0, entries, 0, el);
        final long[] shape = new long[sl];
        for (int i = el; i < el + sl; i++) shape[i - el] = (long) arr[i];
        this.entries = entries;
        this.shape = shape;
        this.cProd = calcProd();
    }

    public long[] calcProd() {
        final int len = shape.length;
        final long[] c = new long[len];
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

    public Tile(FloatTensor h) {
        entries = h.data;
        int size = h.shape.length;
        long[] s = new long[size];
        for(int i=0 ; i<size ; i++){
            s[i] = h.shape[i];
        }
        this.shape = s;
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
        if (idx >= entries.length) throw new RuntimeException("outOfBOundsTensor: " + Arrays.toString(pos));
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
            return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(entries), shape);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkRank(int len) {
        if (this.shape.length != len) {
            LOGGER.error("ranks do not match {} {}", this.shape.length, len);
            throw new RuntimeException();
        }
    }

    private void checkShapes(long[] arr) {
        if (!Arrays.equals(this.shape, arr)) {
            LOGGER.error("shapes do not match {} {}", this.shape, arr);
            throw new RuntimeException();
        }
    }

    public void add(Tile t) {
        checkShapes(t.shape);
        for (int i = 0; i < this.entries.length; i++) this.entries[i] += t.entries[i];
    }

    public void multScalar(float s) {
        for (int i = 0; i < this.entries.length; i++) this.entries[i] *= s;
    }

    public float[] getBand(int i, int ch) {
        final long[] coords = new long[shape.length];
        Arrays.fill(coords, 0);
        coords[i] = ch;
        final float[] resp = new float[(int) (shape[shape.length - 1] * shape[shape.length - 2])];
        for (int k = 0; k < shape[shape.length - 1]; k++) {
            for (int l = 0; l < shape[shape.length - 2]; l++) {
                coords[shape.length - 1] = k;
                coords[shape.length - 2] = l;
                resp[(int) (shape[shape.length - 2] * k + l)] = entryAt(coords);
            }
        }
        return resp;
    }
}
