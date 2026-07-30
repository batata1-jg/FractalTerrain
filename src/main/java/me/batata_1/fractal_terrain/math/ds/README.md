# ds/

## Overview

Three spatial-index implementations serve two different access patterns: `QuadTree` is a mutable,
long-lived index that supports insert/remove during use; `ImmutableQuadTree` and `ImmutableRTree` are
build-once, query-many indexes constructed fresh from a fully-known point/shape set on every per-tile
hydrology build. `ImmutableQuadTree` backs cached per-tile point indexes; `ImmutableRTree` backs the
`HydrologicalUnit` index.

## Architecture

`QuadTree` guards its node list with a `ReentrantReadWriteLock`: concurrent reads (`getPointsInBox`,
`getPointsInCircle`, `containsPoint`, …) take the read lock, mutations (`insertPoint`, `removePoint`,
`clear`) take the exclusive write lock. Recursion-local state stays on the call stack, so readers never
interfere with each other.

`ImmutableQuadTree` and `ImmutableRTree` take the opposite approach: the full point/shape set is
supplied once at construction, the tree is packed (euler-tour sort for the quadtree, Sort-Tile-Recursive
bulk load for the R-tree), and there is no insert/remove path afterward. Because nothing ever mutates
post-construction, both are safe to share across threads with no lock at all.

## Invariants

- **`QuadTree`'s read/write lock guards query concurrency, not construction sharing.** The lock lets
  multiple threads query one already-built tree concurrently, or lets one writer mutate it safely
  between reads. It does **not** make it safe for multiple threads to build (i.e. concurrently call
  `insertPoint`) into the same `QuadTree` instance as part of constructing it — per-tile hydrology
  callers (`GlobalNetworkBuilder`, `LocalDrainageTracer` and the `RiverNetwork` they build into) build
  and mutate one `QuadTree`/`RiverNetwork` per tile on a single thread; the lock's job is to protect that
  finished tree against concurrent readers afterward, not to parallelize the build itself.
- **`ImmutableQuadTree`/`ImmutableRTree` are naturally concurrent because they are frozen.** There is no
  mutation API post-construction, so no lock is needed for concurrent queries. Do not add an
  insert/remove path to either class without re-adding the locking `QuadTree` relies on — the "no lock
  needed" property depends entirely on the structure staying frozen after construction.
