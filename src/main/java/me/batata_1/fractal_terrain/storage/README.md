# storage/

## Overview

`Storage<T extends Persistable<T>>` is a lazily-populated tile cache keyed by integer coordinate tuples
(`TileKey`), with optional disk persistence. It backs every `infinitetensor/InfiniteTensor` and the
heightmap cache. A `Storage` is either disk-backed (payload's `serialize`/`deserialize` work, and a
non-null path was given) or cache-only (evicted entries are lost entirely) — decided once in the
constructor and never revisited.

## Architecture

Two independent paths share the cache map (`CACHE: ConcurrentHashMap<TileKey, CompletableFuture<T>>`):

- **Load path** (`fetchEntry` → `loadInto`): reads a persisted tile from disk. `NonIntersectingInfiniteTensor`
  and `NonIntersectingSpatialIndex` override `loadInto` to catch `EntryNotLoadableException` (cache-only
  storage, unpersisted key, missing file, or deserialization failure) and recompute the entry synchronously
  instead of failing. Both also enforce their byte budget there, because `loadInto` is their only insert
  path — see `infinitetensor/README.md`.
- **Compute path** (`getOrCompute` / `claimForCompute` + `fulfillClaim`/`abandonClaim`): produces a tile
  freshly, used by `InfiniteTensor.computeSingle`/`computeBatched`.

Both paths converge on `persistAndRecord` (compute) or the freeze-and-complete block inside `loadInto`
(load) as the single point where a payload is published for other threads to observe.

## Design Decisions

**Reads are lock-free; only eviction bookkeeping is locked.** `getEntry` on a cache hit never blocks on
any lock — `CACHE` is a `ConcurrentHashMap` and hits return directly. The only lock, `evictionLock`, guards
`cachedEntryByteSizes`/`totalCachedBytes` (the byte-budget bookkeeping) and is never acquired by a
reader; only `recordCachedEntry`, `pollOldest`, and `evictIfNeeded` touch it. This keeps the tile-read hot
path (shared across many worker threads generating a chunk) contention-free.

**The byte budget is insertion-ordered, not LRU.** `cachedEntryByteSizes` is a plain `LinkedHashMap` (not
access-ordered), and `recordCachedEntry` — the only thing that reorders it — runs solely from the two
insert points, never from `getEntry`. A tile read on every single chunk is therefore still evicted once
enough newer tiles land ahead of it. Any budget must be sized as headroom over the working set rather than
fitted to it; `infinitetensor/README.md` records why the two large tensors use eight tiles.

**Single-flight via `putIfAbsent`, not `computeIfAbsent`.** Both `fetchEntry` (load) and `claimForCompute`
(compute) use a plain `CACHE.get` followed by `CACHE.putIfAbsent` to install a fresh promise, deliberately
avoiding `ConcurrentHashMap.computeIfAbsent` — `computeIfAbsent` would hold a bin lock for the entire
duration of the mapping function, and the mapping function here can be an expensive disk read or ONNX
inference call. `putIfAbsent`'s winner populates/settles the promise *after* the map operation returns, so
no bin lock is ever held across a slow load or compute; losers of the race simply await the winner's
`CompletableFuture`.

**`claimForCompute`/`fulfillClaim`/`abandonClaim` is a public claim API, not just an internal helper.**
`InfiniteTensor`'s batched compute (`InfiniteTensor.java:221-261`) depends on this API when a batch
function computes several windows at once: it claims every window's key up front, so a second thread
requesting one of those windows blocks on the batch's own promise instead of racing to recompute it, then
settles each claim as the batch result for that window becomes available. Do not bypass `claimForCompute`
with a check-then-act pattern (e.g. `if (!inStorage(key)) compute()`) — only the atomic `putIfAbsent`
inside it guarantees a key is computed at most once when many worker threads race for overlapping slices.

**A chunk reads a tile channel as one window, not as 1024 point lookups.** `ChunkChannelFill` exists
because `GLOBAL_SCALE_CORRECTION` is 5, so a 16-block chunk spans 3.2 tensor pixels and a channel's whole
working set for that chunk is a 4x4 or 5x5 window. Its window bounds are `floor(minPx)` to `ceil(maxPx)`,
never `floor` to `floor + 1`: on a coordinate that lands exactly on a pixel the two corners coincide, and
reading one pixel further would cross a 512-px tile boundary and materialise a whole neighbouring tile —
an ONNX inference, for relief — to supply a value multiplied by zero. `open` takes the pixel scale as a
parameter rather than assuming `GLOBAL_SCALE_CORRECTION`, because the biome densities convert with
`scale * GLOBAL_SCALE_CORRECTION`; passing a scale that disagrees with the one the caller samples at
would size the window to miss the corners the sampler reads.

## Invariants

**Entries are frozen before publication (MUST-3).** `persistAndRecord` and the disk-reload branch inside
`loadInto` are the only two places a payload transitions from "just produced" to "published for other
threads to read," and both call `entry.freeze()` as their first action — before the entry is ever inserted
into `CACHE` via `CompletableFuture.complete`. The freeze write happens-before publication because both run
on the same thread, before the key becomes observably complete to any other (awaiting or racing) thread —
so no thread can ever see an unfrozen payload through the cache. See `infinitetensor/README.md` for what
"frozen" guards (and does not guard) on the `FloatTensor` payload type itself.

- Never add a third path that publishes an entry into `CACHE` without freezing first — both existing
  publication points (`persistAndRecord`, `loadInto`'s success branch) do; a new one must too.
- Never touch `evictionLock` from a read path; it exists solely to serialize eviction bookkeeping mutations
  against each other and against `evictIfNeeded`.
- `evictIfNeeded` purges `GENERATED_ENTRIES` before `CACHE` for cache-only payloads specifically so a
  racing reader never observes a key that `inStorage()` claims exists but that has nowhere to be fetched
  from — do not reorder those two removals.
