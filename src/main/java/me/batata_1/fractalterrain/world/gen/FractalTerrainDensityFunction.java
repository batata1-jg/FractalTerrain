package me.batata_1.fractalterrain.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.concurrent.ExecutionException;

import static me.batata_1.fractalterrain.world.HeightProvider.getElevation;

public class FractalTerrainDensityFunction implements DensityFunction {

    public static final Codec<FractalTerrainDensityFunction> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("minVal").forGetter(g -> g.MIN_VAL),
                    Codec.INT.fieldOf("maxVal").forGetter(g -> g.MAX_VAL)
            ).apply(instance, FractalTerrainDensityFunction::new)
    );

    public static final CodecHolder<FractalTerrainDensityFunction> CODEC_HOLDER = CodecHolder.of(CODEC);

    private final int MAX_VAL;
    private final int MIN_VAL;

    public FractalTerrainDensityFunction(int maxVal, int minVal) {
        MAX_VAL = maxVal;
        MIN_VAL = minVal;
    }

    @Override
    public double sample(NoisePos pos) {
        int x = pos.blockX();
        int y = pos.blockY();
        int z = pos.blockZ();
        return getElevation(x,z) - y;
    }

    @Override
    public void fill(double[] densities, DensityFunction.EachApplier applier) {
        applier.fill(densities, this);
    }

    @Override
    public DensityFunction apply(DensityFunction.DensityFunctionVisitor visitor) {
        return visitor.apply(this);
    }

    @Override
    public double minValue() {
        return MIN_VAL;
    }

    @Override
    public double maxValue() {
        return MAX_VAL;
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        return CODEC_HOLDER;
    }
}