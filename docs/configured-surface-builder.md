# Configured surface builder

> **Source:** <https://minecraft.wiki/w/Configured_surface_builder>  
> **Revision:** 3259622 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

This page describes content that has been removed from *Minecraft*.

This feature was present in earlier versions of *Minecraft*, but has since been removed.

This feature is exclusive to *Java Edition*.

**Surface builders** control how the surface of the terrain is shaped and what blocks it is generated with. Configured surface builders are stored as JSON files within a data pack in the path `data/<namespace>/worldgen/configured_surface_builder`. They are used in world generation.

## JSON format

[NBT Compound / JSON Object] The root tag

- [String] type:[*[needs testing](https://minecraft.wiki/w/Talk:Configured_surface_builder)*] The type of surface builder to use, must be one of `"default"`, `"mountain"`, `"shattered_savanna"`, `"gravelly_mountain"`, `"giant_tree_taiga"`, `"swamp"`, `"badlands"`, `"wooded_badlands"`, `"eroded_badlands"`, `"frozen_ocean"`, `"nether"`, `"nether_forest"`, `"soul_sand_valley"`, `"basalt_deltas"`, or `"nope"`. These choices change the generation of patterns of surface materials, and in the case of "frozen\_ocean" and "eroded\_badlands", add generated icebergs and buttes, respectively. For example, the mixed patterns of stone in mountains and the terracotta of mesas are coded for through these options.
- [NBT Compound / JSON Object] config: Configuration for the surface builder.
  - [NBT Compound / JSON Object] top\_material:[*[needs testing](https://minecraft.wiki/w/Talk:Configured_surface_builder)*] The block to use for the topmost layer of the terrain.
    - [String] Name: The namespaced id of the block to use.
    - [NBT Compound / JSON Object] Properties: Block states
      - [String] *state*: A block state key and its value.
  - [NBT Compound / JSON Object] under\_material:[*[needs testing](https://minecraft.wiki/w/Talk:Configured_surface_builder)*] The block to use directly under the topmost layer of the terrain.
    - [String] Name: The namespaced id of the block to use.
    - [NBT Compound / JSON Object] Properties: Block states
      - [String] *state*: A block state key and its value.
  - [NBT Compound / JSON Object] underwater\_material:[*[needs testing](https://minecraft.wiki/w/Talk:Configured_surface_builder)*] The block to use under bodies of water.
    - [String] Name: The namespaced id of the block to use.
    - [NBT Compound / JSON Object] Properties: Block states
      - [String] *state*: A block state key and its value.

## History

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Configured_surface_builder?action=edit&section=).

## Navigation
