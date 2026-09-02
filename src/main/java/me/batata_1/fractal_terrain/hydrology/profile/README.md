# profile/

## Overview

One function, `RiverInfluenceCarve.computeRiverGrid`, turns the hydrological-primitive index into
elevation edits for every carve in the mod, plus one painter that turns the result into placed water.

The tile-level shell carve (`carveRiverInfluence`, which wraps `computeRiverGrid` over the 514x514 padded
tile) runs **three** times per tile build, on three different elevation buffers:

1. `GlobalNetworkBuilder.build` — global-only graph, into its own clone. Discarded except through the
   drainage field computed over it.
2. `LocalNetworkBuilder.build` — unified graph, into its own clone. Also discarded: this clone exists only
   so `LocalDrainageTracer` walks a carved surface.
3. `RiverProvider.carveRivers` — unified-and-meandered graph, into a third fresh clone of the raw decoded
   elevation. This is the only one whose written values survive: it is what `RiverProvider` crops and
   publishes as `hydrology_relief`.

The bed carve is per-chunk only, with no tile-level counterpart. It runs from
`PopulateNoiseStep.fineGrainedPrimitivePass`, which prefetches every primitive in reach once per chunk
(via `HydrologyProfileInprinter.prefetchChunk`) and then calls `computeRiverGrid` over the chunk's 16x16
lattice. A tile's published elevation therefore carries the valley shell but not the trench; the trench is
cut at chunk-fill time, against the shell the tile already published.

## Architecture

**`computeRiverGrid`** merges every river primitive touching a lattice into one `(height, water, weight)`
triple per lattice point in a caller-supplied `float[] acc`, plus the nearest primitive's packed type in a
parallel `long[] typeMask`. It is ambient-free: it never reads the caller's current elevation, only writes
its own merged surface. Both the shell and the bed call it against their own lattice — the shell over the
514x514 padded tile, the bed over the chunk's 16x16 grid — so there is one merge law, not one per lattice.
Neither path stabs the R-tree per pixel; the tree is queried once per chunk and not at all per tile. For
each primitive the loop tabulates a
cross-section lookup table once (`RosgenProfile.sampleCrossSection`, anchored on an integer perp-lattice
index shared by every primitive), then walks only the lattice cells its influence radius can reach,
interpolating the LUT instead of re-evaluating `RosgenProfile.delta`'s branchy per-region logic at every
point.

**`d` is a banded rectangle scale, not a radius.** Each primitive's footprint is the rotated rectangle
the spatial index stores it under — `influenceLen` along the flow tangent, `influenceWidth` across it.
The raw scale is the factor that rectangle must be scaled by to contain the lattice point,
`max(|tang| / influenceLen, |perp| / influenceWidth)`, so `raw <= 1` is exactly "inside the footprint"
and is what the in-band mask tests. `carvePrimitive` then remaps that raw scale through
`RiverInfluenceCarve.band`, three linear pieces pinned so `BED_EDGE` (0.25) always lands on the bank and
`FLOODPLAIN_EDGE` (0.5) always lands on the floodplain edge, whatever the primitive's width. `d` is
dimensionless: `UNSET_MIN_DIST` and the blend width are read against that banded scale, not against
relief-pixels. Both half-extents come from `RiverPrimitive.getLength()` / `getWidth()`, so a primitive
indexed under a non-square rectangle carves the shape it was indexed under.

The banding is load-bearing in two directions. It gives the paint side a width-independent coordinate: a
consumer classifies a point into bed / floodplain / influence with two comparisons against constants and
no access to the primitive. And because the banded value feeds the smoothed-min recurrence, it changes
which primitive wins where — a small tributary's bed and floodplain outrank a large trunk's influence
band — and the same value drives the `acc[]` blend weight through
`acc[a + 2] = 1 - clamp(dist, 0, 1)`, so carved elevation moves too: a floodplain edge that weighted
near 1 weights 0.5. Retune `BED_EDGE` and `FLOODPLAIN_EDGE` if the reach is wrong; the paint side needs
a width-independent coordinate either way.

The control points are clamped into `[0, 1]` and into order before the slopes are taken.
`RosgenProfile.floodPlainLength` is a free per-type law: `E` returns less than `marginLen` at maximum
width, a minimum-influence primitive can push its floodplain past its own rim, and the
`HydrologyProfile` default (which `DA` inherits) returns exactly `marginLen`. Each inversion would
otherwise give the band a negative slope or a division by zero.

`marginLen` and `floodPlainLen` are substituted into the same `max` the raw scale itself comes out of,
not applied to the perpendicular axis alone. So the bed band extends along the flow tangent as well as
across it, and a channel's last primitive ends in an isotropic cap of the bed's own radius rather than
being cut off square — which is what a segment ending should look like. The alternative, banding only
the perpendicular axis, would leave the along-flow coordinate unbanded and break the paint side's
width-independence at every channel end.

`carvePrimitiveInfluence`, the tile-level shell carve, keeps its own two-piece `dd` remap and is not
banded. Unifying the two would move shell terrain and bed terrain together, leaving any regression
unattributable.

**Merge law.** Both lattices run the bed's original sequential smoothed-min-distance recurrence: a running
per-lattice-point `dist[i]` seeded at 64, updated per primitive by a smoothstep of
`(dist[i] - d)`, and the `(height, water, weight)` triple
blended toward the current primitive with that same weight — "closest primitive wins, nearby competitors
blend in," driven by primitive order rather than an explicit nearest-channel query. `primitives` MUST
already be sorted by `HydrologicalPrimitive.comparator`, which orders by feature-type ordinal first —
every `RiverPrimitive` sorts before any other feature type. `computeRiverGrid` walks that sorted list and
stops at the first non-`RiverPrimitive` entry, returning that index for a later family pass to resume
from; the stop only lands at the true end of the river run because of the sort order, not because the
loop tracks position itself. The blend carries no per-primitive footprint weighting — distance alone
drives it — and there is no nearest-wins rule: near-equidistant competitors blend rather than one
winning outright.

**Cut-only.** `computeRiverGrid`'s output `h` is a pure weighted blend with no ambient clamp folded in.
Each call site recovers its carved elevation as `(1 - w) * ambient + w * min(h, ambient)`, applying the
`min` once against the merged height rather than per contributing primitive. Both the shell and the bed
are therefore cut-only: neither can raise terrain above ambient.

**LUT residual.** The cross-section LUT is anchored on an integer perp-lattice index and interpolated
linearly, so it smears the `RosgenProfile` margin discontinuity (`perpDist <= marginLen -> -10`) across
one lattice cell — one block wide in the bed path, one pixel wide in the shell path. Accepted, not an
oversight; revisit if bed rims read as unexpectedly soft.

**Call sites.** `RiverInfluenceCarve.carveRiverInfluence` reads and writes a caller-supplied padded-tile
buffer, skipping pixels with negative ambient elevation (ocean). The three tile-level callers are listed in
the Overview; each hands it a different buffer, and only `RiverProvider.carveRivers`' survives.
`PopulateNoiseStep.fineGrainedPrimitivePass` carves the bed and, from `computeRiverGrid`'s water lane and
type mask, also populates `Types.WATER_HEIGHT` and `Types.RIVER_TYPE`. It is fed by
`HydrologyProfileInprinter.prefetchChunk`, which queries the R-tree once per chunk (chunk center plus
half-diagonal radius, in the relief-pixel frame) and sorts the result with
`HydrologicalPrimitive.comparator` — the ordering `computeRiverGrid` requires.

**Why the carve math is static and the prefetch is not.** `HydrologyProfileInprinter` binds a
`RiverProvider` to answer `prefetchChunk`; `RiverInfluenceCarve` holds no instance state at all. They are
separate classes because merging them would put a `hydrology.providers` field on the class the carve lives
in, and `providers` already depends on the carve — a package cycle. Keep new carve math in
`RiverInfluenceCarve` and new provider-bound queries in the inprinter.

**`ZoneCategory` is reserved, not live.** No carve path reads it: `HydrologyProfile` carries no
`categoryAt`/`zoneWeight` counterpart, and the distance-weighted blend `computeRiverGrid` runs above is
the only merge rule. The enum and the `WATERFALL`/`LAKE_BED` reservations referenced from
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

**Every carve uses a hard `min`, not the blended one.** `computeRiverGrid` merges every contributing
primitive into one height `H` per lattice point with no ambient clamp; each call site then applies
`Math.min(H, ambient)` once, against the merged height, rather than per primitive. No call site calls
`RosgenProfile.blendMin` — the smooth minimum that would round the rim where the valley cone meets
untouched ground. `blendMin` exists on `RosgenProfile` but has no caller anywhere in `src/main`, and the
`CARVE_BLEND_RANGE` constant it needs is not defined anywhere. Expect a visible crease at the carve
boundary for as long as that holds.
