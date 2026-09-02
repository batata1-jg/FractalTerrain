# Hydrology surface painting: primitives choose the blocks their carve exposes

Date: 2026-09-02
Status: proposed — nothing here has been implemented
Branch: `feature/hydrology`
Measured at: `df7ca2e`, with a dirty working tree (docs-only modifications)

## Problem

`RosgenProfile` decides what a river *does to elevation*. Nothing decides what a river *is made of*. A
type-A headwater trench and a type-C lowland meander cut different cross-sections into the terrain and
then get identical grass placed on them, because `FractalTerrainSurfaceSystem.buildSurface` evaluates
only the vanilla `SurfaceRules` chain from `NoiseGeneratorSettings.surfaceRule()`, which knows nothing
about hydrology.

Three pieces of the mechanism this needs already exist and are unused:

- `HydrologyProfilePainter` is documented as "the block/biome/vegetation side of the hydrology profile —
  the painting twin of `HydrologyProfileInprinter`". Its `riverWaterTop` has no caller anywhere in
  `src/main`; `insideChannel` is called only from `debug/Infinite3DVisualizer`.
- `Types.RIVER_TYPE` is a `long[256]` per chunk, written every chunk by
  `PopulateNoiseStep.fineGrainedPrimitivePass` from the carve's `typeMask` — the winning primitive's
  family ordinal in the high word, its sub-classification (the Rosgen ordinal, for rivers) in the low.
  Nothing reads it.
- `ZoneCategory` enumerates BED / FLOODPLAIN / INFLUENCE in priority order and is reserved but dead:
  `HydrologyProfile` carries no `categoryAt` counterpart.

What is missing is the per-block *position within the cross-section*. The carve computes it —
`dist[i]`, the footprint scale of the winning primitive — and discards it when
`fineGrainedPrimitivePass` returns.

## Decisions

**D1. `d` becomes a banded coordinate with fixed breakpoints.** `RiverInfluenceCarve.carvePrimitive`
remaps its footprint scale through three linear pieces so that `0.25` is the bed/floodplain boundary and
`0.5` is the floodplain/influence boundary, for every primitive regardless of its width. A consumer then
classifies a point with two comparisons against constants and needs no access to the primitive.

**D2. The banded value is remapped before the merge, not after.** `dist[i]` feeds the smoothed-min
recurrence, so banding changes which primitive wins where — a small tributary's bed and floodplain now
outrank a large trunk's influence band. This is the intent, not a side effect: bed and floodplain are
the zones that should assert themselves. It also changes carved elevation, because the same `dist[i]`
drives the `acc[]` blend weight and `acc[a + 2] = 1 - clamp(dist, 0, 1)`.

**D3. The control points come through the same `max` the coordinate does.** `marginLen` and
`floodPlainLen` are substituted into `carvePrimitive`'s own expression rather than being applied to the
perpendicular axis alone. The along-flow consequence is an isotropic end-cap of the bed's own radius
(`marginLen`) past a channel's last primitive, which is the geometrically correct way for a segment to
end. `carvePrimitiveInfluence` already contains the two-piece form of this remap (`floodPlainNormLen`
and `dd`), so D1 generalizes an existing pattern rather than introducing one.

**D4. Hydrology stays Minecraft-free.** No file under `hydrology/` imports `net.minecraft` today, which
is what lets the golden suite run as plain JUnit. The profile answers with a `SurfaceMaterial` token; a
table under `world/gen/surfacebuilder/` maps token to `BlockState`.

**D5. The profile tabulates a column, it does not answer per block.** `buildSurface`'s 16x16 loop is
below the hot/cold line (`ARCHITECTURE.md`, "Hot sites in this repo"), and `performance.md` forbids new
per-iteration virtual dispatch. One virtual call per claimed column fills a reused
`SurfaceMaterial[]` scratch buffer; the inner loop reads that array. This mirrors
`RosgenProfile.sampleCrossSection`, which tabulates a cross-section LUT once per primitive for exactly
this reason.

**D6. A claim may extend the column, and defers by material.** `riverPaintDepth` overrides
`sedimentLayerDepth` upward, and any depth whose tabulated material is `DEFER` falls through to the
vanilla rule. Without the first half, a steep type-A headwater — the case that most wants cobble — gets
`sedimentDepth == -1` and zero loop iterations.

## Method

### 1. Band the carve coordinate

`RiverInfluenceCarve.carvePrimitive`, in the block already hoisting `marginLen`, `floodPlainLen`,
`invLen` and `invWidth` above the lattice loop:

```java
final double marginNorm = Math.max(marginLen * invLen, marginLen * invWidth);
final double floodPlainNorm = Math.max(floodPlainLen * invLen, floodPlainLen * invWidth);
```

Inside the loop, the raw scale feeds a three-piece remap before anything reads it:

```java
final double raw = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
final double d = band(raw, marginNorm, floodPlainNorm);
```

`band` is a private static helper on `RiverInfluenceCarve`, with the reciprocals hoisted alongside the
control points so the loop body carries no division:

| Input range                        | Output range   | Meaning     |
| ---------------------------------- | -------------- | ----------- |
| `[0, marginNorm]`                  | `[0, 0.25]`    | bed         |
| `[marginNorm, floodPlainNorm]`     | `[0.25, 0.5]`  | floodplain  |
| `[floodPlainNorm, 1]`              | `[0.5, 1]`     | influence   |

`d = 1` still means the influence rim, so the `mask = d <= 1.0` in-band test and `UNSET_MIN_DIST = 64`
seed keep their meaning.

`carvePrimitiveInfluence` keeps its existing two-piece `dd`. Unifying the shell onto the same helper
would move shell terrain and bed terrain in one change, leaving a regression unattributable; it is
named in "Out of scope" as a follow-up.

The bed/floodplain breakpoints are named constants on `RiverInfluenceCarve`, not literals, since
`HydrologyProfile` implementations compare against them.

### 2. Publish the banded distance

New channel in `FractalTerrainHeightmap.Types`, alongside the existing `RIVER_TYPE`:

```java
RIVER_DIST(pos -> new float[1 << 8]),
```

Filled in `fineGrainedPrimitivePass`'s existing per-column loop, next to `riverType[pos] =
buffers.typeMask[pos]`:

```java
riverDist[pos] = buffers.dist[pos];
```

`RIVER_TYPE == HydrologicalFeature.NONE` remains the gate. Where nothing claimed a cell, `dist` still
holds `UNSET_MIN_DIST` and must not be read.

### 3. Hydrology-side contract

`hydrology/profile/SurfaceMaterial.java` — Minecraft-free, one constant per material a profile can ask
for, plus `DEFER`:

```java
public enum SurfaceMaterial {
    DEFER, GRAVEL, COBBLE, SAND, SILT, CLAY, MUD
}
```

`hydrology/profile/HydrologyProfile.java` gains one default, sitting beside `shellElevation`:

```java
/** Materials this profile paints down the column, tabulated into {@code out}; returns how many
 *  entries it filled. Zero leaves the column entirely to the vanilla surface rules. */
default int riverPaintDepth(int subType, float dist, SurfaceMaterial[] out) {
    return 0;
}
```

`subType` is `HydrologicalFeature.unpackSub(tag)` — the Rosgen ordinal for rivers. `dist` is the banded
coordinate. `out` is caller-owned and reused; implementations write `out[0]` (the surface block)
downward and must not retain it.

`DefaultProfile` inherits the zero, so an unimplemented feature type stays as invisible to the surface
as it already is to the carve. `RosgenProfile` overrides per constant, in the same style as
`bedDelta` / `floodPlainDelta`.

`HydrologicalFeature` gains the tag-to-profile resolver, since the surface path holds a packed tag and
never a primitive instance:

```java
public HydrologyProfile profileFor(int sub) {
    return DefaultProfile.INSTANCE;
}
```

`RIVER` overrides it to `RosgenProfile.of(RosgenType.values()[sub])`, matching how `prototype()` and
`addPrimitives` are already specialized per constant.

### 4. World-side palette

`world/gen/surfacebuilder/HydrologySurfacePalette.java` — the only place `SurfaceMaterial` meets
Minecraft:

```java
static @Nullable BlockState of(SurfaceMaterial material, boolean underwater)
```

`DEFER` returns `null`. The `underwater` variant exists so a bank material can differ above and below
the river's water surface without the profile needing a Y coordinate.

### 5. The surface loop

`FractalTerrainSurfaceSystem.buildSurface` gains a per-column preamble and one branch inside the depth
loop. The scratch array is allocated once per `buildSurface` call — once per chunk, amortized over 256
columns — not per column:

```java
final SurfaceMaterial[] paint = new SurfaceMaterial[HydrologyTuning.MAX_RIVER_PAINT_DEPTH];
...
final int pos = 16 * dx + dz;
final long tag = riverType[pos];
int riverPaintDepth = 0;
if (tag != HydrologicalFeature.NONE) {
    final int sub = HydrologicalFeature.unpackSub(tag);
    riverPaintDepth = HydrologicalFeature.unpack(tag)
            .profileFor(sub)
            .riverPaintDepth(sub, riverDist[pos], paint);
}
final float waterY = water_heightmap[pos];
final int lastDepth = Math.max(sedimentLayerDepth, riverPaintDepth - 1);

for (int d = 0; d <= lastDepth; d++) {
    final int y = relief_height - d;
    ...
    materialRuleContext.updateY(stoneDepthAbove, stoneDepthBellow, fluid_height, x, y, z);
    BlockState newBlockState = null;
    if (d < riverPaintDepth) {
        newBlockState = HydrologySurfacePalette.of(paint[d], y < waterY);
    }
    if (newBlockState == null) newBlockState = blockStateRule.tryApply(x, y, z);
    if (newBlockState != null) blockColumn.setBlock(y, newBlockState);
}
```

`MAX_RIVER_PAINT_DEPTH` is a new `HydrologyTuning` constant and is the contract bound on
`riverPaintDepth`'s return value; a profile returning more is a programming error, not a runtime
condition to handle.

`updateY` still runs on painted blocks. Skipping it when the material is not `DEFER` would let painted
blocks bypass the vanilla rule chain entirely, but `SurfaceRules.Context` caches per-column state
lazily and tolerating gaps in `y` is unverified; it is named in "Out of scope".

## Verification

The suite has broken and been repaired several times, so the baseline below is a claim to re-measure,
not a fact.

1. **Baseline before touching anything.** Build a worktree at `df7ca2e`, copy
   `libs/onnxruntime/teste.jar` into it (`libs/` is git-ignored; without it the build reports ~132
   phantom errors), run `gradle test`, and diff `build/test-results/test/*.xml` against
   `.superpowers/conventions-alignment/post-migration-failures.txt`. The recorded state is 102 tests,
   9 failed, 1 skipped, of which three are known-wrong expectations rather than defects.

2. **New tests**, all Minecraft-free and therefore runnable in the existing JUnit suite:
   - `band` maps `0`, `marginNorm`, `floodPlainNorm` and `1` to exactly `0`, `0.25`, `0.5` and `1`.
   - `band` is monotonic across a swept input range and never exceeds `1` for inputs at the rim.
   - `band` is well-defined when `marginNorm == floodPlainNorm` (a profile whose `floodPlainLength`
     returns `width / 2`, which is the `HydrologyProfile` default) — no division by zero.
   - `riverPaintDepth` returns at most `MAX_RIVER_PAINT_DEPTH` for every `RosgenProfile` constant across
     the banded range, parameterized over the enum rather than one test per type.

3. **Golden re-baseline.** `MeandersGoldenTest` and `RiverGoldenTest` compare against stored output and
   will move, because D2 changes carved elevation by design. Re-baseline them in the same commit as the
   carve change, with the new expectations generated from the banded carve, and record in the commit
   message that the diff is D2 and not a regression.

4. `gradle spotlessApply`, then `gradle build`.

## Files

| File                                                       | Change                                                        |
| ---------------------------------------------------------- | ------------------------------------------------------------- |
| `hydrology/profile/RiverInfluenceCarve.java`               | `band` helper, control points, banded `d` in `carvePrimitive` |
| `hydrology/profile/SurfaceMaterial.java`                   | new enum                                                      |
| `hydrology/profile/HydrologyProfile.java`                  | `riverPaintDepth` default                                     |
| `hydrology/profile/RosgenProfile.java`                     | `riverPaintDepth` override per Rosgen constant                |
| `hydrology/features/HydrologicalPrimitive.java`            | `HydrologicalFeature.profileFor`                              |
| `config/HydrologyTuning.java`                              | `MAX_RIVER_PAINT_DEPTH`                                       |
| `storage/FractalTerrainHeightmap.java`                     | `Types.RIVER_DIST`                                            |
| `world/gen/populatenoise/PopulateNoiseStep.java`           | publish `buffers.dist` into `RIVER_DIST`                      |
| `world/gen/surfacebuilder/HydrologySurfacePalette.java`    | new token-to-`BlockState` table                               |
| `world/gen/surfacebuilder/FractalTerrainSurfaceSystem.java`| column preamble and paint branch in `buildSurface`            |
| `hydrology/profile/README.md`                              | the banded coordinate, D2's terrain consequence, D3's end-cap |
| `world/gen/surfacebuilder/CLAUDE.md`                       | index entry for the palette                                   |
| `ARCHITECTURE.md`                                          | `buildSurface`'s column loop named as a hot site              |

## Out of scope

- **Water placement.** `HydrologyProfilePainter.riverWaterTop` stays uncalled; water continues to come
  from `fluid_height` in the rule context.
- **Biome parameters and the vegetation PDF**, both named as future work in the painter's javadoc.
- **Unifying `carvePrimitiveInfluence` onto the three-piece band** (D3). A separate change, so shell
  and bed terrain do not move together.
- **Skipping `updateY` for painted blocks.** A measurable win, gated on confirming `SurfaceRules.Context`
  tolerates gaps in `y`.
- **`ZoneCategory`.** The banded coordinate makes the zones comparable numerically, so nothing in this
  change needs the enum. It stays reserved.
- **`fh = water_heightmap[pos] + seaLevel - relief_height`** in the existing loop appears to add
  `seaLevel` a second time, since `PopulateNoiseStep` already folds it into both `relief_height` and
  `waterElev`. Noticed while tracing `underwater`; not diagnosed and not touched here.

## Risks

**Terrain moves everywhere rivers reach.** D2 is the whole point of the change and also its largest
risk: the floodplain edge weights `0.5` where it previously weighted near zero, so the carve reaches
further and blends differently. If the result is unacceptable, the fix is to retune the breakpoints —
they are constants — not to abandon the banding, since the paint side needs a width-independent
coordinate either way.

**The band is computed per lattice point on a hot path.** Three comparisons and a multiply-add replace
one `max`. Every reciprocal is hoisted per primitive, so the loop body gains no division.

**`RIVER_DIST` costs one `float[256]` per cached chunk heightmap** — about 1 KB, against the existing
`long[256]` for `RIVER_TYPE`.

**A `SurfaceMaterial` with no plausible 1.20.1 block** (`SILT` has no vanilla counterpart) forces the
palette into a substitution. Choose the substitute in the palette, where it is visible, rather than
dropping the token and losing the profile's ability to express the distinction.
