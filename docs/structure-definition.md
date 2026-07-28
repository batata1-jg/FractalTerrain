# Structure definition

> **Source:** <https://minecraft.wiki/w/Structure_definition>  
> **Revision:** 3614831 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_8 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.20.3** — Added optional [NBT List / JSON Array] pool\_aliases field to **jigsaw** structures.
- **1.20.3** — Aliases represent the possibility to rewire jigsaw pool connections by redirecting pool references on individual structure instances.
- **1.20.3** — Alias variants are represented in `type`.
- **1.21** — Added optional [Int] dimension\_padding field to **jigsaw** structures.
- **1.21** — [Int][NBT Compound / JSON Object] dimension\_padding can now also be specified separately for [Int] bottom and [Int] top padding.
- **1.21** — Added optional [String] liquid\_settings field to **jigsaw** structures.
- **1.21.9** — [Int][NBT Compound / JSON Object] max\_distance\_from\_center can now also be specified separately for [Int] horizontal and [Int] vertical distance.
- **26.2** — Changed the `minecraft:block_rot` structure processor.

---
Not to be confused with Structure file.

This feature is exclusive to *Java Edition*.

There is a related tutorial page for this topic!

See Tutorial:Custom structures.

A **structure** is a large decoration, covering an area up to 257×8129×257 blocks centered on the structure start. Structures often consist of multiple pieces that are fit together to form the overall structure. They are configured using JSON files within a data pack in the path `data/<namespace>/worldgen/structure`. To generate in a world, a structure needs to be part of at least one structure set.

## JSON format

- [NBT Compound / JSON Object] The root tag.
  - [String] type: The ID of structure feature type.
  - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
  - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
  - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
  - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
    - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
      - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
      - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
        - [NBT Compound / JSON Object]: The spawner data for a single mob.
          - [String] type: The namespaced entity id of the mob.
          - [Int] weight: How often this mob should spawn, higher values produce more spawns.
          - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
          - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - Additional fields depending on value of [String] type, see Structure types.

## Structure types

Structures use different types. The structure types and their corresponding configuration are listed below:

### Using Jigsaw Blocks

Main article: Jigsaw structure

Jigsaw structures are using template pools and jigsaw blocks and allow full customization of structure generation using a datapack.

**jigsaw**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `jigsaw`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - [String][NBT Compound / JSON Object] start\_pool: One template pool (an [String] ID, or a new [NBT Compound / JSON Object] template pool definition) — The template pool the structure starts from.
  - [Int] size: Value between 0 and 20 (inclusive) — The depth of jigsaw structures to generate.
  - [Int][NBT Compound / JSON Object] start\_height: If `project_start_to_heightmap` is unset, the structure will start at the value provided. Otherwise, the value acts as an offset from the heightmap.
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
  - [String] project\_start\_to\_heightmap: (optional) The heightmap the start height should project to. Can be `WORLD_SURFACE_WG`, `WORLD_SURFACE`, `OCEAN_FLOOR_WG`, `OCEAN_FLOOR`, `MOTION_BLOCKING`, or `MOTION_BLOCKING_NO_LEAVES`.
  - [String] start\_jigsaw\_name: (optional) The name of the jigsaw block the structure start attaches to.
  - [Int][NBT Compound / JSON Object] max\_distance\_from\_center: Defines the maximum distance of any piece from the structure start. When defined as a single number, defines both horizontal and vertical distance and is limited to the limits of [Int] horizontal (see below).
    - [Int] horizontal: Value between 1 and 128 (inclusive) when [String] terrain\_adaptation is "none", otherwise from 1 to 116 (inclusive). — The maximum horizontal Chebyshev distance from the jigsaw pieces to the structure start.
    - [Int] vertical: Optional value between 1 and 4064 (defaults to 4064) — The maximum vertical distance of any piece to the structure start.
  - [Boolean] use\_expansion\_hack: Allows the structure's vertical limit to be expanded if necessary. Used in villages to prevent cut-off when terrain height varies.
  - [NBT List / JSON Array] pool\_aliases: (optional) used to rewire jigsaw pool connections by redirecting pool references on individual structure instances.
    - [NBT Compound / JSON Object]: pool alias
  - [Int][NBT Compound / JSON Object] dimension\_padding: (optional, defaults to `0`). Padding on the top and bottom world limit. [Int]: shorthand to set the same value for [Int] bottom and [Int] top.
    - [Int] bottom: (optional, defaults to `0`), non-negative. Amount of blocks at the bottom build limit that are excluded from the outer bounding box of the structure.
    - [Int] top: (optional, defaults to `0`), non-negative. Amount of blocks at the top build limit that are excluded from the outer bounding box of the structure.
  - [String] liquid\_settings: (optional, defaults to `apply_waterlogging`). How blocks with `waterlogged` block state should generate when they overlap with existing water. `apply_waterlogging`: waterlog block placed inside water, `ignore_waterlogging`: keep the `waterlogged` block state as is.

### Using structure templates

These structure types use specific structure templates, but they use hard-coded relative positioning between those structure templates instead of using jigsaw blocks.

**end\_city**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:end_city`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**igloo**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:igloo`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**nether\_fossil**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:nether_fossil`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - [Int][NBT Compound / JSON Object] start\_height: The y-value where the structure starts.
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

**ocean\_ruin**

**ruined\_portal**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:ruined_portal`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - [NBT List / JSON Array] setups: (Cannot be empty) A list of ruined portal setups to randomly choose one from it.
    - [Int] weight: The weight this ruined portal setup is chosen.
    - [String] placement: Either `on_land_surface`, `partly_buried`, `on_ocean_floor`, `in_mountain`, `underground`, `in_nether`. Determines how the ruined portal is placed.
    - [Float] air\_pocket\_probability: The probability that the ruined portal generates an air pocket around it. Value between 0.0 and 1.0 (inclusive).
    - [Float] mossiness: Determines how mossy the ruined portal is, as an argument for `minecraft:block_age` processor. Value between 0.0 and 1.0 (inclusive).
    - [Boolean] overgrown: Determines whether or not jungle leaves generate.
    - [Boolean] vines: Determines whether or not vines generate on the ruined portal.
    - [Boolean] can\_be\_cold: Determines whether or not lava and magma can be replaced with netherrack.
    - [Boolean] replace\_with\_blackstone: Determines whether or not stone bricks in the ruined portal are replaced with their blackstone equivalents.

**shipwreck**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:shipwreck`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - [Boolean] is\_beached: (optional, defaults to false) Whether or not the shipwreck is beached.

**woodland\_mansion**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:woodland_mansion`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

### Hardcoded structures

These structure types use code to place the blocks of the structure directly.

**buried\_treasure**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:buried_treasure`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**desert\_pyramid**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:desert_pyramid`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**fortress**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:fortress`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**jungle\_temple**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:jungle_temple`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**mineshaft**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:mineshaft`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.
  - [String] mineshaft\_type: Either `normal` or `mesa`. `normal` for mineshaft made of oak, while `mesa` for mineshaft made of dark oak.

**ocean\_monument**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:ocean_monument`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**stronghold**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:stronghold`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

**swamp\_hut**

- [NBT Compound / JSON Object] Structure configuration
  - [String] type: `minecraft:swamp_hut`
  - Fields common to all structures — inherited from Template:Nbt inherit/structure/template:

    - [String][NBT List / JSON Array] biomes: Any number of biome(s) (an [String] ID, or a [String] tag with `#`, or an [NBT List / JSON Array] array containing [String] IDs) — Biomes that this structure is allowed to generate in.
    - [String] step: The step where the structure generates. See also the `features` field in custom biome. Structure features are generated prior to features in the same step. One of `raw_generation`, `lakes`, `local_modifications`, `underground_structures`, `surface_structures`, `strongholds`, `underground_ores`, `underground_decoration`, `fluid_springs`, `vegetal_decoration`, and `top_layer_modification`.
    - [String] terrain\_adaptation: (Optional, defaults to `none`) The type of terrain adaptation used for the structure. `none` for no adaptation, `beard_thin` for generating terrain under the structure, while removing terrain inside the structure (used by pillager outposts and villages), `beard_box` for advanced alternative of `beard_thin` (used by ancient cities), `bury` for generating terrain surrounding the structure to make it buried (used by strongholds), and `encapsulate` for advanced alternative of `bury` (used by Trial Chambers).
    - [NBT Compound / JSON Object] spawn\_overrides: (Required, but can be empty. If this object doesn't contain a certain category, the category's spawn setting won't be overridden, and mobs are spawned based on biome.) Overrides the mobs that can spawn in this structure. Used for things like blaze and wither skeleton spawning in nether fortresses, and can also be used to block mobs from spawning like in ancient cities.
      - [NBT Compound / JSON Object] <mob category>: The key must be one of `monster`, `creature`, `ambient`, `water_creature`, `underground_water_creature`, `water_ambient`, `misc`, or `axolotls`.
        - [String] bounding\_box：Can be `piece` or `full`. If `full`, overrides spawn setting inside the full bounding box of the structure. If `piece`, only the bounding boxes of all structure pieces.
        - [NBT List / JSON Array] spawns：(Required, but can be empty. If empty, mobs in this category do not spawn.) A list of spawner data objects, one for each mob which should spawn in this structure.
          - [NBT Compound / JSON Object]: The spawner data for a single mob.
            - [String] type: The namespaced entity id of the mob.
            - [Int] weight: How often this mob should spawn, higher values produce more spawns.
            - [Int] minCount: The minimum count of mobs to spawn in a pack. Must be greater than 0.
            - [Int] maxCount: The maximum count of mobs to spawn in a pack. Must be greater than 0. And must be not less than [Int] minCount.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.19 | | | 22w11a | | | | The structure system was rewritten. |
| Renamed "configured structure feature" registry to "structure". |
| Moved `worldgen/configured_structure_feature` to `worldgen/structure` folder. |
| 22w12a | | | | Added [Int] max\_distance\_from\_center field to **jigsaw** structures. |
| 22w13a | | | | Added `rottable_blocks` field into `minecraft:block_rot` processor. |
| Now the `integrity` field in `minecraft:block_rot` processor becomes a required field, and now it must be between 0.0 and 1.0 (inclusive). |
| Replaced the [Boolean] adapt\_noise field in configured structure feature with [String] terrain\_adaptation field. Before, it is an optional boolean value that defaults to false. |
| All fields except `type` of jigsaw structure are now wrapped in a `value` field. |
| 22w14a | | | | Reverted the change on jigsaw structure from last snapshot. |
| Now the `rottable_blocks` field in `minecraft:block_rot` processor also accepts a block ID or a list of block IDs, and the block tag now needs to be prefixed with a `#`. |
| 22w17a | | | | Added [String] start\_jigsaw\_name field to **jigsaw** structures. |
| 1.19.3 | | | 22w44a | | | | Removed the [String] name field in structure pool. Before, this field was a required field, but had no effect. |
| 1.19.4 | | | Pre-release 1 | | | | Now [Int] minCount and [Int] maxCount in [NBT Compound / JSON Object] spawners in configured structure feature must be a positive integer. And [Int] maxCount must be not less than [Int] minCount. |
| 1.20 | | | 23w12a | | | | Added `capped` processor. |
| The [NBT Compound / JSON Object] output\_nbt field in the `rule` processor used to set fixed NBT data to the block entity has now been changed to [NBT Compound / JSON Object] block\_entity\_modifier. Before, [NBT Compound / JSON Object] output\_nbt was an NBT compound in JSON object form, see NBT format#JSON and NBT. |
| 1.20.3 | | | 23w42a | | | | Added optional [NBT List / JSON Array] pool\_aliases field to **jigsaw** structures. |
| Aliases represent the possibility to rewire jigsaw pool connections by redirecting pool references on individual structure instances. |
| Alias variants are represented in `type`. |
| 1.21 | | | 24w19a | | | | Added optional [Int] dimension\_padding field to **jigsaw** structures. |
| 24w20a | | | | [Int][NBT Compound / JSON Object] dimension\_padding can now also be specified separately for [Int] bottom and [Int] top padding. |
| pre1 | | | | Added optional [String] liquid\_settings field to **jigsaw** structures. |
| 1.21.9 | | | 25w31a | | | | [Int][NBT Compound / JSON Object] max\_distance\_from\_center can now also be specified separately for [Int] horizontal and [Int] vertical distance. |
| 26.2 | | | snap6 | | | | Changed the `minecraft:block_rot` structure processor. |

## External links

- [Structure Generator on misode.github.io](https://misode.github.io/worldgen/structure/)

## Navigation
