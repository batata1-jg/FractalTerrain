package me.batata_1.fractal_terrain.mixin;

import java.util.stream.Stream;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.world.features.VegetationController;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {

    //@SuppressWarnings("DataFlowIssue")
    @Inject(method = "placeWithContext", at = @At("HEAD"), cancellable = true)
    private void VegetationPlacementAlter(
            PlacementContext placementContext,
            RandomSource randomSource,
            BlockPos blockPos,
            CallbackInfoReturnable<Boolean> cir) {
        if (FractalTerrainInstance.exists()) {
            ConfiguredFeature<?, ?> configuredFeature =
                    ((PlacedFeature) (Object) this).feature().value();
            if (VegetationController.checkIfLargeVegetation(configuredFeature.config())) {
                Stream<BlockPos> stream = Stream.of(blockPos);
                for (PlacementModifier placementModifier : ((PlacedFeature) (Object) this).placement()) {
                    stream = stream.flatMap(
                            (blockPosx) -> placementModifier.getPositions(placementContext, randomSource, blockPosx));
                }
                if (VegetationController.densityAt(blockPos.getX(), blockPos.getZ()) < randomSource.nextFloat())
                    stream = Stream.of();
                MutableBoolean mutableBoolean = new MutableBoolean();
                stream.forEach((blockPosx) -> {
                    if (configuredFeature.place(
                            placementContext.getLevel(), placementContext.generator(), randomSource, blockPosx)) {
                        mutableBoolean.setTrue();
                    }
                });

                cir.setReturnValue(mutableBoolean.isTrue());
            }
        }
    }
}
