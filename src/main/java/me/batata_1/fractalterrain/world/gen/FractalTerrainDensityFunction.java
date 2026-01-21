package me.batata_1.fractalterrain.world.gen;

import me.batata_1.fractalterrain.storage.Tile;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.world.HeightProvider.getElevation;

public class FractalTerrainDensityFunction implements DensityFunction {

    @Override
    public double sample(NoisePos pos) {
        int x = pos.blockX();
        int y = pos.blockY();
        int z = pos.blockZ();
        try {

            int targetHeight = getElevation(x,z);

            return targetHeight - y;

        } catch (InterruptedException | ExecutionException e) {
            return -y;
        }
    }

    @Override
    public void fill(double[] densities, EachApplier applier) {

    }

    @Override
    public DensityFunction apply(DensityFunctionVisitor visitor) {
        return null;
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 0;
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        return null;
    }
}
