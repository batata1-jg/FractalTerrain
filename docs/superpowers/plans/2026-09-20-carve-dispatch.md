# Carve Dispatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish `8bf6885`'s half-landed carve refactor so both carve passes dispatch on the primitive
instead of an `instanceof` chain and an enum, and the bed pass merges every family through one shared
distance buffer under one law.

**Architecture:** `HydrologicalPrimitive` gains four abstract hooks — `carveInfluence`, `carveBed`,
`tabulateBedLut` — and each family answers them off its own record fields. The per-pass cross-section
math moves into two sibling classes in `hydrology.carvers`, `InfluenceCarver` (shell) and `BedCarver`
(bed), each holding every footprint shape its pass can cut. What remains of `RiverInfluenceCarve` — the
thread-local buffers, the banding constants, the two orchestration entry points — becomes `LatticeCarve`.
The bed pass then drops its second ranking buffer and merges rivers and discs against one `dist[]`,
so list order alone decides which primitive owns a lattice point.

**Tech Stack:** Java 21, Fabric/Loom 1.14.10, Gradle 9.2.1, JUnit 5, palantirJavaFormat via Spotless.

**Spec:** `docs/superpowers/specs/2026-09-20-carve-dispatch-design.md`

---

## Global Constraints

Every task's requirements implicitly include this section.

**Read before the first edit of a task.** Per root `CLAUDE.md`'s "Read the guidelines before
implementing": this file's pointers, then `ARCHITECTURE.md` (this change crosses providers, frames and
the generation pipeline), then `.claude/conventions/CLAUDE.md` and from it `documentation.md`,
`structural.md`, `class-structure.md`, `performance.md`, `temporal.md`, `intent-markers.md`. Then the
`CLAUDE.md`/`README.md` in the directory being edited — `hydrology/profile/` and `hydrology/features/`
both have one. Do not paraphrase them here; read them.

**Gradle.** There is no checked-in wrapper and the `gradle` on PATH (8.14) is too old for Loom. Use:

```
C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat
```

referred to below as `$GRADLE`. Run it from the repo root. `--offline` works for `compileJava`,
`compileTestJava`, `test`, `spotlessApply`, `spotlessCheck`.

**DANGER — do not run `git restore`, `git checkout --`, `git stash`, or `git clean` in this repo.**
`docs/superpowers/specs/2026-09-17-primitive-grid-storage-design.md` and
`docs/superpowers/specs/2026-09-20-carve-dispatch-design.md` are staged as 0-byte blobs while the
worktree holds 24 KB and 29 KB of real text. Any of those commands silently replaces the real files with
nothing. `git add <path>` on a file you are about to touch is the only safe way to clear the trap.

**`libs/` is git-ignored.** If you work in a fresh worktree, copy `libs/onnxruntime/teste.jar` into it
first, or the build fails with ~132 phantom errors unrelated to this change.

**Test baseline is a claim, not a fact.** Root `CLAUDE.md` quotes "102 tests, 9 failed, 1 skipped" at
`df7ca2e`. `HEAD` (`8bf6885`) does not compile, so that number is unmeasured here. Task 1 measures it and
records the result; **every later task compares against Task 1's recorded messages**, in
`build/test-results/test/*.xml`, not against the quoted list. Comparing failure *messages*, not just test
names, is what proves a refactor left generation output untouched.

**Spotless is repo-wide.** `$GRADLE spotlessApply` reformats every Java file. Run it, then check
`git diff --stat` for reformats outside the task's scope before committing.

**Naming, fixed for the whole plan.** Use exactly these; they are what later tasks reference:

| Old                                    | New                          |
| -------------------------------------- | ----------------------------- |
| `RiverInfluenceCarve` (class)          | `LatticeCarve`                |
| `RiverBedCarver` (interface)           | `BedCarver` (final class)     |
| `InfluenceCarver` (enum)               | `InfluenceCarver` (final class, different contents) |
| `carveRiverInfluenceGrid`              | `carveInfluenceGrid`          |
| `computeRiverGrid`                     | `computeBedGrid`              |

**Terrain-change budget.** Tasks 1, 2, 3, 5 and 6 change no carved elevation. Task 4 is the only one
that does. If a golden moves in any task but 4, the task is wrong — do not re-baseline it.

**Package cycle.** `hydrology.features` importing `hydrology.carvers` already exists at `HEAD`
(`RosgenCarvedPrimitive` imports the `InfluenceCarver` enum). This plan widens it; it does not introduce
it. Do not try to break it here — that is the out-of-scope architectural question the spec names.

---

## File Structure

### Created

| File | Responsibility |
| ---- | -------------- |
| `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/CLAUDE.md` | Index for the `carvers` package |
| `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/README.md` | The two passes, the one merge law, the dispatch seam |
| `src/test/java/me/batata_1/fractal_terrain/hydrology/features/BedDispatchTest.java` | Per-family bed-carve contracts: what carves, what no-ops, what throws, and that the LUT hook matches the profile |

### Renamed

| From | To |
| ---- | -- |
| `hydrology/carvers/RiverInfluenceCarve.java` | `hydrology/carvers/LatticeCarve.java` |
| `hydrology/carvers/RiverBedCarver.java` | `hydrology/carvers/BedCarver.java` |

### Rewritten in place

| File | Becomes |
| ---- | ------- |
| `hydrology/carvers/InfluenceCarver.java` | The shell pass's cross-section repertoire (was a three-constant enum) |

### Deleted

| File | Why |
| ---- | --- |
| `hydrology/profile/HydrologyProfileInprinter.java` | One-method wrapper; its query inlines at its single call site (D17) |

### Modified

| File | What changes |
| ---- | ------------ |
| `hydrology/features/HydrologicalPrimitive.java` | Gains `carveInfluence`, `carveBed`, `tabulateBedLut`; loses `getInfluenceCarver` |
| `hydrology/features/RosgenCarvedPrimitive.java` | `carveInfluence` default wired to the rectangle shell carve |
| `hydrology/features/RadialPrimitive.java` | No-op `carveInfluence`; real `carveBed`/`tabulateBedLut` defaults |
| `hydrology/features/PositionOnlyPrimitive.java` | No-op defaults for all three hooks |
| `hydrology/features/RiverPrimitive.java` | Implements `RosgenCarvedPrimitive` again; real `carveBed`/`tabulateBedLut` |
| `hydrology/features/OxbowLakePrimitive.java` | `carveBed`/`tabulateBedLut` throw |
| `hydrology/features/AbandonedRiverPrimitive.java` | `carveInfluence` overrides the radial shell carve |
| `hydrology/profile/RadialProfile.java` | Two authored band control points |
| `hydrology/profile/RosgenProfile.java` | `LatticeCarve.BED_EDGE` / `FLOODPLAIN_EDGE` references |
| `world/gen/populatenoise/PopulateNoiseStep.java` | Builds a `BedGrid`; inlines the influence query |
| `GenerationContext.java`, `FractalTerrainInstance.java` | Lose the inprinter field/getter |
| `hydrology/GlobalNetworkBuilder.java`, `hydrology/LocalNetworkBuilder.java`, `hydrology/providers/RiverProvider.java` | Renamed shell entry point |
| `debug/tests/SpatialIndexBenchmark.java` | Loses a dead local |
| Tests listed per task | Rewritten where they assert on a deleted mechanism |

---

### Task 1: Close the compile break and make the shell pass dispatch on the primitive

Implements D1, D2, D3, D5. `HEAD` does not compile, so this task also establishes the test baseline
every later task is measured against.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java:33`, `:62-64`
- Rewrite: `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/InfluenceCarver.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/RiverInfluenceCarve.java` (delete `carveRosgenInfluence` and `carveRadialInfluence`; change the dispatch loop)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RosgenCarvedPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/PositionOnlyPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/AbandonedRiverPrimitive.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/InfluenceCarverDefaultsTest.java` (rewritten)
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RosgenCarvedPrimitiveCodecTest.java` (one method deleted)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `void HydrologicalPrimitive.carveInfluence(RiverInfluenceCarve.ShellGrid grid)` — abstract.
  - `public static void InfluenceCarver.carveRosgenInfluence(RosgenCarvedPrimitive primitive, RiverInfluenceCarve.ShellGrid grid)`
  - `public static void InfluenceCarver.carveRadialInfluence(RadialPrimitive primitive, RiverInfluenceCarve.ShellGrid grid)`
  - `RiverPrimitive implements RosgenCarvedPrimitive`, so `normal()`, `width()`, `curvature()`,
    `elevation()`, `rosgenType()`, `seed()` and the inherited `getProfile()` default are reachable
    through that type again.

- [ ] **Step 1: Snapshot git state before touching anything**

A developer agent has previously blamed its own out-of-scope deletions on pre-existing WIP. Record the
truth first.

```bash
git status --porcelain > /tmp/pre-task1-status.txt
git diff --cached --numstat > /tmp/pre-task1-staged.txt
cat /tmp/pre-task1-status.txt
```

- [ ] **Step 2: Restore `RosgenCarvedPrimitive` on `RiverPrimitive` (D1)**

In `RiverPrimitive.java`, change the `implements` clause:

```java
public record RiverPrimitive(
        double[] coord,
        double influence,
        RosgenType rosgenType,
        double[] normal,
        double curvature,
        double width,
        double elevation,
        long seed)
        implements RosgenCarvedPrimitive {
```

`RosgenCarvedPrimitive` already extends both `SpatialIndexRotatedRectangle` and
`HydrologicalPrimitive`, so no second interface is listed. Remove the now-unused
`import me.batata_1.fractal_terrain.math.ds.SpatialIndexRotatedRectangle;`.

**Do not add `getProfile()` to `HydrologicalPrimitive` (D3).** `RiverPrimitive.h()`'s
`(RosgenProfile) getProfile()` resolves through `RosgenCarvedPrimitive`'s own default once this step
lands; the only other caller reaches it the same way. Nothing calls it on the base type, and declaring
it there would put a profile on every family that has none.

- [ ] **Step 3: Delete the two malformed `carveBed` stubs**

In `RiverPrimitive.java`, delete these three lines and the `RiverBedCarver` import above them. The real
`carveBed` arrives in Task 3.

```java
    public void carveBed(float[] lut, float baseIdx , float[] acc , long ) {
        RiverBedCarver.carve();
    }
```

In `InfluenceCarver.java`, delete the dangling line `public void carveBed(HydrologicalPrimitive primitive)`.
The whole enum is replaced in Step 5, but deleting it here keeps the file parseable in between.

- [ ] **Step 4: Verify the build now reports the semantic breaks**

Run: `$GRADLE compileJava --offline --console=plain`

Expected: FAIL, with no syntax errors left. The spec predicts one survivor —
`RiverInfluenceCarve.java:61`'s `getInfluenceCarver()` on the base type. Record the actual list in the
task report; the build, not the spec, is the authority on whether that list is exhaustive.

- [ ] **Step 5: Replace the `InfluenceCarver` enum with the shell pass's carve class (D5)**

Overwrite `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/InfluenceCarver.java` entirely.
Cut both whole methods — javadoc, comments and all — out of `RiverInfluenceCarve.java` and paste them
here **verbatim**. Neither touches a private member of that class, so this is a pure move; the only
edit is widening each from package-private to `public`. Do not reproduce them from this plan; copy the
source.

Each method keeps its own inline tabulation (`profile.sampleCrossSection` / `sampleRadialSection`). The
shell pass gets **no** counterpart to D9's `tabulateBedLut` hook: that hook exists because one
`RadialPrimitive` bed default serves three families and would otherwise carry three tabulations, and
the shell pass has no such shared cut — its rectangle method serves two families through one
tabulation, its radial method one.

```java
package me.batata_1.fractal_terrain.hydrology.carvers;

import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.RadialPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RosgenCarvedPrimitive;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;

/**
 * Every cross-section the shell pass knows how to cut, reached from a primitive's own
 * {@code carveInfluence}. A footprint shape is a method here rather than a class of its own, so the
 * pass's whole repertoire reads in one place; {@link RiverBedCarver} is the bed pass's twin.
 */
public final class InfluenceCarver {

    private InfluenceCarver() {}

    public static void carveRosgenInfluence(RosgenCarvedPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
        // body moved verbatim from RiverInfluenceCarve.carveRosgenInfluence
    }

    public static void carveRadialInfluence(RadialPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
        // body moved verbatim from RiverInfluenceCarve.carveRadialInfluence
    }
}
```

Do not restructure or re-comment either body. The `:PERF:` markers, the null-normal guard, the
zero-extent guard and the NaN-elevation guard all travel with the code.

- [ ] **Step 6: Declare the hook on `HydrologicalPrimitive` and delete `getInfluenceCarver` (D2)**

In `HydrologicalPrimitive.java`, add `import me.batata_1.fractal_terrain.hydrology.carvers.RiverInfluenceCarve;`
and, next to `getType()`:

```java
    /** This primitive's contribution to the shell pass, cut into {@code grid}'s ambient buffer.
     *  Abstract rather than defaulted, so a new family cannot silently carve no shell. */
    void carveInfluence(RiverInfluenceCarve.ShellGrid grid);
```

The class javadoc's sentence "Carve behavior is split so the carve never switches on the concrete record
type: {@link #getProfile()} answers what cross-section to cut" names a method this interface does not
declare. Reword it to name `carveInfluence` and to say `getProfile()` lives on the shape interfaces.

- [ ] **Step 7: Give each shape interface its `carveInfluence` body (D2)**

`RosgenCarvedPrimitive.java` — replace the `getInfluenceCarver()` default with:

```java
    @Override
    default void carveInfluence(RiverInfluenceCarve.ShellGrid grid) {
        InfluenceCarver.carveRosgenInfluence(this, grid);
    }
```

`RadialPrimitive.java` — replace the `getInfluenceCarver()` default with:

```java
    /** A bowl contributes no shell influence: the shell is the valley a flow tangent cuts, and a disc
     *  has none. {@link AbandonedRiverPrimitive} is the one radial family that overrides this. */
    @Override
    default void carveInfluence(RiverInfluenceCarve.ShellGrid grid) {}
```

`PositionOnlyPrimitive.java` — replace the `getInfluenceCarver()` default with:

```java
    @Override
    default void carveInfluence(RiverInfluenceCarve.ShellGrid grid) {}
```

`AbandonedRiverPrimitive.java` — replace the `getInfluenceCarver()` override with:

```java
    @Override
    public void carveInfluence(RiverInfluenceCarve.ShellGrid grid) {
        InfluenceCarver.carveRadialInfluence(this, grid);
    }
```

Fix imports in all four: drop the enum import where it is no longer used, add
`me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver` and
`me.batata_1.fractal_terrain.hydrology.carvers.RiverInfluenceCarve` where they are now needed. Any
javadoc naming `InfluenceCarver.ROSGEN` / `.RADIAL` / `.NONE` names constants that no longer exist —
reword to describe the behaviour, without referring to what the code used to be (`temporal.md`).

- [ ] **Step 8: Point the shell dispatch loop at the hook**

In `RiverInfluenceCarve.carveRiverInfluenceGrid`, replace:

```java
        for (HydrologicalPrimitive primitive : primitives) {
            primitive.getInfluenceCarver().carveInfluence(primitive, grid);
        }
```

with:

```java
        for (HydrologicalPrimitive primitive : primitives) {
            primitive.carveInfluence(grid);
        }
```

- [ ] **Step 9: Rewrite `InfluenceCarverDefaultsTest` as a behaviour test**

The dispatch has no token left to compare, so the assertion moves onto the carved buffer. Replace the
file's body entirely:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.carvers.RiverInfluenceCarve;
import org.junit.jupiter.api.Test;

/** Which families cut a shell cross-section and which contribute none. Asserted on the carved buffer:
 *  the dispatch is a virtual call, so there is no token to compare. */
class InfluenceCarverDefaultsTest {

    private static final int PADDED = 16;

    private static float[] flat() {
        final float[] elev = new float[PADDED * PADDED];
        Arrays.fill(elev, 20f);
        return elev;
    }

    @Test
    void riverCutsARosgenCrossSection() {
        final float[] elev = flat();
        final RiverPrimitive river = new RiverPrimitive(
                new double[] {8.0, 8.0}, 5.0, RiverPrimitive.RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, 5.0);

        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(river), PADDED);

        assertTrue(elev[8 * PADDED + 8] < 20f, "the channel centre must be cut");
    }

    @Test
    void confluenceSourceDeltaAndWaterfallContributeNoShell() {
        for (final HydrologicalPrimitive primitive : List.<HydrologicalPrimitive>of(
                new ConfluencePrimitive(new double[] {8.0, 8.0}, 4.0, 5.0),
                new SourcePrimitive(new double[] {8.0, 8.0}, 4.0, 5.0),
                new DeltaPrimitive(new double[] {8.0, 8.0}),
                new WaterfallPrimitive(new double[] {8.0, 8.0}))) {
            final float[] elev = flat();
            final float[] before = elev.clone();

            RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(primitive), PADDED);

            assertArrayEquals(before, elev, 1e-6f, primitive.getType() + " must carve no shell");
        }
    }
}
```

- [ ] **Step 10: Drop the dispatch assertion from the codec test**

`RosgenCarvedPrimitiveCodecTest` asserts `InfluenceCarver.ROSGEN`, a constant that no longer exists. Its
two fixtures sit at coordinates off any small grid, so the method cannot be converted to a behaviour
assertion in place — and shell dispatch is not a codec test's concern. Delete the whole method:

```java
    @ParameterizedTest
    @MethodSource("primitives")
    void carvesTheShellAsARosgenCrossSection(RosgenCarvedPrimitive primitive) {
        assertEquals(
                InfluenceCarver.ROSGEN, primitive.getInfluenceCarver());
    }
```

Delete the now-unused `InfluenceCarver` import. `InfluenceCarverShellTest` and the rewritten
`InfluenceCarverDefaultsTest` carry the dispatch coverage between them: the rewritten test covers
`RIVER`, `CONFLUENCE`, `SOURCE`, `DELTA` and `WATERFALL`, and `InfluenceCarverShellTest` already covers
`OXBOW_LAKE` and `ABANDONED_RIVER` — every family, across the two files. Narrow the class javadoc from
"Persistence and rectangle geometry shared by every {@link RosgenCarvedPrimitive}" to persistence only,
since that is all the class still asserts.

- [ ] **Step 11: Format and build**

```bash
$GRADLE spotlessApply --offline
git diff --stat
$GRADLE build --offline --console=plain
```

Expected: PASS. Check `git diff --stat` for Spotless reformats outside this task's files before
continuing.

- [ ] **Step 12: Measure and record the test baseline**

```bash
$GRADLE test --offline --console=plain
grep -h "<failure\|<testsuite " build/test-results/test/*.xml > /tmp/task1-baseline.txt
```

Record in the task report: total, failed and skipped counts, plus the full failure message of every
failing test. **This is the comparison target for Tasks 2-6.** Do not fix pre-existing failures — root
`CLAUDE.md`'s Test section explains why three of them are known-wrong expectations rather than defects.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/InfluenceCarver.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/RiverInfluenceCarve.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/ \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/
git commit -m "refactor(hydrology): dispatch the shell carve on the primitive"
```

---

### Task 2: Rename the carve classes and give the bed pass one `band`

Implements D4's naming half, D6, D7. Mechanical throughout: no method body changes shape, no terrain
moves. The one substantive edit is collapsing the two copies of `band` that `8bf6885` left behind.

**Files:**
- Rename: `hydrology/carvers/RiverInfluenceCarve.java` → `hydrology/carvers/LatticeCarve.java`
- Rename: `hydrology/carvers/RiverBedCarver.java` → `hydrology/carvers/BedCarver.java`
- Modify: `hydrology/carvers/InfluenceCarver.java` (the `ShellGrid` type name)
- Modify: every `.java` naming either class or either entry point — find them in Step 1
- Test: `hydrology/profile/ComputeRiverGridTest.java`, `RadialCarveTest.java`, `InfluenceCarverShellTest.java`, `RiverPaintDepthTest.java`, `hydrology/features/InfluenceCarverDefaultsTest.java`

**Interfaces:**
- Consumes: Task 1's `carveInfluence` hook and `InfluenceCarver` class.
- Produces:
  - `LatticeCarve` — `UNSET_MIN_DIST`, `BED_EDGE`, `FLOODPLAIN_EDGE`, `maxLutLen`, `GridBuffers`,
    `ShellGrid`, `shellDistanceField`, `carveInfluenceGrid`, `computeBedGrid`.
  - `BedCarver` — `public final class`, private constructor; holds
    `static double band(double raw, double marginNorm, double floodPlainNorm, double bedSlope, double floodPlainSlope, double outerSlope)`
    and `static void carve(...)`.

- [ ] **Step 1: Enumerate every reference before renaming**

```bash
grep -rn "RiverInfluenceCarve\|RiverBedCarver\|carveRiverInfluenceGrid\|computeRiverGrid" src --include=*.java
```

Expected: roughly 20 main-source files and 6 test files. Keep the list — Step 6 checks it is empty.

- [ ] **Step 2: Rename `RiverInfluenceCarve` to `LatticeCarve` (D6)**

```bash
git mv src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/RiverInfluenceCarve.java \
       src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/LatticeCarve.java
```

Rename the class declaration, the private constructor, and every reference found in Step 1 —
`import` lines, qualified uses, `{@link}` and `{@code}` javadoc references, and plain-prose mentions in
comments. The class javadoc already reads "The stateless lattice carve shared by every carve call site",
so it needs no rewording beyond the two entry-point names Step 4 changes.

- [ ] **Step 3: Rename `RiverBedCarver` to `BedCarver` and make it a class (D4)**

```bash
git mv src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/RiverBedCarver.java \
       src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/BedCarver.java
```

`8bf6885` declared it `public interface` although nothing implements it and both members are `static`.
Make it the same shape as `InfluenceCarver`, which D5 requires the two to share:

```java
/**
 * Every cross-section the bed pass knows how to cut, reached from a primitive's own {@code carveBed}.
 * A footprint shape is a method here rather than a class of its own, so the pass's whole repertoire
 * reads in one place; {@link InfluenceCarver} is the shell pass's twin.
 */
public final class BedCarver {

    private BedCarver() {}
```

Add `static` to `band` and `carve` where the interface implied it.

- [ ] **Step 4: Rename both orchestration entry points (D7)**

In `LatticeCarve.java`: `carveRiverInfluenceGrid` → `carveInfluenceGrid`, `computeRiverGrid` →
`computeBedGrid`. Update the three shell call sites (`GlobalNetworkBuilder.java:155`,
`LocalNetworkBuilder.java:43`, `RiverProvider.java:233`), the one bed call site
(`PopulateNoiseStep.java:79`), every test call, and every javadoc reference — including
`RiverInfluenceCarve`'s own class javadoc, `ConfluencePrimitive`'s, `RadialPrimitive`'s,
`HydrologyProfileInprinter`'s and `HydrologyProfile.riverPaintDepth`'s `@param dist`.

- [ ] **Step 5: Collapse the duplicate `band` onto `BedCarver`**

Two copies exist. `LatticeCarve.band` takes six arguments with the three slopes hoisted per primitive;
`BedCarver.band` takes three and recomputes those slopes at every lattice point. The bed merge loop is
hot (`ARCHITECTURE.md` names `computeRiverGrid` and its per-primitive helpers below the line), so the
hoisted form is the one to keep.

Delete `BedCarver`'s three-argument `band` outright. Move `LatticeCarve`'s six-argument `band` to
`BedCarver` unchanged, keeping its javadoc and its `:PERF:` marker, and make it `static`:

```java
    /**
     * A raw footprint scale remapped onto the banded coordinate the paint side reads. Bed and floodplain
     * assert themselves in the merge, and a consumer classifies against {@link LatticeCarve#BED_EDGE}
     * and {@link LatticeCarve#FLOODPLAIN_EDGE} without access to the primitive.
     */
    // :PERF: six primitive parameters instead of a control-point object; this runs per lattice point,
    // and an object would allocate per primitive and dispatch per point.
    static double band(
            double raw,
            double marginNorm,
            double floodPlainNorm,
            double bedSlope,
            double floodPlainSlope,
            double outerSlope) {
        if (raw <= marginNorm) return raw * bedSlope;
        if (raw <= floodPlainNorm) return LatticeCarve.BED_EDGE + (raw - marginNorm) * floodPlainSlope;
        return LatticeCarve.FLOODPLAIN_EDGE + (raw - floodPlainNorm) * outerSlope;
    }
```

`BED_EDGE`, `FLOODPLAIN_EDGE` and `UNSET_MIN_DIST` stay on `LatticeCarve` — D6 names them as part of
what remains there, and `RosgenProfile.riverPaintDepth` and `RiverPaintDepthTest` read them from
outside the carvers package.

`BedCarver.carve` still has no caller (Task 3 wires it up), so change its body only where the deleted
three-argument `band` was called: compute the three slopes once, above the merge loop, exactly as
`LatticeCarve.carveRiverPrimitive` does today —

```java
        // :PERF: reciprocals hoisted per primitive; the merge loop below runs per lattice point and
        // carries no division. A zero denominator means the piece it scales is empty, so the slope is
        // never read and 0 keeps it finite.
        final double bedSlope = marginNorm > 0.0 ? LatticeCarve.BED_EDGE / marginNorm : 0.0;
        final double floodPlainSlope = floodPlainNorm > marginNorm
                ? (LatticeCarve.FLOODPLAIN_EDGE - LatticeCarve.BED_EDGE) / (floodPlainNorm - marginNorm)
                : 0.0;
        final double outerSlope =
                floodPlainNorm < 1.0 ? (1.0 - LatticeCarve.FLOODPLAIN_EDGE) / (1.0 - floodPlainNorm) : 0.0;
```

— and pass them into `band`.

- [ ] **Step 6: Point the test helper at the new home**

`ComputeRiverGridTest.bandOf` calls `RiverInfluenceCarve.band(...)`. It now calls `BedCarver.band(...)`;
the argument list is unchanged. The three `BED_EDGE`/`FLOODPLAIN_EDGE` reads in the same file, and the
six in `RiverPaintDepthTest`, become `LatticeCarve.BED_EDGE` / `LatticeCarve.FLOODPLAIN_EDGE`.

- [ ] **Step 7: Verify no reference survives**

```bash
grep -rn "RiverInfluenceCarve\|RiverBedCarver\|carveRiverInfluenceGrid\|computeRiverGrid" src --include=*.java
```

Expected: no output. Markdown files still carry the old names; Task 6 fixes those.

- [ ] **Step 8: Format, build, test**

```bash
$GRADLE spotlessApply --offline
$GRADLE build --offline --console=plain
$GRADLE test --offline --console=plain
```

Expected: build PASS; test results byte-identical to Task 1's recorded baseline. A rename cannot move a
number — any difference means a reference was rewritten wrongly.

- [ ] **Step 9: Commit**

```bash
git add -A src/
git commit -m "refactor(hydrology): rename the carve classes to LatticeCarve and BedCarver"
```

---

### Task 3: Dispatch the bed carve on the primitive

Implements D4's bed half, D8, D9, D10. The `instanceof` chain goes; the two bed cross-sections move into
`BedCarver`; LUT construction becomes its own per-primitive hook. **Terrain must not move**: the merge
law stays exactly as it is, `radialDist` included.

**Files:**
- Modify: `hydrology/carvers/LatticeCarve.java` (add `BedGrid`; rewrite `computeBedGrid`; delete `carveRiverPrimitive` and `carveRadialPrimitive`)
- Modify: `hydrology/carvers/BedCarver.java` (`carve` gains its caller and the LUT callback; new `carveRadial`)
- Modify: `hydrology/features/HydrologicalPrimitive.java` (two new abstract hooks)
- Modify: `hydrology/features/RiverPrimitive.java`, `RadialPrimitive.java`, `PositionOnlyPrimitive.java`, `OxbowLakePrimitive.java`
- Modify: `world/gen/populatenoise/PopulateNoiseStep.java`
- Test: `hydrology/profile/ComputeRiverGridTest.java`, `hydrology/profile/RadialCarveTest.java`, `hydrology/features/RadialPrimitiveCodecTest.java`
- Create: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/BedDispatchTest.java`

**Interfaces:**
- Consumes: Task 2's `LatticeCarve`, `BedCarver`, `BedCarver.band`.
- Produces:
  - `public record LatticeCarve.BedGrid(int gridSize, double startX, double startZ, double resolution,
    float[] acc, long[] typeMask, float[] dist, float[] radialDist, float[] lut, double[] perpRow,
    double[] perpCol, double[] tangRow, double[] tangCol, float[] elevs)`
  - `public static void LatticeCarve.computeBedGrid(BedGrid grid, List<HydrologicalPrimitive> primitives)` — returns `void`.
  - `void HydrologicalPrimitive.carveBed(LatticeCarve.BedGrid grid)` — abstract.
  - `void HydrologicalPrimitive.tabulateBedLut(float[] lut, int baseIdx, int n, double resolution)` — abstract.
  - `static void BedCarver.carve(HydrologicalPrimitive owner, LatticeCarve.BedGrid grid, double cx,
    double cz, double nx, double nz, double influenceLen, double influenceWidth, double marginLen,
    double floodPlainLen, float waterSurface, long type)`
  - `static void BedCarver.carveRadial(HydrologicalPrimitive owner, LatticeCarve.BedGrid grid, double cx,
    double cz, double radius, float waterSurface, long type)`

- [ ] **Step 1: Confirm the `OxbowLakePrimitive` throw is unreachable in production**

D10 makes `OxbowLakePrimitive.carveBed` throw, and `computeBedGrid` will call it unconditionally. Verify
the spec's claim before relying on it — a wrong answer here crashes chunk generation.

```bash
grep -n "saveHistory" src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java
grep -n "new RiverNetwork" src/main/java/me/batata_1/fractal_terrain/hydrology/GlobalNetworkBuilder.java
grep -rn "recordRemovedComplement\|recordAbandoned" src/main --include=*.java
```

Expected: the only production `RiverNetwork` construction uses the three-argument constructor, which
sets `saveHistory = false`; both mint sites are gated on that flag. Record what you find. If the
evidence does not hold, stop and report rather than adding a defensive skip — the spec explicitly
rejects one, so the decision has to be re-made rather than worked around.

- [ ] **Step 2: Add the `BedGrid` record to `LatticeCarve`**

Beside `ShellGrid`:

```java
    /** Per-call scratch the bed pass's primitive carvers share: the lattice frame, the merge buffers
     *  every primitive blends into, the per-primitive cross-section LUT, the projection scratch a
     *  rectangle carve tabulates, and the ambient field each contribution is capped against. One
     *  instance per carve call, which is once per chunk — above the per-column loop, not inside it. */
    public record BedGrid(
            int gridSize,
            double startX,
            double startZ,
            double resolution,
            float[] acc,
            long[] typeMask,
            float[] dist,
            float[] radialDist,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs) {}
```

- [ ] **Step 3: Replace `computeBedGrid`'s body with one sorted walk (D8)**

```java
    /**
     * Merges every primitive touching the lattice into one (height, water, weight) triple per point in
     * {@code grid.acc()}, plus the winning primitive's packed type in {@code grid.typeMask()}.
     *
     * <p>{@code primitives} MUST be sorted by {@link HydrologicalPrimitive#comparator}. The merge is a
     * sequential recurrence over one shared ranking buffer, so the caller's sort is what decides which
     * primitive owns a lattice point — a contract, not a convenience.
     */
    public static void computeBedGrid(BedGrid grid, List<HydrologicalPrimitive> primitives) {
        final int points = grid.gridSize() * grid.gridSize();
        Arrays.fill(grid.acc(), 0, 3 * points, 0f);
        Arrays.fill(grid.typeMask(), 0, points, HydrologicalPrimitive.HydrologicalFeature.NONE);
        Arrays.fill(grid.dist(), 0, points, (float) UNSET_MIN_DIST);
        Arrays.fill(grid.radialDist(), 0, points, (float) UNSET_MIN_DIST);

        for (final HydrologicalPrimitive primitive : primitives) {
            primitive.carveBed(grid);
        }
    }
```

The walk reproduces today's two-arm order exactly: `comparator` sorts by family ordinal, `RIVER` is 0
and is the only family `RiverPrimitive` carries, so every river is contiguous at the front — which is
what today's `while (... instanceof RiverPrimitive)` run relies on. The radial families keep their
relative order behind them.

Seeding `radialDist` up front rather than between the two arms is equivalent: nothing reads it before
the first radial primitive writes it.

Then delete the private `carveRiverPrimitive` and `carveRadialPrimitive` methods — Steps 4 and 5 are
where their bodies land.

- [ ] **Step 4: Finish `BedCarver.carve` and hand it the LUT callback (D9)**

`carve` already holds the rectangle clip and the merge loop. Three changes.

First, replace the thirteen buffer/frame parameters with the record, and swap `(float[] lut, int baseIdx)`
for the primitive that owns the table:

```java
    static void carve(
            HydrologicalPrimitive owner,
            LatticeCarve.BedGrid grid,
            double cx,
            double cz,
            double nx,
            double nz,
            double influenceLen,
            double influenceWidth,
            double marginLen,
            double floodPlainLen,
            float waterSurface,
            long type) {
        final int gridSize = grid.gridSize();
        final double startX = grid.startX();
        final double startZ = grid.startZ();
        final double resolution = grid.resolution();
        final float[] acc = grid.acc();
        final long[] typeMask = grid.typeMask();
        final float[] dist = grid.dist();
        final float[] lut = grid.lut();
        final double[] perpRow = grid.perpRow();
        final double[] perpCol = grid.perpCol();
        final double[] tangRow = grid.tangRow();
        final double[] tangCol = grid.tangCol();
        final float[] elevs = grid.elevs();
```

Second, compute the table's extent from the clip it already derives — this is the duplication D9 names,
and the clip stays here because it belongs to the shape — and call the hook:

```java
        final double invStep = 1.0 / resolution;
        final int baseIdx = (int) Math.floor(perpMin * invStep);
        final int n = (int) Math.floor(perpMax * invStep) - baseIdx + 2;
        owner.tabulateBedLut(lut, baseIdx, n, resolution);
```

Third — a fidelity fix, not a design change — clamp the LUT index against the **used extent**, not the
buffer capacity. `8bf6885` wrote `Math.clamp((int) f, 0, lut.length - 2)`, which can read entries past
`n` that this primitive never filled. `LatticeCarve.carveRiverPrimitive` clamps to `n - 2`; restore that:

```java
                final int i0 = Math.clamp((int) f, 0, n - 2);
```

Everything else in the body — the AABB clip, the perp extrema, the row/column projection tabulation, the
`marginNorm`/`floodPlainNorm` clamping, the hoisted slopes from Task 2, the merge recurrence — stays as
it is.

- [ ] **Step 5: Add `BedCarver.carveRadial` (D4)**

Move `LatticeCarve.carveRadialPrimitive`'s body here verbatim, under the same parameter treatment. The
merge arithmetic is unchanged in this task — `radialDist`, the `priorWeight` height gate, the
`priorWeight` type gate and the maxed weight lane all survive; Task 4 is where they go.

```java
    /**
     * One disc's contribution, clipped to the lattice points it reaches. The circle admits no affine
     * row/column split, so the distance is computed per cell rather than tabulated per axis.
     */
    static void carveRadial(
            HydrologicalPrimitive owner,
            LatticeCarve.BedGrid grid,
            double cx,
            double cz,
            double radius,
            float waterSurface,
            long type) {
        final int gridSize = grid.gridSize();
        final double startX = grid.startX();
        final double startZ = grid.startZ();
        final double resolution = grid.resolution();
        final float[] acc = grid.acc();
        final long[] typeMask = grid.typeMask();
        final float[] radialDist = grid.radialDist();
        final float[] lut = grid.lut();
        final float[] elevs = grid.elevs();
```

The AABB clip, the `radMin`/`radMax` extrema and the `baseIdx`/`n` derivation move across unchanged;
replace the inline `radial.getRadialProfile().sampleRadialSection(...)` call with:

```java
        owner.tabulateBedLut(lut, baseIdx, n, resolution);
```

The `radius <= 0` and `Double.isNaN(elevation)` guards do **not** move here — they need the primitive's
own fields, so they go in `RadialPrimitive.carveBed` (Step 7) where `elevation()` is in scope.

- [ ] **Step 6: Declare the two hooks on `HydrologicalPrimitive` (D8, D9)**

```java
    /** This primitive's contribution to the bed pass, blended into {@code grid}'s merge buffers.
     *  Abstract rather than defaulted, so a new family cannot silently carve no bed. */
    void carveBed(LatticeCarve.BedGrid grid);

    /** Fills {@code lut[0..n)} with this primitive's cross-section, entry {@code i} being the surface at
     *  {@code (baseIdx + i) * resolution} along the carve's own cross-section axis. Separate from
     *  {@link #carveBed} because the table is the family's to build and the cut is the footprint
     *  shape's: one {@code RadialPrimitive} default serves three families through one disc carve. */
    void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution);
```

- [ ] **Step 7: Implement the hooks per family (D10)**

`RiverPrimitive.java` — real, wired to the rectangle carve:

```java
    @Override
    public void carveBed(LatticeCarve.BedGrid grid) {
        // A null normal has no tangent -- the projection inside the carve would NPE.
        if (normal == null) return;
        final RosgenProfile profile = (RosgenProfile) getProfile();
        BedCarver.carve(
                this,
                grid,
                coord[0],
                coord[1],
                normal[0],
                normal[1],
                getLength() * 0.5,
                getWidth() * 0.5,
                width / 2,
                profile.floodPlainLength(width),
                (float) (elevation + HydrologicalPrimitive.waterLine(width)),
                HydrologicalFeature.RIVER.pack(RosgenType.orDefault(rosgenType).ordinal()));
    }

    @Override
    public void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution) {
        final RosgenProfile profile = (RosgenProfile) getProfile();
        profile.sampleCrossSection(
                lut,
                n,
                resolution,
                baseIdx,
                seed,
                elevation,
                profile.floodPlainLength(width),
                width / 2,
                FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width),
                curvature);
    }
```

`RadialPrimitive.java` — one default serving `ConfluencePrimitive`, `SourcePrimitive` and
`AbandonedRiverPrimitive`:

```java
    @Override
    default void carveBed(LatticeCarve.BedGrid grid) {
        final double radius = getRadius();
        if (radius <= 0) return;
        // Deferred: elevation is NaN-sentinelled until RiverNetwork.remapHistory resolves it, which no
        // production caller does -- carving the sentinel would cut every cell it reaches to NaN.
        if (Double.isNaN(elevation())) return;
        BedCarver.carveRadial(
                this,
                grid,
                coord()[0],
                coord()[1],
                radius,
                (float) (elevation() + HydrologicalPrimitive.waterLine(width())),
                getType().pack(0));
    }

    @Override
    default void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution) {
        getRadialProfile()
                .sampleRadialSection(
                        lut,
                        n,
                        resolution,
                        baseIdx,
                        elevation(),
                        1.0 / getRadius(),
                        FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width()));
    }
```

`PositionOnlyPrimitive.java` — a genuine no-op, preserving what `WaterfallPrimitive` and
`DeltaPrimitive` do today by falling through both arms of the chain:

```java
    /** A skeleton feature carries only a position, so it cuts no bed and tabulates no cross-section. */
    @Override
    default void carveBed(LatticeCarve.BedGrid grid) {}

    @Override
    default void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution) {}
```

`OxbowLakePrimitive.java` — no bed carve exists for a shed meander yet:

```java
    @Override
    public void carveBed(LatticeCarve.BedGrid grid) {
        throw new UnsupportedOperationException("OxbowLakePrimitive has no bed carve");
    }

    @Override
    public void tabulateBedLut(float[] lut, int baseIdx, int n, double resolution) {
        throw new UnsupportedOperationException("OxbowLakePrimitive has no bed cross-section");
    }
```

`RosgenCarvedPrimitive` gets **no** `carveBed` default — the two families implementing it answer
differently, and a default would hide that.

- [ ] **Step 8: Build the `BedGrid` at the one bed call site**

Delete the line `HydrologyProfileInprinter.carvePrimitives(buffers,primitives);`. It calls an empty
loop over the primitive list — the bed pass's dispatch now lives inline in `computeBedGrid`, mirroring
where the shell pass's already does. The class itself goes in Task 5.

Then replace the fifteen-argument `computeBedGrid` call with:

```java
        final LatticeCarve.BedGrid grid = new LatticeCarve.BedGrid(
                GRID_SIZE,
                chunkPos.getMinBlockX() / scale,
                chunkPos.getMinBlockZ() / scale,
                GRID_RESOLUTION,
                buffers.acc,
                buffers.typeMask,
                buffers.dist,
                buffers.radialDist,
                buffers.lut,
                buffers.perpRow,
                buffers.perpCol,
                buffers.tangRow,
                buffers.tangCol,
                interpolatedElevs);
        LatticeCarve.computeBedGrid(grid, primitives);
```

The `final float[] acc = buffers.acc;` local above it still feeds the per-column loop; leave it.

- [ ] **Step 9: Update the bed-pass tests for the new signature**

`ComputeRiverGridTest` and `RadialCarveTest` each build their buffers through a helper; add a second
helper beside it so the change lands once per file rather than at every call:

```java
    private static LatticeCarve.BedGrid grid(LatticeCarve.GridBuffers b, float[] elevs) {
        return new LatticeCarve.BedGrid(
                GRID, 0, 0, RES, b.acc, b.typeMask, b.dist, b.radialDist, b.lut,
                b.perpRow, b.perpCol, b.tangRow, b.tangCol, elevs);
    }
```

Every `LatticeCarve.computeBedGrid(0, 0, RES, GRID, List.of(...), b.acc, ... , null)` call becomes
`LatticeCarve.computeBedGrid(grid(b, null), List.of(...))`. `RadialCarveTest.carveWithElevs` becomes
`grid(b, elevs)`. `RadialCarveTest.productionResolutionRadialDiscStaysWithinTheLut` builds its own grid
at `prodRes` rather than `RES`.

- [ ] **Step 10: Replace the `stop`-return test**

`computeBedGrid` returns `void`, so `ComputeRiverGridTest.stopsAtTheFirstNonRiverPrimitiveAndReportsWhere`
has nothing to assert. Its fixture still proves something worth keeping — that a radial family behind the
river run is carved rather than skipped — so replace the method with:

```java
    @Test
    void carvesEveryFamilyInOneSortedWalk() {
        // The walk has no river-run bound: a source sorted behind every river is still carved, and the
        // sort is what puts it there.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologicalPrimitive source =
                new me.batata_1.fractal_terrain.hydrology.features.SourcePrimitive(new double[] {4.0, 4.0}, 2.0, 100.0);
        final LatticeCarve.GridBuffers b = buffers();

        LatticeCarve.computeBedGrid(grid(b, null), List.of(river, source));

        assertTrue(b.acc[3 * idx(4, 4) + 2] > 0, "the source behind the river run must still claim its cells");
    }
```

- [ ] **Step 11: Correct the stale comment in `RadialPrimitiveCodecTest`**

`RadialPrimitiveCodecTest.sortsAfterEveryRiverPrimitive:110` explains itself by "computeRiverGrid's river
loop stops at the first non-river entry", describing a loop that no longer exists. The test itself stays
— the sort contract it pins is now load-bearing for a different reason. Replace the two comment lines:

```java
        // The bed pass merges in list order against one shared ranking buffer, so the sort is what puts
        // every river ahead of every disc; a radial family sorting first would carve over the beds.
```

- [ ] **Step 12: Add the per-family bed-dispatch contracts**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/features/BedDispatchTest.java`. It is a new
file rather than an addition to an existing one because it spans every family's bed hooks, which no
current test class covers:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.carvers.LatticeCarve;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import org.junit.jupiter.api.Test;

/** What each family does when the bed pass reaches it, and that the cross-section hook fills the same
 *  table the profile does. */
class BedDispatchTest {

    private static final int GRID = 16;
    private static final double RES = 1.0;

    private static LatticeCarve.GridBuffers buffers() {
        final LatticeCarve.GridBuffers b = new LatticeCarve.GridBuffers();
        b.ensure(GRID, LatticeCarve.maxLutLen(GRID, RES));
        return b;
    }

    private static LatticeCarve.BedGrid grid(LatticeCarve.GridBuffers b) {
        return new LatticeCarve.BedGrid(
                GRID, 0, 0, RES, b.acc, b.typeMask, b.dist, b.radialDist, b.lut,
                b.perpRow, b.perpCol, b.tangRow, b.tangCol, null);
    }

    @Test
    void aPositionOnlyPrimitiveCarvesNothing() {
        final LatticeCarve.GridBuffers b = buffers();
        LatticeCarve.computeBedGrid(grid(b), List.of());
        final float[] empty = b.acc.clone();
        final long[] emptyMask = b.typeMask.clone();

        LatticeCarve.computeBedGrid(
                grid(b),
                List.of(new WaterfallPrimitive(new double[] {8.0, 8.0}), new DeltaPrimitive(new double[] {8.0, 8.0})));

        assertArrayEquals(empty, b.acc, "a skeleton feature perturbed the merged surface");
        assertArrayEquals(emptyMask, b.typeMask, "a skeleton feature perturbed the type mask");
    }

    @Test
    void anOxbowRefusesTheBedPassRatherThanCarvingSilently() {
        final OxbowLakePrimitive oxbow = new OxbowLakePrimitive(
                new double[] {8.0, 8.0}, (byte) 3, 2.0, 5.0, 100.0, new double[] {1.0, 0.0}, 0.0, null);

        assertThrows(UnsupportedOperationException.class, () -> oxbow.carveBed(grid(buffers())));
    }

    @Test
    void theRiverLutHookFillsWhatTheProfileWouldHaveInline() {
        final RiverPrimitive river = new RiverPrimitive(
                new double[] {8.0, 8.0}, 5.0, RiverPrimitive.RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, 100.0);
        final int baseIdx = -4;
        final int n = 9;

        final float[] fromHook = new float[n];
        river.tabulateBedLut(fromHook, baseIdx, n, RES);

        final float[] fromProfile = new float[n];
        final RosgenProfile profile = (RosgenProfile) river.getProfile();
        profile.sampleCrossSection(
                fromProfile,
                n,
                RES,
                baseIdx,
                river.seed(),
                river.elevation(),
                profile.floodPlainLength(river.width()),
                river.width() / 2,
                FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(river.width()),
                river.curvature());

        assertArrayEquals(fromProfile, fromHook, "the hook must tabulate the profile's own cross-section");
    }
}
```

- [ ] **Step 13: Format, build, test**

```bash
$GRADLE spotlessApply --offline
$GRADLE build --offline --console=plain
$GRADLE test --offline --console=plain
```

Expected: build PASS, and **no test result differs from Task 1's baseline** except the three methods
this task rewrote or added. Every `RadialCarveTest` assertion must still pass unchanged — that is the
evidence the dispatch move is terrain-neutral, and it is the only chance to get it before Task 4 moves
terrain deliberately.

- [ ] **Step 14: Commit**

```bash
git add -A src/
git commit -m "refactor(hydrology): dispatch the bed carve on the primitive"
```

---

### Task 4: One merge law for every family

Implements D11-D15. **This is the only task in the plan that changes terrain.** A bowl stops deepening
the already-merged river surface and blends against ambient like every other primitive; goldens near a
confluence or a source re-baseline.

**Files:**
- Modify: `hydrology/profile/RadialProfile.java` (two authored control points)
- Modify: `hydrology/carvers/BedCarver.java` (`carveRadial`'s merge arithmetic)
- Modify: `hydrology/carvers/LatticeCarve.java` (`GridBuffers.radialDist`, `BedGrid.radialDist`, `computeBedGrid`'s seeding)
- Modify: `hydrology/features/RadialPrimitive.java` (`carveBed` stops passing a type)
- Modify: `world/gen/populatenoise/PopulateNoiseStep.java` (`BedGrid` loses an argument)
- Modify: `storage/FractalTerrainHeightmap.java` (the `RIVER_DIST` channel comment)
- Test: `hydrology/profile/RadialCarveTest.java`, `hydrology/profile/ComputeRiverGridTest.java`, `hydrology/features/BedDispatchTest.java`

**Interfaces:**
- Consumes: Task 3's `BedGrid`, `carveBed`, `BedCarver.carveRadial`.
- Produces:
  - `public static final double RadialProfile.MARGIN_NORM = 0.5`
  - `public static final double RadialProfile.FLOOD_PLAIN_NORM = 0.75`
  - `static void BedCarver.carveRadial(HydrologicalPrimitive owner, LatticeCarve.BedGrid grid, double cx,
    double cz, double radius, float waterSurface)` — the `long type` parameter is gone.
  - `LatticeCarve.BedGrid` without `radialDist`: `(int gridSize, double startX, double startZ,
    double resolution, float[] acc, long[] typeMask, float[] dist, float[] lut, double[] perpRow,
    double[] perpCol, double[] tangRow, double[] tangCol, float[] elevs)`

- [ ] **Step 1: Write the failing banding test**

A disc and a channel must land on the same breakpoints once they share a buffer. Add to `RadialCarveTest`:

```java
    /** One buffer holding two scales would leave BED_EDGE and FLOODPLAIN_EDGE meaning whichever family
     *  wrote last, so a disc is banded on the same breakpoints a channel is. */
    @Test
    void bandsTheDiscOnTheSameBreakpointsAsAChannel() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        assertEquals(0f, b.dist[idx(8, 8)], 1e-6f, "the disc centre is the floor of its bed");
        assertEquals(
                (float) LatticeCarve.BED_EDGE, b.dist[idx(8, 10)], 1e-6f, "half the radius is the bank");
        assertEquals(
                (float) LatticeCarve.FLOODPLAIN_EDGE,
                b.dist[idx(8, 11)],
                1e-6f,
                "three quarters of the radius is the floodplain edge");
    }
```

The lattice is chosen so the arithmetic is exact in binary: `bowl(4.0, …)` has radius 4 about `(8, 8)`,
so `idx(8, 10)` is at radius 2 (`raw = 0.5`) and `idx(8, 11)` at radius 3 (`raw = 0.75`), and a lone
primitive takes weight 1 everywhere inside its footprint, so `dist[i]` ends at exactly `d`.

- [ ] **Step 2: Run it to watch it fail**

Run: `$GRADLE test --offline --tests "*RadialCarveTest.bandsTheDiscOnTheSameBreakpointsAsAChannel"`
Expected: FAIL — the raw normalised radius reaches `dist` unbanded, so the bank reads 0.5 rather than
`BED_EDGE`, and the disc's values sit in `radialDist` rather than `dist` at all.

- [ ] **Step 3: Give `RadialProfile` the two control points (D12)**

At the top of the enum body, beside the existing members:

```java
    /** Where a disc's bed gives way to its floodplain, as a fraction of the radius. The bed is the
     *  inner half, matching a disc running to {@code width()} — twice a channel's painted bed. An
     *  authored gate, not a measurement. */
    public static final double MARGIN_NORM = 0.5;

    /** Where a disc's floodplain gives way to its influence band, as a fraction of the radius. An
     *  authored gate, not a measurement. */
    public static final double FLOOD_PLAIN_NORM = 0.75;
```

- [ ] **Step 4: Rewrite `carveRadial`'s merge arithmetic (D11-D15)**

Drop the `long type` parameter. Replace everything from `final double invRadius` to the end of the
merge loop with:

```java
        final double invRadius = 1.0 / radius;
        // :PERF: slopes hoisted per primitive; the merge loop below runs per lattice point and carries
        // no division. Both control points are constants, so no denominator here can be zero.
        final double bedSlope = LatticeCarve.BED_EDGE / RadialProfile.MARGIN_NORM;
        final double floodPlainSlope = (LatticeCarve.FLOODPLAIN_EDGE - LatticeCarve.BED_EDGE)
                / (RadialProfile.FLOOD_PLAIN_NORM - RadialProfile.MARGIN_NORM);
        final double outerSlope =
                (1.0 - LatticeCarve.FLOODPLAIN_EDGE) / (1.0 - RadialProfile.FLOOD_PLAIN_NORM);

        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double ddx = (startX + row * resolution) - cx;
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final int a = 3 * i;
                final double ddz = (startZ + col * resolution) - cz;
                // A circle admits no affine row/column split the way a rectangle's two projections do,
                // so the true distance is computed per cell rather than tabulated per axis.
                final double rad = Math.sqrt(ddx * ddx + ddz * ddz);
                final double raw = rad * invRadius;
                final double d = band(
                        raw,
                        RadialProfile.MARGIN_NORM,
                        RadialProfile.FLOOD_PLAIN_NORM,
                        bedSlope,
                        floodPlainSlope,
                        outerSlope);
                // Tested on the raw scale rather than the banded one, as the rectangle carve is: the
                // band saturates at the rim and a point past it would otherwise read as in-band.
                final double mask = raw <= 1.0 ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;

                final double f = rad * invStep - baseIdx;
                final int i0 = Math.clamp((int) f, 0, n - 2);
                final double sampled = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                // Capped against real ambient elevation, so the bowl blends against the ground it stands
                // on rather than against a merged surface an earlier primitive already cut.
                final double h = (elevs != null) ? Math.min(elevs[i], sampled) : sampled;

                dist[i] = (float) ((1 - w) * dist[i] + w * d);
                acc[a] = (float) ((1 - w) * acc[a] + w * h);
                acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
                acc[a + 2] = 1 - Math.clamp(dist[i], 0, 1);
            }
        }
```

Three things are gone rather than changed, and each is deliberate:

- **No `typeMask` write** (D13). A disc runs to `width()`, twice a channel's painted bed, and
  `RadialProfile` does not override `riverPaintDepth`, so the base returns 0 — a column a bowl reaches
  paints identically whether its tag reads `CONFLUENCE` or `NONE`. Removing the write is simpler than
  gating it and drops the only place the two passes read each other's bookkeeping.
- **No `priorWeight`** (D14). Reading `elevs[i]` rather than `acc[a]` makes the zero-fill guard
  unnecessary instead of unsafe to drop.
- **No `Math.max` on the weight lane** (D15). A cell inside the AABB but outside the disc takes
  `w = 0`, leaves `dist[i]` untouched, and so reproduces whatever claim was already there; the
  assignment is the same form the rectangle carve uses.

Add the `RadialProfile` import to `BedCarver`.

- [ ] **Step 5: Delete the second ranking buffer (D11)**

In `LatticeCarve`:
- `GridBuffers` — delete the `radialDist` field and its line in `ensure`.
- `BedGrid` — delete the `radialDist` component.
- `computeBedGrid` — delete its `Arrays.fill(grid.radialDist(), …)`.
- Extend `computeBedGrid`'s javadoc: `grid.dist()` is published into `Types.RIVER_DIST`, so it now
  reflects whichever primitive won each cell rather than the river pass alone.

In `RadialPrimitive.carveBed`, drop the `getType().pack(0)` argument.

In `PopulateNoiseStep`, drop `buffers.radialDist` from the `BedGrid` construction.

Three test helpers build a `BedGrid` positionally and must drop the same argument, or they will bind
`lut` to the `dist` slot and fail in ways that look like merge bugs: `ComputeRiverGridTest.grid`,
`RadialCarveTest.grid` and `BedDispatchTest.grid`.

- [ ] **Step 6: Correct the `RIVER_DIST` channel comment**

`FractalTerrainHeightmap.Types.RIVER_DIST`'s comment claims the value is "Only meaningful where
RIVER_TYPE is not NONE". A radial-claimed column now carries a meaningful banded distance and a `NONE`
tag, so that sentence is false. Replace it:

```java
        // The winning primitive's banded footprint coordinate, from BedCarver.band — whichever family
        // won the cell, including the radial ones that stamp no type. Holds the carve's unset seed
        // where no primitive reached.
```

- [ ] **Step 7: Re-baseline the radial merge tests**

Four `RadialCarveTest` methods assert the law this task replaces. Each is rewritten, not deleted — the
behaviour it pinned still has a successor worth pinning.

`neverLiftsARiverBedItOverlaps` becomes:

```java
    /** A bowl blends against ambient like every other family, so a rim above a river's bed pulls the
     *  merged surface up toward the bowl's own floor in proportion to its weight. */
    @Test
    void blendsAgainstAmbientRatherThanTheMergedRiverSurface() {
        final LatticeCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float riverBed = riverOnly.acc[3 * idx(8, 8)];

        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(knot(CENTRE, 100.0), bowl(4.0, 120.0)));

        assertTrue(
                b.acc[3 * idx(8, 8)] > riverBed,
                "the bowl's own floor sits above the river bed, and order alone decides the outcome");
    }
```

`stampsTheConfluenceFamilyOnTheCellsItWins` becomes:

```java
    /** A disc claims no type: it paints nothing either way, and not writing the tag is what keeps a
     *  river bed crossing the disc in its own surface materials. */
    @Test
    void claimsNoTypeOnTheCellsItCarves() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        assertEquals(
                HydrologicalPrimitive.HydrologicalFeature.NONE,
                b.typeMask[idx(8, 8)],
                "a cell only a disc reached must stay untagged");
    }
```

`leavesTheRiverDistanceFieldUntouched` becomes:

```java
    /** One shared ranking buffer: the published distance is the winning primitive's, whichever family
     *  that is, which is what lets a rectangle and a disc rank against each other at all. */
    @Test
    void publishesTheWinningPrimitivesDistanceWhicheverFamilyWon() {
        final LatticeCarve.GridBuffers b = buffers();
        carve(b, List.of(knot(4.0, 100.0), bowl(4.0, 100.0)));

        assertEquals(0f, b.dist[idx(8, 8)], 1e-6f, "the bowl centre is the nearest thing to that cell");
        assertTrue(b.dist[idx(4, 4)] < (float) LatticeCarve.UNSET_MIN_DIST, "the river still holds its own cells");
    }
```

`carvesAnAbandonedRiverPrimitiveThroughTheRadialDispatch` keeps its elevation assertion and loses its
tag assertion (D13 removes the write for every radial family, this one included). Delete these lines:

```java
        assertEquals(
                HydrologicalPrimitive.HydrologicalFeature.ABANDONED_RIVER,
                HydrologicalPrimitive.HydrologicalFeature.unpack(b.typeMask[centre]));
```

The remaining eight `RadialCarveTest` methods must pass **unchanged**. Update the javadoc on
`carvesToItsOwnLawWhereNoRiverReached` (its "an ungated min against the zero-filled acc" rationale
describes a guard that is gone — the reason is now that a null ambient field leaves nothing to cap
against) and on `keepsTheRiverWeightAtCellsOutsideItsDisc` (the weight is assigned, not maxed; the cell
survives because `w = 0` leaves `dist[i]` untouched, so the assignment reproduces the river's own claim).

`ignoresANonRadialTailPrimitive`, `leavesTheRiverTypeTagOnCellsTheRiverClaimed`,
`publishesItsOwnWaterSurface`, `carvesToTheSourceConeLawAtHalfRadius`,
`clampsToAmbientElevationBelowTheBowlFloor` and `productionResolutionRadialDiscStaysWithinTheLut` are
unaffected. If any of them fails, the merge rewrite is wrong — do not re-baseline it.

`RiverPaintDepthTest` needs no change beyond the `LatticeCarve` rename Task 2 already made: it exercises
`RosgenProfile.riverPaintDepth` against the two constants directly, and neither constant's value moves.
The spec lists it as needing a rewrite; it does not.

- [ ] **Step 8: Run the banding test and the suite**

```bash
$GRADLE spotlessApply --offline
$GRADLE build --offline --console=plain
$GRADLE test --offline --console=plain
```

Expected: PASS for `bandsTheDiscOnTheSameBreakpointsAsAChannel`, and the whole suite back to Task 1's
baseline plus this task's rewritten methods. `ComputeRiverGridTest` is river-only and must be
**byte-identical** to the baseline — no river assertion may move, since nothing in this task touches
the rectangle path.

- [ ] **Step 9: Commit**

```bash
git add -A src/
git commit -m "feat(hydrology): merge every carve family through one shared distance buffer"
```

---

### Task 5: Delete `HydrologyProfileInprinter`

Implements D17. A one-method wrapper whose query inlines at its single call site, where the sort
contract `computeBedGrid` depends on becomes visible next to the carve.

**Files:**
- Delete: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java`
- Modify: `world/gen/populatenoise/PopulateNoiseStep.java:63`, `:71-72`
- Modify: `GenerationContext.java:8`, `:46`, `:63`, `:114-116`
- Modify: `FractalTerrainInstance.java:9`, `:121-123`
- Modify: `debug/tests/SpatialIndexBenchmark.java:21`, `:96`
- Modify: `hydrology/providers/RiverProvider.java:323` (javadoc), `hydrology/profile/HydrologyProfilePainter.java:12` (javadoc)
- Modify: `src/test/java/me/batata_1/fractal_terrain/math/VectorOpsProjectionTest.java:82` (comment)

**Interfaces:**
- Consumes: Task 4's `BedGrid` shape.
- Produces: no new API. `RiverProvider.queryInfluence(double[] pt, double extraRadius)` is now called
  directly by `PopulateNoiseStep`.

- [ ] **Step 1: Inline the query at the one call site**

In `PopulateNoiseStep.fineGrainedPrimitivePass`, replace the inprinter lookup and `prefetchChunk` call:

```java
        final List<HydrologicalPrimitive> primitives = FractalTerrainInstance.getRiverProvider()
                .queryInfluence(new double[] {chunkCenterPixelX, chunkCenterPixelZ}, chunkRadiusPx);
        // computeBedGrid merges in list order against one shared ranking buffer, so this sort is what
        // decides which primitive owns each lattice point.
        primitives.sort(HydrologicalPrimitive.comparator);
```

Keep the existing "One influence query serves the whole chunk" comment above it — it explains the
prefetch, which has not changed. Drop the `HydrologyProfileInprinter` import.

- [ ] **Step 2: Delete the class and its holders**

```bash
git rm src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java
```

- `GenerationContext` — delete the import, the `hydrologyInprinter` field, its assignment in the
  constructor, and `getHydrologyInprinter()`.
- `FractalTerrainInstance` — delete the import and the static `getHydrologyInprinter()`.
- `SpatialIndexBenchmark` — delete the import and line 96's `final HydrologyProfileInprinter carver = …`.
  The local has no other reference in the file; confirm with
  `grep -n "carver" src/main/java/me/batata_1/fractal_terrain/debug/tests/SpatialIndexBenchmark.java`
  before deleting.

- [ ] **Step 3: Correct the javadoc that names it**

- `RiverProvider.queryInfluence` — "feeds `HydrologyProfileInprinter`'s flat distance-weighted merge"
  becomes "feeds the bed pass's per-chunk prefetch".
- `HydrologyProfilePainter`'s class javadoc — "The painting twin of `HydrologyProfileInprinter`" loses
  its twin; reword to name the carve it is the twin of (`LatticeCarve`).
- `VectorOpsProjectionTest:82`'s comment "(HydrologyProfileInprinter always orients its two-segment
  polyline downstream)" names a class and a behaviour that no longer exist anywhere. Verify what
  actually orients that polyline today and name that, or delete the parenthetical if nothing does.

- [ ] **Step 4: Verify nothing references it**

```bash
grep -rn "HydrologyProfileInprinter\|prefetchChunk\|carvePrimitives" src --include=*.java
```

Expected: no output.

- [ ] **Step 5: Format, build, test**

```bash
$GRADLE spotlessApply --offline
$GRADLE build --offline --console=plain
$GRADLE test --offline --console=plain
```

Expected: identical to Task 4's results. This task moves no math.

- [ ] **Step 6: Commit**

```bash
git add -A src/
git commit -m "refactor(hydrology): inline the per-chunk influence query at its call site"
```

---

### Task 6: Move the carve documentation to the package that holds the carve

`hydrology/carvers/` has no `CLAUDE.md` and no `README.md`, while `hydrology/profile/`'s pair still
documents three classes that no longer live there — one of which no longer exists. Thirteen `src/`
directories carry a `README.md`; this one is the gap. The test package holding the carve tests moves
here too, since it is the same mismatch.

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/CLAUDE.md`
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/carvers/README.md`
- Rewrite: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md`, `README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md`, `README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/README.md`, `hydrology/rosgen/README.md`
- Modify: `ARCHITECTURE.md`
- Move: `src/test/.../hydrology/profile/{ComputeRiverGridTest,RadialCarveTest,InfluenceCarverShellTest}.java` → `src/test/.../hydrology/carvers/`
- Create: `src/test/java/me/batata_1/fractal_terrain/hydrology/carvers/CLAUDE.md`
- Modify: `src/test/.../hydrology/CLAUDE.md`, `hydrology/profile/CLAUDE.md`, `hydrology/features/CLAUDE.md`
- Modify: `docs/superpowers/specs/CLAUDE.md`, `docs/superpowers/specs/2026-09-20-carve-dispatch-design.md`
- Modify: `CLAUDE.md` (root, Test section baseline)

**Interfaces:** documentation only; no code contract changes.

- [ ] **Step 1: Move the carve tests into a `carvers` test package**

The baseline comparison is finished, so the suite names may move now.

```bash
mkdir -p src/test/java/me/batata_1/fractal_terrain/hydrology/carvers
git mv src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java \
       src/test/java/me/batata_1/fractal_terrain/hydrology/carvers/ComputeBedGridTest.java
git mv src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialCarveTest.java \
       src/test/java/me/batata_1/fractal_terrain/hydrology/carvers/RadialCarveTest.java
git mv src/test/java/me/batata_1/fractal_terrain/hydrology/profile/InfluenceCarverShellTest.java \
       src/test/java/me/batata_1/fractal_terrain/hydrology/carvers/InfluenceCarverShellTest.java
```

Change each file's `package` to `me.batata_1.fractal_terrain.hydrology.carvers;`, rename the
`ComputeRiverGridTest` class to `ComputeBedGridTest`, and add the imports the three now need for the
`hydrology.features` and `hydrology.profile` types they had been reaching package-privately. Run
`$GRADLE test --offline` and fix any access error before continuing.

`RiverPaintDepthTest`, `RadialProfileTest` and `SampleCrossSectionTest` stay in `hydrology/profile/` —
they test profiles, not carves.

- [ ] **Step 2: Write `carvers/CLAUDE.md`**

Index only, in the format `.claude/conventions/documentation.md` specifies and
`hydrology/profile/CLAUDE.md` demonstrates: a one-line purpose, then a Files table with What and
When-to-read columns for `README.md`, `LatticeCarve.java`, `BedCarver.java` and `InfluenceCarver.java`.
No prose beyond the table — the narrative belongs in `README.md`.

- [ ] **Step 3: Write `carvers/README.md`**

Move, do not rewrite from scratch, the carve narrative currently in `hydrology/profile/README.md`. These
sections travel: the Overview's three shell call sites and the per-chunk bed carve, "`d` is a banded
rectangle scale, not a radius", "Merge law", "Cut-only", "LUT residual", "Call sites". Update each to the
shape this plan leaves behind:

- One `dist[]`, no `radialDist`. The "Radial pass" section is gone as a separate pass; a disc now merges
  in the same walk, banded on `RadialProfile.MARGIN_NORM` / `FLOOD_PLAIN_NORM`, claiming no `typeMask`
  and capping against ambient like every other family.
- "For a `RadialPrimitive`, `d` *is* a radius scale" is no longer true — a disc's raw radius scale is
  banded through the same `BedCarver.band` a rectangle's is, which is what makes one buffer sound.
- `Types.RIVER_DIST` reflects whichever primitive won a cell, not the river pass alone.
- The dispatch seam: both passes call one virtual method per primitive
  (`carveInfluence`, `carveBed`), the footprint shape lives in `InfluenceCarver` / `BedCarver`, and the
  cross-section table is the family's own `tabulateBedLut`. Say why the split falls there: the table
  needs geometry only the family knows, the cut needs the clip and the recurrence, which belong to the
  shape — one `RadialPrimitive` default serves three families through one disc carve.
- `OxbowLakePrimitive.carveBed` throws, and why that is unreachable today: the only production
  `RiverNetwork` construction leaves `saveHistory` false, so no shed primitive is ever minted. Record
  the evidence Task 3 Step 1 gathered, and that it needs re-checking before history is enabled.
- The "Why the carve math is static and the prefetch is not" section describes a class that no longer
  exists; drop it.

`profile/README.md` keeps only the paint half: `HydrologyProfile`'s contract, `RosgenProfile`'s laws and
material columns, `RadialProfile`'s three shape laws and its two authored control points,
`SurfaceMaterial`, `ZoneCategory`'s reserved-not-live note, `HydrologyProfilePainter`, and the four
"Design decisions / known limitations" entries. Where it must name a carve, link to
`../carvers/README.md` rather than restating it.

- [ ] **Step 4: Rewrite `profile/CLAUDE.md`**

Delete the rows for `RiverInfluenceCarve.java`, `HydrologyProfileInprinter.java` and
`InfluenceCarver.java`. Add a line under the header pointing at `../carvers/` for anything about the
carve itself. Update `RadialProfile.java`'s row to mention the two band control points.

- [ ] **Step 5: Update the feature-package docs**

`features/CLAUDE.md`:
- `RosgenCarvedPrimitive.java`'s row says "what `InfluenceCarver.ROSGEN` dispatches on" — name the
  rectangle shell carve instead.
- `RadialPrimitive.java`'s row says "The interface the carve's radial pass dispatches on" — there is no
  radial pass; it is the shape whose defaults answer both hooks for three families.
- `HydrologicalPrimitive.java`'s row should name the three carve hooks alongside the comparator.
- `OxbowLakePrimitive.java`'s row should say its bed carve is not implemented.

`features/README.md`, four claims to correct:
- "`RiverInfluenceCarve.computeRiverGrid`'s first pass relies on that: it walks the sorted list and
  stops at the first non-river" — there is one walk and no stop; the sort is what orders the merge.
- "carves through the shell pass's `InfluenceCarver.ROSGEN` dispatch" — name `carveInfluence`.
- "A family that emits primitives must sort after `RIVER`" — the reason changes from truncating a run
  to losing the merge order; the rule itself stands.
- "A family that carves radially must implement `RadialPrimitive`" — still true, and now the reason is
  that the interface is where both bed hooks are defaulted.

- [ ] **Step 6: Update `hydrology/README.md`, `hydrology/rosgen/README.md` and `ARCHITECTURE.md`**

`hydrology/README.md:48`, `:111`, `:134` and `rosgen/README.md:73` name `RiverInfluenceCarve`,
`carveRiverInfluenceGrid` or `computeRiverGrid`; rename in place, and check `:134`'s recurrence claim
still describes one shared buffer.

`ARCHITECTURE.md`, at lines 9, 103, 110, 114, 124, 133-139, 229, 243, 278, 407, 468:
- Every `RiverInfluenceCarve` / `computeRiverGrid` / `carveRiverInfluence` name.
- The `hydrology/profile/` inventory at 103 and the hot-site list at 407 move the carve to
  `hydrology/carvers/LatticeCarve.java`, naming `computeBedGrid`, `carveInfluenceGrid`, and
  `BedCarver`/`InfluenceCarver` as the per-primitive helpers.
- 133-139 describes `prefetchChunk` and the `radialDist`-ranked second pass; both are gone. Replace with
  the inlined query and the single sorted walk.
- 229, 243 and 278 list `HydrologyProfileInprinter` in the provider build order, the caller-migration
  list and the milestone-ownership note; remove it from each.
- 468's "*Within a pass:* `computeRiverGrid` is a…" order-dependence claim is now stronger, not weaker:
  one buffer means list order is the only thing deciding outcomes. Say that.

- [ ] **Step 7: Update the test-package docs**

- Create `src/test/.../hydrology/carvers/CLAUDE.md` indexing the three moved test classes plus what each
  gates.
- `src/test/.../hydrology/CLAUDE.md:23` describes `profile/` as "The lattice carve: `computeRiverGrid`
  merge law…" — split that into a `carvers/` row and a narrowed `profile/` row.
- `src/test/.../hydrology/profile/CLAUDE.md` loses the three moved rows and its "Gates `computeRiverGrid`"
  header line.
- `src/test/.../hydrology/features/CLAUDE.md` — `InfluenceCarverDefaultsTest`'s row describes an enum
  assertion; it is a behaviour test now. Add a row for `BedDispatchTest.java`.

- [ ] **Step 8: Close out the spec and the plan's own baseline**

- `docs/superpowers/specs/2026-09-20-carve-dispatch-design.md` — `Status: proposed — nothing here
  implemented.` becomes implemented, naming the commit range. Replace the closing "Implementation: Not
  yet written" with a pointer to `docs/superpowers/plans/2026-09-20-carve-dispatch.md`. Note the two
  places the plan decided against the spec: `RiverPaintDepthTest` needed no rewrite, and `8bf6885`'s
  extraction had two fidelity bugs (`lut.length - 2` for `n - 2`, and the un-hoisted `band` slopes) that
  the plan corrects rather than preserving.
- `docs/superpowers/specs/CLAUDE.md` — update this spec's row to *implemented*, and the
  `2026-09-17-primitive-grid-storage-design.md` row, whose Part 2 this supersedes.
- Root `CLAUDE.md`'s Test section — replace the `df7ca2e` baseline with the one Task 1 measured, at the
  commit it was measured at, keeping the paragraph that says to treat any quoted baseline as a claim to
  re-verify.

`git add` each markdown file before editing it, so a 0-byte staged blob can never shadow it (see Global
Constraints).

- [ ] **Step 9: Verify no stale name survives in a tracked doc**

```bash
grep -rn "RiverInfluenceCarve\|RiverBedCarver\|HydrologyProfileInprinter\|computeRiverGrid\|carveRiverInfluenceGrid\|radialDist" \
  --include=*.md ARCHITECTURE.md README.md CLAUDE.md src/ docs/superpowers/specs/CLAUDE.md
```

Expected: no output. `.superpowers/` and the dated specs under `docs/superpowers/specs/` are historical
records of intent and keep the names they were written with — do not edit them, except this spec's own
status line.

- [ ] **Step 10: Build, test, commit**

```bash
$GRADLE spotlessApply --offline
$GRADLE build --offline --console=plain
$GRADLE test --offline --console=plain
git add -A src/ docs/ ARCHITECTURE.md
git commit -m "docs(hydrology): move the carve documentation to the carvers package"
```

Expected: the suite matches Task 5's results, with the three moved test classes reported under their new
package.

---

## Execution Notes

**What proves this landed correctly.** Tasks 1-3, 5 and 6 must reproduce Task 1's measured test results
exactly. Task 4 is the only task allowed to move a number, and the methods it may move are listed by
name in its Step 7. A golden moving anywhere else means the change is wrong, not that the golden is
stale.

**What this plan does not do**, per the spec's own scope:

1. Everything in `2026-09-17-primitive-grid-storage-design.md`'s Part 1 — `PrimitiveGrid`, the
   chunk-aligned cell grid and halo, CSR fanout, cross-call LUT caching. `tabulateBedLut` is the seam
   such a cache would fill from; nothing here caches a table between carve calls.
2. `AbandonedRiverPrimitive`'s real secant tangent and its conversion to `RosgenCarvedPrimitive`. It
   stays a `RadialPrimitive`, relocated rather than rewritten.
3. Whether shell-prep-in-`RiverProvider` / bed-detail-in-`PopulateNoiseStep` is the right architectural
   split at all. `OxbowLakePrimitive`'s real bed carve most likely belongs inside whatever that
   restructuring decides, which is why D10 leaves a throw rather than a guess.
