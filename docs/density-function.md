# Density function

> **Source:** <https://minecraft.wiki/w/Density_function>  
> **Revision:** 3678002 · **Retrieved:** 2026-07-28  
> **Target version:** this text describes the *latest* Minecraft release. FractalTerrain targets **Java Edition 1.20.1**, so parts of this page describe behaviour that does not exist in the target version. See the flags below, and treat the page's own **History** section as authoritative.


## ⚠ Post-1.20.1 changes on this page

_6 Java Edition history entries newer than 1.20.1, extracted from this page's History table. Anything described here is **not** in 1.20.1 and the page body may document it as if it were current._

- **1.21.9** — Added density functions `minecraft:find_top_surface` and `minecraft:invert`.
- **26.2** — Added density function `minecraft:interval_select`.
- **26.2** — Removed density function `minecraft:weird_scale_sampler`. Its functionality has been replaced with `interval_select`.
- **26.3** *(unreleased)* — Added the following density functions: - `ceil` - `div` - `floor` - `negate` - `lerp` - `round` - `sub` - `truncate`
- **26.3** *(unreleased)* — `invert` has been renamed to `reciprocal`.
- **26.3** *(unreleased)* — Numerous `argument` fields in density functions have been renamed.

## Inline version markers

_The wiki's own inline `upcoming` / `until` annotations, outside History._

**upcoming** — 33 occurrence(s):

- - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be cached.
- - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be interpolated.
- - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.
- Named `reciprocal`.​[*upcoming: JE 26.3*]
- - [String][Double][NBT Compound / JSON Object] argument1​[*until: JE 26.3*] / [Double] left​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The first input of the calcula
- - [String][Double][NBT Compound / JSON Object] argument2​[*until: JE 26.3*] / [Double] right​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The second input of the calcu
- Performs division between two arguments.​[*upcoming: JE 26.3*]
- Performs subtraction between two arguments.​[*upcoming: JE 26.3*]
- - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The desired density of new chu
- Rounds the input value to positive infinity.​[*upcoming: JE 26.3*]
- - [Double] argument​[*until: JE 26.3*] / [Double] value​[*upcoming: JE 26.3*]: A constant value. Value between −1000000.0 and 1000000.0 (both inclusive).
- Rounds the input value to negative infinity.​[*upcoming: JE 26.3*]
- _…5 more_

---
This feature is exclusive to *Java Edition*.

**Density functions** make up mathematical expressions to obtain a number from a position, stored as JSON files within a data pack in the path `data/<namespace>/worldgen/density_function`. They are referenced from the noise router in noise settings.

## JSON format

A density function can be a constant number or an object.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type.
  - Other additional fields depend on the value of [String] type, described below.

If the [String] type is `constant`, a shorthand format is:

- [Double]: A constant number. Value between −1000000.0 and 1000000.0 (both inclusive).

## Marker functions

### cache\_2d

Only computes the input density once per horizontal position.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `cache_2d`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be cached.

### cache\_all\_in\_cell

Used by the game onto `final_density` and should not be referenced in data packs.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `cache_all_in_cell`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be cached.

### cache\_once

If this density function is referenced twice, it is only computed once per block position.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `cache_once`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be cached.

### flat\_cache

Calculate the value per 4×4 column (Value at each block in one column is the same). And it is calculated only once per column, at Y=0. Used often in combination with `interpolated`.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `flat_cache`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be cached.

### interpolated

Interpolates at each block in one cell based on the input density function value of some cells around. The size of each cell is `size_horizontal * 4` and `size_vertical * 4`. Used often in combination with `flat_cache`.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `interpolated`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input to be interpolated.

## Mapped density functions

### abs

Calculates the absolute value of the input density function.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `abs`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

### cube

Cubes the input (`x^3`).

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `cube`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

### half\_negative

If the input is negative, returns half of the input. Otherwise returns the input. (`x < 0 ? x/2 : x`)

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `half_negative`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

### interval\_select

Selects between a number of density functions based on an input density function and a set of threshold values.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `interval_select`).
  - [Int] input: The value to be compared with any given `thresholds`.
  - [Float] thresholds: The threshold values to compare `input` with.
    - If the input value is less than the threshold values, the result of `functions[i]` will be selected.
    - If the input value is greater than the last threshold value, the last function will be selected.
    - There must be one fewer `thresholds` than `functions`.
  - [NBT List / JSON Array] functions: The resulting functions (at least two) to be selected from.
    - There must be one more element in `functions` than `thresholds`.

### invert

Named `reciprocal`.​[*upcoming: JE 26.3*]
Inverts the input (`1/x`).

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `invert`).
  - [String][Double][NBT Compound / JSON Object] argument: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

### quarter\_negative

If the input is negative, returns a quarter of the input. Otherwise returns the input. (`x < 0 ? x/4 : x`)

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `quarter_negative`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

### square

Squares the input. (`x^2`)

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `square`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

### squeeze

First clamps the input between −1 and 1, then transforms it using x/2 - x\*x\*x/24.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `squeeze`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The input of the calculation.

## Functions with two arguments

### add

Adds two density functions together.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `add`).
  - [String][Double][NBT Compound / JSON Object] argument1​[*until: JE 26.3*] / [Double] left​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The first input of the calculation.
  - [String][Double][NBT Compound / JSON Object] argument2​[*until: JE 26.3*] / [Double] right​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The second input of the calculation.

### div

Performs division between two arguments.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `div`).
  - [String][Double][NBT Compound / JSON Object] left — The left-hand side of the operation.
  - [String][Double][NBT Compound / JSON Object] right — The right-hand side of the operation.

### mul

Multiplies two inputs.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `mul`).
  - [String][Double][NBT Compound / JSON Object] argument1​[*until: JE 26.3*] / [Double] left​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The first input of the calculation.
  - [String][Double][NBT Compound / JSON Object] argument2​[*until: JE 26.3*] / [Double] right​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The second input of the calculation.

### min

Returns the minimum of two inputs.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `min`).
  - [String][Double][NBT Compound / JSON Object] argument1​[*until: JE 26.3*] / [Double] left​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The first input of the calculation.
  - [String][Double][NBT Compound / JSON Object] argument2​[*until: JE 26.3*] / [Double] right​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The second input of the calculation.

### max

Returns the maximum of two inputs.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `max`).
  - [String][Double][NBT Compound / JSON Object] argument1​[*until: JE 26.3*] / [Double] left​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The first input of the calculation.
  - [String][Double][NBT Compound / JSON Object] argument2​[*until: JE 26.3*] / [Double] right​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The second input of the calculation.

### sub

Performs subtraction between two arguments.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `sub`).
  - [String][Double][NBT Compound / JSON Object] left — The left-hand side of the operation.
  - [String][Double][NBT Compound / JSON Object] right — The right-hand side of the operation.

## Other density functions

### beardifier

Adds beards for structures (see the `terrain_adaptation` field in structures). Its value is added to the `final_density` in noise setting by the game. Should not be referenced in data packs.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `beardifier`).

### blend\_alpha

Used in vanilla for smooth transition to chunks generated in old versions.​[*more information needed*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `blend_alpha`).

### blend\_density

Used in vanilla for smooth transition to chunks generated in old versions.​[*more information needed*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `blend_density`).
  - [String][Double][NBT Compound / JSON Object] argument​[*until: JE 26.3*] / [Double] input​[*upcoming: JE 26.3*]: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The desired density of new chunks.

### blend\_offset

Used in vanilla for smooth transition to chunks generated in old versions.​[*more information needed*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `blend_offset`).

### ceil

Rounds the input value to positive infinity.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `ceil`).
  - [Int] input: The input to round.
  - [Int] multiple: The output will be rounded to an integer multiple of this value. If not specified, it defaults to a constant 1.

### clamp

Clamps the input between two values.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `clamp`).
  - [Double][NBT Compound / JSON Object] input: One **density function** (a new [Double][NBT Compound / JSON Object] density function definition, an [String]ID is not allowed here[1]) — The input to clamp.
  - [Double] min: The lower bound. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [Double] max: The upper bound. Value between −1000000.0 and 1000000.0 (both inclusive).

### constant

A constant value.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `constant`).
  - [Double] argument​[*until: JE 26.3*] / [Double] value​[*upcoming: JE 26.3*]: A constant value. Value between −1000000.0 and 1000000.0 (both inclusive).

### end\_islands

Samples at current position using a noise algorithm used for end islands. Its minimum value is −0.84375 and its maximum value is 0.5625.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `end_islands`).

### find\_top\_surface

Scans through a column of an input density and returns the topmost y-level that is above 0. If no such position exists within the bounds, the [Int] lower\_bound is returned.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `find_top_surface`).
  - [String][Double][NBT Compound / JSON Object] density: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The density function to scan.
  - [String][Double][NBT Compound / JSON Object] upper\_bound: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The y-level to start the scan at. Usually a 2D density function.
  - [Int] lower\_bound: The y-level to stop the scan.
  - [Int] cell\_height: The resolution of the scan. E.g. if set to `4`, then only every 4th block is checked.

### floor

Rounds the input value to negative infinity.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `floor`).
  - [Int] input: The input to round.
  - [Int] multiple: The output will be rounded to an integer multiple of this value. If not specified, it defaults to a constant 1.

### lerp

Performs (unclamped) linear interpolation between two arguments based on an alpha.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `lerp`).
  - [Int] alpha: The interpolation factor (e.g. 0 = first, 1 = second). Any value outside of `[0; 1]` will extrapolate.
  - [Int] first: the value at `alpha=0`.
  - [Int] second: the value at `alpha=1`.

### negate

Negates the input.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `negate`).
  - [String] input: The function to negate.

### noise

Samples a noise.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `noise`).
  - [String] noise: One noise (an [String] ID) — The noise to sample.
  - [Double] xz\_scale: Scales the X and Z before sampling.
  - [Double] y\_scale: Scales the Y before sampling.

### old\_blended\_noise

Samples a legacy noise. ​[*more information needed*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `old_blended_noise`).
  - [Double] xz\_scale: Value between 0.001 and 1000.0 (both inclusive).
  - [Double] y\_scale: Value between 0.001 and 1000.0 (both inclusive).
  - [Double] xz\_factor: Value between 0.001 and 1000.0 (both inclusive).
  - [Double] y\_factor: Value between 0.001 and 1000.0 (both inclusive).
  - [Double] smear\_scale\_multiplier: Value between 1.0 and 8.0 (both inclusive).

### range\_choice

Computes the input value, and depending on that result returns one of two other density functions. Basically an if-then-else statement.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `range_choice`).
  - [String][Double][NBT Compound / JSON Object] input: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — The value to compare
  - [Double] min\_inclusive: The lower bound of the range. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [Double] max\_exclusive: The upper bound of the range. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [String][Double][NBT Compound / JSON Object] when\_in\_range: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — Used when the input is inside the range.
  - [String][Double][NBT Compound / JSON Object] when\_out\_of\_range: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — Used when the input is outside the range.

### round

Rounds the input value to to the nearest integer (ties round up).​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `round`).
  - [Int] input: The input to round.
  - [Int] multiple: The output will be rounded to an integer multiple of this value. If not specified, it defaults to a constant 1.

### shift

Samples a noise at `(x/4, y/4, z/4)`, then multiplies it by 4.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `shift`).
  - [String] argument​[*until: JE 26.3*] / [String] noise​[*upcoming: JE 26.3*]: One noise (an [String] ID) — The noise to sample.

### shift\_a

Samples a noise at `(x/4, 0, z/4)`, then multiplies it by 4.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `shift_a`).
  - [String] argument​[*until: JE 26.3*] / [String] noise​[*upcoming: JE 26.3*]: One noise (an [String] ID) — The noise to sample.

### shift\_b

Samples a noise at `(z/4, x/4, 0)`, then multiplies it by 4.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `shift_b`).
  - [String] argument​[*until: JE 26.3*] / [String] noise​[*upcoming: JE 26.3*]: One noise (an [String] ID) — The noise to sample.

### shifted\_noise

Similar to `noise`, but first shifts the input coordinates.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `shifted_noise`).
  - [String] noise: One noise (an [String] ID) — The noise to sample.
  - [Double] xz\_scale: Scales the X and Z before sampling.
  - [Double] y\_scale: Scales the Y before sampling.
  - [String][Double][NBT Compound / JSON Object] shift\_x: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — offset of the position in the X direction.
  - [String][Double][NBT Compound / JSON Object] shift\_y: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — offset of the position in the Y direction.
  - [String][Double][NBT Compound / JSON Object] shift\_z: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — offset of the position in the Z direction.

### spline

Computes a cubic spline.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `spline`).
  - [Float][NBT Compound / JSON Object] spline: The spline. Can be either a number or an object.
    - [String][Double][NBT Compound / JSON Object] coordinate: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — Input determining the location on the spline.
    - [NBT List / JSON Array] points: (Cannot be empty) List of points of the cubic spline.
      - [NBT Compound / JSON Object]: A point of the cubic spline.
        - [Float] location: The location of this point.
        - [Float][NBT Compound / JSON Object] value: The value of this point. Can be either a number or a spline object.
        - [Float] derivative: The slope at this point.

### truncate

Rounds the input value to 0.​[*upcoming: JE 26.3*]

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `truncate`).
  - [Int] input: The input to round.
  - [Int] multiple: The output will be rounded to an integer multiple of this value. If not specified, it defaults to a constant 1.

### y\_clamped\_gradient

Clamps the Y coordinate between `from_y` and `to_y` and then linearly maps it to a range.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `y_clamped_gradient`).
  - [Int] from\_y: The value to be mapped to `from_value`. Value between −4064 and 4062 (both inclusive).
  - [Int] to\_y: The value to be mapped to `to_value`. Value between −4064 and 4062 (both inclusive).
  - [Double] from\_value: The value to map `from_y` to. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [Double] to\_value: The value to map `to_y` to. Value between −1000000.0 and 1000000.0 (both inclusive).

## Removed density functions

This section describes content that has been removed from *Minecraft*.

This feature was present in earlier versions of *Minecraft*, but has since been removed.

### slide

Removed in 22w12a

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `slide`).
  - [String][Double][NBT Compound / JSON Object] argument: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition)

### The legacy "spline"

Removed in 22w11a

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `spline`).
  - [Float][NBT Compound / JSON Object] spline: The spline. Can be either a number or an object.
    - [String][Double][NBT Compound / JSON Object] coordinate: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition) — Input determining the location on the spline.
    - [NBT List / JSON Array] points: (Cannot be empty) List of points of the cubic spline.
      - [Float] location: The location of this point.
      - [Float][NBT Compound / JSON Object] value: The value of this point. Can be either a number or a spline object.
      - [Float] derivative: The slope at this point.
  - [Double] min\_value: The min value of the output. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [Double] max\_value: The max value of the output. Value between −1000000.0 and 1000000.0 (both inclusive).

### terrain\_shaper\_spline

Removed in 22w11a

Calculate the spline from the noise settings.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `terrain_shaper_spline`).
  - [String] spline: Can be `offset`, `factor`, or`jaggedness`.
  - [Double] min\_value: The min value of the output. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [Double] max\_value: The max value of the output. Value between −1000000.0 and 1000000.0 (both inclusive).
  - [String][Double][NBT Compound / JSON Object] continentalness: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition)
  - [String][Double][NBT Compound / JSON Object] erosion: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition)
  - [String][Double][NBT Compound / JSON Object] weirdness: One density function (an [String] ID, or a new [Double][NBT Compound / JSON Object] density function definition)

### weird\_scaled\_sampler

Removed in 26.2-snap5

According to the input value, scales and enhances (or weakens) some regions of the specified noise, and then returns the absolute value.

- [NBT Compound / JSON Object]: Root object.
  - [String] type: The ID of the density function type (in this case, `weird_scaled_sampler`).
  - [String] rarity\_value\_mapper: Can be `type_1`（The minimum scale is 0.75, and the maximum is 2.0）or `type_2`（The minimum scale is 0.5, and the maximum is 3.0.)
  - [String] noise: One noise (an [String] ID) — The noise to sample.
  - [String][Double][NBT Compound / JSON Object] input: The input density function. Can be an ID of a density function, or a density function in the form of a JSON object or a constant number.

## History

| *Java Edition* | | | | | | | |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1.18.2 | | | pre1 | | | | Added density functions: `abs`, `add`, `beardifier`, `blend_alpha`, `blend_density`, `blend_offset`, `cache_2d`, `cache_all_in_cell`, `cache_once`, `clamp`, `constant`, `cube`, `end_islands`, `flat_cache`, `half_negative`, `interpolated`, `max`, `min`, `mul`, `noise`, `old_blended_noise`, `quarter_negative`, `range_choice`, `shift`, `shift_a`, `shift_b`, `shifted_noise`, `slide`, `square`, `squeeze`, `terrain_shaper_spline`, `weird_scaled_sampler`, and `y_clamped_gradient`. |
| pre2 | | | | Added density function `spline`. |
| 1.19 | | | 22w11a | | | | Removed density function `terrain_shaper_spline`. |
| Removed `min_value` and `max_value` fields in `spline`. |
| 22w12a | | | | Removed density function `slide`. Instead a combination of `add`, `mul`, and `y_clamped_gradient` is used to achieve the same result. |
| Added fields to `old_blended_noise` density function: xz\_scale, y\_scale, xz\_factor, y\_factor, and smear\_scale\_multiplier. |
| 1.21.9 | | | 25w31a | | | | Added density functions `minecraft:find_top_surface` and `minecraft:invert`. |
| 26.2 | | | snap5 | | | | Added density function `minecraft:interval_select`. |
| Removed density function `minecraft:weird_scale_sampler`. Its functionality has been replaced with `interval_select`. |
| Upcoming *Java Edition* | | | | | | | |
| 26.3 | | | snap4 | | | | Added the following density functions:  - `ceil` - `div` - `floor` - `negate` - `lerp` - `round` - `sub` - `truncate` |
| `invert` has been renamed to `reciprocal`. |
| Numerous `argument` fields in density functions have been renamed. |

## Issues

Issues relating to "Density function" are maintained on the bug tracker. Issues should be reported and viewed [there](https://bugs.mojang.com/issues/?jql=project%20in%20%28MC%29%20AND%20%28resolution%20is%20EMPTY%20OR%20resolution%20in%20%281%2C%202%2C%206%29%29%20AND%20%28summary%20~%20%22Density%20function%22%29%20ORDER%20BY%20resolution%20DESC).

## References

1. ["[MC-252814] Clamp density function takes a direct input and doesn't allow a reference – Jira"](https://bugs.mojang.com/browse/MC/issues/MC-252814) – Mojira, June 11, 2022.

## External links

- [Density Function Generator on misode.github.io](https://misode.github.io/worldgen/density-function/)

## Navigation
