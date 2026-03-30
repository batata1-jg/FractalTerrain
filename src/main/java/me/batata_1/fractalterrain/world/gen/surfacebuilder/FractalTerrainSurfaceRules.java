package me.batata_1.fractalterrain.world.gen.surfacebuilder;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.YOffset;
import net.minecraft.world.gen.noise.NoiseParametersKeys;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

public class FractalTerrainSurfaceRules {

    private static final MaterialRules.MaterialRule AIR = block(Blocks.AIR);
    private static final MaterialRules.MaterialRule BEDROCK = block(Blocks.BEDROCK);
    private static final MaterialRules.MaterialRule WHITE_TERRACOTTA = block(Blocks.WHITE_TERRACOTTA);
    private static final MaterialRules.MaterialRule ORANGE_TERRACOTTA = block(Blocks.ORANGE_TERRACOTTA);
    private static final MaterialRules.MaterialRule TERRACOTTA = block(Blocks.TERRACOTTA);
    private static final MaterialRules.MaterialRule RED_SAND = block(Blocks.RED_SAND);
    private static final MaterialRules.MaterialRule RED_SANDSTONE = block(Blocks.RED_SANDSTONE);
    private static final MaterialRules.MaterialRule STONE = block(Blocks.STONE);
    private static final MaterialRules.MaterialRule DEEPSLATE = block(Blocks.DEEPSLATE);
    private static final MaterialRules.MaterialRule DIRT = block(Blocks.DIRT);
    private static final MaterialRules.MaterialRule PODZOL = block(Blocks.PODZOL);
    private static final MaterialRules.MaterialRule COARSE_DIRT = block(Blocks.COARSE_DIRT);
    private static final MaterialRules.MaterialRule MYCELIUM = block(Blocks.MYCELIUM);
    private static final MaterialRules.MaterialRule GRASS_BLOCK = block(Blocks.GRASS_BLOCK);
    private static final MaterialRules.MaterialRule CALCITE = block(Blocks.CALCITE);
    private static final MaterialRules.MaterialRule GRAVEL = block(Blocks.GRAVEL);
    private static final MaterialRules.MaterialRule SAND = block(Blocks.SAND);
    private static final MaterialRules.MaterialRule SANDSTONE = block(Blocks.SANDSTONE);
    private static final MaterialRules.MaterialRule PACKED_ICE = block(Blocks.PACKED_ICE);
    private static final MaterialRules.MaterialRule SNOW_BLOCK = block(Blocks.SNOW_BLOCK);
    private static final MaterialRules.MaterialRule MUD = block(Blocks.MUD);
    private static final MaterialRules.MaterialRule POWDER_SNOW = block(Blocks.POWDER_SNOW);
    private static final MaterialRules.MaterialRule ICE = block(Blocks.ICE);
    private static final MaterialRules.MaterialRule WATER = block(Blocks.WATER);

    private static MaterialRules.MaterialRule block(Block block) {
        return MaterialRules.block(block.getDefaultState());
    }

    public static MaterialRules.MaterialRule createFractalTerrainOverworldSurfaceRule() {
        return createDefaultRule(false, false, true);
    }

    public static MaterialRules.MaterialRule createFractalTerrainOverworldLargeSurfaceRule() {
        return createLargeRule(false, false, true);
    }

    public static MaterialRules.MaterialRule createDefaultRule(
            boolean surface, boolean bedrockRoof, boolean bedrockFloor) {
        MaterialRules.MaterialCondition isAboveY97 = MaterialRules.aboveY(YOffset.fixed(97), 2);
        MaterialRules.MaterialCondition isAboveY256 = MaterialRules.aboveY(YOffset.fixed(256), 0);
        MaterialRules.MaterialCondition isAbove63WithStoneDepth =
                MaterialRules.aboveYWithStoneDepth(YOffset.fixed(63), -1);
        MaterialRules.MaterialCondition isAbove74WithStoneDepth =
                MaterialRules.aboveYWithStoneDepth(YOffset.fixed(74), 1);
        MaterialRules.MaterialCondition isAboveY60 = MaterialRules.aboveY(YOffset.fixed(60), 0);
        MaterialRules.MaterialCondition isAboveY62 = MaterialRules.aboveY(YOffset.fixed(62), 0);
        MaterialRules.MaterialCondition isAboveY63 = MaterialRules.aboveY(YOffset.fixed(63), 0);
        MaterialRules.MaterialCondition isAboveWaterMinusOne = MaterialRules.water(-1, 0);
        MaterialRules.MaterialCondition isAboveWaterZero = MaterialRules.water(0, 0);
        MaterialRules.MaterialCondition waterWithStoneDepth = MaterialRules.waterWithStoneDepth(-6, -1);
        MaterialRules.MaterialCondition theHoleOne = MaterialRules.hole();
        MaterialRules.MaterialCondition isFrozenOcean =
                MaterialRules.biome(BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN);
        MaterialRules.MaterialCondition isSteep = MaterialRules.steepSlope();
        MaterialRules.MaterialRule placeGrassAndDirt =
                MaterialRules.sequence(MaterialRules.condition(isAboveWaterZero, GRASS_BLOCK), DIRT);
        MaterialRules.MaterialRule placeSandAndSandstone =
                MaterialRules.sequence(MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING, SANDSTONE), SAND);
        MaterialRules.MaterialRule placeGravelAndStone =
                MaterialRules.sequence(MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING, STONE), GRAVEL);
        MaterialRules.MaterialCondition isBeachOrWarmOcean =
                MaterialRules.biome(BiomeKeys.WARM_OCEAN, BiomeKeys.BEACH, BiomeKeys.SNOWY_BEACH);
        MaterialRules.MaterialCondition isDesert = MaterialRules.biome(BiomeKeys.DESERT);
        MaterialRules.MaterialRule realizeStonyPeaks_StonyShore_WindsweptHills_DripstoneCaves = MaterialRules.sequence(
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.STONY_PEAKS),
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.noiseThreshold(NoiseParametersKeys.CALCITE, -0.0125, 0.0125),
                                        CALCITE),
                                STONE)),
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.STONY_SHORE),
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.noiseThreshold(NoiseParametersKeys.GRAVEL, -0.05, 0.05),
                                        placeGravelAndStone),
                                STONE)),
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.WINDSWEPT_HILLS),
                        MaterialRules.condition(surfaceNoiseThreshold(1.0), STONE)),
                MaterialRules.condition(isBeachOrWarmOcean, placeSandAndSandstone),
                MaterialRules.condition(isDesert, placeSandAndSandstone),
                MaterialRules.condition(MaterialRules.biome(BiomeKeys.DRIPSTONE_CAVES), STONE));
        MaterialRules.MaterialRule placePowderSnow1 = MaterialRules.condition(
                MaterialRules.noiseThreshold(NoiseParametersKeys.POWDER_SNOW, 0.45, 0.58),
                MaterialRules.condition(isAboveWaterZero, POWDER_SNOW));
        MaterialRules.MaterialRule placePowderSnow2 = MaterialRules.condition(
                MaterialRules.noiseThreshold(NoiseParametersKeys.POWDER_SNOW, 0.35, 0.6),
                MaterialRules.condition(isAboveWaterZero, POWDER_SNOW));
        MaterialRules.MaterialRule
                realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_MangroveSwamp =
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.FROZEN_PEAKS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.PACKED_ICE, -0.5, 0.2),
                                                        PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.ICE, -0.0625, 0.025),
                                                        ICE),
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.SNOWY_SLOPES),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, STONE),
                                                placePowderSnow1,
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.JAGGED_PEAKS), STONE),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.GROVE),
                                        MaterialRules.sequence(placePowderSnow1, DIRT)),
                                realizeStonyPeaks_StonyShore_WindsweptHills_DripstoneCaves,
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_SAVANNA),
                                        MaterialRules.condition(surfaceNoiseThreshold(1.75), STONE)),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(
                                                        surfaceNoiseThreshold(2.0), placeGravelAndStone),
                                                MaterialRules.condition(surfaceNoiseThreshold(1.0), STONE),
                                                MaterialRules.condition(surfaceNoiseThreshold(-1.0), DIRT),
                                                placeGravelAndStone)),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.MANGROVE_SWAMP), MUD),
                                DIRT);
        MaterialRules.MaterialRule
                realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_OldGrowthPineTaiga_IceSpikes_MangroveSwamp_MushroomFields =
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.FROZEN_PEAKS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.PACKED_ICE, 0.0, 0.2),
                                                        PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.ICE, 0.0, 0.025),
                                                        ICE),
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.SNOWY_SLOPES),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, STONE),
                                                placePowderSnow2,
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.JAGGED_PEAKS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, STONE),
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.GROVE),
                                        MaterialRules.sequence(
                                                placePowderSnow2,
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                realizeStonyPeaks_StonyShore_WindsweptHills_DripstoneCaves,
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_SAVANNA),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(surfaceNoiseThreshold(1.75), STONE),
                                                MaterialRules.condition(surfaceNoiseThreshold(-0.5), COARSE_DIRT))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(
                                                        surfaceNoiseThreshold(2.0), placeGravelAndStone),
                                                MaterialRules.condition(surfaceNoiseThreshold(1.0), STONE),
                                                MaterialRules.condition(surfaceNoiseThreshold(-1.0), placeGrassAndDirt),
                                                placeGravelAndStone)),
                                MaterialRules.condition(
                                        MaterialRules.biome(
                                                BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(surfaceNoiseThreshold(1.75), COARSE_DIRT),
                                                MaterialRules.condition(surfaceNoiseThreshold(-0.95), PODZOL))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.ICE_SPIKES),
                                        MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK)),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.MANGROVE_SWAMP), MUD),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.MUSHROOM_FIELDS), MYCELIUM),
                                placeGrassAndDirt);
        MaterialRules.MaterialCondition noiseThreshold1 =
                MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, -0.909, -0.5454);
        MaterialRules.MaterialCondition noiseThreshold2 =
                MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, -0.1818, 0.1818);
        MaterialRules.MaterialCondition noiseThreshold3 =
                MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, 0.5454, 0.909);
        MaterialRules.MaterialRule realizeEverything = MaterialRules.sequence(
                MaterialRules.condition(
                        MaterialRules.STONE_DEPTH_FLOOR,
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WOODED_BADLANDS),
                                        MaterialRules.condition(
                                                isAboveY97,
                                                MaterialRules.sequence(
                                                        MaterialRules.condition(noiseThreshold1, COARSE_DIRT),
                                                        MaterialRules.condition(noiseThreshold2, COARSE_DIRT),
                                                        MaterialRules.condition(noiseThreshold3, COARSE_DIRT),
                                                        placeGrassAndDirt))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.SWAMP),
                                        MaterialRules.condition(
                                                isAboveY62,
                                                MaterialRules.condition(
                                                        MaterialRules.not(isAboveY63),
                                                        MaterialRules.condition(
                                                                MaterialRules.noiseThreshold(
                                                                        NoiseParametersKeys.SURFACE_SWAMP, 0.0),
                                                                WATER)))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.MANGROVE_SWAMP),
                                        MaterialRules.condition(
                                                isAboveY60,
                                                MaterialRules.condition(
                                                        MaterialRules.not(isAboveY63),
                                                        MaterialRules.condition(
                                                                MaterialRules.noiseThreshold(
                                                                        NoiseParametersKeys.SURFACE_SWAMP, 0.0),
                                                                WATER)))))),
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.WOODED_BADLANDS),
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR,
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isAboveY256, ORANGE_TERRACOTTA),
                                                MaterialRules.condition(
                                                        isAbove74WithStoneDepth,
                                                        MaterialRules.sequence(
                                                                MaterialRules.condition(noiseThreshold1, TERRACOTTA),
                                                                MaterialRules.condition(noiseThreshold2, TERRACOTTA),
                                                                MaterialRules.condition(noiseThreshold3, TERRACOTTA),
                                                                MaterialRules.terracottaBands())),
                                                MaterialRules.condition(
                                                        isAboveWaterMinusOne,
                                                        MaterialRules.sequence(
                                                                MaterialRules.condition(
                                                                        MaterialRules.STONE_DEPTH_CEILING,
                                                                        RED_SANDSTONE),
                                                                RED_SAND)),
                                                MaterialRules.condition(
                                                        MaterialRules.not(theHoleOne), ORANGE_TERRACOTTA),
                                                MaterialRules.condition(waterWithStoneDepth, WHITE_TERRACOTTA),
                                                placeGravelAndStone)),
                                MaterialRules.condition(
                                        isAbove63WithStoneDepth,
                                        MaterialRules.sequence(
                                                MaterialRules.condition(
                                                        isAboveY63,
                                                        MaterialRules.condition(
                                                                MaterialRules.not(isAbove74WithStoneDepth),
                                                                ORANGE_TERRACOTTA)),
                                                MaterialRules.terracottaBands())),
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
                                        MaterialRules.condition(waterWithStoneDepth, WHITE_TERRACOTTA)))),
                MaterialRules.condition(
                        MaterialRules.STONE_DEPTH_FLOOR,
                        MaterialRules.condition(
                                isAboveWaterMinusOne,
                                MaterialRules.sequence(
                                        MaterialRules.condition(
                                                isFrozenOcean,
                                                MaterialRules.condition(
                                                        theHoleOne,
                                                        MaterialRules.sequence(
                                                                MaterialRules.condition(isAboveWaterZero, AIR),
                                                                MaterialRules.condition(
                                                                        MaterialRules.temperature(), ICE),
                                                                WATER))),
                                        realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_OldGrowthPineTaiga_IceSpikes_MangroveSwamp_MushroomFields))),
                MaterialRules.condition(
                        waterWithStoneDepth,
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR,
                                        MaterialRules.condition(
                                                isFrozenOcean, MaterialRules.condition(theHoleOne, WATER))),
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
                                        realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_MangroveSwamp),
                                MaterialRules.condition(
                                        isBeachOrWarmOcean,
                                        MaterialRules.condition(
                                                MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH_RANGE_6, SANDSTONE)),
                                MaterialRules.condition(
                                        isDesert,
                                        MaterialRules.condition(
                                                MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH_RANGE_30,
                                                SANDSTONE)))),
                MaterialRules.condition(
                        MaterialRules.STONE_DEPTH_FLOOR,
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS), STONE),
                                MaterialRules.condition(
                                        MaterialRules.biome(
                                                BiomeKeys.WARM_OCEAN,
                                                BiomeKeys.LUKEWARM_OCEAN,
                                                BiomeKeys.DEEP_LUKEWARM_OCEAN),
                                        placeSandAndSandstone),
                                placeGravelAndStone)));
        ImmutableList.Builder<MaterialRules.MaterialRule> builder = ImmutableList.builder();
        if (bedrockRoof) {
            builder.add(MaterialRules.condition(
                    MaterialRules.not(
                            MaterialRules.verticalGradient("bedrock_roof", YOffset.belowTop(5), YOffset.getTop())),
                    BEDROCK));
        }

        if (bedrockFloor) {
            builder.add(MaterialRules.condition(
                    MaterialRules.verticalGradient("bedrock_floor", YOffset.getBottom(), YOffset.aboveBottom(5)),
                    BEDROCK));
        }

        FractalTerrainMaterialRules.SteepSlope fracSteepSlope = new FractalTerrainMaterialRules.SteepSlope(1200, 0.2F);

        MaterialRules.MaterialRule conditionOnSlopeTest =
                MaterialRules.condition(MaterialRules.not(fracSteepSlope), realizeEverything);
        builder.add(conditionOnSlopeTest);
        builder.add(MaterialRules.condition(
                MaterialRules.verticalGradient("deepslate", YOffset.fixed(0), YOffset.fixed(8)), DEEPSLATE));
        return MaterialRules.sequence(builder.build().toArray(MaterialRules.MaterialRule[]::new));
    }

    public static MaterialRules.MaterialRule createLargeRule(
            boolean surface, boolean bedrockRoof, boolean bedrockFloor) {
        MaterialRules.MaterialCondition isAboveY97 = MaterialRules.aboveY(YOffset.fixed(97), 2);
        MaterialRules.MaterialCondition isAboveY256 = MaterialRules.aboveY(YOffset.fixed(256), 0);
        MaterialRules.MaterialCondition isAbove63WithStoneDepth =
                MaterialRules.aboveYWithStoneDepth(YOffset.fixed(63), -1);
        MaterialRules.MaterialCondition isAbove74WithStoneDepth =
                MaterialRules.aboveYWithStoneDepth(YOffset.fixed(74), 1);
        MaterialRules.MaterialCondition isAboveY60 = MaterialRules.aboveY(YOffset.fixed(60), 0);
        MaterialRules.MaterialCondition isAboveY62 = MaterialRules.aboveY(YOffset.fixed(62), 0);
        MaterialRules.MaterialCondition isAboveY63 = MaterialRules.aboveY(YOffset.fixed(63), 0);
        MaterialRules.MaterialCondition isAboveWaterMinusOne = MaterialRules.water(-1, 0);
        MaterialRules.MaterialCondition isAboveWaterZero = MaterialRules.water(0, 0);
        MaterialRules.MaterialCondition waterWithStoneDepth = MaterialRules.waterWithStoneDepth(-6, -1);
        MaterialRules.MaterialCondition theHoleOne = MaterialRules.hole();
        MaterialRules.MaterialCondition isFrozenOcean =
                MaterialRules.biome(BiomeKeys.FROZEN_OCEAN, BiomeKeys.DEEP_FROZEN_OCEAN);
        MaterialRules.MaterialCondition isSteep = MaterialRules.steepSlope();
        MaterialRules.MaterialRule placeGrassAndDirt =
                MaterialRules.sequence(MaterialRules.condition(isAboveWaterZero, GRASS_BLOCK), DIRT);
        MaterialRules.MaterialRule placeSandAndSandstone =
                MaterialRules.sequence(MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING, SANDSTONE), SAND);
        MaterialRules.MaterialRule placeGravelAndStone =
                MaterialRules.sequence(MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING, STONE), GRAVEL);
        MaterialRules.MaterialCondition isBeachOrWarmOcean =
                MaterialRules.biome(BiomeKeys.WARM_OCEAN, BiomeKeys.BEACH, BiomeKeys.SNOWY_BEACH);
        MaterialRules.MaterialCondition isDesert = MaterialRules.biome(BiomeKeys.DESERT);
        MaterialRules.MaterialRule realizeStonyPeaks_StonyShore_WindsweptHills_DripstoneCaves = MaterialRules.sequence(
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.STONY_PEAKS),
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.noiseThreshold(NoiseParametersKeys.CALCITE, -0.0125, 0.0125),
                                        CALCITE),
                                STONE)),
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.STONY_SHORE),
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.noiseThreshold(NoiseParametersKeys.GRAVEL, -0.05, 0.05),
                                        placeGravelAndStone),
                                STONE)),
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.WINDSWEPT_HILLS),
                        MaterialRules.condition(surfaceNoiseThreshold(1.0), STONE)),
                MaterialRules.condition(isBeachOrWarmOcean, placeSandAndSandstone),
                MaterialRules.condition(isDesert, placeSandAndSandstone),
                MaterialRules.condition(MaterialRules.biome(BiomeKeys.DRIPSTONE_CAVES), STONE));
        MaterialRules.MaterialRule placePowderSnow1 = MaterialRules.condition(
                MaterialRules.noiseThreshold(NoiseParametersKeys.POWDER_SNOW, 0.45, 0.58),
                MaterialRules.condition(isAboveWaterZero, POWDER_SNOW));
        MaterialRules.MaterialRule placePowderSnow2 = MaterialRules.condition(
                MaterialRules.noiseThreshold(NoiseParametersKeys.POWDER_SNOW, 0.35, 0.6),
                MaterialRules.condition(isAboveWaterZero, POWDER_SNOW));
        MaterialRules.MaterialRule
                realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_MangroveSwamp =
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.FROZEN_PEAKS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.PACKED_ICE, -0.5, 0.2),
                                                        PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.ICE, -0.0625, 0.025),
                                                        ICE),
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.SNOWY_SLOPES),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, STONE),
                                                placePowderSnow1,
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.JAGGED_PEAKS), STONE),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.GROVE),
                                        MaterialRules.sequence(placePowderSnow1, DIRT)),
                                realizeStonyPeaks_StonyShore_WindsweptHills_DripstoneCaves,
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_SAVANNA),
                                        MaterialRules.condition(surfaceNoiseThreshold(1.75), STONE)),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(
                                                        surfaceNoiseThreshold(2.0), placeGravelAndStone),
                                                MaterialRules.condition(surfaceNoiseThreshold(1.0), STONE),
                                                MaterialRules.condition(surfaceNoiseThreshold(-1.0), DIRT),
                                                placeGravelAndStone)),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.MANGROVE_SWAMP), MUD),
                                DIRT);
        MaterialRules.MaterialRule
                realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_OldGrowthPineTaiga_IceSpikes_MangroveSwamp_MushroomFields =
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.FROZEN_PEAKS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.PACKED_ICE, 0.0, 0.2),
                                                        PACKED_ICE),
                                                MaterialRules.condition(
                                                        MaterialRules.noiseThreshold(
                                                                NoiseParametersKeys.ICE, 0.0, 0.025),
                                                        ICE),
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.SNOWY_SLOPES),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, STONE),
                                                placePowderSnow2,
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.JAGGED_PEAKS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isSteep, STONE),
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.GROVE),
                                        MaterialRules.sequence(
                                                placePowderSnow2,
                                                MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK))),
                                realizeStonyPeaks_StonyShore_WindsweptHills_DripstoneCaves,
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_SAVANNA),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(surfaceNoiseThreshold(1.75), STONE),
                                                MaterialRules.condition(surfaceNoiseThreshold(-0.5), COARSE_DIRT))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(
                                                        surfaceNoiseThreshold(2.0), placeGravelAndStone),
                                                MaterialRules.condition(surfaceNoiseThreshold(1.0), STONE),
                                                MaterialRules.condition(surfaceNoiseThreshold(-1.0), placeGrassAndDirt),
                                                placeGravelAndStone)),
                                MaterialRules.condition(
                                        MaterialRules.biome(
                                                BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA),
                                        MaterialRules.sequence(
                                                MaterialRules.condition(surfaceNoiseThreshold(1.75), COARSE_DIRT),
                                                MaterialRules.condition(surfaceNoiseThreshold(-0.95), PODZOL))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.ICE_SPIKES),
                                        MaterialRules.condition(isAboveWaterZero, SNOW_BLOCK)),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.MANGROVE_SWAMP), MUD),
                                MaterialRules.condition(MaterialRules.biome(BiomeKeys.MUSHROOM_FIELDS), MYCELIUM),
                                placeGrassAndDirt);
        MaterialRules.MaterialCondition noiseThreshold1 =
                MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, -0.909, -0.5454);
        MaterialRules.MaterialCondition noiseThreshold2 =
                MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, -0.1818, 0.1818);
        MaterialRules.MaterialCondition noiseThreshold3 =
                MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, 0.5454, 0.909);
        MaterialRules.MaterialRule realizeEverything = MaterialRules.sequence(
                MaterialRules.condition(
                        MaterialRules.STONE_DEPTH_FLOOR,
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.WOODED_BADLANDS),
                                        MaterialRules.condition(
                                                isAboveY97,
                                                MaterialRules.sequence(
                                                        MaterialRules.condition(noiseThreshold1, COARSE_DIRT),
                                                        MaterialRules.condition(noiseThreshold2, COARSE_DIRT),
                                                        MaterialRules.condition(noiseThreshold3, COARSE_DIRT),
                                                        placeGrassAndDirt))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.SWAMP),
                                        MaterialRules.condition(
                                                isAboveY62,
                                                MaterialRules.condition(
                                                        MaterialRules.not(isAboveY63),
                                                        MaterialRules.condition(
                                                                MaterialRules.noiseThreshold(
                                                                        NoiseParametersKeys.SURFACE_SWAMP, 0.0),
                                                                WATER)))),
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.MANGROVE_SWAMP),
                                        MaterialRules.condition(
                                                isAboveY60,
                                                MaterialRules.condition(
                                                        MaterialRules.not(isAboveY63),
                                                        MaterialRules.condition(
                                                                MaterialRules.noiseThreshold(
                                                                        NoiseParametersKeys.SURFACE_SWAMP, 0.0),
                                                                WATER)))))),
                MaterialRules.condition(
                        MaterialRules.biome(BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.WOODED_BADLANDS),
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR,
                                        MaterialRules.sequence(
                                                MaterialRules.condition(isAboveY256, ORANGE_TERRACOTTA),
                                                MaterialRules.condition(
                                                        isAbove74WithStoneDepth,
                                                        MaterialRules.sequence(
                                                                MaterialRules.condition(noiseThreshold1, TERRACOTTA),
                                                                MaterialRules.condition(noiseThreshold2, TERRACOTTA),
                                                                MaterialRules.condition(noiseThreshold3, TERRACOTTA),
                                                                MaterialRules.terracottaBands())),
                                                MaterialRules.condition(
                                                        isAboveWaterMinusOne,
                                                        MaterialRules.sequence(
                                                                MaterialRules.condition(
                                                                        MaterialRules.STONE_DEPTH_CEILING,
                                                                        RED_SANDSTONE),
                                                                RED_SAND)),
                                                MaterialRules.condition(
                                                        MaterialRules.not(theHoleOne), ORANGE_TERRACOTTA),
                                                MaterialRules.condition(waterWithStoneDepth, WHITE_TERRACOTTA),
                                                placeGravelAndStone)),
                                MaterialRules.condition(
                                        isAbove63WithStoneDepth,
                                        MaterialRules.sequence(
                                                MaterialRules.condition(
                                                        isAboveY63,
                                                        MaterialRules.condition(
                                                                MaterialRules.not(isAbove74WithStoneDepth),
                                                                ORANGE_TERRACOTTA)),
                                                MaterialRules.terracottaBands())),
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
                                        MaterialRules.condition(waterWithStoneDepth, WHITE_TERRACOTTA)))),
                MaterialRules.condition(
                        MaterialRules.STONE_DEPTH_FLOOR,
                        MaterialRules.condition(
                                isAboveWaterMinusOne,
                                MaterialRules.sequence(
                                        MaterialRules.condition(
                                                isFrozenOcean,
                                                MaterialRules.condition(
                                                        theHoleOne,
                                                        MaterialRules.sequence(
                                                                MaterialRules.condition(isAboveWaterZero, AIR),
                                                                MaterialRules.condition(
                                                                        MaterialRules.temperature(), ICE),
                                                                WATER))),
                                        realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_OldGrowthPineTaiga_IceSpikes_MangroveSwamp_MushroomFields))),
                MaterialRules.condition(
                        waterWithStoneDepth,
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR,
                                        MaterialRules.condition(
                                                isFrozenOcean, MaterialRules.condition(theHoleOne, WATER))),
                                MaterialRules.condition(
                                        MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
                                        realizeFrozenPeaks_SnowySlopes_JaggedPeaks_Grove_WindsweptSavanna_WindsweptGravellyHills_MangroveSwamp),
                                MaterialRules.condition(
                                        isBeachOrWarmOcean,
                                        MaterialRules.condition(
                                                MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH_RANGE_6, SANDSTONE)),
                                MaterialRules.condition(
                                        isDesert,
                                        MaterialRules.condition(
                                                MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH_RANGE_30,
                                                SANDSTONE)))),
                MaterialRules.condition(
                        MaterialRules.STONE_DEPTH_FLOOR,
                        MaterialRules.sequence(
                                MaterialRules.condition(
                                        MaterialRules.biome(BiomeKeys.FROZEN_PEAKS, BiomeKeys.JAGGED_PEAKS), STONE),
                                MaterialRules.condition(
                                        MaterialRules.biome(
                                                BiomeKeys.WARM_OCEAN,
                                                BiomeKeys.LUKEWARM_OCEAN,
                                                BiomeKeys.DEEP_LUKEWARM_OCEAN),
                                        placeSandAndSandstone),
                                placeGravelAndStone)));
        ImmutableList.Builder<MaterialRules.MaterialRule> builder = ImmutableList.builder();
        if (bedrockRoof) {
            builder.add(MaterialRules.condition(
                    MaterialRules.not(
                            MaterialRules.verticalGradient("bedrock_roof", YOffset.belowTop(5), YOffset.getTop())),
                    BEDROCK));
        }

        if (bedrockFloor) {
            builder.add(MaterialRules.condition(
                    MaterialRules.verticalGradient("bedrock_floor", YOffset.getBottom(), YOffset.aboveBottom(5)),
                    BEDROCK));
        }

        FractalTerrainMaterialRules.SteepSlope fracSteepSlope = new FractalTerrainMaterialRules.SteepSlope(1200, 1F);

        MaterialRules.MaterialRule conditionOnSlopeTest =
                MaterialRules.condition(MaterialRules.not(fracSteepSlope), realizeEverything);
        builder.add(conditionOnSlopeTest);
        builder.add(MaterialRules.condition(
                MaterialRules.verticalGradient("deepslate", YOffset.fixed(0), YOffset.fixed(8)), DEEPSLATE));
        return MaterialRules.sequence(builder.build().toArray(MaterialRules.MaterialRule[]::new));
    }

    private static MaterialRules.MaterialCondition surfaceNoiseThreshold(double min) {
        return MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, min / 8.25, Double.MAX_VALUE);
    }
}
