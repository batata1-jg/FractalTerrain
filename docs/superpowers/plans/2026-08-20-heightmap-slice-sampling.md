# Heightmap Slice Sampling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `FractalTerrainHeightmap`'s per-pixel `getValue` fill with one `getSlice` per channel per chunk, and give the relief and biome tile caches a byte budget they have never had.

**Architecture:** `InfiniteTensor.getSlice`'s window-accumulation loop is extracted into a shared `SliceAssembler` static. `NonIntersectingInfiniteTensor` gains its own `getSlice` built on that static — it does **not** move under `InfiniteTensor`. A new `ChunkChannelFill` takes one single-channel slice per chunk and upscales it with allocation-free samplers that reproduce `Interpolation`'s exact arithmetic.

**Tech Stack:** Java 21, Fabric Loom (Minecraft 1.20.1), JUnit 5 (`useJUnitPlatform()`), palantirJavaFormat via Spotless.

**Spec:** `docs/superpowers/specs/2026-08-20-heightmap-slice-sampling-design.md` (committed at `1c6a1b5`)

---

## Global Constraints

Every task's requirements implicitly include this section.

**Branch:** `feature/hydrology`. The working tree has unrelated uncommitted changes — stage only the files your task names.

**Read before your first edit** (per root `CLAUDE.md`; read the index first, then only the sections you touch, once per session per file):
- `CLAUDE.md` (repo root) — the index
- `ARCHITECTURE.md` — "Hot/cold line of abstraction" section
- The `README.md` / `CLAUDE.md` in the directory you are editing
- `.claude/conventions/CLAUDE.md`, then `performance.md` (all tasks — this is hot-path work), `documentation.md` (any docstring or comment), `structural.md`, `temporal.md`, `intent-markers.md`

**Gradle invocation.** There is no `gradlew`. The `gradle` on PATH is 8.14 and is **too old** for fabric-loom 1.14.10. Use the cached 9.2.1 distribution:

```
C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat
```

Set the working directory to the repo root first.

**Acceptance for every task:** `gradle spotlessApply`, then `gradle build`, then `gradle test`.

**Spotless is repo-wide.** `spotlessApply` reformats every Java file in the repo, including ones carrying unrelated uncommitted changes. Run `git diff --stat` after it and confirm nothing outside your task's file list was reformatted before you stage.

**Test baseline — a claim to re-measure, not a fact.** At `06a15dd` plus working-tree changes (measured 2026-08-19): **90 tests, 20 failed, 1 skipped**, all pre-existing, in `RosgenKeyTest` (6), `ConfluencePrimitiveTest` (4), `ChannelGeometryTest` (3), `LocalRiverGoldenTest` (2), `MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1), `CentrelineTest` (1). The suite has broken and been repaired several times. Compare the failure **messages** in `build/test-results/test/*.xml`, not just which names fail. Do not chase these; do not claim your change caused or fixed them without comparing messages.

**Tests in this project are headless and Minecraft-free.** Zero files under `src/test/java/` import `net.minecraft`, by design (`PipelineSessionReloadRaceTest` explicitly stands in for the real object to stay headless). Two consequences that this plan is built around:
- `ChunkChannelFill`'s core takes `int minBlockX, int minBlockZ`, **not** a `ChunkPos`. The `ChunkPos` unwrap lives in the provider fillers.
- `net.minecraft.util.Mth` is a pure static math utility with no game state and is on the Loom test classpath. Task 3 uses `Mth.lerp2` deliberately, because reproducing it by hand risks losing bit-identity. If it turns out to be unavailable at test runtime, Task 3 gives the exact fallback.

**Bit-identity is the acceptance bar, not approximate equality.** Two arithmetic details carry it:
- The pixel coordinate is a **float** division: `(minBlockX + dx) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION` where the constant is `float` 5f. Do not widen it to double.
- `Mth.lerp` is `start + delta * (end - start)`. The existing `Interpolation.sampleBilinear` uses `a * (1 - t) + b * t`, which rounds differently. **Do not model the new samplers on `sampleBilinear`.**

**Deviations from the spec, deliberate:** (1) new samplers are named `sampleWindowBilinear`/`sampleWindowSmoothStep`, not `sampleBilinear` overloads, because same-name overloads with different rounding are a trap; (2) `ChunkChannelFill` slices **one channel**, not all of them, which the spec did not specify; (3) the core takes block origins rather than `ChunkPos`, for headless testability.

---

### Task 1: Extract the slice accumulator

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceAssembler.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/InfiniteTensor.java:74-120`
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/CLAUDE.md`, `src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceAssemblerTest.java`

**Interfaces:**
- Consumes: `InfiniteTensor.buildRange(int[], int[])` and `InfiniteTensor.iterateWindows(int[], int[], WindowConsumer)` — both package-private statics, already present.
- Produces: `SliceAssembler.assemble(TensorWindow, int[] start, int[] end, WindowSource, RegionWriter) -> FloatTensor`; `SliceAssembler.WindowSource.get(int[]) -> FloatTensor`; `SliceAssembler.RegionWriter.write(FloatTensor output, FloatTensor src, int[][] dstRegion, int[][] srcRegion)`; `SliceAssembler.COPY` (a `RegionWriter`). All package-private in `infinitetensor`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceAssemblerTest.java`:

```java
package me.batata_1.fractal_terrain.infinitetensor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for the window-accumulation loop shared by both infinite-tensor flavours. */
class SliceAssemblerTest {

    /** Window w of a 1-D-in-x, 4-wide-in-z layout, filled so every pixel is uniquely identifiable. */
    private static FloatTensor tileOf(int[] windowIndex, int width, int height) {
        final float[] d = new float[width * height];
        for (int ix = 0; ix < width; ix++) {
            for (int iz = 0; iz < height; iz++) {
                d[ix * height + iz] = (windowIndex[0] * width + ix) * 100f + (windowIndex[1] * height + iz);
            }
        }
        return new FloatTensor(d, new int[] {width, height});
    }

    @Test
    void copiesASingleWindowExactly() {
        final TensorWindow window = new TensorWindow(new int[] {4, 4});
        final FloatTensor out = SliceAssembler.assemble(
                window, new int[] {0, 0}, new int[] {4, 4}, wi -> tileOf(wi, 4, 4), SliceAssembler.COPY);

        assertArrayEquals(new int[] {4, 4}, out.getShape());
        for (int ix = 0; ix < 4; ix++) {
            for (int iz = 0; iz < 4; iz++) {
                assertEquals(ix * 100f + iz, out.get(ix * 4 + iz), 0.0f);
            }
        }
    }

    @Test
    void stitchesAcrossFourWindows() {
        final TensorWindow window = new TensorWindow(new int[] {4, 4});
        // [2,6) x [2,6) straddles windows (0,0), (0,1), (1,0), (1,1).
        final FloatTensor out = SliceAssembler.assemble(
                window, new int[] {2, 2}, new int[] {6, 6}, wi -> tileOf(wi, 4, 4), SliceAssembler.COPY);

        assertArrayEquals(new int[] {4, 4}, out.getShape());
        for (int gx = 2; gx < 6; gx++) {
            for (int gz = 2; gz < 6; gz++) {
                assertEquals(gx * 100f + gz, out.get((gx - 2) * 4 + (gz - 2)), 0.0f, "at " + gx + "," + gz);
            }
        }
    }

    @Test
    void visitsOnlyTheWindowsTheRangeTouches() {
        final TensorWindow window = new TensorWindow(new int[] {4, 4});
        final List<String> visited = new ArrayList<>();
        SliceAssembler.assemble(
                window,
                new int[] {0, 0},
                new int[] {4, 4},
                wi -> {
                    visited.add(Arrays.toString(wi));
                    return tileOf(wi, 4, 4);
                },
                SliceAssembler.COPY);

        assertEquals(List.of("[0, 0]"), visited);
    }

    @Test
    void addWriterAccumulatesWhereCopyOverwrites() {
        final TensorWindow window = new TensorWindow(new int[] {4, 4});
        final SliceAssembler.RegionWriter add = (o, s, dst, src) -> o.addFrom(s, dst, src);
        final FloatTensor out = SliceAssembler.assemble(
                window, new int[] {0, 0}, new int[] {4, 4}, wi -> tileOf(wi, 4, 4), add);

        // Output starts zeroed and windows are disjoint, so add and copy agree here — this pins that
        // equivalence, which is why COPY is safe for a non-intersecting tensor.
        assertEquals(0f * 100f + 3f, out.get(3), 0.0f);
        assertEquals(2f * 100f + 1f, out.get(2 * 4 + 1), 0.0f);
    }

    @Test
    void skipsAWindowWhoseSourceReturnsNull() {
        final TensorWindow window = new TensorWindow(new int[] {4, 4});
        final FloatTensor out = SliceAssembler.assemble(
                window,
                new int[] {0, 0},
                new int[] {8, 4},
                wi -> wi[0] == 1 ? null : tileOf(wi, 4, 4),
                SliceAssembler.COPY);

        assertEquals(0f * 100f + 1f, out.get(1), 0.0f); // window 0 present
        assertEquals(0.0f, out.get(4 * 4 + 1), 0.0f); // window 1 skipped, stays zero
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*SliceAssemblerTest*"`
Expected: FAIL — compilation error, `SliceAssembler` does not exist.

- [ ] **Step 3: Create `SliceAssembler`**

Create `src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceAssembler.java`:

```java
package me.batata_1.fractal_terrain.infinitetensor;

/**
 * Assembles one dense slice out of the windows intersecting a pixel range.
 *
 * <p>Extracted so a non-overlapping tensor can slice without inheriting {@link InfiniteTensor}'s
 * dependency graph and compute machinery, which it has no use for.
 */
final class SliceAssembler {

    private SliceAssembler() {}

    /** Supplies one window's tensor, or {@code null} to leave that window's region untouched. */
    @FunctionalInterface
    interface WindowSource {
        FloatTensor get(int[] windowIndex);
    }

    /** Writes one window's overlapping region into the output. */
    @FunctionalInterface
    interface RegionWriter {
        void write(FloatTensor output, FloatTensor src, int[][] dstRegion, int[][] srcRegion);
    }

    /** Overwrite, for disjoint windows: at most one window covers any output pixel. */
    static final RegionWriter COPY = (output, src, dstRegion, srcRegion) -> output.copyFrom(src, dstRegion, srcRegion);

    static FloatTensor assemble(
            TensorWindow window, int[] start, int[] end, WindowSource source, RegionWriter writer) {
        final int n = start.length;
        final int[][] pixelRange = InfiniteTensor.buildRange(start, end);

        final int[] outShape = new int[n];
        for (int d = 0; d < n; d++) outShape[d] = end[d] - start[d];
        final FloatTensor output = new FloatTensor(outShape);

        final int[] lo = window.getLowestIntersection(pixelRange);
        final int[] hi = window.getHighestIntersection(pixelRange);

        // Reused across windows — iteration is sequential/single-threaded, and each is fully
        // recomputed per window before use, so hoisting these out of the loop is safe.
        final int[][] isect = new int[n][2];
        final int[][] srcRegion = new int[n][2];
        final int[][] dstRegion = new int[n][2];
        InfiniteTensor.iterateWindows(lo, hi, windowIndex -> {
            final FloatTensor cached = source.get(windowIndex);
            if (cached == null) return;

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

            writer.write(output, cached, dstRegion, srcRegion);
        });
        return output;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "*SliceAssemblerTest*"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Delegate `InfiniteTensor.getSlice` to the new static**

In `InfiniteTensor.java`, replace the whole body of `getSlice` (currently lines 74-120) with:

```java
    /** The read entry point: assembles a slice from however many windows it spans. */
    public FloatTensor getSlice(int[] start, int[] end) {
        ensureComputed(buildRange(start, end));
        if (storage == null) throw new IllegalStateException("storage was not initialized");

        final FloatTensor output =
                SliceAssembler.assemble(outputWindow, start, end, this::getEntryOrRecompute, this::updateOutput);

        storage.evictIfNeeded(cacheLimitBytes);
        return output;
    }
```

Leave `getEntryOrRecompute`, `updateOutput`, `ensureComputed`, `buildRange` and `iterateWindows` exactly as they are. The commented-out `// output.addFrom(cached, dstRegion, srcRegion);` line disappears with the old body — that is intended; `updateOutput` is already the sole write.

Note: this computes `buildRange` twice per slice (once here, once inside `assemble`). That is one `int[n][2]` per slice call, not per pixel — the warm band, not the hot one. Leave it rather than threading a second parameter through.

- [ ] **Step 6: Verify the whole suite still behaves**

Run: `gradle spotlessApply`, then `git diff --stat` and confirm only this task's files changed, then `gradle build`, then `gradle test`.
Expected: build succeeds; test counts and failure **messages** match the baseline in Global Constraints, plus 5 new passing tests (95 tests, 20 failed, 1 skipped).

- [ ] **Step 7: Update the `infinitetensor/` docs**

In `src/main/java/me/batata_1/fractal_terrain/infinitetensor/CLAUDE.md`, add a row to the Files table, keeping the existing column alignment:

```markdown
| `SliceAssembler.java`             | Shared window-accumulation loop behind both tensors' `getSlice`         | Changing slice assembly, window intersection, or region writing |
```

In `src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md`, in the **Architecture** section, replace the sentence beginning "`InfiniteTensor.getSlice(start, end)` first calls `ensureComputed`..." so it names the split:

```markdown
`InfiniteTensor.getSlice(start, end)` first calls `ensureComputed`, which recursively ensures every
upstream dependency's needed windows exist before computing this tensor's own missing windows
(single-flighted through `Storage.getOrCompute`/`claimForCompute`), then hands off to
`SliceAssembler.assemble`, which accumulates every window intersecting the requested range into one
output `FloatTensor`. `assemble` takes the two things that differ between tensors as parameters: how to
fetch a window (`WindowSource`) and how to write its region (`RegionWriter` — `updateOutput` for an
`InfiniteTensor`, `SliceAssembler.COPY` for a non-intersecting one). Both are invoked once per
intersecting window, never per pixel. `FloatTensor` is the payload every `InfiniteTensor` produces and
every `Storage<FloatTensor>` caches.
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/infinitetensor/SliceAssembler.java \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/InfiniteTensor.java \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md \
        src/test/java/me/batata_1/fractal_terrain/infinitetensor/SliceAssemblerTest.java
git commit -m "refactor: extract slice accumulation into SliceAssembler

Lifts InfiniteTensor.getSlice's window loop verbatim into a shared
static so a non-intersecting tensor can slice without inheriting the
dependency and compute machinery. Behaviour unchanged.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: `NonIntersectingInfiniteTensor.getSlice`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensor.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingSliceTest.java`

**Interfaces:**
- Consumes: `SliceAssembler.assemble(...)` and `SliceAssembler.COPY` from Task 1.
- Produces: `NonIntersectingInfiniteTensor.getSlice(int[] start, int[] end) -> FloatTensor` (public); a 5-argument constructor `(String path, String name, int[] shape, Function<TileKey, FloatTensor> f, long cacheLimitBytes)`; the existing 4-argument constructor delegates to it with `Long.MAX_VALUE`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingSliceTest.java`:

```java
package me.batata_1.fractal_terrain.infinitetensor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import me.batata_1.fractal_terrain.storage.TileKey;
import org.junit.jupiter.api.Test;

/** {@code getSlice} must agree with {@code getValue} pixel for pixel, and touch no extra tile. */
class NonIntersectingSliceTest {

    private static final int CHANNELS = 3;
    private static final int SIDE = 8;

    /** Deterministic, asymmetric in x/z and non-linear, so a transposed or off-by-one read shows up. */
    static float valueAt(int ch, int gx, int gz) {
        return ch * 1000f + gx * 0.5f + gz * 0.25f + Math.floorMod(gx * 31 + gz * 17, 7);
    }

    private static FloatTensor buildTile(TileKey key) {
        final float[] d = new float[CHANNELS * SIDE * SIDE];
        final int tx = key.get(1);
        final int tz = key.get(2);
        for (int c = 0; c < CHANNELS; c++) {
            for (int ix = 0; ix < SIDE; ix++) {
                for (int iz = 0; iz < SIDE; iz++) {
                    d[c * SIDE * SIDE + ix * SIDE + iz] = valueAt(c, tx * SIDE + ix, tz * SIDE + iz);
                }
            }
        }
        return new FloatTensor(d, new int[] {CHANNELS, SIDE, SIDE});
    }

    /** Cache-only tensor (null path): a miss recomputes through {@code loadInto}, no disk involved. */
    private static NonIntersectingInfiniteTensor tensor(Set<TileKey> touched) {
        return new NonIntersectingInfiniteTensor(null, "test", new int[] {CHANNELS, SIDE, SIDE}, key -> {
            touched.add(key);
            return buildTile(key);
        });
    }

    @Test
    void sliceMatchesGetValueWithinOneTile() {
        final NonIntersectingInfiniteTensor t = tensor(new LinkedHashSet<>());
        final FloatTensor slice = t.getSlice(new int[] {1, 2, 3}, new int[] {2, 6, 7});

        final int width = 4;
        final int height = 4;
        for (int gx = 2; gx < 6; gx++) {
            for (int gz = 3; gz < 7; gz++) {
                final float expected = t.getValue(new int[] {1, gx, gz});
                assertEquals(expected, slice.get((gx - 2) * height + (gz - 3)), 0.0f, "at " + gx + "," + gz);
            }
        }
        assertEquals(width * height, slice.getSize());
    }

    @Test
    void sliceMatchesGetValueAcrossATileBoundary() {
        final NonIntersectingInfiniteTensor t = tensor(new LinkedHashSet<>());
        // [6,11) crosses the boundary at 8 in both axes.
        final FloatTensor slice = t.getSlice(new int[] {2, 6, 6}, new int[] {3, 11, 11});

        final int height = 5;
        for (int gx = 6; gx < 11; gx++) {
            for (int gz = 6; gz < 11; gz++) {
                final float expected = t.getValue(new int[] {2, gx, gz});
                assertEquals(expected, slice.get((gx - 6) * height + (gz - 6)), 0.0f, "at " + gx + "," + gz);
            }
        }
    }

    @Test
    void sliceMatchesGetValueAtNegativeCoordinates() {
        final NonIntersectingInfiniteTensor t = tensor(new LinkedHashSet<>());
        final FloatTensor slice = t.getSlice(new int[] {0, -3, -10}, new int[] {1, 2, -5});

        final int height = 5;
        for (int gx = -3; gx < 2; gx++) {
            for (int gz = -10; gz < -5; gz++) {
                final float expected = t.getValue(new int[] {0, gx, gz});
                assertEquals(expected, slice.get((gx + 3) * height + (gz + 10)), 0.0f, "at " + gx + "," + gz);
            }
        }
    }

    @Test
    void sliceTouchesOnlyTheTilesTheRangeSpans() {
        final Set<TileKey> touched = new LinkedHashSet<>();
        final NonIntersectingInfiniteTensor t = tensor(touched);
        // Entirely inside tile (0,0): one tile built.
        t.getSlice(new int[] {0, 1, 1}, new int[] {1, 4, 4});
        assertEquals(1, touched.size());

        touched.clear();
        final Set<TileKey> touched2 = new LinkedHashSet<>();
        final NonIntersectingInfiniteTensor t2 = tensor(touched2);
        // Crosses both boundaries: four tiles built.
        t2.getSlice(new int[] {0, 7, 7}, new int[] {1, 10, 10});
        assertEquals(4, touched2.size());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*NonIntersectingSliceTest*"`
Expected: FAIL — `getSlice` is not defined on `NonIntersectingInfiniteTensor`.

- [ ] **Step 3: Add the constructor overload and `getSlice`**

Replace the body of `NonIntersectingInfiniteTensor.java` with:

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

    /** Soft cap on cached tile bytes; {@code Long.MAX_VALUE} disables eviction. */
    private final long cacheLimitBytes;

    /** Unbounded cache — the historical behaviour of every tensor here. */
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
    }

    public float getValue(final int[] coords) {
        final FloatTensor entry = getEntry(outWindow.getSinglePixelIntersection(coords));
        return entry.entryAt(outWindow.getPerWindowCoord(coords));
    }

    /** Bulk read of {@code [start, end)} as one dense tensor, stitched from the tiles it spans.
     *  The allocation-free alternative to a {@link #getValue} per pixel — see {@code README.md}. */
    public FloatTensor getSlice(int[] start, int[] end) {
        final FloatTensor slice = SliceAssembler.assemble(outWindow, start, end, this::getEntry, SliceAssembler.COPY);
        evictIfNeeded(cacheLimitBytes);
        return slice;
    }
}
```

There is deliberately no `ensureComputed` call: `loadInto` already recomputes a missing tile synchronously, so `getEntry` self-heals per window through `Storage`'s single-flight.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "*NonIntersectingSliceTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Run the acceptance checks**

Run: `gradle spotlessApply`, then `git diff --stat` (confirm scope), then `gradle build`, then `gradle test`.
Expected: 99 tests, 20 failed, 1 skipped — the same 20, same messages.

- [ ] **Step 6: Document the slice path**

In `src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md`, add to the **Architecture** section, after the `getSlice` paragraph:

```markdown
`NonIntersectingInfiniteTensor.getSlice` reaches the same assembler by a shorter route: its windows are
disjoint, so it needs no `ensureComputed` pass — `loadInto` already recomputes a missing tile
synchronously, and `SliceAssembler.COPY` overwrites rather than accumulates because at most one window
can cover any output pixel. Reading a chunk-sized region through `getSlice` costs one tile lookup per
spanned tile; reading it through `getValue` costs four lookups and four allocations per pixel.
```

Also update the `NonIntersectingInfiniteTensor.java` row in `infinitetensor/CLAUDE.md`:

```markdown
| `NonIntersectingInfiniteTensor.java` | Non-overlapping-window tensor over `Storage`; recoverable miss recompute; bulk `getSlice` | Per-tile caches with disjoint windows, bulk region reads |
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingInfiniteTensor.java \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/README.md \
        src/main/java/me/batata_1/fractal_terrain/infinitetensor/CLAUDE.md \
        src/test/java/me/batata_1/fractal_terrain/infinitetensor/NonIntersectingSliceTest.java
git commit -m "feat: bulk getSlice on NonIntersectingInfiniteTensor

Adds a dense region read stitched from the tiles it spans, plus an
optional cache byte budget (defaulted to unbounded, so no existing
call site changes behaviour).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Allocation-free window samplers

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/math/Interpolation.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `Interpolation.sampleWindowBilinear(float[] data, float px, float pz, int originX, int originZ, int height) -> double`
  - `Interpolation.sampleWindowSmoothStep(float[] data, float px, float pz, int originX, int originZ, int height) -> double`

  `data` is a row-major `[width][height]` window; `originX`/`originZ` are its lower pixel bounds; `height` is its z-extent, i.e. the row stride. `px`/`pz` are absolute pixel coordinates and must lie within `[origin, origin + extent - 1]` after `floor`/`ceil`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java`:

```java
package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The window samplers must be bit-identical to the per-pixel path they replace, not merely close:
 * the heightmap fill they feed is compared against generated terrain.
 */
class InterpolationWindowSampleTest {

    private static final int ORIGIN_X = 3;
    private static final int ORIGIN_Z = -2;
    private static final int WIDTH = 5;
    private static final int HEIGHT = 4;

    /** Asymmetric in x/z and non-linear, so a transposed or off-by-one read cannot pass. */
    private static float fieldAt(int gx, int gz) {
        return gx * 0.5f + gz * 0.25f + Math.floorMod(gx * 31 + gz * 17, 7);
    }

    private static float[] window() {
        final float[] d = new float[WIDTH * HEIGHT];
        for (int ix = 0; ix < WIDTH; ix++) {
            for (int iz = 0; iz < HEIGHT; iz++) {
                d[ix * HEIGHT + iz] = fieldAt(ORIGIN_X + ix, ORIGIN_Z + iz);
            }
        }
        return d;
    }

    /** The existing per-pixel path, driven off the same field. */
    private static final Function<int[], Float> POINT_SOURCE = p -> fieldAt(p[1], p[2]);

    private static double reference(float px, float pz, boolean smooth) {
        final int[] coords = new int[3];
        final float[] nodes = new float[4];
        return smooth
                ? Interpolation.interpolateSmoothStep(px, pz, coords, nodes, POINT_SOURCE)
                : Interpolation.interpolateBilinear(px, pz, coords, nodes, POINT_SOURCE);
    }

    private static void assertMatchesReference(float px, float pz) {
        final float[] w = window();
        assertEquals(
                reference(px, pz, false),
                Interpolation.sampleWindowBilinear(w, px, pz, ORIGIN_X, ORIGIN_Z, HEIGHT),
                0.0,
                "bilinear at " + px + "," + pz);
        assertEquals(
                reference(px, pz, true),
                Interpolation.sampleWindowSmoothStep(w, px, pz, ORIGIN_X, ORIGIN_Z, HEIGHT),
                0.0,
                "smoothstep at " + px + "," + pz);
    }

    @Test
    void matchesReferenceAtInteriorFractions() {
        assertMatchesReference(4.4f, -0.6f);
        assertMatchesReference(5.75f, -1.25f);
        assertMatchesReference(3.2f, -1.8f);
    }

    @Test
    void matchesReferenceOnExactPixels() {
        // floor == ceil here; the sampler must read one column, not two.
        assertMatchesReference(4.0f, -1.0f);
        assertMatchesReference(3.0f, -2.0f);
        assertMatchesReference(7.0f, 1.0f);
    }

    @Test
    void matchesReferenceOnWindowEdges() {
        assertMatchesReference(3.0f, -2.0f);
        assertMatchesReference(6.99f, 0.99f);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*InterpolationWindowSampleTest*"`
Expected: FAIL — `sampleWindowBilinear` is not defined.

- [ ] **Step 3: Add the samplers**

Append to `Interpolation.java`, before the closing brace:

```java
    // -------------------------------------------------------------------------
    // Window sampling: the bulk path, reading an already-materialized slice
    // -------------------------------------------------------------------------

    /**
     * Bilinear sample of a window already in memory, reproducing {@link #interpolateBilinear}'s exact
     * arithmetic — the same {@code floor}/{@code ceil} corners and the same {@link Mth#lerp2}.
     *
     * <p>The corner rule is load-bearing, not stylistic. On a coordinate that lands exactly on a pixel,
     * {@code floor == ceil} and one column is read; a {@code floor + 1} sampler would read one pixel
     * further, which at a tile boundary pulls in the next tile and costs a full tile build for a value
     * multiplied by zero.
     */
    public static double sampleWindowBilinear(
            float[] data, float px, float pz, int originX, int originZ, int height) {
        final int x0 = (int) Math.floor(px);
        final int x1 = (int) Math.ceil(px);
        final int z0 = (int) Math.floor(pz);
        final int z1 = (int) Math.ceil(pz);

        final int rowX0 = (x0 - originX) * height;
        final int rowX1 = (x1 - originX) * height;
        final int colZ0 = z0 - originZ;
        final int colZ1 = z1 - originZ;

        return Mth.lerp2(
                px - Math.floor(px),
                pz - Math.floor(pz),
                data[rowX0 + colZ0],
                data[rowX1 + colZ0],
                data[rowX0 + colZ1],
                data[rowX1 + colZ1]);
    }

    /** Smoothstep twin of {@link #sampleWindowBilinear}; the easing matches {@link #stepSmoothstep}
     *  term for term, inlined rather than applied through the {@code Function} so the hot path does
     *  not box a {@code Double} per corner. */
    public static double sampleWindowSmoothStep(
            float[] data, float px, float pz, int originX, int originZ, int height) {
        final int x0 = (int) Math.floor(px);
        final int x1 = (int) Math.ceil(px);
        final int z0 = (int) Math.floor(pz);
        final int z1 = (int) Math.ceil(pz);

        final int rowX0 = (x0 - originX) * height;
        final int rowX1 = (x1 - originX) * height;
        final int colZ0 = z0 - originZ;
        final int colZ1 = z1 - originZ;

        final double dx = px - Math.floor(px);
        final double dz = pz - Math.floor(pz);

        return Mth.lerp2(
                3 * (dx * dx) - 2 * (dx * dx * dx),
                3 * (dz * dz) - 2 * (dz * dz * dz),
                data[rowX0 + colZ0],
                data[rowX1 + colZ0],
                data[rowX0 + colZ1],
                data[rowX1 + colZ1]);
    }
```

Do **not** model these on the existing `sampleBilinear`, which uses `a * (1 - t) + b * t` and rounds differently from `Mth.lerp`'s `a + t * (b - a)`.

**Fallback if `Mth` is unavailable at test runtime** (it should not be — it is a pure static utility with no game state, and Loom puts Minecraft on the test classpath). Replace both `Mth.lerp2(...)` calls with a private helper and use `lerp2(...)`:

```java
    /** Vanilla {@code Mth.lerp2}, reproduced term for term. */
    private static double lerp2(double dx, double dz, double v00, double v10, double v01, double v11) {
        return lerpRaw(dz, lerpRaw(dx, v00, v10), lerpRaw(dx, v01, v11));
    }

    /** Vanilla {@code Mth.lerp}: {@code start + delta * (end - start)}. */
    private static double lerpRaw(double delta, double start, double end) {
        return start + delta * (end - start);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "*InterpolationWindowSampleTest*"`
Expected: PASS, 3 tests. Every assertion uses a delta of exactly `0.0` — any failure means the arithmetic diverged, not that a tolerance was too tight.

- [ ] **Step 5: Run the acceptance checks**

Run: `gradle spotlessApply`, then `git diff --stat` (confirm scope), then `gradle build`, then `gradle test`.
Expected: 102 tests, 20 failed, 1 skipped.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/math/Interpolation.java \
        src/test/java/me/batata_1/fractal_terrain/math/InterpolationWindowSampleTest.java
git commit -m "feat: allocation-free window samplers in Interpolation

Bilinear and smoothstep sampling of an in-memory window, bit-identical
to the per-pixel path: same floor/ceil corners, same Mth.lerp2, no
boxing. The floor/ceil rule keeps a chunk at a tile edge from pulling
in the neighbouring tile.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `ChunkChannelFill` and the heightmap rewiring

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java`

**Interfaces:**
- Consumes: `NonIntersectingInfiniteTensor.getSlice` (Task 2); `Interpolation.sampleWindowBilinear`/`sampleWindowSmoothStep` (Task 3).
- Produces:
  - `ChunkChannelFill.bilinear(NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ) -> float[]` (length 256, indexed `(dx << 4) + dz`)
  - `ChunkChannelFill.smoothStep(NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ) -> float[]`
  - `ChunkChannelFill.sliceForChunk(NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ) -> FloatTensor` (single-channel window; Task 5 uses this)
  - `ChunkChannelFill.pixelFloor(int block) -> int`, `ChunkChannelFill.pixelCeil(int block) -> int` (Task 5 uses both)
  - `ReliefProvider.fillElev/fillBlurredElev/fillGradX/fillGradY/fillRefinedGrad/fillRes(ChunkPos) -> float[]`
  - `BiomeProvider.fillContinentalness/fillTemperature/fillVegetation(ChunkPos) -> float[]`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java`:

```java
package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
 * The slice-based fill must reproduce the per-pixel fill exactly AND touch no tile the old path did
 * not. The value check alone would pass while silently doubling tile builds at chunk edges.
 */
class ChunkChannelFillTest {

    private static final int CHANNELS = 3;
    private static final int SIDE = 64;

    /** Chunk origins: interior, straddling the tile boundary at pixel 64 (block 320), and negative. */
    private static final int[][] ORIGINS = {
        {0, 0}, {160, 160}, {304, 304}, {320, 320}, {-16, -16}, {-336, 48}, {315, -5}
    };

    private static float valueAt(int ch, int gx, int gz) {
        return ch * 1000f + gx * 0.5f + gz * 0.25f + Math.floorMod(gx * 31 + gz * 17, 7);
    }

    private static NonIntersectingInfiniteTensor tensor(Set<String> touched) {
        return new NonIntersectingInfiniteTensor(null, "test", new int[] {CHANNELS, SIDE, SIDE}, key -> {
            touched.add(key.get(1) + "," + key.get(2));
            final float[] d = new float[CHANNELS * SIDE * SIDE];
            for (int c = 0; c < CHANNELS; c++) {
                for (int ix = 0; ix < SIDE; ix++) {
                    for (int iz = 0; iz < SIDE; iz++) {
                        d[c * SIDE * SIDE + ix * SIDE + iz] =
                                valueAt(c, key.get(1) * SIDE + ix, key.get(2) * SIDE + iz);
                    }
                }
            }
            return new FloatTensor(d, new int[] {CHANNELS, SIDE, SIDE});
        });
    }

    /** The path being replaced, reproduced verbatim from FractalTerrainHeightmap.fillBilinear. */
    private static float[] referenceFill(
            NonIntersectingInfiniteTensor t, int ch, int minBlockX, int minBlockZ, boolean smooth) {
        final float[] heights = new float[1 << 8];
        final int[] mutableCoords = new int[3];
        final float[] mutableNodes = new float[4];
        final Function<int[], Float> source = p -> {
            p[0] = ch;
            return t.getValue(p);
        };
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final float px = (dx + minBlockX) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
                final float pz = (dz + minBlockZ) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
                heights[(dx << 4) + dz] = (float) (smooth
                        ? Interpolation.interpolateSmoothStep(px, pz, mutableCoords, mutableNodes, source)
                        : Interpolation.interpolateBilinear(px, pz, mutableCoords, mutableNodes, source));
            }
        }
        return heights;
    }

    @Test
    void bilinearFillIsBitIdenticalToThePerPixelPath() {
        for (int[] origin : ORIGINS) {
            for (int ch = 0; ch < CHANNELS; ch++) {
                final float[] expected = referenceFill(tensor(new LinkedHashSet<>()), ch, origin[0], origin[1], false);
                final float[] actual =
                        ChunkChannelFill.bilinear(tensor(new LinkedHashSet<>()), ch, origin[0], origin[1]);
                assertArrayEquals(expected, actual, 0.0f, "ch " + ch + " at " + origin[0] + "," + origin[1]);
            }
        }
    }

    @Test
    void smoothStepFillIsBitIdenticalToThePerPixelPath() {
        for (int[] origin : ORIGINS) {
            final float[] expected = referenceFill(tensor(new LinkedHashSet<>()), 0, origin[0], origin[1], true);
            final float[] actual = ChunkChannelFill.smoothStep(tensor(new LinkedHashSet<>()), 0, origin[0], origin[1]);
            assertArrayEquals(expected, actual, 0.0f, "at " + origin[0] + "," + origin[1]);
        }
    }

    @Test
    void fillTouchesExactlyTheTilesThePerPixelPathTouched() {
        for (int[] origin : ORIGINS) {
            final Set<String> referenceTouched = new LinkedHashSet<>();
            referenceFill(tensor(referenceTouched), 1, origin[0], origin[1], false);

            final Set<String> sliceTouched = new LinkedHashSet<>();
            ChunkChannelFill.bilinear(tensor(sliceTouched), 1, origin[0], origin[1]);

            assertEquals(referenceTouched, sliceTouched, "tiles at " + origin[0] + "," + origin[1]);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*ChunkChannelFillTest*"`
Expected: FAIL — `ChunkChannelFill` does not exist.

- [ ] **Step 3: Create `ChunkChannelFill`**

Create `src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java`:

```java
package me.batata_1.fractal_terrain.storage;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.infinitetensor.FloatTensor;
import me.batata_1.fractal_terrain.infinitetensor.NonIntersectingInfiniteTensor;
import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * Upscales one tensor channel to a chunk's 16x16 blocks from a single window read.
 *
 * <p>Exists because the per-pixel alternative costs four tile lookups and four allocations per block:
 * a chunk spans 3.2 tensor pixels, so the whole working set is one 4x4 or 5x5 window that one
 * {@code getSlice} returns dense.
 *
 * <p>Takes block origins rather than a {@code ChunkPos} so the arithmetic stays testable without
 * Minecraft on the classpath, matching the rest of the JUnit suite.
 */
public final class ChunkChannelFill {

    private ChunkChannelFill() {}

    /** Blocks per chunk axis. */
    private static final int SPAN = 16;

    /** The single-channel window covering {@code [minBlockX, minBlockX + 15]} in both axes.
     *
     * <p>Bounds are {@code floor(min)} to {@code ceil(max)} inclusive, matching the corner rule in
     * {@link Interpolation#sampleWindowBilinear}: taking {@code floor + 1} instead would reach one
     * pixel past the chunk and, at a tile edge, force the neighbouring tile to be built. */
    public static FloatTensor sliceForChunk(
            NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ) {
        final int px0 = originPixel(minBlockX);
        final int pz0 = originPixel(minBlockZ);
        final int px1 = lastPixel(minBlockX);
        final int pz1 = lastPixel(minBlockZ);
        return tensor.getSlice(new int[] {ch, px0, pz0}, new int[] {ch + 1, px1 + 1, pz1 + 1});
    }

    /** Lower pixel bound for a chunk axis origin. */
    public static int originPixel(int minBlock) {
        return (int) Math.floor(minBlock / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION);
    }

    /** Upper (inclusive) pixel bound for a chunk axis origin. */
    public static int lastPixel(int minBlock) {
        return (int) Math.ceil((minBlock + SPAN - 1) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION);
    }

    /** Bilinear upscale of one channel over a chunk, indexed {@code (dx << 4) + dz}. */
    public static float[] bilinear(NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ) {
        return fill(tensor, ch, minBlockX, minBlockZ, false);
    }

    /** Smoothstep upscale of one channel over a chunk, indexed {@code (dx << 4) + dz}. */
    public static float[] smoothStep(NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ) {
        return fill(tensor, ch, minBlockX, minBlockZ, true);
    }

    private static float[] fill(
            NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ, boolean smooth) {
        final float scale = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final int px0 = originPixel(minBlockX);
        final int pz0 = originPixel(minBlockZ);
        final int height = lastPixel(minBlockZ) - pz0 + 1;

        // :PERF: backing array hoisted once per fill instead of a FloatTensor.get per corner; the slice
        // is built fresh by SliceAssembler and never published to a cache, so it is unfrozen and
        // dataUnsafe() is the documented escape hatch rather than a freeze-invariant violation.
        final float[] window = sliceForChunk(tensor, ch, minBlockX, minBlockZ).dataUnsafe();

        final float[] out = new float[SPAN * SPAN];
        for (int dx = 0; dx < SPAN; dx++) {
            final float px = (dx + minBlockX) / scale;
            for (int dz = 0; dz < SPAN; dz++) {
                final float pz = (dz + minBlockZ) / scale;
                out[(dx << 4) + dz] = (float) (smooth
                        ? Interpolation.sampleWindowSmoothStep(window, px, pz, px0, pz0, height)
                        : Interpolation.sampleWindowBilinear(window, px, pz, px0, pz0, height));
            }
        }
        return out;
    }
}
```

The pixel coordinate is a **float** division because the path it replaces is: `GLOBAL_SCALE_CORRECTION` is `float 5f` and `(int + float)` promotes to `float`, not `double`. Widening it breaks bit-identity.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "*ChunkChannelFillTest*"`
Expected: PASS, 3 tests. If `fillTouchesExactlyTheTilesThePerPixelPathTouched` fails, the corner rule was violated — re-read Task 3 Step 3 before changing the test.

- [ ] **Step 5: Add the relief chunk fillers**

In `ReliefProvider.java`, add after the existing pixel accessors (keep the `getElev`/`getBlurredElev`/... getters — `Infinite3DVisualizer` and others still use them):

```java
    // -------------------------------------------------------------------------
    // Chunk fillers (consumed by FractalTerrainHeightmap)
    // -------------------------------------------------------------------------

    /** Elevation for the 16x16 blocks of {@code pos}, smoothstep-upscaled to match the legacy getHeight. */
    public float[] fillElev(ChunkPos pos) {
        return ChunkChannelFill.smoothStep(final_tiles, 0, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** Blurred elevation for the 16x16 blocks of {@code pos}. */
    public float[] fillBlurredElev(ChunkPos pos) {
        return ChunkChannelFill.bilinear(final_tiles, 1, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** Elevation gradient in x for the 16x16 blocks of {@code pos}. */
    public float[] fillGradX(ChunkPos pos) {
        return ChunkChannelFill.bilinear(final_tiles, 2, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** Elevation gradient in z for the 16x16 blocks of {@code pos}. */
    public float[] fillGradY(ChunkPos pos) {
        return ChunkChannelFill.bilinear(final_tiles, 3, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** Refined gradient for the 16x16 blocks of {@code pos}. */
    public float[] fillRefinedGrad(ChunkPos pos) {
        return ChunkChannelFill.bilinear(final_tiles, 4, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** DoG residual for the 16x16 blocks of {@code pos}. */
    public float[] fillRes(ChunkPos pos) {
        return ChunkChannelFill.bilinear(final_tiles, 6, pos.getMinBlockX(), pos.getMinBlockZ());
    }
```

Add the imports `me.batata_1.fractal_terrain.storage.ChunkChannelFill` and `net.minecraft.world.level.ChunkPos`.

Channel indices must match the class javadoc's layout: `[0]` elev, `[1]` blurredElev, `[2]` gradX, `[3]` gradY, `[4]` refinedGrad, `[5]` lowFreqGrad, `[6]` res. Note `fillRes` is channel **6**, not 5.

- [ ] **Step 6: Add the biome chunk fillers**

In `BiomeProvider.java`, add beside the existing `fillErosion`/`fillWeirdness` in the "Per-chunk channel producers" section:

```java
    /** Continentalness for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillContinentalness(ChunkPos pos) {
        return ChunkChannelFill.bilinear(
                final_tiles, BiomeChannels.CONTINENTALNESS.channel, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** Temperature for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillTemperature(ChunkPos pos) {
        return ChunkChannelFill.bilinear(
                final_tiles, BiomeChannels.TEMPERATURE.channel, pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** Vegetation/humidity for the 16x16 blocks of {@code pos}, indexed {@code x*16 + z}. */
    public float[] fillVegetation(ChunkPos pos) {
        return ChunkChannelFill.bilinear(
                final_tiles, BiomeChannels.HUMIDITY.channel, pos.getMinBlockX(), pos.getMinBlockZ());
    }
```

Add the import `me.batata_1.fractal_terrain.storage.ChunkChannelFill`.

- [ ] **Step 7: Rewire the `Types` entries**

In `FractalTerrainHeightmap.java`, replace the nine converted constants:

```java
        ELEVATION(getReliefProvider()::fillElev),
        REFINED_GRAD(getReliefProvider()::fillRefinedGrad),
        RES(getReliefProvider()::fillRes),
        BLURRED_ELEV(getReliefProvider()::fillBlurredElev),
        GRAD_X(getReliefProvider()::fillGradX),
        GRAD_Y(getReliefProvider()::fillGradY),
        CONTINENTALNESS(getBiomeProvider()::fillContinentalness),
        EROSION(getBiomeProvider()::fillErosion),
        TEMPERATURE(getBiomeProvider()::fillTemperature),
        VEGETATION(getBiomeProvider()::fillVegetation),
        WEIRDNESS(getBiomeProvider()::fillWeirdness),
```

Delete the now-unused `fillBilinear` and `fillSmoothStep` private statics and the `java.util.function.Function`, `me.batata_1.fractal_terrain.math.Interpolation` and `me.batata_1.fractal_terrain.FractalTerrainConfig` imports if nothing else in the file uses them.

Update the `Types` javadoc paragraph that describes the fill helpers, since they no longer live here:

```java
     * <p>Relief channels (elevation, gradients, residual) and the climate channels
     * (continentalness…weirdness) are produced by their own provider, each of which reads one tensor
     * window per chunk through {@code ChunkChannelFill} rather than sampling per block. The climate and
     * gradient channels upscale bilinearly; {@link #ELEVATION} uses smoothstep to match the legacy
     * {@code getHeight}.
```

- [ ] **Step 8: Run the acceptance checks**

Run: `gradle spotlessApply`, then `git diff --stat` (confirm scope), then `gradle build`, then `gradle test`.
Expected: build succeeds; 105 tests, 20 failed, 1 skipped — same 20, same messages.

- [ ] **Step 9: Document the new storage file**

Add a row to the Files table in `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md`:

```markdown
| `ChunkChannelFill.java` | One tensor-window read per chunk channel, then an allocation-free upscale | Changing how a heightmap channel is sampled or its pixel-window bounds |
```

- [ ] **Step 10: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java \
        src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java \
        src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java \
        src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java \
        src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java
git commit -m "perf: fill heightmap channels from one window read per chunk

Replaces 9216 per-pixel getValue calls per chunk with nine single-channel
slices, upscaled allocation-free. Output is bit-identical and the set of
tiles touched is unchanged, both asserted.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Biome density functions

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java` (the three nested density classes)
- Test: `src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java` (extend — add the transform cases)

**Interfaces:**
- Consumes: `ChunkChannelFill.bilinear`, `ChunkChannelFill.sliceForChunk`, `ChunkChannelFill.originPixel`, `ChunkChannelFill.lastPixel` (Task 4); `Interpolation.sampleWindowBilinear` (Task 3).
- Produces: `ChunkChannelFill.bilinearTransformed(NonIntersectingInfiniteTensor, int ch, int minBlockX, int minBlockZ, FloatUnaryOp op) -> float[]` and the nested `ChunkChannelFill.FloatUnaryOp` interface.

**The trap this task exists to avoid.** `WeirdnessDensity` interpolates `Math.abs(value)` and `Math.signum(value)` — the transform is applied to **each corner before interpolation**, not to the interpolated result. Sampling the raw slice and then taking `abs` is a different function and would silently change generated terrain. The fix is to transform the small window array once, then sample it normally, which is bit-identical because the transform still lands on each corner.

- [ ] **Step 1: Write the failing test**

Append to `ChunkChannelFillTest.java`:

```java
    /** Per-corner transform reference: the transform lands on the corners, never on the result. */
    private static float[] referenceTransformedFill(
            NonIntersectingInfiniteTensor t, int ch, int minBlockX, int minBlockZ, java.util.function.DoubleUnaryOperator op) {
        final float[] heights = new float[1 << 8];
        final int[] mutableCoords = new int[3];
        final float[] mutableNodes = new float[4];
        final Function<int[], Float> source = p -> {
            p[0] = ch;
            return (float) op.applyAsDouble(t.getValue(p));
        };
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final float px = (dx + minBlockX) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
                final float pz = (dz + minBlockZ) / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
                heights[(dx << 4) + dz] =
                        (float) Interpolation.interpolateBilinear(px, pz, mutableCoords, mutableNodes, source);
            }
        }
        return heights;
    }

    @Test
    void transformedFillAppliesTheOpPerCornerNotToTheResult() {
        for (int[] origin : ORIGINS) {
            final float[] expectedAbs =
                    referenceTransformedFill(tensor(new LinkedHashSet<>()), 2, origin[0], origin[1], Math::abs);
            final float[] actualAbs = ChunkChannelFill.bilinearTransformed(
                    tensor(new LinkedHashSet<>()), 2, origin[0], origin[1], Math::abs);
            assertArrayEquals(expectedAbs, actualAbs, 0.0f, "abs at " + origin[0] + "," + origin[1]);

            final float[] expectedSign =
                    referenceTransformedFill(tensor(new LinkedHashSet<>()), 2, origin[0], origin[1], Math::signum);
            final float[] actualSign = ChunkChannelFill.bilinearTransformed(
                    tensor(new LinkedHashSet<>()), 2, origin[0], origin[1], Math::signum);
            assertArrayEquals(expectedSign, actualSign, 0.0f, "signum at " + origin[0] + "," + origin[1]);
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*ChunkChannelFillTest*"`
Expected: FAIL — `bilinearTransformed` is not defined.

- [ ] **Step 3: Add the transformed fill**

Append to `ChunkChannelFill.java`:

```java
    /** Per-element transform applied to a window before sampling. */
    @FunctionalInterface
    public interface FloatUnaryOp {
        float apply(float value);
    }

    /** Bilinear upscale with {@code op} applied to every window pixel first.
     *
     * <p>Applying the transform to the window rather than the sampled result is not an optimisation —
     * it is the existing semantics. {@code WeirdnessDensity} interpolates {@code abs} and {@code signum}
     * of the corners, and {@code op(lerp(a, b))} is not {@code lerp(op(a), op(b))}. */
    public static float[] bilinearTransformed(
            NonIntersectingInfiniteTensor tensor, int ch, int minBlockX, int minBlockZ, FloatUnaryOp op) {
        final float scale = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;
        final int px0 = originPixel(minBlockX);
        final int pz0 = originPixel(minBlockZ);
        final int height = lastPixel(minBlockZ) - pz0 + 1;

        // :PERF: the window is at most 5x5, so transforming it in place beats transforming four corners
        // per block; dataUnsafe() is safe here for the same reason as in fill().
        final float[] window = sliceForChunk(tensor, ch, minBlockX, minBlockZ).dataUnsafe();
        for (int i = 0; i < window.length; i++) window[i] = op.apply(window[i]);

        final float[] out = new float[SPAN * SPAN];
        for (int dx = 0; dx < SPAN; dx++) {
            final float px = (dx + minBlockX) / scale;
            for (int dz = 0; dz < SPAN; dz++) {
                final float pz = (dz + minBlockZ) / scale;
                out[(dx << 4) + dz] = (float) Interpolation.sampleWindowBilinear(window, px, pz, px0, pz0, height);
            }
        }
        return out;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "*ChunkChannelFillTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Convert `ErosionDensity.fillArray(float[], ChunkPos)`**

Replace that method in `BiomeProvider.ErosionDensity`:

```java
        public void fillArray(float[] erosionMap, ChunkPos pos) {
            final float[] raw = ChunkChannelFill.bilinear(
                    FractalTerrainInstance.getBiomeProvider().final_tiles,
                    channel,
                    pos.getMinBlockX(),
                    pos.getMinBlockZ());
            for (int i = 0; i < raw.length; i++) {
                final double res = raw[i];
                erosionMap[i] = (float) ((SHATTERED_LO < res && res < SHATTERED_HI) ? res + SHATTERED_PUSH : res);
            }
        }
```

This requires `ErosionDensity` to retain its channel index. Add a `private final int channel;` field assigned in the constructor from the existing `ch` parameter.

The band nudge stays **after** sampling, exactly as `sample(int, int)` applies it today — it is a function of the interpolated value, not of the corners.

- [ ] **Step 6: Convert `WeirdnessDensity.fillArray(float[], ChunkPos)`**

Replace that method in `BiomeProvider.WeirdnessDensity`, adding a `private final int channel;` field the same way:

```java
        public void fillArray(float[] weirdnessMap, ChunkPos pos) {
            final NonIntersectingInfiniteTensor tiles = FractalTerrainInstance.getBiomeProvider().final_tiles;
            final int minX = pos.getMinBlockX();
            final int minZ = pos.getMinBlockZ();
            // Magnitude and sign are interpolated separately (see the class javadoc), each transformed
            // per corner — hence two transformed fills over the same channel, not one raw fill.
            final float[] magnitude = ChunkChannelFill.bilinearTransformed(tiles, channel, minX, minZ, Math::abs);
            final float[] sign = ChunkChannelFill.bilinearTransformed(tiles, channel, minX, minZ, Math::signum);
            for (int i = 0; i < magnitude.length; i++) {
                weirdnessMap[i] = (sign[i] >= 0) ? magnitude[i] : -magnitude[i];
            }
        }
```

- [ ] **Step 7: Convert the `fillArray(double[], ContextProvider)` overloads**

For each of `BiomeProviderDensity`, `ErosionDensity` and `WeirdnessDensity`, the positions come from `applier.forIndex(i)` and are arbitrary, not a chunk rectangle. Take two passes over the batch — bound, then sample — and fall back to the per-point path when the bound is implausibly large. Add to `BiomeProviderDensity` (and mirror the shape in the other two, applying their own post-processing):

```java
        /** Batches wider than a chunk's pixel window are not what this path sees in practice; falling
         *  back keeps a pathological batch from allocating an unbounded slice. */
        private static final int MAX_BATCH_SPAN_BLOCKS = 64;

        @Override
        public void fillArray(double[] densities, @NotNull ContextProvider applier) {
            if (densities.length == 0) return;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                minX = Math.min(minX, pos.blockX());
                maxX = Math.max(maxX, pos.blockX());
                minZ = Math.min(minZ, pos.blockZ());
                maxZ = Math.max(maxZ, pos.blockZ());
            }

            if (maxX - minX >= MAX_BATCH_SPAN_BLOCKS || maxZ - minZ >= MAX_BATCH_SPAN_BLOCKS) {
                for (int i = 0; i < densities.length; i++) {
                    densities[i] = compute(applier.forIndex(i));
                }
                return;
            }

            final NonIntersectingInfiniteTensor tiles = FractalTerrainInstance.getBiomeProvider().final_tiles;
            final int px0 = ChunkChannelFill.originPixel(minX);
            final int pz0 = ChunkChannelFill.originPixel(minZ);
            final int height = ChunkChannelFill.lastPixel(maxZ - 15) - pz0 + 1;
            final float[] window = tiles.getSlice(
                            new int[] {channel, px0, pz0},
                            new int[] {channel + 1, ChunkChannelFill.lastPixel(maxX - 15) + 1, pz0 + height})
                    .dataUnsafe();

            final float scale = GLOBAL_SCALE_CORRECTION;
            for (int i = 0; i < densities.length; i++) {
                final FunctionContext pos = applier.forIndex(i);
                densities[i] = Interpolation.sampleWindowBilinear(
                        window, pos.blockX() / scale, pos.blockZ() / scale, px0, pz0, height);
            }
        }
```

`ChunkChannelFill.lastPixel` takes a chunk **origin** and adds 15 internally, so passing `maxX - 15` yields `ceil(maxX / scale)` — the bound this path actually needs. Leave `compute(FunctionContext)` untouched in all three classes; it is the single-point path and a slice is strictly more work there.

- [ ] **Step 8: Run the acceptance checks**

Run: `gradle spotlessApply`, then `git diff --stat` (confirm scope), then `gradle build`, then `gradle test`.
Expected: 106 tests, 20 failed, 1 skipped.

**Coverage gap, stated rather than papered over:** the `fillArray(double[], ContextProvider)` conversions have no JUnit coverage — `ContextProvider` is a Minecraft interface and the density classes are private nested types, so exercising them headlessly is not possible without restructuring beyond this plan's scope. They are covered by compilation and by the shared `ChunkChannelFill` primitives the previous steps test. Flag this in the review.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java \
        src/main/java/me/batata_1/fractal_terrain/storage/ChunkChannelFill.java \
        src/test/java/me/batata_1/fractal_terrain/storage/ChunkChannelFillTest.java
git commit -m "perf: slice-based sampling in the biome density functions

Erosion and weirdness chunk fills now read one window instead of 1024
and 2048 point lookups. Weirdness keeps its per-corner abs/signum
semantics by transforming the window, not the sampled result.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Relief and biome cache budgets

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/README.md`

**Interfaces:**
- Consumes: the 5-argument `NonIntersectingInfiniteTensor` constructor (Task 2).
- Produces: no new API. This is a runtime behaviour change only.

This task is last and separate on purpose: it is the only step that changes memory behaviour rather than arithmetic, so a reviewer can reject it while keeping the optimization.

- [ ] **Step 1: Budget the relief tensor**

In `ReliefProvider.java`, add beside the other geometry constants:

```java
    /** Cached relief tiles are 7 x 512 x 512 floats (7.34 MB each) and were previously never evicted.
     *  A tile spans 2560 blocks, so a whole render distance sits inside one to four tiles; eight leaves
     *  enough headroom that the FIFO (not LRU) eviction order in {@code Storage} cannot drop a live
     *  tile. Lowering this is what would break that. */
    private static final long TILE_CACHE_BYTES = 8L * RELIEF_CHANNELS * INNER * INNER * Float.BYTES;
```

and pass it:

```java
    public ReliefProvider(String path) {
        final_tiles = new NonIntersectingInfiniteTensor(
                path,
                "final_relief_tiles",
                new int[] {RELIEF_CHANNELS, INNER, INNER},
                this::buildReliefTile,
                TILE_CACHE_BYTES);
    }
```

- [ ] **Step 2: Budget the biome tensor**

In `BiomeProvider.java`, add beside the tile-channel constants:

```java
    /** Same reasoning as {@code ReliefProvider.TILE_CACHE_BYTES}: eight tiles against a working set of
     *  at most four, so FIFO eviction never reaches a tile still in use. */
    private static final long TILE_CACHE_BYTES = 8L * TILE_CHANNELS * 512 * 512 * Float.BYTES;
```

and pass it as the fifth argument to the `final_tiles` constructor call.

Leave `GlobalRiverProvider`, `DifferenceOfGaussians` and `LocalRiverProvider.carved` on the four-argument constructor: they are coarse and small, two of them are built with `path == null` in the debug harnesses where an eviction means a full recompute rather than a disk reload, and `carved` is scheduled for deletion.

- [ ] **Step 3: Run the acceptance checks**

Run: `gradle spotlessApply`, then `git diff --stat` (confirm scope), then `gradle build`, then `gradle test`.
Expected: 106 tests, 20 failed, 1 skipped.

- [ ] **Step 4: Document the budgets**

Add to the **Design Decisions** section of `src/main/java/me/batata_1/fractal_terrain/storage/README.md`:

```markdown
**Eviction order is FIFO, not LRU, and the budgets are sized around that.** `recordCachedEntry` runs
only when an entry is inserted (`persistAndRecord`, or the disk-reload branch of `loadInto`) and never
on a read, so `cachedEntryByteSizes` orders by insertion age rather than by last use — a tile read on
every chunk is still evicted once enough newer tiles land. `ReliefProvider` and `BiomeProvider` each
budget eight tiles (~59 MB) against a working set of at most four (a tile spans 2560 blocks, so a whole
render distance sits inside one to four tiles). That margin is load-bearing: lowering either budget
toward the working set is what would turn the FIFO order into a thrash. Every other
`NonIntersectingInfiniteTensor` stays unbounded via the four-argument constructor.
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/relief/ReliefProvider.java \
        src/main/java/me/batata_1/fractal_terrain/world/biome/BiomeProvider.java \
        src/main/java/me/batata_1/fractal_terrain/storage/README.md
git commit -m "fix: bound the relief and biome tile caches

Both accumulated 7.34 MB tiles for the life of the world; getSlice now
trims them to an eight-tile budget, sized well above the four-tile
working set because Storage evicts FIFO rather than LRU.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Final verification

After Task 6, before declaring the plan done:

- [ ] Run `gradle spotlessApply && gradle build && gradle test` from a clean state.
- [ ] Confirm the failure set is still the baseline 20, comparing **messages** in `build/test-results/test/*.xml`.
- [ ] Update the **Status** block in `src/test/java/me/batata_1/fractal_terrain/CLAUDE.md` with the new total and the measurement date, and add an `infinitetensor/` and `storage/` row to its Subdirectories table.
- [ ] Run `gradle runClient` and generate terrain around the origin and around block 2560 (a tile boundary), confirming no visible seam and no crash. This is the only check that exercises the `fillArray(double[], ContextProvider)` conversions from Task 5.

## Known coverage gaps

Carried from the spec and from Task 5, so a reviewer does not have to rediscover them:

- `compute(FunctionContext)` stays on `getValue` by decision. `Climate.Sampler.sample` calls `compute`, not `fillArray`, so the biome share of the win is smaller than the channel count suggests.
- `fillArray(double[], ContextProvider)` has no JUnit coverage (Minecraft interface, private nested classes). Covered only by compilation and the `runClient` check above.
- The provider fillers' `ChunkPos` unwrap is untested by JUnit by design; the tested boundary starts at the block origins.
