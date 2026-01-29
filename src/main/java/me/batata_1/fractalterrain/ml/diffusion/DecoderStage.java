package me.batata_1.fractalterrain.ml.diffusion;

import com.mojang.datafixers.util.Pair;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.TileRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DecoderStage {

    private static final ArrayList<EntryStorage<TileRegion>> decoderTiles = new ArrayList<>();

    public static void bootstrap() {
        for( int i=0 ; i<2 ; i++) {
            decoderTiles.add( new EntryStorage<>("decoder/" + i, 256, TileRegion.getRegionCodec()));
            decoderTiles.get(i).bootstrap();
        }
    }

    public static TileRegion[][] run(
            Pair<Integer,Integer> xz,
            TileRegion[][] latents,
            int seed
    ) {
        List<List<Float>> h = new ArrayList<>();
        for(int i=0 ; i<256 ; i++) {
            h.add(new ArrayList<>());
            for(int j=0 ; j<256 ; j++) {
                h.get(i).add(0.0f);
            }
        }
        var t = new TileRegion(h, (byte) 1);
        var o = new TileRegion[2];
        Arrays.fill(o, t);
        var v = new TileRegion[4][2];
        Arrays.fill(v,o);
        return v;
    }
}
