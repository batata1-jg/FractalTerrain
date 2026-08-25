# Architecture

System overview for `FractalTerrain` post the 2026-07 code-hygiene refactor. Package root:
`src/main/java/me/batata_1/fractal_terrain/`.

> **The `feature/hydrology` branch is mid-rework and some stages are switched off.** The per-pixel
> hydrology carve stack is active: `PopulateNoiseStep.updateToFinalElev` reads each column's real
> per-column relief and refines it through the lattice carve (`fineGrainedPrimitivePass` →
> `HydrologyProfileInprinter.computeRiverGrid`) before writing `ELEVATION`, so
> `Types.RIVER_DIFFERENCE` is not uniformly `0`. Biome decoration is still
> unconditionally disabled by a debug flag. See "Current debug state" below for the exact per-flag state
> (including the surface step, which currently runs) before assuming any generation behaviour described
> here is observable in-game.

## Overview

One loaded overworld drives two independent generators that meet at the relief/biome tile:

1. **Diffusion pipeline** (`ml/`) — a JVM-lifetime ONNX pipeline (`WorldPipeline`) that turns a world
   seed into coarse climate/elevation tensors, then a decoded high-resolution relief residual.
2. **Hydrology + biome** (`hydrology/`, `world/biome/`, `relief/`) — per-world providers, built once at
   world load by `GenerationContext`, that consume the diffusion tensors to trace rivers, carve terrain,
   and classify biome parameters, each result cached per 512×512 tile.

`GenerationContext` (keystone M-008) is the seam between them: it owns the whole per-world provider
graph and is reached today through `FractalTerrainInstance`, a thin static adapter kept for incremental
caller migration (still in progress — see "Caller migration" below).

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
           ├─ GlobalRiverProvider   (hydrology/)  — coarse-px riverPrimitive network per 64×64-coarse tile
           ├─ RiverProvider         (hydrology/)  — 512-native-px hydrological-primitive index + carved-elevation tensor
           │     ├─ GlobalNetworkBuilder    — traces/relaxes the global network inside a tile; assigns + carves the global-only graph to shape the drainage field
           │     ├─ LocalNetworkBuilder     — attaches the local trace onto that SAME graph in place, then TWO bed-elevation propagation passes over the unified graph with a carve between them
           │     └─ LocalDrainageTracer      — the local trace `LocalNetworkBuilder` drives, off the drainage field
           ├─ ReliefProvider        (relief/)     — decodes residual + imports RiverProvider's carved elevation as ch0 → [RELIEF_CHANNELS=7,512,512]
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
`GLOBAL_RIVER_CHANNELS=4`) — see Invariants.

**Diffusion stages** (`ml/pipeline/`): `WorldPipeline.java` is the orchestrator; `CoarseStage.java`,
`LatentStage.java`, `DecoderStage.java`, `ClimateProvider.java` are the four extracted collaborators
(M-009). `EDMScheduler.java` (diffusion schedule/step update) and `SyntheticMapFactory.java`
(seed-derived synthetic conditioning map) are shared helpers; `WorldPipelineModelConfig.java` loads
model-specific constants (means/stds, latent compression, native resolution) from
`world_pipeline_config.json` so a model swap needs no recompile.

**Hydrology split** (`hydrology/`): `RiverProvider.java` owns two per-tile stores — `primitives`, a
`NonIntersectingSpatialIndex<ImmutableRTree<HydrologicalPrimitive>>` cache (a 50 MB soft cap), and
`hydrology_relief`, a `NonIntersectingInfiniteTensor` (`{1,512,512}`, no cap of its own) holding the
river-carved elevation. Both stores' compute callbacks call the same private `buildTile`, which builds ONE
per-tile `RiverNetwork` graph and returns both artifacts from that single run; a small capacity-4 memo
(`recentTiles`) means a tile whose primitives and carved elevation are both requested does not run the
trace/carve pipeline twice. `buildTile` splits across two collaborators: `GlobalNetworkBuilder.java` traces
the global subgraph, relaxes it with `GradientNetworkRelaxation`, runs `ChannelElevationAssigner.assign`
and one `carveRiverInfluence` pass over the global-only graph to shape the drainage field, and returns the
`RiverNetwork` together with that drainage field and the boundary-elevation map it accumulated;
`LocalNetworkBuilder.java` then traces the drainage-derived local network with `LocalDrainageTracer.java`
and attaches every surviving segment directly onto that SAME graph in place, returning nothing from the
tracer itself. It works through the atomic seam: `RiverNetwork.viewAtomic()` yields an `AtomicView` in
which every interior spline point is a first-class node, the tracer appends `SOURCE`/interior/`DRAIN`
nodes and directed edges to it, and `RiverNetwork.manageCollisions(step, view)` orients, captures and
prunes that view before folding it back into the canonical graph. `LocalNetworkBuilder` then runs TWO
`ChannelElevationAssigner.assign` passes over the now-unified graph, with one `carveRiverInfluence` pass
between them (into a fresh clone of the raw elevation, not `GlobalNetworkBuilder`'s carved clone), and
returns the carved padded elevation `RiverProvider` crops and publishes. `RiverProvider.collectPrimitives`
then runs ONE pass emitting every hydrological primitive — global and local, one shared feature-id counter
— by resampling geometry and reading each channel's assigned `Channel.bedElevations` (oxbows/abandoned
paths fall back to a decoded-terrain sampler, since they carry no `bedElevations`). There is no per-pixel
global boolean mask anywhere in this path: proximity to a global-channel point
(`HydrologyTuning.LOCAL_ATTACH_RADIUS`) replaces both the old mask's walk-termination exclusion and its
reach-seed adjacency, read from a point index built fresh over the graph's channels each call.
`HydrologyTileGeometry.java` centralizes the shared tile-frame geometry (`GRID=512`, `PAD=1`, `PADDED=514`,
`COARSE_PX=256`) every stage depends on. `Drainage.java` (sink-fill, D8/D4 drainage direction, flow
accumulation, and the `Drainage.FlowGraph` routing topology both the flow accumulator and the local trace
walk) and `ChannelGeometry.java` are lower-level shared helpers. The `hydrology/profile/` subpackage
(`HydrologyProfileInprinter`, `HydrologyProfilePainter`, `HydrologyProfile`, `RosgenProfile`) turns the
hydrological-primitive index into carve/paint operations consumed by `world/gen/`. `GlobalRiverProvider.java`
is independent of `RiverProvider` and caches its own 64×64-coarse-px tiles directly (coarse-px addressed,
not the 512-native-px tile grid — see Coordinate frames).

**Hydrology carve pipeline.** Both carve stages run the *same* function —
`HydrologyProfileInprinter.computeRiverGrid` — over their own lattice. There is one merge law, not two.
`hydrology/profile/README.md` is the authority on its mechanics; the summary here is only enough to
place it in the pipeline.

`computeRiverGrid` merges every river primitive touching a lattice into one `(height, water, weight)`
triple per lattice point plus the nearest primitive's packed family/Rosgen type in a parallel
`long[]` mask. It is **ambient-free** — it never reads the caller's current elevation, only writes its
own merged surface — so each call site recovers its result with one blend,
`(1 - w) * ambient + w * min(h, ambient)`. That makes both stages **cut-only**: neither can raise
terrain. Per primitive it tabulates a cross-section LUT once (`RosgenProfile.sampleCrossSection`,
anchored on a shared integer perp-lattice index) and walks only the cells its footprint reaches, instead
of re-evaluating `RosgenProfile.delta`'s branchy per-region logic per point.

The two call sites:

1. **Tile-level shell carve** (`HydrologyProfileInprinter.carveRiverInfluence`), over the 514×514 padded
   tile, called once from `GlobalNetworkBuilder.build` (global-only graph, to shape the drainage field the
   local trace reads) and once from `LocalNetworkBuilder.build` (unified graph, into a separate clone —
   see "`buildTile` order" below for why the two calls no longer share one accumulating buffer). Each call
   reads and writes its own buffer and skips pixels with negative ambient elevation (ocean).
2. **Per-chunk bed carve** (`PopulateNoiseStep.fineGrainedPrimitivePass`), over the chunk's 16×16
   lattice, written into `Types.RIVER_DIFFERENCE` plus `Types.WATER_HEIGHT` and `Types.RIVER_TYPE`.
   It is fed by `HydrologyProfileInprinter.prefetchChunk`, which stabs the R-tree **once per chunk**
   (chunk centre plus half-diagonal radius, relief-pixel frame) and sorts by
   `HydrologicalPrimitive.comparator` — the ordering `computeRiverGrid` requires, and what lets it stop
   at the first non-`RiverPrimitive` entry. `HydrologyProfilePainter` reads `RIVER_DIFFERENCE` back to
   place river water.

Superseded designs, so old comments and commits read correctly: the shell's per-pixel R-tree stab and
distance-weighted average over `HydrologyProfile.shellElevation`/`riverInfluenceElevation`; the bed's
`resolveNearestPrimitiveIndex` + `sampleNearestChannel` → `NearestChannelSample.carveInto` polyline
foot-point sample; and, before that, the `ZoneCategory` priority merge (`WATERFALL` > `BED` >
`LAKE_BED` > `FLOODPLAIN` > `INFLUENCE`). None of those symbols exist any more. `ZoneCategory` itself
survives only as a reservation for feature types that have yet to grow real profiles.

`HydrologyTuning.MAX_LOCAL_WIDTH` is retained but unread by live code, not even through the
`FractalTerrainConfig` facade re-export.

`RiverProvider.buildTile` order, now split across `GlobalNetworkBuilder`, `LocalNetworkBuilder` and
`RiverProvider` itself — note that `assign` runs **three** times and the carve runs **twice**, once per
class, on two independent buffers rather than one shared accumulating buffer:

1. `GlobalNetworkBuilder.build` traces/relaxes the global network, then runs `ChannelElevationAssigner
   .assign` over the global-only graph, then `carveRiverInfluence` into its own clone of the decoded
   elevation — so the `fillSinks` + `computeDrainageDirection` drainage field it computes next over that
   clone already sees valleys. It returns the network, that drainage field, the boundary-elevation map it
   accumulated, and the global-only-carved elevation (used downstream only for a debug snapshot — see
   below).
2. `LocalNetworkBuilder.build` traces the local network off that drainage field and attaches every
   surviving segment directly onto that SAME graph (`LocalDrainageTracer.traceLocalNetwork`, in place, no
   return value). The tracer walks the drainage field in upstream-to-downstream order over a
   `Drainage.FlowGraph`, appends `SOURCE`/interior/`DRAIN` nodes to the graph's `AtomicView`, wires each
   new node to any nearby global-channel node (`HydrologyTuning.LOCAL_ATTACH_RADIUS`), and closes with
   `RiverNetwork.manageCollisions`, which orients the view, captures crossings and prunes every branch
   that reaches no drain.
3. `LocalNetworkBuilder.build` augments the boundary map with those local `SOURCE`/`DRAIN` nodes → a
   second `ChannelElevationAssigner.assign` over the now-unified graph → `carveRiverInfluence` (the
   pipeline's second and last carve) into a **fresh clone of the raw decoded elevation** — not
   `GlobalNetworkBuilder`'s carved clone — → the boundary map is re-seeded against that just-carved
   surface → a third `ChannelElevationAssigner.assign` so the final bed elevations agree with the carve.
   It returns the carved padded elevation.
4. Back in `RiverProvider.computeTile`, `RiverNetwork.collectPrimitives` emits every primitive (global and
   local, one shared feature-id counter, reading each channel's assigned `Channel.bedElevations`;
   oxbows/abandoned paths carry no `bedElevations` and fall back to a decoded-terrain sample) into the
   R-tree, sampled against the RAW decoded elevation rather than either carved buffer. There is no third
   carve: `LocalNetworkBuilder`'s returned elevation is only cropped (`RiverProvider.cropToTile`) into the
   published `hydrology_relief` tensor, which `ReliefProvider.computeTile` reads back through
   `getCarvedElevationTile` as relief channel 0; the `@TestOnly` `Stages` debug sink additionally captures
   `GlobalNetworkBuilder`'s discarded global-only-carved elevation as `elevationFirstPass`.

Both carve calls collect primitives **unfiltered**. The first is global-only purely by timing — the local
network does not exist yet — while the second, running after the local trace, carves local shells too.
`RiverNetwork.collectPrimitives` has a channel-id-filtering overload that would restrict the carve to global
channels, but **nothing calls it**. This matters because local networks are traced with no coarse halo,
so a local shell can be truncated at this tile's `PAD=1` border and seam against its neighbour; global
floodplains use the 2×2-cell halo and are unaffected.

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

`GenerationContext`'s constructor (`GenerationContext.java:51-82`) wires the per-world graph in one
fixed order — later providers may reach earlier ones (through the adapter) during their own tile
compute, so this order is load-bearing:

| Order | Provider | Package | Depends on |
| ----- | -------- | ------- | ---------- |
| 1 | `GlobalRiverProvider` | `hydrology/` | `WorldPipeline` coarse tensor (via the static `pipeline` field) |
| 2 | `RiverProvider` | `hydrology/` | `GlobalRiverProvider` (fallback via adapter when no test override), decoder tensor |
| 2a | `HydrologyProfileInprinter` / `HydrologyProfilePainter` | `hydrology/profile/` | the just-built `RiverProvider` |
| 3 | `ReliefProvider` | `relief/` | decoder tensor, plus `RiverProvider`'s `hydrology_relief` tile for elevation channel 0 (via the adapter) |
| 4 | `BiomeProvider` | `world/biome/` | `ReliefProvider`, `RiverProvider` (both via the adapter), climate from `WorldPipeline` |

`RiverProvider` also accepts a test-only override (`setGlobalRiverProvider`) so golden tests can
inject a fixture instead of reaching the production singleton — the seam the caller migration (below) is expected to widen into the production path.

`PopulateNoiseStep`, `FractalTerrainSurfaceSystem`, and `FractalTerrainHeightmapCache` are built after
the provider graph, reading from it via `FractalTerrainInstance`/`GenerationContext`; they are not part
of the `global → local → relief → biome` ordering constraint.

## The `GenerationContext` seam

`GenerationContext.java` holds the whole per-world graph (server, `ReliefProvider`, `BiomeProvider`,
`GlobalRiverProvider`, `RiverProvider`, `HydrologyProfileInprinter`/`Painter`, `PopulateNoiseStep`,
`FractalTerrainSurfaceSystem`, `FractalTerrainHeightmapCache`, `RandomState`, `Infinite3DVisualizer`) as
`final` fields, constructed once per world load.

**Safe publication.** `FractalTerrainInstance` holds the current context behind a
`private static volatile CompletableFuture<GenerationContext> context`
(`FractalTerrainInstance.java:40`). `init()` calls `context.complete(new GenerationContext(...))` only
after the constructor has fully run; every getter goes through `current()` → `context.get()`, which has
a happens-before edge with that `complete()` — so a worker thread can never observe a
partially-constructed or null-then-swapped context. `close()` resets `context` to a fresh incomplete
future so a subsequent `init()` republishes cleanly.

**`FractalTerrainInstance`** (`FractalTerrainInstance.java`) is the thin static adapter kept from
DL-007: it forwards ~15 historical getters (`getReliefProvider`, `getBiomeProvider`,
`getGlobalRiverProvider`, `getRiverProvider`, `getHydrologyCarver`, `getHydrologyPainter`,
`getPopulateNoiseStep`, `getSurfaceBuilder`, `getHeightmapCache`, `getNoiseConfig`, `getServer`, …) to
`current().getX()`. It also owns the one JVM-lifetime object outside `GenerationContext`: the shared
`public static volatile WorldPipeline pipeline` field, loaded once via `initPipeline()` and reused
across world (re)loads — the pipeline outlives any single world, so it is deliberately not part of the
per-world context.

**Caller migration is unfinished, and no longer separately tracked.** The plan file that used to
enumerate it is gone; this list is the record. Files still reaching through the static adapter (excluding
`FractalTerrain.java`, `FractalTerrainInstance.java` and `GenerationContext.java`, which legitimately own
or publish it):

| Area | Files |
| ---- | ----- |
| Mixins / Fabric-instantiated | `mixin/PlacedFeatureMixin.java`, `mixin/SteepSlopePredicateMixin.java`, `world/biome/source/FractalTerrainBiomeSource.java`, `world/gen/chunk/FractalTerrainChunkGenerator.java` |
| Providers & generation | `hydrology/GlobalRiverProvider.java`, `hydrology/RiverProvider.java`, `relief/ReliefProvider.java`, `relief/DecoderChannels.java`, `world/biome/BiomeProvider.java`, `world/gen/populatenoise/PopulateNoiseStep.java`, `world/gen/surfacebuilder/FractalTerrainSurfaceSystem.java` |
| Storage & helpers | `storage/FractalTerrainHeightmap.java`, `storage/FractalTerrainHeightmapCache.java`, `math/DifferenceOfGaussians.java` |
| Debug / manual harnesses | `debug/Debug.java`, `debug/Infinite3DVisualizer.java`, `debug/tests/GlobalRiverTest.java`, `debug/tests/RiverTest.java`, `debug/tests/SpatialIndexBenchmark.java` |

Mixins and Fabric-instantiated types have no constructor the mod controls, so they are expected to keep
resolving through the adapter permanently; full removal of the static getters is scoped to the remaining
mod-constructed providers and has no owner milestone. `hydrology/profile/HydrologyProfileInprinter.java` has
already been migrated — it takes its `RiverProvider` by constructor. Treat
`FractalTerrainInstance.getX()` reach-throughs as expected, not as debt to silently clean up.

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

**Logging.** `debug/Debug.java`'s `Debug.getLogger(Class<?> clazz)` is the *intended* logging entry
point: `LoggerFactory.getLogger("fractal_terrain/" + clazz.getName())` (`Debug.java:35-36`). It is not
the actual convention — the split favors bypassing the facade, 18 files using `Debug.getLogger` (mostly
via its static import) against 22 calling `LoggerFactory.getLogger` directly (`ModConfig`,
`ChannelElevationAssigner`, `LocalDrainageTracer`, `ImmutableRTree`,
`PipelineModels`, `ReachRosgenClassifier`, most `debug/tests/` harnesses, …; `Debug.java` itself is
excluded from that count, since the facade is implemented on top of `LoggerFactory`). M-002 migrated
files opportunistically rather than exhaustively, and files added since (e.g. the Rosgen/ONNX-asset
classes) mostly did not adopt the facade either. Always check a class's own logger initializer rather
than assuming the facade; new code should prefer `Debug.getLogger`.

## The three MUST-fixes

**MUST-1 — `PipelineSession` (reload race).** `ml/pipeline/PipelineSession.java` is an immutable
`record(long seed, SyntheticMapFactory syntheticMapFactory, float[] tau)`. `WorldPipeline` holds it
behind one `private volatile PipelineSession session` (`WorldPipeline.java:41`); every stage reads it
via a `Supplier<PipelineSession>` bound to `currentSession()`, snapshotting once per tile/batch instead
of reading `seed`/`syntheticMapFactory`/`tau` as three independent fields. `updateInstance` builds a
whole new session and assigns it in one write (`WorldPipeline.java:95-103`), so no worker can ever
observe a torn `(seed, map, tau)` triple. Regression: `src/test/java/.../ml/pipeline/PipelineSessionReloadRaceTest.java`
— a headless proxy (no real `SyntheticMapFactory`, since that needs ONNX assets) that drives the same
single-volatile-swap mechanism with a writer thread reloading and four reader threads asserting
`seed == tau[0]` never tears.

**MUST-2 — model load moved into `init()`.** `PipelineModels.load()` (`ml/models/PipelineModels.java:33`)
starts loading on a background daemon thread and returns immediately; `awaitLoad()` blocks on a
`CountDownLatch` until loaded or failed. Neither runs from a static initializer — `FractalTerrainInstance
.initPipeline()` (`FractalTerrainInstance.java:43-50`) calls `PipelineModels.load()` +
`PipelineModels.awaitLoad()` explicitly, so a load failure (e.g. missing datapack/model assets) surfaces
as a normal `IllegalStateException` from `init()` instead of an `ExceptionInInitializerError` that would
permanently poison the class with `NoClassDefFoundError` on every subsequent reference.

**MUST-3 — `FloatTensor` frozen at the cache-write boundary.** `infinitetensor/FloatTensor.java` holds a
`private volatile boolean frozen` (default `false`) flipped exactly once by `freeze()`. Every mutator
(`set`, `writeFrom`, `dataUnsafe`, `addFrom`, `copyFrom`) calls `checkMutable()` first and throws
`IllegalStateException` once frozen; readers (`get`, `readInto`, `entryAt`) stay callable regardless of
frozen state, so cached tensors can be read concurrently without per-read copies.

**The guarantee is narrower than "immutable once cached".** `shape` is `private final`, but `data` is
declared **`public final float[]`** — a caller can write `tensor.data[i] = x` directly and bypass
`freeze()`/`checkMutable()` entirely. Only mutation routed through the class's own methods is guarded.
(The one external reader today, `ReliefProvider.java:97`, only reads.) Treat the freeze as a guard
against accidental misuse of the tensor API, not as an enforced immutability boundary. Note also that
`copyRange` *does* allocate (`Arrays.copyOfRange`).
`storage/Storage.java` is the only caller of `freeze()`: both `persistAndRecord` (fresh compute) and
`loadInto` (disk reload) freeze *before* the entry is published into `CACHE` via
`CompletableFuture.complete` (`Storage.java:297`, `:287`) — freeze happens-before publication because it
runs on the same thread, before the key is observably completed to any other thread.

## Current debug state

The `feature/hydrology` branch carries several deliberate switch-offs. They are easy to mistake for bugs
in the architecture described above, so they are listed here explicitly. All are in code, not config
files — flipping them back means editing source.

| What | Where | Effect |
| ---- | ----- | ------ |
| Surface step runs | `DebugConfig.DISABLE_SURFACE_STEP = false \|\| !DISABLE_3D_VISUALIZER` | `DISABLE_3D_VISUALIZER = true`, so this evaluates to **false** — `buildSurface()` is NOT skipped; the surface step runs normally. |
| Biome decoration skipped | `DebugConfig.DISABLE_BIOME_DECORATION = true \|\| !DISABLE_3D_VISUALIZER` | Unconditionally true; `applyBiomeDecoration()` is a no-op. |
| Visualizer fill path inactive | `DebugConfig.DISABLE_3D_VISUALIZER = true` | `fillFromNoise()` takes the production `doFill()` branch, not the debug `debugDoFill()` path. |
| River humidity dead | `BiomeProvider.riverHumidity` | Loop body commented out; the method has no call sites and returns an all-zero array. |

## Coordinate frames

| Frame | Unit | Defined / grounded in |
| ----- | ---- | ---------------------- |
| **block-px** | 1 Minecraft world block | Chunk/column generation code (`world/gen/`); `BiomeProvider` derives tile origins as `tileX << 9` (`BiomeProvider.java:256`). |
| **tile** | 512×512 block-px (= 512×512 native-px) unit that keys nearly every per-tile cache (`Storage`/`NonIntersectingInfiniteTensor`) | `HydrologyTileGeometry.GRID = 512`, `PAD = 1`, `PADDED = 514` (`HydrologyTileGeometry.java:16-18`); `tileX = blockX >> 9` (inverse of the shift above). `GlobalRiverProvider` is the one exception — its own tile cache is addressed directly in coarse-px (see below), a separate grid from the 512-native-px relief/local-riverPrimitive/biome tile grid. |
| **native** | 1 native px, the decoder/relief pixel resolution; 1:1 with block-px inside a tile | `TensorLayout` fixes the axis order `CH=0/X=1/Z=2` (`TensorLayout.java:16-19`) for every ONNX-facing tensor in this frame; `DecoderChannels.INNER = 512` / `relief/ReliefProvider`'s `INNER = 512` size the relief tile in native px. |
| **coarse** | 1 coarse unit = 256 native px | `HydrologyTileGeometry.COARSE_PX = 256` (`HydrologyTileGeometry.java:19`); `GlobalRiverProvider` caches its own tiles in this frame directly (`getArrow(cx, cz)` etc., 64×64-coarse-px tiles); `GlobalNetworkBuilder` bridges the two frames by mapping a 512-native-px relief tile `(tileX, tileZ)` onto its 2×2 owned coarse cells `(tileX*2 + a, tileZ*2 + b)` (`GlobalNetworkBuilder.java:58-59`, `:94-95`). No current source javadoc spells out the "1 unit = 256 native pixels" definition in prose; `COARSE_PX` is the only normative source. |

## Hot/cold line of abstraction

Allocation and abstraction cost is judged by call frequency, not code size or apparent complexity. Three
bands, in order of increasing cost:

- **Cold (above the line):** code called a few times — provider construction, `GenerationContext` wiring,
  network tracing/relaxation orchestration, persistence/codec paths, debug harnesses. Abstraction,
  allocation, interfaces, streams, records, defensive copies are all fine and preferred for clarity.
  Strive to put as much code as possible above the line.
- **Warm (tile creation):** `RiverProvider.buildTile` and the 512×512-iteration tile passes it
  drives (`Drainage`, `ChannelElevationAssigner`, the `ChannelMigrator` models). Runs 512×512 times, but once per tile with
  the result cached — moderate abstraction is acceptable, unless the warm code recurses, which multiplies
  the cost back into hot territory.
- **Hot (below the line, permanently):** a chunk's 16×16 = 256-column inner loop is never cold, no matter
  how it is optimized. Optimize for performance at all costs: avoid heap allocation, avoid virtual
  dispatch/abstraction layers, avoid varargs boxing, avoid iterator/stream allocation, hoist invariants
  out of loops, reuse scratch buffers, prefer parallel primitive arrays over object graphs.

**Hot sites in this repo:**

- `world/gen/populatenoise/PopulateNoiseStep.java` `fineGrainedPrimitivePass`, the per-column blend loop
  at lines 85–96 — runs 256 times per chunk, for every chunk generated.
- `hydrology/features/HydrologicalPrimitive.java:81–85` — `double h(double[] pt, Object... args)`,
  `double w(double[] pt, Object... args)`, `double d(double[] pt)`, and every implementation
  (`RiverPrimitive`, `ConfluencePrimitive`, `SourcePrimitive`, `DeltaPrimitive`, `WaterfallPrimitive`,
  `OxbowLakePrimitive`, `AbandonedRiverPrimitive`), plus everything those call
  (`HydrologyProfile`/`RosgenProfile`/`DefaultProfile`, `VectorOps`). The `Object... args` varargs on
  `h`/`w` is itself an allocation-per-call hazard — an example of the signature shape this rule warns
  against.

**Hot-path code that already follows the rule** — the reference patterns to copy:

- `PopulateNoiseStep.java:45–53` — one `prefetchChunk` influence query serves all 256 columns of a chunk,
  instead of one query per column.
- `PopulateNoiseStep.java:54` — `final double[] mutablePt = new double[2]`, a single scratch buffer
  mutated per column instead of a fresh point allocated per block.
- `PopulateNoiseStep.java:21–24` — `static final` `BlockState` constants.
- The heightmap's parallel primitive arrays (`float[]`/`enum[]`) rather than per-column objects.

Declare an intentional hot-path optimization with `:PERF: [what]; [why]`
(`.claude/conventions/intent-markers.md`) — this is how below-the-line code tells the quality reviewer
that an allocation-avoiding or abstraction-skipping pattern is deliberate, not an oversight. See
`.claude/conventions/performance.md` for the actionable rule list for someone writing code.

## Invariants (do not violate)

- **Seams before splits.** The seam milestones (M-005/M-006/M-007/M-008) landed before the god-class
  splits (M-009..M-012); do not reintroduce shared mutable static state that bypasses `PipelineSession`
  or `GenerationContext`.
- **Build order `global → local → relief → biome`** is fixed in `GenerationContext`'s constructor
  (`GenerationContext.java:55-60`) and mirrored by the fallback chain each provider uses when no test
  override is set (`RiverProvider` → `GlobalRiverProvider`, `ReliefProvider`/`BiomeProvider` →
  `RiverProvider`). Reordering it changes which providers can legally depend on which.
- **ONNX tensor-layout invariants are fixed.** `CH=0/X=1/Z=2` and the per-stage channel counts in
  `TensorLayout.java` (`DECODER_CHANNELS=8`, `RELIEF_CHANNELS=7`, `BIOME_CHANNELS=6`,
  `GLOBAL_RIVER_CHANNELS=4`) are the model I/O contract — any stage boundary must preserve them exactly.
- **`Storage`/`InfiniteTensor` lock-ordering and CAS single-flight are deliberate.** Reads are lock-free;
  the only lock (`evictionLock`) guards eviction bookkeeping and is never touched by readers
  (`Storage.java:49`). Compute/load claims use an atomic `CACHE.putIfAbsent` (`claimForCompute`,
  `fetchEntry`) — losers block on the winner's future rather than recomputing. `InfiniteTensor`'s batched
  window claims (`InfiniteTensor.java:221-261`) depend on this claim API to claim a whole window's worth
  of dependency slices without a duplicate compute — do not bypass it.
- **`FloatTensor` is frozen once cached** — see MUST-3. Never mutate a tensor obtained from a `Storage`
  cache; if you need a mutable copy, use `slice`/`copyRange`, which allocate a fresh tensor. Note the
  freeze does **not** guard the `public final data` array — this invariant is a convention the compiler
  will not enforce for you.
- **The hydrology carve is order-dependent, at two levels — but no longer through one shared buffer.**
  *Across passes:* `carveRiverInfluence` runs once inside `GlobalNetworkBuilder` (into its own elevation
  clone, feeding only the drainage field the local trace walks) and once inside `LocalNetworkBuilder`
  (into a separate, fresh clone of the raw decoded elevation, which is what `RiverProvider` publishes).
  The two calls no longer compound on one accumulating buffer the way a single `buildTile` method once
  made them; skipping or reordering the global carve still changes the published output, but only by
  changing which cells the drainage direction — and therefore the local trace — crosses. *Within a pass:*
  `computeRiverGrid` is a sequential smoothed-min-distance recurrence over the primitive list, so its
  input MUST already be sorted by `HydrologicalPrimitive.comparator` — that sort is what puts every
  `RiverPrimitive` first and lets the loop stop at the first non-river entry, and it also fixes how
  near-equidistant competitors blend. Neither level is a refactor-safe region.
- **`RiverNetwork`/`QuadTree` reuse is per-tile and single-threaded.** `GlobalNetworkBuilder` builds and
  returns a fresh `RiverNetwork` purely from its parameters; `LocalDrainageTracer` then mutates
  that same network in place to attach the local subgraph (`traceLocalNetwork` returns nothing) — both are
  still per-tile/no-shared-state (documented explicitly in both classes' Invariants sections), so each
  tile build's graph carries no state shared across tiles or threads. `QuadTree` itself is safe for
  concurrent reads under its own read/write lock (`QuadTree.java:10-16`), but that contract exists for
  within-tree query concurrency, not for sharing one instance's *construction* across worker threads.

## Testing stance

**The suite does not compile, and the baseline below is a claim to re-verify, not a fact.**
`src/test/java/me/batata_1/fractal_terrain/` (JUnit 5, `gradle test`, `useJUnitPlatform()` in
`build.gradle`) holds 15 test classes. Measured 2026-08-23: `gradle compileJava compileClientJava
spotlessCheck` passes, but `gradle build` fails at `:compileTestJava` — `hydrology/features/
ConfluencePrimitiveTest.java` calls `ConfluencePrimitive.w(double[])` and `.d(double[])`, neither of which
exists on `ConfluencePrimitive` (it implements only `h(double[])`); 9 errors. This is pre-existing and
unrelated to the river-provider split documented above — the test file was last modified three commits
before that refactor began. `gradle test` therefore cannot run at all as the tree stands.

With `ConfluencePrimitiveTest` excluded, a run reported **81 tests, 19 failed, 1 skipped** — that figure is
measured with a test file excluded, not a clean baseline:

| Test | Covers | Status |
| ---- | ------ | ------ |
| `hydrology/GlobalRiverGoldenTest.java` | `GlobalRiverProvider`'s per-tile pipeline via `computeTileForTest` | 1 failing — tile checksum drifted from the captured golden |
| `hydrology/RiverGoldenTest.java` | `RiverProvider`'s local trace via `traceLocalNetworkForTest` | 2 failing — "synthetic field produced no local channels: fixture is degenerate" |
| `hydrology/ChannelGeometryTest.java` | Width-to-depth law (`depthForWidth`, ratio floor, exponent) | 3 failing — the pinned constants no longer match the law |
| `hydrology/features/ConfluencePrimitiveTest.java` | Junction arm bracketing, blend, `w` falloff, wire round-trip | **does not compile** — calls `.w(double[])`/`.d(double[])`, neither exists on `ConfluencePrimitive`; excluded from the 81/19/1 count above |
| `hydrology/features/HydrologicalFeaturePackTest.java` | The family/sub-type long stamped into `Types.RIVER_TYPE` | passing |
| `hydrology/profile/ComputeRiverGridTest.java` | The lattice carve's merge law, footprint scale, buffer reuse, type mask | 3 failing — newly visible now the suite reaches it, not caused by the river-provider split (its only exercised production file, `HydrologyProfileInprinter`, changed by import renames and formatting only) |
| `hydrology/profile/SampleCrossSectionTest.java` | The per-primitive cross-section LUT and its scratch-buffer contract | passing |
| `hydrology/meanders/MeandersGoldenTest.java` | `Meanders`/`RiverNetwork` collision semantics + a migration golden | 2 failing, 1 `@Disabled` (see below) |
| `hydrology/meanders/RiverNetworkSeamGoldenTest.java` | The canonical↔atomic seam round trip | passing |
| `hydrology/network/CentrelineTest.java` | `Centreline.normalAt` stencil, source one-sidedness, junction tie-break | 1 failing — wedged-channel normal off the true through-direction |
| `hydrology/rosgen/RosgenKeyTest.java` | `RosgenKey`'s Level-I decision key, one case per type plus ordering | 6 failing — type boundaries disagree (E→C, G→F, C→B) |
| `hydrology/rosgen/ReachRosgenClassifierTest.java` | Reach segmentation + downstream-first graph walk | passing |
| `hydrology/rosgen/ReachMetricsSamplerTest.java` | Raster sampling against hand-derived analytic answers | 1 failing — slope off by ~2.7× |
| `math/VectorOpsProjectionTest.java` | `VectorOps.projectPointOntoSegment` clamping and bank sign | passing |
| `ml/pipeline/PipelineSessionReloadRaceTest.java` | MUST-1 — the reload-race regression (see above) | passing |

Each headless golden drives the exact production code path over a synthetic seeded fixture (no ONNX
dependency), so a divergence in the deterministic hydrology math fails `gradle test` immediately, once the
suite compiles.

**Every failure above except `ComputeRiverGridTest`'s is pre-existing, and the baseline is a claim to
re-verify, not a fact** — this suite has broken and been repaired several times. Do not attribute a red
`gradle test` to your own change without re-measuring at `HEAD` first, and compare the *failure messages*
in `build/test-results/test/*.xml`, not just which test names fail. A worktree needs
`libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports ~132 phantom errors.

The failures fall into four kinds:

- **A test file itself does not compile** (`ConfluencePrimitiveTest`) — calls two methods
  `ConfluencePrimitive` does not implement. Pre-existing, unrelated to the river-provider split.
- **Constants the tests pin no longer match the code** (`ChannelGeometryTest`, `RosgenKeyTest`,
  `ReachMetricsSamplerTest`, `CentrelineTest`). These are stale expectations, not runtime faults — but
  which side is wrong is undecided in each case, so do not re-baseline by copying the observed value.
  `ComputeRiverGridTest` likely belongs in this bucket too now that the suite reaches it; it was not
  previously known-red only because the `:compileTestJava` break kept the suite from running that far.
- **A degenerate fixture** (`RiverGoldenTest`) — the synthetic field now yields no local channels
  at all, so two assertions never reach the behaviour they meant to gate. The fixture needs rebuilding
  before its result means anything.
- **Checksum drift** (`GlobalRiverGoldenTest`, `MeandersGoldenTest`) — the captured goldens do not
  describe what the code computes. Whether code or golden is wrong is unresolved; do not re-capture
  without deciding that first.

`MeandersGoldenTest.danglingTributaryIsCapturedIntoTrunk` is `@Disabled`, and the reason is a capability
gap rather than a flaky expectation: capture is driven by `detectCrossings`, which builds its quadtree
from `RiverNetwork.channels`, so only a **canonical** `Channel` can be found crossing a trunk. A dangling
tributary cannot be expressed as one — `update()`'s chain walk calls `onlyOutgoing()` on every non-drain
node, and a dangling end has no outgoing edge — and one added through the atomic view has no `Channel` at
all. Restoring the test needs a supported way to attach a dangling canonical channel.
