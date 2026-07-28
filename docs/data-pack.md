# Data pack

> **Source:** <https://minecraft.wiki/w/Data_pack>  
> **Revision:** 3682935 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_21 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.20.2** — Data packs can now support multiple pack formats.
- **1.20.2** — Data packs can now contain overlays which are applied over the "normal" contents of a pack.
- **1.20.5** — Added wolf variants which can be defined through data packs.
- **1.20.5** — Data packs can now define custom banner patterns.
- **1.21** — Data packs can now define custom painting variants.
- **1.21** — Enchantments are now data-driven and can be defined through data packs.
- **1.21** — Data packs can additionally define enchantment providers.
- **1.21** — Renamed several directories: - `tags/items` -> `tags/item` - `tags/blocks` -> `tags/block` - `tags/entity_types` -> `tags/entity_type` - `tags/fluids` -> `tags/fluid` - `tags/game_events` -> `tags/game_event`
- **1.21** — Renamed several directories: - `structures` -> `structure` - `advancements` -> `advancement` - `recipes` -> `recipe` - `loot_tables` -> `loot_table` - `predicates` -> `predicate` - `item_modifiers` ->`item_modifier` - `functions` -> `function` - `tags/functions` -> `tags/function`
- **1.21** — Data packs can now define custom jukebox songs.
- **1.21.2** — Data packs can now define custom goat horn instruments.
- **1.21.2** — Trial spawner configurations can now also be defined in datapacks, instead of only in the Trial spawner block entity.
- **1.21.5** — Added end-to-end GameTest system. Added test environment and test instance definitions to data packs.
- **1.21.5** — Added pig variants; including definitions in data packs.
- **1.21.5** — Cat and Frog variants can now be defined in data packs.
- **1.21.5** — Added cow variants; including definitions in data packs.
- **1.21.5** — Added chicken variants; including definitions in data packs.
- **1.21.5** — Added wolf sound variants definition to data packs.
- **1.21.6** — Added `/datapack create`, that can create new empty directory data packs for current world.
- **1.21.6** — Data packs can now define custom dialogs.
- **1.21.11** — Added timelines.

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**upcoming** — 3 occurrence(s):

- - decorated\_pot\_pattern**\***: Defines decorated pot patterns from `provides_pottery_pattern` data component.​[*upcoming: JE 26.3*]
- - number\_provider: Number provider used in loot tables, `compostable` data component etc.​[*upcoming: JE 26.3*]
- - slot\_source: Defines slot source, used in `/item` and loot table.​[*upcoming: JE 26.3*]

---
This article is about the data pack system. For the command, see Commands/datapack. For the resource pack system, see Resource pack. For the similar system in *Bedrock Edition*, see Behavior pack.

This feature is exclusive to *Java Edition*.

There are related tutorial pages for this topic!

See Tutorial:Creating a data pack and Tutorial:Importing a data pack.

A **data pack** or **datapack** is a collection of data used to configure a number of features of *Minecraft*. A data pack is either a folder or a [`.zip` file](https://en.wikipedia.org/wiki/Zip_(file_format)) containing a `pack.mcmeta` file. Data packs are used to define among others advancements, dimensions, enchantments, loot tables, recipes, structures, and biomes (see § Contents for a full list). The definitions of the vanilla features is done using a built-in data pack. Experiments are enabled by adding separate bundled data packs to a world. Similarly, custom data packs can be added to a world to add or modify features and define functions. A datapack can also change/edit terrain.

## Usage

Data packs can be added to a world during world creation in the Create New World screen in the **More** tab by clicking the **Data Packs** button. This menu allows drag-and-drop of data packs from a file explorer. Alternatively, data packs can be added to an existing world by manually placing them in the `.minecraft/saves/<world>/datapacks` folder of a world.

When adding or modifying a data pack while the world is loaded, changes done to registry tags, loot tables, recipes, advancements, item modifiers, predicates, functions, and structure templates can be loaded using the `/reload` command. Other features require the world or server to be rebooted for changes to take effect (see § Experimental Settings).

Data packs load their data based on the load order. This order can be seen and altered in the **Data Packs** screen during world creation, and by using the `/datapack` command. The loading order of data packs is stored in the `level.dat` file. If a file exists in multiple data packs only the file in the last data pack is used. This is often referred to this file **overriding** the files in the earlier packs. However, tag files without `"replace": true` merge their content with the files loaded from earlier packs.

If a data pack is corrupted, broken or contains malformed entries that can't simply be ignored by the game (such as by adding a non-existent entry in a vanilla tag), an error is shown when trying to open the world that asks the player if they want to enable Safe Mode, or return to title and fix the issue. Safe Mode disables all data packs except the vanilla one, possibly allowing the world to be opened.

### Experimental Settings

Not to be confused with Experiments.

Some data pack features are considered **experimental settings** by the game. If a world has enabled a data pack that uses these features, opening the world in singleplayer will display a warning screen to the player. Additionally, worlds using experimental settings **cannot** be played on Realms: Attempting to upload such a world to Realms results in a server error.

Internally, most experimental settings use *dynamic registries* (as opposed to *static registries*). This means any changes regarding these features cannot be loaded using the `/reload` command: the world must be exited and reopened (singleplayer), or the server rebooted (multiplayer) for the changes to take effect.

For a data pack to be marked as "using experimental settings", it must contain at least one valid file inside one of several specific folders. For instance, defining a custom instrument inside the `data/instrument/` folder **counts** as using experimental settings, whereas defining an instrument through item components **does not** count as such.

See § Folder structure for which folders are considered experimental settings.

## Contents

Data packs use a folder structure to contain the data. On the top level, a data pack has to contain a `pack.mcmeta` file containing meta-data about the data pack. The data is organized into namespaces to avoid files from different packs unintentionally interfering with each other. Files are loaded as follows:

- The file `data/<namespace>/<registry name>/<path>.json` is loaded into the `<registry name>` registry with ID `<namespace>:<path>`. Both `<registry name>` and `<path>` can contain slashes (`/`), which results in extra sub-folders.
  - Functions use the `.mcfunction` extension
  - Structure files use the `.nbt` extension
- Tags are loaded from files `data/<namespace>/tags/<registry name>/<path>.json` which results in a `<registry name>` tag named `#<namespace>:<path>`.

### Folder structure

If a folder is marked with an asterisk (**\***), it means that the game considers the feature to be experimental, and having a valid file inside any of these folders will mark the data pack as using experimental settings.

- / *<data pack name>*
  - pack.mcmeta: Metadata of the data pack. This is the only mandatory file.
  - pack.png: The picture to display next to the data pack in the "Data Pack Selection" screen.
  - data
    - *<namespace>*: Folder of the namespace to use, see Identifier § Namespaces. More than one directory for different namespaces may exist under the `data` directory. The `minecraft` namespace is used for vanilla files and can be used to override them.
      - function: `.mcfunction` files with lists of commands.
      - structure: `.nbt` files defining a saved structure of blocks.
      - tags: Collections of things. Each sub-folder defines tags of a specific type using `.json` files.
        - function: Tags of functions.
        - *<registry name>*: Tags can be defined for any registry, see Tag (Java Edition) § List of tag types for tag types used.

        All following folders contain `.json` files defining the content:
      - advancement: Definitions of advancements.
      - banner\_pattern**\***: Textures and names to use for banner patterns.
      - cat\_variant**\***: Textures and spawn conditions of cat variants.
      - chat\_type**\***: Formatting of chat messages.
      - chicken\_variant**\***: Textures and spawn conditions of chicken variants.
      - cow\_variant**\***: Textures and spawn conditions of cow variants.
      - damage\_type**\***: Attributes of damage and death messages.
      - decorated\_pot\_pattern**\***: Defines decorated pot patterns from `provides_pottery_pattern` data component.​[*upcoming: JE 26.3*]
      - dialog**\***: Definitions of dialogs.
      - dimension**\***: Biome layout and terrain of dimensions.
      - dimension\_type**\***: Properties of dimensions.
      - enchantment**\***: Enchantment effects, supported items, level cost, etc.
      - enchantment\_provider**\***: Selection of enchantments for specific uses.
      - frog\_variant**\***: Textures and spawn conditions of frog variants.
      - instrument**\***: Instruments for goat horns.
      - item\_modifier: Loot functions used to modify items.
      - jukebox\_song**\***: Jukebox song definitions.
      - loot\_table: Loot from mobs, blocks, chests, etc.
      - number\_provider: Number provider used in loot tables, `compostable` data component etc.​[*upcoming: JE 26.3*]
      - painting\_variant**\***: Size and texture of paintings.
      - pig\_variant**\***: Textures and spawn conditions of pig variants.
      - predicate: Tests for specific conditions based on position, mobs, etc.
      - recipe: Recipes for crafting, smelting, etc.
      - slot\_source: Defines slot source, used in `/item` and loot table.​[*upcoming: JE 26.3*]
      - sulfur\_cube\_archetype**\***: Defines Sulfur Cube archetypes.
      - test\_environment**\***: A way to group up GameTests and give them the right preconditions to run.
      - test\_instance**\***: A test that can be run by the GameTest framework.
      - timeline**\***: A timeline which specifies events and attributes according to the time of day.
      - trade\_set**\***: A set of trades selected by villagers and wandering traders.
      - trial\_spawner**\***: Configuration of trial spawners.
      - trim\_material**\***: Colors, ingredients, and name of materials for trimming
      - trim\_pattern**\***: Textures and name of patterns for trimming
      - villager\_trade**\***: Trades of villagers and wandering traders
      - wolf\_sound\_variant**\***: Sound variants of wolfs.
      - wolf\_variant**\***: Textures and spawn conditions of wolf variants.
      - world\_clock**\***: Clocks used to keep track of internal time.
      - worldgen**\***
        - biome: Biome generation options, effects, etc.
        - configured\_carver: Carver cave definitions
        - configured\_feature: Configuration of features.
        - density\_function: Mathematical operations to calculate values for each position in the world.
        - noise: Size and amplitudes of a [noise](https://en.wikipedia.org/wiki/Perlin_noise).
        - noise\_settings: Terrain shape including noise caves, and main terrain block types.
        - placed\_feature: Placement of features within a chunk.
        - processor\_list: Post-processing of blocks in structures.
        - structure: Definition of structure generation and allowed biomes.
        - structure\_set: Distribution of a set of structures within the world.
        - template\_pool: A set of templates (structure files) for use in jigsaw structures.
        - world\_preset: Sets of dimensions selectable in the **Create World** screen.
        - flat\_level\_generator\_preset: Presets selectable for the "Superflat" world type.
        - multi\_noise\_biome\_source\_parameter\_list: Name of a preset to use for the multi noise biome layout.
      - zombie\_nautilus\_variant**\***: Textures and spawn conditions of zombie nautilus variants.

### pack.mcmeta

Main article: pack.mcmeta

A data pack is identified by *Minecraft* based on the presence of the `pack.mcmeta` file in the root directory of the data pack, which contains data in [JSON](https://en.wikipedia.org/wiki/JSON) format.

A `pack.mcmeta` file to produce a data pack that looks like the "vanilla" data pack in 1.21.9 would look like this:

`pack.mcmeta`

```
{
    "pack": {
        "description": {
            "translate": "dataPack.vanilla.description"
        },
        "min_format": [88, 0],
        "max_format": [88, 0]
    }
}
```

#### Pack format

For the full list of pack formats in all versions, see Pack format § List of data pack formats.

Data pack formats

| Value | Releases | Significant/Breaking Changes |
| --- | --- | --- |
| 4 | 1.13 – 1.14.4 | - Added the initial pack format version of 4. |
| 5 | 1.15 – 1.16.1 | - Added predicates. |
| 6 | 1.16.2 – 1.16.5 | - Added experimental support for custom world generation. |
| 7 | 1.17 – 1.17.1 | - The `/replaceitem` command was replaced with `/item`. - The `set_damage` loot function now require a valid [String] type field. |
| 8 | 1.18 – 1.18.1 | - Loot table functions `set_contents` and `set_loot_table` now require a [String] type field. - Removed length limits for scoreboards, score holders and team names. |
| 9 | 1.18.2 | - The `/locate` command now takes a configured structure as its first parameter rather than a structure type, so many grouped structures now require a structure type tag. E.g. `/locate village` is now `/locate #village`. |
| 10 | 1.19 – 1.19.3 | - Data packs can now have a [NBT Compound / JSON Object] filter section in `pack.mcmeta`. - Merged `/locatebiome` with `/locate`, changing its syntax. |
| 12 | 1.19.4 | - Added damage types. - Removed all boolean flags in damage predicates, instead damage type tags can now be tested for. - Biome field [String] precipitation changed to [Boolean] has\_precipitation. |
| 15 | 1.20 – 1.20.1 | - Changed sign NBT. E.g. `Text1` is now `front_text.messages[0]`. - All fields in `placed_block`, `item_used_on_block`, and `allay_drop_item_on_block` advancement triggers have been collapsed to a single location field. - Renamed the `alternative` predicate to `any_of`. |
| 18 | 1.20.2 | - Added function macros. - Effects now use namespaced IDs rather than numeric values in NBT. E.g. `1` is now `minecraft:speed`. |
| 26 | 1.20.3 – 1.20.4 | - Text components are parsed more strictly. - Renamed `grass` block and item to `short_grass`. - Added scoreboard display names and number formats. |
| 41 | 1.20.5 – 1.20.6 | - Renamed the `sweeping` enchantment to `sweeping_edge`. - Changed the behavior of the `item_used_on_block` advancement trigger. - Replaced some behavior of amplifiers above 127 with attributes. - Unstructured NBT data attached item stacks has been replaced with structured components. - Removed `durability`, `potions`, `nbt`, and `enchantments` fields in item predicates. - Recipe output can now specify components. - Int and float providers used in worldgen definitions are no longer wrapped in an extra `value` field next to `type`. - Added new item sub-predicates and loot functions. |
| 48 | 1.21 – 1.21.1 | - Added data driven enchantments. - Added data driven paintings. - Renamed the `enchantment` field to `enchantments` in the item sub predicate. - Renamed legacy folders like `loot_tables` and `tags/items` to `loot_table` and `tags/item` and `functions` to `function`. - Removed the [NBT List / JSON Array] power fireball tag and replaced it with [Float] acceleration\_power. - Attributes now have a single resource location `id` field instead of a `name` and `uuid`. |
| 57 | 1.21.2 – 1.21.3 | - Removed attribute ID prefixes such as `generic.`. - Changed formats of data components, loot tables and predicates. - Added `/rotate` - Added new data components, loot tables and item tags. - Added key input predicate. - Added `crafting_transmute` recipe type. - Renamed enchantment effect `damage_item` to `change_item_damage`. |
| 61 | 1.21.4 | - Renamed tnt minecart `TNTFuse` to `fuse`. - Renamed fields of furnace block entities. - Added required field `duration` to trail particle. - Changed format of the `custom_model_data` component and loot function. |
| 71 | 1.21.5 | - Text components are now saved as objects in NBT rather than strings containing JSON and many commands such as `/tellraw` now take SNBT rather than JSON. - The Game Tests system is now accessible through data packs and for mods. - Added components `blocks_attacks`, `break_sound`, `potion_duration_scale`, `provides_banner_patterns`, `provides_trim_material`, `tooltip_display`, and `weapon`. - Added many entity variant components. - Commands that place blocks, such as `/setblock`, now have a `strict` argument. - Pig, frog, chicken and cow variants are now data-driven. - The [NBT List / JSON Array] ArmorItems, [NBT List / JSON Array] HandItems, [NBT Compound / JSON Object] body\_armor\_item, [NBT Compound / JSON Object] SaddleItem, and [Boolean] Saddle NBT tags were removed and merged into the [NBT Compound / JSON Object] equipment field. - The [Int] SpawnX, [Int] SpawnY, [Int] SpawnZ, [Float] SpawnAngle, [String] SpawnDimension, and [Boolean] SpawnForced NBT tags were removed and merged into the [NBT Compound / JSON Object] respawn field. - Item components that had only two fields (with one of them being the [Boolean] show\_in\_tooltip) now have the other field inlined to top-level. - Removed `hide_tooltip` and `hide_additional_tooltip` components and [Boolean] show\_in\_tooltip field from all components in favor of the new `tooltip_display` component. - Many changes have been made to entities and block entities' NBT data. - Many other changes have also been made. For all technical changes and additions, see Java Edition 1.21.5 § Technical. |
| 80 | 1.21.6 | - Dimension type definitions have a new optional field, `cloud_height` that indicates what Y-level the clouds start in the dimension. - The [NBT Compound / JSON Object] Particle field has been renamed to [NBT Compound / JSON Object] custom\_particle, and now always functions as an exact override for the default colored `entity_effect` particle. - `/datapack` now has a new argument: `create`. - Added `/version`. - Added [NBT Compound / JSON Object] display field to `attribute_modifier` item component entries. - Added `camera_distance`, `waypoint_transmit_range`, and `waypoint_receive_range` attributes. - `painting/variant` item component no longer accepts inline variants. - Added `/dialog`. - Added `ui` sound category. |
| 81 | 1.21.7 – 1.21.8 | - Added `music_disc_lava_chicken` item and `minecraft:music_disc.lava_chicken` sound event |
| 88.0 | 1.21.9 – 1.21.10 | - Pack format now includes minor versions, which are incremented instead of the major version when non-breaking changes are made. - `minecraft:profile` components now resolve differently. - World borders are now dimension-specific. |
| 94.1 | 1.21.11 | - Added `/stopwatch` and `/execute (if|unless) stopwatch`. - Added several new predicate types. - Added environment attributes. - The fields of the loot function `minecraft:filtered` have been changed. - Added slot sources. - The names of all game rules have been changed. - Added timelines. - Added `minecraft:attack_range` component. |
| 101.1 | 26.1 – 26.1.2 | - Villager trades are now data-driven. - Wolf sound variants sound events in `wolf_sound_variant` have been moved into a new field `adult_sounds` and the field `baby_sounds` was added. - Added world clocks and time markers, and subsequently changed the behaviour of `/time`. - Added and renamed many block and fluid tags which now control support and growth of crops. - Added `minecraft:dye` data component and many `crafting_special_*` recipe types are now (more) data-driven. - Added sound variants to Cats, Pigs, Cows and Chickens, which can be data-driven. - Changed the behaviour of `minecraft:nbt` text component. - Features spawned from Bone Meal are now data-driven. |
| 107.1 | 26.2 | - Added various things related to sulfur cubes, including the `minecraft:sulfur_cube_archetype` registry and `sulfur_cube_content` data component. - For configured features and dimension types, `cannot_replace`, `invalid_blocks`, `root_replaceable`, `replaceable` and `infiniburn` now also accept an ID and or a list of IDs in addition to a tag. - The entity predicate format has changed from a structure with multiple optional fields to one similar to data component maps. Unknown fields are now rejected rather than ignored. - Added `minecraft:entity_tags` entity sub-predicate. - Added additional fields to `lake` feature type. - Slime sub-predicate has been renamed from `minecraft:type_specific/slime` to `minecraft:type_specific/cube_mob`. - Added `minecraft:nameplate_distance` and `below_name_distance` attributes. - Team color arguments (used in `/team modify [name] color` and `/waypoint modify [name] color`) now accept only lowercase names with underscores. - Removed `weird_scaled_sampler` density function, which is replaced by `interval_select`. - Removed the `noise_gradient` surface rule in favour of `noise_threshold`, which has a new boolean field `is_3d`. - Removed `HurtByTimestamp` in favour of `ticks_since_last_hurt_by_mob`. - Added `#causes_periodic_geyser_eruptions` and `#causes_continuous_geyser_eruptions` block tags. - Added `/unpublish`. - Added `sulfur_cube_hot` damage type. - Added `flat_all_dimensions` world preset. - Added `#not_affected_by_geysers` entity type tag. |

## History

For pack format history, see  § Pack format.

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.13 | | | 17w43a | | | | Added data packs. |
| 17w46a | | | | Added `/datapack`, a command to control loaded data packs. |
| 17w48a | | | | Data packs can now load custom recipes. |
| Added the initial pack format version of `4`. |
| 17w49a | | | | Tags can now be created with data packs. |
| 17w49b | | | | Tags can now be created for functions. |
| Functions tagged in `minecraft:tick` now run at the beginning of every tick. |
| 18w01a | | | | Functions tagged in `minecraft:load` now run once after a (re)load. |
| Crash reports now list what data packs are enabled. |
| 1.14 | | | 18w43a | | | | Tags can now be created for entity types. |
| 1.15 | | | 19w38a | | | | Added predicates. |
| 1.16 | | | 20w22a | | | | Slightly changed data pack loading to prevent custom data packs from crashing. |
| If data pack reload fails, changes are not applied and the game continues using previous data. |
| Changes to data pack list are stored only after successful reload. |
| If existing data packs prevent the world from loading, the game gives an option to load the world in safe mode, which loads only vanilla data pack. |
| Added `--safeMode` option for servers to load only with vanilla data pack. |
| Game now detects any critical data pack issues, such as required tags being missing, and prevent the world from being loaded. |
| Pre-release 1 | | | | Data packs can now be loaded before the world is created. |
| Data packs can now add and change dimensions and dimension types. |
| 1.16.2 | | | 20w27a | | | | Data packs can now have a pack.png in the root folder, and display it in the data pack menu. |
| 20w28a | | | | Custom worlds now support custom biomes and can now be used in custom dimension generators. |
| Data packs can now customize world generation in the `worldgen` folder. |
| 1.17 | | | 20w45a | | | | Pack format in `version.json` has been split into data and resource versions. |
| 20w46a | | | | Added item modifiers. |
| 1.18.2 | | | Pre-release 1 | | | | It is now possible to add custom structures in experimental data packs: the game now generates and stores data-driven configured structures. |
| A lot of the cave generation is now configurable through data packs. |
| 1.19 | | | 22w11a | | | | Data packs can now apply filters which block files from packs applied before the current pack. |
| 1.19.3 | | | 22w42a | | | | Added chat types. |
| Added a subsection called `datapacks`. |
| The Vanilla world generation data pack is now visible within the game's jar. |
| 1.19.4 Experiment Update 1.20 | | | 23w04a | | | | Added `trim_pattern` and `trim_material` registries which is used to define armor trims. |
| 1.19.4 | | | 23w06a | | | | Added damage types. |
| 1.20 | | | 23w06a | | | | Trim patterns and trim materials definitions are no longer experimental. |
| 1.20.2 | | | 23w31a | | | | Data packs can now support multiple pack formats. |
| Data packs can now contain overlays which are applied over the "normal" contents of a pack. |
| 1.20.5 | | | 24w10a | | | | Added wolf variants which can be defined through data packs. |
| Data packs can now define custom banner patterns. |
| 1.21 | | | 24w18a | | | | Data packs can now define custom painting variants. |
| Enchantments are now data-driven and can be defined through data packs. |
| Data packs can additionally define enchantment providers. |
| 24w19a | | | | Renamed several directories:  - `tags/items` -> `tags/item` - `tags/blocks` -> `tags/block` - `tags/entity_types` -> `tags/entity_type` - `tags/fluids` -> `tags/fluid` - `tags/game_events` -> `tags/game_event` |
| 24w21a | | | | Renamed several directories:  - `structures` -> `structure` - `advancements` -> `advancement` - `recipes` -> `recipe` - `loot_tables` -> `loot_table` - `predicates` -> `predicate` - `item_modifiers` ->`item_modifier` - `functions` -> `function` - `tags/functions` -> `tags/function` |
| Data packs can now define custom jukebox songs. |
| 1.21.2 | | | 24w33a | | | | Data packs can now define custom goat horn instruments. |
| 24w35a | | | | Trial spawner configurations can now also be defined in datapacks, instead of only in the Trial spawner block entity. |
| 1.21.5 | | | 25w03a | | | | Added end-to-end GameTest system. Added test environment and test instance definitions to data packs. |
| Added pig variants; including definitions in data packs. |
| 25w04a | | | | Cat and Frog variants can now be defined in data packs. |
| 25w05a | | | | Added cow variants; including definitions in data packs. |
| 25w06a | | | | Added chicken variants; including definitions in data packs. |
| 25w08a | | | | Added wolf sound variants definition to data packs. |
| 1.21.6 | | | 25w15a | | | | Added `/datapack create`, that can create new empty directory data packs for current world. |
| 25w20a | | | | Data packs can now define custom dialogs. |
| 1.21.11 | | | 25w45a | | | | Added timelines. |

## Issues

Issues relating to "Data pack" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28mc%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22Data%20pack%22%29%20ORDER%20BY%20resolution%20DESC).

## Gallery

## See also

- Add-on
- Resource pack
- Tutorial: Importing a data pack
- Tutorial: Creating a data pack
- Tutorial: Creating a resource pack

## External links

- [misode.github.io](https://misode.github.io/) provides data pack generators

## Navigation
