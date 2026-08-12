# profile/

## Overview

Two carve stages turn the hydrological-primitive index into elevation edits, plus one painter that turns the
result into placed water. Both carve stages are active: the tile-level shell carve runs inside
`LocalRiverProvider.buildTile` (twice — see `hydrology/README.md`), and the per-pixel bed carve runs from
`PopulateNoiseStep`, which per column resolves the nearest channel and calls
`NearestChannelSample.carveInto` during chunk fill.

## Architecture

**Shell carve** (`HydrologyProfileInprinter.carveRiverShells`, static): per padded-tile pixel, stabs an
R-tree over the primitives it was handed, keeps every candidate whose influence circle contains the pixel, and
overwrites the pixel with the **distance-weighted average** of those primitives'
`HydrologyProfile.shellElevation` values, weighted `1 - radialDist/influenceRadius`. There is no
nearest-wins rule: at a confluence the overlapping channels blend rather than one winning outright.

The buffer's current elevation is sampled once per pixel *before* the primitive loop, so every contributing
primitive sees the same `curElev` and the result does not depend on primitive order. Pixels with negative ambient
elevation (ocean) are skipped. It reads and writes the same buffer, so repeated calls compound —
`buildTile` relies on this to shape the drainage field before the local trace, then to carve local
shells in afterward.

**Bed carve** (`HydrologyProfileInprinter.sampleNearestChannel` → `NearestChannelSample.carveInto`):
resolves **one** channel per point rather than merging many. `resolveNearestPrimitiveIndex` picks the
nearest `RiverPrimitive` knot; `sampleNearestChannel` then projects the point onto the two-segment
polyline through that knot and its knot-adjacent neighbours, and reads width, curvature, bed elevation and
Rosgen type **at the foot point on the centreline** rather than at the knot. The result is a single
`NearestChannelSample` — one coherent cross-section instead of several knots' disagreeing tangent-line
distances.

`carveInto` is then `min(ambient, bedElevation + RosgenProfile.delta(...))`. It needs no influence radius:
outside the floodplain the profile is a cone rising away from the channel, so the min returns ambient
wherever that cone clears it. A knot with no knot-adjacent neighbour in range falls back to its own tangent
line (`isolatedKnotSample`), which is the correct answer for an isolated knot, not a degraded one.

Only `RiverPrimitive` participates: `sampleNearestChannel` returns `null` for any other feature type, and
`PopulateNoiseStep` writes a zero river-difference in that case. `prefetchChunk`/`PrefetchedPrimitives`
still amortize the R-tree query so one query serves every block of a chunk.

**`ZoneCategory` is currently reserved, not live.** No carve path reads it: `HydrologyProfile.categoryAt`
and `zoneWeight` no longer exist, and the zone-priority merge they drove was replaced by the
single-nearest-channel carve above. The enum and the `WATERFALL`/`LAKE_BED` reservations referenced from
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
override a subset of `floodPlainLength`/`riverInfluence`/`bedDelta`/`floodPlainDelta`; `DA` overrides
nothing and silently falls back to every enum-level default. A primitive with a `null` `rosgenType` (untyped
— e.g. emitted by a `null` `ChannelTyper`) is coalesced to `RosgenType.A` by every consumer
(`NearestChannelSample.carveInto`, `RiverPrimitive.getProfile`), not left unhandled.

**The bed carve currently uses a hard `min`, not the blended one.** `NearestChannelSample.carveInto`
calls `Math.min` directly; the `RosgenProfile.blendMin(…, CARVE_BLEND_RANGE)` call that rounds the rim
where the valley cone meets untouched ground is commented out beside it, leaving `CARVE_BLEND_RANGE`
unread. Expect a visible crease at the carve boundary until that line is restored.
