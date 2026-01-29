package me.batata_1.fractalterrain.ml.diffusion;

import ai.onnxruntime.OnnxTensor;
import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;

public abstract class StorageInterface {
    
    protected final EntryStorage<TileRegion> storage;
    protected final int size;
    protected final long[] shape;

    protected StorageInterface(EntryStorage<TileRegion> storage, int size, long[] shape) {
        this.storage = storage;
        this.size = size;
        this.shape = shape;
        this.storage.bootstrap();
    }

    abstract OnnxTensor[] runInference(int x,int z);

    synchronized void addToStorage(int x , int z , OnnxTensor[] batch) {
        for(int i=0 ; i<4 ; i++) {
            var coords = Pair.of(x+dx[i],z+dz[i]);
            final int I = i;
            CompletableFuture<TileRegion> ct = CompletableFuture.supplyAsync( () -> {
                TileRegion tile = new TileRegion(batch[I],(1<<I));
                if(storage.existsEntry(coords)) {
                    try {
                        tile.addToCur(storage.getEntry(coords).get());
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
                return tile;
            },EXECUTOR);
        }
    }

    public OnnxTensor getTile(Pair<Integer,Integer> xz) throws ExecutionException, InterruptedException {
        if(storage.existsEntry(xz)) {
            TileRegion t = storage.getEntry(xz).get();
            if( t.isComplete() ) return t.get();
        }
        return completeTiles(xz.getFirst(),xz.getSecond());
    }

    public synchronized OnnxTensor completeTiles(int x,int z) throws ExecutionException, InterruptedException {
        // assuminfo default float array = 0
        TileRegion t;
        if(storage.existsEntry(Pair.of(x,z))) {
            t = storage.getEntry(Pair.of(x,z)).get();
            if( t.isComplete() ) return t.get();
        } else {
            t = new TileRegion(new float[size],shape,0);
        }

        for(int i=0 ; i<4 ; i++) if((t.getState() & (1<<i)) == 0 ) {
            var batch = runInference(x+interX[i],z+interZ[i]);
            addToStorage(x+interX[i],z+interZ[i],batch);
        }

        return storage.getEntry(Pair.of(x,z)).get().get();
    }


}
