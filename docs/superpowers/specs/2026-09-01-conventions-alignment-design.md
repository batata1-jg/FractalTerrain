# Conventions alignment: fastutil collections, injection seams, and the hydrology package cycle

Date: 2026-09-01
Status: proposed — nothing here has been implemented
Branch: `feature/hydrology`
Measured at: `3c7e681`, **with a dirty working tree** — see "Measurement caveats"

## Problem

Three conventions this project has written down are unevenly applied in the code.

`performance.md:42` makes fastutil a MUST wherever a Set, Map, or List is needed, on the grounds that
this project's data is primitive-keyed (block, tile and chunk coordinates) and a generic `java.util`
collection boxes every key and value. `src/main` currently contains **162 `java.util` collection
instantiations** — 133 in production code, 29 under `debug/`. The de facto pattern the convention cites
(`FractalTerrainChunkGenerator` lines 7-9) is followed in three files out of forty-four.

`class-structure.md` orders members API-first. Two classes this work opens violate it, and one
(`HydrologyProfileInprinter`) additionally conflates two unrelated roles: a stateless tile-level carve
helper that `RiverProvider` calls, and a provider-backed painter that holds a `RiverProvider` field.
That conflation is what produces a bidirectional package cycle between `hydrology/profile` and
`hydrology/providers`.

Separately, collaborators are reached through global static state rather than injection.
`FractalTerrainInstance` acts as a service locator at 16 call sites; the sharpest case is
`BiomeProvider`, whose nested static density classes hold no reference to the enclosing provider and so
route back through the locator to read their own outer instance's `finalTiles`.

None of this is new work in kind. The 2026-07 refactor pass landed Phase A of a singleton-to-DI
migration and deferred Phase B; `relief/DecoderChannels` already exists purely to break the analogous
`relief ↔ providers` cycle. This spec finishes both patterns and applies the collection rule.

## Decisions

Four decisions were taken during design. Each is load-bearing; changing one changes the shape of the
work.

1. **One spec, partitioned by output risk, not by subsystem.** The property that binds these items is
   whether they can change generated terrain, which is also the property the test suite cannot
   currently check. Group A is provably output-neutral; Group B is not. Each group gets its own gate.

2. **Iteration-order drift is accepted.** Migrating a `HashMap` or `HashSet` changes its iteration
   order, and several sites feed primitive emission, so terrain for a given seed will change. The
   alternative — sorting keys at every emission site, extending the pattern already at
   `RiverNetwork:302-303` and `:476` — was considered and rejected as not worth the sort cost and
   per-site analysis.

   **Consequence, stated plainly:** re-baselined golden fixtures cannot distinguish an intended drift
   from a migration bug, because both look like "output changed". Decision 3 exists to restore an
   oracle that can.

3. **Order-independent invariants are the real gate for Group B.** Properties that hold regardless of
   iteration order pass under intended drift and fail on a genuine bug. These are written and landed
   *before* any Group B change, against known-good output.

4. **Approach 1 for declared types.** Lists change at the instantiation only and keep `java.util.List`
   as the declared type, because `ObjectArrayList` implements `List` and no caller needs a
   primitive-specialised list API — 123 sites land with zero signature ripple. Primitive-keyed maps and
   sets get fastutil *interface* declared types (`Int2ObjectMap`, `IntSet`) over open-hash
   implementations, because routing through `java.util.Map` re-boxes and forfeits the entire point.

## Method

Instantiation counts come from
`grep -rno "new \(ArrayList\|HashMap\|HashSet\|LinkedList\|LinkedHashMap\|TreeMap\|TreeSet\|ArrayDeque\|PriorityQueue\)<" --include=*.java src/main`,
split on the `debug/` path prefix. Declared types were then read per site to classify key and value
types, because the instantiation alone does not reveal whether a map is primitive-keyed.

Order sensitivity was established two ways: by grepping for `.entrySet()`, `.keySet()` and `.values()`
to find every iterated collection, and by grepping for existing determinism guards. The second is what
makes the risk concrete rather than theoretical — `RiverNetwork:302-303` reads *"sorted for a
deterministic emission order independent of HashMap/HashSet iteration"*, and `Centreline:72-74` records
that its tie-break exists so the result *"does not depend on `Endpoint#incoming`'s hash-set iteration
order"*. Guards at some sites imply load-bearing hash order at the unguarded ones.

fastutil availability was verified against the resolved artifact rather than assumed:
`~/.gradle/caches/modules-2/files-2.1/it.unimi.dsi/fastutil/8.5.9/…/fastutil-8.5.9.jar`. **Version
8.5.9.** Every class named in this spec was confirmed present with `unzip -l`, and the three APIs the
design depends on were confirmed with `javap`.

## Measurement caveats — read before trusting a line number

- **The working tree was dirty when measured.** `git status` at `3c7e681` shows
  `src/main/.../world/biome/BiomeProvider.java` and `ClimateToBiomeTransformer.java` modified, and
  `ClimateVariableTransform.java` staged for deletion. `BiomeProvider` is a B4 target, so its line
  references may already have moved. Re-grep before editing; do not trust a line number here as
  current.
- Counts are grep counts, not compiler facts. A commented-out or string-literal occurrence inflates
  them.
- `humidityFromBodiesOfWater` is dead in this branch but is being developed on another branch and will
  be merged. It and `HUMIDITY_FALLOFF` (`BiomeProvider:42`, referenced only from the commented-out line
  inside that method) are **excluded from every item in this spec**. This cannot be recorded as a code
  comment: `temporal.md` forbids merge and branch narratives in comments as planning artifacts.
- `gradle test` **cannot run at all** at `3c7e681`. `:compileTestJava` fails because
  `ConfluencePrimitiveTest` calls `ConfluencePrimitive.w(double[])` and `.d(double[])`, neither of
  which exists. The last usable figure — measured with that file excluded — was 77 tests, 18 failing.
  Treat that as a claim to re-verify, not a baseline.

---

# Group A — output-neutral

Provably cannot change generated terrain. Gate for every item:

```
gradle spotlessApply && gradle compileJava compileClientJava spotlessCheck
```

`gradle build` is **not** the gate: it fails at `:compileTestJava` for the pre-existing reason above,
until B0 lands.

## A1. Delete `ReliefProvider.getLowFreqGrad`

`relief/ReliefProvider.java:132-135`. Public, zero call sites, docstring already reads *"Dead ahead of
this change and not this change's to remove."*

Delete the method and its docstring. Drop imports the deletion orphans.

**Do not touch `BiomeProvider.humidityFromBodiesOfWater`.** The earlier dead-code survey paired these
two; only this one is deletable.

Verify: `grep -rn 'getLowFreqGrad' src/main/java src/test/java` returns nothing.

## A2. 123 `ArrayList` → `ObjectArrayList`

Swap the instantiation, never the declared or parameter type:

```java
// Before
final List<HydrologicalPrimitive> primitives = new ArrayList<>();
// After
final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>();
```

`ObjectArrayList` implements `java.util.List`, so no signature changes anywhere. Lists have no
hash-order concept and preserve insertion order identically, so output is bit-identical — this is why
123 of the 162 sites sit in Group A rather than Group B.

Distribution: `RiverNetwork` 19, `AtomicView` 18, `QuinticHermiteSpline` 9, `QuadTree` 7,
`ImmutableQuadTree` 7, `Skeletonizer` 7, `MarchingSquares` 7, `InfiniteTensor` 7, `ImmutableRTree` 6,
`ReachRosgenClassifier` 3, `GlobalNetworkBuilder` 3, `RiverProvider` 2, `GlobalRiverProvider` 2, and one
each in `Storage`, `LatentStage`, `Meanders`, `GradientNetworkRelaxation`.

`performance.md:59-61` requires a primitive array over any collection for fixed-size or inner-loop data.
Where a site qualifies, flag it in the task report rather than converting it — converting it to
`ObjectArrayList` would satisfy the letter of the rule and miss its point.

Verify: `grep -rn 'new ArrayList<' src/main/java | grep -v '/debug/'` returns nothing; golden PNG dumps
byte-identical to a pre-change run.

## A3. Split `HydrologyProfileInprinter`; break the package cycle

The class serves two roles. `RiverProvider` calls only its static members —
`carveRiverInfluence` (`:35`, from `RiverProvider:180`) and `shellDistanceField` (`:487`, from
`RiverProvider:183`) — and never touches the instance side. Meanwhile the instance side holds a
`RiverProvider` field (`:24`) used at `:64`. So a class that needs a `RiverProvider` to function is also
the home of the helpers `RiverProvider` calls during tile build.

The cycle, confirmed bidirectional:

| Direction | Sites |
| --- | --- |
| `profile` → `providers` | `HydrologyProfileInprinter:10`, `HydrologyProfilePainter:5` |
| `providers` → `profile` | `RiverProvider:18` (`HydrologyProfileInprinter`), `RiverProvider:19` (`RosgenProfile`) |

Extract the stateless half into a new class, following the `relief/DecoderChannels` precedent — whose
own docstring states it exists as a stateless static helper to avoid an instance dependency cycle
between two providers.

New class, ordered per `class-structure.md` (API surface, then fields, then privates, then debug-only):

```java
/**
 * Stateless tile-level shell carve over river primitives. Split from the provider-backed
 * painter so the tile pipeline can pre-carve without depending on {@link RiverProvider}.
 */
public final class RiverInfluenceCarve {
    // 1 — API surface
    private RiverInfluenceCarve() {}
    public static void carveRiverInfluence(float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize)
    public static float[] shellDistanceField()
    public static int maxLutLen(int gridSize, double resolution)
    public static int computeRiverGrid(...)

    // 2 — fields
    public static final double UNSET_MIN_DIST = 64;
    private static final ThreadLocal<GridBuffers> SHELL_BUFFERS = ...;

    // 3 — private methods
    private static void computeRiverInfluenceGrid(...)
    private static void carvePrimitive(...)

    // 4 — debug/test-only
    //     GridBuffers, if harnesses reach it
}
```

The name is a proposal; anything that reads as "stateless carve" is fine.

`RosgenProfile.of(...)` at `RiverProvider:211` is the second `providers` → `profile` edge. The import
edge fully clears only if that is addressed too; if it is left, the win is the role split alone, which
is still worth having. Decide during implementation and record which.

`HydrologyProfileInprinter` keeps only its instance half and, being substantially rewritten, gets the
same ordering treatment — today its field precedes its constructor (`:24` before `:26`) and
`UNSET_MIN_DIST` (`:83`) is stranded between two public methods.

**Do not reorder `HydrologyProfilePainter`.** It is untouched by the extraction and already a clean
one-way consumer, so the churn exclusion in `class-structure.md` applies.

## A4. `debug/` — 29 sites (optional, last)

Same mechanical treatment as A2 and B1, applied to `debug/`. Cold-path visualisation code, zero risk.

`debug/` is exempt from cleanup by a 2026-08-19 decision, and "repo-wide" was the later instruction;
this item exists to satisfy the latter without letting it block anything. Land last, or drop it.

---

# Group B — output-affecting

**Terrain output changes here by design** (Decision 2). Gate: the B0 invariants must pass; goldens are
re-baselined once, at the end, in a fixtures-only commit.

Land one item per commit. Two of these in one commit makes a terrain diff unattributable.

## B0. Repair the suite and write the invariants — lands first, alone

Nothing else in Group B starts until this is green.

**Repair.** `src/test/.../hydrology/features/ConfluencePrimitiveTest.java` calls
`ConfluencePrimitive.w(double[])` and `.d(double[])`; `ConfluencePrimitive` implements only
`h(double[])`. 9 errors. Fix the test against the real API, or delete the assertions that reference
methods which do not exist — read the class first to decide which. This is the single change that turns
`gradle test` from "cannot compile" into a usable instrument.

Then re-measure and record the true baseline. The quoted 77/18 figure is stale by construction.

**Invariants.** New tests asserting properties that survive iteration-order drift. For a fixed seed:

| Invariant | Catches |
| --- | --- |
| Channel and node counts stable | Collections silently dropping or duplicating entries |
| Flow conservation at confluences — tributary sum equals downstream, within epsilon | Wrong map lookups feeding the flow accumulation |
| Connectivity — no orphaned channels; every non-source node has an inbound arm | Graph edges lost through a bad key type |
| Total carved volume within epsilon | Aggregate carve drift from a mis-migrated primitive list |
| **Run-twice determinism** — same seed, same process, byte-identical output | A genuinely unordered structure reaching emission |

The last is the most important. This migration's realistic failure mode is not "different terrain" —
that is expected — but "different terrain *each run*", which a re-baselined golden would never catch and
which would make every later golden flaky.

Epsilon values and exact assertion APIs are written against the real classes during implementation;
they are not guessable from this document.

## B1. 16 `HashMap` / 11 `HashSet` → fastutil

Declared as fastutil interfaces, implemented by open-hash classes (Decision 4).

| Site | Current | Target |
| --- | --- | --- |
| `ChannelElevationAssigner:32,39,101,102` | `Map<Integer, Double>` | `Int2DoubleMap` |
| `ChannelElevationAssigner:41` | `Map<Integer, Integer>` | `Int2IntMap` |
| `ChannelElevationAssigner:42` | `Map<Integer, Double>` | `Int2DoubleMap` |
| `GlobalNetworkBuilder:81,136,138` | `Map<Long, CellInfo>` | `Long2ObjectMap<CellInfo>` |
| `GlobalNetworkBuilder:85` | `Map<Long, Integer>` | `Long2IntMap` |
| `GlobalNetworkBuilder:86` | `Map<EdgeKey, Integer>` | `Object2IntMap<EdgeKey>` |
| `GlobalNetworkBuilder:87` | `Map<Integer, Double>` | `Int2DoubleMap` |
| `RiverNetwork:54,55` | `Map<Integer, Channel>`, `Map<Integer, Endpoint>` | `Int2ObjectMap<…>` |
| `RiverNetwork:226` | `Map<Integer, Integer>` | `Int2IntMap` |
| `RiverNetwork:598` | `Map<Integer, double[]>` | `Int2ObjectMap<double[]>` |
| `AtomicView:40` | `Map<Integer, int[]>` | `Int2ObjectMap<int[]>` |
| `AtomicView:227` | `Set<Long>` | `LongSet` |
| `ReachRosgenClassifier:31` | `Map<Integer, RosgenType[]>` | `Int2ObjectMap<RosgenType[]>` |
| `ReachRosgenClassifier:62` | `Map<Integer, Boolean> seen` | `IntSet` — a set is the right shape |
| `MarchingSquares:91,145` | `Map<Long, Segment>` | `Long2ObjectMap<Segment>` |
| `MarchingSquares:131` | `Set<Long>` | `LongSet` |
| `Endpoint:37` | `public final Set<Integer> incoming` | `IntSet` — public field, ripples to callers |
| `QuadTree:44` | `public final Set<T> points` | `ObjectSet<T>` |

**Every primitive-valued map gets `defaultReturnValue(-1)` immediately after construction.** fastutil
primitive-valued maps return a default (0), never `null`, on a miss. `GlobalNetworkBuilder:85`
(`centerIdx`) and `RiverNetwork:226` (`endpointToAtomicId`) hold node indices **where 0 is valid**, so an
unset default silently returns a real-looking node instead of signalling absence.

Object-valued fastutil maps still return `null`, so sites like
`final CellInfo c = cells.get(...); if (c == null) continue;` are unaffected.

Audit every `get` on a primitive-valued map **by hand**. A repo-wide grep for inline null comparison
found exactly one site (`Storage:234`, on an excluded `ConcurrentHashMap`), but it cannot see
`x = map.get(k)` and `if (x == null)` split across lines. The grep is a lower bound, not a clearance.

## B2. Ordering-sensitive collections

Where ordering *is* the contract, the target must preserve it. Migrating any of these to a plain
open-hash set silently destroys a documented determinism guarantee.

| Site | Current | Target | Contract |
| --- | --- | --- | --- |
| `AtomicView:123` `ready` | `TreeSet<Integer>` | `IntSortedSet` / `IntAVLTreeSet` | *"ascending atomic id — deterministic frontier"* |
| `AtomicView:258` `candidates` | `TreeSet<Integer>` | `IntSortedSet` / `IntAVLTreeSet` | *"ascending → deterministic"* |
| `RiverNetwork:304` `structural` | `TreeSet<Integer>` | `IntSortedSet` / `IntAVLTreeSet` | *"sorted for a deterministic emission order"* |
| `InfiniteTensor:124` `pendingSet` | `LinkedHashSet<TileKey>` | `ObjectLinkedOpenHashSet<TileKey>` | insertion order |
| `OnnxModel:365` `feed` | `LinkedHashMap<String, OnnxTensor>` | `Object2ObjectLinkedOpenHashMap` | ONNX input feed order |
| `Storage:52` `cachedEntryByteSizes` | `LinkedHashMap<TileKey, Long>` | `Object2LongLinkedOpenHashMap` | iterated at `:328` |

**API change, not a rename.** `IntSortedSet` has no primitive `pollFirst()` — verified against 8.5.9,
which exposes `firstInt()`, `lastInt()` and `remove(int)`. `AtomicView:126`'s `ready.pollFirst()` becomes
`firstInt()` followed by `remove(...)`. Calling the inherited boxing `pollFirst()` would compile and
quietly reintroduce the allocation this migration exists to remove.

## B3. `RiverProvider` LRU tile cache

`RiverProvider:77-78` is an access-ordered `LinkedHashMap` with eviction, wrapped in
`Collections.synchronizedMap`:

```java
return Collections.synchronizedMap(new LinkedHashMap<>(RECENT_TILE_CAPACITY, 0.75f, true) {
    // removeEldestEntry override
```

Precedent: `storage/FractalTerrainHeightmapCacheAccessor:13` already uses
`ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(MAX_CACHE_ENTRIES))`.

8.5.9 provides every primitive this needs — confirmed by `javap`:
`getAndMoveToFirst(long)`, `removeLast()`, `removeFirst()`, `firstLongKey()`, `lastLongKey()`.

Three semantic gaps, none a type swap:

1. `accessOrder=true` reorders on every `get`. fastutil requires an explicit `getAndMoveToFirst` — a
   plain `get` does **not** refresh recency, so a straight swap silently degrades the cache to
   insertion-order eviction.
2. `removeEldestEntry` has no equivalent. Eviction becomes an explicit size check against
   `RECENT_TILE_CAPACITY` followed by `removeLast()`.
3. fastutil is **not thread-safe**. Either keep the `Collections.synchronizedMap` wrapper, or follow the
   precedent to `ThreadLocal`. `RiverProvider:53` documents that `buildTile` is deterministic so a cache
   miss is *"never a correctness issue"*, which makes `ThreadLocal` viable — **verify that claim holds
   before relying on it**, because it is the entire argument for dropping synchronisation.

Also replace the `java.util.*` wildcard import at `RiverProvider:7`.

Verify: eviction fires at capacity; recency actually refreshes on hit; hit rate not materially worse
under `gradle runClient`; no `HydrologyResult` retention.

## B4. Injection seam

Two independent lifecycle problems. **Do not interleave them** — both change construction order, and
interleaving makes a regression unattributable.

**B4a — `BiomeProvider` density classes.** Five sites (`:433, :450, :478, :517, :522`) where nested
static classes reach the enclosing provider through `FractalTerrainInstance.getBiomeProvider()` to read
`finalTiles`. Thread the tile store in as a constructor parameter; read its declared type off the field
rather than assuming it.

Read `ARCHITECTURE.md` on provider construction order first. The locator may be papering over a real
initialisation-order constraint — **if it is, stop and report that instead of forcing the change.** That
is the most likely way this item turns out to be a bad idea.

`class-structure.md` ordering is **not** re-applied here: commit `bb30059` already ordered these classes
API-first, and re-laying them out would undo deliberate work. The diff shows the new parameter and the
removed locator calls, nothing else. `BiomeProvider` is also modified in the working tree and contested
by another branch, so keep the diff tight.

**B4b — `ml/models`.** `PipelineModels:22-88` is a `volatile static` singleton behind `getInstance()`,
with one external consumer (`FractalTerrainInstance:47`), so constructor injection is a narrow change.
`ModelAssetManager:44-54` resolves its asset root in a static initialiser calling
`FabricLoader.getInstance().getGameDir()` with a hardcoded `run/` fallback; that fallback exists to
support running outside a Fabric game dir, so preserve the capability as an injected path rather than
deleting it.

Find the 2026-07 Phase A record under `.superpowers/` before starting — Phase B was scoped there, and
that scoping outranks anything inferred fresh from the code.

`PipelineModels` is substantially rewritten, so `class-structure.md` ordering applies to it.

---

## Verification

| Stage | Command | Standard |
| --- | --- | --- |
| Every item | `gradle spotlessApply` then `gradle compileJava compileClientJava spotlessCheck` | Must pass. Not `gradle build` — it fails at `:compileTestJava` until B0. |
| After B0 | `gradle test` | Compiles and runs; record the true baseline |
| Every Group B item | `gradle test` | B0 invariants pass |
| B3, B4 | `gradle runClient` | Loads, generates, no construction-order failure |
| B1, B2 | `gradle globalRiverTest`, `gradle riverTest` | PNG dumps inspected; drift expected, *structure* plausible |
| Group A | as above, plus PNG diff | Dumps **byte-identical** — any diff is a bug |
| End of Group B | re-baseline goldens | Fixtures-only commit, no production code |

The asymmetry is the point: Group A must not change output, Group B is expected to. A byte-identical
requirement on Group A is what makes it safe to land without the invariants in place.

## Files

**Created:** the new stateless carve class (A3); invariant test classes (B0).

**Modified — production:** `relief/ReliefProvider` (A1); ~17 files for the list swap (A2);
`hydrology/profile/HydrologyProfileInprinter`, `hydrology/providers/RiverProvider` (A3, B1, B3);
`hydrology/ChannelElevationAssigner`, `hydrology/GlobalNetworkBuilder`, `hydrology/network/RiverNetwork`,
`hydrology/network/AtomicView`, `hydrology/network/Endpoint`, `hydrology/rosgen/ReachRosgenClassifier`,
`math/MarchingSquares`, `math/ds/QuadTree`, `infinitetensor/InfiniteTensor`, `ml/models/OnnxModel`,
`storage/Storage` (B1, B2); `world/biome/BiomeProvider`, `ml/models/PipelineModels`,
`ml/models/ModelAssetManager` (B4).

**Modified — test:** `ConfluencePrimitiveTest` (B0); golden fixtures (re-baseline).

**Not modified:** `build.gradle` — fastutil 8.5.9 is already on the classpath transitively via
Minecraft/Fabric, and `performance.md:51-53` states adding a dependency line would be redundant.

## Out of scope

- `BiomeProvider.humidityFromBodiesOfWater` and `HUMIDITY_FALLOFF` — dead here, merging from another
  branch.
- The four concurrent structures: `Storage:37`, `Storage:40`, `FractalTerrainHeightmapCache:19`,
  `NoiseSampler:11`. fastutil ships no concurrent collections — there is nothing to migrate to. This is
  an absence of a target, not a risk judgement.
- `noise/` — byte-identity vendored; its own `CLAUDE.md` forbids reformatting.
- Minecraft-owned registry iteration: `LevelUtilsMixin:39`, `FractalTerrainBiomeSource:51,100-124`.
- `RiverNetwork:783` — the `// TODO: fix this, not all drains are deltas` correctness gap. Real, and a
  hydrology modelling question, not a refactor. Tracked separately.
- `instanceof` dispatch on `HydrologicalPrimitive`. Of 9 matches, `RiverPrimitive:126` is `equals()` and
  `HydrologyProfileInprinter:162` is a run-length loop guard; 4 of the remainder are in exempt `debug/`.
  Three production sites is too thin to justify reshaping the interface contract.
- Any reordering under `class-structure.md` beyond the three classes named in A3 and B4b.

## Risks

**Silent recency loss in B3.** A `get` that does not call `getAndMoveToFirst` compiles, passes tests,
and degrades the LRU to insertion-order eviction. Nothing fails; the cache just gets worse. Mitigated by
asserting recency behaviour directly rather than inferring it from hit rate.

**`defaultReturnValue` on index-valued maps.** A miss returning 0 where 0 is a valid node index produces
a plausible wrong graph, not an exception. Mitigated by the mandatory `defaultReturnValue(-1)` rule and
by hand-auditing every `get`, since grep cannot find split null-checks.

**Boxing `pollFirst` in B2.** Compiles, works, reintroduces the allocation the migration exists to
remove. Mitigated by naming the exact replacement (`firstInt()` + `remove`).

**B4a may be structurally blocked.** If the service locator encodes a real initialisation-order
constraint, the change is not merely hard but wrong. The instruction is to stop and report, not to force
it.

**Drift masks bugs during Group B.** This is the accepted cost of Decision 2 and the reason B0 lands
first. If the invariants prove too weak in practice, the correct response is to strengthen them, not to
proceed without an oracle.

**Line numbers are stale.** Measured against a dirty tree with `BiomeProvider` modified and a second
branch in flight. Re-grep before every edit.
