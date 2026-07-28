# Biome definition

> **Source:** <https://minecraft.wiki/w/Biome_definition>  
> **Revision:** 3677851 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_9 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.2** — Carver types have been removed. The carvers field now lists carvers directly instead of having to specify the type.
- **1.21.4** — Added [Float] music\_volume field to [NBT Compound / JSON Object] effects.
- **1.21.4** — Changed the [NBT List / JSON Array] music field to be a weighted list of music objects.
- **1.21.5** — Added [Int] dry\_foliage\_color field to [NBT Compound / JSON Object] effects.
- **1.21.11** — Added [NBT Compound / JSON Object] attributes field.
- **1.21.11** — Removed [Int] fog\_color‌, [Int] sky\_color‌, [Int] water\_fog\_color, [NBT Compound / JSON Object] particle, [String][NBT Compound / JSON Object] ambient\_sound, [NBT Compound / JSON Object] mood\_sound, [NBT Compound / JSON Object] additions\_sounds, [NBT List / JSON Array] music, and [Float] music\_volume fields in [NBT Compound / JSON Object] effects.
- **1.21.11** — The [String][NBT List / JSON Array][Int] water\_color, [String][NBT List / JSON Array][Int] foliage\_color, [String][NBT List / JSON Array][Int] dry\_foliage\_color, and [String][NBT List / JSON Array][Int] grass\_color fields now accept a hex-string or array of red, green, blue floats in addition to the packed integer value.
- **26.3** *(unreleased)* — Removed the following fields: - `creature_spawn_probability` - `spawners` - `spawn_costs`
- **26.3** *(unreleased)* — The above fields have been moved to environment attributes.

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**removed / changed since** — 3 occurrence(s):

- - [Float] creature\_spawn\_probability​[*until: JE 26.3*]: (optional) Higher value results in more creatures spawned in world generation. Must be between 0.0 and 0.9999999 (both inclusive).
- - [NBT Compound / JSON Object] spawners​[*until: JE 26.3*]: (Required, but can be empty. If this object doesn't contain a certain category, mobs in this category do not spawn.) Entity spawning settings.
- - [NBT Compound / JSON Object] spawn\_costs​[*until: JE 26.3*]: (Required, but can be empty. Only mobs listed here use the spawn cost mechanism) See Mob spawning § Spawn costs for details.

---
For client-side biome customization in resource packs in *Bedrock Edition*, see Resource pack § Biomes.

Biomes are stored as JSON files within a data pack in the path `data/<namespace>/worldgen/biome` in *Java Edition* or in a behavior pack in the folder `<behavior pack>/biomes` in *Bedrock Edition*.

## JSON format

### *Java Edition*

- [NBT Compound / JSON Object]: The root object.
  - [Boolean] has\_precipitation: Determines whether or not the biome has precipitation.
  - [Float] temperature: Controls gameplay features like grass and foliage color, and a height adjusted temperature (which controls whether raining or snowing occurs if [Boolean] has\_precipitation is `true`, and generation details of some features).
  - [String] temperature\_modifier: (optional, defaults to none) Either `none` or `frozen`. Modifies temperature before calculating the height adjusted temperature. If `frozen`, makes some places' temperature high enough to rain (0.2).
  - [Float] downfall: Controls grass and foliage color.
  - [NBT Compound / JSON Object] effects: Ambient effects in this biome.
    - [Int] water\_color: (Required, but the normal value is 4159204) Decimal value converted from Hex color to use for water blocks and cauldrons.
    - [Int] foliage\_color: (optional) Decimal value converted from Hex color to use for tree leaves and vines. If not present, the value depends on downfall and the temperature.
    - [Int] dry\_foliage\_color: (optional) Decimal value converted from Hex color to use for leaf litter.
    - [Int] grass\_color: (optional) Decimal value converted from Hex color to use for grass blocks, short grass, tall grass, ferns, tall ferns, and sugar cane. If not present, the value depends on downfall and temperature.
    - [String] grass\_color\_modifier: (optional, defaults to none) Can be `none`, `dark_forest` or `swamp`.
  - [NBT Compound / JSON Object] attributes: (optional) Map of environment attributes that apply when in this biome.
  - [String][NBT Compound / JSON Object][NBT List / JSON Array] carvers: Any number of carver(s) (a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) (Required, but can be empty)
  - [NBT List / JSON Array] features: List of generation steps (Can be empty). Usually there are 11 steps, but any amount is possible.
    - [String][NBT Compound / JSON Object][NBT List / JSON Array] each entry of the list: Any number of placed feature(s) (a [String] tag with `#`, or an [NBT List / JSON Array] array containing either [String] IDs or new [NBT Compound / JSON Object] definitions in the same data type) — Features to place during that generation step. The same placed features in the same step in two biomes cannot be in different orders. In each step, all feature IDs need to be ordered consistently across biomes. For example, in the UNDERGROUND\_ORES step of minecraft:plains, ore\_dirt is before ore\_gravel. In other biomes' UNDERGROUND\_ORES step, if both ore\_dirt and ore\_gravel as present, ore\_gravel cannot be before ore\_dirt.

    These generation steps are also referred to by name for structure generation. They are, in order:

    - `RAW_GENERATION`: Used by small end island features in vanilla.
    - `LAKES`: Used by lava lakes in vanilla.
    - `LOCAL_MODIFICATIONS`: Used for amethyst geodes and icebergs in vanilla.
    - `UNDERGROUND_STRUCTURES`: Used for dungeons and overworld fossils in vanilla.
    - `SURFACE_STRUCTURES`: Used for desert wells and blue ice patches in vanilla.
    - `STRONGHOLDS`: Not used for features in vanilla.
    - `UNDERGROUND_ORES`: Used for overworld ore blobs, overworld dirt/gravel/stone variant blobs, and sand/gravel/clay disks in vanilla.
    - `UNDERGROUND_DECORATION`: Used for infested block blobs, nether gravel and blackstone blobs, and all nether ore blobs in vanilla.
    - `FLUID_SPRINGS`: Used for water and lava springs in vanilla.
    - `VEGETAL_DECORATION`: Used for trees, bamboo, cacti, kelp, and other ground and ocean vegetation in vanilla.
    - `TOP_LAYER_MODIFICATION`: Used for surface freezing in vanilla.
  - [Float] creature\_spawn\_probability​[*until: JE 26.3*]: (optional) Higher value results in more creatures spawned in world generation. Must be between 0.0 and 0.9999999 (both inclusive).
  - [NBT Compound / JSON Object] spawners​[*until: JE 26.3*]: (Required, but can be empty. If this object doesn't contain a certain category, mobs in this category do not spawn.) Entity spawning settings.
    - [NBT List / JSON Array] <mob category>: (Can be empty. If empty, mobs in this category do not spawn.) The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`. A list of spawner data objects, one for each mob which should spawn in this biome.
      - [NBT Compound / JSON Object]: The spawner data for a single mob.
        - [String] type: The namespaced entity id of the mob.
        - [Int] weight: How often this mob should spawn, higher values produce more spawns.
        - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
        - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - [NBT Compound / JSON Object] spawn\_costs​[*until: JE 26.3*]: (Required, but can be empty. Only mobs listed here use the spawn cost mechanism) See Mob spawning § Spawn costs for details.
    - [NBT Compound / JSON Object] <entity id>: The namespaced entity id of the mob.
      - [Double] energy\_budget: New mob's maximum potential.
      - [Double] charge: New mob's charge.

An interactive widget is being loaded. If this does not work for you, please reload the page or check if JavaScript is working or enabled.

### *Bedrock Edition*

This section is a work in progress.

Please help expand and improve it. The talk page may contain suggestions.

- [NBT Compound / JSON Object]: The root object.
  - [String] format\_version: The format version of the file, requires `"1.21.110"` for all current features.
  - [NBT Compound / JSON Object] minecraft:biome: The biome definition.
    - [NBT Compound / JSON Object] description
      - [String] identifier: A namespaced identifier for the biome.
    - [NBT Compound / JSON Object] components: Components used to define the biome's generation.
      - [NBT Compound / JSON Object] minecraft:climate: Defines the climate properties of the biome.
        - [Float] downfall: Downfall, mainly causes grass and foliage tints to change in terms of humidity (if not overridden in resource packs), and the random extinction of fire. If set to 0, no precipitation occurs.
        - [NBT List / JSON Array] snow\_accumulation: Snow accumulation, the first value controls how much snow is pre-generated (0 being one uniform layer, 0.125 randomized patches without snow and stacked near blocks, higher increases the snow piles), the second value controls the speed of snow accumulation during snowfall and must be equal to or higher than the first value.
        - [Float] temperature: Temperature, controls at what height rainfall transitions to snowfall and water freezes, changes grass, sky, and foliage tints, affects sponge drying.
      - [NBT Compound / JSON Object] minecraft:creature\_spawn\_probability
        - [Float] probability: Chance of creatures spawning when the chunk is generated. Must be ≤0.75.
      - [NBT Compound / JSON Object] minecraft:humidity
        - [Boolean] is\_humid: Whether or not the biome is humid. This overrides the downfall property with 0 or 1 while leaving tints unaffected.
      - [NBT Compound / JSON Object] minecraft:map\_tints
        - [NBT List / JSON Array][String] foliage: The color foliage will be tinted by on a map in this biome for example `#6a7039`. If not set, this defaults to the colormap determined by temperature and downfall.
        - [NBT Compound / JSON Object] grass: The color grass will be tinted by on a map in this biome. If not set, this defaults to the colormap determined by temperature and downfall.
          - [String] type: It could be `noise` or `tint`. When set to `noise`, this will apply a secondary randomized temperature gradient in the biome and use the colormap to tint grass, like in swamps.
          - [String] tint: Available only if the type is tint, it is used to define the fixed color of the grass on the map, for example `#aea42a`.
      - [NBT Compound / JSON Object] minecraft:mountain\_parameters: Parameters used to drive mountain slope blocks.
        - [Boolean] east\_slopes: Whether to have custom east-facing slopes.
        - [Boolean] west\_slopes: Whether to have custom west-facing slopes.
        - [Boolean] north\_slopes: Whether to have custom north-facing slopes.
        - [Boolean] south\_slopes: Whether to have custom south-facing slopes.
        - [NBT Compound / JSON Object] material: Block to use as steep material.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object] top\_slide: Controls the density tapering that happens at the top of the world to prevent terrain from reaching too high.
          - [Boolean] enabled
        - [NBT Compound / JSON Object] steep\_material\_adjustment: Defines surface material for steep slopes.
          - [Boolean] east\_slopes: Whether to have east-facing slopes.
          - [Boolean] west\_slopes: Whether to have west-facing slopes.
          - [Boolean] north\_slopes: Whether to have north-facing slopes.
          - [Boolean] south\_slopes: Whether to have south-facing slopes.
          - [NBT Compound / JSON Object] material: Block to use as steep material.
            - [String] name: Identifier of the block.
            - [NBT Compound / JSON Object] states: Block states for the block.
      - [NBT Compound / JSON Object] minecraft:overworld\_height: Deprecated, only used for pre-Caves & Cliffs generation and unexplored explorer map terrain rendering. Specifies the noise parameters used to drive terrain height.
        - [NBT List / JSON Array] noise\_params: 2 values, first is depth, and the second is scale.
        - [String] noise\_type: Uses a built-in preset instead of specifying values. Available presets are:

          - `beach`
          - `deep_ocean`
          - `default`
          - `default_mutated`
          - `extreme`
          - `highlands`
          - `less_extreme`
          - `lowlands`
          - `mountains`
          - `mushroom`
          - `ocean`
          - `river`
          - `stone_beach`
          - `swamp`
          - `taiga`
      - [NBT Compound / JSON Object] minecraft:overworld\_generation\_rules: Deprecated, only used for pre-Caves & Cliffs generation. Specifies where the biome generates.
        - [String] generate\_for\_climates: Controls where the biome initially attempts to generate, a climate zone (`medium`, `warm`, `lukewarm`, `cold`, or `frozen`), and the weight relative to other biomes in that climate zone. Note that `lukewarm` can only be used for oceans; as climate zones are placed differently here. It is not possible to control rare climate zones or edge transformations.
        - [NBT List / JSON Array][String] hills\_transformation: The identifier of the biome to replace it in hills areas. This can either be one string, or a list of array pairs, containing the identifier and the weight, to randomly select from multiple biomes.
        - [NBT List / JSON Array][String] mutate\_transformation: The identifier of the biome to replace it in mutated areas.
        - [NBT List / JSON Array][String] river\_transformation: The identifier of the biome to replace it at river noise.
        - [NBT List / JSON Array][String] shore\_transformation: The identifier of the biome to replace it at the border of oceans.
      - [NBT Compound / JSON Object] minecraft:replace\_biomes: Allows this biome to replace parts of one or more vanilla biomes.
        - [NBT List / JSON Array] replacements: The list of replacements
          - [NBT Compound / JSON Object]: *An replacement*
            - [Float] amount: Chance that the replacement is attempted. Must be >0.0 and ≤1.0.
            - [String] dimension: The dimension in which this replacement can occur. Can be either `minecraft:overworld`, or `minecraft:nether`.
            - [Float] noise\_frequency\_scale: Scale to alter the frequency of replacements. Lower values make bigger areas that are less common, and higher values make smaller areas that are more common. Must be >0.0 and ≤100.0.
            - [NBT List / JSON Array] targets: The biomes that can be replaced.
              - [String]: A biome ID.
    - [NBT Compound / JSON Object] minecraft:surface\_builder
      - [NBT Compound / JSON Object] builder: Controls the blocks used for surface layers.
        - [String] type: The type of surface builder. Can be `minecraft:capped`, `minecraft:frozen_ocean`, `minecraft:mesa`, `minecraft:overworld`, `minecraft:swamp`, or `minecraft:the_end`.

          If `type` is `minecraft:capped`:
        - [NBT Compound / JSON Object][String] beach\_material: Material used near sea level.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] ceiling\_materials: Material used for the surface ceiling.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] floor\_materials: Material used for the surface floor.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] foundation\_material: Material used to replace solid blocks that are not surface blocks.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] sea\_material: Material used to replace air below sea level.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.

          If `type` is `minecraft:frozen_ocean` or `minecraft:overworld`:
        - [NBT Compound / JSON Object][String] foundation\_material: Material used to replace solid blocks that are not surface blocks.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] mid\_material: Material used a layer below the surface of the biome.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [Int] sea\_floor\_depth: How deep below sea level the sea floor should be. Must be ≤127.
        - [NBT Compound / JSON Object][String] sea\_floor\_material: Material used as a floor for bodies of water.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] sea\_material: Material used to replace air below sea level.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] top\_material: Material used for the surface of the biome.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.

          If `type` is `minecraft:mesa`:
        - [Boolean] bryce\_pillars: Whether the mesa generates with hoodoos.
        - [NBT Compound / JSON Object][String] clay\_material: Base clay material.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] foundation\_material: Material used to replace solid blocks that are not surface blocks.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] hard\_clay\_material: Hardened clay material.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [Boolean] has\_forest: Adds coarse dirt and grass at high altitudes.
        - [NBT Compound / JSON Object][String] mid\_material: Material used a layer below the surface of the biome.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [Int] sea\_floor\_depth: How deep below sea level the sea floor should be. Must be ≤127.
        - [NBT Compound / JSON Object][String] sea\_floor\_material: Material used as a floor for bodies of water.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] sea\_material: Material used to replace air below sea level.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] top\_material: Material used for the surface of the biome.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.

          If `type` is `minecraft:swamp`:
        - [NBT Compound / JSON Object][String] foundation\_material: Material used to replace solid blocks that are not surface blocks.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [Int] max\_puddle\_depth\_below\_sea\_level: The depth at which surface blocks can be replaced with water for puddles. Must be ≤127.
        - [NBT Compound / JSON Object][String] mid\_material: Material used a layer below the surface of the biome.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [Int] sea\_floor\_depth: How deep below sea level the sea floor should be. Must be ≤127.
        - [NBT Compound / JSON Object][String] sea\_floor\_material: Material used as a floor for bodies of water.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] sea\_material: Material used to replace air below sea level.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
        - [NBT Compound / JSON Object][String] top\_material: Material used for the surface of the biome.
          - [String] name: Identifier of the block.
          - [NBT Compound / JSON Object] states: Block states for the block.
      - [NBT Compound / JSON Object] minecraft:surface\_material\_adjustments: Specify fine changes to blocks used in terrain generation.
        - [NBT List / JSON Array] adjustments
          - [NBT Compound / JSON Object]: *An adjustment*
            - [String][Boolean][Float] height\_range: ​[*more information needed*]
            - [Float] noise\_frequency\_scale: The scale to apply to the position when accessing the noise value.
            - [NBT List / JSON Array] noise\_range: A range of noise values for which this adjustment should be applied.
            - [NBT Compound / JSON Object] materials: Materials to use when this adjustment is active.
              - [NBT Compound / JSON Object][String] foundation\_material: Material used to replace solid blocks that are not surface blocks.
                - [String] name: Identifier of the block.
                - [NBT Compound / JSON Object] states: Block states for the block.
              - [NBT Compound / JSON Object][String] mid\_material: Material used a layer below the surface of the biome.
                - [String] name: Identifier of the block.
                - [NBT Compound / JSON Object] states: Block states for the block.
              - [NBT Compound / JSON Object][String] sea\_floor\_material: Material used as a floor for bodies of water.
                - [String] name: Identifier of the block.
                - [NBT Compound / JSON Object] states: Block states for the block.
              - [NBT Compound / JSON Object][String] sea\_material: Material used to replace air below sea level.
                - [String] name: Identifier of the block.
                - [NBT Compound / JSON Object] states: Block states for the block.
              - [NBT Compound / JSON Object][String] top\_material: Material used for the surface of the biome.
                - [String] name: Identifier of the block.
                - [NBT Compound / JSON Object] states: Block states for the block.
      - [NBT Compound / JSON Object] minecraft:tags
        - [NBT List / JSON Array] tags: Tags for the biome.
    - [NBT Compound / JSON Object] minecraft:village\_type
      - [String] type: The type of village for this biome. Valid values are `default`, `desert`, `ice`, `savanna`, and `taiga`.

## History

### *Java Edition*

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.16.2 | | | 20w28a | | | | Added experimental support for biomes in data packs. |
| 20w30a | | | | Added the `grass_color`, `foliage_color`, `water_color`, and `water_fog_color` effects properties. |
| pre2 | | | | Added the `player_spawn_friendly` property. |
| 1.19 | | | 22w11a | | | | Removed the `category` field. Functionality has been moved to biome tags. |
| 1.19.3 | | | Pre-release 3 | | | | Now when specifying a sound event, a fixed audible range can be also specified within an object. |
| 1.19.4 | | | 23w03a | | | | Renamed the `precipitation` field to `has_precipitation`. And now it is a boolean, and when it is true, whether it rains or snows are determined only by temperature. Before, it can be one of "none", "rain" and "snow".[1] |
| Pre-release 1 | | | | Now [Int] minCount and [Int] maxCount in [NBT Compound / JSON Object] spawners must be a positive integer. And [Int] maxCount must be not less than [Int] minCount. |
| 1.21.2 | | | 24w33a | | | | Carver types have been removed. The carvers field now lists carvers directly instead of having to specify the type. |
| 1.21.4 | | | 24w44a | | | | Added [Float] music\_volume field to [NBT Compound / JSON Object] effects. |
| Changed the [NBT List / JSON Array] music field to be a weighted list of music objects. |
| 1.21.5 | | | 25w08a | | | | Added [Int] dry\_foliage\_color field to [NBT Compound / JSON Object] effects. |
| 1.21.11 | | | 25w42a | | | | Added [NBT Compound / JSON Object] attributes field. |
| Removed [Int] fog\_color‌, [Int] sky\_color‌, [Int] water\_fog\_color, [NBT Compound / JSON Object] particle, [String][NBT Compound / JSON Object] ambient\_sound, [NBT Compound / JSON Object] mood\_sound, [NBT Compound / JSON Object] additions\_sounds, [NBT List / JSON Array] music, and [Float] music\_volume fields in [NBT Compound / JSON Object] effects. |
| 25w44a | | | | The [String][NBT List / JSON Array][Int] water\_color, [String][NBT List / JSON Array][Int] foliage\_color, [String][NBT List / JSON Array][Int] dry\_foliage\_color, and [String][NBT List / JSON Array][Int] grass\_color fields now accept a hex-string or array of red, green, blue floats in addition to the packed integer value. |
| Upcoming *Java Edition* | | | | | | | |
| 26.3 | | | snap4 | | | | Removed the following fields:  - `creature_spawn_probability` - `spawners` - `spawn_costs` |
| The above fields have been moved to environment attributes. |

### *Bedrock Edition*

| *Bedrock Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ? | | | | | | | Custom biomes were added |
| 1.18 | | | | | | | Custom biomes have become obsolete and impossible to create due to the world generation change. |
| 1.20.60 | | | Preview 1.20.60.24 | | | | Biome tags are now specified under an array within the `minecraft:tags` component rather than as loose JSON objects. |
| 1.21.70 | | | Preview 1.21.70.23 | | | | Removed unused experimental json field `peaks_factor` from `minecraft:mountain_parameters` in the behavior pack biome file. |
| 1.21.80 Experiment Custom biomes | | | Preview 1.21.80.27 | | | | Added `minecraft:replace_biomes` component to allow for custom biomes to replace portions of vanilla biomes. To use, add to custom biome files in behavior packs. |
| 1.21.100 Experiment Custom biomes | | | Preview 1.21.100.20 | | | | Custom biome names are no longer implied by their filenames. Biome names are explicitly specified via the `identifier` property under the `description` sub-object of their JSON definition. |
| Biome identifiers must now be namespaced (ex: `minecraft:plains`). Biomes with a format version lower than 1.21.90 will have their identifier automatically prefixed `minecraft:` when loaded if no namespace is already specified. |
| Preview 1.21.100.22 | | | | Added a new server side biome component `surface_builder` that will combine the components `surface_parameters`, `frozen_ocean_surface`, `mesa_surface`, `swamp_surface`, `capped_surface`, and `the_end_surface` into one component. Each biome can only use one builder type. The component can be used for world generation settings such as foundation material and sea floor depth. |
| Added `minecraft:humidity` component with `is_humid` this forces a biome to either always be humid or never humid. Humidity effects the spread chance, and spread rate of fire in the biome. |
| Added `minecraft:partially_frozen` This component will impact the temperature in a frozen biome, causing some areas to not be frozen. Ex: patchy ice, patchy snow. |
| 1.21.111 Experiment Custom biomes | | | Preview 1.21.110.20 | | | | Added `max_puddle_depth_below_sea_level` to the `surface_builder` biome component for the swamp builder type. The component will set the search depth for how far below sea level to search for a surface to add a puddle. |
| Removed support for loading custom biomes for base game versions 1.21.100 and below. |
| 1.21.111 | | | Preview 1.21.110.25 | | | | Custom biomes were released from experimental. |
| 26.0 | | | Preview 26.0.25 | | | | Added `minecraft:village_type` biome component that determines the type of the village in the biome. This also allows for the generation of villages in the biome; not using this component means the village will never be generated in the biome. |
| Enabled the Biome Replacement feature in the Nether, however, currently it doesn't work as well as it should. |
| 26.20 Experiment Upcoming Creator Features | | | Preview 26.20.23 | | | | Added `minecraft:subsurface_builder` biome component to apply surface generation rules below the Overworld's terrain surface. Includes `minecraft:noise_gradient` builder type for placing block bands via Perlin noise distribution, with parameters `non_replaceable_blocks` and `gradient_blocks`. |
| `minecraft:surface_builder` now suppose the type `minecraft:noise_gradient` doing the same as mentioned above. |
| 26.30 | | | Preview 26.30.25 | | | | `minecraft:subsurface_builder` and `minecraft:surface_builder` with `minecraft:noise_gradient` type are now stable and no longer require experimental features. Requires `format_version` 1.26.30 or higher. |
| Preview 26.30.29 | | | | Biome replacement in the Nether is now working as it should. |

## External links

- [Biome Generator on misode.github.io](https://misode.github.io/worldgen/biome/)

## References

1. To fix [MC-230678](https://bugs.mojang.com/browse/MC-230678), [MC-233893](https://bugs.mojang.com/browse/MC-233893), [MC-238904](https://bugs.mojang.com/browse/MC-238904), [MC-247836](https://bugs.mojang.com/browse/MC-247836), [MC-254132](https://bugs.mojang.com/browse/MC-254132), and [MC-255811](https://bugs.mojang.com/browse/MC-255811).

## Navigation
