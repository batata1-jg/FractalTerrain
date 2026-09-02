package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * What a hydrology profile can ask to see at the surface of its own carve, as a token rather than a
 * block.
 *
 * <p>Exists so {@code hydrology/} stays free of {@code net.minecraft}: the profile names a material and
 * {@code world/gen/surfacebuilder/HydrologySurfacePalette} owns the mapping to a block state, including
 * any substitution a material with no vanilla counterpart needs.
 */
public enum SurfaceMaterial {

    /** Leave this depth to the vanilla surface rules — a claimed column need not paint every layer. */
    DEFER,
    GRAVEL,
    COBBLE,
    SAND,
    SILT,
    CLAY,
    MUD
}
