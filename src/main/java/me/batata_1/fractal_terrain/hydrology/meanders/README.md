# meanders/

## Overview

`RiverNetwork` is the mutable graph a single tile build works on: a directed dendritic
in-tree of `Channel` edges and `Endpoint` nodes, migrated by the `ChannelMigrator` models
(`GradientNetworkRelaxation`, then `Meanders`) and mutated in place by the
local-drainage attach and collision passes. It exists as a mutable structure — rather than an immutable
value rebuilt per step — because relaxation, local attachment, and collision resolution all need to
edit the same graph incrementally without re-deriving it from scratch each time.

## Architecture

All mutation funnels through one seam instead of ad hoc graph edits: `viewAtomic()` converts the
canonical (channel/node map) view into an `AtomicView`, where every interior spline point — not just
channel endpoints — is a first-class node with a directed adjacency list. Callers append nodes/edges to
that view (the local drainage trace does this), and `update(AtomicView)` folds it back into the
canonical `RiverNetwork` in place: it re-derives per-node flow (`accumulateAndCorrectFlow`), re-emits
`Channel`s as maximal chains between structural nodes (source/drain/confluence), and re-assigns channel
ids while preserving SOURCE/DRAIN node ids (so a boundary-elevation map keyed on those ids stays valid
across an `update`). `manageCollisions` is a full rebuild over this same seam: detect bed-overlap
crossings, then run a multi-source BFS from every DRAIN over the reversed graph (shortest hop count,
ties broken by straightest continuation) to orient and prune dangling branches (recording pruned runs
as `ABANDONED_RIVER` when history is enabled), then `update` the result back in.

Per one `LocalRiverProvider.buildTile` call: `GlobalNetworkBuilder` builds a fresh `RiverNetwork`
purely from its parameters (global-only graph), relaxes it with a `GradientNetworkRelaxation`, and
returns it; `LocalDrainageTracer` then
mutates that SAME instance in place via `viewAtomic()`/`manageCollisions` to attach the local subgraph
traced off the drainage field. Both stages, and the graph itself, live only for the duration of that one
`buildTile` call.

## Invariants

**Per-tile, single-threaded — no state shared across tiles or threads.** A fresh `RiverNetwork`
is constructed for every `buildTile` call; nothing about the graph (channel/node id
counters, the working `QuadTree`, the `AtomicView` seam) is reused or shared between tiles or across
worker threads. `GlobalNetworkBuilder` builds and returns its network purely from its call parameters
(no shared mutable state, so concurrent per-tile builds on different worker threads never interact), and
`LocalDrainageTracer` documents the same per-call contract for the mutation it performs afterward.

**What breaks if violated.** `RiverNetwork`'s id counters (`nextChannelId`, `nextNodeId`), its working
`QuadTree` (cleared and rebuilt per collision pass), and `AtomicView` construction have no
synchronization — `update()` clears and rewrites `channels`/`nodes`/`quadTree` unconditionally on the
calling thread. Sharing one `RiverNetwork` instance's construction or mutation (`viewAtomic`/`update`/
`manageCollisions`) across two worker threads, or reusing one instance across two tiles, races on that
unsynchronized state and can silently corrupt channel/node ids or the quadtree's crossing-detection
results — there is no lock to catch it. `QuadTree` itself does hold its own read/write lock, but that
guards concurrent *query* traffic against a stable tree, not concurrent construction — it does not make
sharing a network's build across threads safe.
