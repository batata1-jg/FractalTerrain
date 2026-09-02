# features/

## Overview

Six feature families share one spatial index and one persistence payload, but only two of them are ever
minted and only one of those is ever carved. `HydrologicalFeature.RIVER.addPrimitives` walks a channel's
spline points and emits a `RiverPrimitive` per point; `SOURCE` emits one `SourcePrimitive` per headwater
endpoint; `ABANDONED_RIVER`, `OXBOW_LAKE`, `WATERFALL` and `DELTA` all override `addPrimitives` with an
empty body. The records for those four exist so the type tag, the codec and the `HydrologyProfile`
extension point are already in place when they grow real behaviour — not because anything produces them
today.

## Architecture

**Carve reaches river primitives only, and the sort order is what makes that cheap.**
`HydrologicalPrimitive.comparator` orders by `getType().ordinal()` first, and `RIVER` is ordinal 0, so
every `RiverPrimitive` sorts ahead of every other family. `RiverInfluenceCarve.computeRiverGrid` relies on
that: it walks the sorted list only while the entry is a `RiverPrimitive` and returns the index where the
river run ended, so a later family pass could resume there. No such pass exists. A `SourcePrimitive` is
therefore indexed, persisted and queryable, but contributes nothing to any elevation.

**No primitive carries a per-point carve method, and none should grow one.** The carve never asks a
primitive for its elevation at a point: `RiverInfluenceCarve` tabulates each primitive's cross-section into
a lookup table once (`RosgenProfile.sampleCrossSection`) and walks only the lattice cells its footprint
reaches, so per-point cost is an array read, not a virtual call. A feature family plugs in through
`HydrologicalFeature.addPrimitives` and a `HydrologyProfile` — the shape of its cross-section — never
through a sampler the carve would have to invoke per pixel.

## Invariants

- **The collect-and-merge path allocates nothing per lattice cell.** `computeRiverGrid` runs once per
  chunk generated, over every primitive the prefetch returned, and sits below this repo's hot/cold line of
  abstraction (root `ARCHITECTURE.md`). No `new`, no boxing, no iterator or stream allocation in
  `getProfile`, `RosgenProfile.sampleCrossSection`, or anything they call. `addPrimitives` runs once per
  tile build rather than per chunk, so it is the one member of this package allowed to allocate — and it
  does, one record per spline point.
- **`addPrimitives` takes `Object... args`, which already allocates an array per call.** Do not add a
  second varargs or boxed-object parameter to any family.
- **A family that emits primitives must sort after `RIVER`.** `computeRiverGrid`'s stop condition is the
  first non-river entry, so a family with an ordinal below `RIVER`'s truncates the river run and silently
  drops carve.
- Mark any allocation that looks avoidable but is intentional with `:PERF: [what]; [why]`
  (`.claude/conventions/intent-markers.md`) rather than leaving it for a reviewer to flag.
