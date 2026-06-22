package me.batata_1.fractal_terrain.world.gen.surfacebuilder;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.Objects;
import me.batata_1.fractal_terrain.mixin.MaterialRuleContextAccessor;
import me.batata_1.fractal_terrain.relief.ReliefAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;

public class FractalTerrainSurfaceSystem extends SurfaceSystem {

    private static final Logger LOG = getLogger(FractalTerrainSurfaceSystem.class);

    private static final BlockState WHITE_TERRACOTTA;
    private static final BlockState ORANGE_TERRACOTTA;
    private static final BlockState TERRACOTTA;
    private static final BlockState YELLOW_TERRACOTTA;
    private static final BlockState BROWN_TERRACOTTA;
    private static final BlockState RED_TERRACOTTA;
    private static final BlockState LIGHT_GRAY_TERRACOTTA;
    private static final BlockState PACKED_ICE;
    private static final BlockState SNOW_BLOCK;

    static {
        WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.defaultBlockState();
        ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
        YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.defaultBlockState();
        BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.defaultBlockState();
        RED_TERRACOTTA = Blocks.RED_TERRACOTTA.defaultBlockState();
        LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
        PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
        SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    }

    private final BlockState defaultState;
    private final int seaLevel;
    //  private final BlockState[] terracottaBands;
    private final NormalNoise terracottaBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final PositionalRandomFactory randomDeriver;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;
    private final SurfaceRules.RuleSource surfaceRules;

    public FractalTerrainSurfaceSystem(
            RandomState noiseConfig,
            BlockState defaultState,
            int seaLevel,
            PositionalRandomFactory randomDeriver,
            SurfaceRules.RuleSource surfaceRules) {
        super(noiseConfig, defaultState, seaLevel, randomDeriver);
        this.defaultState = defaultState;
        this.seaLevel = seaLevel;
        this.randomDeriver = randomDeriver;
        this.terracottaBandsOffsetNoise = noiseConfig.getOrCreateNoise(Noises.CLAY_BANDS_OFFSET);
        // this.terracottaBands = createTerracottaBands(randomDeriver.split(new Identifier("clay_bands")));
        this.surfaceNoise = noiseConfig.getOrCreateNoise(Noises.SURFACE);
        this.surfaceSecondaryNoise = noiseConfig.getOrCreateNoise(Noises.SURFACE_SECONDARY);
        this.badlandsPillarNoise = noiseConfig.getOrCreateNoise(Noises.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = noiseConfig.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = noiseConfig.getOrCreateNoise(Noises.BADLANDS_SURFACE);
        this.icebergPillarNoise = noiseConfig.getOrCreateNoise(Noises.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = noiseConfig.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = noiseConfig.getOrCreateNoise(Noises.ICEBERG_SURFACE);
        this.surfaceRules = surfaceRules;
    }

    private int quantize(final float baseValue, final int steps) {
        return (int) (Math.floor(baseValue * steps + 0.5));
    }

    private int sedimentDepth(final int x, final int z, ReliefAccessor accessor) {
        //              final float grad = (float) (reliefGradInterpolation.interpolateBilinear(x, z));
        //              final float normDepth = 1 / (1 + grad * grad / fallOf);
        //              return quantize(normDepth, maxDepth - minDepth) + minDepth;
        return 3;
    }

    static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    private BlockState sedimentStrata(int x, int z, int distFromSurface, int surfaceHeight) {
        if (distFromSurface == surfaceHeight) return GRASS_BLOCK;
        return DIRT;
    }

    private void buildSurface(
            final int x,
            final int z,
            final ChunkAccess chunk,
            final int dx,
            final int dz,
            final int[] reliefBaseHeight) {
        final int surfaceHeight = reliefBaseHeight[((dx << 4) + dz)];
    }

    private BlockState topLayer(final int x, final int z) {
        return applyTerrainGradient(x, z);
    }

    private static final BlockState[] terrainGradient = {
        Blocks.OBSIDIAN.defaultBlockState(),
        Blocks.BLACKSTONE.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
        Blocks.SMOOTH_BASALT.defaultBlockState(),
        Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
        Blocks.CYAN_TERRACOTTA.defaultBlockState(),
        Blocks.DEEPSLATE.defaultBlockState(),
        Blocks.TUFF.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState(),
        Blocks.STONE.defaultBlockState(),
        Blocks.ANDESITE.defaultBlockState(),
        Blocks.DIORITE.defaultBlockState(),
        Blocks.CALCITE.defaultBlockState(),
        Blocks.SNOW_BLOCK.defaultBlockState()
    };

    public BlockState applyTerrainGradient(final int x, final int z) {
        final int colors = terrainGradient.length - 1;
        //        final int idx = (int) (Math.floor(
        //                (Math.tanh(reliefResInterpolation.interpolateBilinear(x, z) / 15.0) * 0.5 + 0.5) * colors +
        // 0.5));
        return terrainGradient[0];
    }

    public void buildSurface(
            ReliefAccessor accessor,
            RandomState noiseConfig,
            BiomeManager biomeAccess,
            Registry<Biome> biomeRegistry,
            WorldGenerationContext heightContext,
            final ChunkAccess chunk,
            NoiseChunk chunkNoiseSampler) {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        BlockColumn blockColumn = new SurfaceBuilderBlockCollum(chunk, mutable, chunkPos);
        Objects.requireNonNull(biomeAccess);
        SurfaceRules.Context materialRuleContext = MaterialRuleContextAccessor.createMaterialRuleContext(
                this, noiseConfig, chunk, chunkNoiseSampler, biomeAccess::getBiome, biomeRegistry, heightContext);
        SurfaceRules.SurfaceRule blockStateRule = surfaceRules.apply(materialRuleContext);
        BlockPos.MutableBlockPos mutable2 = new BlockPos.MutableBlockPos();

        // Debug.debugMixin(materialRuleContext);

        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                final int x = startX + dx;
                final int z = startZ + dz;
                final int surface_height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz) + 1;
                mutable.setX(x).setZ(z);
                Holder<Biome> registryEntry = biomeAccess.getBiome(mutable2.set(x, 0, z));
                //                if (registryEntry.matchesKey(BiomeKeys.ERODED_BADLANDS)) {
                //                    this.placeBadlandsPillar(blockColumn, xx, n, o, chunk);
                //                }
                materialRuleContext.updateXZ(x, z);
                int stoneDepthAbove = 0;
                int fluid_height = Integer.MIN_VALUE;
                int s = Integer.MAX_VALUE;
                int bottomY = chunk.getMinBuildHeight();
                final int sedimentLayerDepth = sedimentDepth(x, z, accessor);

                for (int d = 0; d <= sedimentLayerDepth; d++) {
                    final int y = surface_height - d;
                    final int stoneDepthBellow = y - s + 1;
                    materialRuleContext.updateY(stoneDepthAbove, -Integer.MAX_VALUE, fluid_height, x, y, z);
                    BlockState blockState2 = blockStateRule.tryApply(x, y, z);
                    if (blockState2 != null) {
                        blockColumn.setBlock(y, blockState2);
                    }
                }
                for (int y = surface_height; y >= bottomY; --y) {}

                //                if (registryEntry.matchesKey(BiomeKeys.FROZEN_OCEAN) ||
                // registryEntry.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN)) {
                //                    this.placeIceberg(materialRuleContext.estimateSurfaceHeight(),
                // (Biome)registryEntry.value(), blockColumn, mutable2, xx, n, o);
                //                }
            }
        }
    }

    public static class SurfaceBuilderBlockCollum implements BlockColumn {

        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos mutable;
        private final ChunkPos chunkPos;

        public SurfaceBuilderBlockCollum(ChunkAccess chunk, BlockPos.MutableBlockPos mutable, ChunkPos chunkPos) {
            this.chunk = chunk;
            this.mutable = mutable;
            this.chunkPos = chunkPos;
        }

        @Override
        public void setBlock(int y, BlockState state) {
            LevelHeightAccessor heightLimitView = chunk.getHeightAccessorForGeneration();
            if (y >= heightLimitView.getMinBuildHeight() && y < heightLimitView.getMaxBuildHeight()) {
                chunk.setBlockState(mutable.setY(y), state, false);
                if (!state.getFluidState().isEmpty()) {
                    chunk.markPosForPostprocessing(mutable);
                }
            }
        }

        @Override
        public BlockState getBlock(int y) {
            return chunk.getBlockState(mutable.setY(y));
        }

        public String toString() {
            return "ChunkBlockColumn " + chunkPos;
        }
    }

    public void buildSurface(
            RandomState noiseConfig,
            BiomeManager biomeAccess,
            Registry<Biome> biomeRegistry,
            boolean useLegacyRandom,
            WorldGenerationContext heightContext,
            final ChunkAccess chunk,
            NoiseChunk chunkNoiseSampler,
            SurfaceRules.RuleSource materialRule) {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getMinBlockX();
        int startZ = chunkPos.getMinBlockZ();
        BlockColumn blockColumn = new SurfaceBuilderBlockCollum(chunk, mutable, chunkPos);
        Objects.requireNonNull(biomeAccess);
        SurfaceRules.Context materialRuleContext = MaterialRuleContextAccessor.createMaterialRuleContext(
                this, noiseConfig, chunk, chunkNoiseSampler, biomeAccess::getBiome, biomeRegistry, heightContext);
        SurfaceRules.SurfaceRule blockStateRule = (SurfaceRules.SurfaceRule) materialRule.apply(materialRuleContext);
        BlockPos.MutableBlockPos mutable2 = new BlockPos.MutableBlockPos();

        // Debug.debugMixin(materialRuleContext);

        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                final int x = startX + dx;
                final int z = startZ + dz;
                final int surface_height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz) + 1;
                mutable.setX(x).setZ(z);
                Holder<Biome> registryEntry = biomeAccess.getBiome(mutable2.set(x, 0, z));
                //                if (registryEntry.matchesKey(BiomeKeys.ERODED_BADLANDS)) {
                //                    this.placeBadlandsPillar(blockColumn, xx, n, o, chunk);
                //                }
                materialRuleContext.updateXZ(x, z);
                int stoneDepthAbove = 0;
                int fluid_height = Integer.MIN_VALUE;
                int s = Integer.MAX_VALUE;
                int bottomY = chunk.getMinBuildHeight();
                // final int sedimentLayerDepth = sedimentDepth(x, z, 10, -1, 4);

                //                for (int d = 0; d <= sedimentLayerDepth; d++) {
                //                    final int y = surface_height-d;
                //                    final int stoneDepthBellow = y - s + 1;
                //                    materialRuleContext.updateY(stoneDepthAbove, -Integer.MAX_VALUE, fluid_height, x,
                // y, z);
                //                    BlockState blockState2 = blockStateRule.tryApply(x, y, z);
                //                    if (blockState2 != null) {
                //                        blockColumn.setBlock(y, blockState2);
                //                    }
                //                }
                for (int y = surface_height; y >= bottomY; --y) {}

                //                if (registryEntry.matchesKey(BiomeKeys.FROZEN_OCEAN) ||
                // registryEntry.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN)) {
                //                    this.placeIceberg(materialRuleContext.estimateSurfaceHeight(),
                // (Biome)registryEntry.value(), blockColumn, mutable2, xx, n, o);
                //                }
            }
        }
    }

    //    private void replaceBlock(BlockColumn blockColumn, int y, int stoneDepthAbove , int fluid_height , int s , int
    // bottomY, ) {
    //        BlockState blockState = blockColumn.getBlock(y);
    //        if (blockState.isAir()) {
    //            stoneDepthAbove = 0;
    //            fluid_height = Integer.MIN_VALUE;
    //        } else if (!blockState.getFluidState().isEmpty()) {
    //            if (fluid_height == Integer.MIN_VALUE) {
    //                fluid_height = y + 1;
    //            }
    //        } else {
    //            if (s >= y) {
    //                s = DimensionType.WAY_BELOW_MIN_Y;
    //
    //                for (int v = y - 1; v >= bottomY - 1; --v) {
    //                    BlockState blockState2 = blockColumn.getBlock(v);
    //                    if (!this.isStone(blockState2)) {
    //                        s = v + 1;
    //                        break;
    //                    }
    //                }
    //            }
    //
    //            ++stoneDepthAbove;
    //            final int stoneDepthBellow = y - s + 1;
    //            materialRuleContext.updateY(stoneDepthAbove, stoneDepthBellow, fluid_height, x, y, z);
    //            if (blockState == this.defaultState) {
    //                BlockState blockState2 = blockStateRule.tryApply(x, y, z);
    //                if (blockState2 != null) {
    //                    blockColumn.setBlock(y, blockState2);
    //                }
    //            }
    //        }
    //    }

    protected int getSurfaceDepth(int blockX, int blockZ) {
        double d = this.surfaceNoise.getValue(blockX, 0.0F, blockZ);
        return (int) (d * (double) 2.75F
                + (double) 3.0F
                + this.randomDeriver.at(blockX, 0, blockZ).nextDouble() * (double) 0.25F);
    }

    protected double getSurfaceSecondary(int blockX, int blockZ) {
        return this.surfaceSecondaryNoise.getValue((double) blockX, (double) 0.0F, (double) blockZ);
    }

    // nem ar nem agua / lava
    private boolean isStone(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
