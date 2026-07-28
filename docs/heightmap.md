# Heightmap

> **Source:** <https://minecraft.wiki/w/Heightmap>  
> **Revision:** 3661311 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

This article is about the heightmaps of chunk terrain. For the heightmaps controlling three-dimensional textures, see Texture set.

This article is missing information about: Heightmaps in *Bedrock Edition*
Note: Heightmaps do exist in *Bedrock Edition*, this is confirmed by feature placement behavior packs which can use `"y": "query.heightmap(variable.worldx, variable.worldz)"` in the distribution to place a feature on the surface, but no other information about heightmaps has been found.

Please expand the article to include this information. Further details may exist on the [talk page](https://minecraft.wiki/w/Talk:Heightmap).

**Heightmaps** are maps to store the Y-level of the highest block at each horizontal coordinate. They are stored in chunk files and used for various game calculations.

## Motion blocking

The last four heightmaps are based on a property called motion blocking. This is defined as follows:

- Cobwebs and bamboo saplings are considered not motion blocking.
- The block must be "solid". On *Java Edition*, this is defined using the Legacy solid property. Note that this is not the same as having a collision box. Some blocks like snow azaleas are not considered solid while having a collision box, and other blocks like banners are considered solid while having no collision box.

## Types

Several different heightmaps check and store highest block of different types, and are used for different purposes. In *Java Edition*, there are six heightmap types:

- `WORLD_SURFACE`: Stores the Y-level of the highest non-air (all types of air) block.
- `WORLD_SURFACE_WG`: Stores the Y-level of the highest non-air (all types of air) block. Used only during world generation.
- `OCEAN_FLOOR`: Stores the Y-level of the highest block whose material blocks motion. Used only on the server side.
- `OCEAN_FLOOR_WG`: Stores the Y-level of the highest block whose material blocks motion. Used only during world generation.
- `MOTION_BLOCKING`: Stores the Y-level of the highest block whose material blocks motion or blocks that contains a fluid (water, lava, or waterlogged blocks).
- `MOTION_BLOCKING_NO_LEAVES`: Stores the Y-level of the highest block whose material blocks motion, or blocks that contains a fluid (water, lava, or waterlogged blocks), except various leaves. Used only on the server side, e.g. for pillager patrol spawning.

## Usage

Heightmaps are used for various in-game calculations. For example, a lightning rod can only be triggered when there is no non-air block above it. Therefore, the game compares the Y-level of lightning rod with heightmap WORLD\_SURFACE to know if there are non-air blocks above it.

Heightmaps of both server side and client side can be seen in debug screen. The "CH" line is the value in client side heightmaps at the player's current X/Z coordinates. The "SH" line is the server side value in heightmaps at the player's current X/Z coordinates. In the two lines:

- S means WORLD\_SURFACE
- O means OCEAN\_FLOOR
- M means MOTION\_BLOCKING
- ML means MOTION\_BLOCKING\_NO\_LEAVES

`/execute positioned over` can change the execution position of the command to one block above the Y-level stored in the specified heightmap. WORLD\_SURFACE\_WG and OCEAN\_FLOOR\_WG cannot be used in this command.

## Navigation
