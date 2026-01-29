package me.batata_1.fractalterrain.storage;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public class Tile {

    private static final MapCodec<Tile> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.listOf().listOf().fieldOf("entries").forGetter((Tile o) -> o.ENTRIES)
            ).apply(instance, Tile::new )
    );

    protected final int TILE_SIZE;
    protected final List<List<Float>> ENTRIES;

    public static MapCodec<Tile> getCodec() {
        return CODEC;
    }

    public Tile( List<List<Float>> h ) {
        ENTRIES = h;
        TILE_SIZE = h.size();
    }

    public Float entry(Pair<Integer,Integer> xz) {
        return ENTRIES.get(TILE_SIZE -xz.getFirst() -1).get(xz.getSecond());
    }

}
