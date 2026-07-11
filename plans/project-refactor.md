# FractalTerrain — Major Code-Hygiene Refactor Plan

> Status: **DRAFT for review** · Plan id `fractal-terrain-refactor` · Authored 2026-07-11
> Produced via the planner skill (codebase-analysis → quality-review → problem-analysis → plan-design → plan-QR).
> Quality-review findings are folded in and marked **[QR]**; see *Quality-review revisions* at the end.

## Problem

Behavior-preserving code-hygiene refactor of the FractalTerrain Fabric mod:

- **No god classes** — decompose the 7 oversized classes along surveyed responsibility clusters.
- **Easy/consistent logging** — one idiom, correct logger names, no `System.out/err`.
- **No duplicate utilities/constants** — one home per concept (config, geometry/tuning constants, vector math).
- **Easy segmentation** — replace global-static wiring with an injectable seam so packages are liftable.
- **LLM-friendly in-source docs** — responsibility/collaborators/invariants blocks; author the missing `ARCHITECTURE.md`.
- **Scalable/expandable config** — split the config grab-bag; make `DEBUG` runtime-toggleable.

Plus three concurrency/lifecycle defects surfaced by the quality reviewer, and dead-code removal.

**Generated-terrain output must not change.**

## Root cause (why the hygiene debt exists)

Global-static wiring is the *only* integration seam: `FractalTerrainInstance` exposes ~15 static
getters (and a JVM-lifetime static `WorldPipeline` built in a class-init block), and every consumer
reaches through it (~46 static reach-throughs across 21 files). Cross-cutting concerns (config,
logging, tuning constants, debug) route through that same global plane. Because any class can reach
any collaborator and any constant statically, **nothing forces responsibilities to stay local** —
so responsibilities accrete into god classes, concepts get re-declared in several places, debug/
config/production code interlink, and shared mutable statics are read unsynchronized across the
multi-threaded chunk-generation workers.

## Approach — seams before splits

Phased so that **no god class is split before its collaborators are injectable and its shared
mutable state is frozen**. Splitting first would mechanically touch the shared static
`WorldPipeline`/`FractalTerrainInstance` state and promote the latent MUST-1 reload race into a
routine concurrency bug.

| Phase | Theme | Milestones |
| ----- | ----- | ---------- |
| 0 | Low-risk hygiene | M-001 delete dead code · M-002 unify logging · M-003 decompose config |
| 1 | Test gate | M-004 JUnit golden tests for deterministic layers |
| 2 | Seams + lifecycle MUST-fixes | M-005 awaitLoad→init · M-006 FloatTensor freeze · M-007 PipelineSession · M-008 context keystone |
| 3 | God-class splits | M-009 WorldPipeline · M-010 hydrology · M-011 climate · M-012 FastNoiseLite |
| 4 | Docs | M-013 ARCHITECTURE.md + memory |

### Testing stance (your decisions)

- **ONNX/diffusion half — structural only.** `pipelineTest` needs ~1 GB weights + GPU and is not
  headless/CI-runnable, so MUST-1 / MUST-3 / the WorldPipeline split are verified by
  behavior-preserving-by-construction (mechanical, math-identical extraction) plus focused review.
- **Deterministic layers — JUnit golden gate.** Add `src/test/java`; convert the deterministic
  river/meander/spatial harnesses into golden-assertion tests that fail on divergence. Keep the
  PNG/visual debug dumps.

## Constraints

- MUST preserve generated-terrain output (behavior-preserving; golden-gated where feasible).
- MUST introduce seams (DI + frozen shared state) **before** splitting any god class.
- **MUST-1**: fix the WorldPipeline reload race via an immutable `PipelineSession` swapped behind one
  volatile ref; write the reload-race regression exercise first.
- **MUST-2**: move `PipelineModels.awaitLoad()` out of the static initializer into `init()`.
- **MUST-3**: freeze `FloatTensor.data/.shape` before any tensor-touching split.
- MUST-NOT use the default package; delete the five 100%-commented root files.
- ONNX tensor-layout invariants (`CH=0/X=1/Z=2`, channel counts) and the model I/O contract are fixed
  and constrain WorldPipeline split boundaries.
- Run `spotlessApply` (palantirJavaFormat) before commits; the build enforces `spotlessCheck`.
- No build-system overhaul beyond adding a test source set + its Gradle test task.

## Key decisions

| # | Decision | Rationale |
| - | -------- | --------- |
| DL-001 | Seams-before-splits, phased | Splits touch shared static state; inject + freeze first so the MUST-1 race isn't promoted. |
| DL-002 | Diffusion half verified structurally only | A runtime golden needs ~1 GB weights + GPU; use mechanical extraction + review instead. |
| DL-003 | JUnit `src/test/java` golden tests for deterministic layers | Reproducible fixed-seed harnesses → assert saved goldens → divergence fails the build. |
| DL-004 | MUST-1 via immutable `PipelineSession` behind one volatile ref | Per-field volatility can't make a multi-field read atomic; snapshot once per worker. |
| DL-005 | MUST-2 by moving `awaitLoad()` into `init()` | Avoids `ExceptionInInitializerError`→permanent `NoClassDefFoundError` masking the missing-datapack error. |
| DL-006 | MUST-3 `FloatTensor` immutability contract | Reference-final arrays are contents-mutable and shared via the cache across threads. |
| DL-007 | `FractalTerrainInstance` static locator → context object (thin adapter kept) | ~46 reach-throughs across 21 files make big-bang removal risky; migrate package-by-package. |
| DL-008 | Split config into TensorLayout/DebugConfig/HydrologyTuning/ModConfig; runtime DEBUG | One owner per concern removes duplication; DEBUG cached in final fields to stay cheap. |
| DL-009 | Unify logging on a corrected `Debug` facade | Two idioms coexist; `Debug.getLogger` names loggers via `clazz.toString()` (malformed). |
| DL-010 | Split the four god classes along surveyed clusters, after seams | Preserves behavior while enabling segmentation. |
| DL-011 | Delete the five default-package files outright | 100% commented, stale package; deletion is behavior-preserving. |
| DL-012 | Consolidate hydrology border/sampling constants into HydrologyTuning during the hydrology split | Repointing edits exactly the files being split; avoid touching them twice. |

### Rejected alternatives

- **RA-001** Split god classes first → promotes the MUST-1 race to a routine bug. (DL-001)
- **RA-002** Remove `FractalTerrainInstance` in one pass → 46 reach-throughs make big-bang high-risk. (DL-007)
- **RA-003** Runtime ONNX golden gate → needs ~1 GB weights + GPU, not CI-runnable. (DL-002)
- **RA-004** Rewrite from scratch → much complexity is inherent; refactor along seams. (DL-001)
- **RA-005** Fully dynamic per-check DEBUG reads → hot-path overhead; cache in final fields. (DL-008)

### Invariants (do not violate)

- Seams-before-splits.
- Build order `global → local → relief → biome` (implicit in static-init order) must be preserved in the context object.
- Compile-time `DEBUG_*` were intentionally zero-cost-when-false — a runtime toggle must not add hot-path cost. **[QR: note the tradeoff below.]**
- `Storage`/`InfiniteTensor` lock-ordering and CAS single-flight are deliberate — do not "simplify".
- ONNX tensor-layout invariants (`CH=0/X=1/Z=2`, fixed channel counts) constrain any stage boundary.
- `RiverNetwork`/`QuadTree` reuse is per-tile single-threaded, and config `.properties` I/O is try/caught with safe fallbacks — neither is a concurrency blocker (reviewer-confirmed).

## Risks

| # | Risk | Mitigation |
| - | ---- | ---------- |
| R-001 | Diffusion half has no automated behavior gate; a WorldPipeline-split slip could silently alter terrain. | Mechanical, math-identical extraction + focused review; **[QR]** confine each structural-only split to its own wave so a regression is bisectable. |
| R-002 | The keystone touches ~21 caller files; incomplete migration could leave callers reading a stale/uninitialized context. | Thin adapter over the context; **[QR]** add safe-publication acceptance criteria + own the migration-completion tracking. |
| R-003 | Runtime-toggleable DEBUG could add hot-path overhead. | Read once into final fields. **[QR]** Accept that a runtime `final boolean` is *not* a compile-time constant, so `if(DEBUG)` blocks are no longer dead-code-eliminated; keep genuinely hot-path flags compile-time (or accept one predictable branch). |

## Dataflow & seam boundaries

```
Seed + reload-scoped inputs (seed, syntheticMapFactory, tau)
        │  bundled + published
        ▼
PipelineSession  (immutable, single volatile swap)            [M-007]
        │  snapshot read once per worker
        ▼
WorldPipeline: Coarse → Latent → Decoder → Climate           [M-009]
        │  relief + climate tensor          │ relief tensor
        ▼                                    ▼
Climate/Biome transform                 GlobalRiver network   [M-010]
(classify / shore-dist / transform)          │
        [M-011]                              ▼
        │ biome params            LocalRiver: build / elevation /
        │                          drainage / meanders / carve  [M-010]
        ▼                                    │ carved rivers
        └──────────► Surface / populate → chunk blocks ◄───────┘
```

---

## Milestones

### Wave W-001 — Phase 0

#### M-001 · Delete default-package dead code  `[low-risk]`
Remove the five default-package files (`MapProvider`, `ContinentalScaleMapProvider`,
`FractalTerrainMaterialRules`, `FractalTerrainSurfaceRules`, `GaussianNoisePatchProvider`) — all
100% commented-out, referencing the stale `fractalterrain` package.
- **Accept:** `src/main/java` has no top-level `.java` files; `gradle build` + `spotlessCheck` pass.
- **Test:** build/compile gate. (DL-011)

### Wave W-002 — Phase 0 (parallel)

#### M-002 · Unify logging on a corrected `Debug` facade  `[low-risk]`
Files: `debug/Debug.java`, `debug/TensorVisualizer.java`, **[QR] `debug/tests/PipelineTest.java`**.
- Fix `Debug.getLogger` to derive the name from `clazz.getName()`/`getSimpleName()`, not `clazz.toString()`.
- Delete the no-op `Debug.printStream` (`peek` with no terminal op).
- Route `System.out/err` through the facade in `Debug` (1 site) and `TensorVisualizer` (2 sites).
- **[QR — qa-007]** Also cover the **11** `System.out/err` sites in `debug/tests/PipelineTest.java`
  (lines 54,65,68,97–101,105,108,110), which are within refactor scope. `PipelineTest` stays a manual
  harness, so either route its prints through the facade or explicitly document them as retained
  console output — but the "3 sites" count in the original constraint was incomplete.
- Establish `Debug.getLogger(Class)` as the single logging entry point; migrate direct
  `LoggerFactory` usage opportunistically as other milestones rewrite their files.
- **Accept:** logger name is `fractal_terrain/<class-name>` with no `class ` prefix; no `System.out/err`
  remains in the named files; `printStream` gone. **Test:** unit (name assertion) + grep gate. (DL-009)

#### M-003 · Decompose `FractalTerrainConfig`  `[low-risk]`
Split into `config/TensorLayout` (CH/X/Z + channel counts), `config/DebugConfig` (flags, sourced from
`.properties` at load, cached in final fields), `config/HydrologyTuning` (width/carve laws + home for
the river/meander border constants consolidated in M-010), `config/ModConfig` (`.properties` helpers +
global scalars). `FractalTerrainConfig` becomes a thin delegating facade.
- **[QR — R-003/qa-014]** Runtime-loaded `DEBUG` `final` fields are *not* compile-time constants; JIT
  dead-code elimination of `if(DEBUG)` blocks is lost. Keep genuinely hot-path flags compile-time, or
  accept a single predictable branch; do not re-read properties per check (RA-005).
- **Accept:** each class owns one concern; DEBUG defaults equal prior values when unset; no numeric
  constant value changes; build + `spotlessCheck` pass. **Test:** unit assertions on indices/defaults. (DL-008)

### Wave W-003 — Phase 1

#### M-004 · JUnit golden-test gate for deterministic layers  `[test-infrastructure]`
Add a `src/test/java` JUnit source set + Gradle `test` task (no build overhaul). Convert
`GlobalRiverTest`, `LocalRiverTest`, `MeandersTest`, and the correctness portion of
`SpatialIndexBenchmark` into golden-assertion tests for a fixed seed. Keep the visualizers.
`pipelineTest` stays a manual harness, excluded from the assertion gate.
- **[QR — qa-003, assumption "golden fixtures are deterministic"]** Before trusting single-capture
  fixtures, add a determinism pre-check: run each harness N times (fixed seed) and assert identical
  output; only then freeze the fixture. If any layer is non-deterministic, capture a tolerance or
  canonicalize before gating.
- **Accept:** `gradle test` runs the four golden tests headless and they pass against captured
  fixtures; mutating a computation fails the corresponding test; `pipelineTest` unchanged.
- **Test:** golden/characterization, fixed-seed. (DL-002, DL-003)

### Wave W-004 — Phase 2 (parallel)

#### M-005 · MUST-2: move `awaitLoad` out of static init into `init()`  `[high-risk · lifecycle]`
Relocate `PipelineModels.load()/awaitLoad()` from the `FractalTerrainInstance` `static{}` block into
an explicit `init()` invoked at a controlled lifecycle point. A load failure must surface the
documented missing-datapack error, not `ExceptionInInitializerError`/`NoClassDefFoundError`.
- **Accept:** class-loading no longer blocks on model loading and cannot throw from class-init; a
  missing datapack yields the documented user-facing error via `init()`. **Test:** structural/review. (DL-005)

#### M-006 · MUST-3: freeze `FloatTensor` immutability contract  `[high-risk · concurrency]`
Give `FloatTensor.data/.shape` an immutability contract (private final + read-only accessors) so
`Storage`-cached tensors are safe to publish across reader threads; preserve the numeric read API.
- **[QR — qa-009, hot-path]** Freeze at the **cache-write boundary**, not by defensive-copying on
  every `getSlice`/read — per-read allocation on the tensor hot path would regress performance. Prefer
  an unmodifiable view or a documented "frozen once cached" invariant enforced at the `Storage`
  boundary over copy-on-read.
- **Accept:** external code cannot mutate a cached tensor post-construction; identical numeric reads;
  no per-read copy added to the hot path. **Test:** structural/review + unit. (DL-006)

### Wave W-005 — Phase 2

#### M-007 · MUST-1: immutable `PipelineSession` for the reload race  `[high-risk · concurrency]`
Bundle reload-scoped inputs (`seed`, `syntheticMapFactory`, `tau`) into an immutable
`PipelineSession`; `updateInstance` constructs a new session and swaps a single volatile reference;
worker-thread tile closures capture one snapshot so a reload cannot tear a multi-field read.
**Write the reload-race regression exercise first.**
- **Accept:** no worker reads the three fields independently — all read one snapshot; a simulated
  reload during generation cannot mix old/new inputs; terrain unchanged for a stable session.
- **Test:** regression (reload-during-generation) + structural review. (DL-004)

### Wave W-006 — Phase 2

#### M-008 · Keystone: `FractalTerrainInstance` statics → `GenerationContext`  `[high-risk · seam]`
Introduce `GenerationContext` holding the provider graph, constructed in build order
`global → local → relief → biome`. Keep `FractalTerrainInstance` as a thin adapter returning the
current context so the ~46 reach-throughs keep working during incremental migration.
- **[QR — qa-013 / R-002] Added acceptance criteria:**
  1. **Safe publication** — the current-context reference is published safely (volatile / final-field
     happens-before); a worker never observes a partially-constructed or null-then-swapped context.
  2. **Migration ownership** — a tracked checklist of the ~21 caller files enumerates migration to
     injected context access; DL-007's "full removal once callers are migrated" gets an explicit owner
     (a follow-up milestone or a checked task list), not just prose.
- **Accept (base):** one `GenerationContext` owns wiring in correct build order; static getters resolve
  through the adapter identically; no terrain change. **Test:** structural/review. (DL-007)

### Wave W-007 — Phase 3 · golden-gated split

#### M-010 · Split hydrology god class + consolidate river constants  `[structural]`
Split the 1022-line `LocalRiverProvider` into `GlobalNetworkBuilder` / `ChannelElevationAssigner` /
`LocalDrainageTracer` (+ geometry helpers). Consolidate duplicated constants — `GlobalRiver.RAMP_WIDTH`,
`LocalRiver.FILL_PADDING`/`RESAMPLE_DIST`, `Meanders.DX`/`MARGIN_INFLUENCE_FACTOR` — into
`HydrologyTuning`. **Gated by the M-004 golden tests.**
- **Accept:** single-responsibility collaborators; no duplicated border/sampling constants remain;
  `GlobalRiver`/`LocalRiver`/`Meanders` golden tests stay green; build + `spotlessCheck` pass.
- **Test:** golden/characterization (M-004 must stay green). (DL-010, DL-012)

> **[QR — qa-011]** M-010 is golden-gated, so it is safe to run in this wave. The **structural-only**
> splits below (no runtime golden) are moved to their own sequential waves so a silent terrain
> regression is attributable to exactly one split (bisectable), satisfying R-001's mitigation.

### Wave W-007a — Phase 3 · structural-only (isolated)  **[QR-revised]**

#### M-009 · Split `WorldPipeline` into diffusion stages  `[structural]`
Extract `CoarseStage` / `LatentStage` / `DecoderStage` / `ClimateProvider` along the existing
coarse→latent→decoder→climate clusters; `WorldPipeline` becomes an orchestrator composing them via the
`PipelineSession` snapshot. Preserve the ONNX tensor-layout contract at every boundary.
- **[QR — qa-003, assumption "clusters cleanly extractable"]** Precede the split with a short
  extractability spike: confirm each stage's inputs/outputs cross only the CH/X/Z tensor boundary and
  share no hidden mutable field with siblings; if a cluster is entangled, adjust the boundary before
  extracting.
- **Accept:** each stage single-responsibility; tensor math byte-for-byte identical (mechanical);
  build + `spotlessCheck` pass. **Test:** structural/review (no runtime golden per DL-002). Runs in its
  own wave for bisectability (R-001). (DL-010)

### Wave W-007b — Phase 3 · structural-only (isolated)  **[QR-revised]**

#### M-011 · Split `ClimateVariableTransform` + relocate biome enums  `[structural]`
Split the 792-line class into `BiomeParameterClassifier` / `ShoreDistanceCalculator` /
`ClimateToBiomeTransformer`; move the 8 nested enums into `world/biome/parameters/`. Preserve
biome-parameter output.
- **[QR — qa-003]** Same extractability spike as M-009 before splitting.
- **Accept:** delegates to the three collaborators; enums relocated; identical classification;
  build + `spotlessCheck` pass. **Test:** structural/review. Own wave for bisectability. (DL-010)

### Wave W-007c — Phase 3 · structural-only (lowest coupling)  **[QR-revised]**

#### M-012 · Extract `FastNoiseLite` strategies + relocate vector math  `[structural · low-coupling]`
Extract per-noise strategies from the 4313-line `FastNoiseLite` along its noise-type clusters; move the
embedded `Vector2`/`Vector3` into the `math` package. Preserve noise output.
- **Accept:** dispatcher selects among strategies; `Vector2/3` in `math`; identical noise values
  (indirectly exercised by the deterministic golden tests); build + `spotlessCheck` pass.
- **Test:** structural/review, indirectly covered by M-004. (DL-010)

> The three structural-only waves (W-007a/b/c) are ordered lowest-blast-radius-last is optional; the
> requirement from **[QR]** is only that each lands in a **separate** wave from the others so a
> regression bisects to one milestone. M-012 has the lowest coupling and can go first if preferred.

### Wave W-008 — Phase 4

#### M-013 · Author `ARCHITECTURE.md` + update memory  `[documentation]`
Write the missing `ARCHITECTURE.md` (referenced by `CLAUDE.md`): pipeline overview, provider graph,
`GenerationContext` seam, config/logging conventions, the three MUST-fixes, coordinate frames
(tile/native/coarse/block px), and the deterministic-only golden-test stance. Record the invariants so
future sessions inherit the invisible knowledge; update project memory.
- **Accept:** `ARCHITECTURE.md` matches the post-refactor layout and seams; documents the MUST-fixes and
  the golden gate. **Test:** docs-only. (DL-001, DL-002)

---

## Execution order (revised waves)

```
W-001  M-001                         (delete dead code)
W-002  M-002 ∥ M-003                 (logging · config)
W-003  M-004                         (JUnit golden gate + determinism pre-check)
W-004  M-005 ∥ M-006                 (awaitLoad→init · FloatTensor freeze)
W-005  M-007                         (PipelineSession, MUST-1)
W-006  M-008                         (GenerationContext keystone + safe-publication criteria)
W-007  M-010                         (hydrology split — golden-gated)
W-007a M-009                         (WorldPipeline split — structural-only, isolated)  [QR]
W-007b M-011                         (climate split — structural-only, isolated)        [QR]
W-007c M-012                         (FastNoiseLite split — structural-only, isolated)  [QR]
W-008  M-013                         (ARCHITECTURE.md + memory)
```

## Goal → milestone coverage

| Refactor goal | Milestones |
| ------------- | ---------- |
| No god classes | M-009, M-010, M-011, M-012 |
| Easy/consistent logging | M-002 |
| No duplicate utilities/constants | M-003, M-010 (constants), M-012 (Vector2/3) |
| Easy segmentation | M-008 (context seam), M-003 (config split) |
| LLM-friendly in-source docs | M-013 (+ per-class docs added during each split) |
| Scalable/expandable config | M-003 (config split + runtime DEBUG) |
| MUST-fixes (correctness) | M-005, M-006, M-007 |
| Dead code / default package | M-001 |

---

## Quality-review revisions (folded in above)

The plan-QR pass surfaced these; each is applied in the milestone/wave it names. (They were verified by
the QR agents but could not be persisted via the planner CLI — see *Tooling note*.)

1. **qa-007 (FAIL → applied):** M-002 logging scope now includes `debug/tests/PipelineTest.java`'s 11
   `System.out/err` sites; the original "3 sites" count was incomplete.
2. **qa-011 (FAIL → applied):** the structural-only splits M-009/M-011/M-012 are separated into their
   own waves (W-007a/b/c) so a silent terrain regression is bisectable to one milestone — required by
   R-001's mitigation, which the original single W-007 undermined.
3. **qa-013 (FAIL → applied):** M-008 gains safe-publication acceptance criteria (volatile/happens-before
   for the context ref) and an explicit owner for completing the ~21-caller migration.
4. **qa-003 (FAIL → applied):** the two M-rated assumptions get dedicated validation — a determinism
   re-run before freezing M-004 fixtures, and an extractability spike before the M-009/M-011 splits.
5. **qa-009 / qa-014 (FAIL → applied):** M-006 freezes `FloatTensor` at the cache-write boundary (no
   per-read copies on the hot path); M-003/R-003 note that runtime `DEBUG` loses compile-time
   dead-code elimination, so genuinely hot flags stay compile-time.
6. **qa-001 (PASS):** decision log is complete and all `decision_refs` resolve.

## Tooling note (environment)

The planner CLI is not Windows-portable, which blocked the scripted QR/render loop:

- `skills/planner/cli/qr.py:33` unconditionally `import fcntl` (POSIX-only) → every `update-item`
  fails with `ModuleNotFoundError: No module named 'fcntl'`, so QR results couldn't be persisted.
- `skills/planner/shared/plan.py:207` (`save_plan`) uses `tmp_path.rename(path)`, which raises
  `FileExistsError` on Windows (`os.rename` won't overwrite) → all plan mutation commands fail;
  `plan.json` was authored directly against the schema and validated read-only.

Both are one-line fixes (`fcntl` → `msvcrt`/platform-guard; `rename` → `os.replace`). Because these are
shared skill infrastructure, they were not patched unilaterally. This markdown was rendered directly
from the validated `plan.json` with the QR revisions applied by hand.
