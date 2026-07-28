# Noise router

> **Source:** <https://minecraft.wiki/w/Noise_router>  
> **Revision:** 3654407 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_1 Java Edition history entry newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.9** — Replaced `initial_density_without_jaggedness` with `preliminary_surface_level` determining the preliminary surface directly, instead of by scanning the initial density.

---
This feature is exclusive to *Java Edition*.

The **noise router** is a collection of density functions. Density functions compute a value for each block position. They are used for terrain generation, biome layout, aquifers, ore veins, and more. A noise router is a part of a dimension's noise settings.

## JSON format

The different density functions of the noise router and their uses.

- [NBT Compound / JSON Object] noise\_router: Each entry is one density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition)
  - [String][Double][NBT Compound / JSON Object] preliminary\_surface\_level: A 2D density function (sampled at Y=0) determining the Y-level of the preliminary surface; should generally be lower than the actual terrain height (determined by the final density). Used by the generation of aquifers and surface rules.
  - [String][Double][NBT Compound / JSON Object] final\_density: Determines where there is an air or a default block. If positive, returns a default block that can be replaced by the surface rule. Otherwise, an air block where aquifers can generate.
  - [String][Double][NBT Compound / JSON Object] barrier: Affects whether to separate between aquifers and open areas in caves. Larger values leads to higher probability to separate.
  - [String][Double][NBT Compound / JSON Object] fluid\_level\_floodedness: Affects the probability of generating liquid in an cave for aquifer. The larger value leads to higher probability. The noise value greater than 1.0 is regarded as 1.0, and value less than -1.0 is regarded as -1.0.
  - [String][Double][NBT Compound / JSON Object] fluid\_level\_spread: Affects the height of the liquid surface at a horizontal position. Smaller value leads to higher probability for lower height.
  - [String][Double][NBT Compound / JSON Object] lava: Affects whether an aquifer here uses lava instead of water. The threshold is 0.3.
  - [String][Double][NBT Compound / JSON Object] vein\_toggle: Affects ore vein type, vertical range and richness, given a vein of the appropriate type can generate at the location. If the Y coordinate is between 0 and 50 (inclusive), and the noise value is greater than 0.0, the vein is a copper vein. If the Y coordinate is between -60 and -8 (inclusive), and the noise value is less than or equal to 0.0, the vein is an iron vein. Veins generate where this value is at least 0.4 at 20 blocks from the limits and at least 0.6 at the limits, with a linear falloff.
  - [String][Double][NBT Compound / JSON Object] vein\_ridged: Controls which blocks are part of a vein. If greater than or equal to 0.0, the block is not part of a vein. If less than 0.0, the block has a 30% probability to be replaced by either the vein type's filler block, or possibly an ore block.
  - [String][Double][NBT Compound / JSON Object] vein\_gap: Affects which blocks in a vein are ore blocks. If this is less than or equal to -0.3, the vein type's stone block is placed. Otherwise, with a probability equal to the absolute value of **vein\_toggle** mapped from 0.4 - 0.6 to 10% - 30%, with values outside of this range clamped, an ore block is placed, with a further 2% chance for the ore block to be a raw metal block.
  - [String][Double][NBT Compound / JSON Object] temperature: The temperature values only for biome placement. Note that this field and the following five fields do not affect terrain shape, as terrain generation is defined in **final\_density**.
  - [String][Double][NBT Compound / JSON Object] vegetation: The humidity values only for biome placement.
  - [String][Double][NBT Compound / JSON Object] continents: The continentalness values only for biome placement.
  - [String][Double][NBT Compound / JSON Object] erosion: The erosion values only for biome placement and aquifer generation.
  - [String][Double][NBT Compound / JSON Object] depth: The depth values only for biome placement and aquifer generation.
  - [String][Double][NBT Compound / JSON Object] ridges: The weirdness values only for biome placement.

## Final density

`final_density` is the main density function that determines whether a block position should be solid or air. If the function returns a value greater than 0, the noise settings' **default\_block** is placed. Otherwise, either air or the **default\_fluid** is placed, decided by the aquifer logic. Only afterward, the **default\_block** is replaced with other blocks using the surface rules.

Setting the final density is set to 0 results in a void dimension, similarly setting it to 1 would completely fill the world with stone.

An example noise settings file

`data/minecraft/worldgen/noise_settings/overworld.json`

```
{
  "sea_level": -64,
  "disable_mob_generation": false,
  "aquifers_enabled": false,
  "ore_veins_enabled": false,
  "legacy_random_source": false,
  "default_block": {
    "Name": "minecraft:stone"
  },
  "default_fluid": {
    "Name": "minecraft:water",
    "Properties": {
      "level": "0"
    }
  },
  "noise": {
    "min_y": -64,
    "height": 384,
    "size_horizontal": 2,
    "size_vertical": 2
  },
  "noise_router": {
    "barrier": 0,
    "fluid_level_floodedness": 0,
    "fluid_level_spread": 0,
    "lava": 0,
    "temperature": 0,
    "vegetation": 0,
    "continents": 0,
    "erosion": 0,
    "depth": 0,
    "ridges": 0,
    "initial_density_without_jaggedness": 0,
    "final_density": 0,
    "vein_toggle": 0,
    "vein_ridged": 0,
    "vein_gap": 0
  },
  "spawn_target": [],
  "surface_rule": {
    "type": "minecraft:sequence",
    "sequence": []
  }
}
```

### Flat world

Using the `y_clamped_gradient` density function, a flat world can be created. In the following example positions at Y=-64 get a density of 1 and positions at Y=320 get a density of -1.

```
{
  "type": "minecraft:y_clamped_gradient",
  "from_y": -64,
  "to_y": 320,
  "from_value": 1,
  "to_value": -1
}
```

### Noises

To bring some variety to the world, a noise is needed. By adding the previous `y_clamped_gradient` to a noise, the height of the terrain is based on a noise that varies along the X and Z coordinates.

```
{
  "type": "minecraft:add",
  "argument1": {
    "type": "minecraft:y_clamped_gradient",
    "from_y": -64,
    "to_y": 320,
    "from_value": 1,
    "to_value": -1
  },
  "argument2": {
    "type": "minecraft:noise",
    "noise": "minecraft:gravel",
    "xz_scale": 2,
    "y_scale": 0
  }
}
```

The noise can be scaled to alter the results. Using `"xz_scale": 0.5` makes the terrain smoother.

To get overhangs, the noise also needs to vary along the Y coordinate. This can be done with `"xz_scale": 1` and `"y_scale": 1`.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.18.2 | | | pre1 | | | | Added noise router to noise settings |
| 1.21.9 | | | 25w31a | | | | Replaced `initial_density_without_jaggedness` with `preliminary_surface_level` determining the preliminary surface directly, instead of by scanning the initial density. |

## Navigation
