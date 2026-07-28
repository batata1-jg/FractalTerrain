# Noise settings

> **Source:** <https://minecraft.wiki/w/Noise_settings>  
> **Revision:** 3691431 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_2 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **26.3** *(unreleased)* — The `surface_rule` field has been renamed to `material_rule`
- **26.3** *(unreleased)* — The `spawn_target` field has been updated to allow use of any density function, instead of just a subset of those defined within the `noise_router` field.

---
This feature is exclusive to *Java Edition*.

**Noise settings** are for generating the shape of the terrain and noise caves, and what blocks the terrain is generated with, stored as JSON files within a data pack in the path `data/<namespace>/worldgen/noise_settings`, and are used with the `minecraft:noise` generator in a dimension. Vanilla settings include `minecraft:overworld` for normal Overworld generation, `minecraft:amplified` for Amplified Overworld generation, `minecraft:nether` for regular Nether generation, `minecraft:caves` for Cave (Nether-like generation but with Overworld terrain features) generation, `minecraft:end` for regular End generation, and `minecraft:floating_islands` for Floating Islands (similar to The End outer islands) generation.

## JSON format

- [NBT Compound / JSON Object]: Root object.
  - [Int] sea\_level: The sea level in this dimension. Note that this value only affects world generation. The sea level for mob spawning is a fixed value 63.
  - [Boolean] disable\_mob\_generation: Disables creature spawning upon chunk generation.
  - [Boolean] ore\_veins\_enabled: Whether ore veins generate.
  - [Boolean] aquifers\_enabled: Whether aquifers generate. If set to false, almost all caves below sea level are filled with water.
  - [Boolean] legacy\_random\_source: Whether to use the old random number generator from before 1.18 for world generation.
  - [NBT Compound / JSON Object] default\_block: The default block used for the terrain.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT Compound / JSON Object] default\_fluid: The default block used for seas and lakes.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT List / JSON Array] spawn\_target: (Required, but can be empty) A list of climate parameters to specify the points around which the player tries to spawn. The game selects some horizonal locations that are not more than 2560 blocks away from the origin (0,0), then sample the noise values ("depth" noise and "offset" are always 0), and calculate `((x^2+z^2)^2) / 390625 + (the square of the mininum distance to the ranges in the list)`. The player spawns near the location where this value is smallest.
    - [NBT Compound / JSON Object]：A range.
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
  - [NBT Compound / JSON Object] noise: Fields for world generation.
    - [Int] min\_y: The minimum Y coordinate where terrain starts generating. Value between -2032 and 2031 (both inclusive). Must be divisible by 16.
    - [Int] height: The total height where terrain generates. Value between 0 and 4064 (both inclusive). Must be divisible by 16. And `min_y + height` cannot exceed 2032.
    - [Int] size\_horizontal: Value between 0 and 4 (both inclusive). Note that the noise breaks into the 12×12 block section per chunk for the value 3.
    - [Int] size\_vertical: Value between 0 and 4 (both inclusive)
  - [NBT Compound / JSON Object] noise\_router: The noise router routes density functions to noise parameters used for world generation.
  - [NBT Compound / JSON Object] surface\_rule: The main surface rule to place blocks in the terrain.

## Noise router

Main article: Noise router

The **noise router** is a collection of density functions. Density functions compute a value for each block position. They are used for terrain generation, biome layout, aquifers, ore veins, and more.

## Surface rule

Main article: Surface rule

**Surface rules** are used to determine the block for each solid position of the terrain. Noise settings contains one root [NBT Compound / JSON Object] surface\_rule field that contains all the logic for placing the different surface blocks.

## Defaults

These are the default settings for some vanilla presets. For brevity, some fields are summarized or omitted entirely; the vanilla data pack's `minecraft/worldgen/noise_settings` directory has them in full.

Amplified and large biomes are the same as Overworld generation, but the [NBT Compound / JSON Object] noise\_router is adjusted.

| Property | Overworld | Caves | Floating Islands | The Nether | The End |
| --- | --- | --- | --- | --- | --- |
| [Int] sea\_level | 63 | 32 | -64 | 32 | 0 |
| [Boolean] disable\_mob\_generation | False | False | False | False | True |
| [Boolean] ore\_veins\_enabled | True | False | False | False | False |
| [Boolean] aquifers\_enabled | True | False | False | False | False |
| [Boolean] legacy\_random\_source | False | True | True | True | True |
| [NBT Compound / JSON Object] default\_block | Stone | | | Netherrack | End Stone |
| [NBT Compound / JSON Object] default\_fluid | Water | | | Lava | Air |
| [NBT List / JSON Array] spawn\_target | Two spawn targets | Empty | Empty | Empty | Empty |
| [Int] noise.min\_y | -64 | -64 | 0 | 0 | 0 |
| [Int] noise.height | 384 | 192 | 256 | 128 | 128 |
| [Int] noise.size\_horizontal | 1 | 1 | 2 | 1 | 2 |
| [Int] noise.size\_vertical | 2 | 2 | 1 | 2 | 1 |
| [NBT Compound / JSON Object] surface\_rule | The usual Overworld rules: places bedrock, deepslate, grass, and all biome-specific blocks. | Similar to the Overworld, but with a bedrock roof and tweaked to allow generating dirt and grass not on the surface. | Similar to the Overworld, but with no bedrock floor and tweaked to allow generating dirt and grass not on the surface. | Places the bedrock floor and roof, and biome-specific blocks. | Places end stone only. |

## History

| Upcoming *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 26.3 | | | snap1 | | | | The `surface_rule` field has been renamed to `material_rule` |
| snap2 | | | | The `spawn_target` field has been updated to allow use of any density function, instead of just a subset of those defined within the `noise_router` field. |

## External links

- [Noise settings Generator on misode.github.io](https://misode.github.io/worldgen/noise-settings/)

## Navigation
