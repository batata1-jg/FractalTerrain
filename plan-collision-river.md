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
  `update()` re-assigns structural (JUNCTION-equivalent) ids, it **must preserve the canonical id of every
  atomic node whose carried provenance is SOURCE or DRAIN** — a **type-based** rule enforced in `update()`
  itself. This is general: it covers SOURCE/DRAIN nodes minted at *any* time (including Phase-3 local
  sources/drains), needs no reserved id range, and keeps boundary-elevation consumers — scoped to
  source/drain ids — valid across `update()`/`manageCollisions`.
- **Losing the local-vs-global *channel* distinction post-seam is harmless *for the elevation consumer*.**
  `LocalRiverProvider.buildTile` today tells local channels from global by a before/after channel-id snapshot
  (`:238-246`), which works only because `split()` mints new ids for just the channels it touches. `update()`
  re-assigns *every* channel id each collision pass, so that snapshot no longer distinguishes — but for the
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
  flow. This anchor is the *ceiling* the per-basin correction clamps to and the *target* the near-drain lerp
  smooths up to (below); it is **not** summed into accumulation. Once anchors are fixed there is no
  global/local distinction — a global river is simply a river with large accumulated flow.
- **Accumulation runs over the ATOMIC graph, not `Endpoint.incoming`.** Recomputed from scratch after each
  rewire, reverse-topological over the atomic in-tree adjacency (`List<List<Integer>>`):
  `totalFlow[node] = ownFlow[node] + Σ totalFlow[children]`. Walking the atomic adjacency (whose order is
  already pinned — see Determinism) keeps the confluence sum order deterministic without touching the
  canonical `Endpoint`/`Channel` structure, which stays canonical-only. Use the simple graph walk, **not** an
  `Endpoint.incoming` (`HashSet`) iteration.
- **Drain preservation = clamp + near-drain lerp (NOT a basin-wide rescale).** A single multiplicative
  `scale = drainAnchor / totalFlowAtDrain` applied to the whole basin is **wrong**: because a small local
  basin usually drains into a much larger multi-tile river, `drainAnchor ≫ totalFlowAtDrain` gives `scale ≫ 1`,
  inflating every headwater tributary to the trunk's discharge. Instead, after accumulation, per drain (over
  its BASIN):
  1. **Clamp** every basin node to the anchor ceiling — `flow[n] = min(totalFlow[n], drainAnchor)`. The anchor
     is the most any node in the basin may read; a node's natural accumulated flow is otherwise kept as-is
     (headwaters stay small).
  2. The drain itself reads its anchor exactly — `flow[drain] = drainAnchor`.
  3. **Smooth the drain jump** so width is continuous into the joined river: follow the **mainstem** (the
     highest clamped-flow predecessor, tie-broken by ascending atomic id) upstream from the drain. Only if the
     jump `drainAnchor - flow[mainstemUpstream]` exceeds `DRAIN_FLOW_SMOOTH_STEP` (=10, tunable), build a ramp
     chain of mainstem nodes (drain-adjacent first) that stops at whichever comes FIRST: the natural clamped
     profile has caught up (`drainAnchor - flow[node] ≤ DRAIN_FLOW_SMOOTH_STEP`), or the chain reaches
     `DRAIN_FLOW_SMOOTH_MAX_NODES` (=20) nodes. Linearly interpolate from `drainAnchor` at the drain down to
     the far-end node's natural flow across the chain, taking `max(existing, ramped)` per node (the ramp only
     *raises* flow, never lowers a tributary already carrying more).

  This keeps each un-branched reach monotonic and headwaters physically small, and confines the anchor's
  influence to the last ≤ 20 mainstem nodes before the drain instead of the whole basin. Interior width still
  breathes slightly with resample density (per-spline-point `ownFlow` varies with point count) — an **accepted**
  drift (see Deferred); determinism is unaffected because point count is seed-deterministic.

**Accumulation + clamp + near-drain lerp, pinned:**

```java
// Walks the ATOMIC adjacency (List<List<Integer>>), never Endpoint.incoming (a HashSet) —
// invariant: confluence sum order must not depend on HashMap/HashSet iteration (Determinism).
// Populates atomic.flow[] (the DERIVED per-point flow) from carried inputs atomic.ownFlow[]
// (per-cell constant / SOURCE seed) and atomic.anchorFlow[] (DRAIN-only ceiling+target).
void accumulateAndCorrectFlow(AtomicView atomic) {
    int n = atomic.size();

    // "children" in totalFlow[node] = ownFlow[node] + Σ totalFlow[children] means tributaries:
    // predecessors u of an edge u -> node in the atomic adjacency. Build them once, each list
    // sorted ascending by atomic id so the summation order at every confluence is pinned.
    List<List<Integer>> predecessors = buildPredecessorsSortedById(atomic);

    int[] remainingIn = new int[n];
    for (int v = 0; v < n; v++) remainingIn[v] = predecessors.get(v).size();

    double[] totalFlow = new double[n];
    TreeSet<Integer> ready = new TreeSet<>(); // ascending atomic id — deterministic frontier order
    for (int v = 0; v < n; v++) if (remainingIn[v] == 0) ready.add(v); // sources first (in-degree 0)

    while (!ready.isEmpty()) {
        int node = ready.pollFirst();
        double sum = atomic.ownFlow[node];
        for (int tributary : predecessors.get(node)) { // already finalized, ascending-id order
            sum += totalFlow[tributary];
        }
        totalFlow[node] = sum;
        for (int next : atomic.adjacency.get(node)) {   // node's single downstream successor (K1)
            if (--remainingIn[next] == 0) ready.add(next);
        }
    }

    // Per drain (sorted for determinism): CLAMP the basin to the anchor ceiling, then LERP the
    // last <= DRAIN_FLOW_SMOOTH_MAX_NODES mainstem nodes up to the anchor. NO basin-wide rescale.
    for (int drain : sortedDrainIds(atomic)) {
        double anchor = atomic.anchorFlow[drain];

        // 1. clamp every basin node to the anchor ceiling (headwaters keep their small natural flow)
        for (int node : basinOf(atomic, drain, predecessors)) {
            atomic.flow[node] = Math.min(totalFlow[node], anchor);
        }
        // 2. the drain reads its anchor exactly
        atomic.flow[drain] = anchor;

        // 3. smooth the drain jump along the mainstem, if it exceeds the step threshold
        int up = mainstemPredecessor(drain, predecessors, atomic.flow); // highest clamped-flow trib, tie: min id
        if (up == NONE || anchor - atomic.flow[up] <= DRAIN_FLOW_SMOOTH_STEP) continue;

        // build the ramp chain (drain-adjacent first), stopping when the natural profile catches up
        // or the chain hits DRAIN_FLOW_SMOOTH_MAX_NODES nodes — whichever comes first
        List<Integer> chain = new ArrayList<>();
        int node = up;
        while (node != NONE
                && chain.size() < DRAIN_FLOW_SMOOTH_MAX_NODES
                && anchor - atomic.flow[node] > DRAIN_FLOW_SMOOTH_STEP) {
            chain.add(node);
            node = mainstemPredecessor(node, predecessors, atomic.flow);
        }

        // 4. linearly ramp anchor (at the drain) down to the far-end node's natural flow across the
        //    chain; only raise (max) so a tributary already carrying more is never lowered
        int span = chain.size();
        double farFlow = atomic.flow[chain.get(span - 1)];
        for (int k = 0; k < span; k++) {                 // k = 0 is the drain-adjacent node
            double t = (double) (k + 1) / (span + 1);     // 0 -> drain (anchor), 1 -> far end (farFlow)
            double ramped = lerp(anchor, farFlow, t);
            int cn = chain.get(k);
            atomic.flow[cn] = Math.max(atomic.flow[cn], ramped);
        }
    }
}
```

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
HashMap iteration order never leaks in. For each channel walk points `0..last`; add one atomic node per
**interior** point; emit directed edge `i → i+1`. **Carry each node's `ownFlow`** (the per-cell constant
`FLOW_PER_CELL_LOCAL/GLOBAL` for interior/junction nodes, the captured seed for a SOURCE) — **not** a per-point
`flow`, which is DERIVED later by `accumulateAndCorrectFlow`; DRAIN nodes additionally carry their `anchorFlow`.
Collapse **endpoints keyed on the canonical `Endpoint` node id** (`Channel.startNodeId`/`endNodeId`): the first
channel touching a given node id adds its atomic node, later channels reuse it, so channels sharing a graph node
share one atomic node. This keys on the graph node id, **NOT a position epsilon** (interior points always get a
fresh atomic id — there is no reliance on interior spacing). Carry each collapsed node's `Endpoint.Type`
provenance so `update()` can preserve SOURCE/DRAIN ids and assign only JUNCTION-equivalents fresh. Mark sources
(`startNodeId` is `SOURCE`) and drains (`endNodeId` is `DRAIN`). Velocity/acceleration are **not** carried —
re-inferred by `createCatmullRom` on rebuild.

**Atomic-view data structure** (per-node position/flow/role/provenance, parallel to the adjacency):

```java
final class AtomicView {
    double[] x, z;                 // position, parallel arrays indexed by atomic id
    double[] flow;                 // DERIVED per-point flow — NOT carried; populated by
                                   //   accumulateAndCorrectFlow() before update()/width reads
    double[] ownFlow;              // CARRIED input: FLOW_PER_CELL_LOCAL/GLOBAL (interior/junction)
                                   //   or the SOURCE seed
    double[] anchorFlow;           // CARRIED input, valid only where role == DRAIN — the clamp
                                   //   ceiling + near-drain lerp target
    Endpoint.Type[] role;          // SOURCE / DRAIN / JUNCTION / null (interior point)
    int[] canonicalId;             // valid only where role == SOURCE || DRAIN — the Endpoint id to preserve
    List<List<Integer>> adjacency; // adjacency[u]: directed tree-successor edge(s), indexed by atomic id
}
```

**`viewAtomic()` pseudocode:**

```java
AtomicView viewAtomic(RiverNetwork net) {
    AtomicView atomic = new AtomicView();
    Map<Integer, Integer> endpointToAtomicId = new HashMap<>(); // canonical Endpoint id -> atomic id

    List<Integer> channelIds = new ArrayList<>(net.channels.keySet());
    Collections.sort(channelIds); // mirror detectCrossings' Collections.sort(channelIds) — pins
                                  // atomic-id assignment against HashMap iteration order (Determinism)

    for (int channelId : channelIds) {
        Channel ch = net.channels.get(channelId);
        int last = ch.points.size() - 1;
        int[] atomicIdOfPoint = new int[ch.points.size()];

        for (int i = 0; i <= last; i++) {
            if (i != 0 && i != last) {
                // interior point: ALWAYS gets a fresh atomic node — keyed on nothing but its
                // position in this channel, never collapsed with any other point (no epsilon).
                // Carries the per-cell ownFlow constant; flow is DERIVED later, not carried.
                atomicIdOfPoint[i] = atomic.addNode(ch.points.get(i),
                        /* ownFlow */ FLOW_PER_CELL, /* anchorFlow */ NONE,
                        /* role */ null, /* canonicalId */ NONE);
                continue;
            }

            // endpoint point: collapse keyed on the canonical Endpoint node id — the first channel
            // touching this Endpoint adds the atomic node, every later channel reusing the same
            // Endpoint id reuses it, so channels sharing a graph node share one atomic node
            int endpointNodeId = (i == 0) ? ch.startNodeId : ch.endNodeId;
            Integer existing = endpointToAtomicId.get(endpointNodeId);
            if (existing != null) {
                atomicIdOfPoint[i] = existing;
                continue;
            }

            Endpoint ep = net.nodes.get(endpointNodeId);
            boolean boundary = ep.type == SOURCE || ep.type == DRAIN;
            // ownFlow: SOURCE carries its captured seed (ep.sourceFlow); DRAIN/JUNCTION carry the
            // per-cell constant. anchorFlow: DRAIN only (ep.sourceFlow holds the anchor), else NONE.
            double ownFlow    = (ep.type == SOURCE) ? ep.sourceFlow : FLOW_PER_CELL;
            double anchorFlow = (ep.type == DRAIN)  ? ep.sourceFlow : NONE;
            int atomicId = atomic.addNode(ch.points.get(i), ownFlow, anchorFlow,
                    /* role */ ep.type, /* canonicalId */ boundary ? endpointNodeId : NONE);
            endpointToAtomicId.put(endpointNodeId, atomicId);
            atomicIdOfPoint[i] = atomicId;
        }

        for (int i = 0; i < last; i++) {
            atomic.adjacency.get(atomicIdOfPoint[i]).add(atomicIdOfPoint[i + 1]); // directed i -> i+1
        }
        // velocity/acceleration NOT carried — createCatmullRom re-infers them on rebuild
    }
    return atomic;
}
```

**`update()` — atomic → canonical, IN PLACE.** Requires the atomic view fully directed with **exactly one
outgoing edge per non-drain node** — assert this explicitly (this is invariant **K1**). `update()` mutates
`this`: **clear** `channels`/`nodes`/`quadTree`, **set `nextNodeId` past `max(preserved canonical id)`** so
freshly-assigned JUNCTION ids can't collide with a preserved SOURCE/DRAIN id, then re-emit into `this`. It does
**not** allocate a fresh `RiverNetwork` — every caller (`Meanders`, `LocalDrainageTracer`, `LocalRiverProvider`,
the internal `quadTree`) holds this same instance, so rebuilding in place preserves object identity and needs no
`network = network.update(...)` reassignment (it may `return this` for chaining). Walk each maximal directed
chain between structural nodes (source / drain / in-degree ≥ 2 confluence) into a `Channel`: positions → points,
node flows → `flow[]`, `createCatmullRom(points)` → spline, then build+wire the `Channel`/`Endpoint`s directly
into `this` through an **inlined** single-outflow (K1) guard — the same guard `mintChannel` used, now inlined in
`emitChannel` since `mintChannel` is deleted (see Construction path). **Preserve the canonical node id for every
atomic node whose carried provenance is SOURCE or DRAIN** (so boundary-elevation lookups stay valid — Finding 1);
only JUNCTION-equivalent structural nodes get freshly assigned ids. `bedElevations` are intentionally **not**
preserved (the seam is bed-elevation-agnostic). Sources 1-out, DRAIN 0-out, JUNCTION exactly-1-out. A cheap
bounded-walk assertion (each source reaches a drain in ≤ node-count hops) may guard `update()` defensively,
but acyclicity holds **by construction** (Phase 3 step 2), not by that guard.

**Single-outflow check (K1)** — used by `update()`, modeled on `Endpoint.java:53-59` degree + `Endpoint.Type`:

```java
void assertSingleOutflow(AtomicView atomic) { // K1
    for (int id = 0; id < atomic.size(); id++) {
        int outdeg = atomic.adjacency.get(id).size(); // directed tree-successor slot only —
                                                        // never counts undirected crossing edges
        switch (atomic.role[id]) {
            case SOURCE -> assert outdeg == 1 : "SOURCE must have exactly one outgoing edge";
            case DRAIN  -> assert outdeg == 0 : "DRAIN must have no outgoing edge";
            default     -> assert outdeg == 1 : "JUNCTION/interior must have exactly one outgoing edge";
        }
    }
}
```

**`update()` pseudocode** — IN-PLACE rebuild of `this`; maximal directed chains between structural nodes
(source / drain / in-degree ≥ 2 confluence), rebuilt into `Channel`s via `createCatmullRom`, with type-based
SOURCE/DRAIN canonical-id preservation and only JUNCTION-equivalents re-assigned:

```java
RiverNetwork update(AtomicView atomic) {                 // mutates `this`; returns `this` for chaining
    assertSingleOutflow(atomic); // K1 — asserted before any mutation

    // structural nodes = source / drain / confluence (in-degree >= 2); sorted for deterministic
    // emission order, independent of HashMap/HashSet iteration
    TreeSet<Integer> structural = new TreeSet<>();
    int[] indegree = computeIndegree(atomic); // count of predecessors per atomic id
    for (int id = 0; id < atomic.size(); id++) {
        if (atomic.role[id] == SOURCE || atomic.role[id] == DRAIN || indegree[id] >= 2) {
            structural.add(id);
        }
    }

    // Preserve the canonical id of every atomic node whose carried provenance is SOURCE or DRAIN
    // (Finding: boundary-elevation lookups key on these ids). JUNCTION-equivalents (everything
    // else in `structural`) stay NONE and get a fresh id below.
    int[] canonicalIdOf = new int[atomic.size()];
    Arrays.fill(canonicalIdOf, NONE);
    int maxPreserved = -1;
    for (int id : structural) {
        if (atomic.role[id] == SOURCE || atomic.role[id] == DRAIN) {
            canonicalIdOf[id] = atomic.canonicalId[id];
            maxPreserved = Math.max(maxPreserved, atomic.canonicalId[id]);
        }
    }

    // IN-PLACE reset: wipe the canonical view, then set the id counter PAST every preserved id so a
    // freshly-assigned JUNCTION id can never collide with a preserved SOURCE/DRAIN id.
    this.channels.clear();
    this.nodes.clear();
    this.quadTree.clear();
    this.nextNodeId = maxPreserved + 1;

    for (int start : structural) {                        // sorted — deterministic emission order
        if (atomic.role[start] == DRAIN) continue;         // drains only terminate a chain, never start one
        int cur = onlyOutgoing(atomic, start);              // K1: exactly one outgoing edge
        List<Integer> chain = new ArrayList<>(List.of(start));
        while (!structural.contains(cur)) {
            chain.add(cur);
            cur = onlyOutgoing(atomic, cur);                // interior node: single outgoing edge (K1)
        }
        chain.add(cur);                                      // cur: the next structural node (confluence or drain)
        emitChannel(atomic, chain, canonicalIdOf);          // wires directly into `this`
    }
    return this;
}

// Builds a Channel from the chain and wires it into `this` directly (mintChannel is deleted — its
// single-outflow K1 guard is inlined here). Reuses the KEPT insertChannel QuadTree helper.
void emitChannel(AtomicView atomic, List<Integer> chain, int[] canonicalIdOf) {
    List<Vec2> points = new ArrayList<>();
    double[] flow = new double[chain.size()];
    for (int i = 0; i < chain.size(); i++) {
        points.add(atomic.pos(chain.get(i)));
        flow[i] = atomic.flow[chain.get(i)];               // DERIVED flow, already populated by accumulate
    }
    Spline spline = QuinticHermiteSpline.createCatmullRom(points); // re-derives velocity/acceleration;
                                                                     // NOT lossless — accepted (Finding)
    int startId = idFor(canonicalIdOf, chain.get(0));                  // fresh iff JUNCTION-equivalent
    int endId   = idFor(canonicalIdOf, chain.get(chain.size() - 1));
    // inlined K1 guard: the start endpoint must not already own an outgoing channel (single-outflow)
    assert !this.nodes.containsKey(startId) || this.nodes.get(startId).outgoing == NONE;
    Channel ch = new Channel(points, flow, spline);        // flow-taking constructor (Phase 2)
    this.channels.put(nextChannelId(), ch);
    wireEndpoints(startId, endId, ch);                     // creates/links Endpoints in `this`
    insertChannel(ch);                                     // KEPT QuadTree helper (also used by manageCutoffs)
    // bedElevations intentionally NOT preserved — the seam is bed-elevation-agnostic (Finding)
}

// Reuse the preserved canonical id if this atomic node carried one (SOURCE/DRAIN); otherwise assign
// a fresh JUNCTION-equivalent id from the counter (which was set past every preserved id above).
int idFor(int[] canonicalIdOf, int atomicId) {
    return canonicalIdOf[atomicId] != NONE ? canonicalIdOf[atomicId] : nextNodeId++;
}

// Defensive only — acyclicity holds BY CONSTRUCTION (Phase 3 step 2), not by this guard.
void assertBoundedWalk(AtomicView atomic) {
    int limit = atomic.size();
    for (int sourceId : sortedSourceIds(atomic)) {
        int cur = sourceId, hops = 0;
        while (atomic.role[cur] != DRAIN) {
            assert ++hops <= limit : "cycle suspected: source " + sourceId + " did not reach a drain";
            cur = onlyOutgoing(atomic, cur);
        }
    }
}
```

## Construction path: no specs — everything enters through the atomic view

The old spec/mint construction surface — `EdgeSpec`, `NodeSpec`, the `Crossing` record, `insertSpecs`,
`attachSourceToExistingNode`, `attachSourceToNewDrain`, and `mintChannel` — is **eliminated** (Phase 3). Their
job (declaring nodes/edges then folding them into the graph) is now done by building an `AtomicView` directly
and calling `update()`. One helper is explicitly **kept**: `insertChannel` (`RiverNetwork.java:255`) is the
`QuadTree`-population helper used by the **retained** `manageCutoffs`, so it stays as an internal helper and
`emitChannel` reuses it — it is **not** part of the deleted surface.

Blast radius (callers verified against the code):

| Consumer | Uses today | Rework |
| --- | --- | --- |
| `RiverNetwork` ctor / `insertSpecs` | builds from `NodeSpec`/`EdgeSpec` | new construction entry: build an `AtomicView` directly, then `update()` folds it in. `insertSpecs`, `NodeSpec`, `EdgeSpec`, `Crossing` **deleted** |
| `GlobalNetworkBuilder` (`:97-169,225-275`) | builds `NodeSpec`/`EdgeSpec` lists | emit atomic nodes/edges directly (with `ownFlow`/`anchorFlow`) into an `AtomicView`, then `update()` |
| `LocalDrainageTracer.attachSegment` (`:123-162`) | `split` + `attachSourceToExistingNode` + `attachSourceToNewDrain` | export each local channel as a **fresh-SOURCE** atomic edge attached via undirected **crossing** edges; the coast-drain branch (was `attachSourceToNewDrain`) becomes a **fresh DRAIN** atomic node. **All three** old methods eliminated (not just `split`) |
| `Meanders.simulate`/`relax` (`:56-66`) | take `NodeSpec`/`EdgeSpec` | take an `AtomicView` (or a network built from one); update the delegators |
| `RiverNetworkVisualizer` (incl. `seeNetwork(...)`, invoked from the `RiverNetwork` ctor under `DEBUG_RIVER_NET`) | renders from `NodeSpec`/`EdgeSpec` | retarget to the atomic view (or to `Channel`s post-`update()`) |
| `MeandersGoldenTest`, `debug/tests/MeandersTest`, `LocalRiverGoldenTest` (incl. `syntheticGlobalNetwork()`, which builds via the spec-taking ctor) | build specs | rewrite fixtures as atomic node/edge specs |

**Phasing.** `mintChannel` **survives through Phase 2** (reworked there to build `flow[]` — it is still the
legacy spec-construction entry while `GlobalNetworkBuilder` emits specs) and is **deleted in Phase 3** once
construction moves fully to the atomic view / `emitChannel`. `insertSpecs`, `attachSourceToExistingNode`,
`attachSourceToNewDrain`, `NodeSpec`, `EdgeSpec`, and `Crossing` are all deleted in Phase 3 alongside it.
`insertChannel` is **kept** throughout.

## Sequencing (risk order — each step lands with its own test before the next)

### Phase 1 — Land the seam (`viewAtomic()` / `update()`) + round-trip golden

- Add the atomic-view data structure and `viewAtomic()` / `update()` to `RiverNetwork.java`. No behavior
  change yet: nothing calls them in production.
- Add an explicit **single-outflow check** used by `update()` (iterate `nodes`; SOURCE 1-out, DRAIN 0-out,
  JUNCTION exactly-1-out — model on `Endpoint.java:53-59` degree + `Endpoint.Type`).
- **Round-trip golden** (new `src/test/java/.../meanders/` test, modeled on
  `MeandersGoldenTest.networkSignature` `:262-276` and reusing the `doubleToLongBits`-exact
  `LocalRiverGoldenTest.networkChecksum` idiom `:216-230`): build a network, **capture its signature first**
  (`update()` now mutates in place), then round-trip and compare —
  `long before = signature(net); AtomicView av = net.viewAtomic(); net.update(av); assert before == signature(net)`
  on **points + topology** (Phase 1 has no flow yet). Add a determinism-across-runs variant like the existing
  goldens.
- **The fixture must exercise the id-preservation rule, and the signature alone does not.**
  `networkSignature` (`:262-276`) checksums channel/node **counts** + point coordinates — it never inspects
  node ids, and the `oneEdge` SOURCE→DRAIN builder (`:287-294`) has no JUNCTION, so a trivial round trip would
  pass while `update()`'s "preserve SOURCE/DRAIN ids, re-assign JUNCTION-equivalents" rule (the load-bearing
  invariant for boundary-elevation lookups) went entirely untested. Require a fixture with **at least one
  JUNCTION confluence** in the *canonical* view — construct it directly from the node/edge specs (a shared
  JUNCTION `NodeSpec` fed by two `SOURCE→JUNCTION` edges with one `JUNCTION→DRAIN` edge out, i.e. an Endpoint
  of 2 incoming + 1 outgoing; the existing `MeandersGoldenTest` builders at `:302-339` produce only *crossing*
  edges, never a shared-node confluence, so a new spec is needed) — and add a **direct assertion that every
  SOURCE and DRAIN node keeps its
  canonical id** across the in-place round trip (compare the id sets by `Endpoint.Type`, not just the
  point/topology signature), plus a companion check that JUNCTION-equivalent ids are free to churn (the
  signature's `nodes=` count still matches). This is what makes the Phase-1 gate actually guard Finding-1's
  rule rather than incidentally pass. (This fixture is authored against `NodeSpec`/`EdgeSpec`, which still
  exist in Phase 1; it is rewritten as atomic node/edge specs in Phase 3 when the spec surface is deleted —
  see Construction path.)

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

**`flowAt(t)` + `reSample`/`keepOnly` flow maintenance:**

```java
double flowAt(double t) {
    // Mirrors bedElev(t) (Channel.java:120-124) but UNCONDITIONAL — flow[] is never null, so
    // there is no null-gated branch the way bedElev has.
    int i = (int) Math.floor(t);
    int lo = clamp(i, 0, flow.length - 1);
    int hi = clamp(i + 1, 0, flow.length - 1);
    double frac = t - i;
    return lerp(flow[lo], flow[hi], frac);
}

Channel reSample(...) {
    List<Vec2> newPoints = ...; // existing resample logic, unchanged
    double[] newFlow = new double[newPoints.size()];
    for (int i = 0; i < newPoints.size(); i++) {
        newFlow[i] = flowAt(tOf(newPoints.get(i))); // computed unconditionally — unlike bedElev's null check
    }
    return new Channel(newPoints, newFlow, /* ... */);
}

Channel keepOnly(int[] indexesToKeep) {
    double[] newFlow = new double[indexesToKeep.length];
    for (int i = 0; i < indexesToKeep.length; i++) {
        newFlow[i] = flow[indexesToKeep[i]]; // straight slice — kept points carry their own flow verbatim
    }
    return new Channel(/* points sliced the same way */, newFlow, /* ... */);
}
```

- Thread flow through construction. There are exactly **four** `new Channel(...)` sites (full-repo grep):
  `mintChannel:196`, `split:295`, `merge:341`, and `LocalDrainageTracer.buildLocalChannel:278` — all four
  currently width-taking, all four must be accounted for:
  - `RiverNetwork.mintChannel` (`:188-210`) builds `flow[]` — today it takes `startWidth`/`endWidth` and calls
    `new Channel(min(...))` + `setWidthProfile` (`:196-197`); rework it to build a per-point `flow[]` and go
    through the flow-taking constructor. `Endpoint` gains a `sourceFlow`/anchor seed field (SOURCE seed +
    DRAIN anchor). `mintChannel` **survives Phase 2** as the legacy spec-construction entry (still fed by
    `GlobalNetworkBuilder`'s specs); it is deleted in Phase 3 once construction moves to `emitChannel` /
    the atomic view (see Construction path).
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
  the large multi-tile rivers (the DRAIN anchors the near-drain clamp + lerp correction is *load-bearing* on) sit.
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
- Add `accumulateAndCorrectFlow` (reverse-topo **accumulation + clamp + near-drain lerp**, per the pinned
  pseudocode above) as a `RiverNetwork` method; call it where flow must be current (post-construction, and
  later post-rewire in Phase 3). Add the two tuning constants `DRAIN_FLOW_SMOOTH_STEP`/`DRAIN_FLOW_SMOOTH_MAX_NODES`
  to `HydrologyTuning` here.
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

     **Two-mark DFS pseudocode:**

     ```java
     void manageCollisions(AtomicView atomic) {
         CollisionGraph g = detectCrossings(atomic); // step 1: pinned undirected crossing edges,
                                                       // ascending atomic-node-id order per node

         boolean[] visited = new boolean[atomic.size()];
         boolean[] streamMarked = new boolean[atomic.size()];
         int[] outgoing = new int[atomic.size()];
         Arrays.fill(outgoing, -1); // -1 = unset

         for (int sourceId : sortedSourceIds(atomic)) { // sorted-source-id order (mandatory — Determinism)
             if (!visited[sourceId]) {
                 dfsVisit(atomic, g, sourceId, new ArrayDeque<>(), visited, streamMarked, outgoing);
             }
         }

         // step 3 — WRITE THE DFS ORIENTATION BACK INTO atomic.adjacency. The DFS decided trunk
         // selection in the local `outgoing[]` array; accumulateAndCorrectFlow / assertSingleOutflow /
         // update() all read atomic.adjacency, so it MUST be rebuilt from `outgoing[]` before they run,
         // or they would operate on the stale pre-DFS topology. Each streamMarked non-drain node gets
         // exactly its single promoted successor; drains (and unmarked/pruned nodes) get an empty list.
         rebuildAdjacencyFromOutgoing(atomic, outgoing, streamMarked); // step 3: kept = promoted edges only

         // step 4 — prune unmarked nodes; each dangling unmarked sub-path -> ABANDONED_RIVER unit
         // step 5 — accumulateAndCorrectFlow(atomic); then update() folds the oriented view back in place
     }

     // Returns true iff `node`'s branch reached a drain/streamMarked terminus (promoted, or was
     // already a terminus on entry). `stack` is the current path from the originating source.
     boolean dfsVisit(AtomicView atomic, CollisionGraph g, int node, Deque<Integer> stack,
                       boolean[] visited, boolean[] streamMarked, int[] outgoing) {
         if (streamMarked[node] || atomic.role[node] == DRAIN) return true; // already a terminus
         if (visited[node]) return false; // on-stack ancestor or exhausted branch — NOT promoted, NOT marked

         visited[node] = true;
         stack.push(node);

         // pinned per-node adjacency order: directed tree-successor edge first (if the node still
         // has one), then undirected crossing partners ascending by atomic node id (Determinism —
         // the exact tie-break trunk selection turns on)
         List<Integer> neighbors = new ArrayList<>();
         if (g.treeSuccessor[node] != NONE) neighbors.add(g.treeSuccessor[node]);
         neighbors.addAll(g.crossingPartners.get(node)); // already ascending by construction

         for (int next : neighbors) {
             if (streamMarked[next] || atomic.role[next] == DRAIN) {
                 // Promotion happens ONLY here: reaching a DRAIN or an already-streamMarked node.
                 // Promotes only the not-yet-streamMarked SUFFIX of the current stack — every node
                 // on `stack` is guaranteed not-yet-marked (an already-streamMarked node is never
                 // pushed, per the entry guard above), so the whole stack is that suffix.
                 promoteSuffix(stack, next, streamMarked, outgoing);
                 stack.pop();
                 return true;
             }
             if (!visited[next] && dfsVisit(atomic, g, next, stack, visited, streamMarked, outgoing)) {
                 stack.pop(); // promotion already happened for this whole stack, deeper in the recursion
                 return true;
             }
             // else: `next` is visited-but-unmarked (dead branch / ancestor) — try the next neighbor
         }

         stack.pop();
         return false; // exhausted — node stays merely visited, unpromoted; pruned in step 4
     }

     // Wires each node on `stack` (top-of-stack first) to its single outgoing edge toward
     // `terminus`, then streamMarks it. Each node's outgoing edge is set EXACTLY ONCE, at the
     // instant it transitions unmarked -> streamMarked; an already-streamMarked node's outgoing
     // edge is IMMUTABLE and is never revisited by this method.
     void promoteSuffix(Deque<Integer> stack, int terminus, boolean[] streamMarked, int[] outgoing) {
         int next = terminus;
         for (int node : stack) {                 // top-of-stack first
             assert !streamMarked[node];           // invariant: set-outgoing-exactly-once
             assert outgoing[node] == -1;
             outgoing[node] = next;
             streamMarked[node] = true;
             next = node;
         }
     }
     ```

  3. **Write the DFS orientation back into `atomic.adjacency` — kept edges = promoted
     (`streamMarked`-outgoing) edges only.** The DFS records trunk selection in a local `outgoing[]` array,
     but `accumulateAndCorrectFlow`/`assertSingleOutflow`/`update()` all read `atomic.adjacency`. So after the
     DFS, **rebuild `atomic.adjacency` from `outgoing[]`**: each `streamMarked` non-drain node's adjacency
     becomes exactly its single promoted successor; drains and pruned/unmarked nodes get an empty list. Keeping
     "promoted edges only" — not "any edge between two marked nodes" — is what avoids leaving un-oriented
     diamonds that would break `update()`. Accumulation and `update()` then run against the ORIENTED topology,
     never the stale pre-DFS one.
  4. Prune unmarked nodes. A branch that never reached a drain/`streamMarked` node is simply never promoted
     → its nodes stay unmarked and are pruned; walk each such pruned dangling sub-path into a polyline and
     record it as an `ABANDONED_RIVER` unit at collection time. So **nothing vanishes silently** — an
     unreachable channel is demoted to abandoned, consistent with the DL-013/DL-015 clean-drop philosophy.
     (Oxbows stay with `manageCutoffs`, out of scope.)
  5. `accumulateAndCorrectFlow` (reverse-topo accumulation + clamp + near-drain lerp over the now-oriented
     `adjacency`), then `update()`.
- **Migrate local insertion off the spec/attach surface.** `LocalDrainageTracer.attachSegment` (`:123-162`)
  stops calling `network.split(...)` **and** `attachSourceToExistingNode` **and** `attachSourceToNewDrain`
  (**all three** eliminated — the plan previously migrated only `split`). Instead each local channel is exported
  into the atomic view as an edge with a **fresh SOURCE node**, attached via **undirected crossing edges**; the
  coast-drain branch (formerly `attachSourceToNewDrain`) becomes a **fresh DRAIN atomic node**. A local channel
  whose DFS reaches a drain (or a `streamMarked` node) is kept; one whose branch reaches neither is demoted to
  an `ABANDONED_RIVER` unit (step 4) — never silently dropped. Run the atomic collision pass after local tracing
  in `LocalRiverProvider.buildTile` (integration point). Accept the attach-threshold shift from radius-based
  (`LOCAL_ATTACH_RADIUS=4.0`) to bed-overlap-based (folded into A2/C5).
- **Delete** the collision + spec/mint surface: `split`, `merge`, the `Crossing` record,
  `segmentAndResolve` + its union-find, `compareChannels`/`reachesDownstream`, `deleteOrphanDrains`,
  `mergePassFromSources`, the legacy scalar `width` accessor, **and** the construction surface
  `mintChannel`, `insertSpecs`, `attachSourceToExistingNode`, `attachSourceToNewDrain`, `NodeSpec`,
  `EdgeSpec` (see Construction path). **KEEP `insertChannel`** — the `QuadTree` helper used by the retained
  `manageCutoffs` and reused by `emitChannel`; it is not part of the deleted surface. Update the `Meanders`
  delegators (`:261-271`) and **retarget `RiverNetworkVisualizer`** (incl. `seeNetwork(...)`) off
  `NodeSpec`/`EdgeSpec` onto the atomic view (or `Channel`s post-`update()`).
- **Rewrite** — not retarget — the affected `MeandersGoldenTest` tests. `split`/`merge` cases (`:32-85`)
  test now-deleted primitives and are removed; `collisionCapture`/`sameContactPointCapture` (`:90-140`)
  assert OLD-algorithm structural outcomes (degree-2-junction elimination, `merge`-pass confluences,
  strongest-channel trunk) and must be **written from scratch** with new scenarios and assertions matching
  the two-mark DFS semantics (promoted-edge trunk selection by visitation order, abandoned-river demotion,
  single-outflow after prune) — this is new test authoring, not method-name substitution. Also update
  `debug/tests/MeandersTest.java`, which calls the deleted primitives directly, and rewrite
  `LocalRiverGoldenTest.syntheticGlobalNetwork()`, which builds via the spec-taking constructor, onto atomic
  node/edge specs.
- **Re-baseline** goldens again (topology/trunk output changes — the accepted A2 deferred risk:
  trunk-at-confluence is now DFS order, not width/flow strength).

## Critical files

- `hydrology/meanders/RiverNetwork.java` — seam (`viewAtomic` with `addNode` / **in-place** `update` +
  single-outflow check + `emitChannel` with inlined K1 guard), `accumulateAndCorrectFlow` (clamp + near-drain
  lerp), new `manageCollisions` (two-mark DFS + `adjacency` write-back); delete `split`/`merge`/`Crossing`/
  `segmentAndResolve`/`compareChannels`/`deleteOrphanDrains`/`mergePassFromSources` **and** the construction
  surface `mintChannel`/`insertSpecs`/`attachSourceToExistingNode`/`attachSourceToNewDrain`/`NodeSpec`/`EdgeSpec`.
  **KEEP `insertChannel`** (QuadTree helper for the retained `manageCutoffs`, reused by `emitChannel`).
- `hydrology/meanders/Channel.java` — `double[] flow` + derived `intakeWidth`/`dischargeWidth`/`depth`/
  `widthAt`; a **flow-taking** constructor (used by `mintChannel` + `buildLocalChannel`); maintain `flow[]`
  through the live paths `reSample`/`keepOnly` only. Delete the dead `add*`/`removeIndexes` (`:142-185`,
  no callers in `src/`) rather than extend flow upkeep into them.
- `hydrology/meanders/Endpoint.java` — `sourceFlow`/anchor seed field (SOURCE seed + DRAIN anchor). **No
  reserved id block** — id stability for boundary elevations comes from `update()`'s type-based
  SOURCE/DRAIN-id preservation.
- `hydrology/meanders/Meanders.java` — re-route width reads (`:145,175,202,111`); update delegators.
- `hydrology/LocalDrainageTracer.java` — migrate attach off `split()`/`attachSourceToExistingNode`/
  `attachSourceToNewDrain` (all three) to atomic-edge (fresh SOURCE) + crossing edges, coast-drain branch to a
  fresh DRAIN atomic node; rework `buildLocalChannel:278` to build a real per-point `flow[]` from `flow[cell]`
  and use the flow-taking `Channel` constructor (not the width bridge).
- `hydrology/LocalRiverProvider.java` — run the atomic collision pass after local tracing (accept channel-id
  churn — the local/global distinction is not needed for the elevation consumer post-seam; see Findings). The
  `debug`-only local/global PNG split (`stages.channels`/`localChannels`, `:283-291`, fed by the pre-trace
  channel-id snapshot `:238-246`) **does** break under the churn — see Deferred.
- `hydrology/GlobalNetworkBuilder.java` / `GlobalRiverProvider` — carry **flow** (not width) in `EdgeSpec`;
  `GlobalRiverProvider` persists a 4th flow channel + `getFlow` accessor (`getTile:282-295`, `getWidth:109-111`).
- `config/TensorLayout.java` — bump `GLOBAL_RIVER_CHANNELS` 3→4 for the persisted global-tile flow channel.
- `config/HydrologyTuning.java` — reuse `widthFromFlow`, `FLOW_PER_CELL_LOCAL/GLOBAL`,
  `FLOW_INITIAL_LOCAL/GLOBAL` (read-only); **add** two property-overridable constants for the near-drain lerp:
  `DRAIN_FLOW_SMOOTH_STEP = 10` (min jump that triggers smoothing / per-step ceiling) and
  `DRAIN_FLOW_SMOOTH_MAX_NODES = 20` (max mainstem nodes the ramp spans).
- `hydrology/meanders/RiverNetworkVisualizer.java` — retarget off `NodeSpec`/`EdgeSpec` (incl. `seeNetwork(...)`,
  called from the `RiverNetwork` ctor under `DEBUG_RIVER_NET`) onto the atomic view or `Channel`s post-`update()`.
- Tests: new round-trip golden (JUNCTION-confluence fixture + SOURCE/DRAIN id-stability assertion; snapshot
  signature before the in-place round trip); re-baseline `MeandersGoldenTest.java` (`:255`) &
  `GlobalRiverGoldenTest.java` (`:112`); rewrite spec-built fixtures (`MeandersGoldenTest`,
  `LocalRiverGoldenTest.syntheticGlobalNetwork()`) onto atomic node/edge specs; update
  `debug/tests/MeandersTest.java` and `debug/tests/LocalRiverTest.java` (its `04_global_channels`/
  `05_local_channels` split, `:86-87`).

## Reuse (don't reinvent)

- `HydrologyTuning.widthFromFlow` (`:153-156`) — the width law.
- `Channel.bedElev(t)` (`:120-124`) — the pattern to mirror for `flowAt(t)` resample-alignment.
- `ChannelGeometry.channelsOverlap` (`hydrology/ChannelGeometry.java:35-37`) — the bed-overlap crossing test.
- `RiverNetwork.mintChannel` single-outflow guard (`:204-207`) — the K1 enforcement logic to **inline into
  `emitChannel`** (mintChannel itself is deleted in Phase 3); `emitChannel` applies the same guard while
  wiring endpoints directly into `this`.
- `MeandersGoldenTest.networkSignature` (`:262-276`) / `LocalRiverGoldenTest.networkChecksum` (`:216-230`) /
  `reachesDrain` walkers — golden + invariant idioms to model the new tests on.

## Determinism (must-hold)

Production draws no RNG. Topology determinism rests on: **`update()`'s type-based SOURCE/DRAIN-id
preservation** (boundary-elevation ids survive re-assignment without any reserved id range);
**sorted-by-id channel iteration in `viewAtomic()`** (atomic-id assignment must not depend on HashMap order);
**flow accumulation over the atomic adjacency** rather than `Endpoint.incoming` (a `HashSet`), so the
confluence sum order is pinned and the `doubleToLongBits` goldens stay bit-stable; and **sorted source ids +
sorted adjacency** in the Phase-3 **two-mark DFS** (replaces the old channel-id-sort + width/id
`compareChannels` total order). "Sorted adjacency" is pinned to a total order per Phase 3 step 2 — **directed
tree-successor edge first (if present), then undirected crossing partners ascending by atomic node id** — so
the visitation order at a mixed node (directed successor + crossing edges) never depends on HashMap iteration;
this is the exact tie-break trunk selection turns on. The two-mark DFS is deterministic and acyclic by
construction — no RNG, no cycle guard. The DFS's promoted `outgoing[]` is then **written back into
`atomic.adjacency`** (Phase 3 step 3) so `accumulateAndCorrectFlow` and `update()` read the oriented topology,
not the stale pre-DFS one — accumulation and emission are otherwise driven off a topology the DFS never
decided. Golden signatures already sort channels by id, so per-channel output is order-independent; only
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
- Round-trip assertion is the core Phase-1 gate (`update()` mutates in place, so snapshot first):
  `long before = signature(net); net.update(net.viewAtomic()); assert before == signature(net)`.

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
  before/after channel-id snapshot (`:238-246`) no longer identifies local channels once `update()` re-assigns
  every id, so the `debug`-only `stages.channels`/`stages.localChannels` partition (`:283-291`) and the
  `04_global_channels`/`05_local_channels` PNGs (`debug/tests/LocalRiverTest.java:86-87`) go silently wrong.
  Consistent with the plan's decision to add **no** local/global provenance tag, this is accepted as a
  debug-only regression; `LocalRiverTest.java` and the `buildTile` split must be updated (drop the split, or
  re-derive it another way) as part of Phase 3 rather than engineered around with a tag. No production path is
  affected (the elevation consumer is churn-tolerant — see Findings).
- **Interior resample-density drift (accepted).** Per-point `ownFlow` is per-spline-point, so denser
  resampling accumulates slightly more flow → interior width breathes a little as `reSample` changes point
  density across sim steps. Decision (a): **accept it** — determinism is unaffected (point count is
  seed-deterministic, so the same seed reproduces the same flow bit-for-bit), and the clamp + near-drain lerp
  no longer normalizes it away (it never was its job). A per-arc-length `ownFlow` would remove the drift but is
  orthogonal to this plan and deferred.
- **No green baseline** — suite is 6/17 red; sequence strictly so each step lands with its own passing test.
