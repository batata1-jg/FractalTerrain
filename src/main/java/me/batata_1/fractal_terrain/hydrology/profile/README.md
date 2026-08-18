# profile/

## Overview

Two carve stages turn the hydrological-primitive index into elevation edits, plus one painter that turns the
result into placed water. Both carve stages are active: the tile-level shell carve runs inside
`LocalRiverProvider.buildTile` (twice — see `hydrology/README.md`), and the per-pixel bed carve runs from
`PopulateNoiseStep.fineGrainedPrimitivePass`, which prefetches every primitive in reach once per chunk and
then, per column, blends across all of them by distance rather than resolving a single nearest channel.

## Architecture

**Shell carve** (`HydrologyProfileInprinter.carveRiverShells`, static): per padded-tile pixel, stabs an
R-tree over the primitives it was handed, keeps every candidate whose influence circle contains the pixel, and
overwrites the pixel with the **weighted average** of `river.h(river.d(pixel))` across those primitives,
weighted by `river.w(pixel)` — the same per-Rosgen-type cross-section (`RosgenProfile.delta`) the bed carve
below reads, not `HydrologyProfile.shellElevation` (still defined on the interface, but with no caller
anywhere in `src/main`). There is no nearest-wins rule: at a confluence the overlapping channels blend
rather than one winning outright.

The buffer's current elevation is sampled once per pixel *before* the primitive loop (as `ambient`, gating
the ocean skip below), and the weighted-average accumulation across primitives is a running sum, so the
result does not depend on primitive order. Pixels with negative ambient elevation (ocean) are skipped. It
reads and writes the same buffer, so repeated calls compound — `buildTile` relies on this to shape the
drainage field before the local trace, then to carve local shells in afterward.

**Bed carve** (`PopulateNoiseStep.fineGrainedPrimitivePass`, fed by `HydrologyProfileInprinter.prefetchChunk`):
blends across **every** contributing primitive in one running merge rather than resolving a single nearest
channel. `prefetchChunk` queries the R-tree once per chunk (chunk center plus half-diagonal radius, in the
relief-pixel frame) and sorts the result with `HydrologicalPrimitive.comparator`, which orders by
feature-type ordinal first — every `RiverPrimitive` sorts before any other feature type. The per-column loop
walks that sorted list and `break`s at the first non-`RiverPrimitive` entry; the break only lands at the true
end of the river run because of that sort order, not because the loop tracks position itself.

For each `RiverPrimitive` whose `containsPoint` covers the column, the loop keeps a running `smoothedMinDist`
(seeded at 64) and blends `mergedElevation` toward `min(river.h(river.d(point)), ambientElevation)` with
`weight = smoothStep(-1, 1, (smoothedMinDist - dist) / 0.1)` — a soft "closest primitive wins, nearby
competitors blend in" accumulation driven by iteration order rather than an explicit nearest-channel query.
`river.d(point)` is the signed distance from `point` to the primitive along its stored `normal`;
`river.h(signedDist)` adds `RosgenProfile.delta(...)` to the primitive's base `elevation`. The loop also
computes an `influenceWeight` from `river.w(point)`, but `weight` is `smoothDistWeight * 1`: `influenceWeight`
has no effect on the merge as the loop is currently written.

Only `RiverPrimitive` participates: any other feature type in the prefetched list ends the loop via the
`break` above before it can contribute, so it never reaches `containsPoint`/`h`/`d`. A column no
`RiverPrimitive` covers keeps `mergedElevation == ambientElevation`, so `Types.RIVER_DIFFERENCE` comes out
zero for it.

`prefetchChunk` and the per-column loop run once per chunk and once per column respectively (256 column
calls/chunk, every chunk generated), so both sit below this repo's hot/cold line of abstraction (root
`ARCHITECTURE.md`): no heap allocation, no new abstraction layers, the scratch `mutablePt` reused across
columns rather than allocated per column.

**`ZoneCategory` is currently reserved, not live.** No carve path reads it: `HydrologyProfile.categoryAt`
and `zoneWeight` no longer exist, and the zone-priority merge they drove was replaced by the
distance-weighted blends both carve stages run above. The enum and the `WATERFALL`/`LAKE_BED` reservations referenced from
`WaterfallPrimitive` and `OxbowLakePrimitive` javadoc remain as the intended home for those feature types
once they grow real profiles.

**Painter** (`HydrologyProfilePainter`): reads `Types.RIVER_DIFFERENCE` — the delta the bed carve
wrote — to compute `riverWaterTop` (fills water down to `reliefHeight - diff` where `diff < 0`)
and tests channel membership (`insideChannel`, `pt` within `width/2` of a primitive) via the same influence
query used by the inprinter.

## Design decisions / known limitations

**Bed-trench depth is a hard-coded function of width, not a true cross-section.** `RosgenProfile
.delta` computes the bed depth as `FractalTerrainConfig.GLOBAL_SCALE_CORRECTION *
ChannelGeometry.depthForWidth(width)`, and `ChannelGeometry.depthForWidth` is a pure empirical
width-to-depth law (`depth = max(0.5, (width / DEPTH_WIDTH_SCALE) ^ (1 / DEPTH_WIDTH_EXP))`) with no
dependence on the surrounding terrain, valley shape, or local slope at that point on the channel. Every
Rosgen type's `bedDelta` override reshapes that same width-derived depth across the channel's
cross-section (e.g. `C`'s asymmetric `smoothMax` shelf, `D`'s noise-perturbed braid), but none of them
measure an actual terrain cross-section to carve against — the trench is the same shape and depth
wherever a reach's width is the same, independent of what terrain it cuts through. Treat any visual
"the riverPrimitive doesn't look like it's cutting into this hillside" result as expected behavior of this
design, not a bug in a specific `RosgenProfile` constant.

**Only `RosgenProfile.A` overrides everything a type needs.** `B`, `C`, `D`, `E`, `F`, `G`, `Aa` each
override a subset of `floodPlainLength`/`bedDelta`/`floodPlainDelta`/`valleyDelta`; `DA` overrides
nothing and silently falls back to every enum-level default. A primitive with a `null` `rosgenType` (untyped
— e.g. emitted by a `null` `ChannelTyper`) is coalesced to `RosgenType.A` by `RiverPrimitive.getProfile()`,
the only place `rosgenType` resolves to a profile, not left unhandled.

**The bed carve currently uses a hard `min`, not the blended one.**
`PopulateNoiseStep.fineGrainedPrimitivePass` computes `Math.min(river.h(river.d(point)), ambientElevation)`
directly for each contributing primitive; only the distance-based `weight` blends across primitives, never
`RosgenProfile.blendMin` — the smooth minimum that would round the rim where the valley cone meets untouched
ground. `blendMin` still exists on `RosgenProfile`, but has no caller anywhere in `src/main`; the
`CARVE_BLEND_RANGE` constant it would need is gone entirely, not merely unread. Expect a visible crease at
the carve boundary until `blendMin` is wired back in.
