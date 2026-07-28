# Carver definition

> **Source:** <https://minecraft.wiki/w/Carver_definition>  
> **Revision:** 3672909 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_5 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **26.3** *(unreleased)* — Removed carver `nether_cave`.
- **26.3** *(unreleased)* — Added cave carver fields: - `count` - `start_vertical_radius_multiplier` - `thickness` - `weird_thickness_bias`
- **26.3** *(unreleased)* — Renamed cave carver field `yScale` to `room_vertical_radius_multiplier`.
- **26.3** *(unreleased)* — Renamed canyon carver field `yScale` to `y_scale` and moved under the `shape` compound.
- **26.3** *(unreleased)* — Removed carver fields: - `replaceable` - `lava_level` - `debug_settings`

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**removed / changed since** — 6 occurrence(s):

- ​[*until: JE 26.3*]
- - [NBT Compound / JSON Object] lava\_level​[*until: JE 26.3*]: The Y-level below or equal to which the carved areas are filled with lava. Doesn't affect `nether_cave` (where lava level is always `bottom_y + 31`). Note that the carver is also filled with the fl
- - [String][NBT List / JSON Array] replaceable​[*until: JE 26.3*]: Blocks that can be carved. Can be a block ID, a block tag, or a list of block IDs.
- - [NBT Compound / JSON Object] debug\_settings​[*until: JE 26.3*]: (optional) Replaces blocks in the carved areas for debugging.
- Nether caves​[*until: JE 26.3*] are similar to caves, but with a less frequency and wider tunnels [*verify*]. Also, aquifer doesn't work: The carved blocks below `bottom_y + 32.0` are filled with lava.
- - [Float][NBT Compound / JSON Object] yScale​[*until: JE 26.3*]: Vertically scales canyons.

**upcoming** — 7 occurrence(s):

- ​[*upcoming: JE 26.3*]
- - [Float][NBT Compound / JSON Object] yScale​[*until: JE 26.3*] / [NBT Compound / JSON Object] room\_vertical\_radius\_multiplier​[*upcoming: JE 26.3*]: Vertically scales circular voids.
- - [Int][NBT Compound / JSON Object] count​[*upcoming: JE 26.3*]: The number of cave tunnels to create from a seed chunk. The number must not be negative.
- - [Float][NBT Compound / JSON Object] start\_vertical\_radius\_multiplier​[*upcoming: JE 26.3*]: Multiplier for the vertical radius of the first segment in each tunnel. If not specified, it defaults to 1.0.
- - [Float][NBT Compound / JSON Object] thickness​[*upcoming: JE 26.3*]: Multiplier for the radius of carved tunnels.
- - [Boolean][NBT Compound / JSON Object] weird\_thickness\_bias​[*upcoming: JE 26.3*]: If `true`, thickness will be multiplied with a 10% chance (e.g. `random(0, 1) * random(0, 3) + 1`). If not specified, it defaults to `false`.
- - [Float][NBT Compound / JSON Object] y\_scale​[*upcoming: JE 26.3*]: Vertically scales canyons.

---
This feature is exclusive to *Java Edition*.

**Configured carvers** are used to add caves and canyons. They are referenced in biomes.

## Definition

Configured carvers can be defined in data packs, as part of the directory structure below.

- *data pack name*.zip or  *data pack name*
  - pack.mcmeta
  - data
    - *namespace*
      - worldgen
        - **configured\_carver**
          - **<name>.json**
        - More directories…
      - More directories…

​[*until: JE 26.3*]

- *data pack name*.zip or  *data pack name*
  - pack.mcmeta
  - data
    - *namespace*
      - worldgen
        - **carver**
          - **<name>.json**
        - More directories…
      - More directories…

​[*upcoming: JE 26.3*]

## JSON format

- [NBT Compound / JSON Object]: The root object.
  - [String] type: The ID of carver type; see below.
  - [NBT Compound / JSON Object] config: Configuration values for the carver.
    - [Float] probability: The probability that each chunk attempts to generate carvers. Value between 0 and 1 (both inclusive).
    - [NBT Compound / JSON Object] y: The height at which this carver attempts to generate.
      - Height provider — inherited from Template:Nbt inherit/height provider/template:

        - **Specifying a constant height:**
        - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

          - [Int] absolute: An absolute height as seen on the F3 screen.
          - [Int] above\_bottom: A relative height starting at the bottom of the world.
          - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
        - **Either specifying a constant height directly as above, or specifying the types and additional fields as below:**
        - [String] type: The type of the height provider. One of `constant` (specify a constant height), `uniform` (random value in a uniform distribution), `biased_to_bottom` (random value, biased towards the bottom), `very_biased_to_bottom` (random value, stronger biased towards the bottom), `trapezoid` (random value, isosceles trapezoidal distribution), and `weighted_list` (random value from a weighted list).

          If `type` is `constant`, additional fields are as follows:
        - [NBT Compound / JSON Object] value: The vertical anchor to use as constant height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.

          If `type` is `uniform`, additional fields are as follows:
        - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
        - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.

          If `type` is `biased_to_bottom` or `very_biased_to_bottom`, additional fields are as follows:
        - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
        - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
        - [Int] inner: (optional, defaults to 1). Value between 1 and `max_height - min_height` from anchors (inclusive). Graph below [Int] inner (inclusive) looks like a random distribution and above looks like an exponential distribution. `very_biased_to_bottom` will have a sharper exponential distribution.

          If `type` is `trapezoid`, additional fields are as follows:
        - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
        - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
          - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

            - [Int] absolute: An absolute height as seen on the F3 screen.
            - [Int] above\_bottom: A relative height starting at the bottom of the world.
            - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
        - [Int] plateau: (optional, defaults to 0) The length of the range in the middle of the trapezoid distribution that has a uniform distribution.

          If `type` is `weighted_list`, additional fields are as follows:
        - [NBT List / JSON Array] distribution: (Cannot be empty) A random weighted pool of height providers.
          - [NBT Compound / JSON Object] One entry in the random pool.
            - [Int] data: A height provider.
              - Height provider — inherited from Template:Nbt inherit/height provider/template:

                - **Specifying a constant height:**
                - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                  - [Int] absolute: An absolute height as seen on the F3 screen.
                  - [Int] above\_bottom: A relative height starting at the bottom of the world.
                  - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
                - **Either specifying a constant height directly as above, or specifying the types and additional fields as below:**
                - [String] type: The type of the height provider. One of `constant` (specify a constant height), `uniform` (random value in a uniform distribution), `biased_to_bottom` (random value, biased towards the bottom), `very_biased_to_bottom` (random value, stronger biased towards the bottom), `trapezoid` (random value, isosceles trapezoidal distribution), and `weighted_list` (random value from a weighted list).

                  If `type` is `constant`, additional fields are as follows:
                - [NBT Compound / JSON Object] value: The vertical anchor to use as constant height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.

                  If `type` is `uniform`, additional fields are as follows:
                - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
                - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.

                  If `type` is `biased_to_bottom` or `very_biased_to_bottom`, additional fields are as follows:
                - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
                - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
                - [Int] inner: (optional, defaults to 1). Value between 1 and `max_height - min_height` from anchors (inclusive). Graph below [Int] inner (inclusive) looks like a random distribution and above looks like an exponential distribution. `very_biased_to_bottom` will have a sharper exponential distribution.

                  If `type` is `trapezoid`, additional fields are as follows:
                - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
                - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
                  - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

                    - [Int] absolute: An absolute height as seen on the F3 screen.
                    - [Int] above\_bottom: A relative height starting at the bottom of the world.
                    - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
                - [Int] plateau: (optional, defaults to 0) The length of the range in the middle of the trapezoid distribution that has a uniform distribution.

                  If `type` is `weighted_list`, additional fields are as follows:
                - [NBT List / JSON Array] distribution: (Cannot be empty) A random weighted pool of height providers.
                  - [NBT Compound / JSON Object] One entry in the random pool.
                    - [Int] data: A height provider.
                      - Height provider — inherited from Template:Nbt inherit/height provider/template:

                        - **Specifying a constant height:**
                        - Choices for a vertical anchor (must choose only one of the three)
                        - **Either specifying a constant height directly as above, or specifying the types and additional fields as below:**
                        - [String] type: The type of the height provider. One of `constant` (specify a constant height), `uniform` (random value in a uniform distribution), `biased_to_bottom` (random value, biased towards the bottom), `very_biased_to_bottom` (random value, stronger biased towards the bottom), `trapezoid` (random value, isosceles trapezoidal distribution), and `weighted_list` (random value from a weighted list).

                          If `type` is `constant`, additional fields are as follows:
                        - [NBT Compound / JSON Object] value: The vertical anchor to use as constant height.
                          - Choices for a vertical anchor (must choose only one of the three)

                          If `type` is `uniform`, additional fields are as follows:
                        - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
                          - Choices for a vertical anchor (must choose only one of the three)
                        - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
                          - Choices for a vertical anchor (must choose only one of the three)

                          If `type` is `biased_to_bottom` or `very_biased_to_bottom`, additional fields are as follows:
                        - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
                          - Choices for a vertical anchor (must choose only one of the three)
                        - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
                          - Choices for a vertical anchor (must choose only one of the three)
                        - [Int] inner: (optional, defaults to 1). Value between 1 and `max_height - min_height` from anchors (inclusive). Graph below [Int] inner (inclusive) looks like a random distribution and above looks like an exponential distribution. `very_biased_to_bottom` will have a sharper exponential distribution.

                          If `type` is `trapezoid`, additional fields are as follows:
                        - [NBT Compound / JSON Object] min\_inclusive: The vertical anchor to use as minimum height.
                          - Choices for a vertical anchor (must choose only one of the three)
                        - [NBT Compound / JSON Object] max\_inclusive: The vertical anchor to use as maximum height.
                          - Choices for a vertical anchor (must choose only one of the three)
                        - [Int] plateau: (optional, defaults to 0) The length of the range in the middle of the trapezoid distribution that has a uniform distribution.

                          If `type` is `weighted_list`, additional fields are as follows:
                        - [NBT List / JSON Array] distribution: (Cannot be empty) A random weighted pool of height providers.
                          - [NBT Compound / JSON Object] One entry in the random pool.
                            - [Int] data: A height provider.
                              - Height provider
                            - [Int] weight: The weight of this entry.
                    - [Int] weight: The weight of this entry.
            - [Int] weight: The weight of this entry.
    - [NBT Compound / JSON Object] lava\_level​[*until: JE 26.3*]: The Y-level below or equal to which the carved areas are filled with lava. Doesn't affect `nether_cave` (where lava level is always `bottom_y + 31`). Note that the carver is also filled with the fluid from aquifers, which always include lava below `-54`.
      - Choices for a vertical anchor (must choose only one of the three) — inherited from Template:Nbt inherit/vertical anchor/template:

        - [Int] absolute: An absolute height as seen on the F3 screen.
        - [Int] above\_bottom: A relative height starting at the bottom of the world.
        - [Int] below\_top: A relative height starting at the top of the world. Higher values move the height down.
    - [String][NBT List / JSON Array] replaceable​[*until: JE 26.3*]: Blocks that can be carved. Can be a block ID, a block tag, or a list of block IDs.
    - [NBT Compound / JSON Object] debug\_settings​[*until: JE 26.3*]: (optional) Replaces blocks in the carved areas for debugging.
      - [Boolean] debug\_mode: (optional, defauts to false) Enable debug mode for this carver.
      - [NBT Compound / JSON Object] air\_state: (optional, defaults to acacia button's default state) Replaces air blocks.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
      - [NBT Compound / JSON Object] water\_state: (optional, defaults to acacia button's default state) Replaces water blocks and then waterlogs these blocks.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
      - [NBT Compound / JSON Object] lava\_state: (optional, defaults to acacia button's default state) Replaces lava blocks.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
      - [NBT Compound / JSON Object] barrier\_state: (optional, defaults to acacia button's default state) Replaces barriers of aquifers.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
    - Additional fields based on [String] type; see below.

## Carver Type

The **carver type** determines the shape of the carving.

### `cave` and `nether_cave`

Carves a cave. A cave is a long tunnel that sometimes branches. Sometimes one or more tunnels start from a circular void.

Nether caves​[*until: JE 26.3*] are similar to caves, but with a less frequency and wider tunnels [*verify*]. Also, aquifer doesn't work: The carved blocks below `bottom_y + 32.0` are filled with lava.

Additional fields:

- [Float][NBT Compound / JSON Object] yScale​[*until: JE 26.3*] / [NBT Compound / JSON Object] room\_vertical\_radius\_multiplier​[*upcoming: JE 26.3*]: Vertically scales circular voids.
- [Int][NBT Compound / JSON Object] count​[*upcoming: JE 26.3*]: The number of cave tunnels to create from a seed chunk. The number must not be negative.
  - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

    - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

      If `type` is `constant`, additional fields are as follows:
    - [Float] value: The constant value to use.

      If `type` is `uniform`, additional fields are as follows:
    - [Float] min\_inclusive: The minimum possible value (inclusive).
    - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

      If `type` is `clamped_normal`, additional fields are as follows:
    - [Float] mean: The mean.
    - [Float] deviation: The deviation.
    - [Float] min: The minimum value to clamp to.
    - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

      If `type` is `trapezoid`, additional fields are as follows:
    - [Float] min: The minimum value.
    - [Float] max: The maximum value. Must be larger than [Float] min.
    - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
- [Float][NBT Compound / JSON Object] horizontal\_radius\_multiplier: Horizonally scales cave tunnels. Doesn't affect the length of tunnels.
  - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

    - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

      If `type` is `constant`, additional fields are as follows:
    - [Float] value: The constant value to use.

      If `type` is `uniform`, additional fields are as follows:
    - [Float] min\_inclusive: The minimum possible value (inclusive).
    - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

      If `type` is `clamped_normal`, additional fields are as follows:
    - [Float] mean: The mean.
    - [Float] deviation: The deviation.
    - [Float] min: The minimum value to clamp to.
    - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

      If `type` is `trapezoid`, additional fields are as follows:
    - [Float] min: The minimum value.
    - [Float] max: The maximum value. Must be larger than [Float] min.
    - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
- [Float][NBT Compound / JSON Object] vertical\_radius\_multiplier: Vertically scales cave tunnels. Doesn't affect the length of tunnels.
- [Float][NBT Compound / JSON Object] start\_vertical\_radius\_multiplier​[*upcoming: JE 26.3*]: Multiplier for the vertical radius of the first segment in each tunnel. If not specified, it defaults to 1.0.
  - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

    - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

      If `type` is `constant`, additional fields are as follows:
    - [Float] value: The constant value to use.

      If `type` is `uniform`, additional fields are as follows:
    - [Float] min\_inclusive: The minimum possible value (inclusive).
    - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

      If `type` is `clamped_normal`, additional fields are as follows:
    - [Float] mean: The mean.
    - [Float] deviation: The deviation.
    - [Float] min: The minimum value to clamp to.
    - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

      If `type` is `trapezoid`, additional fields are as follows:
    - [Float] min: The minimum value.
    - [Float] max: The maximum value. Must be larger than [Float] min.
    - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
- [Float][NBT Compound / JSON Object] floor\_level: Value between -1.0 and 1.0 (both inclusive). Change the shape of the cave's horizontal floor. If 0.0, carves the terrain with ellipsoids. If 1.0, carves with upper semi-ellipsoids, resulting in a level floor.
- [Float][NBT Compound / JSON Object] thickness​[*upcoming: JE 26.3*]: Multiplier for the radius of carved tunnels.
- [Boolean][NBT Compound / JSON Object] weird\_thickness\_bias​[*upcoming: JE 26.3*]: If `true`, thickness will be multiplied with a 10% chance (e.g. `random(0, 1) * random(0, 3) + 1`). If not specified, it defaults to `false`.

  - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

    - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

      If `type` is `constant`, additional fields are as follows:
    - [Float] value: The constant value to use.

      If `type` is `uniform`, additional fields are as follows:
    - [Float] min\_inclusive: The minimum possible value (inclusive).
    - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

      If `type` is `clamped_normal`, additional fields are as follows:
    - [Float] mean: The mean.
    - [Float] deviation: The deviation.
    - [Float] min: The minimum value to clamp to.
    - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

      If `type` is `trapezoid`, additional fields are as follows:
    - [Float] min: The minimum value.
    - [Float] max: The maximum value. Must be larger than [Float] min.
    - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`

### `canyon`

Carves a canyon.

Additional fields:

- [Float][NBT Compound / JSON Object] yScale​[*until: JE 26.3*]: Vertically scales canyons.
  - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

    - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

      If `type` is `constant`, additional fields are as follows:
    - [Float] value: The constant value to use.

      If `type` is `uniform`, additional fields are as follows:
    - [Float] min\_inclusive: The minimum possible value (inclusive).
    - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

      If `type` is `clamped_normal`, additional fields are as follows:
    - [Float] mean: The mean.
    - [Float] deviation: The deviation.
    - [Float] min: The minimum value to clamp to.
    - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

      If `type` is `trapezoid`, additional fields are as follows:
    - [Float] min: The minimum value.
    - [Float] max: The maximum value. Must be larger than [Float] min.
    - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
- [Float][NBT Compound / JSON Object] vertical\_rotation: Vertical rotation as a canyon extends.
  - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

    - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

      If `type` is `constant`, additional fields are as follows:
    - [Float] value: The constant value to use.

      If `type` is `uniform`, additional fields are as follows:
    - [Float] min\_inclusive: The minimum possible value (inclusive).
    - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

      If `type` is `clamped_normal`, additional fields are as follows:
    - [Float] mean: The mean.
    - [Float] deviation: The deviation.
    - [Float] min: The minimum value to clamp to.
    - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

      If `type` is `trapezoid`, additional fields are as follows:
    - [Float] min: The minimum value.
    - [Float] max: The maximum value. Must be larger than [Float] min.
    - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
- [NBT Compound / JSON Object] shape: The shape to use for the ravine.
  - [Float][NBT Compound / JSON Object] y\_scale​[*upcoming: JE 26.3*]: Vertically scales canyons.
  - [Float][NBT Compound / JSON Object] distance\_factor: Scales the length of canyons. Higher values make canyons longer.
    - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

      - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

        If `type` is `constant`, additional fields are as follows:
      - [Float] value: The constant value to use.

        If `type` is `uniform`, additional fields are as follows:
      - [Float] min\_inclusive: The minimum possible value (inclusive).
      - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean.
      - [Float] deviation: The deviation.
      - [Float] min: The minimum value to clamp to.
      - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

        If `type` is `trapezoid`, additional fields are as follows:
      - [Float] min: The minimum value.
      - [Float] max: The maximum value. Must be larger than [Float] min.
      - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
  - [Float][NBT Compound / JSON Object] thickness: Scales the breadth and height of canyons.
    - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

      - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

        If `type` is `constant`, additional fields are as follows:
      - [Float] value: The constant value to use.

        If `type` is `uniform`, additional fields are as follows:
      - [Float] min\_inclusive: The minimum possible value (inclusive).
      - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean.
      - [Float] deviation: The deviation.
      - [Float] min: The minimum value to clamp to.
      - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

        If `type` is `trapezoid`, additional fields are as follows:
      - [Float] min: The minimum value.
      - [Float] max: The maximum value. Must be larger than [Float] min.
      - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
  - [Float][NBT Compound / JSON Object] horizontal\_radius\_factor: Scales the breadth of canyons. Higher values make canyons wider.
    - Float provider — inherited from Template:Nbt inherit/float\_provider/template:

      - [String] type: The type of the float provider. One of `constant`, `uniform`, `clamped_normal`, or `trapezoid`.

        If `type` is `constant`, additional fields are as follows:
      - [Float] value: The constant value to use.

        If `type` is `uniform`, additional fields are as follows:
      - [Float] min\_inclusive: The minimum possible value (inclusive).
      - [Float] max\_exclusive: The maximum possible value (exclusive). Must be larger than [Float] min\_inclusive.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean.
      - [Float] deviation: The deviation.
      - [Float] min: The minimum value to clamp to.
      - [Float] max: The maximum value to clamp to. Must be larger than [Float] min.

        If `type` is `trapezoid`, additional fields are as follows:
      - [Float] min: The minimum value.
      - [Float] max: The maximum value. Must be larger than [Float] min.
      - [Float] plateau: The range in the middle of the trapezoid distribution that has a uniform distribution. Must be less than or equal to `max - min`
  - [Float] vertical\_radius\_default\_factor: Vertically scales canyons. Higher values make canyons deeper.
  - [Float] vertical\_radius\_center\_factor: Scales the height based on the horizontal distance from the canyon's center, resulting in deeper center.
  - [Int] width\_smoothness: Higher values smooth canyon walls on the vertical axis. Must be greater than 0.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.16.2 | | | 20w28a | | | | Added experimental support for configured carvers in data packs. |
| 1.17 | | | 21w06a | | | | Removed carvers `underwater_canyon` and `underwater_cave`. |
| 21w08a | | | | Added configuration for the canyon carver. |
| Added optional carver field `debug_settings`. When enabled with `debug_mode`, can place blocks from `air_state`. |
| 21w11a | | | | Renamed canyon carver field `distanceFactor` to `distance_factor`. |
| 21w13a | | | | Added configuration for the cave carver. |
| Replaced carver field `bottom_inclusive` and `top_inclusive` with field `y`, which is a height provider. Added field `yScale`. |
| Moved canyon carver shape fields into sub-object `shape`. |
| 21w16a | | | | Added field `aquifers_enabled`. |
| Added fields `water_state`, `lava_state`, and `barrier_state` in the `debug_settings` object. |
| 1.18 | | | 21w38a | | | | Removed field `aquifers_enabled`. |
| 1.19 | | | 22w15a | | | | Added field `replaceable` to carver configuration. |
| Upcoming *Java Edition* | | | | | | | |
| 26.3 | | | snap2 | | | | Removed carver `nether_cave`. |
| Added cave carver fields:  - `count` - `start_vertical_radius_multiplier` - `thickness` - `weird_thickness_bias` |
| Renamed cave carver field `yScale` to `room_vertical_radius_multiplier`. |
| Renamed canyon carver field `yScale` to `y_scale` and moved under the `shape` compound. |
| Removed carver fields:  - `replaceable` - `lava_level` - `debug_settings` |

## External links

- [Configured carver Generator on misode.github.io](https://misode.github.io/worldgen/carver/)

## Navigation
