package me.batata_1.fractalterrain.storage;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.mojang.datafixers.util.Pair;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.math.CoordTranslator.toInter;
import static me.batata_1.fractalterrain.math.CoordTranslator.toIntra;
import static me.batata_1.fractalterrain.util.DebugTensors.seeTensor;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;
import static me.batata_1.fractalterrain.util.MlUtil.merge;

public abstract class StorageInterface {

    protected final EntryStorage<TileRegion> storage;
    protected final int size;
    protected final long[] shape;

    protected StorageInterface( EntryStorage<TileRegion> storage, int size, long[] shape) {
        this.storage = storage;
        this.size = size;
        this.shape = shape;
    }


    abstract protected OnnxTensor[] runInference(int x,int z) throws OrtException, ExecutionException, InterruptedException, IOException;

    protected EntryStorage<TileRegion> getStorage(int i) {
        return storage;
    }

    public OnnxTensor getTilesAsTensor(int x, int z) throws ExecutionException, OrtException, InterruptedException, IOException {
//        LOGGER.info("getTilesAsTensor: in: {} {}",x,z);
        OnnxTensor[] t = new OnnxTensor[4];
        for(int i=0 ; i<4 ; i++) {
//            LOGGER.info("   calling getTileAsTensorOf:{}",Pair.of(x+dx[i],z+dz[i]));
            t[i] = getTileAsTensor(Pair.of(x+dx[i],z+dz[i]));
//            seeTensor(t[i],"debug subtile " +i+" ",false,0);
        }
        // quando ele merga, ele pode ter rotacionado a tile. Pra checar isso tem q olhar a posicao q as tiles tao no plano e a imagem final
        return merge(t);
    }

    public OnnxTensor getTileAsTensor(Pair<Integer,Integer> xz) throws ExecutionException, InterruptedException, OrtException, IOException {
        return getTile(xz).get();
    }

    //xz global
    public float getValue(Pair<Integer,Integer> xz) throws IOException, ExecutionException, OrtException, InterruptedException {
        TileRegion t = getTile(toInter(xz, (int) shape[shape.length-1]));
        var intra = toIntra(xz, (int) shape[shape.length-1]);
        return t.entryAt(new long[]{ intra.getFirst(),intra.getSecond()});
    }


    public float getValue(Pair<Integer,Integer> xz,int ch) throws IOException, ExecutionException, OrtException, InterruptedException {
        TileRegion t = getTile(toInter(xz, (int) shape[shape.length-1]));
        var intra = toIntra(xz, (int) shape[shape.length-1]);
        return t.entryAt(new long[]{ch, intra.getFirst(),intra.getSecond()});
    }

    public TileRegion getTile(Pair<Integer,Integer> xz) throws ExecutionException, InterruptedException, OrtException, IOException {
        var s = getStorage(0);
        if(s.existsEntry(xz)) {
            TileRegion t = s.getEntry(xz).get();
            if( t.isComplete() ) return t;
        }
        return completeTiles(xz.getFirst(),xz.getSecond(),s);
    }

    private static final int[] dxCerto = {0,0,-1,-1};
    private static final int[] dzCerto = {0,-1,0,-1};
    public synchronized TileRegion completeTiles(int x,int z, EntryStorage<TileRegion> s) throws ExecutionException, InterruptedException, OrtException, IOException {
        // assuminfo default float array = 0

        TileRegion t;
        if(s.existsEntry(Pair.of(x,z))) {
            t = s.getEntry(Pair.of(x,z)).get();
            if( t.isComplete() ) return t;
        } else {
            t = new TileRegion(new float[size],shape,0);
        }
//        LOGGER.info("mas aqui sim ne?");
        for(int i=0 ; i<4 ; i++)  {
//            if((t.getState() & (1<<i)) != 0) LOGGER.info("       it {} tile {} {} ja adicionada",i,x+dxCerto[i],z+dzCerto[i]);
            if((t.getState() & (1<<i)) == 0 ) {
                LOGGER.info("       it {} adicionando tile {} {} to {}",i,x+dxCerto[i],z+dzCerto[i],s.getPath());
                var batch = runInference(x+dxCerto[i],z+dzCerto[i]);
                addToStorage(x+dxCerto[i],z+dzCerto[i],batch,s);
            }

        }
//        LOGGER.info("chega no complete tiles?");
        //TODO: completeTIles not correct
        return s.getEntry(Pair.of(x,z)).get();
    }

    synchronized void addToStorage(int x , int z , OnnxTensor[] batch, EntryStorage<TileRegion> s) {

        for(int i=0 ; i<4 ; i++) {
            var coords = Pair.of(x-dxCerto[i],z-dzCerto[i]);
            TileRegion tile = new TileRegion(batch[i],(1<<i));
            if(s.existsEntry(coords)) {
                try {
                    tile.add(s.getEntry(coords).get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            storage.addOrOverwriteEntry(
                    CompletableFuture.supplyAsync( () -> {
//                        LOGGER.info("completa logo prrr");
                        return tile;
                    },EXECUTOR),
                    coords);
//            LOGGER.info("adding {}",coords);
        }
    }

}
