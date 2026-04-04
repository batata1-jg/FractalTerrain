package me.batata_1.fractalterrain.storage;

import ai.onnxruntime.OnnxTensor;
import java.io.*;
import java.util.Random;

public class TileRegion extends Tile {

    private int regionState;

    public void serialize(String path) throws IOException {
        final int el = this.entries.length;
        final int sl = this.shape.length;
        final float[] arr = new float[el + sl + 2];
        System.arraycopy(this.entries, 0, arr, 0, el);
        for (int i = el; i < (el + sl); i++) arr[i] = (float) this.shape[i - el];
        arr[el + sl] = (float) el;
        arr[el + sl + 1] = (float) this.regionState;
        final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path + ".ser"));
        out.writeObject(arr);
    }

    public void deserialize(String path) throws IOException, ClassNotFoundException {
        final ObjectInputStream in = new ObjectInputStream(new FileInputStream(path + ".ser"));
        final float[] arr = (float[]) in.readObject();
        final int slAddEl = arr.length - 2;
        final int el = (int) arr[slAddEl];
        final int sl = slAddEl - el;
        final float[] entries = new float[el];
        System.arraycopy(arr, 0, entries, 0, el);
        final long[] shape = new long[sl];
        for (int i = el; i < el + sl; i++) shape[i - el] = (long) arr[i];
        this.entries = entries;
        this.shape = shape;
        this.cProd = calcProd();
        this.regionState = (int) arr[arr.length - 1];
    }

    public TileRegion() {}

    public TileRegion(float[] en, long[] sh, int state) {
        super(en, sh);
        regionState = state;
    }

    public TileRegion(OnnxTensor h, int state) {
        super(h);
        regionState = state;
    }

    public int getState() {
        return regionState;
    }

    public boolean isComplete() {
        return regionState == 15;
    }

    public void add(TileRegion t) {
        super.add(t);
        this.regionState += t.regionState;
    }
}
