# World preset definition

> **Source:** <https://minecraft.wiki/w/World_preset_definition>  
> **Revision:** 3048456 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

For vanilla world presets, see World preset.

This feature is exclusive to *Java Edition*.

**World presets** can be chosen in "World Type" button in "More World Options..." screen in "Create New World" to determine what dimensions the new world has.

## Usage

When opening the "More World Options..." screen for the first time, the "World Type" button is displayed as "Default". In this case the new world does **not** use any world preset, but uses the three vanilla dimensions by default, and tries to read the dimension files from the dimension folder of the data pack. In this case, the player can modify these three dimensions or add new dimensions with the dimension files, but they cannot delete these three vanilla dimensions.

Once the player clicks the "World Type" button, the game uses the world preset to determine the dimensions of the new world, no longer using the dimension folder. Changing the button back to "Default" uses the `minecraft:normal` world preset, instead of the original default state. However, without the data pack, the default state is exactly the same as the "Default" preset (`minecraft:normal`).

## Definition

Custom world presets are stored as JSON files in the data pack directory structure, highlighted below:

- *data pack name*.zip or  *data pack name*
  - pack.mcmeta
  - data
    - *namespace*
      - **worldgen**
        - **world\_preset**: *<name>*.json
      - More directories…

## JSON format

- [NBT Compound / JSON Object]: The root object.
  - [NBT Compound / JSON Object] dimensions: A collection of dimensions. Must contain a `minecraft:overworld` object.
    - [NBT Compound / JSON Object] *<dimension name>*: The definition of a dimension, see Dimension definition § JSON format.

## Tags

Main article: World preset tag (Java Edition)

To make a custom world preset appear in the "World Type" button, it must be added to the following tags:

- `minecraft:normal`: The world presets displayed in the "World Type" button.
- `minecraft:extended`: The world presets in the "World Type" button when holding down the `Alt` key.

It is also necessary to define the display text with the key of `generator.<namespace>.<name>` in the language file in a resource pack .

## World presets with settings

There are currently two world presets that have hardcoded settings screens where the overworld dimension can be customized. Includes `minecraft:flat` (Superflat) and `minecraft:single_biome_surface` (single biome).

### Superflat

When Superflat is selected in the "World Type" button, clicking the "Customize" button opens the "Superflat Customization" screen, where the player can directly change block of each layer, or click the “Presets” button to modify the superflat generator settings via preset code text.

#### Superflat Level Generation Preset

Clicking the "Presets" button to open the "Select a Preset" screen, the player can modify the settings of the superflat generator through preset code text, or they can choose a "**Flat Level Generator Preset (aka. Flat World Preset)**".

Superflat level generation presets are stored as JSON files in `data/<namespace>/worldgen/flat_level_generator_preset/<name>.json`.

- [NBT Compound / JSON Object]: The root object.
  - [String] display: This icons. Must be an item ID.
  - [NBT Compound / JSON Object] settings: The settings for the generator of the superflat overworld.
    - Flat generation settings — inherited from Template:Nbt inherit/flat generator settings/template:

      - [NBT List / JSON Array] layers: (required, but can be empty) Layer settings. This list is interpreted from top to bottom, starting at world bottom.
        - [NBT Compound / JSON Object]: A superflat layer.
          - [Int] height: The height of this layer. Value between 0 and 4064 (both inclusive).
          - [String] block: (optional, defaults to `minecraft:air`) The block ID.
      - [String] biome: (optional, defaults to `minecraft:plains`) The ID of the single biome of the world.
      - [Boolean] lakes: (optional, defaults to false) Whether or not to generate lava lakes. If set to true, then lava lakes (`lake_lava_underground` and `lake_lava_surface`) generate often even in biomes where lakes don't normally generate.
      - [Boolean] features: (optional, defaults to false) Whether or not to generate biome-specific placed features. Note that the placed features in the `UNDERGROUND_STRUCTURES` and `SURFACE_STRUCTURES` steps never generate.
      - [String][NBT List / JSON Array] structure\_overrides: (optional, defaults to all the structure sets) List of structure sets to use. Can be a ID of structure set, a structure set tag, or a list of structure set IDs.

To make a custom superflat level generation preset appear in the "Select a Preset" screen, it must be added to the `minecraft:visible` tag. It is also necessary to define the display text with the key of `flat_world_preset.<namespace>.<name>` in the language file in a resource pack .

### Single Biome

Clicking the "Customize" button, and in the "Buffet world customization" screen, the player can choose the single biome of the overworld.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.19 | | | 22w11a | | | | World presets/types and superflat world presets in "Create World" screen can now be controlled by data packs. |
| New registry types `worldgen/world_preset` and `worldgen/flat_level_generator_preset` were added to data-driven presets (like "Amplified" or "Single Biome"). |

## External links

- [World preset Generator on misode.github.io](https://misode.github.io/worldgen/world-preset/)

## Navigation
