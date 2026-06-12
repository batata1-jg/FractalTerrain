package me.batata_1.fractal_terrain.mixin;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.world.features.VegetationController;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

import static me.batata_1.fractal_terrain.debug.Debug.DEBUG_LOGGER;
import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {

    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "placeWithContext", at = @At("HEAD"), cancellable = true)
    private void VegetationPlacementAlter(PlacementContext placementContext, RandomSource randomSource, BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        if(FractalTerrainInstance.exists()) {
            ConfiguredFeature<?, ?> configuredFeature = ((PlacedFeature) (Object) this).feature().value();
            if(VegetationController.checkIfLargeVegetation(configuredFeature.config())) {
                Stream<BlockPos> stream = Stream.of(blockPos);
                for (PlacementModifier placementModifier : ((PlacedFeature) (Object) this).placement()) {
                    stream = stream.flatMap((blockPosx) -> placementModifier.getPositions(placementContext, randomSource, blockPosx));
                }
                //TODO:check if nextfloat returns between 0 and 1
                if(VegetationController.densityAt(blockPos.getX(),blockPos.getZ()) < randomSource.nextFloat()) stream = Stream.of();
                MutableBoolean mutableBoolean = new MutableBoolean();
                stream.forEach((blockPosx) -> {
                    if (configuredFeature.place(placementContext.getLevel(), placementContext.generator(), randomSource, blockPosx)) {
                        mutableBoolean.setTrue();
                    }

                });

                cir.setReturnValue(mutableBoolean.isTrue());
            }
        }
    }

}
