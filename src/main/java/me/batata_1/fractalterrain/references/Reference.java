package me.batata_1.fractalterrain.references;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class Reference {

    public static String ModID = "fractal-terrain";
    public static final Logger LOGGER = LoggerFactory.getLogger(ModID);

    @NotNull
    public static Identifier identifier(@NotNull String path ) {
        return Objects.requireNonNull(Identifier.of(ModID, path));
    }

    public static MutableText translate( String key , Object ... param ) {
    return Text.translatable( ModID + "." + key, param);
    }

}
