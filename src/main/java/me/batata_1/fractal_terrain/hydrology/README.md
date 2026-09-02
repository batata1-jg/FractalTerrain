# hydrology/

## Overview

`RiverProvider` is the per-tile riverPrimitive pipeline: from the decoded terrain and the global (coarse)
riverPrimitive network it produces, per 512x512 relief tile, two artifacts from `buildTile` — a spatial
index of `HydrologicalPrimitive` influence circles (the queryable network geometry) and a carved,
sink-filled elevation tensor. `GlobalNetworkBuilder` traces/relaxes the global subgraph and carves it once
to shape the drainage field the local trace walks; `LocalNetworkBuilder` carves a second scratch surface and
attaches the local trace onto that same graph; `Meanders` then migrates the unified graph laterally, and
`RiverProvider.carveRivers` performs the one carve whose written values are kept. That carved elevation is
published as `hydrology_relief` and read back by
`ReliefProvider` through `getCarvedElevationTile`, which stamps it into relief channel 0 — so the relief
every downstream consumer sees carries the same cut the primitives were stamped along.

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
`recentTiles`, a capacity-4 `Long2ObjectLinkedOpenHashMap` guarded by `synchronized` blocks, holds the last
few `buildTile` results keyed by tile so the second store's callback finds the first's result already
computed. fastutil has no `accessOrder` flag, so access ordering is maintained by hand:
`getAndMoveToFirst`/`putAndMoveToFirst`, never a plain `get` — which would silently degrade the memo to
insertion-order eviction. The read-then-compute-then-write is not atomic, so two threads racing on the same miss
can both recompute — that costs a redundant pipeline run, never a correctness issue, since `buildTile` is
deterministic. The memo is consulted only on the production path (`stages == null`); the `@TestOnly`
debug/test seams that pass a `Stages` sink always recompute, bypassing the memo entirely.

**`buildTile` ordering** (load-bearing — see `RiverProvider.computeTile`, `GlobalNetworkBuilder.build` and
`LocalNetworkBuilder.build` for the exact code). Every stage that carves does so into its *own* fresh clone
of the raw decoded elevation `base[0]`; no tile-level buffer accumulates across stages, and only stage 4's
clone is published:

1. `GlobalNetworkBuilder.build` traces the global (coarse-arrow) subgraph for this tile over the owned 2x2
   coarse cells, then relaxes it down-gradient with `GradientNetworkRelaxation`. It builds the
   `ReachRosgenClassifier` typer against its clone before touching it, runs `ChannelElevationAssigner.assign`
   over the global-only graph, shell-carves that clone with `RiverInfluenceCarve.carveRiverInfluence`, and
   finishes with `Drainage.fillSinks` + `Drainage.computeDrainageDirection` over the carved result — so the
   drainage field the local trace walks next already sees valleys. It returns the network, that drainage
   field, the boundary-elevation map, the typer, and the carved clone. The clone feeds nothing downstream
   except the drainage field and a debug-only snapshot (`Stages.elevationFirstPass`).
2. `LocalNetworkBuilder.build` takes a second fresh clone. It seeds boundary elevations for every
   `SOURCE`/`DRAIN` node, runs `assign` (the pipeline's 2nd), shell-carves that clone, and only then runs
   `LocalDrainageTracer.traceLocalNetwork` — walking stage 1's drainage field over stage 2's carved surface
   and attaching every surviving segment onto the SAME graph in place (see `meanders/README.md` for why this
   must run single-threaded per tile). The method returns `void`: its clone is scratch whose entire purpose
   is to give the local trace a carved surface to sample, and it is discarded on return.
3. `Meanders`, constructed over the unified graph and `base[4]` (refined gradient, sampled read-only), runs
   `simulate(25)`. This is the pipeline's lateral-erosion pass and it mutates channel geometry, so every
   elevation and primitive derived before it is stale by construction — which is why stage 4 re-derives
   rather than reusing stage 2's work.
4. `RiverProvider.carveRivers` takes a third fresh clone and produces the published surface. It seeds any
   missing boundary elevations, runs `assign` (3rd), collects the unified graph's primitives, and
   shell-carves that clone. It then re-seeds *every* `SOURCE`/`DRAIN` boundary elevation by sampling the
   surface it just carved — overwriting, not `putIfAbsent` — and runs `assign` a 4th time, so the bed
   elevations stored on the graph agree with the terrain that was actually published. `RiverProvider` crops
   this clone to 512x512 and publishes it as `hydrology_relief`.
5. Back in `RiverProvider.computeTile`, a final `RiverNetwork.collectPrimitives` emits every primitive
   (global and local, one shared feature-id counter) into the R-tree in the WORLD relief-pixel frame,
   typed by a fresh `ReachRosgenClassifier` over the RAW `base[0]` and sized by
   `max(2, 1.5 * RosgenProfile.of(type).floodPlainLength(width))` — so a primitive's influence radius
   reflects the terrain the network was traced over, not the cut. There is no carve here.

**The bed trench is not cut at tile level.** No stage above carves a bed; `hydrology_relief` carries the
valley shell only. The trench is cut per chunk by `PopulateNoiseStep.fineGrainedPrimitivePass`, against the
shell the tile already published — see `profile/README.md`.

Every carve collects primitives *unfiltered*: stage 1 is global-only purely because the local network does
not exist yet at that point, not because of an explicit filter. `RiverNetwork.collectPrimitives` has a
channel-id-filtering overload that would restrict a carve to one subgraph, but nothing in this pipeline
passes anything but `channelId -> true`. Local shells are traced with no coarse halo, so a local shell can
be truncated at this tile's `PAD = 1` border and seam visibly against its neighbour tile; global floodplains
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

**Carve-before-trace exists to shape the surface a trace walks, not to filter primitives.** Stages 1 and 2
each carve their own clone *before* the trace that reads it, so `fillSinks`/`computeDrainageDirection` and
`LocalDrainageTracer` route drainage through carved valleys instead of raw decoder noise. Neither is an
attempt to keep some subgraph out of the shell — every `carveRiverInfluence` call is unfiltered, as noted
above. Their written values never reach `hydrology_relief`: stage 4 clones the raw elevation again rather
than continuing from either, so what survives from stages 1 and 2 is where the traces ran, not what they
wrote.

**Stage 4 re-derives instead of reusing, because `Meanders` invalidates everything before it.** Migrating
the channels moves every spline point, which moves every primitive and every bed elevation derived from the
old geometry. Carrying stage 2's carved buffer or its collected primitives forward across stage 3 would
publish terrain cut along channels that have since moved.

## Invariants

- **Only the primitives store enforces a byte budget.** `NonIntersectingSpatialIndex.loadInto` calls
  `Storage.evictIfNeeded(PRIMITIVE_CACHE_LIMIT_BYTES)` as its last statement, after the promise for that
  key is settled on both the disk-reload and the recompute branch — never on the lock-free cache-hit path
  (see Architecture). `hydrology_relief` extends `Storage` directly and has no equivalent cap.
- **No tile-level carve compounds on another; order-dependence travels through the graph, not through a
  buffer.** All three tile carves write their own fresh clone of `base[0]`, and only stage 4's is
  published. Reordering or skipping an earlier carve still changes the published elevation, but by a longer
  route: stage 1's carve decides which cells the drainage direction crosses and therefore where the local
  trace runs; stage 2's carve decides what surface that trace samples; stage 3 moves the channels both
  produced. The graph is the accumulator — every stage mutates the same `RiverNetwork` in place — so the
  stages are not independent even though their buffers are.
- **Within one carve call the merge is a sequential recurrence.** `computeRiverGrid` is a smoothed-min
  distance recurrence, so the primitive list it is handed MUST already be sorted by
  `HydrologicalPrimitive.comparator`. Nearest-primitive-wins holds regardless of order, but which
  near-equidistant competitors blend in, and where the river run ends, do not. See `profile/README.md` for
  the merge law.
- **The per-tile `RiverNetwork` graph is per-tile, single-threaded.** See
  `meanders/README.md` for the full contract; `GlobalNetworkBuilder` and `LocalDrainageTracer` both
  document (and rely on) it.
