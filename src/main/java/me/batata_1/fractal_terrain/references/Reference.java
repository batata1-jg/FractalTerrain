package me.batata_1.fractal_terrain.references;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reference {

    public static String ModID = "fractal_terrain";
    public static final Logger LOGGER = LoggerFactory.getLogger(ModID);

    @NotNull
    public static ResourceLocation identifier(@NotNull String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(ModID, path));
    }

    public static MutableComponent translate(String key, Object... param) {
        return Component.translatable(ModID + "." + key, param);
    }
}
