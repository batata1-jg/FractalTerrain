package me.batata_1.fractalterrain.storage;

import ai.onnxruntime.OnnxTensor;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.*;
import java.util.List;

public class TileRegion extends Tile{


    public static void serialize(String path, TileRegion t) throws IOException {
        int el = t.entries.length;
        int sl = t.shape.length;
        float[] arr = new float[el+sl+2];
        System.arraycopy(t.entries, 0, arr, 0, el);
        for( int i=el ; i<(el+sl) ; i++)
            arr[i] = (float) t.shape[i-el];
        arr[el+sl] = (float) el;
        arr[el+sl+1] = (float) t.regionState;
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path + ".ser"));
        out.writeObject(arr);
    }

    public static <T extends Tile> T deserialize(String path) throws IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream( new FileInputStream(path + ".ser"));
        float[] arr = (float[]) in.readObject();
        int slAddEl = arr.length -2;
        int el = (int) arr[slAddEl];
        int sl = slAddEl - el;
        float[] entries = new float[el];
        System.arraycopy(arr, 0, entries, 0, el);
        long[] shape = new long[sl];
        for(int i=el ; i<el+sl ; i++) shape[i-el] = (long) arr[i];
        return (T) new TileRegion(entries,shape,(int) arr[arr.length-1] );
    }

    private final int regionState;

    public TileRegion(float[] en, long[] sh,int state ) {
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

    public void addToCur(TileRegion t) {

    }

}
