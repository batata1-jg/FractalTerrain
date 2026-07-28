# Dimension definition

> **Source:** <https://minecraft.wiki/w/Dimension_definition>  
> **Revision:** 3683072 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

This article is about data pack contents. For other uses, see Dimension.

This feature is exclusive to *Java Edition*.

This article needs to be updated.

Please update this article to reflect recent updates or newly available information. The talk page may contain suggestions.
**Reason:** dimension format changes in 26.3

There is a related tutorial page for this topic!

See Tutorial:Adding a new dimension.

**Dimensions** are JSON files located in data packs that define dimensions for the game. New dimensions added can be accessed by using commands, like `/execute in <dimension> run teleport <coordinates>`.

## Usage

Dimensions are stored as JSON files within a data pack, at the path `data/<namespace>/dimension/<name>.json`. Alternatively, a Custom world preset can be used to customize all dimensions in a world. Dimensions stored separately override the dimension set in the selected world preset. This allows datapacks to only override a single dimension while keeping the other dimensions untouched. However, the user-selected world preset then doesn't have an impact on the given dimension.

## JSON format

When stored as separate dimensions, they follow the following syntax:

- [NBT Compound / JSON Object] The root tag.
  - [String] type: One dimension type (an [String] ID). Can be preset `overworld`, `the_nether`, `the_end`, `overworld_caves`, or a custom dimension type
  - [NBT Compound / JSON Object] generator: Generation settings used for that dimension.
    - [String] type: The generator type as resource location. One of `noise`, `flat`, or `debug`.
    - Additional fields of the generator, described below.

## Generator types

### debug

The generator type used when selecting debug mode in the world creation menu. This generator has no additional fields.

### flat

The generator type used for superflat worlds.

- Additional fields:
  - [NBT Compound / JSON Object] settings: Superflat settings.
    - Flat generation settings — inherited from Template:Nbt inherit/flat generator settings/template:

      - [NBT List / JSON Array] layers: (required, but can be empty) Layer settings. This list is interpreted from top to bottom, starting at world bottom.
        - [NBT Compound / JSON Object]: A superflat layer.
          - [Int] height: The height of this layer. Value between 0 and 4064 (both inclusive).
          - [String] block: (optional, defaults to `minecraft:air`) The block ID.
      - [String] biome: (optional, defaults to `minecraft:plains`) The ID of the single biome of the world.
      - [Boolean] lakes: (optional, defaults to false) Whether or not to generate lava lakes. If set to true, then lava lakes (`lake_lava_underground` and `lake_lava_surface`) generate often even in biomes where lakes don't normally generate.
      - [Boolean] features: (optional, defaults to false) Whether or not to generate biome-specific placed features. Note that the placed features in the `UNDERGROUND_STRUCTURES` and `SURFACE_STRUCTURES` steps never generate.
      - [String][NBT List / JSON Array] structure\_overrides: (optional, defaults to all the structure sets) List of structure sets to use. Can be a ID of structure set, a structure set tag, or a list of structure set IDs.

### noise

The generator used in all the default dimensions.

- Additional fields:
  - [String][NBT Compound / JSON Object] settings: One noise settings (an [String] ID, or a new [NBT Compound / JSON Object] noise settings definition) — Settings for the noise generator.
  - [NBT Compound / JSON Object] biome\_source: Settings determining the biome layout.
    - [String] type: The biome source type as a resource location.
    - Additional fields of the biome source, described below.

## Biome sources

### checkerboard

The checkerboard biome source places biomes in a checkerboard pattern.

- Additional fields:
  - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs)
  - [Int] scale: Optional. Value between 0 and 62 that defaults to 2. Determines the size of the checkerboard grid. A scale of 0 means each cell of the grid is one chunk wide. Doubles each time the scale increases.

### fixed

The fixed biome source, also called single biome, uses one specified biome everywhere.

- Additional fields:
  - [String] biome: One biome (an [String] ID) — The single biome to use.

### multi\_noise

- Additional fields:
  - [String] preset: A reference to a parameter list. The default parameter lists are `overworld` and `nether`.
- Or:
  - [NBT List / JSON Array] biomes: List of biome parameters points. Needs at least one entry. Biomes can appear in more than one parameter point.
    - [NBT Compound / JSON Object]: A parameter point.
      - [String] biome: One biome (an [String] ID) — The biome used at this parameter point.
      - [NBT Compound / JSON Object] parameters: The parameters of this entry
        - Noise parameter for biome (See World generation § Biomes for usages of each parameter in vanilla game) — inherited from Template:Nbt inherit/parameter point/template:

          - [NBT Compound / JSON Object] temperature: Not to be confused with the temperature value listed on Biome.
            - [Float] min: Value between -2.0 and 2.0 (both inclusive). The min value.
            - [Float] max: Value between -2.0 and 2.0 (both inclusive). The max value. Must be not less than `min`
          - [NBT List / JSON Array] temperature: Shorthand of [NBT Compound / JSON Object] temperature.
            - [Float]: Value between -2.0 and 2.0 (both inclusive). The min value.
            - [Float]: Value between -2.0 and 2.0 (both inclusive). The max value. Must be not less than `min`
          - [Float] temperature: Value between -2.0 and 2.0 (both inclusive). Shorthand of [NBT List / JSON Array] temperature when the min equals to the max.
          - [NBT Compound / JSON Object][NBT List / JSON Array][Float] humidity: The format is the same as `temperature`.
          - [NBT Compound / JSON Object][NBT List / JSON Array][Float] continentalness: The format is the same as `temperature`.
          - [NBT Compound / JSON Object][NBT List / JSON Array][Float] erosion: The format is the same as `temperature`.
          - [NBT Compound / JSON Object][NBT List / JSON Array][Float] weirdness: The format is the same as `temperature`.
          - [NBT Compound / JSON Object][NBT List / JSON Array][Float] depth: The format is the same as `temperature`.
          - [Float] offset: Value between 0.0 and 1.0 (both inclusive). Similar to the other parameters but is 0 everywhere. thus setting this parameter nearer to 0 leads to a greater edge over others, all else being equal.

### the\_end

The biome source used for the End dimension. This biome source has no additional fields.

## Multi noise parameter list

A multi-noise biome source parameter list is stored as JSON files within a data pack, at the path `data/<namespace>/worldgen/multi_noise_biome_source_parameter_list/<name>.json`. It is used to avoid changing world preset files when adding new biomes to experimental data packs.

The syntax is as follows:

- [String] preset: A reference to a hardcoded parameter list preset. The available presets are `overworld` and `nether`.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.16 | | | Pre-release 1 | | | | Added dimensions to data packs. |
| 1.16.2 | | | 20w29a | | | | The noise settings for custom dimensions can now also be stored in separate files. |
| pre1 | | | | Custom dimensions now use the same folder pattern in data packs as other resources: `namespace/<type>/resource.json`. |
| 1.19 | | | 22w11a | | | | Removed the `seed` field in `noise` generator and `the_end` biome source, and the world seed is now always used for all dimensions. |
| Dimension types can no longer be inlined in the dimension, they have to be a reference to a separate dimension\_type file. |
| 1.19.4 | | | 1.19.4-pre1 | | | | Added multi-noise biome source parameter list. |

## External links

- [Dimension Generator on misode.github.io](https://misode.github.io/dimension/)

## Navigation
