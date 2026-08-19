package me.batata_1.fractal_terrain.mixin;

import static terrablender.util.LevelUtils.initializeBiomes;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractal_terrain.world.gen.chunk.FractalTerrainChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrablender.util.LevelUtils;

@Mixin(LevelUtils.class)
public abstract class LevelUtilsMixin {

    @Inject(method = "shouldApplyToBiomeSource", at = @At(value = "HEAD"), cancellable = true)
    private static void FractalTerrainBiomeSourceEnabeler(
            BiomeSource biomeSource, CallbackInfoReturnable<Boolean> cir) {
        if (biomeSource instanceof FractalTerrainBiomeSource) cir.setReturnValue(true);
    }

    @Inject(method = "initializeOnServerStart", at = @At(value = "TAIL"))
    private static void FractalTerrainTerrablenderCompat(
            MinecraftServer server,
            CallbackInfo ci,
            @Local(name = "levelStemRegistry") Registry<LevelStem> levelStemRegistry,
            @Local(name = "seed") long seed,
            @Local(name = "registryAccess") RegistryAccess registryAccess) {
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : levelStemRegistry.entrySet()) {
            LevelStem stem = (LevelStem) entry.getValue();
            if (stem.generator() instanceof FractalTerrainChunkGenerator ftChunkGen) {
                initializeBiomes(
                        registryAccess,
                        stem.type(),
                        (ResourceKey) entry.getKey(),
                        new NoiseBasedChunkGenerator(ftChunkGen.getBiomeSource(), ftChunkGen.generatorSettings()),
                        seed);
            }
        }
    }
}
