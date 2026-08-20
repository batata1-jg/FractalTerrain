# hydrology/

## Overview

`LocalRiverProvider` is the per-tile riverPrimitive pipeline: from the decoded terrain and the global (coarse)
riverPrimitive network it produces, per 512x512 relief tile, a single artifact from `buildTile` — a
spatial index of `HydrologicalPrimitive` influence circles (the queryable network geometry). The carved,
sink-filled elevation `buildTile` computes along the way is an internal input to the drainage trace and
the two elevation-assignment passes; it is never published, since `ReliefProvider` decodes elevation
channel 0 itself from the same diffusion residual rather than importing it.

## Architecture

**Single-store cache, 50 MB budget.** `primitives` is a
`NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>>` backed by `Storage`'s per-store
cache, with a 50 MB soft cap on cached bytes (`PRIMITIVE_CACHE_LIMIT_BYTES`) enforced through
`Storage.evictIfNeeded` on every cache miss inside `NonIntersectingSpatialIndex.loadInto`. An evicted
tile simply falls off `CACHE`; the next `buildPrimitivesTile` call for that key reruns the whole
`buildTile` pipeline and, if the store is disk-backed, a prior disk-persisted tile is reloaded instead of
recomputed. There is no cross-store fill to worry about: `buildTile` computes and returns exactly one
artifact, so a cache miss always means "recompute (or reload) the primitives for this tile," never
"reconstruct a second store from a first store's result."

**`buildTile` ordering** (load-bearing — see `LocalRiverProvider.buildTile` javadoc for the exact code
reference):

1. `GlobalNetworkBuilder.build` traces the global (coarse-arrow) subgraph for this tile, then relaxes it
   down-gradient with `GradientNetworkRelaxation` (NOT `Meanders`, which has no pipeline caller), over this
   tile's owned 2x2 coarse cells, returning the network plus the boundary-elevation map it accumulated
   for source/drain nodes.
2. `ChannelElevationAssigner.assign` over the global-only graph, then `HydrologyProfileInprinter
   .carveRiverShells` carves the global valley shell into the decoded elevation — so the drainage field
   computed next already sees valleys.
3. `Drainage.fillSinks` + `Drainage.computeDrainageDirection` over that carved elevation.
4. `LocalDrainageTracer.traceLocalNetwork` traces the local network directly off the drainage field and
   attaches every surviving segment onto the SAME graph in place (void return — see `meanders/README.md`
   for why this must run single-threaded per tile).
5. The boundary-elevation map is augmented with the local trace's new `SOURCE`/`DRAIN` nodes, then
   `ChannelElevationAssigner.assign` runs a second time over the now-unified graph.
9. `RiverNetwork.collectPrimitives` emits every primitive (global and local, one shared feature-id counter,
   `dx = max(width/2, MIN_CONVERT_SPACING)` resample spacing) into the R-tree, which `buildTile` returns
   directly; `carveRiverShells` runs a second time on the padded buffer, but the buffer itself is never
   published — cropping it to the 512x512 tile happens only inside the `@TestOnly` `Stages` sink.

Both `carveRiverShells` passes collect primitives *unfiltered*: the first is global-only purely because the
local network does not exist yet at that point, not because of an explicit filter. `RiverNetwork
.collectPrimitives` has a channel-id-filtering overload that would restrict a carve to one subgraph, but
nothing in this pipeline calls it. Local shells are traced with no coarse halo, so a local shell can be
truncated at this tile's `PAD = 1` border and seam visibly against its neighbour tile; global floodplains
use a 2x2-cell halo and are unaffected by this.

## Coordinate frames

| Frame | Unit | Where it is grounded |
| ----- | ---- | --------------------- |
| block-px | 1 Minecraft world block | `world/gen/` chunk code; a tile origin is `tileX << 9`. |
| tile | 512x512 block-px = 512x512 native-px | `HydrologyTileGeometry.GRID = 512`; `tileX = blockX >> 9`. `GlobalRiverProvider` is the one exception in this package — it caches its own tiles addressed directly in coarse-px, a separate grid from this one. |
| native | 1 decoder/relief pixel, 1:1 with block-px inside a tile | The frame `HydrologicalPrimitive` coordinates and the carved-elevation tensor live in. |
| coarse | 1 coarse unit = 256 native px | `HydrologyTileGeometry.COARSE_PX = 256`. `GlobalNetworkBuilder` bridges the two frames: a 512-native-px tile `(tileX, tileZ)` owns the 2x2 coarse cells `(tileX*2 + a, tileZ*2 + b)` for `a, b` in `{0, 1}`. |

`HydrologyTileGeometry` (`GRID = 512`, `PAD = 1`, `PADDED = GRID + 2*PAD = 514`, `COARSE_PX = 256`)
centralizes these constants for every class in this package; `buildTile` works over the padded
`514x514` buffer and crops to `512x512` only at the end, since the 1px halo is needed for neighbour
sampling at the tile border throughout the pipeline.

## Design decisions

**Local attach by proximity, not terrain shape.** The local trace joins to the global network purely by
spatial proximity (`HydrologyTuning.LOCAL_ATTACH_RADIUS`), read from a point index built fresh over the
graph's channels each call — not from a per-pixel "is this a global riverPrimitive" boolean mask. This is why
drainage can be computed once, up front, on the globally-carved elevation, rather than being recomputed
after each local segment attaches.

**Global-only carve-first ordering exists to shape drainage, not to filter primitives.** The global shell is
carved into the elevation *before* the local trace so `fillSinks`/`computeDrainageDirection` route the
local drainage through carved valleys instead of raw decoder noise. It is not an attempt to keep local
primitives out of the tile-level shell — both `carveRiverShells` calls are unfiltered, as noted above.

## Invariants

- **The primitives store enforces its own byte budget.** `NonIntersectingSpatialIndex.loadInto` calls
  `Storage.evictIfNeeded(PRIMITIVE_CACHE_LIMIT_BYTES)` as its last statement, after the promise for that
  key is settled on both the disk-reload and the recompute branch — never on the lock-free cache-hit path
  (see Architecture).
- **The hydrology carve is order-dependent, across passes and within one.** `carveRiverShells` reads and
  writes one shared buffer, so results depend on how many carve passes have run and which primitives
  existed in the graph at the time; `buildTile` runs it twice by design (see Architecture), and adding,
  reordering or deduplicating those passes changes terrain output. Within a single pass,
  `computeRiverGrid` is a sequential smoothed-min-distance recurrence, so the primitive list it is handed
  MUST already be sorted by `HydrologicalPrimitive.comparator` — nearest-primitive-wins holds regardless
  of order, but which near-equidistant competitors blend in, and where the river run ends, do not.
  See `profile/README.md` for the merge law.
- **The per-tile `RiverNetwork` graph is per-tile, single-threaded.** See
  `meanders/README.md` for the full contract; `GlobalNetworkBuilder` and `LocalDrainageTracer` both
  document (and rely on) it.
