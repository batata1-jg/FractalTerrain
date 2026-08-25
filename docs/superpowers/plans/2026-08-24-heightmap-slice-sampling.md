# Heightmap Slice Sampling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the heightmap's ~12 288 per-pixel `NonIntersectingInfiniteTensor.getValue` calls per chunk with ~11 window slices, and give the two large tile caches a real byte budget.

**Architecture:** The window-intersection walk inside `InfiniteTensor.getSlice` is lifted verbatim to a package-private static (`SliceGeometry`) so `NonIntersectingInfiniteTensor` can grow its own `getSlice` without a hierarchy move. A new `storage/ChunkChannelFill` fetches one `[1, w, h]` slice per channel per chunk and drives 256 allocation-free samples through two new `math/Interpolation` overloads. The nine `fillBilinear`/`fillSmoothStep` heightmap channels and the three `BiomeProvider` density functions both switch to that helper. Separately, `NonIntersectingInfiniteTensor` gains a `cacheLimitBytes` enforced in `loadInto`, mirroring its sibling `NonIntersectingSpatialIndex`.

**Tech Stack:** Java 21, Fabric 1.20.1, Gradle (Loom), JUnit 5, palantirJavaFormat via Spotless.

**Spec:** `docs/superpowers/specs/2026-08-20-heightmap-slice-sampling-design.md`

## Global Constraints

- **Read the project guidance before the first edit of a session.** Root `CLAUDE.md` "Development" read order: root `CLAUDE.md`, then the `README.md`/`CLAUDE.md` in or above the directory being edited, then `ARCHITECTURE.md` (this change crosses providers and the generation pipeline, so it applies), then `.claude/conventions/CLAUDE.md` followed by `documentation.md`, `structural.md`, `code-quality/`, `performance.md`, `temporal.md`, `intent-markers.md`. Read them; do not paraphrase them from this plan.
- **Docstring budgets are hard** (`.claude/conventions/documentation.md` Tier 3): field 1 line, method 3 lines, class 10 lines. At most one line describes the thing; every other line answers *why* or *where in the pipeline*.
- **`gradle spotlessApply` before every commit.** `spotlessCheck` is wired into `gradle build` and will fail the build otherwise. Use the cached `gradle-9.2.1`, not a PATH `gradle` 8.x.
- **Bit-identical output is the acceptance bar for Tasks 3-6.** Every converted path must produce the exact same `float` as the path it replaces. Where a formula is copied, copy the expression *and its association order* character-for-character.
- **The corner rule: `floor` and `ceil`, never `floor` and `floor + 1`.** `Interpolation.interpolate` uses `xs = {floor(x), ceil(x)}`. On an exact pixel `floor == ceil`, so only one column is read. `floor + 1` gives a numerically identical value (the extra column has weight zero) but reads one pixel further, which at a 512-px tile boundary forces a whole extra tile to materialise — a full ONNX inference, for a value multiplied by zero.
- **`GLOBAL_SCALE_CORRECTION = 5f`** (`config/ModConfig.java:22`), a compile-time constant that javac inlines, so referencing it never triggers `ModConfig`'s static initializer (which needs `FabricLoader` and would fail headless). The same holds for `RELIEF_CHANNELS = 7`, `BIOME_CHANNELS = 6`, `CH = 0`, `X = 1`, `Z = 2`. **Never reference a non-constant config member from code a JUnit test loads.**
- **Tile geometry:** relief and biome tiles are `[C, 512, 512]` with disjoint windows (`TensorWindow(int[] size)` sets `stride == size`). Tensor index order is `[channel, x, z]`; the flat index within a tile is `ch * 512 * 512 + x * 512 + z`.
- **Test baseline is a claim, not a fact.** `gradle build` currently fails at `:compileTestJava` — `src/test/.../hydrology/features/ConfluencePrimitiveTest.java` calls `ConfluencePrimitive.w(double[])` and `.d(double[])`, neither of which exists; 9 errors. Pre-existing and out of scope. With that file excluded a run reported 81 tests, 19 failed, 1 skipped (`RosgenKeyTest` 6, `ComputeRiverGridTest` 3, `ChannelGeometryTest` 3, `RiverGoldenTest` 2, `MeandersGoldenTest` 2, `GlobalRiverGoldenTest` 1, `ReachMetricsSamplerTest` 1, `CentrelineTest` 1). Re-measure at `HEAD` in a worktree with `libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored; without it you get ~132 phantom errors), and compare failure **messages** in `build/test-results/test/*.xml`, not just test names.
- **Running the new tests while `ConfluencePrimitiveTest` is broken.** `gradle test --tests '<pattern>'` still compiles the whole test source set and will fail. Add this line to `build.gradle` for the duration of local verification and **revert it before the final commit**:

  ```groovy
  sourceSets.test.java.exclude '**/hydrology/features/ConfluencePrimitiveTest.java'
  ```

  Record in the final report that this exclusion was used and reverted.

---

## File Structure

**New**

| File | Responsibility |
| --- | --- |
| `src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometry.java` | The window-intersection walk, lifted verbatim out of `InfiniteTensor.getSlice`, plus its one `RegionVisitor` interface. Geometry only — no fetch, no write. |
| `src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java` | One chunk-window slice over one tile channel (`ChunkWindow`), plus the two 256-sample upscale loops the heightmap channels use. |
| `src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometryTest.java` | Characterises the extracted walk: which windows, which src/dst regions. |
| `src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensorSliceTest.java` | `getSlice` equals `getValue` pixel-for-pixel across tile boundaries and negative coordinates; `cacheLimitBytes` bounds the cache. |
| `src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java` | The two new samplers are bit-identical to `Interpolation.interpolateBilinear`/`interpolateSmoothStep`. |
| `src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java` | End-to-end equivalence against the legacy per-pixel path, plus the tile-touch-set assertion. |

**Changed**

| File | Change |
| --- | --- |
| `infinitetensor/InfiniteTensor.java` | `getSlice` delegates its walk to `SliceGeometry`; the dead commented-out `output.addFrom(...)` line goes. |
| `infinitetensor/NonIntersectingInfiniteTensor.java` | New `getSlice`; `cacheLimitBytes` field; 5-arg constructor with the 4-arg one delegating; `evictIfNeeded` in `loadInto`. |
| `relief/ReliefProvider.java` | 8-tile cache budget; six chunk fillers; five orphaned getters deleted. |
| `world/biome/BiomeProvider.java` | 8-tile cache budget; three chunk fillers; three orphaned getters deleted; the three nested densities collapse onto a shared base and switch to windows. |
| `math/Interpolation.java` | Two window-sampling overloads. |
| `storage/FractalTerrainHeightmap.java` | Eleven `Types` entries become provider-delegating lambdas; `fillBilinear`/`fillSmoothStep` removed. |
| `infinitetensor/CLAUDE.md`, `infinitetensor/README.md`, `storage/CLAUDE.md`, `storage/README.md`, `math/CLAUDE.md`, `ARCHITECTURE.md`, `src/test/.../CLAUDE.md` | Document `SliceGeometry`, NIIT's slice path, the budgets, `ChunkChannelFill`, and the new hot site. |

---

## Deviations from the spec, and why

Both are decided; do not relitigate them mid-implementation.

**1. Converted `Types` entries stay lambdas, and the two existing method-reference entries become lambdas too.**

The spec (§4) points at `EROSION(getBiomeProvider()::fillErosion)` as the shape to copy. That shape is a latent bug. An enum constant's argument is evaluated at `Types` class-initialization, so `getBiomeProvider()` runs once, ever, and the resulting method reference captures **that** `BiomeProvider` instance. `FractalTerrainInstance` holds the provider graph in a per-world `GenerationContext` that `close()` replaces (`FractalTerrainInstance.java:62-66`), so after a world reload the eagerly-bound reference points at the previous world's provider. Today's nine lambdas (`pos -> fillBilinear(pos, getReliefProvider()::getElev)`) re-resolve the provider on every call and do not have this problem.

Copying the eager shape to nine more channels would spread the bug. Instead every converted entry takes the form `ELEVATION(pos -> getReliefProvider().fillElev(pos))`, and `EROSION`/`WEIRDNESS` are converted to the same form. The cost is one static getter call per chunk per channel — eleven per chunk, cold by `.claude/conventions/performance.md`'s bands.

**2. The new test is `storage/ChunkChannelFillTest`, not `FractalTerrainHeightmapFillTest`.**

`FractalTerrainHeightmap.Types` reaches through `FractalTerrainInstance` to live providers, which need a `MinecraftServer`, a loaded ONNX pipeline and a world — none of which a headless JUnit run has. The spec's own §6 describes constructing a synthetic `NonIntersectingInfiniteTensor` over an in-memory creating function with `path == null`; that is exactly what `ChunkChannelFill` can be driven with directly. Same assertions, testable layer.

---

## Task 1: Extract the window walk into `SliceGeometry`

Pure move. `InfiniteTensor.getSlice` must behave identically afterwards; the next task is what gives the extraction a second caller.

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometry.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/InfiniteTensor.java:74-121` (`getSlice`)
- Test: `src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometryTest.java`

**Interfaces:**
- Consumes: `InfiniteTensor.iterateWindows(int[] lo, int[] hi, WindowConsumer)` and `InfiniteTensor.buildRange(int[] start, int[] end)` — both package-private statics already in this package. `TensorWindow.getLowestIntersection(int[][])`, `getHighestIntersection(int[][])`, `getBounds(int[])`.
- Produces:
  - `static void SliceGeometry.forEachIntersection(TensorWindow window, int[][] pixelRange, RegionVisitor visitor)` — package-private.
  - `interface SliceGeometry.RegionVisitor { void visit(int[] windowIndex, int[][] dstRegion, int[][] srcRegion); }` — package-private, `@FunctionalInterface`. All three arrays are reused buffers.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometryTest.java`:

```java
package me.batata_1.fractal_terrain.infinitetensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Characterises the window walk lifted out of {@code InfiniteTensor.getSlice}. Locks the src/dst
 * region arithmetic so a later caller cannot silently change which pixels a slice reads.
 */
class SliceGeometryTest {

    /** One visit, flattened: window index then dst/src bounds per dimension. */
    private static String record(int[] wi, int[][] dst, int[][] src) {
        final StringBuilder sb = new StringBuilder("w=");
        for (int v : wi) sb.append(v).append(',');
        sb.append(" dst=");
        for (int[] d : dst) sb.append(d[0]).append("..").append(d[1]).append(',');
        sb.append(" src=");
        for (int[] s : src) sb.append(s[0]).append("..").append(s[1]).append(',');
        return sb.toString();
    }

    private static List<String> walk(TensorWindow window, int[] start, int[] end) {
        final List<String> visits = new ArrayList<>();
        SliceGeometry.forEachIntersection(
                window, InfiniteTensor.buildRange(start, end), (wi, dst, src) -> visits.add(record(wi, dst, src)));
        return visits;
    }

    @Test
    void singleWindowSliceMapsSrcAndDstDirectly() {
        final TensorWindow window = new TensorWindow(new int[] {2, 8, 8});
        final List<String> visits = walk(window, new int[] {0, 2, 3}, new int[] {1, 5, 6});
        assertEquals(List.of("w=0,0,0, dst=0..1,0..3,0..3, src=0..1,2..5,3..6,"), visits);
    }

    @Test
    void sliceCrossingATileBoundaryVisitsBothWindows() {
        final TensorWindow window = new TensorWindow(new int[] {2, 8, 8});
        final List<String> visits = walk(window, new int[] {0, 6, 0}, new int[] {1, 10, 2});
        assertEquals(
                List.of(
                        "w=0,0,0, dst=0..1,0..2,0..2, src=0..1,6..8,0..2,",
                        "w=0,1,0, dst=0..1,2..4,0..2, src=0..1,0..2,0..2,"),
                visits);
    }

    @Test
    void negativeCoordinatesWalkNegativeWindowIndices() {
        final TensorWindow window = new TensorWindow(new int[] {2, 8, 8});
        final List<String> visits = walk(window, new int[] {0, -2, 0}, new int[] {1, 2, 1});
        assertEquals(
                List.of(
                        "w=0,-1,0, dst=0..1,0..2,0..1, src=0..1,6..8,0..1,",
                        "w=0,0,0, dst=0..1,2..4,0..1, src=0..1,0..2,0..1,"),
                visits);
    }

    @Test
    void overlappingWindowsVisitTheSameOutputPixelTwice() {
        // stride < size: the overlap InfiniteTensor's additive accumulation exists for.
        final TensorWindow window = new TensorWindow(new int[] {1, 8, 8}, new int[] {1, 4, 4});
        final List<String> visits = walk(window, new int[] {0, 4, 4}, new int[] {1, 6, 6});
        assertEquals(
                List.of(
                        "w=0,0,0, dst=0..1,0..2,0..2, src=0..1,4..6,4..6,",
                        "w=0,0,1, dst=0..1,0..2,0..2, src=0..1,4..6,0..2,",
                        "w=0,1,0, dst=0..1,0..2,0..2, src=0..1,0..2,4..6,",
                        "w=0,1,1, dst=0..1,0..2,0..2, src=0..1,0..2,0..2,"),
                visits);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Add the temporary `build.gradle` exclusion from Global Constraints, then:

```
gradle test --tests 'me.batata_1.fractal_terrain.infinitetensor.SliceGeometryTest'
```

Expected: FAIL at compilation — `cannot find symbol: class SliceGeometry`.

- [ ] **Step 3: Create `SliceGeometry`**

Create `src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometry.java`:

```java
package me.batata_1.fractal_terrain.infinitetensor;

/**
 * The window walk shared by {@link InfiniteTensor#getSlice} and
 * {@link NonIntersectingInfiniteTensor#getSlice}.
 *
 * <p>Geometry only: it decides which windows a pixel range touches and how each one's pixels map
 * into the output, and leaves fetching the window and writing it to the caller. That split is what
 * lets a non-intersecting tensor reuse the arithmetic without joining {@link InfiniteTensor}'s
 * hierarchy — the two differ only in how they obtain a window, and that difference lives in the
 * visitor body.
 */
final class SliceGeometry {

    private SliceGeometry() {}

    /** Receives one intersecting window's geometry. */
    @FunctionalInterface
    interface RegionVisitor {
        /** All three arrays are reused buffers; a visitor that keeps one must copy it. */
        void visit(int[] windowIndex, int[][] dstRegion, int[][] srcRegion);
    }

    /** Visits every window of {@code window} that overlaps {@code pixelRange}, in window-index order. */
    static void forEachIntersection(TensorWindow window, int[][] pixelRange, RegionVisitor visitor) {
        final int n = pixelRange.length;
        final int[] lo = window.getLowestIntersection(pixelRange);
        final int[] hi = window.getHighestIntersection(pixelRange);
        // Reused across windows — iteration is sequential/single-threaded, and each is fully
        // recomputed per window before use, so hoisting these out of the loop is safe.
        final int[][] isect = new int[n][2];
        final int[][] srcRegion = new int[n][2];
        final int[][] dstRegion = new int[n][2];
        InfiniteTensor.iterateWindows(lo, hi, windowIndex -> {
            final int[][] wBounds = window.getBounds(windowIndex);

            // Intersection of the window bounds with the requested pixel range.
            for (int d = 0; d < n; d++) {
                isect[d][0] = Math.max(pixelRange[d][0], wBounds[d][0]);
                isect[d][1] = Math.min(pixelRange[d][1], wBounds[d][1]);
                if (isect[d][0] >= isect[d][1]) return; // no overlap
            }

            for (int d = 0; d < n; d++) {
                srcRegion[d][0] = isect[d][0] - wBounds[d][0];
                srcRegion[d][1] = isect[d][1] - wBounds[d][0];
                dstRegion[d][0] = isect[d][0] - pixelRange[d][0];
                dstRegion[d][1] = isect[d][1] - pixelRange[d][0];
            }

            visitor.visit(windowIndex, dstRegion, srcRegion);
        });
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```
gradle test --tests 'me.batata_1.fractal_terrain.infinitetensor.SliceGeometryTest'
```

Expected: PASS, 4 tests.

- [ ] **Step 5: Point `InfiniteTensor.getSlice` at the new static**

In `src/main/java/me/batata_1/fractal_terrain/infinitetensor/InfiniteTensor.java`, replace the whole body of `getSlice` (currently lines 74-121) with:

```java
    /** The read entry point: assembles a slice from however many windows it spans. */
    public FloatTensor getSlice(int[] start, int[] end) {
        int n = shape.length;
        int[][] pixelRange = buildRange(start, end);

        ensureComputed(pixelRange);

        // Accumulate contributions from all intersecting windows.
        int[] outShape = new int[n];
        for (int d = 0; d < n; d++) outShape[d] = end[d] - start[d];
        FloatTensor output = new FloatTensor(outShape);

        if (storage == null) throw new IllegalStateException("storage was not initialized");
        SliceGeometry.forEachIntersection(outputWindow, pixelRange, (windowIndex, dstRegion, srcRegion) -> {
            final FloatTensor cached = getEntryOrRecompute(windowIndex);
            if (cached == null) return;
            updateOutput(output, cached, dstRegion, srcRegion);
        });

        storage.evictIfNeeded(cacheLimitBytes);
        return output;
    }
```

Three things this deliberately does: it keeps `ensureComputed` before and `evictIfNeeded` after, so the surrounding behaviour is unchanged; it drops the commented-out `output.addFrom(cached, dstRegion, srcRegion);` line rather than carrying a dead comment into a shared helper; and it now computes a window's geometry *before* fetching it rather than after, so a null entry pays the intersection arithmetic before being skipped — a branch that is defensive and should never fire.

- [ ] **Step 6: Verify the build and the existing suite are unchanged**

```
gradle spotlessApply
gradle compileJava compileClientJava spotlessCheck
gradle test
```

Expected: compile PASSES. `gradle test` matches the Global Constraints baseline exactly — same test names, same failure *messages* in `build/test-results/test/*.xml` — plus the 4 new `SliceGeometryTest` passes. Any new failure is this task's and must be fixed here.

- [ ] **Step 7: Commit, as two commits**

Two, not one, so the diff of the second shows a move rather than a rewrite — the mitigation for "the extracted walk drifts from `InfiniteTensor`'s original". Reorder the work if you already committed: the file and its test first, the caller rewire second.

```bash
git add src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometry.java \
        src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceGeometryTest.java
git commit -m "refactor: extract the window walk as SliceGeometry"

git add src/main/java/me/batata_1/fractal_terrain/infinitetensor/InfiniteTensor.java
git commit -m "refactor: InfiniteTensor.getSlice walks through SliceGeometry"
```

---

## Task 2: `NonIntersectingInfiniteTensor.getSlice` and cache budgets

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensor.java` (whole file)
- Modify: `src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java:47-52` (constructor)
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java:139-142` (constructor)
- Test: `src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensorSliceTest.java`

**Interfaces:**
- Consumes: `SliceGeometry.forEachIntersection` and `InfiniteTensor.buildRange` from Task 1. `Storage.getEntry(int[])`, `Storage.evictIfNeeded(long)`, `Storage.persistAndRecord(TileKey, T)`, `FloatTensor.addFrom(FloatTensor, int[][], int[][])`.
- Produces:
  - `public FloatTensor NonIntersectingInfiniteTensor.getSlice(int[] start, int[] end)` — `start`/`end` are `[channel, x, z]` pixel bounds, `end` exclusive; returns a fresh, unfrozen `FloatTensor` of shape `end - start`.
  - `public NonIntersectingInfiniteTensor(String path, String name, int[] shape, Function<TileKey, FloatTensor> f, long cacheLimitBytes)` — the 4-arg constructor delegates with `Long.MAX_VALUE`.

### Budgets

Eight tiles per budgeted tensor. A 512-px tile is 2560 blocks = **160 chunks** per axis, so an entire render distance sits inside one to four tiles. That headroom is load-bearing, not slack: `Storage.recordCachedEntry` runs only on insert, never on read, so `cachedEntryByteSizes` is **FIFO, not LRU** — a tile read on every chunk is still evicted once eight newer tiles land. A working set of at most four against a budget of eight cannot hit that; lowering the budget is what breaks first.

Only the two large tensors get a budget. `global_river` is `[4, 64, 64]` = 65 KB, `dog_tensor` is `[1, 64, 64]` = 16 KB, and `hydrology_relief` is `[1, 512, 512]` = 1.05 MB — all stay on the 4-arg constructor and pay only a lock acquire and one comparison on a miss, which is what `NonIntersectingSpatialIndex` already pays.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensorSliceTest.java`:

```java
package me.batata_1.fractal_terrain.infinitetensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code getSlice} to the per-pixel {@code getValue} it replaces, and pins the cache budget to
 * the insert path. Runs on a synthetic in-memory tensor ({@code path == null}), so no model, no ONNX
 * and no world are involved.
 */
class NonIntersectingInfiniteTensorSliceTest {

    private static final int CHANNELS = 3;
    private static final int SIDE = 8;

    /** Distinct per (channel, global x, global z) so a mis-indexed read cannot coincide with a right one. */
    private static float expected(int ch, int gx, int gz) {
        return ch * 1_000_003f + gx * 31f + gz;
    }

    private final Set<TileKey> built = new LinkedHashSet<>();

    private NonIntersectingInfiniteTensor tensor(long cacheLimitBytes) {
        return new NonIntersectingInfiniteTensor(
                null,
                "synthetic",
                new int[] {CHANNELS, SIDE, SIDE},
                key -> {
                    built.add(key);
                    final int tileX = key.get(1);
                    final int tileZ = key.get(2);
                    final float[] entries = new float[CHANNELS * SIDE * SIDE];
                    for (int ch = 0; ch < CHANNELS; ch++) {
                        for (int ix = 0; ix < SIDE; ix++) {
                            for (int iz = 0; iz < SIDE; iz++) {
                                entries[(ch * SIDE + ix) * SIDE + iz] =
                                        expected(ch, tileX * SIDE + ix, tileZ * SIDE + iz);
                            }
                        }
                    }
                    return new FloatTensor(entries, new int[] {CHANNELS, SIDE, SIDE});
                },
                cacheLimitBytes);
    }

    private void assertSliceMatchesGetValue(int ch, int x0, int z0, int x1, int z1) {
        final NonIntersectingInfiniteTensor t = tensor(Long.MAX_VALUE);
        final FloatTensor slice = t.getSlice(new int[] {ch, x0, z0}, new int[] {ch + 1, x1, z1});
        final int rowStride = z1 - z0;
        for (int gx = x0; gx < x1; gx++) {
            for (int gz = z0; gz < z1; gz++) {
                assertEquals(
                        t.getValue(new int[] {ch, gx, gz}),
                        slice.get((gx - x0) * rowStride + (gz - z0)),
                        "at (" + ch + "," + gx + "," + gz + ")");
            }
        }
    }

    @Test
    void sliceInsideOneTileMatchesGetValue() {
        assertSliceMatchesGetValue(1, 2, 3, 6, 7);
    }

    @Test
    void sliceAcrossATileBoundaryMatchesGetValue() {
        assertSliceMatchesGetValue(2, 6, 6, 11, 11);
    }

    @Test
    void sliceAtNegativeCoordinatesMatchesGetValue() {
        assertSliceMatchesGetValue(0, -10, -3, -5, 2);
    }

    @Test
    void sliceTouchesOnlyTheTilesItOverlaps() {
        final NonIntersectingInfiniteTensor t = tensor(Long.MAX_VALUE);
        built.clear();
        // 8..15 is exactly tile 1 on both axes: a floor/floor+1 sampler would also drag in tile 2.
        t.getSlice(new int[] {0, 8, 8}, new int[] {1, 16, 16});
        assertEquals(1, built.size(), "built " + built);
    }

    @Test
    void cacheLimitEvictsOnInsertSoGetValueReadersAreBoundedToo() {
        final long tileBytes = (long) CHANNELS * SIDE * SIDE * Float.BYTES;
        final NonIntersectingInfiniteTensor t = tensor(2 * tileBytes);
        for (int tileX = 0; tileX < 6; tileX++) {
            t.getValue(new int[] {0, tileX * SIDE, 0});
        }
        built.clear();
        // Tile 0 is long evicted under a 2-tile budget, so reading it again rebuilds it.
        t.getValue(new int[] {0, 0, 0});
        assertEquals(1, built.size(), "expected tile 0 to be rebuilt, built " + built);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
gradle test --tests 'me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensorSliceTest'
```

Expected: FAIL at compilation — no 5-arg constructor, no `getSlice` on `NonIntersectingInfiniteTensor`.

- [ ] **Step 3: Rewrite `NonIntersectingInfiniteTensor`**

Replace `src/main/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensor.java` with:

```java
package me.batata_1.fractal_terrain.infinitetensor;

import com.google.common.base.Function;
import java.util.concurrent.CompletableFuture;
import me.batata_1.fractal_terrain.storage.EntryNotLoadableException;
import me.batata_1.fractal_terrain.storage.Storage;
import me.batata_1.fractal_terrain.storage.TileKey;

public class NonIntersectingInfiniteTensor extends Storage<FloatTensor> {

    private final TensorWindow outWindow;
    private final Function<TileKey, FloatTensor> entry_creating_function;

    /** Soft cap on cached bytes; {@code Long.MAX_VALUE} disables eviction. */
    private final long cacheLimitBytes;

    /** Unbounded cache, the historical behaviour of every tensor here. */
    public NonIntersectingInfiniteTensor(String path, String name, int[] shape, Function<TileKey, FloatTensor> f) {
        this(path, name, shape, f, Long.MAX_VALUE);
    }

    public NonIntersectingInfiniteTensor(
            String path, String name, int[] shape, Function<TileKey, FloatTensor> f, long cacheLimitBytes) {
        super(path, name, shape.length, new FloatTensor(new int[] {1}));
        this.entry_creating_function = f;
        this.outWindow = new TensorWindow(shape);
        this.cacheLimitBytes = cacheLimitBytes;
    }

    /** Makes a cache miss recoverable by recomputing the tile, which is what turns {@code Storage}
     *  into a lazily-materialized infinite tensor. Runs on the calling thread, so a creating function
     *  that reads other tiles simply recurses. */
    @Override
    protected void loadInto(TileKey key, CompletableFuture<FloatTensor> promise) {
        try {
            super.loadInto(key, promise);
        } catch (EntryNotLoadableException miss) {
            final FloatTensor entry = entry_creating_function.apply(key);
            persistAndRecord(key, entry);
            promise.complete(entry);
        }
        // The only insert path into Storage's accounting, so the budget holds for getValue-only readers
        // too. Runs after the promise settles on both branches: evicting first would drop the in-flight
        // promise from CACHE and let a racing reader start a duplicate compute.
        evictIfNeeded(cacheLimitBytes);
    }

    /** Bulk read of a pixel range, the path a chunk fill takes instead of four {@link #getValue} calls
     *  per pixel. {@code end} is exclusive; the result is freshly allocated and never cached, so the
     *  caller may read its backing array directly. */
    public FloatTensor getSlice(int[] start, int[] end) {
        final int[] outShape = new int[start.length];
        for (int d = 0; d < outShape.length; d++) outShape[d] = end[d] - start[d];
        final FloatTensor out = new FloatTensor(outShape);
        // No ensureComputed step: loadInto already recomputes a missing tile, so getEntry self-heals
        // per window, and both routes converge on Storage's single-flight.
        SliceGeometry.forEachIntersection(
                outWindow,
                InfiniteTensor.buildRange(start, end),
                (wi, dst, src) -> out.addFrom(getEntry(wi), dst, src));
        return out;
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }
}
```

`addFrom` — not a copy — is the correct write here even though the windows never overlap: `TensorWindow(int[] size)` sets `stride == size`, so at most one window covers any output pixel, and over a zero-initialised `new FloatTensor(outShape)` accumulate and overwrite produce identical bytes. That is why no writer parameter is threaded through `SliceGeometry`.

- [ ] **Step 4: Run the test to verify it passes**

```
gradle test --tests 'me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensorSliceTest'
```

Expected: PASS, 5 tests.

- [ ] **Step 5: Give the relief tensor its budget**

In `src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java`, add after the `DOG_PADDED` constant:

```java
    /** Cached relief tiles. Far above the 1-4 tile working set (a tile is 160 chunks per axis) because
     *  Storage's byte accounting is FIFO, not LRU: a tile read every chunk still ages out. */
    private static final int MAX_CACHED_TILES = 8;

    private static final long CACHE_LIMIT_BYTES =
            (long) MAX_CACHED_TILES * RELIEF_CHANNELS * INNER * INNER * Float.BYTES;
```

and change the constructor to:

```java
    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path,
                "final_relief_tiles",
                new int[] {RELIEF_CHANNELS, INNER, INNER},
                this::buildReliefTile,
                CACHE_LIMIT_BYTES);
    }
```

- [ ] **Step 6: Give the biome tensor its budget**

In `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java`, add after the `TILE_CHANNELS` declaration:

```java
    /** Cached biome tiles. Same 8-tile reasoning as {@code ReliefProvider}: Storage's accounting is
     *  FIFO, so the budget must sit well above the 1-4 tile working set. Sized off TILE_CHANNELS, so it
     *  already covers the larger tile the visualizer's debug channel produces. */
    private static final int MAX_CACHED_TILES = 8;

    private static final long CACHE_LIMIT_BYTES =
            (long) MAX_CACHED_TILES * TILE_CHANNELS * TILE_PIXELS * Float.BYTES;
```

and change the constructor's first statement to:

```java
        final_tiles = new NonIntersectingInfiniteTensor(
                path, "final_biome_tiles", new int[] {TILE_CHANNELS, 512, 512}, buildTile(), CACHE_LIMIT_BYTES);
```

- [ ] **Step 7: Verify**

```
gradle spotlessApply
gradle compileJava compileClientJava spotlessCheck
gradle test
```

Expected: compile PASSES; `gradle test` matches the baseline plus the 5 new passes.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensor.java \
        src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java \
        src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java \
        src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensorSliceTest.java
git commit -m "feat: slice reads and an 8-tile cache budget for NonIntersectingInfiniteTensor"
```

---

## Task 3: Allocation-free window samplers in `Interpolation`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/math/Interpolation.java` (add two public statics + one private helper)
- Test: `src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java`

**Interfaces:**
- Consumes: `net.minecraft.util.Mth.lerp2(double, double, double, double, double, double)`, already imported by this file.
- Produces:
  - `public static double Interpolation.sampleWindowBilinear(float[] data, float px, float pz, int originX, int originZ, int rowStride)`
  - `public static double Interpolation.sampleWindowSmoothStep(float[] data, float px, float pz, int originX, int originZ, int rowStride)`

  `px`/`pz` are **global** tensor-pixel coordinates; `originX`/`originZ` are the window's global pixel origin; `rowStride` is the window's Z extent. Indexing is `data[(x - originX) * rowStride + (z - originZ)]`. No allocation, no clamping — the caller sizes the window to cover `floor(px)..ceil(px)`.

Why the parameters are global rather than window-local: the existing `Interpolation.interpolate` computes `deltaX = x - Math.floor(x)` on the global value. Passing globals and subtracting the origin only at the *index* makes the delta arithmetic character-for-character the same expression, so bit-identity needs no floating-point argument.

These sit beside the existing `sampleBilinear(float[], double, double, int)`, which is square-only and edge-clamped and is **not** a substitute. The different name is deliberate — same name, different clamping semantics would be a trap.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java`:

```java
package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Pins the window samplers to {@code interpolate}, the per-pixel path they replace. Equality is exact,
 * not approximate: both compute the same corners with the same double expressions.
 */
class InterpolationWindowSampleTest {

    private static final float SCALE = 5f;
    private static final int ORIGIN_X = -3;
    private static final int ORIGIN_Z = 7;
    private static final int W = 9;
    private static final int H = 11;

    /** Irregular enough that a transposed or off-by-one index cannot coincide with a correct read. */
    private static float cell(int gx, int gz) {
        return (float) (Math.sin(gx * 0.7) * 13.0 + Math.cos(gz * 1.3) * 7.0 + gx * 0.25 - gz * 0.5);
    }

    private static float[] window() {
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = cell(ORIGIN_X + ix, ORIGIN_Z + iz);
        }
        return d;
    }

    /** The legacy per-pixel corner reader, over the same field. */
    private static final Function<int[], Float> POINT_SOURCE = p -> cell(p[1], p[2]);

    @Test
    void bilinearMatchesTheLegacyPerPixelPath() {
        final float[] d = window();
        for (int blockX = -14; blockX <= 40; blockX++) {
            for (int blockZ = 36; blockZ <= 90; blockZ++) {
                final float px = blockX / SCALE;
                final float pz = blockZ / SCALE;
                final double legacy = Interpolation.interpolateBilinear(px, pz, new int[3], new float[4], POINT_SOURCE);
                assertEquals(
                        legacy,
                        Interpolation.sampleWindowBilinear(d, px, pz, ORIGIN_X, ORIGIN_Z, H),
                        0.0,
                        "block (" + blockX + "," + blockZ + ")");
            }
        }
    }

    @Test
    void smoothStepMatchesTheLegacyPerPixelPath() {
        final float[] d = window();
        for (int blockX = -14; blockX <= 40; blockX++) {
            for (int blockZ = 36; blockZ <= 90; blockZ++) {
                final float px = blockX / SCALE;
                final float pz = blockZ / SCALE;
                final double legacy =
                        Interpolation.interpolateSmoothStep(px, pz, new int[3], new float[4], POINT_SOURCE);
                assertEquals(
                        legacy,
                        Interpolation.sampleWindowSmoothStep(d, px, pz, ORIGIN_X, ORIGIN_Z, H),
                        0.0,
                        "block (" + blockX + "," + blockZ + ")");
            }
        }
    }

    @Test
    void anExactPixelReadsOneColumnAndOneRow() {
        // blockX 15 / 5 == 3.0 exactly: floor == ceil, so the sampler must not reach px 4.
        final float[] d = new float[W * H];
        for (int ix = 0; ix < W; ix++) {
            for (int iz = 0; iz < H; iz++) d[ix * H + iz] = (ORIGIN_X + ix == 4) ? Float.NaN : 1f;
        }
        assertEquals(1.0, Interpolation.sampleWindowBilinear(d, 15 / SCALE, 40 / SCALE, ORIGIN_X, ORIGIN_Z, H), 0.0);
    }
}
```

The third test is the corner rule made executable: a `floor + 1` implementation reads the `NaN` column and the assertion fails.

- [ ] **Step 2: Run the test to verify it fails**

```
gradle test --tests 'me.batata_1.fractal_terrain.math.InterpolationWindowSampleTest'
```

Expected: FAIL at compilation — `cannot find symbol: method sampleWindowBilinear`.

- [ ] **Step 3: Add the two overloads**

In `src/main/java/me/batata_1/fractal_terrain/math/Interpolation.java`, add after the existing `sampleSmoothStep(float[], double, double, int)`:

```java
    /** Bilinear sample of a pre-sliced window, allocation-free. Replaces four tensor lookups per pixel
     *  on the chunk-fill path; {@code px}/{@code pz} are global pixel coords, the window origin is
     *  subtracted only at the index. Unclamped — the caller sizes the window to cover floor..ceil. */
    public static double sampleWindowBilinear(
            float[] data, float px, float pz, int originX, int originZ, int rowStride) {
        final int colLo = ((int) Math.floor(px) - originX) * rowStride;
        final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
        final int rowLo = (int) Math.floor(pz) - originZ;
        final int rowHi = (int) Math.ceil(pz) - originZ;
        final double deltaX = px - Math.floor(px);
        final double deltaZ = pz - Math.floor(pz);
        return Mth.lerp2(
                deltaX, deltaZ, data[colLo + rowLo], data[colHi + rowLo], data[colLo + rowHi], data[colHi + rowHi]);
    }

    /** Smoothstep counterpart to {@link #sampleWindowBilinear}; the elevation channel uses this one. */
    public static double sampleWindowSmoothStep(
            float[] data, float px, float pz, int originX, int originZ, int rowStride) {
        final int colLo = ((int) Math.floor(px) - originX) * rowStride;
        final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
        final int rowLo = (int) Math.floor(pz) - originZ;
        final int rowHi = (int) Math.ceil(pz) - originZ;
        final double deltaX = smoothStep(px - Math.floor(px));
        final double deltaZ = smoothStep(pz - Math.floor(pz));
        return Mth.lerp2(
                deltaX, deltaZ, data[colLo + rowLo], data[colHi + rowLo], data[colLo + rowHi], data[colHi + rowHi]);
    }

    /** Unboxed twin of {@link #stepSmoothstep}; the expression is duplicated verbatim so the two cannot
     *  drift and the window samplers stay bit-identical to the per-pixel path. */
    private static double smoothStep(double x) {
        return 3 * (x * x) - 2 * (x * x * x);
    }
```

The node order — `(xLo,zLo)`, `(xHi,zLo)`, `(xLo,zHi)`, `(xHi,zHi)` — matches `interpolate`'s `nodes[2 * i + j]` with `i` the z index and `j` the x index. `stepBilinear` is the identity `x -> x`, so omitting it from the bilinear form changes nothing numerically.

- [ ] **Step 4: Run the test to verify it passes**

```
gradle test --tests 'me.batata_1.fractal_terrain.math.InterpolationWindowSampleTest'
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/math/Interpolation.java \
        src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java
git commit -m "feat: allocation-free window samplers in Interpolation"
```

---

## Task 4: `ChunkChannelFill`

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java`

**Interfaces:**
- Consumes: `NonIntersectingInfiniteTensor.getSlice` (Task 2), `Interpolation.sampleWindowBilinear`/`sampleWindowSmoothStep` (Task 3), `FloatTensor.dataUnsafe()`, `FractalTerrainConfig.GLOBAL_SCALE_CORRECTION`.
- Produces:
  - `public record ChunkChannelFill.ChunkWindow(float[] data, int originX, int originZ, int rowStride)`
  - `public static ChunkWindow ChunkChannelFill.open(NonIntersectingInfiniteTensor tiles, int channel, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ)` — bounds inclusive on both ends.
  - `public static ChunkWindow ChunkChannelFill.open(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos)`
  - `public static float[] ChunkChannelFill.fillBilinear(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos)` — `float[256]`, indexed `localX * 16 + localZ`.
  - `public static float[] ChunkChannelFill.fillSmoothStep(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos)` — same indexing.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java`:

```java
package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import org.junit.jupiter.api.Test;

/**
 * The equivalence gate for the slice-based chunk fill: same floats and the same set of tiles
 * materialised as the per-pixel path. Runs on a synthetic in-memory tensor, so no model, no ONNX and
 * no world are involved — {@code ChunkPos} is avoided for the same reason.
 */
class ChunkChannelFillTest {

    private static final float SCALE = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
    private static final int CHANNELS = 3;
    private static final int TILE = 512;

    /** Chunk origins covering a tile interior, both tile-boundary crossings, and negative coordinates. */
    private static final int[][] CHUNK_ORIGINS = {
        {0, 0}, {160, 320}, {2544, 16}, {2540, 2540}, {2544, 2544}, {-16, -16}, {-2560, -2544}, {-2576, 48}
    };

    private static float cell(int ch, int gx, int gz) {
        return (float) (Math.sin(gx * 0.031 + ch) * 40.0 + Math.cos(gz * 0.017 - ch) * 25.0);
    }

    private static NonIntersectingInfiniteTensor tensor(Set<TileKey> built) {
        return new NonIntersectingInfiniteTensor(null, "synthetic", new int[] {CHANNELS, TILE, TILE}, key -> {
            built.add(key);
            final int baseX = key.get(1) * TILE;
            final int baseZ = key.get(2) * TILE;
            final float[] entries = new float[CHANNELS * TILE * TILE];
            for (int ch = 0; ch < CHANNELS; ch++) {
                for (int ix = 0; ix < TILE; ix++) {
                    for (int iz = 0; iz < TILE; iz++) {
                        entries[(ch * TILE + ix) * TILE + iz] = cell(ch, baseX + ix, baseZ + iz);
                    }
                }
            }
            return new FloatTensor(entries, new int[] {CHANNELS, TILE, TILE});
        });
    }

    /** The path this change replaces: four {@code getValue} corner reads per pixel. */
    private static float[] legacyFill(
            NonIntersectingInfiniteTensor t, int channel, int startX, int startZ, boolean smooth) {
        final float[] out = new float[1 << 8];
        final int[] coords = new int[3];
        final float[] nodes = new float[4];
        final Function<int[], Float> source = p -> {
            p[0] = channel;
            return t.getValue(p);
        };
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final float px = (dx + startX) / SCALE;
                final float pz = (dz + startZ) / SCALE;
                out[(dx << 4) + dz] = (float)
                        (smooth
                                ? Interpolation.interpolateSmoothStep(px, pz, coords, nodes, source)
                                : Interpolation.interpolateBilinear(px, pz, coords, nodes, source));
            }
        }
        return out;
    }

    private static float[] newFill(
            NonIntersectingInfiniteTensor t, int channel, int startX, int startZ, boolean smooth) {
        final ChunkChannelFill.ChunkWindow w =
                ChunkChannelFill.open(t, channel, startX, startZ, startX + 15, startZ + 15);
        final float[] out = new float[1 << 8];
        for (int dx = 0; dx < 16; dx++) {
            final float px = (dx + startX) / SCALE;
            for (int dz = 0; dz < 16; dz++) {
                final float pz = (dz + startZ) / SCALE;
                out[(dx << 4) + dz] = (float)
                        (smooth
                                ? Interpolation.sampleWindowSmoothStep(
                                        w.data(), px, pz, w.originX(), w.originZ(), w.rowStride())
                                : Interpolation.sampleWindowBilinear(
                                        w.data(), px, pz, w.originX(), w.originZ(), w.rowStride()));
            }
        }
        return out;
    }

    @Test
    void windowFillIsBitIdenticalToThePerPixelPath() {
        for (int ch = 0; ch < CHANNELS; ch++) {
            for (int[] origin : CHUNK_ORIGINS) {
                for (boolean smooth : new boolean[] {false, true}) {
                    final float[] legacy = legacyFill(tensor(new LinkedHashSet<>()), ch, origin[0], origin[1], smooth);
                    final float[] fresh = newFill(tensor(new LinkedHashSet<>()), ch, origin[0], origin[1], smooth);
                    for (int i = 0; i < legacy.length; i++) {
                        assertEquals(
                                legacy[i],
                                fresh[i],
                                0.0f,
                                "ch " + ch + " chunk (" + origin[0] + "," + origin[1] + ") smooth=" + smooth
                                        + " i=" + i);
                    }
                }
            }
        }
    }

    @Test
    void windowFillTouchesExactlyTheTilesThePerPixelPathTouches() {
        for (int[] origin : CHUNK_ORIGINS) {
            final Set<TileKey> legacyTiles = new LinkedHashSet<>();
            final Set<TileKey> freshTiles = new LinkedHashSet<>();
            legacyFill(tensor(legacyTiles), 0, origin[0], origin[1], false);
            newFill(tensor(freshTiles), 0, origin[0], origin[1], false);
            assertEquals(legacyTiles, freshTiles, "chunk (" + origin[0] + "," + origin[1] + ")");
        }
    }
}
```

The second test is the assertion the spec calls out as the only one that catches a `floor + 1` sampler: the value check alone passes while silently doubling ONNX work at tile edges. Chunk origins 2540 and 2544 straddle the tile-512 boundary at block 2560 deliberately — 2540..2555 must touch one tile, 2544..2559 must touch two.

- [ ] **Step 2: Run the test to verify it fails**

```
gradle test --tests 'me.batata_1.fractal_terrain.storage.ChunkChannelFillTest'
```

Expected: FAIL at compilation — `cannot find symbol: class ChunkChannelFill`.

- [ ] **Step 3: Create `ChunkChannelFill`**

Create `src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java`:

```java
package me.batata_1.fractal_terrain.storage;

import static me.batata_1.fractal_terrain.FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;

import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;
import net.minecraft.world.level.ChunkPos;

/**
 * One tile channel's pixel window for a block rectangle, plus the upscale loops that read it.
 *
 * <p>Exists because a chunk spans only 3.2 tensor pixels: the whole working set for a channel is a
 * 4x4 or 5x5 window, so one slice replaces the 1024 cached lookups the four-corner per-pixel path
 * costs. Both {@code ReliefProvider} and {@code BiomeProvider} fill their heightmap channels through
 * here, and the biome density functions reuse {@link #open} for their own compositions.
 */
public final class ChunkChannelFill {

    private ChunkChannelFill() {}

    /**
     * A slice of one channel, addressed in global tensor-pixel coordinates.
     *
     * <p>Held as a raw array rather than a {@link FloatTensor} so the 256-sample loop indexes it
     * without a per-read accessor.
     */
    public record ChunkWindow(float[] data, int originX, int originZ, int rowStride) {}

    /** Slices {@code channel} over the pixels a block rectangle needs; both bounds are inclusive. */
    public static ChunkWindow open(
            NonIntersectingInfiniteTensor tiles,
            int channel,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ) {
        // floor/ceil, never floor/floor+1: on an exact pixel the two corners coincide, and reading one
        // pixel further would cross a tile boundary and materialise a whole tile for a zero weight.
        final int originX = (int) Math.floor(minBlockX / GLOBAL_SCALE_CORRECTION);
        final int originZ = (int) Math.floor(minBlockZ / GLOBAL_SCALE_CORRECTION);
        final int lastX = (int) Math.ceil(maxBlockX / GLOBAL_SCALE_CORRECTION);
        final int lastZ = (int) Math.ceil(maxBlockZ / GLOBAL_SCALE_CORRECTION);
        final FloatTensor slice =
                tiles.getSlice(new int[] {channel, originX, originZ}, new int[] {channel + 1, lastX + 1, lastZ + 1});
        // :PERF: raw backing array; getSlice allocates this tensor fresh and never publishes it to a
        // cache, so it is unfrozen and outside the freeze invariant infinitetensor/README.md states.
        return new ChunkWindow(slice.dataUnsafe(), originX, originZ, lastZ - originZ + 1);
    }

    /** {@link #open} for a whole chunk. */
    public static ChunkWindow open(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos) {
        final int startX = pos.getMinBlockX();
        final int startZ = pos.getMinBlockZ();
        return open(tiles, channel, startX, startZ, startX + 15, startZ + 15);
    }

    /** Bilinear upscale of {@code channel} over one chunk, indexed {@code localX * 16 + localZ}. */
    public static float[] fillBilinear(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos) {
        final int startX = pos.getMinBlockX();
        final int startZ = pos.getMinBlockZ();
        final ChunkWindow window = open(tiles, channel, pos);
        final float[] data = window.data();
        final int originX = window.originX();
        final int originZ = window.originZ();
        final int rowStride = window.rowStride();
        final float[] out = new float[1 << 8];
        for (int dx = 0; dx < 16; dx++) {
            final float px = (dx + startX) / GLOBAL_SCALE_CORRECTION;
            for (int dz = 0; dz < 16; dz++) {
                final float pz = (dz + startZ) / GLOBAL_SCALE_CORRECTION;
                out[(dx << 4) + dz] =
                        (float) Interpolation.sampleWindowBilinear(data, px, pz, originX, originZ, rowStride);
            }
        }
        return out;
    }

    /** Smoothstep counterpart to {@link #fillBilinear}; the elevation channel uses this one. */
    public static float[] fillSmoothStep(NonIntersectingInfiniteTensor tiles, int channel, ChunkPos pos) {
        final int startX = pos.getMinBlockX();
        final int startZ = pos.getMinBlockZ();
        final ChunkWindow window = open(tiles, channel, pos);
        final float[] data = window.data();
        final int originX = window.originX();
        final int originZ = window.originZ();
        final int rowStride = window.rowStride();
        final float[] out = new float[1 << 8];
        for (int dx = 0; dx < 16; dx++) {
            final float px = (dx + startX) / GLOBAL_SCALE_CORRECTION;
            for (int dz = 0; dz < 16; dz++) {
                final float pz = (dz + startZ) / GLOBAL_SCALE_CORRECTION;
                out[(dx << 4) + dz] =
                        (float) Interpolation.sampleWindowSmoothStep(data, px, pz, originX, originZ, rowStride);
            }
        }
        return out;
    }
}
```

The window fields are hoisted into locals before each 256-iteration loop rather than read off the record per pixel — `.claude/conventions/performance.md` puts a chunk's 16x16 loop permanently in the hot band, where no new per-iteration dispatch layer is acceptable.

- [ ] **Step 4: Run the test to verify it passes**

```
gradle test --tests 'me.batata_1.fractal_terrain.storage.ChunkChannelFillTest'
```

Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java \
        src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java
git commit -m "feat: per-chunk channel slice fill"
```

---

## Task 5: Convert the nine heightmap channels

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java` (add six fillers, delete five getters)
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java` (add three fillers, delete three getters)
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java` (eleven `Types` entries, remove both fill helpers)

**Interfaces:**
- Consumes: `ChunkChannelFill.fillBilinear`/`fillSmoothStep` (Task 4).
- Produces, on `ReliefProvider` — each returns `float[256]` indexed `localX * 16 + localZ`:
  `public float[] fillElev(ChunkPos)`, `fillBlurredElev`, `fillGradX`, `fillGradY`, `fillRefinedGrad`, `fillRes`.
- Produces, on `BiomeProvider`, same shape: `public float[] fillContinentalness(ChunkPos)`, `fillTemperature`, `fillVegetation`. (`fillErosion` and `fillWeirdness` already exist and keep their signatures.)

- [ ] **Step 1: Add the relief chunk fillers**

In `src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java`, replace the whole "Pixel accessors" section (from `public Float get_entry` through `public Float getRes`) with:

```java
    // -------------------------------------------------------------------------
    // Pixel accessors
    // -------------------------------------------------------------------------

    public Float get_entry(final int[] mutableCoords, final int ch) {
        mutableCoords[FractalTerrainConfig.CH] = ch;
        return final_tiles.getValue(mutableCoords);
    }

    /** Kept for {@code Infinite3DVisualizer}, the only caller left once the heightmap reads slices. */
    public Float getElev(int[] xz) {
        return get_entry(xz, 0);
    }

    /** Dead ahead of this change and not this change's to remove. */
    public Float getLowFreqGrad(final int[] xz) {
        return get_entry(xz, 5);
    }

    // -------------------------------------------------------------------------
    // Per-chunk channel producers (consumed by FractalTerrainHeightmap)
    // -------------------------------------------------------------------------

    /** River-carved elevation for the 16x16 blocks of {@code pos}. Smoothstep, matching legacy getHeight. */
    public float[] fillElev(ChunkPos pos) {
        return ChunkChannelFill.fillSmoothStep(final_tiles, 0, pos);
    }

    public float[] fillBlurredElev(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, 1, pos);
    }

    public float[] fillGradX(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, 2, pos);
    }

    public float[] fillGradY(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, 3, pos);
    }

    public float[] fillRefinedGrad(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, 4, pos);
    }

    public float[] fillRes(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, 6, pos);
    }
```

Add the imports `me.batata_1.fractal_terrain.storage.ChunkChannelFill` and `net.minecraft.world.level.ChunkPos`. This deletes `getBlurredElev`, `getGradX`, `getGradY`, `getRefinedGrad` and `getRes`, whose only caller was the heightmap. `get_entry` stays as the shared body of the two survivors.

- [ ] **Step 2: Add the biome chunk fillers**

In `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java`, in the "Per-chunk channel producers" section, add beside the existing `fillErosion`/`fillWeirdness`:

```java
    /** Continentalness for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillContinentalness(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, BiomeChannels.CONTINENTALNESS.channel, pos);
    }

    /** Temperature for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillTemperature(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, BiomeChannels.TEMPERATURE.channel, pos);
    }

    /** Vegetation/humidity for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillVegetation(ChunkPos pos) {
        return ChunkChannelFill.fillBilinear(final_tiles, BiomeChannels.HUMIDITY.channel, pos);
    }
```

Add the import `me.batata_1.fractal_terrain.storage.ChunkChannelFill`.

In the "Accessors (kept last)" section, delete `getContinentalness`, `getTemperature` and `getVegetation` — the heightmap was their only caller. Keep `biomeChannel` (it is the shared body of `getDistShore`) and keep `getDistShore` and `getWeirdness`, both `@TestOnly` and both read by `Infinite3DVisualizer`.

- [ ] **Step 3: Convert the `Types` entries**

In `src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java`, replace the eleven non-special entries with:

```java
        ELEVATION(pos -> getReliefProvider().fillElev(pos)),
        REFINED_GRAD(pos -> getReliefProvider().fillRefinedGrad(pos)),
        RES(pos -> getReliefProvider().fillRes(pos)),
        BLURRED_ELEV(pos -> getReliefProvider().fillBlurredElev(pos)),
        GRAD_X(pos -> getReliefProvider().fillGradX(pos)),
        GRAD_Y(pos -> getReliefProvider().fillGradY(pos)),
        CONTINENTALNESS(pos -> getBiomeProvider().fillContinentalness(pos)),
        EROSION(pos -> getBiomeProvider().fillErosion(pos)),
        TEMPERATURE(pos -> getBiomeProvider().fillTemperature(pos)),
        VEGETATION(pos -> getBiomeProvider().fillVegetation(pos)),
        WEIRDNESS(pos -> getBiomeProvider().fillWeirdness(pos)),
```

Lambdas, not `getReliefProvider()::fillElev`. An enum constant's argument is evaluated once at class-initialization, so a method reference would capture the first world's provider forever, while `FractalTerrainInstance.close()` replaces the `GenerationContext` on every world reload. `EROSION` and `WEIRDNESS` are converted for the same reason.

Then delete the two `private static float[] fillBilinear(...)` and `fillSmoothStep(...)` helpers, and drop the now-unused `java.util.function.Function`, `FractalTerrainConfig` and `Interpolation` imports.

Two incidental cleanups fall out of that deletion rather than needing their own edit — confirm both, do not go looking for a change to make:

- `fillBilinear`'s `mutableCoords[0]` and `[1]` writes were already dead (`Interpolation.interpolate` overwrites `[1]` and `[2]`, and `ReliefProvider.get_entry` overwrites `[0]`); `fillSmoothStep` already omitted them.
- The aliasing where `TensorWindow.getPerWindowCoord(int[])` mutates the caller's array in place leaves the heightmap path. It survives on `getValue`, which stays, so the method itself is unchanged.

Update the enum's class docstring — the current text describes `Function<int[], Float>` per-pixel sources and names the removed helpers, and `.claude/conventions/documentation.md` requires prose grounded in the code as it is:

```java
    /**
     * The heightmap kinds. Each carries the provider call that fills a whole chunk's channel in one
     * pass, so the cache computes once per chunk rather than once per block.
     *
     * <p>Relief channels (elevation, gradients, residual) come from {@code ReliefProvider}, the climate
     * channels from {@code BiomeProvider}; each provider takes one tensor slice over the chunk's ~5x5
     * pixel window and upscales it. Each entry resolves its provider per call rather than capturing one,
     * because a world reload replaces the {@code GenerationContext} the providers live in.
     */
```

- [ ] **Step 4: Verify nothing still calls the deleted getters**

```
grep -rn "getBlurredElev\|getGradX\|getGradY\|getRefinedGrad\|getRes\|getContinentalness\|getTemperature\|getVegetation" src/
```

Expected: no hits. If `getRes` matches something unrelated (e.g. a `getResult`), confirm the match is not one of the deleted methods.

- [ ] **Step 5: Verify the build**

```
gradle spotlessApply
gradle compileJava compileClientJava spotlessCheck
gradle test
```

Expected: compile PASSES; `gradle test` matches the baseline plus the new tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java \
        src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java \
        src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java
git commit -m "perf: heightmap channels fill from one slice per chunk"
```

---

## Task 6: Convert the biome density functions

The heightmap path is only part of the cost: `ErosionDensity.fillArray` adds 1024 lookups per chunk and `WeirdnessDensity.fillArray` adds 2048, because it runs two independent `Interpolation`s over the same channel. One window serves both — the largest single reduction in this change.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java` (the three nested density classes)

**Interfaces:**
- Consumes: `ChunkChannelFill.open`, `ChunkChannelFill.ChunkWindow`, `Interpolation.sampleWindowBilinear` (Tasks 3-4).
- Produces: no new public API. `fillErosion(ChunkPos)`/`fillWeirdness(ChunkPos)` keep their existing signatures and behaviour.

**What stays on `getValue`:** `compute(FunctionContext)`. It is a single point, where a slice is strictly more work than a lookup. `Climate.Sampler.sample` calls `compute`, not `fillArray`, so `compute` remains the dominant path for vanilla biome resolution and the biome share of this win is smaller than the channel count suggests. Converting it properly needs a per-thread slice cache, deliberately out of scope. This is also exactly why the budget in Task 2 is enforced in `loadInto` rather than in `getSlice`: a `getSlice`-side budget would leave every `getValue`-only reader unbounded, and for `final_biome_tiles` that is the dominant path.

- [ ] **Step 1: Introduce a shared base for the three densities**

The three nested classes currently repeat `fillArray(double[], ContextProvider)`, `compute`, `minValue`, `maxValue` and `codec` verbatim, and two of them repeat `fillArray(float[], ChunkPos)`. Collapse that onto a base so the window plumbing is written once.

In `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java`, add at the top of the "Nested density functions" section:

```java
    /** Widest block span the batch path will slice before falling back to per-point reads. A vanilla
     *  {@code fillArray} batch is cell-sized, so this only guards a pathological caller. */
    private static final int MAX_BATCH_SPAN_BLOCKS = 64;

    /**
     * Shared body of the three tile-channel densities.
     *
     * <p>Holds the two ways to read one channel — a pre-sliced chunk window for the batch and chunk
     * fills, and the per-point {@link Interpolation} that still backs {@code compute} — so each
     * subclass supplies only its own composition (a shattered-band nudge, a magnitude-times-sign
     * product) rather than a fourth copy of the fill loops.
     */
    private abstract static class ChannelDensity implements DensityFunction.SimpleFunction {

        /** Tile channel this density reads; the window slices it directly. */
        protected final int channel;

        protected ChannelDensity(final int channel) {
            this.channel = channel;
        }

        /** This density's value at a block, reading a window already sliced over it. */
        protected abstract double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ);

        /** This density's value at a single block, via the per-point tensor path. */
        protected abstract double sample(int blockX, int blockZ);

        /** Bilinear read of {@link #channel} out of an open window; the subclasses compose on top. */
        protected static double read(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            return Interpolation.sampleWindowBilinear(
                    window.data(),
                    blockX / GLOBAL_SCALE_CORRECTION,
                    blockZ / GLOBAL_SCALE_CORRECTION,
                    window.originX(),
                    window.originZ(),
                    window.rowStride());
        }

        private ChunkChannelFill.ChunkWindow open(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
            return ChunkChannelFill.open(
                    FractalTerrainInstance.getBiomeProvider().final_tiles,
                    channel,
                    minBlockX,
                    minBlockZ,
                    maxBlockX,
                    maxBlockZ);
        }

        /** Two passes: bound the batch's block rectangle, slice it once, then sample. Falls back to
         *  per-point reads for a batch too wide to slice, rather than allocating an unbounded window. */
        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                final int x = pos.blockX();
                final int z = pos.blockZ();
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }

            if (maxX - minX >= MAX_BATCH_SPAN_BLOCKS || maxZ - minZ >= MAX_BATCH_SPAN_BLOCKS) {
                for (int i = 0; i < densities.length; i++) {
                    final FunctionContext pos = applier.forIndex(i);
                    densities[i] = sample(pos.blockX(), pos.blockZ());
                }
                return;
            }

            final ChunkChannelFill.ChunkWindow window = open(minX, minZ, maxX, maxZ);
            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                densities[i] = sample(window, pos.blockX(), pos.blockZ());
            }
        }

        /** The 16x16 chunk form, backing {@code fillErosion}/{@code fillWeirdness}. */
        public void fillArray(float[] out, ChunkPos pos) {
            final int startX = pos.getMinBlockX();
            final int startZ = pos.getMinBlockZ();
            final ChunkChannelFill.ChunkWindow window = open(startX, startZ, startX + 15, startZ + 15);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    out[x * 16 + z] = (float) sample(window, startX + x, startZ + z);
                }
            }
        }

        @Override
        public double compute(FunctionContext pos) {
            return sample(pos.blockX(), pos.blockZ());
        }

        @Override
        public double minValue() {
            return 0;
        }

        @Override
        public double maxValue() {
            return 0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return null;
        }
    }
```

- [ ] **Step 2: Reduce the three densities onto the base**

Replace the three nested classes with:

```java
    /** Plain bilinear interpolation of a stored biome channel. */
    private static class BiomeProviderDensity extends ChannelDensity {

        private final Interpolation interpolation;

        public BiomeProviderDensity(final float scale, final int ch) {
            super(ch);
            interpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        @Override
        protected double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            return read(window, blockX, blockZ);
        }

        @Override
        protected double sample(int blockX, int blockZ) {
            return interpolation.interpolateBilinear(blockX, blockZ);
        }
    }

    /**
     * Erosion density that nudges values out of vanilla's shattered band (erosion level 5,
     * 0.45-0.55) until shattered biomes are handled. See {@code worldgeneration101.md}
     * ("Shattered biomes") and {@link BiomeParameterClassifier#isShatteredErosion}.
     */
    private static class ErosionDensity extends ChannelDensity {

        // Shattered (erosion level 5) avoidance band and the push applied to escape it.
        private static final double SHATTERED_LO = 0.44, SHATTERED_HI = 0.55, SHATTERED_PUSH = 0.15;

        private final Interpolation interpolation;

        public ErosionDensity(final float scale, final int ch) {
            super(ch);
            interpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos);
            });
        }

        private static double nudge(double res) {
            if (SHATTERED_LO < res && res < SHATTERED_HI) res += SHATTERED_PUSH;
            return res;
        }

        @Override
        protected double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            return nudge(read(window, blockX, blockZ));
        }

        @Override
        protected double sample(int blockX, int blockZ) {
            return nudge(interpolation.interpolateBilinear(blockX, blockZ));
        }
    }

    /**
     * Weirdness, interpolated as magnitude and sign separately.
     *
     * <p>Split because the two are read by different consumers: terrain uses only the magnitude, so
     * keeping that smooth preserves landscape shape, while the sign is biome-selection-only and can
     * scatter freely — which is what folds the world into more weirdness bands.
     */
    private static class WeirdnessDensity extends ChannelDensity {

        private final Interpolation valueInterpolation;
        private final Interpolation signInterpolation;

        public WeirdnessDensity(final float scale, final int ch) {
            super(ch);
            valueInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return Math.abs(
                        FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos));
            });
            signInterpolation = new Interpolation(scale * GLOBAL_SCALE_CORRECTION, mutablePos -> {
                mutablePos[CH] = ch;
                return Math.signum(
                        FractalTerrainInstance.getBiomeProvider().final_tiles.getValue(mutablePos));
            });
        }

        /** Both interpolations read the same channel, so one window serves the magnitude and the sign —
         *  the 2048-lookup half of the per-chunk cost collapses to a single slice here. */
        @Override
        protected double sample(ChunkChannelFill.ChunkWindow window, int blockX, int blockZ) {
            final float px = blockX / GLOBAL_SCALE_CORRECTION;
            final float pz = blockZ / GLOBAL_SCALE_CORRECTION;
            final float[] data = window.data();
            final int originX = window.originX();
            final int originZ = window.originZ();
            final int rowStride = window.rowStride();
            final double magnitude = interpolateWithin(data, px, pz, originX, originZ, rowStride, true);
            final double sign = interpolateWithin(data, px, pz, originX, originZ, rowStride, false);
            return (sign >= 0) ? magnitude : -magnitude;
        }

        /** Corner-wise {@code abs}/{@code signum} before the lerp, matching what the two
         *  {@link Interpolation}s do inside their own per-corner source functions. */
        private static double interpolateWithin(
                float[] data, float px, float pz, int originX, int originZ, int rowStride, boolean magnitude) {
            final int colLo = ((int) Math.floor(px) - originX) * rowStride;
            final int colHi = ((int) Math.ceil(px) - originX) * rowStride;
            final int rowLo = (int) Math.floor(pz) - originZ;
            final int rowHi = (int) Math.ceil(pz) - originZ;
            final float n00 = transform(data[colLo + rowLo], magnitude);
            final float n10 = transform(data[colHi + rowLo], magnitude);
            final float n01 = transform(data[colLo + rowHi], magnitude);
            final float n11 = transform(data[colHi + rowHi], magnitude);
            return Mth.lerp2(px - Math.floor(px), pz - Math.floor(pz), n00, n10, n01, n11);
        }

        private static float transform(float raw, boolean magnitude) {
            return magnitude ? Math.abs(raw) : Math.signum(raw);
        }

        @Override
        protected double sample(int blockX, int blockZ) {
            if (signInterpolation.interpolateBilinear(blockX, blockZ) >= 0) {
                return valueInterpolation.interpolateBilinear(blockX, blockZ);
            }
            return -valueInterpolation.interpolateBilinear(blockX, blockZ);
        }
    }
```

`WeirdnessDensity` cannot use `ChannelDensity.read`: its two `Interpolation`s apply `Math.abs`/`Math.signum` to each **corner** before the lerp, not to the interpolated result, so the transform must happen inside the sampler. `interpolateWithin` reproduces `Interpolation.interpolate`'s corner order and delta expressions exactly for that reason.

Add the import `net.minecraft.util.Mth` to `BiomeProvider.java`.

- [ ] **Step 3: Point `fillErosion`/`fillWeirdness` at the base**

The two public methods keep their bodies, but the casts now target the base class:

```java
    /** Erosion for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z} (see {@link ErosionDensity}). */
    public float[] fillErosion(ChunkPos pos) {
        final float[] res = new float[1 << 8];
        ((ChannelDensity) erosionDensity).fillArray(res, pos);
        return res;
    }

    /** Weirdness for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z} (see {@link WeirdnessDensity}). */
    public float[] fillWeirdness(ChunkPos pos) {
        final float[] res = new float[1 << 8];
        ((ChannelDensity) weirdnessDensity).fillArray(res, pos);
        return res;
    }
```

- [ ] **Step 4: Verify**

```
gradle spotlessApply
gradle compileJava compileClientJava spotlessCheck
gradle test
```

Expected: compile PASSES; `gradle test` matches the baseline plus the new tests.

- [ ] **Step 5: Confirm in a running world**

The density path has no headless test — `ContextProvider` and `Climate.Sampler` need a live world. Run:

```
gradle runClient
```

Create a new world, fly ~1000 blocks, and confirm biome placement and coastlines look unchanged against a world generated before this branch. Note in the report that this was a visual check, not an assertion.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java
git commit -m "perf: biome densities fill from one slice per batch"
```

---

## Task 7: Documentation

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/math/CLAUDE.md`
- Modify: `ARCHITECTURE.md`
- Modify: `src/test/java/me/batata_1/fractal_terrain/CLAUDE.md`

Follow `.claude/conventions/documentation.md`: CLAUDE.md is a pure index (What / When to read, no rationale), README.md carries only knowledge not visible from the code, and comments use the timeless present per `.claude/conventions/temporal.md` — no "was changed to", no dates in prose.

- [ ] **Step 1: `infinitetensor/CLAUDE.md`**

Add one row to the Files table, beside the other entries:

```markdown
| `SliceGeometry.java`              | Window-intersection walk shared by both slice paths; geometry only, no fetch or write | Changing which windows a slice touches, adding a slice caller |
```

Update the `NonIntersectingInfiniteTensor.java` row's What column:

```markdown
| `NonIntersectingInfiniteTensor.java` | Non-overlapping-window tensor over `Storage`; recoverable miss recompute, bulk `getSlice`, byte budget enforced on insert | Per-tile caches with disjoint windows, bulk reads, cache budgets |
```

- [ ] **Step 2: `infinitetensor/README.md`**

In **Architecture**, after the existing `InfiniteTensor.getSlice` paragraph, add:

```markdown
`NonIntersectingInfiniteTensor` has its own `getSlice` over the same `SliceGeometry` walk, but no
`ensureComputed` step: its `loadInto` already recomputes a missing tile, so `getEntry` self-heals per
window and both routes converge on `Storage`'s single-flight. It writes with `addFrom` even though its
windows never overlap — `TensorWindow(int[] size)` sets `stride == size`, so at most one window covers
any output pixel, and over a zero-initialised output accumulate and overwrite produce identical bytes.
That is why `SliceGeometry` carries no writer parameter: `updateOutput` is abstract, but
`AdditiveInfiniteTensor` is its only subclass and its only body is the same `addFrom`.
```

In **Invariants**, add:

```markdown
**A `NonIntersectingInfiniteTensor`'s byte budget is enforced in `loadInto`, not in `getSlice`.**
`loadInto` is the only path by which one of these inserts into `Storage`'s accounting — the disk-hit
branch inside `super.loadInto` and the recompute branch both reach `recordCachedEntry` — so evicting
there bounds the cache no matter what the reader called. A `getSlice`-side budget would leave every
`getValue`-only reader unbounded, and for `final_biome_tiles` that is the dominant path, since vanilla's
`Climate.Sampler.sample` calls `compute(FunctionContext)`. Eviction must run *after* the promise is
settled: evicting first drops the in-flight promise from `CACHE` and lets a racing reader start a
duplicate compute. `NonIntersectingSpatialIndex.loadInto` states the same ordering constraint.

**The 8-tile budgets on `final_relief_tiles` and `final_biome_tiles` are margin, not a fit.**
`Storage.recordCachedEntry` runs only on insert, never on read, so its accounting is FIFO, not LRU — a
tile read on every chunk is still evicted once eight newer ones land. A 512-px tile is 2560 blocks, or
160 chunks per axis, so an entire render distance sits inside one to four tiles; the gap between four
and eight is what makes the FIFO ordering safe. Lowering either budget is what breaks first. The other
three tensors (`global_river` 65 KB, `dog_tensor` 16 KB, `hydrology_relief` 1.05 MB) stay unbounded
because none is large enough to matter.

**A slice returned by `getSlice` is unfrozen and safe to read through `dataUnsafe()`.** It is allocated
fresh per call and never published to a cache, so it is outside the freeze invariant above — the one
place reaching into a tensor's backing array is not the bug this README otherwise says it is. See
`storage/ChunkChannelFill`, which carries the matching `:PERF:` marker.
```

- [ ] **Step 3: `storage/CLAUDE.md`**

Add one row to the Files table:

```markdown
| `ChunkChannelFill.java` | One tile channel's pixel window for a block rectangle, plus the 256-sample chunk upscale loops | Filling a heightmap channel, sampling a tile channel over a chunk |
```

- [ ] **Step 4: `storage/README.md`**

In **Architecture**, extend the Load path bullet:

```markdown
- **Load path** (`fetchEntry` → `loadInto`): reads a persisted tile from disk. `NonIntersectingInfiniteTensor`
  and `NonIntersectingSpatialIndex` override `loadInto` to catch `EntryNotLoadableException` (cache-only
  storage, unpersisted key, missing file, or deserialization failure) and recompute the entry synchronously
  instead of failing. Both also enforce their byte budget there, because `loadInto` is their only insert
  path — see `infinitetensor/README.md`.
```

In **Design Decisions**, add:

```markdown
**A chunk reads a tile channel as one window, not as 1024 point lookups.** `ChunkChannelFill` exists
because `GLOBAL_SCALE_CORRECTION` is 5, so a 16-block chunk spans 3.2 tensor pixels and a channel's whole
working set for that chunk is a 4x4 or 5x5 window. Its window bounds are `floor(minPx)` to `ceil(maxPx)`,
never `floor` to `floor + 1`: on a coordinate that lands exactly on a pixel the two corners coincide, and
reading one pixel further would cross a 512-px tile boundary and materialise a whole neighbouring tile —
an ONNX inference for relief — to supply a value multiplied by zero.
```

- [ ] **Step 5: `math/CLAUDE.md`**

Update the `Interpolation.java` row:

```markdown
| `Interpolation.java`       | Bilinear/smoothstep interpolation over a sampled function, plus allocation-free samplers over a pre-sliced window | Upsampling a field, filling a chunk from a tensor slice |
```

- [ ] **Step 6: `ARCHITECTURE.md`**

In the "Hot/cold line of abstraction" section's **Hot sites in this repo** list, add:

```markdown
- `storage/ChunkChannelFill.java` `fillBilinear`/`fillSmoothStep` and `world/biome/BiomeProvider`'s
  `ChannelDensity.fillArray` — the 16x16 sample loops every heightmap channel and every biome density
  runs per chunk. The window fields are hoisted into locals before each loop rather than read off the
  `ChunkWindow` record per pixel, for this reason.
```

Then re-read the sections of `ARCHITECTURE.md` describing the heightmap and provider data flow, and correct any sentence that describes the per-pixel `getValue` fill. Grep for `fillBilinear`, `getValue` and `Interpolation` in that file and fix what no longer matches.

- [ ] **Step 7: `src/test/java/me/batata_1/fractal_terrain/CLAUDE.md`**

Add two rows to the Subdirectories table:

```markdown
| `infinitetensor/` | Window-walk geometry and `NonIntersectingInfiniteTensor` slice/budget behaviour | Changing slice assembly, window intersection, or a cache budget |
| `storage/`   | Chunk-window channel fill: equivalence and tile-touch against the per-pixel path | Changing the heightmap fill or the window bounds |
```

and extend the `math/` row:

```markdown
| `math/`      | `VectorOps` point-onto-segment projection; window-sampler equivalence to the per-pixel interpolation | Changing projection/clamping used by the hydrology carve, or the window samplers |
```

Leave the Status section's baseline figures alone — they are still the measured pre-existing state — but append the new suites to the count if a fresh measurement was taken.

- [ ] **Step 8: Verify the temporary build exclusion is gone**

```
git diff build.gradle
```

Expected: empty. If the `sourceSets.test.java.exclude` line from Global Constraints is still there, remove it now.

- [ ] **Step 9: Final verification**

```
gradle spotlessApply
gradle build
gradle test
```

Expected: `gradle build` still fails at `:compileTestJava` on `ConfluencePrimitiveTest` (pre-existing, out of scope — report it, do not fix it). `gradle test` with that file excluded matches the Global Constraints baseline, plus the 14 new passes from Tasks 1-4.

- [ ] **Step 10: Commit**

```bash
git add ARCHITECTURE.md \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md \
        src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/storage/README.md \
        src/main/java/me/batata_1/fractal_terrain/math/CLAUDE.md \
        src/test/java/me/batata_1/fractal_terrain/CLAUDE.md
git commit -m "docs: SliceGeometry, NIIT slice path, cache budgets, ChunkChannelFill"
```

---

## Out of scope

Do not do these as part of this plan, even if they look adjacent:

- `compute(FunctionContext)` and the per-thread slice cache it would need.
- `GlobalRiverProvider`, `DifferenceOfGaussians` and `Infinite3DVisualizer`, which read single cells where a slice is more work than a lookup.
- The `TensorWindow.getSinglePixelIntersection` allocation (it carries its own `TODO`). Removing it would speed up every remaining `getValue` caller, but it is a separate change with a separate blast radius.
- `ReliefProvider.getLowFreqGrad`, dead before this change and not this change's to remove.
- `BiomeProvider.riverHumidity`, likewise dead before this change.
- Repairing `ConfluencePrimitiveTest`, which blocks `gradle build` for unrelated reasons.
