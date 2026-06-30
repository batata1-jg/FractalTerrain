package me.batata_1.fractal_terrain.mixin;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.Interpolation;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SurfaceRules.Context.SteepMaterialCondition.class)
public abstract class SteepSlopePredicateMixin {

    @Unique
    private static final Logger LOG = Debug.getLogger(SteepSlopePredicateMixin.class);

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void correctSteep(CallbackInfoReturnable<Boolean> cir) {
        if (FractalTerrainInstance.exists()) {
            final SurfaceRules.Context.SteepMaterialCondition thisObject =  (SurfaceRules.Context.SteepMaterialCondition) (Object) this;
            final int x = thisObject.context.blockX;
            final int z = thisObject.context.blockZ;
            final float slope = FractalTerrainInstance.getHeightmapCache().getOrCompute(new ChunkPos(x>>4,z>>4)).get(FractalTerrainHeightmap.Types.REFINED_GRAD,x&15,z&15);
            cir.setReturnValue(slope>=35);
        }
    }
}
