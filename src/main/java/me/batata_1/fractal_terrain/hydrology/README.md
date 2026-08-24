# hydrology/

## Overview

`RiverProvider` is the per-tile riverPrimitive pipeline: from the decoded terrain and the global (coarse)
riverPrimitive network it produces, per 512x512 relief tile, two artifacts from `buildTile` — a spatial
index of `HydrologicalPrimitive` influence circles (the queryable network geometry) and a carved,
sink-filled elevation tensor. `GlobalNetworkBuilder` traces/relaxes the global subgraph and carves it once
to shape the drainage field the local trace walks; `LocalNetworkBuilder` attaches the local trace onto that
same graph, re-assigns bed elevations across the unified graph, and carves the result into the elevation
`RiverProvider` publishes. That carved elevation is published as `hydrology_relief`, but has no reader yet
— `ReliefProvider` still decodes elevation channel 0 itself from the same diffusion residual rather than
importing it.

## Architecture

**Two stores, one shared compute, and a bounded memo so a tile is built once, not twice.** `primitives` is
a `NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>>` backed by `Storage`'s per-store
cache, with a 50 MB soft cap on cached bytes (`PRIMITIVE_CACHE_LIMIT_BYTES`) enforced through
`Storage.evictIfNeeded` on every cache miss inside `NonIntersectingSpatialIndex.loadInto`. `hydrology_relief`
is a `NonIntersectingInfiniteTensor` of shape `{1, 512, 512}` holding the river-carved elevation, cropped
from the padded 514 buffer; it extends `Storage` directly rather than going through
`NonIntersectingSpatialIndex`, so it carries no soft cap of its own — the 50 MB budget applies only to
`primitives`.

Both stores' compute functions call the same private `RiverProvider.buildTile`, which runs the whole
trace/carve pipeline and returns both artifacts together. Without a memo, a tile whose primitives and
carved elevation are both requested (the common case, since every provider built after `RiverProvider`
reaches through it) would run that pipeline twice — once per store's independent cache-miss callback.
`recentTiles`, a capacity-4 access-order `LinkedHashMap` wrapped in `Collections.synchronizedMap`, holds
the last few `buildTile` results keyed by tile so the second store's callback finds the first's result
already computed. The read-then-compute-then-write is not atomic, so two threads racing on the same miss
can both recompute — that costs a redundant pipeline run, never a correctness issue, since `buildTile` is
deterministic. The memo is consulted only on the production path (`stages == null`); the `@TestOnly`
debug/test seams that pass a `Stages` sink always recompute, bypassing the memo entirely.

**`buildTile` ordering** (load-bearing — see `RiverProvider.computeTile`, `GlobalNetworkBuilder.build` and
`LocalNetworkBuilder.build` for the exact code):

1. `GlobalNetworkBuilder.build` traces the global (coarse-arrow) subgraph for this tile, then relaxes it
   down-gradient with `GradientNetworkRelaxation` (NOT `Meanders`, which has no pipeline caller), over this
   tile's owned 2x2 coarse cells. It then runs `ChannelElevationAssigner.assign` over the global-only graph
   and `HydrologyProfileInprinter.carveRiverInfluence` (the first carve) into its own clone of the decoded
   elevation, and finishes with `Drainage.fillSinks` + `Drainage.computeDrainageDirection` over that carved
   clone — so the drainage field the local trace walks next already sees valleys. It returns the network,
   the drainage field, the boundary-elevation map it accumulated, the Rosgen typer it built (against the
   pre-assign, pre-carve elevation), and the global-only-carved elevation. That carved clone feeds nothing
   downstream except the drainage field above and a debug-only snapshot (`Stages.elevationFirstPass`) —
   `LocalNetworkBuilder` carves a fresh clone of the raw decoded elevation, not this one.
2. `LocalNetworkBuilder.build` traces the local network directly off that drainage field with
   `LocalDrainageTracer.traceLocalNetwork`, attaching every surviving segment onto the SAME graph in place
   (void return — see `meanders/README.md` for why this must run single-threaded per tile). It augments the
   boundary-elevation map with the local trace's new `SOURCE`/`DRAIN` nodes, then runs
   `ChannelElevationAssigner.assign` (the pipeline's 2nd assign) over the now-unified graph,
   `HydrologyProfileInprinter.carveRiverInfluence` (the 2nd and last carve) into its own fresh clone of the
   raw decoded elevation, re-seeds the boundary-elevation map against that just-carved surface, and runs
   `ChannelElevationAssigner.assign` a 3rd time so the final bed elevations agree with the carve. It returns
   the carved padded elevation — this is exactly what `RiverProvider` crops and publishes as
   `hydrology_relief`.
3. Back in `RiverProvider.computeTile`, `RiverNetwork.collectPrimitives` emits every primitive (global and
   local, one shared feature-id counter, `dx = max(width/2, MIN_CONVERT_SPACING)` resample spacing) into
   the R-tree, sampled against the RAW decoded elevation (not either carved buffer) — so a primitive's
   influence radius reflects the terrain the network was traced over, not the cut. There is no third carve
   here: the padded elevation `LocalNetworkBuilder` returned is only cropped (`RiverProvider.cropToTile`)
   into the `hydrology_relief` tensor.

Both carve calls collect primitives *unfiltered*: the first is global-only purely because the local network
does not exist yet at that point, not because of an explicit filter. `RiverNetwork.collectPrimitives` has a
channel-id-filtering overload that would restrict a carve to one subgraph, but nothing in this pipeline
calls it. Local shells are traced with no coarse halo, so a local shell can be truncated at this tile's
`PAD = 1` border and seam visibly against its neighbour tile; global floodplains use a 2x2-cell halo and
are unaffected by this.

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
carved into `GlobalNetworkBuilder`'s own elevation clone *before* the local trace so `fillSinks`/
`computeDrainageDirection` route the local drainage through carved valleys instead of raw decoder noise. It
is not an attempt to keep local primitives out of the tile-level shell — both `carveRiverInfluence` calls
are unfiltered, as noted above. Because `LocalNetworkBuilder` carves a separate, fresh clone of the raw
elevation rather than continuing from `GlobalNetworkBuilder`'s clone, the global carve's own written values
never reach the published `hydrology_relief` tensor — only their effect on the drainage field, and
therefore on where the local trace runs, does.

## Invariants

- **Only the primitives store enforces a byte budget.** `NonIntersectingSpatialIndex.loadInto` calls
  `Storage.evictIfNeeded(PRIMITIVE_CACHE_LIMIT_BYTES)` as its last statement, after the promise for that
  key is settled on both the disk-reload and the recompute branch — never on the lock-free cache-hit path
  (see Architecture). `hydrology_relief` extends `Storage` directly and has no equivalent cap.
- **The hydrology carve is order-dependent, but no longer through one shared buffer.**
  `GlobalNetworkBuilder`'s carve and `LocalNetworkBuilder`'s carve each write their own clone of the
  decoded elevation — `GlobalNetworkBuilder`'s clone only feeds the drainage field
  (`Drainage.fillSinks`/`computeDrainageDirection`) and a debug snapshot, and is otherwise discarded;
  `LocalNetworkBuilder` carves a fresh clone of the raw decoded elevation, and that clone is what
  `RiverProvider` crops and publishes. The two calls therefore do not compound on one accumulating buffer
  the way a single `buildTile` method once made them. Order-dependence survives through the drainage field
  instead: skipping or reordering the global carve changes which cells the drainage direction — and
  therefore the local trace — crosses, which changes the final published elevation even though the global
  carve's own written values never appear in it. Within a single `carveRiverInfluence` call,
  `computeRiverGrid` is still a sequential smoothed-min-distance recurrence, so the primitive list it is
  handed MUST already be sorted by `HydrologicalPrimitive.comparator` — nearest-primitive-wins holds
  regardless of order, but which near-equidistant competitors blend in, and where the river run ends, do
  not. See `profile/README.md` for the merge law.
- **The per-tile `RiverNetwork` graph is per-tile, single-threaded.** See
  `meanders/README.md` for the full contract; `GlobalNetworkBuilder` and `LocalDrainageTracer` both
  document (and rely on) it.
