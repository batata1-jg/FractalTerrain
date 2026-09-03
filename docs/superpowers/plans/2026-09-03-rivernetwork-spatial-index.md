# RiverNetwork's Mutable QuadTree Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `RiverNetwork`'s shared mutable `QuadTree` in favor of two purpose-fit indexes:
`detectCrossings` moves to a per-call `ImmutableRTree` (build-once, query-many, no lock), and
`manageCutoffs` moves to a new mutable `SpatialHashGrid` (live insert/remove, no lock, exact circle
test), leaving `QuadTree` itself untouched for its other caller (`LocalDrainageTracer`).

**Architecture:** `SpatialHashGrid<T extends SpatialIndexPoint>` is a new `math/ds/` class: points
bucketed by `floor(coord / cellSize)` into a `Long2ObjectOpenHashMap` of per-cell lists, with an exact
squared-distance test on every circle-query candidate (the bucket scan is a superset, same as
`QuadTree`'s box-then-exact pattern). `RiverNetwork.detectCrossings` builds a private
`ImmutableRTree<CrossingPoint>` (a zero-radius `SpatialIndexCircle` adapter) fresh each call instead of
clearing/reinserting into the shared field. `RiverNetwork.manageCutoffs`'s field changes type from
`QuadTree<Channel.ChannelPt>` to `SpatialHashGrid<Channel.ChannelPt>`; its own body is otherwise
untouched. `SpatialIndexBenchmark` gets a correctness cross-check (throws on mismatch) plus a
queries/sec or op-mix throughput comparison for each site, over one real tile's channel set.

**Tech Stack:** Java 21, Fabric/Minecraft 1.20.1, JUnit 5 (`gradle test`), fastutil collections, Gradle
`JavaExec` debug harnesses.

**Spec:** `docs/superpowers/specs/2026-09-03-rivernetwork-spatial-index-design.md`

## Global Constraints

- No lock on `SpatialHashGrid` — per `math/ds/README.md`'s invariant, every per-tile hydrology caller
  builds and mutates one spatial index per tile on a single thread, so a lock would only buy uncontended
  overhead. Do not add one, and do not add an insert/remove path to `ImmutableQuadTree`/`ImmutableRTree`
  without re-adding the locking this invariant currently lets them skip.
- `QuadTree` itself is not touched, renamed, or removed — `LocalDrainageTracer.java:78-79` still
  constructs its own independently.
- Collections: fastutil over `java.util` (`Long2ObjectOpenHashMap`, `ObjectArrayList`), per
  `.claude/conventions/performance.md`.
- New-class member ordering: constructors/public API surface first, then fields, then private methods,
  per `.claude/conventions/class-structure.md`.
- Docstrings: class docstrings ≤10 lines, method docstrings ≤3 lines, field docstrings ≤1 line, and
  every line beyond the first states *why*/*where in the pipeline*, never *how* — per
  `.claude/conventions/documentation.md` Tier 3.
- Every task that touches Java runs `gradle spotlessApply` then `gradle build`. Every task that touches
  `hydrology/` also runs `gradle test`, comparing failure *messages* in `build/test-results/test/*.xml`
  (not just which test names fail) against the baseline recorded in root `CLAUDE.md` — **102 tests, 9
  failed, 1 skipped at `df7ca2e`**, itself a claim to re-verify, not a fact. A worktree needs
  `libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports ~132 phantom
  errors.
- Every commit message ends with:
  ```
  Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01Vg7YgZoRKXCcrpk2Eq2ReM
  ```

---

## File Structure

| File | Change |
| ---- | ------ |
| `src/main/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGrid.java` | New. Mutable bucketed point index (D2). |
| `src/test/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGridTest.java` | New. Unit gate for insert/remove/clear/containsPoint/getPointsInCircle, including the exact-circle-vs-bucket correctness case. |
| `src/test/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md` | New. Index for the new test directory. |
| `src/test/java/me/batata_1/fractal_terrain/math/CLAUDE.md` | Modified. Adds a Subdirectories row for `ds/`. |
| `src/main/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md` | Modified. Adds a Files row for `SpatialHashGrid.java`. |
| `src/main/java/me/batata_1/fractal_terrain/math/ds/README.md` | Modified. Extends the "three implementations" overview to four; documents `SpatialHashGrid`'s bucket/exact-test design, no-lock rationale, and cell-size choice. |
| `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java` | Modified. `detectCrossings` moves to `ImmutableRTree` (D1); `quadTree` field, `manageCutoffs`/`getPtsCloseTo`/`insertChannelInQuadTree`/`cutRiverSection`/`beginStep`/`update` move to `SpatialHashGrid` (D2). |
| `src/main/java/me/batata_1/fractal_terrain/debug/tests/SpatialIndexBenchmark.java` | Modified. Adds a correctness cross-check + throughput comparison for each of D1 and D2, over one real tile's channel set. |
| `src/main/java/me/batata_1/fractal_terrain/debug/tests/CLAUDE.md` | Modified. Updates `SpatialIndexBenchmark.java`'s "What" description. |

Out of scope (per spec): `QuadTree` itself, `ImmutableQuadTree`'s `findSection` alignment bug,
`AtomicView.resolveCrossingEdges`/`crossingCandidatePairs`, and any change to `channelsOverlap`/
`segmentCrossing`.

---

### Task 1: `SpatialHashGrid` — new mutable bucketed point index

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGrid.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGridTest.java`
- Create: `src/test/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md`
- Modify: `src/test/java/me/batata_1/fractal_terrain/math/CLAUDE.md`

**Guidelines:** `.claude/conventions/CLAUDE.md`, `.claude/conventions/performance.md`,
`.claude/conventions/class-structure.md`, `.claude/conventions/documentation.md`,
`src/main/java/me/batata_1/fractal_terrain/math/ds/README.md` (existing three-index design, the
`QuadTree`/`ImmutableRTree` shape this class mirrors).

**Interfaces:**
- Consumes: `SpatialIndexPoint` (`get(int axis)`, `getCoords()`), `SpatialIndex<T>` (`numEntries()`,
  `getAllEntries()`, `SpatialIndex.requirePlanar(SpatialIndexPoint, String)`,
  `SpatialIndex.requirePlanar(double[], String)`) — both already exist in `math/ds/`.
- Produces: `SpatialHashGrid<T extends SpatialIndexPoint>` with public API `SpatialHashGrid(double
  cellSize)`, `void insertPoint(T pt)`, `void removePoint(T pt)`, `void clear()`, `boolean
  containsPoint(T pt)`, `List<T> getPointsInCircle(double[] center, double radius)`, plus the
  `SpatialIndex<T>` methods `int numEntries()` and `List<T> getAllEntries()`. Task 3 and Task 4 (D1/D2)
  and Task 5 (benchmark) construct and call this type.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGridTest.java`:

```java
package me.batata_1.fractal_terrain.math.ds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpatialHashGridTest {

    private record TestPoint(double[] coords) implements SpatialIndexPoint {
        @Override
        public double[] getCoords() {
            return coords;
        }
    }

    @Test
    void getPointsInCircleFindsAnInsertedPointWithinRadius() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        TestPoint pt = new TestPoint(new double[] {1.0, 1.0});
        grid.insertPoint(pt);

        List<TestPoint> hits = grid.getPointsInCircle(new double[] {0.0, 0.0}, 2.0);

        assertEquals(1, hits.size());
        assertEquals(pt, hits.getFirst());
    }

    /** The correctness detail the design spec calls out: a candidate sharing the query's scanned cell
     *  must still be rejected by the exact distance test if it sits outside the true circle. */
    @Test
    void getPointsInCircleExcludesAPointInsideTheCoveringCellButOutsideTheCircle() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        // cellSize=4: a query at (0,0) r=1 covers cells with index in [-1,0] on each axis, which
        // includes cell (0,0) spanning world [0,4)x[0,4). A point at (3,3) lives in that scanned cell
        // but is far outside r=1 — a bucket-only match (no exact test) would wrongly include it.
        TestPoint farCorner = new TestPoint(new double[] {3.0, 3.0});
        grid.insertPoint(farCorner);

        List<TestPoint> hits = grid.getPointsInCircle(new double[] {0.0, 0.0}, 1.0);

        assertTrue(hits.isEmpty(), "point in the covering cell but outside the circle must be excluded");
    }

    @Test
    void removePointDropsItFromSubsequentQueries() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        TestPoint pt = new TestPoint(new double[] {1.0, 1.0});
        grid.insertPoint(pt);
        grid.removePoint(pt);

        assertFalse(grid.containsPoint(pt));
        assertTrue(grid.getPointsInCircle(new double[] {0.0, 0.0}, 5.0).isEmpty());
        assertEquals(0, grid.numEntries());
    }

    @Test
    void clearEmptiesTheGrid() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        grid.insertPoint(new TestPoint(new double[] {1.0, 1.0}));
        grid.insertPoint(new TestPoint(new double[] {-5.0, 9.0}));

        grid.clear();

        assertEquals(0, grid.numEntries());
        assertTrue(grid.getAllEntries().isEmpty());
    }

    @Test
    void containsPointReflectsInsertAndRemoveAcrossNegativeCoordinates() {
        SpatialHashGrid<TestPoint> grid = new SpatialHashGrid<>(4.0);
        TestPoint pt = new TestPoint(new double[] {-6.5, -2.5});
        assertFalse(grid.containsPoint(pt));

        grid.insertPoint(pt);
        assertTrue(grid.containsPoint(pt));

        grid.removePoint(pt);
        assertFalse(grid.containsPoint(pt));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "me.batata_1.fractal_terrain.math.ds.SpatialHashGridTest"`
Expected: compile failure — `cannot find symbol: class SpatialHashGrid`.

- [ ] **Step 3: Implement `SpatialHashGrid`**

Create `src/main/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGrid.java`:

```java
package me.batata_1.fractal_terrain.math.ds;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

/**
 * Mutable, bucketed point index for live insert/remove interleaved with circle queries — the one access
 * pattern neither immutable index in this package covers, since {@link ImmutableQuadTree}/
 * {@link ImmutableRTree} have no mutation path by design (see {@code README.md}).
 *
 * <p>Exists for {@code RiverNetwork.manageCutoffs}'s cut-and-continue walk, which removes points
 * mid-scan; rebuilding a whole immutable tree after every cut would cost far more than the bulk load it
 * amortizes elsewhere. No lock: per {@code README.md}'s invariant, every caller builds and mutates one
 * index per tile on a single thread, so {@link QuadTree}'s {@code ReentrantReadWriteLock} would only buy
 * uncontended overhead here.
 */
public final class SpatialHashGrid<T extends SpatialIndexPoint> implements SpatialIndex<T> {

    /** Cells are {@code cellSize} wide on each axis; size it to the caller's own query radius. */
    public SpatialHashGrid(double cellSize) {
        if (!(cellSize > 0)) throw new IllegalArgumentException("cellSize must be > 0: " + cellSize);
        this.cellSize = cellSize;
    }

    public void insertPoint(T pt) {
        SpatialIndex.requirePlanar(pt, "point");
        final long key = cellKey(pt.get(X), pt.get(Z));
        ObjectArrayList<T> bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new ObjectArrayList<>();
            buckets.put(key, bucket);
        }
        bucket.add(pt);
        size++;
    }

    public void removePoint(T pt) {
        SpatialIndex.requirePlanar(pt, "point");
        final long key = cellKey(pt.get(X), pt.get(Z));
        final ObjectArrayList<T> bucket = buckets.get(key);
        if (bucket != null && bucket.remove(pt)) {
            size--;
            if (bucket.isEmpty()) buckets.remove(key);
        }
    }

    public void clear() {
        buckets.clear();
        size = 0;
    }

    public boolean containsPoint(T pt) {
        final ObjectArrayList<T> bucket = buckets.get(cellKey(pt.get(X), pt.get(Z)));
        return bucket != null && bucket.contains(pt);
    }

    /** Exact circle query: the covering cells are a superset, so every candidate is re-tested against
     *  the true squared distance before being returned (see {@code README.md}'s correctness note). */
    public List<T> getPointsInCircle(double[] center, double radius) {
        SpatialIndex.requirePlanar(center, "center");
        final List<T> hits = new ObjectArrayList<>();
        final double radiusSq = radius * radius;
        final long minCellX = cellIndex(center[X] - radius);
        final long maxCellX = cellIndex(center[X] + radius);
        final long minCellZ = cellIndex(center[Z] - radius);
        final long maxCellZ = cellIndex(center[Z] + radius);
        for (long cx = minCellX; cx <= maxCellX; cx++) {
            for (long cz = minCellZ; cz <= maxCellZ; cz++) {
                final ObjectArrayList<T> bucket = buckets.get(packKey(cx, cz));
                if (bucket == null) continue;
                for (T pt : bucket) {
                    final double deltaX = pt.get(X) - center[X];
                    final double deltaZ = pt.get(Z) - center[Z];
                    if (deltaX * deltaX + deltaZ * deltaZ <= radiusSq) hits.add(pt);
                }
            }
        }
        return hits;
    }

    @Override
    public int numEntries() {
        return size;
    }

    @Override
    public List<T> getAllEntries() {
        final List<T> all = new ObjectArrayList<>(size);
        for (ObjectArrayList<T> bucket : buckets.values()) all.addAll(bucket);
        return all;
    }

    private static final int X = 0;
    private static final int Z = 1;

    private final double cellSize;
    private final Long2ObjectOpenHashMap<ObjectArrayList<T>> buckets = new Long2ObjectOpenHashMap<>();
    private int size = 0;

    private long cellIndex(double coord) {
        return (long) Math.floor(coord / cellSize);
    }

    private long cellKey(double x, double z) {
        return packKey(cellIndex(x), cellIndex(z));
    }

    /** Packs two cell indices into one lookup key; a true bijection as long as both stay within
     *  {@code int} range, which every real caller's coordinate/cellSize ratio does. */
    private static long packKey(long cx, long cz) {
        return (cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "me.batata_1.fractal_terrain.math.ds.SpatialHashGridTest"`
Expected: PASS, 5 tests green.

- [ ] **Step 5: Add the test-directory index**

Create `src/test/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md`:

```markdown
# ds/ (test)

## Files

| File                       | What                                                                                                          | When to read                                                               |
| -------------------------- | --------------------------------------------------------------------------------------------------------------| ----------------------------------------------------------------------------|
| `SpatialHashGridTest.java` | Insert/remove/clear/containsPoint/getPointsInCircle, including the exact-circle-vs-covering-cell boundary case | Changing the bucket layout, the exact distance test, or cell-size handling |
```

Edit `src/test/java/me/batata_1/fractal_terrain/math/CLAUDE.md` — add a Subdirectories section at the
end of the file:

```markdown

## Subdirectories

| Directory | What                                     | When to read                              |
| --------- | ----------------------------------------- | ------------------------------------------ |
| `ds/`     | Spatial-index data structure unit tests   | Changing a `math/ds/` index implementation |
```

- [ ] **Step 6: Verify the whole build**

Run: `gradle spotlessApply` then `gradle build`.
Expected: both succeed with no formatting diffs left uncommitted.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGrid.java \
        src/test/java/me/batata_1/fractal_terrain/math/ds/SpatialHashGridTest.java \
        src/test/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md \
        src/test/java/me/batata_1/fractal_terrain/math/CLAUDE.md
git commit -m "$(cat <<'EOF'
feat(math/ds): add SpatialHashGrid, a mutable bucketed point index

Bridges the gap between QuadTree (mutable, but locked for a concurrency
guarantee no per-tile caller needs) and the immutable indexes (no
mutation path at all): live insert/remove with an exact circle test,
no lock, for RiverNetwork.manageCutoffs's cut-and-continue walk.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Vg7YgZoRKXCcrpk2Eq2ReM
EOF
)"
```

---

### Task 2: Document `SpatialHashGrid` in `math/ds/`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/math/ds/README.md`

**Guidelines:** `.claude/conventions/documentation.md` (CLAUDE.md = pure index, README.md = invisible
knowledge), the current `README.md` (read in full already — this task edits it in place).

**Interfaces:**
- Consumes: `SpatialHashGrid` from Task 1 (already merged).
- Produces: nothing code-facing; Task 4 references "see `math/ds/README.md`" in its own doc comments,
  so this task must land first or the reference is momentarily dangling (harmless, but land in order).

- [ ] **Step 1: Add the `CLAUDE.md` index row**

Edit `src/main/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md`. Current file:

```markdown
# ds/

Spatial-index data structures: a mutable `QuadTree` and immutable, build-once `ImmutableQuadTree`/`ImmutableRTree` variants.

## Files

| File                          | What                                                        | When to read                                    |
| ----------------------------- | ---------------------------------------------------------- | ----------------------------------------------- |
| `README.md` | Index-choice rationale, the `ImmutableQuadTree` alignment bug, locking invariants | Choosing an index, debugging dropped points, before adding a mutation path |
| `SpatialIndex.java`           | Query interface over indexed shapes                        | Consuming a spatial index                       |
| `QuadTree.java`               | Mutable quadtree with read/write lock                      | Building a quadtree, concurrent-read contract   |
| `ImmutableQuadTree.java`      | Immutable, frozen quadtree                                 | Cached per-tile point indexes                   |
| `ImmutableRTree.java`         | Immutable R-tree (backs the `HydrologicalPrimitive` index)      | Rectangle/shape range queries per tile          |
| `SpatialIndexShape.java`      | Base shape type indexed by the structures                  | Adding a shape type                             |
| `SpatialIndexPoint.java`      | Point shape                                                | Point queries                                   |
| `SpatialIndexCircle.java`     | Circle shape                                               | Radius queries                                  |
| `SpatialIndexRectangle.java`  | Axis-aligned rectangle shape                               | Bounding-box queries                            |
| `SpatialIndexRotatedRectangle.java` | Oriented rectangle shape (center/angle/length/width) | Queries by a footprint aligned to a bearing     |
| `CoordPoint.java`             | Integer coordinate record                                 | Coordinate keys in indexes                       |
```

Replace the top sentence and insert a new row directly after `QuadTree.java`:

```markdown
# ds/

Spatial-index data structures: mutable `QuadTree`/`SpatialHashGrid` and immutable, build-once
`ImmutableQuadTree`/`ImmutableRTree` variants.

## Files

| File                          | What                                                        | When to read                                    |
| ----------------------------- | ---------------------------------------------------------- | ----------------------------------------------- |
| `README.md` | Index-choice rationale, the `ImmutableQuadTree` alignment bug, locking invariants | Choosing an index, debugging dropped points, before adding a mutation path |
| `SpatialIndex.java`           | Query interface over indexed shapes                        | Consuming a spatial index                       |
| `QuadTree.java`               | Mutable quadtree with read/write lock                      | Building a quadtree, concurrent-read contract   |
| `SpatialHashGrid.java`        | Mutable bucketed point index, no lock                      | Live insert/remove interleaved with circle queries |
| `ImmutableQuadTree.java`      | Immutable, frozen quadtree                                 | Cached per-tile point indexes                   |
| `ImmutableRTree.java`         | Immutable R-tree (backs the `HydrologicalPrimitive` index)      | Rectangle/shape range queries per tile          |
| `SpatialIndexShape.java`      | Base shape type indexed by the structures                  | Adding a shape type                             |
| `SpatialIndexPoint.java`      | Point shape                                                | Point queries                                   |
| `SpatialIndexCircle.java`     | Circle shape                                               | Radius queries                                  |
| `SpatialIndexRectangle.java`  | Axis-aligned rectangle shape                               | Bounding-box queries                            |
| `SpatialIndexRotatedRectangle.java` | Oriented rectangle shape (center/angle/length/width) | Queries by a footprint aligned to a bearing     |
| `CoordPoint.java`             | Integer coordinate record                                 | Coordinate keys in indexes                       |
```

- [ ] **Step 2: Update `README.md`'s Overview**

Edit `src/main/java/me/batata_1/fractal_terrain/math/ds/README.md`. Replace:

```markdown
Three spatial-index implementations serve two different access patterns: `QuadTree` is a mutable,
long-lived index that supports insert/remove during use; `ImmutableQuadTree` and `ImmutableRTree` are
build-once, query-many indexes constructed fresh from a fully-known point/shape set on every per-tile
hydrology build. `ImmutableQuadTree` backs cached per-tile point indexes; `ImmutableRTree` backs the
`HydrologicalPrimitive` index.
```

with:

```markdown
Four spatial-index implementations serve two different access patterns: `QuadTree` and
`SpatialHashGrid` are mutable, long-lived indexes that support insert/remove during use;
`ImmutableQuadTree` and `ImmutableRTree` are build-once, query-many indexes constructed fresh from a
fully-known point/shape set on every per-tile hydrology build. `ImmutableQuadTree` backs cached per-tile
point indexes; `ImmutableRTree` backs the `HydrologicalPrimitive` index and `RiverNetwork`'s
crossing-detection candidate search; `SpatialHashGrid` backs `RiverNetwork`'s cutoff search, which needs
live removal interleaved with queries — the one access pattern neither immutable index supports.
```

- [ ] **Step 3: Add an Architecture paragraph**

In the same file, after the existing `ImmutableQuadTree`/`ImmutableRTree` paragraph (the one ending
"...both are safe to share across threads with no lock at all."), add:

```markdown

`SpatialHashGrid` buckets points by `floor(coord / cellSize)` into a `Long2ObjectOpenHashMap` of
per-cell lists. `insertPoint`/`removePoint` touch only the point's own bucket; `getPointsInCircle` scans
the query circle's covering cells and applies the same exact squared-distance test
`SpatialIndexCircle.containsPoint` does, rather than returning the bucket scan's superset. Like
`QuadTree` it supports live mutation; unlike `QuadTree` it carries no lock (see Invariants). Cell size is
a constructor parameter, not a hardcoded constant — the class is otherwise general-purpose, and a
different caller may have a different natural query scale. `RiverNetwork` sizes it to
`ceil(sqrt(HydrologyTuning.maxNativeWidth()))`, keeping expected cell occupancy near one query's worth of
area across the tuning range, since its own cutoff query radius is `sqrt(width)`.
```

- [ ] **Step 4: Add an Invariants bullet**

In the same file's `## Invariants` section, after the existing `ImmutableQuadTree`/`ImmutableRTree`
bullet ("...no lock needed" property depends entirely on the structure staying frozen after
construction."), add:

```markdown
- **`SpatialHashGrid` carries no lock, unlike `QuadTree`.** Both rely on the same single-writer-per-tile
  invariant above; `QuadTree` pays its `ReentrantReadWriteLock` on every `insertPoint`/`removePoint`/
  `clear` call regardless, uncontended overhead under that invariant. `SpatialHashGrid` was added after
  this was understood, so it simply does not pay for a guarantee nothing needs.
```

- [ ] **Step 5: Verify and commit**

Run: `gradle spotlessApply` then `gradle build` (Markdown is unaffected by Spotless, but this confirms
nothing else broke).

```bash
git add src/main/java/me/batata_1/fractal_terrain/math/ds/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/math/ds/README.md
git commit -m "$(cat <<'EOF'
docs(math/ds): document SpatialHashGrid's design and no-lock rationale

Extends the "three spatial-index implementations" overview to four,
ahead of RiverNetwork wiring it in.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Vg7YgZoRKXCcrpk2Eq2ReM
EOF
)"
```

---

### Task 3: D1 — `detectCrossings` moves to `ImmutableRTree`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java`

**Guidelines:** `.claude/conventions/CLAUDE.md`, `.claude/conventions/documentation.md`,
`src/main/java/me/batata_1/fractal_terrain/hydrology/network/README.md` (canonical/atomic seam,
stream-capture invariants this method feeds), `src/main/java/me/batata_1/fractal_terrain/math/ds/README.md`.

**Interfaces:**
- Consumes: `ImmutableRTree<T extends SpatialIndexShape>` (`queryContaining(double[] queryPoint, double
  inflateRadius, List<T> out)`), `SpatialIndexCircle` (`getCenter()`, `getRadius()`) — both already
  exist. `Channel.getChannelAsPts()` (existing, returns `Channel.ChannelPt[]`).
- Produces: no public signature changes. The private `quadTree` field stays `QuadTree<Channel.ChannelPt>`
  in this task — Task 4 retypes it. `detectCrossings`'s return type (`List<int[]>`) and behavior are
  unchanged; only its candidate-generation internals move.

This task does **not** touch the `quadTree` field, `beginStep`, `getPtsCloseTo`, `manageCutoffs`,
`insertChannelInQuadTree`, `cutRiverSection`, or `update`'s reset block — those still read/write the
field and are Task 4's job. After this task, the field is written by `update`/`emitChannel`/
`manageCutoffs` exactly as before but never read by `detectCrossings`.

- [ ] **Step 1: Update imports**

In `RiverNetwork.java`, replace:

```java
import me.batata_1.fractal_terrain.math.ds.QuadTree;
```

with:

```java
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
```

- [ ] **Step 2: Add the `CrossingPoint` adapter and rewrite `detectCrossings`**

Replace the existing `detectCrossings` method (currently `RiverNetwork.java:587-633`):

```java
    /** Finds one crossing edge per overlapping channel pair (closest overlapping points, tested via
     *  {@link ChannelGeometry#channelsOverlap}), feeding the collision/orientation pass below. */
    private List<int[]> detectCrossings(AtomicView atomic) {
        quadTree.clear();
        final List<Integer> channelIds = new ObjectArrayList<>(channels.keySet());
        Collections.sort(channelIds);
        for (int channelId : channelIds) insertChannelInQuadTree(channels.get(channelId));

        double maxHalf = 0.0;
        for (int channelId : channelIds) {
            final Channel c = channels.get(channelId);
            for (int i = 0; i < c.numPts(); i++)
                maxHalf = Math.max(maxHalf, ChannelGeometry.bedHalfWidth(c.widthAt(i)));
        }

        final List<int[]> edges = new ObjectArrayList<>();
        for (int channelAId : channelIds) {
            final Channel channelA = channels.get(channelAId);
            final int[] aAtomic = atomic.pointAtomicIds.get(channelAId);
            final Int2ObjectOpenHashMap<double[]> bestByPartner =
                    new Int2ObjectOpenHashMap<>(); // partnerId -> {atomA, atomB, dist}
            for (int posA = 0; posA < channelA.numPts(); posA++) {
                final double[] pointA = channelA.spline.points().get(posA);
                final double halfA = ChannelGeometry.bedHalfWidth(channelA.widthAt(posA));
                final List<Channel.ChannelPt> nearby = quadTree.getPointsInCircle(pointA, halfA + maxHalf);
                nearby.sort(null);
                for (Channel.ChannelPt np : nearby) {
                    final int channelBId = np.channelId();
                    if (channelBId <= channelAId) continue;
                    final Channel channelB = channels.get(channelBId);
                    final int posB = np.index();
                    final double distance = VectorOps.distance(pointA, np.toArray());
                    if (!ChannelGeometry.channelsOverlap(distance, channelA.widthAt(posA), channelB.widthAt(posB)))
                        continue;
                    final int atomA = aAtomic[posA];
                    final int atomB = atomic.pointAtomicIds.get(channelBId)[posB];
                    if (atomA == atomB) continue; // shared confluence node
                    if (isTreeAdjacent(atomic, atomA, atomB)) continue; // already directly connected
                    final double[] cur = bestByPartner.get(channelBId);
                    if (cur == null || distance < cur[2])
                        bestByPartner.put(channelBId, new double[] {atomA, atomB, distance});
                }
            }
            for (double[] c : bestByPartner.values()) edges.add(new int[] {(int) c[0], (int) c[1]});
        }
        return edges;
    }
```

with:

```java
    /** Zero-radius adapter so a {@link Channel.ChannelPt} can be stored in an {@link ImmutableRTree} —
     *  its stabbing query reduces to a circle query exactly when every stored shape has zero radius (see
     *  {@code math/ds/README.md}). */
    private record CrossingPoint(Channel.ChannelPt pt) implements SpatialIndexCircle {
        @Override
        public double[] getCenter() {
            return pt.toArray();
        }

        @Override
        public double getRadius() {
            return 0.0;
        }
    }

    /** Finds one crossing edge per overlapping channel pair (closest overlapping points, tested via
     *  {@link ChannelGeometry#channelsOverlap}), feeding the collision/orientation pass below. */
    private List<int[]> detectCrossings(AtomicView atomic) {
        final List<Integer> channelIds = new ObjectArrayList<>(channels.keySet());
        Collections.sort(channelIds);

        final List<CrossingPoint> points = new ObjectArrayList<>();
        for (int channelId : channelIds) {
            for (Channel.ChannelPt pt : channels.get(channelId).getChannelAsPts()) points.add(new CrossingPoint(pt));
        }
        final ImmutableRTree<CrossingPoint> rtree = new ImmutableRTree<>(points, null); // never persisted

        double maxHalf = 0.0;
        for (int channelId : channelIds) {
            final Channel c = channels.get(channelId);
            for (int i = 0; i < c.numPts(); i++)
                maxHalf = Math.max(maxHalf, ChannelGeometry.bedHalfWidth(c.widthAt(i)));
        }

        final List<int[]> edges = new ObjectArrayList<>();
        final List<CrossingPoint> nearbyBuffer = new ObjectArrayList<>();
        for (int channelAId : channelIds) {
            final Channel channelA = channels.get(channelAId);
            final int[] aAtomic = atomic.pointAtomicIds.get(channelAId);
            final Int2ObjectOpenHashMap<double[]> bestByPartner =
                    new Int2ObjectOpenHashMap<>(); // partnerId -> {atomA, atomB, dist}
            for (int posA = 0; posA < channelA.numPts(); posA++) {
                final double[] pointA = channelA.spline.points().get(posA);
                final double halfA = ChannelGeometry.bedHalfWidth(channelA.widthAt(posA));
                nearbyBuffer.clear();
                rtree.queryContaining(pointA, halfA + maxHalf, nearbyBuffer);
                final List<Channel.ChannelPt> nearby = new ObjectArrayList<>(nearbyBuffer.size());
                for (CrossingPoint cp : nearbyBuffer) nearby.add(cp.pt());
                nearby.sort(null);
                for (Channel.ChannelPt np : nearby) {
                    final int channelBId = np.channelId();
                    if (channelBId <= channelAId) continue;
                    final Channel channelB = channels.get(channelBId);
                    final int posB = np.index();
                    final double distance = VectorOps.distance(pointA, np.toArray());
                    if (!ChannelGeometry.channelsOverlap(distance, channelA.widthAt(posA), channelB.widthAt(posB)))
                        continue;
                    final int atomA = aAtomic[posA];
                    final int atomB = atomic.pointAtomicIds.get(channelBId)[posB];
                    if (atomA == atomB) continue; // shared confluence node
                    if (isTreeAdjacent(atomic, atomA, atomB)) continue; // already directly connected
                    final double[] cur = bestByPartner.get(channelBId);
                    if (cur == null || distance < cur[2])
                        bestByPartner.put(channelBId, new double[] {atomA, atomB, distance});
                }
            }
            for (double[] c : bestByPartner.values()) edges.add(new int[] {(int) c[0], (int) c[1]});
        }
        return edges;
    }
```

Note: `nearby.sort(null)`, `channelsOverlap`, and `bestByPartner` are unchanged — only candidate
generation (building `nearby`) moved from a shared `QuadTree` to a per-call `ImmutableRTree`.

- [ ] **Step 2: Build and test**

Run: `gradle spotlessApply` then `gradle build`.
Then: `gradle test`, compare `build/test-results/test/*.xml` failure messages against the baseline in
root `CLAUDE.md`. Expected: identical to baseline (no new failures, no fixed failures) —
`RiverNetworkSeamGoldenTest`, `MeandersGoldenTest`, and `RiverGoldenTest` all exercise
`manageCollisions`/`detectCrossings`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java
git commit -m "$(cat <<'EOF'
refactor(hydrology): move detectCrossings to a per-call ImmutableRTree

detectCrossings clears and rebuilds the whole point set on every call
and never mutates mid-query — a build-once/query-many shape ImmutableRTree
already fits, without paying QuadTree's read/write lock for a guarantee
(concurrent readers of a finished tree) nothing here needs.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Vg7YgZoRKXCcrpk2Eq2ReM
EOF
)"
```

---

### Task 4: D2 — `manageCutoffs` moves to `SpatialHashGrid`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java`

**Guidelines:** same as Task 3, plus re-read `math/ds/README.md` as updated by Task 2 (cell-size
rationale this task's field initializer depends on).

**Interfaces:**
- Consumes: `SpatialHashGrid<T>` from Task 1 (`insertPoint`, `removePoint`, `clear`, `containsPoint`,
  `getPointsInCircle`), `HydrologyTuning.maxNativeWidth()` (existing).
- Produces: the `quadTree` field is fully removed; `spatialHashGrid` (type
  `SpatialHashGrid<Channel.ChannelPt>`) is the sole spatial index field left on `RiverNetwork`, read by
  `beginStep`, `getPtsCloseTo`, `manageCutoffs`, `insertChannelInQuadTree`, `cutRiverSection`, and
  written by `update`'s reset block. No public signature changes.

- [ ] **Step 1: Update imports**

Replace:

```java
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
```

with:

```java
import me.batata_1.fractal_terrain.math.ds.ImmutableRTree;
import me.batata_1.fractal_terrain.math.ds.SpatialHashGrid;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
```

- [ ] **Step 2: Retype the field, drop the now-dead `INF` constant**

Replace:

```java
    private static final double INF = 1e3;
    /** Floor on the resample spacing used when converting features to {@link HydrologicalPrimitive}s. */
    private static final double MIN_CONVERT_SPACING = 0.5;

    private static final Logger LOG = getLogger(RiverNetwork.class);

    private final int gridSize;
    private final QuadTree<Channel.ChannelPt> quadTree =
            new QuadTree<>(new double[] {-INF, -INF}, new double[] {INF, INF});
```

with:

```java
    /** Floor on the resample spacing used when converting features to {@link HydrologicalPrimitive}s. */
    private static final double MIN_CONVERT_SPACING = 0.5;

    /** {@code manageCutoffs}'s own query radius is {@code sqrt(width)}; sizing cells to that keeps
     *  expected occupancy near one query's worth of area across the tuning range. */
    private static final double CUTOFF_GRID_CELL_SIZE = Math.ceil(Math.sqrt(HydrologyTuning.maxNativeWidth()));

    private static final Logger LOG = getLogger(RiverNetwork.class);

    private final int gridSize;
    private final SpatialHashGrid<Channel.ChannelPt> spatialHashGrid = new SpatialHashGrid<>(CUTOFF_GRID_CELL_SIZE);
```

`INF` (`1e3`) had exactly one other reference (the `quadTree` bounds it sized) — `SpatialHashGrid` is
unbounded, so it disappears entirely rather than going unused.

- [ ] **Step 3: Retarget `update`'s reset block**

Replace (inside `update`, currently around line 331):

```java
        this.quadTree.clear();
```

with:

```java
        this.spatialHashGrid.clear();
```

- [ ] **Step 4: Update `emitChannel`'s stale comment**

Replace (currently around line 387):

```java
        insertChannelInQuadTree(ch); // kept QuadTree helper (also used by manageCutoffs)
```

with:

```java
        insertChannelInQuadTree(ch); // shared helper, also used by manageCutoffs
```

- [ ] **Step 5: Retarget `beginStep`**

Replace:

```java
    /** Clears the working spatial index at the start of a step. */
    public void beginStep() {
        quadTree.clear();
    }
```

with:

```java
    /** Clears the working spatial index at the start of a step. */
    public void beginStep() {
        spatialHashGrid.clear();
    }
```

- [ ] **Step 6: Retarget `getPtsCloseTo`**

Replace:

```java
    private List<Channel.ChannelPt> getPtsCloseTo(Channel.ChannelPt pt) {
        // Retained-path read (manageCutoffs): derived width of the CURRENT point.
        return quadTree.getPointsInCircle(
                pt.toArray(), Math.sqrt(channels.get(pt.channelId()).widthAt(pt.index())));
    }
```

with:

```java
    private List<Channel.ChannelPt> getPtsCloseTo(Channel.ChannelPt pt) {
        // Retained-path read (manageCutoffs): derived width of the CURRENT point.
        return spatialHashGrid.getPointsInCircle(
                pt.toArray(), Math.sqrt(channels.get(pt.channelId()).widthAt(pt.index())));
    }
```

- [ ] **Step 7: Retarget `manageCutoffs`**

Replace (currently lines 663-684):

```java
    public void manageCutoffs(Channel ch, int step) {
        if (ch.spline.checkNaN()) {
            throw new RuntimeException("cannot cut becuse spline is NaN");
        }
        quadTree.clear();
        insertChannelInQuadTree(ch);
        List<Integer> newPathIndexes = new ObjectArrayList<>();

        for (int id = 0; id < ch.numPts() - 1; id++) {
            if (!quadTree.containsPoint(ch.pt(id))) continue;
            newPathIndexes.add(id);
            List<Channel.ChannelPt> ptList = getPtsCloseTo(ch.pt(id));
            ptList.sort(null);
            for (Channel.ChannelPt cpt : ptList) {
                if (cpt.index() <= id + 1 || cpt.channelId() != ch.channelId) continue;
                cutRiverSection(id, cpt.index(), ch);
            }
        }
        newPathIndexes.add(ch.numPts() - 1);
        if (saveHistory) recordRemovedComplement(ch, newPathIndexes, step);
        ch.keepOnly(newPathIndexes);
    }
```

with:

```java
    public void manageCutoffs(Channel ch, int step) {
        if (ch.spline.checkNaN()) {
            throw new RuntimeException("cannot cut becuse spline is NaN");
        }
        spatialHashGrid.clear();
        insertChannelInQuadTree(ch);
        List<Integer> newPathIndexes = new ObjectArrayList<>();

        for (int id = 0; id < ch.numPts() - 1; id++) {
            if (!spatialHashGrid.containsPoint(ch.pt(id))) continue;
            newPathIndexes.add(id);
            List<Channel.ChannelPt> ptList = getPtsCloseTo(ch.pt(id));
            ptList.sort(null);
            for (Channel.ChannelPt cpt : ptList) {
                if (cpt.index() <= id + 1 || cpt.channelId() != ch.channelId) continue;
                cutRiverSection(id, cpt.index(), ch);
            }
        }
        newPathIndexes.add(ch.numPts() - 1);
        if (saveHistory) recordRemovedComplement(ch, newPathIndexes, step);
        ch.keepOnly(newPathIndexes);
    }
```

- [ ] **Step 8: Retarget `insertChannelInQuadTree` and `cutRiverSection` (names unchanged)**

Replace:

```java
    private void insertChannelInQuadTree(Channel ch) {
        Channel.ChannelPt[] pts = ch.getChannelAsPts();
        for (Channel.ChannelPt pt : pts) {
            quadTree.insertPoint(pt);
        }
    }

    private void cutRiverSection(int from, int to, Channel ch) {
        for (int i = from; i < to; i++) quadTree.removePoint(ch.pt(i));
    }
```

with:

```java
    private void insertChannelInQuadTree(Channel ch) {
        Channel.ChannelPt[] pts = ch.getChannelAsPts();
        for (Channel.ChannelPt pt : pts) {
            spatialHashGrid.insertPoint(pt);
        }
    }

    private void cutRiverSection(int from, int to, Channel ch) {
        for (int i = from; i < to; i++) spatialHashGrid.removePoint(ch.pt(i));
    }
```

Both keep their names — the spec deliberately minimizes blast radius here; do not rename them to
"...InHashGrid" or similar.

- [ ] **Step 9: Confirm no `QuadTree` reference remains in this file**

Run: `grep -n "QuadTree" src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java`
Expected: no matches (the import and every field/usage are gone).

- [ ] **Step 10: Build and test**

Run: `gradle spotlessApply` then `gradle build`.
Then: `gradle test`, compare failure messages against baseline exactly as in Task 3 Step 2. This is the
regression gate for cutoffs specifically — `MeandersGoldenTest.meandersGoldenSignatureMatchesCapturedFixture`
and `meandersSimulationIsDeterministicAcrossRuns` both drive `manageCutoffs` every simulated step.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java
git commit -m "$(cat <<'EOF'
refactor(hydrology): move manageCutoffs to SpatialHashGrid

manageCutoffs interleaves live removal with queries mid-walk, which no
immutable index can serve — it needs a mutable structure, just not
QuadTree's read/write lock, which is uncontended overhead under the
per-tile single-writer invariant math/ds/README.md already states.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Vg7YgZoRKXCcrpk2Eq2ReM
EOF
)"
```

---

### Task 5: Extend `SpatialIndexBenchmark` with D1/D2 correctness + throughput sections

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/debug/tests/SpatialIndexBenchmark.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/debug/tests/CLAUDE.md`

**Guidelines:** `.claude/conventions/CLAUDE.md`, `.claude/conventions/documentation.md`,
`src/main/java/me/batata_1/fractal_terrain/debug/tests/CLAUDE.md` (existing harness-per-Gradle-task
pattern), the current `SpatialIndexBenchmark.java` (read in full already).

**Interfaces:**
- Consumes: `RiverProvider.debugStages(int, int)` → `RiverProvider.Stages` with public field `network`
  (`RiverNetwork`, existing, used the same way `RiverTest.java` already uses it);
  `RiverNetwork.getChannels()`, `Channel.getChannelAsPts()`, `Channel.numPts()`, `Channel.widthAt(int)`,
  `Channel.channelId` (all existing public members); `ChannelGeometry.bedHalfWidth(double)` (existing);
  `QuadTree<T>` (existing, used here only as the "legacy" baseline, mirroring the file's existing
  `ImmutableQuadTree`-as-baseline pattern) and `SpatialHashGrid<T>` from Task 1.
- Produces: no new public API — this is a `@TestOnly` `main()` harness. Adds private helper types
  `ChannelPointCircle`, `CutoffIndex`, `QuadTreeCutoffIndex`, `HashGridCutoffIndex`, and private methods
  `benchDetectCrossingsCandidateGeneration(RiverNetwork)`, `benchManageCutoffsOpMix(RiverNetwork)`,
  `runCutoffWalk(CutoffIndex, Channel.ChannelPt[], int)`, `benchOp(String, Runnable)`.

- [ ] **Step 1: Add imports**

In `SpatialIndexBenchmark.java`, add (alphabetically among the existing `me.batata_1...` imports):

```java
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.network.Channel;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
```

and, alongside the existing `ImmutableQuadTree`/`ImmutableRTree`/`SpatialIndexPoint` imports:

```java
import me.batata_1.fractal_terrain.math.ds.QuadTree;
import me.batata_1.fractal_terrain.math.ds.SpatialHashGrid;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;
```

- [ ] **Step 2: Add the `ChannelPointCircle` adapter next to `PrimitivePoint`**

After the existing `PrimitivePoint` record (ends at the line before `public static void main`), add:

```java

    /** Zero-radius adapter mirroring {@code RiverNetwork.CrossingPoint}, so a {@link Channel.ChannelPt}
     *  can be stored in an {@link ImmutableRTree} stab query for this benchmark's own comparison. */
    private record ChannelPointCircle(Channel.ChannelPt pt) implements SpatialIndexCircle {
        @Override
        public double[] getCenter() {
            return pt.toArray();
        }

        @Override
        public double getRadius() {
            return 0.0;
        }
    }
```

- [ ] **Step 3: Call the two new benchmark sections from `main`**

Replace (currently near the end of `main`):

```java
        // ---- provider-level queries: world points, PROVIDER_MARGIN clear of borders (see above) ----
        bench(
                "RiverProvider.queryInfluence",
                worldInnerPoints(5, worldOriginX, worldOriginZ),
                pt -> localRivers.queryInfluence(pt).toArray().length);

        LOG.info(
                "throughput ratio (rtree/quadtree): influence query {}x, insideChannel test {}x",
                String.format("%.2f", rtreeInfluenceOpsPerSec / legacyInfluenceOpsPerSec),
                String.format("%.2f", rtreeMembershipOpsPerSec / legacyMembershipOpsPerSec));
        LOG.info("SpatialIndexBenchmark done. See {}", DEBUG_PATH);
    }
```

with:

```java
        // ---- provider-level queries: world points, PROVIDER_MARGIN clear of borders (see above) ----
        bench(
                "RiverProvider.queryInfluence",
                worldInnerPoints(5, worldOriginX, worldOriginZ),
                pt -> localRivers.queryInfluence(pt).toArray().length);

        // ---- RiverNetwork spatial-index migration (2026-09-03 design): detectCrossings -> ImmutableRTree,
        // manageCutoffs -> SpatialHashGrid, both against one real tile's channel set --------------------
        LOG.info("building RiverNetwork debug stages for the crossing/cutoff benchmark...");
        final RiverProvider.Stages riverNetworkStages = localRivers.debugStages(TILE_X, TILE_Z);
        benchDetectCrossingsCandidateGeneration(riverNetworkStages.network);
        benchManageCutoffsOpMix(riverNetworkStages.network);

        LOG.info(
                "throughput ratio (rtree/quadtree): influence query {}x, insideChannel test {}x",
                String.format("%.2f", rtreeInfluenceOpsPerSec / legacyInfluenceOpsPerSec),
                String.format("%.2f", rtreeMembershipOpsPerSec / legacyMembershipOpsPerSec));
        LOG.info("SpatialIndexBenchmark done. See {}", DEBUG_PATH);
    }
```

- [ ] **Step 4: Add the D1 section, after `crossCheckInfluenceQueries`**

After the existing `crossCheckInfluenceQueries` method (ends with the closing brace before the
`worldTilePoints` comment), insert:

```java

    /** D1's regression gate: the R-tree candidate set must exactly match the QuadTree baseline
     *  {@code detectCrossings} used to build, over real query radii from a real tile's channels. Then
     *  queries/sec for each, at a representative radius. */
    private static void benchDetectCrossingsCandidateGeneration(RiverNetwork network) {
        final List<Channel.ChannelPt> channelPts = new ObjectArrayList<>();
        double maxHalf = 0.0;
        for (final Channel ch : network.getChannels()) {
            for (final Channel.ChannelPt pt : ch.getChannelAsPts()) channelPts.add(pt);
            for (int i = 0; i < ch.numPts(); i++)
                maxHalf = Math.max(maxHalf, ChannelGeometry.bedHalfWidth(ch.widthAt(i)));
        }
        if (channelPts.isEmpty()) {
            LOG.warn("detectCrossings benchmark skipped: tile has no channel points");
            return;
        }
        LOG.info("detectCrossings benchmark: {} channels, {} points", network.getChannelCount(), channelPts.size());

        final QuadTree<Channel.ChannelPt> quadTree =
                new QuadTree<>(new double[] {-1e3, -1e3}, new double[] {1e3, 1e3});
        for (final Channel.ChannelPt pt : channelPts) quadTree.insertPoint(pt);

        final List<ChannelPointCircle> circles = new ObjectArrayList<>(channelPts.size());
        for (final Channel.ChannelPt pt : channelPts) circles.add(new ChannelPointCircle(pt));
        final ImmutableRTree<ChannelPointCircle> rtree = new ImmutableRTree<>(circles, null);

        final Random rng = new Random(7);
        final List<ChannelPointCircle> stabBuffer = new ObjectArrayList<>(64);
        int mismatches = 0;
        for (int i = 0; i < CROSS_CHECK_POINTS; i++) {
            final Channel.ChannelPt query = channelPts.get(rng.nextInt(channelPts.size()));
            final double radius = ChannelGeometry.bedHalfWidth(query.width()) + maxHalf;

            final Set<Channel.ChannelPt> quadHits = new HashSet<>(quadTree.getPointsInCircle(query.toArray(), radius));

            stabBuffer.clear();
            rtree.queryContaining(query.toArray(), radius, stabBuffer);
            final Set<Channel.ChannelPt> stabHits = new HashSet<>();
            for (final ChannelPointCircle circle : stabBuffer) stabHits.add(circle.pt());

            if (!quadHits.equals(stabHits)) mismatches++;
        }
        if (mismatches > 0)
            throw new IllegalStateException("detectCrossings R-tree candidate set disagreed with the QuadTree"
                    + " baseline on " + mismatches + " of " + CROSS_CHECK_POINTS + " points");
        LOG.info("detectCrossings cross-check passed: R-tree candidates match QuadTree on {} points", CROSS_CHECK_POINTS);

        final double benchRadius = maxHalf * 2;
        final Random quadRng = new Random(8);
        bench(
                "detectCrossings quadtree candidate query",
                () -> channelPts.get(quadRng.nextInt(channelPts.size())).toArray(),
                pt -> quadTree.getPointsInCircle(pt, benchRadius).size());

        final Random rtreeRng = new Random(9);
        final List<ChannelPointCircle> queryBuffer = new ObjectArrayList<>(64);
        bench(
                "detectCrossings rtree candidate query",
                () -> channelPts.get(rtreeRng.nextInt(channelPts.size())).toArray(),
                pt -> {
                    queryBuffer.clear();
                    return rtree.queryContaining(pt, benchRadius, queryBuffer).size();
                });
    }
```

- [ ] **Step 5: Add the D2 section, its `CutoffIndex` adapters, and `benchOp`**

Immediately after the method added in Step 4, insert:

```java

    /** Minimal view over {@code manageCutoffs}'s four operations, so the same walk exercises the
     *  QuadTree baseline and the SpatialHashGrid replacement without duplicating the algorithm. */
    private interface CutoffIndex {
        void insert(Channel.ChannelPt pt);

        void remove(Channel.ChannelPt pt);

        boolean contains(Channel.ChannelPt pt);

        List<Channel.ChannelPt> closeTo(Channel.ChannelPt pt, double radius);
    }

    private record QuadTreeCutoffIndex(QuadTree<Channel.ChannelPt> tree) implements CutoffIndex {
        @Override
        public void insert(Channel.ChannelPt pt) {
            tree.insertPoint(pt);
        }

        @Override
        public void remove(Channel.ChannelPt pt) {
            tree.removePoint(pt);
        }

        @Override
        public boolean contains(Channel.ChannelPt pt) {
            return tree.containsPoint(pt);
        }

        @Override
        public List<Channel.ChannelPt> closeTo(Channel.ChannelPt pt, double radius) {
            return tree.getPointsInCircle(pt.toArray(), radius);
        }
    }

    private record HashGridCutoffIndex(SpatialHashGrid<Channel.ChannelPt> grid) implements CutoffIndex {
        @Override
        public void insert(Channel.ChannelPt pt) {
            grid.insertPoint(pt);
        }

        @Override
        public void remove(Channel.ChannelPt pt) {
            grid.removePoint(pt);
        }

        @Override
        public boolean contains(Channel.ChannelPt pt) {
            return grid.containsPoint(pt);
        }

        @Override
        public List<Channel.ChannelPt> closeTo(Channel.ChannelPt pt, double radius) {
            return grid.getPointsInCircle(pt.toArray(), radius);
        }
    }

    /** Replicates {@code RiverNetwork.manageCutoffs}'s walk exactly (insert the channel, walk id order,
     *  query-then-cut), parameterized over {@link CutoffIndex} so the same algorithm exercises either
     *  structure. Returns the surviving indexes {@code manageCutoffs} would keep. */
    private static List<Integer> runCutoffWalk(CutoffIndex index, Channel.ChannelPt[] pts, int channelId) {
        for (Channel.ChannelPt pt : pts) index.insert(pt);
        final List<Integer> keptIndexes = new ObjectArrayList<>();
        for (int id = 0; id < pts.length - 1; id++) {
            if (!index.contains(pts[id])) continue;
            keptIndexes.add(id);
            final List<Channel.ChannelPt> close = index.closeTo(pts[id], Math.sqrt(pts[id].width()));
            close.sort(null);
            for (Channel.ChannelPt cpt : close) {
                if (cpt.index() <= id + 1 || cpt.channelId() != channelId) continue;
                for (int i = id; i < cpt.index(); i++) index.remove(pts[i]);
            }
        }
        keptIndexes.add(pts.length - 1);
        return keptIndexes;
    }

    /** D2's regression gate + throughput comparison: same manageCutoffs walk, QuadTree baseline vs
     *  SpatialHashGrid replacement, over the tile's largest channel (most insert/remove/query traffic). */
    private static void benchManageCutoffsOpMix(RiverNetwork network) {
        Channel largest = null;
        for (final Channel ch : network.getChannels())
            if (largest == null || ch.numPts() > largest.numPts()) largest = ch;
        if (largest == null) {
            LOG.warn("manageCutoffs benchmark skipped: tile has no channels");
            return;
        }
        final Channel.ChannelPt[] pts = largest.getChannelAsPts();
        final int channelId = largest.channelId;
        final double cellSize = Math.ceil(Math.sqrt(HydrologyTuning.maxNativeWidth()));

        final List<Integer> quadKept = runCutoffWalk(
                new QuadTreeCutoffIndex(new QuadTree<>(new double[] {-1e3, -1e3}, new double[] {1e3, 1e3})),
                pts,
                channelId);
        final List<Integer> hashKept =
                runCutoffWalk(new HashGridCutoffIndex(new SpatialHashGrid<>(cellSize)), pts, channelId);
        if (!quadKept.equals(hashKept))
            throw new IllegalStateException("manageCutoffs SpatialHashGrid walk diverged from the QuadTree"
                    + " baseline: quadtree kept " + quadKept + ", hashgrid kept " + hashKept);
        LOG.info(
                "manageCutoffs cross-check passed: SpatialHashGrid kept indexes match QuadTree ({} points)",
                pts.length);

        benchOp(
                "manageCutoffs quadtree op-mix (channel " + channelId + ", " + pts.length + " points)",
                () -> runCutoffWalk(
                        new QuadTreeCutoffIndex(new QuadTree<>(new double[] {-1e3, -1e3}, new double[] {1e3, 1e3})),
                        pts,
                        channelId));
        benchOp(
                "manageCutoffs hashgrid op-mix (channel " + channelId + ", " + pts.length + " points)",
                () -> runCutoffWalk(new HashGridCutoffIndex(new SpatialHashGrid<>(cellSize)), pts, channelId));
    }

    /** {@link #bench} for a niladic op-mix pass (insert/remove/query all inside one call) instead of a
     *  single query point — used by the manageCutoffs benchmark, which rebuilds its structure per call
     *  exactly as {@code RiverNetwork.manageCutoffs} does. */
    private static void benchOp(String label, Runnable op) {
        final long warmupEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmupEnd) op.run();

        long ops = 0;
        final long start = System.nanoTime();
        final long measureEnd = start + MEASURE_NANOS;
        long now;
        while ((now = System.nanoTime()) < measureEnd) {
            op.run();
            ops++;
        }
        final double seconds = (now - start) / 1e9;
        LOG.info(
                "{}: {} ops/sec ({} ops in {}s)", label, Math.round(ops / seconds), ops, String.format("%.2f", seconds));
    }
```

- [ ] **Step 6: Update the harness index**

Edit `src/main/java/me/batata_1/fractal_terrain/debug/tests/CLAUDE.md` — in its `SpatialIndexBenchmark.java`
row, replace the "What" cell:

```
| `SpatialIndexBenchmark.java`  | R-tree vs. legacy-quadtree correctness cross-check, then queries/sec throughput benchmark                                                    | `spatialIndexBenchmark`  | Spatial-index microbenchmark                                |
```

with:

```
| `SpatialIndexBenchmark.java`  | R-tree vs. legacy-quadtree correctness cross-check + throughput benchmark, plus RiverNetwork's detectCrossings (R-tree) and manageCutoffs (SpatialHashGrid) sections | `spatialIndexBenchmark`  | Spatial-index microbenchmark                                |
```

- [ ] **Step 7: Build and run**

Run: `gradle spotlessApply` then `gradle build`.
Run: `gradle spatialIndexBenchmark`.
Expected: both existing cross-checks still pass, plus the two new log lines "detectCrossings cross-check
passed..." and "manageCutoffs cross-check passed..." with no thrown `IllegalStateException`, followed by
queries/sec (D1) and ops/sec (D2) log lines for both structures under comparison.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/debug/tests/SpatialIndexBenchmark.java \
        src/main/java/me/batata_1/fractal_terrain/debug/tests/CLAUDE.md
git commit -m "$(cat <<'EOF'
test(hydrology): benchmark detectCrossings/manageCutoffs's new indexes

Adds the correctness cross-check (throws on mismatch) and throughput
comparison the spatial-index migration design calls for, over one real
tile's channel set, mirroring the file's existing R-tree-vs-quadtree
pattern for the primitives index.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Vg7YgZoRKXCcrpk2Eq2ReM
EOF
)"
```

---

### Task 6: Full regression pass

**Files:** none (verification only).

**Guidelines:** root `CLAUDE.md` Test section (baseline + re-verify policy), the spec's Verification
section.

**Interfaces:** none — this task runs commands and reads output, no code changes.

- [ ] **Step 1: Full build**

Run: `gradle spotlessApply` then `gradle build`. Expected: clean success.

- [ ] **Step 2: JUnit suite against baseline**

Run: `gradle test`.
Compare `build/test-results/test/*.xml` failure *messages* against
`.superpowers/conventions-alignment/post-migration-failures.txt` (the archived baseline messages) —
message equality, not just failing-test-name equality, is what proves this change left crossing/cutoff
output untouched. Expected: identical set of 9 failures with identical messages,
`independentCrossingsAreNotMerged` still the only `MeandersGoldenTest` failure. If any message differs,
stop and treat it as a regression from this plan's changes, not a pre-existing issue.

- [ ] **Step 3: Manual visual check**

Run: `gradle meandersTest`. Confirm the crossing-to-confluence merge and pruning scenarios it prints
still match their expected outcomes (same pass/fail summary as before this branch).

Run: `gradle riverTest` for at least one tile. Open the PNG dumps under the configured debug output path
and compare the channel/cutoff/crossing imagery by eye against a pre-change run (or against the
description in `network/README.md`'s "Stream capture" section) — the golden suite gates topology, not the
visual shape of a cut meander loop.

- [ ] **Step 4: Benchmark run**

Run: `gradle spatialIndexBenchmark` once more, end to end, and read the full log: both new cross-checks
pass, and note the reported throughput numbers (ImmutableRTree vs QuadTree for D1; SpatialHashGrid vs
QuadTree for D2) for the PR description or commit message — no specific ratio is required to pass, since
the design's goal is dropping lock overhead and per-call rebuild cost, not hitting a target number.

- [ ] **Step 5: Report**

Summarize, in the PR/handoff message: confirmation that the JUnit baseline is unchanged message-for-message,
the two new benchmark cross-checks passed, and the manual PNG comparison showed no visual regression.
Name any convention deviations from Global Constraints, with reasons, if any were necessary.

---

## Self-Review

**Spec coverage:**
- D1 (`detectCrossings` → `ImmutableRTree`, `CrossingPoint` adapter, `nearby.sort(null)`/`channelsOverlap`/
  `bestByPartner` untouched) → Task 3.
- D2 (`SpatialHashGrid` new class, no lock, cell size = `ceil(sqrt(maxNativeWidth()))`, field retype,
  `insertChannelInQuadTree`/`cutRiverSection`/`getPtsCloseTo`/`beginStep` names unchanged) → Tasks 1 and 4.
- D3 (`AtomicView.resolveCrossingEdges`/`crossingCandidatePairs` untouched) → verified by omission; no
  task touches `AtomicView.java`.
- Files table (`SpatialHashGrid.java`, `math/ds/CLAUDE.md`, `math/ds/README.md`, `RiverNetwork.java`,
  `SpatialIndexBenchmark.java`) → Tasks 1, 2, 3, 4, 5 respectively.
- Out-of-scope list (`QuadTree`, `ImmutableQuadTree` alignment bug, collision/BFS/capture algorithm,
  `channelsOverlap`/`segmentCrossing`) → no task touches any of them; Task 4 Step 9 explicitly greps to
  confirm `QuadTree` is gone from `RiverNetwork.java` specifically (not the class itself).
- Verification section (build/test baseline comparison, benchmark cross-check + queries/sec/op-mix,
  manual PNG check) → Task 6, plus per-task build/test steps in Tasks 1, 3, 4, 5.

**Placeholder scan:** no "TBD"/"add error handling"/"similar to Task N" language; every step carries
concrete code, exact file paths, or a literal command.

**Type consistency:** `SpatialHashGrid<T extends SpatialIndexPoint>` (Task 1) is the exact type
`RiverNetwork.spatialHashGrid` (Task 4) and `SpatialIndexBenchmark`'s `HashGridCutoffIndex` (Task 5) use
it as; `Channel.ChannelPt` is the consistent point type across `CrossingPoint` (Task 3),
`spatialHashGrid` (Task 4), and `ChannelPointCircle`/`CutoffIndex` (Task 5); method names
(`insertChannelInQuadTree`, `cutRiverSection`, `getPtsCloseTo`, `beginStep`) match between Task 4's edits
and Task 5's mirrored `runCutoffWalk`.
