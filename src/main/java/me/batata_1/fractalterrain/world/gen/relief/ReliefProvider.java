package me.batata_1.fractalterrain.world.gen.relief;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

import me.batata_1.fractalterrain.ml.Models;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.storage.Tile;
import me.batata_1.fractalterrain.util.Debug;
import me.batata_1.fractalterrain.world.ContinentalScaleMapProvider;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReliefProvider {

    private final EntryStorage final_tiles;

    public ReliefProvider(String pathSave) {
        final_tiles = new EntryStorage(pathSave,"fractal_terrain",512,xz->{

        });
    }

    public float getElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 0);
    }

    public float getBlurredElev(Pair<Integer, Integer> xz) {
        return getValue(xz, 1);
    }

    public float getGradX(Pair<Integer, Integer> xz) {
        return getValue(xz, 2);
    }

    public float getGradY(Pair<Integer, Integer> xz) {
        return getValue(xz, 3);
    }

    public float getRefinedGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 4);
    }

    public float getBlurredGrad(Pair<Integer, Integer> xz) {
        return getValue(xz, 5);
    }

    public float getRes(Pair<Integer, Integer> xz) {
        return getValue(xz, 6);
    }

    private float getValue(Pair<Integer, Integer> xz, int ch) {
        try {
            return final_tiles.getValue(xz, ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public EntryStorage getStorage() {
        return final_tiles;
    }
    // global coords -> correct to get coarse value
    private Pair<Integer, Integer> convertCoarse(Pair<Integer, Integer> xz) {
        return xz;
    }

    private float getCoarseValue(Pair<Integer, Integer> xz, int ch) {
        try {
            return final_raw_tiles.getValue(convertCoarse(xz), ch);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public float getContinentalElev(Pair<Integer, Integer> xz) {
        return getElev(xz);
    }

    public float getRawGrad(Pair<Integer, Integer> xz) {
        return (float) Math.tanh(getBlurredGrad(xz) / 1000.0);
    }

    public float getRawTemp(Pair<Integer, Integer> xz) {
        final double val = ContinentalScaleMapProvider.sampleTemperature(
                convertCoarse(xz).getFirst(), convertCoarse(xz).getSecond());
        return (float) val;
    }

    public float getRawTempSTD(Pair<Integer, Integer> xz) {
        return getCoarseValue(xz, 3);
    }

    public float getRawPrecip(Pair<Integer, Integer> xz) {
        return (float) ContinentalScaleMapProvider.samplePrecipitation(
                convertCoarse(xz).getFirst(), convertCoarse(xz).getSecond());
    }

    public float getRawPrecipSTD(Pair<Integer, Integer> xz) {
        return getCoarseValue(xz, 5);
    }

    public OnnxTensor getTilesAsTensor(int i, int j) {
        try {
            return final_tiles.getEntry(Pair.of(i, j)).get().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
