# Placed feature

> **Source:** <https://minecraft.wiki/w/Placed_feature>  
> **Revision:** 3663986 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_7 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21** — Added `fixed_placement` placement modifier.
- **1.21.2** — Removed `carving_mask` placement modifier.
- **26.1** — Changed the maximum value of [Int][NBT Compound / JSON Object] count field from `count` placement modifier to 4096.
- **26.3** *(unreleased)* — Renamed `random_offset` to `offset`.
- **26.3** *(unreleased)* — Removed `offset` fields `xz_spread` and `y_spread`.
- **26.3** *(unreleased)* — Added `offset` fields `x`, `y` and `z`.
- **26.3** *(unreleased)* — Added `cuboid` and `random_chance` placement modifiers.

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**upcoming** — 6 occurrence(s):

- - **cuboid**​[*upcoming: JE 26.3*] — Repeats a feature in a cuboid shape.
- - **random\_chance**​[*upcoming: JE 26.3*] — Gates the feature being placed behind a random chance.
- - **random\_offset**​[*until: JE 26.3*] / **offset**​[*upcoming: JE 26.3*] — Applies an offset to the current position. Contrary to its name, the applied offset is only random if the specified integer provider isn't a constant. In other words, a y\_spread of -
- - [Int][NBT Compound / JSON Object] x​[*upcoming: JE 26.3*]: Value between -16 and 16 (inclusive).
- - [Int][NBT Compound / JSON Object] y​[*upcoming: JE 26.3*]: Value between -16 and 16 (inclusive).
- - [Int][NBT Compound / JSON Object] z​[*upcoming: JE 26.3*]: Value between -16 and 16 (inclusive).

**removed / changed since** — 2 occurrence(s):

- - [Int][NBT Compound / JSON Object] xz\_spread​[*until: JE 26.3*]: Value between -16 and 16 (inclusive).
- - [Int][NBT Compound / JSON Object] y\_spread​[*until: JE 26.3*]: Value between -16 and 16 (inclusive).

---
This feature is exclusive to *Java Edition*.

A **placed feature** determines where a **configured feature** should be attempted to be placed using placement modifiers. They can be referenced in biomes.

Placed features are stored as JSON files within a data pack, in the `data/<namespace>/worldgen/placed_feature` folder.

## JSON format

- [NBT Compound / JSON Object]: Root object.
  - [String][NBT Compound / JSON Object] feature: One configured feature (an [String] ID, or a new [NBT Compound / JSON Object] configured feature definition) — The feature to place.
  - [NBT List / JSON Array] placement: A list of placement modifiers, applied in order.
    - [NBT Compound / JSON Object]: A placement modifier.
      - [String] type: The type of this placement modifier.
      - Other additional fields depend on the value of [String] type, described below.

## Placement modifiers

When a placed feature is referenced through a biome file, the placed feature tells the configured feature in the `feature` field to place once on the northwest corner of each chunk at the bottom layer of the world. When a placed feature is referenced from a configured feature file or through the `/place` command, the placed feature tells the configured feature in the `feature` field to place once where the original feature/player is located respectively. Placement modifiers can change the position of the feature and the amount of placements.

Placed features are applied in order to determine where feature placement attempt(s) should occur. This can include moving the placement's position, number of positions, and filtering out positions based on given conditions. Each placement attempt applies placement modifiers separately.

The possible values for [String] type and associated additional fields:

- **biome** — Returns the current position if the biome at that position includes this placed feature, otherwise returns empty. No additional field. In effect, this predicate restricts features from being placed outside the edges of any biome that generates the feature. This modifier type cannot be used in placed features that are referenced from other configured features (for example, from entries in a random\_selector type feature). Minecraft does not catch this type of error automatically on trying to load the world; instead the game runs normally until it tries to generate the feature, which causes the game to crash.

- **block\_predicate\_filter** — Returns the current position when the predicate is passed, otherwise return empty.
  - [NBT Compound / JSON Object] predicate: The block predicate to test.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.

- **count** — Returns multiple copies of the current block position. Although the count is limited to 4096, multiple count predicates can be used, allowing them to be stacked multiplicatively to achieve much higher values.
  - [Int][NBT Compound / JSON Object] count: Value between 0 and 4096 (inclusive).
    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

        If `type` is `constant`, additional fields are as follows:
      - [Int] value: The constant value to use.

        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum possible value.
      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

        If `type` is `clamped`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
      - [Int][NBT Compound / JSON Object] source: The source int provider
        - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

          - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

            If `type` is `constant`, additional fields are as follows:
          - [Int] value: The constant value to use.

            If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum possible value.
          - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

            If `type` is `clamped`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
          - [Int][NBT Compound / JSON Object] source: The source int provider
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider
                  - [Int] weight: The weight of this entry.

            If `type` is `clamped_normal`, additional fields are as follows:
          - [Float] mean: The mean value of the normal distribution.
          - [Float] deviation: The deviation of the normal distribution.
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be.

            If `type` is `weighted_list`, additional fields are as follows:
          - [NBT List / JSON Array] distribution: A random pool of int providers.
            - [NBT Compound / JSON Object]: One entry in the random pool.
              - [Int][NBT Compound / JSON Object] data: An int.
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.
              - [Int] weight: The weight of this entry.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean value of the normal distribution.
      - [Float] deviation: The deviation of the normal distribution.
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be.

        If `type` is `weighted_list`, additional fields are as follows:
      - [NBT List / JSON Array] distribution: A random pool of int providers.
        - [NBT Compound / JSON Object]: One entry in the random pool.
          - [Int][NBT Compound / JSON Object] data: An int.
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                        If `type` is `constant`, additional fields are as follows:
                      - [Int] value: The constant value to use.

                        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum possible value.
                      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                        If `type` is `clamped`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                      - [Int][NBT Compound / JSON Object] source: The source int provider
                        - Int provider

                        If `type` is `clamped_normal`, additional fields are as follows:
                      - [Float] mean: The mean value of the normal distribution.
                      - [Float] deviation: The deviation of the normal distribution.
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be.

                        If `type` is `weighted_list`, additional fields are as follows:
                      - [NBT List / JSON Array] distribution: A random pool of int providers.
                        - [NBT Compound / JSON Object]: One entry in the random pool.
                          - [Int][NBT Compound / JSON Object] data: An int.
                            - Int provider
                          - [Int] weight: The weight of this entry.
                  - [Int] weight: The weight of this entry.
          - [Int] weight: The weight of this entry.

- **count\_on\_every\_layer** — In the horizontal relative range (0,0) to (16,16), at each vertical layer separated by air, lava or water, tries to randomly select the specified number of horizontal positions, whose Y coordinate is one block above this layer at this selected horizontal position. Return these selected positions.
  - [Int][NBT Compound / JSON Object] count：Count on each layer. Value between 0 and 256 (inclusive).
    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

        If `type` is `constant`, additional fields are as follows:
      - [Int] value: The constant value to use.

        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum possible value.
      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

        If `type` is `clamped`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
      - [Int][NBT Compound / JSON Object] source: The source int provider
        - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

          - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

            If `type` is `constant`, additional fields are as follows:
          - [Int] value: The constant value to use.

            If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum possible value.
          - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

            If `type` is `clamped`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
          - [Int][NBT Compound / JSON Object] source: The source int provider
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider
                  - [Int] weight: The weight of this entry.

            If `type` is `clamped_normal`, additional fields are as follows:
          - [Float] mean: The mean value of the normal distribution.
          - [Float] deviation: The deviation of the normal distribution.
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be.

            If `type` is `weighted_list`, additional fields are as follows:
          - [NBT List / JSON Array] distribution: A random pool of int providers.
            - [NBT Compound / JSON Object]: One entry in the random pool.
              - [Int][NBT Compound / JSON Object] data: An int.
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.
              - [Int] weight: The weight of this entry.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean value of the normal distribution.
      - [Float] deviation: The deviation of the normal distribution.
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be.

        If `type` is `weighted_list`, additional fields are as follows:
      - [NBT List / JSON Array] distribution: A random pool of int providers.
        - [NBT Compound / JSON Object]: One entry in the random pool.
          - [Int][NBT Compound / JSON Object] data: An int.
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                        If `type` is `constant`, additional fields are as follows:
                      - [Int] value: The constant value to use.

                        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum possible value.
                      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                        If `type` is `clamped`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                      - [Int][NBT Compound / JSON Object] source: The source int provider
                        - Int provider

                        If `type` is `clamped_normal`, additional fields are as follows:
                      - [Float] mean: The mean value of the normal distribution.
                      - [Float] deviation: The deviation of the normal distribution.
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be.

                        If `type` is `weighted_list`, additional fields are as follows:
                      - [NBT List / JSON Array] distribution: A random pool of int providers.
                        - [NBT Compound / JSON Object]: One entry in the random pool.
                          - [Int][NBT Compound / JSON Object] data: An int.
                            - Int provider
                          - [Int] weight: The weight of this entry.
                  - [Int] weight: The weight of this entry.
          - [Int] weight: The weight of this entry.

- **cuboid**​[*upcoming: JE 26.3*] — Repeats a feature in a cuboid shape.
  - [Int][NBT Compound / JSON Object] xz\_size: The size of the cuboid along the x and z axes. Value between 1 and 16 (inclusive).
  - [Int][NBT Compound / JSON Object] y\_size: The size of the cuboid along the y axis. Value between 1 and 16 (inclusive).
    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

        If `type` is `constant`, additional fields are as follows:
      - [Int] value: The constant value to use.

        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum possible value.
      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

        If `type` is `clamped`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
      - [Int][NBT Compound / JSON Object] source: The source int provider
        - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

          - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

            If `type` is `constant`, additional fields are as follows:
          - [Int] value: The constant value to use.

            If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum possible value.
          - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

            If `type` is `clamped`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
          - [Int][NBT Compound / JSON Object] source: The source int provider
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider
                  - [Int] weight: The weight of this entry.

            If `type` is `clamped_normal`, additional fields are as follows:
          - [Float] mean: The mean value of the normal distribution.
          - [Float] deviation: The deviation of the normal distribution.
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be.

            If `type` is `weighted_list`, additional fields are as follows:
          - [NBT List / JSON Array] distribution: A random pool of int providers.
            - [NBT Compound / JSON Object]: One entry in the random pool.
              - [Int][NBT Compound / JSON Object] data: An int.
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.
              - [Int] weight: The weight of this entry.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean value of the normal distribution.
      - [Float] deviation: The deviation of the normal distribution.
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be.

        If `type` is `weighted_list`, additional fields are as follows:
      - [NBT List / JSON Array] distribution: A random pool of int providers.
        - [NBT Compound / JSON Object]: One entry in the random pool.
          - [Int][NBT Compound / JSON Object] data: An int.
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                        If `type` is `constant`, additional fields are as follows:
                      - [Int] value: The constant value to use.

                        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum possible value.
                      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                        If `type` is `clamped`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                      - [Int][NBT Compound / JSON Object] source: The source int provider
                        - Int provider

                        If `type` is `clamped_normal`, additional fields are as follows:
                      - [Float] mean: The mean value of the normal distribution.
                      - [Float] deviation: The deviation of the normal distribution.
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be.

                        If `type` is `weighted_list`, additional fields are as follows:
                      - [NBT List / JSON Array] distribution: A random pool of int providers.
                        - [NBT Compound / JSON Object]: One entry in the random pool.
                          - [Int][NBT Compound / JSON Object] data: An int.
                            - Int provider
                          - [Int] weight: The weight of this entry.
                  - [Int] weight: The weight of this entry.
          - [Int] weight: The weight of this entry.
  - [Boolean][NBT Compound / JSON Object] include\_interior: Optional; Whether to place interior blocks of the cuboid. Defaults to `true`.
  - [Boolean][NBT Compound / JSON Object] include\_edges: Optional; Whether to place edge blocks of the cuboid. Defaults to `true`. If set to `false`, the cuboid is generated as "somewhat rounded".[1]

- **environment\_scan** — Scans blocks either up or down, until the target condition is met. Returns the block position for which the target condition matches. If no target can be found within the maximum number of steps, returns empty.
  - [String] direction\_of\_search: One of `up` or `down`.
  - [Int] max\_steps: Value between 1 and 32 (inclusive).
  - [NBT Compound / JSON Object] target\_condition: The block predicate that is searched for.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.
  - [NBT Compound / JSON Object] allowed\_search\_condition: (optional) If specified, each step must match this block predicate in order to continue the scan. If a block that doesn't match it is met, but no target block found, returns empty.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.

- **fixed\_placement** — Returns all specified positions, if they are in the current chunk.
  - [NBT List / JSON Array] positions: A list of all placement positions
    - [NBT List / JSON Array]: A position
      - [Int]: x coordinate
      - [Int]: y coordinate
      - [Int]: z coordinate

- **height\_range** — Sets the Y coordinate to a value provided by a height provider. Returns the new position.
  - [NBT Compound / JSON Object] height: The new Y coordinate.
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

- **heightmap** — Sets the Y coordinate to one block above the heightmap. Returns the new position.
  - [String] heightmap: The heightmap to use. One of `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`, `OCEAN_FLOOR`, `OCEAN_FLOOR_WG`, `WORLD_SURFACE` or `WORLD_SURFACE_WG`.

- **in\_square** — For both X and Z, it adds a random value between 0 and 15 (both inclusive). This is a shortcut for a random\_offset modifier with y\_spread set to 0 and xz\_spread as a uniform int from 0 to 15. No additional fields.

- **noise\_based\_count** — When the noise value at the current block position is positive, returns multiple copies of the current block position, whose count is based on a noise value and can gradually change based on the noise value. When noise value is negative or 0, returns empty. The count is calculated by `ceil((noise(x / noise_factor, z / noise_factor) + noise_offset) * noise_to_count_ratio)`.
  - [Double] noise\_factor: Scales the noise input horizontally. Higher values make for wider and more spaced out peaks.
  - [Double] noise\_offset：(optional, defaults to 0) Vertical offset of the noise.
  - [Int] noise\_to\_count\_ratio: Ratio of noise value to count.

- **noise\_threshold\_count** — Returns multiple copies of the current block position. The count is either below\_noise or above\_noise, based on the noise value at the current block position. First checks `noise(x / 200, z / 200) < noise_level`. If that is true, uses `below_noise`, otherwise `above_noise`.
  - [Double] noise\_level: The threshold within the noise of when to use `below_noise` or `above_noise`.
  - [Int] below\_noise: The count when the noise is below the threshold. Value lower than 0 is treated as 0.
  - [Int] above\_noise: The count when the noise is above the threshold. Value lower than 0 is treated as 0.

- **random\_chance**​[*upcoming: JE 26.3*] — Gates the feature being placed behind a random chance.
  - [Float][NBT Compound / JSON Object] chance: Value between 0 and 1 representing the chance that the feature gets placed.

- **random\_offset**​[*until: JE 26.3*] / **offset**​[*upcoming: JE 26.3*] — Applies an offset to the current position. Contrary to its name, the applied offset is only random if the specified integer provider isn't a constant. In other words, a y\_spread of -12 will always offset the placed feature downwards by 12 blocks. Also note that the even though the x and z axes share the same integer provider, they are sampled individually, so, for example, if a `uniform` type integer provider is used for the xz\_spread with a min of 4 and a max of 16, the x offset could be 12 while the z offset could be 5. Specifying unique X and Z values isn't possible.
  - [Int][NBT Compound / JSON Object] xz\_spread​[*until: JE 26.3*]: Value between -16 and 16 (inclusive).
    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

        If `type` is `constant`, additional fields are as follows:
      - [Int] value: The constant value to use.

        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum possible value.
      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

        If `type` is `clamped`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
      - [Int][NBT Compound / JSON Object] source: The source int provider
        - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

          - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

            If `type` is `constant`, additional fields are as follows:
          - [Int] value: The constant value to use.

            If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum possible value.
          - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

            If `type` is `clamped`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
          - [Int][NBT Compound / JSON Object] source: The source int provider
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider
                  - [Int] weight: The weight of this entry.

            If `type` is `clamped_normal`, additional fields are as follows:
          - [Float] mean: The mean value of the normal distribution.
          - [Float] deviation: The deviation of the normal distribution.
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be.

            If `type` is `weighted_list`, additional fields are as follows:
          - [NBT List / JSON Array] distribution: A random pool of int providers.
            - [NBT Compound / JSON Object]: One entry in the random pool.
              - [Int][NBT Compound / JSON Object] data: An int.
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.
              - [Int] weight: The weight of this entry.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean value of the normal distribution.
      - [Float] deviation: The deviation of the normal distribution.
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be.

        If `type` is `weighted_list`, additional fields are as follows:
      - [NBT List / JSON Array] distribution: A random pool of int providers.
        - [NBT Compound / JSON Object]: One entry in the random pool.
          - [Int][NBT Compound / JSON Object] data: An int.
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                        If `type` is `constant`, additional fields are as follows:
                      - [Int] value: The constant value to use.

                        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum possible value.
                      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                        If `type` is `clamped`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                      - [Int][NBT Compound / JSON Object] source: The source int provider
                        - Int provider

                        If `type` is `clamped_normal`, additional fields are as follows:
                      - [Float] mean: The mean value of the normal distribution.
                      - [Float] deviation: The deviation of the normal distribution.
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be.

                        If `type` is `weighted_list`, additional fields are as follows:
                      - [NBT List / JSON Array] distribution: A random pool of int providers.
                        - [NBT Compound / JSON Object]: One entry in the random pool.
                          - [Int][NBT Compound / JSON Object] data: An int.
                            - Int provider
                          - [Int] weight: The weight of this entry.
                  - [Int] weight: The weight of this entry.
          - [Int] weight: The weight of this entry.
  - [Int][NBT Compound / JSON Object] y\_spread​[*until: JE 26.3*]: Value between -16 and 16 (inclusive).
    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

        If `type` is `constant`, additional fields are as follows:
      - [Int] value: The constant value to use.

        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum possible value.
      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

        If `type` is `clamped`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
      - [Int][NBT Compound / JSON Object] source: The source int provider
        - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

          - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

            If `type` is `constant`, additional fields are as follows:
          - [Int] value: The constant value to use.

            If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum possible value.
          - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

            If `type` is `clamped`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
          - [Int][NBT Compound / JSON Object] source: The source int provider
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider
                  - [Int] weight: The weight of this entry.

            If `type` is `clamped_normal`, additional fields are as follows:
          - [Float] mean: The mean value of the normal distribution.
          - [Float] deviation: The deviation of the normal distribution.
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be.

            If `type` is `weighted_list`, additional fields are as follows:
          - [NBT List / JSON Array] distribution: A random pool of int providers.
            - [NBT Compound / JSON Object]: One entry in the random pool.
              - [Int][NBT Compound / JSON Object] data: An int.
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.
              - [Int] weight: The weight of this entry.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean value of the normal distribution.
      - [Float] deviation: The deviation of the normal distribution.
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be.

        If `type` is `weighted_list`, additional fields are as follows:
      - [NBT List / JSON Array] distribution: A random pool of int providers.
        - [NBT Compound / JSON Object]: One entry in the random pool.
          - [Int][NBT Compound / JSON Object] data: An int.
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                        If `type` is `constant`, additional fields are as follows:
                      - [Int] value: The constant value to use.

                        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum possible value.
                      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                        If `type` is `clamped`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                      - [Int][NBT Compound / JSON Object] source: The source int provider
                        - Int provider

                        If `type` is `clamped_normal`, additional fields are as follows:
                      - [Float] mean: The mean value of the normal distribution.
                      - [Float] deviation: The deviation of the normal distribution.
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be.

                        If `type` is `weighted_list`, additional fields are as follows:
                      - [NBT List / JSON Array] distribution: A random pool of int providers.
                        - [NBT Compound / JSON Object]: One entry in the random pool.
                          - [Int][NBT Compound / JSON Object] data: An int.
                            - Int provider
                          - [Int] weight: The weight of this entry.
                  - [Int] weight: The weight of this entry.
          - [Int] weight: The weight of this entry.
  - [Int][NBT Compound / JSON Object] x​[*upcoming: JE 26.3*]: Value between -16 and 16 (inclusive).
  - [Int][NBT Compound / JSON Object] y​[*upcoming: JE 26.3*]: Value between -16 and 16 (inclusive).
  - [Int][NBT Compound / JSON Object] z​[*upcoming: JE 26.3*]: Value between -16 and 16 (inclusive).
    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

        If `type` is `constant`, additional fields are as follows:
      - [Int] value: The constant value to use.

        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum possible value.
      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

        If `type` is `clamped`, additional fields are as follows:
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
      - [Int][NBT Compound / JSON Object] source: The source int provider
        - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

          - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

            If `type` is `constant`, additional fields are as follows:
          - [Int] value: The constant value to use.

            If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum possible value.
          - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

            If `type` is `clamped`, additional fields are as follows:
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
          - [Int][NBT Compound / JSON Object] source: The source int provider
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider
                  - [Int] weight: The weight of this entry.

            If `type` is `clamped_normal`, additional fields are as follows:
          - [Float] mean: The mean value of the normal distribution.
          - [Float] deviation: The deviation of the normal distribution.
          - [Int] min\_inclusive: The minimum allowed value that the number will be.
          - [Int] max\_inclusive: The maximum allowed value that the number will be.

            If `type` is `weighted_list`, additional fields are as follows:
          - [NBT List / JSON Array] distribution: A random pool of int providers.
            - [NBT Compound / JSON Object]: One entry in the random pool.
              - [Int][NBT Compound / JSON Object] data: An int.
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.
              - [Int] weight: The weight of this entry.

        If `type` is `clamped_normal`, additional fields are as follows:
      - [Float] mean: The mean value of the normal distribution.
      - [Float] deviation: The deviation of the normal distribution.
      - [Int] min\_inclusive: The minimum allowed value that the number will be.
      - [Int] max\_inclusive: The maximum allowed value that the number will be.

        If `type` is `weighted_list`, additional fields are as follows:
      - [NBT List / JSON Array] distribution: A random pool of int providers.
        - [NBT Compound / JSON Object]: One entry in the random pool.
          - [Int][NBT Compound / JSON Object] data: An int.
            - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

              - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                If `type` is `constant`, additional fields are as follows:
              - [Int] value: The constant value to use.

                If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum possible value.
              - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                If `type` is `clamped`, additional fields are as follows:
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
              - [Int][NBT Compound / JSON Object] source: The source int provider
                - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                  - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                    If `type` is `constant`, additional fields are as follows:
                  - [Int] value: The constant value to use.

                    If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum possible value.
                  - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                    If `type` is `clamped`, additional fields are as follows:
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                  - [Int][NBT Compound / JSON Object] source: The source int provider
                    - Int provider

                    If `type` is `clamped_normal`, additional fields are as follows:
                  - [Float] mean: The mean value of the normal distribution.
                  - [Float] deviation: The deviation of the normal distribution.
                  - [Int] min\_inclusive: The minimum allowed value that the number will be.
                  - [Int] max\_inclusive: The maximum allowed value that the number will be.

                    If `type` is `weighted_list`, additional fields are as follows:
                  - [NBT List / JSON Array] distribution: A random pool of int providers.
                    - [NBT Compound / JSON Object]: One entry in the random pool.
                      - [Int][NBT Compound / JSON Object] data: An int.
                        - Int provider
                      - [Int] weight: The weight of this entry.

                If `type` is `clamped_normal`, additional fields are as follows:
              - [Float] mean: The mean value of the normal distribution.
              - [Float] deviation: The deviation of the normal distribution.
              - [Int] min\_inclusive: The minimum allowed value that the number will be.
              - [Int] max\_inclusive: The maximum allowed value that the number will be.

                If `type` is `weighted_list`, additional fields are as follows:
              - [NBT List / JSON Array] distribution: A random pool of int providers.
                - [NBT Compound / JSON Object]: One entry in the random pool.
                  - [Int][NBT Compound / JSON Object] data: An int.
                    - Int provider — inherited from Template:Nbt inherit/int\_provider/template:

                      - [String] type: The type of the int provider. One of `constant`, `uniform`, `biased_to_bottom`, `clamped`, `clamped_normal`, or `weighted_list`.

                        If `type` is `constant`, additional fields are as follows:
                      - [Int] value: The constant value to use.

                        If `type` is `uniform` or `biased_to_bottom`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum possible value.
                      - [Int] max\_inclusive: The maximum possible value. Cannot be less than [Int] min\_inclusive.

                        If `type` is `clamped`, additional fields are as follows:
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be. Cannot be less than [Int] min\_inclusive.
                      - [Int][NBT Compound / JSON Object] source: The source int provider
                        - Int provider

                        If `type` is `clamped_normal`, additional fields are as follows:
                      - [Float] mean: The mean value of the normal distribution.
                      - [Float] deviation: The deviation of the normal distribution.
                      - [Int] min\_inclusive: The minimum allowed value that the number will be.
                      - [Int] max\_inclusive: The maximum allowed value that the number will be.

                        If `type` is `weighted_list`, additional fields are as follows:
                      - [NBT List / JSON Array] distribution: A random pool of int providers.
                        - [NBT Compound / JSON Object]: One entry in the random pool.
                          - [Int][NBT Compound / JSON Object] data: An int.
                            - Int provider
                          - [Int] weight: The weight of this entry.
                  - [Int] weight: The weight of this entry.
          - [Int] weight: The weight of this entry.

- **rarity\_filter** — Either returns the current position or empty. The chance is calculated as `1 / chance`.
  - [Int] chance: Must be a positive integer.

- **surface\_relative\_threshold\_filter** — Returns the current position if the surface is inside a range. Otherwise returns empty.
  - [String] heightmap：The heightmap to use. One of `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`, `OCEAN_FLOOR`, `OCEAN_FLOOR_WG`, `WORLD_SURFACE` or `WORLD_SURFACE_WG`.
  - [Int] min\_inclusive: The minimum relative height from the surface to current position.
  - [Int] max\_inclusive: The maximum relative height from the surface to current position.

- **surface\_water\_depth\_filter** — If the number of blocks of a motion blocking material under the surface (the top non-air block) is less than the specified depth, return the current position. Otherwise return empty.
  - [Int] max\_water\_depth: The maximum allowed depth.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.18 | | | pre1 | | | | Introduced placed features, stored in `worldgen/placed_feature` directory. |
| 1.21 | | | pre2 | | | | Added `fixed_placement` placement modifier. |
| 1.21.2 | | | 24w33a | | | | Removed `carving_mask` placement modifier. |
| 26.1 | | | pre3 | | | | Changed the maximum value of [Int][NBT Compound / JSON Object] count field from `count` placement modifier to 4096. |
| Upcoming *Java Edition* | | | | | | | |
| 26.3 | | | snap2 | | | | Renamed `random_offset` to `offset`. |
| Removed `offset` fields `xz_spread` and `y_spread`. |
| Added `offset` fields `x`, `y` and `z`. |
| snap3 | | | | Added `cuboid` and `random_chance` placement modifiers. |

### Removed modifiers

Added in 1.18 Pre-release 1. Removed in 24w33a:

- **carving\_mask** — Returns all positions in the current chunk that have been carved out by a carver. This does not include blocks carved out by noise caves.
  - [String] step: The carving step. Either `air` or `liquid`. 'Liquid'-type carvers are not used in vanilla.

## References

1. ["Minecraft 26.3 Snapshot 3"](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-3) by Java Team – Minecraft.net, July 7, 2026.

## External links

- [Placed feature Generator on misode.github.io](https://misode.github.io/worldgen/placed-feature/)

## Navigation
