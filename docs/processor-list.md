# Processor list

> **Source:** <https://minecraft.wiki/w/Processor_list>  
> **Revision:** 3572989 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.

A **processor list** is used to transform blocks of a structure template during generation. They contain a list of processors. Processor lists are configured using JSON files stored within a data pack in the folder `data/<namespace>/worldgen/processor_list` or an add-on in the folder `<root BP>/worldgen/processors`.

## JSON format

A processor list can be a list, or an object that contains a list.
*Java Edition*:
A list:

- [NBT List / JSON Array]: A list of processors.
  - [NBT Compound / JSON Object]: A processor object (see below)

Or an object:

- [NBT Compound / JSON Object]: An object of processor list.
  - [NBT List / JSON Array] processors: A list of processors.
    - [NBT Compound / JSON Object]: A processor object (see below)

*Bedrock Edition*:

- [NBT Compound / JSON Object] The root tag.
  - [String] format\_version: The game version to run the file as. Versions above 1.21.20 can be used.
  - [NBT Compound / JSON Object] minecraft:processor\_list
    - [NBT Compound / JSON Object] description
      - [String] identifier: The identifier used for referencing this processor.
    - [NBT List / JSON Array] processors: A list of processors.
      - [NBT Compound / JSON Object] processor\_type: A processor. The type of processor determines the format of the rest of the processor.

## Processors

### rule

Replaces blocks with custom rules

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:rule`
  - [NBT List / JSON Array] rules: (Required, but can be empty) A list of rules. Only the first rule that all conditions are met takes effect. This is decided anew for each block.
    - [NBT Compound / JSON Object]: A rule.
      - [NBT Compound / JSON Object] position\_predicate: (Optional, defaults to an "always\_true" test) A § Position rule test to apply to the distance from the structure start to this block.
      - [NBT Compound / JSON Object] input\_predicate: A § Rule test to apply to the block placed by the structure.
      - [NBT Compound / JSON Object] location\_predicate: A § Rule test to apply to the block in the world that is replaced by the structure.
      - [NBT Compound / JSON Object] output\_state: The block that is placed when all conditions are met. Omitting block states use default values (e.g. the replacement of stairs with stairs without changing states need 40 rules to check for all facing combinations).
        - Block state — inherited from Template:Nbt inherit/block state/template:

          - [String] Name: The identifier of the block to use.
          - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
            - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
      - [NBT Compound / JSON Object] block\_entity\_modifier: (optional) Modifies nbt data of the block entity if all conditions are met.
        - [String] type: Can be `minecraft:append_loot`(Appends [String] LootTable and [Long] LootTableSeed fields to the block entity. The [Long] LootTableSeed uses a random number seeded by the block position.), `minecraft:append_static`‌[*JE only*](Merges specified data into the block entity.), `minecraft:clear`‌[*JE only*](Resets any existing fields on the block entity.) or `minecraft:passthrough`(Do nothing.).

          If `type` is `minecraft:append_loot`, additional field is as follows:
        - [String] loot\_table: The resource location of a loot table.

          If `type` is `minecraft:append_static`, additional field is as follows:
        - [NBT Compound / JSON Object] data: The nbt data to be merged into the block entity. Needs to be in JSON form, see NBT format#JSON and NBT.

### block\_rot

This feature is exclusive to *Java Edition*.

Randomly removes blocks. The removed blocks are not replaced by air, but keep the old blocks before the structure being generated

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:block_rot`
  - [Float] integrity: The probability of randomly removing blocks in the structure. Value between 0 and 1.
  - [String][NBT List / JSON Array] rottable\_blocks: (optional) Blocks that can be removed. A block ID or a block tag, or a list of block IDs.

### block\_age

This feature is exclusive to *Java Edition*.

Makes blocks aged. A stone, stone bricks, or cracked stone bricks block has a chance of 0.5 to be replaced with one of cracked stone bricks, stone brick stairs, mossy stone bricks, and mossy stone brick stairs. All variants of stairs have a 0.5 chance to become one of stone slab, stone brick slab, mossy stone brick stairs, and mossy stone brick slab. All variants of slabs and walls may remain unchanged or become mossy stone brick variants. Obsidian also has a 0.15 chance to be replaced with crying obsidian.

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:block_age`
  - [Float] mossiness: Values below 0.0 is treated as 0.0; values above 1.0 is treated as 1.0. The probability of using mossy variants when making a block aged.

### block\_ignore

Removes specified blocks. The removed blocks are not replaced by air, but keep the old blocks before the structure being generated.

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:block_ignore`
  - [NBT List / JSON Array] blocks: (Required, but can be empty) IDs of blocks to ignore. Specifying block states has no effect.
    - [NBT Compound / JSON Object]: A block.
      - Block state — inherited from Template:Nbt inherit/block state/template:

        - [String] Name: The identifier of the block to use.
        - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
          - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

### gravity

This feature is exclusive to *Java Edition*.

Change the Y-level of blocks' positions to fit the terrain like a village road. Note that this is not used to make floating gravity blocks fall down. This processor is hardcoded to be used on a structure template if its "projection" field in its template pool is "terrain\_matching"

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:gravity`
  - [String] heightmap: (optional, defaults to WORLD\_SURFACE\_WG) Must be one of `"WORLD_SURFACE_WG"`(if not during world generation, fallbacks to `WORLD_SURFACE`), `"WORLD_SURFACE"`, `"OCEAN_FLOOR_WG"`(if not during world generation, fallbacks to `OCEAN_FLOOR`), `"OCEAN_FLOOR"`, `"MOTION_BLOCKING"`, or `"MOTION_BLOCKING_NO_LEAVES"`.
  - [Int] offset: (optional, defaults to 0) The offset relative to the terrain. For example: 0 is to place the structure on the ground, -1 is to sink one block into the ground. When this processor is used on a structure template by hardcoding (when the template's "projection" field in its template pool is "terrain\_matching"), [Int] offset is -1.

### protected\_blocks

Specifies which blocks in the world cannot be overridden by this structure

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:protected_blocks`
  - [String] value: A block tag with `#`.

### blackstone\_replace

This feature is exclusive to *Java Edition*.

Replaces all stone-variant blocks with blackstone variants and all iron bars with chains.

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:blackstone_replace`

### jigsaw\_replacement

This feature is exclusive to *Java Edition*.

Replaces jigsaw blocks with the specified final state. This processor is hardcoded to be used unless generated in the jigsaw block GUI.

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:jigsaw_replacement`

### lava\_submerged\_block

This feature is exclusive to *Java Edition*.

Blocks with incomplete outline shapes cannot override the lava in the world

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:lava_submerged_block`

### capped

Applies a processor to some random blocks instead of applying it to all blocks.

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:capped`
  - [Int][NBT Compound / JSON Object] value: The number of blocks on which the processor is applied. Must be greater than 0. If it is greater than or equal to the total number of blocks in the structure template, all blocks are processed as if the processor is not capped.
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
  - [NBT Compound / JSON Object] delegate: Another processor object

### nop

This feature is exclusive to *Java Edition*.

Does nothing

- [NBT Compound / JSON Object]: A processor object
  - [String] processor\_type: `minecraft:nop`

## Rule test

Rule tests are used to test if a block matches specific conditions.

### always\_true

Matches any block

- [NBT Compound / JSON Object]: a rule test
  - [String] predicate\_type: `always_true`

### block\_match

Tests is the block is the specified block.

- [NBT Compound / JSON Object]: a rule test
  - [String] predicate\_type: `block_match`
  - [String] block: A block ID.

### blockstate\_match

Tests is the block for the specified block state.

- [NBT Compound / JSON Object]: a rule test
  - [String] predicate\_type: `blockstate_match`
  - [NBT Compound / JSON Object] block\_state: A block state.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.

### random\_block\_match

Tests is the block is the specified block. Then only matches with a given probability.

- [NBT Compound / JSON Object]: a rule test
  - [String] predicate\_type: `random_block_match`
  - [String] block: A block ID.
  - [Float] probability: The probability of the predicate to pass if the block is found. Values below 0.0 is treated as 0.0; values above 1.0 is treated as 1.0.

### random\_blockstate\_match

Tests is the block for the specified block state. Then only matches with a given probability.

- [NBT Compound / JSON Object]: a rule test
  - [String] predicate\_type: `random_blockstate_match`
  - [NBT Compound / JSON Object] block\_state: A block state.
    - Block state — inherited from Template:Nbt inherit/block state/template:

      - [String] Name: The identifier of the block to use.
      - [NBT Compound / JSON Object] Properties: (Optional, can be empty) Block properties. Unspecified properties of the specified block will be set to their default values.
        - [String] <property>: (Optional) Block state property key-value pair. The property must be possessed by the specified block.
  - [Float] probability: The probability of the predicate to pass if the block state is found. Values below 0.0 is treated as 0.0; values above 1.0 is treated as 1.0.

### tag\_match

Tests if the block is in the specified block tag.

- [NBT Compound / JSON Object]: a rule test
  - [String] predicate\_type: `tag_match`
  - [String] tag: A block tag without `#`.

## Position rule test

Position rule tests are used to test if a position matches specific conditions.

### always\_true

Matches any position.

- [NBT Compound / JSON Object]: a position rule test
  - [String] predicate\_type: `always_true`

### linear\_pos

This feature is exclusive to *Java Edition*.

Passes with a random probability, the probability is based on the 3D Manhattan distance to the structure start (center bottom block of first element).

- [NBT Compound / JSON Object]: a position rule test
  - [String] predicate\_type: `linear_pos`
  - [Float] min\_chance: (optional, default is 0.0) The probability (probability less than 0 is treated as 0, greater than 1 is treated as 1) for the predicate to pass when the distance of a block to the structure start is equal to or less than [Int] min\_dist.
  - [Float] max\_chance: (optional, default is 0.0) The probability (probability less than 0 is treated as 0, greater than 1 is treated as 1) for the predicate to pass when the distance of a block to the structure start is equal to or greater than [Int] max\_dist. If a block's distance is between [Int] min\_dist and [Int] max\_dist, probability is obtained by linear interpolation `(distance - min_dist ) / ( max_dist - min_dist ) * ( max_chance - min_chance ) + min_chance`.
  - [Int] min\_dist: (optional, defaults to 0) the distance when the minimum probability is used. Must be less than [Int] max\_dist.
  - [Int] max\_dist: (optional, defaults to 0) the distance when the maximum probability is used. Must be greater than [Int] min\_dist.

### axis\_aligned\_linear\_pos

Passes with a random probability, the probability is based on the distance to the structure start (center bottom block of first element) along the specified axis.

- [NBT Compound / JSON Object]: a position rule test
  - [String] predicate\_type: `axis_aligned_linear_pos`
  - [String] axis: (optional, defaults to `y`) can be `x`, `y` or `z`.
  - [Float] min\_chance: (optional, default is 0.0) The probability (probability less than 0 is treated as 0, greater than 1 is treated as 1) for the predicate to pass when the distance of a block to the structure start is equal to or less than [Int] min\_dist.
  - [Float] max\_chance: (optional, default is 0.0) The probability (probability less than 0 is treated as 0, greater than 1 is treated as 1) for the predicate to pass when the distance of a block to the structure start is equal to or greater than [Int] max\_dist. If a block's distance is between [Int] min\_dist and [Int] max\_dist, probability is obtained by linear interpolation `(distance - min_dist ) / ( max_dist - min_dist ) * ( max_chance - min_chance ) + min_chance`.
  - [Int] min\_dist: (optional, defaults to 0) the distance when the minimum probability is used. Must be less than [Int] max\_dist.
  - [Int] max\_dist: (optional, defaults to 0) the distance when the maximum probability is used. Must be greater than [Int] min\_dist.

## External links

- [Processor list Generator on misode.github.io](https://misode.github.io/worldgen/processor-list/)

## Navigation
