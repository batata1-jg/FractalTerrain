# Carve dispatch: polymorphic `carveBed`, extracted carvers, and one merge law

Date: 2026-09-20
Status: implemented, across `d4b0274`..`ffa198f` (dispatch, extraction, the merge-law rewrite) plus the
documentation move landing this record.
Branch: `feature/hydrology`
Measured at: `8bf6885`

## Problem

`RiverInfluenceCarve` conflates two structurally different carve passes — shell/influence
(`carveRiverInfluenceGrid`, cut-only into `elevs[]`) and bed (`computeRiverGrid`, the `acc`
triple-buffer merge) — and dispatches each primitive's contribution through hand-written `instanceof`
chains that vary per family (`instanceof RiverPrimitive`, then `instanceof RadialPrimitive`; a third
family, `HistoricPrimitive`'s sheds, matches neither and carves nothing). The bed pass also keeps two
ranking buffers apart, `dist[]` for rivers and `radialDist[]` for discs, with a family-based gate
deciding when a radial contribution may claim ground a river already holds.

Commit `8bf6885` ("new carving bed system") began fixing the dispatch half by hand: it moved
`InfluenceCarver`/`RiverInfluenceCarve` into a new `hydrology.carvers` package, added a
`RiverBedCarver` interface extracting the rectangle/tangent bed-carve math out of
`RiverInfluenceCarve.carveRiverPrimitive`, and started adding a per-primitive `carveBed(...)` method.
It is committed at `HEAD` in a **non-compiling** state. This document is the target end-state for
finishing that work coherently.

## Relationship to the `PrimitiveGrid` spec

`2026-09-17-primitive-grid-storage-design.md` covers two changes: a storage rewrite (its Part 1,
replacing `RiverProvider`'s per-tile `ImmutableRTree` with a chunk-aligned CSR `PrimitiveGrid`) and
this carve-dispatch refactor (its Part 2). They are separable, and this one lands first: it touches no
persisted format, needs no cache migration, and its correctness does not depend on how primitives are
stored.

The split changes two of the storage spec's decisions where they leaned on Part 1:

- Its D17 deleted `HydrologyProfileInprinter` on the grounds that Part 1's `primitivesInCell` superseded
  `prefetchChunk`. Without Part 1 the per-chunk query is still `queryInfluence`, so the deletion has a
  different destination — see D17 below.
- Its Part 1 D6 (the unified merge law) is in scope here rather than there, because it is what the
  polymorphic dispatch in D8 exists to enable.

Decision numbering restarts. The mapping to the storage spec's numbers, for anyone tracing a decision
back:

| Here | There | Here    | There         |
| ---- | ----- | ------- | ------------- |
| D1   | D8    | D9      | —             |
| D2   | —     | D10     | D15           |
| D3   | D9    | D11–D15 | Part 1 D6     |
| D4   | D10   | D16     | D16           |
| D5   | D11   | D17     | D17 (amended) |
| D6   | D12   |         |               |
| D7   | D13   |         |               |
| D8   | D14   |         |               |

D2 and D9 have no counterpart. D2 corrects an error in the storage spec's own consequences section —
see below; D9 is a split that spec never reached.

## Decisions

### Compile-break closure

**D1. Restore `RiverPrimitive implements RosgenCarvedPrimitive`.** Not a design choice — undoing an
accidental drop. `RiverPrimitive.java:33` reads `implements HydrologicalPrimitive ,
SpatialIndexRotatedRectangle`; the record still carries every field `RosgenCarvedPrimitive` needs
(`normal`, `width`, `curvature`, `elevation`, `rosgenType`, `seed`), and nothing else about it changes.

**D2. Shell-carve dispatch becomes polymorphic too; the `InfluenceCarver` *enum* and
`getInfluenceCarver()` are deleted.**

`HydrologicalPrimitive` declares neither `getProfile()` nor `getInfluenceCarver()`; both live only on
`RosgenCarvedPrimitive`, `RadialPrimitive`, `PositionOnlyPrimitive` and `AbandonedRiverPrimitive`. The
shell pass's dispatch loop calls the latter on the bare base type:

```java
// RiverInfluenceCarve.java:60-62
for (HydrologicalPrimitive primitive : primitives) {
    primitive.getInfluenceCarver().carveInfluence(primitive, grid);
}
```

That break is independent of D1 and survives it. The storage spec's consequences section names this
call site among the breaks that resolve "for free" once `RiverPrimitive` implements
`RosgenCarvedPrimitive` again; restoring an interface on one record cannot fix a call on the base
type, and this decision is the correction.

Two ways out: declare `getInfluenceCarver()` abstract on the base type, or delete the enum and declare
the carve itself abstract there. The second, matching D8 — both passes then dispatch by one mechanism
rather than an enum table for the shell and a virtual call for the bed, and the name `InfluenceCarver`
is freed for the class D5 gives it. Illustrative shape:

```java
void carveInfluence(LatticeCarve.ShellGrid grid);
```

Abstract rather than defaulted to a no-op, so a new primitive family cannot silently carve no shell.
The per-family bodies reproduce today's enum mapping exactly, which is **not** the mirror of D10's bed
mapping:

- `RosgenCarvedPrimitive` gets a default wired to `InfluenceCarver`'s rectangle method.
- `RadialPrimitive` gets a **no-op** default. Its `getInfluenceCarver()` returns `NONE` today, so
  `ConfluencePrimitive` and `SourcePrimitive` contribute no shell influence, and the no-op preserves
  that.
- `AbandonedRiverPrimitive` overrides that no-op with `InfluenceCarver`'s radial method. It is the only
  `InfluenceCarver.RADIAL` holder today, so it remains the radial shell carve's sole caller.
- `PositionOnlyPrimitive` gets a no-op default, matching its `NONE`.

The breaks this document closes, and what closes each. The build is the authority on whether the list
is exhaustive:

| Break | Closed by |
| ----- | --------- |
| `RosgenCarvedPrimitiveCodecTest` constructing a `RiverPrimitive` as a `RosgenCarvedPrimitive` | D1 |
| `InfluenceCarverDefaultsTest`'s `river.getInfluenceCarver()` | D1 compiles it; D2 deletes the method it asserts on, so it is rewritten rather than repaired |
| `RiverPrimitive.java:81`'s `(RosgenProfile) getProfile()` inside the deprecated `h()` | D1 |
| `RiverPrimitive.java:62`'s malformed `carveBed(float[] lut, float baseIdx , float[] acc , long )` | D8 |
| `InfluenceCarver.java:39`'s dangling `carveBed(HydrologicalPrimitive primitive)` stub | D2 (the enum holding it is deleted) |
| `RiverInfluenceCarve.java:61`'s `getInfluenceCarver()` on `HydrologicalPrimitive` | **D2** |

**D3. `getProfile()` is not restored as a general `HydrologicalPrimitive` method.** Once D4/D5 extract
the carve math, nothing calls it polymorphically: the two remaining call sites (`RiverPrimitive`'s
deprecated `h()`, and the profile lookup inside `InfluenceCarver`'s rectangle method) reach it through
`RosgenCarvedPrimitive`'s own default. D2's reasoning does not transfer here, nor this one's back to
D2 — the difference is whether a call site holds the base type.

### Extraction and naming

**D4. `RiverBedCarver` → `BedCarver`, and it grows a second static method: the radial bed-carve math
extracted from `RiverInfluenceCarve.carveRadialPrimitive`.** `BedCarver` ends up holding both shapes —
rectangle/tangent (today's `carve`, already extracted) and radial — because it serves every family,
not just rivers. Both extractions are pure moves apart from the merge-law changes D11–D15 specify.

**D5. New class `InfluenceCarver` houses the shell pass's extracted math: `carveRosgenInfluence` and
`carveRadialInfluence` both.** The name is free because D2 deletes the enum that held it.

`InfluenceCarver` and `BedCarver` take the same shape deliberately: one class per pass, holding every
cross-section that pass knows how to cut. Neither is a one-method class that happens to have picked up
a second method — the rectangle/radial pair is the pass's repertoire, and a further footprint shape is
a further static method on the class serving its pass, not a new class. That only the rectangle path
has a production caller in either class today (D16 keeps `AbandonedRiverPrimitive` out of every
production network) is a fact about what the networks mint, not about what a pass is responsible for.

`ShellCarver` was considered and rejected. The pass is called the shell pass —
`hydrology/profile/CLAUDE.md` and `hydrology/features/README.md` both say "shell pass"/"bed pass" —
but every method the class receives, and the provider seam they hang off (`queryInfluence`,
`Types.RIVER_DIST`), is named for influence, and the class should not force a reader to translate
between the two vocabularies at its own boundary. `InfluenceCarve` was rejected too: the `-er` name is
this codebase's convention for the thing that holds behaviour.

**D6. `RiverInfluenceCarve` → `LatticeCarve`.** What is left once D4/D5 pull the per-type math out —
the `ThreadLocal<GridBuffers>`, the shared constants (`UNSET_MIN_DIST`, `BED_EDGE`, `FLOODPLAIN_EDGE`),
and the two orchestration entry points — is not river-specific. The name matches the class's own
javadoc: "the stateless lattice carve shared by every carve call site".

**D7. Both orchestration entry points drop "River":** `carveRiverInfluenceGrid` → `carveInfluenceGrid`,
`computeRiverGrid` → `computeBedGrid`.

### Dispatch

**D8. Bed-carve dispatch becomes polymorphic.** `HydrologicalPrimitive` gains an abstract
`carveBed(...)`, called once per primitive in `computeBedGrid`'s sorted-order walk, replacing the
`instanceof RiverPrimitive` / `instanceof RadialPrimitive` chain outright. Illustrative shape; the
exact parameter list is an implementation-plan detail:

```java
void carveBed(LatticeCarve.GridBuffers buffers, double startX, double startZ, double resolution,
              int gridSize, float[] elevs);
```

Each implementation reads its own geometry off `this` and calls the matching `BedCarver` method — the
same math, reached by a virtual call instead of a type check.

Three consequences of removing the chain:

- `computeBedGrid` returns `void`. Today `computeRiverGrid` returns `stop`, the index one past the last
  river primitive, bounding the river run; with one walk there is no run to bound. Production ignores
  the value already — `PopulateNoiseStep` discards it, and `ComputeRiverGridTest.java:210` is its only
  reader.
- The sort is promoted from an implicit precondition to a stated contract on `computeBedGrid`.
  `HydrologicalPrimitive.comparator` orders by family ordinal first (`RIVER` is 0), then by descending
  influence within rivers. Under D11 the list order is the only thing deciding outcomes, so the caller
  sorting is load-bearing rather than incidental.
- Nothing survives of the old `InfluenceCarver` enum's `carveBed` stub. That method was misplaced on an
  enum scoped to the shell pass; under D2 the enum is gone and both passes dispatch on the primitive.

**D9. Cross-section LUT construction is its own per-primitive method, separate from `carveBed`.** The
two steps depend on different things and vary independently. Filling the table needs geometry only the
family knows and the lattice does not — a river reads `width`, `curvature`, `elevation` and `seed` and
calls `RosgenProfile.sampleCrossSection`; a radial reads `radius`, `width` and `elevation` and calls
`RadialProfile.sampleRadialSection`. Cutting with the table needs the footprint clip and the merge
recurrence, which belong to the *shape* rather than the family: D10's single `RadialPrimitive` default
serves three families through one `BedCarver` call, and would have to carry three tabulations if the
LUT were built inside it.

So `HydrologicalPrimitive` gains a second abstract hook beside `carveBed`. Illustrative shape:

```java
void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution);
```

`baseIdx` and `n` are the table's extent, and they come from the footprint clip — the clipped box's
nearest and furthest reach along the carve's own cross-section axis, perpendicular for a rectangle and
radius for a disc. That clip is what caps `n` at the grid diagonal instead of the full footprint, and
`8bf6885` computes it twice: `carveRiverPrimitive` derives `baseIdx` to pass in, and `BedCarver.carve`
recomputes the same AABB and projection extrema internally. Resolving that duplication is what fixes
where the hook is called from, and the implementation plan decides it. The decision here is only that
the tabulation is reached through the primitive and the cut through the shape, with no method doing
both.

The shell pass splits the same way, for the same reason — `InfluenceCarver`'s two methods each
tabulate before their own merge loop today.

**D10. Per-family `carveBed` bodies:**

- `RiverPrimitive.carveBed` — real, wired to `BedCarver.carve(...)`.
- `RadialPrimitive` gets a real **default** wired to `BedCarver`'s radial method. This one default
  covers `ConfluencePrimitive`, `SourcePrimitive` and — because D16 keeps it on `RadialPrimitive` —
  `AbandonedRiverPrimitive`, each supplying its own table through D9's hook and its `RadialProfile`.
- `PositionOnlyPrimitive` gets a default that is a genuine no-op. `WaterfallPrimitive` and
  `DeltaPrimitive` implement this, not `RadialPrimitive`, and fall through both arms of today's chain,
  carving nothing; the no-op preserves that exactly. Its `tabulateBedLut` is a no-op for the same
  reason.
- `OxbowLakePrimitive.carveBed` throws `UnsupportedOperationException` — this repo's idiom for "not
  implemented yet". See the Consequences section for why the throw is unreachable in production.

### The merge law

Today's bed pass runs rivers against `dist[]`, then radials against a separate `radialDist[]`, with
the radial contribution gated on the weight the river pass left behind. D11–D15 collapse that into one
pass over one buffer.

**D11. One shared `dist[]`; `GridBuffers.radialDist` is deleted.** Every family's distance function is
a soft `[0,1]` influence closeness — 1 outside the footprint, 0 fully affected — rather than a metric,
so a rectangle-footprint river and a disc-footprint bowl rank against each other on one scale. The
sequential smoothed-min recurrence — seed at `UNSET_MIN_DIST`, smoothstep weight, blend toward the
current primitive — keeps its shape, amended only where D14 and D15 say; it runs once over every
primitive in list order, with no separate radial pass and no weight gate protecting a river bed.

`Types.RIVER_DIST` is published from that shared buffer, so it reflects whichever primitive won each
cell — broader than today's river-pass-only guarantee, and intended.

**D12. A radial primitive's distance runs through the same `band()` law as a river's.** Today the
radial pass writes a raw normalised radius (`d = rad * invRadius`) while the river pass writes
`band(raw, marginNorm, floodPlainNorm)`, whose breakpoints `BED_EDGE = 0.25` and
`FLOODPLAIN_EDGE = 0.5` are what `RosgenProfile.riverPaintDepth` (`RosgenProfile.java:348`) reads off
`Types.RIVER_DIST`. One buffer holding two scales would leave those breakpoints meaning whichever
family wrote last.

`RadialProfile` supplies the two control points, normalised against the disc radius:

- `marginNorm = 0.5` — the bed is the inner half of the disc. This matches the existing statement that
  the disc "runs to `width()`, twice a channel's painted bed".
- `floodPlainNorm = 0.75`.

Both are authored gates, not measurements, and carry that in their doc comments the way
`HydrologyTuning.K_BRAID` and `BRAID_MIN_WIDTH` already do.

**D13. A radial primitive never writes `typeMask`.** Today `carveRadialPrimitive` claims the tag only
where `priorWeight <= 0`, so a river bed crossing a confluence disc keeps its `RIVER` tag and its
surface materials. Dropping that gate wholesale would strip both. Removing the write entirely is the
simpler rule and is surface-neutral: `Types.RIVER_TYPE` has one consumer
(`FractalTerrainSurfaceSystem.java:144`), and a radial-tagged column already paints nothing, because
`RadialProfile` does not override `riverPaintDepth` and the base returns 0. A column a bowl reaches is
therefore painted identically whether its tag reads `SOURCE`/`CONFLUENCE` or `NONE`.

This also removes the `priorWeight` read from the tag rule, which is the only place the two passes
observed each other's bookkeeping.

**D14. One height rule for every family: `h = min(elevs[i], ownSample)`.** The river path already does
this. The radial path instead computes `priorWeight > 0 ? min(acc[a], bounded) : bounded`, guarding
against `acc` being zero-filled — an unconditional `min` against `acc[a]` would clamp a bowl standing on
high ground down to zero. Reading `elevs[i]`, which is real ambient elevation, makes the guard
unnecessary rather than unsafe to drop.

This changes terrain. A bowl no longer deepens the already-merged river surface; it blends against
ambient like every other primitive. Where a river has cut below a bowl's own floor, the bowl now pulls
the blend up toward that shallower floor in proportion to its weight. That is what "order decides
outcomes, no family gating" costs, and it is the largest visible change in this document.

**D15. The published blend weight is assigned, not maxed: `acc[a + 2] = 1 - clamp(dist[i], 0, 1)`.**
The radial path maxes today only because cells inside its square AABB but outside its disc take `w = 0`
and never touch `radialDist`, so an assignment there would erase a river's claim. With one buffer that
case does not arise and the river path's assignment is the consistent form.

### Left alone

**D16. `AbandonedRiverPrimitive` is not restructured.** It stays a `RadialPrimitive`, keeps the radial
shell carve through D2's override, and gets its bed carve from D10's `RadialPrimitive` default —
relocated, not rewritten.

Rejected: giving it the `RosgenCarvedPrimitive` shape now with `normal` defaulted to `null`, deferring
only the tangent-sourcing step. That would drop its shell-carve contribution to nothing, via the
rectangle carve's null-normal guard, until a follow-up lands — a terrain change this document is not
entitled to make. Wiring a real secant tangent touches `RiverNetwork.java` and needs an unreviewed
length/width sizing law for the primitive's new rectangle footprint; shape and tangent defer together.

**D17. `HydrologyProfileInprinter` is deleted, and its query is inlined at `PopulateNoiseStep`.** The
class is a one-method wrapper: `prefetchChunk` calls `riverProvider.queryInfluence(pt, chunkRadiusPx)`
and sorts by `HydrologicalPrimitive.comparator`. Both lines move to the single call site
(`PopulateNoiseStep.java:71`), where D8's sort contract is then visible next to the carve that depends
on it.

Its `carvePrimitives` stub (`HydrologyProfileInprinter.java:24`, an empty loop over the primitive list)
is discarded rather than repaired, along with its dead call site (`PopulateNoiseStep.java:77`). The bed
pass's dispatch loop lives inline inside `LatticeCarve.computeBedGrid`, mirroring where the shell
pass's loop already lives; neither pass has centralised its dispatch in a separate class.

The cycle-avoidance rationale the class was split out for (`profile/README.md`) no longer holds:
`RiverProvider` imports `hydrology.carvers` classes directly today.

When Part 1 lands, it replaces the inlined `queryInfluence` call with `primitivesInCell` at that same
site, with no class to delete.

## Consequences and residual risk

- **`OxbowLakePrimitive`'s throw (D10) is unreachable in production, verified rather than assumed.**
  `GlobalNetworkBuilder.java:141` is the only production `RiverNetwork` construction and uses the
  three-argument constructor, which sets `saveHistory = false`. Both mint sites — `recordAbandoned`
  (`RiverNetwork.java:434`) and `recordRemovedComplement` (`:707`) — are gated on that flag, so
  `lastStates` is always empty and `collectPrimitives`' shed loop never runs. No defensive skip in
  `computeBedGrid` is needed. The same evidence means `AbandonedRiverPrimitive` reaches no production
  carve either, so D16's "relocated, not rewritten" is covered by tests alone today — for its bed carve
  and for the radial shell carve, which is that primitive's alone. Re-check this before enabling
  history.

- **D14 changes terrain at confluences and sources.** This is the one behavioural change here that is
  not a relocation, and it is deliberate. Any golden asserting carved elevation near a radial primitive
  re-baselines.

- **The goldens re-baseline in the same change that moves dispatch.** D11–D15 and D8 land together, so
  the golden suite cannot validate the dispatch move independently of the merge-law change. Accepted;
  the mitigation is that D8's own correctness is structural (one sorted walk reproduces
  rivers-before-radials exactly, because `comparator` orders `RIVER` first) and can be argued from the
  sort contract rather than from output.

- **Test surface needing rewriting, not just re-passing:** `ComputeRiverGridTest` (entry-point name,
  `void` return, one buffer), `InfluenceCarverDefaultsTest` (it asserts on `getInfluenceCarver()`,
  which D2 deletes — it becomes a `carveInfluence` behaviour test), `RadialCarveTest` and the
  `AbandonedRiverPrimitive` dual shell+bed golden locked in at `a2a876c` (D14, D15),
  `InfluenceCarverShellTest` and `RiverPaintDepthTest` (names and the banded radial distance), and
  `RadialPrimitiveCodecTest.java:110`, whose comment about "`computeRiverGrid`'s river loop stops at
  the first non-river entry" describes a loop that no longer exists. Re-measure the suite baseline at
  `HEAD` before attributing any other red test to this change — see root `CLAUDE.md`'s Test section;
  the quoted baseline is a claim to verify, not a fact.

- **New coverage:** a column a radial primitive claims leaves `typeMask` at `HydrologicalFeature.NONE`
  (D13); the shared `dist[]` is monotone in footprint penetration and banded identically for a river
  and a disc (D11, D12); a `PositionOnlyPrimitive` in the list carves nothing in either pass (D2, D10);
  every `HydrologicalFeature` prototype's `carveInfluence` reproduces the enum arm it used to name
  (D2); a primitive's `tabulateBedLut` fills the same table the inlined tabulation did, over the same
  extent (D9).

- **No persistence risk.** Nothing here touches a serialized payload, a type tag, or a store name.

- **Delta and Waterfall's dispatch mechanism changes in both passes; their output does not.** D2 and
  D10 chose their default bodies to reproduce today's real no-op exactly.

## Explicitly out of scope

1. **Everything in `2026-09-17-primitive-grid-storage-design.md`'s Part 1** — `PrimitiveGrid`, the
   chunk-aligned cell grid and its halo, CSR fanout, per-primitive LUTs, and the removal of
   `queryInfluence`/`anyInfluencingPrimitive`. That spec needs a revision pass of its own before it is
   ready: its halo is derived from `HydrologyTuning.MAX_INFLUENCE_RADIUS`, but a `RiverPrimitive` is
   indexed as a rotated rectangle whose half-width is `1.5 × influence`, so the bound the halo must
   satisfy is not the one the spec names.

   Its per-primitive LUT cache is the one piece of Part 1 that meets this document: D9's
   `tabulateBedLut` is the seam such a cache would fill from. Nothing here caches a table across carve
   calls.

2. **`AbandonedRiverPrimitive`'s real tangent and `RosgenCarvedPrimitive` conversion** (D16) — wiring
   the secant-derived tangent at its `RiverNetwork` mint site, choosing its rectangle sizing law, and
   only then switching its shell carve from `InfluenceCarver`'s radial method to the rectangle one.
   Needs its own design pass.

3. **Whether shell-prep-in-`RiverProvider` / bed-detail-in-`PopulateNoiseStep` is the right
   architectural split at all.** This document makes each pass internally consistent but does not
   change which pass runs where. `OxbowLakePrimitive`'s real bed carve (D10's stub) most likely belongs
   inside whatever that restructuring decides, rather than being designed in isolation first.

## Implementation

`docs/superpowers/plans/2026-09-20-carve-dispatch.md`. Two places the plan's execution decided against
this spec's text, recorded here so the two documents do not silently drift:

- `RiverPaintDepthTest` needed no rewrite — the "Test surface needing rewriting" list above names it,
  but its assertions already read through `LatticeCarve`'s constants and needed only the mechanical
  rename every file in the package got.
- `8bf6885`'s extraction of the bed carve had two fidelity bugs relative to the pre-refactor
  `RiverInfluenceCarve.carveRiverPrimitive`: an off-by-one in the LUT clamp (`lut.length - 2` where the
  original read `n - 2`) and slope terms (`bedSlope`/`floodPlainSlope`/`outerSlope`) recomputed inside
  the per-lattice-point loop rather than hoisted once per primitive. The plan corrects both rather than
  preserving them, since D4 is a pure extraction and neither bug is a decision this document made.
