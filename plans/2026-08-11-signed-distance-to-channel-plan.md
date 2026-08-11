# Signed Distance to Channel Centreline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-primitive tangent-line distance with one signed perpendicular distance per channel, so the per-pixel carve evaluates each channel's Rosgen profile exactly once instead of averaging contradictory answers.

**Architecture:** A point's distance is measured to the two-segment polyline through the nearest spline knot and its two knot-adjacent neighbours. The projection yields a normalised position along the winning segment (`segParam`), which is reused to interpolate width, bed elevation and curvature at the foot point — so the profile sees one coherent cross-section rather than a knot's snapshot. Nearest channel wins outright; confluences are deferred to future junction primitives.

**Tech Stack:** Java 21, Fabric 1.20.1, JUnit 5 (`useJUnitPlatform()`), Gradle 9.2.1, palantirJavaFormat via Spotless.

**Spec:** `plans/2026-08-11-signed-distance-to-channel-design.md`

## Global Constraints

- **Gradle is not the one on PATH.** `C:\Gradle\gradle-8.14` is too old for fabric-loom 1.14.10. Every Gradle command in this plan means:
  `& "C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat"`
  Referred to below as `$GRADLE`. Run from the repo root. Pass `--offline` to keep `generateModelAssetManifest` off Hugging Face.
- **Spotless is repo-wide.** `$GRADLE spotlessApply` reformats every Java file, including ones carrying unrelated uncommitted changes. Run it before each commit, then check `git diff --stat` and stage only your own files.
- **Docstrings follow `.claude/conventions/documentation.md`.** State WHY and where a thing sits in the pipeline, never HOW. Hard budgets: 1 line for a field, 3 for a method, 10 for a class. Do not narrate the algorithm — the code is the algorithm.
- **All geometry is in the relief-pixel frame,** not block coordinates. `FractalTerrainConfig.GLOBAL_SCALE_CORRECTION` converts.
- **The sign convention is fixed by existing tuning.** `signedPerpDist` must keep the sign that `RiverPrimitive.d(pt) = dot(normal, pt - coord)` produces, because the asymmetric F and G profiles in `RosgenProfile` were tuned against it. Any test that pins a sign pins it against `d(pt)`.
- **`ids` packing:** `RiverPrimitive.ids` is `channelId << 32 | knotIndex`. `(int) ids` is the knot index; `(int) (ids >>> 32)` is the channel id.
- **Index vocabulary:** `knotIndex` = position along the spline. `primitiveIndex` = position in the prefetched sorted list. These are different spaces and Task 4 exists because they disagree.
- **Do not touch `RosgenProfile.delta:232`** (`if (perpDist <= marginLen) return -10;`). It is deliberate debug instrumentation the author will revert separately.

## Spec Amendments

Two corrections to the design doc, discovered while writing this plan. Implement the corrected versions.

1. **`NearestChannelSample` carries `channelId`.** Spec §4 requires `channelId` as `RosgenProfile.delta`'s `randSeed`, but the §5 record has no field to carry it out of the sampler. Added.
2. **`carvedElevation` is a method, not a field.** It depends on `ambientElevation`, which the sampler does not receive. The record exposes `carveInto(double ambientElevation)` instead. This also keeps the record purely geometric, so Task 4 can be tested without any profile involvement.
3. **§6's `smoothMin` is wrong for this use.** `RosgenProfile.smoothMin(a, b, lambda)` evaluates to `a - sqrt(lambda)/2` when `a == b`, so it never returns `ambientElevation` exactly. Every pixel whose nearest primitive is a river would sink by `sqrt(lambda)/2` regardless of distance. Task 6 adds `RosgenProfile.blendMin`, a quadratic smooth-min that is *exactly* `Math.min` once the inputs are farther apart than its blend range.

## File Structure

| File | Responsibility | Task |
| --- | --- | --- |
| `math/VectorOps.java` | Modify: add `projectPointOntoSegment` — the point-to-segment kernel, allocation-free | 2 |
| `math/VectorOpsProjectionTest.java` | Create (test): projection, clamping, degenerate segment | 2 |
| `hydrology/features/RiverPrimitive.java` | Modify: unpack `ids`, knot-adjacency predicate, static `waterLine` | 3, 7 |
| `hydrology/features/RiverPrimitiveIdsTest.java` | Create (test): id packing and adjacency, incl. meander loopback | 3 |
| `hydrology/profile/NearestChannelSample.java` | Create: one channel's cross-section at one point + `carveInto` | 4, 6 |
| `hydrology/profile/HydrologyProfileInprinter.java` | Modify: `sampleNearestChannel`, `resolveNearestPrimitiveIndex` | 1, 4, 7 |
| `hydrology/profile/NearestChannelSampleTest.java` | Create (test): distance, sign, interpolation, guard fallbacks | 4 |
| `hydrology/profile/RosgenProfile.java` | Modify: add `blendMin` | 6 |
| `hydrology/profile/BlendMinTest.java` | Create (test): exactness outside range, continuity | 6 |
| `world/gen/populatenoise/PopulateNoiseStep.java` | Modify: rewrite the gutted per-pixel loop | 7 |

Tasks 2, 3, 4, 6 are pure functions with no Minecraft runtime dependency, so all four are covered by fast JUnit tests. Task 7 is wiring and is verified visually.

---

### Task 1: Restore compilation and capture the test baseline

Nothing compiles right now, so there is no test cycle to do TDD in. `HydrologyProfileInprinter.carveRiverPrimitives` is a half-written stub with no return statement. It is dead code — nothing calls it, because the loop in `PopulateNoiseStep` that would have called it is currently empty. Delete it; Task 4 introduces its replacement properly.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java:80-84`

**Interfaces:**
- Consumes: nothing
- Produces: a compiling `:compileJava` and `:compileTestJava`, which every later task's test cycle depends on

- [ ] **Step 1: Confirm the break is exactly what this task claims**

```
$GRADLE compileJava --offline -q --console=plain
```

Expected: exactly one error —
`HydrologyProfileInprinter.java:84: error: missing return statement`

If other errors appear, stop and report. This plan assumes the tree is otherwise green.

- [ ] **Step 2: Delete the dead stub**

Remove these five lines entirely (`HydrologyProfileInprinter.java:80-84`):

```java
    public static double[] carveRiverPrimitives(List<HydrologicalPrimitive> primitives,int id,double[] pt) {
        if(id==-1) return new double[]{0,0};

    }
```

Also delete the now-unused private helper `resolveRiverNearestIdsChunk` (`HydrologyProfileInprinter.java:54-66`). It builds a per-chunk array of nearest ids that nothing consumes, and Task 7 resolves the index per pixel instead. Leave `resolveRiverNearestId` alone — Task 7 renames it.

- [ ] **Step 3: Verify main compiles**

```
$GRADLE compileJava --offline -q --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Capture the JUnit baseline before writing any new test**

```
$GRADLE test --offline --console=plain
```

Record the exact pass/fail list in your commit message. **Expect failures.** CLAUDE.md documents a pre-existing historical breakdown of roughly 8 failures across `LocalRiverGoldenTest` (an `ArrayIndexOutOfBoundsException: Index 262144 out of bounds for length 262144`), `GlobalRiverGoldenTest`, `SpatialIndexCorrectnessGoldenTest` and `MeandersGoldenTest`. That record predates the Rosgen test classes and has not been reproducible for some time, so treat whatever you observe now as the real baseline. Every later task compares against *this* list, not against green.

Do not fix any of them. They are outside this plan's scope.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java
git commit -m "chore: drop the dead carveRiverPrimitives stub to restore compilation"
```

---


### Task 2: `VectorOps.projectPointOntoSegment`

The geometry kernel. Given a point and a segment, produce the normalised position along the segment of the closest point on it, and the squared distance to that point. Allocation-free: it writes into a caller-owned scratch array, matching the `mutablePt` pattern the per-pixel loop already uses.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/math/VectorOps.java` (append before the closing brace)
- Test: `src/test/java/me/batata_1/fractal_terrain/math/VectorOpsProjectionTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `public static void VectorOps.projectPointOntoSegment(double[] point, double[] segStart, double[] segEnd, double[] outProjection)` — writes `outProjection[0] = segParam` (clamped to `[0,1]`), `outProjection[1] = distSq`. 2D-only, reads indices 0 and 1.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/math/VectorOpsProjectionTest.java`:

```java
package me.batata_1.fractal_terrain.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the point-to-segment projection kernel. */
class VectorOpsProjectionTest {

    private final double[] projection = new double[2];

    @Test
    void projectsOntoSegmentInterior() {
        // Segment along +x from (0,0) to (4,0); the point sits directly above its midpoint.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 3.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.5, projection[0], 1e-12);
        assertEquals(9.0, projection[1], 1e-12);
    }

    @Test
    void clampsBeforeSegmentStart() {
        // Foot would land at segParam = -0.75; clamping makes the start point the closest point (3-4-5).
        VectorOps.projectPointOntoSegment(
                new double[] {-3.0, 4.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.0, projection[0], 1e-12);
        assertEquals(25.0, projection[1], 1e-12);
    }

    @Test
    void clampsPastSegmentEnd() {
        VectorOps.projectPointOntoSegment(
                new double[] {7.0, 4.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(1.0, projection[0], 1e-12);
        assertEquals(25.0, projection[1], 1e-12);
    }

    @Test
    void degenerateSegmentReportsDistanceToItsStart() {
        // Two coincident knots must not divide by zero; the segment collapses to a point.
        VectorOps.projectPointOntoSegment(
                new double[] {3.0, 4.0}, new double[] {1.0, 1.0}, new double[] {1.0, 1.0}, projection);
        assertEquals(0.0, projection[0], 1e-12);
        assertEquals(13.0, projection[1], 1e-12);
    }

    @Test
    void diagonalSegmentProjectsCorrectly() {
        // Segment (0,0)->(2,2); point (2,0) projects to the midpoint (1,1), distSq = 2.
        VectorOps.projectPointOntoSegment(
                new double[] {2.0, 0.0}, new double[] {0.0, 0.0}, new double[] {2.0, 2.0}, projection);
        assertEquals(0.5, projection[0], 1e-12);
        assertEquals(2.0, projection[1], 1e-12);
    }

    @Test
    void pointOnTheSegmentHasZeroDistance() {
        VectorOps.projectPointOntoSegment(
                new double[] {3.0, 0.0}, new double[] {0.0, 0.0}, new double[] {4.0, 0.0}, projection);
        assertEquals(0.75, projection[0], 1e-12);
        assertEquals(0.0, projection[1], 1e-12);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.math.VectorOpsProjectionTest" --console=plain
```

Expected: compilation failure — `cannot find symbol: method projectPointOntoSegment`.

- [ ] **Step 3: Write the implementation**

Append to `VectorOps.java`, immediately after `perpendicular`:

```java
    /**
     * Closest point on a segment, as the normalised position along it plus the squared distance.
     *
     * <p>Writes into {@code outProjection} rather than returning, so the per-pixel carve loop stays
     * allocation-free. 2D-only by design: reads only indices 0 and 1, no length check (hot path).
     */
    public static void projectPointOntoSegment(
            double[] point, double[] segStart, double[] segEnd, double[] outProjection) {
        final double segX = segEnd[0] - segStart[0];
        final double segZ = segEnd[1] - segStart[1];
        final double segLenSq = segX * segX + segZ * segZ;
        final double toPointX = point[0] - segStart[0];
        final double toPointZ = point[1] - segStart[1];
        final double segParam =
                segLenSq < 1e-12 ? 0.0 : Math.clamp((toPointX * segX + toPointZ * segZ) / segLenSq, 0.0, 1.0);
        final double footToPointX = toPointX - segParam * segX;
        final double footToPointZ = toPointZ - segParam * segZ;
        outProjection[0] = segParam;
        outProjection[1] = footToPointX * footToPointX + footToPointZ * footToPointZ;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.math.VectorOpsProjectionTest" --console=plain
```

Expected: 6 tests, all PASS.

- [ ] **Step 5: Format and commit**

```bash
$GRADLE spotlessApply --offline
git diff --stat
git add src/main/java/me/batata_1/fractal_terrain/math/VectorOps.java \
        src/test/java/me/batata_1/fractal_terrain/math/VectorOpsProjectionTest.java
git commit -m "feat: add allocation-free point-to-segment projection to VectorOps"
```

---


### Task 3: Unpack `ids` and define knot adjacency on `RiverPrimitive`

Spec §3's guard. `RiverPrimitive.ids` packs two numbers and nothing currently unpacks them. The non-obvious requirement is check 3: list adjacency does not imply knot adjacency, because `prefetchChunk` runs a spatial query and a meander that loops back into the chunk contributes several non-consecutive runs of knots that land in neighbouring list slots.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitiveIdsTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `public int RiverPrimitive.channelId()`, `public int RiverPrimitive.knotIndex()`, `public boolean RiverPrimitive.isKnotAdjacentTo(RiverPrimitive other)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitiveIdsTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the channel/knot packing in RiverPrimitive.ids and the adjacency guard built on it. */
class RiverPrimitiveIdsTest {

    /** Mirrors the packing in HydrologicalFeature.RIVER.addPrimitives. */
    private static RiverPrimitive knot(int channelId, int knotIndex) {
        return new RiverPrimitive(
                new double[] {0.0, 0.0},
                1.0,
                RosgenType.A,
                new double[] {0.0, -1.0},
                0.0,
                2.0,
                0.0,
                knotIndex | (((long) channelId) << 32));
    }

    @Test
    void unpacksChannelAndKnotFromTheSameLong() {
        final RiverPrimitive primitive = knot(7, 42);
        assertEquals(7, primitive.channelId());
        assertEquals(42, primitive.knotIndex());
    }

    @Test
    void consecutiveKnotsOfOneChannelAreAdjacent() {
        assertTrue(knot(3, 10).isKnotAdjacentTo(knot(3, 11)));
        assertTrue(knot(3, 11).isKnotAdjacentTo(knot(3, 10)));
    }

    @Test
    void aKnotIsNotAdjacentToItself() {
        assertFalse(knot(3, 10).isKnotAdjacentTo(knot(3, 10)));
    }

    @Test
    void knotsOfDifferentChannelsAreNeverAdjacent() {
        // Two rivers running close: their primitives interleave in the spatial query result.
        assertFalse(knot(3, 10).isKnotAdjacentTo(knot(4, 11)));
    }

    @Test
    void nonConsecutiveKnotsOfOneChannelAreNotAdjacent() {
        // The meander-loopback case: one channel enters the chunk twice, so knots 10 and 57
        // can land in neighbouring slots of the sorted prefetch list.
        assertFalse(knot(3, 10).isKnotAdjacentTo(knot(3, 57)));
    }

    @Test
    void packingSurvivesAChannelIdWithTheHighBitSet() {
        // knotIndex must not be sign-extended out of the low word.
        final RiverPrimitive primitive = knot(Integer.MAX_VALUE, 5);
        assertEquals(Integer.MAX_VALUE, primitive.channelId());
        assertEquals(5, primitive.knotIndex());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.features.RiverPrimitiveIdsTest" --console=plain
```

Expected: compilation failure — `cannot find symbol: method channelId()`.

- [ ] **Step 3: Write the implementation**

Add to `RiverPrimitive.java`, just after the `getType()` override:

```java
    /** Which channel this knot belongs to — the high word of {@link #ids}. */
    public int channelId() {
        return (int) (ids >>> 32);
    }

    /** Position along the channel's spline — the low word of {@link #ids}. */
    public int knotIndex() {
        return (int) ids;
    }

    /**
     * Whether two knots are genuinely consecutive on one channel.
     *
     * <p>Adjacency in the prefetched list does not imply this: a spatial query returns a looping
     * meander as several non-consecutive runs, and joining across a gap would fabricate a segment.
     */
    public boolean isKnotAdjacentTo(RiverPrimitive other) {
        return channelId() == other.channelId() && Math.abs(knotIndex() - other.knotIndex()) == 1;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.features.RiverPrimitiveIdsTest" --console=plain
```

Expected: 6 tests, all PASS.

- [ ] **Step 5: Format and commit**

```bash
$GRADLE spotlessApply --offline
git diff --stat
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitiveIdsTest.java
git commit -m "feat: unpack channel/knot ids and define knot adjacency on RiverPrimitive"
```

---


### Task 4: `NearestChannelSample` and `sampleNearestChannel`

The core of the change: spec §1–§4 assembled. Produce one signed distance and one coherent set of channel attributes for a point, from the nearest knot and its knot-adjacent neighbours.

Note the segment orientation: the segment is always built from the *lower* knot index to the higher one, so `segParam` runs downstream and `lerp(start, end, segParam)` interpolates attributes in the right direction regardless of which neighbour won.

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSample.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSampleTest.java`

**Interfaces:**
- Consumes: `VectorOps.projectPointOntoSegment` (Task 2); `RiverPrimitive.channelId()`, `knotIndex()`, `isKnotAdjacentTo()` (Task 3)
- Produces:
  - `public record NearestChannelSample(double signedPerpDist, double channelWidth, double channelCurvature, double bedElevation, RiverPrimitive.RosgenType rosgenType, int channelId)`
  - `public static NearestChannelSample HydrologyProfileInprinter.sampleNearestChannel(List<HydrologicalPrimitive> primitives, int nearestPrimitiveIndex, double[] point)` — returns `null` when `nearestPrimitiveIndex < 0` or the entry is not a `RiverPrimitive`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSampleTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the one-distance-per-channel sampler. */
class NearestChannelSampleTest {

    /**
     * A knot on a channel running along +x, so the spline normal is perpendicular(+x) = (0,-1)
     * and a point at +z reads as a negative signedPerpDist.
     */
    private static RiverPrimitive knot(int channelId, int knotIndex, double x, double width, double bedElev) {
        return new RiverPrimitive(
                new double[] {x, 0.0},
                4.0,
                RosgenType.C,
                new double[] {0.0, -1.0},
                0.0,
                width,
                bedElev,
                knotIndex | (((long) channelId) << 32));
    }

    /** Three knots of channel 1 at x = 0, 4, 8 — the straight reference reach. */
    private static List<HydrologicalPrimitive> straightReach() {
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        primitives.add(knot(1, 0, 0.0, 2.0, 60.0));
        primitives.add(knot(1, 1, 4.0, 6.0, 50.0));
        primitives.add(knot(1, 2, 8.0, 10.0, 40.0));
        return primitives;
    }

    @Test
    void returnsNullWhenNoPrimitiveWasNearest() {
        assertNull(HydrologyProfileInprinter.sampleNearestChannel(straightReach(), -1, new double[] {0.0, 0.0}));
    }

    @Test
    void straightReachGivesTheExactPerpendicularDistance() {
        // Point 3 above the reach, nearest knot is the middle one at x=4.
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 1, new double[] {4.0, 3.0});
        assertNotNull(sample);
        assertEquals(3.0, Math.abs(sample.signedPerpDist()), 1e-9);
    }

    @Test
    void signMatchesTheLegacyTangentLineProjection() {
        // The sign convention the F/G profiles were tuned against is dot(normal, pt - coord).
        final List<HydrologicalPrimitive> reach = straightReach();
        final RiverPrimitive middle = (RiverPrimitive) reach.get(1);
        for (double side : new double[] {3.0, -3.0}) {
            final double[] point = {4.0, side};
            final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(reach, 1, point);
            assertEquals(Math.signum(middle.d(point)), Math.signum(sample.signedPerpDist()), 1e-12);
        }
    }

    @Test
    void attributesAreInterpolatedAtTheFootPointNotReadOffTheKnot() {
        // Point above x=6, the midpoint between knot 1 (width 6, bed 50) and knot 2 (width 10, bed 40).
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 1, new double[] {6.0, 2.0});
        assertNotNull(sample);
        assertEquals(8.0, sample.channelWidth(), 1e-9);
        assertEquals(45.0, sample.bedElevation(), 1e-9);
    }

    @Test
    void interpolationRunsDownstreamRegardlessOfWhichNeighbourWins() {
        // Nearest knot is 2 (x=8) but the foot lands back at x=6, between knots 1 and 2.
        // If the segment were oriented from the nearest knot outward, width would read 8 the
        // other way round and bed elevation would come out 45 vs 45 — so pin the asymmetric pair.
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 2, new double[] {6.5, 2.0});
        assertNotNull(sample);
        assertEquals(6.0 + 4.0 * 0.625, sample.channelWidth(), 1e-9);
        assertEquals(50.0 - 10.0 * 0.625, sample.bedElevation(), 1e-9);
    }

    @Test
    void aNeighbourFromAnotherChannelIsIgnored() {
        // Channel 2's knot sits closer in list order but must not become a segment endpoint.
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        primitives.add(knot(2, 0, 4.0, 6.0, 99.0));
        primitives.add(knot(1, 0, 0.0, 2.0, 60.0));
        primitives.add(knot(2, 5, 8.0, 6.0, 99.0));
        // Index 1 is the lone knot of channel 1; both neighbours belong to channel 2.
        final double[] point = {0.0, 3.0};
        final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(primitives, 1, point);
        assertNotNull(sample);
        assertEquals(1, sample.channelId());
        assertEquals(2.0, sample.channelWidth(), 1e-9);
        assertEquals(((RiverPrimitive) primitives.get(1)).d(point), sample.signedPerpDist(), 1e-9);
    }

    @Test
    void nonConsecutiveKnotsOfOneChannelFallBackToTheKnotTangentLine() {
        // The meander loopback: same channel, but knots 0 and 57 are not a real segment.
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        primitives.add(knot(1, 0, 0.0, 2.0, 60.0));
        primitives.add(knot(1, 57, 4.0, 6.0, 30.0));
        primitives.add(knot(1, 58, 8.0, 6.0, 30.0));
        final double[] point = {0.0, 3.0};
        final NearestChannelSample sample = HydrologyProfileInprinter.sampleNearestChannel(primitives, 0, point);
        assertNotNull(sample);
        // Knot 0's only list neighbour is knot 57 — not adjacent, so no segment exists.
        assertEquals(((RiverPrimitive) primitives.get(0)).d(point), sample.signedPerpDist(), 1e-9);
        assertEquals(2.0, sample.channelWidth(), 1e-9);
    }

    @Test
    void theNearerOfTheTwoCandidateSegmentsWins() {
        // Point sits above x=1, far nearer the (0,4) segment than anything past knot 1.
        final NearestChannelSample sample =
                HydrologyProfileInprinter.sampleNearestChannel(straightReach(), 1, new double[] {1.0, 2.0});
        assertNotNull(sample);
        assertEquals(2.0, Math.abs(sample.signedPerpDist()), 1e-9);
        assertEquals(2.0 + 4.0 * 0.25, sample.channelWidth(), 1e-9);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.profile.NearestChannelSampleTest" --console=plain
```

Expected: compilation failure — `cannot find symbol: class NearestChannelSample`.

- [ ] **Step 3: Create the record**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSample.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;

/**
 * One channel's cross-section as seen from one point — the single answer that replaces the
 * disagreeing per-primitive tangent-line distances.
 *
 * <p>Every field is read at the foot point on the centreline, not at the nearest knot, so the
 * profile evaluates one coherent cross-section instead of a knot's snapshot of it.
 *
 * <p>Produced by {@code HydrologyProfileInprinter.sampleNearestChannel}, consumed by the per-pixel
 * pass in {@code PopulateNoiseStep}. Purely geometric: turning it into terrain is {@code carveInto}.
 */
public record NearestChannelSample(
        double signedPerpDist,
        double channelWidth,
        double channelCurvature,
        double bedElevation,
        RosgenType rosgenType,
        int channelId) {}
```

- [ ] **Step 4: Implement the sampler**

Replace the deleted stub's location in `HydrologyProfileInprinter.java` with these four methods. Add `import me.batata_1.fractal_terrain.math.VectorOps;` if Spotless has not already kept it.

```java
    /**
     * The signed perpendicular distance from {@code point} to the nearest channel, with that channel's
     * cross-section read at the foot point.
     *
     * <p>Measures against the two-segment polyline through the nearest knot rather than the quintic:
     * the primitives carry no velocity or acceleration, so the true curve cannot be rebuilt here.
     */
    public static NearestChannelSample sampleNearestChannel(
            List<HydrologicalPrimitive> primitives, int nearestPrimitiveIndex, double[] point) {
        if (nearestPrimitiveIndex < 0 || nearestPrimitiveIndex >= primitives.size()) return null;
        if (!(primitives.get(nearestPrimitiveIndex) instanceof RiverPrimitive nearestKnot)) return null;

        final double[] projection = new double[2];
        RiverPrimitive segStart = null;
        RiverPrimitive segEnd = null;
        double bestSegParam = 0.0;
        double bestDistSq = Double.MAX_VALUE;

        for (int offset = -1; offset <= 1; offset += 2) {
            final int neighbourIndex = nearestPrimitiveIndex + offset;
            if (neighbourIndex < 0 || neighbourIndex >= primitives.size()) continue;
            if (!(primitives.get(neighbourIndex) instanceof RiverPrimitive neighbour)) continue;
            if (!nearestKnot.isKnotAdjacentTo(neighbour)) continue;
            // Always orient the segment downstream, so segParam interpolates from the lower knot up.
            final boolean neighbourIsUpstream = neighbour.knotIndex() < nearestKnot.knotIndex();
            final RiverPrimitive start = neighbourIsUpstream ? neighbour : nearestKnot;
            final RiverPrimitive end = neighbourIsUpstream ? nearestKnot : neighbour;
            VectorOps.projectPointOntoSegment(point, start.coord(), end.coord(), projection);
            if (projection[1] >= bestDistSq) continue;
            bestDistSq = projection[1];
            bestSegParam = projection[0];
            segStart = start;
            segEnd = end;
        }

        if (segStart == null) return isolatedKnotSample(nearestKnot, point);
        return interpolatedSample(segStart, segEnd, bestSegParam, bestDistSq, point);
    }

    /** A knot with no knot-adjacent neighbour in range influences the point alone, so its tangent
     *  line is the correct answer — not a degraded one. */
    private static NearestChannelSample isolatedKnotSample(RiverPrimitive knot, double[] point) {
        return new NearestChannelSample(
                knot.d(point), knot.width(), knot.curvature(), knot.elevation(), knot.rosgenType(), knot.channelId());
    }

    /** Signs the distance with the interpolated normal so it agrees with {@link RiverPrimitive#d},
     *  which is the convention the asymmetric Rosgen profiles were tuned against. */
    private static NearestChannelSample interpolatedSample(
            RiverPrimitive segStart, RiverPrimitive segEnd, double segParam, double distSq, double[] point) {
        final double footX = lerp(segStart.coord()[0], segEnd.coord()[0], segParam);
        final double footZ = lerp(segStart.coord()[1], segEnd.coord()[1], segParam);
        final double[] footNormal = VectorOps.normalize(new double[] {
            lerp(segStart.normal()[0], segEnd.normal()[0], segParam),
            lerp(segStart.normal()[1], segEnd.normal()[1], segParam)
        });
        final double side = footNormal[0] * (point[0] - footX) + footNormal[1] * (point[1] - footZ);
        return new NearestChannelSample(
                Math.signum(side) * Math.sqrt(distSq),
                lerp(segStart.width(), segEnd.width(), segParam),
                lerp(segStart.curvature(), segEnd.curvature(), segParam),
                lerp(segStart.elevation(), segEnd.elevation(), segParam),
                segParam < 0.5 ? segStart.rosgenType() : segEnd.rosgenType(),
                segStart.channelId());
    }

    private static double lerp(double from, double to, double t) {
        return from + t * (to - from);
    }
```

- [ ] **Step 5: Run the test to verify it passes**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.profile.NearestChannelSampleTest" --console=plain
```

Expected: 8 tests, all PASS.

- [ ] **Step 6: Format and commit**

```bash
$GRADLE spotlessApply --offline
git diff --stat
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSample.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSampleTest.java
git commit -m "feat: one signed distance per channel via two-segment polyline projection"
```

---


### Task 5: Confirm the polyline approximation is good enough

A checkpoint, not a feature. The spec accepts chord error on the grounds that resampling holds knot spacing near `width/2`. That claim has not been measured. This task measures it, so the decision to skip Newton refinement is evidence-based rather than assumed.

**Files:**
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/PolylineChordErrorTest.java`

**Interfaces:**
- Consumes: `HydrologyProfileInprinter.sampleNearestChannel` (Task 4)
- Produces: nothing consumed by later tasks; a pass/fail gate on the design decision

- [ ] **Step 1: Write the test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/PolylineChordErrorTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * Bounds the error the two-segment polyline introduces against a curve of known analytic distance.
 * Justifies not reconstructing the quintic — see the design doc's "out of scope" section.
 */
class PolylineChordErrorTest {

    /** A circular arc of this radius is the worst case a meander realistically reaches. */
    private static final double ARC_RADIUS = 24.0;

    private static List<HydrologicalPrimitive> arc(int knotCount, double knotSpacing) {
        final List<HydrologicalPrimitive> primitives = new ArrayList<>();
        final double angleStep = knotSpacing / ARC_RADIUS;
        for (int i = 0; i < knotCount; i++) {
            final double angle = i * angleStep;
            final double x = ARC_RADIUS * Math.cos(angle);
            final double z = ARC_RADIUS * Math.sin(angle);
            // Outward radial direction; perpendicular to the arc tangent, so it is the spline normal.
            primitives.add(new RiverPrimitive(
                    new double[] {x, z},
                    4.0,
                    RosgenType.C,
                    new double[] {Math.cos(angle), Math.sin(angle)},
                    1.0 / ARC_RADIUS,
                    2.0,
                    0.0,
                    i | (1L << 32)));
        }
        return primitives;
    }

    @Test
    void chordErrorStaysBelowAQuarterBlockAtRealisticKnotSpacing() {
        // Resampling holds spacing near width/2; width 2 in the relief-pixel frame gives spacing 1.
        final List<HydrologicalPrimitive> primitives = arc(9, 1.0);
        double worstError = 0.0;
        for (double offset = -6.0; offset <= 6.0; offset += 0.25) {
            final double angle = 4 * (1.0 / ARC_RADIUS); // sample around the middle knot
            final double radius = ARC_RADIUS + offset;
            final double[] point = {radius * Math.cos(angle), radius * Math.sin(angle)};
            final NearestChannelSample sample =
                    HydrologyProfileInprinter.sampleNearestChannel(primitives, 4, point);
            // Analytic distance to the circle is exactly |offset|.
            worstError = Math.max(worstError, Math.abs(Math.abs(sample.signedPerpDist()) - Math.abs(offset)));
        }
        assertTrue(worstError < 0.25, "polyline chord error grew to " + worstError + " relief pixels");
    }

    @Test
    void signIsStableAcrossTheCentrelineOnACurve() {
        // The failure this whole change exists to prevent: neighbouring evaluations disagreeing
        // about which side of the channel a point is on.
        final List<HydrologicalPrimitive> primitives = arc(9, 1.0);
        final double angle = 4 * (1.0 / ARC_RADIUS);
        for (double offset = 0.5; offset <= 6.0; offset += 0.25) {
            final double[] outside = {(ARC_RADIUS + offset) * Math.cos(angle), (ARC_RADIUS + offset) * Math.sin(angle)};
            final double[] inside = {(ARC_RADIUS - offset) * Math.cos(angle), (ARC_RADIUS - offset) * Math.sin(angle)};
            final double outsideSign =
                    Math.signum(HydrologyProfileInprinter.sampleNearestChannel(primitives, 4, outside).signedPerpDist());
            final double insideSign =
                    Math.signum(HydrologyProfileInprinter.sampleNearestChannel(primitives, 4, inside).signedPerpDist());
            assertTrue(outsideSign != insideSign, "banks share a sign at offset " + offset);
        }
    }
}
```

- [ ] **Step 2: Run the test**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.profile.PolylineChordErrorTest" --console=plain
```

Expected: 2 tests PASS.

**If `chordErrorStaysBelowAQuarterBlockAtRealisticKnotSpacing` fails, stop and report the measured error.** Do not loosen the threshold to make it green — the threshold is the decision. A failure means the design's "out of scope: reconstructing the true quintic" call needs revisiting with the author, and that is a design conversation, not an implementation one.

- [ ] **Step 3: Commit**

```bash
$GRADLE spotlessApply --offline
git add src/test/java/me/batata_1/fractal_terrain/hydrology/profile/PolylineChordErrorTest.java
git commit -m "test: bound polyline chord error against an analytic arc"
```

---


### Task 6: `blendMin` and `carveInto`

Spec §6, with the amendment. The existing `RosgenProfile.smoothMin(a, b, lambda)` returns `a - sqrt(lambda)/2` when `a == b`, so it never yields ambient exactly — using it here would sink every pixel whose nearest primitive is a river, at any distance. `blendMin` is a quadratic smooth-min that equals `Math.min` exactly once the inputs are farther apart than its blend range.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java` (beside the existing `smoothMin`/`smoothMax` at lines 167-175)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSample.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/BlendMinTest.java`

**Interfaces:**
- Consumes: `NearestChannelSample` (Task 4)
- Produces: `public static double RosgenProfile.blendMin(double a, double b, double blendRange)`; `public double NearestChannelSample.carveInto(double ambientElevation)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/BlendMinTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** Unit tests for the carve blend, and for the invariant that a river only ever lowers ground. */
class BlendMinTest {

    private static final double RANGE = 4.0;

    @Test
    void isExactlyMinWhenInputsAreFartherApartThanTheRange() {
        // This is the property RosgenProfile.smoothMin lacks: terrain the river cannot reach must
        // come back bit-identical, not sunk by a constant.
        assertEquals(10.0, RosgenProfile.blendMin(10.0, 100.0, RANGE), 0.0);
        assertEquals(10.0, RosgenProfile.blendMin(100.0, 10.0, RANGE), 0.0);
        assertEquals(10.0, RosgenProfile.blendMin(10.0, 14.0, RANGE), 0.0);
    }

    @Test
    void neverExceedsTheHardMin() {
        for (double a = -10.0; a <= 10.0; a += 0.5) {
            for (double b = -10.0; b <= 10.0; b += 0.5) {
                assertTrue(RosgenProfile.blendMin(a, b, RANGE) <= Math.min(a, b) + 1e-12);
            }
        }
    }

    @Test
    void isContinuousAtTheRangeBoundary() {
        final double justInside = RosgenProfile.blendMin(10.0, 10.0 + RANGE - 1e-9, RANGE);
        final double justOutside = RosgenProfile.blendMin(10.0, 10.0 + RANGE + 1e-9, RANGE);
        assertEquals(justInside, justOutside, 1e-6);
    }

    @Test
    void isSymmetric() {
        assertEquals(RosgenProfile.blendMin(3.0, 5.0, RANGE), RosgenProfile.blendMin(5.0, 3.0, RANGE), 1e-12);
    }

    @Test
    void aRiverNeverRaisesTerrain() {
        // The invariant the whole composition exists to enforce, swept across the profile's zones.
        final NearestChannelSample sample = new NearestChannelSample(0.0, 6.0, 0.0, 50.0, RosgenType.C, 1);
        for (double ambient = 40.0; ambient <= 90.0; ambient += 1.0) {
            for (double dist = -40.0; dist <= 40.0; dist += 0.5) {
                final NearestChannelSample at =
                        new NearestChannelSample(dist, 6.0, 0.0, 50.0, RosgenType.C, 1);
                assertTrue(
                        at.carveInto(ambient) <= ambient + 1e-9,
                        "carve raised terrain at dist=" + dist + " ambient=" + ambient);
            }
        }
        assertTrue(sample.carveInto(90.0) < 90.0, "a river directly underfoot must cut down");
    }

    @Test
    void terrainFarFromTheChannelIsReturnedUntouched() {
        // 100 relief pixels out the valley cone has climbed far above ambient, so min picks ambient
        // and blendMin must hand it back bit-identical rather than sinking it. Ambient is chosen
        // just above the bed so the cone clears it by far more than CARVE_BLEND_RANGE, without this
        // test needing to know RosgenProfile.C's exact floodPlainLength.
        final NearestChannelSample far = new NearestChannelSample(100.0, 6.0, 0.0, 50.0, RosgenType.C, 1);
        assertEquals(51.0, far.carveInto(51.0), 0.0);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.profile.BlendMinTest" --console=plain
```

Expected: compilation failure — `cannot find symbol: method blendMin`.

- [ ] **Step 3: Add `blendMin` to `RosgenProfile`**

Insert directly after the existing `smoothMin` (`RosgenProfile.java:171-175`):

```java
    /**
     * Smooth minimum that is <em>exactly</em> {@link Math#min} once the inputs differ by more than
     * {@code blendRange}.
     *
     * <p>Unlike {@link #smoothMin}, which biases its result downward even for equal inputs, this
     * leaves terrain the river cannot reach bit-identical — the carve must not sink the whole map.
     */
    public static double blendMin(double a, double b, double blendRange) {
        if (blendRange <= 0.0) return Math.min(a, b);
        final double overlap = Math.max(blendRange - Math.abs(a - b), 0.0) / blendRange;
        return Math.min(a, b) - overlap * overlap * blendRange * 0.25;
    }
```

- [ ] **Step 4: Add `carveInto` to `NearestChannelSample`**

Add to the record body (replacing its empty `{}`):

```java
public record NearestChannelSample(
        double signedPerpDist,
        double channelWidth,
        double channelCurvature,
        double bedElevation,
        RosgenType rosgenType,
        int channelId) {

    /** How far the rim rounds where the valley cone meets untouched ground, in relief pixels. */
    private static final double CARVE_BLEND_RANGE = 2.0;

    /**
     * Cuts this cross-section into the shell-carved terrain.
     *
     * <p>Needs no influence radius: outside the floodplain the profile is a cone rising away from
     * the channel, so the min hands back ambient wherever that cone clears it.
     */
    public double carveInto(double ambientElevation) {
        final RosgenProfile profile = RosgenProfile.of(rosgenType == null ? RosgenType.A : rosgenType);
        final double bedTarget =
                bedElevation + profile.delta(channelId, signedPerpDist, channelWidth, channelCurvature);
        return RosgenProfile.blendMin(ambientElevation, bedTarget, CARVE_BLEND_RANGE);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```
$GRADLE test --offline --tests "me.batata_1.fractal_terrain.hydrology.profile.BlendMinTest" --console=plain
```

Expected: 6 tests, all PASS.

Note: `aRiverNeverRaisesTerrain` exercises `RosgenProfile.delta`, which currently returns a hardcoded `-10` inside the channel margin. That is the author's debug instrumentation and the test is written to tolerate it — the assertion is one-sided (never raises), so reverting the `-10` later will not break it.

- [ ] **Step 6: Format and commit**

```bash
$GRADLE spotlessApply --offline
git diff --stat
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/NearestChannelSample.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/BlendMinTest.java
git commit -m "feat: compose the carve with an exact-outside-range blend min"
```

---


### Task 7: Wire the per-pixel pass

The loop in `PopulateNoiseStep.fineGrainedPrimitivePass` is currently empty — the old weighted merge was deleted and nothing replaced it. This task fills it in, and finishes the renames the design calls for.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java:68-78` (rename `resolveRiverNearestId`)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java` (static `waterLine`)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java` (drop the instance `waterLine` override)
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/PopulateNoiseStep.java:52-70`

**Interfaces:**
- Consumes: `NearestChannelSample`, `sampleNearestChannel` (Task 4), `carveInto` (Task 6)
- Produces: `public static int HydrologyProfileInprinter.resolveNearestPrimitiveIndex(List<HydrologicalPrimitive> primitives, double[] point)`; `public static float HydrologicalPrimitive.waterLine(double channelWidth)`

- [ ] **Step 1: Rename and publish the index resolver**

In `HydrologyProfileInprinter.java`, replace the private `resolveRiverNearestId` with:

```java
    /** Index into {@code primitives} of the river knot whose coordinate is nearest {@code point}, or
     *  -1 when none is. Relies on the comparator sorting rivers first, so the scan can stop early. */
    public static int resolveNearestPrimitiveIndex(List<HydrologicalPrimitive> primitives, double[] point) {
        int nearestIndex = -1;
        double nearestDistSq = Double.MAX_VALUE;
        for (int i = 0; i < primitives.size(); i++) {
            if (!(primitives.get(i) instanceof RiverPrimitive river)) break;
            final double distSq = VectorOps.distanceSquared(point, river.coord());
            if (distSq >= nearestDistSq) continue;
            nearestIndex = i;
            nearestDistSq = distSq;
        }
        return nearestIndex;
    }
```

This also fixes the double `distanceSquared` call the original made per candidate.

- [ ] **Step 2: Make `waterLine` a function of the interpolated width**

In `HydrologicalPrimitive.java`, add a static beside the existing default:

```java
    /** Water surface offset below the bank, stepped by channel size. Static because the carve reads
     *  it at an interpolated width, not at any one primitive's. */
    static float waterLine(double channelWidth) {
        if (channelWidth <= 1.5) return -1;
        if (channelWidth <= 2.5) return -2;
        return -3;
    }
```

In `RiverPrimitive.java`, replace the instance override's body so the two cannot drift:

```java
    @Override
    public float waterLine() {
        return HydrologicalPrimitive.waterLine(width);
    }
```

- [ ] **Step 3: Fill in the per-pixel loop**

In `PopulateNoiseStep.java`, delete the stale locals (`refinedElev`, `nearestDist`, `weight`, `weightedElev`, `nearestPrimitive`), the duplicated `primitives.sort(...)` (`prefetchChunk` already sorts), and the stray double semicolon on the `prefetchChunk` line. Replace lines 52-70 with:

```java
        final List<HydrologicalPrimitive> primitives =
                imprinter.prefetchChunk(chunkCenterPixelX, chunkCenterPixelZ, chunkRadiusPx);
        final double[] mutablePt = new double[2];

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int pos = (dx << 4) + dz;
                final float ambientElevation = interpolatedElevs[pos];
                mutablePt[0] = (startX + dx) / scale;
                mutablePt[1] = (startZ + dz) / scale;

                final int nearestPrimitiveIndex =
                        HydrologyProfileInprinter.resolveNearestPrimitiveIndex(primitives, mutablePt);
                final NearestChannelSample sample =
                        HydrologyProfileInprinter.sampleNearestChannel(primitives, nearestPrimitiveIndex, mutablePt);

                if (sample == null) {
                    riverDifference[pos] = 0;
                    riverType[pos] = null;
                    interpolatedElevs[pos] = Math.max(bottom, ambientElevation) + seaLevel - 1;
                    continue;
                }

                final float refinedElev = (float) sample.carveInto(ambientElevation);
                riverDifference[pos] = refinedElev - ambientElevation;
                interpolatedElevs[pos] = Math.max(bottom, refinedElev) + seaLevel - 1;

                if (Math.abs(sample.signedPerpDist()) <= (sample.channelWidth() / 2) + 0.25) {
                    riverType[pos] = HydrologicalPrimitive.HydrologicalFeature.RIVER;
                    waterElev[pos] = (float) (HydrologicalPrimitive.waterLine(sample.channelWidth())
                            + Math.max(bottom, sample.bedElevation())
                            + seaLevel
                            - 1);
                } else {
                    riverType[pos] = null;
                }
            }
        }
```

Add the import `me.batata_1.fractal_terrain.hydrology.profile.NearestChannelSample;`.

- [ ] **Step 4: Verify the whole tree compiles and the suite matches its baseline**

```
$GRADLE build --offline --console=plain
```

Expected: BUILD SUCCESSFUL, with `spotlessCheck` clean. Then:

```
$GRADLE test --offline --console=plain
```

Expected: the four new test classes green, and the pre-existing failures **identical to the Task 1 baseline** — same test names, same count. Any new failure is yours. Any baseline failure that now passes is worth reporting but is not a problem.

- [ ] **Step 5: Verify visually — this is the real acceptance test**

```
$GRADLE localRiverTest --offline --console=plain
```

Inspect the PNG dumps under `run/debug`. Two specific things, both of which were the point of the change:

1. **Bed width no longer breathes between knots along a bend.** Previously the channel visibly pinched and swelled at knot spacing; it should now hold a constant width through curves.
2. **The bed/floodplain boundary is a single clean contour, not a fringe.** The old weighted merge produced a dithered band where primitives disagreed about which zone a pixel was in.

Then launch the client and fly a river:

```
$GRADLE runClient --offline
```

- [ ] **Step 6: Commit**

```bash
$GRADLE spotlessApply --offline
git diff --stat
git add src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/PopulateNoiseStep.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileInprinter.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java
git commit -m "feat: carve the river bed from one signed distance per channel"
```

---

## Known-Deferred

Listed so a reviewer does not read them as oversights. All three are the author's explicit calls, recorded in the design doc.

- **Confluences show a seam** where the nearest-channel winner flips. Junction primitives will resolve this; blending channels here was rejected.
- **`RosgenProfile.delta:232`'s `return -10`** stays. It is debug instrumentation to be reverted separately. This change makes it more conspicuous — a crisp flat-bottomed trench rather than a noisy one — which is expected, not a regression.
- **The quintic is not reconstructed.** Task 5 bounds the cost of that choice; revisit only if it fails.
- **`RiverPrimitive.w(pt)` and `h(pt)` are now unused by the per-pixel pass** but are still used by the tile-level `carveRiverShells`. Leave them.

## Known-Deferred

Listed so a reviewer does not read them as oversights. All three are the author's explicit calls, recorded in the design doc.

- **Confluences show a seam** where the nearest-channel winner flips. Junction primitives will resolve this; blending channels here was rejected.
- **`RosgenProfile.delta:232`'s `return -10`** stays. It is debug instrumentation to be reverted separately. This change makes it more conspicuous — a crisp flat-bottomed trench rather than a noisy one — which is expected, not a regression.
- **The quintic is not reconstructed.** Task 5 bounds the cost of that choice; revisit only if it fails.
- **`RiverPrimitive.w(pt)` and `h(pt)` are now unused by the per-pixel pass** but are still used by the tile-level `carveRiverShells`. Leave them.
