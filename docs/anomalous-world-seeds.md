# Anomalous world seeds

> **Source:** <https://minecraft.wiki/w/Anomalous_world_seeds>  
> **Revision:** 3692034 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

This article is a work in progress.

Please help expand and improve it. The talk page may contain suggestions.

Certain world seeds are known for producing unexpected results when used for generating worlds, often due to interacting with random number generators in certain ways.

## Diagonal feature repetition

This section is missing information about: Why does in earlier Java Edition versions, did only the first of each seed list in Matthew's video[1] seem to actually work?

Please expand the section to include this information. Further details may exist on the [talk page](https://minecraft.wiki/w/Talk:Anomalous_world_seeds).

It is possible for feature generation, specifically that which governs objects such as trees, ores, lakes and other similar minor structures, to repeat diagonally if certain values happen to equal each other or one to equal the other's negative.[1] For example, in *Java Edition* before 1.18, the seed `102496288339226` will cause features to repeat along the positive-positive diagonal, and the seed `74811678275130` will cause them to repeat along the positive-negative diagonal. Prior to Java Edition Beta 1.8, caves were also affected by this diagonal repetition.

What appears to be the exact same effect can be seen in *Bedrock Edition* with different seeds. The seeds `1669320484` and `289849025`, for example, cause trees, monster rooms, amethyst geodes and world decorations to repeat on the positive-negative diagonal.[2] It is not known if there are any seeds that cause repetition on the positive-positive diagonal.

## Zero as output from pRNG function

This feature is exclusive to *Java Edition*.

Java's built-in Math.random() function will output a pseudorandom value for any given input value; it is possible for this output value to be 0, just as any other number.[3] In such a case, certain types of population data for adjacent chunks will be changed by zero (i.e. unchanged), resulting in affected structures generating identically in adjacent chunks.[4] The structures primarily affected by this are carvers (caves, canyons, underwater caves and underwater canyons) and mineshafts. Certain aspects of feature placement, such as water lakes, also appear to suffer placement affected to a much more minor extent, which is most pronounced in controlled environments such as superflat worlds with features enabled.​[*more information needed*]

`Random.nextDouble()` will return a value of `0` on the first call if the provided value is `107038380838084`, on the second call if the value is `164311266871034`, and on the third call if the value is `240144965573432`.

- In vanilla, the seed `107038380838084` will cause affected structures to repeat every chunk on the X-axis, and the seed `164311266871034` will cause affected structures to repeat every chunk on the Z-axis. The seed `240144965573432` does not appear to have any effects.
- When using certain mods such as [Tall Worlds](https://www.curseforge.com/minecraft/mc-mods/tall-worlds) or [OpenCubicChunks](https://www.curseforge.com/minecraft/mc-mods/opencubicchunks) which institute a three-dimensional chunk system, the seed `107038380838084` will cause affected structures to repeat every chunk on the X-axis, and the seed `164311266871034` will cause affected structures to repeat every chunk on the Y-axis, and the seed `240144965573432` will cause affected structures to repeat every chunk on the Z-axis.

Certain carvers, for unknown reasons, use a salt of either -1 or -2 when generating. As a result, not all carver types will repeat in one given world. If we take the X-axis seed as an example:

- `107038380838084` causes some carver caves to repeat, most often those near the surface
- `107038380838083` generally causes deeper underground carver caves to repeat
- `107038380838082` causes canyons to repeat; carver caves appear to be unaffected

In general, if a data pack is used to add new carvers to generation, each carver type will have its own salt.

### Integer overflow and higher periods

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Anomalous_world_seeds?action=edit&section=).

## Non-orthogonal mineshaft repetition

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Anomalous_world_seeds?action=edit&section=).
*Some information to include: Matthew's video[5] has two example seeds in the description. How do they do what they do?*

## Possible BE biome color corruption

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Anomalous_world_seeds?action=edit&section=).
*Some information to include: See comment threads on [https://www.reddit.com/r/MCPE/comments/5i6sae/recursive\_infinite\_mineshaft\_seeds\_on\_mcpe/](https://www.reddit.com/r/MCPE/comments/5i6sae/recursive_infinite_mineshaft_seeds_on_mcpe/) and actually attempt to confirm this happens in game.*

## Shadow seeds

It is possible for different seeds to share major generational characteristics with other seeds through certain mechanisms.

### Biome shadow seeds

In Java Edition before 1.18, by adding the constant`-7379792620528906219` (in calculation) to the world seed, it was possible to obtain a "shadow seed". In this seed, biome arrangements were identical to the ones of the original seed, but locations of features (caves, hills) and generated structures (villages, temples, etc.) were different in the new seed.

### Unused bits

In Java Edition before 1.18, the shapes of the biomes were generated using only the upper 24 bits of the world seed. Additionally, caves and badlands terracotta patterns only use the upper 48 bits of the world seed.

## General information

Main article: World seed

### Generation quirks

Through certain seeds, it is possible to observe interesting effects.

#### Changing terrain without changing some structures

Only certain sections of the seed are used to generate specific features within the world. It is possible to generate multiple worlds with identical cave systems, Nether biomes and other arrangements of generated structures simply by converting the seed into binary and tweaking the desired bits.[6] An example is the seed generator using only the first 48 bits to generate cave systems and badlands clay banding layers.

Any seed calculated as `4294967296 × n + 1669320484` also generate maps with repeating features.

#### Repetition

This section of the article is empty.

You can help by [expanding it](https://minecraft.wiki/w/Anomalous_world_seeds?action=edit&section=).

## History

| Java Edition Infdev | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 20100616-1808 | | | | | | | First version known to be affected by diagonal repetition seeds. Caves will repeat diagonally. |
| ? | | | | | | | Trees also repeat diagonally on diagonal repetition seeds.​[*more information needed*] |
| Java Edition Beta | | | | | | | |
| 1.8 | | | Pre-release | | | | Caves have changed to no longer generate as features, and therefore no longer will repeat diagonally. They will instead now repeat orthogonally under a different class of "broken" seeds. |
| *Java Edition* | | | | | | | |
| 1.13 | | | 18w06a | | | | Certain seeds, such as 82025653894727, would not repeat features diagonally in versions prior to this, but will do so from this version onwards. The exact reasoning for this is unknown. |
| pre7 | | | | A salt of -1 has been implemented that causes underwater caves and non-underwater canyons to repeat on different seeds from non-underwater caves and underwater canyons. |
| 1.17 | | | 21w07a | | | | Orthogonal repetition seeds now also affect the transition from stone to grimstone/deepslate, causing striking horizontal patterns not present in "normal" seeds. |
| 21w08a | | | | A salt of -2 has been implemented alongside the introduction of crack carvers that causes them to repeat on different seeds from either caves or conventional canyons. |
| 1.18 | | | Experimental Snapshot 1 | | | | Crack carvers no longer exist. The -2 salt now affects all types of ravines instead. |
| The seeds that cause underwater caves and non-underwater ravines to repeat now also appear to affect some other, presumably new non-underwater carvers.​[*more information needed*] |
| Pre-release 7 | | | | All existing seeds featuring diagonal feature repetition appear to have been patched; while the phenomenon presumably still exists, no seeds exhibiting it from this version onward have been found. |

## References

1. ["This Minecraft Seed Makes Everything Repeat"](https://youtube.com/watch?v=UtNXUMrSIxQ) – Matthew Bolan on YouTube, January 5, 2020
2. [MCPE-95011](https://bugs.mojang.com/browse/MCPE-95011)
3. [https://stackoverflow.com/questions/3065554/can-javas-random-function-be-zero](https://stackoverflow.com/questions/3065554/can-javas-random-function-be-zero)
4. [MC-111378](https://bugs.mojang.com/browse/MC-111378) – Math error (random generation of zero) causing map gen to fail – resolved as "Won't Fix".
5. ["This Minecraft Seed Makes Everything Repeat"](https://youtube.com/watch?v=UtNXUMrSIxQ) – Matthew Bolan on YouTube, January 5, 2020
6. [https://www.minecraftforum.net/forums/minecraft-java-edition/seeds/2229720-can-two-different-seeds-produce-identical-worlds](https://www.minecraftforum.net/forums/minecraft-java-edition/seeds/2229720-can-two-different-seeds-produce-identical-worlds)

## Navigation
