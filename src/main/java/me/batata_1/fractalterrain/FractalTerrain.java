package me.batata_1.fractalterrain;

import ai.onnxruntime.OrtEnvironment;
import me.batata_1.fractalterrain.references.Reference;
import me.batata_1.fractalterrain.storage.EntryStorage;
import me.batata_1.fractalterrain.world.HeightProvider;
import me.batata_1.fractalterrain.world.gen.FractalTerrainDensityFunction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;

import static me.batata_1.fractalterrain.references.Reference.LOGGER;
import static me.batata_1.fractalterrain.util.FractalTerrainUtil.ENV;

public class FractalTerrain implements ModInitializer {

    @Override
    public void onInitialize() {

        Registry.register(Registries.DENSITY_FUNCTION_TYPE, Reference.identifier("fractal_terrain") ,
                FractalTerrainDensityFunction.CODEC);
        LOGGER.info("registered density");


        try {
            ENV = OrtEnvironment.getEnvironment();
        } catch ( NoClassDefFoundError | RuntimeException e ) {
            LOGGER.error(e.getMessage());
        }

        ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer server) -> {
            try {
                LOGGER.info("activating fractal terrain");
                EntryStorage.setInstance( new FractalTerrainInstance(server) );
                HeightProvider.bootstrapTileStorages();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

//        LOGGER.info(FabricLoader.getInstance().getModContainer("fractalterrain").get().getRootPaths().toString());
//        ModContainer terrenomod = FabricLoader.getInstance().getModContainer("terrenomod").get();

    }
}
