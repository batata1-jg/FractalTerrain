package me.batata_1.fractal_terrain.terrablender;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.loader.impl.lib.sat4j.pb.tools.INegator;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

public class FractalTerrainRegion extends Region {
    public static final ResourceLocation LOCATION = new ResourceLocation("fractal_terrain:overworld");

    public FractalTerrainRegion(int weight) {
        super(LOCATION, RegionType.OVERWORLD, weight);
    }

    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        (new OverworldBiomeBuilder()).addBiomes(mapper);
    }

}
