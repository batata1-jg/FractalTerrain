# Hydrological primitive carving — current situation and open design

Status: **design discussion, not yet implemented.** Captures where `RiverInfluenceCarve` and the
primitive type hierarchy stand today, the gap identified against the intended design, and the
questions blocking a settled `getCarver()`-based redesign. Supersedes the "centralize behind
`RiverProvider`" (work-3) and "polymorphic `instanceof` replacement" (work-15) framing from the
2026-09-04 refactor review — both are folded into the design below. The redesign itself is
deferred to another session; only the two method renames below (2026-09-05) have landed.

**2026-09-05 update:** `RiverInfluenceCarve`'s two public/package entry points were renamed for
clarity ahead of the deferred redesign — `carveRiverInfluence` → `carveRiverInfluenceGrid` (the
shell pass) and `carvePrimitiveInfluence` → `carveRiverPrimitiveInfluence` (the bed pass). All
call sites and docs below use the new names; the design content is otherwise unchanged.

## Two carve stages, not one

`RiverInfluenceCarve.java` (`hydrology/profile/`) implements two structurally different carve
passes that share a file but not a lattice:

| | Shell / influence pass | Bed pass |
|---|---|---|
| Entry point | `carveRiverInfluenceGrid` → `computeRiverGrid` | `computeRiverInfluenceGrid` → `carveRiverPrimitiveInfluence` |
| Writes into | `acc` triple-buffer (height, water, weight) | `elevs[]` directly, cut-only (`Math.min`) |
| Called from | `RiverProvider` stage | `PopulateNoiseStep` (much later) |
| Purpose | Large, coarse landscape shaping — makes room for later changes | Per-primitive detail carve, at chunk resolution |
| Primitive coverage today | `RiverPrimitive` + `RadialPrimitive` | `RiverPrimitive` **only** |

The `RiverProvider`-stage pass runs first and shapes the landscape broadly; `PopulateNoiseStep`
runs much later and carves each primitive's own characteristics into the already-shaped terrain.

## The priority mechanism (how "confluence beats river" actually works)

Traced to the real code, not just the concept:

1. `HydrologicalPrimitive.comparator` sorts primarily by `getType().ordinal()`. Enum declaration
   order is `RIVER(0), ABANDONED_RIVER(1), OXBOW_LAKE(2), SOURCE(3), WATERFALL(4), DELTA(5),
   CONFLUENCE(6)` — ascending, so rivers sort first, confluences last.
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
**sibling** interface to `RadialPrimitive` — not a subtype. The carve dispatch in
`computeRiverGrid`/`computeRiverInfluenceGrid` is `instanceof RiverPrimitive` then
`instanceof RadialPrimitive`; `HistoricPrimitive` matches neither. Confirmed by
`OxbowLakePrimitive`'s own javadoc: *"Skeleton. It carries the step that cut it and the width it
was cut at, but no water level and no loop geometry, so it carves nothing of its own and blends
as a plain `DefaultProfile` influence disc."* They're indexed and queryable, but silently
contribute nothing to either carve pass.

This is a known, documented WIP state (`ZoneCategory.LAKE_BED` is already reserved for it), not a
bug — but it means the intended design (below) is an extension, not a refactor of working
behavior.

## Intended design (per project owner, 2026-09-04)

- **River, OxbowLake, and AbandonedRiver primitives should share one influence-carving framework**
  (the shell pass) — a shed meander/captured channel is close enough in shape to a live river
  that the same cross-section carve algorithm should apply to all three.
- **Each primitive type may still have its own bed-carving logic**, independent of which shell
  algorithm it shares. Shell and bed are decoupled per type, not paired.
- **Primitives carved by the same algorithm can still sit in different priority tiers** by using
  different distance fields — this is the general mechanism (not special-cased to
  river-vs-radial) for encoding "type A always wins over type B regardless of relative
  influence/distance value."

## Proposed shape (supersedes the flat `getCarver()` idea)

```java
interface HydrologicalPrimitive {
    ShellCarver<?> getShellCarver();          // cross-section/blend math — shared across a family
    @Nullable BedCarver<?> getBedCarver();     // per-chunk cut — independent, nullable
    PriorityGroup getPriorityGroup();          // which scratch distance-buffer + processing slot
}
```

- `ShellCarver`/`BedCarver` implementations are stateless `static final` singletons — no
  allocation per primitive, no allocation per lattice point. Dispatch happens once per primitive
  (outer loop, dozens per tile), not once per lattice point (thousands) — so even fully virtual,
  non-inlined calls are negligible next to the inner per-point loops that already dominate this
  function's cost. Only 2 concrete carver types exist today (river-shaped, radial-shaped), so any
  dispatch site is bimorphic — HotSpot's inline cache handles that at monomorphic speed; no
  megamorphic cliff to worry about.
- `PriorityGroup` generalizes today's hardcoded `dist`/`radialDist` pair into N buffers, still
  `ThreadLocal`-scoped scratch arrays sized off `GridBuffers` (no allocation enters the hot path).
  Processing order across groups stays centrally defined — either keep riding
  `HydrologicalFeature.ordinal()` or promote it to an explicit ordered list.
- The sequential-recurrence invariant (`comparator`-sorted order is load-bearing; a group's blend
  reads state the earlier group left in `acc`) is preserved by the caller loop, not by the carver
  classes — extracting `getShellCarver()`/`getBedCarver()` only replaces the `instanceof` branch,
  not the loop structure or the shared-buffer threading.

## Open questions blocking implementation

1. **Do OxbowLake/AbandonedRiver reuse the literal `RiverShellCarver` singleton**, or do they need
   their own variant? River's cross-section carve depends on `river.normal()` for a flow tangent
   (`carveRiverPrimitive` bails if `normal == null`); Oxbow/AbandonedRiver are `SpatialIndexCircle`
   or `HistoricPrimitive`-shaped (from `HistoricPrimitive.java`), not tangent-oriented — so literal
   code reuse may need `HistoricPrimitive` to gain a normal/tangent concept, or the shared
   "framework" is a parameterized algorithm rather than one literal implementation.
2. **How many priority tiers are needed?** Today: 2 (river-shaped via `dist`, radial-shaped via
   `radialDist`). Does Oxbow/AbandonedRiver need a 3rd tier (same shell math as rivers, own
   distance buffer, so a shed feature never outranks a live river regardless of relative
   "influence"), or do they slot into the existing river tier?
3. **Bed-carving for Oxbow/AbandonedRiver** — leave `getBedCarver()` returning `null` for now
   (matches the current "Skeleton" javadoc exactly), or build real bed-carve logic for them now?

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

- `hydrology/profile/README.md` — the shell/bed split and the priority-group mechanism aren't
  stated anywhere; this is the natural home.
- `hydrology/profile/CLAUDE.md` — `RiverInfluenceCarve.java`'s row should name both passes and the
  priority mechanism, not just "the stateless carve."
- `hydrology/features/CLAUDE.md` — `HistoricPrimitive.java`'s row needs updating once it's wired
  into a carve family (currently accurate as "no pass carves this yet," which will become stale).
- `hydrology/README.md` — if the `RiverProvider`-stage vs. `PopulateNoiseStep`-stage timing isn't
  already explicit there, it should reference this split.
