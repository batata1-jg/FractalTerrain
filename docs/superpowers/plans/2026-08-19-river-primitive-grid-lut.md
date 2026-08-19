# River-Primitive Grid LUT Carve Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace both river-carve stages (`PopulateNoiseStep.carveRiverColumns` and
`HydrologyProfileInprinter.carveRiverShells`) with one LUT-backed lattice pass that also emits the water
surface and a packed type mask.

**Architecture:** One static function, `HydrologyProfileInprinter.computeRiverGrid`, merges every river
primitive into a lattice of `(height, water, weight)` triples plus a `long` type mask, using a
per-primitive cross-section lookup table anchored on a global integer perp lattice. It never sees the
caller's ambient elevation, so both call sites recover their own result with one blend. The per-pixel
R-tree in the shell path disappears entirely.

**Tech Stack:** Java 21, Fabric 1.20.1, JUnit 5, Gradle (palantirJavaFormat via Spotless).

**Spec:** `docs/superpowers/specs/2026-08-18-river-primitive-grid-lut-design.md`

## Global Constraints

- **Read the guidelines before the first edit.** Root `CLAUDE.md` "Read the guidelines before
  implementing"; then `.claude/conventions/CLAUDE.md` and, from it, `documentation.md` (every docstring
  and comment), `structural.md` + `code-quality/` (all code), `performance.md` (Tasks 2-4 and 6-7 are
  hot paths), `intent-markers.md` (`:PERF:`/`:SCHEMA:` usage), `temporal.md` (comment tense). Also read
  the `README.md`/`CLAUDE.md` in each directory being edited, and `ARCHITECTURE.md` for the
  hot/cold line.
- **Docstring budgets** (`.claude/conventions/documentation.md`): 1 line for a field, 3 for a method,
  10 for a class. WHY and pipeline placement, never HOW.
- **Formatting:** run `gradle spotlessApply` before every commit. `gradle build` enforces
  `spotlessCheck`.
- **Gradle:** invoke the cached `gradle-9.2.1`, not the `gradle` on `PATH` (which is 8.14).
- **Frames:** all primitive geometry is in the relief-pixel frame. `GLOBAL_SCALE_CORRECTION = 5`
  converts blocks to relief pixels. `MAX_INFLUENCE_RADIUS = 64.0`, `PADDED = 514`.
- **Accumulator layout:** `acc` is stride 3, `[h, water, w]`, so lattice point `i` occupies
  `acc[3*i]`, `acc[3*i+1]`, `acc[3*i+2]`. Lattice index is `row * gridSize + col`; row is X, column is Z.
- **Weight naming:** lowercase `w` is one primitive's blend weight (loop-local). `weight` /
  uppercase `W` in prose is the accumulated weight in `acc[3*i+2]`. Do not conflate them.
- **Sentinel:** `HydrologicalFeature.NONE == -1L` means no primitive reached a lattice cell. `0L` is a
  valid packed value (`RIVER` + `RosgenType.A`) and must never be used as "empty".
- **Do not fold the buffer parameters back into a holder class passed to `computeRiverGrid`.** The spec
  section "Why no scratch class" rejects that deliberately. A caller-side holder is fine and Task 3
  builds one; the *function signature* takes plain arrays.

## Test baseline

The JUnit suite does **not** compile at `1d32c85`. Task 0 fixes that. After Task 0 the baseline is
**77 tests, 20 failed, 1 skipped**, measured at `def61ea` on 2026-08-19:

> `RosgenKeyTest` (6), `ConfluencePrimitiveTest` (4), `ChannelGeometryTest` (3), `LocalRiverGoldenTest` (2),
> `MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1), `CentrelineTest` (1)

Root `CLAUDE.md` records 74/19/1 from 2026-08-17 at `1d32c85`. That count is stale: `CentrelineTest`
changed in `354acd4`, adding three tests of which
`wedgedShortChannelNormalMatchesTrueThroughDirectionNotItsOwnChord` fails. It asserts which stencil
`Centreline.normalAt` walks — the normal's *direction*. `normalAt` still returns
`perpendicular(normalize(...))`, so the unit-length invariant this plan's `d = sqrt(tang² + perp²)`
depends on is intact, and the failure is unrelated to this work.

Every task after Task 0 must leave that set unchanged **except Task 7**, where `LocalRiverGoldenTest`
and `GlobalRiverGoldenTest` are expected to move (spec Behaviour change 2). Compare the *failure
messages* in `build/test-results/test/*.xml`, not just which test names fail.

## Decision needing your veto before Task 0

Task 0 **deletes four test files** that reference symbols absent from `src/main`. They cannot compile,
so they are already dead — but deleting them is a real loss in one case:
`SpatialIndexCorrectnessGoldenTest` encoded the `ImmutableQuadTree.findSection` tiling-bug contract. It
needs `RosgenProfile.riverInfluence(double)`, which does not exist and is out of this plan's scope to
reinstate. Recorded in Follow-ups. If you would rather revive it than delete it, say so before Task 0.

## File Structure

| File | Responsibility | Task |
| --- | --- | --- |
| `src/test/.../profile/NearestChannelSampleTest.java` | **Delete** — references removed `NearestChannelSample` | 0 |
| `src/test/.../profile/BlendMinTest.java` | **Delete** — same | 0 |
| `src/test/.../profile/PolylineChordErrorTest.java` | **Delete** — same | 0 |
| `src/test/.../SpatialIndexCorrectnessGoldenTest.java` | **Delete** — needs absent `RosgenProfile.riverInfluence` | 0 |
| `.../hydrology/features/HydrologicalPrimitive.java` | Adds `HydrologicalFeature.pack`/`unpack`/`NONE` | 1 |
| `.../hydrology/profile/RosgenProfile.java` | Adds `sampleCrossSection` | 2 |
| `.../hydrology/profile/HydrologyProfileInprinter.java` | Adds `computeRiverGrid`, `maxLutLen`, `GridBuffers`; rewrites `carveRiverShells` | 3, 4, 7 |
| `.../storage/FractalTerrainHeightmap.java` | Retypes `Types.RIVER_TYPE` to `long[]` | 5 |
| `.../world/gen/populatenoise/PopulateNoiseStep.java` | Rewrites `fineGrainedPrimitivePass`; deletes three methods and five constants | 6 |
| `.../hydrology/LocalRiverProvider.java` | Sorts primitives, drops `.toArray` | 7 |
| Docs (5 files) | Bring prose in line | 8 |

---

### Task 0: Unblock the JUnit suite

**Files:**
- Delete: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSampleTest.java`
- Delete: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/BlendMinTest.java`
- Delete: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/PolylineChordErrorTest.java`
- Delete: `src/test/java/me/batata_1/fractal_terrain/hydrology/SpatialIndexCorrectnessGoldenTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: a compiling `:compileTestJava`, which every later task's TDD cycle depends on.

- [ ] **Step 1: Confirm the failure is the documented one, not something new**

```bash
gradle build 2>&1 | grep -E "compileTestJava|error:" | head -40
```

Expected: `:compileTestJava` fails with 32 errors, all in the four files above, naming
`NearestChannelSample`, a 3-arg `sampleNearestChannel`, or `RosgenProfile.riverInfluence`.
If you see errors in *any other* file, STOP and report — the baseline has drifted.

- [ ] **Step 2: Confirm `libs/onnxruntime/teste.jar` is present**

```bash
ls libs/onnxruntime/
```

Expected: `teste.jar` listed. `libs/` is git-ignored; without it you get ~132 phantom errors that look
like real breakage. If missing, STOP and ask.

- [ ] **Step 3: Delete the four files**

```bash
git rm src/test/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSampleTest.java \
       src/test/java/me/batata_1/fractal_terrain/hydrology/profile/BlendMinTest.java \
       src/test/java/me/batata_1/fractal_terrain/hydrology/profile/PolylineChordErrorTest.java \
       src/test/java/me/batata_1/fractal_terrain/hydrology/SpatialIndexCorrectnessGoldenTest.java
```

- [ ] **Step 4: Record the baseline**

```bash
gradle test
```

Expected: the run FAILS overall. (Measured outcome: **77 tests, 20 failed, 1 skipped** — see the Test
baseline section for why this task's expectation of 74/19/1 was stale.) That is the baseline, not
a regression. Save the evidence for later comparison:

```bash
cp -r build/test-results/test .superpowers/sdd/2026-08-19-river-primitive-grid-lut/baseline-test-results
```

If the counts differ from 74/19/1, STOP and report the actual numbers — every later task compares
against them.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test: drop four test files that reference removed symbols

They reference a deleted NearestChannelSample record, a 3-arg
sampleNearestChannel that is now 5-arg and void, and a
RosgenProfile.riverInfluence that does not exist. :compileTestJava has
failed on them since 1d32c85, so no test in the suite could run.

SpatialIndexCorrectnessGoldenTest encoded the ImmutableQuadTree.findSection
tiling contract; reviving it needs riverInfluence back and is tracked
separately.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 1: Packed type mask

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java` (the nested `HydrologicalFeature` enum, after the last constant)
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalFeaturePackTest.java` (create)

**Interfaces:**
- Consumes: the existing `HydrologicalFeature` enum.
- Produces:
  - `long HydrologicalFeature.NONE` — the `-1L` "nothing reached this cell" sentinel
  - `long HydrologicalFeature.pack(int subOrdinal)` — instance method
  - `@Nullable HydrologicalFeature HydrologicalFeature.unpack(long packed)` — static, `null` for `NONE`
  - `int HydrologicalFeature.unpackSub(long packed)` — static

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalFeaturePackTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the family/sub-type packing the carve writes into Types.RIVER_TYPE. */
class HydrologicalFeaturePackTest {

    @Test
    void roundTripsFamilyAndSubType() {
        final long packed = HydrologicalFeature.RIVER.pack(RosgenType.C.ordinal());
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(packed));
        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(packed));
    }

    @Test
    void roundTripsEveryFamilyAtSubTypeZero() {
        // ordinal 0 in both words packs to 0L, which must not be confused with NONE.
        for (final HydrologicalFeature feature : HydrologicalFeature.values()) {
            final long packed = feature.pack(0);
            assertEquals(feature, HydrologicalFeature.unpack(packed));
            assertEquals(0, HydrologicalFeature.unpackSub(packed));
        }
    }

    @Test
    void theEmptySentinelIsNotAValidFamily() {
        // A zero-filled buffer reads as RIVER + RosgenType.A, so -1L is what "untouched" must be.
        assertNull(HydrologicalFeature.unpack(HydrologicalFeature.NONE));
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(0L));
    }

    @Test
    void subTypeDoesNotBleedIntoTheFamilyWord() {
        // A negative sub-ordinal must stay in the low word rather than sign-extending over the family.
        final long packed = HydrologicalFeature.CONFLUENCE.pack(-1);
        assertEquals(HydrologicalFeature.CONFLUENCE, HydrologicalFeature.unpack(packed));
        assertEquals(-1, HydrologicalFeature.unpackSub(packed));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "*HydrologicalFeaturePackTest*"`
Expected: compilation FAILS — `cannot find symbol: method pack(int)`.

- [ ] **Step 3: Write minimal implementation**

In `HydrologicalPrimitive.java`, inside the `HydrologicalFeature` enum, after the final constant's
closing `;` and alongside the other members:

```java
/** Cached so unpacking a lattice cell does not clone the constant array per call. */
private static final HydrologicalFeature[] VALUES = values();

/** What a lattice cell holds when no primitive reached it. Not {@code 0L} — that is a valid
 *  packed value ({@code RIVER} + {@code RosgenType.A}), so a zero-filled buffer would read as river. */
public static final long NONE = -1L;

/** Packs this family with a family-specific sub-classification into one lattice cell. Same split as
 *  {@link RiverPrimitive#ids}: family in the high word, sub-type in the low. */
public long pack(int subOrdinal) {
    return (((long) ordinal()) << 32) | (subOrdinal & 0xFFFFFFFFL);
}

/** The family in a {@link #pack}ed cell, or {@code null} for {@link #NONE}. */
@Nullable
public static HydrologicalFeature unpack(long packed) {
    final int ordinal = (int) (packed >>> 32);
    return ordinal < 0 || ordinal >= VALUES.length ? null : VALUES[ordinal];
}

/** The family-specific sub-classification in a {@link #pack}ed cell. */
public static int unpackSub(long packed) {
    return (int) packed;
}
```

`org.jetbrains.annotations.Nullable` is already imported in this file.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "*HydrologicalFeaturePackTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalFeaturePackTest.java
git commit -m "feat: pack primitive family and sub-type into one long

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: `RosgenProfile.sampleCrossSection`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java` (add after the hoisted-extents `delta` overload, around line 227)
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/SampleCrossSectionTest.java` (create)

**Interfaces:**
- Consumes: the existing 6-arg `RosgenProfile.delta(long, double, double, double, double, double)`.
- Produces:
  ```java
  public void sampleCrossSection(
          float[] lut, int n, double step, int baseIdx, long seed, double elevation,
          double floodPlainLen, double marginLen, double depth, double curvature)
  ```
  `lut[i]` holds the channel surface at signed perpendicular distance `(baseIdx + i) * step`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/SampleCrossSectionTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the cross-section LUT the grid carve tabulates once per primitive. */
class SampleCrossSectionTest {

    private static final long SEED = 0L;
    private static final double ELEVATION = 100.0;
    private static final double FLOOD_PLAIN_LEN = 1.2;
    private static final double MARGIN_LEN = 1.0;
    private static final double DEPTH = 3.0;
    private static final double CURVATURE = 0.0;

    @Test
    void everyEntryMatchesDeltaAtItsAnchoredPerpDistance() {
        final RosgenProfile profile = RosgenProfile.A;
        final int baseIdx = -5;
        final int n = 12;
        final double step = 1.0;
        final float[] lut = new float[n];

        profile.sampleCrossSection(
                lut, n, step, baseIdx, SEED, ELEVATION, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);

        for (int i = 0; i < n; i++) {
            final double perp = (baseIdx + i) * step;
            final double expected =
                    ELEVATION + profile.delta(SEED, perp, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);
            assertEquals((float) expected, lut[i], "entry " + i + " at perp " + perp);
        }
    }

    @Test
    void writesNothingPastN() {
        // The buffer is a reused, oversized scratch array; the carve must not depend on its tail.
        final float[] lut = new float[16];
        java.util.Arrays.fill(lut, Float.NaN);

        RosgenProfile.C.sampleCrossSection(
                lut, 4, 1.0, 0, SEED, ELEVATION, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);

        for (int i = 4; i < lut.length; i++) {
            assertEquals(Float.NaN, lut[i], "entry " + i + " was overwritten");
        }
    }

    @Test
    void aNegativeBaseIdxSamplesTheNegativeSideOfTheChannel() {
        // baseIdx is floor(perpMin / step) and is normally negative; index 0 is the far bank, not the centre.
        final float[] lut = new float[3];
        RosgenProfile.A.sampleCrossSection(
                lut, 3, 1.0, -1, SEED, ELEVATION, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);

        final double centre =
                ELEVATION + RosgenProfile.A.delta(SEED, 0.0, FLOOD_PLAIN_LEN, MARGIN_LEN, DEPTH, CURVATURE);
        assertEquals((float) centre, lut[1], "index 1 should be perp 0");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "*SampleCrossSectionTest*"`
Expected: compilation FAILS — `cannot find symbol: method sampleCrossSection`.

- [ ] **Step 3: Write minimal implementation**

In `RosgenProfile.java`, immediately after the hoisted-extents `delta` overload:

```java
/**
 * Tabulates this profile's cross-section into {@code lut}: {@code lut[i]} is the channel surface at
 * signed perpendicular distance {@code (baseIdx + i) * step}, with the primitive's base elevation
 * folded in. Runs once per primitive per grid, so the branchy per-region logic in {@link #delta}
 * leaves the per-lattice-point loop entirely.
 */
public void sampleCrossSection(
        float[] lut,
        int n,
        double step,
        int baseIdx,
        long seed,
        double elevation,
        double floodPlainLen,
        double marginLen,
        double depth,
        double curvature) {
    for (int i = 0; i < n; i++) {
        lut[i] = (float)
                (elevation + delta(seed, (baseIdx + i) * step, floodPlainLen, marginLen, depth, curvature));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "*SampleCrossSectionTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/SampleCrossSectionTest.java
git commit -m "feat: tabulate a Rosgen cross-section into an anchored LUT

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: `computeRiverGrid` — elevation and weight lanes

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java` (create)

**Interfaces:**
- Consumes: `RosgenProfile.sampleCrossSection` (Task 2).
- Produces:
  - `int HydrologyProfileInprinter.computeRiverGrid(double startX, double startZ, double resolution, int gridSize, List<HydrologicalPrimitive> primitives, float[] acc, long[] typeMask, float[] dist, float[] lut)`
  - `int HydrologyProfileInprinter.maxLutLen(int gridSize, double resolution)`
  - `HydrologyProfileInprinter.GridBuffers` — caller-side holder with public fields `acc`, `typeMask`, `dist`, `lut` and `void ensure(int points, int lutLen)`
  - `double HydrologyProfileInprinter.UNSET_MIN_DIST` (64.0), `double SMOOTH_STEP_DIVISOR` (0.1)

  This task writes the `h` and `weight` lanes only. `acc[3*i+1]` stays `0` and `typeMask` stays
  `NONE`; Task 4 fills them.

**Note on `GridBuffers`:** it is a convenience for call sites, holding the size logic in one place. It
is **not** a parameter of `computeRiverGrid` — callers unpack its fields at the call. See Global
Constraints.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the lattice carve. The geometry is chosen so every assertion lands on an exact LUT
 * entry: normal (1,0) makes perp == (x - cx), and resolution 1.0 with integer coords makes every
 * sampled perp an integer multiple of the LUT step, so linear interpolation is exact.
 */
class ComputeRiverGridTest {

    private static final int GRID = 16;
    private static final double RES = 1.0;
    private static final int POINTS = GRID * GRID;

    /** A straight knot whose normal points along +X, centred on the lattice. */
    private static RiverPrimitive knot(double cx, double elevation, RosgenType type, long ids) {
        return new RiverPrimitive(
                new double[] {cx, 8.0}, 5.0, type, new double[] {1.0, 0.0}, 0.0, 2.0, elevation, ids);
    }

    private static HydrologyProfileInprinter.GridBuffers buffers() {
        final HydrologyProfileInprinter.GridBuffers b = new HydrologyProfileInprinter.GridBuffers();
        b.ensure(POINTS, HydrologyProfileInprinter.maxLutLen(GRID, RES));
        return b;
    }

    private static int idx(int row, int col) {
        return row * GRID + col;
    }

    @Test
    void carvesTheChannelCentreToTheProfileSurface() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        // perp == 0 and tang == 0 at (8, 8): the primitive owns the cell outright.
        final int centre = idx(8, 8);
        assertEquals(1.0f, b.acc[3 * centre + 2], 1e-6f, "weight at the centre");
        // RosgenProfile.delta returns a flat -10 inside marginLen (width 2 -> marginLen 1).
        assertEquals(90.0f, b.acc[3 * centre], 1e-4f, "height at the centre");
    }

    @Test
    void leavesLatticePointsOutsideTheInfluenceUntouched() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        // influence is 5, so (0, 0) is out of range on both axes.
        final int corner = idx(0, 0);
        assertEquals(0.0f, b.acc[3 * corner + 2], "weight in the corner");
        assertEquals(0.0f, b.acc[3 * corner], "height in the corner");
    }

    @Test
    void theNearerPrimitiveWinsRegardlessOfItsElevation() {
        // Both reach (9, 8); B sits on it, A is one pixel away. B must own the cell even though it is
        // the higher primitive -- the merge is distance-driven, not elevation-driven.
        final RiverPrimitive a = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive b2 = knot(9.0, 200.0, RosgenType.A, 1L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(a, b2), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(190.0f, b.acc[3 * idx(9, 8)], 1e-4f);
    }

    @Test
    void reseedsSoASecondCallDoesNotCompound() {
        final HydrologyProfileInprinter.GridBuffers b = buffers();
        final List<HydrologicalPrimitive> one = List.of(knot(8.0, 100.0, RosgenType.A, 0L));

        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, one, b.acc, b.typeMask, b.dist, b.lut);
        final float first = b.acc[3 * idx(8, 8)];
        HydrologyProfileInprinter.computeRiverGrid(0, 0, RES, GRID, one, b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(first, b.acc[3 * idx(8, 8)], "buffers are reused across calls and must be reseeded");
    }

    @Test
    void stopsAtTheFirstNonRiverPrimitiveAndReportsWhere() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologicalPrimitive source =
                new me.batata_1.fractal_terrain.hydrology.features.SourcePrimitive(new double[] {8.0, 8.0});
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        final int stop = HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river, source), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(1, stop, "the river run ends at index 1");
    }

    @Test
    void skipsAPrimitiveWithNoTangent() {
        // A null normal has no cross-section; carving it would NPE in the projection.
        final RiverPrimitive noNormal =
                new RiverPrimitive(new double[] {8.0, 8.0}, 5.0, RosgenType.A, null, 0.0, 2.0, 100.0, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(noNormal), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(0.0f, b.acc[3 * idx(8, 8) + 2]);
    }

    @Test
    void lutIsLongEnoughForAPrimitiveSpanningTheWholeDiagonal() {
        // A 45-degree normal maximises the reachable perp range; the LUT must not overflow.
        final RiverPrimitive diagonal = new RiverPrimitive(
                new double[] {8.0, 8.0},
                64.0,
                RosgenType.A,
                new double[] {Math.sqrt(0.5), Math.sqrt(0.5)},
                0.0,
                2.0,
                100.0,
                0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(diagonal), b.acc, b.typeMask, b.dist, b.lut);

        assertTrue(b.acc[3 * idx(8, 8) + 2] > 0.0f, "the diagonal primitive should still carve");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "*ComputeRiverGridTest*"`
Expected: compilation FAILS — `cannot find symbol: class GridBuffers`, `method computeRiverGrid`.

- [ ] **Step 3: Write minimal implementation**

In `HydrologyProfileInprinter.java`, add these imports: `java.util.Arrays` (already present),
`me.batata_1.fractal_terrain.FractalTerrainConfig`, `me.batata_1.fractal_terrain.config.HydrologyTuning`,
`me.batata_1.fractal_terrain.hydrology.ChannelGeometry`.

Add a new section above the existing "Tile-level shell pre-carve" section:

```java
// -------------------------------------------------------------------------
// Lattice carve (shared by the bed and shell stages)
// -------------------------------------------------------------------------

/** Per-lattice-point "no primitive seen yet" distance, in relief-pixels. */
public static final double UNSET_MIN_DIST = 64;

/** Blend width of the distance smoothstep. At 0.1 the weight is effectively a hard 0/1 selector;
 *  HydrologyTuning.PRIMITIVE_BLEND_STRENGTH is what belongs here once the real blend is restored. */
public static final double SMOOTH_STEP_DIVISOR = 0.1;

/**
 * The buffers {@link #computeRiverGrid} writes, bundled so each call site keeps one sizing rule
 * rather than four. Deliberately not a parameter of the carve itself — see the design spec's
 * "Why no scratch class": a second primitive family needs its own {@code acc} against the same grid.
 *
 * <p>Not thread-safe by construction. Chunk generation is multithreaded, so each thread owns one.
 */
public static final class GridBuffers {
    public float[] acc = new float[0];
    public long[] typeMask = new long[0];
    public float[] dist = new float[0];
    public float[] lut = new float[0];

    /** Grows any buffer that is too small. Never shrinks — the carve fills only the range it uses. */
    public void ensure(int points, int lutLen) {
        if (acc.length < 3 * points) acc = new float[3 * points];
        if (typeMask.length < points) typeMask = new long[points];
        if (dist.length < points) dist = new float[points];
        if (lut.length < lutLen) lut = new float[lutLen];
    }
}

/** Longest cross-section table any primitive can need on this grid: a primitive cannot span more
 *  perp than the grid's diagonal, nor more than its own influence diameter. */
public static int maxLutLen(int gridSize, double resolution) {
    final int diagonal = (int) Math.ceil((gridSize - 1) * Math.sqrt(2.0));
    final int influence = (int) Math.ceil(2 * HydrologyTuning.MAX_INFLUENCE_RADIUS / resolution);
    return Math.min(diagonal, influence) + 3;
}

/**
 * Merges every river primitive into a lattice of (height, water, weight) triples in {@code acc},
 * plus the nearest primitive's packed type in {@code typeMask}. Ambient-free by construction: a
 * caller recovers its carved elevation as {@code (1 - w) * ambient + w * min(h, ambient)}.
 *
 * <p>{@code primitives} MUST be sorted by {@link HydrologicalPrimitive#comparator}. The merge is a
 * sequential recurrence, so order is load-bearing for determinism, and the loop stops at the first
 * non-{@link RiverPrimitive} entry — which lands at the true end of the river run only because that
 * comparator orders by feature-type ordinal first.
 *
 * @return the index of that first non-river primitive, where a later family pass resumes
 */
public static int computeRiverGrid(
        double startX,
        double startZ,
        double resolution,
        int gridSize,
        List<HydrologicalPrimitive> primitives,
        float[] acc,
        long[] typeMask,
        float[] dist,
        float[] lut) {
    final int points = gridSize * gridSize;
    Arrays.fill(acc, 0, 3 * points, 0f);
    Arrays.fill(typeMask, 0, points, HydrologicalPrimitive.HydrologicalFeature.NONE);
    Arrays.fill(dist, 0, points, (float) UNSET_MIN_DIST);

    int stop = 0;
    while (stop < primitives.size() && primitives.get(stop) instanceof RiverPrimitive river) {
        carvePrimitive(river, startX, startZ, resolution, gridSize, acc, typeMask, dist, lut);
        stop++;
    }

    // The height lane accumulates weighted, so it needs one division to become a surface. The water
    // lane does not: its blend default is 0, and (1 - W) * 0 + W * (acc / W) is the raw accumulator.
    for (int i = 0; i < points; i++) {
        final float weight = acc[3 * i + 2];
        if (weight > 0) acc[3 * i] /= weight;
    }
    return stop;
}

/** One primitive's contribution, clipped to the lattice points its footprint reaches. Primitive-outer
 *  so the profile, the seed, the width-invariant extents and the LUT are computed once, not per point. */
private static void carvePrimitive(
        RiverPrimitive river,
        double startX,
        double startZ,
        double resolution,
        int gridSize,
        float[] acc,
        long[] typeMask,
        float[] dist,
        float[] lut) {
    final double[] normal = river.normal();
    // A null normal has no tangent -- river.h() returns flat elevation and the projection would NPE.
    if (normal == null) return;
    final double nx = normal[0], nz = normal[1];
    final double cx = river.coord()[0], cz = river.coord()[1];
    final double influence = river.influence();

    // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow one
    // would silently drop carve -- the exact containment test still runs per lattice point.
    final double halfExtent = influence * (Math.abs(nx) + Math.abs(nz));
    final long rowLo = (long) Math.floor((cx - halfExtent - startX) / resolution);
    final long rowHi = (long) Math.ceil((cx + halfExtent - startX) / resolution);
    final long colLo = (long) Math.floor((cz - halfExtent - startZ) / resolution);
    final long colHi = (long) Math.ceil((cz + halfExtent - startZ) / resolution);
    if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
    final int rowMin = (int) Math.max(rowLo, 0);
    final int rowMax = (int) Math.min(rowHi, gridSize - 1);
    final int colMin = (int) Math.max(colLo, 0);
    final int colMax = (int) Math.min(colHi, gridSize - 1);

    // perp is affine in the lattice coordinates, so its extrema over the clipped box are at the four
    // corners. Intersecting with the influence band is what caps the LUT at the grid diagonal.
    final double x0 = startX + rowMin * resolution, x1 = startX + rowMax * resolution;
    final double z0 = startZ + colMin * resolution, z1 = startZ + colMax * resolution;
    final double p00 = nx * (x0 - cx) + nz * (z0 - cz);
    final double p01 = nx * (x0 - cx) + nz * (z1 - cz);
    final double p10 = nx * (x1 - cx) + nz * (z0 - cz);
    final double p11 = nx * (x1 - cx) + nz * (z1 - cz);
    final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influence);
    final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influence);
    if (perpMin > perpMax) return;

    final double invStep = 1.0 / resolution;
    final int baseIdx = (int) Math.floor(perpMin * invStep);
    final int n = (int) Math.floor(perpMax * invStep) - baseIdx + 2;

    final double width = river.width();
    final double curvature = river.curvature();
    final double elevation = river.elevation();
    final RosgenProfile profile = (RosgenProfile) river.getProfile();
    final long seed = river.ids();
    final double floodPlainLen = profile.floodPlainLength(width);
    final double marginLen = width / 2;
    final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depthForWidth(width);
    profile.sampleCrossSection(
            lut, n, resolution, baseIdx, seed, elevation, floodPlainLen, marginLen, depth, curvature);

    for (int row = rowMin; row <= rowMax; row++) {
        final double ddx = (startX + row * resolution) - cx;
        final double ddz0 = (startZ + colMin * resolution) - cz;
        double perp = nx * ddx + nz * ddz0;
        double tang = ddx * nz - ddz0 * nx;
        double f = perp * invStep - baseIdx;
        final int rowBase = row * gridSize;
        for (int col = colMin; col <= colMax; col++) {
            final int i = rowBase + col;
            final double d = Math.sqrt(tang * tang + perp * perp);
            final double mask = Math.abs(tang) <= influence && Math.abs(perp) <= influence ? 1.0 : 0.0;
            final double t = Math.clamp(((dist[i] - d) / SMOOTH_STEP_DIVISOR + 1) * 0.5, 0, 1);
            final double w = t * t * (3.0 - 2.0 * t) * mask;
            // Clamped for safety only: mask already zeroes anything out of band, but the branch-free
            // body still evaluates h for those lanes.
            final int i0 = Math.clamp((int) f, 0, n - 2);
            final double h = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);

            dist[i] = (float) ((1 - w) * dist[i] + w * d);
            final int a = 3 * i;
            acc[a] = (float) ((1 - w) * acc[a] + w * h);
            acc[a + 2] = (float) (acc[a + 2] + w * (1 - acc[a + 2]));

            perp += nz * resolution;
            tang -= nx * resolution;
            f += nz;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "*ComputeRiverGridTest*"`
Expected: PASS, 7 tests.

If `carvesTheChannelCentreToTheProfileSurface` fails on the height, print the LUT and check `baseIdx`:
the anchoring is what makes `f` land exactly on an integer at perp 0.

- [ ] **Step 5: Confirm the suite baseline is unmoved**

Run: `gradle test`
Expected: **91 tests, 20 failed, 1 skipped** — the Task 0 baseline of 77, plus Task 1's 4, Task 2's 3
and this task's 7, with the same 20 failures. Compare `build/test-results/test/*.xml` against the saved
baseline for the failure *messages*.

- [ ] **Step 6: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java
git commit -m "feat: merge river primitives into a LUT-backed lattice

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `computeRiverGrid` — water lane and type mask

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java` (`carvePrimitive` only)
- Modify: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java`

**Interfaces:**
- Consumes: `HydrologicalFeature.pack`/`unpack`/`unpackSub`/`NONE` (Task 1), `computeRiverGrid` (Task 3).
- Produces: no signature change. `acc[3*i+1]` now holds the merged water surface and `typeMask[i]` the
  nearest primitive's packed type.

- [ ] **Step 1: Write the failing tests**

Append to `ComputeRiverGridTest.java` (the `knot` helper and `buffers()`/`idx()` already exist):

```java
    @Test
    void mergesTheWaterSurfaceWithoutNormalisingIt() {
        // waterLine(2.0) is -2, so the surface sits two below the primitive's own elevation. The water
        // lane blends toward a default of 0, which makes the raw accumulator the answer -- no divide.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(98.0f, b.acc[3 * idx(8, 8) + 1], 1e-4f, "water surface at the centre");
        assertEquals(0.0f, b.acc[3 * idx(0, 0) + 1], "water surface out of range");
    }

    @Test
    void stampsTheNearestPrimitivesFamilyAndRosgenType() {
        // RosgenType.C so the packed value is non-zero -- RIVER + A packs to 0L and would not
        // distinguish a real stamp from an unwritten cell.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.C, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        final long packed = b.typeMask[idx(8, 8)];
        assertEquals(HydrologicalFeature.RIVER, HydrologicalFeature.unpack(packed));
        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(packed));
        assertEquals(HydrologicalFeature.NONE, b.typeMask[idx(0, 0)], "nothing reached the corner");
    }

    @Test
    void anUnclassifiedReachStampsTheProfileItActuallyCarvedWith() {
        // A null rosgenType coalesces to A for the carve, so the mask must say A rather than "unknown".
        final RiverPrimitive river = knot(8.0, 100.0, null, 0L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(river), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(RosgenType.A.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(8, 8)]));
    }

    @Test
    void theTypeMaskFollowsTheNearestPrimitiveNotTheFirst() {
        final RiverPrimitive a = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverPrimitive c = knot(9.0, 100.0, RosgenType.C, 1L);
        final HydrologyProfileInprinter.GridBuffers b = buffers();

        HydrologyProfileInprinter.computeRiverGrid(
                0, 0, RES, GRID, List.of(a, c), b.acc, b.typeMask, b.dist, b.lut);

        assertEquals(RosgenType.C.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(9, 8)]));
        assertEquals(RosgenType.A.ordinal(), HydrologicalFeature.unpackSub(b.typeMask[idx(7, 8)]));
    }
```

Add these imports to the test file:

```java
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle test --tests "*ComputeRiverGridTest*"`
Expected: the four new tests FAIL — water reads `0.0` and the mask reads `NONE` (`-1`) at the centre.

- [ ] **Step 3: Write minimal implementation**

In `carvePrimitive`, after the `depth` line and before `sampleCrossSection`:

```java
    final float waterSurface = (float) (elevation + HydrologicalPrimitive.waterLine(width));
    final long packed = HydrologicalPrimitive.HydrologicalFeature.RIVER.pack(
            RiverPrimitive.RosgenType.orDefault(river.rosgenType()).ordinal());
```

In the inner loop, add the water update beside the height update and the mask store after it:

```java
            acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
```

```java
            // Whoever owns the majority of the blend owns the type. With SMOOTH_STEP_DIVISOR at 0.1
            // the weight is a near-hard selector, so this is the true nearest bar a 0.1-wide band.
            typeMask[i] = w > 0.5 ? packed : typeMask[i];
```

Place `acc[a + 1]` between the `acc[a]` and `acc[a + 2]` lines so the triple is written in order.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle test --tests "*ComputeRiverGridTest*"`
Expected: PASS, 11 tests.

- [ ] **Step 5: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java
git commit -m "feat: emit the water surface and nearest-primitive type from the carve

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Retype `Types.RIVER_TYPE` to `long[]`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java:53`
- Test: `src/test/java/me/batata_1/fractal_terrain/storage/RiverTypeLayerTest.java` (create)

**Interfaces:**
- Consumes: `HydrologicalFeature.NONE` (Task 1).
- Produces: `Types.RIVER_TYPE.creator()` yields a `long[256]`; `Types.RIVER_TYPE.get(payload, x, z)`
  throws `UnsupportedOperationException` rather than mis-casting to `float[]`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/storage/RiverTypeLayerTest.java`:

```java
package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import org.junit.jupiter.api.Test;

/** Unit tests for the packed river-type layer the bed carve writes. */
class RiverTypeLayerTest {

    @Test
    void allocatesOneLongPerColumn() {
        // The creator ignores its ChunkPos argument, so this needs no world.
        final Object payload = Types.RIVER_TYPE.creator().apply(null);
        assertInstanceOf(long[].class, payload);
        assertEquals(256, ((long[]) payload).length);
    }

    @Test
    void refusesTheFloatColumnAccessor() {
        // Types.get casts to float[]; letting RIVER_TYPE through it would be a silent ClassCastException
        // at some unrelated call site instead of a named failure here.
        final Object payload = Types.RIVER_TYPE.creator().apply(null);
        assertThrows(UnsupportedOperationException.class, () -> Types.RIVER_TYPE.get(payload, 0, 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "*RiverTypeLayerTest*"`
Expected: FAIL — `allocatesOneLongPerColumn` gets a `HydrologicalFeature[]`, and
`refusesTheFloatColumnAccessor` throws `ClassCastException`, not `UnsupportedOperationException`.

- [ ] **Step 3: Write minimal implementation**

Replace line 53 of `FractalTerrainHeightmap.java`:

```java
        RIVER_TYPE(pos -> new HydrologicalPrimitive.HydrologicalFeature[1 << 8]) {},
```

with:

```java
        // :SCHEMA: packed by HydrologicalFeature.pack -- family in the high word, sub-type in the low.
        // Recomputed per chunk rather than persisted, so the layout is free to change.
        RIVER_TYPE(pos -> new long[1 << 8]) {
            @Override
            public float get(Object payload, int localX, int localZ) {
                throw new UnsupportedOperationException("RIVER_TYPE is a long[]; read the raw payload");
            }
        },
```

Remove the now-unused `HydrologicalPrimitive` import if nothing else in the file uses it.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle test --tests "*RiverTypeLayerTest*"`
Expected: PASS, 2 tests.

- [ ] **Step 5: Confirm nothing else read the layer**

```bash
grep -rn "RIVER_TYPE" src/main src/client
```

Expected: only the declaration and the unused local in `PopulateNoiseStep.java:72-74`. If anything else
appears, STOP and report — the spec assumed there were no consumers.

- [ ] **Step 6: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java \
        src/test/java/me/batata_1/fractal_terrain/storage/RiverTypeLayerTest.java
git commit -m "refactor: retype the river-type heightmap layer to packed long

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Rewrite the bed carve call site

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/PopulateNoiseStep.java`

**Interfaces:**
- Consumes: `computeRiverGrid`, `maxLutLen`, `GridBuffers` (Tasks 3-4); the `long[]` layer (Task 5).
- Produces: `fineGrainedPrimitivePass` now writes `RIVER_DIFFERENCE`, `ELEVATION`, `WATER_HEIGHT` and
  `RIVER_TYPE`. `carveRiverColumns`, `blendDistance`, `blendElevation`, `PAIR`, `WEIGHT`,
  `FULL_WEIGHT`, `SMOOTH_STEP_DIVISOR` and `UNSET_MIN_DIST` no longer exist.

This task has no new unit test: it is a call-site rewrite whose behaviour is covered by Task 3-4's tests
plus the suite baseline. Its verification is compilation, the unchanged baseline, and a client run.

- [ ] **Step 1: Delete the three methods and five constants**

Delete from `PopulateNoiseStep.java`: `carveRiverColumns`, `blendDistance`, `blendElevation`, and the
`PAIR`, `WEIGHT`, `FULL_WEIGHT`, `SMOOTH_STEP_DIVISOR`, `UNSET_MIN_DIST` fields (including the
"Testing overrides" comment above `SMOOTH_STEP_DIVISOR` — that note moved to `SMOOTH_STEP_DIVISOR`'s new
home in `HydrologyProfileInprinter`). Keep `COLUMNS`, `smoothStep`, and `fillRocks`.

Drop the now-unused imports: `ChannelGeometry`, `RiverPrimitive`, `RosgenProfile`, `Interpolation`.

- [ ] **Step 2: Add the per-thread buffers**

```java
/** Lattice geometry for a chunk: 16x16 blocks, one block being 1 / GLOBAL_SCALE_CORRECTION relief-pixels. */
private static final int GRID_SIZE = 16;

private static final double GRID_RESOLUTION = 1.0 / FractalTerrainConfig.GLOBAL_SCALE_CORRECTION;

/** One instance of this class serves every chunk-generation thread, so the carve buffers cannot be fields. */
private static final ThreadLocal<HydrologyProfileInprinter.GridBuffers> BUFFERS =
        ThreadLocal.withInitial(() -> {
            final HydrologyProfileInprinter.GridBuffers buffers = new HydrologyProfileInprinter.GridBuffers();
            buffers.ensure(COLUMNS, HydrologyProfileInprinter.maxLutLen(GRID_SIZE, GRID_RESOLUTION));
            return buffers;
        });
```

- [ ] **Step 3: Replace the body from the scratch allocation onward**

Delete the `mergedElevation`/`smoothedMinDist` allocation, the seeding loop, the primitive loop and the
final `dx`/`dz` double loop. In their place:

```java
        final HydrologyProfileInprinter.GridBuffers buffers = BUFFERS.get();
        final float[] acc = buffers.acc;
        HydrologyProfileInprinter.computeRiverGrid(
                chunkPos.getMinBlockX() / scale,
                chunkPos.getMinBlockZ() / scale,
                GRID_RESOLUTION,
                GRID_SIZE,
                primitives,
                acc,
                buffers.typeMask,
                buffers.dist,
                buffers.lut);

        for (int pos = 0; pos < COLUMNS; pos++) {
            final float ambient = interpolatedElevs[pos];
            final float weight = acc[3 * pos + 2];
            // acc[3 * pos] is already normalised, so the blend does not divide again. The min is what
            // keeps the carve cut-only; it is applied once here rather than per primitive.
            final double merged = weight > 0
                    ? (1 - weight) * ambient + weight * Math.min(acc[3 * pos], ambient)
                    : ambient;
            riverDifference[pos] = (float) (merged - ambient);
            waterElev[pos] = weight > 0 ? (float) (acc[3 * pos + 1] + seaLevel - 1) : 0f;
            riverType[pos] = buffers.typeMask[pos];
            interpolatedElevs[pos] = (float) (Math.max(bottom, merged) + seaLevel - 1);
        }
```

Change the `riverType` local's declaration from
`final HydrologicalPrimitive.HydrologicalFeature[] riverType = (HydrologicalPrimitive.HydrologicalFeature[]) ...`
to `final long[] riverType = (long[]) heightmap.get(Types.RIVER_TYPE);`.

Delete the `startX`/`startZ` locals if nothing else uses them.

- [ ] **Step 4: Build**

Run: `gradle spotlessApply && gradle build`
Expected: BUILD SUCCESSFUL apart from the pre-existing test failures. `:compileJava`,
`:compileClientJava` and `:spotlessCheck` must all pass.

- [ ] **Step 5: Confirm the suite baseline is unmoved**

Run: `gradle test`
Expected: the same 20 failures with the same messages as the saved baseline. This task must
not move the golden tests — they exercise `LocalRiverProvider`/`GlobalNetworkBuilder`, not
`PopulateNoiseStep`. **If a golden test moves here, STOP and report:** it means the bed path is
reachable from the goldens and the change is bigger than the spec assumed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/PopulateNoiseStep.java
git commit -m "refactor: carve the bed through the lattice pass

Also populates WATER_HEIGHT and RIVER_TYPE, both of which the pass has
fetched and discarded until now.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Rewrite the shell carve

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java` (`carveRiverShells`)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java:166-169`

**Interfaces:**
- Consumes: `computeRiverGrid`, `maxLutLen`, `GridBuffers` (Tasks 3-4).
- Produces: `carveRiverShells(float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize)`.
  `ImmutableRTree`, the per-pixel `double[]` point, the `queryContaining` lists and `CARVE_INDEX_SLACK`
  are gone.

**This is the task that changes generated worlds** (spec Behaviour change 2). `LocalRiverGoldenTest` and
`GlobalRiverGoldenTest` are *expected* to move here and nowhere else.

- [ ] **Step 1: Rewrite `carveRiverShells`**

Replace the whole method and the `CARVE_INDEX_SLACK` constant with:

```java
/** One instance of this class serves every tile build, so the carve buffers cannot be fields. */
private static final ThreadLocal<GridBuffers> SHELL_BUFFERS = ThreadLocal.withInitial(GridBuffers::new);

/** Carves the valley shell in place. Does not zone — the shell is one broad pull, applied before any
 *  primitive has a bed to distinguish. Compounds across calls on the same buffer. */
public static void carveRiverShells(
        float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize) {
    if (primitives.isEmpty()) return;
    final int points = paddedSize * paddedSize;
    final GridBuffers buffers = SHELL_BUFFERS.get();
    buffers.ensure(points, maxLutLen(paddedSize, 1.0));
    final float[] acc = buffers.acc;

    computeRiverGrid(
            0, 0, 1.0, paddedSize, primitives, acc, buffers.typeMask, buffers.dist, buffers.lut);

    for (int i = 0; i < points; i++) {
        final float ambient = elevation[i];
        if (ambient < 0) continue;
        final float weight = acc[3 * i + 2];
        if (weight <= 1e-8f) continue;
        elevation[i] = (float) ((1 - weight) * ambient + weight * Math.min(acc[3 * i], ambient));
    }
}
```

Delete the `ImmutableRTree` and `VectorOps` imports if nothing else in the file uses them
(`sampleNearestChannel` still uses `VectorOps` — check before removing).

- [ ] **Step 2: Update the call site**

In `LocalRiverProvider.java`, replace lines 166-169:

```java
        HydrologyProfileInprinter.carveRiverShells(
                carvedElevationGlobal,
                collectPrimitives(network, rawElev, 0, 0).toArray(new HydrologicalPrimitive[0]),
                PADDED);
```

with:

```java
        // computeRiverGrid's merge is a sequential recurrence, so the order is load-bearing for
        // determinism; collectPrimitives does not guarantee it.
        final List<HydrologicalPrimitive> shellPrimitives = collectPrimitives(network, rawElev, 0, 0);
        shellPrimitives.sort(HydrologicalPrimitive.comparator);
        HydrologyProfileInprinter.carveRiverShells(carvedElevationGlobal, shellPrimitives, PADDED);
```

`collectPrimitives` delegates to `RiverNetwork.collectPrimitives`, which builds a `new ArrayList<>`
(`RiverNetwork.java:738`), so sorting it in place is safe.

- [ ] **Step 3: Build**

Run: `gradle spotlessApply && gradle build`
Expected: `:compileJava`, `:compileClientJava`, `:spotlessCheck` pass.

- [ ] **Step 4: Run the suite and classify every moved test**

Run: `gradle test`

Expected: **20 failures still, but `LocalRiverGoldenTest` (2) and `GlobalRiverGoldenTest` (1) now fail
with different messages** — different carved elevations, per Behaviour change 2. Diff against the
baseline:

```bash
diff -r .superpowers/sdd/2026-08-19-river-primitive-grid-lut/baseline-test-results build/test-results/test | head -60
```

Every message change must be in `LocalRiverGoldenTest` or `GlobalRiverGoldenTest`. **If any other test's
message changed, STOP and report which** — nothing else should be reachable from the shell carve.

- [ ] **Step 5: Visual check**

```bash
gradle globalRiverTest
gradle localRiverTest
```

Then compare the PNGs under `run/debug/` against the pre-change dumps. Expect the shell to be visibly
distance-driven rather than footprint-weighted, and never above the surrounding terrain. Report what
changed; do not silently accept a shell that lost its valleys.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java
git commit -m "refactor: carve shells through the lattice pass, dropping the per-pixel R-tree

The shell adopts the bed's distance recurrence, so it loses its per-primitive
footprint weighting and becomes cut-only. That changes the drainage field the
local trace reads, so traced networks and generated worlds differ.
LocalRiverGoldenTest and GlobalRiverGoldenTest move as a result.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md`
- Modify: `ARCHITECTURE.md`

**Interfaces:**
- Consumes: everything Tasks 1-7 built.
- Produces: nothing code depends on.

Root `CLAUDE.md` routes documentation work to the `technical-writer` subagent. Dispatch it with the
briefing below; change only what the briefing names.

- [ ] **Step 1: Read the current state of each file**

```bash
cat src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md
cat src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md
cat src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md
cat src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md
grep -n "Hot sites in this repo" -A 30 ARCHITECTURE.md
```

- [ ] **Step 2: Dispatch `technical-writer` with this briefing**

> **Outcome:** the five files below describe the river carve as it now is — one LUT-backed lattice pass
> serving both stages — with no stale claims left.
>
> **Files to change (only these):**
> - `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md` — the shell/bed split is now
>   one function; the per-pixel R-tree is gone; the merge law is the distance recurrence with the
>   per-primitive footprint weighting removed; both stages are cut-only; the LUT smears the margin
>   discontinuity across one lattice cell. Also fix an existing error: it says `carveRiverShells` runs
>   twice per tile, but `LocalRiverProvider` calls it once.
> - `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md` — the
>   `HydrologyProfileInprinter` and `RosgenProfile` rows.
> - `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md` — the row names a
>   `resolveRiverColumns` that does not exist; it becomes the `computeRiverGrid` + ambient-blend
>   description, and should mention that the pass now writes `WATER_HEIGHT` and `RIVER_TYPE`.
> - `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md` — the `Types` row: `RIVER_TYPE` is a
>   `long[]` packed by `HydrologicalFeature.pack`, not a `HydrologicalFeature[]`.
> - `ARCHITECTURE.md`, "Hot sites in this repo" — fix the `PopulateNoiseStep` line/range, and delete the
>   `sampleNearestChannel`/`NearestChannelSample.carveInto` entry, which names symbols that are gone.
>
> **Guidelines to read yourself** (do not take these paraphrased): root `CLAUDE.md`; the `README.md` or
> `CLAUDE.md` in each directory you edit; `ARCHITECTURE.md`; `.claude/conventions/CLAUDE.md`, then
> `documentation.md` and `temporal.md`.
>
> **Design source:** `docs/superpowers/specs/2026-08-18-river-primitive-grid-lut-design.md`.
>
> **Scope:** documentation only. Do not touch any `.java` file, and do not rewrite surrounding prose the
> briefing does not name.
>
> **Acceptance:** `gradle spotlessApply` then `gradle build`.

- [ ] **Step 3: Read the returned diff before accepting it**

A subagent's claim is not evidence. Check each of the five files actually changed, that no `.java` file
did, and that the stale `carveRiverShells`-runs-twice claim is gone.

- [ ] **Step 4: Build and commit**

```bash
gradle spotlessApply && gradle build
git add -A
git commit -m "docs: describe the lattice carve that replaced both river stages

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Follow-ups (not in this plan)

- **Revive `SpatialIndexCorrectnessGoldenTest`.** Deleted in Task 0. It encoded the
  `ImmutableQuadTree.findSection` tiling contract and needs `RosgenProfile.riverInfluence(double)`
  reinstated. Per project policy a known bug gets an `@Disabled` contract test, never a golden over
  broken output.
- **Cross-family merge.** Oxbow, delta, waterfall and confluence primitives compute into their own
  buffers and merge in `fineGrainedPrimitivePass`'s main loop. If a family ever shares the river `dist`
  chain rather than running its own, the reseed in `computeRiverGrid` must move out to the caller.
- **`carveRiverShells` pays 3.2 MB** per thread for the water lane and type mask it does not read.
  Revisit if the tile grid grows past 514.
- **Restore a real `SMOOTH_STEP_DIVISOR`.** At 0.1 it is a hard 0/1 selector. Restoring
  `HydrologyTuning.PRIMITIVE_BLEND_STRENGTH` makes the type mask fuzzy and the water lane fade at the
  influence fringe at the same time; both are documented in the spec's Known residuals.
