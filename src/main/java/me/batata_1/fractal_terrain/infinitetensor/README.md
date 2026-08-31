# infinitetensor/

## Overview

Tiled "infinite" tensor abstraction over `storage/Storage`: an `InfiniteTensor` has unbounded logical
extent but is computed and cached window-by-window (`TensorWindow` defines window size/stride/offset), with
each window's compute function receiving already-resolved dependency slices. `AdditiveInfiniteTensor` sums
overlapping-window contributions on read (used for the diffusion tensors, whose windows overlap);
`NonIntersectingInfiniteTensor` covers the disjoint-window case directly on top of `Storage` and recomputes
a window on a recoverable load failure instead of propagating it.

## Architecture

`InfiniteTensor.getSlice(start, end)` first calls `ensureComputed`, which recursively ensures every
upstream dependency's needed windows exist before computing this tensor's own missing windows
(single-flighted through `Storage.getOrCompute`/`claimForCompute`), then accumulates every window
intersecting the requested range into one output `FloatTensor` via `updateOutput` (add for
`AdditiveInfiniteTensor`, overwrite for a non-overlapping tensor). `FloatTensor` is the payload every
`InfiniteTensor` produces and every `Storage<FloatTensor>` caches. The walk that decides *which* windows a
range touches, and how each one's pixels map into the output, lives in `SliceGeometry` — geometry only,
with fetching and writing left to the visitor, which is what lets two unrelated tensor types share it.

`NonIntersectingInfiniteTensor` has its own `getSlice` over the same `SliceGeometry` walk, but no
`ensureComputed` step: its `loadInto` already recomputes a missing tile, so `getEntry` self-heals per
window and both routes converge on `Storage`'s single-flight. It writes with `addFrom` even though its
windows never overlap — `TensorWindow(int[] size)` sets `stride == size`, so at most one window covers
any output pixel, and over a zero-initialised output accumulate and overwrite produce identical bytes.
That is why `SliceGeometry` carries no writer parameter: `updateOutput` is abstract, but
`AdditiveInfiniteTensor` is its only subclass and its only body is the same `addFrom`.

## Invariants

**`FloatTensor` is frozen once published into a `Storage` cache (MUST-3).** `FloatTensor` holds a
`private volatile boolean frozen` flipped exactly once by `freeze()`. Every one of its own mutator methods
(`set`, `writeFrom`, `dataUnsafe`, `addFrom`, `copyFrom`) calls `checkMutable()` first and throws
`IllegalStateException` once frozen, while every reader (`get`, `readInto`, `entryAt`, `copyRange`) stays
callable regardless of frozen state — so a tensor published into a cache can be read concurrently by many
threads with no per-read copy and no risk of a reader observing an in-progress mutation.

`FloatTensor` itself never calls `freeze()` on itself; the only caller is `storage/Storage`, at the exact
point a payload transitions from "just produced" to "published for other threads to read" — see
`storage/README.md`. From inside `infinitetensor/`, a tensor obtained from `InfiniteTensor.getSlice`'s
internal cache reads (`storage.getEntry`) must be treated as already frozen and read-only; construct a
fresh `FloatTensor` (e.g. via `slice`/`copyRange`, both of which allocate) if a mutable copy is needed.

**The freeze guard is not a hard immutability boundary.** `FloatTensor.shape` is `private final`, but
`data` is declared `public final float[]` — the array reference cannot be reassigned, but a caller with a
reference to the tensor can still write `tensor.data[i] = x` directly, bypassing `freeze()`/`checkMutable()`
entirely. The freeze only guards mutation routed through `FloatTensor`'s own methods; it is a convention
enforced by API discipline, not something the compiler or the frozen flag can prevent. Treat any code that
reaches into `.data` on a tensor obtained from a cache as a bug regardless of whether `isFrozen()` would
have caught it.

**A `NonIntersectingInfiniteTensor`'s byte budget is enforced in `loadInto`, not in `getSlice`.**
`loadInto` is the only path by which one of these inserts into `Storage`'s accounting — the disk-hit
branch inside `super.loadInto` and the recompute branch both reach `recordCachedEntry` — so evicting
there bounds the cache no matter what the reader called. A `getSlice`-side budget would leave every
`getValue`-only reader unbounded, and for `final_biome_tiles` that is the dominant path, since vanilla's
`Climate.Sampler.sample` calls `compute(FunctionContext)`. Eviction must run *after* the promise is
settled: evicting first drops the in-flight promise from `CACHE` and lets a racing reader start a
duplicate compute. `NonIntersectingSpatialIndex.loadInto` states the same ordering constraint.

**The 8-tile budgets on `final_relief_tiles` and `final_biome_tiles` are margin, not a fit.**
`Storage.recordCachedEntry` runs only on insert, never on read, so its accounting is insertion-ordered
(FIFO), not LRU — a tile read on every chunk is still evicted once eight newer ones land. A 512-px tile
is 2560 blocks, or 160 chunks per axis, so an entire render distance sits inside one to four tiles; the
gap between four and eight is what makes the FIFO ordering safe. Lowering either budget is what breaks
first. The other three tensors (`global_river` 65 KB, `dog_tensor` 16 KB, `hydrology_relief` 1.05 MB)
stay unbounded because none is large enough to matter.

**A slice returned by `getSlice` is unfrozen and safe to read through `dataUnsafe()`.** It is allocated
fresh per call and never published to a cache, so it is outside the freeze invariant above — the one
place reaching into a tensor's backing array is not the bug this README otherwise says it is. See
`storage/ChunkChannelFill`, which carries the matching `:PERF:` marker.

**Iteration buffers passed to `WindowConsumer`/`iterateWindows` are mutable and reused.** The `int[]`
handed to a window-iteration callback is a single buffer mutated across iterations; a consumer that needs
the value to outlive the callback must copy it (e.g. via `TileKey` or `clone()`), not retain the reference.
