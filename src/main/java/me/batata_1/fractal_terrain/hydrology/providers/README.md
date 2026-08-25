# providers/

## Overview

Everything else in `hydrology/` is stateless stage math, called once per tile and discarded. These two
classes are the package's only `Storage`-backed providers: they hold the per-world tile caches, own the
disk identity of what hydrology publishes, and are the only hydrology objects `GenerationContext`
constructs and hands to downstream consumers. That is the seam this subpackage marks — persistence and
provider lifetime on this side, pure per-tile computation on the other.

The two sit in a strict order. `GlobalRiverProvider` decides a river's course at continental scale in the
coarse-pixel frame, before any tile-local detail exists. `RiverProvider` then attaches a drainage-derived
local network onto that skeleton and publishes the per-tile artifacts. The stage ordering inside
`RiverProvider.buildTile`, and the two-store/memo cache design, are documented in `../README.md`; this file
covers what is local to the providers themselves.

## The `global_river` tensor

`GlobalRiverProvider` caches its own tiles — shape `{GLOBAL_RIVER_CHANNELS, 64, 64}`, addressed directly in
coarse-px, on a grid unrelated to the 512-native-px tiling every other hydrology class uses. Computation
runs over a padded `64 + 2*PAD` buffer (`PAD = HydrologyTuning.RAMP_WIDTH`, a halo giving border pixels
neighbours for the gradient descent) and crops to the central 64×64 on publish.

| Ch | Contents        | Read by                                                       |
| -- | --------------- | ------------------------------------------------------------- |
| 0  | Packed arrow bitfield, stored via `Float.intBitsToFloat` | `getArrow`; decoded with `outgoingMask`/`isSource`/`isCoast`/`isSink`/`isRiver` |
| 1  | River width (0 on non-river pixels) | `getWidth`                                |
| 2  | River-bed elevation, forced monotonically non-increasing downstream | `getElevation` — the target `ReliefProvider.carveRiver` carves toward |
| 3  | Raw flow accumulation | `getFlow`                                               |

**Channel 0 is an integer smuggled through a float tensor.** The store is a `FloatTensor`, so arrows round-trip
through `Float.intBitsToFloat`/`floatToIntBits` rather than through a numeric cast — reading channel 0 as a
float yields a meaningless denormal. Arrow bits *accumulate* per pixel, which is how converging tributaries
come to share one pixel.

Arrow bit layout: bits 0–7 are the outgoing direction mask (one bit per D8 neighbour), then `SOURCE = 1<<8`,
`COAST = 1<<9`, `SINK = 1<<10`, `RIVER = 1<<11`.

**Upstream direction is derived, never stored.** `ingoingMask` scans the four neighbours in the upper half of
the direction table and asks each whether its outgoing mask points back. Storing an ingoing mask would have to
be written by whichever tile owns the *neighbour* pixel, so it would go stale at every tile border; deriving it
keeps confluences consistent across borders and lets one pixel carry several tributaries.

**Flow is persisted rather than recovered from width.** Channel 3 duplicates information channel 1 was derived
from (`HydrologyTuning.widthFromFlow`). It is kept because the width law is not safely invertible at the
precision the local network needs, so `GlobalNetworkBuilder` re-derives width forward from flow instead of
inverting the published width.

## Design decisions

**Elevation is normalized on read, not on write.** `paddedElevation` divides the coarse elevation slice by the
pipeline's blend weight (coarse channel 6) and yields 0 where that weight is below `1e-6`. The coarse tensor
accumulates weighted contributions from overlapping model windows, so its raw elevation channel is a weighted
*sum*, not a height; a pixel no window covered has weight 0 and no defined elevation.

**The ridge/coast thresholds and the border ramp are seeding heuristics, not terrain facts.**
`RIDGE_THRESHOLD` / `COAST_THRESHOLD` select where rivers are born and where they terminate;
`RAMP_HEIGHT` raises border pixels on a linear inward decay so a descent that reaches the tile edge is pushed
back inward rather than pooling there. `MAX_WALK_STEPS` bounds a single source's descent against a pathological grid: a walk that reaches it stops
where it is, having logged a warning, leaving a river that terminates mid-grid with the arrows it already
wrote intact. Nothing repairs that river afterwards.

**The provider seam is what tests substitute.** `RiverProvider` holds a `@TestOnly` nullable
`GlobalRiverProvider` override, falling back to the singleton when it is null. Golden tests drive a synthetic
global network through that field rather than standing up a `WorldPipeline`, because `GlobalRiverProvider`
reads the coarse tensor through the static `pipeline` field and cannot otherwise be isolated.

## Invariants

- **`GlobalRiverProvider` must be constructed before `RiverProvider`.** It reads the `WorldPipeline` coarse
  tensor; `RiverProvider` reads its tiles. `GenerationContext`'s build order encodes this and nothing else
  enforces it.
- **The two providers live in different tile grids.** `GlobalRiverProvider`'s key is a 64×64 *coarse-px* tile;
  `RiverProvider`'s is a 512×512 native-px tile (`HydrologyTileGeometry.GRID`). A key from one is never valid
  in the other. `GlobalNetworkBuilder` owns the conversion — see the frames table in `../README.md`.
- **Only `primitives` enforces a byte budget.** `hydrology_relief` extends `Storage` directly and has no soft
  cap of its own; see `../README.md` for where the cap is applied.
