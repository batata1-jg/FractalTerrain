package me.batata_1.fractal_terrain.world.gen.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.spline.Spline;
import me.batata_1.fractal_terrain.relief.PopulateNoiseStep;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.slf4j.Logger;

public final class FractalTerrainChunkGenerator extends ChunkGenerator {

    private static final Logger LOG = Debug.getLogger(FractalTerrainChunkGenerator.class);
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState WATER = Blocks.WATER.getDefaultState();
    private static final BlockState DEFAUT = Blocks.STONE.getDefaultState();

    private final RegistryEntry<ChunkGeneratorSettings> settings;
    private final FractalTerrainBiomeSource biomeSource;
    private final PopulateNoiseStep populateNoiseStep;
    private final Supplier<AquiferSampler.FluidLevelSampler> fluidLevelSampler;

    public static final Codec<FractalTerrainChunkGenerator> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            FractalTerrainBiomeSource.CODEC // Use your biome source's CODEC
                                    .fieldOf("biome_source")
                                    .forGetter(FractalTerrainChunkGenerator::getBiomeSource),
                            ChunkGeneratorSettings.REGISTRY_CODEC
                                    .fieldOf("settings")
                                    .forGetter(FractalTerrainChunkGenerator::getSettings))
                    .apply(instance, FractalTerrainChunkGenerator::new));

    public FractalTerrainChunkGenerator(
            FractalTerrainBiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.settings = settings;
        this.populateNoiseStep = new PopulateNoiseStep(1.0F);
        this.fluidLevelSampler = Suppliers.memoize(() -> createFluidLevelSampler(settings.value()));
    }

    private static AquiferSampler.FluidLevelSampler createFluidLevelSampler(ChunkGeneratorSettings settings) {
        AquiferSampler.FluidLevel fluidLevel = new AquiferSampler.FluidLevel(-54, Blocks.LAVA.getDefaultState());
        int i = settings.seaLevel();
        AquiferSampler.FluidLevel fluidLevel2 = new AquiferSampler.FluidLevel(i, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, i) ? fluidLevel : fluidLevel2;
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public RegistryEntry<ChunkGeneratorSettings> getSettings() {
        return settings;
    }

    public FractalTerrainBiomeSource getBiomeSource() {
        return biomeSource;
    }

    private static final Spline phacelleSpline =
            new Spline(new float[] {4, 8, 40}, new float[] {0, 0.25F, 1}, new float[] {0, 0, 0});

    private int[] getBaseHeightArr(final int startX, final int startZ) {
        final int[] heights = new int[1 << 8];
        final int seaLevel = settings.value().seaLevel() - 1;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                heights[(dx << 4) + dz] = Math.max(
                                populateNoiseStep.getHeight(startX + dx, startZ + dz),
                                settings.value().generationShapeConfig().minimumY())
                        + seaLevel;
            }
        }
        return heights;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(
            Executor executor,
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk) {
        GenerationShapeConfig generationShapeConfig =
                this.settings.value().generationShapeConfig().trimHeight(chunk.getHeightLimitView());
        int k = MathHelper.floorDiv(generationShapeConfig.height(), generationShapeConfig.verticalSize());
        if (k <= 0) {
            return CompletableFuture.completedFuture(chunk);
        }
        return CompletableFuture.supplyAsync(
                Util.debugSupplier(() -> this.populateNoise(chunk), () -> "fractal_terrain_chunk_generator"), executor);
    }

    private Chunk populateNoise(final Chunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();
        final int startingX = chunkPos.getStartX();
        final int startingZ = chunkPos.getStartZ();
        final int seaLevel = settings.value().seaLevel() - 1;
        final int bottom = settings.value().generationShapeConfig().minimumY();
        populateNoiseStep.ensureTilesForChunk(startingX, startingZ);
        final int[] reliefBaseHeight = getBaseHeightArr(startingX, startingZ);
        final Heightmap oceanHeightmap = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        final Heightmap surfaceHeightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        final BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int xx = startingX + dx;
                final int zz = startingZ + dz;
                final int reliefHeight = reliefBaseHeight[(dx << 4) + dz];
                final int aboveWaterHeight = Math.max(reliefHeight, seaLevel);
                mutable.set(xx, bottom, zz);
                BlockState state;
                for (int y = bottom; y <= aboveWaterHeight; y++) {
                    mutable.setY(y);

                    if (reliefHeight < y) {
                        state = WATER;
                    } else {
                        state = populateNoiseStep.fillRocks(xx, y, zz);
                    }

                    chunk.setBlockState(mutable, state, false);
                    surfaceHeightmap.trackUpdate(xx & 0xF, y, zz & 0xF, state);
                    oceanHeightmap.trackUpdate(xx & 0xF, y, zz & 0xF, state);
                    mutable.set(xx, y, zz);
                    chunk.markBlockForPostProcessing(mutable);
                }
            }
        }
        return chunk;
    }

    @Override
    public void carve(
            ChunkRegion chunkRegion,
            long seed,
            NoiseConfig noiseConfig,
            BiomeAccess biomeAccess,
            StructureAccessor structureAccessor,
            Chunk chunk,
            GenerationStep.Carver carverStep) {}

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
        if (!SharedConstants.isOutsideGenerationArea(chunk.getPos())) {
            HeightContext heightContext = new HeightContext(this, region);
            this.buildSurface(
                    chunk,
                    heightContext,
                    noiseConfig,
                    structures,
                    region.getBiomeAccess(),
                    region.getRegistryManager().get(RegistryKeys.BIOME),
                    Blender.getBlender(region));
        }
    }

    @VisibleForTesting
    public void buildSurface(
            Chunk chunk,
            HeightContext heightContext,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            BiomeAccess biomeAccess,
            Registry<Biome> biomeRegistry,
            Blender blender) {
        ChunkNoiseSampler chunkNoiseSampler =
                chunk.getOrCreateChunkNoiseSampler((chunkx) -> this.createChunkNoiseSampler(
                        chunkx, structureAccessor, blender, FractalTerrainInstance.getNoiseConfig()));
        if (chunk.getPos().z == 0 && chunk.getPos().x == 0) {
            LOG.info(" chunkNoise sampler of 0 ,0 : {}", chunkNoiseSampler);
            LOG.info(" chunkNoise sampler estiate H : {}", chunkNoiseSampler.estimateSurfaceHeight(0, 0));
        }
//        FractalTerrainInstance.getSurfaceBuilder()
//                .buildSurface(noiseConfig, biomeAccess, biomeRegistry, heightContext, chunk, chunkNoiseSampler);
    }

    private ChunkNoiseSampler createChunkNoiseSampler(
            Chunk chunk, StructureAccessor world, Blender blender, NoiseConfig noiseConfig) {
        return ChunkNoiseSampler.create(
                chunk,
                noiseConfig,
                StructureWeightSampler.createStructureWeightSampler(world, chunk.getPos()),
                this.settings.value(),
                this.fluidLevelSampler.get(),
                blender);
    }

    @Override
    public void populateEntities(ChunkRegion region) {}

    @Override
    public int getWorldHeight() {
        return settings.value().generationShapeConfig().height();
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinimumY() {
        return settings.value().generationShapeConfig().minimumY();
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return Math.max(
                populateNoiseStep.getHeight(x, z),
                settings.value().generationShapeConfig().minimumY());
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] blockStates =
                // can be negative
                new BlockState
                        [Math.max(
                                        populateNoiseStep.getHeight(x, z),
                                        settings.value().generationShapeConfig().minimumY())
                                - settings.value().generationShapeConfig().minimumY()];
        Arrays.fill(blockStates, DEFAUT);
        return new VerticalBlockSample(settings.value().generationShapeConfig().minimumY(), blockStates);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {}
}
