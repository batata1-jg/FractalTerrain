# Biome tag (Java Edition)

> **Source:** <https://minecraft.wiki/w/Biome_tag_%28Java_Edition%29>  
> **Revision:** 3643312 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_12 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.5** — Added `spawns_cold_variant_farm_animals`, and `spawns_warm_variant_farm_animals` tags.
- **1.21.5** — Added `pale_garden` to `#has_structure/woodland_mansion` tag.
- **1.21.5** — Added `windswept_savanna` and removed `windswept_hills` from `#spawns_warm_variant_farm_animals` tag.
- **1.21.5** — Added 18 values to `#spawns_cold_variant_farm_animals` tag. - added `cold_ocean`, `deep_cold_ocean`, `deep_dark`, `deep_frozen_ocean`, `frozen_ocean`, `frozen_peaks`, `frozen_river`, `grove`, `ice_spikes`, `jagged_peaks`, `snowy_beach`, `snowy_plains`, `snowy_slopes`, `stony_peaks`, `taiga`, `windswept_gravelly_hills`, `windswept_hills`, and `#is_end`
- **1.21.5** — Added 9 and removed 9 values from `#spawns_warm_variant_farm_animals` tag. - added `desert`, `mangrove_swamp`, `warm_ocean`, `lukewarm_ocean`, `deep_lukewarm_ocean`, `#is_jungle`, `#is_savanna`, `#is_nether`, and `#is_badlands` - removed `savanna`, `savanna_plateau`, `windswept_savanna`, `jungle`, `sparse_jungle`, `bamboo_jungle`, `eroded_badlands`, `wooded_badlands`, and `badlands`
- **1.21.9** — Added `cherry_grove` to `#stronghold_biased_to` tag.
- **1.21.11** — Removed `#has_closer_water_fog`, `#increased_fire_burnout`, `#plays_underwater_music`, and `snow_golem_melts` tags.
- **1.21.11** — Added `#spawns_coral_variant_zombie_nautilus` tag.
- **1.21.11** — Removed `#without_patrol_spawns` tag.
- **26.2** — Added `sulfur_caves` to `#is_overworld`, `#stronghold_biased_to`, `#has_structure/mineshaft`, `#has_structure/ruined_portal_standard`, and `#has_structure/trial_chambers` tags.
- **26.3** *(unreleased)* — Added 18 tags. - added `#has_structure/abandoned_camp_bamboo_jungle`, `#has_structure/abandoned_camp_birch_forest`, `#has_structure/abandoned_camp_cherry_grove`, `#has_structure/abandoned_camp_dappled_forest`, `#has_structure/abandoned_camp_flower_forest`, `#has_structure/abandoned_camp_forest`, `#has_structure/abandoned_camp_meadow`, `#has_structure/abandoned_camp_old_growth_birch_forest`, `#has_
- **26.3** *(unreleased)* — Added `dappled_forest` to `#is_forest`, `#is_overworld`, `#spawns_cold_variant_farm_animals`, and `#has_structure/trial_chambers` tags.

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**upcoming** — 22 occurrence(s):

- - `minecraft:dappled_forest`​[*upcoming: JE 26.3*]
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_bamboo\_jungle​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_birch\_forest​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_cherry\_grove​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_dappled\_forest​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_flower\_forest​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_forest​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_meadow​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_old\_growth\_birch\_forest​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_old\_growth\_pine\_taiga​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_old\_growth\_spruce\_taiga​[*upcoming: JE 26.3*] *(1 values)*
- - [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_pale\_garden​[*upcoming: JE 26.3*] *(1 values)*
- _…7 more_

---
"Biome tag" redirects here. For Biome tags in *Bedrock Edition*, see Biome tag (Bedrock Edition).

A **biome tag** is a group of biomes. Biome tags are used to control where structures generate, the spawn conditions of various entities, and numerous other gameplay features. See below for the use of each tag. Biome tags can also be used when testing for biome arguments in commands with `#<resource location>`, which succeeds if the biome matches any of the biomes specified in the tag.

## List of tags

### allows\_surface\_slime\_spawns

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:allows\_surface\_slime\_spawns *(2 values)*

  - `minecraft:mangrove_swamp`
  - `minecraft:swamp`

### allows\_tropical\_fish\_spawns\_at\_any\_height

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:allows\_tropical\_fish\_spawns\_at\_any\_height *(1 values)*

  - `minecraft:lush_caves`

### is\_badlands

​[*more information needed*]

Used in the `#has_structure/mineshaft_mesa`, `#has_structure/ruined_portal_mountain`, `#spawns_warm_variant_farm_animals`, and `#spawns_warm_variant_frogs` tags.

- [NBT List / JSON Array] #minecraft:is\_badlands *(3 values)*

  - `minecraft:badlands`
  - `minecraft:eroded_badlands`
  - `minecraft:wooded_badlands`

### is\_beach

​[*more information needed*]

Used in the `#has_structure/buried_treasure`, `#has_structure/mineshaft`, `#has_structure/ruined_portal_standard`, and `#has_structure/shipwreck_beached` tags.

- [NBT List / JSON Array] #minecraft:is\_beach *(2 values)*

  - `minecraft:beach`
  - `minecraft:snowy_beach`

### is\_deep\_ocean

​[*more information needed*]

Used in the `#has_structure/ocean_monument`, and `#is_ocean` tags.

- [NBT List / JSON Array] #minecraft:is\_deep\_ocean *(4 values)*

  - `minecraft:deep_cold_ocean`
  - `minecraft:deep_frozen_ocean`
  - `minecraft:deep_lukewarm_ocean`
  - `minecraft:deep_ocean`

### is\_end

​[*more information needed*]

Used in the `#spawns_cold_variant_farm_animals`, and `#spawns_cold_variant_frogs` tags.

- [NBT List / JSON Array] #minecraft:is\_end *(5 values)*

  - `minecraft:end_barrens`
  - `minecraft:end_highlands`
  - `minecraft:end_midlands`
  - `minecraft:small_end_islands`
  - `minecraft:the_end`

### is\_forest

​[*more information needed*]

Used in the `#has_structure/mineshaft`, and `#has_structure/ruined_portal_standard` tags.

- [NBT List / JSON Array] #minecraft:is\_forest *(8 values)*

  - `minecraft:birch_forest`
  - `minecraft:dappled_forest`​[*upcoming: JE 26.3*]
  - `minecraft:dark_forest`
  - `minecraft:flower_forest`
  - `minecraft:forest`
  - `minecraft:grove`
  - `minecraft:old_growth_birch_forest`
  - `minecraft:pale_garden`

### is\_hill

​[*more information needed*]

Used in the `#has_structure/mineshaft`, and `#has_structure/ruined_portal_mountain` tags.

- [NBT List / JSON Array] #minecraft:is\_hill *(3 values)*

  - `minecraft:windswept_forest`
  - `minecraft:windswept_gravelly_hills`
  - `minecraft:windswept_hills`

### is\_jungle

​[*more information needed*]

Used in the `#has_structure/mineshaft`, `#has_structure/ruined_portal_jungle`, `#spawns_warm_variant_farm_animals`, and `#spawns_warm_variant_frogs` tags.

- [NBT List / JSON Array] #minecraft:is\_jungle *(3 values)*

  - `minecraft:bamboo_jungle`
  - `minecraft:jungle`
  - `minecraft:sparse_jungle`

### is\_mountain

​[*more information needed*]

Used in the `#has_structure/mineshaft`, `#has_structure/pillager_outpost`, and `#has_structure/ruined_portal_mountain` tags.

- [NBT List / JSON Array] #minecraft:is\_mountain *(6 values)*

  - `minecraft:cherry_grove`
  - `minecraft:frozen_peaks`
  - `minecraft:jagged_peaks`
  - `minecraft:meadow`
  - `minecraft:snowy_slopes`
  - `minecraft:stony_peaks`

### is\_nether

​[*more information needed*]

Used in the `#has_structure/nether_fortress`, `#has_structure/ruined_portal_nether`, `#spawns_warm_variant_farm_animals`, and `#spawns_warm_variant_frogs` tags.

- [NBT List / JSON Array] #minecraft:is\_nether *(5 values)*

  - `minecraft:basalt_deltas`
  - `minecraft:crimson_forest`
  - `minecraft:nether_wastes`
  - `minecraft:soul_sand_valley`
  - `minecraft:warped_forest`

### is\_ocean

​[*more information needed*]

Used in the `#has_structure/mineshaft`, `#has_structure/ruined_portal_ocean`, `#has_structure/shipwreck`, `#required_ocean_monument_surrounding`, and `#water_on_map_outlines` tags.

- [NBT List / JSON Array] #minecraft:is\_ocean *(6 values)*

  - `#minecraft:is_deep_ocean`
  - `minecraft:cold_ocean`
  - `minecraft:frozen_ocean`
  - `minecraft:lukewarm_ocean`
  - `minecraft:ocean`
  - `minecraft:warm_ocean`

### is\_overworld

​[*more information needed*]

Used in the `#has_structure/stronghold` tag.

- [NBT List / JSON Array] #minecraft:is\_overworld *(56 values)*

  - `minecraft:badlands`
  - `minecraft:bamboo_jungle`
  - `minecraft:beach`
  - `minecraft:birch_forest`
  - `minecraft:cherry_grove`
  - `minecraft:cold_ocean`
  - `minecraft:dappled_forest`​[*upcoming: JE 26.3*]
  - `minecraft:dark_forest`
  - `minecraft:deep_cold_ocean`
  - `minecraft:deep_dark`
  - `minecraft:deep_frozen_ocean`
  - `minecraft:deep_lukewarm_ocean`
  - `minecraft:deep_ocean`
  - `minecraft:desert`
  - `minecraft:dripstone_caves`
  - `minecraft:eroded_badlands`
  - `minecraft:flower_forest`
  - `minecraft:forest`
  - `minecraft:frozen_ocean`
  - `minecraft:frozen_peaks`
  - `minecraft:frozen_river`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:jungle`
  - `minecraft:lukewarm_ocean`
  - `minecraft:lush_caves`
  - `minecraft:mangrove_swamp`
  - `minecraft:meadow`
  - `minecraft:mushroom_fields`
  - `minecraft:ocean`
  - `minecraft:old_growth_birch_forest`
  - `minecraft:old_growth_pine_taiga`
  - `minecraft:old_growth_spruce_taiga`
  - `minecraft:pale_garden`
  - `minecraft:plains`
  - `minecraft:riverUnit`
  - `minecraft:savanna`
  - `minecraft:savanna_plateau`
  - `minecraft:snowy_beach`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`
  - `minecraft:sparse_jungle`
  - `minecraft:stony_peaks`
  - `minecraft:stony_shore`
  - `minecraft:sulfur_caves`
  - `minecraft:sunflower_plains`
  - `minecraft:swamp`
  - `minecraft:taiga`
  - `minecraft:warm_ocean`
  - `minecraft:windswept_forest`
  - `minecraft:windswept_gravelly_hills`
  - `minecraft:windswept_hills`
  - `minecraft:windswept_savanna`
  - `minecraft:wooded_badlands`

### is\_river

​[*more information needed*]

Used in the `#has_structure/mineshaft`, `#has_structure/ruined_portal_standard`, `#more_frequent_drowned_spawns`, `#reduce_water_ambient_spawns`, `#required_ocean_monument_surrounding`, and `#water_on_map_outlines` tags.

- [NBT List / JSON Array] #minecraft:is\_river *(2 values)*

  - `minecraft:frozen_river`
  - `minecraft:riverUnit`

### is\_savanna

​[*more information needed*]

Used in the `#spawns_warm_variant_farm_animals`, and `#spawns_warm_variant_frogs` tags.

- [NBT List / JSON Array] #minecraft:is\_savanna *(3 values)*

  - `minecraft:savanna`
  - `minecraft:savanna_plateau`
  - `minecraft:windswept_savanna`

### is\_taiga

​[*more information needed*]

Used in the `#has_structure/mineshaft`, and `#has_structure/ruined_portal_standard` tags.

- [NBT List / JSON Array] #minecraft:is\_taiga *(4 values)*

  - `minecraft:old_growth_pine_taiga`
  - `minecraft:old_growth_spruce_taiga`
  - `minecraft:snowy_taiga`
  - `minecraft:taiga`

### mineshaft\_blocking

Used in mineshaft generation to check if next structure piece can`t be placed

- [NBT List / JSON Array] #minecraft:mineshaft\_blocking *(1 values)*

  - `minecraft:deep_dark`

### more\_frequent\_drowned\_spawns

Drowned spawn attempts have a 1 in 15 chance to succeed to spawn in biomes in this tag (as opposed to 1 in 40 in other biomes) and are not limited to least 5 blocks below the sea level.

- [NBT List / JSON Array] #minecraft:more\_frequent\_drowned\_spawns *(1 values)*

  - `#minecraft:is_river`

### polar\_bears\_spawn\_on\_alternate\_blocks

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:polar\_bears\_spawn\_on\_alternate\_blocks *(2 values)*

  - `minecraft:deep_frozen_ocean`
  - `minecraft:frozen_ocean`

### produces\_corals\_from\_bonemeal

Coral can be farmed by using bone meal in these biomes.

- [NBT List / JSON Array] #minecraft:produces\_corals\_from\_bonemeal *(1 values)*

  - `minecraft:warm_ocean`

### reduce\_water\_ambient\_spawns

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:reduce\_water\_ambient\_spawns *(1 values)*

  - `#minecraft:is_river`

### required\_ocean\_monument\_surrounding

These biomes must be present around a location where a monument is possible to generate.

- [NBT List / JSON Array] #minecraft:required\_ocean\_monument\_surrounding *(2 values)*

  - `#minecraft:is_ocean`
  - `#minecraft:is_river`

### spawns\_cold\_variant\_farm\_animals

Cows, pigs, and chickens that spawn in these biomes will be their cold variant. Sheep that spawn in these biomes will use the cold wool colors.

- [NBT List / JSON Array] #minecraft:spawns\_cold\_variant\_farm\_animals *(23 values)*

  - `#minecraft:is_end`
  - `minecraft:cold_ocean`
  - `minecraft:dappled_forest`​[*upcoming: JE 26.3*]
  - `minecraft:deep_cold_ocean`
  - `minecraft:deep_dark`
  - `minecraft:deep_frozen_ocean`
  - `minecraft:frozen_ocean`
  - `minecraft:frozen_peaks`
  - `minecraft:frozen_river`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:old_growth_pine_taiga`
  - `minecraft:old_growth_spruce_taiga`
  - `minecraft:snowy_beach`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`
  - `minecraft:stony_peaks`
  - `minecraft:taiga`
  - `minecraft:windswept_forest`
  - `minecraft:windswept_gravelly_hills`
  - `minecraft:windswept_hills`

### spawns\_cold\_variant\_frogs

Frogs that spawn in these biomes will be their cold(green) variant.

- [NBT List / JSON Array] #minecraft:spawns\_cold\_variant\_frogs *(13 values)*

  - `#minecraft:is_end`
  - `minecraft:deep_dark`
  - `minecraft:deep_frozen_ocean`
  - `minecraft:frozen_ocean`
  - `minecraft:frozen_peaks`
  - `minecraft:frozen_river`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:snowy_beach`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`

### spawns\_coral\_variant\_zombie\_nautilus

Zombie Nautiluses that spawn in these biomes will be their coral variant.

- [NBT List / JSON Array] #minecraft:spawns\_coral\_variant\_zombie\_nautilus *(1 values)*

  - `minecraft:warm_ocean`

### spawns\_gold\_rabbits

Rabbits in these biomes always use the gold variant, unless the biome also has the `spawns_white_rabbits` tag.

- [NBT List / JSON Array] #minecraft:spawns\_gold\_rabbits *(1 values)*

  - `minecraft:desert`

### spawns\_snow\_foxes

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:spawns\_snow\_foxes *(10 values)*

  - `minecraft:frozen_ocean`
  - `minecraft:frozen_peaks`
  - `minecraft:frozen_river`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:snowy_beach`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`

### spawns\_warm\_variant\_farm\_animals

Cows, pigs, and chickens that spawn in these biomes will be their warm variant. Sheep that spawn in these biomes will use the warm wool colors.

- [NBT List / JSON Array] #minecraft:spawns\_warm\_variant\_farm\_animals *(9 values)*

  - `#minecraft:is_badlands`
  - `#minecraft:is_jungle`
  - `#minecraft:is_nether`
  - `#minecraft:is_savanna`
  - `minecraft:deep_lukewarm_ocean`
  - `minecraft:desert`
  - `minecraft:lukewarm_ocean`
  - `minecraft:mangrove_swamp`
  - `minecraft:warm_ocean`

### spawns\_warm\_variant\_frogs

Frogs that spawn in these biomes will be their warm(white) variant.

- [NBT List / JSON Array] #minecraft:spawns\_warm\_variant\_frogs *(7 values)*

  - `#minecraft:is_badlands`
  - `#minecraft:is_jungle`
  - `#minecraft:is_nether`
  - `#minecraft:is_savanna`
  - `minecraft:desert`
  - `minecraft:mangrove_swamp`
  - `minecraft:warm_ocean`

### spawns\_white\_rabbits

Rabbits in these biomes have an 80% chance of being white and a 20% chance of being white splotched. Other variants will not spawn. Takes precedence over `spawns_gold_rabbits`.

- [NBT List / JSON Array] #minecraft:spawns\_white\_rabbits *(10 values)*

  - `minecraft:frozen_ocean`
  - `minecraft:frozen_peaks`
  - `minecraft:frozen_river`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:snowy_beach`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`

### stronghold\_biased\_to

When placing a stronghold, the position is moved up to 112 block on the x and z axis to a random position where the biome at y=0 is in this tag, if such a position exists.

- [NBT List / JSON Array] #minecraft:stronghold\_biased\_to *(38 values)*

  - `minecraft:badlands`
  - `minecraft:bamboo_jungle`
  - `minecraft:birch_forest`
  - `minecraft:cherry_grove`
  - `minecraft:dark_forest`
  - `minecraft:desert`
  - `minecraft:dripstone_caves`
  - `minecraft:eroded_badlands`
  - `minecraft:flower_forest`
  - `minecraft:forest`
  - `minecraft:frozen_peaks`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:jungle`
  - `minecraft:lush_caves`
  - `minecraft:meadow`
  - `minecraft:mushroom_fields`
  - `minecraft:old_growth_birch_forest`
  - `minecraft:old_growth_pine_taiga`
  - `minecraft:old_growth_spruce_taiga`
  - `minecraft:pale_garden`
  - `minecraft:plains`
  - `minecraft:savanna`
  - `minecraft:savanna_plateau`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`
  - `minecraft:sparse_jungle`
  - `minecraft:stony_peaks`
  - `minecraft:sulfur_caves`
  - `minecraft:sunflower_plains`
  - `minecraft:taiga`
  - `minecraft:windswept_forest`
  - `minecraft:windswept_gravelly_hills`
  - `minecraft:windswept_hills`
  - `minecraft:windswept_savanna`
  - `minecraft:wooded_badlands`

### water\_on\_map\_outlines

These biomes appear as water on map outlines.

- [NBT List / JSON Array] #minecraft:water\_on\_map\_outlines *(4 values)*

  - `#minecraft:is_ocean`
  - `#minecraft:is_river`
  - `minecraft:mangrove_swamp`
  - `minecraft:swamp`

### without\_wandering\_trader\_spawns

Biomes where wandering traders do not spawn.

- [NBT List / JSON Array] #minecraft:without\_wandering\_trader\_spawns *(1 values)*

  - `minecraft:the_void`

### without\_zombie\_sieges

Players in these biomes cannot trigger zombie sieges.

- [NBT List / JSON Array] #minecraft:without\_zombie\_sieges *(1 values)*

  - `minecraft:mushroom_fields`

### has\_structure/abandoned\_camp\_bamboo\_jungle

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_bamboo\_jungle​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:bamboo_jungle`

### has\_structure/abandoned\_camp\_birch\_forest

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_birch\_forest​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:birch_forest`

### has\_structure/abandoned\_camp\_cherry\_grove

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_cherry\_grove​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:cherry_grove`

### has\_structure/abandoned\_camp\_dappled\_forest

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_dappled\_forest​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:dappled_forest`

### has\_structure/abandoned\_camp\_flower\_forest

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_flower\_forest​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:flower_forest`

### has\_structure/abandoned\_camp\_forest

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_forest​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:forest`

### has\_structure/abandoned\_camp\_meadow

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_meadow​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:meadow`

### has\_structure/abandoned\_camp\_old\_growth\_birch\_forest

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_old\_growth\_birch\_forest​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:old_growth_birch_forest`

### has\_structure/abandoned\_camp\_old\_growth\_pine\_taiga

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_old\_growth\_pine\_taiga​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:old_growth_pine_taiga`

### has\_structure/abandoned\_camp\_old\_growth\_spruce\_taiga

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_old\_growth\_spruce\_taiga​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:old_growth_spruce_taiga`

### has\_structure/abandoned\_camp\_pale\_garden

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_pale\_garden​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:pale_garden`

### has\_structure/abandoned\_camp\_savanna

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_savanna​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:savanna`

### has\_structure/abandoned\_camp\_snowy\_taiga

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_snowy\_taiga​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:snowy_taiga`

### has\_structure/abandoned\_camp\_sparse\_jungle

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_sparse\_jungle​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:sparse_jungle`

### has\_structure/abandoned\_camp\_swamp

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_swamp​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:swamp`

### has\_structure/abandoned\_camp\_taiga

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_taiga​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:taiga`

### has\_structure/abandoned\_camp\_windswept\_forest

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_windswept\_forest​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:windswept_forest`

### has\_structure/abandoned\_camp\_wooded\_badlands

​[*more information needed*]

- [NBT List / JSON Array] #minecraft:has\_structure/abandoned\_camp\_wooded\_badlands​[*upcoming: JE 26.3*] *(1 values)*

  - `minecraft:wooded_badlands`

### has\_structure/ancient\_city

Ancient city can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ancient\_city *(1 values)*

  - `minecraft:deep_dark`

### has\_structure/bastion\_remnant

Bastion remnants can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/bastion\_remnant *(4 values)*

  - `minecraft:crimson_forest`
  - `minecraft:nether_wastes`
  - `minecraft:soul_sand_valley`
  - `minecraft:warped_forest`

### has\_structure/buried\_treasure

Buried treasure can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/buried\_treasure *(1 values)*

  - `#minecraft:is_beach`

### has\_structure/desert\_pyramid

Desert pyramid can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/desert\_pyramid *(1 values)*

  - `minecraft:desert`

### has\_structure/end\_city

End city can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/end\_city *(2 values)*

  - `minecraft:end_highlands`
  - `minecraft:end_midlands`

### has\_structure/igloo

Igloo can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/igloo *(3 values)*

  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`

### has\_structure/jungle\_temple

Jungle temple can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/jungle\_temple *(2 values)*

  - `minecraft:bamboo_jungle`
  - `minecraft:jungle`

### has\_structure/mineshaft

Mineshaft can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/mineshaft *(23 values)*

  - `#minecraft:is_beach`
  - `#minecraft:is_forest`
  - `#minecraft:is_hill`
  - `#minecraft:is_jungle`
  - `#minecraft:is_mountain`
  - `#minecraft:is_ocean`
  - `#minecraft:is_river`
  - `#minecraft:is_taiga`
  - `minecraft:desert`
  - `minecraft:dripstone_caves`
  - `minecraft:ice_spikes`
  - `minecraft:lush_caves`
  - `minecraft:mangrove_swamp`
  - `minecraft:mushroom_fields`
  - `minecraft:plains`
  - `minecraft:savanna`
  - `minecraft:savanna_plateau`
  - `minecraft:snowy_plains`
  - `minecraft:stony_shore`
  - `minecraft:sulfur_caves`
  - `minecraft:sunflower_plains`
  - `minecraft:swamp`
  - `minecraft:windswept_savanna`

### has\_structure/mineshaft\_mesa

Mesa mineshaft can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/mineshaft\_mesa *(1 values)*

  - `#minecraft:is_badlands`

### has\_structure/nether\_fortress

Nether fortress can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/nether\_fortress *(1 values)*

  - `#minecraft:is_nether`

### has\_structure/nether\_fossil

Nether fossil can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/nether\_fossil *(1 values)*

  - `minecraft:soul_sand_valley`

### has\_structure/ocean\_monument

Ocean monument can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ocean\_monument *(1 values)*

  - `#minecraft:is_deep_ocean`

### has\_structure/ocean\_ruin\_cold

Cold ocean ruin can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ocean\_ruin\_cold *(6 values)*

  - `minecraft:cold_ocean`
  - `minecraft:deep_cold_ocean`
  - `minecraft:deep_frozen_ocean`
  - `minecraft:deep_ocean`
  - `minecraft:frozen_ocean`
  - `minecraft:ocean`

### has\_structure/ocean\_ruin\_warm

Warm ocean ruin can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ocean\_ruin\_warm *(3 values)*

  - `minecraft:deep_lukewarm_ocean`
  - `minecraft:lukewarm_ocean`
  - `minecraft:warm_ocean`

### has\_structure/pillager\_outpost

Pillager outpost can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/pillager\_outpost *(7 values)*

  - `#minecraft:is_mountain`
  - `minecraft:desert`
  - `minecraft:grove`
  - `minecraft:plains`
  - `minecraft:savanna`
  - `minecraft:snowy_plains`
  - `minecraft:taiga`

### has\_structure/ruined\_portal\_desert

Desert ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_desert *(1 values)*

  - `minecraft:desert`

### has\_structure/ruined\_portal\_jungle

Jungle ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_jungle *(1 values)*

  - `#minecraft:is_jungle`

### has\_structure/ruined\_portal\_mountain

Mountain ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_mountain *(6 values)*

  - `#minecraft:is_badlands`
  - `#minecraft:is_hill`
  - `#minecraft:is_mountain`
  - `minecraft:savanna_plateau`
  - `minecraft:stony_shore`
  - `minecraft:windswept_savanna`

### has\_structure/ruined\_portal\_nether

Nether ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_nether *(1 values)*

  - `#minecraft:is_nether`

### has\_structure/ruined\_portal\_ocean

Ocean ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_ocean *(1 values)*

  - `#minecraft:is_ocean`

### has\_structure/ruined\_portal\_standard

Normal ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_standard *(13 values)*

  - `#minecraft:is_beach`
  - `#minecraft:is_forest`
  - `#minecraft:is_river`
  - `#minecraft:is_taiga`
  - `minecraft:dripstone_caves`
  - `minecraft:ice_spikes`
  - `minecraft:lush_caves`
  - `minecraft:mushroom_fields`
  - `minecraft:plains`
  - `minecraft:savanna`
  - `minecraft:snowy_plains`
  - `minecraft:sulfur_caves`
  - `minecraft:sunflower_plains`

### has\_structure/ruined\_portal\_swamp

Swamp ruined portal can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/ruined\_portal\_swamp *(2 values)*

  - `minecraft:mangrove_swamp`
  - `minecraft:swamp`

### has\_structure/shipwreck

Shipwreck can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/shipwreck *(1 values)*

  - `#minecraft:is_ocean`

### has\_structure/shipwreck\_beached

Beached shipwreck can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/shipwreck\_beached *(1 values)*

  - `#minecraft:is_beach`

### has\_structure/stronghold

Stronghold can generate in these biomes. This includes all overworld biomes to ensure that the stronghold is always placed, even when no optimal biome is found (see `#stronghold_biased_to`).

- [NBT List / JSON Array] #minecraft:has\_structure/stronghold *(1 values)*

  - `#minecraft:is_overworld`

### has\_structure/swamp\_hut

Swamp hut can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/swamp\_hut *(1 values)*

  - `minecraft:swamp`

### has\_structure/trail\_ruins

Trail ruins can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/trail\_ruins *(6 values)*

  - `minecraft:jungle`
  - `minecraft:old_growth_birch_forest`
  - `minecraft:old_growth_pine_taiga`
  - `minecraft:old_growth_spruce_taiga`
  - `minecraft:snowy_taiga`
  - `minecraft:taiga`

### has\_structure/trial\_chambers

Trial chambers can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/trial\_chambers *(55 values)*

  - `minecraft:badlands`
  - `minecraft:bamboo_jungle`
  - `minecraft:beach`
  - `minecraft:birch_forest`
  - `minecraft:cherry_grove`
  - `minecraft:cold_ocean`
  - `minecraft:dappled_forest`​[*upcoming: JE 26.3*]
  - `minecraft:dark_forest`
  - `minecraft:deep_cold_ocean`
  - `minecraft:deep_frozen_ocean`
  - `minecraft:deep_lukewarm_ocean`
  - `minecraft:deep_ocean`
  - `minecraft:desert`
  - `minecraft:dripstone_caves`
  - `minecraft:eroded_badlands`
  - `minecraft:flower_forest`
  - `minecraft:forest`
  - `minecraft:frozen_ocean`
  - `minecraft:frozen_peaks`
  - `minecraft:frozen_river`
  - `minecraft:grove`
  - `minecraft:ice_spikes`
  - `minecraft:jagged_peaks`
  - `minecraft:jungle`
  - `minecraft:lukewarm_ocean`
  - `minecraft:lush_caves`
  - `minecraft:mangrove_swamp`
  - `minecraft:meadow`
  - `minecraft:mushroom_fields`
  - `minecraft:ocean`
  - `minecraft:old_growth_birch_forest`
  - `minecraft:old_growth_pine_taiga`
  - `minecraft:old_growth_spruce_taiga`
  - `minecraft:pale_garden`
  - `minecraft:plains`
  - `minecraft:riverUnit`
  - `minecraft:savanna`
  - `minecraft:savanna_plateau`
  - `minecraft:snowy_beach`
  - `minecraft:snowy_plains`
  - `minecraft:snowy_slopes`
  - `minecraft:snowy_taiga`
  - `minecraft:sparse_jungle`
  - `minecraft:stony_peaks`
  - `minecraft:stony_shore`
  - `minecraft:sulfur_caves`
  - `minecraft:sunflower_plains`
  - `minecraft:swamp`
  - `minecraft:taiga`
  - `minecraft:warm_ocean`
  - `minecraft:windswept_forest`
  - `minecraft:windswept_gravelly_hills`
  - `minecraft:windswept_hills`
  - `minecraft:windswept_savanna`
  - `minecraft:wooded_badlands`

### has\_structure/village\_desert

Desert village can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/village\_desert *(1 values)*

  - `minecraft:desert`

### has\_structure/village\_plains

Plains village can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/village\_plains *(2 values)*

  - `minecraft:meadow`
  - `minecraft:plains`

### has\_structure/village\_savanna

Savanna village can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/village\_savanna *(1 values)*

  - `minecraft:savanna`

### has\_structure/village\_snowy

Snowy village can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/village\_snowy *(1 values)*

  - `minecraft:snowy_plains`

### has\_structure/village\_taiga

Taiga village can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/village\_taiga *(1 values)*

  - `minecraft:taiga`

### has\_structure/woodland\_mansion

Woodland mansion can generate in these biomes.

- [NBT List / JSON Array] #minecraft:has\_structure/woodland\_mansion *(2 values)*

  - `minecraft:dark_forest`
  - `minecraft:pale_garden`

## Removed tags

### has\_closer\_water\_fog

These biomes have a shorter view distance underwater.

Version added: 22w14a. Version removed: 25w42a.

- [NBT List / JSON Array] #minecraft:has\_closer\_water\_fog *(2 values)*

  - `minecraft:mangrove_swamp`
  - `minecraft:swamp`

### increased\_fire\_burnout

​[*more information needed*]

Version added: 23w03a. Version removed: 25w42a.

- [NBT List / JSON Array] #minecraft:increased\_fire\_burnout *(8 values)*

  - `minecraft:bamboo_jungle`
  - `minecraft:frozen_peaks`
  - `minecraft:jagged_peaks`
  - `minecraft:jungle`
  - `minecraft:mangrove_swamp`
  - `minecraft:mushroom_fields`
  - `minecraft:snowy_slopes`
  - `minecraft:swamp`

### plays\_underwater\_music

The player hears the special underwater music when underwater in these biomes.

Version added: 22w11a. Version removed: 25w42a.

- [NBT List / JSON Array] #minecraft:plays\_underwater\_music *(2 values)*

  - `#minecraft:is_ocean`
  - `#minecraft:is_river`

### snow\_golem\_melts

​[*more information needed*]

Version added: 23w03a. Version removed: 25w42a.

- [NBT List / JSON Array] #minecraft:snow\_golem\_melts *(12 values)*

  - `minecraft:badlands`
  - `minecraft:basalt_deltas`
  - `minecraft:crimson_forest`
  - `minecraft:desert`
  - `minecraft:eroded_badlands`
  - `minecraft:nether_wastes`
  - `minecraft:savanna`
  - `minecraft:savanna_plateau`
  - `minecraft:soul_sand_valley`
  - `minecraft:warped_forest`
  - `minecraft:windswept_savanna`
  - `minecraft:wooded_badlands`

### without\_patrol\_spawns

​[*more information needed*]

Version added: 22w11a. Version removed: 25w45a.

- [NBT List / JSON Array] #minecraft:without\_patrol\_spawns *(1 values)*

  - `minecraft:mushroom_fields`

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.18.2 | | | 22w07a | | | | Biome tags are now used to determine which biomes a structure can generate in. |
| Added 42 tags.  - added `#is_badlands`, `#is_beach`, `#is_deep_ocean`, `#is_forest`, `#is_hill`, `#is_jungle`, `#is_mountain`, `#is_nether`, `#is_ocean`, `#is_river`, `#is_taiga`, `#has_structure/bastion_remnant`, `#has_structure/buried_treasure`, `#has_structure/desert_pyramid`, `#has_structure/end_city`, `#has_structure/igloo`, `#has_structure/jungle_temple`, `#has_structure/mineshaft`, `#has_structure/mineshaft_mesa`, `#has_structure/nether_fortress`, `#has_structure/nether_fossil`, `#has_structure/ocean_monument`, `#has_structure/ocean_ruin_cold`, `#has_structure/ocean_ruin_warm`, `#has_structure/pillager_outpost`, `#has_structure/ruined_portal_desert`, `#has_structure/ruined_portal_jungle`, `#has_structure/ruined_portal_mountain`, `#has_structure/ruined_portal_nether`, `#has_structure/ruined_portal_ocean`, `#has_structure/ruined_portal_standard`, `#has_structure/ruined_portal_swamp`, `#has_structure/shipwreck`, `#has_structure/shipwreck_beached`, `#has_structure/stronghold`, `#has_structure/swamp_hut`, `#has_structure/village_desert`, `#has_structure/village_plains`, `#has_structure/village_savanna`, `#has_structure/village_snowy`, `#has_structure/village_taiga`, and `#has_structure/woodland_mansion` |
| Pre-release 2 | | | | Removed `the_void` from `#has_structure/stronghold` tag. |
| 1.19 | | | 22w11a | | | | Added 21 tags.  - added `#allows_surface_slime_spawns`, `#allows_tropical_fish_spawns_at_any_height`, `#has_closer_water_fog`, `#is_end`, `#is_overworld`, `#is_savanna`, `#more_frequent_drowned_spawns`, `#only_allows_snow_and_gold_rabbits`, `#plays_underwater_music`, `#polar_bears_spawn_on_alternate_blocks`, `#produces_corals_from_bonemeal`, `#reduce_water_ambient_spawns`, `#required_ocean_monument_surrounding`, `#spawns_cold_variant_frogs`, `#spawns_warm_variant_frogs`, `#stronghold_biased_to`, `#water_on_map_outlines`, `#without_patrol_spawns`, `#without_wandering_trader_spawns`, and `#without_zombie_sieges` |
| Added 1 and removed 35 values from `#has_structure/stronghold` tag.  - added `#is_overworld` - removed `badlands`, `bamboo_jungle`, `birch_forest`, `dark_forest`, `desert`, `dripstone_caves`, `eroded_badlands`, `flower_forest`, `forest`, `frozen_peaks`, `grove`, `ice_spikes`, `jagged_peaks`, `jungle`, `lush_caves`, `meadow`, `mushroom_fields`, `old_growth_birch_forest`, `old_growth_pine_taiga`, `old_growth_spruce_taiga`, `plains`, `savanna`, `savanna_plateau`, `snowy_plains`, `snowy_slopes`, `snowy_taiga`, `sparse_jungle`, `stony_peaks`, `sunflower_plains`, `taiga`, `windswept_forest`, `windswept_gravelly_hills`, `windswept_hills`, `windswept_savanna`, and `wooded_badlands` |
| 22w13a | | | | Added `#has_structure/ancient_city` tag. |
| Added `warm_ocean` to `#spawns_warm_variant_frogs` tag. |
| Added `deep_dark`, `deep_frozen_ocean`, `frozen_ocean`, `frozen_peaks`, `frozen_river`, `grove`, `jagged_peaks`, `snowy_beach`, `snowy_slopes`, and `snowy_taiga` and removed `#is_mountain` from `#spawns_cold_variant_frogs` tag. |
| 22w14a | | | | Added `mangrove_swamp` to `#spawns_warm_variant_frogs`, `#water_on_map_outlines`, `#has_closer_water_fog`, `#is_overworld`, `#allows_surface_slime_spawns`, `#has_structure/mineshaft`, and `#has_structure/ruined_portal_swamp` tag. |
| Pre-release 2 | | | | Added `#mineshaft_blocking` tag. |
| 1.19.4 | | | 23w03a | | | | Renamed tag `#only_allows_snow_and_gold_rabbits` to `#spawns_gold_rabbits`. |
| Added `#increased_fire_burnout`, `#snow_golem_melts`, `#spawns_gold_rabbits`, `#spawns_snow_foxes`, and `#spawns_white_rabbits` tags. |
| 1.19.4 Experiment Update 1.20 | | | 23w07a | | | | Added `cherry_grove` to `#is_mountain` tag. |
| 1.20 | | | 23w12a | | | | Added `cherry_grove` to `#is_overworld` tag. |
| 1.20 Experiment Update 1.21 | | | 23w12a | | | | Added `#has_structure/trail_ruins` tag. |
| 1.20.3 Experiment Update 1.21 | | | 23w45a | | | | Added `#has_structure/trial_chambers` tag. |
| 1.21 Experiment Update 1.21 | | | 24w21a | | | | Removed `deep_dark` from `#has_structure/trial_chambers` tag. |
| 1.21.2 Experiment Winter Drop | | | 24w40a | | | | Added `pale_garden` to `#is_forest`, and `#stronghold_biased_to` tag. |
| Pre-release 1 | | | | Added `pale_garden` to `#is_overworld` tag. |
| Pre-release 2 | | | | Added `pale_garden` to `#has_structure/trial_chambers` tag. |
| 1.21.5 | | | 25w02a | | | | Added `spawns_cold_variant_farm_animals`, and `spawns_warm_variant_farm_animals` tags. |
| Added `pale_garden` to `#has_structure/woodland_mansion` tag. |
| 25w05a | | | | Added `windswept_savanna` and removed `windswept_hills` from `#spawns_warm_variant_farm_animals` tag. |
| 25w06a | | | | Added 18 values to `#spawns_cold_variant_farm_animals` tag.  - added `cold_ocean`, `deep_cold_ocean`, `deep_dark`, `deep_frozen_ocean`, `frozen_ocean`, `frozen_peaks`, `frozen_river`, `grove`, `ice_spikes`, `jagged_peaks`, `snowy_beach`, `snowy_plains`, `snowy_slopes`, `stony_peaks`, `taiga`, `windswept_gravelly_hills`, `windswept_hills`, and `#is_end` |
| Added 9 and removed 9 values from `#spawns_warm_variant_farm_animals` tag.  - added `desert`, `mangrove_swamp`, `warm_ocean`, `lukewarm_ocean`, `deep_lukewarm_ocean`, `#is_jungle`, `#is_savanna`, `#is_nether`, and `#is_badlands` - removed `savanna`, `savanna_plateau`, `windswept_savanna`, `jungle`, `sparse_jungle`, `bamboo_jungle`, `eroded_badlands`, `wooded_badlands`, and `badlands` |
| 1.21.9 | | | 25w31a | | | | Added `cherry_grove` to `#stronghold_biased_to` tag. |
| 1.21.11 | | | 25w42a | | | | Removed `#has_closer_water_fog`, `#increased_fire_burnout`, `#plays_underwater_music`, and `snow_golem_melts` tags. |
| 25w45a | | | | Added `#spawns_coral_variant_zombie_nautilus` tag. |
| Removed `#without_patrol_spawns` tag. |
| 26.2 | | | snap1 | | | | Added `sulfur_caves` to `#is_overworld`, `#stronghold_biased_to`, `#has_structure/mineshaft`, `#has_structure/ruined_portal_standard`, and `#has_structure/trial_chambers` tags. |
| Upcoming *Java Edition* | | | | | | | |
| 26.3 | | | snap1 | | | | Added 18 tags.  - added `#has_structure/abandoned_camp_bamboo_jungle`, `#has_structure/abandoned_camp_birch_forest`, `#has_structure/abandoned_camp_cherry_grove`, `#has_structure/abandoned_camp_dappled_forest`, `#has_structure/abandoned_camp_flower_forest`, `#has_structure/abandoned_camp_forest`, `#has_structure/abandoned_camp_meadow`, `#has_structure/abandoned_camp_old_growth_birch_forest`, `#has_structure/abandoned_camp_old_growth_pine_taiga`, `#has_structure/abandoned_camp_old_growth_spruce_taiga`, `#has_structure/abandoned_camp_pale_garden`, `#has_structure/abandoned_camp_savanna`, `#has_structure/abandoned_camp_snowy_taiga`, `#has_structure/abandoned_camp_sparse_jungle`, `#has_structure/abandoned_camp_swamp`, `#has_structure/abandoned_camp_taiga`, `#has_structure/abandoned_camp_windswept_forest`, and `#has_structure/abandoned_camp_wooded_badlands` |
| Added `dappled_forest` to `#is_forest`, `#is_overworld`, `#spawns_cold_variant_farm_animals`, and `#has_structure/trial_chambers` tags. |

## Navigation
