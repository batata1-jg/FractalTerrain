package me.batata_1.fractalterrain.world.gen.chunk;

import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.batata_1.fractalterrain.FractalTerrainInstance;
import me.batata_1.fractalterrain.math.Interpolation;
import me.batata_1.fractalterrain.registry.FractalTerrainRegistryKeys;
import me.batata_1.fractalterrain.registry.SettingsRegistry;
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

import static me.batata_1.fractalterrain.FractalTerrainInstance.reliefSource;
import static me.batata_1.fractalterrain.references.Reference.LOGGER;

// TODO: add compat , consertar biomes, reescrever isso

public final class FractalTerrainChunkGenerator extends ChunkGenerator {

    public record Settings (
            RegistryEntry<PostProcessingRelief.Settings> postConfig,
            float scale,
            int seaLevel,
            int bottomY,
            int topY
    ) implements SettingsRegistry.Settings {

        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        PostProcessingRelief.Settings.REGISTRY_CODEC.fieldOf("post_config").forGetter(Settings::postConfig),
                        Codec.FLOAT.optionalFieldOf("scale",1F).forGetter(Settings::scale),
                        Codec.INT.optionalFieldOf("sea_level",63).forGetter(Settings::seaLevel),
                        Codec.INT.optionalFieldOf("bottom_y",-64).forGetter(Settings::bottomY),
                        Codec.INT.optionalFieldOf("top_y",1600).forGetter(Settings::topY)
                ).apply(instance, Settings::new)
        );

        public static final Codec<RegistryEntry<Settings>> REGISTRY_CODEC = RegistryElementCodec.of(FractalTerrainRegistryKeys.FRACTAL_TERRAIN_CHUNK_GENERATOR_SETTINGS,CODEC);


    }

    private static final BlockState DEFAUT = Blocks.STONE.getDefaultState();

    private final RegistryEntry<Settings> settings;
    private final Interpolation interp;

//    public static final Codec<FractalTerrainChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
//            ((MapCodec) BiomeSource.CODEC.fieldOf("biome_source")).forGetter(g -> ((FractalTerrainChunkGenerator) g).biomeSource),
//            ((MapCodec) Settings.REGISTRY_CODEC.fieldOf("settings")).forGetter( g -> ((FractalTerrainChunkGenerator) g).settings)
//            ).apply((Applicative<FractalTerrainChunkGenerator, ?>)instance, instance.stable(FractalTerrainChunkGenerator::new))
//            );

    public static final Codec<FractalTerrainChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Settings.REGISTRY_CODEC.fieldOf("settings").forGetter((FractalTerrainChunkGenerator g) -> g.settings)
    ).apply(instance,FractalTerrainChunkGenerator::new));

    public FractalTerrainChunkGenerator(BiomeSource biomeSource, RegistryEntry<Settings> settings) {
        super(biomeSource);
        this.settings = settings;
        this.interp = new Interpolation(settings.value().scale());
        interp.setF(xz -> {
            try {
                return reliefSource.get().getElev(xz);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public RegistryEntry<Settings> getSettings() { return settings; }

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
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {}

    @Override
    public void populateEntities(ChunkRegion region) {}

    @Override
    public int getWorldHeight() {
        return settings.value().topY();
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(
            Executor executor,
            Blender blender,
            NoiseConfig noiseConfig,
            StructureAccessor structureAccessor,
            Chunk chunk) {
        return CompletableFuture.supplyAsync( () -> this.populateNoise(chunk) , executor );
    }

    private int getBaseHeight(int x , int z) {
        return (int) interp.interpolateSmoothStep(x,z) + settings.value().seaLevel()-1;
    }


    private Chunk populateNoise(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startingX = chunkPos.getStartX();
        int startingZ = chunkPos.getStartZ();
        for(int dx=0 ; dx<16 ; dx++) {
            for(int dz=0 ; dz<16 ; dz++) {
                int h = getBaseHeight(startingX+dx,startingZ+dz);
                for(int y = settings.value().bottomY(); y<=h ; y++) {
                    chunk.setBlockState(new BlockPos(startingX+dx,y,startingZ+dz),DEFAUT,false);
                }
            }
        }

        return chunk;
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
        BlockState[] blockStates = new BlockState[getBaseHeight(x,z) - settings.value().bottomY()];
        Arrays.fill(blockStates,DEFAUT);
        return new VerticalBlockSample(settings.value().bottomY(),blockStates);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {}

}
