package me.batata_1.fractal_terrain.mixin;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SurfaceRules.Context.class)
public interface MaterialRuleContextAccessor {
    @Invoker("<init>")
    static SurfaceRules.Context createMaterialRuleContext(
            SurfaceSystem surfaceBuilder,
            RandomState noiseConfig,
            ChunkAccess chunk,
            NoiseChunk chunkNoiseSampler,
            Function<BlockPos, Holder<Biome>> posToBiome,
            Registry<Biome> registry,
            WorldGenerationContext heightContext) {
        throw new UnsupportedOperationException();
    }
}
