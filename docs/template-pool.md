# Template pool

> **Source:** <https://minecraft.wiki/w/Template_pool>  
> **Revision:** 3584615 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

A **template pool** is a group of structure pieces of jigsaw structures. A structure piece is a structure template, a placed feature, or a combination of multiple other pieces. During generation, pieces are randomly selected from the pool. Template pools are configured using JSON files stored within a data pack in the folder `data/<namespace>/worldgen/template_pool` or a addon at `<root BP>/worldgen/template_pools`.

## JSON format

*Java Edition*:

- [NBT Compound / JSON Object] The root tag.
  - [String] fallback: One template pool (an [String] ID) — Used for terminating pieces (such as the end of a village road) or as fallback if structures in this pool can't generate. (Note that empty pool elements don't trigger this, they just generate nothing.)
  - [NBT List / JSON Array] elements: A list of elements to randomly select from.
    - [NBT Compound / JSON Object]: An element.
      - [Int] weight: How likely this element is to be chosen when using this pool. Value between 1 and 150 (inclusive).
      - [NBT Compound / JSON Object] element: A pool element.
        - [String] element\_type: The type of the pool element. See Template pool § Pool elements.
        - [String] projection: Can be `rigid` to place a fixed structure (like a house), or `terrain_matching` to match the terrain height (like a village road).
        - Additional fields depending on [String] element\_type. See Template pool § Pool elements.

*Bedrock Edition*:

- [NBT Compound / JSON Object] The root tag.
  - [String] format\_version: ​[*more information needed*]
  - [NBT Compound / JSON Object] minecraft:template\_pool
    - [NBT Compound / JSON Object] description
      - [String] identifier: The identifier used for this template pool.
    - [String] fallback: One template pool (an [String] ID) — Used for terminating pieces (such as the end of a village road) or as fallback if structures in this pool can't generate. (Note that empty pool elements don't trigger this, they just generate nothing.)
    - [NBT List / JSON Array] elements: A list of elements to randomly select from.
      - [NBT Compound / JSON Object]: An element.
        - [Int] weight: How likely this element is to be chosen when using this pool. Value between 1 and 150 (inclusive).
        - [NBT Compound / JSON Object] element: A pool element.
          - [String] element\_type: The type of the pool element. See Template pool § Pool elements.
          - [String] projection: Can be `rigid` to place a fixed structure (like a house), or `terrain_matching` to match the terrain height (like a village road).
        - Additional fields depending on [String] element\_type. See Template pool § Pool elements.

## Pool elements

A **pool element** represents a single piece of a jigsaw structure.

### Single pool element

This piece places a single structure template. The jigsaw blocks in the template are used for connections and the size of the template determines the bounding box of the piece. The template gets processed using a processor list.

- [NBT Compound / JSON Object]: The element
  - [String] element\_type: `minecraft:single_pool_element`
  - [String] projection: See above.
  - [String] location: One structure template (an [String] ID) — The template to place
  - [String][NBT Compound / JSON Object][NBT List / JSON Array] processors: One processor list (an [String] ID, or a new [NBT Compound / JSON Object][NBT List / JSON Array] processor list definition) — The processors that should modify the template.
  - [String] override\_liquid\_settings‌[*JE only*]: Overrides the `liquid_settings` value in structure definition for this particular element. See Jigsaw\_structure#Data\_values.

​[*more information needed*]

### Legacy single pool element

This feature is exclusive to *Java Edition*.

This is a legacy version of the single pool element that doesn't place any air blocks in the template, instead keeping the original block of the world.

- [NBT Compound / JSON Object]: The element
  - [String] element\_type: `minecraft:legacy_single_pool_element`
  - [String] projection: See above.
  - [String] location: One structure template (an [String] ID) — The template to place
  - [String][NBT Compound / JSON Object][NBT List / JSON Array] processors: One processor list (an [String] ID, or a new [NBT Compound / JSON Object][NBT List / JSON Array] processor list definition) — The processors that should modify the template.

### Feature pool element

This piece places a placed feature. The bounding box of the piece is 1×1×1 blocks. The piece gets connected to its parent piece as if it had a jigsaw block that faces downwards with the name `minecraft:bottom`‌[*JE only*]. In bedrock the name can be anything and no target name is required for the parent jigsaw block.

Feature will always attempt to spawn if jigsaw `joint` is set to `rollable`. If `joint` is set to `aligned`, then a feature will attempth to spawn only when structure rotation upon generation is corresponding to jigsaw `orientation` in next cases: 0°=`up_south`, 90°=`up_east`, 180°=`up_north`, 270°=`up_west`.

- [NBT Compound / JSON Object]: The element
  - [String] element\_type: `minecraft:feature_pool_element`
  - [String] projection: See above.
  - [String] feature: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition) — The feature to place.

### List pool element

This feature is exclusive to *Java Edition*.

This piece places multiple pool elements in sequence. The first element is connected using a jigsaw block, further elements are placed with their starting point at (0,0,0) of the bounding box of the first element using the same rotation. If an element fails to generate, then it and all further elements are discarded. The bounding box of the piece is the smallest box that contains all element bounding boxes. This piece cannot generate any further child pieces.

- [NBT Compound / JSON Object]: The element
  - [String] element\_type: `minecraft:list_pool_element`
  - [String] projection: See above.
  - [NBT List / JSON Array] elements: A list of elements to choose from.
    - [NBT Compound / JSON Object]: A pool element.

### Empty pool element

This piece doesn't place anything.

- [NBT Compound / JSON Object]: The element
  - [String] element\_type: `minecraft:empty_pool_element`
  - [String] projection: See above. ‌[*JE only*]

## External links

- [Template pool Generator on misode.github.io](https://misode.github.io/worldgen/template-pool/)

## Navigation
