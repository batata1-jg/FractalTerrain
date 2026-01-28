package me.batata_1.fractalterrain.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public class TileRegion extends Tile{

    private static final MapCodec<TileRegion> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.listOf().listOf().fieldOf("entries").forGetter((TileRegion o) -> o.ENTRIES),
                    Codec.BYTE.fieldOf("intersecNum").forGetter((TileRegion o) -> o.intersectionNumber)
            ).apply( instance, TileRegion::new));

    public static MapCodec<TileRegion> getRegionCodec() {
        return CODEC;
    }

    private byte intersectionNumber;

    public TileRegion(List<List<Float>> h, byte num) {
        super(h);
        intersectionNumber = num;
    }

}
