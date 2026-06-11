package me.batata_1.fractal_terrain.terrablender;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.loader.impl.lib.sat4j.pb.tools.INegator;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.VanillaBiomeParameters;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

public class FractalTerrainRegion extends Region {
    public static final Identifier LOCATION = new Identifier("fractal_terrain:overworld");

    public FractalTerrainRegion(int weight) {
        super(LOCATION, RegionType.OVERWORLD, weight);
    }

    public void addBiomes(Registry<Biome> registry, Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> mapper) {
        (new VanillaBiomeParameters()).writeOverworldBiomeParameters(mapper);
    }

}
