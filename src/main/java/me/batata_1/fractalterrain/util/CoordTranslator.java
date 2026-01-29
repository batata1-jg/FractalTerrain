package me.batata_1.fractalterrain.util;

import com.mojang.datafixers.util.Pair;

import static me.batata_1.fractalterrain.util.FractalTerrainUtil.*;


public record CoordTranslator() {

    public static Pair<Integer,Integer> toTileCoords(Pair<Integer,Integer> xz) {
        return toGeneralCoords(xz, REGION_LENGTH);
    }

    public static Pair<Integer,Integer> toCurTile(Pair<Integer,Integer> xz) {
        return toCurGeneral(xz, REGION_LENGTH);
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
                XZ.getFirst()* REGION_LENGTH + x,
                XZ.getSecond()* REGION_LENGTH + z
        );
    }

    // i,j,k entryAt coords
    public static int flattenedCoords(int i , int j , int k , long[] shape) {
        return (int) (shape[0]*shape[1]*i + shape[0]*j + k);
    }

    public static Pair<Integer,Integer> toInter(Pair<Integer,Integer> xz, int entry_size) {
        return  Pair.of(
                Math.ceilDiv(xz.getFirst(),entry_size),
                Math.floorDiv(xz.getSecond(),entry_size)
        );
    }

    public static Pair<Integer,Integer> toIntra(Pair<Integer,Integer> xz, int entry_size) {
        int x, z;
        x = xz.getFirst() % entry_size;
        z = xz.getSecond() % entry_size;
        x = entry_size - x - 1;
        assert(0<=x&&x<entry_size&&0<=z&&z<entry_size);
        return Pair.of(x,z);
    }

    // intra e inter
    public static Pair<Integer,Integer> toEntry(Pair<Integer,Integer> jk , Pair<Integer,Integer> xz, int entry_size) {
        int x , z;
        x = xz.getFirst()*entry_size - jk.getFirst();
        z = xz.getSecond()*entry_size + jk.getSecond();
        return Pair.of(x,z);
    }

}
