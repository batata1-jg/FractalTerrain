# features/

## Overview

Seven feature families share one spatial index and one persistence payload; three of them are ever minted
in practice (history stays disabled below), but five now carve when they are minted: `RIVER` directly,
`SOURCE`/`CONFLUENCE`/`ABANDONED_RIVER` through `RadialPrimitive`'s shared bed-carve default, and
`OXBOW_LAKE` through the same rectangle shell dispatch as `RIVER` (its bed carve is not implemented — see
`../carvers/README.md`). `HydrologicalFeature.RIVER.addPrimitives` walks a channel's spline
points and emits a `RiverPrimitive` per point; `SOURCE` emits one `SourcePrimitive` per headwater endpoint
whose emitting channel has positive width; `CONFLUENCE` emits one `ConfluencePrimitive` per `JUNCTION`
endpoint of degree three or more where at least two incident channels emitted. `ABANDONED_RIVER` and
`OXBOW_LAKE` re-mint what `RiverNetwork`'s history deque already shed, shifting it into the collect frame;
`WATERFALL` and `DELTA` still override `addPrimitives` with an empty body, so their records exist only to
hold the type tag, the codec and the `HydrologyProfile` extension point until they grow real behaviour.
Nothing produces a history primitive in practice: every `RiverNetwork` in the pipeline is built with
history disabled.

## Architecture

**Carve dispatch is one virtual call per primitive, and the sort order still decides the merge.**
`HydrologicalPrimitive.comparator` orders by `getType().ordinal()` first, and `RIVER` is ordinal 0, so
every `RiverPrimitive` sorts ahead of every other family, and by descending influence among rivers.
`LatticeCarve.computeBedGrid` walks that sorted list once, end to end, calling each primitive's own
`carveBed` — there is no stop at the last river and no second pass; the sort is what decides which
near-equidistant primitives blend into a lattice point rather than one winning outright (see
`../carvers/README.md`'s Merge law). `WaterfallPrimitive` and `DeltaPrimitive` implement
`PositionOnlyPrimitive`, whose `carveInfluence`/`carveBed`/`tabulateBedLut` are all genuine no-ops, so
either is indexed, persisted and queryable but contributes nothing to any elevation.
`OxbowLakePrimitive` carves the bed pass's rectangle shape too, in principle — it implements
`RosgenCarvedPrimitive` — but its `carveBed`/`tabulateBedLut` throw `UnsupportedOperationException`
rather than answering it (see `../carvers/README.md` for why that throw is unreachable in production
today). Its shell carve is real: `RosgenCarvedPrimitive`'s `carveInfluence` default routes it through
the same rectangle cross-section a `RiverPrimitive` carves.

**No primitive carries a per-point carve method, and none should grow one.** The bed and shell carves
never ask a primitive for its elevation at a point: `BedCarver`/`InfluenceCarver` tabulate each
primitive's cross-section into a lookup table once (through the primitive's own `tabulateBedLut`, which
in turn reads its family's profile — `RosgenProfile.sampleCrossSection` for a `RiverPrimitive`,
`RadialProfile.sampleRadialSection` for a `RadialPrimitive`) and walk only the lattice cells the
footprint reaches, so per-point cost is an array read, not a virtual call. A feature family plugs into
the carve through `HydrologicalPrimitive`'s three hooks (`carveInfluence`, `carveBed`, `tabulateBedLut`)
and, for its cross-section shape, a `HydrologyProfile` — never through a sampler the carve would have to
invoke per pixel. `HydrologicalFeature.addPrimitives` is the separate, warm-path seam that mints a
family's primitives from the traced network once per tile build; it has no part in the per-pixel carve.

## Invariants

- **The collect-and-merge path allocates nothing per lattice cell.** `computeBedGrid` runs once per
  chunk generated, over every primitive the prefetch returned, and sits below this repo's hot/cold line of
  abstraction (root `ARCHITECTURE.md`). No `new`, no boxing, no iterator or stream allocation in
  `carveBed`, `tabulateBedLut`, `RosgenProfile.sampleCrossSection`, or anything they call. `addPrimitives`
  runs once per tile build rather than per chunk, so it is the one member of this package allowed to
  allocate — and it does, one record per spline point.
- **`addPrimitives` takes `Object... args`, which already allocates an array per call.** Do not add a
  second varargs or boxed-object parameter to any family.
- **A family that emits primitives must sort after `RIVER`.** `computeBedGrid`'s merge is a sequential
  smoothed-min recurrence over one shared `dist[]`, so the caller's sort is what decides which
  near-equidistant primitives blend at a lattice point; a family with an ordinal below `RIVER`'s would
  change that blend order, not truncate a run — there is no run left to truncate — but the sort
  requirement stands.
- **A family that carves radially must implement `RadialPrimitive`.** Its `carveInfluence` no-op and its
  `carveBed`/`tabulateBedLut` defaults are where the disc carve is wired for every implementer; a record
  that does not implement it must supply its own three hooks (or inherit `PositionOnlyPrimitive`'s
  no-ops) to carve anything at all.
- Mark any allocation that looks avoidable but is intentional with `:PERF: [what]; [why]`
  (`.claude/conventions/intent-markers.md`) rather than leaving it for a reviewer to flag.
