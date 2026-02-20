package me.batata_1.fractalterrain.math;

import com.mojang.datafixers.util.Pair;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;


public class CoordTranslator {

    public static Pair<Integer,Integer> toInter(Pair<Integer,Integer> xz, int entry_size) {
        return  Pair.of(
                Math.floorDiv(xz.getFirst(),entry_size),
                Math.floorDiv(xz.getSecond(),entry_size)
        );
    }

    public static Pair<Integer,Integer> toIntra(Pair<Integer,Integer> xz, int entry_size) {
        int x, z;
        x = xz.getFirst() % entry_size;
        z = xz.getSecond() % entry_size;

        if(x<0) x = (x + entry_size) % entry_size;
        if(z<0) z = (z + entry_size) % entry_size;

        if(x<0 || entry_size<=x || z<0 || entry_size<=z ) {
            LOGGER.error(" x z intra {} {}",x,z);
            throw new RuntimeException("out of bounds");
        }

        return Pair.of(x,z);
    }

    // intra e inter
    public static Pair<Integer,Integer> toEntry(Pair<Integer,Integer> jk , Pair<Integer,Integer> xz, int entry_size) {
        int x , z;
        x = xz.getFirst()*entry_size + jk.getFirst();
        z = xz.getSecond()*entry_size + jk.getSecond();
        return Pair.of(x,z);
    }

}
