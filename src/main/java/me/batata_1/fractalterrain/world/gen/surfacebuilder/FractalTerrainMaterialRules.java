package me.batata_1.fractalterrain.world.gen.surfacebuilder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import me.batata_1.fractalterrain.math.Interpolation;
import me.batata_1.fractalterrain.references.Reference;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

import java.util.concurrent.ExecutionException;

public class FractalTerrainMaterialRules extends MaterialRules {

    static <A> void register(
            Registry<Codec<? extends A>> registry, Identifier id, CodecHolder<? extends A> codecHolder) {
        Registry.register(registry, id, codecHolder.codec());
    }

    public interface FractalTerrainMaterialRule extends MaterialRule {

        static void register(Registry<Codec<? extends MaterialRule>> registry) {
            FractalTerrainMaterialRules.register(
                    registry, Reference.identifier("fractal_terrain_default"), FractalTerrainMaterialDefaultRule.CODEC);
            FractalTerrainMaterialRules.register(
                    registry, Reference.identifier("fractal_terrain_large"), FractalTerrainMaterialLargeRule.CODEC);
        }

        CodecHolder<? extends MaterialRules.MaterialRule> codec();
    }

    enum FractalTerrainMaterialDefaultRule implements FractalTerrainMaterialRules.FractalTerrainMaterialRule {
        INSTANCE;
        final MaterialRules.MaterialRule RULE = FractalTerrainSurfaceRules.createFractalTerrainOverworldSurfaceRule();

        static final CodecHolder<FractalTerrainMaterialRules.FractalTerrainMaterialDefaultRule> CODEC =
                CodecHolder.of(MapCodec.unit(INSTANCE));

        @Override
        public CodecHolder<? extends MaterialRule> codec() {
            return CODEC;
        }

        @Override
        public BlockStateRule apply(MaterialRuleContext materialRuleContext) {
            return RULE.apply(materialRuleContext);
        }
    }

    enum FractalTerrainMaterialLargeRule implements FractalTerrainMaterialRules.FractalTerrainMaterialRule {
        INSTANCE;
        final MaterialRules.MaterialRule RULE =
                FractalTerrainSurfaceRules.createFractalTerrainOverworldLargeSurfaceRule();

        static final CodecHolder<FractalTerrainMaterialRules.FractalTerrainMaterialLargeRule> CODEC =
                CodecHolder.of(MapCodec.unit(INSTANCE));

        @Override
        public CodecHolder<? extends MaterialRule> codec() {
            return CODEC;
        }

        @Override
        public BlockStateRule apply(MaterialRuleContext materialRuleContext) {
            return RULE.apply(materialRuleContext);
        }
    }

    public interface FractalTerrainMaterialCondition extends MaterialCondition {

        static void register(Registry<Codec<? extends MaterialCondition>> registry) {
            FractalTerrainMaterialRules.register(registry, Reference.identifier("steep_slope"), SteepSlope.CODEC);
        }

        CodecHolder<? extends MaterialRules.MaterialCondition> codec();
    }

    record SteepSlope(float minConsideredSteep, float scale) implements FractalTerrainMaterialCondition {

        static final CodecHolder<FractalTerrainMaterialRules.SteepSlope> CODEC =
                CodecHolder.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Codec.FLOAT
                                        .optionalFieldOf("min_considered_steep", 4.0F)
                                        .forGetter(SteepSlope::minConsideredSteep),
                                Codec.FLOAT.optionalFieldOf("scale", 0.2F).forGetter(null))
                        .apply(instance, SteepSlope::new)));

        @Override
        public CodecHolder<? extends MaterialRules.MaterialCondition> codec() {
            return CODEC;
        }

        @Override
        public BooleanSupplier apply(MaterialRuleContext materialRuleContext) {
            final Interpolation i = new Interpolation(scale);
            i.setF(xz -> {
                try {
                    return FractalTerrainInstance.reliefSource.get().getRefinedGrad(xz);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

            class aboveSlopePredicate extends MaterialRules.FullLazyAbstractPredicate {

                protected aboveSlopePredicate() {
                    super(materialRuleContext);
                }

                @Override
                protected boolean test() {
                    return i.interpolateBilinear(this.context.blockX, this.context.blockZ)
                            >= SteepSlope.this.minConsideredSteep;
                }
            }

            return new aboveSlopePredicate();
        }
    }
}
