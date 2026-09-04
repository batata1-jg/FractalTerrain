# meanders/

## Overview

Point-migration models: they displace the spline points of an already-built `RiverNetwork` so a graph
traced from the coarse arrow field settles into geometry that field could not express. Two rules share
one driver — `GradientNetworkRelaxation` slides points down the decoded terrain gradient, `Meanders`
bends them sideways by their own curvature. Neither builds, owns, or disposes of the graph:
`ChannelMigrator` takes an already-constructed network by injection, so several models can be driven
over the same instance in sequence without any one of them holding it privately.

## Architecture

**Both models run in the generation pipeline, in sequence.** `GlobalNetworkBuilder.build` constructs the
tile's `RiverNetwork` and relaxes it with `GradientNetworkRelaxation` in place — step count scales with
the elevation of the tile's primary owned cell, capped at `MAX_RELAX_STEPS`, at a fixed `dx` of 5. Later,
`RiverProvider.computeTile` runs `new Meanders(ctx.network(), base[4]).simulate(25, 10)` between
`LocalNetworkBuilder.build` and `carveRivers`, so meander migration lands on the already-relaxed network
right before the carve reads it.

A migration model holds no topology logic. `ChannelMigrator.step` fixes the sequence — resample,
migrate, re-seat endpoints, resolve cutoffs, resolve collisions, resample again — and delegates every
structural consequence of the displacement (self-intersection cutoffs, stream-capture collisions,
endpoint re-seating) back to the injected `RiverNetwork`. A subclass supplies only `migrate`. That
split is what keeps two models driven over one graph from falling out of step: the ordering exists in
exactly one place. How the network absorbs those structural changes is `../network/`'s concern, not
this package's.

## Design Decisions

**Endpoint pinning differs between the two rules, deliberately.** `Meanders` pins channel endpoints —
a bend must not drag a confluence off its junction. `GradientNetworkRelaxation` does not: a traced
node's position is itself a coarse-derived estimate worth relaxing, and `RiverNetwork.resolveEndpoints`
re-seats the graph's nodes after every step regardless.

**`dx` is deliberately overloaded.** The step spacing is also, through `HydrologyTuning.maxMigration`,
the cap on per-step displacement. A caller that coarsens the geometry therefore gets proportionally
bolder migration, and spacing and displacement cannot drift out of scale with each other.

**Migration fades to zero near the grid border, scaled by channel width.** A channel's carve band is
wider than its centreline, so a channel migrating to the very edge would carve outside the grid; wider
channels are confined further in. The cap on that margin (`MAX_MARGIN_FRACTION`) is uncalibrated and
under-damps near the centre — a known rough edge, not a tuned value.

**Meander migration is attenuated by local terrain gradient magnitude, exponentially.** A confined,
steep reach should not meander like a flat floodplain, so `Meanders` scales its displacement by
`exp(-gradMag / GRAD_REF)`: flat ground (gradMag 0) migrates at full rate, and the rate decays toward
zero as the sampled gradient grows. `GRAD_REF` is its own hardcoded constant in `Meanders` (currently
`1.0`), not derived from `HydrologyTuning.GRAD_THRESHOLD`'s value despite both gating the same raster
(decoder channel 4); it is otherwise uncalibrated. The raster is
optional — a `Meanders` built without one (`meandersTest`, `captureSelectionTest`, `MeandersGoldenTest`)
samples `0.0` everywhere, so `gradScale` is exactly `1.0` and behaves as if unattenuated.

## Invariants

**Per-tile, single-threaded — no state is shared across tiles or worker threads.** This is the contract
that `hydrology/README.md`, `GlobalNetworkBuilder` and `LocalDrainageTracer` all defer to. A fresh
`RiverNetwork` is constructed for every `buildTile` call; nothing about it — the channel and node id
counters, the working `QuadTree`, the atomic-view seam — is reused between tiles or shared between
threads.

**What breaks if violated.** `RiverNetwork`'s id counters and its working `QuadTree` (cleared and
rebuilt per collision pass) carry no synchronization, and folding the atomic view back in clears and
rewrites the channel, node and quadtree state unconditionally on the calling thread. Sharing one
network's construction or mutation across two worker threads, or reusing one instance across two tiles,
races on that unsynchronized state and can silently corrupt channel/node ids or the quadtree's
crossing-detection results. There is no lock to catch it. `QuadTree` does hold its own read/write lock,
but that guards concurrent *queries* against a stable tree — it does not make sharing a network's build
across threads safe.
