package me.batata_1.fractal_terrain.world.biome.source;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;
import static me.batata_1.fractal_terrain.references.Reference.LOGGER;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class FractalTerrainBiomeSource extends MultiNoiseBiomeSource {

    private static final Logger LOG = getLogger(FractalTerrainBiomeSource.class);
    private static final MapCodec<RegistryEntry<Biome>> BIOME_CODEC;
    public static final MapCodec<MultiNoiseUtil.Entries<RegistryEntry<Biome>>> CUSTOM_CODEC;
    private static final MapCodec<RegistryEntry<MultiNoiseBiomeSourceParameterList>> PRESET_CODEC;
    public static final Codec<FractalTerrainBiomeSource> CODEC;
    private final Either<
                    MultiNoiseUtil.Entries<RegistryEntry<Biome>>, RegistryEntry<MultiNoiseBiomeSourceParameterList>>
            biomeEntries;

    private FractalTerrainBiomeSource(
            Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>, RegistryEntry<MultiNoiseBiomeSourceParameterList>>
                    biomeEntries) {
        super(biomeEntries);
        this.biomeEntries = biomeEntries;
    }

    private MultiNoiseUtil.Entries<RegistryEntry<Biome>> getBiomeRegistry() {
        return this.biomeEntries.map(
                (entries) -> {
                    return entries;
                },
                (parameterListEntry) -> {
                    return parameterListEntry.value().getEntries();
                });
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return this.getBiomeRegistry().getEntries().stream().map(Pair::getSecond);
    }

    public static FractalTerrainBiomeSource create(MultiNoiseUtil.Entries<RegistryEntry<Biome>> biomeEntries) {
        return new FractalTerrainBiomeSource(Either.left(biomeEntries));
    }

    public static FractalTerrainBiomeSource create(RegistryEntry<MultiNoiseBiomeSourceParameterList> biomeEntries) {
        return new FractalTerrainBiomeSource(Either.right(biomeEntries));
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        // LOG.debug("getBiome({}, {}, {}, {})", x, y, z, noise);
        //        throw new RuntimeException("Not implemented");
        return this.getBiomeAtPoint(
                FractalTerrainInstance.getBiomeProvider().sampler.sample(x, y, z));
    }

    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, MultiNoiseUtil.MultiNoiseSampler noiseSampler) {
        final MultiNoiseUtil.MultiNoiseSampler sampler = FractalTerrainInstance.getBiomeProvider().sampler;
        super.addDebugInfo(info, pos, noiseSampler);
        int i = BiomeCoords.fromBlock(pos.getX());
        int j = BiomeCoords.fromBlock(pos.getY());
        int k = BiomeCoords.fromBlock(pos.getZ());

        MultiNoiseUtil.NoiseValuePoint noiseValuePoint = sampler.sample(i, j, k);
        float cont = MultiNoiseUtil.toFloat(noiseValuePoint.continentalnessNoise());
        float erosion = MultiNoiseUtil.toFloat(noiseValuePoint.erosionNoise());
        float temp = MultiNoiseUtil.toFloat(noiseValuePoint.temperatureNoise());
        float humid = MultiNoiseUtil.toFloat(noiseValuePoint.humidityNoise());
        float weird = MultiNoiseUtil.toFloat(noiseValuePoint.weirdnessNoise());
        float depth = MultiNoiseUtil.toFloat(noiseValuePoint.depth());
        info.add("fractal_terrain_biomes C: " + cont + " E: " + erosion + " T: " + temp + " H: " + humid + " W: " + weird + " D: " + depth);
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
       // LOGGER.warn("locate biome is WIP");
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
     //   LOGGER.warn("locate biome is WIP");
        for (int i = 0; i < getBiomeEntries().getEntries().size(); i++) {
            if (predicate.test(getBiomeEntries().getEntries().get(i).getSecond())) {
                return Pair.of(
                        new BlockPos(0, 0, 0),
                        getBiomeEntries().getEntries().get(i).getSecond());
            }
        }

        return null;
    }

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
}
