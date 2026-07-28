# Jigsaw Block

> **Source:** <https://minecraft.wiki/w/Jigsaw_Block>  
> **Revision:** 3687283 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_2 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.20.3** — Added "Selection Priority" and "Placement Priority" options.
- **1.20.3** — "Levels" increased from 7 to 20.

---
Jigsaw Block

View all renders

|  |  |
| --- | --- |
| Rarity tier | - Epic ‌[*JE only*] - Common ‌[*BE only*] |
| Renewable | No |
| Stackable | Yes (64) |
| Tool | None |
| Blast resistance | 3,600,000 |
| Hardness | -1 |
| Luminous | No |
| Transparent | No |
| Waterloggable | yes |
| Flammable | No |
| Catches fire from lava | No |
| Map color | 22 COLOR\_LIGHT\_GRAY |
| Note block instrument | Default (Harp) |

```
{
    "extratext": "View [[#Gallery|all renders]]",
    "title": "Jigsaw Block",
    "images": [
        "Jigsaw Block.png"
    ],
    "rows": [
        {
            "field": "\n* Epic ‌<sup class=\" nowrap Inline-Template \" title=\"\">[<i><span title=\"This statement only applies to Java Edition\">(link to Java Edition article, displayed as JE)  only</span></i>]</sup>\n* Common ‌<sup class=\" nowrap Inline-Template \" title=\"\">[<i><span title=\"This statement only applies to Bedrock Edition\">(link to Bedrock Edition article, displayed as BE)  only</span></i>]</sup>",
            "label": "(link to Rarity article, displayed as Rarity tier)"
        },
        {
            "field": "No",
            "label": "(link to Renewable resource article, displayed as Renewable)"
        },
        {
            "field": "Yes (64)",
            "label": "Stackable"
        },
        {
            "field": "None",
            "label": "Tool"
        },
        {
            "field": "3,600,000",
            "label": "(link to Explosion#Blast resistance article, displayed as Blast resistance)"
        },
        {
            "field": "-1",
            "label": "(link to Breaking#Blocks by hardness article, displayed as Hardness)"
        },
        {
            "field": "No",
            "label": "(link to Light article, displayed as Luminous)"
        },
        {
            "field": "No",
            "label": "(link to Opacity article, displayed as Transparent)"
        },
        {
            "field": "yes",
            "label": "(link to Waterlogging article, displayed as Waterloggable)"
        },
        {
            "field": "No",
            "label": "(link to Fire#Flammable blocks article, displayed as Flammable)"
        },
        {
            "field": "No",
            "label": "Catches fire<br>from (link to lava article, displayed as lava)"
        },
        {
            "field": "<span style=\"white-space: nowrap;\"><span style=\"display: inline-block; background-color: rgb(153, 153, 153); border: 1px solid #888; border-radius: 0.3em; color: transparent; width: 1em; height: 1em; vertical-align: -0.36em; margin-right: -0.1em\"><br></span> 22 COLOR<wbr/>_LIGHT<wbr/>_GRAY</span>",
            "label": "(link to Map item format#Color table article, displayed as Map color)"
        },
        {
            "field": "Default (Harp)",
            "label": "(link to Note Block#Instruments article, displayed as Note block instrument)"
        }
    ],
    "invimages": [
        "Jigsaw Block"
    ]
}
```

**Jigsaw blocks** are technical blocks commonly used as a way to construct large structures from smaller sections.

## Obtaining

Jigsaw blocks can be obtained by using various commands such as `/give @s jigsaw`. In *Java Edition*, jigsaw blocks are also available in the Creative inventory when "Operator utilities" is turned on.

In *Java Edition*, structure pieces of jigsaw structures loaded using structure blocks or `/place template` include jigsaw blocks. Structures generated from clicking the "Generate" button in the Jigsaw block interface with the "Keep Jigsaw" setting turned on will also include jigsaw blocks.

In *Bedrock Edition*, structures placed using `/place jigsaw` or `/place structure` with `keepJigsaws` set to `true` include jigsaw blocks.

### Natural generation

Jigsaw blocks do not naturally generate. Some structures rely on jigsaw blocks for generation (e.g. pillager outposts, villages, and ancient cities), but these jigsaw blocks are replaced by other blocks during generation.

## Usage

Players in Survival mode cannot place jigsaw blocks.

### Jigsaw connections

Main article: Jigsaw structure

Jigsaw blocks are function blocks used for the generation of jigsaw structures out of smaller templates.[1] Jigsaw structures are used for the generation of pillager outposts, villages, bastion remnants, ancient cities, trail ruins, and trial chambers; other structures use hardcoded generation. The GUI of a jigsaw block can be used to configure its generation settings. Those are:

Target Pool
:   Refers to a template pool; or an alias of a template pool. The template pool is used to select the connecting structure piece.

Name
:   Name of the jigsaw block.
:   Defaults to `minecraft:empty`.

Target name
:   The desired name of the jigsaw block in the connecting piece to connect to this jigsaw block.
:   Defaults to `minecraft:empty`.

Turns into
:   What the jigsaw block turns into once the whole feature is generated.
:   Defaults to `minecraft:air`.

Selection Priority
:   Defines the order of jigsaw blocks in a template to generate the connecting piece. If the piece being generated contains multiple jigsaw blocks that are all valid connections to the parent block, the game tries to connect to the one with the highest Selection Priority first. In the case of a tie, the connecting block is selected randomly.

Placement Priority
:   Defines the order of in which the connecting piece is processed to handle its jigsaw blocks during the wider structure generation.

Joint type
:   Appears only when jigsaw block is placed facing up or down.
:   Contains two types of joints: Rollable and Aligned

    - Rollable: The connecting piece is placed with a random rotation. Defaults to this.
    - Aligned: The connecting piece is rotated such that the rotations of the jigsaw blocks match (marked by the white bar on the jigsaw block)

### Debug generation

See also: Commands/place

This feature is exclusive to *Java Edition*.

Jigsaw blocks can also be used to generate multiple levels of jigsaw blocks in the world. The settings in the 2nd to last row are only used for this purpose and are not saved when leaving the UI.

Levels
:   Determines how many jigsaw block "levels" it goes through. (ex. Piece>[Layer 1]>[Layer 2]).
:   Can be set to an integer from 0 to 20. Defaults to 0.

Keep Jigsaw
:   Determines if the placed pieces includes the jigsaw blocks it contains or become what its "Turns into" field is set to.
:   Defaults to ON

Generate
:   The button to start the generation.

### Piston interactivity

Jigsaw blocks cannot be pushed by pistons. They also cannot be pushed nor pulled by sticky pistons.

## Sounds

*Java Edition*:

| `stone` sound type | | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Sound | Closed captions | Source | Description | Identifier | Translation key | Volume | Pitch | Attenuation distance |
|  | ​Block broken | Blocks | Once the block has broken | `block.stone.break` | `subtitles.block.generic.break`​ | 1.0 | 0.8 | 16 |
| ​Block placed | Blocks | When the block is placed | `block.stone.place` | `subtitles.block.generic.place`​ | 1.0 | 0.8 | 16 |
|  | ​Block breaking | Blocks | While the block is in the process of being broken | `block.stone.hit` | `subtitles.block.generic.hit`​ | 0.25 | 0.5 | 16 |
|  | ​Something falls on a block | *Entity-Dependent* | Falling on the block with fall damage | `block.stone.fall` | `subtitles.block.generic.fall`​ | 0.5 | 0.75 | 16 |
|  | ​Footsteps | *Entity-Dependent* | Walking on the block | `block.stone.step` | `subtitles.block.generic.footsteps`​ | 0.15 | 1.0 | 16 |

*Bedrock Edition*:

| `normal` sound type | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Sound | Closed captions | Source | Description | Identifier | Translation key | Volume | Pitch |
|  | ​Block broken | Blocks | Once the block has broken | `dig.stone` | `subtitles.block.generic.break`​ | 1.0 | 0.8–1.0 |
| ​Block placed | Blocks | When the block is placed | `place.stone` | `subtitles.block.generic.place`​ | 1.0 | 0.8–1.0 |
|  | ​Block breaking | Blocks | While the block is in the process of being broken | `hit.stone` | `subtitles.block.generic.hit`​ | 0.27 [sound 1] | 0.5 |
| ​Footsteps | Players | Falling on the block with fall damage | `fall.stone` | `subtitles.block.generic.footsteps`​ | 0.4 | 1.0 |
| ​Footsteps | Players | Walking on the block | `step.stone` | `subtitles.block.generic.footsteps`​ | 0.3 | 1.0 |
|  | ​Footsteps | Blocks | Jumping from the block | `jump.stone` | `subtitles.block.generic.footsteps`​ | 0.12 | 1.0 |
| ​Footsteps | Blocks | Falling on the block without fall damage | `land.stone` | `subtitles.block.generic.footsteps`​ | 0.22 | 1.0 |

1. [MCPE-169612](https://bugs.mojang.com/browse/MCPE-169612) – Many blocks make slightly different sounds to stone

## Data values

### ID

*Java Edition*:

| Name | Identifier | Form | Translation key |
| --- | --- | --- | --- |
| Jigsaw Block | `jigsaw` | Block & Item | `block.minecraft.jigsaw` |

| Name | Identifier |
| --- | --- |
| Block entity | `jigsaw` |

*Bedrock Edition*:

| Name | Identifier | Numeric ID | Form | Item ID[i 1] | Translation key |
| --- | --- | --- | --- | --- | --- |
| Jigsaw Block | `jigsaw` | `466` | Block & Giveable Item[i 2] | Identical[i 3] | `tile.jigsaw.name` |

1. ID of block's direct item form, which is used in savegame files and addons.
2. Available with `/give` command.
3. The block's direct item form has the same ID as the block.

| Name | Savegame ID |
| --- | --- |
| Block entity | `JigsawBlock` |

### Block states

See also: Block states

*Java Edition*:

| Name | Default value | Allowed values | Description |
| --- | --- | --- | --- |
| **orientation** | `north_up` | `down_east` `down_north` `down_south` `down_west` `east_up` `north_up` `south_up` `up_east` `up_north` `up_south` `up_west` `west_up` | The direction the jigsaw block is facing. |

*Bedrock Edition*:

| Name | Metadata Bits | Default value | Allowed values | Values for Metadata Bits | Description |
| --- | --- | --- | --- | --- | --- |
| **facing\_direction** | Not Supported | `0` | `0` `1` `2` `3` `4` `5` | `Unsupported` | The direction the jigsaw block is facing. |
| **rotation** | Not Supported | `0` | `0` `1` `2` `3` | `Unsupported` | The rotation around the axis. |

### Block data

A jigsaw block has a block entity associated with it that holds additional data about the block.

*Java Edition*:

See also: Block entity format

- [NBT Compound / JSON Object] Block entity data
  - Tags common to all block entities — inherited from Template:Nbt inherit/blockentity/template:

    - [String] id: Block entity ID
    - [Boolean] keepPacked: 1 or 0 (`true`/`false`) - If `true`, this is an invalid block entity, and this block is not immediately placed when a loaded chunk is loaded. If `false`, this is a normal block entity that can be immediately placed.
    - [Int] x: X coordinate of the block entity.
    - [Int] y: Y coordinate of the block entity.
    - [Int] z: Z coordinate of the block entity.
    - [NBT Compound / JSON Object] components: Optional map of data components that are not represented by additional fields.
      - See Data component format § List of components.
  - [String] final\_state: The block that this jigsaw block becomes.
  - [String] joint: The joint option value, either "rollable" or "aligned".
  - [String] name: The jigsaw block's name. This jigsaw block gets aligned with another structure's jigsaw block that has this value in the target tag.
  - [String] pool: The jigsaw block's target pool to select a structure from.
  - [String] target: The jigsaw block's target name. This jigsaw block gets aligned with another structure's jigsaw block that has this value in the name tag.
  - [Int] selection\_priority: Priority of this jigsaw block being selected for generation. Jigsaw blocks with higher selection priority get selected first.
  - [Int] placement\_priority: Priority of the piece generated by this jigsaw block to place its children. Pieces with higher placement priority generate their children first.

*Bedrock Edition*:

:   See Bedrock Edition level format/Block entity format.

## Videos

Video by slicedlime on how jigsaw blocks are used to generate villages in the 1.14 (Village & Pillage update):

## History

### *Java Edition*

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.14 | | | 18w46a | | | | Added the jigsaw block. It has a GUI, however, they are useless for players at the moment and are used only during world generation (similar to Structure Blocks in data mode). |
| Jigsaw blocks use the missing texture particle when broken. |
| 18w47a | | | | Jigsaw blocks are now used in the generation of pillager outpost structures. |
| 18w48a | | | | Jigsaw blocks are now used in the generation of plains village structures. |
| 18w49a | | | | Jigsaw blocks are now used in the generation of snowy and savanna village structures. |
| 18w50a | | | | Jigsaw blocks are now used in the generation of taiga and desert village structures. |
| The texture of the jigsaw block has been changed. |
| 1.16 | | | 20w13a | | | | A locked texture to the jigsaw block has been added. |
| The interface of the jigsaw block has been changed. |
| 20w16a | | | | A button in the GUI that generates a jigsaw structure starting from the jigsaw block, using a given generation depth has been added. This makes jigsaw blocks now usuable by players. |
| Jigsaw blocks are now used in the generation of bastion remnants. |
| 20w22a | | | | A new "Keep Jigsaws" option that controls whether jigsaw blocks in the resulting structure after using "Generate" remain jigsaw blocks or be replaced by their "Turns Into" block, which defaults to "on" has been added. |
| 1.19 | | | Deep Dark Experimental Snapshot 1 | | | | Jigsaw blocks are now used in the generation of ancient city structures. |
| 1.19.3 | | | 22w44a | | | | Jigsaw blocks are now available in the creative inventory, but only if cheats are enabled. |
| 22w45a | | | | Moved jigsaw blocks behind the "Operator Utilities" tab in the creative inventory. The tab is available only if cheats are enabled and the "Operator Items Tab" option in the controls menu is turned on. |
| 1.20.3 | | | 23w43a | | | | Added "Selection Priority" and "Placement Priority" options. |
| 23w45a | | | | "Levels" increased from 7 to 20. |

### *Bedrock Edition*

| *Bedrock Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.10.0 | | | beta 1.10.0.3 | | | | Added the jigsaw block. |
| The jigsaw block is currently available only by using inventory editors. |
| It is unknown whether jigsaw blocks are used for pillager outpost and new village generation or not. |
| 1.16.0 | | | beta 1.16.0.57 | | | | The texture of the jigsaw block has been changed. |
| Jigsaw blocks are now functional in game. |
| Jigsaw blocks can now be obtained using the `/give <player> jigsaw` command. |
| 1.21.40 | | | Preview 1.21.40.22 | | | | Jigsaw block has had its interface changed. |
| 1.21.50 Experiment Data-Driven Jigsaw Structures | | | Preview 1.21.50.26 | | | | With the new experimental option it is possible to use the jigsaw block for structures. |

## Issues

Issues relating to "Jigsaw" or "Jigsaw block" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%2C%20MCPE%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22jigsaw%22%20OR%20summary%20~%20%22jigsaw%20block%22%29%20ORDER%20BY%20resolution%20DESC).

## Gallery

### Renders

### Screenshots

## See also

- Structure Block

## References

1. ["Minecraft Game"](https://crowdin.com/translate/minecraft/9412/enus-engb#5201242) .

## Navigation
