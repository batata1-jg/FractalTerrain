package me.batata_1.fractal_terrain.world.gen.chunk;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.FractalTerrainInstance;
import me.batata_1.fractal_terrain.debug.Debug;
import me.batata_1.fractal_terrain.math.spline.Spline;
import me.batata_1.fractal_terrain.relief.ReliefAccessor;
import me.batata_1.fractal_terrain.world.biome.source.FractalTerrainBiomeSource;
import me.batata_1.fractal_terrain.world.gen.populatenoise.PopulateNoiseStep;
import net.minecraft.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;

public final class FractalTerrainChunkGenerator extends ChunkGenerator {

    private static final Logger LOG = Debug.getLogger(FractalTerrainChunkGenerator.class);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState DEFAUT = Blocks.STONE.defaultBlockState();

    private final Holder<NoiseGeneratorSettings> settings;
    private final FractalTerrainBiomeSource biomeSource;
    private final PopulateNoiseStep populateNoiseStep;
    private final Supplier<Aquifer.FluidPicker> fluidLevelSampler;

    public static final Codec<FractalTerrainChunkGenerator> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            FractalTerrainBiomeSource.CODEC // Use your biome source's CODEC
                                    .fieldOf("biome_source")
                                    .forGetter(FractalTerrainChunkGenerator::getBiomeSource),
                            NoiseGeneratorSettings.CODEC
                                    .fieldOf("settings")
                                    .forGetter(FractalTerrainChunkGenerator::getSettings))
                    .apply(instance, FractalTerrainChunkGenerator::new));

    public FractalTerrainChunkGenerator(
            FractalTerrainBiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource);
        this.biomeSource = biomeSource;
        this.settings = settings;
        this.populateNoiseStep = new PopulateNoiseStep(1.0F);
        this.fluidLevelSampler = Suppliers.memoize(() -> createFluidLevelSampler(settings.value()));
    }

    private static Aquifer.FluidPicker createFluidLevelSampler(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus fluidLevel = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = settings.seaLevel();
        Aquifer.FluidStatus fluidLevel2 = new Aquifer.FluidStatus(i, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, i) ? fluidLevel : fluidLevel2;
    }

    @Override
    protected @NotNull Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public Holder<NoiseGeneratorSettings> getSettings() {
        return settings;
    }

    public @NotNull FractalTerrainBiomeSource getBiomeSource() {
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
                                settings.value().noiseSettings().minY())
                        + seaLevel;
            }
        }
        return heights;
    }

    @Override
    public @NotNull CompletableFuture<ChunkAccess> fillFromNoise(
            @NotNull Executor executor,
            @NotNull Blender blender,
            @NotNull RandomState noiseConfig,
            @NotNull StructureManager structureAccessor,
            ChunkAccess chunk) {
        NoiseSettings generationShapeConfig =
                this.settings.value().noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int k = Mth.floorDiv(generationShapeConfig.height(), generationShapeConfig.noiseSizeVertical());
        if (k <= 0) {
            return CompletableFuture.completedFuture(chunk);
        }
        return CompletableFuture.supplyAsync(
                Util.name(() -> this.populateNoise(chunk), () -> "fractal_terrain_chunk_generator"), executor);
    }

    private ChunkAccess populateNoise(final ChunkAccess chunk) {
        final ChunkPos chunkPos = chunk.getPos();
        final int startingX = chunkPos.getMinBlockX();
        final int startingZ = chunkPos.getMinBlockZ();
        final int seaLevel = settings.value().seaLevel() - 1;
        final int bottom = settings.value().noiseSettings().minY();
        //  populateNoiseStep.ensureTilesForChunk(startingX, startingZ);
        final int[] reliefBaseHeight = getBaseHeightArr(startingX, startingZ);
        final Heightmap oceanHeightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        final Heightmap surfaceHeightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
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
                        state = populateNoiseStep.placeRiver(state, xx, reliefHeight - y, zz);
                    }

                    chunk.setBlockState(mutable, state, false);
                    surfaceHeightmap.update(xx & 0xF, y, zz & 0xF, state);
                    oceanHeightmap.update(xx & 0xF, y, zz & 0xF, state);
                    mutable.set(xx, y, zz);
                    chunk.markPosForPostprocessing(mutable);
                }
            }
        }
        return chunk;
    }

    @Override
    public void applyCarvers(
            @NotNull WorldGenRegion chunkRegion,
            long seed,
            @NotNull RandomState noiseConfig,
            @NotNull BiomeManager biomeAccess,
            @NotNull StructureManager structureAccessor,
            @NotNull ChunkAccess chunk,
            GenerationStep.@NotNull Carving carverStep) {}

    @Override
    public void buildSurface(
            @NotNull WorldGenRegion region,
            @NotNull StructureManager structures,
            @NotNull RandomState noiseConfig,
            @NotNull ChunkAccess chunk) {
        if (FractalTerrainConfig.DISABLE_SURFACE_STEP) return;
        if (!SharedConstants.debugVoidTerrain(chunk.getPos())) {
            WorldGenerationContext heightContext = new WorldGenerationContext(this, region);
            this.buildSurface(
                    this.populateNoiseStep.getReliefAccessor(),
                    chunk,
                    heightContext,
                    noiseConfig,
                    structures,
                    region.getBiomeManager(),
                    region.registryAccess().registryOrThrow(Registries.BIOME),
                    Blender.of(region));
        }
    }

    @VisibleForTesting
    public void buildSurface(
            ReliefAccessor accessor,
            ChunkAccess chunk,
            WorldGenerationContext heightContext,
            RandomState noiseConfig,
            StructureManager structureAccessor,
            BiomeManager biomeAccess,
            Registry<Biome> biomeRegistry,
            Blender blender) {
        NoiseChunk chunkNoiseSampler = chunk.getOrCreateNoiseChunk((chunkx) -> this.createChunkNoiseSampler(
                chunkx, structureAccessor, blender, FractalTerrainInstance.getNoiseConfig()));
        debugSurfaceAtChunk(0, 0, chunk, chunkNoiseSampler);
        FractalTerrainInstance.getSurfaceBuilder()
                .buildSurface(
                        accessor, noiseConfig, biomeAccess, biomeRegistry, heightContext, chunk, chunkNoiseSampler);
    }

    private NoiseChunk createChunkNoiseSampler(
            ChunkAccess chunk, StructureManager world, Blender blender, RandomState noiseConfig) {
        return NoiseChunk.forChunk(
                chunk,
                noiseConfig,
                Beardifier.forStructuresInChunk(world, chunk.getPos()),
                this.settings.value(),
                this.fluidLevelSampler.get(),
                blender);
    }

    @Override
    public void applyBiomeDecoration(
            @NotNull WorldGenLevel worldGenLevel,
            @NotNull ChunkAccess chunkAccess,
            @NotNull StructureManager structureManager) {
        if (FractalTerrainConfig.DISABLE_BIOME_DECORATION) return;
        ChunkPos chunkPos = chunkAccess.getPos();
        //  if(chunkPos.x!=(-499>>4)||chunkPos.z!=(428>>4)) return;

        SectionPos sectionPos = SectionPos.of(chunkPos, worldGenLevel.getMinSection());
        BlockPos blockPos = sectionPos.origin();
        Registry<Structure> structureRegistry = worldGenLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> indexedCollectedStructures = structureRegistry.stream()
                .collect(Collectors.groupingBy((structurex) -> structurex.step().ordinal()));
        List<FeatureSorter.StepFeatureData> featuresSteps = this.featuresPerStep.get();
        WorldgenRandom worldgenRandom =
                new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed =
                worldgenRandom.setDecorationSeed(worldGenLevel.getSeed(), blockPos.getX(), blockPos.getZ());
        Set<Holder<Biome>> set = new ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach((chunkPosx) -> {
            ChunkAccess chunkAccessFromPos = worldGenLevel.getChunk(chunkPosx.x, chunkPosx.z);

            for (LevelChunkSection levelChunkSection : chunkAccessFromPos.getSections()) {
                PalettedContainerRO<Holder<Biome>> biomeContainer = levelChunkSection.getBiomes();
                Objects.requireNonNull(set);
                biomeContainer.getAll(set::add);
            }
        });
        set.retainAll(this.biomeSource.possibleBiomes());
        int numFeatureSteps = featuresSteps.size();

        try {
            Registry<PlacedFeature> placedFeatureRegistry =
                    worldGenLevel.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
            int clampedNumFeatureSteps = Math.max(GenerationStep.Decoration.values().length, numFeatureSteps);

            for (int step = 0; step < clampedNumFeatureSteps; ++step) {
                int structureLocalSeed = 0;

                // structure generation
                if (structureManager.shouldGenerateStructures()) {
                    for (Structure structure : indexedCollectedStructures.getOrDefault(step, Collections.emptyList())) {
                        worldgenRandom.setFeatureSeed(decorationSeed, structureLocalSeed, step);
                        Supplier<String> supplier = () -> {
                            Optional<String> structureKey =
                                    structureRegistry.getResourceKey(structure).map(Object::toString);
                            Objects.requireNonNull(structure);
                            return (String) structureKey.orElseGet(structure::toString);
                        };

                        try {
                            worldGenLevel.setCurrentlyGenerating(supplier);
                            structureManager
                                    .startsForStructure(sectionPos, structure)
                                    .forEach((structureStart) -> structureStart.placeInChunk(
                                            worldGenLevel,
                                            structureManager,
                                            this,
                                            worldgenRandom,
                                            getWritableArea(chunkAccess),
                                            chunkPos));
                        } catch (Exception exception) {
                            CrashReport crashReport = CrashReport.forThrowable(exception, "Feature placement");
                            CrashReportCategory var10000 = crashReport.addCategory("Feature");
                            Objects.requireNonNull(supplier);
                            var10000.setDetail("Description", supplier::get);
                            throw new ReportedException(crashReport);
                        }

                        ++structureLocalSeed;
                    }
                }

                // indexes less than
                if (step < numFeatureSteps) {
                    IntSet featureIndexesSet = new IntArraySet();
                    // for this step, creates a set with all the possible features in this step from all the biomes
                    for (Holder<Biome> holder : set) {
                        List<HolderSet<PlacedFeature>> biomeFeatureSteps =
                                (this.generationSettingsGetter.apply(holder)).features();
                        if (step < biomeFeatureSteps.size()) {
                            // features in that step
                            HolderSet<PlacedFeature> holderSet = biomeFeatureSteps.get(step);
                            FeatureSorter.StepFeatureData stepFeatureData = featuresSteps.get(step);
                            holderSet.stream()
                                    .map(Holder::value)
                                    .forEach((placedFeaturex) -> featureIndexesSet.add(
                                            stepFeatureData.indexMapping().applyAsInt(placedFeaturex)));
                        }
                    }

                    int numFeatures = featureIndexesSet.size();
                    int[] featureIndexes = featureIndexesSet.toIntArray();
                    Arrays.sort(featureIndexes);
                    FeatureSorter.StepFeatureData features = featuresSteps.get(step);

                    for (int i = 0; i < numFeatures; ++i) {
                        int featureId = featureIndexes[i];
                        PlacedFeature placedFeature = features.features().get(featureId);

                        Supplier<String> featureSupplier = () -> {
                            Optional<String> featureKey = placedFeatureRegistry
                                    .getResourceKey(placedFeature)
                                    .map(Object::toString);
                            Objects.requireNonNull(placedFeature);
                            return (String) featureKey.orElseGet(placedFeature::toString);
                        };
                        worldgenRandom.setFeatureSeed(decorationSeed, featureId, step);

                        try {
                            worldGenLevel.setCurrentlyGenerating(featureSupplier);
                            placedFeature.placeWithBiomeCheck(worldGenLevel, this, worldgenRandom, blockPos);
                        } catch (Exception exception2) {
                            CrashReport crashReport2 = CrashReport.forThrowable(exception2, "Feature placement");
                            CrashReportCategory var43 = crashReport2.addCategory("Feature");
                            Objects.requireNonNull(featureSupplier);
                            var43.setDetail("Description", featureSupplier::get);
                            throw new ReportedException(crashReport2);
                        }
                    }
                }
            }

            worldGenLevel.setCurrentlyGenerating(null);
        } catch (Exception e) {
            CrashReport crashReport = CrashReport.forThrowable(e, "Biome decoration");
            crashReport
                    .addCategory("Generation")
                    .setDetail("CenterX", chunkPos.x)
                    .setDetail("CenterZ", chunkPos.z)
                    .setDetail("Seed", decorationSeed);
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public void spawnOriginalMobs(@NotNull WorldGenRegion region) {}

    @Override
    public int getGenDepth() {
        return settings.value().noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return settings.value().noiseSettings().minY();
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.@NotNull Types heightmap,
            @NotNull LevelHeightAccessor world,
            @NotNull RandomState noiseConfig) {
        return Math.max(
                populateNoiseStep.getHeight(x, z),
                settings.value().noiseSettings().minY());
    }

    @Override
    public @NotNull NoiseColumn getBaseColumn(
            int x, int z, @NotNull LevelHeightAccessor world, @NotNull RandomState noiseConfig) {
        BlockState[] blockStates =
                // can be negative
                new BlockState
                        [Math.max(
                                        populateNoiseStep.getHeight(x, z),
                                        settings.value().noiseSettings().minY())
                                - settings.value().noiseSettings().minY()];
        Arrays.fill(blockStates, DEFAUT);
        return new NoiseColumn(settings.value().noiseSettings().minY(), blockStates);
    }

    @Override
    public void addDebugScreenInfo(
            @NotNull List<String> text, @NotNull RandomState noiseConfig, @NotNull BlockPos pos) {}

    @TestOnly
    private static void debugSurfaceAtChunk(int x, int z, ChunkAccess chunk, NoiseChunk chunkNoiseSampler) {
        if (chunk.getPos().z == x && chunk.getPos().x == z) {
            LOG.info(" chunkNoise sampler of 0 ,0 : {}", chunkNoiseSampler);
            LOG.info(" chunkNoise sampler estiate H : {}", chunkNoiseSampler.preliminarySurfaceLevel(0, 0));
        }
    }
}
