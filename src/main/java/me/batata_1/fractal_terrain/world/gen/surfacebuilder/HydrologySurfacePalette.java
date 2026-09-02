package me.batata_1.fractal_terrain.world.gen.surfacebuilder;

import me.batata_1.fractal_terrain.hydrology.profile.SurfaceMaterial;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The one place a hydrology {@link SurfaceMaterial} meets a block state.
 *
 * <p>Exists so {@code hydrology/} can name what a river exposes without importing {@code net.minecraft}
 * — the profile picks a material, this table picks the block. A material with no 1.20.1 counterpart is
 * substituted here, where the substitution is visible, rather than by dropping the token and losing the
 * profile's ability to express the distinction.
 */
public final class HydrologySurfacePalette {

    private HydrologySurfacePalette() {}

    /**
     * The block a material places, or {@code null} where the profile defers to the vanilla surface
     * rules. {@code underwater} is measured against the river's own water surface, not sea level, so a
     * bank material can differ across the water line without the profile needing a Y coordinate.
     */
    static @Nullable BlockState of(SurfaceMaterial material, boolean underwater) {
        return switch (material) {
            case DEFER -> null;
            case GRAVEL -> GRAVEL;
            case COBBLE -> COBBLE;
            case SAND -> SAND;
            // 1.20.1 has no silt. Dry silt reads as dirt, submerged silt as clay: the two vanilla blocks
            // a fine cohesive sediment looks like on either side of a waterline.
            case SILT -> underwater ? CLAY : DIRT;
            case CLAY -> CLAY;
            case MUD -> MUD;
        };
    }

    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    private static final BlockState MUD = Blocks.MUD.defaultBlockState();
}
