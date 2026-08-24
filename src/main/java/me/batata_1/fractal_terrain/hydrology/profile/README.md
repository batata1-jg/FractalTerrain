# profile/

## Overview

One function, `HydrologyProfileInprinter.computeRiverGrid`, turns the hydrological-primitive index into
elevation edits for both carve stages, plus one painter that turns the result into placed water. The
tile-level shell carve (`carveRiverInfluence`) runs twice per tile build, once from `GlobalNetworkBuilder
.build` (global-only graph, to shape the drainage field) and once from `LocalNetworkBuilder.build`
(unified graph); the per-chunk bed carve runs from `PopulateNoiseStep.fineGrainedPrimitivePass`, which
prefetches every primitive in reach once per chunk and then calls `computeRiverGrid` over the chunk's
lattice.

## Architecture

**`computeRiverGrid`** merges every river primitive touching a lattice into one `(height, water, weight)`
triple per lattice point in a caller-supplied `float[] acc`, plus the nearest primitive's packed type in a
parallel `long[] typeMask`. It is ambient-free: it never reads the caller's current elevation, only writes
its own merged surface. Both carve stages call it against their own lattice — the shell over the
514x514 padded tile, the bed over the chunk's 16x16 grid — so there is one merge law instead of two, and
the R-tree the shell used to stab once per pixel is gone entirely. For each primitive the loop tabulates a
cross-section lookup table once (`RosgenProfile.sampleCrossSection`, anchored on an integer perp-lattice
index shared by every primitive), then walks only the lattice cells its influence radius can reach,
interpolating the LUT instead of re-evaluating `RosgenProfile.delta`'s branchy per-region logic at every
point.

**`d` is a rectangle scale, not a radius.** Each primitive's footprint is the rotated rectangle the spatial
index stores it under — `influenceLen` along the flow tangent, `influenceWidth` across it. `d` is the factor
that rectangle must be scaled by to contain the lattice point,
`max(|tang| / influenceLen, |perp| / influenceWidth)`, so `d <= 1` is exactly "inside the footprint" and one
comparison serves as both the rank and the in-band mask. It is dimensionless: `UNSET_MIN_DIST` and the
`SMOOTH_STEP_DIVISOR` blend width are read against that normalised scale, not against relief-pixels. Both
half-extents come from `RiverPrimitive.getLength()` / `getWidth()`, so a primitive indexed under a
non-square rectangle carves the shape it was indexed under; today both return `influence * 2`.

**Merge law.** Both stages run the bed's original sequential smoothed-min-distance recurrence: a running
per-lattice-point `dist[i]` seeded at 64, updated per primitive by a smoothstep of
`(dist[i] - d)`, and the `(height, water, weight)` triple
blended toward the current primitive with that same weight — "closest primitive wins, nearby competitors
blend in," driven by primitive order rather than an explicit nearest-channel query. `primitives` MUST
already be sorted by `HydrologicalPrimitive.comparator`, which orders by feature-type ordinal first —
every `RiverPrimitive` sorts before any other feature type. `computeRiverGrid` walks that sorted list and
stops at the first non-`RiverPrimitive` entry, returning that index for a later family pass to resume
from; the stop only lands at the true end of the river run because of the sort order, not because the
loop tracks position itself. The shell previously averaged every primitive whose influence circle reached
a pixel, weighted by `river.w(pixel)`; it now inherits the bed's distance recurrence and loses that
per-primitive footprint weighting — there is still no nearest-wins rule (nearby primitives blend rather
than one winning outright), but the weighting driving that blend is distance alone.

**Cut-only.** `computeRiverGrid`'s output `h` is a pure weighted blend with no ambient clamp folded in.
Each call site recovers its carved elevation as `(1 - w) * ambient + w * min(h, ambient)`, applying the
`min` once against the merged height rather than per contributing primitive. Both stages are therefore
cut-only: neither can raise terrain above ambient. The shell previously could — it overwrote a pixel with
a weighted average that might sit above ambient — but that is no longer possible.

**LUT residual.** The cross-section LUT is anchored on an integer perp-lattice index and interpolated
linearly, so it smears the `RosgenProfile` margin discontinuity (`perpDist <= marginLen -> -10`) across
one lattice cell — one block wide in the bed path, one pixel wide in the shell path. Accepted, not an
oversight; revisit if bed rims read as unexpectedly soft.

**Call sites.** `HydrologyProfileInprinter.carveRiverInfluence` reads and writes its own padded-tile
buffer, skipping pixels with negative ambient elevation (ocean); `GlobalNetworkBuilder.build` calls it once
per tile build to shape the drainage field the local trace reads, and `LocalNetworkBuilder.build` calls it
a second time, on its own separate elevation clone, to produce the carved elevation `RiverProvider`
publishes. `PopulateNoiseStep.fineGrainedPrimitivePass`
carves the bed and, from `computeRiverGrid`'s water lane and type mask, also populates
`Types.WATER_HEIGHT` and `Types.RIVER_TYPE`. It is fed by `HydrologyProfileInprinter.prefetchChunk`, which
queries the R-tree once per chunk (chunk center plus half-diagonal radius, in the relief-pixel frame) and
sorts the result with `HydrologicalPrimitive.comparator` — the ordering `computeRiverGrid` requires.

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

**Both carve stages use a hard `min`, not the blended one.** `computeRiverGrid` merges every contributing
primitive into one height `H` per lattice point with no ambient clamp; each call site then applies
`Math.min(H, ambient)` once, against the merged height, rather than per primitive. Neither stage calls
`RosgenProfile.blendMin` — the smooth minimum that would round the rim where the valley cone meets
untouched ground. `blendMin` still exists on `RosgenProfile`, but has no caller anywhere in `src/main`; the
`CARVE_BLEND_RANGE` constant it would need is gone entirely, not merely unread. Expect a visible crease at
the carve boundary until `blendMin` is wired back in.
