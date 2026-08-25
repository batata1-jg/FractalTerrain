# Heightmap slice sampling: one window read per channel instead of four lookups per pixel

Date: 2026-08-20
Revised: 2026-08-24 — re-verified against `f89cd65`; see "Revision notes" at the end
Status: proposed — nothing here has been implemented
Branch: `feature/hydrology`

## Problem

Every per-block sample of a `NonIntersectingInfiniteTensor` costs a cache lookup and four
allocations, and the chunk fill does 12 288 of them for data that lives in a 5x5 pixel window.

`FractalTerrainHeightmap.Types` builds a chunk's channels one at a time. Nine of the fourteen go
through `fillBilinear`/`fillSmoothStep` — six relief (`ELEVATION`, `REFINED_GRAD`, `RES`,
`BLURRED_ELEV`, `GRAD_X`, `GRAD_Y`) and three climate (`CONTINENTALNESS`, `TEMPERATURE`,
`VEGETATION`). Each pixel takes four corner samples, so that is `256 x 4 x 9 = 9216` calls to
`NonIntersectingInfiniteTensor.getValue` per chunk. `EROSION` adds 1024 through
`ErosionDensity.fillArray`, and `WEIRDNESS` adds 2048 because `WeirdnessDensity.sample` runs two
independent `Interpolation`s (magnitude and sign) over the same channel.

Each `getValue` allocates four objects:

| Allocation | Site |
| --- | --- |
| `int[3]` window index | `TensorWindow.getSinglePixelIntersection` (carries its own `TODO` about this) |
| `TileKey` | `Storage.toKey` |
| `int[3]` again | `TileKey`'s defensive `idx.clone()` |
| `Float` box | `Function<int[], Float>` return |

and `Interpolation.interpolate` allocates two more `int[2]`s (`xs`, `zs`) per pixel, across 3072
pixel calls — 2304 from the fill helpers, 768 from the two density fills.

That is roughly **55 000 allocations per chunk**, plus 12 288 `ConcurrentHashMap` lookups and
`CompletableFuture.get()` calls, to read a region that a single `getSlice` returns as one dense
array. The figure is derived from the code, not measured.

`GLOBAL_SCALE_CORRECTION` is 5, so a 16-block chunk spans 3.2 tensor pixels: the entire working set
for one channel is a 4x4 or 5x5 pixel window.

A second, independent problem surfaces on the way: `NonIntersectingInfiniteTensor` never calls
`Storage.evictIfNeeded`. Its sibling `NonIntersectingSpatialIndex` does, and so does
`InfiniteTensor.getSlice`, but NIIT itself has no budget at all. Relief tiles are `[7, 512, 512]` =
**7.34 MB** and biome tiles 6.29-7.34 MB, and they accumulate for the life of the world without
bound.

## Decisions

Settled during brainstorming; recorded with rationale so they are not relitigated.

| Decision | Choice | Why |
| --- | --- | --- |
| Class hierarchy | `NonIntersectingInfiniteTensor` does **not** extend `InfiniteTensor`; the window walk is extracted to a shared static | Smallest blast radius. Avoids the `updateOutput` override, the `updatePath` hazard, and a delegation layer over five call sites |
| Extraction shape | Geometry only, behind one `RegionVisitor`; each caller keeps its own fetch and write | The writer has no live variation (§1), and the source difference costs nothing inside a visitor body |
| `updateOutput` | Untouched | With no hierarchy move there is nothing to override. The premise that it would throw dissolves |
| Fill structure | Each `Types` entry takes its own slice | ~11 `getSlice` per chunk instead of 9216 `getValue`; keeps the `Types` contract and makes each channel independently provable |
| Eviction | Real budgets on relief and biome only | They are the two large tensors. `global_river`, `dog_tensor` and `hydrology_relief` are small (see section 2) |
| Eviction site | Inside `loadInto`, not `getSlice` | `loadInto` is a NIIT's only insert path, so the budget holds for `getValue`-only readers too — and `compute(FunctionContext)` stays on `getValue`. Matches `NonIntersectingSpatialIndex` |
| Budget parameter | New constructor overload, old 4-arg signature delegates with `Long.MAX_VALUE` | Only the two sites that need a budget change; `global_river`, `dog_tensor` and `hydrology_relief` are not churned |
| Call-site scope | Heightmap fill **and** the three `BiomeProvider` density functions | The density path is as hot as the heightmap |
| `compute(FunctionContext)` | Stays on `getValue` | A single point makes a slice strictly more work. Converting it needs a per-thread slice cache, deliberately out of scope |
| Corner selection | `floor`/`ceil`, never `floor`/`floor+1` | Correctness, not style — see section 3 |

## 1. Extract the window geometry

`InfiniteTensor.getSlice` is three parts: `ensureComputed`, an accumulation loop, and
`storage.evictIfNeeded(cacheLimitBytes)`. Inside the middle part, only the geometry is shared with a
non-intersecting tensor: the `lo`/`hi` walk, the intersection of each window's bounds with the
requested pixel range, and the `srcRegion`/`dstRegion` arithmetic derived from it. Fetching a window
and writing it into the output are the caller's business.

New `infinitetensor/SliceGeometry.java` holds that walk as a static, lifted verbatim — the same
`InfiniteTensor.iterateWindows` walk, the same `isect`/`srcRegion`/`dstRegion` buffers hoisted out
of the callback:

```java
static void forEachIntersection(TensorWindow window, int[][] pixelRange, RegionVisitor visitor)

@FunctionalInterface
interface RegionVisitor {
    /** All three arrays are reused buffers; a visitor that keeps one must copy it. */
    void visit(int[] windowIndex, int[][] dstRegion, int[][] srcRegion);
}
```

One interface, not two. Each caller supplies its own fetch and its own write:

| Caller | Visitor body |
| --- | --- |
| `InfiniteTensor` | `var c = getEntryOrRecompute(wi); if (c != null) updateOutput(output, c, dst, src);` |
| `NonIntersectingInfiniteTensor` | `output.addFrom(getEntry(wi), dst, src)` |

`updateOutput` stays exactly where it is, so `InfiniteTensor`'s extension seam is untouched and the
helper never has to reach a protected method on a class it is not.

**Why there is no writer parameter.** `updateOutput` is abstract, which makes it look like a
variation point, but `AdditiveInfiniteTensor` is its only subclass and its only body is
`output.addFrom(src, dstRegion, srcRegion)`. `addFrom` is the correct write for NIIT as well:
windows overlap only when `stride < size`, and NIIT builds its `TensorWindow` through the
`size`-only constructor, where `stride == size`. Its windows are disjoint, so at most one covers any
output pixel, and over a zero-initialised `new FloatTensor(outShape)` accumulate and overwrite
produce identical bytes. A writer parameter would carry no live variation.

**Why there is no source parameter.** The two fetches do differ —
`InfiniteTensor.getEntryOrRecompute` catches and calls `computeSingle`, while NIIT's `getEntry`
self-heals inside `loadInto` — but that difference lives in the visitor body, where it costs
nothing and needs no new type. The `cached == null` guard stays with `InfiniteTensor`, the only
caller whose source can return null. The one behavioural shift is that a window's geometry is now
computed before its fetch rather than after, so a null entry pays the intersection arithmetic before
being skipped; that branch is defensive and should never fire.

`InfiniteTensor.getSlice` keeps `ensureComputed` before and its existing `evictIfNeeded` after, so
its behaviour is unchanged — including the state where `updateOutput` is the sole write. The
commented-out `output.addFrom(...)` line the loop still carries goes away with the lift; a dead
comment must not survive into the extracted static.

**On the new abstraction.** `RegionVisitor` is invoked **once per intersecting window** — one to
four per chunk read, never per pixel. That is the warm band in
`.claude/conventions/performance.md`, not the hot band, so the indirection is within budget. It is
called out here because adding a virtual dispatch layer inside a routine whose purpose is removing
per-sample overhead invites exactly the wrong reading.

## 2. `NonIntersectingInfiniteTensor.getSlice`

```java
public FloatTensor getSlice(int[] start, int[] end) {
    final int[] outShape = new int[start.length];
    for (int d = 0; d < outShape.length; d++) outShape[d] = end[d] - start[d];
    final FloatTensor out = new FloatTensor(outShape);
    SliceGeometry.forEachIntersection(
            outWindow,
            InfiniteTensor.buildRange(start, end),
            (wi, dst, src) -> out.addFrom(getEntry(wi), dst, src));
    return out;
}
```

`buildRange` is package-private and NIIT shares `InfiniteTensor`'s package, so the range conversion
is reused rather than restated.

There is no `ensureComputed` step. `NonIntersectingInfiniteTensor.loadInto` already catches
`EntryNotLoadableException` and recomputes the tile synchronously, so `getEntry` self-heals per
window — the contract documented in `storage/README.md`, unchanged. Both routes still converge on
`Storage`'s single-flight, so no key computes twice.

### Budgets

`NonIntersectingSpatialIndex` — NIIT's sibling under the same `Storage` base — already solves this
exact problem, and its shape is the one to copy: a `private final long cacheLimitBytes` field, taken
as a constructor argument, enforced in `loadInto` after the promise is settled.

```java
@Override
protected void loadInto(TileKey key, CompletableFuture<FloatTensor> promise) {
    try {
        super.loadInto(key, promise);
    } catch (EntryNotLoadableException miss) {
        final FloatTensor entry = entry_creating_function.apply(key);
        persistAndRecord(key, entry);
        promise.complete(entry);
    }
    evictIfNeeded(cacheLimitBytes);
}
```

`loadInto` is the **only** path by which a NIIT inserts into `Storage`'s cache accounting — the
disk-hit branch inside `super.loadInto` and the recompute branch both call through to
`recordCachedEntry`. Evicting there therefore bounds the cache no matter what the reader called, and
a cache hit stays on `Storage`'s lock-free read path. Putting it in `getSlice` instead would leave
every `getValue`-only reader unbounded, which for `final_biome_tiles` is the dominant path (see
section 5).

Evicting after `promise.complete` is load-bearing: evicting first would drop the in-flight promise
from `CACHE` and let a racing reader start a duplicate compute. `NonIntersectingSpatialIndex` states
the same ordering constraint in its own comment.

| Tensor | Site | Tile size | Budget | Rationale |
| --- | --- | --- | --- | --- |
| `final_relief_tiles` | `ReliefProvider` | 7.34 MB | 8 tiles, ~59 MB | The memory problem |
| `final_biome_tiles` | `BiomeProvider` | 6.29-7.34 MB | 8 tiles, ~59 MB | The memory problem |
| `global_river` | `GlobalRiverProvider` | `[4, 64, 64]` = 65 KB | `Long.MAX_VALUE` | Small; `path == null` in the debug harnesses |
| `dog_tensor` | `DifferenceOfGaussians` | `[1, 64, 64]` = 16 KB | `Long.MAX_VALUE` | Small |
| `hydrology_relief` | `RiverProvider` | `[1, 512, 512]` = 1.05 MB | `Long.MAX_VALUE` | Small, and it currently has no reader |

The biome tile is 6.29 MB at `BIOME_CHANNELS = 6` and 7.34 MB when the debug distance-to-shore
channel is present, which is whenever the 3D visualizer is enabled. Budget against the larger.

Eight is deliberately far above the working set. A tile is 512 px = 2560 blocks = **160 chunks** per
axis, so an entire render distance sits inside one to four tiles.

That headroom is what makes the ordering safe. `Storage.recordCachedEntry` runs only on insert,
never on read, so `cachedEntryByteSizes` is **FIFO, not LRU** — a tile read on every chunk is still
evicted once eight newer tiles land. With a working set of at most four against a budget of eight,
that cannot happen; if the budget is ever lowered, this is the property that breaks first.

A miss on a disk-backed tensor is a 7.34 MB reload. A miss when `path == null` is a full recompute,
which for relief means ONNX inference — another reason the two budgeted tensors are the two that
are always disk-backed in a real world.

### Constructor

```java
/** Unbounded cache, the historical behaviour of every tensor here. */
public NonIntersectingInfiniteTensor(String path, String name, int[] shape, Function<TileKey, FloatTensor> f) {
    this(path, name, shape, f, Long.MAX_VALUE);
}
```

Only `ReliefProvider` and `BiomeProvider` move to the five-argument form. `evictIfNeeded(Long.MAX_VALUE)`
on the other three is a lock acquire and one comparison on a miss path, which is the same cost
`NonIntersectingSpatialIndex` already pays.

## 3. Allocation-free window sampler

New overloads in `math/Interpolation`, reading a `[C, w, h]` slice at channel `ch` (base offset
`ch * w * h`, row stride `h`):

```java
public static double sampleBilinear(float[] d, int base, double px, double pz, int w, int h)
public static double sampleSmoothStep(float[] d, int base, double px, double pz, int w, int h)
```

These sit beside the existing `sampleBilinear(float[], double, double, int)`, which is square-only
and edge-clamped and is not a substitute.

### The corner rule

**The corners must be `floor` and `ceil`, not `floor` and `floor + 1`.** This is a correctness
requirement.

`Interpolation.interpolate` takes `xs = {floor(x), ceil(x)}`. When a coordinate lands exactly on a
pixel, `floor == ceil` and only one column is read. A `floor + 1` sampler produces a numerically
identical value — the extra column carries weight zero — but reads one pixel further. At a tile
boundary that pixel belongs to the next 512x512 tile, forcing a materialisation that does not happen
today. For relief that is a full ONNX inference for a value multiplied by zero.

Worked example, `GLOBAL_SCALE_CORRECTION = 5`, tile boundary at pixel 512 (block 2560):

| Chunk `blockX` | Pixel range | `ceil(max)` | Tiles touched | With `floor+1` |
| --- | --- | --- | --- | --- |
| 2540..2555 | 508.0 .. 511.0 | 511 | tile 0 | tile 0 **and 1** — spurious |
| 2544..2559 | 508.8 .. 511.8 | 512 | tiles 0 and 1 | tiles 0 and 1 — same |

The slice bounds follow the same rule: `[floor(minPx), ceil(maxPx)]` inclusive, so
`end = ceil(maxPx) + 1` exclusive.

## 4. Provider fill methods

`fillBilinear`/`fillSmoothStep` leave `FractalTerrainHeightmap` and become a shared static helper,
`storage/ChunkChannelFill`, that both providers call: one `getSlice` for the chunk's window, then
256 allocation-free samples.

The backing `float[]` is hoisted once per fill via `slice.dataUnsafe()` — the tensor is freshly
allocated by `getSlice` and never published to a cache, so it is unfrozen and the documented
escape hatch applies. It carries a `:PERF:` marker per `.claude/conventions/intent-markers.md`,
because `infinitetensor/README.md` states that reaching into `.data` on a tensor obtained from a
cache is a bug regardless of the frozen flag, and a reader needs to see why this one is not that.

Each provider then exposes one chunk-filler per channel, and the converted `Types` entries become
plain method references — **the shape `EROSION(getBiomeProvider()::fillErosion)` already has in that
file**:

```java
ELEVATION      (getReliefProvider()::fillElev),
BLURRED_ELEV   (getReliefProvider()::fillBlurredElev),
GRAD_X         (getReliefProvider()::fillGradX),
...
CONTINENTALNESS(getBiomeProvider()::fillContinentalness),
```

This keeps channel indices inside the providers, where `get_entry` and `biomeChannel` keep them
today, instead of leaking raw ints into `storage/`.

### The per-pixel getters this orphans

The heightmap is the sole caller of eight of the nine getters, so converting the nine `Types`
entries leaves them dead. Delete them with the conversion rather than leaving unreferenced public
API behind:

| Getter | Callers after conversion |
| --- | --- |
| `ReliefProvider.getElev` | `Infinite3DVisualizer.DebugModes.RELIEF` — **keep** |
| `ReliefProvider.getBlurredElev`, `getGradX`, `getGradY`, `getRefinedGrad`, `getRes` | none |
| `BiomeProvider.getContinentalness`, `getTemperature`, `getVegetation` | none |
| `BiomeProvider.getDistShore`, `getWeirdness` | `Infinite3DVisualizer` (`@TestOnly`) — **keep** |

`ReliefProvider.get_entry` and `BiomeProvider.biomeChannel` stay as the shared bodies of the
survivors. `ReliefProvider.getLowFreqGrad` is already dead independently of this change and is not
this spec's to remove.

`GlobalRiverProvider` and `DifferenceOfGaussians` are unaffected: they call `getValue` on their own
`global_river` and `dog_tensor`, not on these getters.

Two incidental cleanups fall out and should land with the rewrite rather than as a follow-up:

- `fillBilinear`'s `mutableCoords[0]` and `[1]` writes are dead. `Interpolation.interpolate`
  overwrites `[1]` and `[2]` (`X`, `Z`), and `ReliefProvider.get_entry` overwrites `[0]` (`CH`).
  `fillSmoothStep` already omits them.
- The aliasing where `TensorWindow.getPerWindowCoord(int[])` mutates the caller's array in place
  disappears. It is currently harmless only because every index is rewritten before the next read.

## 5. Biome density functions

`BiomeProviderDensity`, `ErosionDensity` and `WeirdnessDensity` are private nested classes of
`BiomeProvider`, each holding one or two `Interpolation`s over `final_tiles`.

- `ErosionDensity.fillArray(float[], ChunkPos)` and `WeirdnessDensity.fillArray(float[], ChunkPos)`
  are chunk rectangles and take the section 4 treatment directly. The shattered-band nudge
  (`SHATTERED_LO`/`HI`/`PUSH`) and the magnitude-times-sign composition apply after sampling,
  unchanged.
- `WeirdnessDensity` runs two `Interpolation`s over the same channel, so **one slice serves both**.
  This is where the largest single reduction lands: 2048 lookups to one.
- `fillArray(double[], ContextProvider)` receives arbitrary positions from `applier.forIndex(i)`,
  not a chunk rectangle. Two passes: scan the batch for the `blockX`/`blockZ` bounding box (two int
  reads per index), take one slice over it, then sample. If the bounding box exceeds a sanity bound,
  fall back to the current per-point path rather than allocating an unbounded slice.
- `compute(FunctionContext)` is left on `getValue`. It is a single point, where a slice is strictly
  more work than a lookup.

**Known limitation, accepted.** `Climate.Sampler.sample` calls `compute`, not `fillArray`, so
`compute` is the dominant path for vanilla biome resolution. Leaving it unconverted means the biome
share of the win is smaller than the channel count suggests. Converting it properly requires a
per-thread slice cache, which was considered and rejected during brainstorming on lifetime grounds.
It is also why the biome tensor's budget must be enforced in `loadInto` rather than in `getSlice`.

## 6. Verification

No golden test covers the heightmap fill, so the change brings its own. A new JUnit test constructs a
synthetic `NonIntersectingInfiniteTensor` over a deterministic in-memory creating function with
`path == null` — no model, no ONNX, no world — and asserts two things over a chunk set that includes
tile-boundary crossings in both axes and negative coordinates:

1. **Bit-identical output.** New fill versus the current per-pixel path, per channel. The two
   compute the same corners with the same `double` arithmetic, so equality is exact, not
   approximate.
2. **Identical tile-touch set.** Count the distinct keys the creating function is invoked for. This
   is the assertion that catches the section 3 regression; the value check alone passes while
   silently doubling ONNX work at tile edges.

Acceptance: `gradle spotlessApply`, then `gradle build`, then `gradle test`.

**`gradle build` does not currently reach the tests.** Per the root `CLAUDE.md` baseline measured
2026-08-23, `:compileTestJava` fails with 9 errors:
`src/test/.../hydrology/features/ConfluencePrimitiveTest.java` calls `ConfluencePrimitive.w(double[])`
and `.d(double[])`, neither of which exists. That failure is pre-existing and unrelated to this work,
but it means the new test cannot run until it is repaired or that file is excluded. Repairing it is
not in this spec's scope; the implementer must either fix it first or record the exclusion explicitly.

With `ConfluencePrimitiveTest` excluded, the quoted baseline is 81 tests, 19 failed, 1 skipped:
`RosgenKeyTest` (6), `ComputeRiverGridTest` (3), `ChannelGeometryTest` (3), `RiverGoldenTest` (2),
`MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1),
`CentrelineTest` (1). Compare the **failure messages** in `build/test-results/test/*.xml`, not just
which test names fail. That baseline is a claim to re-measure in a worktree at `HEAD` with
`libs/onnxruntime/teste.jar` copied in, not a fact to trust.

## Files

**New**

| File | Contents |
| --- | --- |
| `infinitetensor/SliceGeometry.java` | The extracted window walk and intersection arithmetic, plus its one `RegionVisitor` interface |
| `storage/ChunkChannelFill.java` | Chunk-window slice fetch plus the 256-sample upscale loop |
| `src/test/.../FractalTerrainHeightmapFillTest.java` | Equivalence and tile-touch assertions |

**Changed**

| File | Change |
| --- | --- |
| `infinitetensor/InfiniteTensor.java` | `getSlice` delegates its window walk to `SliceGeometry`; the dead `addFrom` comment goes |
| `infinitetensor/NonIntersectingInfiniteTensor.java` | `getSlice`, `cacheLimitBytes` field, constructor overload, `evictIfNeeded` in `loadInto` |
| `math/Interpolation.java` | Two window-sampling overloads |
| `storage/FractalTerrainHeightmap.java` | Nine `Types` entries become provider method references; fill helpers removed |
| `relief/ReliefProvider.java` | Six chunk fillers; relief cache budget; five orphaned getters removed |
| `world/biome/BiomeProvider.java` | Three chunk fillers; biome cache budget; three density functions converted; three orphaned getters removed |
| `infinitetensor/README.md`, `infinitetensor/CLAUDE.md`, `storage/README.md` | Document `SliceGeometry`, NIIT's slice path, and the budgets |

## Out of scope

- `compute(FunctionContext)` and the per-thread slice cache it would need.
- `GlobalRiverProvider`, `DifferenceOfGaussians` and `Infinite3DVisualizer`, which read single cells
  where a slice is more work than a lookup.
- The `TensorWindow.getSinglePixelIntersection` allocation (its own `TODO`). Removing it would speed
  up every remaining `getValue` caller, but it is a separate change with a separate blast radius.
- `ReliefProvider.getLowFreqGrad`, dead before this change.
- Repairing `ConfluencePrimitiveTest`, which blocks `gradle build` for unrelated reasons.

## Risks

| Risk | Mitigation |
| --- | --- |
| A `floor+1` sampler silently doubles tile materialisation at chunk edges | The tile-touch assertion in section 6, which is the only check that catches it |
| The 8-tile budget evicts a live tile under FIFO ordering | 160-chunk tiles put the working set at 1-4; the margin is the mitigation and is stated so a future reduction is understood as load-bearing |
| Eviction ordering drops an in-flight promise and doubles a compute | Evict after `promise.complete`, as `NonIntersectingSpatialIndex.loadInto` already does |
| `dataUnsafe()` on a slice reads as a violation of the freeze invariant | `:PERF:` marker plus a comment naming why this tensor is never cached |
| The extracted walk drifts from `InfiniteTensor`'s original | Lift verbatim in one commit, convert callers in the next, so the diff shows a move rather than a rewrite |
| A later `InfiniteTensor` subclass needs a write other than `addFrom` | `updateOutput` is untouched, so that subclass overrides it as today; only NIIT hardcodes `addFrom`, and §1 states the disjointness that makes it correct there |

## Revision notes

What the 2026-08-20 draft assumed, and what `f89cd65` actually holds. Two of these change the design:
the eviction site and the shape of the §1 extraction. The rest are fact corrections.

| Draft said | Tree at `f89cd65` |
| --- | --- |
| `evictIfNeeded` belongs in `NonIntersectingInfiniteTensor.getSlice` | `NonIntersectingSpatialIndex` — NIIT's sibling — landed with the same budget in `loadInto`, which is a NIIT's only insert path and so covers `getValue` readers too. Sections 2 and 5 follow it |
| The accumulation loop has two variation points, so `SliceAssembler` takes a `WindowSource` and a `RegionWriter` | `AdditiveInfiniteTensor` is `updateOutput`'s only subclass and its only body is `addFrom`, which is also correct over NIIT's disjoint windows — so the writer has no live variation, and the source difference needs no bespoke type. §1 extracts geometry only, behind a single `RegionVisitor`, as `SliceGeometry` |
| The only `evictIfNeeded` caller is `InfiniteTensor.getSlice` | `NonIntersectingSpatialIndex.loadInto` calls it as well |
| `local_carved_elev_v2` (1.05 MB) is scheduled for deletion | `LocalRiverProvider` is gone. `RiverProvider` owns `hydrology_relief` `[1, 512, 512]`, newly built and with no reader yet — small, not doomed |
| `WorldPipeline`'s plain `private final long cacheLimitBytes` is the precedent | `NonIntersectingSpatialIndex` is the closer one: same base class, same constructor-parameter shape |
| The stray `output.addFrom` was removed in the working tree | It is committed as a commented-out line in `InfiniteTensor.getSlice`, and the lift should delete it |
| `global_river` and `dog_tensor` are "coarse" | 65 KB and 16 KB respectively; sized in section 2 |
| The per-pixel getters stay for `Infinite3DVisualizer`, `GlobalRiverProvider`, `DifferenceOfGaussians`, `compute` | Only `getElev`, `getDistShore` and `getWeirdness` keep a caller. The other eight are orphaned by the conversion; the last two providers read their own tensors, not these getters. Section 4 lists them |
| Baseline: 90 tests, 20 failed, `LocalRiverGoldenTest`, `ConfluencePrimitiveTest` (4) | `gradle build` fails at `:compileTestJava`, so `gradle test` cannot run at all; 81/19/1 with that file excluded, and `RiverGoldenTest` replaces `LocalRiverGoldenTest`. Section 6 |

Re-verified as still exactly true: the nine `fillBilinear`/`fillSmoothStep` `Types` entries and the
9216/1024/2048 split; the four-allocation `getValue` breakdown; `GLOBAL_SCALE_CORRECTION = 5f`;
`RELIEF_CHANNELS = 7` and `INNER = 512`, giving 7.34 MB relief tiles; 6.29-7.34 MB biome tiles;
`Storage.recordCachedEntry` being insert-only, hence FIFO; the `TensorWindow` `TODO`;
`FloatTensor.dataUnsafe()` throwing once frozen; and the `floor`/`ceil` corners in
`Interpolation.interpolate`.
