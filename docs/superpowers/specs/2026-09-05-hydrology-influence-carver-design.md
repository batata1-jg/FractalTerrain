# Shell-pass primitive carving: rotated-rectangle Oxbow, radial AbandonedRiver, `InfluenceCarver` dispatch

Date: 2026-09-05
Status: proposed — implementation plan written (`docs/superpowers/plans/2026-09-05-hydrology-influence-carver.md`), nothing here has been implemented
Branch: `feature/hydrology`
Measured at: `af832d1`

## Problem

`hydrology-carve-situation.md` (repo root) documents that `OxbowLakePrimitive` and
`AbandonedRiverPrimitive` are indexed and queryable but carve nothing — both implement
`HistoricPrimitive`, a circle-shaped "Skeleton" that blends as a plain `DefaultProfile` disc. The
project owner's 2026-09-04 intended design proposed closing this gap by giving every
`HydrologicalPrimitive` a `getShellCarver()`/`getBedCarver()`/`getPriorityGroup()` triple, replacing
the shell carve's `instanceof RiverPrimitive` dispatch with polymorphism and folding in a separate,
pre-existing concern (2026-09-04 review item work-15: eliminate `instanceof` primitive dispatch).

A 2026-09-05 decision-critic stress-test of that design found two of its load-bearing claims did
not survive verification against the actual code:

- **The two shed primitives are not symmetric.** The design's open question 1 asked whether
  Oxbow/AbandonedRiver "reuse the literal `RiverShellCarver` singleton, or need their own variant,"
  framed as one question with one answer. Tracing both primitives' mint sites in
  `RiverNetwork.java` shows they need different answers: `OxbowLakePrimitive` mints from
  `recordRemovedComplement` (~line 720), which holds a real `Channel` and a spline point index — a
  flow tangent is cheaply available there, the same way `RiverPrimitive` already gets one from
  `Centreline.normalAt(ch, i)` (`HydrologicalPrimitive.java:167`). `AbandonedRiverPrimitive` mints
  from the atomic-eviction path (~line 581), which walks raw `AtomicView` node positions
  (`atomic.pos(cur)`) with **no `Channel` in scope at all** — a tangent was never cheaply available
  there, and deriving one would need new finite-difference logic over consecutive atomic positions.
- **"Just swaps `instanceof` for polymorphism" understated the diff.** Three independent
  verification questions (buffer layout, loop structure, profile-axis overlap) each found the
  proposed change touches more than a dispatch branch: `RiverInfluenceCarve.GridBuffers` hardcodes
  `dist`/`radialDist` as named fields with no N-tier generalization built in, the shell's carve loop
  hardcodes "river prefix, nothing else," and a generic `ShellCarver` would sit alongside — not
  replace — the existing `HydrologyProfile`/`RosgenProfile` axis.

A further finding, made only by reading `RiverInfluenceCarve.carveRiverPrimitiveInfluence` line by
line rather than trusting its parameter list: the shell pass's own `dist` parameter is never read
or written inside that method. The shell pass's merge is **order-independent hard-min**
(`elevs[i] = Math.min(elevs[i], candidate)`), not the bed pass's (`computeRiverGrid`)
order-dependent weighted smoothed-min recurrence. This matters because the original design's
"N-tiered priority" mechanism (`getPriorityGroup()`, one distance buffer per tier) presupposes a
recurrence that ranks primitives against each other — the shell pass has never had one.

This document supersedes `hydrology-carve-situation.md`'s "Intended design" and "Proposed shape"
sections for the shell pass specifically. It does not revisit that document's priority-mechanism
description of `computeRiverGrid` (the bed pass), which remains accurate and unaffected.

## Decisions

**D1. `OxbowLakePrimitive` becomes a rotated rectangle, sharing `RiverPrimitive`'s literal
cross-section algorithm via a new interface, `RosgenCarvedPrimitive`.**

Given a cheap tangent is available at its mint site, Oxbow's shape can match what its cross-section
math actually needs: a rectangle banded along a flow tangent, exactly like `RiverPrimitive`. Rather
than duplicating `RiverPrimitive`'s carve body under a different name, both records implement one
new interface (`normal()`, `width()`, `curvature()`, `elevation()`, `rosgenType()`, `seed()`,
extending `SpatialIndexRotatedRectangle` and `HydrologicalPrimitive`) with a shared default
`getProfile()`. `recordRemovedComplement` is extended to construct a `Centreline` (cheap — it holds
no per-point cache, per its own javadoc) and call `normalAt(ch, i)`/`ch.spline.curvature(i)` at the
same point it already reads `ch.widthAt(i)`.

Oxbow's Rosgen classification is minted `null` (coalescing to `RosgenType.A`, the same convention
`RiverPrimitive` already uses for an untyped reach), rather than threading a `ChannelTyper` through
the cutoff path to recover the parent channel's actual type. That classification concept
(`ChannelTyper.typesFor`) is resolved at `collectPrimitives` time, not carried by a raw `Channel`
object — plumbing it through would be a materially bigger, separable change.

Rejected: giving Oxbow its own bespoke cross-section law instead of reusing `RiverPrimitive`'s. This
was the project owner's original framing ("a shed meander/captured channel is close enough in shape
to a live river that the same cross-section carve algorithm should apply"), and nothing in this
session's investigation contradicted it for Oxbow specifically — only for the pair as a whole.

**D2. `AbandonedRiverPrimitive` becomes radial, joining the existing `RadialPrimitive` family
(`ConfluencePrimitive`, `SourcePrimitive`), with a new `RadialProfile.ABANDONED_RIVER` constant.**

Since no tangent is available at its mint site, forcing rectangle/tangent carving onto
`AbandonedRiverPrimitive` would mean building new derivation machinery to solve a problem the
primitive's own geometry doesn't have: it already carries a center and a width, which is exactly
what `RadialPrimitive` needs (`getRadius() = width()`, the same default `ConfluencePrimitive`/
`SourcePrimitive` rely on unoverridden). Because radius resolves to `width()` immediately —
`HydrologyTuning.widthFromFlow(maxOwn)` is known before the primitive is even constructed — the
separate `influence` field `HistoricPrimitive` used for "resolved later" no longer has anything
left to defer for AbandonedRiver; it is dropped, and its 5-arg constructor becomes 4-arg
(`coord, time, width, elevation`). Only `elevation` remains genuinely deferred (a bed-elevation
assigner pass fills it in later), so `resolved(double, double)` becomes single-argument
`resolved(double elevation)`.

`RadialProfile.ABANDONED_RIVER`'s depth law is a shallow parabola scaled to 40% of `CONFLUENCE`'s
depth fraction at the same radius — a trace that has been silting in since abandonment should not
out-cut a still-active confluence pool. This constant is an aesthetic choice, not a derived one,
and is called out as tunable in the implementation plan.

Because both remaining `HistoricPrimitive` implementors move off it, `HistoricPrimitive.java` itself
is deleted — nothing implements it after this change.

Rejected: giving AbandonedRiver its own new interface (neither `RosgenCarvedPrimitive` nor
`RadialPrimitive`) to keep a separate `influence`-as-deferred-radius concept alive. This was
considered and dropped because it would mean carrying a field (`influence`) that duplicates
`width()`'s already-known value for the sole purpose of preserving a "deferred resolution" shape
that no longer describes reality — dead weight, not a distinction with a difference.

**D3. `ShellCarver` is renamed `InfluenceCarver` and scoped only to the shell/influence pass.
`getBedCarver()` is dropped entirely.**

`InfluenceCarver` is an enum (`ROSGEN`, `RADIAL`, `NONE`), matching this codebase's existing idiom
for a small closed set of shape-dispatched behaviors (`RadialProfile`, `HydrologicalFeature`) rather
than the original doc's `interface` + `static final` singleton framing — chosen to match the two or
three nearest existing files, per this project's own convention for cases no doc settles.
`HydrologicalPrimitive.getInfluenceCarver()` replaces the shell's `instanceof RiverPrimitive`
dispatch (`RiverInfluenceCarve.carveRiverInfluenceGrid` and its tile-level callers:
`GlobalNetworkBuilder.build`, `LocalNetworkBuilder.build`, `RiverProvider.carveRivers`). The bed
pass (`computeRiverGrid`, called only from `PopulateNoiseStep.fineGrainedPrimitivePass`) is
untouched — no bed-carve behavior exists for Oxbow/AbandonedRiver before or after this change, and
building one is a separate, later decision.

`ConfluencePrimitive`/`SourcePrimitive` default to `InfluenceCarver.NONE` — the shell pass does not
carve them today, and this change does not extend it to. Only `RiverPrimitive`/`OxbowLakePrimitive`
(`ROSGEN`, via a shared interface default) and `AbandonedRiverPrimitive` (`RADIAL`, overriding
`RadialPrimitive`'s own `NONE` default on the concrete record) get real shell carvers.

Rejected: extending shell carving to Confluence/Source too, now that the dispatch is general enough
to make that a one-line change. Out of scope — the project owner's spec named River/Oxbow/
AbandonedRiver specifically; extending further is a separate decision with its own visual-regression
surface (see D4's discussion of what changes when a new family enters the shared merge).

**D4. `getPriorityGroup()`/`getPriorityTier()` is dropped. The shell pass keeps today's
order-independent hard-min merge; no distance-buffer generalization is built.**

The original design's N-tier mechanism (one distance buffer per tier, generalizing today's
`dist`/`radialDist` pair) presupposes a recurrence where processing order and per-primitive ranking
matter. The shell pass has no such recurrence — `carveRiverPrimitiveInfluence`'s `dist` parameter is
provably unused, and every primitive's contribution is merged independently via `Math.min` against
the running elevation buffer, which is already commutative and order-independent. Under that law, a
`getPriorityTier()` accessor would have no observable effect on carved output; it is not added.

This was an explicit fork in the design, not an oversight carried forward silently: the project
owner was asked directly whether the shell pass should instead adopt the bed pass's weighted
recurrence (making tiering real, at the cost of changing existing river-only shell output as a side
effect) and chose to keep the simpler, existing law.

Rejected: porting `computeRiverGrid`'s weighted smoothed-min recurrence into the shell pass. This
would have made `getPriorityTier()`/buffer-clearing a real mechanism, but at the cost of changing
`RiverPrimitive`'s own shell-carved output — a regression-risk surface with no offsetting benefit,
since nothing in this change needs cross-primitive ranking to produce correct Oxbow/AbandonedRiver
carving (each primitive's footprint is independent; overlapping footprints already resolve
correctly under hard-min, deepest cut wins).

## Consequences and residual risk

- **River shell output must be byte-identical.** `carveRiverPrimitiveInfluence`'s body is renamed to
  `carveRosgenInfluence` and retargeted at `RosgenCarvedPrimitive` instead of `RiverPrimitive`
  directly; no arithmetic changes. The implementation plan's Task 6 carries a regression test and a
  debug-harness visual diff specifically to prove this.
- **Oxbow entering the shared shell merge is a real, intended visual change**, not a pure addition:
  wherever an oxbow's rectangle footprint overlaps a still-live river's (which is common — a cutoff
  loop is geometrically adjacent to the channel that shed it by construction), the hard-min merge
  will now carve whichever is deeper at that point, which can be the oxbow. This is the point of the
  change, but it means "no existing river geometry moved" cannot be assumed without checking; the
  plan's Task 7 exists to check it against the debug harness.
- **No on-disk migration risk.** `PrimitiveCodec`'s own `:SCHEMA:` comment on the code this plan
  deletes confirms neither shed primitive has ever been written to a cached tile — their serialized
  layout is free to change.
- **Out of scope, explicitly:** bed-carve behavior for Oxbow/AbandonedRiver; threading a real
  `ChannelTyper` through the cutoff path so Oxbow inherits its parent channel's Rosgen type; shell
  carving for Confluence/Source. Each is a separable follow-up, not a blocked prerequisite of this
  change.

## Implementation

`docs/superpowers/plans/2026-09-05-hydrology-influence-carver.md` — 8 tasks, file-by-file, with
exact method bodies, test fixtures, and a task-ordering note (Task 3's `PrimitiveCodec` cleanup runs
after Tasks 4/5, despite its lower number, since it deletes helpers those tasks must first stop
calling).
