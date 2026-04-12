package me.batata_1.fractalterrain.world.gen.chunk;

import static me.batata_1.fractalterrain.FractalTerrainInstance.reliefSource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import me.batata_1.fractalterrain.math.Interpolation;
import me.batata_1.fractalterrain.math.MaskedOps;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
import me.batata_1.fractalterrain.world.gen.RockStrata;
import me.batata_1.fractalterrain.world.gen.relief.PostProcessingRelief;
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

// TODO: add compat , consertar biomes, reescrever isso

public final class FractalTerrainChunkGenerator extends ChunkGenerator {

    public record Settings(
            RegistryEntry<PostProcessingRelief.Settings> postConfig, float scale, int seaLevel, int bottomY, int topY)
            implements SettingsRegistry.Settings {

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        PostProcessingRelief.Settings.REGISTRY_CODEC
                                .fieldOf("post_config")
                                .forGetter(Settings::postConfig),
                        Codec.FLOAT.optionalFieldOf("scale", 1F).forGetter(Settings::scale),
                        Codec.INT.optionalFieldOf("sea_level", 63).forGetter(Settings::seaLevel),
                        Codec.INT.optionalFieldOf("bottom_y", -64).forGetter(Settings::bottomY),
                        Codec.INT.optionalFieldOf("top_y", 1600).forGetter(Settings::topY))
                .apply(instance, Settings::new));

        public static final Codec<RegistryEntry<Settings>> REGISTRY_CODEC =
                RegistryElementCodec.of(FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS, CODEC);
    }

    private static final BlockState DEFAUT = Blocks.STONE.getDefaultState();

    private final RegistryEntry<Settings> settings;
    private final Interpolation reliefInterpolation;
    private final Interpolation reliefGradInterpolation;
    private final Interpolation reliefResInterpolation;
    private final Interpolation reliefLowFreqInterpolation;
    private final Interpolation strataInterpolation;
    private final RockStrata strata;
    private final float GRAD_NORM_CONST = 4;


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
        // TODO: implementar isso direito ou ser mais inteligente e descobri qual dos caras la eu tenho q usar
        reliefLowFreqInterpolation = new Interpolation(settings.value().scale() * (1 << 6));
        initReliefRelatedInterpolation();
        strataInterpolation = new Interpolation(settings.value().scale());
        strata = RockStrata.AngledPlaneStrata.create(7, 8);
        initStrataInterpolation();
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
        reliefResInterpolation.setF(xz -> {
            try {
                return reliefSource.get().getRes(xz);
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

    private int getBaseHeight(int x, int z) {
        final double interpolatedRelief = reliefInterpolation.interpolateSmoothStep(x, z);
        final double strata = computeBaseStrata(x,z,interpolatedRelief);
        return (int) strata;
    }

    private int[] getBaseHeightArr(final int startX , final int startZ) {
        final int[] heights = new int[1<<8];
        final int seaLevel = + settings.value().seaLevel() - 1;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                heights[(dx<<4)+dz] = getBaseHeight(startX + dx, startZ + dz) + seaLevel;
            }
        }
        return heights;
    }

    // TODO add more control to strata
    // TODO add smarter strata
    private void initStrataInterpolation() {
        strataInterpolation.setF(xz -> {
            try {
                final double h = reliefSource.get().getElev(xz);
                final double hStrat = strata.transformY(xz.getFirst(), xz.getSecond(), h);
                final double mask = Math.min(1.0, Math.exp(-reliefSource.get().getRefinedGrad(xz) / 1000.0));
                return (float) MaskedOps.DOUBLE.Add(h, hStrat, mask);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private double computeBaseStrata(int x, int z, double h) {
        final double hStrat = strata.transformY(x, z, h);
        final double mask = Math.min(1.0, Math.exp(-reliefGradInterpolation.interpolateSmoothStep(x, z) / 1000.0));
        return MaskedOps.DOUBLE.Add(h, hStrat, mask) * 0.5 + strataInterpolation.interpolateBilinear(x, z) * 0.5;
////        return MaskedOps.DOUBLE.Add(h,hStrat,mask) * 0.5 + reliefInterpolation.interpolateBilinear(x,z) + 0.5;
//        return MaskedOps.DOUBLE.Add(h,hStrat,mask);
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
        final int[] reliefBaseHeight = getBaseHeightArr(startingX,startingZ);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = bottom; y <= reliefBaseHeight[(dx<<4)+dz]; y++) {
                    chunk.setBlockState(new BlockPos(startingX + dx, y, startingZ + dz), DEFAUT, false);
                }
            }
        }
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                this.buildSurface(startingX + dx,startingZ + dz,chunk, dx,dz, reliefBaseHeight);
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

    private int quantize(final float baseValue , final int steps) {
        return (int) (Math.floor(baseValue*steps + 0.5));
    }

    private int sedimentDepth(final int x , final int z , final int maxDepth , final int minDepth , final float fallOf) {
        final float grad = (float) (reliefGradInterpolation.interpolateBilinear(x,z) / GRAD_NORM_CONST);
        final float normDepth = 1 / ( 1 + grad*grad / fallOf);
        return quantize(normDepth,maxDepth-minDepth) + minDepth;
    }

    private void buildSurface(final int x, final int z,final Chunk chunk,final int dx,final int dz,final int[] reliefBaseHeight) {
        final int surfaceHeight = reliefBaseHeight[((dx<<4)+dz)];
        final int sedimentLayerDepth = sedimentDepth(x,z,10,-1,10);
        for(int i=0 ; i<=sedimentLayerDepth ; i++ ) {
            chunk.setBlockState(new BlockPos(x,surfaceHeight-i,z), sedimentStrata(x,z,surfaceHeight-i,surfaceHeight),false);
        }

        // first layer
        chunk.setBlockState(new BlockPos(x, reliefBaseHeight[((dx<<4)+dz)], z),topLayer(x,z),false);



    }

    private BlockState topLayer(final int x, final int z) {
        return applyTerrainGradient(x,z);
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

    public BlockState applyTerrainGradient(final int x , final int z) {
        final int colors = terrainGradient.length-1;
        final int idx = (int) (Math.floor((Math.tanh(reliefResInterpolation.interpolateBilinear(x,z) / 15.0 )*0.5 + 0.5)*colors + 0.5));
        return terrainGradient[idx];
    }


    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
    }

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
                new BlockState[getBaseHeight(x, z) - settings.value().bottomY()];
        Arrays.fill(blockStates, DEFAUT);
        return new VerticalBlockSample(settings.value().bottomY(), blockStates);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {}
}
