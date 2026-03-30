package me.batata_1.fractalterrain.world.gen.densityfunction;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.atomic.AtomicBoolean;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import me.batata_1.fractalterrain.math.Interpolation;
import me.batata_1.fractalterrain.references.Reference;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

public final class FractalTerrainDensityFunctionTypes {

    public static void register() {
        LOGGER.info("registered densities");
        var registry = Registries.DENSITY_FUNCTION_TYPE;

        Registry.register(registry, Reference.identifier("refined_elevation"), RefinedElevation.CODEC);
        Registry.register(registry, Reference.identifier("preliminary_surface"), PreliminarySurface.CODEC);
        Registry.register(registry, Reference.identifier("continents"), RefinedContinents.CODEC);
        Registry.register(registry, Reference.identifier("temperature"), RawTemp.CODEC);
        Registry.register(registry, Reference.identifier("humidity"), RawPrecip.CODEC);
        Registry.register(registry, Reference.identifier("raw_erosion_grad"), RawErosionGrad.CODEC);
        Registry.register(
                registry, Reference.identifier("erosion_refined_blurred_grad"), RefinedErosionBlurredGrad.CODEC);
        Registry.register(registry, Reference.identifier("depth"), Depth.CODEC);
    }

    public static float correctScale(float scale) {
        return scale * 256;
    }

    public abstract static class InterpolatedFromMap implements DensityFunction {

        protected final Interpolation interp;
        protected static AtomicBoolean active = new AtomicBoolean(false);
        protected static AtomicBoolean seedActive = new AtomicBoolean(false);
        protected static long seed;

        public static synchronized boolean isActive() {
            return active.get();
        }

        protected synchronized void setSeed() {
            seed = FractalTerrainInstance.getServer()
                    .getSaveProperties()
                    .getGeneratorOptions()
                    .getSeed();
            seedActive.set(true);
        }

        public InterpolatedFromMap(Interpolation i) {
            interp = i;
            active.set(true);
        }

        @Override
        public double minValue() {
            return 1.5;
        }

        @Override
        public double maxValue() {
            return -1.5;
        }

        @Override
        public double sample(NoisePos pos) {
            return interp.interpolate(pos.blockX(), pos.blockZ());
        }

        @Override
        public void fill(double[] densities, DensityFunction.EachApplier applier) {
            applier.fill(densities, this);
        }

        public void applySeed() {
            if (!seedActive.get()) setSeed();
        }
    }

    public static class RefinedElevation extends InterpolatedFromMap {

        private final int MAX_VAL;
        private final int MIN_VAL;
        private final float scale;

        public static final Codec<RefinedElevation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("alpha", 0F).forGetter(null),
                        Codec.FLOAT.optionalFieldOf("beta", 0.5F).forGetter(null),
                        Codec.FLOAT.optionalFieldOf("gamma", 5F).forGetter(null),
                        Codec.FLOAT.optionalFieldOf("grad_blur", 0.1F).forGetter(null),
                        Codec.FLOAT.optionalFieldOf("tau", 2.0F).forGetter(null),
                        Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null),
                        Codec.INT.fieldOf("minVal").forGetter(g -> g.MIN_VAL),
                        Codec.INT.fieldOf("maxVal").forGetter(g -> g.MAX_VAL))
                .apply(instance, RefinedElevation::new));

        public record Settings(float alpha, float beta, float gamma, float grad_blur, float tau) {}

        public static Settings setting;

        public static final CodecHolder<RefinedElevation> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RefinedElevation(
                float alpha, float beta, float gamma, float gradBlur, float tau, float scale, int minVal, int maxVal) {
            super(new Interpolation(scale));
            this.scale = scale;
            setting = new Settings(alpha, beta, gamma, gradBlur, tau);
            MAX_VAL = maxVal;
            MIN_VAL = minVal;
        }

        @Override
        public double sample(NoisePos pos) {
            return interp.interpolate(pos.blockX(), pos.blockZ()) * scale - pos.blockY() + 62;
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            interp.setF(xz -> FractalTerrainInstance.post.getElev(xz));
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

    public static class PreliminarySurface extends InterpolatedFromMap {

        private final float scale;

        public static final Codec<PreliminarySurface> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null))
                .apply(instance, PreliminarySurface::new));

        public static final CodecHolder<PreliminarySurface> CODEC_HOLDER = CodecHolder.of(CODEC);

        public PreliminarySurface(float scale) {
            super(new Interpolation(scale));
            this.scale = scale;
        }

        public double sample(NoisePos pos) {
            return interp.interpolate(pos.blockX(), pos.blockZ()) - pos.blockY() + 53;
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            interp.setF(xz -> FractalTerrainInstance.post.getElev(xz) * scale);
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class Depth extends InterpolatedFromMap {

        private final float scale;

        public static final Codec<Depth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null))
                .apply(instance, Depth::new));

        public static final CodecHolder<Depth> CODEC_HOLDER = CodecHolder.of(CODEC);

        public Depth(float scale) {
            super(new Interpolation(scale));
            this.scale = scale;
        }

        public double sample(NoisePos pos) {
            return (interp.interpolate(pos.blockX(), pos.blockZ()) - pos.blockY() + 62) / (128.0);
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            interp.setF(xz -> FractalTerrainInstance.post.getElev(xz) * scale);
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RefinedContinents extends InterpolatedFromMap {

        private final double normFactor;

        public static final Codec<RefinedContinents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null),
                        Codec.DOUBLE.optionalFieldOf("norm_factor", 10.0).forGetter(o -> o.normFactor))
                .apply(instance, RefinedContinents::new));

        public static final CodecHolder<RefinedContinents> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RefinedContinents(float scale, double normFactor) {
            super(new Interpolation(scale));
            this.normFactor = normFactor;
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            super.applySeed();
            interp.setF(
                    xz -> (float) (Math.tanh(FractalTerrainInstance.post.getContinentalElev(xz) / normFactor) * 1.5F));
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RawTemp extends InterpolatedFromMap {

        private final double normFactor;

        public static final Codec<RawTemp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.fieldOf("scale").forGetter(null),
                        Codec.DOUBLE.optionalFieldOf("norm_factor", 10.0).forGetter(o -> o.normFactor))
                .apply(instance, RawTemp::new));

        public static final CodecHolder<RawTemp> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RawTemp(float scale, double normFactor) {
            super(new Interpolation(correctScale(scale)));
            this.normFactor = normFactor;
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            super.applySeed();
            interp.setF(xz -> (float) (Math.tanh(FractalTerrainInstance.post.getRawTemp(xz) / normFactor) * 2.31));
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RawTempSTD extends InterpolatedFromMap {

        private static final float normFactor = 1F;

        public static final Codec<RawTempSTD> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(Codec.FLOAT.fieldOf("scale").forGetter(null)).apply(instance, RawTempSTD::new));

        public static final CodecHolder<RawTempSTD> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RawTempSTD(float scale) {
            super(new Interpolation(scale));
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            super.applySeed();
            interp.setF(xz -> FractalTerrainInstance.post.getRawTempSTD(xz) * normFactor);
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RawPrecip extends InterpolatedFromMap {

        private final double normFactor;

        public static final Codec<RawPrecip> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.fieldOf("scale").forGetter(null),
                        Codec.DOUBLE.optionalFieldOf("norm_factor", 10.0).forGetter(o -> o.normFactor))
                .apply(instance, RawPrecip::new));

        public static final CodecHolder<RawPrecip> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RawPrecip(float scale, double normFactor) {
            super(new Interpolation(correctScale(scale)));
            this.normFactor = normFactor;
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            super.applySeed();
            interp.setF(xz -> (float) (Math.tanh(FractalTerrainInstance.post.getRawPrecip(xz) / normFactor) * 1.76));
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RawPrecipSTD extends InterpolatedFromMap {

        private static final float normFactor = 1F;

        public static final Codec<RawPrecipSTD> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(Codec.FLOAT.fieldOf("scale").forGetter(null)).apply(instance, RawPrecipSTD::new));

        public static final CodecHolder<RawPrecipSTD> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RawPrecipSTD(float scale) {
            super(new Interpolation(scale));
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            super.applySeed();
            interp.setF(xz -> FractalTerrainInstance.post.getRawPrecipSTD(xz) * normFactor);
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RawErosionGrad extends InterpolatedFromMap {

        private static final float normFactor = 2.52F;

        public static final Codec<RawErosionGrad> CODEC = RecordCodecBuilder.create(erosionInstance -> erosionInstance
                .group(Codec.FLOAT.optionalFieldOf("scale", 64F).forGetter(null))
                .apply(erosionInstance, RawErosionGrad::new));

        public static final CodecHolder<RawErosionGrad> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RawErosionGrad(float scale) {
            super(new Interpolation(scale));
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {

            interp.setF(xz -> {
                return (float) (Math.tanh(1 - FractalTerrainInstance.post.getRawGrad(xz)) * normFactor);
            });
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RefinedErosionBlurredGrad extends InterpolatedFromMap {

        private final double normFactor;

        public static final Codec<RefinedErosionBlurredGrad> CODEC =
                RecordCodecBuilder.create(erosionInstance -> erosionInstance
                        .group(
                                Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null),
                                Codec.DOUBLE
                                        .optionalFieldOf("norm_factor", 10.0)
                                        .forGetter(o -> o.normFactor))
                        .apply(erosionInstance, RefinedErosionBlurredGrad::new));

        public static final CodecHolder<RefinedErosionBlurredGrad> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RefinedErosionBlurredGrad(float scale, double normFactor) {
            super(new Interpolation(scale));
            this.normFactor = normFactor;
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            interp.setF(
                    xz -> (float) (Math.tanh(1 - FractalTerrainInstance.post.getBlurredGrad(xz) / normFactor) * 2.52));
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }

    public static class RefinedErosionGrad extends InterpolatedFromMap {

        private static final float normFactor = 2.52F;

        public static final Codec<RefinedErosionGrad> CODEC =
                RecordCodecBuilder.create(erosionInstance -> erosionInstance
                        .group(Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null))
                        .apply(erosionInstance, RefinedErosionGrad::new));

        public static final CodecHolder<RefinedErosionGrad> CODEC_HOLDER = CodecHolder.of(CODEC);

        public RefinedErosionGrad(float scale) {
            super(new Interpolation(scale));
        }

        @Override
        public DensityFunction apply(DensityFunctionVisitor visitor) {
            interp.setF(xz -> {
                return (float) (Math.tanh(1 - FractalTerrainInstance.post.getRefinedGrad(xz) / 10.0) * normFactor);
            });
            return visitor.apply(this);
        }

        @Override
        public CodecHolder<? extends DensityFunction> getCodecHolder() {
            return CODEC_HOLDER;
        }
    }
}
