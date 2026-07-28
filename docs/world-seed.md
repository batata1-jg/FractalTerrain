# World seed

> **Source:** <https://minecraft.wiki/w/World_seed>  
> **Revision:** 3691937 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

For other uses, see Seeds.

The **world seed** (or simply **seed**; referred to as **seed for the world generator** in-game) is a value made up of character(s) (including negative or positive integers) that is used as the basis for generating every *Minecraft* world.[1]

## Compatibility

Seeds are somewhat compatible between *Java* and *Bedrock Edition*, with terrain generation and biomes being the same. However, the placement of generated structures differs between these editions.

## World generation

Main article: World generation

Whenever the game has to generate a new world, it calls upon an algorithm known as *[Perlin noise](https://en.wikipedia.org/wiki/Perlin_noise)*. This algorithm outputs a pseudo-random value that is then used to determine the characteristics and features of the world. However, the algorithm always outputs the same value each time for a constant starting point (seed). Thus, the same seed generates the same terrain every time.

A world's seed is set when that world is created. By default, it is decided automatically, but it can also be set manually. Setting and reusing a seed from one world generates the same world. Either a number or a word/phrase can be used, including negatives. If a word/phrase is used, it is converted into a 32-bit integer using the `.hashCode()` method.

Whenever the world generation algorithm is updated (usually by adding new biomes to the game), the same seed no longer generates the same terrain. If the seed or generator changes in a saved world, new chunks are based on the new seed and no longer match those from the old seed. Deleted chunks can regenerate if the seed and generator remain the same, but changes if either the seed or generator changes. In fact, deleting chunks is sometimes done to let newly-introduced features appear in an old world; see Tutorial:Updating old terrain.

Because seeds are simply random values read into an algorithm and not actually names of different worlds, using a certain seed does not result in a world with any relevance to the value of that seed. For instance, using a biome name as the seed does not necessarily result in the creation of a world with primarily that biome, nor does it spawn the player within the said biome.

An interactive widget is being loaded. If this does not work for you, please reload the page or check if JavaScript is working or enabled.

## Determining the seed

In *Java Edition*, the player can enter the command `/seed` to view and copy the world's seed. This command is available in singleplayer worlds even if cheats are off. The player can also select 'Re-create' in the Worlds menu to see the seed.

In *Bedrock Edition*, the seed can be found and copied on the Edit world screen. There are also 25 seed templates that offers the player several pre-set seeds to generate worlds with specific features near the spawn point. Additionally, the beta version has a visible seed on the top of the screen.

## Changing the seed

This feature is exclusive to *Bedrock Edition*.

The seed of a world can be changed at any time using world templates. By exporting a world and unzipping the file, one can place a manifest.json file in the world's folder with the `world_template` type, and `allow_random_seed:true`. When zipped into a `.mctemplate` file, it can be imported into the game. It appears under the "Create new world" → "Owned by me" → "Imported" list of world templates, which, when selected opens the Create new world screen with the option to change the seed. However, this will not create a new world – all saved chunks are still in the world, meaning that only the world seed is changed; even achievements can still be earned. This also allows various other world creation options to be changed, such as flat world, bonus chest, Hardcore, and starting map, clear all player data, or even lock the world in older versions.

## Notable seeds

This section is missing information about: What seeds are used for Bedrock Edition panoramas?

Please expand the section to include this information. Further details may exist on the [talk page](https://minecraft.wiki/w/Talk:World_seed).

The following map seeds have, at one point or another, been used for generating official *Minecraft* maps and resources or otherwise significant community material.

- The *Java Edition* demo world seed can be played in the full version by entering `North Carolina` in the seed input.
  - The Beta 1.3\_01 PC Gamer demo world seed can be played in the appropriate era by entering `glacier`, all lowercase unlike the famous seed where the G is capitalized, in the seed input.
- The *Bedrock Edition* trial version uses the seed `1193926712`.
  - Historical trial seeds include: `818010429`, `29300`, `1395001428`, `1537846859` ("Forest Glade" seed template).
- The seeds for many of the panoramas used on the title screen are as follows:
  - The panorama used between Beta 1.8 Pre-release and 18w22c is either `2151901553968352745` or `8091867987493326313`,[2] generated between Beta 1.6.6 and Beta 1.7.
  - Bedrock Edition 1.2.0 uses `95475027`, which is the same seed as the "Winding River" seed template generated before 1.18.0.
  - Java Edition 1.13 uses `1458140401`, which is the seed resulting from typing `18w22a` in as a seed, generated in snapshot 18w22a.
  - Java Edition 1.14 uses `2802867088795589976`, taken in 18w48a.
  - Java Edition 1.15 uses `-4404205509303106230`, taken in 19w40a.
  - Java Edition 1.16 uses `6006096527635909600`, taken in 20w13a.
  - Java Edition 1.18 uses `2151901553968352745`, taken in 21w40a.
  - Java Edition 1.19 uses `-1696067516`, which is the seed resulting from typing `thewildupdate` in as a seed, taken in 22w15a.
  - Java Edition 1.20 uses `8554477380691140270`, taken in 23w14a.
  - Java Edition 1.21.6 and Bedrock Edition 1.21.90 use `8819392414030687460`, taken in 25w20a and Preview 1.21.90.26, respectively.
- The seed for the original pack.png file is `3257840388504953787`, taken some time before the release of Alpha v1.2.2[3]. It generates most correctly from Alpha v1.2.0 to Alpha v1.2.5, but can also be generated up to Beta 1.7.3 with minor population differences. It's also the default seed for *Bedrock Edition*'s create new world when no seed is entered or created since Bedrock Edition beta 1.18.20.21. This seed spawns the player in a savanna biome at coordinates `Position: -208, 65, 0`.
- The seed for the Skull on Fire painting is either `-6984854390176336655` or `-1044887956651363087`, generated in Alpha v1.1.2\_01 or prior.[4]
- The seed used for the original Herobrine hoax screenshot is `478868574082066804`, generated in Alpha v1.0.16\_02.
- The seed used for the original Herobrine hoax livestream is `3609313613745973624`, generated in Alpha v1.0.17\_04.
- Seeds used for the Legacy Console Edition tutorial worlds:
  - Each world is referred to by its respective Xbox 360 Edition update.
  - TU1 and TU3 used the seed `1171544198849424676`
  - TU5 used the seed `6173462`
  - TU7 and TU9 used the seed `96414766889474996`
  - TU12 and TU14 used the seed `1227750481513469519`
  - TU19, TU31, TU46, and TU69 were artificially-created and can't be generated by any seed.

### Starter seeds

Main article: Starter Seeds

In *Bedrock Edition*, players opening the game for the first time are offered to quickly create a new world with default settings and predefined seeds. There are currently 11 starter seeds, and many starter seeds have been removed:

- `-7148389242537051542`
- `31563250179158`
- `-2965711870629693628`
- `69427194527559476`
- `1000367306308321`
- `100000061447117197`
- `1143653337750952406`
- `3536029907932046148`
- `-1718501946501227358`
- `-6113761326748789280`
- `7408629636827897200`

**Removed**

- `348722287802000751`
- `31563252268802`
- `651719687612429311`
- `-1992192446241627435`
- `-9222037139879175104`
- `3829878918385877312`
- `54566591155505424`
- `6390326396262027557`
- `7226627000711902118`
- `-6574012378133862552`
- `-2032795982907864146`
- `-7649949940957896961`
- `-115039328491026064`
- `5155879575039368840`
- `2048005618087379093`
- `2204054850500208009`
- `72415961571256213`
- `-6339824463720481367`
- `769898142342073932`
- `1266942`
- `19439991`
- `1835543614670145532`
- `8438280864103146561`
- `-983983965181981808`
- `8804783126757866200`
- `47192204`
- `111643445`
- `6252188339817453822`
- `-8399271073483220130`
- `16285452119529`
- `1899845621555937036`
- `60554305524`
- `18820960`
- `1545213456444500`
- `-6646854586427229651`
- `8008134581819610`
- `8146707790672235138`
- `888882903781400170`
- `8888034329335250`
- `100000019873386049`
- `-6897082504097952427`
- `888880389127738660`
- `-5584399987456711267`
- `298649991203052898`

### Generation anomalies

Some seeds are known to cause strange effects in the world generation, due to their mathematical properties. See Anomalous world seeds for more information.

## Technical

### General

If the seed contains characters other than digits (except the first character, which can be "-" or "+") or falls out of range of a Java `long` number (an integer between −263 and 263−1), the Java `String.hashCode()` function is used to generate the seed number. This restricts *Minecraft* to a subset of the possible worlds to 232 (or 4,294,967,296), due to the `int` datatype being used. A numeric seed or a random (blank) world seed must be used to access the full set of possible worlds (which is 264, or 18,446,744,073,709,551,616, or 18.4 quintillion).

### Overlap between editions

All *Java* and *Bedrock Edition* seeds in the range from -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807[*verify*] (64-bit seeds) generate the same terrain and biomes in both *Java* and *Bedrock Edition*. However, structures, features (i.e. decorators), carver caves, and mob spawns will generate differently.

## Videos

## History

This section is missing information about: Were the seeds always 64-bit integers, or they were 32-bit in older (pre-Infdev) versions?

Please expand the section to include this information. Further details may exist on the [talk page](https://minecraft.wiki/w/Talk:World_seed).

This section needs to be updated.

Please update this section to reflect recent updates or newly available information. The talk page may contain suggestions.
**Reason:** Bedrock experimental changes

### *Java Edition*

| Java Edition pre-Classic | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| rd-20090515 | | | | | | | Added a simple level generator. |
| Java Edition Classic | | | | | | | |
| 0.0.12a | | | | | | | Added a new level generator. |
| [August 25, 2009](https://web.archive.org/web/0/https://notch.tumblr.com/post/170887079) | | | | | | | Showed another new level generator, which generates cliffs more commonly. |
| Java Edition Indev | | | | | | | |
| 0.31 | | | 20091223-1459 | | | | Isometric level rendering screenshot added. |
| 20100106-2158 | | | | The player can now select island, floating, flat, or original as the level type when generating a world. |
| Players can also select square, long, or deep as the level shape. |
| Players can also select small, normal, or huge as the level size. |
| 20100107-1851 | | | | Deep floating maps now have layers of islands. |
| Players can now select a level theme; normal or hell. |
| 20100110 | | | | Islands now generate with more sand. |
| 20100113-2015 | | | | Oceans now generate with infinite water. |
| 20100122-1708 | | | | Water now spawns naturally above sea level and on floating islands. |
| 20100122-2251 | | | | Caves are now less flooded. |
| Java Edition Infdev | | | | | | | |
| 20100227-1414 | | | | | | | Terrain-breaking change to world gen: using any given seed on older versions now generates a different world. |
| World generation has been greatly simplified (with the removal of sand, gravel, caves, and ore blobs) in order to make infinite world generation implementation easier to work with. |
| 20100227-1433 | | | | | | | Brick pyramid generation changed - they now always come to a single point at the top, rather than sometimes being truncated, resulting in "brick square frusta". |
| 20100320 | | | | | | | Reimplemented primitive ore blob generation, in which they spawn as scattered, single blocks. |
| Reimplemented tree generation. |
| 20100325-1545 | | | | | | | Ore blob generation has been changed to the modern generation type. However, a float is used in their generation, causing their generation to break down at excessive distances. |
| Added caves. They generate through all blocks, not just terrestrial blocks. |
| 20100327 | | | | | | | Terrain-breaking change to world gen: using any given seed on older versions now generates a different world. |
| World generation has been significantly overhauled, which is visually fairly obvious. |
| Removed caves. |
| Flowers no longer generate. |
| The infinite stone wall at 33,554,432 no longer generates. Instead, the Far Lands generate at 12,550,824. |
| 20100413-1951 | | | | | | | All trees are now fancy (large) trees. |
| Sand and gravel now generate with the world again. |
| 20100420 | | | | | | | Terrain-breaking change to world gen: using any given seed on older versions now generates a different world. |
| World generation seems considerably less mountainous, and more hilly. |
| 20100608 | | | | | | | All trees are now small trees again - big trees do not generate. |
| 20100611 | | | | | | | Terrain-breaking change to world gen: using any given seed on older versions now generates a different world. |
| Terrain now appears to come in large islands. |
| Terrain can now generate high enough to be higher than the world height limit, causing it to be cut off. |
| Monoliths now have the potential to generate. |
| The amount of trees that generate appears to be different now. |
| 20100616-1808 | | | | | | | Minor terrain-breaking change to world gen. |
| Coastlines are now more gradual, resulting in smaller "ocean" areas. |
| Reimplemented caves. |
| Random patches of flowers and mushrooms now generate. |
| Springs of water and lava now generate. |
| Lava now naturally generates, although how it does so exactly is unknown. |
| Java Edition Alpha | | | | | | | |
| v1.2.0 | | | preview | | | | Terrain-breaking change to world gen: using any given seed on older versions now generates a different world. |
| Added biomes. |
| Java Edition Beta | | | | | | | |
| 1.3 | | | | | | | It is now possible to manually determine the seed upon world creation. |
| Any 64-bit signed integer value can be entered and will create a world with that seed, *unless* the number is 0, in which case the world will be created with a random seed as though no seed was provided. |
| A world with the seed 0 can be created by providing a string that hashes to 0, such as `creashaks organzine`, `pollinating sandboxes`, or `zsjpxah` (the shortest one).[5] |
| 1.8 | | | Pre-release | | | | Terrain-breaking change to world gen. |
| The debug screen now displays the seed number. |
| *Java Edition* | | | | | | | |
| 1.2.1 | | | 12w03a | | | | Minor terrain-breaking change to world gen. |
| 12w07a | | | | Seeds can no longer change biomes in existing worlds due to the Anvil file format. |
| ? | | | | Multiplayer servers no longer send the seed to clients. |
| 1.3.1 | | | 12w18a | | | | Due to singleplayer becoming multiplayer, the world's seed is no longer displayed on the debug screen. |
| 12w21a | | | | Added `/seed`, which displays the current world seed. |
| 1.7.2 | | | 13w36a | | | | Terrain-breaking change to world gen with the introduction of many new biomes. |
| 1.13 | | | 18w06a | | | | World generator rewritten in a mostly non-breaking way. |
| 1.18 | | | 1.18 Experimental Snapshot 1 | | | | Terrain-breaking change to the world gen with the introduction of multinoise, terrain noise, biome builders, and new caves. |
| Seed limit is now 48-bit.[6] |
| 1.18 experimental snapshot 2 | | | | World generator rewritten in a non-breaking way. |
| 21w41a | | | | Replaced the random number generator used in world generation, which reverted the seed limit back to 64-bit.[6] |
| Seeds have been reshuffled due to this change. Worlds no longer look like they did in previous snapshots. |
| 21w43a | | | | Seeds have been reshuffled again. Worlds no longer look like they did in previous snapshots. |
| 1.18.2 | | | 22w03a | | | | The seed "0" (zero) can now be used normally; entering it will generate a world with this seed, rather than a random seed. |
| Any spaces before or after a provided seed is now trimmed. |

### *Bedrock Edition*

| Pocket Edition Alpha | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v0.1.0 | | | | | | | Added seeds (32 bit). |
| v0.7.2 | | | | | | | The seed for each world is now displayed in the world selection menu when the "Edit" button is pressed. |
| v0.9.0 | | | build 1 | | | | Terrain-breaking change to world gen: using any given seed on older versions now generates a different world. |
| v0.11.0 | | | build 7 | | | | The seed is now displayed at the top of the screen in development versions while the player is in a world. |
| ? | | | | Before this update, the game would use a Unix timestamp as the seed if no other seed was typed. From this update onwards, it uses a random number generation. |
| *Bedrock Edition* | | | | | | | |
| 1.18.0 | | | beta 1.18.0.20 | | | | Terrain-breaking change to the world gen. Revamp Caves, Mountains, and Terrain height system. |
| beta 1.18.0.22 | | | | Replaced the random number generator used in world generation, resulting in different terrain being generated using same seed. |
| beta 1.18.0.24 | | | | Seeds have been reshuffled again. Worlds no longer look like they did in previous betas. |
| 1.18.30 | | | beta 1.18.20.21 | | | | Worlds can now be created with 64-bit seeds. |
| Single-digit seeds such as "0" can now be used normally. |

### Legacy Console Edition

| Legacy Console Edition | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Xbox 360 | Xbox One | PS3 | PS4 | PS Vita | Wii U | Switch |  |
| TU0 | CU1 | 1.00 | 1.00 | 1.00 | Patch 1 | 1.0.1 | Added seeds (64-bit). |
| TU3 | Added a seed display to the level load screen (requires existing levels to be saved out again to add the display). |
| TU5 | Terrain-breaking change to world gen. |
| TU12 | Minor terrain-breaking change to world gen. Biome info is now saved to worlds instead of recalculated when biome generation changes. |
| TU31 | CU19 | 1.22 | 1.22 | 1.22 | Patch 3 | Terrain-breaking change to world gen. |
| TU54 | CU44 | 1.52 | 1.52 | 1.52 | Patch 24 | 1.0.4 | Added Biome Scale slider and Find Balanced Seed option. |

### *New Nintendo 3DS Edition*

| *New Nintendo 3DS Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0.1.0 | | | | | | | Added seeds (32 bit). |

## References

1. [A Short Demystification of the 'Map Seed'](https://web.archive.org/web/20120307165955/https://www.mojang.com/2011/02/a-short-demystification-of-the-map-seed/) - Jens Bergensten on Mojang.com; February 23, 2011
2. [https://reddit.com/r/Minecraft/comments/hthrmk/](https://reddit.com/r/Minecraft/comments/hthrmk/)
3. [https://reddit.com/r/Minecraft/comments/iocx6f/](https://reddit.com/r/Minecraft/comments/iocx6f/)
4. [https://reddit.com/r/Minecraft/comments/iqg3ey/](https://reddit.com/r/Minecraft/comments/iqg3ey/)
5. [https://www.minecraftforum.net/forums/minecraft-java-edition/seeds/299287-i-was-just-curious-about-seed-0-we-found-it](https://www.minecraftforum.net/forums/minecraft-java-edition/seeds/299287-i-was-just-curious-about-seed-0-we-found-it)
6. [MC-236650](https://bugs.mojang.com/browse/MC-236650)

## External links

### Bedrock and Java Editions

- [**Chunkbase Minecraft Apps**: Online seed/map explorer tool](http://chunkbase.com/apps/)
- [**Minecraft Seeds on Reddit**: Community-driven Minecraft Seeds](https://www.reddit.com/r/minecraftseeds/)
- [**Minecraft Seeds**: Community-driven Minecraft Seeds](https://minecraft-seeds.net)
- [**LookingForSeed**: Find Minecraft seeds from various categories](https://lookingforseed.com)
- [**MinecraftSearch Seed Map**: Seed map with structure and ore finders for Java and Bedrock](https://minecraftsearch.com/tools/seed-map)

### Java Edition only

- [**Random Seed Reader**: Local Java tool](https://github.com/thedarkfreak/Minecraft-Save-Seed-Reader/wiki)
- [**Minecraft SeedHunt**: Selected Seeds from various categories for Java 1.16](https://seedhunt.net/advanced)
- [**Minecraft Seeds Java Edition**: New Seeds for 1.16.4 Version](http://minecraftgames.co.uk/seeds/)
- [**SeedCracker**: guess a seed from a multiplayer server](https://github.com/KaptainWutax/SeedCracker)
- [**Seeder**: Seed/map explorer tool and finder for *Java*](https://www.mcseeder.com/)

## Navigation
