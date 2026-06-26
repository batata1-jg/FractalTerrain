package me.batata_1.fractal_terrain.world.gen.surfacebuilder;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.Objects;

import me.batata_1.fractal_terrain.mixin.MaterialRuleContextAccessor;
import me.batata_1.fractal_terrain.relief.ReliefAccessor;
import me.batata_1.fractal_terrain.world.biome.Continentalness;
import me.batata_1.fractal_terrain.world.biome.ErosionLevel;
import me.batata_1.fractal_terrain.world.biome.PeaksValleys;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class FractalTerrainSurfaceSystem extends SurfaceSystem {

    private static final Logger LOG = getLogger(FractalTerrainSurfaceSystem.class);

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
    private final float fallOf = 25;
    private final int minDepth = -1;
    private final int maxDepth = 10;

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

    private final ThreadLocal<Integer> mutableDepth = ThreadLocal.withInitial(()-> 0);
    private int sedimentDepth(final int x, final int z, ReliefAccessor accessor) {
        final float grad = (float) (accessor.reliefGradInterpolation().interpolateBilinear(x, z));
        final float normDepth = 1 / (1 + grad * grad / fallOf);
        mutableDepth.set(quantize(normDepth, maxDepth - minDepth) + minDepth);
        highErosionPvFactor(x,z,accessor);
        return mutableDepth.get();
    }

    private void highErosionPvFactor(int x, int z, ReliefAccessor accessor) {
        int erosionLvl = ErosionLevel.erosionLevel((float) accessor.erosion().interpolateBilinear(x,z));
        Continentalness c = Continentalness.of((float) accessor.continentalness().interpolateBilinear(x,z));
        PeaksValleys pv = PeaksValleys.of((float) accessor.erosion().interpolateBilinear(x,z));
        if(erosionLvl==0||erosionLvl==1) {
            switch (pv) {
                case PEAKS -> mutableDepth.set(50);
                case HIGH -> {
                    if(erosionLvl==0) mutableDepth.set(50);
                }
                default -> {
                    if(c.equals(Continentalness.COAST)) mutableDepth.set(50);
                }
            }
        }
    }

    static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    private BlockState sedimentStrata(int x, int z, int distFromSurface, int surfaceHeight) {
        if (distFromSurface == surfaceHeight) return GRASS_BLOCK;
        return DIRT;
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
        final int bottomY = chunk.getMinBuildHeight();

        for (int dx = 0; dx < 16; ++dx) {
            for (int dz = 0; dz < 16; ++dz) {
                final int x = startX + dx;
                final int z = startZ + dz;
                mutable.setX(x).setZ(z);
                materialRuleContext.updateXZ(x, z);
                final int surface_height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz);
                int relief_height = 0; // terrain h without water
                for (int y = surface_height; y >= bottomY; y--)
                    if (blockColumn.getBlock(y).getFluidState().isEmpty()) {
                        relief_height = y;
                        break;
                    }
                //   if(x==328&&z==383) LOG.error("HEIGHT {}",relief_height);
                int stoneDepthAbove = -10;
                int stoneDepthBellow = 0;
                int fluid_height = Integer.MIN_VALUE;
                int s = Integer.MAX_VALUE;
                final int sedimentLayerDepth = sedimentDepth(x, z, accessor);

                for (int d = 0; d <= sedimentLayerDepth; d++) {
                    final int y = relief_height - d;
                    stoneDepthAbove = d;
                    stoneDepthBellow = relief_height + 128 - d;
                    final int seaH = seaLevel - relief_height + 61;
                    fluid_height = seaH;
                    materialRuleContext.updateY(stoneDepthAbove, stoneDepthBellow, fluid_height, x, y, z);
                    BlockState newBlockState = blockStateRule.tryApply(x, y, z);
                    if (newBlockState != null) {
                        blockColumn.setBlock(y, newBlockState);
                    }
                }
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
        public void setBlock(int y, @NotNull BlockState state) {
            LevelHeightAccessor heightLimitView = chunk.getHeightAccessorForGeneration();
            if (y >= heightLimitView.getMinBuildHeight() && y < heightLimitView.getMaxBuildHeight()) {
                chunk.setBlockState(mutable.setY(y), state, false);
                if (!state.getFluidState().isEmpty()) {
                    chunk.markPosForPostprocessing(mutable);
                }
            }
        }

        @Override
        public @NotNull BlockState getBlock(int y) {
            return chunk.getBlockState(mutable.setY(y));
        }

        public String toString() {
            return "ChunkBlockColumn " + chunkPos;
        }
    }

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
