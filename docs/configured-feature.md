# Configured feature

> **Source:** <https://minecraft.wiki/w/Configured_feature>  
> **Revision:** 3685678 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_24 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.4** — The `simple_block` feature type has a new optional field: `schedule_tick`.
- **1.21.5** — Added `attached_to_logs` field into the `minecraft:tree` feature config.
- **26.1** — Renamed `forest_rock` feature type to `block_blob`.
- **26.2** — `minecraft:geode`: The fields `cannot_replace` and `invalid_blocks` in blocks section of feature configuration now also accept an ID and a list of IDs in addition to a tag.
- **26.2** — `minecraft:root_system`: The field `root_replaceable` in the feature configuration now also accepts an ID or a list of IDs in addition to a tag.
- **26.2** — `minecraft:vegetation_patch`: The field `replaceable` in the feature configuration now also accepts an ID and or a list of IDs in addition to a tag.
- **26.2** — `minecraft:waterlogged_vegetation_patch`: The field replaceable in the feature configuration now also accepts an ID and or a list of IDs in addition to a tag.
- **26.2** — `pointed_dripstone` has been renamed to `speleothem`.
- **26.2** — `dripstone_cluster` has been renamed to `speleothem_cluster`.
- **26.2** — Added the `base_block`, `pointed_block`, and `replaceable_blocks` fields to the renamed features, as well as the latter to `large_dripstone`.
- **26.2** — Additional speleothem fields have been renamed (e.g. `chance_of_taller_dripstone` to `chance_of_taller_generation`) to account for the name changes.
- **26.2** — `minecraft:multiface_growth`: The field `block` is now mandatory (defaulted to `minecraft:glow_lichen`).
- **26.2** — Added the `minecraft:weighted_random_selector` feature type.
- **26.2** — `minecraft:large_dripstone`: The maximum allowed value for `column_radius` has been reduced from `19` to `16`.
- **26.2** — `minecraft:root_system`: Added `level_test_distance` and `field max_level_deviation` fields.
- **26.3** *(unreleased)* — The `worldgen/configured_feature` registry has moved to `worldgen/feature` and configuration is now done inline in the root object rather than separated into a config field.
- **26.3** *(unreleased)* — Added `minecraft:end_podium` feature type.
- **26.3** *(unreleased)* — `minecraft:tree` feature type: added `shelf_mushroom` tree decorator type, added `poplar_foliage_placer` foliage placer type, added `poplar_trunk_placer` trunk placer type.
- **26.3** *(unreleased)* — Renamed the following feature types: - `basalt_columns` to `stepped_column_cluster` - `basalt_pillar` to `single_block_pillar` - `glowstone_blob` to `random_neighbor_spread`
- **26.3** *(unreleased)* — Added `minecraft:overlay` and `minecraft:projected_random_patchy_square` feature types.
- **26.3** *(unreleased)* — Removed the following feature types: - `minecraft:coral_mushroom` - `minecraft:kelp` - `minecraft:seagrass` - `minecraft:sea_pickle`
- **26.3** *(unreleased)* — `minecraft:coral_claw` and `minecraft:coral_tree` feature types: Added the `feature` field.
- **26.3** *(unreleased)* — Removed the following feature types: - `minecraft:nether_forest_vegetation` - `minecraft:twisting_vines` - `minecraft:weeping_vines`
- **26.3** *(unreleased)* — `minecraft:sculk_patch` feature type: Removed the `extra_rare_growths` and `catalyst_chance` fields.

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**removed / changed since** — 14 occurrence(s):

- - [NBT Compound / JSON Object] config​[*until: JE 26.3*]: Configuration of this configured feature. The properties depend on the value of [String] type, described below.
- ​[*until: JE 26.3*]
- - [Int][NBT Compound / JSON Object] extra\_rare\_growths​[*until: JE 26.3*] The number of extra shriekers generated.
- - [Float] catalyst\_chance​[*until: JE 26.3*] The probability of generating a catalyst. Value between 0.0 and 1.0 (inclusive).

**upcoming** — 24 occurrence(s):

- - [Int][NBT Compound / JSON Object] reach​[*until: JE 26.3*] / [NBT Compound / JSON Object] column\_reach​[*upcoming: JE 26.3*] The max radius of a column in this column cluster. Value between 0 and 3 (inclusive).
- - [Int][NBT Compound / JSON Object] column\_count​[*upcoming: JE 26.3*] The number of columns to generate. Value between 1 and 150 (inclusive).
- - [Int][NBT Compound / JSON Object] cluster\_reach​[*upcoming: JE 26.3*] The size of the square to attempt to generate columns in. Value between 0 and 13 (inclusive) which is also limited by `height`.
- - [String] block​[*upcoming: JE 26.3*] Which block to place.
- - [String] can\_replace​[*upcoming: JE 26.3*] Which blocks can be replaced by the column.
- - [String] continue\_through​[*upcoming: JE 26.3*] Which pre-existing blocks to accept as part of a column.
- - [String] cannot\_place\_on​[*upcoming: JE 26.3*] Which blocks are avoided when starting placement.
- ​[*upcoming: JE 26.3*]
- - [String] can\_replace​[*upcoming: JE 26.3*] Which blocks to replace.
- - [Short][NBT Compound / JSON Object] direction​[*upcoming: JE 26.3*] Vertical direction of the pillar, being `up` or `down`.
- - [Float][NBT Compound / JSON Object] chance\_to\_continue​[*upcoming: JE 26.3*] Optional probability between 0 and 1 (inclusive) that the pillar continues another block, assuming the block matches `can_replace`.
- - [String] cap\_feature​[*upcoming: JE 26.3*] Optional placed feature at the end of the pillar.
- _…5 more_

---
This feature is exclusive to *Java Edition*.

A **configured feature** is the configuration of a feature type. They can be used in **placed features** to define the features that are placed in a world.

Configured features are stored as JSON files within a data pack in the `data/<namespace>/worldgen/configured_feature` folder.

## JSON format

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of feature type.
  - [NBT Compound / JSON Object] config​[*until: JE 26.3*]: Configuration of this configured feature. The properties depend on the value of [String] type, described below.

## Feature types

This section needs cleanup to comply with the style guide.

[[discuss](https://minecraft.wiki/w/Talk:Configured_feature)]

Please help [improve](https://minecraft.wiki/w/Configured_feature?action=edit&section=1) this section. The [talk page](https://minecraft.wiki/w/Talk:Configured_feature) may contain suggestions.
*Reason:* Fix all the redirects in the transclusions, as the features have been moved to title case per MCW:TITLE

A **feature type** determines how and what a configured feature should generate. They are hardcoded, thus new ones cannot be added through datapacks. Most feature types have configuration options that can be set using a configured feature. The following lists all feature types and their configuration options.

**bamboo**

- [NBT Compound / JSON Object] config
  - [Float] probability The probability for a podzol disk to generate below the bamboo. The disk has a radius of 1 to 4 blocks. Value between 0.0 and 1.0 (inclusive).

**basalt\_columns**

- [NBT Compound / JSON Object] config
  - [Int][NBT Compound / JSON Object] reach​[*until: JE 26.3*] / [NBT Compound / JSON Object] column\_reach​[*upcoming: JE 26.3*] The max radius of a column in this column cluster. Value between 0 and 3 (inclusive).
  - [Int][NBT Compound / JSON Object] column\_count​[*upcoming: JE 26.3*] The number of columns to generate. Value between 1 and 150 (inclusive).
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
  - [Int][NBT Compound / JSON Object] height The max height is `height + 1`. Value between 1 and 10 (inclusive).
  - [Int][NBT Compound / JSON Object] cluster\_reach​[*upcoming: JE 26.3*] The size of the square to attempt to generate columns in. Value between 0 and 13 (inclusive) which is also limited by `height`.
  - [String] block​[*upcoming: JE 26.3*] Which block to place.
  - [String] can\_replace​[*upcoming: JE 26.3*] Which blocks can be replaced by the column.
  - [String] continue\_through​[*upcoming: JE 26.3*] Which pre-existing blocks to accept as part of a column.
  - [String] cannot\_place\_on​[*upcoming: JE 26.3*] Which blocks are avoided when starting placement.
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

**block\_blob**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state: The block state to use.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT Compound / JSON Object] can\_place\_on: The block predicate that defines which blocks the rock can be placed on.
    - Block predicate — inherited from Template:Nbt inherit/block predicate/template:

      - [String] type: The type of the block predicate. See Block predicate § Types for options.
      - additional fields depending on [String] type. See Block predicate § Types.

**block\_column**

- [NBT Compound / JSON Object] config
  - [String] direction The direction of the column. One of `up`, `down`, `north`, `east`, `south`, or `west`.
  - [NBT Compound / JSON Object] allowed\_placement A block predicate that must be passed for each position of the column.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.
  - [Boolean] prioritize\_tip Determines where to cut off blocks when space is restricted. If `true`, starts removing layers from the start of the column.
  - [NBT List / JSON Array] layers (Required, but can be empty) The layers of this column.
    - [NBT Compound / JSON Object] A layer.
      - [Int][NBT Compound / JSON Object] height Specifying the height of the layer. Must be a non-negative int.
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
      - [NBT Compound / JSON Object] provider The block to use for this layer.
        - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

          - [String] type: The type of the block state provider, see Block state provider § Types for option.
          - Additional fields based on [String] type, see Block state provider § Types.

**block\_pile**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state\_provider The block to use.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.

**delta\_feature**

[NBT Compound / JSON Object] config

- [NBT Compound / JSON Object] contents The block to use on the inside of the delta.
  - Block state — inherited from Template:Nbt inherit/block state/template:

    - [String] Name: The identifier of the block to use.
    - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
      - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
- [NBT Compound / JSON Object] rim The block to use for the rim of the delta.
  - Block state — inherited from Template:Nbt inherit/block state/template:

    - [String] Name: The identifier of the block to use.
    - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
      - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
- [Int][NBT Compound / JSON Object] size The size of the inside of the delta. Value between 0 and 16 (inclusive).
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
- [Int][NBT Compound / JSON Object] rim\_size The size of the rim of the delta. Value between 0 and 16 (inclusive).
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

The only rule for which blocks the delta feature can replace (via the 'contents' or 'rim' block state) is that the target block must have one air block above it and no horizontally-adjacent air blocks. One consequence of this is that delta features cannot generate underwater, but can generate on the water's surface if the feature placement is on the same y-level.

Delta features are always generated last regardless of the order specified by their biome's generation step. In effect, this means that delta blocks can be overridden by features such as tall grass, often resulting in a splotchy appearance.

**disk**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state\_provider A rule-based block state provider.
    - A rule-based block state provider — inherited from Template:Nbt inherit/rule-based block state provider/template:
      - [NBT Compound / JSON Object] A rule-based block state provider
        - [NBT Compound / JSON Object] fallback The block to use when no rules' predicates match. This field is optional. If unspecified and no rules' predicates match, then no block is placed.
          - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

            - [String] type: The type of the block state provider, see Block state provider § Types for option.
            - Additional fields based on [String] type, see Block state provider § Types.
        - [NBT List / JSON Array] rules (Required, but can be empty) Rules of the block to use.
          - [NBT Compound / JSON Object] One rule.
            - [NBT Compound / JSON Object] if\_true The block predicate of this rule.
              - [String] type: The type of the block predicate. See Block predicate § Types for options.
              - additional fields depending on [String] type. See Block predicate § Types.
            - [NBT Compound / JSON Object] then The block to use when the predicate is passed.
              - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

                - [String] type: The type of the block state provider, see Block state provider § Types for option.
                - Additional fields based on [String] type, see Block state provider § Types.
  - [Int][NBT Compound / JSON Object] radius The radius of this disk. Value between 0 and 8 (inclusive).
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
  - [Int] half\_height Half of the height of this disk. Value between 0 and 4 (inclusive).
  - [NBT Compound / JSON Object] target A block predicate that must be passed to generate this feature.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.

**end\_gateway**

- [NBT Compound / JSON Object] config
  - [Boolean] exact: Whether the gateway should teleport entities to the exact exit position.
  - [NBT List / JSON Array] exit: (optional) The block position where the gateway should exit.
    - [Int] X coordinate.
    - [Int] Y coordinate.
    - [Int] Z coordinate.

**end\_podium**

​[*upcoming: JE 26.3*]

**end\_spike**

- [NBT Compound / JSON Object] config
  - [Boolean] crystal\_invulnerable (optional, defaults to false) Whether the End crystals on it are invulnerable.
  - [NBT List / JSON Array] crystal\_beam\_target (optional) Block position of the beam target.
    - [Int] The X coordinate.
    - [Int] The Y coordinate.
    - [Int] The Z coordinate.
  - [NBT List / JSON Array] spikes (Required, but can be empty. If empty, uses the default random spikes) Configurations of each spike.
    - [NBT Compound / JSON Object] A spike.
      - [Int] centerX (optional, defaults to 0) The X coordinate.
      - [Int] centerZ (optional, defaults to 0) The Z coordinate.
      - [Int] radius (optional, defaults to 0) The radius of the spike.
      - [Int] height (optional, defaults to 0) The height of the spike.
      - [Boolean] guarded (optional, defaults to false) Whether to generate an iron bar cage around the crystal.

**fill\_layer**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state The block to fill with.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [Int] height The layer to fill, starting at the bottom of the world. Value between 0 and 4064 (inclusive).

**flower**

- [NBT Compound / JSON Object] config
  - [Int] tries: (optional, defaults to 128) The number of attempts to generate. Must be a positive integer.
  - [Int] xz\_spread: (optional, defaults to 7) The horizontal spread range. Must be a non-negative integer.
  - [Int] y\_spread: (optional, defaults to 3) The vertical spread range. Must be a non-negative integer.
  - [String][NBT Compound / JSON Object] feature: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition) — The placed feature that this patch generates.

**fallen\_tree**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] trunk\_provider The block to use for the trunk.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [Int][NBT Compound / JSON Object] log\_length The length of the fallen log. Value between 0 and 16 (inclusive).
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
  - [NBT List / JSON Array] log\_decorators (Required, but can be empty) Decorations to add to fallen log of the tree
    - [NBT Compound / JSON Object] A decorator, see Tree definition § Decorator
  - [NBT List / JSON Array] stump\_decorators (Required, but can be empty) Decorations to add to the stump.
    - [NBT Compound / JSON Object] A decorator, see Tree definition § Decorator

**fossil**

- [NBT Compound / JSON Object] config
  - [NBT List / JSON Array] fossil\_structures: (Cannot be empty) A list of fossil structure templates to choose from.
    - [String]: One structure template (an [String] ID)
  - [NBT List / JSON Array] overlay\_structures: (Cannot be empty) A list of overlay structure templates to choose from. Has to have the same length as [NBT List / JSON Array] fossil\_structures.
    - [String]: One structure template (an [String] ID)
  - [String][NBT List / JSON Array][NBT Compound / JSON Object] fossil\_processors: One processor list (an [String] ID, or a new [NBT List / JSON Array][NBT Compound / JSON Object] processor list definition) — The processor for fossil structure templates.
  - [String][NBT List / JSON Array][NBT Compound / JSON Object] overlay\_processors: One processor list (an [String] ID, or a new [NBT List / JSON Array][NBT Compound / JSON Object] processor list definition) — The processor for overlay structure templates.
  - [Int] max\_empty\_corners\_allowed: Integer between 0 and 7 — How many corners of the structure are allowed to be empty for it to generate. Prevents structures floating in the air.

**geode**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] blocks The blocks used for the geode.
    - [NBT Compound / JSON Object] filling\_provider The blockstate provider used for the 'filling' layer. This is air in vanilla geodes.
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] inner\_layer\_provider The blockstate provider used for the inner layer. This is an 'amethyst block' in vanilla geodes.
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] alternate\_inner\_layer\_provider The blockstate provider used for the inner layer 'alternate' block. This is a 'budding amethyst' block in vanilla geodes
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] middle\_layer\_provider The blockstate provider used for the middle layer. This is calcite in vanilla geodes.
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] outer\_layer\_provider The blockstate provider used for the outer layer. This is smooth basalt in vanilla geodes
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT List / JSON Array] inner\_placements (At least one blockstate entry required) The blockstates placed within the geode, adjacent to the 'alternate\_inner\_layer\_provider' block(s) by default. In vanilla geodes there are 4 entries, for 'small\_amethyst\_bud', 'medium\_amethyst\_bud', 'large\_amethyst\_bud' and 'amethyst\_cluster'.
      - [NBT Compound / JSON Object] A block state.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
    - [String] cannot\_replace A block tag listing which blocks not to replace. The tag used by vanilla geodes is `#minecraft:features_cannot_replace`.
    - [String] invalid\_blocks A block tag listing invalid blocks. Due to [MC-264886](https://bugs.mojang.com/browse/MC-264886), any value is treated as `#minecraft:geode_invalid_blocks`. Additionally, air is an invalid block.
  - [NBT Compound / JSON Object] layers The thickness of each layer. Unknown units, seems to be non-linear. Larger values generate larger geodes. Values smaller than precisely 0.01 seem to be broken, resulting in much larger geodes than expected.
    - [Double] filling (optional, defaults to 1.7) Value between 0.01 and 50 (inclusive).
    - [Double] inner\_layer (optional, defaults to 2.2) Value between 0.01 and 50 (inclusive).
    - [Double] middle\_layer (optional, defaults to 3.2) Value between 0.01 and 50 (inclusive).
    - [Double] outer\_layer (optional, defaults to 4.2) Value between 0.01 and 50 (inclusive).
  - [NBT Compound / JSON Object] crack The configuration of the crack on the geode.
    - [Double] generate\_crack\_chance (optional, defaults to 1.0) The probability for generating crack. Value between 0.0 and 1.0 (inclusive).
    - [Double] base\_crack\_size (optional, defaults to 2) Value between 0.0 and 5.0 (inclusive).
    - [Int] crack\_point\_offset (optional, defaults to 2) Value between 0 and 10 (inclusive).
  - [Double] noise\_multiplier (optional, defaults to 0.05) Value between 0.0 and 1.0 (inclusive).
  - [Double] use\_potential\_placements\_chance (optional, defaults to 0.35) The probability for placing the inner placement on a block of inner layer. Value between 0 and 1 (inclusive).
  - [Double] use\_alternate\_layer0\_chance (optional, defaults to 0.0) The chance for a given 'inner\_layer\_provider' block to be replaced with an 'alternate\_inner\_layer\_provider' block. Value between 0 and 1 (inclusive).
  - [Boolean] placements\_require\_layer0\_alternate (optional, defaults to true) Whether the 'inner\_placements' block(s) can only be placed on an 'alternate\_inner\_layer\_provider' block.
  - [Int][NBT Compound / JSON Object] outer\_wall\_distance (optional, defaults to a uniform int between 4 and 5) The offset on each coordinate of the center from the feature start. Value between 1 and 20 (inclusive).
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
  - [Int][NBT Compound / JSON Object] distribution\_points (optional, defaults to a uniform int between 3 and 4) Value between 1 and 20 (inclusive).
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
  - [Int] invalid\_blocks\_threshold Check `distribution_points` times near the center of the geode, and if the number of invalid blocks found exceeds this number, the feature will not be generated.
  - [Int][NBT Compound / JSON Object] point\_offset (optional, defaults to a uniform int between 1 and 2) Value between 1 and 10.
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
  - [Int] min\_gen\_offset (optional, defaults to -16) The minimum Chebyshev distance between the block and the center.
  - [Int] max\_gen\_offset (optional, defaults to 16) The maximum Chebyshev distance between the block and the center.

Unlike ore features, geode features are always centered on their bottom northwest corner. For this reason, any placement predicates should be offset by the geode's average radius in order to perform checks on the proper location.

The 'min\_gen\_offset' and 'max\_gen\_offset' values determines the geode's cutoff size. Values greater than the default of ±16 slightly increase the effective cutoff size for very large geodes, though the size is ultimately limited to within the adjacent chunks.

**huge\_brown\_mushroom**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] cap\_provider The block to use for the cap.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [NBT Compound / JSON Object] stem\_provider The block to use for the stem.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [Int] foliage\_radius (optional，defaults to 2) The size of the cap.
  - [NBT Compound / JSON Object] can\_place\_on The block predicate that defines which blocks the huge mushroom can be placed on.
    - Block predicate — inherited from Template:Nbt inherit/block predicate/template:

      - [String] type: The type of the block predicate. See Block predicate § Types for options.
      - additional fields depending on [String] type. See Block predicate § Types.

**huge\_fungus**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] hat\_state The block to use for the hat.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT Compound / JSON Object] decor\_state The block to use as decoration.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT Compound / JSON Object] stem\_state The block to use for the stem.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT Compound / JSON Object] valid\_base\_block The block to place this feature on.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [Boolean] planted (optional, defaults to false) Whether this huge fungus is planted. If false, it can't exceed the world ceiling, can replace blocks whose material is `plant`, and doesn't drop items when replaced by other blocks.
  - [NBT Compound / JSON Object] replaceable\_blocks A block predicate. The predicate must pass for a block to be replaced by this feature.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.

**huge\_red\_mushroom**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] cap\_provider The block to use for the cap.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [NBT Compound / JSON Object] stem\_provider The block to use for the stem.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [Int] foliage\_radius (optional，defaults to 2) The size of the cap.
  - [NBT Compound / JSON Object] can\_place\_on The block predicate that defines which blocks the huge mushroom can be placed on.
    - Block predicate — inherited from Template:Nbt inherit/block predicate/template:

      - [String] type: The type of the block predicate. See Block predicate § Types for options.
      - additional fields depending on [String] type. See Block predicate § Types.

**iceberg**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state The block to use.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

**lake**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] fluid The block to use for the fluid of the lake.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [NBT Compound / JSON Object] barrier The block to use for the barrier of the lake. If 'air' is specified, the barrier remains unchanged instead of overwriting any existing blocks.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [String] can\_place\_feature Describes the block that the feature can be placed on.
  - [String] can\_replace\_with\_air\_or\_fluid Describes the block that the feature can replace with air or the provided `fluid` block.
  - [String] can\_replace\_with\_barrier Describes the block that the feature can replace with the provided `barrier` block.

**large\_dripstone**

- [NBT Compound / JSON Object] config
  - [Int] floor\_to\_ceiling\_search\_range (optional, defaults to 30) The search range from start point to cave floor or ceiling (rather than from floor to ceiling). Value between 1 and 512 (inclusive).
  - [Int][NBT Compound / JSON Object] column\_radius Used to provide a min and max value for radius. Note that this int provider doesn't provide a single int, but provides the min and max value of the specified distribution. Value between 1 and 16 (inclusive). See [the graph for details](https://www.desmos.com/calculator/8epce7fyjr).
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
  - [Float][NBT Compound / JSON Object] height\_scale Higher value leads to higher height. Value between 0.0 and 20.0 (inclusive).
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
  - [Float] max\_column\_radius\_to\_cave\_height\_ratio The ratio of the max radius to the height of the cave. Value between 0.0 and 1.0 (inclusive).
  - [Float][NBT Compound / JSON Object] stalactite\_bluntness Truncate the tip of stalactite. Higher value leads to lower height. Value between 0.1 and 10.0 (inclusive).
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
  - [Float][NBT Compound / JSON Object] stalagmite\_bluntness Truncate the tip of stalagmite. Higher value leads to lower height. Value between 0.1 and 10.0 (inclusive).
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
  - [Float][NBT Compound / JSON Object] wind\_speed Larger value results in larger inclination. Value between 0.0 and 2.0 (inclusive).
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
  - [Int] min\_radius\_for\_wind The min column radius to used for the wind. Value between 0 and 100.
  - [Float] min\_bluntness\_for\_wind The min value of the bluntnesses to used for the wind. Value between 0.0 and 5.0 (inclusive).
  - [String] replaceable\_blocks Describes which blocks the feature can generate on.

**multiface\_growth**

- [NBT Compound / JSON Object] config
  - [String] block (defaults to `glow_lichen`) The block to place, currently must be `glow_lichen` or `sculk_vein`.
  - [Int] search\_range (optional, defaults to 10) Value between 1 and 64 (inclusive).
  - [Float] chance\_of\_spreading (optional, defaults to 0.5) Value between 0.0 and 1.0 (inclusive).
  - [Boolean] can\_place\_on\_floor (optional, defaults to false).
  - [Boolean] can\_place\_on\_ceiling (optional, defaults to false).
  - [Boolean] can\_place\_on\_wall (optional, defaults to false).
  - [String][NBT List / JSON Array] can\_be\_placed\_on Can be a block ID or a block tag, or a list of block IDs.

**nether\_forest\_vegetation**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state\_provider The block to use.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [Int] spread\_width The horizonal distance to spread to. The max width is `spread_width * 2 -1`. Must be a positive integer.
  - [Int] spread\_height The vertical distance to spread. The max height is `spread_height * 2 -1`. Must be a positive integer.

​[*until: JE 26.3*]

**netherrack\_replace\_blobs**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state The block to use.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [NBT Compound / JSON Object] target The block to replace. Properties here are ignored.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [Int][NBT Compound / JSON Object] radius Value between 0 and 12 (inclusive).
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

**no\_bonemeal\_flower**

- [NBT Compound / JSON Object] config
  - [Int] tries: (optional, defaults to 128) The number of attempts to generate. Must be a positive integer.
  - [Int] xz\_spread: (optional, defaults to 7) The horizontal spread range. Must be a non-negative integer.
  - [Int] y\_spread: (optional, defaults to 3) The vertical spread range. Must be a non-negative integer.
  - [String][NBT Compound / JSON Object] feature: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition) — The placed feature that this patch generates.

**ore**

- [NBT Compound / JSON Object] config
  - [Int] size Value between 0 and 64 (inclusive). Determines the size of the ore vein. Further details on the relationship between 'size' and the actual number of blocks comprising the ore vein is available here.
  - [Float] discard\_chance\_on\_air\_exposure Value between 0 and 1 (inclusive). The chance for the entire ore vein to be discarded if any constituent block is adjacent to an air block. Other non-solid blocks such as water do not count.
  - [NBT List / JSON Array] targets (required, but can be empty) A list of targets.
    - [NBT Compound / JSON Object] A target.
      - [NBT Compound / JSON Object] target A  rule test to check the block to replace.
      - [NBT Compound / JSON Object] state The block to use.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

Note that ore features (if allowed to generate in air) will not generate without a solid block nearby to act as the starting point. The ore feature does not necessarily have to intersect with this point, but the required distance does seem to scale with the ore size.

**overlay**

- [NBT Compound / JSON Object] config
  - [String][NBT Compound / JSON Object][NBT List / JSON Array] features: Any number of placed feature(s) (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing either [String] IDs or new [NBT Compound / JSON Object] definitions in the same data type); Cannot be empty — Features to choose from.

​[*upcoming: JE 26.3*]

**projected\_random\_patchy\_square**

- [NBT Compound / JSON Object] config
  - [String] block: Which block to place.
  - [String] project\_through: Which blocks are considered "empty" and should be projected through.
  - [Int][NBT Compound / JSON Object] size: The size of the square as measured from the center to the edge. Value between 1 and 16 (inclusive).
  - [Int][NBT Compound / JSON Object] max\_projection\_height: The maximum change in y-level from the original height to the placement height for a block in the square. The number must not be negative.

​[*upcoming: JE 26.3*]

**random\_boolean\_selector**

- [NBT Compound / JSON Object] config
  - [String][NBT Compound / JSON Object] feature\_false: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition)
  - [String][NBT Compound / JSON Object] feature\_true: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition)

**random\_selector**

- [NBT Compound / JSON Object] config
  - [String][NBT Compound / JSON Object] default: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition) — Used if none of the below features are chosen.
  - [NBT List / JSON Array] features: (Required, but can be empty) A list of placed features from which to randomly choose.
    - [NBT Compound / JSON Object]: A feature and its corresponding chance.
      - [String][NBT Compound / JSON Object] feature: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition)
      - [Float] chance: The chance of this feature being chosen. Value between 0.0 and 1.0 (inclusive).

The defined features can either reference a placed feature directly, or can reference a configured feature by nesting another "feature" node within. For more information, see this page on the two allowed types of feature definitions.

**random\_patch**

- [NBT Compound / JSON Object] config
  - [Int] tries: (optional, defaults to 128) The number of attempts to generate. Must be a positive integer.
  - [Int] xz\_spread: (optional, defaults to 7) The horizontal spread range. Must be a non-negative integer.
  - [Int] y\_spread: (optional, defaults to 3) The vertical spread range. Must be a non-negative integer.
  - [String][NBT Compound / JSON Object] feature: One placed feature (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition) — The placed feature that this patch generates.

**replace\_single\_block**

- [NBT Compound / JSON Object] config
  - [NBT List / JSON Array] targets (Required, but can be empty) A list of targets.
    - [NBT Compound / JSON Object] A target.
      - [NBT Compound / JSON Object] target A  rule test to check the block to replace.
      - [NBT Compound / JSON Object] state The block to use.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

**root\_system**

- [NBT Compound / JSON Object] config
  - [Int] required\_vertical\_space\_for\_tree Value between 1 and 64 (inclusive).
  - [Int] level\_test\_distance Value between 0 and 16 (inclusive).
  - [Int] max\_level\_deviation Value between 0 and 64 (inclusive).
  - [Int] root\_radius Value between 1 and 64 (inclusive).
  - [Int] root\_placement\_attempts Value between 1 and 256 (inclusive).
  - [Int] root\_column\_max\_height Value between 1 and 4096 (inclusive).
  - [Int] hanging\_root\_radius Value between 1 and 64 (inclusive).
  - [Int] hanging\_roots\_vertical\_span Value between 1 and 16 (inclusive).
  - [Int] hanging\_root\_placement\_attempts Value between 1 and 256 (inclusive).
  - [Int] allowed\_vertical\_water\_for\_tree Value between 1 and 64 (inclusive).
  - [String][NBT List / JSON Array] root\_replaceable A block ID or a list of IDs, or tag with `#` specifying which blocks can be replaced by the root column.
  - [NBT Compound / JSON Object] root\_state\_provider The block to use for the root column.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [NBT Compound / JSON Object] hanging\_root\_state\_provider The block to use hanging below the root column.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [NBT Compound / JSON Object] allowed\_tree\_position The block predicate used to check if the tree position is valid.
    - [String] type: The type of the block predicate. See Block predicate § Types for options.
    - additional fields depending on [String] type. See Block predicate § Types.
  - [String][NBT Compound / JSON Object] feature The placed feature to place on top of the root system. Can be an ID of a placed feature, or a placed feature object.

**scattered\_ore**

- [NBT Compound / JSON Object] config
  - [Int] size Value between 0 and 64 (inclusive). Determines the size of the ore vein. Further details on the relationship between 'size' and the actual number of blocks comprising the ore vein is available here.
  - [Float] discard\_chance\_on\_air\_exposure Value between 0 and 1 (inclusive). The chance for the entire ore vein to be discarded if any constituent block is adjacent to an air block. Other non-solid blocks such as water do not count.
  - [NBT List / JSON Array] targets (required, but can be empty) A list of targets.
    - [NBT Compound / JSON Object] A target.
      - [NBT Compound / JSON Object] target A  rule test to check the block to replace.
      - [NBT Compound / JSON Object] state The block to use.
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

Note that ore features (if allowed to generate in air) will not generate without a solid block nearby to act as the starting point. The ore feature does not necessarily have to intersect with this point, but the required distance does seem to scale with the ore size.

**sculk\_patch**

[NBT Compound / JSON Object] config

- [Int] charge\_count The number of charges. Value between 1 and 32 (inclusive).
- [Int] amount\_per\_charge The initial value of each charge. Value between 1 and 500 (inclusive).
- [Int] spread\_attempts The number of attempts to spread. Value between 1 and 64 (inclusive).
- [Int] growth\_rounds The number of times to generate. Value between 0 and 8 (inclusive).
- [Int] spread\_rounds The number of times to spread. Value between 0 and 8 (inclusive).
- [Int][NBT Compound / JSON Object] extra\_rare\_growths​[*until: JE 26.3*] The number of extra shriekers generated.
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
- [Float] catalyst\_chance​[*until: JE 26.3*] The probability of generating a catalyst. Value between 0.0 and 1.0 (inclusive).

**seagrass**

[NBT Compound / JSON Object] config

- [Float] probability Value between 0.0 and 1.0 (inclusive). Probability of using tall seagrass instead of seagrass

​[*until: JE 26.3*]

**sea\_pickle**

- [NBT Compound / JSON Object] config
  - [Int][NBT Compound / JSON Object] count Value between 0 and 256 (inclusive). The max count of the sea pickle block (not single sea pickle).
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

​[*until: JE 26.3*]

**simple\_block**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] to\_place The block to use.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [Boolean] schedule\_tick (optional, defaults to false) Whether to schedule a block update.

**simple\_random\_selector**

- [NBT Compound / JSON Object] config
  - [String][NBT Compound / JSON Object][NBT List / JSON Array] features: Any number of placed feature(s) (an [String] ID, or a new [NBT Compound / JSON Object] placed feature definition, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing either [String] IDs or new [NBT Compound / JSON Object] definitions in the same data type); Cannot be empty — Features to choose from.

**speleothem**

- [NBT Compound / JSON Object] config
  - [Float] chance\_of\_taller\_generation (optional, defaults to 0.2) Value between 0.0 and 1.0 (inclusive). Probability for double-block dripstone.
  - [Float] chance\_of\_directional\_spread (optional, defaults to 0.7) Value between 0.0 and 1.0 (inclusive). Probability that the dripstone spreads in a horizontal direction.
  - [Float] chance\_of\_spread\_radius2 (optional, defaults to 0.5) Value between 0.0 and 1.0 (inclusive). Probability of horizontal spread by two blocks.
  - [Float] chance\_of\_spread\_radius3 (optional, defaults to 0.5) Value between 0.0 and 1.0 (inclusive). After the spread by two blocks, probability of spreading the third block.
  - [String] base\_block} Describes the block forming the base of the speleothem.
  - [String] pointed\_block Describes the block creating the columns of the speleothem.
  - [String] replaceable\_blocks Describes which blocks the feature can generate on.

**speleothem\_cluster**

- [NBT Compound / JSON Object] config
  - [Int] floor\_to\_ceiling\_search\_range For how many blocks the feature searches for the floor or ceiling. Value between 1 and 512 (inclusive).
  - [Int][NBT Compound / JSON Object] height The height of the cluster. Value between 1 and 128 (inclusive).
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
  - [Int][NBT Compound / JSON Object] radius The radius of the cluster. Value between 1 and 128 (inclusive).
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
  - [Int] max\_stalagmite\_stalactite\_height\_diff The maximum height difference between stalagmites and stalactites. Value between 0 and 64 (inclusive).
  - [Int] height\_deviation The height deviation. Value between 1 and 64 (inclusive).
  - [Int][NBT Compound / JSON Object] speleothem\_block\_layer\_thickness The dripstone block layer's thickness. Value between 0 and 128 (inclusive).
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
  - [Float][NBT Compound / JSON Object] density Value between 0.0 and 2.0 (inclusive).
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
  - [Float][NBT Compound / JSON Object] wetness Value between 0.0 and 2.0 (inclusive).
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
  - [Float] chance\_of\_speleothem\_column\_at\_max\_distance\_from\_center Value between 0.0 and 1.0 (inclusive).
  - [Int] max\_distance\_from\_edge\_affecting\_chance\_of\_speleothem Value between 1 and 64 (inclusive).
  - [Int] max\_distance\_from\_center\_affecting\_height\_bias Value between 1 and 64 (inclusive).
  - [String] base\_block Describes the block forming the base of the speleothem.
  - [String] pointed\_block Describes the block creating the columns of the speleothem.
  - [String] replaceable\_blocks Describes which blocks the feature can generate on.

**spike**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] can\_place\_on: The block predicate that defines which blocks the spike can be placed on.
  - [NBT Compound / JSON Object] can\_replace: The block predicate that defines which blocks the spike can replace.
  - [NBT Compound / JSON Object] state: The block state to use.

**spring\_feature**

- [NBT Compound / JSON Object] config
  - [NBT Compound / JSON Object] state The fluid to use.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [Int] rock\_count (optional, defaults to 4) The required number of blocks adjacent to the spring that belong to [NBT List / JSON Array] valid\_blocks. Block above is not counted.
  - [Int] hole\_count (optional, defaults to 1) The required number of air blocks adjacent to the spring. Block above is not counted.
  - [Boolean] requires\_block\_below (optional, defaults to true) Whether the spring feature requires a block in [NBT List / JSON Array] valid\_blocks below it.
  - [String][NBT List / JSON Array] valid\_blocks Can be a block ID or a block tag, or a list of block IDs.

**tree**

- [NBT Compound / JSON Object]: Root object.
  - [String] type: `minecraft:tree`
  - [NBT Compound / JSON Object] config
    - [Boolean] ignore\_vines (optional, defaults to false) Allows the tree to generate even if there are vines blocking it.
    - [NBT Compound / JSON Object] below\_trunk\_provider An optional rule-based block state provider defining how to replace the block below the trunk. For the default value, see Tree definition § Default below trunk provider
      - A rule-based block state provider — inherited from Template:Nbt inherit/rule-based block state provider/template:
        - [NBT Compound / JSON Object] A rule-based block state provider
          - [NBT Compound / JSON Object] fallback The block to use when no rules' predicates match. This field is optional. If unspecified and no rules' predicates match, then no block is placed.
            - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

              - [String] type: The type of the block state provider, see Block state provider § Types for option.
              - Additional fields based on [String] type, see Block state provider § Types.
          - [NBT List / JSON Array] rules (Required, but can be empty) Rules of the block to use.
            - [NBT Compound / JSON Object] One rule.
              - [NBT Compound / JSON Object] if\_true The block predicate of this rule.
                - [String] type: The type of the block predicate. See Block predicate § Types for options.
                - additional fields depending on [String] type. See Block predicate § Types.
              - [NBT Compound / JSON Object] then The block to use when the predicate is passed.
                - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

                  - [String] type: The type of the block state provider, see Block state provider § Types for option.
                  - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] trunk\_provider The block to use for the trunk. Note that when the trunk placer is `fancy_trunk_placer`, the block must have `axis` property, such as logs.
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] foliage\_provider The block to use for the foliage.
      - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

        - [String] type: The type of the block state provider, see Block state provider § Types for option.
        - Additional fields based on [String] type, see Block state provider § Types.
    - [NBT Compound / JSON Object] minimum\_size Defines the width of the tree at different heights relative to the lowest trunk block, for the minimum size of the feature, see Tree definition § Minimum size
    - [NBT Compound / JSON Object] trunk\_placer Defines how the trunk is generated, see Tree definition § Trunk placer
    - [NBT Compound / JSON Object] foliage\_placer Defines how the foliage is generated, see Tree definition § Foliage placer
    - [NBT Compound / JSON Object] root\_placer (optional) Controls how roots are generated, and which blocks to use, see Tree definition § Root placer
    - [NBT List / JSON Array] decorators (Required, but can be empty) Decorations to add to the tree apart from the trunk and leaves.
      - [NBT Compound / JSON Object] A decorator, see Tree definition § Decorator

**twisting\_vines**

- [NBT Compound / JSON Object] config
  - [Int] spread\_width Must be a positive integer. The max spread width is `spread_width * 2 + 1`
  - [Int] spread\_height Must be a positive integer. The max spread height is `spread_height * 2 + 1`
  - [Int] max\_height Must be a positive integer. The max length is `max_height * 2`, and the min length is 1.

​[*until: JE 26.3*]

**underwater\_magma**

- [NBT Compound / JSON Object] config
  - [Int] floor\_search\_range Value between 0 and 512 (inclusive).
  - [Int] placement\_radius\_around\_floor Value between 0 and 64 (inclusive).
  - [Float] placement\_probability\_per\_valid\_position Value between 0.0 and 1.0 (inclusive).

**vegetation\_patch**

- [NBT Compound / JSON Object] config
  - [String] surface The surface to place on. One of `floor`, or `ceiling`
  - [Int][NBT Compound / JSON Object] depth Value between 1 and 128 (inclusive). Amount of blocks, that should be replaced by column.
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
  - [Int] vertical\_range Value between 1 and 256 (inclusive). Y radius in which column should search for available placement.
  - [Float] extra\_bottom\_block\_chance Value between 0.0 and 1.0 (inclusive). Chance to add 1 to [Int][NBT Compound / JSON Object] depth.
  - [Float] extra\_edge\_column\_chance Value between 0.0 and 1.0 (inclusive). Chance to add search position adjacent to initial rectangle.
  - [Float] vegetation\_chance Value between 0.0 and 1.0 (inclusive). The chance of placing [String][NBT Compound / JSON Object] vegetation\_feature on found available position.
  - [Int][NBT Compound / JSON Object] xz\_radius XZ radius for searching available positions. Also note that the even though the x and z axes share the same integer provider, they are sampled individually, for example, if a `uniform` type integer provider is used for the [Int][NBT Compound / JSON Object] xz\_radius with a min of 2 and a max of 8, the x radius could be 6 while the z radius could be 3. Specifying unique X and Z values isn't possible.
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
  - [String] replaceable A block tag with `#` specifying which blocks can be replaced by the column.
  - [NBT Compound / JSON Object] ground\_state The block, used for generating column.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [String][NBT Compound / JSON Object] vegetation\_feature The placed feature to place on found available position. Can be a placed feature ID, or a placed feature object.

**waterlogged\_vegetation\_patch**

- [NBT Compound / JSON Object] config
  - [String] surface The surface to place on. One of `floor`, or `ceiling`
  - [Int][NBT Compound / JSON Object] depth Value between 1 and 128 (inclusive). Amount of blocks, that should be replaced by column.
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
  - [Int] vertical\_range Value between 1 and 256 (inclusive). Y radius in which column should search for available placement.
  - [Float] extra\_bottom\_block\_chance Value between 0.0 and 1.0 (inclusive). Chance to add 1 to [Int][NBT Compound / JSON Object] depth.
  - [Float] extra\_edge\_column\_chance Value between 0.0 and 1.0 (inclusive). Chance to add search position adjacent to initial rectangle.
  - [Float] vegetation\_chance Value between 0.0 and 1.0 (inclusive). The chance of placing [String][NBT Compound / JSON Object] vegetation\_feature on found available position.
  - [Int][NBT Compound / JSON Object] xz\_radius XZ radius for searching available positions. Also note that the even though the x and z axes share the same integer provider, they are sampled individually, for example, if a `uniform` type integer provider is used for the [Int][NBT Compound / JSON Object] xz\_radius with a min of 2 and a max of 8, the x radius could be 6 while the z radius could be 3. Specifying unique X and Z values isn't possible.
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
  - [String] replaceable A block tag with `#` specifying which blocks can be replaced by the column.
  - [NBT Compound / JSON Object] ground\_state The block, used for generating column.
    - Block state provider — inherited from Template:Nbt inherit/block state provider/template:

      - [String] type: The type of the block state provider, see Block state provider § Types for option.
      - Additional fields based on [String] type, see Block state provider § Types.
  - [String][NBT Compound / JSON Object] vegetation\_feature The placed feature to place on found available position. Can be a placed feature ID, or a placed feature object.

### Configuration-less features

These features have files in the `data/<namespace>/worldgen/configured_feature` folder, but none of them have any configuration options in current versions.

**basalt\_pillar**

- [NBT Compound / JSON Object] config
  - [String] block​[*upcoming: JE 26.3*] Which block to place.
  - [String] can\_replace​[*upcoming: JE 26.3*] Which blocks to replace.
  - [Short][NBT Compound / JSON Object] direction​[*upcoming: JE 26.3*] Vertical direction of the pillar, being `up` or `down`.
  - [Float][NBT Compound / JSON Object] chance\_to\_continue​[*upcoming: JE 26.3*] Optional probability between 0 and 1 (inclusive) that the pillar continues another block, assuming the block matches `can_replace`.
  - [String] cap\_feature​[*upcoming: JE 26.3*] Optional placed feature at the end of the pillar.

​[*until: JE 26.3*]

**blue\_ice**

- [NBT Compound / JSON Object] config: Empty

**bonus\_chest**

- [NBT Compound / JSON Object] config: Empty

**chorus\_plant**

- [NBT Compound / JSON Object] config: Empty

**coral\_claw**

- [NBT Compound / JSON Object] config
  - [String] feature​[*upcoming: JE 26.3*]: A placed feature that is used to place at every desired block position.

​[*until: JE 26.3*]

**coral\_mushroom**

- [NBT Compound / JSON Object] config
  - [String] feature​[*upcoming: JE 26.3*]: A placed feature that is used to place at every desired block position.

​[*until: JE 26.3*]

**coral\_tree**

- [NBT Compound / JSON Object] config
  - [String] feature​[*upcoming: JE 26.3*]: A placed feature that is used to place at every desired block position.

​[*until: JE 26.3*]

**desert\_well**

- [NBT Compound / JSON Object] config: Empty

**end\_island**

- [NBT Compound / JSON Object] config: Empty

**end\_platform**

- [NBT Compound / JSON Object] config: Empty

**freeze\_top\_layer**

- [NBT Compound / JSON Object] config: Empty

**glowstone\_blob**

- [NBT Compound / JSON Object] config
  - [String] block​[*upcoming: JE 26.3*] Which block to place.
  - [String] accepted\_neighbors​[*upcoming: JE 26.3*] Which blocks count as a valid neighbor.
  - [String] can\_replace​[*upcoming: JE 26.3*] Which blocks to replace.
  - [Int][NBT Compound / JSON Object] attempts​[*upcoming: JE 26.3*] How many placements attempts to make. Value between 1 and 3,000 (inclusive).
  - [Int][NBT Compound / JSON Object] xz\_offset​[*upcoming: JE 26.3*] What offsets to try against the x and z axes. Value between -16 and 16 (inclusive).
  - [Int][NBT Compound / JSON Object] y\_offset​[*upcoming: JE 26.3*] What offsets to try against the y axis. Value between -16 and 16 (inclusive).

​[*until: JE 26.3*]

**kelp**

- [NBT Compound / JSON Object] config: Empty

​[*until: JE 26.3*]

**monster\_room**

- [NBT Compound / JSON Object] config: Empty

**no\_op**

- [NBT Compound / JSON Object] config: Empty

**vines**

- [NBT Compound / JSON Object] config: Empty

**void\_start\_platform**

- [NBT Compound / JSON Object] config: Empty

**weeping\_vines**

- [NBT Compound / JSON Object] config: Empty

​[*until: JE 26.3*]

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.16.2 | | | 20w28a | | | | Added experimental support for configured features in data packs. |
| 20w29a | | | | Renamed the `minecraft:decorated_flower` feature in `minecraft:no_bonemeal_flower`. |
| 1.17 | | | 20w45a | | | | Added `minecraft:geode` feature. |
| 20w49a | | | | Added `minecraft:dripstone_cluster`, `minecraft:large_dripstone`, and `minecraft:small_dripstone` features. |
| 21w03a | | | | Added `minecraft:glow_lichen` feature type. |
| 1.19 | | | 22w11a | | | | Added `block` field into `glow_lichen` (`multiface_growth`) feature config. |
| Added `minecraft:sculk_patch` feature type. |
| 22w12a | | | | Leaves are now waterloggable. And foliage placers now always waterlog the blocks when replacing water, if the foliage block has a waterlogged block state. |
| 22w13a | | | | Added `extra_rare_growths` field into `sculk_patch` feature config. |
| 22w14a | | | | Renamed the `glow_lichen` feature type to `multiface_growth`. |
| Added `surface_disk` feature type. |
| Added `can_origin_replace` field into the `minecraft:disk` feature config. Must be a block ID or a block tag, or a list of block IDs. The feature origin must in these blocks to generate the feature. |
| Added `root_placer` field into the `minecraft:tree` feature config. |
| Added `attached_to_leaves` decorator into the `minecraft:tree` feature config. |
| Added `probability` field into the `leave_vine` decorator of the `minecraft:tree` feature config. |
| Added `upwards_branching_trunk_placer` trunk placer into the `minecraft:tree` feature config. |
| 22w15a | | | | Merged the `surface_disk` and `ice_patch` feature types into the `disk` type. |
| Added `state_provider` and `targets` fields into the `minecraft:disk` feature config, replacing `state`, `targets` and `can_origin_replace` fields. |
| Added `trunk_offset_y` and `above_root_placement` fields into the root placer in the `minecraft:tree` feature config. |
| Removed the `y_offset` field of the `mangrove_root_placer` root placer in the `minecraft:tree` feature config. Moved all other additional fields of `mangrove_root_placer` into an object [NBT Compound / JSON Object] mangrove\_root\_placement. |
| 1.19.4 | | | 23w07a | | | | Added `minecraft:cherry_foliage_placer` and `minecraft:cherry_trunk_placer` into the `minecraft:tree` feature config. |
| 1.20 | | | 23w17a | | | | Added `replaceable_blocks` field into the `minecraft:huge_fungus` feature config. |
| 1.21.4 | | | 24w44a | | | | The `simple_block` feature type has a new optional field: `schedule_tick`. |
| 1.21.5 | | | 25w09a | | | | Added `attached_to_logs` field into the `minecraft:tree` feature config. |
| 26.1 | | | snap6 | | | | Renamed `forest_rock` feature type to `block_blob`. |
| 26.2 | | | snap1 | | | | `minecraft:geode`: The fields `cannot_replace` and `invalid_blocks` in blocks section of feature configuration now also accept an ID and a list of IDs in addition to a tag. |
| `minecraft:root_system`: The field `root_replaceable` in the feature configuration now also accepts an ID or a list of IDs in addition to a tag. |
| `minecraft:vegetation_patch`: The field `replaceable` in the feature configuration now also accepts an ID and or a list of IDs in addition to a tag. |
| `minecraft:waterlogged_vegetation_patch`: The field replaceable in the feature configuration now also accepts an ID and or a list of IDs in addition to a tag. |
| `pointed_dripstone` has been renamed to `speleothem`. |
| `dripstone_cluster` has been renamed to `speleothem_cluster`. |
| Added the `base_block`, `pointed_block`, and `replaceable_blocks` fields to the renamed features, as well as the latter to `large_dripstone`. |
| Additional speleothem fields have been renamed (e.g. `chance_of_taller_dripstone` to `chance_of_taller_generation`) to account for the name changes. |
| snap5 | | | | `minecraft:multiface_growth`: The field `block` is now mandatory (defaulted to `minecraft:glow_lichen`). |
| snap6 | | | | Added the `minecraft:weighted_random_selector` feature type. |
| `minecraft:large_dripstone`: The maximum allowed value for `column_radius` has been reduced from `19` to `16`. |
| `minecraft:root_system`: Added `level_test_distance` and `field max_level_deviation` fields. |
| Upcoming *Java Edition* | | | | | | | |
| 26.3 | | | snap1 | | | | The `worldgen/configured_feature` registry has moved to `worldgen/feature` and configuration is now done inline in the root object rather than separated into a config field. |
| Added `minecraft:end_podium` feature type. |
| `minecraft:tree` feature type: added `shelf_mushroom` tree decorator type, added `poplar_foliage_placer` foliage placer type, added `poplar_trunk_placer` trunk placer type. |
| snap2 | | | | Renamed the following feature types:  - `basalt_columns` to `stepped_column_cluster` - `basalt_pillar` to `single_block_pillar` - `glowstone_blob` to `random_neighbor_spread` |
| Added `minecraft:overlay` and `minecraft:projected_random_patchy_square` feature types. |
| snap3 | | | | Removed the following feature types:  - `minecraft:coral_mushroom` - `minecraft:kelp` - `minecraft:seagrass` - `minecraft:sea_pickle` |
| `minecraft:coral_claw` and `minecraft:coral_tree` feature types: Added the `feature` field. |
| snap4 | | | | Removed the following feature types:  - `minecraft:nether_forest_vegetation` - `minecraft:twisting_vines` - `minecraft:weeping_vines` |
| snap5 | | | | `minecraft:sculk_patch` feature type: Removed the `extra_rare_growths` and `catalyst_chance` fields. |

## Issues

Issues relating to "Configured feature" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22Configured%20feature%22%29%20ORDER%20BY%20resolution%20DESC).

## External links

- [Configured feature Generator on misode.github.io](https://misode.github.io/worldgen/feature/)

## Navigation
