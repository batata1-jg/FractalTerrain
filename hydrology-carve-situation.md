# Hydrological primitive carving — current situation and open design

Status: **spec settled, not yet implemented.** Captures where `RiverInfluenceCarve` and the
primitive type hierarchy stand today, the gap identified against the intended design, and the
`InfluenceCarver`-based redesign that closes it. Supersedes the "centralize behind
`RiverProvider`" (work-3) and "polymorphic `instanceof` replacement" (work-15) framing from the
2026-09-04 refactor review — both are folded into the design below. A 2026-09-05 decision-critic
stress-test found the original "OxbowLake and AbandonedRiver share one framework" framing false;
the project owner re-scoped the design the same day (see "Intended design" below) to resolve it
asymmetrically. Only the two method renames below (2026-09-05) have landed so far; the rest of
this document is now an implementation-ready spec.

**2026-09-05 update:** `RiverInfluenceCarve`'s two shell-pass methods were renamed for clarity
ahead of the deferred redesign — `carveRiverInfluence` → `carveRiverInfluenceGrid` (public; the
tile-level entry point called from `GlobalNetworkBuilder`, `LocalNetworkBuilder`, and
`RiverProvider.carveRivers`) and `carvePrimitiveInfluence` → `carveRiverPrimitiveInfluence`
(private; reached from `carveRiverInfluenceGrid` via `computeRiverInfluenceGrid`). **Both belong
to the shell pass** — an earlier version of this document mislabeled `carveRiverPrimitiveInfluence`
as the bed pass, which is wrong: the bed pass's entry point is the separate, un-renamed
`computeRiverGrid`, called directly by `PopulateNoiseStep.fineGrainedPrimitivePass`. The "Two
carve stages" table and "The gap" section below reflect the corrected mapping, checked against
`hydrology/profile/README.md` and the current call sites.

## Two carve stages, not one

`RiverInfluenceCarve.java` (`hydrology/profile/`) implements two structurally different carve
passes that share a file but not a lattice:

| | Shell / influence pass | Bed pass |
|---|---|---|
| Entry point | `carveRiverInfluenceGrid` → `computeRiverInfluenceGrid` → `carveRiverPrimitiveInfluence` | `computeRiverGrid` (called directly) |
| Writes into | `elevs[]` directly, cut-only (`Math.min`) | `acc` triple-buffer (height, water, weight) |
| Called from | `GlobalNetworkBuilder.build`, `LocalNetworkBuilder.build`, `RiverProvider.carveRivers` — tile-level, 514×514 padded grid | `PopulateNoiseStep.fineGrainedPrimitivePass`, via `HydrologyProfileInprinter.prefetchChunk` — per-chunk, 16×16 grid |
| Purpose | Large, coarse landscape shaping — makes room for later changes | Per-primitive detail carve, at chunk resolution |
| Primitive coverage today | `RiverPrimitive` **only** — no radial dispatch at all | `RiverPrimitive` + `RadialPrimitive` |

Three tile-level callers run the shell pass per tile build (`GlobalNetworkBuilder`,
`LocalNetworkBuilder`, then `RiverProvider.carveRivers`), each into its own elevation clone; only
`RiverProvider.carveRivers`'s clone survives to be published. `PopulateNoiseStep` runs much later,
at chunk-fill time, and carves each primitive's own characteristics — plus the radial families the
shell pass never touches — into the already-shaped terrain.

## The priority mechanism (how "confluence beats river" actually works)

This entire mechanism lives inside `computeRiverGrid` — the bed pass. The shell pass never builds
a radial group, so it has nothing to prioritize against.

Traced to the real code, not just the concept:

1. `HydrologicalPrimitive.comparator` sorts primarily by `getType().ordinal()`. Enum declaration
   order is `RIVER(0), ABANDONED_RIVER(1), OXBOW_LAKE(2), SOURCE(3), WATERFALL(4), DELTA(5),
   CONFLUENCE(6)` — ascending, so rivers sort first, confluences last. Within the `RIVER` group
   itself the comparator applies a second key, descending `RiverPrimitive.influence()` — not
   documented before this pass — so two same-type river primitives don't process in arbitrary
   encounter order either.
2. `computeRiverGrid` walks the sorted list once: all `RiverPrimitive`s first (into `acc`/`dist`),
   then everything else in ordinal order (into `acc`/`radialDist`).
3. Two separate distance buffers exist because a disc's radius scale and a channel's banded
   rectangle scale aren't comparable — letting them rank-compete would compare "inside" by two
   different measures.
4. Because the later group (radials) uses its **own** fresh distance buffer, it never has to
   out-rank the earlier group's confidence to matter — its blend always applies **on top** of
   whatever the river pass already wrote (`(1-w)*acc[a] + w*h`).

So: **priority = enum-ordinal processing order + a private distance buffer per group.** A
later-processed group wins ties by construction, not by comparing distance values across groups.
"Carve algorithm" and "priority group" are independent axes — two primitive types can share
identical cross-section math while sitting in different priority tiers, by using different
distance buffers.

## The gap: `OxbowLakePrimitive` / `AbandonedRiverPrimitive` aren't carved at all today

Both implement `HistoricPrimitive` (`hydrology/features/HistoricPrimitive.java`), which is a
**sibling** interface to `RadialPrimitive` — not a subtype. Only `computeRiverGrid` (the bed pass)
runs a two-step dispatch, `instanceof RiverPrimitive` then `instanceof RadialPrimitive`;
`computeRiverInfluenceGrid` (the shell pass) dispatches on `HydrologicalPrimitive.asRiver` only —
rivers, full stop, no radial branch at all. So `RadialPrimitive`s (`ConfluencePrimitive`,
`SourcePrimitive`) are carved by the bed pass alone, and `HistoricPrimitive` matches neither
dispatch in either pass. Confirmed by `OxbowLakePrimitive`'s own javadoc: *"Skeleton. It carries the step that cut it and the width it
was cut at, but no water level and no loop geometry, so it carves nothing of its own and blends
as a plain `DefaultProfile` influence disc."* They're indexed and queryable, but silently
contribute nothing to either carve pass.

This is a known, documented WIP state (`ZoneCategory.LAKE_BED` is already reserved for it), not a
bug — but it means the intended design (below) is an extension, not a refactor of working
behavior.

## Intended design (per project owner, 2026-09-04; re-scoped 2026-09-05 after decision-critic review)

The original framing below ("one shared framework for all three") was wrong — a decision-critic
stress-test (2026-09-05) traced both shed primitives' actual mint sites and found they are not
symmetric: `OxbowLakePrimitive` mints from a real `Channel` and spline index
(`RiverNetwork.recordRemovedComplement`), so a flow tangent is cheaply available, the same way
`RiverPrimitive` gets one from `Centreline.normalAt`. `AbandonedRiverPrimitive` mints from raw
`AtomicView` node positions (`RiverNetwork`'s eviction path, around `evictOlderThan`) with no
`Channel` in scope at all — no tangent is cheaply available there. The re-scoped design resolves
this asymmetrically instead of forcing one answer onto both:

- **`OxbowLakePrimitive` gets a rotated-rectangle footprint**, matching `RiverPrimitive`'s shape
  (length along the flow tangent, width across it), so it can share the literal cross-section
  carve algorithm with rivers. It no longer implements `HistoricPrimitive` as currently defined
  (which extends `SpatialIndexCircle`) — its interface home changes along with its shape.
- **`AbandonedRiverPrimitive` gets radial symmetry** (a disc, like `RadialPrimitive`) instead —
  this sidesteps its actual blocker (no tangent data at its mint site) rather than solving it.
- **Renamed: `ShellCarver` → `InfluenceCarver`.** Scope is narrowed to exactly the shell/influence
  pass — carving primitive *influence*, not the bed, during `RiverProvider`'s tile-creation step
  (`carveRiverInfluenceGrid` and its tile-level callers). Bed carving is untouched by this
  redesign; `getBedCarver()` is dropped. `InfluenceCarver` uses `RosgenProfile`'s existing
  cross-section law, so River and (now-rectangular) Oxbow share the same algorithm; AbandonedRiver
  (now radial) uses the radial law `ConfluencePrimitive`/`SourcePrimitive` already carve with.
- **N-tiered priority without N buffers.** Rather than one distance buffer per tier, each tier
  **reuses the same `dist` buffer**, cleared back to `UNSET_MIN_DIST` before that tier's
  primitives are carved. `acc` (the accumulated height/water/weight) is never cleared — it carries
  the sequential recurrence across every tier, exactly as today across the river/radial split.
  Clearing `dist` per tier reproduces what `radialDist` achieves today (a later tier ranks only
  against its own members, never out-competing an earlier tier's confidence) without allocating a
  second array per tier.
- **Ordering alone does the rest.** Processing tiers in `HydrologicalPrimitive.comparator`'s
  existing ordinal order and clearing `dist` between tiers is sufficient; no new priority data
  structure is needed beyond "clear, then carve, in order."

## Proposed shape (supersedes the flat `getCarver()` idea and the original `ShellCarver`/`BedCarver` split)

```java
interface HydrologicalPrimitive {
    InfluenceCarver<?> getInfluenceCarver();   // cross-section/blend math for the shell/influence pass only
    PriorityTier getPriorityTier();            // processing order; dist is cleared between tiers, not duplicated
}
```

- Bed carving is out of scope for this pass — `getBedCarver()` is dropped from this design; the
  bed pass (`computeRiverGrid`, called from `PopulateNoiseStep`) is untouched.
- `InfluenceCarver` implementations are stateless `static final` singletons — dispatch is once per
  primitive, not per lattice point, so cost is negligible relative to the inner per-point loops.
  Concrete carvers now number at least 2 shapes (rectangle/tangent: River, Oxbow; radial:
  AbandonedRiver, Confluence, Source) — re-verify the bimorphic-dispatch assumption once the real
  call site exists and an actual per-tile primitive count is measured; decision-critic flagged
  both the shape count and the count itself as previously unverified.
- `PriorityTier` orders tiers; the loop clears `dist` (never `acc`) between tiers and carves that
  tier's primitives in `comparator` order. No per-tier buffer allocation.

## Open questions — resolved 2026-09-05 (see re-scoped design above)

1. ~~Do OxbowLake/AbandonedRiver reuse the literal `RiverShellCarver` singleton, or need their own
   variant?~~ **Resolved, asymmetrically:** Oxbow reuses the river-style rectangle/tangent carve
   (its own rotated footprint, tangent from its `Channel` mint site). AbandonedRiver does not — it
   carves radially instead, on the same law as `ConfluencePrimitive`/`SourcePrimitive`.
2. ~~How many priority tiers are needed?~~ **Resolved:** as many as `comparator` order needs — the
   mechanism reuses one `dist` buffer, cleared between tiers, rather than one buffer per tier, so
   tier count is no longer a buffer-sizing question.
3. ~~Bed-carving for Oxbow/AbandonedRiver — leave `getBedCarver()` null, or build real logic now?~~
   **Resolved, deferred:** out of scope. This redesign covers only the shell/influence pass; bed
   carving for shed features remains a separate, later decision.

## Related work-item decisions from the same review (context)

- **work-1** (inject `GenerationContext` instead of static `FractalTerrainInstance` reach-through)
  — accepted. Multi-consumer sharing (e.g. `BiomeProvider` needing both `RiverProvider` and
  `ReliefProvider`, where `ReliefProvider` itself needs `RiverProvider`) is a DAG, not a diamond
  problem — `GenerationContext` already builds providers in topological order and can pass shared
  references, exactly like `hydrologyInprinter`/`hydrologyPainter` already do with `riverProvider`
  today (`GenerationContext.java:57-58`).
- **work-3** (centralize `RiverInfluenceCarve` invocation behind `RiverProvider`) — **retracted**.
  `RiverInfluenceCarve` is deliberately kept out of `hydrology.providers` to avoid a dependency
  cycle (per its own docstring), and two of the four original call sites (`GlobalNetworkBuilder`,
  `LocalNetworkBuilder`) are carving their own private elevation clones during tracing, which is
  intentional, not scattering. Superseded by the `getCarver()` design above.
- **work-4** (staged construction / fallback in `GenerationContext`) — accepted, scoped down: wrap
  each provider construction in a try/catch that adds identifying context and rethrows; world load
  still fails fast. Full graceful degradation is out of scope — it would require every downstream
  provider to accept a nullable dependency, fighting work-1's DI goal.
- **work-5** (consolidate the 3 `@Mixin` classes behind one interceptor) — **dropped**. The three
  mixins (`SteepSlopePredicateMixin`, `PlacedFeatureMixin`, `LevelUtilsMixin`) hook unrelated
  vanilla/terrablender extension points at very different call frequencies (per-block surface
  predicate vs. per-chunk feature placement vs. once-at-server-start); the only shared code is a
  one-line `FractalTerrainInstance.exists()` check, too cheap to be worth abstracting, and doing so
  would add indirection into the hottest mixin (`SteepSlopePredicateMixin`) for no benefit.
- **work-12** (align `GlobalNetworkBuilder.build`/`LocalNetworkBuilder.build` contracts) —
  option B chosen: rename `Result` → `Context`, have `RiverProvider` pre-allocate it and pass it
  into both builders, making `GlobalNetworkBuilder.build` `void` too (matching
  `LocalNetworkBuilder`'s existing style). Needs verification before implementing: whether
  `RiverNetwork` supports "constructed empty, populated in place," and whether
  `RiverProvider.computeTile` is the only caller of `GlobalNetworkBuilder.build`.
- **work-15** (replace `instanceof` primitive dispatch with polymorphism) — superseded by the
  `getCarver()`/`ShellCarver`/`BedCarver`/`PriorityGroup` design above.

## Docs to update once the open questions are answered

- **`hydrology/profile/README.md` — already covers this, no longer a gap.** It documents the
  shell/bed split, the merge law, and the priority mechanism in detail (verified 2026-09-05); this
  document's corrections above were checked against it. What it does *not* cover is the proposed
  `getCarver()`/`ShellCarver`/`BedCarver`/`PriorityGroup` design below — add that once the open
  questions are settled.
- **`hydrology/profile/CLAUDE.md` — currently wrong, not just incomplete.** Its
  `RiverInfluenceCarve.java` row claims `carveRiverInfluenceGrid` wraps `computeRiverGrid` for the
  shell; per the corrected mapping above, it does not — `carveRiverInfluenceGrid` wraps
  `computeRiverInfluenceGrid`/`carveRiverPrimitiveInfluence`, a separate code path, and
  `computeRiverGrid` is the bed pass. This is the same swap this document had; fix it there too.
- **`ARCHITECTURE.md`** (repo root, "Hydrology carve pipeline" section) — still uses the
  pre-rename name `carveRiverInfluence` (not `carveRiverInfluenceGrid`) for the shell entry point.
  Its shell/bed description is otherwise correct (it already states the shell pass has no radial
  branch), so this is a naming touch-up, not a correctness fix.
- `hydrology/features/CLAUDE.md` — `HistoricPrimitive.java`'s row needs updating once it's wired
  into a carve family (currently accurate as "no pass carves this yet," which will become stale).
- `hydrology/README.md` — if the tile-level shell (`GlobalNetworkBuilder`/`LocalNetworkBuilder`/
  `RiverProvider.carveRivers`) vs. per-chunk bed (`PopulateNoiseStep`) timing isn't already
  explicit there, it should reference this split.
