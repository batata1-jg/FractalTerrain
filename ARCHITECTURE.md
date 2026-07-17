# Architecture

System overview for `FractalTerrain` post the 2026-07 code-hygiene refactor (see
`plans/project-refactor.md`). Package root: `src/main/java/me/batata_1/fractal_terrain/`.

## Overview

One loaded overworld drives two independent generators that meet at the relief/biome tile:

1. **Diffusion pipeline** (`ml/`) — a JVM-lifetime ONNX pipeline (`WorldPipeline`) that turns a world
   seed into coarse climate/elevation tensors, then a decoded high-resolution relief residual.
2. **Hydrology + biome** (`hydrology/`, `world/biome/`, `relief/`) — per-world providers, built once at
   world load by `GenerationContext`, that consume the diffusion tensors to trace rivers, carve terrain,
   and classify biome parameters, each result cached per 512×512 tile.

`GenerationContext` (keystone M-008) is the seam between them: it owns the whole per-world provider
graph and is reached today through `FractalTerrainInstance`, a thin static adapter kept for incremental
caller migration (`plans/m008-caller-migration.md`, still in progress).

## Pipeline overview

```
seed ──► WorldPipeline (JVM-lifetime, me/batata_1/fractal_terrain/ml/pipeline/)
           │
           ├─ CoarseStage   (20-step DPM-Solver++)  ──► coarse  AdditiveInfiniteTensor [7,*,*]
           ├─ LatentStage   (2 flow-matching steps)  ──► latents AdditiveInfiniteTensor
           ├─ DecoderStage  (1 flow-matching step
           │                 + fuzed post-process)   ──► residual AdditiveInfiniteTensor [DECODER_CHANNELS=8,*,*]
           └─ ClimateProvider (windowed lapse-rate
                               regression over coarse) ──► getClimate(x,z,elev)
           │
           │  all four share one PipelineSession snapshot (MUST-1, see below)
           ▼
   GenerationContext build order:  global → local → relief → biome
           │
           ├─ GlobalRiverProvider   (hydrology/)  — coarse-px river network per 64×64-coarse tile
           ├─ LocalRiverProvider    (hydrology/)  — 512-native-px carved elevation + hydrological-unit index
           │     ├─ GlobalNetworkBuilder    — traces/relaxes the global network inside a tile
           │     ├─ LocalDrainageTracer      — local network off the drainage field, attached in place onto that SAME graph
           │     └─ ChannelElevationAssigner — ONE bed-elevation propagation pass over the unified global+local graph
           ├─ ReliefProvider        (relief/)     — imports carved elevation + decodes residual → [RELIEF_CHANNELS=7,512,512]
           └─ BiomeProvider         (world/biome/) — climate+relief → vanilla biome parameters
                     └─ ClimateVariableTransform (facade) → ClimateToBiomeTransformer
                              ├─ BiomeParameterClassifier
                              └─ ShoreDistanceCalculator
           │
           ▼
   FractalTerrainChunkGenerator / FractalTerrainSurfaceSystem / PopulateNoiseStep (world/gen/)
           │
           ▼
   chunk blocks
```

Every ONNX-facing tensor uses the fixed axis order `CH=0/X=1/Z=2` and the channel counts in
`config/TensorLayout.java` (`DECODER_CHANNELS=8`, `RELIEF_CHANNELS=7`, `BIOME_CHANNELS=6`,
`GLOBAL_RIVER_CHANNELS=3`) — see Invariants.

**Diffusion stages** (`ml/pipeline/`): `WorldPipeline.java` is the orchestrator; `CoarseStage.java`,
`LatentStage.java`, `DecoderStage.java`, `ClimateProvider.java` are the four extracted collaborators
(M-009). `EDMScheduler.java` (diffusion schedule/step update) and `SyntheticMapFactory.java`
(seed-derived synthetic conditioning map) are shared helpers; `WorldPipelineModelConfig.java` loads
model-specific constants (means/stds, latent compression, native resolution) from
`world_pipeline_config.json` so a model swap needs no recompile.

**Hydrology split** (`hydrology/`, M-010; unified onto one graph by the 2026-07 river-network
unification, `plan-hydrology.md`): `LocalRiverProvider.java` is a thin orchestrator over a dual-store
cache (an `ImmutableRTree<HydrologicalUnit>` spatial index + a carved-elevation `FloatTensor`, filled by
one `buildTile` call). `buildTile` builds ONE per-tile `RiverNetwork` graph: `GlobalNetworkBuilder.java`
traces/relaxes the global (Meanders) subgraph and returns it together with the boundary-elevation map it
accumulated (it no longer calls `assign` itself); `LocalDrainageTracer.java` then traces the
drainage-derived local network and attaches every surviving segment directly onto that SAME graph in
place (`SOURCE` root → `JUNCTION`-`split()` or coast-`DRAIN` terminus), returning nothing, instead of
handing back a detached channel list; `ChannelElevationAssigner.java` then runs ONE `assign` pass over
the now-unified graph, and `RiverNetwork.collectUnits` runs ONE pass emitting every hydrological unit —
global and local, one shared feature-id counter — by resampling geometry and reading each channel's
assigned `Channel.bedElevations` (oxbows/abandoned paths fall back to a decoded-terrain sampler, since
they carry no `bedElevations`). There is no per-pixel global boolean mask anywhere in this path:
proximity to a global-channel point (`HydrologyTuning.LOCAL_ATTACH_RADIUS`) replaces both the old mask's
walk-termination exclusion and its reach-seed adjacency, read from a point index built fresh over the
graph's channels each call. `HydrologyTileGeometry.java` centralizes the shared tile-frame geometry
(`GRID=512`, `PAD=1`, `PADDED=514`, `COARSE_PX=256`) all three depend on. `PipelinePreprocessing.java`
(sink-fill, drainage direction, flow accumulation) and `ChannelGeometry.java` are lower-level shared
helpers. The `hydrology/profile/` subpackage (`HydrologyProfileCarver`, `HydrologyProfilePainter`,
`HydrologyProfile`, `RosgenProfile`) turns the hydrological-unit index into carve/paint operations
consumed by `world/gen/`. `GlobalRiverProvider.java` is independent of `LocalRiverProvider` and caches
its own 64×64-coarse-px tiles directly (coarse-px addressed, not the 512-native-px tile grid — see
Coordinate frames).

**Hydrology carve pipeline — "carve first, detail later".** `RosgenProfile` defines a feature's
*reference* elevation as the bank; the per-pixel bed is `reference − depth`. Two carve stages, in two
different places, sum to the intended trench:

1. **Tile-level shell carve** (`HydrologyProfileCarver.carveRiverShells`, static) — run as a single pass
   over the GLOBAL-only units of the unified network (local channels are excluded from this pass; DL-011
   keeps the local shell carve disabled — see the `buildTile` order below) within the single
   once-per-tile `LocalRiverProvider.buildTile` flow. Pulls the elevation toward each unit's shell floor
   (`RosgenProfile.shellFloor` = bank − `HydrologyTuning.FREEBOARD`), lens-masked (`RosgenProfile.lensMask`)
   so the *mask* is exactly 1 (flat floor) out to `floodPlainLength` and falls to exactly 0 at
   `riverInfluence`; the resulting delta is `(shellFloor − ambient) × mask`. Every unit's delta is composed
   via `min()` of an absolute floor target against a pristine (pre-carve) ambient snapshot — never a
   relative subtract — so overlapping global units on one shared buffer never double-deepen a confluence.
2. **Per-pixel bed residual** (`HydrologyProfileCarver.carve`/`carveAtPixel`/`carvePrefetched` →
   `HydrologyProfile.computeForUnit`) — applied per block in `PopulateNoiseStep`, on top of the already
   shell-carved elevation. Cuts only `bed = shell − depth` within the bed half-width
   (`RosgenProfile.bedResidualDelta`); the deepest covering unit wins at a confluence (min-composite,
   consistent with the shell kernel). `Types.RIVER_DIFFERENCE` is `refined − shell` (trench-vs-shell, not
   vs. the original decoded terrain).

`LocalRiverProvider.buildTile` order: trace/relax the global network (`GlobalNetworkBuilder.build`,
returning the network plus its boundary-elevation map) → `fillSinks` + drainage on the raw decoded
elevation → trace the local network and attach every surviving segment directly onto that SAME graph
(`LocalDrainageTracer.traceLocalNetwork`, in place, no return value) as `SOURCE`/`JUNCTION`-split/coast-
`DRAIN` edges, proximity to a global-channel point (`HydrologyTuning.LOCAL_ATTACH_RADIUS`) gating both
attachment and walk termination → augment the boundary-elevation map with the local `SOURCE`/`DRAIN`
nodes the trace minted → ONE `ChannelElevationAssigner.assign` pass over the now-unified graph → ONE
`RiverNetwork.collectUnits` pass emitting every unit (global and local, one shared feature-id counter,
reading each channel's assigned `Channel.bedElevations` rather than deriving bed from decoded terrain) →
carve ONLY the global-river shell into the decoded elevation (DL-011: the local shell carve stays
disabled — local networks are traced with no coarse halo, so a local shell can be truncated at this
tile's `PAD=1` border, an accepted seam risk for local floodplains that straddle a tile edge; global
floodplains use the 2×2-cell halo and are unaffected) → crop to the 512×512 tile. The carved-elevation
cache store is `local_carved_elev_v2` (renamed from `local_carved_elev`; new-worlds-only, old tiles are
orphaned and the frontier regenerates under the new name).

**Biome split** (`world/biome/`, M-011): `ClimateVariableTransform.java` is a thin public facade
preserving the pre-split signature; it forwards to `ClimateToBiomeTransformer.java`, which uses
`ShoreDistanceCalculator.java` (distance-to-shore upscaling) and `BiomeParameterClassifier.java`
(`is…(value)` band predicates). The relocated enums live in `world/biome/parameters/`
(`Continentalness`, `ErosionLevel`, `TemperatureLevel`, `HumidityLevel`, `PeaksValleys`, `TempBand`).
`BiomeProvider.java` is the actual per-tile builder/density-function wiring consumed by
`FractalTerrainBiomeSource`; it is a separate class from `ClimateVariableTransform`, not part of the
M-011 split itself.

**Noise** (`noise/`, M-012): `FastNoiseLite.java` is the dispatcher; per-noise-type strategies live in
`noise/strategy/` (`BasicGridWarpStrategy`, `CellularStrategy`, `OpenSimplex2Strategy`,
`OpenSimplex2SStrategy`, `PerlinStrategy`, `SimplexGradientWarpStrategy`, `ValueStrategy`,
`ValueCubicStrategy`, plus lookup tables in `NoiseTables.java`, which lives in `noise/strategy/`, not
directly under `noise/`). `Vector2`/`Vector3` live in `math/`.

## Provider graph

`GenerationContext`'s constructor (`GenerationContext.java:58-89`) wires the per-world graph in one
fixed order — later providers may reach earlier ones (through the adapter) during their own tile
compute, so this order is load-bearing:

| Order | Provider | Package | Depends on |
| ----- | -------- | ------- | ---------- |
| 1 | `GlobalRiverProvider` | `hydrology/` | `WorldPipeline` coarse tensor (via the static `pipeline` field) |
| 2 | `LocalRiverProvider` | `hydrology/` | `GlobalRiverProvider` (fallback via adapter when no test override), decoder tensor |
| 2a | `HydrologyProfileCarver` / `HydrologyProfilePainter` | `hydrology/profile/` | the just-built `LocalRiverProvider` |
| 3 | `ReliefProvider` | `relief/` | `LocalRiverProvider` (fallback via adapter when no test override), decoder tensor |
| 4 | `BiomeProvider` | `world/biome/` | `ReliefProvider`, `LocalRiverProvider` (both via the adapter), climate from `WorldPipeline` |

`ReliefProvider` and `LocalRiverProvider` also accept a test-only override (`setLocalRiverProvider`,
`setGlobalRiverProvider`) so golden tests can inject a fixture instead of reaching the production
singleton — the seam the caller migration (below) is expected to widen into the production path.

`PopulateNoiseStep`, `FractalTerrainSurfaceSystem`, and `FractalTerrainHeightmapCache` are built after
the provider graph, reading from it via `FractalTerrainInstance`/`GenerationContext`; they are not part
of the `global → local → relief → biome` ordering constraint.

## The `GenerationContext` seam

`GenerationContext.java` holds the whole per-world graph (server, `ReliefProvider`, `BiomeProvider`,
`GlobalRiverProvider`, `LocalRiverProvider`, `HydrologyProfileCarver`/`Painter`, `PopulateNoiseStep`,
`FractalTerrainSurfaceSystem`, `FractalTerrainHeightmapCache`, `RandomState`, `Infinite3DVisualizer`) as
`final` fields, constructed once per world load.

**Safe publication.** `FractalTerrainInstance` holds the current context behind a
`private static volatile CompletableFuture<GenerationContext> context`
(`FractalTerrainInstance.java:45`). `init()` calls `context.complete(new GenerationContext(...))` only
after the constructor has fully run; every getter goes through `current()` → `context.get()`, which has
a happens-before edge with that `complete()` — so a worker thread can never observe a
partially-constructed or null-then-swapped context. `close()` resets `context` to a fresh incomplete
future so a subsequent `init()` republishes cleanly.

**`FractalTerrainInstance`** (`FractalTerrainInstance.java`) is the thin static adapter kept from
DL-007: it forwards ~15 historical getters (`getReliefProvider`, `getBiomeProvider`,
`getGlobalRiverProvider`, `getLocalRiverProvider`, `getHydrologyCarver`, `getHydrologyPainter`,
`getPopulateNoiseStep`, `getSurfaceBuilder`, `getHeightmapCache`, `getNoiseConfig`, `getServer`, …) to
`current().getX()`. It also owns the one JVM-lifetime object outside `GenerationContext`: the shared
`public static volatile WorldPipeline pipeline` field, loaded once via `initPipeline()` and reused
across world (re)loads — the pipeline outlives any single world, so it is deliberately not part of the
per-world context.

**Caller migration is tracked, not finished.** `plans/m008-caller-migration.md` enumerates the ~15
mod-constructed caller files that still reach through the static adapter (`debug/Debug.java`,
`Infinite3DVisualizer.java`, `LocalRiverProvider.java`, `SteepSlopePredicateMixin.java`,
`FractalTerrainSurfaceSystem.java`, `HydrologyProfileCarver.java`, `PlacedFeatureMixin.java`,
`PopulateNoiseStep.java`, `FractalTerrainHeightmapCache.java`, `FractalTerrainHeightmap.java`,
`BiomeProvider.java`, `ReliefProvider.java`, `FractalTerrainBiomeSource.java`,
`FractalTerrainChunkGenerator.java`), all currently marked "not migrated". Mixins and Fabric-instantiated
types have no constructor the mod controls, so they are expected to keep resolving through the adapter
permanently; full removal of the static getters is scoped to the remaining mod-constructed providers and
has no owner-milestone beyond that checklist yet. Treat `FractalTerrainInstance.getX()` reach-throughs
as expected, not as debt to silently clean up — check that file before assuming a caller should hold an
injected `GenerationContext` instead.

## Config & logging conventions

Config is split by concern under `config/`, with a thin backward-compatible facade at the package root:

| Class | Owns | Package |
| ----- | ---- | ------- |
| `TensorLayout` | `CH/X/Z` axis indices, per-stage channel counts | `config/` |
| `DebugConfig` | Debug flags (property-sourced) + visualizer mode constants | `config/` |
| `HydrologyTuning` | River width/carve-profile law, border/sampling constants | `config/` |
| `ModConfig` | `.properties` load/parse machinery + remaining global scalars | `config/` |
| `FractalTerrainConfig` | Re-exports all four under their historical names | package root (`me.batata_1.fractal_terrain`), **not** under `config/` |

`FractalTerrainConfig` (`FractalTerrainConfig.java`) is a `record` with no state of its own — every
constant is `= TensorLayout.X` / `= DebugConfig.DEBUG` / etc. New code should reference the owning
`config/` class directly; existing callers keep compiling against the facade unchanged.

**Runtime-toggleable `DEBUG` and the R-003 tradeoff.** `DebugConfig` fields sourced from
`.properties` (`DEBUG`, `TEST_INSTANCE`, `DEBUG_RIVER_NET`, `DEBUG_DSHORE`, …) are `static final`
booleans read once via `ModConfig.readBoolean` at class-load — cheap, but a runtime-loaded `final
boolean` is *not* a compile-time constant, so `if (DEBUG)` guards around these are no longer
dead-code-eliminated by the JIT the way a `static final` literal would be. `DebugConfig.java` documents
the consequence directly: flags on a genuinely hot per-block/per-column/per-sample path
(`VIZ_H_CONTROL_MODE`, `VIZ_PAINT_CONTROL_MODE`) stay compile-time enum constants instead of
property-sourced fields for exactly this reason; `DISABLE_3D_VISUALIZER` is read once per chunk fill
(not per block), so its runtime cost is negligible.

**Logging.** `debug/Debug.java`'s `Debug.getLogger(Class<?> clazz)` is the single logging entry point:
`LoggerFactory.getLogger("fractal_terrain/" + clazz.getName())` (`Debug.java:35-37`) — every `*_LOGGER`/
`LOG` field in the mod should be initialized this way, not via direct `LoggerFactory.getLogger(...)`.
(`ml/models/PipelineModels.java` and a few other pre-existing sites still call `LoggerFactory` directly;
M-002 migrated files opportunistically rather than exhaustively — check a class's own logger
initializer before assuming it follows the facade.)

## The three MUST-fixes

**MUST-1 — `PipelineSession` (reload race).** `ml/pipeline/PipelineSession.java` is an immutable
`record(long seed, SyntheticMapFactory syntheticMapFactory, float[] tau)`. `WorldPipeline` holds it
behind one `private volatile PipelineSession session` (`WorldPipeline.java:51`); every stage reads it
via a `Supplier<PipelineSession>` bound to `currentSession()`, snapshotting once per tile/batch instead
of reading `seed`/`syntheticMapFactory`/`tau` as three independent fields. `updateInstance` builds a
whole new session and assigns it in one write (`WorldPipeline.java:105-113`), so no worker can ever
observe a torn `(seed, map, tau)` triple. Regression: `src/test/java/.../ml/pipeline/PipelineSessionReloadRaceTest.java`
— a headless proxy (no real `SyntheticMapFactory`, since that needs ONNX assets) that drives the same
single-volatile-swap mechanism with a writer thread reloading and four reader threads asserting
`seed == tau[0]` never tears.

**MUST-2 — model load moved into `init()`.** `PipelineModels.load()` (`ml/models/PipelineModels.java:33`)
starts loading on a background daemon thread and returns immediately; `awaitLoad()` blocks on a
`CountDownLatch` until loaded or failed. Neither runs from a static initializer — `FractalTerrainInstance
.initPipeline()` (`FractalTerrainInstance.java:53-60`) calls `PipelineModels.load()` +
`PipelineModels.awaitLoad()` explicitly, so a load failure (e.g. missing datapack/model assets) surfaces
as a normal `IllegalStateException` from `init()` instead of an `ExceptionInInitializerError` that would
permanently poison the class with `NoClassDefFoundError` on every subsequent reference.

**MUST-3 — `FloatTensor` frozen at the cache-write boundary.** `infinitetensor/FloatTensor.java` keeps
`data`/`shape` `private final` and never exposes a mutable reference; a `private volatile boolean
frozen` (default `false`) is flipped exactly once by `freeze()`. Every mutator (`set`, `writeFrom`,
`dataUnsafe`, `addFrom`, `copyFrom`) calls `checkMutable()` first and throws `IllegalStateException`
once frozen; every reader (`get`, `readInto`, `copyRange`, `entryAt`) never allocates and stays callable
regardless of frozen state — so cached tensors are safe to read concurrently without per-read copies.
`storage/Storage.java` is the only caller of `freeze()`: both `persistAndRecord` (fresh compute) and
`loadInto` (disk reload) freeze *before* the entry is published into `CACHE` via
`CompletableFuture.complete` (`Storage.java:325`, `:339`) — freeze happens-before publication because it
runs on the same thread, before the key is observably completed to any other thread.

## Coordinate frames

| Frame | Unit | Defined / grounded in |
| ----- | ---- | ---------------------- |
| **block-px** | 1 Minecraft world block | Chunk/column generation code (`world/gen/`); `BiomeProvider` derives tile origins as `tileX << 9` (`BiomeProvider.java:283`). |
| **tile** | 512×512 block-px (= 512×512 native-px) unit that keys nearly every per-tile cache (`Storage`/`NonIntersectingInfiniteTensor`) | `HydrologyTileGeometry.GRID = 512`, `PAD = 1`, `PADDED = 514` (`HydrologyTileGeometry.java:22-24`); `tileX = blockX >> 9` (inverse of the shift above). `GlobalRiverProvider` is the one exception — its own tile cache is addressed directly in coarse-px (see below), a separate grid from the 512-native-px relief/local-river/biome tile grid. |
| **native** | 1 native px, the decoder/relief pixel resolution; 1:1 with block-px inside a tile | `TensorLayout` fixes the axis order `CH=0/X=1/Z=2` (`TensorLayout.java:16-19`) for every ONNX-facing tensor in this frame; `DecoderChannels.INNER = 512` / `relief/ReliefProvider`'s `INNER = 512` size the relief tile in native px. |
| **coarse** | 1 coarse unit = 256 native px | `HydrologyTileGeometry.COARSE_PX = 256` (`HydrologyTileGeometry.java:25`); `WorldPipeline.getCoarseSlice`'s javadoc: "Coordinates are in coarse index units (1 unit = 256 native pixels)" (`WorldPipeline.java:131-133`). `GlobalRiverProvider` caches its own tiles in this frame directly (`getArrow(cx, cz)` etc., 64×64-coarse-px tiles); `GlobalNetworkBuilder` bridges the two frames by mapping a 512-native-px relief tile `(tileX, tileZ)` onto its 2×2 owned coarse cells `(tileX*2 + a, tileZ*2 + b)` (`GlobalNetworkBuilder.java:55-58`, `:91-95`). |

## Invariants (do not violate)

- **Seams before splits.** The seam milestones (M-005/M-006/M-007/M-008) landed before the god-class
  splits (M-009..M-012); do not reintroduce shared mutable static state that bypasses `PipelineSession`
  or `GenerationContext`.
- **Build order `global → local → relief → biome`** is fixed in `GenerationContext`'s constructor
  (`GenerationContext.java:61-67`) and mirrored by the fallback chain each provider uses when no test
  override is set (`LocalRiverProvider` → `GlobalRiverProvider`, `ReliefProvider`/`BiomeProvider` →
  `LocalRiverProvider`). Reordering it changes which providers can legally depend on which.
- **ONNX tensor-layout invariants are fixed.** `CH=0/X=1/Z=2` and the per-stage channel counts in
  `TensorLayout.java` (`DECODER_CHANNELS=8`, `RELIEF_CHANNELS=7`, `BIOME_CHANNELS=6`,
  `GLOBAL_RIVER_CHANNELS=3`) are the model I/O contract — any stage boundary must preserve them exactly.
- **`Storage`/`InfiniteTensor` lock-ordering and CAS single-flight are deliberate.** Reads are lock-free;
  the only lock (`evictionLock`) guards eviction bookkeeping and is never touched by readers
  (`Storage.java:33-37`). Compute/load claims use an atomic `CACHE.putIfAbsent` (`claimForCompute`,
  `fetchEntry`) — losers block on the winner's future rather than recomputing. `LocalRiverProvider`'s
  dual-store build (units index + carved elevation from one `buildTile` call) depends on this claim API
  to cross-fill the second store without a duplicate compute — do not bypass it.
- **`FloatTensor` is frozen once cached** — see MUST-3. Never mutate a tensor obtained from a `Storage`
  cache; if you need a mutable copy, use `slice`/`copyRange`, which allocate a fresh tensor.
- **`RiverNetwork`/`QuadTree` reuse is per-tile and single-threaded.** `GlobalNetworkBuilder` builds and
  returns a fresh `Meanders`/`RiverNetwork` purely from its parameters; `LocalDrainageTracer` then mutates
  that same network in place to attach the local subgraph (`traceLocalNetwork` returns nothing) — both are
  still per-tile/no-shared-state (documented explicitly in both classes' Invariants sections), so each
  tile build's graph carries no state shared across tiles or threads. `QuadTree` itself is safe for
  concurrent reads under its own read/write lock (`QuadTree.java:10-16`), but that contract exists for
  within-tree query concurrency, not for sharing one instance's *construction* across worker threads.

## Testing stance

**Deterministic layers have a golden gate.** `src/test/java/me/batata_1/fractal_terrain/` (JUnit 5,
`gradle test`, configured `useJUnitPlatform()` in `build.gradle`) holds:

| Test | Covers |
| ---- | ------ |
| `hydrology/GlobalRiverGoldenTest.java` | `GlobalRiverProvider`'s per-tile pipeline via `computeTileForTest` |
| `hydrology/LocalRiverGoldenTest.java` | `LocalRiverProvider`'s local-network trace via `traceLocalNetworkForTest` |
| `hydrology/meanders/MeandersGoldenTest.java` | `Meanders` relaxation |
| `hydrology/SpatialIndexCorrectnessGoldenTest.java` | The spatial-index correctness portion of the former `SpatialIndexBenchmark` |
| `ml/pipeline/PipelineSessionReloadRaceTest.java` | MUST-1 — the reload-race regression (see above) |

Each headless golden test drives the exact production code path over a synthetic seeded fixture (no
ONNX dependency), so a divergence in the deterministic hydrology math fails `gradle test` immediately.

**The diffusion half has no automated behavior gate.** `debug/tests/PipelineTest.java` (the
`pipelineTest` Gradle `JavaExec` task) needs the ~1 GB ONNX model weights plus GPU/CPU inference and is
not headless/CI-runnable, so it stays a manual harness excluded from the JUnit gate. The `WorldPipeline`
split (M-009) and MUST-1/MUST-3 are instead verified by mechanical, math-identical extraction plus
focused review (plan decision DL-002) — treat any future change to `CoarseStage`/`LatentStage`/
`DecoderStage`/`ClimateProvider` as needing manual `pipelineTest` verification, since no test will catch
a silent numeric regression there. The remaining manual harnesses (`globalRiverTest`, `localRiverTest`,
`meandersTest`, `spatialIndexBenchmark`) keep their PNG/visual debug dumps for the same tiles the golden
tests assert numerically.
