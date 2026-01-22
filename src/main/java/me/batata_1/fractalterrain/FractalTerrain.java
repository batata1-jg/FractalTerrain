package me.batata_1.fractalterrain;

import ai.onnxruntime.OrtEnvironment;
import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.storage.TileStorage;
import me.batata_1.fractalterrain.world.gen.FractalTerrainDensityFunction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.IOException;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.ENV;

public class FractalTerrain implements ModInitializer {

    @Override
    public void onInitialize() {

        Registry.register(Registries.DENSITY_FUNCTION_TYPE, Reference.identifier("fractal_terrain") ,
                FractalTerrainDensityFunction.CODEC);



        try {
            ENV = OrtEnvironment.getEnvironment();
        } catch ( NoClassDefFoundError | RuntimeException e ) {
            LOGGER.error(e.getMessage());
        }

        ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer server) -> {
            try {
                LOGGER.info("activating fractal terrain");
                TileStorage.setInstance( new FractalTerrainInstance(server) );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

//        LOGGER.info(FabricLoader.getInstance().getModContainer("fractalterrain").get().getRootPaths().toString());
//        ModContainer terrenomod = FabricLoader.getInstance().getModContainer("terrenomod").get();

    }
}
