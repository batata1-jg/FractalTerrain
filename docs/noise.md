# Noise

> **Source:** <https://minecraft.wiki/w/Noise>  
> **Revision:** 3290722 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

For sounds that make noise, see Sound.

This feature is exclusive to *Java Edition*.

A **noise** is a technical JSON file that can be referenced by a density function and surface rule. They are stored within a data pack in the folder `data/<namespace>/worldgen/noise`.

In addition to being called by density functions and surface rules, there are also some noises with hard-coded usage:

- `minecraft:surface`: Affects the surface layer thickness in surface rules.
- `minecraft:surface_secondary`: Used as the thickness of additional surface layer in surface rules.
- `minecraft:clay_bands_offset`: Used to generate badland terracotta bands in `bandlands` surface rules.
- `minecraft:badlands_pillar`, `minecraft:badlands_pillar_roof`, `minecraft:badlands_surface`: Used when generating badland hoodoos in `minecraft:eroded_badlands` biome.
- `minecraft:iceberg_pillar`, `minecraft:iceberg_pillar_roof`, `minecraft:iceberg_surface`: Used when generating iceberg terrain feature in `minecraft:frozen_ocean` and `minecraft:deep_frozen_ocean` biomes.

## JSON format

- [NBT Compound / JSON Object]: Root object.
  - [Int] firstOctave: First octave. If [Boolean] legacy\_random\_source in the noise settings is true, it must be an integer less than or equal to 1, otherwise the value range is not unlimited.
  - [NBT List / JSON Array] amplitudes: Amplitudes of sub-noise. The frequency of a sub-noise at `index` (from 0) in the list is `2^(- firstOctave + index)`, and the amplitude (the value of the sub-noise ranges from `-amplitude` to `+amplitude`) of this sub-noise is about `1.04 * doubleValueAtIndex * 2^(sizeOfThisList - index - 1) / (2^sizeOfThisList - 1)` (assuming the range of 3D Improved Perlin Noise is ±1.04). For each sub-noise, two 3D improved perlin noises are created and their average is taken. The final range of this noise value is `± 10 * sumOfSubNoises / ( 3 * ( 1 + 1 / m ) )`, where m is the number of elements in the list after removing the leading and trailing zero elements. If [Boolean] legacy\_random\_source in the noise settings is true, the length must be less than or equal to `1-firstOctave`. If false, the length is unlimited.
    - [Double]: A double value for a sub-noise.

## History

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Noise?action=edit&section=).

## External links

- [Noise Generator on misode.github.io](https://misode.github.io/worldgen/noise/)

## Navigation
