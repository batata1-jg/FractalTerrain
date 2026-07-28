# Surface rule

> **Source:** <https://minecraft.wiki/w/Surface_rule>  
> **Revision:** 3630036 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_2 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **26.2** — Changed the `noise_threshold` surface rule condition.
- **26.2** — Removed the `noise_gradient` surface rule.

---
This feature is exclusive to *Java Edition*.

**Surface rules** are used to determine the block for each solid position of the terrain. They are responsible for grass and dirt layers, creating different bands of terracotta in badlands, for deepslate, bedrock, and more.

Surface rules are used in the noise settings of a data pack.

## JSON format

A surface rule is a type of [decision tree](https://en.wikipedia.org/wiki/Decision_tree). Using a combination of sequences and conditions, it can implement checks to place the right blocks in the right places.

- [NBT Compound / JSON Object] The root object.
  - [String] type: Type of the surface rule as a resource location.
  - Extra fields of the surface rule, described below.

The possible values for [String] type and associated extra fields:

- **bandlands** [*[sic](https://en.wikipedia.org/wiki/Sic)*] — Used in badlands to place terracotta. This rule has no extra fields.

- **block** — Places a specified block.
  - [NBT Compound / JSON Object] result\_state: The block state to place.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

- **condition** — Checks a condition
  - [NBT Compound / JSON Object] if\_true: The **surface condition** (see below) to check.
  - [NBT Compound / JSON Object] then\_run: The **surface rule** to run if the condition matches.

- **sequence** — Tries surface rules in order, only the first that matches is applied.
  - [NBT List / JSON Array] sequence: A list of surface rules to try.
    - [NBT Compound / JSON Object] A **surface rule** object.

## Surface conditions

- [NBT Compound / JSON Object] The root object.
  - [String] type: Type of the surface condition as a resource location.
  - Extra fields of the surface condition, described below.

The possible values for [String] type and associated extra fields:

- **above\_preliminary\_surface** — Checks if the current position is above the preliminary surface level, which is a Y-level usually a few blocks below the main surface, ignoring noise caves. This is used to prevent grass blocks from being placed in noise caves. This condition has no extra fields. The preliminary surface level is calculated from the `initial_density_without_jaggedness` noise router density function.

- **biome** — Checks the biome at the current position.
  - [NBT List / JSON Array] biome\_is: A list of biomes where this condition matches.
    - [String] A resource location of a biome.

- **hole** — Passes for columns where the surface depth is 0. This condition has no extra fields.

- **noise\_threshold** — Computes the noise value of the current column using a specified noise and checks if it is between the min and max threshold.
  - [String] noise: One noise (an [String] ID).
  - [Double] min\_threshold: The minimum noise value where the condition passes.
  - [Double] max\_threshold: The maximum noise value where the condition passes.

- **not** — Inverts a surface conditions, passing when the nested condition fails.
  - [NBT Compound / JSON Object] invert: The **surface condition** object to invert.

- **steep** — Checks if the current position is a steep face on the north or east sides of a mountain. This condition has no extra fields.

- **stone\_depth** — Checks if the current position is within a specified distance from the surface, either upward or downward, using terrain depth. This is used in vanilla to place grass blocks and dirt layers on the surface in the overworld, and soul sand, soul soil, gravel and basalt floors and ceilings in the nether.
  - [String] surface\_type: Either `floor` or `ceiling`. If `floor`, the blocks will be placed based on the distance to the surface above, if `ceiling`, the distance to the surface below will be used instead.
  - [Int] offset: The vertical offset.
  - [Boolean] add\_surface\_depth: If true, adds the surface depth to the offset. Note: this is *not* `add_stone_depth`!
  - [Int] secondary\_depth\_range: Adds a mapped value of the secondary surface depth to the offset, calculated using the formula: `map(surface_secondary(X,0,Z), -1, 1, 0, secondary_depth_range)`.

- **temperature** — Checks if the current block is in a biome that is cold enough for snowfall. This condition has no extra fields.

- **vertical\_gradient** — Compares the current Y position, with a messy transition, just like the deepslate and bedrock transitions.
  - [String] random\_name: Used as a seed to randomize the gradient.
  - [NBT Compound / JSON Object] true\_at\_and\_below: The lower vertical anchor. Positions at and below this always pass.
    - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

      - [Int] absolute: An absolute height as seen on the F3 screen.
      - [Int] above\_bottom: A relative height starting at the bottom of the world.
      - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
  - [NBT Compound / JSON Object] false\_at\_and\_above: The upper vertical anchor. Positions at and above this always fail. The Y-coords between the two vertical anchors produces a gradient where the probability of success is `(false_at_and_above - Y) / (false_at_and_above - true_at_and_below)`.
    - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

      - [Int] absolute: An absolute height as seen on the F3 screen.
      - [Int] above\_bottom: A relative height starting at the bottom of the world.
      - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.

- **water** — Checks if the current position is above water, based on terrain depth. Note that if there is no water above this block, the condition always passes, regardless of the values of the fields.
  - [Int] offset: The value added to the water depth before the comparison is done. This value can be negative to match blocks at a specific depth relative to water surface.
  - [Int] surface\_depth\_multiplier: Value between -20 and 20. How much it is affected by the surface depth. `surface_depth(X,Z) * surface_depth_multiplier` is added to offset before comparing.
  - [Boolean] add\_stone\_depth: If true, adds the distance to the surface to the offset, effectively checking if the surface block above this one is above water.

- **y\_above** — Checks if the current position is above a specified height (exclusive).
  - [NBT Compound / JSON Object] anchor: The vertical anchor to compare the height with.
    - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

      - [Int] absolute: An absolute height as seen on the F3 screen.
      - [Int] above\_bottom: A relative height starting at the bottom of the world.
      - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
  - [Int] surface\_depth\_multiplier: Value between -20 and 20. How much it is affected by the surface depth. `surface_depth(X,Z) * surface_depth_multiplier` is added to anchor before comparing.
  - [Boolean] add\_stone\_depth: If true, adds the distance to the surface above to the offset.

## Surface depth

The surface depth is an integer value computed for each column, which is used by various conditions. It uses the `minecraft:surface` noise. The calculation is as follows: `floor(surface(X,0,Z) × 2.75 + 3.0 + positional_noise(X,0,Z) × 0.25)` where `surface` returns the noise value of the `minecraft:surface` noise and `positional_noise` returns a random value between 0 and 1.

## Secondary surface depth

The secondary surface depth is a value between -1 and 1 computed for each column, which can be used by the stone\_depth condition. It uses the `minecraft:surface_secondary` noise.

## Terrain depth

The generator tracks the vertical distance to the surface above (internally `stoneDepthAbove`) and the cavity below (internally `stoneDepthBelow`), as well as how deep underwater each block is (if there is any water above it at all; `waterHeight`). These values are used in the stone\_depth, water and y\_above conditions.

## History

### *Java Edition*

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 26.2 | | | snap6 | | | | Changed the `noise_threshold` surface rule condition. |
| Removed the `noise_gradient` surface rule. |

## Navigation
