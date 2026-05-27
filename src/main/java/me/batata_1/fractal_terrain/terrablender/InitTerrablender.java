package me.batata_1.fractal_terrain.terrablender;

import com.google.common.collect.ImmutableList;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.core.TerraBlender;
import terrablender.worldgen.IExtendedBiomeSource;
import terrablender.worldgen.IExtendedNoiseGeneratorSettings;
import terrablender.worldgen.IExtendedParameterList;

public class InitTerrablender {

    public static void initializeBlenderBiomes(
            DynamicRegistryManager registryAccess,
            RegistryKey<DimensionOptions> levelResourceKey,
            ChunkGeneratorSettings settings,
            FractalTerrainBiomeSource biomeSource,
            long seed) {

        RegionType regionType = RegionType.OVERWORLD;
        ((IExtendedNoiseGeneratorSettings) (Object) settings).setRegionType(regionType);
        MultiNoiseUtil.Entries<RegistryEntry<Biome>> parameters = biomeSource.getBiomeEntries();
        IExtendedParameterList parametersEx = (IExtendedParameterList) parameters;
        parametersEx.initializeForTerraBlender(registryAccess, regionType, seed);
        Registry<Biome> biomeRegistry = registryAccess.get(RegistryKeys.BIOME);
        ImmutableList.Builder<RegistryEntry<Biome>> builder = ImmutableList.builder();
        Regions.get(regionType)
                .forEach((region) -> region.addBiomes(
                        biomeRegistry, (pair) -> builder.add(biomeRegistry.entryOf(pair.getSecond()))));
        ((IExtendedBiomeSource) biomeSource).appendDeferredBiomesList(builder.build());
        TerraBlender.LOGGER.info(
                String.format("Initialized TerraBlender biomes for level stem %s", levelResourceKey.getValue()));
    }
}
