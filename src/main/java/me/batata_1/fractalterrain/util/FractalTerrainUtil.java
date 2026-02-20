package me.batata_1.fractalterrain.util;

import ai.onnxruntime.*;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.references.Reference.ModID;

public class FractalTerrainUtil {

    public static final Executor EXECUTOR = Executors.newFixedThreadPool((Runtime.getRuntime().availableProcessors()>>2) == 0 ? 1 :( Runtime.getRuntime().availableProcessors()>>2));
    public static final float MAX_ML_HEIGHT = 1765.0F;
    public static final float INTERPOLATION_SCALE = 5.0F;
    public static final int REGION_LENGTH = (int) INTERPOLATION_SCALE* 512;

    public static final int[] dx = {0,0,-1,-1};
    public static final int[] dz = {0,1,0,1};



    private static String NorP(int x) {
        if(x<0) return "N";
        return "P";
    }

    public static String giveNameToTile(Pair<Integer,Integer> xz ) {
        return    NorP(xz.getFirst()) + Math.abs(xz.getFirst()) + "-"
                + NorP(xz.getSecond())+ Math.abs(xz.getSecond());
    }

    public static Pair<Integer,Integer> interpretTileName(String s) {
        int m = s.indexOf('-');
        if( m == -1) {
            LOGGER.error("invalid in tiles dir (no '-')");
            return null;
        }
        int x = Integer.parseInt(s.substring(1,m));
        if(s.charAt(0)=='N') x = -x;
        if(s.length()-4<0 || m+2==s.length() ) {
            LOGGER.error("invalid int tiles dir (wrong format): {} , s.len-4 = {} , m+1 = {}" , s ,
                    s.length()-4 , m+2 );
            return null;
        }
        int z = Integer.parseInt(s.substring(m+2 , s.length()-4));
        if(s.charAt(m+1)=='N') z = -z;
        return new Pair<>(x,z);
    }


}
