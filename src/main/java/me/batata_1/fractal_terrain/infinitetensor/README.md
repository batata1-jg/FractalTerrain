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
`InfiniteTensor` produces and every `Storage<FloatTensor>` caches.

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

**Iteration buffers passed to `WindowConsumer`/`iterateWindows` are mutable and reused.** The `int[]`
handed to a window-iteration callback is a single buffer mutated across iterations; a consumer that needs
the value to outlive the callback must copy it (e.g. via `TileKey` or `clone()`), not retain the reference.
