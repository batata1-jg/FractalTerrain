# Primitive storage: replacing `RiverProvider`'s per-tile `ImmutableRTree` with `PrimitiveGrid`

Date: 2026-09-17
Status: proposed — nothing here implemented. Part 1 (storage, below) and Part 2 (carve dispatch,
further below) are both covered by this document; Part 3 (whether shell-prep-in-`RiverProvider` /
bed-detail-in-`PopulateNoiseStep` is itself the right split) remains deferred — see Part 2's own
"Explicitly out of scope" section.
Branch: `feature/hydrology`
Measured at: `8bf6885`

## Problem

`RiverProvider` caches one `ImmutableRTree<HydrologicalPrimitive>` per tile
(`NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>> primitives`), stabbed once per
chunk by `HydrologyProfileInprinter.prefetchChunk` to feed `PopulateNoiseStep.fineGrainedPrimitivePass`'s
bed carve. The project owner wants to replace this with a persisted array-based structure — primarily
for persistence-format unification (riding the same array/`Persistable` machinery the rest of the
per-tile stores use, rather than the R-tree's bespoke build), simplicity (dropping the R-tree's
node-tree machinery for a problem that turns out to be chunk-grid-shaped), and performance, though nothing
in the current code documents the R-tree's stab as an actual bottleneck — it runs once per chunk, never
per-pixel.

This document is the result of three parallel `decision-critic` stress-test passes (spatial/merge
correctness, grid geometry/tile frames, persistence/memory/perf) followed by several rounds of
clarification, recorded here as the decisions below rather than re-litigated.

## Decisions

**D1. New class `PrimitiveGrid` (`hydrology/providers/PrimitiveGrid.java`), not a repurposed
`FloatTensor`.**

The project owner's original framing used `FloatTensor`'s int-bitcast trick (`Float.floatToRawIntBits`)
to store an index tensor. That was dropped in favor of a dedicated `Persistable` class, for two reasons:
the flat primitive array and the per-primitive LUT (D5) have no existing persistence path either way, so
only the tensor piece would have ridden `FloatTensor`'s format for free; and a bit-reinterpreted index
sitting in a `float[]` is a landmine the moment any generic float-tensor code (`Blur`, `Interpolation`,
`FloatTensor.addFrom`) ever touches it — a negative or otherwise unlucky index can land on a NaN bit
pattern and silently canonicalize. `PrimitiveGrid` owns its own binary format (magic `"PGR1"`, mirroring
`ImmutableRTree`'s own `"IRT1"`), so nothing outside it ever needs to know the packing convention.

`PrimitiveGrid` implements `SpatialIndex<HydrologicalPrimitive>` (`numEntries()`, `getAllEntries()`) and
`Persistable<PrimitiveGrid>`, which is all `NonIntersectingSpatialIndex<I extends SpatialIndex<?> &
Persistable<I>>` requires — it drops into `RiverProvider.primitives`'s existing type parameter in place
of `ImmutableRTree<HydrologicalPrimitive>` with no change to `NonIntersectingSpatialIndex`, `Storage`, or
the existing 50 MB soft cap (`RiverProvider.PRIMITIVE_CACHE_LIMIT_BYTES`) and eviction wiring.

Rejected: genericizing `PrimitiveGrid` into `math/ds/` the way `ImmutableRTree<T extends
SpatialIndexShape>` is generic. The LUT (D5) is meaningless for any shape type but
`HydrologicalPrimitive`, and there is exactly one consumer — genericizing it would be abstraction with no
second caller to justify it.

**D2. Grid is chunk-aligned: 160×160 core cells per tile, one cell per Minecraft chunk, plus a 20-cell
halo on every side (200×200 stored).**

`HydrologyTileGeometry.GRID = 512` is in **relief-pixel** units, and `FractalTerrainConfig
.GLOBAL_SCALE_CORRECTION = 5` is blocks per relief-pixel (confirmed against `ChunkChannelFill`'s own doc
comment and `PopulateNoiseStep`'s block→relief-pixel conversion, both load-bearing production code, not
just documentation). So one tile spans `512 × 5 = 2560` blocks = `2560 / 16 = 160` Minecraft chunks per
side — the project owner's original `512 × 5 / 16` arithmetic was correct throughout; the reviewers'
initial "should be 32×32" expectation was itself wrong, traced to `ARCHITECTURE.md`'s coordinate-frame
table claiming native-px is "1:1 with block-px inside a tile" and `tileX = blockX >> 9` — both stale
against `GLOBAL_SCALE_CORRECTION`'s actual use everywhere else in the codebase. **This is a real
documentation bug in `ARCHITECTURE.md`, independent of this spec; flagging it here so it isn't lost, but
fixing it is not part of this change.**

A primitive's influence can reach `HydrologyTuning.MAX_INFLUENCE_RADIUS = 64` relief-px = 320 blocks = 20
chunk-cells past its own center. Today's `ImmutableRTree` handles a primitive near a tile edge reaching
into the neighbor tile for free, because it is a pure geometric structure in world coordinates —
tile-boundary-agnostic by construction. A flat 160×160 grid cannot reproduce that: fanout (D3) is confined
to one tile's own array, so a primitive near an edge would lose whatever part of its reach crosses the
boundary. Fix, mirroring the existing `HydrologyTileGeometry.PAD`/`PADDED` pattern used for the elevation
lattice: pad the grid by the same 20-cell halo on every side (`200 × 200` stored, local cell coordinates
range `[-20, 180)` on each axis). Cell size is exactly 16 blocks (1 Minecraft chunk) — no fractional block
alignment anywhere.

**D3. Cell contents are CSR (compressed-sparse-row) buckets of arbitrary length, not one primitive per
cell.**

A carve lattice point is routinely reached by several overlapping primitives (a river bed, its floodplain
taper, a confluence bowl gated on the river's own depth) — this is the documented, load-bearing merge law
`RiverInfluenceCarve.computeRiverGrid` runs (D6 changes how that merge works, not whether multiple
primitives can reach one point). A single index per cell would structurally discard that. Instead:

- `int[] cellOffsets`, length `200*200 + 1` — standard CSR offsets.
- `int[] cellValues` — flat concatenation of every cell's bucket of primitive indices (indices into
  `primitives`, D4).

Built once per tile (warm tier, per `ARCHITECTURE.md`'s hot/cold line — moderate abstraction acceptable
here), by a two-pass counting-sort fill with no per-cell allocation: a count pass over each primitive's
MBR-derived, halo-clamped cell range; a prefix-sum into `cellOffsets`; a second fill pass writing into
`cellValues` via a scratch cursor array. Each primitive fans into every cell its MBR touches — reusing
the same MBR (`writeMbrInto`) `ImmutableRTree` already computes, and the same "MBR is a conservative
superset, over-inclusion is fine" precedent `ImmutableRTree`'s own docs already establish.

**D4. `primitives: HydrologicalPrimitive[]` stays a flat, collect-order array** — the direct equivalent
of `ImmutableRTree.elements`, indexed by the same integer that `cellValues` stores. `numEntries()` /
`getAllEntries()` read off it directly, unchanged in meaning from today.

**D5. `lut: float[][]`, parallel to `primitives` — a per-primitive precomputed table, built once at
tile-build time, empty for primitive types that don't need one.**

Today, `RosgenProfile.sampleCrossSection` tabulates a primitive's cross-section fresh every time
`computeRiverGrid` runs — three times per tile for the shell carve, plus once per chunk for the bed carve
(so the same primitive gets re-tabulated on every one of the ~1024 chunks in its tile that touch it).
Precomputing it once per primitive and storing it removes that redundant work. The LUT's meaning is not
fixed across primitive types (a river's cross-section table is not a bowl's radial profile, if radial
primitives end up needing one at all) — `lut[i]` is whatever primitive `i`'s own family needs, including
possibly nothing.

**D6. The carve's `dist[]`/`radialDist[]` split collapses into one shared `dist[]`/`acc[]` for every
primitive family; the river-pass "radial cannot overwrite a claimed river bed" gate is dropped; order
(the order primitives appear in a cell's/query's list) decides outcomes, with no family-based
special-casing.**

Today `computeRiverGrid` keeps two buffers apart specifically because a river's banded-rectangle distance
scale and a bowl/cone's disc-radius scale are not the same unit, and because `dist[]` is live data
published to `Types.RIVER_DIST` that must survive the radial pass untouched. Both reasons go away under
one convention: every primitive's distance function is redefined to be a soft `[0,1]` "influence
closeness" value — **1 means no influence (outside the footprint), 0 means fully affected** — rather than
a strict metric, so a rectangle-footprint river and a disc-footprint bowl can share one array as long as
each honors the convention. The existing sequential smoothed-min recurrence (seed, smoothstep update,
blend-toward-current-primitive) is unchanged in its own logic; it now just runs once over every primitive
regardless of family, with no separate radial pass and no weight-gate protecting a river bed from being
overwritten by a weaker radial primitive. `typeMask` claiming follows the same unified rule: whichever
primitive is currently driving `acc[]` at a point stamps `typeMask` there, uniformly.

`Types.RIVER_DIST` is published from the same shared `dist[]` post-carve, so it now reflects whichever
primitive (river or radial) won each cell — a broadened meaning from today's river-pass-only guarantee.
This is confirmed intended, not an oversight to patch downstream.

Rejected: keeping the two-buffer split and only unifying the distance *formula*. The project owner's
explicit direction was to drop the separate buffer entirely, not just normalize units within it.

**D7. `RiverProvider`'s existing general geometric query surface
(`queryInfluence`/`anyInfluencingPrimitive`, and `ImmutableRTree.anyContaining`/`queryContaining`
underneath it) is removed, not reimplemented on `PrimitiveGrid`.**

`PrimitiveGrid` was at risk of needing to replicate `ImmutableRTree`'s full arbitrary-point stab API,
because `HydrologyProfilePainter.insideChannel` and two debug-only callers (`Infinite3DVisualizer`'s
`debugRiver`, `SpatialIndexBenchmark`) all go through it. Tracing actual callers rather than trusting
`profile/README.md`'s description of `insideChannel` as a live painter check found it is in fact only
called from a debug class today, and it is scheduled for removal anyway: under the new merge law, "is
this point in the river bed" is answered directly by reading the already-published `Types.RIVER_DIST`
channel and checking `< 0.25` (the existing `BED_EDGE` breakpoint), no spatial-index query at all. That
leaves zero production callers of the old query surface, so `PrimitiveGrid` carries exactly one query
method:

```
void primitivesInCell(int localCellX, int localCellZ, List<HydrologicalPrimitive> out)
```

— a bounds-check against `[-20, 180)` (no-op outside it) followed by a direct CSR-offset walk. No
dedup logic is needed: within one query, at most one tile contributes at most one cell's bucket, and a
given primitive lives in exactly one tile's storage.

`RiverProvider`'s replacement for `HydrologyProfileInprinter.prefetchChunk` no longer needs a
center-point-plus-radius query either — a chunk position (`ChunkPos.x`/`.z`) maps directly onto a cell
coordinate, no relief-pixel conversion needed at query time (that conversion only mattered for the old
point-stab). Cross-tile reach still goes through `NonIntersectingSpatialIndex.forEachTileWithin`
unmodified, called with `radius = HydrologyTuning.MAX_INFLUENCE_RADIUS` (the tile-sweep radius, deciding
which neighboring tiles to even visit) — the per-tile visitor changes from "stab this tile's R-tree at
this world point" to "translate the query chunk into this tile's local (possibly halo, possibly
out-of-range) cell coordinate using the `tileOriginX/Z` the visitor already receives, then call
`primitivesInCell`."

`forEachTileWithin`/`TensorWindow` themselves are **not** changed to use an overlapping window
(`size=200, stride=160`) baked in at construction, even though that would compute an identical tile set
for this one call site — `HydrologyProfilePainter.insideChannel`'s existing `anyInfluencingPrimitive` call
uses a different, call-site-specific radius (`HydrologyTuning.maxNativeWidth() / 2.0`), proving the
generic radius parameter is genuinely used elsewhere, not a vestige to design away. (`insideChannel`
itself is removed under this same decision, per D7's first paragraph — this is about not changing shared
`NonIntersectingSpatialIndex` machinery on the strength of a caller that's also being deleted.)

Rejected: keeping a slow linear-scan fallback for `anyContaining`/`queryContaining` purely so
`Infinite3DVisualizer`/`SpatialIndexBenchmark` keep compiling. The project owner chose to let those
debug-only call sites break and be fixed later, consistent with `debug/` being exempt from the same
hygiene bar as production code elsewhere in this project.

## Consequences and residual risk

- **Expected test breakage, not regression.** `RadialCarveTest` and the `AbandonedRiverPrimitive`
  dual shell+bed carve golden (locked in at `a2a876c`) encode today's gated, buffer-separated behavior
  and will need rewriting to match D6's unified pass — this is the point of the change. Any golden test
  asserting carved elevation pixel values is a candidate for re-baselining for the same reason. Re-verify
  the test suite baseline at `HEAD` before attributing any other red test to this change — see root
  `CLAUDE.md`'s Test section; the baseline has broken and been repaired multiple times and is not a fact
  to trust unmeasured.
- **`Infinite3DVisualizer`'s `debugRiver` and `SpatialIndexBenchmark` stop compiling** once
  `queryInfluence`/`anyInfluencingPrimitive`/`ImmutableRTree.anyContaining`/`queryContaining` are removed
  (D7). Left broken deliberately; not this change's responsibility to fix. Separately, `debugRiver` is
  currently mismarked as not deprecated even though `debugHydroZones` already supersedes it — worth a
  follow-up annotation fix, not part of this spec.
- **No manual cache migration.** `PrimitiveGrid` gets a new self-describing format (`"PGR1"`); bad-magic
  on deserialize throws, which `Storage` already converts into a recompute. Recommend bumping
  `RiverProvider`'s tile-store name (`"local_river_units_v3"` → `"_v4"`) as belt-and-suspenders, matching
  the precedent already set when `SourcePrimitive`'s payload grew.
- **New coverage needed, as integration-style tests over a real synthetic fixture (no mocking
  `PrimitiveGrid`), matching this codebase's existing golden-test style:** fanout correctness (a primitive
  registers into every cell its MBR reaches, halo included), the cross-tile halo lookup (a primitive near
  tile A's edge is found by a query chunk in tile B), and a `PrimitiveGrid` serialize/deserialize
  round-trip.
- **Halo storage overhead.** `200×200` vs. a hypothetical exact `160×160` is ~1.56× the cell count. Traded
  deliberately for keeping the per-chunk query to one lookup (no runtime multi-cell union), matching the
  hot-path discipline `PopulateNoiseStep.fineGrainedPrimitivePass` is already held to.
- **Coupling to watch:** the 20-cell halo (D2) and the `MAX_INFLUENCE_RADIUS` tile-sweep radius (D7) must
  stay derived from the same `HydrologyTuning.MAX_INFLUENCE_RADIUS` constant. They are mathematically
  required to agree (the halo is exactly what makes the tile-sweep radius sufficient); expressing them as
  two independent literals instead of one shared constant would let them drift apart silently.

## Part 2: finishing `8bf6885`'s carve-dispatch refactor

### Problem

`RiverInfluenceCarve` conflates two structurally different carve passes — shell/influence
(`carveRiverInfluenceGrid`, cut-only into `elevs[]`) and bed (`computeRiverGrid`, the `acc`
triple-buffer merge) — and dispatches each primitive's contribution through hand-written `instanceof`
chains that vary per family (`instanceof RiverPrimitive`, then `instanceof RadialPrimitive`; a third
family, `HistoricPrimitive`'s sheds, matches neither and silently carves nothing — the "gap" the
repo-root `hydrology-carve-situation.md` already documents). The project owner judged this a bad shape
to keep extending.

Commit `8bf6885` ("new carving bed system") began fixing this by hand: it moved `InfluenceCarver`/
`RiverInfluenceCarve` into a new `hydrology.carvers` package, added a new `RiverBedCarver` interface
extracting the rectangle/tangent bed-carve math out of `RiverInfluenceCarve.carveRiverPrimitive`, and
started adding a per-primitive `carveBed(...)` method. It is committed at `HEAD` in a **non-compiling**
state — four call sites fail (`InfluenceCarver.java:39`'s dangling method, `RiverPrimitive.java:62`'s
malformed parameter list, `RiverInfluenceCarve.java:61`'s now-unresolvable `getInfluenceCarver()` call
on a bare `HydrologicalPrimitive`, and `RosgenCarvedPrimitiveCodecTest.java`'s construction of a
`RiverPrimitive` as a `RosgenCarvedPrimitive`), all four tracing to one root cause: `RiverPrimitive`
dropped `implements RosgenCarvedPrimitive` as an apparent accidental side effect of starting the
`carveBed` work, not a deliberate design choice. This section is the target end-state for finishing
that work coherently, arrived at via three parallel `decision-critic` stress-test passes (dispatch
closure across every real call site; whether `AbandonedRiverPrimitive` can defensibly gain a tangent;
naming and `HydrologyProfileInprinter`'s fate) plus the project owner's own direction throughout.

### Decisions

**D8. Restore `RiverPrimitive implements RosgenCarvedPrimitive`.** Not a design choice — undoing the
accidental drop that causes three of the four current compile breaks. `RiverPrimitive` keeps every
field `RosgenCarvedPrimitive` needs (`normal`, `width`, `curvature`, `elevation`, `rosgenType`, `seed`);
nothing else about the record changes.

**D9. `getProfile()` is not restored as a general `HydrologicalPrimitive` method.** Once D10/D11 extract
the carve math into `BedCarver`/`ShellCarver`, nothing calls `getProfile()` polymorphically anymore —
the two remaining call sites (`RiverPrimitive`'s deprecated `h()`, and wherever `carveRosgenInfluence`'s
profile lookup lands inside `ShellCarver`) inline `RosgenProfile.of(RiverPrimitive.RosgenType
.orDefault(rosgenType()))` directly instead of going through an interface method whose only remaining
purpose would be that one call.

**D10. `RiverBedCarver` → `BedCarver`, and it grows a second static method: the radial bed-carve math
extracted from `RiverInfluenceCarve.carveRadialPrimitive`.** `BedCarver` ends up holding both shapes —
rectangle/tangent (today's `carve`, already extracted) and radial (newly extracted) — because it will
serve every family, not just rivers. Both extractions are pure moves: same math, same parameter shapes,
no behavior change.

**D11. New class `ShellCarver` (not `InfluenceCarve`) houses `carveRosgenInfluence`'s extracted math.**
`InfluenceCarve` was considered and rejected: one letter from the existing `InfluenceCarver` *enum* in
the same package, which inverts the usual convention that the `-er` name holds behavior and the bare
noun doesn't — a real misread risk, not a cosmetic one. `ShellCarver` instead reuses this codebase's own
documented vocabulary (`hydrology/profile/README.md` and `ARCHITECTURE.md` both already say "shell
pass"/"bed pass"), pairs cleanly with `BedCarver`, and shares no letters with it.

`carveRadialInfluence` (the shell pass's radial counterpart, used only by `AbandonedRiverPrimitive`
today) is **not** extracted into a parallel `ShellCarver` method this round — it stays exactly where it
is, called directly by `InfluenceCarver.RADIAL`. Nothing in this session's scope moves it: D16 keeps
`AbandonedRiverPrimitive` radial, so it remains this method's only production caller and there is no
second shape to justify generalizing it yet.

**D12. `RiverInfluenceCarve` → `LatticeCarve`.** Once D10/D11 pull the per-type math out, what's left —
the `ThreadLocal<GridBuffers>`, the shared constants (`UNSET_MIN_DIST`, `BED_EDGE`, `FLOODPLAIN_EDGE`),
and the two orchestration entry points — is no longer river-specific. The name matches the class's own
existing javadoc ("the stateless lattice carve shared by every carve call site").

**D13. Both orchestration entry points drop "River" from their names, matching D10/D11's generalization:**
`carveRiverInfluenceGrid` → `carveInfluenceGrid`; `computeRiverGrid` → `computeBedGrid`. Exact spelling
is a proposal, not load-bearing — easy to change in the implementation plan if a clearer pair turns up.

**D14. Bed-carve dispatch becomes polymorphic: `HydrologicalPrimitive` gains an abstract `carveBed(...)`,
called once per primitive in `computeBedGrid`'s existing sorted-order walk, replacing the
`instanceof RiverPrimitive` / `instanceof RadialPrimitive` chain outright** — not layered alongside it.
Illustrative shape (exact parameter list is an implementation-plan detail, not a spec commitment):

```java
void carveBed(LatticeCarve.GridBuffers buffers, double startX, double startZ, double resolution,
              int gridSize, float[] elevs);
```

Each implementation pulls its own geometry off `this` (coord, normal, width, …), tabulates its LUT
exactly as today's `carveRiverPrimitive`/`carveRadialPrimitive` already do, and calls the matching
`BedCarver` static method (D10) with it — the same math, the same buffers, reached by a virtual call
instead of a type check. This is the mechanism Part 1's D6 (unified `dist[]`/`acc[]`, no family-based
gating, order decides outcomes) already assumed; D14 is what actually replaces the `instanceof` chain
that D6 described only at the merge-law level.

**D15. Per-family `carveBed` bodies, chosen to change zero behavior except where D17 explicitly accepts
a change:**

- `RiverPrimitive.carveBed` — real, wired to `BedCarver.carve(...)`.
- `RadialPrimitive` gets a real **default** `carveBed(...)`, wired to `BedCarver`'s new radial method
  (D10). This single default covers `ConfluencePrimitive`, `SourcePrimitive`, and — because D16 keeps
  it on `RadialPrimitive` — `AbandonedRiverPrimitive` too, all three getting their existing, working
  bed-carve relocated, not rewritten.
- `PositionOnlyPrimitive` gets a default `carveBed(...)` that is a genuine no-op. `WaterfallPrimitive`
  and `DeltaPrimitive` implement this, not `RadialPrimitive`, and today fall through both branches of
  `computeRiverGrid`'s `instanceof` chain, carving nothing — the no-op default preserves that exactly.
- `OxbowLakePrimitive.carveBed` throws `UnsupportedOperationException` — a deliberate, loud stub (this
  repo's existing idiom for "not implemented yet"), left for the project owner to complete in a later
  session. **Residual risk, not a design gap:** unlike Waterfall/Delta, `OxbowLakePrimitive` *also*
  falls through both branches of today's `instanceof` chain and therefore also carves nothing today —
  D14 turns that silent no-op into a hard throw the first time any `OxbowLakePrimitive` actually reaches
  a chunk's bed-carve loop. Its own `serializePrimitive` javadoc states no such payload has ever been
  written to a cached tile, so this is believed currently unreachable in practice, but it should be
  verified (or the loop should skip it defensively) before this ships — see Consequences.

**D16. `AbandonedRiverPrimitive` is explicitly *not* restructured this session — it stays
`RadialPrimitive`, keeps `InfluenceCarver.RADIAL` for shell carve, and keeps its real bed-carve via
D15's `RadialPrimitive` default (relocated, not rewritten).**

A decision-critic pass traced its actual mint site (`RiverNetwork`'s eviction path, around
`evictOlderThan`) and found a defensible, non-fabricated tangent source *does* exist — a secant over
the same `pts` array `Centreline.normalAt` already trusts elsewhere for `RiverPrimitive` — contrary to
this document's own earlier (2026-09-05) finding that no tangent was available there at all.

Rejected anyway: giving `AbandonedRiverPrimitive` the `RosgenCarvedPrimitive` shape now with `normal`
defaulted to `null` (deferring only the tangent-sourcing step, using `carveRosgenInfluence`'s existing
null-normal no-op guard as a safety net). This was the stress-test's own recommendation, but the project
owner rejected it: it would silently drop `AbandonedRiverPrimitive`'s current, real shell-carve
contribution to nothing until a follow-up session lands — a genuine terrain-output change, which this
session is explicitly scoped as refactor/performance only, not entitled to make. Wiring the real secant
tangent touches `RiverNetwork.java` and needs an unreviewed length/width sizing constant for the
primitive's new rectangle footprint — real design work, deferred as one unit (shape *and* tangent
together) to a later session, per Part 3 below.

**D17. `HydrologyProfileInprinter` is deleted.** Its one real method, `prefetchChunk`, is superseded by
Part 1's D7 `primitivesInCell` lookup, exposed directly on `RiverProvider` and called straight from
`PopulateNoiseStep` — no wrapper class. Its broken `carvePrimitives` stub is discarded, not repaired:
the bed pass's actual per-primitive dispatch loop (D14) lives inline inside `LatticeCarve.computeBedGrid`,
mirroring exactly where the shell pass's own dispatch loop already lives today
(`LatticeCarve.carveInfluenceGrid`, née `carveRiverInfluenceGrid`) — neither pass has ever centralized
its dispatch loop in a separate class, and there's no reason to start now. The cycle-avoidance rationale
Inprinter was originally split out for (`profile/README.md`) no longer holds: `RiverProvider` already
imports `hydrology.carvers` classes directly today.

### Consequences and residual risk

- **The four current compile breaks are fixed, three of them "for free" by D8 alone** (`RosgenCarvedPrimitiveCodecTest`,
  `InfluenceCarverDefaultsTest`, and `RiverInfluenceCarve.java:61`'s dispatch call all resolve once
  `RiverPrimitive` implements `RosgenCarvedPrimitive` again); the fourth (`InfluenceCarver.java`'s
  dangling `carveBed` stub) is fixed by D14 by deleting it from the enum outright — `carveBed` was
  misplaced there to begin with; it belongs on `HydrologicalPrimitive`/its subtypes (D14/D15), not on
  the shell-only `InfluenceCarver` dispatch enum, which keeps doing exactly what it does today.
- **`OxbowLakePrimitive`'s silent gap becomes a loud throw (D15).** Verify no production path currently
  mints/persists an `OxbowLakePrimitive` that would reach a chunk's bed-carve loop before shipping this;
  if one might, gate `computeBedGrid`'s dispatch loop to catch-and-skip (or explicitly no-op) `Oxbow`
  until its `carveBed` is real, rather than let a single unhandled feature crash chunk generation.
- **Test surface most likely to need rewriting, not just re-passing:** `ComputeRiverGridTest`,
  `InfluenceCarverShellTest`, `RadialCarveTest`, and `RiverPaintDepthTest` all probe the carve through
  its current entry points/names; D12/D13's renames and D14's dispatch-mechanism change move where these
  tests attach even where the underlying math (and therefore the expected output) is unchanged.
- **Confluence/Source/Delta/Waterfall's dispatch mechanism changes; their output does not** — D15 chose
  their default `carveBed` bodies specifically to reproduce today's real behavior (Confluence/Source)
  or today's real no-op (Delta/Waterfall) exactly, via extraction rather than rewrite.
- **`AbandonedRiverPrimitive` is unchanged by this document**, despite the project owner's original
  message proposing to convert it — D16 records that reversal and why, so it isn't re-litigated as an
  oversight later.
- Every consequence Part 1 already listed (golden-test re-baselining, `Infinite3DVisualizer`/
  `SpatialIndexBenchmark` staying broken, no cache migration needed here since this section touches no
  persisted format) applies unchanged; this section adds no new persistence-format risk.

### Explicitly out of scope (Part 3, not written yet)

Two items are named here so they aren't lost, not because either is close to ready:

1. **`AbandonedRiverPrimitive`'s real tangent + `RosgenCarvedPrimitive` conversion** (D16) — wiring the
   secant-derived tangent at its `RiverNetwork` mint site, choosing its rectangle sizing law, and only
   then switching it off `InfluenceCarver.RADIAL`. Needs its own design pass, not a rename-and-extract.
2. **Whether shell-prep-in-`RiverProvider` / bed-detail-in-`PopulateNoiseStep` is itself the right
   architectural split** — the project owner's original framing for this whole effort, and still
   entirely untouched. Part 2 above makes each pass internally consistent (polymorphic dispatch,
   shared naming) but does not change which pass runs where or why the split exists at all. `OxbowLakePrimitive`'s
   real bed-carve logic (D15's stub) most likely belongs inside whatever this restructuring decides,
   rather than being designed in isolation first.

## Implementation

Not yet written. Follows once this spec is reviewed, via the `writing-plans` skill.
