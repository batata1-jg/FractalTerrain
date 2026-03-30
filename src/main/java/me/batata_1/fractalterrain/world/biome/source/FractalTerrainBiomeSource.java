package me.batata_1.fractalterrain.world.biome.source;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.jetbrains.annotations.Nullable;

public class FractalTerrainBiomeSource extends BiomeSource {

    private static final MapCodec<RegistryEntry<Biome>> BIOME_CODEC;
    public static final MapCodec<MultiNoiseUtil.Entries<RegistryEntry<Biome>>> CUSTOM_CODEC;
    public static final MapCodec<RegistryEntry<MultiNoiseBiomeSourceParameterList>> PRESET_CODEC;
    public static final Codec<FractalTerrainBiomeSource> CODEC;

    public final Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>, RegistryEntry<MultiNoiseBiomeSourceParameterList>>
            biomeEntries;

    static {
        BIOME_CODEC = Biome.REGISTRY_CODEC.fieldOf("biome");
        CUSTOM_CODEC = MultiNoiseUtil.Entries.createCodec(BIOME_CODEC).fieldOf("biomes");
        PRESET_CODEC = MultiNoiseBiomeSourceParameterList.REGISTRY_CODEC
                .fieldOf("preset")
                .withLifecycle(Lifecycle.stable());
        CODEC = Codec.mapEither(CUSTOM_CODEC, PRESET_CODEC)
                .xmap(FractalTerrainBiomeSource::new, (FractalTerrainBiomeSource biomeSource) -> biomeSource
                        .biomeEntries)
                .codec();
    }

    private FractalTerrainBiomeSource(
            Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>, RegistryEntry<MultiNoiseBiomeSourceParameterList>>
                    biomeEntries) {
        this.biomeEntries = biomeEntries;
    }

    public static FractalTerrainBiomeSource create(MultiNoiseUtil.Entries<RegistryEntry<Biome>> biomeEntries) {
        return new FractalTerrainBiomeSource(Either.left(biomeEntries));
    }

    public static FractalTerrainBiomeSource create(RegistryEntry<MultiNoiseBiomeSourceParameterList> biomeEntries) {
        return new FractalTerrainBiomeSource(Either.right(biomeEntries));
    }

    @Override
    protected Codec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    private MultiNoiseUtil.Entries<RegistryEntry<Biome>> getBiomeEntries() {
        return this.biomeEntries.map(
                (entries) -> entries,
                (parameterListEntry) -> parameterListEntry.value().getEntries());
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return this.getBiomeEntries().getEntries().stream().map(Pair::getSecond);
    }

    public RegistryEntry<Biome> getBiomeAtPoint(MultiNoiseUtil.NoiseValuePoint point) {
        return this.getBiomeEntries().get(point);
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        return this.getBiomeAtPoint(noise.sample(x, y, z));
    }

    @Nullable
    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(
            int x,
            int y,
            int z,
            int radius,
            int blockCheckInterval,
            Predicate<RegistryEntry<Biome>> predicate,
            Random random,
            boolean bl,
            MultiNoiseUtil.MultiNoiseSampler noiseSampler) {
        LOGGER.warn("locate biome is WIP");
        for (int i = 0; i < getBiomeEntries().getEntries().size(); i++) {
            if (predicate.test(getBiomeEntries().getEntries().get(i).getSecond())) {
                return Pair.of(
                        new BlockPos(0, 0, 0),
                        getBiomeEntries().getEntries().get(i).getSecond());
            }
        }

        return null;
    }

    @Nullable
    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(
            BlockPos origin,
            int radius,
            int horizontalBlockCheckInterval,
            int verticalBlockCheckInterval,
            Predicate<RegistryEntry<Biome>> predicate,
            MultiNoiseUtil.MultiNoiseSampler noiseSampler,
            WorldView world) {
        LOGGER.warn("locate biome is WIP");
        for (int i = 0; i < getBiomeEntries().getEntries().size(); i++) {
            if (predicate.test(getBiomeEntries().getEntries().get(i).getSecond())) {
                return Pair.of(
                        new BlockPos(0, 0, 0),
                        getBiomeEntries().getEntries().get(i).getSecond());
            }
        }

        return null;
    }
}
