# Feature

> **Source:** <https://minecraft.wiki/w/Feature>  
> **Revision:** 3630050 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

For other uses, see Feature (disambiguation).

This article is about generated features in the world. For the structures, see Structure.

Not to be confused with Terrain features.

**Features** (also known as **generated features**) are small decorators generated in each chunk after terrain generation. This page lists generated features in a *Minecraft* world.

## Overworld

### Aboveground

| Feature | Description |
| --- | --- |
| Forest rock | A boulder made entirely of mossy cobblestone found in old growth taiga biomes. |
| Iceberg | A small island-like ice feature made out of blue ice or packed ice, and snow, found in frozen oceans and deep frozen oceans. |
| Blue ice | Small blobs of blue ice adjacent to packed ice blocks, generating at or below the ocean surface. |
| Freeze top layer | Ice on water and snow on blocks. In *Bedrock Edition*, it is technically not a feature. |
| Ice spike | Tall spires made of packed ice found in the ice spikes biome. In order to generate using the /place command, the position given is required to be on a snow block and in an ice spikes biome. |
| Ice patch | Disk-like feature of packed ice. |
| Lava lake | A small, widespread feature that contains a volume of lava. |
| Disk | Small circular patches of clay, sand (sandstone if above air), grass (dirt if under solid-material block or water), and gravel found throughout the world. |
| Bonus Chest | A chest surrounded by torches generated near the player's spawn if the "Bonus chest" option is toggled on. |
| Void start platform | A 33 by 33 platform of stone centered on a block of cobblestone. Only generates in the void biome or the Void superflat preset at the bottom of the world. |
| Desert Well | A structure-like feature made of sandstone blocks and slabs which contains water source blocks and suspicious sand inside. |
| Spring | A water or lava source block that generates on the side of a hill, cave or ravine. |
| Pile | A pile of blocks that only generate in villages. There are 5 variants of pile: Hay, Ice, Melon, Pumpkin and Snow. |
| Sulfur Spring | A pool of sulfur, sulfur spikes and potent sulfur surrounded with tuff, granite, and cinnabar, used to indicate the sulfur caves biome. |

### Underground

| Feature | Description |
| --- | --- |
| Lava lake | A small, widespread feature that contains a volume of lava. |
| Monster room | A structure-like feature made of cobblestone and mossy cobblestone that contains a monster spawner and several loot chests. |
| Fossil | A rarely-occurring skeletal feature composed of bone blocks and sometimes also coal ore or diamond ore. |
| Dripstone cluster | Some pointed dripstone blocks generated hanging from the ceiling. |
| Large dripstone | Stalagnate composed entirely of dripstone blocks. |
| Pointed dripstone | A single stalagmite or stalactite composed of pointed dripstone blocks. |
| Underwater magma | Group of magma blocks found underwater. |
| Amethyst geode | Large geodes consisting of amethyst and budding amethyst blocks surrounded by a layer of calcite and an outer layer of smooth basalt. |
| Ore | A feature consisting of a natural deposit of ores or other blocks. |
| Sulfur Pool | A body of water surrounded by sulfur blocks with potent sulfur at the bottom in the sulfur caves biome. |
| Sulfur Spike | A small feature of sulfur blocks with one or multiple sulfur spikes in the sulfur caves biome. |

## The Nether

| Feature | Description |
| --- | --- |
| Delta | One-block-deep sheets of constrained lava. |
| Basalt column | Vertical clusters of basalt blocks found within basalt delta biomes |
| Basalt pillar | Tall features made up of basalt block. |
| Glowstone blob | Piles of glowstone. |
| Ore | A feature consisting of a natural deposit of ores or other blocks. |
| Blob‌[*Java Edition only*] | A feature consisting of a natural deposit of basalts or blackstones. |
| Spring | A single lava source block that generates inside blocks or open to air. |
| Fire patch | A patch of fire or soul fire. |

## The End

| Feature | Description |
| --- | --- |
| Exit portal | The exit portal in the End. |
| End spike | Tall spikes made of obsidian. |
| End gateway | End gateway block surrounded with bedrock. |
| Small island | Hemispherical clusters of End stone blocks. |
| End platform | Square of obsidian at the entity spawnpoint in the End. |

## Plant-like

| Feature | Description |
| --- | --- |
| Vines | A single vine block found in jungle biomes and lush cave biomes. |
| Bamboo | Bamboo plant with optional podzol found in jungle biomes. |
| Glow lichen | A single glow lichen block found underground. |
| Sculk patch | A feature that places sculk catalysts and sculk shriekers. |
| Huge mushroom | Tree-like features that consist of mushroom blocks. |
| Tree | A feature consisting of logs and appropriate leaves. |
| Coral reef | Coral reefs are features that consist of coral, coral fans, coral blocks and sea pickles. In Bedrock Edition, the coral reefs can also consist of dead coral, dead coral fans and dead coral blocks. |
| Chorus plant | A plant-like feature of chorus flower in the End. In *Bedrock Edition*, it is technically not a feature. |
| Vegetation | Patches of vegetation like flowers and grass. |

### Composed features

The following features are composed of multiple plant-like features mentioned above.

*Java Edition*:

| Configured feature ID | Included configured features |
| --- | --- |
| `bamboo_vegetation` | `patch_grass_jungle` `fancy_oak` `jungle_bush` `mega_jungle_tree` |
| `dark_forest_vegetation` | `oak` `huge_brown_mushroom` `huge_red_mushroom` `dark_oak` `birch` `fancy_oak` |

*Bedrock Edition*:

| Feature ID | Included features |
| --- | --- |
| `legacy:flower_forest_foliage_feature` `legacy:forest_foliage_feature` `legacy:roofed_forest_foliage_feature` | `huge_mushroom_feature` `scatter_tall_grass_around_forest_foliage_feature` `double_plant_feature` `select_roofed_tree_feature` `select_birch_tree_feature` `fancy_oak_tree_feature` `select_oak_tree_feature` |
| `legacy:swamp_foliage_feature` | `seagrass_feature` `scatter_tall_grass_around_tree_feature` `huge_mushroom_feature` `swamp_tree_feature` |

## Generation

Main article: World generation § Features

Features are generated for a given chunk after the terrain has been formed. When features are generated, they can spill over into neighboring chunks that have been previously generated. Thus, a tree at the edge of the generated world may be overwritten by a village before the player reaches it. It is also theoretically possible for two worlds generated with the same seed, from the same version of *Minecraft*, to differ slightly depending on the players' travel routes, because the chunk generation order may determine which of two conflicting features overwrite or suppress the other.

## History

This section is missing information about: additional history

Please expand the section to include this information. Further details may exist on the [talk page](https://minecraft.wiki/w/Talk:Feature).

### *Java Edition*

| Java Edition Classic | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.0.14a | | | | | | | Added trees. |
| Java Edition Indev | | | | | | | |
| [March 21, 2010](https://blog.omniarchive.uk/post/464021044/caves-and-features/) | | | | | | | Notch describes an implementation for Terrain Features. |
| Java Edition Infdev | | | | | | | |
| 20100616-1808 | | | | | | | Added springs. |
| 20100625-1917 | | | | | | | Added dungeons. |
| Java Edition Alpha | | | | | | | |
| v1.2.0 | | | preview | | | | Added the Nether, which contains ceiling springs and glowstone blobs. |
| v1.2.6 | | | | | | | Added water and lava lakes. |
| Java Edition Beta | | | | | | | |
| 1.8 | | | Pre-release | | | | Added disks. |
| *Java Edition* | | | | | | | |
| 1.0.0 | | | Beta 1.9 Prerelease | | | | Roses generate naturally once again. |
| 1.0.0 | | | Beta 1.9 Prerelease 4 | | | | Added the exit portal and End spikes along with the End. |
| 1.2.1 | | | 12w04a | | | | Added desert wells. |
| 1.3.1 | | | 12w16a | | | | Added bonus chests. |
| 1.5 | | | 13w02a | | | | Added hidden lava spring in the Nether. |
| 1.7.2 | | | 13w36a | | | | Added forest rocks, ice spikes and ice patches. |
| 1.9 | | | 15w31a | | | | Added End gateways. |
| 15w37a | | | | Added void start platforms. |
| 1.10 | | | 16w20a | | | | Added fossils. |
| 1.13 | | | 18w15a | | | | Added icebergs. |
| 1.16 | | | 20w06a | | | | Added basalt pillars. |
| 20w15a | | | | Added deltas and basalt columns. |
| Added basalt blobs and blackstone blobs. |
| 1.17 | | | 20w45a | | | | Added amethyst geodes. |
| 20w49a | | | | Added dripstone features. |
| 21w06a | | | | Added underwater magma. |
| 1.18 | | | 21w40a | | | | Water lakes no longer generate, as aquifers provide an effective substitute.[1] |
| 21w41a | | | | Freeze top layer now becomes a feature technically. |

### *Bedrock Edition*

| Pocket Edition Alpha | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v0.1.0 | | | | | | | Added springs. |
| v0.9.0 | | | build 1 | | | | Added dungeons, forest rocks, ice spikes, ice patches, water and lava lakes, disks, and desert wells. |
| v0.12.1 | | | build 1 | | | | Added the Nether, which contains glowstone blobs, ceiling springs and hidden lava. |
| *Pocket Edition* | | | | | | | |
| 1.0.0 | | | alpha 0.17.0.1 | | | | Added the exit portal, End gateways, and End spikes. |
| 1.1.3 | | | alpha 1.1.3.0 | | | | Added fossils. |
| *Bedrock Edition* | | | | | | | |
| 1.2.0 | | | beta 1.2.0.2 | | | | Added bonus chests. |
| 1.4.0 | | | beta 1.2.14.2 | | | | Added icebergs. |
| 1.16.0 | | | beta 1.16.0.51 | | | | Added basalt pillars. |
| beta 1.16.0.57 | | | | Added deltas and basalt columns. |
| 1.16.220 | | | beta 1.16.220.50 | | | | Added the dripstone clusters. They don't naturally generate yet and are only accessible through add-ons. |
| 1.17.0 | | | beta 1.17.0.50 | | | | Dripstone clusters now generate throughout caves in the Overworld. |
| Added amethyst geodes. |
| 1.18.0 | | | beta 1.18.0.21 | | | | Water lakes are removed, as aquifers provide an effective substitute.[2] |
| beta 1.18.0.22 | | | | Added underwater magma.[3] |

### Legacy Console Edition

| Legacy Console Edition | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Xbox 360 | Xbox One | PS3 | PS4 | PS Vita | Wii U | Switch |  |
| TU1 | CU1 | 1.00 | 1.00 | 1.00 | Patch 1 | 1.0.1 | Added dungeons, and water and lava lakes. |
| TU5 | Added bonus chests. |
| TU9 | Added the exit portal, End gateways, and End spikes. |
| TU12 | Added desert wells. |
| TU19 | CU7 | 1.12 | 1.12 | 1.12 | Added hidden lava spring in the Nether.[*[is this the correct version?](https://minecraft.wiki/w/Talk:Feature)*] |
| TU31 | CU19 | 1.22 | 1.22 | 1.22 | Patch 3 | Added forest rocks, ice spikes and ice patches. |
| TU43 | CU33 | 1.36 | 1.36 | 1.36 | Patch 13 | Added fossils. |
| TU46 | CU36 | 1.38 | 1.38 | 1.38 | Patch 15 | Added End gateways. |
| TU69 |  | 1.76 | 1.76 | 1.76 | Patch 38 |  | Added icebergs. |

### *New Nintendo 3DS Edition*

| *New Nintendo 3DS Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.1.0 | | | | | | | Added dungeons. |
| 1.7.10 | | | | | | | Added exit portal and End gateways. |
| 1.9.19 | | | | | | | Added fossils. |

### *Minecraft Education*

| *Minecraft Education* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.0.21 | | | | | | | Added bonus chests. |

## Issues

Issues relating to "Feature" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%2C%20MCPE%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22Feature%22%29%20ORDER%20BY%20resolution%20DESC).

## References

1. ["Minecraft Snapshot 21w40a"](https://www.minecraft.net/en-us/article/minecraft-snapshot-21w40a) – Minecraft.net.
2. ["Minecraft Beta - 1.18.0.21 (Xbox One/Windows 10/Android)"](https://feedback.minecraft.net/hc/en-us/articles/4411290325901) – feedback.minecraft.net, October 14, 2021.
3. [MCPE-141376](https://bugs.mojang.com/browse/MCPE-141376) – [Experimental] Magma blocks doesn't generate at the bottom of the underwater cave – resolved as "Fixed".

## Navigation
