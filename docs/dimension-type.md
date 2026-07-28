# Dimension type

> **Source:** <https://minecraft.wiki/w/Dimension_type>  
> **Revision:** 3650747 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_11 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.6** — Added [Int] cloud\_height to the dimension type; cloud presence is no longer controlled by [String] effects and their height is no longer a fixed value.
- **1.21.11** — Added new attributes field for dimensions to specify Environment Attributes.
- **1.21.11** — Many fields have been migrated to environment attributes: - [Boolean] ultrawarm -> `minecraft:gameplay/water_evaporates`, `minecraft:gameplay/fast_lava`, `visual/default_dripstone_particle` - [Boolean] bed\_works -> `minecraft:gameplay/bed_rule` - [Boolean] respawn\_anchor\_works -> `minecraft:gameplay/respawn_anchor_works` - [Int] cloud\_height -> `minecraft:visual/cloud_height` - [Boolean] pigli
- **1.21.11** — The [Boolean] natural field no longer controls whether Nether portals spawn piglin, which was migrated to the `minecraft:gameplay/nether_portal_spawns_piglin` environment attribute.
- **1.21.11** — Added a new optional [String][NBT List / JSON Array] timelines field.
- **1.21.11** — The remaining functionality of [Boolean] natural field has been migrated to the `minecraft:gameplay/eyeblossom_open` and `minecraft:gameplay/creaking_active` environment attributes.
- **1.21.11** — The [String] effects field has been removed and replaced with the following new fields: [String] skybox and [String] cardinal\_light.
- **1.21.11** — The [Int] fixed\_time field has been replaced by [Boolean] has\_fixed\_time.
- **26.1** — Added [String] default\_clock field
- **26.1** — Added [Boolean] has\_ender\_dragon\_fight field
- **26.2** — The field `infiniburn` now also accepts an ID and a list of IDs in addition to a tag.

---
This feature is exclusive to *Java Edition*.

**Dimension types** are technical JSON files within a data pack, in the folder `data/<namespace>/dimension_type`. They define properties of a dimension such as world height build limits, the ambient light, and more.

## JSON format

- [NBT Compound / JSON Object] The root object.
  - [Double] coordinate\_scale: The multiplier applied to coordinates when leaving the dimension. Value between 0.00001 and 30000000.0 (both inclusive).
  - [Boolean] has\_skylight: Whether the dimension has skylight or not. If set to false, weather is additionally disabled.
  - [Boolean] has\_ceiling: Whether the dimension has a bedrock ceiling. Note that this is only a logical ceiling. It is unrelated with whether the dimension really has a block ceiling. If set to true, there is no weather, the way respawn and mob spawning positions are calculated is changed,​[*more information needed*] and maps record terrain within a 32 block radius around the player, instead of 64 – however, no data is actually recorded, and instead a pattern will be displayed.
  - [Boolean] has\_ender\_dragon\_fight: Whether this dimension can have an ender dragon fight.(generates obsidian pillars,ender dragon spawns, bedrock end fountain generates.) ​[*more information needed*]
  - [Float] ambient\_light: How much light the dimension has. When set to 0, it completely follows the light level; when set to 1, there is no ambient lighting.​[*more information needed*]
  - [Boolean] has\_fixed\_time: (optional, defaults to `false`) Whether this dimension has fixed time. ​[*more information needed*]
  - [Int] monster\_spawn\_block\_light\_limit: A single integer from 0 to 15. Block light level must be less than or equal to this value for monsters to spawn.
  - [Int][NBT Compound / JSON Object] monster\_spawn\_light\_level: An integer or int provider from 0 to 15. Each time a monster spawn is attempted, a value is calculated using this int provider. The result of the following formula must be less than or equal to this value for monsters to spawn: `max( skyLight - 10, blockLight )` during thunderstorms, and `max( internalSkyLight, blockLight )` during other weather.
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
  - [Int] logical\_height: The maximum height to which chorus fruits and Nether portals can bring players within this dimension. This excludes portals that were already built above the limit as they still connect normally. Cannot be greater than [Int] height.
  - [Int] min\_y: The minimum height in which blocks can exist within this dimension. Must be between -2032 and 2031 and be a multiple of 16 (effectively making 2016 the maximum).
  - [Int] height: The total height in which blocks can exist within this dimension. Must be between 16 and 4064 and be a multiple of 16. The maximum building height = min\_y + height - 1, which cannot be greater than 2031.
  - [String] infiniburn: A block tag with `#`. Fires on these blocks burns infinitely.
  - [String] skybox: (optional, defaults to `overworld`) The skybox to use. Can be `none` `overworld` `end`.
  - [String] cardinal\_light: (optional, defaults to `default`) Direction of cardinal light affecting blocks. Can be `default` `nether`
  - [NBT Compound / JSON Object] attributes: Map of environment attributes that apply when in this dimension.
  - [String] default\_clock: One world clock (an [String] ID) to use as the default for this dimension. This clock will be used as default for the `/time` command, and the `minecraft:wake_up_from_sleep` and `minecraft:roll_village_siege` time markers of this clock will be used. If not specified, the dimension doesn't have a default clock.
  - [String][NBT List / JSON Array] timelines: Any number of timeline(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) that are active in this dimension.

## Defaults

These are the settings used by the 3 dimensions present in Vanilla and the additional Overworld Caves settings provided by Minecraft.

| Property | Overworld | The Nether | The End | Overworld Caves |
| --- | --- | --- | --- | --- |
| [Boolean] has\_skylight | True | False | True | True |
| [Boolean] has\_ceiling | False | True | False | True |
| [Boolean] has\_ender\_dragon\_fight | False | False | True | False |
| [Double] coordinate\_scale | 1.0 | 8.0 | 1.0 | 1.0 |
| [Boolean] has\_fixed\_time | False | True | True | False |
| [Float] ambient\_light | 0.0 | 0.1 | 0.25 | 0.0 |
| [Int] min\_y | -64 | 0 | 0 | -64 |
| [Int] height | 384 | 256 | 256 | 384 |
| [Int] logical\_height | 384 | 128 | 256 | 384 |
| [Int][NBT Compound / JSON Object] monster\_spawn\_light\_level | 0-7 | 7 | 15 | 0-7 |
| [Int] monster\_spawn\_block\_light\_limit | 0 | 15 | 0 | 0 |
| [String] infiniburn | `#infiniburn_overworld` | `#infiniburn_nether` | `#infiniburn_end` | `#infiniburn_overworld` |
| [String] skybox | overworld | none | end | overworld |
| [String] cardinal\_light | default | nether | default | default |
| [String] default\_clock | overworld | N/A | the\_end | overworld |

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.16 | | | Pre-release 1 | | | | Added dimension types to data packs. |
| 1.16.2 | | | pre1 | | | | Dimension types now use the same folder pattern in data packs as other resources: `namespace/<type>/resource.json`. |
| pre2 | | | | Replaced the field [Boolean] shrunk with [Double] coordinate\_scale. |
| 1.17 | | | 20w49a | | | | Added [Int] min\_y and [Int] height. |
| 1.18.2 | | | 22w06a | | | | infiniburn's defined tag must be preceded by a # symbol; this was previously optional. |
| 1.19 | | | 22w11a | | | | Dimension types can no longer be inlined in the dimension, they have to be a reference to a separate dimension\_type file. |
| pre1 | | | | Added [Int] monster\_spawn\_block\_light\_limit and [Int][NBT Compound / JSON Object] monster\_spawn\_light\_level to the dimension type. |
| 1.21.6 | | | 25w15a | | | | Added [Int] cloud\_height to the dimension type; cloud presence is no longer controlled by [String] effects and their height is no longer a fixed value. |
| 1.21.11 | | | 25w42a | | | | Added new attributes field for dimensions to specify Environment Attributes. |
| Many fields have been migrated to environment attributes:  - [Boolean] ultrawarm -> `minecraft:gameplay/water_evaporates`, `minecraft:gameplay/fast_lava`, `visual/default_dripstone_particle` - [Boolean] bed\_works -> `minecraft:gameplay/bed_rule` - [Boolean] respawn\_anchor\_works -> `minecraft:gameplay/respawn_anchor_works` - [Int] cloud\_height -> `minecraft:visual/cloud_height` - [Boolean] piglin\_safe -> `minecraft:gameplay/piglins_zombify` - [Boolean] has\_raids -> `minecraft:gameplay/can_start_raid` |
| The [Boolean] natural field no longer controls whether Nether portals spawn piglin, which was migrated to the `minecraft:gameplay/nether_portal_spawns_piglin` environment attribute. |
| 25w45a | | | | Added a new optional [String][NBT List / JSON Array] timelines field. |
| The remaining functionality of [Boolean] natural field has been migrated to the `minecraft:gameplay/eyeblossom_open` and `minecraft:gameplay/creaking_active` environment attributes. |
| The [String] effects field has been removed and replaced with the following new fields: [String] skybox and [String] cardinal\_light. |
| The [Int] fixed\_time field has been replaced by [Boolean] has\_fixed\_time. |
| 26.1 | | | snap3 | | | | Added [String] default\_clock field |
| snap6 | | | | Added [Boolean] has\_ender\_dragon\_fight field |
| 26.2 | | | snap1 | | | | The field `infiniburn` now also accepts an ID and a list of IDs in addition to a tag. |

## External links

- [Dimension type Generator on misode.github.io](https://misode.github.io/dimension-type/)

## Navigation
