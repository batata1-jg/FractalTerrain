package me.batata_1.fractal_terrain.world.gen.surfacebuilder;

import static me.batata_1.fractal_terrain.debug.Debug.getLogger;

import java.util.Objects;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.mixin.MaterialRuleContextAccessor;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap;
import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import me.batata_1.fractal_terrain.world.biome.parameters.Continentalness;
import me.batata_1.fractal_terrain.world.biome.parameters.ErosionLevel;
import me.batata_1.fractal_terrain.world.biome.parameters.HumidityLevel;
import me.batata_1.fractal_terrain.world.biome.parameters.PeaksValleys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
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

    private float humidityBasedFalloff(int x, int z, FractalTerrainHeightmap h) {
        float humidity = h.get(Types.VEGETATION, x, z);
        return (Math.clamp(humidity, -1, 1) + 1) * 10;
    }

    private int sedimentDepth(final int x, final int z, FractalTerrainHeightmap h) {
        final float grad = h.get(Types.REFINED_GRAD, x, z);
        final float normDepth = (float) (1 / (1 + grad * grad / Math.pow(humidityBasedFalloff(x, z, h), 2)));
        float depth = quantize(normDepth, maxDepth - minDepth) + minDepth;
        // float depth = normDepth;
        depth = highErosionPvFactor(depth, x, z, h);
        depth = correctHighHumidity(depth, x, z, h);
        return (int) depth;
    }

    private float correctHighHumidity(float depth, int x, int z, FractalTerrainHeightmap h) {
        float humidity = h.get(Types.VEGETATION, x, z);
        if (HumidityLevel.of(humidity).equals(HumidityLevel.LEVEL_4)) return Math.max(depth, 1);
        return depth;
    }

    private float highErosionPvFactor(float depth, int x, int z, FractalTerrainHeightmap h) {
        ErosionLevel erosion = ErosionLevel.of(h.get(Types.EROSION, x, z));
        Continentalness c = Continentalness.of(h.get(Types.CONTINENTALNESS, x, z));
        PeaksValleys pv = PeaksValleys.of(h.get(Types.WEIRDNESS, x, z));
        if (erosion == ErosionLevel.LEVEL_0 || erosion == ErosionLevel.LEVEL_1) {
            switch (pv) {
                case PEAKS -> {
                    return 50;
                }
                case HIGH -> {
                    if (erosion == ErosionLevel.LEVEL_0) return 50;
                }
                default -> {
                    if (c.equals(Continentalness.COAST)) return 50;
                }
            }
        }
        return depth;
    }

    public void buildSurface(
            RandomState noiseConfig,
            BiomeManager biomeAccess,
            Registry<Biome> biomeRegistry,
            WorldGenerationContext heightContext,
            final ChunkAccess chunk,
            NoiseChunk chunkNoiseSampler) {
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final ChunkPos chunkPos = chunk.getPos();
        final FractalTerrainHeightmap heightmaps =
                FractalTerrainInstance.getHeightmapCache().getOrCompute(chunkPos);
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
                int relief_height = (int) heightmaps.get(Types.ELEVATION, dx, dz);
                int stoneDepthAbove;
                int stoneDepthBellow;
                int fluid_height;
                final int sedimentLayerDepth = sedimentDepth(dx, dz, heightmaps);

                for (int d = 0; d <= sedimentLayerDepth; d++) {
                    final int y = relief_height - d;
                    stoneDepthAbove = d + 1;
                    stoneDepthBellow = relief_height + 128 - d;
                    // 64
                    final int seaH = 2 * seaLevel - relief_height;
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
}
