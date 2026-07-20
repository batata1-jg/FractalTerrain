package me.batata_1.fractal_terrain.world.biome.source;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class FractalTerrainBiomeSource extends MultiNoiseBiomeSource {

    private static final Logger LOG = getLogger(FractalTerrainBiomeSource.class);
    private static final MapCodec<Holder<Biome>> BIOME_CODEC;
    public static final MapCodec<Climate.ParameterList<Holder<Biome>>> CUSTOM_CODEC;
    private static final MapCodec<Holder<MultiNoiseBiomeSourceParameterList>> PRESET_CODEC;
    public static final Codec<FractalTerrainBiomeSource> CODEC;
    private final Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries;

    private FractalTerrainBiomeSource(
            Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries) {
        super(biomeEntries);
        this.biomeEntries = biomeEntries;
    }

    private Climate.ParameterList<Holder<Biome>> getBiomeRegistry() {
        return this.biomeEntries.map(
                (entries) -> {
                    return entries;
                },
                (parameterListEntry) -> {
                    return parameterListEntry.value().parameters();
                });
    }

    @Override
    protected @NotNull Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.getBiomeRegistry().values().stream().map(Pair::getSecond);
    }

    public static FractalTerrainBiomeSource createFromList(Climate.@NotNull ParameterList<Holder<Biome>> biomeEntries) {
        return new FractalTerrainBiomeSource(Either.left(biomeEntries));
    }

    public static FractalTerrainBiomeSource createFromPreset(
            @NotNull Holder<MultiNoiseBiomeSourceParameterList> biomeEntries) {
        return new FractalTerrainBiomeSource(Either.right(biomeEntries));
    }

    @Override
    public @NotNull Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.@NotNull Sampler noise) {
        //           Climate.TargetPoint t = new Climate.TargetPoint(0,0,0,0,0,0);
        ////         LOG.debug("getBiome({}, {}, {}, {})", x, y, z, noise);
        ////                throw new RuntimeException("Not implemented");
        //          return this.getNoiseBiome(t);
        return this.getNoiseBiome(
                FractalTerrainInstance.getBiomeProvider().sampler.sample(x, y, z));
    }

    @Override
    public void addDebugInfo(@NotNull List<String> info, @NotNull BlockPos pos, Climate.@NotNull Sampler noiseSampler) {
        final Climate.Sampler sampler = FractalTerrainInstance.getBiomeProvider().sampler;
        super.addDebugInfo(info, pos, sampler);
        int i = QuartPos.fromBlock(pos.getX());
        int j = QuartPos.fromBlock(pos.getY());
        int k = QuartPos.fromBlock(pos.getZ());

        Climate.TargetPoint noiseValuePoint = sampler.sample(i, j, k);
        float cont = Climate.unquantizeCoord(noiseValuePoint.continentalness());
        float erosion = Climate.unquantizeCoord(noiseValuePoint.erosion());
        float temp = Climate.unquantizeCoord(noiseValuePoint.temperature());
        float humid = Climate.unquantizeCoord(noiseValuePoint.humidity());
        float weird = Climate.unquantizeCoord(noiseValuePoint.weirdness());
        float depth = Climate.unquantizeCoord(noiseValuePoint.depth());
        info.add("fractal_terrain_biomes C: " + cont + " E: " + erosion + " T: " + temp + " H: " + humid + " W: "
                + weird + " D: " + depth);
    }

    @Nullable
    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
            int x,
            int y,
            int z,
            int radius,
            int blockCheckInterval,
            Predicate<Holder<Biome>> predicate,
            RandomSource random,
            boolean bl,
            Climate.Sampler noiseSampler) {
        // LOGGER.warn("locate biome is WIP");
        for (int i = 0; i < parameters().values().size(); i++) {
            if (predicate.test(parameters().values().get(i).getSecond())) {
                return Pair.of(
                        new BlockPos(0, 0, 0), parameters().values().get(i).getSecond());
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
            BlockPos origin,
            int radius,
            int horizontalBlockCheckInterval,
            int verticalBlockCheckInterval,
            Predicate<Holder<Biome>> predicate,
            Climate.Sampler noiseSampler,
            LevelReader world) {
        //   LOGGER.warn("locate biome is WIP");
        for (int i = 0; i < parameters().values().size(); i++) {
            if (predicate.test(parameters().values().get(i).getSecond())) {
                return Pair.of(
                        new BlockPos(0, 0, 0), parameters().values().get(i).getSecond());
            }
        }

        return null;
    }

    static {
        BIOME_CODEC = Biome.CODEC.fieldOf("biome");
        CUSTOM_CODEC = Climate.ParameterList.codec(BIOME_CODEC).fieldOf("biomes");
        PRESET_CODEC =
                MultiNoiseBiomeSourceParameterList.CODEC.fieldOf("preset").withLifecycle(Lifecycle.stable());
        CODEC = Codec.mapEither(CUSTOM_CODEC, PRESET_CODEC)
                .xmap(FractalTerrainBiomeSource::new, (FractalTerrainBiomeSource biomeSource) -> biomeSource
                        .biomeEntries)
                .codec();
    }
}
