# hydrology/

## Overview

`LocalRiverProvider` is the per-tile river pipeline: from the decoded terrain and the global (coarse)
river network it produces, per 512x512 relief tile, two artifacts from a single `buildTile` pass — a
spatial index of `HydrologicalUnit` influence circles (the queryable network geometry) and a carved,
sink-filled elevation tensor. The two artifacts describe the same graph and are only cheap to produce
together, which is why they are built and cached as a pair rather than as two independent providers.

## Architecture

**Dual-store cache.** `units` (a `NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalUnit>>`) and
`carved` (a `NonIntersectingInfiniteTensor`) are backed by `Storage`'s independent per-store cache, but
both come out of one `buildTile(tileX, tileZ, stages)` call — splitting them into two providers would
mean tracing/relaxing/carving the network twice. Whichever store is requested first
(`buildUnitsTile`/`buildCarvedTile`) runs `buildTile` under a `pending` single-flight map keyed by
`TileKey`, then cross-fills the *other* store via that store's own `claimForCompute`/`fulfillClaim` pair
— so a caller that only ever asks for `getCarvedElev` still populates the units index for a later
caller, without a second compute. Bypassing the claim API (writing into either store directly) breaks
this cross-fill and can leave one store permanently unpopulated for a tile the other has cached.

**`buildTile` ordering** (load-bearing — see `LocalRiverProvider.buildTile` javadoc for the exact code
reference):

1. `GlobalNetworkBuilder.build` traces + Meanders-relaxes the global (coarse-arrow) subgraph for this
   tile's owned 2x2 coarse cells, returning the network plus the boundary-elevation map it accumulated
   for source/drain nodes.
2. `ChannelElevationAssigner.assign` over the global-only graph, then `HydrologyProfileCarver
   .carveRiverShells` carves the global valley shell into the decoded elevation — so the drainage field
   computed next already sees valleys.
3. `Drainage.fillSinks` + `Drainage.computeDrainageDirection` over that carved elevation.
4. `LocalDrainageTracer.traceLocalNetwork` traces the local network directly off the drainage field and
   attaches every surviving segment onto the SAME graph in place (void return — see `meanders/README.md`
   for why this must run single-threaded per tile).
5. The boundary-elevation map is augmented with the local trace's new `SOURCE`/`DRAIN` nodes, then
   `ChannelElevationAssigner.assign` runs a second time over the now-unified graph.
9. `RiverNetwork.collectUnits` emits every unit (global and local, one shared feature-id counter,
   `dx = max(width/2, MIN_CONVERT_SPACING)` resample spacing) into the R-tree; `carveRiverShells` runs a
   second time; the padded buffer is cropped to the 512x512 tile.

Both `carveRiverShells` passes collect units *unfiltered*: the first is global-only purely because the
local network does not exist yet at that point, not because of an explicit filter. `RiverNetwork
.collectUnits` has a channel-id-filtering overload that would restrict a carve to one subgraph, but
nothing in this pipeline calls it. Local shells are traced with no coarse halo, so a local shell can be
truncated at this tile's `PAD = 1` border and seam visibly against its neighbour tile; global floodplains
use a 2x2-cell halo and are unaffected by this.

## Coordinate frames

| Frame | Unit | Where it is grounded |
| ----- | ---- | --------------------- |
| block-px | 1 Minecraft world block | `world/gen/` chunk code; a tile origin is `tileX << 9`. |
| tile | 512x512 block-px = 512x512 native-px | `HydrologyTileGeometry.GRID = 512`; `tileX = blockX >> 9`. `GlobalRiverProvider` is the one exception in this package — it caches its own tiles addressed directly in coarse-px, a separate grid from this one. |
| native | 1 decoder/relief pixel, 1:1 with block-px inside a tile | The frame `HydrologicalUnit` coordinates and the carved-elevation tensor live in. |
| coarse | 1 coarse unit = 256 native px | `HydrologyTileGeometry.COARSE_PX = 256`. `GlobalNetworkBuilder` bridges the two frames: a 512-native-px tile `(tileX, tileZ)` owns the 2x2 coarse cells `(tileX*2 + a, tileZ*2 + b)` for `a, b` in `{0, 1}`. |

`HydrologyTileGeometry` (`GRID = 512`, `PAD = 1`, `PADDED = GRID + 2*PAD = 514`, `COARSE_PX = 256`)
centralizes these constants for every class in this package; `buildTile` works over the padded
`514x514` buffer and crops to `512x512` only at the end, since the 1px halo is needed for neighbour
sampling at the tile border throughout the pipeline.

## Design decisions

**Local attach by proximity, not terrain shape.** The local trace joins to the global network purely by
spatial proximity (`HydrologyTuning.LOCAL_ATTACH_RADIUS`), read from a point index built fresh over the
graph's channels each call — not from a per-pixel "is this a global river" boolean mask. This is why
drainage can be computed once, up front, on the globally-carved elevation, rather than being recomputed
after each local segment attaches.

**Global-only carve-first ordering exists to shape drainage, not to filter units.** The global shell is
carved into the elevation *before* the local trace so `fillSinks`/`computeDrainageDirection` route the
local drainage through carved valleys instead of raw decoder noise. It is not an attempt to keep local
units out of the tile-level shell — both `carveRiverShells` calls are unfiltered, as noted above.

## Invariants

- **The dual-store build depends on `Storage`'s claim API.** `LocalRiverProvider`'s cross-fill (see
  Architecture) requires computing a tile's units and carved elevation together and publishing both
  through `claimForCompute`/`fulfillClaim`; do not compute one store's tile independently of the other.
- **`FloatTensor` obtained from `carved` is frozen once cached** — never mutate a tensor read via
  `getCarvedElev`; take a `slice`/`copyRange` first if a mutable copy is needed.
- **The hydrology carve is order-dependent.** `carveRiverShells` reads and writes one shared buffer and
  keeps only the nearest unit per pixel, so results depend on how many carve passes have run and which
  units existed in the graph at the time. `buildTile` runs it twice by design (see Architecture) — adding,
  reordering, or deduplicating those passes changes terrain output.
- **The per-tile `RiverNetwork`/`Meanders` graph is per-tile, single-threaded.** See
  `meanders/README.md` for the full contract; `GlobalNetworkBuilder` and `LocalDrainageTracer` both
  document (and rely on) it.
