# Structure set

> **Source:** <https://minecraft.wiki/w/Structure_set>  
> **Revision:** 3598061 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

A **structure set** is used to determine the position of structures in the world during world generation. They are configured using JSON files stored within a data pack in the path `data/<namespace>/worldgen/structure_set` or an add-on in the path `<root BP>/worldgen/structure_sets`. Structure sets are not referenced in a dimension or biome. Instead, the existence of the resource is enough to make the structures generate. The valid biomes of a structure are determined by the structure itself (see Structure/JSON format).

The structure sets uses the [NBT Compound / JSON Object] placement to determine the placement of structures. For any position a random structure is selected from the [NBT List / JSON Array] structures list. If the selected structure can't be placed because its not in a valid biome, a different structure is selected.

## JSON format

*Java Edition*:

- [NBT Compound / JSON Object]: Root object.
  - [NBT List / JSON Array] structures: (Required, but can be empty) The structures that may be placed. One configured structure feature shouldn't be included by two structure sets.
    - [NBT Compound / JSON Object]: A structure to be placed.
      - [String][NBT Compound / JSON Object] structure: One structure (an [String] ID) — The structure to be placed.
      - [Int] weight: Determines the chance of it being chosen over others. Must be a positive integer.
  - [NBT Compound / JSON Object] placement: How the structures should be placed.
    - [Int] salt: A number that assists in randomization; see [salt (cryptography)](https://en.wikipedia.org/wiki/salt_(cryptography)). Must be a non-negative integer.
    - [Float] frequency: (Optional, default to 1.0) Probability to try to generate if other conditions below are met. Values between 0.0 to 1.0 (inclusive). Setting it to a number does not mean one structure is generated this often, only that the game attempts to generate one; biomes or terrain could lead to the structure not being generated.
    - [String] frequency\_reduction\_method: (Optional, defaults to `default`) Provides a random number generator algorithm for frequency. One of `default` (the random number depends on the seed, position and [Int] salt), `legacy_type_1` (the random number depends only on the seed and position, and randomness only occurs when the locations differ greatly), `legacy_type_2` (same as `default`, but with fixed salt: 10387320) and `legacy_type_3` (the random number depends only on seed and position).
    - [NBT Compound / JSON Object] exclusion\_zone: Specifies that it cannot be placed near certain structures.
      - [Int] chunk\_count: Value between 1 and 16 (inclusive).
      - [String] other\_set: A structure set ID.
    - [NBT List / JSON Array] locate\_offset: (optional, defaults to [0,0,0]) The chunk coordinate offset given when using `/locate structure`.
      - [Int]: X. Value between -16 and 16 (inclusive).
      - [Int]: Y. Value between -16 and 16 (inclusive).
      - [Int]: Z. Value between -16 and 16 (inclusive).
    - [String] type: One of `minecraft:concentric_rings` or `minecraft:random_spread`.
    - Additional fields depending on value of [String] type, see Placement types.

*Bedrock Edition*:

- [NBT Compound / JSON Object] Structure Set
  - [String] format\_version: The version of the game to run this file as. Can be a version above 1.21.20.
  - [NBT Compound / JSON Object] minecraft:structure\_set:
    - [NBT Compound / JSON Object] description
      - [String] identifier: The identifier used for the structure set.
    - [NBT Compound / JSON Object] placement: How the structures should be placed.
      - [String] type: The type of spread. Can be `minecraft:random_spread`.
      - [Float] salt: A random 8 number sequence used for randomizing placement similarly to a world seed.
      - [Float] spacing: Size in chunks of the grid space used to place the structure
      - [Float] separation: Padding between placements of set placements.
      - [String] spread\_type: Algorithm used when placing structures. Can be `linear` or `triangular`.
    - [NBT List / JSON Array] structures: Weighted list of structures that can be placed.
      - [NBT Compound / JSON Object]: A structure to be placed.
        - [String] structure: A structure identifier.
        - [Float] weight: Determines the chance of it being chosen over others. Must be a positive integer.

## Placement types

The placement type determines how the structures are spread in a world. There are two placement types.

### random\_spread

Structures are spread evenly throughout the entire world. In vanilla, this placement type is used for most structures (like bastion remnants or swamp huts‌[*Java Edition only*]). Starting from chunk 0 0 and moving outwards [Int] spacing number of chunks, a grid of initial positions is created. After that the offset is applied to each position by X and Z separately based on the additional fields.

- additional fields:
  - [String] spread\_type: (optional, defaults to `linear`) One of `linear` or `triangular`. `linear` sets offset to a random value between 0 and `spacing - separation - 1` (inclusive). `triangular` takes the sum of 2 random numbers between 0 and `spacing - separation - 1` (inclusive) then divides that sum by 2, rounding down to nearest integer. This value results in less random offset, where values have a much higher chance to be closer to the middle of the distribution, than to the sides.
  - [Int] spacing: Average distance between two neighboring generation attempts. Value between 0 and 4096 (inclusive).
  - [Int] separation: Minimum distance (in chunks) between two neighboring attempts. Value between 0 and 4096 (inclusive). Has to be strictly smaller than [Int] spacing. The maximum distance of two neighboring generation attempts is `2*spacing - separation`.

### concentric\_rings

This feature is exclusive to *Java Edition*.

A fixed number of structures are placed in concentric rings around the origin of the world. In vanilla, this placement is only used for strongholds.

- additional fields:
  - [Int] distance: The thickness of a ring plus the thickness of a gap between two rings. Ring and gap are equal in thickness. Value between 0 and 1023 (inclusive). Unit is **6 chunks**
  - [Int] count: The total number of generation attempts in this dimension. Value between 1 and 4095 (inclusive).
  - [String][NBT List / JSON Array] preferred\_biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes in which the structure is likely to be generated. After choosing the initial position of generation, a search in a square with a radius of 112 blocks begins. It proceeds from -X to +X, from -Z to +Z in 4×4 squares. When a position inside a biome from [String][NBT List / JSON Array] preferred\_biomes is found, it is considered the new position. If multiple positions are found, one is chosen randomly. If no positions are found, the initial position is used.
  - [Int] spread: How many attempts are on the closest ring to spawn. Value between 0 and 1023 (inclusive). The number of attempts on the Nth ring is: `spread * (N^2 + 3 * N + 2) / 6`, until the number of attempts reaches the total [Int] count.

## Default structure sets

| Structure set | Structures | Separation | Spacing | Salt | Notes |
| --- | --- | --- | --- | --- | --- |
| `ancient_cities` | Ancient City | 8 | 24 | 20083232 |  |
| `buried_treasures` | Buried Treasure | 0 | 1 | 0 | Probability of 1%, locate\_offset of x:9, y:0, z:9 |
| `desert_pyramids` | Desert Pyramid | 8 | 32 | 14357617 |  |
| `end_cities` | End City | 11 | 20 | 10387313 |  |
| `igloos` | Igloo | 8 | 32 | 14357618 |  |
| `jungle_temples` | Jungle Pyramid | 8 | 32 | 14357619 |  |
| `mineshafts` | Mineshaft Badlands Mineshaft | 0 | 1 | 0 | Probability of 0.4% |
| `nether_complexes` | 40% Nether Fortress 60% Bastion Remnant | 4 | 27 | 30084232 |  |
| `nether_fossils` | Nether Fossil | 1 | 2 | 14357921 |  |
| `ocean_monuments` | Ocean Monument | 5 | 32 | 10387313 | Triangular spread type |
| `ocean_ruins` | Ocean Ruin | 8 | 20 | 14357621 |  |
| `pillager_outposts` | Pillager Outpost | 8 | 32 | 165745296 | Probability of 20%, exclusion zone of 10 chunks from any village |
| `ruined_portals` | Ruined Portal | 15 | 40 | 34222645 |  |
| `shipwrecks` | Shipwreck | 4 | 24 | 165745295 |  |
| `strongholds` | Stronghold |  |  | 0 | Concentric rings distance=32 count=128 spread=3 |
| `swamp_huts` | Swamp Hut | 8 | 32 | 14357620 |  |
| `trail_ruins` | Trail Ruins | 8 | 34 | 83469867 |  |
| `trial_chambers` | Trial Chambers | 12 | 34 | 94251327 |  |
| `villages` | Plains Village Desert Village Savanna Village Snowy Village Taiga Village | 8 | 34 | 10387312 |  |
| `woodland_mansions` | Woodland Mansion | 20 | 80 | 10387319 | Triangular spread type |

## External links

- [Structure set Generator on misode.github.io](https://misode.github.io/worldgen/structure-set/)

## Navigation
