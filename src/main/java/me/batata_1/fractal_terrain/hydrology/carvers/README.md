# carvers/

## Overview

Two functions turn the hydrological-primitive index into elevation edits: `LatticeCarve.carveInfluenceGrid`
(the shell) and `LatticeCarve.computeBedGrid` (the bed). Both dispatch per primitive — `carveInfluence`
for the shell, `carveBed` for the bed — onto the footprint shapes held in `InfluenceCarver` and
`BedCarver`, so neither orchestration method switches on a concrete record type.

The tile-level shell carve (`carveInfluenceGrid`, over the 514x514 padded tile) runs **three** times per
tile build, on three different elevation buffers:

1. `GlobalNetworkBuilder.build` — global-only graph, into its own clone. Discarded except through the
   drainage field computed over it.
2. `LocalNetworkBuilder.build` — unified graph, into its own clone. Also discarded: this clone exists only
   so `LocalDrainageTracer` walks a carved surface.
3. `RiverProvider.carveRivers` — unified-and-meandered graph, into a third fresh clone of the raw decoded
   elevation. This is the only one whose written values survive: it is what `RiverProvider` crops and
   publishes as `hydrology_relief`.

The bed carve is per-chunk only, with no tile-level counterpart. It runs from
`PopulateNoiseStep.fineGrainedPrimitivePass`, which queries every primitive in reach once per chunk
(`RiverProvider.queryInfluence`, inlined at that call site) and then calls `computeBedGrid` over the
chunk's 16x16 lattice. A tile's published elevation therefore carries the valley shell but not the
trench; the trench is cut at chunk-fill time, against the shell the tile already published.

## Architecture

**`carveInfluenceGrid`** merges every primitive touching the padded tile into `elevs[]` with a hard
`Math.min` per primitive — cut-only, order-independent, and with no ranking buffer of its own: each
primitive clamps the lattice against whatever the primitives before it already cut.

**`computeBedGrid`** merges every primitive touching the chunk's lattice into one `(height, water,
weight)` triple per lattice point in `grid.acc()`, plus the winning primitive's packed type in
`grid.typeMask()`, over one shared distance buffer `grid.dist()`. Neither the shell carve nor the bed
carve stabs the spatial index per pixel; the index is queried once per chunk (bed) or handed a
pre-collected list (shell), never per lattice point. For each primitive the loop tabulates a
cross-section lookup table once (`tabulateBedLut`, or the shell carve's own inline tabulation), then
walks only the lattice cells its footprint reaches, interpolating the LUT instead of re-evaluating the
profile's branchy per-region logic at every point.

**`d` is a banded footprint scale, not a radius.** A rectangle-footprint primitive's raw scale is the
factor its rectangle must be scaled by to contain the lattice point,
`max(|tang| / influenceLen, |perp| / influenceWidth)`, so `raw <= 1` is exactly "inside the footprint".
A disc's raw scale is the plain Euclidean distance from its centre, divided by its radius. `BedCarver.
carve` and `BedCarver.carveRadial` both remap their own raw scale through the same `BedCarver.band`,
three linear pieces pinned so `LatticeCarve.BED_EDGE` (0.25) always lands on the bank and
`LatticeCarve.FLOODPLAIN_EDGE` (0.5) always lands on the floodplain edge, whatever the primitive's size.
A rectangle's control points come from its own `marginLen`/`floodPlainLen`; a disc's come from
`RadialProfile.MARGIN_NORM` (0.5) and `RadialProfile.FLOOD_PLAIN_NORM` (0.75) — authored gates, not
measurements, the same way a rectangle's control points are clamped into `[0, 1]` and into order before
their slopes are taken. `d` is dimensionless either way: `LatticeCarve.UNSET_MIN_DIST` and
`HydrologyTuning.PRIMITIVE_BLEND_STRENGTH` read against whichever scale a primitive produced, without
caring whether it measures a rectangle or a circle. Banding both scales through the same law is what
makes one shared `dist[]` sound — see Merge law below.

The banding is load-bearing in two directions. It gives the paint side a size-independent coordinate: a
consumer classifies a point into bed / floodplain / influence with two comparisons against constants and
no access to the primitive. And because the banded value feeds the smoothed-min recurrence, it changes
which primitive wins where — a small tributary's bed and floodplain outrank a large trunk's influence
band — and the same value drives the `acc[]` blend weight through
`acc[a + 2] = 1 - clamp(dist, 0, 1)`, so carved elevation moves too: a floodplain edge that weighted
near 1 weights 0.5.

`InfluenceCarver.carveRosgenInfluence`, the tile-level shell carve, keeps its own two-piece `dd` remap
and is not banded through `BedCarver.band`. Unifying the two would move shell terrain and bed terrain
together, leaving any regression unattributable.

**Merge law.** `computeBedGrid` runs one sequential smoothed-min-distance recurrence over one shared
`dist[]`: seeded at `LatticeCarve.UNSET_MIN_DIST` (64), updated per primitive by a smoothstep of
`(dist[i] - d)`, and the `(height, water, weight)` triple blended toward the current primitive with that
same weight — "closest primitive wins, nearby competitors blend in," driven by primitive order rather
than an explicit nearest-channel query. `primitives` MUST already be sorted by
`HydrologicalPrimitive.comparator`, which orders every `RiverPrimitive` before any other family and, among
rivers, by descending influence; the merge is a sequential recurrence, so the sort is what decides which
near-equidistant competitors blend into a lattice point rather than one winning outright. There is one
walk over the whole sorted list, with no stop at the last river and no second pass: a radial primitive
sorted behind every river is still carved, ranked against the same `dist[]` a river wrote into.

A radial primitive never writes `typeMask` — a cell it claims stays `HydrologicalFeature.NONE` unless an
earlier primitive already tagged it. The height rule is one `h = min(elevs[i], sample)` for every family:
a bowl or cone blends against real ambient elevation exactly like a channel does, rather than a merged
surface an earlier primitive already cut. The published weight is assigned, not maxed —
`acc[a + 2] = 1 - clamp(dist[i], 0, 1)` — because with one shared buffer a cell inside a primitive's
clipped AABB but outside its true footprint (`raw > 1`) is masked to `w = 0` and never disturbs the
assignment, so no earlier claim needs protecting by a max. `Types.RIVER_DIST` is published from that
shared buffer, so it reflects whichever primitive won each cell, not the river pass alone.

**`AbandonedRiverPrimitive` is carved by both the shell and the bed.** It implements `RadialPrimitive`,
so `computeBedGrid`'s single walk carves it exactly like a `ConfluencePrimitive`/`SourcePrimitive`
through `RadialPrimitive`'s default `carveBed`, and it overrides `RadialPrimitive`'s no-op
`carveInfluence` to route through `InfluenceCarver.carveRadialInfluence` as well. This is a deliberate
consequence of the family/interface rule in `features/README.md` — "a family that carves radially must
implement `RadialPrimitive`" — not an oversight left over from splitting the shell dispatch off the bed
one.

**The dispatch seam.** Both passes reach a primitive through one virtual call —
`HydrologicalPrimitive.carveInfluence`/`carveBed` — and the footprint shape that call runs lives in
`InfluenceCarver`/`BedCarver`. Filling a primitive's cross-section table is a separate hook,
`tabulateBedLut`, because the two steps depend on different things: the table needs geometry only the
family knows (a river reads `width`/`curvature`/`elevation`/`seed`; a radial reads `radius`/`width`/
`elevation`), while the cut needs the footprint clip and the merge recurrence, which belong to the shape
rather than the family. `RadialPrimitive`'s one `carveBed` default serves `ConfluencePrimitive`,
`SourcePrimitive` and `AbandonedRiverPrimitive`; if the table were built inside that default it would
have to carry three tabulations instead of reading each family's own `tabulateBedLut`.

**Cut-only.** Both passes' output is a pure blend with no ambient clamp folded into the merge itself.
`carveInfluenceGrid` applies `Math.min` per primitive directly into `elevs[]`. `computeBedGrid`'s output
`h` is a weighted blend of already-ambient-capped samples (each primitive's own `Math.min(elevs[i],
sample)`); the caller then recovers its carved elevation as `(1 - w) * ambient + w * min(acc[a],
ambient)`, applying one more `min` against the merged height. Neither pass can raise terrain above
ambient.

**LUT residual.** A primitive's cross-section LUT is anchored on an integer perp-lattice (or radial)
index and interpolated linearly, so it smears a profile's own discontinuities across one lattice cell —
one block wide in the bed path, one pixel wide in the shell path. Accepted, not an oversight; revisit if
bed rims read as unexpectedly soft.

**`OxbowLakePrimitive.carveBed` and `tabulateBedLut` throw `UnsupportedOperationException`.** Its real
bed carve has no design yet (see the Explicitly out of scope section of
`docs/superpowers/specs/2026-09-20-carve-dispatch-design.md`), and `computeBedGrid` calls every
primitive's `carveBed` unconditionally, so the throw would crash chunk generation if an
`OxbowLakePrimitive` ever reached it. It does not today: the only production `RiverNetwork` construction
(`GlobalNetworkBuilder.java`'s `new RiverNetwork(PADDED, nodeSpecs, edgeSpecs)`) uses the three-argument
constructor, which sets `saveHistory = false`; both mint sites for a shed primitive
(`RiverNetwork.recordAbandoned`, `recordRemovedComplement`) are gated on that flag, so `lastStates` is
always empty and no `OxbowLakePrimitive` is ever collected into a carved list. Re-check this evidence
before enabling history. `OxbowLakePrimitive.carveInfluence` is real (it inherits `RosgenCarvedPrimitive`'s
rectangle shell default) and unaffected by this gap — only the bed carve is unimplemented.

**Call sites.** `LatticeCarve.carveInfluenceGrid` reads and writes a caller-supplied padded-tile buffer,
skipping pixels with negative ambient elevation (ocean). The three tile-level callers are listed in the
Overview; each hands it a different buffer, and only `RiverProvider.carveRivers`'s survives.
`PopulateNoiseStep.fineGrainedPrimitivePass` carves the bed and, from `computeBedGrid`'s water lane and
type mask, also populates `Types.WATER_HEIGHT`, `Types.RIVER_TYPE` and `Types.RIVER_DIST`. It queries
`RiverProvider.queryInfluence` once per chunk (chunk centre plus half-diagonal radius, in the
relief-pixel frame) and sorts the result with `HydrologicalPrimitive.comparator` — the ordering
`computeBedGrid` requires — directly at that call site: the query is one line, small enough to read next
to the carve that depends on its sort.

**`ZoneCategory` is reserved, not live.** No carve path reads it: neither pass here has a
zone-priority merge — the distance-weighted blend above is the only merge rule. See `profile/README.md`
for the enum itself and its `WATERFALL`/`LAKE_BED` reservations.
