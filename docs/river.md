# River

> **Source:** <https://minecraft.wiki/w/River>  
> **Revision:** 3691546 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_1 Java Edition history entry newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.5** — Added bushes and firefly bushes, which can generate in rivers.

---
For other uses, see River (disambiguation).

River

|  |  |
| --- | --- |
| Blocks | Grass Block Seagrass Bush |
| Climate | |
| Temperature | 0.5 |
| Downfall | 0.5 |
| Precipitation | Yes |
| Colors | |
| Grass color | #8EB971 |
| Foliage color | #71A74D |
| Dry foliage color | #A17448 |
| Water color | #3F76E4‌[*JE only*]   #0084FF‌[*BE only*] |
| Vibrant Visuals | |
| Atmospherics | Default |
| Lighting | Default |
| Volumetric fog | Default |
| Color grading | Default |

```
{
    "title": "River",
    "rows": [
        {
            "field": "<span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:BlockSprite grass-block.png article, displayed as 16x16px|link=Grass Block|alt=|class=pixel-image|)</span>(link to Grass Block article, displayed as <span class=\"sprite-text\">Grass Block</span>)</span><br><span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:BlockSprite seagrass.png article, displayed as 16x16px|link=Seagrass|alt=|class=pixel-image|)</span>(link to Seagrass article, displayed as <span class=\"sprite-text\">Seagrass</span>)</span><br><span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:BlockSprite bush.png article, displayed as 16x16px|link=Bush|alt=|class=pixel-image|)</span>(link to Bush article, displayed as <span class=\"sprite-text\">Bush</span>)</span>",
            "label": "Blocks"
        },
        {
            "field": "",
            "label": "Climate"
        },
        {
            "field": "0.5",
            "label": "(link to Biome#Temperature article, displayed as Temperature)"
        },
        {
            "field": "0.5",
            "label": "(link to Biome#Downfall article, displayed as Downfall)"
        },
        {
            "field": "Yes",
            "label": "(link to Biome#Precipitation article, displayed as Precipitation)"
        },
        {
            "field": "",
            "label": "Colors"
        },
        {
            "field": "<span style=\"white-space: nowrap;\"><span style=\"display: inline-block; background-color: #8EB971; border: 1px solid #888; border-radius: 0.3em; color: transparent; width: 1em; height: 1em; vertical-align: -0.36em; margin-right: -0.1em\"><br></span> #8EB971</span>",
            "label": "Grass color"
        },
        {
            "field": "<span style=\"white-space: nowrap;\"><span style=\"display: inline-block; background-color: #71A74D; border: 1px solid #888; border-radius: 0.3em; color: transparent; width: 1em; height: 1em; vertical-align: -0.36em; margin-right: -0.1em\"><br></span> #71A74D</span>",
            "label": "Foliage color"
        },
        {
            "field": "<span style=\"white-space: nowrap;\"><span style=\"display: inline-block; background-color: #A17448; border: 1px solid #888; border-radius: 0.3em; color: transparent; width: 1em; height: 1em; vertical-align: -0.36em; margin-right: -0.1em\"><br></span> #A17448</span>",
            "label": "Dry foliage color"
        },
        {
            "field": "<span style=\"white-space: nowrap;\"><span style=\"display: inline-block; background-color: #3F76E4; border: 1px solid #888; border-radius: 0.3em; color: transparent; width: 1em; height: 1em; vertical-align: -0.36em; margin-right: -0.1em\"><br></span> #3F76E4</span>‌<sup class=\" nowrap Inline-Template \" title=\"\">[<i><span title=\"This statement only applies to Java Edition\">(link to Java Edition article, displayed as JE)  only</span></i>]</sup><br><span style=\"white-space: nowrap;\"><span style=\"display: inline-block; background-color: #0084FF; border: 1px solid #888; border-radius: 0.3em; color: transparent; width: 1em; height: 1em; vertical-align: -0.36em; margin-right: -0.1em\"><br></span> #0084FF</span>‌<sup class=\" nowrap Inline-Template \" title=\"\">[<i><span title=\"This statement only applies to Bedrock Edition\">(link to Bedrock Edition article, displayed as BE)  only</span></i>]</sup>",
            "label": "Water color"
        },
        {
            "field": "",
            "label": "Vibrant Visuals"
        },
        {
            "field": "(link to Vibrant Visuals/Configurations#Default atmospherics article, displayed as Default)",
            "label": "(link to Vibrant Visuals#Atmospherics article, displayed as Atmospherics)"
        },
        {
            "field": "(link to Vibrant Visuals/Configurations#Default lighting article, displayed as Default)",
            "label": "(link to Vibrant Visuals#Atmospherics article, displayed as Lighting)"
        },
        {
            "field": "(link to Fog#Vanilla volumetric fog settings article, displayed as Default)",
            "label": "(link to Fog#Volumetric fog article, displayed as Volumetric fog)"
        },
        {
            "field": "(link to Vibrant Visuals/Configurations#Default color grading article, displayed as Default)",
            "label": "(link to Vibrant Visuals#Post processing effects article, displayed as Color grading)"
        }
    ],
    "invimages": [],
    "images": [
        "River.png",
        "River Vibrant Visuals.png"
    ]
}
```

A **river** is a common aquatic biome that cuts through most land biomes. Rivers can serve as borders between various other biomes, and usually lead to oceans, although they are also able to form loops.

## Generation

Rivers generate in land whenever the PV values are on their lowest; in the deepest part of valleys. River generation does not depend on temperature or humidity, meaning that they can cut through any type of land, but they are replaced by frozen rivers in the frozen temperature zones.

The shape of rivers is determined by the PV noise. They always twist around higher landmasses, sometimes they form loops that can be small enough to have no land inside.

Rivers do not generate in swamps or mangrove swamps, but their water bodies are often connected. The depth and width of a river strongly depends on the surrounding terrain; near oceans they are mostly wide, with some islands and flooded land biomes, further inland they often dry up with barely any water. Around mountains or elevated terrain, rivers form deep fjords with tall cliffs, and they abruptly end when the continentalness reaches the highest value. Here, no rivers generate and the valleys consist of elevated land biomes.

Rivers typically serve as a division between two different biomes, with one biome variant on one side, and a different variant on the other.

## Description

A river biome on its own does not have many distinct features, most of its characteristics are created by the terrain generation or surrounding biomes. They are filled with still water up to the sea level, and the terrain below consists of default lakebed blocks, covered with seagrass. Specifically, it is separated into patches of dirt, clay, sand, and gravel.

When a river generates land, it is just covered by grass blocks with some patches of bushes. However, features from bordering biomes often leak into the river biome. This happens most notably with wooded badlands, where the grass allows many oak trees and leaf litter to generate. Due to the extended coastlines, firefly bushes and sugar cane are often found near rivers, although they are no more common than in other biomes.

No fully-passive mobs spawn within river biomes themselves, but they frequently wander into them from the surrounding biomes if they can spawn there. Drowned can spawn underwater, as can salmon and squid. In *Bedrock Edition*, no hostile mobs spawn above water.

## Mobs

The following mobs naturally spawn here:

In ***Java Edition***

| Mob | Spawn weight | Group size |
| --- | --- | --- |
| Monster category | | |
| Creeper | 100⁄615 | 4 |
| Drowned | 100⁄615 | 1 |
| Skeleton | 100⁄615 | 4 |
| Slime[note 1] | 100⁄615 | 4 |
| Spider | 100⁄615 | 4 |
| Zombie | 95⁄615 | 4 |
| Enderman | 10⁄615 | 1–4 |
| Witch | 5⁄615 | 1 |
| Zombie Villager | 5⁄615 | 1 |
| Underground water creature category | | |
| Glow Squid | 1 | 4–6 |
| Water creature category | | |
| Squid | 1 | 1–4 |
| Water ambient category | | |
| Salmon | 1 | 1–5 |
| Ambient category | | |
| Bat | 1 | 8 |

1. Spawn attempt succeeds only in slime chunks.

```
{ "hasNotes": true, "notes": { "Slime": "Spawn attempt succeeds only in slime chunks." }, "1": { "totalWeight": 615, "mobs": [ { "size": "4", "mob": "Creeper", "weight": 100 }, { "size": "1", "mob": "Drowned", "weight": 100 }, { "size": "4", "mob": "Skeleton", "weight": 100 }, { "note": "Spawn attempt succeeds only in slime chunks.", "mob": "Slime", "weight": 100, "notename": "Slime", "size": "4" }, { "size": "4", "mob": "Spider", "weight": 100 }, { "size": "4", "mob": "Zombie", "weight": 95 }, { "size": "1-4", "mob": "Enderman", "weight": 10 }, { "size": "1", "mob": "Witch", "weight": 5 }, { "size": "1", "mob": "Zombie Villager", "weight": 5 } ], "category": "monster" }, "2": { "totalWeight": 10, "mobs": [ { "size": "4-6", "mob": "Glow Squid", "weight": 10 } ], "category": "underground" }, "3": { "totalWeight": 2, "mobs": [ { "size": "1-4", "mob": "Squid", "weight": 2 } ], "category": "watercreature" }, "4": { "totalWeight": 5, "mobs": [ { "size": "1-5", "mob": "Salmon", "weight": 5 } ], "category": "waterambient" }, "5": { "totalWeight": 10, "mobs": [ { "size": "8", "mob": "Bat", "weight": 10 } ], "category": "ambient" } }
```

In ***Bedrock Edition***

| Mob | Spawn weight | Group size |
| --- | --- | --- |
| Monster category | | |
| Drowned | 100 | 1 |
| Slime[note 1] | 100 | 1 |
| Creature category | | |
| Glow Squid | 10 | 2–4 |
| Squid | 8 | 2 |
| Water creature category | | |
| Salmon | 1 | 3–5 |

1. Spawn attempt succeeds only in slime chunks.

```
{ "hasNotes": true, "notes": { "Slime": "Spawn attempt succeeds only in slime chunks." }, "1": { "totalWeight": 200, "mobs": [ { "size": "1", "mob": "Drowned", "weight": 100 }, { "note": "Spawn attempt succeeds only in slime chunks.", "mob": "Slime", "weight": 100, "notename": "Slime", "size": "1" } ], "category": "monster" }, "2": { "totalWeight": 18, "mobs": [ { "size": "2-4", "mob": "Glow Squid", "weight": 10 }, { "size": "2", "mob": "Squid", "weight": 8 } ], "category": "creature" }, "3": { "totalWeight": 16, "mobs": [ { "size": "3-5", "mob": "Salmon", "weight": 16 } ], "category": "watercreature" } }
```

## Sounds

### Music

These music tracks when the player is underwater in a river:

| Title | Preview | *Java Edition* | | *Bedrock Edition* | |
| --- | --- | --- | --- | --- | --- |
| Weight | Probability | Weight | Probability |
| Axolotl |  | 1 | 33.33% | 1 | 33.33% |
| Dragon Fish |  | 1 | 33.33% | 1 | 33.33% |
| Shuniji |  | 1 | 33.33% | 1 | 33.33% |

These music tracks play while the player is not underwater in a river:

| Title | Preview | *Java Edition* | | *Bedrock Edition* | |
| --- | --- | --- | --- | --- | --- |
| Weight | Probability | Weight | Probability |
| A Familiar Room |  | 1 | 2.70% | — | — |
| Below and Above |  | 1 | 2.70% | 1 | 2.78% |
| Broken Clocks |  | 1 | 2.70% | 1 | 2.78% |
| Clark |  | 1 | 2.70% | 1 | 2.78% |
| Comforting Memories |  | 1 | 2.70% | 1 | 2.78% |
| Danny |  | 1 | 2.70% | 1 | 2.78% |
| Dry Hands |  | 1 | 2.70% | 1 | 2.78% |
| Ebb |  | 2 | 5.41% | 2 | 5.56% |
| Featherfall |  | 1 | 2.70% | 1 | 2.78% |
| Fireflies |  | 1 | 2.70% | 1 | 2.78% |
| Floating Dream |  | 1 | 2.70% | 1 | 2.78% |
| Haggstrom |  | 1 | 2.70% | 1 | 2.78% |
| Home |  | 2 | 5.41% | 2 | 5.56% |
| Key |  | 1 | 2.70% | 1 | 2.78% |
| komorebi |  | 1 | 2.70% | 1 | 2.78% |
| Left to Bloom |  | 1 | 2.70% | 1 | 2.78% |
| Lilypad |  | 1 | 2.70% | 1 | 2.78% |
| Living Mice |  | 1 | 2.70% | 1 | 2.78% |
| Memories |  | 2 | 5.41% | 2 | 5.56% |
| Mice on Venus |  | 1 | 2.70% | 1 | 2.78% |
| Minecraft |  | 1 | 2.70% | 1 | 2.78% |
| Nightly |  | 2 | 5.41% | 2 | 5.56% |
| One More Day |  | 1 | 2.70% | 1 | 2.78% |
| O's Piano |  | 1 | 2.70% | 1 | 2.78% |
| Oxygène |  | 1 | 2.70% | 1 | 2.78% |
| Puzzlebox |  | 1 | 2.70% | 1 | 2.78% |
| Shores |  | 2 | 5.41% | 2 | 5.56% |
| Subwoofer Lullaby |  | 1 | 2.70% | 1 | 2.78% |
| Sweden |  | 1 | 2.70% | 1 | 2.78% |
| Watcher |  | 1 | 2.70% | 1 | 2.78% |
| Wet Hands |  | 1 | 2.70% | 1 | 2.78% |
| yakusoku |  | 1 | 2.70% | 1 | 2.78% |

In Creative mode, the following tracks can play:

| Title | Preview | *Java Edition* | | *Bedrock Edition* | |
| --- | --- | --- | --- | --- | --- |
| Weight | Probability | Weight | Probability |
| Aria Math |  | 1 | 3.03% | 1 | 3.13% |
| Biome Fest |  | 1 | 3.03% | 1 | 3.13% |
| Blind Spots |  | 1 | 3.03% | 1 | 3.13% |
| Dreiton |  | 1 | 3.03% | 1 | 3.13% |
| Haunt Muskie |  | 1 | 3.03% | 1 | 3.13% |
| Taswell |  | 1 | 3.03% | 1 | 3.13% |
| Key |  | 1 | 3.03% | 1 | 3.13% |
| Subwoofer Lullaby |  | 1 | 3.03% | 1 | 3.13% |
| Living Mice |  | 1 | 3.03% | 1 | 3.13% |
| Haggstrom |  | 1 | 3.03% | 1 | 3.13% |
| Minecraft |  | 1 | 3.03% | 1 | 3.13% |
| Oxygène |  | 1 | 3.03% | 1 | 3.13% |
| Mice on Venus |  | 1 | 3.03% | 1 | 3.13% |
| Dry Hands |  | 1 | 3.03% | 1 | 3.13% |
| Wet Hands |  | 1 | 3.03% | 1 | 3.13% |
| Clark |  | 1 | 3.03% | 1 | 3.13% |
| Sweden |  | 1 | 3.03% | 1 | 3.13% |
| Danny |  | 1 | 3.03% | 1 | 3.13% |
| Left to Bloom |  | 1 | 3.03% | 1 | 3.13% |
| One More Day |  | 1 | 3.03% | 1 | 3.13% |
| Floating Dream |  | 1 | 3.03% | 1 | 3.13% |
| Comforting Memories |  | 1 | 3.03% | 1 | 3.13% |
| A Familiar Room |  | 1 | 3.03% | — | — |
| Featherfall |  | 1 | 3.03% | 1 | 3.13% |
| Watcher |  | 1 | 3.03% | 1 | 3.13% |
| Puzzlebox |  | 1 | 3.03% | 1 | 3.13% |
| komorebi |  | 1 | 3.03% | 1 | 3.13% |
| yakusoku |  | 1 | 3.03% | 1 | 3.13% |
| Lilypad |  | 1 | 3.03% | 1 | 3.13% |
| Below and Above |  | 1 | 3.03% | 1 | 3.13% |
| O's Piano |  | 1 | 3.03% | 1 | 3.13% |
| Broken Clocks |  | 1 | 3.03% | 1 | 3.13% |
| Fireflies |  | 1 | 3.03% | 1 | 3.13% |

## Data values

### ID

*Java Edition*:

| Name | Identifier | Translation key |
| --- | --- | --- |
| River | `river` | `biome.minecraft.river` |

*Bedrock Edition*:

| Name | Identifier | Numeric ID |
| --- | --- | --- |
| [No displayed name] | `river` | `7` |

## Achievements

|  | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Icon | | Achievement | In-game description | Actual requirements (if different) | Gamerscore earned | Trophy type (PS) |
| PS4 | Other |
|  |  | Adventuring Time | Discover 17 biomes. | Visit any 17 biomes. Does not have to be in a single world. | 40 | Silver |

## Advancements

| Icon | Advancement | In-game description | Actual requirements (if different) |
| --- | --- | --- | --- |
|  | Adventuring Time | Discover every biome | Visit *all* of these 55​[*until: Third Drop 2026*]/56​[*upcoming*] biomes:  - Badlands - Bamboo Jungle - Beach - Birch Forest - Cherry Grove - Cold Ocean - Dappled Forest - Dark Forest - Deep Cold Ocean - Deep Dark - Deep Frozen Ocean - Deep Lukewarm Ocean - Deep Ocean - Desert - Dripstone Caves - Eroded Badlands - Flower Forest - Forest - Frozen Ocean - Frozen Peaks - Frozen River - Grove - Ice Spikes - Jagged Peaks - Jungle - Lukewarm Ocean - Lush Caves - Mangrove Swamp - Meadow - Mushroom Fields - Ocean - Old Growth Birch Forest - Old Growth Pine Taiga - Old Growth Spruce Taiga - Pale Garden - Plains - River - Savanna - Savanna Plateau - Snowy Beach - Snowy Plains - Snowy Slopes - Snowy Taiga - Sparse Jungle - Stony Peaks - Stony Shore - Sunflower Plains - Sulfur Caves - Swamp - Taiga - Warm Ocean - Windswept Forest - Windswept Gravelly Hills - Windswept Hills - Windswept Savanna - Wooded Badlands The advancement is only for Overworld biomes. Other biomes may also be visited, but are ignored for this advancement. |

## History

This section is missing information about: Legacy Console Edition

Please expand the section to include this information. Further details may exist on the [talk page](https://minecraft.wiki/w/Talk:River).

### Development

|  |  |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [August 18, 2011](https://twitter.com/notch/status/104187327949176833) | | | | | | | Notch tweeted a screenshot of a river biome. |

### *Java Edition*

| Java Edition Beta | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.8 | | | Pre-release | | | | Added rivers as a separate biome. Previously, river-like water bodies could generate as part of the terrain generation algorithm. |
| *Java Edition* | | | | | | | |
| 1.7.2 | | | 13w36a | | | | River is now required for the new "Adventuring Time" achievement. |
| 1.13 | | | 18w08b | | | | Added salmon, which spawn in rivers. |
| 18w11a | | | | Added drowned, which spawn in rivers. |
| 18w14a | | | | Seagrass now generates in rivers. |
| 1.13.1 | | | 18w31a | | | | Squid now spawn only in oceans and rivers, making them slightly more common in rivers, as there are fewer places elsewhere for them to spawn. |
| 1.18 | | | experimental snapshot 4 | | | | River biomes are less likely to form a steep, dry river gorge in mountainous terrain. Instead, they either carve a fjord through it, or they raise the terrain to form a valley. |
| Rivers that go through swamps tend to be more shallow. |
| experimental snapshot 5 | | | | Rivers are less likely to be super deep or get choked off in flat areas. |
| Rivers tend to get shallow and merge with swamps instead of carving through them. |
| 21w37a | | | | Prior to the removal of numerical biome IDs, this biome's numerical ID was 7. |
| 1.21.5 | | | 25w05a | | | | Added bushes and firefly bushes, which can generate in rivers. |

### *Bedrock Edition*

| Pocket Edition Alpha | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v0.1.0 | | | | | | | Added biomes, including rivers. |
| *Bedrock Edition* | | | | | | | |
| 1.2.13 | | | beta 1.2.13.8 | | | | Added drowned, which spawn in river biomes. |
| 1.4.0 | | | beta 1.2.14.2 | | | | Added salmon, which spawn in rivers. |
| Rivers now generate with seagrass. |
| 1.13.0 | | | beta 1.13.0.9 | | | | Squid can now spawn in rivers. |
| 1.21.70 Experiment Drop 1 2025 | | | Preview 1.21.70.20 | | | | Added bushes and firefly bushes, which can generate in rivers. |

## Issues

Issues relating to "River" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%2C%20MCPE%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22River%22%29%20ORDER%20BY%20resolution%20DESC).

## Trivia

- Rivers override canyons at or close to the surface level, causing the canyon to be abruptly cut off by a wall of stone. However, if the canyon is long enough, it may continue on the other side of the river.
  - Canyons can generate underground in river biomes without being cut off.

## Gallery

### Screenshots

#### Historical screenshots

### Mojang screenshots

### In other media

## See also

- Frozen River
- Mushroom Field Shore
- Swamp
- Beach
- Ocean

## External links

- ["Around the Block: River"](https://www.minecraft.net/en-us/article/around-block--river) by Duncan Geere – Minecraft.net, October 22, 2020.

## Navigation
