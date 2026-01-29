package me.batata_1.fractalterrain.storage;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public class TileRegion extends Tile{

    private static final MapCodec<TileRegion> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.listOf().listOf().fieldOf("entries").forGetter((TileRegion o) -> o.ENTRIES),
                    Codec.BYTE.fieldOf("state").forGetter((TileRegion o) -> o.regionState)
            ).apply( instance, TileRegion::new));

    public static MapCodec<TileRegion> getRegionCodec() {
        return CODEC;
    }

    private byte regionState;

    public TileRegion(List<List<Float>> h, byte num) {
        super(h);
        regionState = num;
    }

    public void updateState(byte s) {
        regionState = s;
    }

    public byte getState() {
        return regionState;
    }

    public boolean isComplete() {
        return regionState == (byte) 15;
    }

    // interTile coords
    public Float entry(Pair<Integer,Integer> xz) {
        return ENTRIES.get(xz.getFirst()).get(xz.getSecond());
    }

    public List<List<Float>> getRegion() {
        return ENTRIES;
    }

}
