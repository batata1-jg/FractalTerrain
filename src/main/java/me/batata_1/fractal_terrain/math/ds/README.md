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

## Known Issue: `ImmutableQuadTree` root-square alignment

`findSection` assigns quadrants against an infinite tiling anchored at the origin:
`floorMod(floor(coord / m), 2)` per axis. That rule is only consistent with recursive bisection if the
root square is itself aligned to that tiling.

It is not. The constructor sizes the root square from the point bounding box — lower corner minus a
fixed 5-unit margin, side `max(width, height) + 10` — which lands at an arbitrary origin. The loop that
used to snap the root to a power-of-two-aligned cell is commented out in the constructor.

The consequence is that the build's quadrant sort and the query descent can disagree, and points are
silently dropped from query results rather than an error being raised. `validate()` detects it: its
point-inside-its-leaf-square check is what fails.

Do not write a golden test capturing current output — that would freeze the broken behaviour. Known
bugs get a `@Disabled` contract test asserting the intended behaviour instead.

Two constructor comments still describe the disabled loop as if it ran (the non-finite-input rejection
explains itself in terms of "poisons the alignment loop"). The input check is still worth keeping; only
its stated rationale is stale.

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
