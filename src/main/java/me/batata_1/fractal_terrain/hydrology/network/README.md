# network/

## Overview

The river network is a directed dendritic in-tree: edges are `Channel`s keyed by channel id, vertices
are typed `Endpoint`s sitting on channel endpoints. Every node has at most one outgoing edge — multiple
tributaries may flow in, only one channel flows out. That is invariant **K1**, and `Endpoint.outgoing`
is a single channel id rather than a set because of it.

## The canonical↔atomic seam

All network mutation is funnelled through one conversion, so topology rules are validated in exactly one
place instead of at each call site.

- **Canonical view** — the `channels`/`nodes` maps. What the rest of the pipeline reads.
- **Atomic view** (`AtomicView`) — an adjacency list where *every interior spline point* is a
  first-class node. What per-point algorithms need.

The round trip is `viewAtomic()` → mutate → `accumulateAndCorrectFlow(av)` → `update(av)`. `update`
folds the atomic view back into `this` in place and returns `this`; it does not allocate a new network.

`RiverNetworkSeamGoldenTest` gates the round trip as bit-exact.

### What `update` preserves, and what it does not

`update` **preserves SOURCE and DRAIN canonical ids** — boundary-elevation maps key on them, so
re-assigning them would silently misplace every seeded bed elevation. Every JUNCTION-equivalent
structural node gets a fresh id, and the node counter is set past every preserved id so a fresh id can
never collide with a preserved one.

`update` **re-assigns every channel id**. This has a consequence worth stating plainly: the local-vs-global
channel distinction cannot survive a collision pass. `RiverProvider.Stages.localChannels` is
therefore always empty and the local-only debug render is blank — an accepted debug-only regression.

`bedElevations` are deliberately not preserved across the seam; `ChannelElevationAssigner` re-derives them.

### Flow ordering

`accumulateAndCorrectFlow` accumulates over the **atomic adjacency**, never over `Endpoint.incoming`.
`incoming` is a `HashSet`, so summing over it would let iteration order leak into the result and break
the bit-exact goldens. Accumulating over the adjacency pins the confluence sum order to ascending
atomic id.

Near a drain, the last few mainstem nodes are ramped up toward the drain's anchor flow. A drain reads
its anchor exactly, which can far exceed the natural accumulated flow just upstream; without the ramp,
channel width would step discontinuously where a tributary joins. There is deliberately **no basin-wide
rescale** — only a clamp to the anchor ceiling plus the local lerp.

## Stream capture

`detectAndResolveCaptures` rebuilds topology from scratch over the atomic view rather than patching it, which is
what keeps the result acyclic and single-outflow by construction:

1. Detect crossings by bed overlap on per-point width; join the crossing node pairs with undirected edges.
2. Layered multi-source BFS from every DRAIN over the reversed graph — a predecessor list *is* the
   reversal, so a node's BFS parent is by definition its forward-graph successor and no un-reversal step
   exists. Distance is hop count: cheap and O(V+E), at the cost of a slight bias toward coarsely-sampled
   channels, since atomic spacing varies by pass (`RESAMPLE_DIST` vs. `DX`). Whole layers are processed
   with candidates sorted by ascending atomic id, so the result depends only on layer structure — not on
   dequeue order, and not on any per-source start order. Parents tied at the same distance break on
   straightest continuation (least deflection from the candidate's own existing heading toward its
   parent, so a captured channel keeps flowing the way it was already going; a layer-0 DRAIN has no
   tangent and falls back to nearest-by-squared-distance), then ascending atomic id.
3. SOURCE nodes are reachable but never expanded and never anyone's parent — `resolveEndpoints` cannot
   express a SOURCE with incoming flow. A node is alive only if BFS reaches it *and* a forward sweep from
   a reached SOURCE along parent edges reaches it back; reachability alone can leave an interior-role
   headwater that `update` never starts a chain from (it only starts at SOURCE, DRAIN, or in-degree >= 2),
   silently dropping its points.
4. Keep only the parent edges of live nodes; prune the rest, recording each dangling sub-path as an
   `ABANDONED_RIVER` when recording is on. One parent per node gives single-outflow (K1) and strictly
   decreasing distance along parent edges gives acyclicity, both by construction.
5. Re-derive flow, then `update` back in place.

The net effect: a crossing that `resolveCrossingEdges` planarizes into a shared node forces a merge —
the shared node has two forward continuations but K1 permits only one outgoing edge, so one channel's
continuation survives and the other is pruned to an `ABANDONED_RIVER`. The BFS picks the survivor by
shortest hop count to a drain, so the physically nearer drain wins; two rivers meeting at the same
elevation do form a confluence, so a forced merge here is the realistic outcome, not a limitation. A
dangling tributary crossing a trunk is captured into a live channel the same way; a branch reaching no
drain is pruned.

This was once planned as a separate `StreamCaptureResolver` class. That never landed — the logic lives
here in `RiverNetwork`.

## Edge planarization

`AtomicView.resolveCrossingEdges` re-routes geometrically crossing edges through a shared node at the
intersection, preserving flow direction. It exists because meander migration lets channels drift across
each other, and the downstream carve assumes a planar network.

Candidate pairs come from an x-ordered sweep line over segment bounding boxes: segments enter the sweep
in ascending `minX` order and leave once their `maxX` falls behind the sweep position, with the segments
currently spanning the line held in a status structure keyed by `minY` so a bounded key-range scan finds
every box that could still overlap. The sweep is exact — a candidate pair is only missed if its boxes
truly do not overlap — rather than pruned by a fixed radius. Two straight segments meet in at most one
point, so resolving every original pair once fully planarizes the set in a single pass — the new
sub-segments meet only at the shared crossing nodes. Detection and the exact intersection test stay
separated: the sweep only emits candidate pairs, sorted ascending; a second pass runs the intersection
test and assigns crossing-node ids over that sorted order, because the intersection determinant is not
symmetric in floating point and the ids must not depend on the order the sweep happens to discover pairs.

## Invariants

- **K1, single outflow**, enforced by `assertSingleOutflow` as a hard precondition of `update`. It
  throws rather than asserting, so the guard holds without `-ea`.
- **`addDirectedEdge` is the only sanctioned way to grow the adjacency.** It rejects duplicates; a
  duplicate edge would read as a second outflow and trip K1.
- **Node types differ in mutability**, and the split/merge/prune paths rely on it: a SOURCE is created
  only at construction and never destroyed, a DRAIN is never created and deleted only when a capture
  orphans it, a JUNCTION is created and destroyed freely.
- **`ChannelTyper` implementations type every point, including endpoints.** Deciding that a point is a
  source or drain rather than a reach is topology, and only `collectPrimitives` owns the graph; a typer sees
  geometry and raster only.
- **Primitive spacing must stay `dx <= width/2`** where `collectPrimitives` resamples. The floodplain corridor is
  a union of per-primitive discs, and looser spacing scallops it.
