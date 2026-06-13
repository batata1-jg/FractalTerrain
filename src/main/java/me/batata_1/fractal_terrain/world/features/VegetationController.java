package me.batata_1.fractal_terrain.world.features;

import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class VegetationController {

    public static boolean checkIfLargeVegetation(FeatureConfiguration config) {
        return config instanceof TreeConfiguration;
    }

    public static float densityAt(int x, int z) {
        return 0;
    }
}
