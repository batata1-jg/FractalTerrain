package me.batata_1.fractalterrain.ml.diffusion;

import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LatentStage {

    private static final ArrayList<EntryStorage<TileRegion>> latentTiles = new ArrayList<>();

    public static void bootstrap() {
        for( int i=0 ; i<6 ; i++) {
            latentTiles.add( new EntryStorage<>("latent/" + i, 32, TileRegion.getRegionCodec()));
            latentTiles.get(i).bootstrap();
        }
    }

    public static TileRegion[][] run(
            Pair<Integer,Integer> xz,
            TileRegion[][] latents,
            float[][][] coarse,
            int seed
    ) {
        //coarse so precisa de sessao de 1
        // vai ter que combinar os 4 latents em um negocio so pra passar para o modelo, talvez isso seja feito dentro do onnx
        List<List<Float>> h = new ArrayList<>();
        for(int i=0 ; i<32 ; i++) {
            h.add(new ArrayList<>());
            for(int j=0 ; j<32 ; j++) {
                h.get(i).add(0.0f);
            }
        }
        var t = new TileRegion(h, (byte) 1);
        var o = new TileRegion[6];
        Arrays.fill(o, t);
        var v = new TileRegion[4][6];
        Arrays.fill(v,o);
        return v;
    }
}
