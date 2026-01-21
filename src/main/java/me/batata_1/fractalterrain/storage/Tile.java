package me.batata_1.fractalterrain.storage;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.TILE_LENGTH;


public class Tile {

    public static final MapCodec<Tile> CODEC = RecordCodecBuilder.mapCodec( instance ->
            instance.group(
                    Codec.SHORT.listOf().listOf().fieldOf("HEIGHTS").forGetter((Tile o) -> o.HEIGHTS)
            ).apply(instance, Tile::new )
    );

    private final List<List<Short>> HEIGHTS;

    public Tile( List<List<Short>> h ) {
        HEIGHTS = h;
    }

    public int entry(Pair<Integer,Integer> xz) {
        return HEIGHTS.get(TILE_LENGTH -xz.getFirst() -1).get(xz.getSecond());
    }

}
