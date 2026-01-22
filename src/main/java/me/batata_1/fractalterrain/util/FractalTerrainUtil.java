package me.batata_1.fractalterrain.util;

import ai.onnxruntime.OrtEnvironment;
import com.mojang.datafixers.util.Pair;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

public record FractalTerrainUtil() {


    public static OrtEnvironment ENV = null;
    public static final int TILE_LENGTH = 128;
    public static final float MAX_ML_HEIGHT = 1765.0F;
    public static final float INTERPOLATION_SCALE = 20.0F;
    public static final int REGION_LENTGH = (int) INTERPOLATION_SCALE*TILE_LENGTH;

    private static String NorP(int x) {
        if(x<0) return "N";
        return "P";
    }

    public static String giveNameToTile(Pair<Integer,Integer> xz ) {
        return    NorP(xz.getFirst()) + Math.abs(xz.getFirst()) + "-"
                + NorP(xz.getSecond())+ Math.abs(xz.getSecond())
                + ".json";
    }

    public static Pair<Integer,Integer> interpretTileName(String s) {
        int m = s.indexOf('-');
        if( m == -1) {
            LOGGER.error("invalid json in tiles dir (no '-')");
            return null;
        }
        int x = Integer.parseInt(s.substring(1,m));
        if(s.charAt(0)=='N') x = -x;
        if(s.length()-5<0 || m+2==s.length() ) {
            LOGGER.error("invalid json int tiles dir (wrong format): {} , s.len-5 = {} , m+1 = {}" , s ,
                    s.length()-5 , m+2 );
            return null;
        }
        int z = Integer.parseInt(s.substring(m+2 , s.length()-5));
        if(s.charAt(m+1)=='N') z = -z;
        return new Pair<>(x,z);
    }

    public static Pair<Integer,Integer> toGeneralCoords(Pair<Integer,Integer> xz , int translateFactor) {
        int x, z;
        x = xz.getFirst() % translateFactor;
        z = xz.getSecond() % translateFactor;
        if(x<0) x += translateFactor;
        if(z<0) z += translateFactor;
        return Pair.of(x,z);
    }

    public static Pair<Integer,Integer> toCurGeneral(Pair<Integer,Integer> xz , int translateFactor) {
        return Pair.of(
                Math.floorDiv(xz.getFirst(),translateFactor),
                Math.floorDiv(xz.getSecond(),translateFactor)
        );
    }
}
