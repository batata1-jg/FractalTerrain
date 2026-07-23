# Collapse the river-graph mutation surface onto a single validated seam

## Context

`RiverNetwork` is a mutable per-tile directed single-outflow in-tree (`Map<Integer,Channel>` +
`Map<Integer,Endpoint>`). Today it is mutated through several ad-hoc primitives — `split`, `merge`, a
private `Crossing` record, and a five-stage `manageCollisions` pipeline — plus direct `split()` calls from
`LocalDrainageTracer`. Every one of these pointer-chases the implicit graph (`Endpoint.incoming` /
`Endpoint.outgoing` + `Channel.startNodeId/endNodeId`); there is no explicit graph view. This spreads the
mutation surface across many entry points and makes topology changes (stream capture, local attach) fragile.

The goal is to collapse all network mutation onto **one validated seam**: an explicit conversion between
the **canonical (channel) view** (current field storage) and an **atomic (node) view**
(`List<List<Integer>>` adjacency where every interior spline point is a first-class node). Other code
mutates only the atomic view; `update()` validates and folds it back. Collision handling is rebuilt as a
from-scratch orient-and-prune over the atomic view (deleting `split`, `merge`, `Crossing`,
`compareChannels`, `segmentAndResolve`), and `Channel` switches from storing **width** to storing **flow**
(additive at confluences), with width derived via `HydrologyTuning.widthFromFlow`.

### Findings that shaped this plan (verified against the code, not assumed)

- **No `StreamCaptureResolver` exists.** A prior memory note claimed collision handling was extracted into
  that class; a full-repo grep finds nothing. All collision logic is inline in
  `RiverNetwork.java`. Plan is "rebuild inside/around `RiverNetwork`," not "replace a resolver." *(Stale
  memory — correct it after this lands.)*
- **`createCatmullRom` is NOT lossless.** `QuinticHermiteSpline.createCatmullRom(points)`
  (`math/spline/QuinticHermiteSpline.java:19-34`) re-derives velocity/acceleration from positions by finite
  differences and **zeros endpoint derivatives**. The pipeline already rebuilds via `createCatmullRom` every
  step (`Meanders.java:109,120`), so *dropping* derivatives across the seam is fine — but the round-trip
  golden must assert identity on **points + topology + flow**, never on the spline object's derivatives.
- **No epsilon / flow / adjacency exists today.** `Channel.width`/`depth` are `final` scalars
  (`Channel.java:12`); switching to flow is a real restructuring, not a field swap. There is no
  `List<List<Integer>>` graph anywhere.
- **`widthFromFlow` already exists** (`config/HydrologyTuning.java:153-156`):
  `clamp(WIDTH_FLOW_SCALE·sqrt(flow), MIN_WIDTH, MAX_WIDTH)`. The trace-time D8 flow field is already
  computed by `PipelinePreprocessing.computeFlow` and read per-point by the builders — only `Channel`
  discards it today.
- **The seam is bed-elevation-agnostic.** `update()` intentionally does **not** preserve
  `Channel.bedElevations`; discarding them is acceptable. In the Meanders sim they are `null` anyway; at the
  Phase-3 integration point `LocalRiverProvider.buildTile` runs `ChannelElevationAssigner.assign`
  (`hydrology/ChannelElevationAssigner.java:101-118`) over the **global** graph *before* `traceLocalNetwork`,
  so global channels do carry bed elevations there — but those exist only to carve the global rivers; once
  carved their purpose is complete and `buildTile` re-runs `assign` after the collision pass, re-deriving
  them. So the seam neither reads nor round-trips `bedElevations`. *(Corrects the earlier "seam runs while
  `bedElevations == null`" claim, which was false at the Phase-3 integration point.)*
- **Boundary elevations key on stable SOURCE/DRAIN ids.** `GlobalNetworkBuilder.boundaryElevByNodeIdx`
  (`GlobalNetworkBuilder.java:101-167`) and `LocalRiverProvider`'s before/after seeding (`~:238-267`) look
  up elevations by node id captured *before* simulation, and only ever for SOURCE/DRAIN nodes. Since
  `update()` re-mints structural (JUNCTION-equivalent) ids, it **must preserve the canonical id of every
  atomic node whose carried provenance is SOURCE or DRAIN** — a **type-based** rule enforced in `update()`
  itself. This is general: it covers SOURCE/DRAIN nodes minted at *any* time (including Phase-3 local
  sources/drains), needs no reserved id range, and keeps boundary-elevation consumers — scoped to
  source/drain ids — valid across `update()`/`manageCollisions`.
- **Losing the local-vs-global *channel* distinction post-seam is harmless *for the elevation consumer*.**
  `LocalRiverProvider.buildTile` today tells local channels from global by a before/after channel-id snapshot
  (`:238-246`), which works only because `split()` mints new ids for just the channels it touches. `update()`
  re-mints *every* channel id each collision pass, so that snapshot no longer distinguishes — but for the
  **elevation** consumer this doesn't matter: the **first** `ChannelElevationAssigner.assign`/carve pass runs
  *before* local tracing and the collision pass and bakes the global rivers' bed elevations into
  `carvedElevation`. Global rivers are thus already carved with approximately-correct elevations; the
  **second** `assign` (after the collision pass) re-derives elevations off that already-carved raster and
  lands approximately right without needing to know which channels were global. So channel-id churn across the
  seam is an **accepted** consequence for elevation, not a bug to engineer around — no local/global provenance
  tag is added. **But there is a second consumer the churn *does* break:** the `debug`-only
  `stages.channels`/`stages.localChannels` split (`:283-291`), which partitions on the same snapshot set and
  feeds the `04_global_channels`/`05_local_channels` PNGs via `debug/tests/LocalRiverTest.java` (`:86-87`).
  Once `update()` runs between the snapshot and the render, every channel falls into `globalOnly` and the
  split PNG is silently wrong (no exception). Since the plan declines a provenance tag, this is recorded as an
  accepted debug-only risk (see Deferred) — `LocalRiverTest.java` and the `buildTile` split need updating, not
  a new tag.

## The flow model (decisions locked in with the user)

- **Per-point flow.** `Channel` stores `double[] flow` aligned to spline points, maintained through the live
  length-changing paths (`reSample`/`keepOnly`) exactly like `bedElevations` — **but never null** (flow
  drives width during the sim). `widthAt(i) = widthFromFlow(flow[i])`. (`add*`/`removeIndexes` are dead code,
  deleted in Phase 2 — not part of the maintenance surface; see Phase 2.)
- **`ownFlow` per node.** Interior/junction nodes' `ownFlow` = the per-unit constant
  `HydrologyTuning.FLOW_PER_CELL_LOCAL` (`0.001`; global variant `FLOW_PER_CELL_GLOBAL`), mirroring
  `PipelinePreprocessing.computeFlow`. A SOURCE's `ownFlow` = its captured seed/anchor flow (headwater seed
  from the flow-accumulation field, `FLOW_INITIAL_LOCAL`-scale locally). A source has no incoming, so
  `totalFlow[SOURCE] = ownFlow[SOURCE]` = its seed.
- **DRAIN nodes store an ANCHOR flow.** Captured at trace time: the accumulated flow of the river the drain
  joins — for a tile-boundary drain the large, multi-tile **global** river flow, for a coast drain the local
  flow. This anchor is the *target* the per-basin correction rescales to (below); it is **not** summed into
  accumulation. Once anchors are fixed there is no global/local distinction — a global river is simply a
  river with large accumulated flow.
- **Accumulation runs over the ATOMIC graph, not `Endpoint.incoming`.** Recomputed from scratch after each
  rewire, reverse-topological over the atomic in-tree adjacency (`List<List<Integer>>`):
  `totalFlow[node] = ownFlow[node] + Σ totalFlow[children]`. Walking the atomic adjacency (whose order is
  already pinned — see Determinism) keeps the confluence sum order deterministic without touching the
  canonical `Endpoint`/`Channel` structure, which stays canonical-only. Use the simple graph walk, **not** an
  `Endpoint.incoming` (`HashSet`) iteration.
- **Drain preservation = per-drain-basin single scale.** After accumulation, each drain's whole feeding
  subtree (its BASIN) is rescaled by ONE uniform factor so the drain reads its stored anchor:
  `scale = drainAnchor / totalFlowAtDrain; flow'[n] = totalFlow[n]·scale` for every node `n` in that
  drain's basin — a single scale per basin, **NOT** per source→drain path. This preserves the relative
  downstream taper, keeps each un-branched reach monotonic, and renormalizes away the resample-density
  drift (per-spline-point `ownFlow` otherwise varies with point count) — so the correction is
  **load-bearing for determinism**, not cosmetic.

### Derived-width routing (K2 — the scalars Meanders needs)

`Channel.width`/`depth` become derived accessors. The single-`width` reads split by source endpoint:

| Read site | Today | New source |
| --- | --- | --- |
| Border damping (`Meanders.java:175,202`) + depth (`Meanders.java:145`) | `ch.width`/`ch.depth` | **discharge** = `widthFromFlow(flow[last])` (largest flow) |
| Migration rates `computeLocalRates` (`Channel.java:68`, every step) | `width·curvature(t)` | **per-point** `widthAt(t)·curvature(t)` (curvature is already per-point → per-point width is the natural choice) |
| Resample spacing `sqrt(width)` (`Meanders.java:111`) | `ch.width` | **intake** = `widthFromFlow(flow[0])` (lowest flow → finest spacing, conservative for gap-free discs) |
| Cutoff radius `sqrt(width)` (`RiverNetwork.java:230`) | `ch.width` | **intake** |
| `collectUnits` dx `max(width/2,…)` (`RiverNetwork.java:712`) | `ch.width` | **intake** (narrowest — preserves the "spacing ≤ half narrowest" invariant) |
| Per-point emit width (`collectUnits`/`addFeatureUnits`) | `startWidth`→`endWidth` lerp | `widthFromFlow(flow[i])` per point (natural taper) |

## The two views + the seam

- **Canonical (channel) view** — current storage: `channels`/`nodes` maps + id counters
  (`RiverNetwork.java:62-65`). Mutable internally only via `reSample` and cutoff management.
- **Atomic (node) view** — `List<List<Integer>>` adjacency over per-node data (position, flow, role),
  including interior spline points as first-class nodes. Edges directed or undirected. This is what other
  classes mutate.

**`viewAtomic()` — canonical → atomic.** Iterate channels in **sorted-by-id order** (mirror
`detectCrossings`' `Collections.sort(channelIds)` at `:386-388`) so atomic-id assignment is reproducible and
HashMap iteration order never leaks in. For each channel walk points `0..last`; mint one atomic node per
**interior** point; emit directed edge `i → i+1`; carry per-point flow. Collapse **endpoints keyed on the
canonical `Endpoint` node id** (`Channel.startNodeId`/`endNodeId`): the first channel touching a given node
id mints its atomic node, later channels reuse it, so channels sharing a graph node share one atomic node.
This keys on the graph node id, **NOT a position epsilon** (interior points always mint fresh atomic ids —
there is no reliance on interior spacing). Carry each collapsed node's `Endpoint.Type` provenance so
`update()` can preserve SOURCE/DRAIN ids and mint only JUNCTION-equivalents fresh. Mark sources (`startNodeId`
is `SOURCE`) and drains (`endNodeId` is `DRAIN`). Velocity/acceleration are **not** carried — re-inferred by
`createCatmullRom` on rebuild.

**`update()` — atomic → canonical.** Requires the atomic view fully directed with **exactly one outgoing
edge per non-drain node** — assert this explicitly (this is invariant **K1**). Walk each maximal directed
chain between structural nodes (source / drain / in-degree ≥ 2 confluence) into a `Channel`: positions →
points, node flows → `flow[]`, `createCatmullRom(points)` → spline, mint/wire `Endpoint`s through the
single-outflow-guarded mint path. **Preserve the canonical node id for every atomic node whose carried
provenance is SOURCE or DRAIN** (so boundary-elevation lookups stay valid — Finding 1); only
JUNCTION-equivalent structural nodes get freshly minted ids. `bedElevations` are intentionally **not**
preserved (the seam is bed-elevation-agnostic). Sources 1-out, DRAIN 0-out, JUNCTION exactly-1-out. A cheap
bounded-walk assertion (each source reaches a drain in ≤ node-count hops) may guard `update()` defensively,
but acyclicity holds **by construction** (Phase 3 step 2), not by that guard.

## Sequencing (risk order — each step lands with its own test before the next)

### Phase 1 — Land the seam (`viewAtomic()` / `update()`) + round-trip golden

- Add the atomic-view data structure and `viewAtomic()` / `update()` to `RiverNetwork.java`. No behavior
  change yet: nothing calls them in production.
- Add an explicit **single-outflow check** used by `update()` (iterate `nodes`; SOURCE 1-out, DRAIN 0-out,
  JUNCTION exactly-1-out — model on `Endpoint.java:53-59` degree + `Endpoint.Type`).
- **Round-trip golden** (new `src/test/java/.../meanders/` test, modeled on
  `MeandersGoldenTest.networkSignature` `:262-276` and reusing the `doubleToLongBits`-exact
  `LocalRiverGoldenTest.networkChecksum` idiom `:216-230`): build a network, assert
  `signature(canonical) == signature(update(viewAtomic(canonical)))` on **points + topology** (Phase 1 has
  no flow yet). Add a determinism-across-runs variant like the existing goldens.
- **The fixture must exercise the id-preservation rule, and the signature alone does not.**
  `networkSignature` (`:262-276`) checksums channel/node **counts** + point coordinates — it never inspects
  node ids, and the `oneEdge` SOURCE→DRAIN builder (`:287-294`) has no JUNCTION, so a trivial round trip would
  pass while `update()`'s "preserve SOURCE/DRAIN ids, re-mint JUNCTION-equivalents" rule (the load-bearing
  invariant for boundary-elevation lookups) went entirely untested. Require a fixture with **at least one
  JUNCTION confluence** in the *canonical* view — construct it directly from the node/edge specs (a shared
  JUNCTION `NodeSpec` fed by two `SOURCE→JUNCTION` edges with one `JUNCTION→DRAIN` edge out, i.e. an Endpoint
  of 2 incoming + 1 outgoing; the existing `MeandersGoldenTest` builders at `:302-339` produce only *crossing*
  edges, never a shared-node confluence, so a new spec is needed) — and add a **direct assertion that every
  SOURCE and DRAIN node keeps its
  canonical id** across `update(viewAtomic(·))` (compare the id sets by `Endpoint.Type`, not just the
  point/topology signature), plus a companion check that JUNCTION-equivalent ids are free to churn (the
  signature's `nodes=` count still matches). This is what makes the Phase-1 gate actually guard Finding-1's
  rule rather than incidentally pass.

### Phase 2 — `Channel` stores flow; width derived (independently)

- `Channel.java`: replace `startWidth/endWidth` + the `final width,depth` scalars with `double[] flow`
  (aligned to `spline`, never null) and derived `intakeWidth()`, `dischargeWidth()`, `depth()`,
  `widthAt(i)=widthFromFlow(flow[i])`. Maintain `flow[]` through the **live** length-changing paths only —
  `reSample` (add a `flowAt(t)` linear blend mirroring `bedElev(t)` `:120-124`, applied **unconditionally**,
  unlike the null-gated bed branch) and `keepOnly` (`:135-137`). `Channel.add(ChannelPt,Channel)`,
  `add(int,Channel)`, `addFront` (`:142-161`) and `removeIndexes` (`:167-185`) have **no callers anywhere in
  `src/`** (verified — full-repo grep, incl. tests/`debug`); they are the last consumers of the
  velocity/acceleration lists no other path touches. Do **not** extend `flow[]` maintenance into them —
  instead **delete** them as dead code in Phase 2 (they already carry no scalar-width dependency). If a
  reviewer prefers to keep them, they must be given `flow[]` upkeep at that point and the keep justified;
  the plan's default is deletion.
- Thread flow through construction. There are exactly **four** `new Channel(...)` sites (full-repo grep):
  `mintChannel:196`, `split:295`, `merge:341`, and `LocalDrainageTracer.buildLocalChannel:278` — all four
  currently width-taking, all four must be accounted for:
  - `RiverNetwork.mintChannel` (`:188-210`) builds `flow[]` — today it takes `startWidth`/`endWidth` and calls
    `new Channel(min(...))` + `setWidthProfile` (`:196-197`); rework it to build a per-point `flow[]` and go
    through the flow-taking constructor. `Endpoint` gains a `sourceFlow`/anchor seed field (SOURCE seed +
    DRAIN anchor).
  - `LocalDrainageTracer.buildLocalChannel` (`:269-288`) already reads the per-cell flow field
    (`flow[cell]`, `:274,276-277`) and today maps it through `widthFromFlow`+`setWidthProfile` (`:276-279`).
    Rework it to build a **real per-point `flow[]`** — `flow[cells.get(i)]` per point, aligned to the
    pre-resample points and then blended by `flowAt(t)` on `reSample` — and pass it through the **flow-taking**
    `Channel` constructor, **NOT** the temporary width-taking bridge (below). The bridge is deleted in
    Phase 3, whereas `buildLocalChannel` is only *reworked*, not deleted, so a bridge dependency here would
    either break the Phase-3 deletion or re-introduce the flow→width→flow inversion the plan exists to kill.
  - `split:295`/`merge:341` are the width-taking-bridge sites (see the constructor-sites sub-bullet below);
    both are deleted with the collision surface in Phase 3.
- **Global path — persist flow, do not invert width.** `GlobalNetworkBuilder` must carry **flow** (not a
  width double) in `EdgeSpec`. But the cached global tile
  (`TensorLayout.GLOBAL_RIVER_CHANNELS = 3` — arrow bitfield / width / bed elevation) persists **width**, not
  flow: `computeTileFromElevation` computes the per-pixel `flowAccumulation` as a **local** (`:245-249`), maps
  it through `widthFromFlow` into `widths[]` (`:250-253`), and **discards** it — `getTile` (`:282-295`) packs
  only arrow/width/elevation, and `GlobalNetworkBuilder` reads the persisted width via `grp.getWidth`
  (`:134,173`). So carrying flow requires the flow itself, and inverting `widthFromFlow`
  (`clamp(WIDTH_FLOW_SCALE·sqrt(flow), MIN_WIDTH, MAX_WIDTH)`, `HydrologyTuning:153-156`) is precisely the
  lossy width→flow round-trip this plan is built to eliminate — worst at the `MAX_WIDTH` clamp, exactly where
  the large multi-tile rivers (the DRAIN anchors the per-drain-basin correction is *load-bearing* on) sit.
  Consistent with the plan's goal, **persist flow rather than accept the inversion**: bump
  `GLOBAL_RIVER_CHANNELS` 3→4, add a 4th `flows[paddedIndex]` channel in `getTile` fed by the
  already-computed `flowAccumulation`, and a `getFlow(cx,cz)` accessor mirroring `getWidth` (`:109-111`);
  `GlobalNetworkBuilder` reads `grp.getFlow` into `EdgeSpec`. Width stays channel 1 (still consumed by the
  relief carve) — this **adds** a flow channel, it does not replace width. Cost: this bumps the on-disk
  global-tile cache format, so existing cached global tiles must be regenerated (noted under Deferred).
- **Handle residual `Channel.width` reads by lifecycle** — the naïve "keep one legacy accessor" is wrong
  because some `width` reads live in code *retained* past Phase 3, which a Phase-3 accessor-deletion would
  break. Split them:
  - **Deleted-in-Phase-3 reads** (inside the collision surface: `split:295`, `merge:341`,
    `detectCrossings:394/410/432/440`, `compareChannels:462`, `pruneDanglingJunctionLeaves:618`) — bridge
    with a temporary `dischargeWidth()`-backed scalar accessor so Phase 2 compiles; deleted with the
    collision code in Phase 3.
  - **Retained reads migrate to derived width IN PHASE 2** (not bridged): `getPtsCloseTo` (`:230`, called
    only from the kept `manageCutoffs`) → `widthFromFlow(flow[i])` of the **current point**;
    `recordRemovedComplement` (`:275`) → `widthFromFlow` of the **first removed spline point**. Note
    `recordRemovedComplement` now serves **OXBOW_LAKE units only** — dead channels from the Phase-3 DFS prune
    become `ABANDONED_RIVER` units directly during pruning, not routed through it. With these sites carrying
    no scalar-width dependency after Phase 2, the temporary accessor is safe to delete in Phase 3.
  - **Width-bridge constructor sites** `new Channel(channel.width, …)` in `split:295`/`merge:341` (only these
    two — `mintChannel` and `buildLocalChannel` go through the flow-taking constructor, above) need a
    width-taking **constructor** (synthesizing `flow[]` via inverse `widthFromFlow`), not a getter — kept
    through Phase 2, deleted with split/merge in Phase 3. Nothing on a retained path may depend on this bridge.
  - Route `Channel.computeLocalRates` (`:65-71`, run every migration step) to **per-point**
    `widthAt(t)·curvature(t)` (curvature is already per-point).
- Add reverse-topo **accumulation + per-drain-basin single-scale correction** as a `RiverNetwork` method;
  call it where flow must be current (post-mint, and later post-rewire in Phase 3).
- Re-route every width read per the **Derived-width routing** table above.
- **Re-baseline** `GOLDEN_MEANDERS_SIGNATURE` (`MeandersGoldenTest.java:255`) and
  `GlobalRiverGoldenTest.GOLDEN_CHECKSUM` (`:112`) — output legitimately changes (the `sqrt(width)` resample
  and per-point taper now read flow). Capture-then-freeze, and only over output confirmed sane (per the
  spatial-index golden-test policy: never freeze a golden over known-broken output).

### Phase 3 — Replace `manageCollisions` behind the proven seam; delete the old surface

- New `manageCollisions` over the atomic view:
  1. Detect crossings between channels (bed-overlap via `ChannelGeometry.channelsOverlap` on per-point
     `widthFromFlow(flow[i])`); connect crossing node pairs with **undirected** edges (n crossings → n
     edges). No `Crossing` record. (Optionally skip points already adjacent in the directed tree to cut
     redundant edges — but a spurious crossing edge at an existing confluence is harmless under the
     two-mark scheme below, so this is an optimization, not a correctness requirement; no explicit
     `nearSharedNode` replacement is needed.)
  2. **Deterministic DFS with TWO marks — acyclic by construction.** DFS from each source in **sorted
     source-id order over sorted adjacency** (mandatory — trunk selection is decided by visitation order).
     **Pin the per-node adjacency order explicitly:** at each node, expand its **directed tree-successor edge
     first** (the single outgoing edge carried across from `viewAtomic`, if the node still has one), then its
     **undirected crossing-edge partners in ascending atomic-node-id order**. This keeps the DFS following the
     original stream before it considers any crossing, and makes trunk selection at a node with both a
     directed successor and one-or-more crossing partners a total, HashMap-order-independent function of atomic
     node ids — the property the determinism argument rests on. Each node carries two marks: a normal DFS
     `visited` mark and a `streamMark`. Promotion happens **only**
     when the DFS reaches a DRAIN or an already-`streamMarked` node, and it promotes **only the not-yet-
     `streamMarked` SUFFIX of the current stack** — the nodes from the top of stack down to (but **not
     including**) that drain/`streamMarked` terminus. Each promoted suffix node gets its single outgoing edge
     set to the next node toward the terminus and is itself `streamMarked`. An **already-`streamMarked`
     node's outgoing edge is IMMUTABLE** — never reassigned. So each node's outgoing edge is set **exactly
     once**, at the instant it transitions unmarked→`streamMarked`. Reaching a merely-`visited` node (an
     on-stack ancestor, or an exhausted branch that never reached a drain) does **not** promote and does
     **not** `streamMark`. **Why acyclic (proof sketch):** an edge `u→v` is promoted only when `v` already
     drains (`v` is a drain or `streamMarked`); the promoted suffix is fresh, previously-unmarked nodes
     prepended to `v`'s existing acyclic drain-path, so the union stays acyclic and single-outflow (K1)
     holds. No three-color / cycle guard is needed — acyclicity is structural, backed by the
     set-outgoing-exactly-once + immutable-`streamMarked`-outgoing rules above.
  3. **Kept edges = promoted (`streamMarked`-outgoing) edges only** — not "any edge between two marked
     nodes," which would leave un-oriented diamonds that break `update()`.
  4. Prune unmarked nodes. A branch that never reached a drain/`streamMarked` node is simply never promoted
     → its nodes stay unmarked and are pruned; walk each such pruned dangling sub-path into a polyline and
     record it as an `ABANDONED_RIVER` unit at collection time. So **nothing vanishes silently** — an
     unreachable channel is demoted to abandoned, consistent with the DL-013/DL-015 clean-drop philosophy.
     (Oxbows stay with `manageCutoffs`, out of scope.)
  5. Accumulate flow (reverse-topo + per-drain-basin single-scale correction), then `update()`.
- **Migrate local insertion off `split()`.** `LocalDrainageTracer.attachSegment` (`:123-162`) stops calling
  `network.split(...)`; instead each local channel is exported into the atomic view as an edge with a
  **fresh SOURCE node**, attached via **undirected crossing edges**. A local channel whose DFS reaches a
  drain (or a `streamMarked` node) is kept; one whose branch reaches neither is demoted to an
  `ABANDONED_RIVER` unit (step 4) — never silently dropped. Run the atomic collision pass
  after local tracing in `LocalRiverProvider.buildTile` (integration point). Accept the attach-threshold
  shift from radius-based (`LOCAL_ATTACH_RADIUS=4.0`) to bed-overlap-based (folded into A2/C5).
- **Delete** `split`, `merge`, the `Crossing` record, `segmentAndResolve` + its union-find,
  `compareChannels`/`reachesDownstream`, `deleteOrphanDrains`, `mergePassFromSources`, and the legacy
  scalar `width` accessor. Update the `Meanders` delegators (`:261-271`).
- **Rewrite** — not retarget — the affected `MeandersGoldenTest` tests. `split`/`merge` cases (`:32-85`)
  test now-deleted primitives and are removed; `collisionCapture`/`sameContactPointCapture` (`:90-140`)
  assert OLD-algorithm structural outcomes (degree-2-junction elimination, `merge`-pass confluences,
  strongest-channel trunk) and must be **written from scratch** with new scenarios and assertions matching
  the two-mark DFS semantics (promoted-edge trunk selection by visitation order, abandoned-river demotion,
  single-outflow after prune) — this is new test authoring, not method-name substitution. Also update
  `debug/tests/MeandersTest.java`, which calls the deleted primitives directly.
- **Re-baseline** goldens again (topology/trunk output changes — the accepted A2 deferred risk:
  trunk-at-confluence is now DFS order, not width/flow strength).

## Critical files

- `hydrology/meanders/RiverNetwork.java` — seam (`viewAtomic`/`update` + single-outflow check),
  accumulation, new `manageCollisions`; delete `split`/`merge`/`Crossing`/`segmentAndResolve`/
  `compareChannels`/`deleteOrphanDrains`/`mergePassFromSources`.
- `hydrology/meanders/Channel.java` — `double[] flow` + derived `intakeWidth`/`dischargeWidth`/`depth`/
  `widthAt`; a **flow-taking** constructor (used by `mintChannel` + `buildLocalChannel`); maintain `flow[]`
  through the live paths `reSample`/`keepOnly` only. Delete the dead `add*`/`removeIndexes` (`:142-185`,
  no callers in `src/`) rather than extend flow upkeep into them.
- `hydrology/meanders/Endpoint.java` — `sourceFlow`/anchor seed field (SOURCE seed + DRAIN anchor). **No
  reserved id block** — id stability for boundary elevations comes from `update()`'s type-based
  SOURCE/DRAIN-id preservation.
- `hydrology/meanders/Meanders.java` — re-route width reads (`:145,175,202,111`); update delegators.
- `hydrology/LocalDrainageTracer.java` — migrate attach off `split()` to atomic-edge + crossing; rework
  `buildLocalChannel:278` to build a real per-point `flow[]` from `flow[cell]` and use the flow-taking
  `Channel` constructor (not the width bridge).
- `hydrology/LocalRiverProvider.java` — run the atomic collision pass after local tracing (accept channel-id
  churn — the local/global distinction is not needed for the elevation consumer post-seam; see Findings). The
  `debug`-only local/global PNG split (`stages.channels`/`localChannels`, `:283-291`, fed by the pre-trace
  channel-id snapshot `:238-246`) **does** break under the churn — see Deferred.
- `hydrology/GlobalNetworkBuilder.java` / `GlobalRiverProvider` — carry **flow** (not width) in `EdgeSpec`;
  `GlobalRiverProvider` persists a 4th flow channel + `getFlow` accessor (`getTile:282-295`, `getWidth:109-111`).
- `config/TensorLayout.java` — bump `GLOBAL_RIVER_CHANNELS` 3→4 for the persisted global-tile flow channel.
- `config/HydrologyTuning.java` — reuse `widthFromFlow`, `FLOW_PER_CELL_LOCAL/GLOBAL`,
  `FLOW_INITIAL_LOCAL/GLOBAL` (read-only).
- Tests: new round-trip golden (JUNCTION-confluence fixture + SOURCE/DRAIN id-stability assertion); re-baseline
  `MeandersGoldenTest.java` (`:255`) & `GlobalRiverGoldenTest.java` (`:112`); update `debug/tests/MeandersTest.java`
  and `debug/tests/LocalRiverTest.java` (its `04_global_channels`/`05_local_channels` split, `:86-87`).

## Reuse (don't reinvent)

- `HydrologyTuning.widthFromFlow` (`:153-156`) — the width law.
- `Channel.bedElev(t)` (`:120-124`) — the pattern to mirror for `flowAt(t)` resample-alignment.
- `ChannelGeometry.channelsOverlap` (`hydrology/ChannelGeometry.java:35-37`) — the bed-overlap crossing test.
- `RiverNetwork.mintChannel` single-outflow guard (`:204-207`) — the K1 enforcement point `update()` routes
  through.
- `MeandersGoldenTest.networkSignature` (`:262-276`) / `LocalRiverGoldenTest.networkChecksum` (`:216-230`) /
  `reachesDrain` walkers — golden + invariant idioms to model the new tests on.

## Determinism (must-hold)

Production draws no RNG. Topology determinism rests on: **`update()`'s type-based SOURCE/DRAIN-id
preservation** (boundary-elevation ids survive re-minting without any reserved id range);
**sorted-by-id channel iteration in `viewAtomic()`** (atomic-id assignment must not depend on HashMap order);
**flow accumulation over the atomic adjacency** rather than `Endpoint.incoming` (a `HashSet`), so the
confluence sum order is pinned and the `doubleToLongBits` goldens stay bit-stable; and **sorted source ids +
sorted adjacency** in the Phase-3 **two-mark DFS** (replaces the old channel-id-sort + width/id
`compareChannels` total order). "Sorted adjacency" is pinned to a total order per Phase 3 step 2 — **directed
tree-successor edge first (if present), then undirected crossing partners ascending by atomic node id** — so
the visitation order at a mixed node (directed successor + crossing edges) never depends on HashMap iteration;
this is the exact tie-break trunk selection turns on. The two-mark DFS is deterministic and acyclic by
construction — no RNG, no cycle guard. Golden signatures already sort channels by id, so per-channel output is order-independent; only
trunk selection depends on DFS order (accepted A2 risk).

## Verification

- `gradle test` — the JUnit golden suite. Baseline is **6/17 red on `feature/hydrology`** (4
  `LocalRiverGoldenTest` `AIOOBE 262144`, one `GlobalRiverGoldenTest`, one `SpatialIndexCorrectnessGoldenTest`);
  compare against that baseline, not green. Each phase must not regress passing tests; new round-trip golden
  (Phase 1) and re-baselined `MeandersGoldenTest`/`GlobalRiverGoldenTest` (Phases 2–3) must pass. Check
  whether the Phase-3 local-attach rework moves the `AIOOBE` cases.
- `gradle spotlessApply` before any commit (palantirJavaFormat, build-enforced).
- Manual harnesses for eyeball confirmation: `gradle meandersTest`, `gradle localRiverTest`,
  `gradle globalRiverTest` (PNG dumps under `run/debug`).
- Round-trip assertion is the core Phase-1 gate: `signature(net) == signature(update(viewAtomic(net)))`.

## Deferred / accepted risks

- **A2 stream-capture physics** — trunk-at-confluence decided by DFS order, not width/flow strength.
  Interim goldens differ and may look physically arbitrary. Deferred to a later plan.
- **C5 local-attach shift** — radius-based (4.0) → bed-overlap-based attach; topology can differ. Nothing
  vanishes silently: a local branch that reaches a drain is kept, one that does not is demoted to an
  `ABANDONED_RIVER` unit (Phase 3 step 4). Folded into A2.
- **Global-tile cache-format bump** — persisting flow adds a 4th channel to the global tile
  (`GLOBAL_RIVER_CHANNELS` 3→4, Phase 2), changing the on-disk cached-tensor layout. Existing cached global
  tiles are incompatible and must be regenerated (delete `run/`'s global-tile cache before the first run on
  the new schema). Accepted in preference to a lossy `widthFromFlow` inversion on the global path (see Phase 2).
- **Debug local/global channel split breaks under Phase-3 id churn** — `LocalRiverProvider.buildTile`'s
  before/after channel-id snapshot (`:238-246`) no longer identifies local channels once `update()` re-mints
  every id, so the `debug`-only `stages.channels`/`stages.localChannels` partition (`:283-291`) and the
  `04_global_channels`/`05_local_channels` PNGs (`debug/tests/LocalRiverTest.java:86-87`) go silently wrong.
  Consistent with the plan's decision to add **no** local/global provenance tag, this is accepted as a
  debug-only regression; `LocalRiverTest.java` and the `buildTile` split must be updated (drop the split, or
  re-derive it another way) as part of Phase 3 rather than engineered around with a tag. No production path is
  affected (the elevation consumer is churn-tolerant — see Findings).
- **No green baseline** — suite is 6/17 red; sequence strictly so each step lands with its own passing test.
