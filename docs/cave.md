# Cave

> **Source:** <https://minecraft.wiki/w/Cave>  
> **Revision:** 3668952 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_1 Java Edition history entry newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **26.2** — Added the sulfur caves biome.

---
For other uses, see Cave (disambiguation).

It has been suggested that this page be split into *[Noise cave](https://minecraft.wiki/w/Noise_cave?redirect=no)*, *[Carver cave](https://minecraft.wiki/w/Carver_cave?redirect=no)*, and *[Underwater cave](https://minecraft.wiki/w/Underwater_cave?redirect=no)*.

[[discuss](https://minecraft.wiki/w/Talk:Cave#Split_proposal_pt._2)]

If this split affects many pages, or may potentially be controversial, do *not* split until a consensus has been reached.
**Reason:** *Many technical differences that should be noted separately, like how canyons have their own page now. This page is quite long.* (16 February 2025)

Cave

|  |  |
| --- | --- |
| Biomes | - Overworld - The Nether |
| Generates in existing chunks | Yes |
| Consists of | - Air - Cave Air‌[*Java Edition only*] |

```
{
    "title": "Cave",
    "rows": [
        {
            "field": "\n* <span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:EnvSprite overworld.png article, displayed as 16x16px|link=Overworld|alt=|class=pixel-image|)</span>(link to Overworld article, displayed as <span class=\"sprite-text\">Overworld</span>)</span>\n* <span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:EnvSprite the-nether.png article, displayed as 16x16px|link=The Nether|alt=|class=pixel-image|)</span>(link to The Nether article, displayed as <span class=\"sprite-text\">The Nether</span>)</span>",
            "label": "(link to Biome article, displayed as Biomes)"
        },
        {
            "field": "Yes",
            "label": "Generates in<br>existing chunks  "
        },
        {
            "field": "\n* <span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:BlockSprite air.png article, displayed as 16x16px|link=Air|alt=|class=pixel-image|)</span>(link to Air article, displayed as <span class=\"sprite-text\">Air</span>)</span>\n* <span class=\"nowrap\"><span class=\"sprite-file\" style=\"\">(link to File:BlockSprite cave-air.png article, displayed as 16x16px|link=Cave Air|alt=|class=pixel-image|)</span>(link to Cave Air article, displayed as <span class=\"sprite-text\">Cave Air</span>)</span>‌<sup class=\" nowrap Inline-Template \" title=\"\">[<i><span title=\"This statement only applies to Java Edition\">(link to Java Edition article, displayed as Java Edition)  only</span></i>]</sup>",
            "label": "Consists of"
        }
    ],
    "invimages": [],
    "images": [
        "Cave.png"
    ]
}
```

A **cave** (also known as a **cavern**) is a common terrain feature that generates in the Overworld and the Nether. Caves are usually found underground. They are open spaces of various sizes and shapes that often intersect with each other or with different generated structures, creating vast cave systems. They feature an abundance of ores, as well as hostile mobs that spawn in the darkness.

## Generation

Main articles: World generation § Noise caves and World generation § Carvers

Caves are a part of Minecraft's terrain consisting of randomly generated cavities of air, water or lava, creating a hollow area and exposing other blocks generated with the terrain (such as stones and ores).

Caves generate at any altitude up to Y-level 256, and may span from the surface all the way to Y-level -59. Because of low light levels, hostile mobs and certain animals like bats and glow squids (only in water) often spawn in caves.

Caves come in two distinct types; noise caves and carver caves. Noise caves are generated along with the base terrain, while carver caves are added later on as generated features.

An interactive widget is being loaded. If this does not work for you, please reload the page or check if JavaScript is working or enabled.

## Noise caves

Noise caves are generated using a noise. They come in the form of cheese caves, spaghetti caves, and noodle caves.[2] By adjusting noise frequency, hollowness (for cheese caves), and thickness (for spaghetti caves, noodle caves, and noise pillars), noise caves can vary in extremely diverse ways. When generating noise caves, the game firstly generates a random noise field, and "smudges" it using a mathematical trick called [Perlin noise](https://en.wikipedia.org/wiki/Perlin_noise). These processes then result in a 3D noise image. Noise pillars also generate inside cave blobs.

Noise caves are a part of the base terrain generation, and so do not intersect generated structures or mineral deposits. They are typically decorated with biome-specific features and decoration such as grass, sand, snow, or trees at higher y-levels, or dripstone pillars or clay deltas at lower y-levels. This is important, as cave noise is dually used to generate important Overworld terrain features such as overhangs or floating islands on the surface.[3]

### Cheese cave

Not to be confused with Cheese.

Cheese caves are pocket areas of the underground that come in various sizes. Cheese caves offer large and open spaces, being the largest cave type in the game. They frequently generate noise pillars and expose many ores. When generating, the black part of noise image becomes stone or deepslate, and white part becomes air, making it resemble cheese with many holes.

### Spaghetti cave

Spaghetti caves are long, narrow, winding caves and are a bit similar to small or medium carver caves, but longer. When generating, the edge of black and white part of noise image becomes air, making it look like long and wide spaghetti.

### Noodle cave

Noodle caves are a thinner and squigglier variant of spaghetti caves. They consist of tunnels usually 1 to 5 blocks in width. When generating, the edge of black and white part of noise image becomes air, making it look like long and thin noodles.

### Noise pillar

Noise pillars look like large dripstones or speleothems, but consist usually of stone. They can be found in any type of noise cave, making caves more realistic.

### Aquifer

Aquifers are liquid systems used to generate bodies of liquids in a world. Almost all liquids in a world (including those in carvers, noise caves, rivers, and oceans) are generated by aquifers. Aquifers may create underground liquid bodies that look like lakes, as well as water or lava waterfalls. Note that lava lakes are also generated underground, but are not aquifers.

Aquifers below Y=0 may sometimes generate with lava instead of water, with aquifers from y=-55 to -63 always consisting of lava.

If two liquid bodies of different levels or types are too close, some blocks may be generated between them to separate them. These may resemble "rimstone dams" in caves.

## Carver caves

"Carver" redirects here. For carver canyons, see Canyon. For how carvers are generated, see World generation § Carvers. For the definition of carvers in data packs, see Carver definition.

Carver caves' structure typically consists of a series of irregular tunnels branching off and winding in other directions, which may cut through to the surface, creating natural entrances to the cave.

A carver cave may have a main room (aka **circular void**) connecting 1 to 4 trunks, each trunk with zero or two branches. It may also have no main room, only one trunk and zero or two branches on it, that is, an I-shaped or T-shaped cave. There is a one-in-four probability of generating a cave with the main room, and another three-quarters probability of generating an I-shaped or T-shaped cave.

The main room is an ellipsoidal void of various size. It sometimes may be too small to be found, making the cave looks I-shaped or T-shaped. But as long as the cave is not I-shaped or T-shaped, it must have a main room.

For each trunk or branch, it is usually thin at both ends and thick in the middle. However, the thickness does not vary so evenly. It sometimes flickers thick and thin as it advances. Sometimes it becomes so thin that there are some interruptions. Multiple interruptions make it look like a series of continuous bubbles. The length from the root of the trunk to the top of a branch varies from 85 to 112 blocks.
The trunk and branches are curved, and they are often intertwined with each other to make complex caves. Multiple caves sometimes merge with each other making them more messy and spacious.

The ceiling of the main room, trunks, and branches is usually round, while the floor is usually flat.

In contrast with noise caves' gradual and natural openings, carver caves frequently cut straight through surface blocks and generated features, either destroying them or inhibiting their generation. Sand often falls into caves generated near the surface of a desert or beach; craters in the sand can alert the player to caves below the surface. Caves cannot cut through red sand nor snow blocks, despite these generating as a surface block in several biomes.[4] They frequently intersect and expose other natural structures such as other caves, monster rooms, carver canyons, and mineshafts.

### Overworld carver cave

The carver cave generation in the Overworld starts from Y=-56 to Y=180. The probability of cave generation is higher at Y=-56 to Y=47.

Their main rooms vary from roughly 1 to 14 blocks in height, and from roughly 5 to 15 blocks in diameter.
The horizon thickness of trunks varies from roughly 2 to 38, while the vertical thickness of trunks varies from roughly 1 to 36.
The horizon thickness of branches varies from roughly 2 to 7, while the vertical thickness of trunks varies from roughly 1 to 7.

A cave may connect to the surface of the terrain, creating natural entrances to the cave. They sometimes connect to the sea. Caves and mineshafts also regularly contain at least minor water or lava flows as well as caves being made up of a number of aquifers of water and lava.

Some biome decoration might appear as well. For example, in jungle biomes, vines generate in carver caves near the surface, while sculk and related blocks may appear in the deep dark.

### Nether carver cave

The carver cave generation in the Nether starts from Y=0 to Y=126.

Their main rooms vary from roughly 2 to 8 blocks in height, and from roughly 5 to 15 blocks in diameter. Compared to caves in the Overworld, the vertical thickness of the caves in the Nether is higher. The horizontal thickness of trunks varies from roughly 3 to 15, while the vertical thickness of trunks varies from roughly 12 to 64. The horizontal thickness of branches varies from roughly 3 to 5, while the vertical thickness of trunks varies from roughly 12 to 22.

#### Gallery

#### Underwater carver caves

Underwater caves are completely flooded caves that may have underwater magma.

Being completely underwater, only the magma blocks provide any air for entities to breathe, since they generate bubble columns. They sometimes have seagrass, but kelp cannot generate due to being exposed to the sky, unlike sea-access caves.

## Structures and features

### Monster room

Main article: Monster Room

Monster rooms, formerly called dungeons, are structure-like features that appear in the underground that contain monster spawners. They are small rooms made of cobblestone and mossy cobblestone and contain a monster spawner that produces either zombies, skeletons, or spiders and up to 2 chests. Finding a monster room without a chest is unlikely but possible.

### Ancient city

Main article: Ancient City

An ancient city is a palatial structure found in the deep dark, harboring chests containing items that cannot be found anywhere else or that help a player avoid a warden. An ancient city features a large palace that stretches throughout a deep dark biome. The palace is made up of long corridors with gray wool floors to prevent vibrations as well as some smaller ruins off to the side of the main corridors, which contain between one or two loot chests. The city center features a frame resembling a warden's head, where there are reinforced deepslate blocks, which is an unobtainable material in Survival mode. Other unique blocks such as soul lanterns and candles as well as different forms of deepslate can be found here.

### Mineshaft

Main article: Mineshaft

A mineshaft is a generated structure found underground or underwater, having 3×3 mazes of tunnels with cave spiders and ores exposed on the walls or floor regularly.

### Lava lake

Main article: Lava lake

Lava lakes may generate underground with an air pocket, which makes them look like small caves with lava aquifers.

### Amethyst geode

Main article: Amethyst geode

An amethyst geode is a feature found in the underground of the Overworld. Amethyst geodes contain smooth basalt, calcite, and are the only source, apart from Ancient City and Trial Chambers chests, of amethyst items and blocks.

## Cave biomes

There are some biomes generated only in caves in the Overworld.

### Dripstone caves

Main article: Dripstone Caves

A dripstone cave is an underground biome often found far away from oceans, in areas with high continentalness values. It features a landscape covered by speleothems, taking the form of patches of pointed dripstone and large dripstone block pillars. Small sources of water can be found on the floor. Drowned can spawn here in aquifers.

### Lush caves

Main article: Lush Caves

A lush cave is an underground biome that is usually found below biomes with high humidity values, such as dark forests and old growth taigas. It is filled with unique flora, cave vines covering the roof, and clay pools of water on the bottom. This biome can be found underground below azalea trees. Axolotls and tropical fish can spawn here.

### Deep dark

Main article: Deep Dark

The deep dark mainly generates deep underground in the deepslate layer, usually in mountainous areas with low erosion values, and never below an ocean. The floor in this biome often has patches of sculk blocks surrounded by sculk veins, which include sculk-related blocks such as sculk shriekers, sculk sensors, and sculk catalysts. No mobs spawn naturally in this biome, but if a player triggers the sculk shriekers a certain amount of times, the warden, a powerful hostile mob, may appear, giving the player the Darkness effect. This biome may contain one or more ancient cities.

### Sulfur caves

Main article: Sulfur Caves

The sulfur caves are a cave biome that consist of sulfur, sulfur spike and cinnabar blocks, where sulfur cubes and cave spiders naturally spawn. Sulfur pools generate in this biome, which include a block of potent sulfur. Sulfur springs can spawn above a sulfur cave, indicating the presence of one nearby.

## History

### Announcement

|  |  |  |  |  |  |  |  |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [October 3, 2020](https://youtube.com/watch?v=DBvZ2Iqmm3M) | | | | | | | The new cave generation is at Minecraft Live 2020. |

### *Java Edition*

| Java Edition pre-Classic | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Cave game tech test | | | | | | | Development on "Cave Game" started; caverns are showcased. |
| rd-132211 | | | | | | | Caves have been temporarily removed. |
| Java Edition Classic | | | | | | | |
| 0.0.3a | | | | | | | Re-added caves. |
| 0.0.19a | | | | | | | Caves are now more varied in width. |
| [August 25, 2009](https://www.youtube.com/watch?v=YoEn4f7H5M4) | | | | | | | Caves are shown to be longer and more narrow. |
| It was demonstrated that bigger caves are located deeper underground. |
| 0.24\_SURVIVAL\_TEST | | | | | | | Added the above cave changes. |
| Java Edition Indev | | | | | | | |
| 0.31 | | | 20100122-2251 | | | | Caves are no longer filled with water as often. |
| Java Edition Infdev | | | | | | | |
| 20100227-1414 | | | | | | | Removed caves. |
| 20100325-1545 | | | | | | | Caves were reimplemented. |
| 20100327 | | | | | | | Removed caves again. |
| 20100616-1808 | | | | | | | Again reimplemented caves.[*[is this the correct version?](https://minecraft.wiki/w/Talk:Cave)*] |
| Caves are now clustered instead of random.[*[is this the correct version?](https://minecraft.wiki/w/Talk:Cave)*] |
| Caves have 1–5 exits, enabling multiple escapes. |
| Caves are now so clustered that a cave could be described as "Swiss cheese". |
| More water and lava springs generate in caves. |
| Tunnels have a wider variety of thickness.[*[is this the correct version?](https://minecraft.wiki/w/Talk:Cave)*] |
| 20100617-1531 | | | | | | | Gravel and dirt can now be found in caves as ore features. |
| Java Edition Alpha | | | | | | | |
| v1.0.3 | | | | | | | Caves now have ambient noises such as moans and train whistles. |
| v1.2.6 | | | | | | | Pools of water and lava can now be found in caves. |
| *Java Edition* | | | | | | | |
| 1.4.2 | | | 12w38a | | | | Bats added, and are the only passive mob to spawn in dark caves. |
| 12w38b | | | | New cave sounds added. |
| 1.7.2 | | | 13w36a | | | | Cave generation tweaked, making caves less dense and interconnected.[5][6] |
| 1.8 | | | 14w20a | | | | Caves now generate on the surface of desert, mesa, mega taiga, and mushroom biomes. |
| 14w32a | | | | Caves surfacing in mesa biomes now generate with red sandstone rather than sandstone. |
| 1.10 | | | 16w20a | | | | Caves no longer generate with sandstone in the ceiling, when they surface through sand of any type. The sand instead shows unstable sand particles, until it is disturbed. |
| 1.13 | | | 18w08a | | | | Caves and ravines can now generate underwater. |
| [October 3, 2020](https://youtube.com/watch?v=DWZIfsaIgtE&t=24m55s) | | | | | | | The Caves & Cliffs update was announced at Minecraft Live 2020, revealing cave biomes, aquifers, and noise caves. |
| 1.17 | | | 21w06a | | | | Revamped cave generation by adding cheese caves, spaghetti caves, and flooded aquifer caves. |
| Removed flooded carver caves, as they are replaced by aquifers. |
| Caves can now generate down to Y=-60. |
| 21w07a | | | | Noise caves and aquifers appear less often, and caves that are below the Y level of 0 are now made with grimstone |
| 21w08a | | | | Added crevice carvers. |
| Carvers now generate below Y=0. |
| Tweaked cave sizes. |
| Added more variation to carvers.[7] |
| 21w10a | | | | Crevice carvers are now much less common. |
| 21w11a | | | | Tweaked cave sizes. |
| 21w13a | | | | Made carvers less likely to be too flat to walk through. |
| Increased likelihood of huge caves (large cheese caves). |
| Reduced likelihood of tall 1-block thin pillars. |
| Changes were made to make the cheese caves more varied in aspect and size. |
| Deepslate ore features can now be found between heights 0 and 16. |
| Dripstone clusters can now be found rarely in regular caves. |
| 21w15a | | | | All changes to caves from 21w06a-21w13a apart from dripstone clusters and deepslate and tuff ore features have been disabled and relegated to the Caves & Cliffs Prototype Data Pack. |
| 21w18a | | | | Added noodle caves in the prototype data pack. |
| 1.18 | | | Experimental Snapshot 1 | | | | Re-introduced all previous changes to cave generation, which were available only through certain snapshots and the Caves & Cliffs Prototype Data Pack prior to this experimental snapshot. |
| Lush caves and dripstone caves can now generate naturally. |
| Noise caves are no longer flooded above Y=30. |
| Noodle caves now generate at all altitudes.[*verify*] |
| [July 14, 2021](https://twitter.com/henrikkniberg/status/1415237012731346947) | | | | According to Henrik Kniberg, crevice carvers have been retired because noise caves "do a better job of making cool natural-looking crevice". |
| experimental snapshot 2 | | | | Noise caves are less likely to extend from the surface to negative Y levels. |
| Cheese caves are a bit smaller and less likely to intersect the surface. |
| experimental snapshot 3 | | | | Aquifers can go deeper and are more likely to connect with cave systems further down. |
| Aquifers have more high-frequency variation, and are more likely to be spread out instead of concentrated in one region. |
| experimental snapshot 5 | | | | Carvers are now able to cut through red sand and calcite. |
| experimental snapshot 6 | | | | Aquifers under oceans and rivers are more likely to link to underground areas. |
| Carvers are now able to cut through sand and gravel on ocean floors. |
| experimental snapshot 7 | | | | Noodle caves are now able to generate at any height and are no longer capped at Y=130. |
| 21w40a | | | | Some fixes to aquifer generation have resulted in their locations getting shifted around. |
| 21w43a | | | | Reduced the amount of flooded caves near rivers and oceans. |
| 1.19 | | | Deep Dark Experimental Snapshot 1 | | | | Added the deep dark biome and ancient cities. |
| 22w11a | | | | Sculk patches in deep dark now also replace part of the terrain's walls or ceilings, and become larger in general. |
| Ancient cities in Deep Dark from the previous experimental snapshot have not been added. |
| 22w13a | | | | Re-added ancient cities and revamped with new structures, improvements to already existing structures and an updated loot table. |
| 26.2 | | | snap1 | | | | Added the sulfur caves biome. |

### *Bedrock Edition*

| Pocket Edition Alpha | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [Nov 24, 2012](https://youtube.com/watch?v=YMhyX_lKWV4&t=18m15s) | | | | | | | Performance issues relating to caves were discussed at MINECON 2012. |
| v0.9.0 | | | build 1 | | | | Added caves. |
| *Bedrock Edition* | | | | | | | |
| 1.4.0 | | | beta 1.2.20.1 | | | | Caves can now generate underwater. |
| 1.16.0 | | | beta 1.16.0.57 | | | | Added ambient cave sounds. |
| 1.16.220 Experiment Caves and Cliffs | | | beta 1.16.220.52 | | | | Caves now generate down to Y=-64. |
| 1.17.0 Experiment Caves and Cliffs | | | beta 1.16.230.52 | | | | Deepslate ore features can now be found between heights 0 and 16. |
| beta 1.16.230.54 | | | | Dripstone clusters can now be found rarely in regular caves. |
| beta 1.16.230.56 | | | | Revamped cave generation by adding cheese caves, spaghetti caves, and flooded aquifer caves. |
| Caves can now generate down to Y=-60. |
| beta 1.17.0.50 | | | | Lush Caves and Dripstone Caves can now generate naturally behind experimental gameplay toggle. |
| 1.17.10 Experiment Caves and Cliffs | | | release | | | | The new cave generation has been made accessible, behind experimental gameplay toggle. |
| 1.17.30 Experiment Caves and Cliffs | | | beta 1.17.30.23 | | | | Added noodle caves, behind experimental gameplay toggle. |
| Noise caves are no longer flooded above Y=30 behind experimental gameplay toggle. |
| 1.18.0 | | | beta 1.18.0.20 | | | | Lush caves and dripstone caves can now generate naturally by default without enabling experimental gameplay toggle. |
| The new cave generation has been made accessible by default, without enabling experimental gameplay toggle. |

### Legacy Console Edition

| Legacy Console Edition | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Xbox 360 | Xbox One | PS3 | PS4 | PS Vita | Wii U | Switch |  |
| TU1 | CU1 | 1.00 | 1.00 | 1.00 | Patch 1 | 1.0.1 | Added caves. |

### *New Nintendo 3DS Edition*

| *New Nintendo 3DS Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.1.0 | | | | | | | Added caves. |
| 1.3.12 | | | | | | | Improved visibility in caves. |

## Issues

Issues relating to "Cave" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%2C%20MCPE%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22Cave%22%29%20ORDER%20BY%20resolution%20DESC).

## Gallery

### Carvers

### Noise caves

#### Aquifers

### Hollows

### Cracks

### Biomes

### Other

## Trivia

- Before they were added to *Minecraft*, large underground lakes were already present in *Minicraft*.

## See also

- Ambience, sounds that can be heard around and within dark caves.
- A guide to exploring caves

## References

1. ["I get a lot of questions about how the new Minecraft noise caves work, and why we call them silly things like Cheese caves and Spaghetti caves. Here's an attempt to summarize it in a picture, hope it makes some kind of sense :)"](https://twitter.com/henrikkniberg/status/1364265702861987841) – [@henrikkniberg](https://twitter.com/henrikkniberg) on X (formerly Twitter), February 23, 2021
2. ["Minecraft Snapshot 21w06a"](https://www.minecraft.net/en-us/article/minecraft-snapshot-21w06a) – Minecraft.net, February 10, 2021.
3. [https://www.youtube.com/watch?v=ob3VwY4JyzE](https://www.youtube.com/watch?v=ob3VwY4JyzE)
4. ["[MC-16132] Cave carvers don't cut through snow blocks – Jira"](https://bugs.mojang.com/browse/MC/issues/MC-16132) – Mojira, May 23, 2013.
5. ["It seems that the underground is no longer swiss cheese anymore."](https://www.minecraftforum.net/topic/1981051) – Minecraft Forum, September 17, 2013.
6. [de:Datei:Höhlenverteilung 13w36a.gif](https://de.minecraft.wiki/w/Datei:H%C3%B6hlenverteilung_13w36a.gif)
7. ["Oh forgot to mention in the changelog: we also added some more variation to the old cave carvers (width, heigh, floor cutoff, etc). Just to make them blend in a bit better with the noise caves instead of being instantly recognizable."](https://twitter.com/henrikkniberg/status/1364649861761630209) – [@henrikkniberg](https://twitter.com/henrikkniberg) on X (formerly Twitter), February 24, 2021

## Navigation
