# RiverNetwork's mutable QuadTree: R-tree for crossings, a mutable grid for cutoffs

Date: 2026-09-03
Status: proposed — nothing here has been implemented
Branch: `feature/hydrology`
Measured at: `a68bfc6`

## Problem

`RiverNetwork` keeps one mutable spatial index,

```java
private final QuadTree<Channel.ChannelPt> quadTree =
        new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});
```

and uses it at two call sites, both of which are hot in the collision/migration step loop. Profiling
attributes the network step's cost mainly to these two.

**`detectCrossings`** (`RiverNetwork.java:589-633`, called from `manageCollisions`): `quadTree.clear()`,
insert every point of every channel, then for every point run a circle query
(`getPointsInCircle(pointA, halfA + maxHalf)`) against points inserted earlier in the same call. The
whole point set is known before the first query — nothing is removed mid-call.

**`manageCutoffs`** (`RiverNetwork.java:663-695`): `quadTree.clear()`, insert every point of one channel,
then walk the channel's points in order. At each surviving point it queries nearby points
(`getPtsCloseTo`, radius `sqrt(width)`), and any hit far enough ahead in index order triggers
`cutRiverSection`, which removes the cut-away points from the tree. Removal here is not a passive
cleanup — a point removed by an earlier cut is skipped by the `!quadTree.containsPoint(...)` guard when
the walk reaches it, which is how a cut shortens the kept path.

Both call sites pay `QuadTree`'s node-allocating build and its `ReentrantReadWriteLock` (guarding
concurrent readers of a *finished* tree, per `math/ds/README.md`) for a tree that is rebuilt from scratch
on every call and never read concurrently — `math/ds/README.md`'s own invariant is that per-tile hydrology
callers build and mutate one `QuadTree`/`RiverNetwork` per tile on a single thread. The lock buys nothing
here.

One correctness detail governs every replacement: `QuadTree.getPointsInCircle` is not a coarse box query
— `SpatialIndexCircle.containsPoint` (`SpatialIndexCircle.java:42-48`) does an exact squared-distance test
at the leaf. `detectCrossings` and `AtomicView.resolveCrossingEdges` both add their own exact test on top
of a coarser candidate search (`channelsOverlap`, `segmentCrossing`), but `manageCutoffs` does not — it
treats a `getPtsCloseTo` hit as final. Any replacement whose candidate search is a superset (a box, a
bucket) must add the missing exact distance test itself, or `manageCutoffs` starts cutting on points that
are diagonally inside a bucket/box but outside the true circle.

## Decisions

**D1. `detectCrossings` moves to `ImmutableRTree`; no new spatial-index class.**

The call already fits `ImmutableRTree`'s contract exactly — build once from a fully-known point set,
query many times, discard — the same shape `SpatialIndexBenchmark.java` already measured R-tree beating
`QuadTree` on. `ImmutableRTree.queryContaining(queryPoint, inflateRadius, out)` does an exact
`element.containsPointInflated` test at the leaf (`ImmutableRTree.java:391`), so storing each
`Channel.ChannelPt` as a zero-radius circle reproduces `getPointsInCircle` exactly:
`containsPointInflated` reduces to `distance(query, point) <= 0 + inflateRadius`, and passing
`inflateRadius = halfA + maxHalf` is bit-for-bit the same bound `detectCrossings` computes today. The
adapter is a private record local to `RiverNetwork.java`, mirroring `SpatialIndexBenchmark`'s
`PrimitivePoint`:

```java
private record CrossingPoint(Channel.ChannelPt pt) implements SpatialIndexCircle {
    @Override public double[] getCenter() { return pt.toArray(); }
    @Override public double getRadius() { return 0.0; }
}
```

`detectCrossings` builds `new ImmutableRTree<>(points, prototype)` once per call in place of
`quadTree.clear()` + the insert loop, and replaces `quadTree.getPointsInCircle(pointA, halfA + maxHalf)`
with `rtree.queryContaining(pointA, halfA + maxHalf, buffer)`. `nearby.sort(null)`, `channelsOverlap`, and
`bestByPartner` are unchanged — only candidate generation moves.

Rejected: a mutable hash grid (D2) for this site too. It would work, but it is new code solving a problem
`ImmutableRTree` already solves with none — this call never removes a point mid-search, so nothing needs
the mutation D2 exists for.

Rejected: generalizing `AtomicView.crossingCandidatePairs` (the sweep-line already backing
`resolveCrossingEdges`) into a shared utility. The sweep's AVL-tree-map machinery earns its complexity
handling wildly non-uniform segment lengths after meander migration; this call's points are sampled at
near-uniform `DX` spacing with a query radius bounded by a fairly stable width range, so that strength
buys nothing here. It would also touch `AtomicView.java` for a call site that does not need to, which is
strictly more blast radius than D1 for no benefit.

**D2. `manageCutoffs` moves to a new mutable `math/ds/SpatialHashGrid`.**

`ImmutableRTree` cannot serve this site: it has no removal path by design (that absence is exactly what
lets it skip `QuadTree`'s lock), and rebuilding a whole R-tree after every single cut would cost far more
than the STR build it exists to amortize. This site genuinely needs live `insert`/`remove` interleaved
with queries, so it needs a structure built for that.

A precompute-once-and-filter approach (find every candidate pair up front, walk the channel checking a
`boolean[] removed` instead of live removal) was the first design considered, and is provably equivalent —
removal here only ever *hides* an already-fixed geometric relationship, since cutting a channel doesn't
move any point, so precomputing against the full point set and filtering at use time gives the same live
result set at every step. It was rejected anyway: it trades a structure with genuine removal for an
equivalence argument a reviewer has to re-derive, for no performance benefit over a grid that just
supports removal directly.

`SpatialHashGrid<T extends SpatialIndexPoint>` mirrors `QuadTree`'s mutable surface — `insertPoint`,
`removePoint`, `clear`, `containsPoint`, `getPointsInCircle` — so `manageCutoffs`'s call sites barely
change: the field's declared type changes from `QuadTree<Channel.ChannelPt>` to
`SpatialHashGrid<Channel.ChannelPt>`, and the constructor call changes; `insertChannelInQuadTree`,
`cutRiverSection`, `getPtsCloseTo`, and the `id`-walk in `manageCutoffs` are otherwise untouched.

Internally, `getPointsInCircle` buckets by `floor(coord / cellSize)`, scans the query circle's covering
cells, and — per the Problem section's correctness note — applies an explicit
`deltaX * deltaX + deltaZ * deltaZ <= radius * radius` test per candidate before including it, matching
`SpatialIndexCircle.containsPoint`'s exact test rather than returning the bucket scan's superset.

Cell size is a constructor parameter, not a hardcoded constant — `SpatialHashGrid` is otherwise
general-purpose and other callers may have a different natural scale. `RiverNetwork` passes
`Math.sqrt(HydrologyTuning.maxNativeWidth())` rounded up to the next whole unit: `manageCutoffs`'s own
query radius is `sqrt(width)`, so this keeps the expected cell occupancy near one query's worth of area
across the tuning range, without depending on any one channel's current width. The benchmark in
Verification is what confirms this choice rather than a worse one; retuning it later does not need a
design change.

No lock: `QuadTree` pays its `ReentrantReadWriteLock` on every `insertPoint`/`removePoint`/`clear` call
regardless, but per `math/ds/README.md`'s own invariant that lock is never contended — per-tile hydrology
callers build and mutate one `QuadTree`/`RiverNetwork` per tile on a single thread, so every acquisition
here is uncontended overhead bought for a guarantee nothing needs. `SpatialHashGrid` states plainly, same
as the existing two immutable indexes' README section does, that it assumes the same single-writer-per-tile
usage and carries no lock.

**D3. `AtomicView.resolveCrossingEdges` and `crossingCandidatePairs` are untouched.**

Neither call site this design changes shares its workload — `resolveCrossingEdges` handles genuinely
non-uniform segment lengths across the whole network in one batch with an exact intersection test after,
which is precisely what its sweep-line is built for. It was never named as part of the bottleneck, D1
found no benefit to reusing it, and touching it risks `RiverNetworkSeamGoldenTest`'s bit-exact planarization
goldens for zero gain.

## Files

| File | Change |
| ---- | ------ |
| `math/ds/SpatialHashGrid.java` | New. D2: mutable bucketed point index with exact circle queries. |
| `math/ds/CLAUDE.md` | Row for the new class. |
| `math/ds/README.md` | Extends the "Three spatial-index implementations" overview to four; states `SpatialHashGrid`'s access pattern and no-lock rationale (D2) alongside the existing ones. |
| `network/RiverNetwork.java` | `quadTree` field removed; `detectCrossings` moves to `ImmutableRTree` (D1); `manageCutoffs`/`getPtsCloseTo`/`insertChannelInQuadTree`/`cutRiverSection`/`beginStep` move to `SpatialHashGrid` (D2). |
| `debug/tests/SpatialIndexBenchmark.java` | Extended (not a new file, per `structural.md`'s test-organization default) with a correctness cross-check + queries/sec section for each of D1 and D2, mirroring the existing primitive-index sections. |
| `hydrology/meanders/MeandersGoldenTest.java` | No new file; existing crossing/cutoff-shaped cases are the regression gate (see Verification). |

## Out of scope

- **`QuadTree` itself is not touched or removed.** `LocalDrainageTracer.java:78-79` constructs its own
  `QuadTree<CoordPoint>` independently of `RiverNetwork`; the class stays exactly as it is for that and any
  other caller.
- **`ImmutableQuadTree`'s known `findSection` root-alignment bug** (`math/ds/README.md`) is unrelated and
  untouched.
- **No change to the collision/BFS/capture algorithm** downstream of `detectCrossings` (`reverseBfsCapture`,
  `buildOriented`, `update`) — only candidate generation feeding it changes.
- **No change to `channelsOverlap`, `segmentCrossing`, or any other exact-overlap math.**

## Verification

`gradle spotlessApply`, then `gradle build`.

Then `gradle test`, compared against the failure baseline in root `CLAUDE.md` (currently 102 tests / 9
failed / 1 skipped at `df7ca2e`, itself marked as a claim to re-verify, not a fact) — matching the actual
failure *messages* in `build/test-results/test/*.xml`, not just which test names fail, is what proves this
change left crossing/cutoff output untouched. `MeandersGoldenTest` is the direct regression gate for both
D1 and D2 — it already exercises crossing-to-confluence merges and cutoff pruning; its one pre-existing
failure (`independentCrossingsAreNotMerged`, unreachable per `network/README.md`'s planarization
invariant) must remain the only difference from baseline.

`SpatialIndexBenchmark.java` gets one new section per call site, following its existing pattern exactly:
a brute-force cross-check before any timing (throws on mismatch, since a benchmark of a broken index is
worthless), then queries/sec for `detectCrossings`'s `ImmutableRTree` path against the current `QuadTree`
path, and an equivalent op-mix benchmark (insert/remove/query, not just queries/sec) for `manageCutoffs`'s
`SpatialHashGrid` against `QuadTree`, over one real tile's channel set.

Manual visual check: `riverTest` and `meandersTest` PNG dumps for at least one tile with a known cutoff or
crossing, compared before/after by eye — the golden suite gates topology, not the visual shape of a cut
meander loop, and this is the project's established way of catching that class of regression.
