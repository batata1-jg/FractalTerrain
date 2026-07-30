# profile/

## Overview

Two carve stages turn the hydrological-unit index into elevation edits, plus one painter that turns the
result into placed water. Both carve stages are active: the tile-level shell carve runs inside
`LocalRiverProvider.buildTile` (twice — see `hydrology/README.md`), and the per-pixel bed-residual carve
runs from `PopulateNoiseStep`, which calls `HydrologyProfileCarver.carvePrefetched` per column during
chunk fill.

## Architecture

**Shell carve** (`HydrologyProfileCarver.carveRiverShells`, static): per padded-tile pixel, stabs an
R-tree over the units it was handed, keeps candidates whose influence circle contains the pixel, and
overwrites the pixel with `RosgenProfile.riverInfluenceElevation` from the single *nearest* unit — full
pull to the unit's reference elevation out to `floodPlainLength`, released linearly back to the buffer's
current value at `riverInfluence`. Only the nearest unit contributes (no min-composite), so at a
confluence the closest channel's profile wins outright; pixels with negative ambient elevation (ocean)
are skipped. It reads and writes the same buffer, so repeated calls compound — `buildTile` relies on
this to shape the drainage field before the local trace, then to carve local shells in afterward.

**Bed-residual carve** (`HydrologyProfileCarver.carve`/`carveAtPixel`/`carvePrefetched` ->
`HydrologyProfile.computeForUnit`): queries the per-tile unit index for every unit influencing a point
and blends their contributions (nearest-tier average: bed-tier units if any intersect, else
floodplain-tier, else outer-influence-tier — see `carvePrefetched`). Each unit's contribution is its
`RosgenProfile.riverAreaDelta` cross-section delta, faded in over an elliptical footprint around the
unit (`HydrologyProfile.computeForUnit`): full delta on the unit's own cross-section line, decaying to
zero at the floodplain-length ellipse, so the trench tapers along the channel between resample points
rather than stepping abruptly. This cuts the bed trench *below* the shell the first stage already wrote,
consumed per-chunk via `HydrologyProfileCarver.prefetchChunk`/`PrefetchedUnits` so one R-tree query
serves every block of a chunk.

**Painter** (`HydrologyProfilePainter`): reads `Types.RIVER_DIFFERENCE` — the delta the bed-residual
carve wrote — to compute `riverWaterTop` (fills water down to `reliefHeight - diff` where `diff < 0`)
and tests channel membership (`insideChannel`, `pt` within `width/2` of a unit) via the same influence
query used by the carver.

## Design decisions / known limitations

**Bed-trench depth is a hard-coded function of width, not a true cross-section.** `RosgenProfile
.riverAreaDelta` computes the bed depth as `FractalTerrainConfig.GLOBAL_SCALE_CORRECTION *
ChannelGeometry.depthForWidth(width)`, and `ChannelGeometry.depthForWidth` is a pure empirical
width-to-depth law (`depth = max(0.5, (width / DEPTH_WIDTH_SCALE) ^ (1 / DEPTH_WIDTH_EXP))`) with no
dependence on the surrounding terrain, valley shape, or local slope at that point on the channel. Every
Rosgen type's `bedDelta` override reshapes that same width-derived depth across the channel's
cross-section (e.g. `C`'s asymmetric `smoothMax` shelf, `D`'s noise-perturbed braid), but none of them
measure an actual terrain cross-section to carve against — the trench is the same shape and depth
wherever a reach's width is the same, independent of what terrain it cuts through. Treat any visual
"the river doesn't look like it's cutting into this hillside" result as expected behavior of this
design, not a bug in a specific `RosgenProfile` constant.

**Only `RosgenProfile.A` overrides everything a type needs.** `B`, `C`, `D`, `E`, `F`, `G`, `Aa` each
override a subset of `floodPlainLength`/`riverInfluence`/`bedDelta`/`floodPlainDelta`; `DA` overrides
nothing and silently falls back to every enum-level default. A unit with a `null` `rosgenType` (untyped
— e.g. emitted by a `null` `ChannelTyper`) is coalesced to `RosgenType.A` by every consumer
(`HydrologyProfileCarver`, `HydrologyProfile`), not left unhandled.
