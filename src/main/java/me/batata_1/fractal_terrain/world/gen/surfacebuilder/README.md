# surfacebuilder/

## Overview

Replaces vanilla's `SurfaceSystem` so the block capping each column comes from this mod's own heightmaps
rather than from noise-derived density. Two things decide a column: the vanilla surface-rule chain from
`NoiseGeneratorSettings.surfaceRule()`, evaluated through a `SurfaceRules.Context` this class drives
itself, and — where a river claimed the column — the hydrology profile that carved it.

## Architecture

`buildSurface` walks the chunk's 256 columns. For each it computes a sediment depth from the refined
gradient, humidity, erosion and peaks/valleys channels, then walks down from the relief height placing
whatever the rule chain returns.

A river claim adds a second source. `PopulateNoiseStep.fineGrainedPrimitivePass` leaves two parallel
channels behind: `Types.RIVER_TYPE`, the winning primitive's packed family and sub-classification, and
`Types.RIVER_DIST`, that primitive's banded footprint coordinate at this column. Where `RIVER_TYPE` is
not `NONE`, `HydrologicalFeature.profileFor` resolves the tag to a `HydrologyProfile` and
`riverPaintDepth` tabulates a column of `SurfaceMaterial` tokens into a scratch array;
`HydrologySurfacePalette` turns each token into a block state.

Flow: heightmap channels -> profile -> `SurfaceMaterial[]` -> palette -> `BlockColumn.setBlock`, with
the vanilla rule chain underneath every depth the profile did not claim.

## Design decisions

**The profile tabulates a column; it is not asked per block.** The 16x16 column loop is below the
hot/cold line (`ARCHITECTURE.md`), and one virtual call per block would be 256 times the dispatch. One
call per claimed column fills a `SurfaceMaterial[]` allocated once per `buildSurface` and reused across
all 256 columns.

**A claim may extend the column, and defers by material.** `riverPaintDepth` overrides `sedimentDepth`
upward, and any depth whose token is `DEFER` falls through to the vanilla rule. Without the first half a
steep type-A headwater — the case that most wants cobble — gets `sedimentDepth == -1` and zero loop
iterations, so the claim would never be placed. Without the second, a profile could only replace a whole
column, never its lower layers.

**`updateY` still runs on painted blocks.** Skipping it where the material is not `DEFER` would be a
measurable win, but `SurfaceRules.Context` caches per-column state lazily and its tolerance for gaps in
`y` is unverified. Confirm that before optimising it.

**Materials, not blocks, cross the boundary.** Nothing under `hydrology/` imports `net.minecraft`, which
is what lets the golden suite run as plain JUnit. `HydrologySurfacePalette` is the single translation
point, and also where a material with no 1.20.1 counterpart is substituted — silt becomes dirt above the
water line and clay below it. Substituting here rather than dropping the token keeps the profile able to
express a distinction the block list cannot.

## Known limitations

**The fluid height passed to the rule context looks like a double `seaLevel` add.** `buildSurface`
computes `fh = water_heightmap[pos] + seaLevel - relief_height`, but `PopulateNoiseStep` already folds
`seaLevel` into both `Types.WATER_HEIGHT` and `Types.ELEVATION` before this reads them. Undiagnosed;
noticed while tracing the underwater test for the palette. Anything reading `fluid_height` out of the
rule context inherits it.

**Underwater is tested against the river's water surface, not sea level.** `Types.WATER_HEIGHT` holds
zero for any column no river claimed, so the test is only meaningful inside a claim — which is the only
place it is asked.
