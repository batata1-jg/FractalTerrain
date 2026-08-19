# Dead-code survey — 2026-08-19

Reachability inventory of `src/main`, taken at `6a7086e` (branch `feature/hydrology`).
**Nothing has been deleted.** Every row is a recommendation awaiting a decision.

## Method

A class is dead when its simple name appears in no other `.java` file under `main/`, `client/` or
`test/`, **and** in no file under `resources/` or `build.gradle`. The resource check is what keeps
mixins and the Fabric entrypoint out of the list — they are referenced only from JSON.

The scan is **transitive**: after removing a dead class, the scan re-runs, because a class whose only
reference came from a dead class is itself dead. Three rounds were needed to reach a fixed point,
which is why the count grew from 9 to 13.

### Caveats — read before deleting anything

- This is grep reachability, not a compiler reachability proof. It cannot see reflection, and it
  matches on simple names, so a class sharing a name with a common identifier could hide.
  Mitigating fact: `grep -rn 'Class.forName\|getDeclaredMethod\|getDeclaredField\|newInstance('`
  over `src/main` returns **zero hits**, so there is no reflection surface in this codebase.
- Mixin `@Inject` handler methods are called by the mixin framework, never by name from Java. They
  look dead and are not. Two are excluded on this basis, listed under "Not dead" below.
- Minecraft/Fabric `@Override` methods are invoked by the engine. All are excluded.
- `noise/FastNoiseLite.java` is vendored third-party code and is excluded wholesale.
- **`debug/` is exempt by decision** (2026-08-19) — debug utilities are intentionally retained even
  when unreferenced. Listed for completeness, never recommended for deletion.

### Verification

```
gradle spotlessApply && gradle build      # compile proof
gradle test                               # baseline: 77 tests, 20 failed, 1 skipped
gradle runClient                          # catches resource-referenced classes grep cannot see
```

Delete in small batches, compiling between each. The compiler is the real oracle.

---

## Tier 1 — Transitively dead classes

13 classes, **489 lines**. Excluding the exempt `debug/` entry: **12 classes, 402 lines**.

| Class | Lines | Round | Verdict | Rationale |
| --- | --- | --- | --- | --- |
| `registry/FractalTerrainRegistryKeys` | 3 | 1 | **delete** | Empty class body. Nothing to preserve. |
| `terrablender/InitTerrablender` | 3 | 1 | **delete** | Empty class body. The `terrablender/` package contains nothing else. |
| `noise/filters/Filter` | 6 | 2 | **delete** | Abstract base whose only subclass is the equally dead `ErosionFilter`. |
| `registry/SettingsRegistry` | 6 | 2 | **delete** | Referenced only by the dead `RockStrata`. |
| `math/MaskedOps` | 10 | 2 | **delete** | Referenced only by the dead `RockStrata`. |
| `references/ModScreenHandelers` | 14 | 1 | **delete** | Constructor throws `AssertionError`; `register()` only logs. Vestigial mod-template scaffolding — this mod has no screens. |
| `noise/filters/ErosionFilter` | 17 | 1 | **delete** | Stub: `sample()` returns `0`, the constructor discards its `seedOffset`, and the `sampler` field is never assigned. |
| `math/Gradients` | 24 | 1 | **delete** | Sobel kernels plus two `private static` helpers that nothing calls. The class exposes no public API at all — unreachable by construction. |
| `world/gen/chunk/FractalTerrainChunkNoiseSampler` | 39 | 1 | **delete** | Pure pass-through subclass of vanilla `NoiseChunk`: the constructor forwards all nine arguments to `super` and adds no behaviour or overrides. |
| `noise/VoronoiNoiseSampler` | 55 | 3 | **decide** | Real implementation, reachable only through the dead `ErosionFilter`. Delete unless Voronoi noise is wanted for a future filter. |
| `debug/MemoryProfiler` | 87 | 1 | **keep — exempt** | `debug/` exemption. |
| `relief/RockStrata` | 111 | 1 | **decide** | Half-built feature, not merely unused: `Settings.CODEC` is initialised to `null`, which would NPE on any registry round-trip. Pairs with the dead `FractalTerrainSurfaceSystem.sedimentStrata` (Tier 2) — the same abandoned rock-strata feature. Deleting takes `SettingsRegistry` and `MaskedOps` with it. |
| `math/FieldLinePlacer` | 114 | 1 | **decide — author intent** | The only entry with explicit evidence of intent: `@SuppressWarnings("unused") // accepted now; wired into the field shaping later by the user`. Fully documented and implemented. This is planned work, not an accident — **the author should confirm before this goes.** |

**Cluster note.** Deleting `RockStrata` and `ErosionFilter` is what makes five other classes
(`SettingsRegistry`, `MaskedOps`, `Filter`, `VoronoiNoiseSampler`, and transitively their imports)
collectible. Deleting them individually in the wrong order leaves orphans that a single-pass scan
will not flag. Delete each cluster whole:

- **Rock-strata cluster:** `RockStrata` → `SettingsRegistry`, `MaskedOps`, and
  `FractalTerrainSurfaceSystem.sedimentStrata`/`isStone` (Tier 2).
- **Filter cluster:** `ErosionFilter` → `Filter`, `VoronoiNoiseSampler`.

---

## Tier 2 — Dead private methods in live classes

A `private` method whose name appears exactly once in its own file — the declaration — has no
possible caller. These are definitive, with no reflection caveat.

| Location | Method | Verdict | Note |
| --- | --- | --- | --- |
| `world/gen/surfacebuilder/FractalTerrainSurfaceSystem` | `sedimentStrata` | **delete** | Remnant of the `RockStrata` feature. Delete with that cluster. |
| `world/gen/surfacebuilder/FractalTerrainSurfaceSystem` | `isStone` | **delete** | Same cluster. |
| `world/biome/BiomeProvider` | `riverHumidity` | **decide** | River-driven humidity. Plausibly wanted; confirm it was not meant to be wired into the biome parameters. |
| `infinitetensor/FloatTensor` | `checkShapes` | **decide** | A disabled validation helper. If shape checking is wanted, wire it up; otherwise delete. |
| `FractalTerrain` | `addListenerForDynamic` | **decide** | Unused lifecycle-listener registration on the mod entrypoint. |

Excluded as **not dead** despite matching the pattern:

| Location | Method | Why it stays |
| --- | --- | --- |
| `mixin/PlacedFeatureMixin` | `VegetationPlacementAlter` | Mixin `@Inject` handler — invoked by the mixin framework. |
| `mixin/SteepSlopePredicateMixin` | `correctSteep` | Mixin `@Inject` handler. |
| `debug/tests/GlobalRiverTest` | `coastPresence` | `debug/` exemption. |
| `storage/Storage` | `Object` | Scan artifact, not a real method. |

---

## Tier 3 — Unreferenced public methods in live classes

**66 methods across 32 files**, after excluding `@Override`, `debug/`, `FastNoiseLite`, and the
Tier 1 dead classes. These are the least certain tier and need judgement per group, not a blanket
sweep — a public method with no caller may be a deliberate API, a test seam, or genuinely dead.

| Count | File | Character of the group | Suggested handling |
| --- | --- | --- | --- |
| 8 | `world/biome/BiomeParameterClassifier` | `isOcean`, `isInland`, `isMidInland`, `isDeepOcean`, `isMushroomFields`, … — a complete predicate vocabulary where nothing calls any member | **Investigate as a unit.** Either the classifier is unused entirely, or it is an intentionally complete vocabulary. All-or-nothing, not method-by-method. |
| 5 | `storage/Storage` | `printCurrentEntrySet`, `printEntryMapHash`, `getCachedKeys`, `getEntryDir`, `addOrOverwriteEntry` | The two `print*` are debugging aids in a non-`debug/` file — the exemption arguably should cover them. The rest are real API surface. |
| 5 | `math/spline/QuinticHermiteSpline` | `appendFront`, `appendBack`, `firstDerivative`, `secondDerivative`, `nextInSpline` | Library-style completeness on a general-purpose math type. **Low priority** — cheap to keep, and derivatives are plausibly needed by future meander work. |
| 4 | `math/ds/ImmutableQuadTree` | `containsNaN`, `checkResultNaN`, `checkBoxQueryNaN`, `checkCircleQueryNaN` | NaN-validation harness for the spatial index. Given the known `findSection` tiling bug, these are **worth keeping** — possibly worth wiring into a test. |
| 4 | `ml/pipeline/WorldPipelineModelConfig` | `nativeResolution`, `dropWaterPercent`, `elevCoarsePoolMode`, `p5CoarsePoolMode` | Config accessors mirroring the model manifest. Keep — they document the manifest schema even when unread. |
| 3 | `ml/pipeline/EDMScheduler` | `computeKarrasSignas`, `preconditionInputsInPlace`, `preconditionOutputs` | Diffusion-sampler steps. **Verify carefully** — if the pipeline lost its preconditioning, that is a bug, not dead code. |
| 3 | `relief/ReliefProvider` | `getReliefTile`, `getLowFreqGrad`, `get_entry` | On the work-1 DI migration path. **Resolve before migrating** — do not port dead accessors to constructor injection. `get_entry` also breaks Java naming convention. |
| 3 | `world/biome/BiomeProvider` | `fillArray`, `minValue`, `maxValue` | Utility helpers. Likely safe deletes. |
| 2 each | `FractalTerrainBiomeSource`, `Skeletonizer`, `DifferenceOfGaussians`, `NonIntersectingSpatialIndex`, `InfiniteTensor`, `FloatTensor`, `AtomicView` | Mixed | Per-method judgement. |
| 1 each | 17 files incl. `MarchingSquares.traceContours`, `VectorOps.project`, `Channel.flowAt`, `Endpoint.isSourceOrDrain`, `RosgenProfile.smoothMax`, `PopulateNoiseStep.smoothStep` | Mixed | Per-method judgement. |

**Two flags in this tier.**

`math/Skeletonizer.zhangSuen` and `tracePolylines`, plus `math/MarchingSquares.traceContours`, are
the *entire public surface* of both classes — the classes are reachable only because something
imports them, but no caller invokes the algorithms. Combined with the dead `FieldLinePlacer`
(which is the documented upstream stage of skeletonization), this suggests a **whole
raster→skeleton→polyline pipeline that was built and never connected**. Worth investigating as one
question rather than three.

`ml/pipeline/EDMScheduler`'s three unreferenced methods are the opposite risk. Karras sigma
scheduling and input/output preconditioning are not optional parts of an EDM diffusion sampler. If
nothing calls them, either the pipeline routes around them deliberately, or output quality is
silently degraded. **Treat as a possible bug before treating as dead code.**

---

## Tier 4 — Tests

| Test | Status | Note |
| --- | --- | --- |
| `hydrology/profile/NearestChannelSampleTest` | **delete** | Does not compile. Wants the deleted `NearestChannelSample` record and a 3-arg `sampleNearestChannel`; the code has a 5-arg `void` one. |
| `hydrology/profile/BlendMinTest` | **delete** | Does not compile. Same missing symbols. |
| `hydrology/profile/PolylineChordErrorTest` | **delete** | Does not compile. Same missing symbols. |
| `hydrology/SpatialIndexCorrectnessGoldenTest` | **delete** | Does not compile. Wants `RosgenProfile.riverInfluence(double)`, which does not exist. |
| `hydrology/network/CentrelineTest` | **decide — see below** | Compiles; 1 failure. |

Deleting the four non-compiling files is what lets the suite run at all, and is already the
documented local workaround in `CLAUDE.md`. Making that deletion permanent removes a standing trap.

### The `CentrelineTest` question

Flagged as stale code. The evidence splits, so this needs an explicit decision:

**The code under test is live and hot.** `Centreline.normalAt` is called from
`HydrologicalPrimitive.java:150` — inside the permanently-hot band per `ARCHITECTURE.md` — and from
`ReachRosgenClassifier.java:191`. `Centreline` itself is constructed at `RiverNetwork.java:760`.
`HydrologyTuning` lines 55 and 60 document two constants specifically in terms of
`Centreline.normalAt`'s stencil.

**The fixture is what looks stale.** The test builds its scenario through `NodeSpec`/`EdgeSpec`
and wedges "a forced 2-point channel between two straight, offset-but-parallel channels sharing its
end nodes." Since `buildFromSpecs` now resamples at `DX`, a *forced* 2-point channel may no longer
be constructible — which would make the fixture, not the function, the thing that went stale.

So the likely reading is **a stale test over live, hot, load-bearing code**. That matters because
deleting it drops the only coverage of `normalAt`.

Options: delete outright (accepting the coverage loss); repair the fixture against current
`buildFromSpecs` behaviour; or delete now and re-derive coverage when the hydrology stage seams
land, which is when a `normalAt` test becomes easy to write. **Author's call.**

---

## Not dead — excluded on purpose

Recorded so a future scan does not re-flag them.

| What | Why it is reachable |
| --- | --- |
| `FractalTerrain` | Fabric entrypoint, named in `fabric.mod.json` (2 resource references). |
| `mixin/LevelUtilsMixin`, `mixin/PlacedFeatureMixin`, `mixin/SteepSlopePredicateMixin` | Named in the mixins JSON (1 resource reference each). |
| `debug/**` | Exempt by decision, 2026-08-19. |
| `debug/tests/{CaptureSelection,Meanders,Pipeline,GlobalRiver,LocalRiver}Test`, `SpatialIndexBenchmark` | `JavaExec` tasks in `build.gradle`. |
| `noise/FastNoiseLite` | Vendored third-party. Do not edit. |
| All `@Override` methods | Invoked by the Minecraft/Fabric engine. |

---

## Summary

| Tier | Items | Recommended delete | Needs a decision |
| --- | --- | --- | --- |
| 1 — dead classes (non-`debug/`) | 12 (402 lines) | 9 (198 lines) | 3 (`FieldLinePlacer`, `RockStrata`, `VoronoiNoiseSampler`) |
| 2 — dead private methods | 5 | 2 | 3 |
| 3 — unreferenced public methods | 66 across 32 files | — | all 66, by group |
| 4 — tests | 5 | 4 | 1 (`CentrelineTest`) |

**Uncontroversial first batch:** the 9 Tier-1 deletes plus 2 Tier-2 methods plus the 4 non-compiling
tests. Roughly 200 lines of production code removed, no design decision required, and it retires the
documented "delete these four files to run the suite" workaround.

**Blocking on the refactor plan:** `ReliefProvider`'s three unreferenced accessors sit on the work-1
DI-migration path and should be resolved before that migration, not after.
