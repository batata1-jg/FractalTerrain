package me.batata_1.fractalterrain.world.gen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import me.batata_1.fractalterrain.math.Interpolation;
import me.batata_1.fractalterrain.math.Spline;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.world.gen.RockStrata;
import me.batata_1.fractalterrain.noise.PhacelleNoiseSampler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;

import static me.batata_1.fractalterrain.FractalTerrainInstance.reliefSource;

// TODO: add compat , consertar biomes, reescrever isso

public final class FractalTerrainChunkGenerator extends ChunkGenerator {

    public record Settings(
            float scale, int seaLevel, int bottomY, int topY)
            implements SettingsRegistry.Settings {

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("scale", 1F).forGetter(Settings::scale),
                        Codec.INT.optionalFieldOf("sea_level", 63).forGetter(Settings::seaLevel),
                        Codec.INT.optionalFieldOf("bottom_y", -64).forGetter(Settings::bottomY),
                        Codec.INT.optionalFieldOf("top_y", 1600).forGetter(Settings::topY))
                .apply(instance, Settings::new));

        public static final Codec<RegistryEntry<Settings>> REGISTRY_CODEC =
                RegistryElementCodec.of(FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS, CODEC);
    }

    private static final BlockState DEFAUT = Blocks.STONE.getDefaultState();
    private static final BlockState[] Rocks = new BlockState[] {
        Blocks.STONE.getDefaultState(),
        Blocks.DIORITE.getDefaultState(),
        Blocks.ANDESITE.getDefaultState(),
        Blocks.GRANITE.getDefaultState()
    };


    private final RegistryEntry<Settings> settings;
    private final Interpolation reliefInterpolation;
    private final Interpolation reliefGradInterpolation;
    private final Interpolation reliefGradXInterpolation;
    private final Interpolation reliefGradYInterpolation;
    private final Interpolation reliefResInterpolation;
    private final Interpolation reliefBlurredInterpolation;
    private final Interpolation reliefLowFreqInterpolation;
    private final RockStrata strata;
    private final PhacelleNoiseSampler phacelleSampler;

    public static final Codec<FractalTerrainChunkGenerator> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                            Settings.REGISTRY_CODEC
                                    .fieldOf("settings")
                                    .forGetter((FractalTerrainChunkGenerator g) -> g.settings))
                    .apply(instance, FractalTerrainChunkGenerator::new));

    public FractalTerrainChunkGenerator(BiomeSource biomeSource, RegistryEntry<Settings> settings) {
        super(biomeSource);
        this.settings = settings;
        reliefInterpolation = new Interpolation(settings.value().scale());
        reliefGradInterpolation = new Interpolation(settings.value().scale());
        reliefResInterpolation = new Interpolation(settings.value().scale());
        reliefBlurredInterpolation = new Interpolation(settings.value().scale());
        reliefGradXInterpolation = new Interpolation(settings.value().scale());
        reliefGradYInterpolation = new Interpolation(settings.value().scale());
        // TODO: implementar isso direito ou ser mais inteligente e descobri qual dos caras la eu tenho q usar
        reliefLowFreqInterpolation = new Interpolation(settings.value().scale() * (1 << 6));
        initReliefRelatedInterpolation();
        strata = RockStrata.AngledPlaneStrata.create(9, 8, Rocks);
        phacelleSampler = new PhacelleNoiseSampler(5, 32F);
        phacelleInterp.setF(xz -> {
            return phacelleSampler.sample(xz.getFirst(), xz.getSecond());
        });
    }

    private void initReliefRelatedInterpolation() {
        reliefInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getElev(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        reliefGradInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getRefinedGrad(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        reliefGradXInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getGradX(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        reliefGradYInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getGradY(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        reliefResInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getRes(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        reliefBlurredInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getBlurredElev(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public RegistryEntry<Settings> getSettings() {
        return settings;
    }

    // shouldn't depende on the getBaseHeight
    private BlockState fillRocks(int x, int z, int y) {
        return DEFAUT;
    }

    private static final Spline phacelleSpline =
            new Spline(new float[] {4, 8, 40}, new float[] {0, 0.25F, 1}, new float[] {0, 0, 0});

    private final Interpolation phacelleInterp = new Interpolation(1F);

    private int getBaseHeight(int x, int z) {
        final double interpolatedBlurredRelief = reliefBlurredInterpolation.interpolateBilinear(x, z);
        final double interpolatedRelief = reliefInterpolation.interpolateSmoothStep(x, z);
        final double interpolatedGrad = reliefGradInterpolation.interpolateSmoothStep(x, z);
        final double strata = this.strata.sample(x, z, interpolatedRelief, interpolatedGrad, interpolatedBlurredRelief);
        if( interpolatedBlurredRelief < -50 ) return -50;
        return (int) interpolatedRelief;
    }

    private int[] getBaseHeightArr(final int startX, final int startZ) {
        final int[] heights = new int[1 << 8];
        final int seaLevel = +settings.value().seaLevel() - 1;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                heights[(dx << 4) + dz] = getBaseHeight(startX + dx, startZ + dz) + seaLevel;
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
        return CompletableFuture.supplyAsync(() -> this.populateNoise(chunk), executor);
    }

    private Chunk populateNoise(final Chunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();
        final int startingX = chunkPos.getStartX();
        final int startingZ = chunkPos.getStartZ();
        final int bottom = settings.value().bottomY();
        final int[] reliefBaseHeight = getBaseHeightArr(startingX, startingZ);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int curTopLayer = strata.getCurLayer(
                        new double[] {startingX + dx, startingZ + dz}, reliefBaseHeight[(dx << 4) + dz]);
                for (int y = bottom; y <= reliefBaseHeight[(dx << 4) + dz]; y++) {

                    //             chunk.setBlockState(new BlockPos(startingX + dx, y, startingZ + dz),
                    // strata.getStrataBlock(curTopLayer + (int)
                    // Math.floor((y-reliefBaseHeight[(dx<<4)+dz])/strata.getSpacing())), false);
                    chunk.setBlockState(
                            new BlockPos(startingX + dx, y, startingZ + dz),
                            fillRocks(startingX + dx, startingZ + dz, y),
                            false);
                }
            }
        }
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                this.buildSurface(startingX + dx, startingZ + dz, chunk, dx, dz, reliefBaseHeight);
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

    private int quantize(final float baseValue, final int steps) {
        return (int) (Math.floor(baseValue * steps + 0.5));
    }

    private int sedimentDepth(final int x, final int z, final int maxDepth, final int minDepth, final float fallOf) {
        final float grad = (float) (reliefGradInterpolation.interpolateBilinear(x, z));
        final float normDepth = 1 / (1 + grad * grad / fallOf);
        return quantize(normDepth, maxDepth - minDepth) + minDepth;
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
        final int idx = (int) (Math.floor(
                (Math.tanh(reliefResInterpolation.interpolateBilinear(x, z) / 15.0) * 0.5 + 0.5) * colors + 0.5));
        return terrainGradient[idx];
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {}

    @Override
    public void populateEntities(ChunkRegion region) {}

    @Override
    public int getWorldHeight() {
        return settings.value().topY();
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinimumY() {
        return settings.value().bottomY();
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return 0;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        BlockState[] blockStates =
                // can be negative
                new BlockState[getBaseHeight(x, z) - settings.value().bottomY()];
        Arrays.fill(blockStates, DEFAUT);
        return new VerticalBlockSample(settings.value().bottomY(), blockStates);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {}
}
