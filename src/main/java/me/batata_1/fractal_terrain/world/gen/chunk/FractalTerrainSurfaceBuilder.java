package me.batata_1.fractal_terrain.world.gen.chunk;

import me.batata_1.fractal_terrain.mixin.MaterialRuleContextAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.util.math.random.RandomSplitter;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.BlockColumn;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseParametersKeys;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.slf4j.Logger;

import java.util.Objects;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

public class FractalTerrainSurfaceBuilder extends SurfaceBuilder {

    private static final Logger LOG = getLogger(FractalTerrainSurfaceBuilder.class);

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
        WHITE_TERRACOTTA = Blocks.WHITE_TERRACOTTA.getDefaultState();
        ORANGE_TERRACOTTA = Blocks.ORANGE_TERRACOTTA.getDefaultState();
        TERRACOTTA = Blocks.TERRACOTTA.getDefaultState();
        YELLOW_TERRACOTTA = Blocks.YELLOW_TERRACOTTA.getDefaultState();
        BROWN_TERRACOTTA = Blocks.BROWN_TERRACOTTA.getDefaultState();
        RED_TERRACOTTA = Blocks.RED_TERRACOTTA.getDefaultState();
        LIGHT_GRAY_TERRACOTTA = Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState();
        PACKED_ICE = Blocks.PACKED_ICE.getDefaultState();
        SNOW_BLOCK = Blocks.SNOW_BLOCK.getDefaultState();
    }

    private final BlockState defaultState;
    private final int seaLevel;
  //  private final BlockState[] terracottaBands;
    private final DoublePerlinNoiseSampler terracottaBandsOffsetNoise;
    private final DoublePerlinNoiseSampler badlandsPillarNoise;
    private final DoublePerlinNoiseSampler badlandsPillarRoofNoise;
    private final DoublePerlinNoiseSampler badlandsSurfaceNoise;
    private final DoublePerlinNoiseSampler icebergPillarNoise;
    private final DoublePerlinNoiseSampler icebergPillarRoofNoise;
    private final DoublePerlinNoiseSampler icebergSurfaceNoise;
    private final RandomSplitter randomDeriver;
    private final DoublePerlinNoiseSampler surfaceNoise;
    private final DoublePerlinNoiseSampler surfaceSecondaryNoise;
    private final MaterialRules.MaterialRule surfaceRules;

    public FractalTerrainSurfaceBuilder(NoiseConfig noiseConfig, BlockState defaultState, int seaLevel, RandomSplitter randomDeriver,MaterialRules.MaterialRule surfaceRules) {
        super(noiseConfig, defaultState, seaLevel, randomDeriver);
        this.defaultState = defaultState;
        this.seaLevel = seaLevel;
        this.randomDeriver = randomDeriver;
        this.terracottaBandsOffsetNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.CLAY_BANDS_OFFSET);
     //   this.terracottaBands = createTerracottaBands(randomDeriver.split(new Identifier("clay_bands")));
        this.surfaceNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.SURFACE);
        this.surfaceSecondaryNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.SURFACE_SECONDARY);
        this.badlandsPillarNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.BADLANDS_PILLAR);
        this.badlandsPillarRoofNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.BADLANDS_PILLAR_ROOF);
        this.badlandsSurfaceNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.BADLANDS_SURFACE);
        this.icebergPillarNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.ICEBERG_PILLAR);
        this.icebergPillarRoofNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.ICEBERG_PILLAR_ROOF);
        this.icebergSurfaceNoise = noiseConfig.getOrCreateSampler(NoiseParametersKeys.ICEBERG_SURFACE);
        LOG.info("surface rules: {}" , surfaceRules);
        this.surfaceRules = surfaceRules;
    }

    private int quantize(final float baseValue, final int steps) {
        return (int) (Math.floor(baseValue * steps + 0.5));
    }

    private int sedimentDepth(final int x, final int z, final int maxDepth, final int minDepth, final float fallOf) {
  //      final float grad = (float) (reliefGradInterpolation.interpolateBilinear(x, z));
  //      final float normDepth = 1 / (1 + grad * grad / fallOf);
  //      return quantize(normDepth, maxDepth - minDepth) + minDepth;
        return 0;
    }

    static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.getDefaultState();
    static final BlockState DIRT = Blocks.DIRT.getDefaultState();

    private BlockState sedimentStrata(int x, int z, int distFromSurface, int surfaceHeight) {
        if (distFromSurface == surfaceHeight) return GRASS_BLOCK;
        return DIRT;
    }


    private void buildSurface(
            final int x, final int z, final Chunk chunk, final int dx, final int dz, final int[] reliefBaseHeight) {
        final int surfaceHeight = reliefBaseHeight[((dx << 4) + dz)];
        final int sedimentLayerDepth = sedimentDepth(x, z, 10, -1, 4);
        for (int i = 0; i <= sedimentLayerDepth; i++) {
            chunk.setBlockState(
                    new BlockPos(x, surfaceHeight - i, z),
                    sedimentStrata(x, z, surfaceHeight - i, surfaceHeight),
                    false);
        }
    }

    private BlockState topLayer(final int x, final int z) {
        return applyTerrainGradient(x, z);
    }

    private static final BlockState[] terrainGradient = {
            Blocks.OBSIDIAN.getDefaultState(),
            Blocks.BLACKSTONE.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.SMOOTH_BASALT.getDefaultState(),
            Blocks.COBBLED_DEEPSLATE.getDefaultState(),
            Blocks.CYAN_TERRACOTTA.getDefaultState(),
            Blocks.DEEPSLATE.getDefaultState(),
            Blocks.TUFF.getDefaultState(),
            Blocks.COBBLESTONE.getDefaultState(),
            Blocks.STONE.getDefaultState(),
            Blocks.ANDESITE.getDefaultState(),
            Blocks.DIORITE.getDefaultState(),
            Blocks.CALCITE.getDefaultState(),
            Blocks.SNOW_BLOCK.getDefaultState()
    };

    public BlockState applyTerrainGradient(final int x, final int z) {
        final int colors = terrainGradient.length - 1;
//        final int idx = (int) (Math.floor(
//                (Math.tanh(reliefResInterpolation.interpolateBilinear(x, z) / 15.0) * 0.5 + 0.5) * colors + 0.5));
        return terrainGradient[0];
    }

    public static class SurfaceBuilderBlockCollum implements BlockColumn {

        private final Chunk chunk;
        private final BlockPos.Mutable mutable;
        private final ChunkPos chunkPos;

        public SurfaceBuilderBlockCollum(Chunk chunk, BlockPos.Mutable mutable, ChunkPos chunkPos) {
            this.chunk = chunk;
            this.mutable = mutable;
            this.chunkPos = chunkPos;
        }

        @Override
        public void setState(int y, BlockState state) {
            HeightLimitView heightLimitView = chunk.getHeightLimitView();
            if (y >= heightLimitView.getBottomY() && y < heightLimitView.getTopY()) {
                chunk.setBlockState(mutable.setY(y), state, false);
                if (!state.getFluidState().isEmpty()) {
                    chunk.markBlockForPostProcessing(mutable);
                }
            }

        }

        @Override
        public BlockState getState(int y) {
            return chunk.getBlockState(mutable.setY(y));
        }

        public String toString() {
            return "ChunkBlockColumn " + chunkPos;
        }

    }


    public void buildSurface(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry, HeightContext heightContext, final Chunk chunk, ChunkNoiseSampler chunkNoiseSampler) {
//        LOG.info("ta rodando isso?");
        this.buildSurface(noiseConfig,biomeAccess,biomeRegistry,false,heightContext,chunk,chunkNoiseSampler,surfaceRules);
//        final BlockPos.Mutable mutable = new BlockPos.Mutable();
//        final ChunkPos chunkPos = chunk.getPos();
//        int startX = chunkPos.getStartX();
//        int startZ = chunkPos.getStartZ();
//        BlockColumn blockColumn = new SurfaceBuilderBlockCollum( chunk, mutable, chunkPos);
//        Objects.requireNonNull(biomeAccess);

    }

    public void buildSurface(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry, boolean useLegacyRandom, HeightContext heightContext, final Chunk chunk, ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule) {
        final BlockPos.Mutable mutable = new BlockPos.Mutable();
        final ChunkPos chunkPos = chunk.getPos();
        int i = chunkPos.getStartX();
        int j = chunkPos.getStartZ();
        BlockColumn blockColumn = new BlockColumn(this, chunk, mutable, chunkPos) {
            public BlockState getState(int y) {
                return chunk.getBlockState(mutable.setY(y));
            }

            public void setState(int y, BlockState state) {
                HeightLimitView heightLimitView = chunk.getHeightLimitView();
                if (y >= heightLimitView.getBottomY() && y < heightLimitView.getTopY()) {
                    chunk.setBlockState(mutable.setY(y), state, false);
                    if (!state.getFluidState().isEmpty()) {
                        chunk.markBlockForPostProcessing(mutable);
                    }
                }

            }

            public String toString() {
                return "ChunkBlockColumn " + chunkPos;
            }
        };
        Objects.requireNonNull(biomeAccess);
        MaterialRules.MaterialRuleContext materialRuleContext = MaterialRuleContextAccessor.createMaterialRuleContext(this, noiseConfig, chunk, chunkNoiseSampler, biomeAccess::getBiome, biomeRegistry, heightContext);
        MaterialRules.BlockStateRule blockStateRule = (MaterialRules.BlockStateRule)materialRule.apply(materialRuleContext);
        BlockPos.Mutable mutable2 = new BlockPos.Mutable();

        for(int k = 0; k < 16; ++k) {
            for(int l = 0; l < 16; ++l) {
                int m = i + k;
                int n = j + l;
                int o = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, k, l) + 1;
                mutable.setX(m).setZ(n);
                RegistryEntry<Biome> registryEntry = biomeAccess.getBiome(mutable2.set(m, useLegacyRandom ? 0 : o, n));
//                if (registryEntry.matchesKey(BiomeKeys.ERODED_BADLANDS)) {
//                    this.placeBadlandsPillar(blockColumn, m, n, o, chunk);
//                }

                int p = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, k, l) + 1;
                materialRuleContext.initHorizontalContext(m, n);
                int q = 0;
                int r = Integer.MIN_VALUE;
                int s = Integer.MAX_VALUE;
                int t = chunk.getBottomY();

                for(int u = p; u >= t; --u) {
                    BlockState blockState = blockColumn.getState(u);
                    if (blockState.isAir()) {
                        q = 0;
                        r = Integer.MIN_VALUE;
                    } else if (!blockState.getFluidState().isEmpty()) {
                        if (r == Integer.MIN_VALUE) {
                            r = u + 1;
                        }
                    } else {
                        if (s >= u) {
                            s = DimensionType.field_35479;

                            for(int v = u - 1; v >= t - 1; --v) {
                                BlockState blockState2 = blockColumn.getState(v);
                                if (!this.isDefaultBlock(blockState2)) {
                                    s = v + 1;
                                    break;
                                }
                            }
                        }

                        ++q;
                        int v = u - s + 1;
                        materialRuleContext.initVerticalContext(q, v, r, m, u, n);
                        if (blockState == this.defaultState) {
                            BlockState blockState2 = blockStateRule.tryApply(m, u, n);
                            if (blockState2 != null) {
                                blockColumn.setState(u, blockState2);
                            }
                        }
                    }
                }

//                if (registryEntry.matchesKey(BiomeKeys.FROZEN_OCEAN) || registryEntry.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN)) {
//                    this.placeIceberg(materialRuleContext.estimateSurfaceHeight(), (Biome)registryEntry.value(), blockColumn, mutable2, m, n, o);
//                }
            }
        }

    }



    protected int sampleRunDepth(int blockX, int blockZ) {
        double d = this.surfaceNoise.sample(blockX, 0.0F, blockZ);
        return (int)(d * (double)2.75F + (double)3.0F + this.randomDeriver.split(blockX, 0, blockZ).nextDouble() * (double)0.25F);
    }

    protected double sampleSecondaryDepth(int blockX, int blockZ) {
        return this.surfaceSecondaryNoise.sample((double)blockX, (double)0.0F, (double)blockZ);
    }

    private boolean isDefaultBlock(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }


}
