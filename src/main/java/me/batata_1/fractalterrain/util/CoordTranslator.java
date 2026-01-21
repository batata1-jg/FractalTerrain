package me.batata_1.fractalterrain.util;

import com.mojang.datafixers.util.Pair;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;


public record CoordTranslator() {

    public static Pair<Integer,Integer> toTileCoords(Pair<Integer,Integer> xz) {
        return toGeneralCoords(xz,REGION_LENTGH);
    }

    public static Pair<Integer,Integer> toCurTile(Pair<Integer,Integer> xz) {
        return toCurGeneral(xz,REGION_LENTGH);
    }

    public static Pair<Integer,Integer> toCurEntry(Pair<Integer,Integer> xz ) {
        return toCurGeneral(xz, (int) INTERPOLATION_SCALE);
    }

    public static Pair<Integer,Integer> backToNormalCoords( Pair<Integer,Integer> xz , Pair<Integer,Integer> XZ) {
        int x = xz.getFirst();
        int z = xz.getSecond();
        x *= (int) INTERPOLATION_SCALE;
        z *= (int) INTERPOLATION_SCALE;
        return Pair.of(
                XZ.getFirst()*REGION_LENTGH + x,
                XZ.getSecond()*REGION_LENTGH + z
        );
    }

}
