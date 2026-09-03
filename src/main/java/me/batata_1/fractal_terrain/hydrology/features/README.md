# features/

## Overview

Seven feature families share one spatial index and one persistence payload; three of them are ever minted
and the same three are ever carved. `HydrologicalFeature.RIVER.addPrimitives` walks a channel's spline
points and emits a `RiverPrimitive` per point; `SOURCE` emits one `SourcePrimitive` per headwater endpoint
whose emitting channel has positive width; `CONFLUENCE` emits one `ConfluencePrimitive` per `JUNCTION`
endpoint of degree three or more where at least two incident channels emitted. `ABANDONED_RIVER` and
`OXBOW_LAKE` re-mint what `RiverNetwork`'s history deque already shed, shifting it into the collect frame;
`WATERFALL` and `DELTA` still override `addPrimitives` with an empty body, so their records exist only to
hold the type tag, the codec and the `HydrologyProfile` extension point until they grow real behaviour.
Nothing produces a history primitive in practice: every `RiverNetwork` in the pipeline is built with
history disabled.

## Architecture

**Carve reaches river primitives directly and radial primitives through a second pass, and the sort order
is what makes both cheap.** `HydrologicalPrimitive.comparator` orders by `getType().ordinal()` first, and
`RIVER` is ordinal 0, so every `RiverPrimitive` sorts ahead of every other family.
`RiverInfluenceCarve.computeRiverGrid`'s first pass relies on that: it walks the sorted list only while the
entry is a `RiverPrimitive` and returns the index where the river run ended. A second pass then walks the
rest of the sorted list and carves every entry that implements `RadialPrimitive` — `SOURCE` and
`CONFLUENCE` are not adjacent in comparator order (`DELTA` sorts between them), so this is a filtered walk
to the end rather than a resume on a contiguous run. A `DeltaPrimitive`, `WaterfallPrimitive`,
`OxbowLakePrimitive` or `AbandonedRiverPrimitive` is therefore indexed, persisted and queryable, but
contributes nothing to any elevation.

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
- **A family that carves radially must implement `RadialPrimitive`.** `computeRiverGrid`'s second pass
  dispatches on `instanceof RadialPrimitive` and nothing else — a record that does not implement it is
  indexed and persisted like any other primitive but never reaches the radial carve, however far past
  `RIVER` its ordinal sorts.
- Mark any allocation that looks avoidable but is intentional with `:PERF: [what]; [why]`
  (`.claude/conventions/intent-markers.md`) rather than leaving it for a reviewer to flag.
