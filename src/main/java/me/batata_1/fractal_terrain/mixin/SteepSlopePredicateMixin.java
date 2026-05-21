package me.batata_1.fractal_terrain.mixin;

import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MaterialRules.MaterialRuleContext.SteepSlopePredicate.class)
public abstract class SteepSlopePredicateMixin {

    private static final Logger LOG = Debug.getLogger(SteepSlopePredicateMixin.class);
    private final Interpolation interpolation = new Interpolation(1.0F,xz -> FractalTerrainInstance.getReliefProvider().getRefinedGrad(xz));
    private final float threshold = 4;

    @Inject( method = "test", at = @At("HEAD"))
    private void test(CallbackInfoReturnable<Boolean> cir) {
        if(FractalTerrainInstance.exists()) {
//            final MaterialRules.MaterialRuleContext.SteepSlopePredicate thisObject = (MaterialRules.MaterialRuleContext.SteepSlopePredicate) (Object) this;
//            final int x = thisObject.context.blockX;
//            final int z = thisObject.context.blockZ;
//            return interpolation.interpolateBilinear(x,z) >= threshold;
            // TODO: implement this
        }
    }

}
