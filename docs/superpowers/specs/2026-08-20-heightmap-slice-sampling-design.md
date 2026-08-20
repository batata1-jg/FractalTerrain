# Heightmap slice sampling: one window read per channel instead of four lookups per pixel

Date: 2026-08-20
Status: proposed
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

and `Interpolation.interpolate` allocates two more `int[2]`s per sample, across 3072 sample calls.

That is roughly **55 000 allocations per chunk**, plus 12 288 `ConcurrentHashMap` lookups and
`CompletableFuture.get()` calls, to read a region that a single `getSlice` returns as one dense
array. The figure is derived from the code, not measured.

`GLOBAL_SCALE_CORRECTION` is 5, so a 16-block chunk spans 3.2 tensor pixels: the entire working set
for one channel is a 4x4 or 5x5 pixel window.

A second, independent problem surfaces on the way: `NonIntersectingInfiniteTensor` never calls
`Storage.evictIfNeeded` — the only caller is `InfiniteTensor.getSlice`. Relief tiles are
`[7, 512, 512]` = **7.34 MB** and biome tiles 6.29-7.34 MB, and they accumulate for the life of the
world without bound.

## Decisions

Settled during brainstorming; recorded with rationale so they are not relitigated.

| Decision | Choice | Why |
| --- | --- | --- |
| Class hierarchy | `NonIntersectingInfiniteTensor` does **not** extend `InfiniteTensor`; the slice loop is extracted to a shared static | Smallest blast radius. Avoids the `updateOutput` override, the `updatePath` hazard, and a delegation layer over five call sites |
| `updateOutput` | Untouched | With no hierarchy move there is nothing to override. The premise that it would throw dissolves |
| Fill structure | Each `Types` entry takes its own slice | ~11 `getSlice` per chunk instead of 9216 `getValue`; keeps the `Types` contract and makes each channel independently provable |
| Eviction | Real budgets on relief and biome only | They are the two large tensors. `global_river` and `dog_tensor` are coarse and small; `local_carved_elev_v2` is scheduled for deletion |
| Budget parameter | New constructor overload, old 4-arg signature delegates with `Long.MAX_VALUE` | Only the two sites that need a budget change; the doomed `carved` site is not churned |
| Call-site scope | Heightmap fill **and** the three `BiomeProvider` density functions | The density path is as hot as the heightmap |
| `compute(FunctionContext)` | Stays on `getValue` | A single point makes a slice strictly more work. Converting it needs a per-thread slice cache, deliberately out of scope |
| Corner selection | `floor`/`ceil`, never `floor`/`floor+1` | Correctness, not style — see section 3 |

## 1. Extract the slice accumulator

`InfiniteTensor.getSlice` is three parts: `ensureComputed`, an accumulation loop, and
`evictIfNeeded`. Only the middle is shared with a non-intersecting tensor.

New `infinitetensor/SliceAssembler.java` holds the loop as a static, lifted verbatim — the same
intersection arithmetic, the same `isect`/`srcRegion`/`dstRegion` buffers hoisted out of the
callback, the same `InfiniteTensor.iterateWindows` walk:

```java
static FloatTensor assemble(TensorWindow window, int[] start, int[] end,
                            WindowSource source, RegionWriter writer)
```

Two variation points become parameters:

| Caller | `WindowSource` | `RegionWriter` |
| --- | --- | --- |
| `InfiniteTensor` | `this::getEntryOrRecompute` | `this::updateOutput` |
| `NonIntersectingInfiniteTensor` | `this::getEntry` | `output.copyFrom(src, dst, srcRegion)` |

`InfiniteTensor.getSlice` keeps `ensureComputed` before and `evictIfNeeded` after, so its behaviour
is unchanged — including the state where `updateOutput` is the sole write.

The copy writer is correct for disjoint windows for a reason worth stating: at most one window
covers any output pixel, so overwrite and accumulate-into-zero are equivalent. `copyFrom` says so
explicitly rather than relying on the output being zero-initialised.

**On the new abstraction.** `WindowSource` and `RegionWriter` are invoked **once per intersecting
window** — one to four per chunk read, never per pixel. That is the warm band in
`.claude/conventions/performance.md`, not the hot band, so the indirection is within budget. It is
called out here because adding a virtual dispatch layer inside a routine whose purpose is removing
per-sample overhead invites exactly the wrong reading.

## 2. `NonIntersectingInfiniteTensor.getSlice`

```java
public FloatTensor getSlice(int[] start, int[] end) {
    final FloatTensor out = SliceAssembler.assemble(outWindow, start, end, this::getEntry, COPY);
    evictIfNeeded(cacheLimitBytes);
    return out;
}
```

There is no `ensureComputed` step. `NonIntersectingInfiniteTensor.loadInto` already catches
`EntryNotLoadableException` and recomputes the tile synchronously, so `getEntry` self-heals per
window — the contract documented in `storage/README.md`, unchanged. Both routes still converge on
`Storage`'s single-flight, so no key computes twice.

### Budgets

Following `WorldPipeline`'s precedent (a plain `private final long cacheLimitBytes`, not a
properties key), each site declares its own constant.

| Tensor | Tile size | Budget | Rationale |
| --- | --- | --- | --- |
| `final_relief_tiles` | 7.34 MB | 8 tiles, ~59 MB | The memory problem |
| `final_biome_tiles` | 6.29-7.34 MB | 8 tiles, ~59 MB | The memory problem |
| `global_river` | coarse | `Long.MAX_VALUE` | Small; `path == null` in the debug harnesses |
| `dog_tensor` | coarse | `Long.MAX_VALUE` | Small |
| `local_carved_elev_v2` | 1.05 MB | `Long.MAX_VALUE` | Scheduled for deletion |

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

Only `ReliefProvider` and `BiomeProvider` move to the five-argument form.

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
allocated by `SliceAssembler` and never published to a cache, so it is unfrozen and the documented
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

This keeps channel indices inside the providers, where `getBlurredElev` and `biomeChannel` keep them
today, instead of leaking raw ints into `storage/`. The per-pixel getters stay:
`Infinite3DVisualizer`, `GlobalRiverProvider`, `DifferenceOfGaussians` and
`compute(FunctionContext)` still use them.

Two incidental cleanups fall out and should land with the rewrite rather than as a follow-up:

- `fillBilinear`'s `mutableCoords[0]` and `[1]` writes are dead. `Interpolation.interpolate`
  overwrites `[1]` and `[2]`, and `ReliefProvider.get_entry` overwrites `[0]`.
- The aliasing where `TensorWindow.getPerWindowCoord(int[])` mutates the caller's array in place
  disappears. It is currently harmless only because every index is rewritten before the next read.

## 5. Biome density functions

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

Compare the **failure messages** in `build/test-results/test/*.xml`, not just which test names fail.
Per the root `CLAUDE.md`, the quoted baseline — 90 tests, 20 failed, 1 skipped, in `RosgenKeyTest`
(6), `ConfluencePrimitiveTest` (4), `ChannelGeometryTest` (3), `LocalRiverGoldenTest` (2),
`MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1),
`CentrelineTest` (1) — is a claim to re-measure in a worktree at `HEAD` with
`libs/onnxruntime/teste.jar` copied in, not a fact to trust.

## Files

**New**

| File | Contents |
| --- | --- |
| `infinitetensor/SliceAssembler.java` | The extracted accumulation loop plus its two functional interfaces |
| `storage/ChunkChannelFill.java` | Chunk-window slice fetch plus the 256-sample upscale loop |
| `src/test/.../FractalTerrainHeightmapFillTest.java` | Equivalence and tile-touch assertions |

**Changed**

| File | Change |
| --- | --- |
| `infinitetensor/InfiniteTensor.java` | `getSlice` delegates its loop to `SliceAssembler` |
| `infinitetensor/NonIntersectingInfiniteTensor.java` | `getSlice`, `cacheLimitBytes` field, constructor overload |
| `math/Interpolation.java` | Two window-sampling overloads |
| `storage/FractalTerrainHeightmap.java` | Nine `Types` entries become provider method references; fill helpers removed |
| `relief/ReliefProvider.java` | Six chunk fillers; relief cache budget |
| `world/biome/BiomeProvider.java` | Three chunk fillers; biome cache budget; three density functions converted |
| `infinitetensor/README.md`, `infinitetensor/CLAUDE.md`, `storage/README.md` | Document `SliceAssembler`, NIIT's slice path, and the budgets |

## Out of scope

- `compute(FunctionContext)` and the per-thread slice cache it would need.
- `GlobalRiverProvider`, `DifferenceOfGaussians` and `Infinite3DVisualizer`, which read single cells
  where a slice is more work than a lookup.
- The `TensorWindow.getSinglePixelIntersection` allocation (its own `TODO`). Removing it would speed
  up every remaining `getValue` caller, but it is a separate change with a separate blast radius.
- Deleting `LocalRiverProvider.carved`, which is separately scheduled.

## Risks

| Risk | Mitigation |
| --- | --- |
| A `floor+1` sampler silently doubles tile materialisation at chunk edges | The tile-touch assertion in section 6, which is the only check that catches it |
| The 8-tile budget evicts a live tile under FIFO ordering | 160-chunk tiles put the working set at 1-4; the margin is the mitigation and is stated so a future reduction is understood as load-bearing |
| `dataUnsafe()` on a slice reads as a violation of the freeze invariant | `:PERF:` marker plus a comment naming why this tensor is never cached |
| The extracted loop drifts from `InfiniteTensor`'s original | Lift verbatim in one commit, convert callers in the next, so the diff shows a move rather than a rewrite |

## Prior change in the working tree

`InfiniteTensor.getSlice` previously called `updateOutput` **and then** an unconditional
`output.addFrom`, double-counting every `AdditiveInfiniteTensor` slice. The stray `addFrom` was
removed in the working tree before this spec was written. `BiomeProvider.getCoarseSlice` masked the
old behaviour by normalising channel 0 against the channel 6 weight, so the factor of two cancelled
there; any unmasked reader of a diffusion slice now gets different numbers. That change is
independent of this design, but it is in the same working tree and should be verified against
generated output separately rather than being attributed to this work.
