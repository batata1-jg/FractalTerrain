# Chunk format

> **Source:** <https://minecraft.wiki/w/Chunk_format>  
> **Revision:** 3620290 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_18 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.20.2** — Game no longer uses numeric values when storing mob effects to world. For example, `4` becomes `minecraft:mining_fatigue`.
- **1.20.2** — Changed following fields in mob effect instances: - `Id` (integer) -> `id` (string, resource identifier) - `Ambient` -> `ambient` - `Amplifier` -> `amplifier` - `Duration` -> `duration` - `ShowParticles` -> `show_particles` - `ShowIcon` -> `show_icon` - `HiddenEffect` -> `hidden_effect` - `FactorCalculationData` -> `factor_calculation_data`
- **1.20.2** — In NBT format for block entity type `beacon`: - `Primary` (integer) -> `primary_effect` (string, resource identifier) - `Secondary` (integer) -> `secondary_effect` (string, resource identifier)
- **1.21** — Renamed `Attributes` field in mob data to `attributes`
- **1.21** — Changed following fields in mob attributes: - `Name` -> `id` - `Base` -> `base` - `Modifiers` -> `modifiers`
- **1.21** — Changed following fields in mob attribute modifiers: - `UUID` (int array) and `Name` (arbitrary string) -> `id` (string, resource identifier) - `Amount` -> `amount` - `Operation` (integer) -> `operation` (string), with `"add_value"` replacing 0, `"add_multiplied_base"` replacing 1, and `"add_multiplied_total"` replacing 2
- **1.21.2** — Renamed `Lock` field in containers to `lock` and now it has become a compound that represents an item predicate.
- **1.21.5** — The `CustomName` field will no longer be preserved when removed.
- **1.21.5** — The `LootTable` field will no longer be preserved when removed.
- **1.21.5** — `end_gateway`: The `exit_portal` field will no longer be preserved when removed.
- **1.21.5** — `furnace`, `smoker`, `blast_furnace`: The `RecipesUsed` field will no longer be preserved when removed.
- **1.21.5** — `skull`: The `note_block_sound` field will no longer be preserved when removed.
- **1.21.5** — `campfire`: The `CookingTimes` and `CookingTotalTimes` fields will no longer be preserved when removed.
- **1.21.5** — `chiseled_bookshelf`: The `last_interacted_slot` field now defaults to `-1` if not specified.
- **1.21.5** — `hopper`: The `TransferCooldown` field now default to `-1` if not specified.
- **1.21.5** — `jigsaw`: The `name`, `target`, and `pool` fields now default to `minecraft:empty` if not specified, the `final_state` field now defaults to `minecraft:air` if not specified.
- **1.21.5** — `sculk_shrieker`: The `warning_level` field now defaults to `0` if not specified.
- **1.21.5** — `structure_block`: The `ignoreEntities` and showboundingbox fields now default to `true` if not specified, the `posY` field now defaults to 1 if not specified.

---
For chunk format for McRegion, see Chunk format/McRegion. For chunk format for old Anvil, see Chunk format/Anvil/Before 1.13. For the storage format of entities, see Entity format.

This article needs to be updated.

Please update this article to reflect recent updates or newly available information. The talk page may contain suggestions.
**Reason:** missing information about 1.18 formats, this page is W.I.P. and sections that are updated for 1.18 mention it. Bedrock Edition information missing.

**Chunks** store the terrain and entities within a 16×384×16 area in the Overworld, and 16×256×16 in the Nether and the End by default. They also store precomputed lighting, heightmap data for Minecraft's performance, and other meta information.

## NBT structure

See also: Region file format and Anvil file format

Chunks are stored as tags in regional Minecraft Anvil files, which are named in the form `r.x.z.mca`. They are stored in NBT format, with the following structure (updated for 1.18):

- [NBT Compound / JSON Object] The root tag.
  - [Int] DataVersion: Version of the chunk NBT structure.
  - [Int] xPos: X position of the chunk (in absolute chunks from world `x`, `z` origin, **not** relative to the region).
  - [Int] zPos: Z position of the chunk (in absolute chunks from world `x`, `z` origin, **not** relative to the region).
  - [Int] yPos: Lowest Y section position in the chunk (e.g. `-4` in 1.18).
  - [String] Status: Defines the world generation status of this chunk. It is always one of the following: `minecraft:empty`, `minecraft:structure_starts`, `minecraft:structure_references`, `minecraft:biomes`, `minecraft:noise`, `minecraft:surface`, `minecraft:carvers`/`minecraft:liquid_carvers`, `minecraft:features`, `minecraft:light`/`minecraft:initialize_light`, `minecraft:spawn`, or `minecraft:full`. All status except `minecraft:full` are used for chunks called *proto-chunks*, in other words, for chunks with incomplete generation.
  - [Long] LastUpdate: Tick when the chunk was last saved.
  - [NBT List / JSON Array] sections: List of [NBT Compound / JSON Object] Compounds, each tag is a section (also known as sub-chunk). All sections in the world's height are present in this list, even those who are empty (filled with air).
    - [NBT Compound / JSON Object] An individual section.
      - [Byte] Y: The Y position of this section.
      - [NBT Compound / JSON Object] block\_states
        - [NBT List / JSON Array] palette: Set of different block states used in this particular section. The vanilla implementation never includes any unused entries upon writing, so this array will never contain more than 4096 entries, and the indices in the data array will be at most 12 bits long. However the vanilla implementation is also able to handle larger palettes when reading.
          - [NBT Compound / JSON Object] A block
            - [String] Name: Block resource location
            - [NBT Compound / JSON Object] Properties: List of block state properties, with `name` being the name of the block state property
              - [String] *Name*: The block state's name and its value.
        - [Long Array] data: A packed array of 4096 indices pointing to the palette, stored in an array of 64-bit integers ([Long] Longs).
          - If only one block state is present in the palette, this field is not required and the block fills the whole section.
          - All indices are the same length. This length is set to the minimum amount of bits required to represent the largest index in the palette, and then set to a minimum size of 4 bits. The indices are not packed across multiple elements of the array, meaning that if there is no more space in a given 64-bit integer for the whole next index, it starts instead at the first (lowest) bit of the next 64-bit integer. Different sections of a chunk can have different lengths for the indices.
      - [NBT Compound / JSON Object] biomes
        - [NBT List / JSON Array] palette: Set of different biomes used in this particular section. The vanilla implementation never includes any unused entries upon writing, so this array will never contain more than 64 entries, and the indices in the data array will be at most 6 bits long. However the vanilla implementation is also able to handle larger palettes when reading.
          - [String] Name: Biome resource location
        - [Long Array] data: A packed array of 64 indices pointing to the palette, stored in an array of 64-bit integers ([Long] Longs).
          - Biomes in a world are stored in cells of 4×4×4 blocks, and only store 64 individual entries. Biomes at the block level are smoothed based on the cells.​[*more information needed*]
          - If only one biome is present in the palette, this field is not required and the biome fills the whole section.
          - All indices are the same length: the minimum amount of bits required to represent the largest index in the palette. These indices do not have a minimum size. As with block states, the indices are not packed across multiple elements of the array. Different sections of a chunk can have different lengths for the indices.
      - [Byte Array] BlockLight: 2048 [Byte] Bytes stores the amount of block-emitted light in each block. Makes load times faster compared to recomputing at load time. Omitted if no light reaches this section of the chunk. Light level is stored as 4 bits per block, 2 blocks sharing a byte: starting at 0, even blocks take the first nibble, and odd blocks the second one.
      - [Byte Array] SkyLight: 2048 [Byte] Bytes stores the maximum sky light that reaches each block, regardless of current time. If the sky light data for a section is omitted you should look at the light data of the section directly above it. Take the 16x16 layer at the bottom of that section and repeat that light data 16 times to recompute the data for the omitted section. If there is no section above the current one, you are at the top section of the chunk. The light data for this top section should be set as completely bright (`0xF` for each block). The format of how these levels is stored is the same as for BlockLight.
  - [NBT List / JSON Array] block\_entities: Each [NBT Compound / JSON Object] Compound in this list defines a block entity in the chunk.
    - See Block entity format.
  - [NBT Compound / JSON Object] CarvingMasks: Only for *proto-chunks* **(not confirmed for 1.18 format)**.
    - [Byte Array] AIR: A series of bits indicating whether a cave has been dug at a specific position, stored in a byte array.
    - [Byte Array] LIQUID: A series of bits indicating whether an underwater cave has been dug at a specific position, stored in a byte array.
  - [NBT Compound / JSON Object] Heightmaps: Several different heightmaps corresponding to 256 values compacted at 9 bits per value (lowest being 0, highest being 384, both values inclusive). The 9-bit values are stored in an array of 37 [Long] Longs, each containing 7 values ([Long] A Long = 64 bits, 7×9 = 63; the last bit is unused). In versions prior to 1.16 the heightmaps were stored in 36 [Long] Long values, where the bits were arranged in an uninterrupted "stream" across all values, resulting in all 2304 bits being used. The 9-bit values are unsigned, and indicate the amount of blocks above the bottom of the world. This means that converting a world to 1.18 adds 64 to every value.
    - [Long Array] MOTION\_BLOCKING
    - [Long Array] MOTION\_BLOCKING\_NO\_LEAVES
    - [Long Array] OCEAN\_FLOOR
    - [Long Array] OCEAN\_FLOOR\_WG
    - [Long Array] WORLD\_SURFACE
    - [Long Array] WORLD\_SURFACE\_WG
  - [NBT List / JSON Array] Lights: A List of 16 lists that store positions of light sources per chunk section as shorts, only for *proto-chunks* **(not confirmed for 1.18 format)**.
  - [NBT List / JSON Array] entities: A list of entities in the *proto-chunks*, used when generating. This list is not present for fully generated chunks and entities are moved to a separated region files once the chunk is generated; see Entity format for more details. Before 20w45a, it contained all the entities in the chunk. In 21w43a this was renamed from "Entities".
    - [NBT Compound / JSON Object]: An entity.
  - [NBT List / JSON Array] fluid\_ticks: A list of [NBT Compound / JSON Object] Compounds
    - Each [NBT Compound / JSON Object] Compound in this list is an "active" liquid in this chunk waiting to be updated. See Tile Tick Format below.
  - [NBT List / JSON Array] block\_ticks: A list of [NBT Compound / JSON Object] Compounds
    - Each [NBT Compound / JSON Object] Compound in this list is an "active" block in this chunk waiting to be updated. These are used to save the state of redstone machines or falling sand, and other activity. See Tile Tick Format below.
  - [Long] InhabitedTime: The cumulative number of ticks players have been in this chunk. Note that this value increases faster when more players are in the chunk. Used for Regional Difficulty.
  - [NBT Compound / JSON Object] blending\_data: This stores the heights to blend terrain to.
    - [NBT List / JSON Array] heights List of 16 heights measured in doubles that correspond to the y values at 16 locations on the edge of the chunk. (Specifically highest sand, red sand, stone, grass block, podzol, mycelium, coarse dirt, dirt, terracotta, or snow block) ​[*more information needed*]
      - [Double] Section-relative 12 ~ 00's y value.
      - [Double] Section-relative 08 ~ 00's y value.
      - [Double] Section-relative 04 ~ 00's y value.
      - [Double] Section-relative 00 ~ 00's y value.
      - [Double] Section-relative 00 ~ 04's y value.
      - [Double] Section-relative 00 ~ 08's y value.
      - [Double] Section-relative 00 ~ 12's y value.
      - [Double] Section-relative 00 ~ 15's y value.
      - [Double] Section-relative 04 ~ 15's y value.
      - [Double] Section-relative 08 ~ 15's y value.
      - [Double] Section-relative 12 ~ 15's y value.
      - [Double] Section-relative 15 ~ 15's y value.
      - [Double] Section-relative 15 ~ 12's y value.
      - [Double] Section-relative 15 ~ 08's y value.
      - [Double] Section-relative 15 ~ 04's y value.
      - [Double] Section-relative 15 ~ 00's y value.
    - [Int] min\_section: This appears to be the lowest chunk section the chunk has. ​[*more information needed*]
    - [Int] max\_section: This appears to be the highest chunk section the chunk has. ​[*more information needed*]
  - [NBT List / JSON Array] PostProcessing: A List of 24 [NBT List / JSON Array] Lists that store the positions of blocks that need to receive an update when a *proto-chunk* turns into a full chunk, packed in [Short] Shorts. Each list corresponds to specific section in the height of the chunk.
    - See ToBeTicked format below for a description of the coordinate packing format. A common use case for this to is make liquids flow after generating a source block, such as springs in caves.
  - [NBT Compound / JSON Object] structures: Structure data in this chunk.
    - [NBT Compound / JSON Object] references: Coordinates of chunks that contain *starts*.
      - [Long Array] *Structure Name*: Each 64-bit number of this array represents a chunk coordinate (i.e. block coordinate / 16), with its X packed into the low (least significant) 32 bits and Z packed into the high (most significant) 32 bits.
    - [NBT Compound / JSON Object] starts: Structures that are yet to be generated, stored by general type. Some parts of the structures may have already been generated. Completely generated structures are *removed* by setting their *id* to "`INVALID`" and removing all other tags.
      - [NBT Compound / JSON Object] *Structure Name*: Only the structures that can spawn in this dimension are stored, for example, EndCity is stored only in the end chunks.
        - [NBT List / JSON Array] Children: List of structure pieces making up this structure, that were not generated yet. Absent if id is `INVALID`.
          - [NBT Compound / JSON Object] Structure piece data. ​[*more information needed*]
            - [Int Array] BB: Bounding box of the structure piece. (Does not include the part of a village roof that can overhang the road.) Value is 6 ints: the minimum X, Y, and Z coordinates followed by the maximum X, Y, and Z coordinates.
            - [String] BiomeType: The ocean temperature this ocean ruins is in. Valid values are WARM and COLD.
            - [Byte] C: (Village "ViSmH") Hut roof type.[*verify*]
            - [NBT Compound / JSON Object] CA: (Village "ViF" and "ViDF") Crop in the farm plot.[*verify*]
              - [String] Name: Block resource location
              - [NBT Compound / JSON Object] Properties: List of block state properties, with [name] being the name of the block state property
                - [String] *Name*: The block state name and its value.
            - [NBT Compound / JSON Object] CB: (Village "ViF" and "ViDF")
              - separate object with the same format as [NBT Compound / JSON Object] CA
            - [NBT Compound / JSON Object] CC: (Village "ViDF")
              - separate object with the same format as [NBT Compound / JSON Object] CA
            - [NBT Compound / JSON Object] CD: (Village "ViDF")
              - separate object with the same format as [NBT Compound / JSON Object] CA
            - [Byte] Chest: 1 or 0 (`true`/`false`) - (Fortress "NeSCLT" and "NeSCRT") Whether this fortress piece should contain a chest but hasn't had one generated yet. (Stronghold "SHCC") Whether this chest in this stronghold piece was placed. (Village "ViS") Whether the blacksmith chest has been generated.
            - [Int] D: (Mineshaft "MSCrossing") Indicates the "incoming" direction for the crossing.
            - [Int] Depth: (Temples and huts) Depth of the structure (X/Z).
            - [NBT List / JSON Array] Entrances: (Mineshaft "MSRoom") List of exits from the room.
              - [Int Array]: Bounding box of the exit.
            - [String] EntryDoor: (Stronghold) The type of door at the entry to this piece.
            - [Int] GD: Appears to be some sort of measure of how far this piece is from the start.
            - [Int] ground\_level\_delta:
            - [Byte] hasPlacedChest0: 1 or 0 (`true`/`false`) - (Desert temple) Whether 1st chest was placed.
            - [Byte] hasPlacedChest1: 1 or 0 (`true`/`false`) - (Desert temple) Whether 2nd chest was placed.
            - [Byte] hasPlacedChest2: 1 or 0 (`true`/`false`) - (Desert temple) Whether 3rd chest was placed.
            - [Byte] hasPlacedChest3: 1 or 0 (`true`/`false`) - (Desert temple) Whether 4th chest was placed.
            - [Int] Height: (Temples and huts) Height of the structure (Y).
            - [Int] HPos: (Temples, huts and villages) Y level the structure was moved to in order to place it on the surface, or -1 if it hasn't been moved yet.[*verify*]
            - [Byte] hps: 1 or 0 (`true`/`false`) - (Mineshaft "MSCorridor") Whether the corridor has a cave spider monster spawner.
            - [Byte] hr: 1 or 0 (`true`/`false`) - (Mineshaft "MSCorridor") Whether the corridor has rails.
            - [String] id: Identifier for the structure piece. Typically a heavily abbreviated code rather than something human-readable. ​[*more information needed*]
            - [Float] integrity: The integrity of this structure (only used by ocean ruins).
            - [Byte] isBeached:
            - [Byte] isLarge: 1 or 0 (`true`/`false`) - If this ocean ruin is big.
            - [NBT List / JSON Array] junctions: (Village) List of junction points. ​[*more information needed*]
              - [NBT Compound / JSON Object]: The junctions coordinates:
                - [Int] source\_x: X.
                - [Int] source\_ground\_y: Y.
                - [Int] source\_z: Z.
                - [Int] delta\_y: Change Y. ​[*more information needed*]
                - [String] dest\_proj: One of `terrain_matching`, or `rigid`.
            - [Byte] Left: 1 or 0 (`true`/`false`) - (Stronghold "SHS") Whether the corridor has an opening on the left.
            - [Byte] leftHigh: 1 or 0 (`true`/`false`) - (Stronghold "SH5C") Whether the 5-way crossing has an exit on the upper level on the side with the upward staircase.
            - [Byte] leftLow: 1 or 0 (`true`/`false`) - (Stronghold "SH5C") Whether the 5-way crossing has an exit on the lower level on the side with the upward staircase.
            - [Int] Length: (Village "ViSR") Length of the road piece.[*verify*]
            - [String] liquid\_settings:
            - [String] Mirror:
            - [Byte] Mob: 1 or 0 (`true`/`false`) - (Fortress "NeMT") Whether this fortress piece should contain a blaze monster spawner but hasn't had one generated yet. (Stronghold "SHPR") Whether the silverfish monster spawner has been placed in this piece.
            - [Int] MST:
            - [Int] Num: (Mineshaft "MSCorridor") Corridor length.
            - [Int] O: Likely orientation of the structure piece.
            - [Byte] placedHiddenChest: 1 or 0 (`true`/`false`) - (Jungle temple) Whether the hidden chest was placed.
            - [Byte] placedMainChest: 1 or 0 (`true`/`false`) - (Jungle temple) Whether the main chest was placed.
            - [Byte] placedTrap1: 1 or 0 (`true`/`false`) - (Jungle temple) Whether the hallway arrow trap dispenser was placed.
            - [Byte] placedTrap2: 1 or 0 (`true`/`false`) - (Jungle temple) Whether the chest arrow trap dispenser was placed.
            - [NBT Compound / JSON Object] pool\_element:
              - [String] element\_type: The namespaced type of this element.
              - [String] location: The resource location for this element.
              - [NBT Compound / JSON Object] processors:
                - [NBT List / JSON Array] processors:
              - [String] processors: Appears to be a namespaced location.
              - [String] projection:
            - [Int] PosX: The X coordinate origin of this village part.
            - [Int] PosY: The Y coordinate origin of this village part.
            - [Int] PosZ: The Z coordinate origin of this village part.
            - [NBT Compound / JSON Object] properties:
              - [Int] overgrown:
              - [Float] moistness:
              - [Byte] replace\_with\_blackstone:
              - [Byte] vines:
              - [Byte] cold:
              - [Byte] air\_pocket:
            - [Byte] Right: 1 or 0 (`true`/`false`) - (Stronghold "SHS") Whether the corridor has an opening on the right.
            - [Byte] rightHigh: 1 or 0 (`true`/`false`) - (Stronghold "SH5C") Whether the 5-way crossing has an exit on the upper level on the side with the downward staircase.
            - [Byte] rightLow: 1 or 0 (`true`/`false`) - (Stronghold "SH5C") Whether the 5-way crossing has an exit on the lower level on the side with the downward staircase.
            - [String] Rot: Rotation of ocean ruins and shipwrecks. Valid values are COUNTERCLOCKWISE\_90, NONE, CLOCKWISE\_90, and CLOCKWISE\_180.
            - [Byte] sc: 1 or 0 (`true`/`false`) - (Mineshaft "MSCorridor") Whether the corridor has cobwebs.
            - [Int] Seed: (Fortress "NeBEF") Random seed for the broken-bridge fortress piece.
            - [Byte] Source: 1 or 0 (`true`/`false`) - (Stronghold "SHSD") Whether the spiral staircase is the source of the Stronghold or was randomly generated.
            - [Int] Steps: (Stronghold "SHFC") Length of the corridor
            - [Int] T: (Village "ViSmH") Table: 0 is no table, 1 and 2 place it on either side of the hut.[*verify*]
            - [Byte] Tall: 1 or 0 (`true`/`false`) - (Stronghold "SHLi") Whether the library has an upper level.
            - [String] Template: The template of the ocean ruin or shipwreck that was used.
            - [Byte] Terrace: 1 or 0 (`true`/`false`) - (Village "ViSH") Whether the house has a ladder to the roof and fencing.[*verify*]
            - [Byte] tf: 1 or 0 (`true`/`false`) - (Mineshaft "MSCrossing") Whether the crossing is two floors tall.
            - [Int] TPX: The X coordinate origin of this ocean ruin or shipwreck.
            - [Int] TPY: The Y coordinate origin of this ocean ruin or shipwreck.
            - [Int] TPZ: The Z coordinate origin of this ocean ruin or shipwreck.
            - [Byte] Type: (Village) Village type: 0=plains, 1=desert, 2=savanna, 3=taiga.[*verify*]
            - [Int] Type: (Stronghold "SHRC") Indicates whether the room contains a pillar with torches, a fountain, an upper level with a chest, or is just empty room.
            - [Int] VCount: (Village) Count of villagers spawned along with this piece.[*verify*]
            - [String] VerticalPlacement:
            - [Int] Width: (Temples and huts) Width of the structure (X/Z).
            - [Byte] Witch: 1 or 0 (`true`/`false`) - (Witch hut) Whether the initial witch has been spawned for the hut.
            - [Byte] Zombie: 1 or 0 (`true`/`false`) - (Village) Whether this village generated as a zombie village.[*verify*]
        - [Int] ChunkX: Chunk X coordinate of the start of the structure. Absent if id is `INVALID`.
        - [Int] ChunkZ: Chunk Z coordinate of the start of the structure. Absent if id is `INVALID`.
        - [String] id: If there's no structure of this kind in this chunk, this value is "`INVALID`", else it's the structure name.
        - [NBT List / JSON Array] Processed: (Monument only) List of chunks that have had their piece of the structure created. Absent if id is `INVALID`.
          - [NBT Compound / JSON Object]: A chunk.
            - [Int] X: The chunk's X position (chunk coordinates, not block coordinates).
            - [Int] Z: The chunk's Z position.
        - [Int] references: ​[*more information needed*]

## Block format

In the Anvil format, block positions are ordered YZX for compression purposes.

The coordinate system is as follows:

- **X** increases East, decreases West
- **Y** increases upward, decreases downward
- **Z** increases South, decreases North

This means indices are ordered like in a book, with its top to the North, read from beneath and with words written backward: each letter is a different X-index, each line a Z-index, and each page a Y-index. In case of a flat 2D array, the Y-index is omitted, and the array reads like a single page.

Each section is a 16×16×16-block area, with up to 16 sections in a chunk : from 0 at the bottom, to 15 on top. Empty sections are not saved. Each section has a "Y" byte for its Y-index (0 to 15), a "Palette" list linking IDs to block states, and a "BlockStates" long array storing the IDs per block location, compressed by fitting multiple IDs inside each entry (see NBT\_structure above for details on the compression). There might be an additional section at the top and or bottom of the world used to store light, so that light travels properly over and under the world limits.

The pseudo-code below shows how to access individual block information from a single section.

```
byte Nibble4(byte[] arr, int index){
	return index%2 == 0 ? arr[index/2]&0x0F : (arr[index/2]>>4)&0x0F;
}
int BlockPos = y*16*16 + z*16 + x;
compound Block = Palette[change_array_element_size(BlockStates,Log2(length(Palette)))[BlockPos]];
string BlockName = Block.Name;
compound BlockState = Block.Properties;
byte Blocklight = Nibble4(BlockLight, BlockPos);
byte Skylight = Nibble4(SkyLight, BlockPos);
```

## Tile tick format

Tile Ticks represent block updates that need to happen because they could not happen before the chunk was saved. Examples reasons for tile ticks include redstone circuits needing to continue updating, water and lava that should continue flowing, recently placed sand or gravel that should fall, etc. Tile ticks are not used for purposes such as leaf decay, where the decay information is stored in the leaf block data values and handled by Minecraft when the chunk loads. For map makers, tile ticks can be used to update blocks after a period of time has passed with the chunk loaded into memory.

- [NBT Compound / JSON Object] A Tile Tick
  - [String] i: The ID of the block; used to activate the correct block update procedure.
  - [Int] p: If multiple tile ticks are scheduled for the same tick, tile ticks with lower p are processed first. If they also have the same p, the order is unknown.
  - [Int] t: The number of ticks until processing should occur. May be negative when processing is overdue.
  - [Int] x: X position
  - [Int] y: Y position
  - [Int] z: Z position

## ToBeTicked format

This [NBT List / JSON Array] List is always present, and contains 16 [NBT List / JSON Array] Sublists, each representing one of the "sections" of the chunk. Those inside lists may contain [Short] Shorts, each representing a packed coordinate relative to the section : The 4 most significant bits are always 0, then each group of 4 bits (or nibble) represents a section-relative coordinate, from 0 to 15. The order of sections in the list appear to be ordered from bottom to top, and the packing order of the coordinates is `0ZYX`, where `0` refers to the chunk section. When converting *proto-chunks* to full chunks, only coordinates that are stored in PostProcessing appear to receive a tick update, tick updates stored in `block_ticks`, and `fluid_ticks` are ignored. [*verify*]

## History

This section needs to be updated.

Please update this section to reflect recent updates or newly available information. The talk page may contain suggestions.
**Reason:** This history section is missing a significant number of changes, including most of the 1.13 changes, mainly the region file format.

See also: Chunk format/McRegion and Chunk format/Anvil/Before 1.13

| Java Edition Beta | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.2 | | | | | | | Originally, chunks were stored as individual chunk files ".dat" where the file names contained the chunk's position encoded in Base36. |
| The format used was Java Edition Alpha level format. |
| 1.3 | | | | | | | The Region format is introduced. |
| Chunks are now stored in groups of 32×32 chunks in individual ".mcr" files with the coordinates in decimal. |
| The goal was to reduce disk usage by cutting down on the number of file handles Minecraft had open at once.. |
| *Java Edition* | | | | | | | |
| 1.2.1 | | | 12w07a | | | | The Anvil file format is introduced. |
| Chunks are now divided into 16 individual 16×16×16 block sections. |
| Blocks, Data, BlockLight, and SkyLight arrays are now housed in individual chunk Sections. |
| The NBT Format now includes an integer array tag similar to the existing byte array tag. |
| 1.3.1 | | | 12w21a | | | | `MaxExperience`, `RemainingExperience`, `ExperienceRegenTick`, `ExperienceRegenRate` and `ExperienceRegenAmount` from `MobSpawner` have been removed. |
| 1.4.2 | | | 12w34a | | | | Added entity `WitherBoss`. |
| 1.5 | | | 13w02a | | | | Added entity `MinecartTNT`. |
| `Minecart` is now deprecated. |
| 13w03a | | | | Added entity `MinecartHopper`. |
| 13w06a | | | | Added entity `MinecartSpawner`. |
| 1.6.1 | | | 13w16a | | | | Added entity `EntityHorse`. |
| 13w21a | | | | `ArmorType` has been removed from `EntityHorse`. |
| `Saddle` has been removed from `EntityHorse`. |
| release | | | | `Saddle` to `EntityHorse` has been re-added. |
| 1.7.2 | | | 13w39a | | | | Added entity `MinecartCommandBlock`. |
| 13w42a | | | | Added `CanBreakDoors` to `Zombie`. |
| 1.8 | | | 14w02a | | | | Added `Lock` to containers. |
| Item IDs are no longer used when specifying NBT data. |
| Added `Block` to `FallingSand`, using the alphabetical ID format. |
| 14w06a | | | | Added `ShowParticles` to all mobs. |
| Added `PickupDelay` to item entities. |
| Setting `Age` to -32768 makes items, which never expire. |
| `AttackTime` has been removed from mobs. |
| 14w10a | | | | Added `rewardExp` to `Villager`. |
| Added `OwnerUUID` for mobs that can breed. |
| Added `Owner` to `Skull`. |
| Changes to item frames and paintings: added `Facing`, `TileX`, `TileY` and `TileZ` now represent the co-ordinates of the block the item is in rather than what it is placed on. `Direction` and `Dir` are now deprecated. |
| 14w11a | | | | Added entity `Endermite`. |
| Added `EndermiteCount` to `Enderman`. |
| 14w21a | | | | `CustomName` and `CustomNameVisible` now work for all entities. |
| 14w25a | | | | Added entity `Guardian`. |
| Added `Text1`, `Text2`, `Text3` and `Text4` to signs. The limit no longer depends on the amount of characters (16), it now depends on the width of the characters. |
| 14w27a | | | | Added entity `Rabbit`. Added `CommandStats` to command blocks and signs. |
| 14w28a | | | | `EndermiteCount` has been removed from `Enderman`. |
| 14w30a | | | | Added `Silent` for all entities. |
| 14w32a | | | | Added `NoAI` for all mobs. |
| Added entity `ArmorStand`. |
| 14w32c | | | | Added `NoBasePlate` to `ArmorStand`. |
| 1.9 | | | 15w31a | | | | Added tags `HandItems`, `ArmorItems`, `HandDropChances`, and `ArmorDropChances` to `Living`, which now replace the `DropChances` and `Equipment` tags. |
| Added `HandItems` and `ArmorItems` to `ArmorStand`. |
| Added `Glowing` to `Entity`. |
| Added `Team` to `LivingBase`. |
| Added `DragonPhase` to `EnderDragon` |
| Added entity `Shulker`, child of `Entity`. |
| Added entity `ShulkerBullet`, child of `Entity`. |
| Added entity `DragonFireball`, which extends `FireballBase` and has no unique tags. |
| Added entities `TippedArrow` and `SpectralArrow`, children of `Arrow`. |
| Added block `EndGateway`, child of `TileEntity`. |
| Added block `Structure`, child of `TileEntity`. |
| Added item tag `Potion`, child of `tag`. |
| 15w32a | | | | `Tags` and `DataVersion` tag can now be applied on entities. |
| Changed the `Fuse` tag's type for the `PrimedTnt` entity from "Byte" to "Short". |
| 15w32c | | | | Introduced a limit on the amount of tags an entity can now have (1024 tags). When surpassed it displays an error saying: "Cannot add more than 1024 tags to an entity." |
| 15w33a | | | | Added entity `AreaEffectCloud`, child of `Entity`. |
| Added `ExactTeleport` and renamed `Life` to `Age` for `EndGateway`. |
| Added `Linger` to `ThrownPotion`. |
| `DataVersion` from `Entity` has been removed. It is now applied to `Player` only, child of `LivingBase`. |
| `UUID` from `Entity` has been removed. |
| `HealF` under `LivingBase` has now become deprecated. |
| `Health` under `LivingBase` has now changed from type "Short" to "Float". |
| `Equipment` has been removed from `ArmorStand` and `Living` entity, its usage is now replaced by `HandItems` and `ArmorItems`, which were added earlier. |
| 15w33c | | | | Added `BeamTarget` to `EnderCrystal`. |
| 15w34a | | | | Added `powered` and `conditional` byte tags to `Control` tile entity for command blocks. |
| Added `life` and `power` to `FireballBase`. |
| Added `id` inside `SpawnData` to `MobSpawner`. |
| Added `powered` to the `Music` tile entity for note blocks. |
| 15w35a | | | | Added `VillagerProfession` to `Zombie`. |
| 15w37a | | | | Added `Enabled` to `MinecartHopper`. |
| 15w38a | | | | Added `SkeletonTrap` and `SkeletonTrapTime` to `EntityHorse`. |
| 15w41a | | | | `Riding` has been replaced with `Passengers` for all entities. |
| Added `RootVehicle` for all passengers. |
| Added `Type` to `Boat`. |
| 15w42a | | | | Added `Fuel` to `Cauldron` (brewing stands). |
| 15w43a | | | | Added `LootTable` and `LootTableSeed` to `Chest`, `Minecart` and `MinecartHopper`. |
| Added `DeathLootTable` and `DeathLootTableSeed` to all mobs. |
| Added `life` and `power` to all fireballs (`FireballBase`). |
| 15w44a | | | | Added `ShowBottom` to `EnderCrystal`. |
| 15w44b | | | | Added `Potion` and `CustomPotionEffects` to `Arrow`. |
| Added `Potion` to `AreaEffectCloud`. |
| 15w45a | | | | `Linger` has been removed from `ThrownPotion`. Instead, the potion lingers if the stored item has an ID of `minecraft:lingering_potion`. |
| `ThrownPotion` now renders as its stored item, even if the item is not a potion. |
| 15w46a | | | | `MoreCarrotTicks` from `Rabbit` is now set to 40 when rabbits eat a carrot crop, but is not used anyway. |
| 15w47a | | | | Added `PaymentItem` to `Beacon`. |
| 15w49a | | | | `PaymentItem` has been removed from `Beacon`. |
| In order for a sign to accept text, all 4 tags ("Text1", "Text2", "Text3", and "Text4") must exist. |
| 15w51b | | | | The original values of `DisabledSlots` in `ArmorStand` have been changed in nature. |
| 1.10 | | | 16w20a | | | | Added entity `PolarBear`. |
| Added `ZombieType` to `Zombie`, replacing `VillagerProfession` and `IsVillager`. Value of 6 indicates the "husk" zombie. |
| A value of 2 on `SkeletonType` indicates the "stray" skeleton. |
| `NoGravity` extended to all entities. |
| Added `powered`, `showboundingbox` and `showair` to `Structure`. |
| 16w21a | | | | Added `FallFlying` to mobs and armor stands. |
| Added `integrity` and `seed` to `Structure`. |
| pre1 | | | | Added `ParticleParam1` and `ParticleParam2` to `AreaEffectCloud`. |
| 1.11 | | | 16w32a | | | | Horses have been split into entity IDs `horse`, `donkey`, `mule`, `skeleton_horse` and `zombie_horse` for their respective types. `Type` and `HasReproduced` removed from `horse`, `ChestedHorse` and `Items` tags now apply only to `mule` and `donkey`, and `SkeletonTrap` and `SkeletonTrapTime` tags now apply only to `skeleton_horse`. |
| Skeletons have been split into entity IDs `skeleton`, `stray` and `wither_skeleton`. `SkeletonType` tag is removed from all skeleton types. |
| Zombies have been split into entity IDs `zombie`, `zombie_villager` and `husk`. `ZombieType` tag is removed from all zombie types, `ConversionTime` and `Profession` tags now apply only to `zombie_villager`. |
| Guardians have been split into entity IDs `guardian` and `elder_guardian`. `Elder` tag has been removed from `guardian`. |
| Unused savegame IDs `Mob` and `Monster` have been removed. |
| `Pumpkin` byte tag has been added to `snowman`. |
| 16w35a | | | | Added `CustomName` to `banner`. |
| 16w39a | | | | Added `llama`, `llama_spit`, `vindication_illager`, `vex`, `evocation_fangs` and `evocation_illager`. |
| Added `Color` to `shulker`. |
| Added `LocName`, `CustomPotionColor` and `ColorMap` to items. |
| 16w40a | | | | Added `Johnny` to `vindication_illager`. |
| Removed `xTile`, `yTile`, `zTile`, `inTile`, `inGround` from the FireballBase class (in large fireballs, small fireballs, dragon fireballs, and wither skulls). |
| 16w41a | | | | `llama_spit` is now available as a save game entity. |
| 16w42a | | | | Added `crit` to `arrow`. |
| 16w43a | | | | Added `OwnerUUIDLeast` and `OwnerUUIDMost` to `evocation_fangs`. |
| 16w44a | | | | `xTile`, `yTile`, `zTile`, `inTile`, `inGround` have been removed from the fishing bobber entity. |
| pre1 | | | | `ench` has been changed to require at least one compound. |
| 1.12 | | | 17w13a | | | | Added entity `Parrot`, `ShoulderEntityLeft`/`ShoulderEntityRight`, `seenCredits`, `recipeBook` and `Recipes`. |
| 17w14a | | | | Added `isFilteringCraftable`, `isGuiOpen` and `recipes` to `recipeBook`, which is now a compound tag. |
| Added `ConversionPlayerLeast` and `ConversionPlayerMost` to entity `Zombie`. |
| 17w16a | | | | NBT parsing in commands has been improved. |
| 17w17a | | | | Added `toBeDisplayed` to `recipeBook`, `UpdateLastExecution` and `LastExecution`. |
| 17w17b | | | | Added `LoveCauseLeast` and `LoveCauseMost` to breedable entities. |
| 1.13 | | | 17w47a | | | | The `damage` tag from items removed, tools and armor now use `Damage` and maps use `map`, both in `tag`. |
| Endermen's `carried` and `carriedData` merged into `carriedBlockState`. |
| Arrows' `inTile` and `inData` merged into `inBlockState`. |
| Minecarts' `DisplayTile` and `DisplayData` merged into `DisplayState`. |
| Falling blocks' `Block` and `Data` merged into `BlockState`. |
| Moving pistons' `BlockId` and `BlockData` merged into `blockState`. |
| Removed note block and flower pot block entities. |
| Trapped chests now have their own block entity `trapped_chest`. |
| Removed `Base` from banners. |
| Removed `Rot` from heads. |
| 17w47a | | | | Removed `Record` from jukeboxes. |
| 17w47b | | | | Trapped chests no longer have their own block entity, and again use `chest`. |
| 18w01a | | | | `Thrower` and `Owner` has been changed from strings to compounds with two longs named `L` and `M`. |
| 18w02a | | | | Bobbers created by fishing rods now have the entity ID `fishing_bobber`. |
| Painting motives are now lowercased and namespaced. |
| 18w07a | | | | Added `turtle`, `trident`, and `phantom` entities. |
| Added `HomePosX`, `HomePosY`, `HomePosZ`, `TravelPosX`, `TravelPosY`, `TravelPosZ`, and `HasEgg` to `turtle`. |
| Added `AX`, `AY`, `AZ`, and `Size` to `phantom`. |
| 18w15a | | | | Added `dolphin` entity. |
| 18w19a | | | | Renamed `puffer_fish` to `pufferfish`. |
| 18w20a | | | | Renamed `cod_mob` to `cod`. |
| Renamed `salmon_mob` to `salmon`. |
| 18w21a | | | | Added `TreasurePosX`, `TreasurePosY`, `TreasurePosZ`, `GotFish`, and `CanFindTreasure` to `dolphin`. |
| `ench` has been renamed to `Enchantments`. |
| `Enchantments` no longer accepts numeric IDs, and instead requires name IDs. |
| pre5 | | | | Renamed `xp_orb` to `experience_orb`. |
| Renamed `xp_bottle` to `experience_bottle`. |
| Renamed `eye_of_ender_signal` to `eye_of_ender`. |
| Renamed `ender_crystal` to `end_crystal`. |
| Renamed `fireworks_rocket` to `firework_rocket`. |
| Renamed `commandblock_minecart` to `command_block_minecart`. |
| Renamed `villager_golem` to `iron_golem`. |
| Renamed `evocation_fangs` to `evoker_fangs`. |
| Renamed `evocation_illager` to `evoker`. |
| Renamed `vindication_illager` to `vindicator`. |
| Renamed `illusion_illager` to `illusioner`. |
| 1.14 | | | 18w43a | | | | Added `illager_beast`, `panda`, and `pillager` entities. |
| 18w45a | | | | The `LIGHT_BLOCKING` heightmap has been removed. |
| 1.15 | | | 19w36a | | | | The [Int Array] Biomes array in the [NBT Compound / JSON Object] Level tag for each chunk now contains 1024 integers instead of 256, allowing biomes to differ based on altitude. |
| 1.16 | | | 20w12a | | | | UUIDs went from [Long] UUIDLeast/[Long] UUIDMost to [Int Array] UUID. |
| 1.17 | | | 20w45a | | | | `Entities` have been extracted from main (terrain) once they become full chunks, and are now stored in separate entities directory (similar to POI storage). Those new files are still region files with NBT. See Java Edition level format. |
| 20w51a | | | | Added `axolotl`. |
| 21w03a | | | | Added `glow_squid`. |
| 21w13a | | | | Added `goat`. |
| 1.18 | | | 21w39a | | | | Paths for the block and biome data have changed:  - `Level.Sections[].BlockStates` is now `Level.Sections[].block_states.data`. - `Level.Sections[].Palette` is now `Level.Sections[].block_states.palette`. - Chunk’s `Level.Biomes` are now paletted and live in a similar container structure in `Level.Sections[].biomes.palette` and `Level.Sections[].biomes.data`. |
| Chunk’s `Level.CarvingMasks[]` is now `long[]` instead of `byte[]`. |
| 21w43a | | | | Removed chunk’s `Level` and moved everything it contained up.  - `Level.Entities` has moved to `entities`. - `Level.TileEntities` has moved to `block_entities`. - `Level.TileTicks` and `Level.ToBeTicked` have moved to `block_ticks`. - `Level.LiquidTicks` and `Level.LiquidsToBeTicked` have moved to `fluid_ticks`. - `Level.Sections` has moved to `sections`. - `Level.Structures` has moved to `structures`. - `Level.Structures.Starts` has moved to `structures.starts`. - `Level.Sections[].block_states` has moved to `sections[].block_states`. - `Level.Sections[].biomes` has moved to `sections[].biomes`. |
| Added `yPos` the minimum section y position in the chunk. |
| Added `below_zero_retrogen` containing data to support below zero generation. |
| Added `blending_data` containing data to support blending new world generation with existing chunks. |
| 1.20.2 | | | 23w32a | | | | Game no longer uses numeric values when storing mob effects to world. For example, `4` becomes `minecraft:mining_fatigue`. |
| Changed following fields in mob effect instances:  - `Id` (integer) -> `id` (string, resource identifier) - `Ambient` -> `ambient` - `Amplifier` -> `amplifier` - `Duration` -> `duration` - `ShowParticles` -> `show_particles` - `ShowIcon` -> `show_icon` - `HiddenEffect` -> `hidden_effect` - `FactorCalculationData` -> `factor_calculation_data` |
| In NBT format for block entity type `beacon`:  - `Primary` (integer) -> `primary_effect` (string, resource identifier) - `Secondary` (integer) -> `secondary_effect` (string, resource identifier) |
| 1.21 | | | 24w21a | | | | Renamed `Attributes` field in mob data to `attributes` |
| Changed following fields in mob attributes:  - `Name` -> `id` - `Base` -> `base` - `Modifiers` -> `modifiers` |
| Changed following fields in mob attribute modifiers:  - `UUID` (int array) and `Name` (arbitrary string) -> `id` (string, resource identifier) - `Amount` -> `amount` - `Operation` (integer) -> `operation` (string), with `"add_value"` replacing 0, `"add_multiplied_base"` replacing 1, and `"add_multiplied_total"` replacing 2 |
| 1.21.2 | | | 24w39a | | | | Renamed `Lock` field in containers to `lock` and now it has become a compound that represents an item predicate. |
| 1.21.5 | | | 25w07a | | | | The `CustomName` field will no longer be preserved when removed. |
| The `LootTable` field will no longer be preserved when removed. |
| `end_gateway`: The `exit_portal` field will no longer be preserved when removed. |
| `furnace`, `smoker`, `blast_furnace`: The `RecipesUsed` field will no longer be preserved when removed. |
| `skull`: The `note_block_sound` field will no longer be preserved when removed. |
| 25w09a | | | | `campfire`: The `CookingTimes` and `CookingTotalTimes` fields will no longer be preserved when removed. |
| `chiseled_bookshelf`: The `last_interacted_slot` field now defaults to `-1` if not specified. |
| `hopper`: The `TransferCooldown` field now default to `-1` if not specified. |
| `jigsaw`: The `name`, `target`, and `pool` fields now default to `minecraft:empty` if not specified, the `final_state` field now defaults to `minecraft:air` if not specified. |
| `sculk_shrieker`: The `warning_level` field now defaults to `0` if not specified. |
| `structure_block`: The `ignoreEntities` and showboundingbox fields now default to `true` if not specified, the `posY` field now defaults to 1 if not specified. |

## Navigation
