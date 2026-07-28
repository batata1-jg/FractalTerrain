# Terrain features

> **Source:** <https://minecraft.wiki/w/Terrain_features>  
> **Revision:** 3665799 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**upcoming** — 1 occurrence(s):

- - In dappled forests, coarse dirt and grass blocks alternate as top layer above water.​[*upcoming: Third Drop 2026*]

---
This article is about specific terrain generation features. For features generated after terrain generation, see Feature.

This page lists **terrain features** that are created as part of the world generation.

## Overworld

### General terrain generation

Main article: World generation § Terrain

The surface in the Overworld is generated using 3D Perlin noise, which creates pseudo-random variation. More variation is created by three noise paramters: continentalness, erosion, and peaks and valleys. These are also tied to biome placement.

Some features created by this algorithm include:

#### Hills and lakes

Hills in the game can vary from gradual slopes to steep cliffs. They can occur anywhere in the world, including oceans, flatland, or mountains. Hills in an ocean biome can reach high enough to create islands. Similarly, terrain variation can also cause holes and pits, sometimes forming lakes. On an amplified world, hills in most biomes are often extended and form extreme cliffs.

Higher erosion values often result in more "windswept" hills with steep cliffs and generally lower altitudes. At lower erosion, hills are smooth or almost flat, but the terrain can reach much higher elevation.

#### Mountains and valleys

When the erosion value reaches lower values, peaks and valleys start to contribute more to the terrain variation. The general altitude depends on the continentalness.

Mountains are high elevation terrain that have jagged peaks and higher land. Mountainous terrain often includes valleys, slopes, cliffs, and peaks. Sometimes, mountains reach the terrain generation limit of Y=256, where they are cut off and form flat plateaus combined with rugged terrain.

In contrast to windswept hills, however, mountains and valleys are smoothly shaped with rolling hills. **Plateaus** occur at slightly higher erosion or lower continentalness, which are similar to mountains but flattened at around Y=130. More inland, both mountains, valleys, and plateaus can stretch over huge areas with much higher elevation. At the border of different terrain types, steep slopes or cliffs form.

Common terrain features created by peaks and valleys (weirdness) are **mountain rings**. Mountain peaks or plateaus can enclose lower valleys, although they are sometimes incomplete. When the weirdness reaches extreme values, these valleys can reach extreme depths and form craters, sometimes reaching the bottom bedrock layers. When this occurs, stone walls more than a hundred blocks tall are formed to separate the water from the lava above the bedrock.

The peaks and valleys noise usually creates rivers between mountains or plateaus. Depending on the continentalness, these vary in width and depth. High-erosion terrain like swamps can also be affected, but oceans and mushroom fields are not.

#### Cliff

Not to be confused with Stony Shore.

Oceanic cliffs are steep vertical slopes that can sometimes generate when mountainous terrain borders an ocean. Oceans near cliffs are often deep and sometimes small. The cliffs expose many caves, while the surface terrain ends abruptly.

#### Fjord

"Fjord" redirects here. For the mission in *Minecraft Dungeons*, see Dungeons:Frosted Fjord.

Fjords happen when rivers cut through high-medium elevation terrain. Rivers are deeper here than usual.

#### Floating island

Floating islands are structures that float in mid-air. Floating islands are normally just small chunks of floating dirt and stone found near cliffs, but on rare occasions they can be large structures that even have springs and trees on them. Floating islands are most frequently found in or near windswept hills biomes and their variants, as well as windswept savannas.

Floating islands and overhangs are common when the erosion is high, but not at its maximum. This results in windswept terrain often surrounding swamps, with flatland terrain nearby.

### Sea level

Air on altitude Y=62 and lower is replaced by water. This occurs mostly in river and ocean biomes – areas in the terrain generation with low continentalness or peaks and valleys – and can form massive, deep water bodies. The sea level can also create lakes in land biomes, or fill deep craters.

Below Y=-55, all air is replaced by lava, forming a similar "lava level". This mostly applies to caves, as the terrain rarely reaches such depths.

### Surface

For a detailed overview of noise parameters and surface blocks per biome, see World generation § Surface.

Based on the depth value calculated by the terrain generation, water exposure, the biome, and the dimension, the world is "filled" with several blocks in layers. The top layer is always defined, and the following layers are determined by a surface depth noise, roughly 5 blocks deep.

#### Terrain

All blocks that make up the terrain are placed depending on the dimension and altitude. In the Overworld, this is as follows:

- Bedrock: In *Java Edition*, one full layer at Y=-64, followed by a randomized gradient that places bedrock up to layer Y=-59. In *Bedrock Edition*, two full layers at Y=-64 and -63, followed by a noise that randomly places bedrock up to Y=-60, meaning that they cannot generate "floating", and the pattern is always the same regardless of world seed. The bedrock layers cannot be overridden by other terrain features.
- Deepslate generates up to Y=0. Above, a gradient randomly places decreasing amounts of deepslate up to layer Y=8.
- Stone generates anywhere else as terrain block.

#### Surface layers

The uppermost layers of the terrain are converted to a biome-dependent material. The first layer is usually grass blocks, the following few layers are dirt. In shallow water, the first and following layers are always dirt, allowing disks to generate. In deeper water, the first layer is gravel and the following layers are stone. A few biomes override this.

- In deserts, beaches, and warm oceans,‌[*JE only*] the first few layers are sand. A secondary surface depth generates sandstone, reaching deeper in deserts. Except warm oceans,‌[*JE only*] this is not applied in deep water.

- In lukewarm oceans and warm oceans,‌[*BE only*] only the first layer in deep water is sand.

- In ice spikes, the first layer above water is snow blocks.

- In mushroom fields, the first layer above water is mycelium.

- In dripstone caves, the first layer above water is stone.

- In mangrove swamps, the first few layers excluding deep water are mud.
- In jagged peaks, the first layer above water is snow blocks and the following layers are stone.[a][b]

- In *Bedrock Edition*, in sulfur caves as well as frozen oceans and jagged peaks, below any water, the first few layers are stone.

When gravel or sand generates floating, it gets replaced by stone and sandstone, respectively. The surface layers do not apply to caves, meaning these blocks can still generate floating, and other blocks can be exposed.

#### Special surface layers

"Strip" redirects here. For the mechanic used by axes, see Axe § Stripping and scraping.

The following biomes have special surface layers, also known as **strips**. This is created by 2D noise, making blocks appear in varying patches, blobs, and strips.

- In wooded badlands, above roughly layer 97, coarse dirt and grass blocks alternate as top layer above water.
- In dappled forests, coarse dirt and grass blocks alternate as top layer above water.​[*upcoming: Third Drop 2026*]
- In old growth taigas, coarse dirt, podzol, and grass blocks alternate as top layer above water.
- In stony peaks, calcite and stone alternate as the first few layers, not applied in deep water. This forms long strips across the entire biome.
- In stony shores, gravel and stone alternate similarly, forming strips. Floating gravel is replaced by stone.
- In groves and snowy slopes,[b] powder snow and snow blocks alternate as the first layer, forming patches of powder snow.[a]
  - In the first layers below, powder snow alternates with dirt in groves or the mountain slopes noise in snowy slopes. This results in powder snow patches reaching depth, while snow blocks cover only the surface.
- In frozen peaks, ice replaces the top few layers in small blobs. Packed ice generates in larger strips between snow blocks, both in the top few layers.[a][b]
- In windswept hills, as well as frozen oceans in *Bedrock Edition*, stone replaces most dirt and grass in the first few layers, making the latter generate in patches.
- Similarly, in windswept savannas, stone generates in strips in the first few layers. The top grass layer is replaced by coarse dirt in some patches.
- Windswept gravelly hills use a similar noise to windswept hills but with some gravel alternating in the first few layers. In *Bedrock Edition*, the noise is different and most of the biome is covered in gravel, with a few one-block strips of grass blocks (or dirt in shallow water).

#### Mountain slopes

The jagged peaks, frozen peaks, and snowy slopes biomes all use a mechanism to determine steep slopes. North and east cliffs replace other blocks in the first few layers with stone, or packed ice in frozen peaks, where only a thin snow layer generates on top.

#### Badlands terracotta

All badlands biomes have more complex surface algorithms.

- Deep water generates as usual in *Java Edition* with one gravel layer above stone, or entirely stone in *Bedrock Edition*.
- Shallow water replaces the top layer with white terracotta in *Java Edition* only.
- The following above-ground terrain is covered with one red sand layer followed by some orange terracotta layers.
  - Floating red sand is replaced by red sandstone.‌[*JE only*]
- Above roughly Y=74, the top-most layer is replaced by terracotta alternating using noise with *hoodoo terracotta*. The first layers below are always hoodoo terracotta.
- Above Y=256, orange terracotta replaces hoodoo terracotta, visible only in amplified worlds.

"Hoodoo terracotta" is generated as follows. Each world seed generates 192 layers of random stained terracotta colors (red, orange, yellow, brown, white, and light gray), corresponding to the Y-coordinates above *Y=63*. At each horizontal coordinate, each layer may shift up and down by at most 7 blocks based on noise, creating unique variation.

#### Swamp marshland

In swamps and mangrove swamps, a noise creates variation near the sea level. At layer Y=63, the surface materials are sometimes replaced by water, which can extend slightly deeper in mangrove swamps. This creates some undeep marshland in the terrain that already alters between shallow water and flat land a lot.

1. In *Bedrock Edition*, snow blocks may also generate in shallow water.
2. Also affected by mountain slope mechanisms

#### Erosion

Main article: Erosion

This feature is exclusive to *Bedrock Edition*.

When the surface depth is less than or equal to 0, the top layer is stripped away, exposing the layers *below* all surface blocks, ususally stone. In badlands, they are made of orange terracotta. Erosions in frozen oceans occur at the freeze layer and replace it with air, followed by ice or water below.

Commonly, minerals can be found in these, generally coal ore and iron ore. Due to a bug, they do not generate in *Java Edition*.

### Hoodoo

**Hoodoos** are tall spike-like structures found in badlands at the red sand level. While this structure is found exclusively in eroded badlands, all badlands biomes actually have this structure, but set to false except for eroded badlands. Hoodoos are entirely made up of hoodoo terracotta, making it occur at much lower altitudes, although they can reach more than 100 blocks tall.

### Iceberg

This article is about the terrain feature known as "large iceberg". For the smaller feature known as "cone iceberg", see Iceberg (feature).

An **iceberg** is a large terrain feature composed of packed ice and snow blocks.

Large icebergs generate in frozen oceans and deep frozen oceans. They consist of packed ice, and can be topped with snow blocks. Icebergs generate in a wide variety of shapes and sizes, ranging from small islands to giant mountain-like icebergs. They can also generate with cave-like holes (these might be related to the carved recesses in cone icebergs)​[*more information needed*] in them, which sometimes pass through to the other side of the iceberg.

There are often blue ice features attached to them.

### Noise cave

Main article: Cave § Noise caves

**Noise caves** are generated using a noise. They come in the form of **cheese caves**, **spaghetti caves**, and **noodle caves**. By adjusting noise frequency, hollowness (for cheese caves), and thickness (for spaghetti caves, noodle caves, and noise pillars), noise caves can vary in extremely diverse ways. When generating noise caves, the game firstly generates a random noise field, and "smudges" it using a mathematical trick called Perlin noise. These processes then result in a 3D noise image.

Unique features of noise caves are **aquifers**. Bodies or water or lava may form at different altitudes, separated from other bodies by thin walls or ceilings.

Larger noise caves may feature **noise pillars**, formations of rock connecting the ceiling with the floor based on noise, which can have varying sizes.

Some noise caves may also reach the surface, exposing stones or creating floating islands.

### Carvers

Carver caves are narrow, winding tunnels similar to noise caves, but with a clear end and a central starting room. They can generate at any altitude and connect to the surface, other caves, or structures. Sometimes, carver caves are filled with water or lava.

Canyons are similar to carver caves, but shaped like a tall ravine and relatively shorter. Canyons may generate entirely underground, underwater, or exposed to the surface, and they can have different sections similar to aquifers. The walls of a canyon are steep cliffs with some edges and natural variation. They can also reach the lava level.

### Ore veins

Main article: Ore vein

Unlike ore features, ore veins are terrain features that carve through the world and place several blocks. There are two types: veins with copper ore, granite as filler material, and occasional raw copper blocks, forming in the stone layer, and veins with deepslate iron ore, tuff as filler, and some raw iron blocks, forming in the deepslate layer. Ore veins are massive branching networks, sometimes exposed in a cave, but they cannot replace air or water.

## The Nether

### General terrain generation

Main article: World generation § Terrain

Terrain in the Nether is generated similarly to the Overworld with 3D Perlin noise. Instead of surface-oriented parameters, the game has two pairs for the solid terrain at the ceiling and ground, and the hollow space in between. This results in cave-like terrain where most air is found in the middle between the ceiling and the ground.

Floating islands, cliffs, and overhangs are common.

### Lava sea

Similar to the water sea level in the Overworld, a lava sea forms below Y=31 in the Nether, which happens quite often. They can stretch for hundreds of blocks in any direction, and are usually bordered by netherrack, or occasionally soul sand, gravel, and magma blocks. Striders can spawn in lava seas.

Unlike with Overworld oceans, lava seas are not handled as a biome. In *Bedrock Edition*, the lava sea is biome-dependent and can also be generated in the Overworld, replacing the water sea.

### Surface

For a detailed overview of noise parameters and surface blocks per biome, see World generation § Surface.

Based on the depth value calculated by the terrain generation, the biome, and the dimension, the world is "filled" with several blocks in layers.

#### Terrain

All blocks that make up the terrain are placed depending on the dimension and altitude. In the Nether, this is as follows:

- Bedrock: In *Java Edition*, one full layer at Y=0, followed by a randomized gradient that places bedrock up to layer Y=5. In *Bedrock Edition*, two full layers at Y=0 and 1, followed by a noise that randomly places bedrock up to Y=4 meaning that they cannot generate "floating", and the pattern is always the same regardless of world seed. The bedrock layers cannot be overridden by other terrain features.
- Bedrock: Another layer of bedrock generates similarly at the Nether roof. This is one full layer at Y=127, followed by a gradient,‌[*JE only*] or noise‌[*BE only*] that places blocks down to Y=122/‌[*JE only*]123.‌[*BE only*]
- Netherrack generates anywhere else as terrain block.

#### Surface layers

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Terrain_features?action=edit&section=).

#### Nether erosions

Main article: Erosion § The Nether

In the Nether, erosions generate the same size and shape as they do in the Overworld. Unlike their Overworld counterparts, however, Nether erosions replace the ground with netherrack instead of stone. Nether erosions can also expose ores, mainly Nether quartz ore and Nether gold ore.

Notably, erosions generate independent of the y-coordinate; if an erosion generates in an overhang in the Nether, an identical erosion is guaranteed to generate at the exact same x and z coordinates on the ground below such an overhang.

### Nether carvers

Carver caves and canyons generate similarly in the Nether as how they do in the Overworld. Below Y=31, they are filled with lava in *Java Edition*. In *Bedrock Edition*, they are filled with air and some biome features, and are separated from the lava sea by walls. They can often expose bedrock, but not at the ceiling.

## The End

### Central island

The center of the End is a large, asteroid-like island composed entirely of End stone, floating in the void. It features the exit portal in the center, surrounded by 10 End spikes in a circle. The island is home to the ender dragon, and serves as the arena where it is fought.

At a distance of 1000 blocks away, an endless expanse of additional islands begins, away from the main island. These consist of large islands, about the size of the main island, and smaller ones, which are usually thin and small.

### Outer islands

The outer End islands are found 1000 blocks away from the central island, also made entirely of End stone. They vary in size from large islands to smaller "mini islands". Generated structures such as End cities and End ships spawn here, along with chorus trees and erosions. The player can be taken to the End islands through the End gateway.

### End erosions

Main article: Erosion § The End

Erosions generate in the End as they would in the Overworld and the Nether, but they never expose any ores. End erosions may generate on both the central island and outer islands, and chorus trees can occasionally take root in the erosions.

## History

This section needs cleanup to comply with the style guide.

[[discuss](https://minecraft.wiki/w/Talk:Terrain_features)]

Please help [improve](https://minecraft.wiki/w/Terrain_features?action=edit&section=1) this section. The [talk page](https://minecraft.wiki/w/Talk:Terrain_features) may contain suggestions.
*Reason:* move features to Feature

This section needs to be updated.

Please update this section to reflect recent updates or newly available information. The talk page may contain suggestions.
**Reason:** Add all 1.17-1.21 terrain feature

### *Java Edition*

| Java Edition pre-Classic | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Cave game tech test | | | | | | | Development on "Cave Game" started; caverns added. |
| Java Edition Classic | | | | | | | |
| 0.0.12a | | | | | | | Added ocean around map. |
| 0.0.14a | | | | | | | Added trees. At this point they were simply stumps covered with a thin leaf layer. |
| 0.0.15a (Multiplayer Test 1) | | | | | | | Trees have a new shape. |
| [August 25, 2009](https://web.archive.org/web/0/https://notch.tumblr.com/post/170887079) | | | | | | | Video uploaded showing changed cavern generation; longer and narrower caverns, and bigger caves the deeper you travel. |
| Surface lava lakes shown to be possible but unlikely, despite existing as early as 0.0.12a\_03. |
| Java Edition Infdev | | | | | | | |
| 20100227-1414 | | | | | | | Temporarily removed caves, trees and ores to test basic infinite world functionality. |
| 20100320 | | | | | | | Re-added trees and ores. |
| 20100325-1545 | | | | | | | Re-added caves. |
| Redid mineral blobs. |
| 20100327 | | | | | | | Caves are removed again. |
| 20100616-1808 | | | | | | | Caves are now again implemented. |
| Java Edition Alpha | | | | | | | |
| v1.0.1 | | | | | | | Caves can now be far bigger and more expansive. |
| v1.2.0 | | | preview | | | | Added the Nether but not all Nether-related generated structures. |
| v1.2.6 | | | | | | | Added small lakes and rare lava pools, both on the surface and randomly in caves. |
| Java Edition Beta | | | | | | | |
| 1.8 | | | Pre-release | | | | Added huge mushrooms. |
| Added river biomes and vast oceans. |
| Sand and gravel beaches removed due to the changes in the terrain generation algorithm. |
| *Java Edition* | | | | | | | |
| 1.0.0 | | | Beta 1.9 Prerelease 4 | | | | Added the End and End-related main island generated structures, including the End island, the obsidian platform, the End spikes and the exit portal. |
| 1.1 | | | 12w01a | | | | Sand beaches have made a return, but the way they look and generate are not the same as before. |
| 1.2.1 | | | 12w07a | | | | The generation of beaches has been greatly improved. |
| 1.6.1 | | | 13w17a | | | | Water oases no longer generate in deserts. |
| 1.7.2 | | | 13w36a | | | | Gravel beaches have been returned to the terrain. |
| Mountains now generate as part of the "M" biome variants. |
| The desert oases appear in the desert M biome. |
| Moss stone boulders were added. |
| 1.9 | | | 15w31a | | | | Added the outer islands of the End. |
| Added chorus trees. |
| Added the End gateway portal. |
| 1.13 | | | 18w08a | | | | Caves and canyons can now generate underwater. |
| Frozen Ocean have made a return, but without customized, they look and generate, but are not the same unused. |
| 18w10d | | | | Added coral reefs. |
| 18w15a | | | | Added icebergs. |
| 1.16 | | | 20w06a | | | | Added basalt pillars. |

### *Bedrock Edition*

| Pocket Edition Alpha | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v0.1.0 | | | | | | | Added sand and gravel beaches, oceans, frozen oceans, cliffs, mountains, erosions, blobs, springs, and trees. |
| v0.9.0 | | | build 1 | | | | Added caves, lava pools, rivers, huge mushrooms, moss stone boulders, ice patches, and ice spikes. |
| Frozen oceans no longer generate. |
| Removed gravel beaches. |
| v0.12.1 | | | build 1 | | | | Added the Nether, along with lava oceans, glowstone clusters, soul sand beaches, hidden lava, gravel beaches, and Nether quartz blobs. |
| *Pocket Edition* | | | | | | | |
| 1.0.0 | | | alpha 0.17.0.1 | | | | Added the End and End-related generated structures, including the End islands, the obsidian platform, the obsidian towers, the End fountain, End cities, End gateways, and chorus trees. |
| *Bedrock Edition* | | | | | | | |
| 1.2.0 | | | beta 1.2.0.2 | | | | Added ravines. |
| 1.4.0 | | | beta 1.2.14.2 | | | | Added icebergs. |
| Added coral reefs. |
| Ravines can now generate underwater. |
| Frozen oceans have made a return, but the way they look and generate are not the same as before. |
| beta 1.2.20.1 | | | | Caves can now generate underwater. |
| 1.16.0 | | | beta 1.16.0.51 | | | | Added basalt pillars. |

### Legacy Console Edition

| Legacy Console Edition | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Xbox 360 | Xbox One | PS3 | PS4 | PS Vita | Wii U | Switch |  |
| TU5 | CU1 | 1.00 | 1.00 | 1.00 | Patch 1 | 1.0.1 | Added ravines. |
| Sand and gravel beaches removed due to the changes in the terrain generation algorithm. |
| TU9 | Added the End along with End-related structures such as the End island, the obsidian platform, the obsidian towers, and the End fountain. |
| Sand beaches have made a return, but the way they look and generate is not the same as before. |
| TU19 | CU7 | 1.12 | 1.12 | 1.12 | Water oases no longer generate in deserts. |
| TU46 | CU36 | 1.38 | 1.38 | 1.38 | Patch 15 | Added the outer islands of the End. |
| Added chorus trees. |
| Added the End gateway portal. |
| TU69 |  | 1.76 | 1.76 | 1.76 | Patch 38 |  | Added coral reefs and underwater caves and ravines. |

### *New Nintendo 3DS Edition*

| *New Nintendo 3DS Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.7.10 | | | | | | | Added chorus trees. |

## Issues

Issues relating to "Terrain features" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%2C%20MCPE%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22Terrain%20features%22%29%20ORDER%20BY%20resolution%20DESC).

## Videos

## Navigation
