package me.batata_1.fractal_terrain.terrablender;

import com.google.common.collect.ImmutableList;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.core.TerraBlender;
import terrablender.worldgen.IExtendedBiomeSource;
import terrablender.worldgen.IExtendedNoiseGeneratorSettings;
import terrablender.worldgen.IExtendedParameterList;

public class InitTerrablender {

    public static void initializeBlenderBiomes(
            RegistryAccess registryAccess,
            ResourceKey<LevelStem> levelResourceKey,
            NoiseGeneratorSettings settings,
            FractalTerrainBiomeSource biomeSource,
            long seed) {

        RegionType regionType = RegionType.OVERWORLD;
        ((IExtendedNoiseGeneratorSettings) (Object) settings).setRegionType(regionType);
        Climate.ParameterList<Holder<Biome>> parameters = biomeSource.parameters();
        IExtendedParameterList parametersEx = (IExtendedParameterList) parameters;
        parametersEx.initializeForTerraBlender(registryAccess, regionType, seed);
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
        ImmutableList.Builder<Holder<Biome>> builder = ImmutableList.builder();
        Regions.get(regionType)
                .forEach((region) -> region.addBiomes(
                        biomeRegistry, (pair) -> builder.add(biomeRegistry.getHolderOrThrow(pair.getSecond()))));
        ((IExtendedBiomeSource) biomeSource).appendDeferredBiomesList(builder.build());
        TerraBlender.LOGGER.info(
                String.format("Initialized TerraBlender biomes for level stem %s", levelResourceKey.location()));
    }
}
