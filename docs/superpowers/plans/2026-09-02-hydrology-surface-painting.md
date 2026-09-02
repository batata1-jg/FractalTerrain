# Hydrology Surface Painting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** Let a river primitive choose the blocks its own carve exposes, so a type-A headwater trench
comes up cobble and a type-C lowland meander comes up sand-over-silt instead of both coming up grass.

**Architecture:** The carve's per-lattice-point footprint scale `d` is remapped through three linear
pieces with fixed breakpoints (`0.25` = bed/floodplain, `0.5` = floodplain/influence), published into a
new `Types.RIVER_DIST` heightmap channel alongside the existing `Types.RIVER_TYPE`, and read back at
surface-build time. `hydrology/` answers in Minecraft-free `SurfaceMaterial` tokens; a table under
`world/gen/surfacebuilder/` maps token to `BlockState`.

**Tech Stack:** Java 21, Minecraft 1.20.1 / Fabric Loom, JUnit 5 (`useJUnitPlatform()`), Spotless with
palantirJavaFormat, fastutil (already on the classpath transitively).

**Spec:** `docs/superpowers/specs/2026-09-02-hydrology-surface-painting-design.md`

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Read the guidelines before the first edit.** Root `CLAUDE.md` -> the `README.md`/`CLAUDE.md` in or
  above the directory you are editing -> `ARCHITECTURE.md` (this change crosses providers and the
  generation pipeline) -> `.claude/conventions/CLAUDE.md`, then `documentation.md`, `structural.md`,
  `code-quality/`, `class-structure.md`, `performance.md`, `temporal.md`, `intent-markers.md`.
- **Open the message that first proposes or makes a change with** `Guidelines: <paths read>`.
- **`hydrology/` stays Minecraft-free.** No file under
  `src/main/java/me/batata_1/fractal_terrain/hydrology/` may import `net.minecraft`. This is what lets
  the golden suite run as plain JUnit; breaking it breaks every hydrology test.
- **Hot-path rules** (`ARCHITECTURE.md` "Hot/cold line of abstraction",
  `.claude/conventions/performance.md`): `RiverInfluenceCarve.carvePrimitive`'s lattice loop and
  `buildSurface`'s 16x16 column loop are both below the line. No heap allocation in a loop body, no
  boxing, no varargs, no iterator/stream pipelines, no new per-iteration virtual dispatch. Hoist every
  division out of a loop. Declare a deliberate allocation-free or dispatch-free pattern with
  `:PERF: [what]; [why]`.
- **Collections:** `it.unimi.dsi.fastutil` over `java.util`; a fixed-size or hot-loop structure is a
  primitive array, not a collection. Do not add a fastutil dependency line to `build.gradle` — it is
  already on the classpath transitively.
- **Docstring budgets are hard** (`.claude/conventions/documentation.md` Tier 3): field 1 line, method
  3 lines, class 10 lines. At most one line describes the thing itself; every other line answers *why*
  or *where in the pipeline*. Do not state determinism, thread-safety, purity, nullability already
  carried by `@Nullable`, complexity bounds, or units already in the name.
- **Comments are timeless present** (`.claude/conventions/temporal.md`). Write every comment as if the
  code had always been this way. No "now", "added", "replaces", "previously", "TODO".
- **`CLAUDE.md` is a pure index; `README.md` carries the invisible knowledge.** Never put rationale in a
  `CLAUDE.md`.
- **Class member order** (`.claude/conventions/class-structure.md`): constructors and public API first,
  then fields, then private methods, then debug/test-only members. Applies to new classes; do not churn
  the layout of an existing class touched for another reason.
- **Formatting:** run `gradle spotlessApply` before every commit. `gradle build` runs `spotlessCheck`
  and fails on unformatted code.
- **Gradle:** there is no checked-in `gradlew`. Invoke a local `gradle` matching
  `gradle/wrapper/gradle-wrapper.properties`. If working in a git worktree, copy
  `libs/onnxruntime/teste.jar` into it first — `libs/` is git-ignored and without it the build reports
  roughly 132 phantom compile errors.

### Test baseline

Measured 2026-09-02 at `df7ca2e`: **102 tests, 9 failed, 1 skipped**. The nine are `RosgenKeyTest` (4),
`RiverGoldenTest` (2), `MeandersGoldenTest` (1), `CentrelineTest` (1), `ReachMetricsSamplerTest` (1).
Full failure messages are archived in `.superpowers/conventions-alignment/post-migration-failures.txt`.

**Treat this as a claim to re-verify, not a fact** — the suite has broken and been repaired several
times. Re-measure at `HEAD` before blaming your own change, and compare the *actual failure messages* in
`build/test-results/test/*.xml` against the archived file, not just which test names failed.

### No golden should move

`RiverInfluenceCarve.computeRiverGrid` and the `carvePrimitive` helper it drives have exactly one
production caller: `PopulateNoiseStep.fineGrainedPrimitivePass`, which runs at chunk-fill time. The
three tile-level shell carves (`GlobalNetworkBuilder.build`, `LocalNetworkBuilder.build`,
`RiverProvider.carveRivers`) all go through `carveRiverInfluence` -> `computeRiverInfluenceGrid` ->
`carvePrimitiveInfluence`, which this plan does not touch.

No JUnit test exercises chunk fill. So **the banding in Task 1 must leave all 102 test outcomes
byte-identical**, `ComputeRiverGridTest` included (its assertions were checked against the banded
arithmetic while this plan was written; see Task 1 Step 4). If a golden moves, that is a defect to
diagnose, not a re-baseline to record. This corrects the spec's Verification section 3, which assumed
the goldens would move.

Terrain *in a generated world* does change, everywhere rivers reach — that is decision D2 and the point
of the change. It is simply not what the JUnit suite measures.

---

## File Structure

| File | Responsibility |
| ---- | -------------- |
| `hydrology/profile/RiverInfluenceCarve.java` | The `band` remap, its two breakpoint constants, and the banded `d` inside `carvePrimitive` |
| `hydrology/profile/SurfaceMaterial.java` | New. The Minecraft-free material vocabulary a profile answers in |
| `hydrology/profile/HydrologyProfile.java` | New `riverPaintDepth` default, beside `shellElevation` |
| `hydrology/profile/RosgenProfile.java` | The shared `riverPaintDepth` body plus per-constant `bedColumn`/`floodPlainColumn` hooks |
| `hydrology/features/RiverPrimitive.java` | Allocation-free `RosgenType.byOrdinal` |
| `hydrology/features/HydrologicalPrimitive.java` | `HydrologicalFeature.profileFor`, resolving a packed tag to a profile |
| `config/HydrologyTuning.java` | `MAX_RIVER_PAINT_DEPTH`, the contract bound on the scratch buffer |
| `storage/FractalTerrainHeightmap.java` | `Types.RIVER_DIST` channel |
| `world/gen/populatenoise/PopulateNoiseStep.java` | Publishes `buffers.dist` into `RIVER_DIST` |
| `world/gen/surfacebuilder/HydrologySurfacePalette.java` | New. The only place `SurfaceMaterial` meets `BlockState` |
| `world/gen/surfacebuilder/FractalTerrainSurfaceSystem.java` | Per-column paint preamble and the paint branch in the depth loop |
| `world/gen/surfacebuilder/README.md` | New. Why the column is tabulated, why `DEFER` exists, the silt substitution, the `fh` oddity |

Tests: `ComputeRiverGridTest` is extended (Task 1) rather than split — `.claude/conventions/structural.md`
prefers extending an existing test file, and the banding is the same subject that file already covers.
`RiverPaintDepthTest` and `RiverDistChannelTest` are new because they are distinct module boundaries.

---

## Task 1: Band the carve coordinate

`RiverInfluenceCarve.carvePrimitive` remaps its footprint scale through three linear pieces so that
`0.25` is the bed/floodplain boundary and `0.5` is the floodplain/influence boundary for every
primitive, regardless of its width. A consumer then classifies a point with two comparisons against
constants and needs no access to the primitive.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `public static final double RiverInfluenceCarve.BED_EDGE = 0.25`
  - `public static final double RiverInfluenceCarve.FLOODPLAIN_EDGE = 0.5`
  - `static double RiverInfluenceCarve.band(double raw, double marginNorm, double floodPlainNorm, double bedSlope, double floodPlainSlope, double outerSlope)`
    — package-private so the test in the same package can hit the breakpoints exactly.
  - `RiverInfluenceCarve.GridBuffers.dist` holds the banded coordinate rather than the raw footprint
    scale. Task 2 publishes it; Task 3 compares against `BED_EDGE` / `FLOODPLAIN_EDGE`.

### Why the control points must be clamped

The spec's Method section substitutes `marginLen` and `floodPlainLen` into `carvePrimitive`'s own `max`
expression and stops there. Three real inversions break that:

- **`floodPlainNorm` can exceed 1.** `influence` is clamped to `MIN_INFLUENCE_RADIUS = 2.0`, while
  `RosgenProfile.E.floodPlainLength(0.7)` returns about `2.09`. `influenceLen` is `influence`, so
  `floodPlainNorm` reaches about `1.04` and the third piece would run backwards.
- **`marginNorm` can exceed `floodPlainNorm`.** `RosgenProfile.E.floodPlainLength(16.0)` returns about
  `5.33` against a `marginLen` of `8.0`, so the second piece would run backwards.
- **`marginNorm` can equal `floodPlainNorm`.** `HydrologyProfile`'s default `floodPlainLength` returns
  `width / 2`, exactly `marginLen`; `RosgenProfile.DA` inherits it. The second piece is then empty and
  its slope is a division by zero.

So clamp both control points into `[0, 1]` and into order, and guard every reciprocal against a zero
denominator.

### Why the in-band mask moves onto the raw scale

`mask` currently reads `d <= 1.0`. With clamped control points `band` is monotone non-decreasing and
`band(1) == 1` whenever `floodPlainNorm < 1`, so the two are equivalent in the normal case — but when
`floodPlainNorm` clamps to exactly `1`, `band` saturates at `FLOODPLAIN_EDGE` and a point well outside
the footprint would test as in-band. Masking on `raw` is exact in every case and costs nothing.

- [ ] **Step 1: Write the failing tests**

Append to `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java`,
inside the existing class, after `theTypeMaskFollowsTheNearestPrimitiveNotTheFirst`:

```java
    // ---- Banded footprint coordinate ----

    private static double bandOf(double raw, double marginNorm, double floodPlainNorm) {
        final double bedSlope = marginNorm > 0 ? RiverInfluenceCarve.BED_EDGE / marginNorm : 0.0;
        final double floodPlainSlope = floodPlainNorm > marginNorm
                ? (RiverInfluenceCarve.FLOODPLAIN_EDGE - RiverInfluenceCarve.BED_EDGE)
                        / (floodPlainNorm - marginNorm)
                : 0.0;
        final double outerSlope = floodPlainNorm < 1.0
                ? (1.0 - RiverInfluenceCarve.FLOODPLAIN_EDGE) / (1.0 - floodPlainNorm)
                : 0.0;
        return RiverInfluenceCarve.band(raw, marginNorm, floodPlainNorm, bedSlope, floodPlainSlope, outerSlope);
    }

    @Test
    void bandPinsTheControlPointsToTheFixedBreakpoints() {
        final double margin = 0.2;
        final double floodPlain = 0.24;

        assertEquals(0.0, bandOf(0.0, margin, floodPlain), 1e-12, "the centreline");
        assertEquals(RiverInfluenceCarve.BED_EDGE, bandOf(margin, margin, floodPlain), 1e-12, "the bank");
        assertEquals(
                RiverInfluenceCarve.FLOODPLAIN_EDGE,
                bandOf(floodPlain, margin, floodPlain),
                1e-12,
                "the floodplain edge");
        assertEquals(1.0, bandOf(1.0, margin, floodPlain), 1e-12, "the influence rim");
    }

    @Test
    void bandIsMonotonicAcrossTheSweptRange() {
        final double margin = 0.2;
        final double floodPlain = 0.24;
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 1000; i++) {
            final double raw = i / 500.0;
            final double banded = bandOf(raw, margin, floodPlain);
            assertTrue(banded >= previous, "band fell at raw " + raw);
            previous = banded;
        }
    }

    @Test
    void bandIsFiniteWhenTheMarginAndFloodPlainCoincide() {
        // HydrologyProfile's default floodPlainLength returns width / 2, which is marginLen exactly;
        // RosgenProfile.DA inherits it, so the middle piece is empty for a real profile.
        final double coincident = 0.2;
        for (int i = 0; i <= 100; i++) {
            final double raw = i / 50.0;
            assertTrue(Double.isFinite(bandOf(raw, coincident, coincident)), "not finite at raw " + raw);
        }
        assertEquals(RiverInfluenceCarve.BED_EDGE, bandOf(coincident, coincident, coincident), 1e-12);
    }

    @Test
    void bandIsFiniteWhenTheFloodPlainReachesTheRim() {
        // A minimum-influence primitive can push floodPlainLength past its own rim; the clamp lands the
        // control point on exactly 1 and the outer piece becomes empty.
        for (int i = 0; i <= 100; i++) {
            final double raw = i / 50.0;
            assertTrue(Double.isFinite(bandOf(raw, 0.2, 1.0)), "not finite at raw " + raw);
        }
    }

    @Test
    void theCarveWritesTheBandedCoordinateIntoDist() {
        // dist starts at UNSET_MIN_DIST, so a lone primitive owns every cell it reaches outright and
        // dist lands on the banded value exactly, with no blend against a competitor. knot() gives
        // influenceLen 5, influenceWidth 7.5, marginLen 1 and (type A) floodPlainLen 1.2.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        // (8, 8) is the centreline: raw 0.
        assertEquals(0.0f, b.dist[idx(8, 8)], 1e-6f, "the centreline is the bottom of the bed band");
        // (8, 7) sits one pixel along the flow tangent, so raw == marginNorm exactly.
        assertEquals(
                (float) RiverInfluenceCarve.BED_EDGE,
                b.dist[idx(8, 7)],
                1e-6f,
                "one margin length out is the bed/floodplain boundary");
        // (8, 3) sits influenceLen along the flow tangent, so raw == 1 exactly.
        assertEquals(1.0f, b.dist[idx(8, 3)], 1e-6f, "the influence rim");
    }

    @Test
    void theBandedCoordinateIsIndependentOfPrimitiveWidth() {
        // The whole point of D1: a consumer classifies a point against BED_EDGE without knowing which
        // primitive claimed it. A ten-times-wider primitive still calls its own bank the bed edge, so
        // lattice cells that were floodplain for the narrow knot above are bed here.
        final RiverPrimitive wide = new RiverPrimitive(
                new double[] {8.0, 8.0}, 50.0, RosgenType.A, new double[] {1.0, 0.0}, 0.0, 20.0, 100.0, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(wide),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals(0.0f, b.dist[idx(8, 8)], 1e-6f, "the centreline");
        assertTrue(
                b.dist[idx(8, 3)] < RiverInfluenceCarve.BED_EDGE,
                "five pixels out is still bed for a 20-wide channel");
    }

    @Test
    void unclaimedCellsKeepTheUnsetSeedRatherThanABandedValue() {
        // Task 2 publishes dist into a heightmap channel gated on RIVER_TYPE; an unclaimed cell must
        // stay recognisably unset rather than reading as a valid influence-band coordinate.
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        assertEquals((float) RiverInfluenceCarve.UNSET_MIN_DIST, b.dist[idx(0, 0)], "the corner is unclaimed");
        assertEquals(HydrologicalFeature.NONE, b.typeMask[idx(0, 0)], "and its type agrees");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `gradle test --tests "me.batata_1.fractal_terrain.hydrology.profile.ComputeRiverGridTest"`

Expected: compilation of the test source set FAILS with `cannot find symbol: BED_EDGE`,
`cannot find symbol: FLOODPLAIN_EDGE`, `cannot find symbol: method band(...)`.

- [ ] **Step 3: Implement the band**

In `RiverInfluenceCarve.java`, add the two breakpoint constants next to `UNSET_MIN_DIST` (the same
"constants the carve publishes" group, which already sits between the public API and the private
methods):

```java
    /** The banded coordinate at a primitive's bank, where the bed gives way to the floodplain. Fixed for
     *  every primitive regardless of width, so a paint consumer needs no access to the primitive. */
    public static final double BED_EDGE = 0.25;

    /** The banded coordinate at a primitive's floodplain edge, where the influence band begins. */
    public static final double FLOODPLAIN_EDGE = 0.5;
```

Replace the `invLen` / `invWidth` hoist block in `carvePrimitive` (the two lines immediately before the
`for (int row = rowMin; ...)` merge loop) with:

```java
        final double invLen = 1.0 / influenceLen;
        final double invWidth = 1.0 / influenceWidth;
        // Control points clamped into [0, 1] and into order. floodPlainLength is a free per-type law:
        // RosgenProfile.E returns less than marginLen at maximum width, a minimum-influence primitive
        // can push its floodplain past its own rim, and the HydrologyProfile default returns exactly
        // marginLen. Each inversion would give the band a negative slope.
        final double marginNorm = Math.min(Math.max(marginLen * invLen, marginLen * invWidth), 1.0);
        final double floodPlainNorm =
                Math.min(Math.max(Math.max(floodPlainLen * invLen, floodPlainLen * invWidth), marginNorm), 1.0);
        // :PERF: reciprocals hoisted per primitive; the merge loop below runs per lattice point and
        // carries no division. A zero denominator means the piece it scales is empty, so the slope is
        // never read and 0 keeps it finite.
        final double bedSlope = marginNorm > 0.0 ? BED_EDGE / marginNorm : 0.0;
        final double floodPlainSlope =
                floodPlainNorm > marginNorm ? (FLOODPLAIN_EDGE - BED_EDGE) / (floodPlainNorm - marginNorm) : 0.0;
        final double outerSlope = floodPlainNorm < 1.0 ? (1.0 - FLOODPLAIN_EDGE) / (1.0 - floodPlainNorm) : 0.0;
```

Inside the merge loop, replace the two lines computing `d` and `mask` (and their comment) with:

```java
                // How far the footprint rectangle must be scaled to swallow the point: 1 exactly at the
                // rim, so the recurrence ranks primitives by rectangle penetration, not radial distance.
                final double raw = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
                final double d = band(raw, marginNorm, floodPlainNorm, bedSlope, floodPlainSlope, outerSlope);
                // Tested on the raw scale rather than the banded one: where floodPlainNorm clamps to 1
                // the band saturates at FLOODPLAIN_EDGE and a point past the rim would read as in-band.
                final double mask = raw <= 1.0 ? 1.0 : 0.0;
```

Add `band` to the private-method section, immediately after `carvePrimitiveInfluence`:

```java
    /**
     * A raw footprint scale remapped onto the banded coordinate the paint side reads. Exists so bed and
     * floodplain assert themselves in the merge — a tributary's bed outranks a trunk's influence band —
     * and so a consumer classifies a point against {@link #BED_EDGE} and {@link #FLOODPLAIN_EDGE}
     * without knowing which primitive claimed it.
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
        if (raw <= floodPlainNorm) return BED_EDGE + (raw - marginNorm) * floodPlainSlope;
        return FLOODPLAIN_EDGE + (raw - floodPlainNorm) * outerSlope;
    }
```

`carvePrimitiveInfluence` keeps its existing two-piece `dd` untouched. Unifying the shell onto this
helper would move shell terrain and bed terrain in one change, leaving a regression unattributable.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `gradle test --tests "me.batata_1.fractal_terrain.hydrology.profile.ComputeRiverGridTest"`

Expected: PASS, 19 tests (12 pre-existing plus 7 new).

The 12 pre-existing assertions are unaffected because each is either a single-primitive case — where
`dist` seeds at `UNSET_MIN_DIST = 64`, so the smoothstep saturates at `w = 1` for any `d <= 1` and the
banded value never reaches the assertion — or a two-primitive case whose winner is unchanged under the
remap. If one of them fails, the band is not monotone: check the control-point clamps before touching
the expectation.

- [ ] **Step 5: Run the full suite and compare against the baseline**

Run: `gradle test`

Expected: **109 tests, 9 failed, 1 skipped** — the same nine failures, with the same messages. Diff
`build/test-results/test/*.xml` against
`.superpowers/conventions-alignment/post-migration-failures.txt`. A golden moving is a defect: no
golden covers this code path (see "No golden should move" above).

- [ ] **Step 6: Record the banded coordinate in the profile README**

In `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md`, replace the paragraph that
begins **`d` is a rectangle scale, not a radius.** with:

```markdown
**`d` is a banded rectangle scale, not a radius.** Each primitive's footprint is the rotated rectangle
the spatial index stores it under — `influenceLen` along the flow tangent, `influenceWidth` across it.
The raw scale is the factor that rectangle must be scaled by to contain the lattice point,
`max(|tang| / influenceLen, |perp| / influenceWidth)`, so `raw <= 1` is exactly "inside the footprint"
and is what the in-band mask tests. `carvePrimitive` then remaps that raw scale through
`RiverInfluenceCarve.band`, three linear pieces pinned so `BED_EDGE` (0.25) always lands on the bank and
`FLOODPLAIN_EDGE` (0.5) always lands on the floodplain edge, whatever the primitive's width. `d` is
dimensionless: `UNSET_MIN_DIST` and the blend width are read against that banded scale, not against
relief-pixels. Both half-extents come from `RiverPrimitive.getLength()` / `getWidth()`, so a primitive
indexed under a non-square rectangle carves the shape it was indexed under.

The banding is load-bearing in two directions. It gives the paint side a width-independent coordinate: a
consumer classifies a point into bed / floodplain / influence with two comparisons against constants and
no access to the primitive. And because the banded value feeds the smoothed-min recurrence, it changes
which primitive wins where — a small tributary's bed and floodplain outrank a large trunk's influence
band — and the same value drives the `acc[]` blend weight through
`acc[a + 2] = 1 - clamp(dist, 0, 1)`, so carved elevation moves too: a floodplain edge that weighted
near 1 weights 0.5. Retune `BED_EDGE` and `FLOODPLAIN_EDGE` if the reach is wrong; the paint side needs
a width-independent coordinate either way.

The control points are clamped into `[0, 1]` and into order before the slopes are taken.
`RosgenProfile.floodPlainLength` is a free per-type law: `E` returns less than `marginLen` at maximum
width, a minimum-influence primitive can push its floodplain past its own rim, and the
`HydrologyProfile` default (which `DA` inherits) returns exactly `marginLen`. Each inversion would
otherwise give the band a negative slope or a division by zero.

`marginLen` and `floodPlainLen` are substituted into the same `max` the raw scale itself comes out of,
not applied to the perpendicular axis alone. So the bed band extends along the flow tangent as well as
across it, and a channel's last primitive ends in an isotropic cap of the bed's own radius rather than
being cut off square — which is what a segment ending should look like. The alternative, banding only
the perpendicular axis, would leave the along-flow coordinate unbanded and break the paint side's
width-independence at every channel end.

`carvePrimitiveInfluence`, the tile-level shell carve, keeps its own two-piece `dd` remap and is not
banded. Unifying the two would move shell terrain and bed terrain together, leaving any regression
unattributable.
```

- [ ] **Step 7: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java
git commit -F - <<'MSG'
feat(hydrology): band the carve footprint scale onto fixed breakpoints

carvePrimitive remaps its raw footprint scale through three linear pieces so
0.25 is always the bank and 0.5 always the floodplain edge, whatever the
primitive's width. The paint side can then classify a point with two
comparisons against constants.

Banding happens before the smoothed-min merge, so it also changes which
primitive wins where and what elevation the carve blends to: a tributary's bed
outranks a trunk's influence band. Chunk-fill terrain moves by design; no JUnit
golden covers this path, so the suite is unchanged.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01P3CeXwCzWLPfDJMrHdbvZC
MSG
```

---

## Task 2: Publish the banded distance as a heightmap channel

The carve computes the banded coordinate and discards it when `fineGrainedPrimitivePass` returns. A new
`float[256]` channel carries it to the surface builder, alongside the `long[256]` `RIVER_TYPE` already
written from the same loop.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/PopulateNoiseStep.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/storage/RiverDistChannelTest.java` (create)

**Interfaces:**
- Consumes: `RiverInfluenceCarve.UNSET_MIN_DIST` and the banded `GridBuffers.dist` from Task 1.
- Produces: `FractalTerrainHeightmap.Types.RIVER_DIST`, a `float[256]` payload read row-major as
  `[localX * 16 + localZ]` by the inherited `Types.get`. Task 4 reads it as
  `(float[]) heightmaps.get(Types.RIVER_DIST)`.

`Types.ordinal()` indexes `FractalTerrainHeightmap.data`, but no `Types` ordinal is persisted
(`RIVER_TYPE`'s own `:SCHEMA:` marker records that these channels are recomputed per chunk). Append
`RIVER_DIST` after `RIVER_TYPE` anyway, so the two river channels read as a pair.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/storage/RiverDistChannelTest.java`:

```java
package me.batata_1.fractal_terrain.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import me.batata_1.fractal_terrain.storage.FractalTerrainHeightmap.Types;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Payload shape of the banded-distance channel the bed carve publishes for the surface painter. */
class RiverDistChannelTest {

    @Test
    void allocatesOneFloatPerColumn() {
        // The channel is created per cached chunk heightmap, so its size is a memory budget rather than
        // an implementation detail: 256 floats against the 256 longs RIVER_TYPE already costs.
        final Object payload = Types.RIVER_DIST.creator().apply(new ChunkPos(0, 0));
        assertInstanceOf(float[].class, payload);
        assertEquals(256, ((float[]) payload).length);
    }

    @Test
    void readsRowMajorLikeEveryOtherFloatChannel() {
        // RIVER_TYPE overrides get() because it is a long[]; RIVER_DIST must not, so a caller can read
        // it either per block or as a raw array without knowing which.
        final float[] payload = (float[]) Types.RIVER_DIST.creator().apply(new ChunkPos(0, 0));
        payload[3 * 16 + 5] = 0.375f;
        assertEquals(0.375f, Types.RIVER_DIST.get(payload, 3, 5));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "me.batata_1.fractal_terrain.storage.RiverDistChannelTest"`

Expected: compilation of the test source set FAILS with `cannot find symbol: variable RIVER_DIST`.

- [ ] **Step 3: Add the channel and publish into it**

In `FractalTerrainHeightmap.java`, insert immediately after the `RIVER_TYPE` constant's closing `},`
and before `WATER_HEIGHT`:

```java
        // The winning primitive's banded footprint coordinate, from RiverInfluenceCarve.band. Only
        // meaningful where RIVER_TYPE is not NONE; elsewhere it holds the carve's unset seed.
        RIVER_DIST(pos -> new float[1 << 8]),
```

In `PopulateNoiseStep.fineGrainedPrimitivePass`, add the payload handle after the `riverType` line:

```java
        final float[] riverDist = (float[]) heightmap.get(Types.RIVER_DIST);
```

and, inside the per-column loop, immediately after `riverType[pos] = buffers.typeMask[pos];`:

```java
            riverDist[pos] = buffers.dist[pos];
```

Update `fineGrainedPrimitivePass`'s javadoc to name the channel it fills, staying inside the three-line
method budget:

```java
    /** Cuts the river bed into the tile-carved shell, the last elevation pass before blocks are
     *  placed. The shell-to-bed delta lands in {@link Types#RIVER_DIFFERENCE}, which the water fill
     *  reads; {@link Types#RIVER_TYPE} and {@link Types#RIVER_DIST} are what the surface painter reads. */
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle test --tests "me.batata_1.fractal_terrain.storage.RiverDistChannelTest"`

Expected: PASS, 2 tests.

- [ ] **Step 5: Run the full suite**

Run: `gradle test`

Expected: **111 tests, 9 failed, 1 skipped** — the same nine failures with the same messages.

- [ ] **Step 6: Update the two indexes**

In `src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md`, replace the
`FractalTerrainHeightmap.java` row's What cell with:

> Record of per-chunk heightmap layers (`Types`); `Types.RIVER_TYPE` is a `long[]` packed by
> `HydrologicalFeature.pack` (family in the high word, sub-type in the low), not a
> `HydrologicalFeature[]`, and `Types.RIVER_DIST` is the parallel `float[]` banded footprint
> coordinate, valid only where `RIVER_TYPE` is not `NONE`

and its When cell with:

> Reading heightmap channels (elevation/grad/params); reading/unpacking `RIVER_TYPE`; reading
> `RIVER_DIST` for surface painting

In `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md`, extend the
`PopulateNoiseStep.java` row's What cell so the written-channel list reads ``Types.ELEVATION`,
`Types.RIVER_DIFFERENCE`, `Types.WATER_HEIGHT`, `Types.RIVER_TYPE` and `Types.RIVER_DIST``.

- [ ] **Step 7: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/storage/FractalTerrainHeightmap.java \
        src/main/java/me/batata_1/fractal_terrain/storage/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/PopulateNoiseStep.java \
        src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md \
        src/test/java/me/batata_1/fractal_terrain/storage/RiverDistChannelTest.java
git commit -F - <<'MSG'
feat(storage): publish the banded carve distance as Types.RIVER_DIST

fineGrainedPrimitivePass already writes the winning primitive's packed type per
column and throws away where in that primitive's cross-section the column sits.
RIVER_DIST carries it, gated on RIVER_TYPE: an unclaimed cell still holds the
carve's unset seed and must not be read.

Costs one float[256] per cached chunk heightmap, against the long[256]
RIVER_TYPE already costs.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01P3CeXwCzWLPfDJMrHdbvZC
MSG
```

---

## Task 3: The hydrology-side paint contract

A profile answers what its carve exposes, in a Minecraft-free vocabulary, by tabulating a column of
materials into a caller-owned scratch array. One virtual call per claimed column, not one per block.

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/SurfaceMaterial.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfile.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RiverPaintDepthTest.java` (create)

**Interfaces:**
- Consumes: `RiverInfluenceCarve.BED_EDGE`, `RiverInfluenceCarve.FLOODPLAIN_EDGE` (Task 1).
- Produces:
  - `public enum SurfaceMaterial { DEFER, GRAVEL, COBBLE, SAND, SILT, CLAY, MUD }` in
    `me.batata_1.fractal_terrain.hydrology.profile`
  - `default int HydrologyProfile.riverPaintDepth(int subType, float dist, SurfaceMaterial[] out)`,
    returning `0`
  - `public static final int HydrologyTuning.MAX_RIVER_PAINT_DEPTH = 8`
  - `public static RosgenType RiverPrimitive.RosgenType.byOrdinal(int ordinal)`
  - `public HydrologyProfile HydrologicalPrimitive.HydrologicalFeature.profileFor(int sub)`
  - `protected SurfaceMaterial[] RosgenProfile.bedColumn()` and
    `protected SurfaceMaterial[] RosgenProfile.floodPlainColumn()`

Task 4 calls `HydrologicalFeature.unpack(tag).profileFor(sub).riverPaintDepth(sub, dist, paint)` and
maps each returned token through `HydrologySurfacePalette.of`.

### Shape of the RosgenProfile override

`RosgenProfile` already splits a shared law (`delta`) from per-constant hooks (`bedDelta`,
`floodPlainDelta`). `riverPaintDepth` follows the same split: one shared body on the enum mapping the
banded coordinate to a band, and two hooks returning that band's material column. Each column is a
`static final` array, so a constant's override allocates nothing and the shared body copies into the
caller's buffer.

`DA` deliberately overrides neither hook, keeping the "`DA` overrides nothing" property the profile
README records; the enum-level defaults are its column.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RiverPaintDepthTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The paint contract every profile answers: a bounded material column, keyed by the banded coordinate. */
class RiverPaintDepthTest {

    /** The bands a profile can be asked about, plus both boundaries and a point past the rim. */
    private static final float[] SWEPT_BANDS = {
        0.0f,
        (float) RiverInfluenceCarve.BED_EDGE * 0.5f,
        (float) RiverInfluenceCarve.BED_EDGE,
        (float) RiverInfluenceCarve.BED_EDGE + 1e-4f,
        (float) RiverInfluenceCarve.FLOODPLAIN_EDGE,
        (float) RiverInfluenceCarve.FLOODPLAIN_EDGE + 1e-4f,
        1.0f
    };

    private static SurfaceMaterial[] scratch() {
        return new SurfaceMaterial[HydrologyTuning.MAX_RIVER_PAINT_DEPTH];
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void neverPaintsDeeperThanTheScratchBufferHolds(RosgenProfile profile) {
        // MAX_RIVER_PAINT_DEPTH is the contract bound the surface builder sizes its buffer to; a profile
        // returning more would overrun it, which is a programming error rather than a runtime condition.
        final SurfaceMaterial[] out = scratch();
        for (float dist : SWEPT_BANDS) {
            final int depth = profile.riverPaintDepth(0, dist, out);
            assertTrue(depth >= 0, profile + " returned a negative depth at " + dist);
            assertTrue(depth <= HydrologyTuning.MAX_RIVER_PAINT_DEPTH, profile + " returned " + depth);
        }
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void fillsEveryEntryItClaims(RosgenProfile profile) {
        final SurfaceMaterial[] out = scratch();
        for (float dist : SWEPT_BANDS) {
            Arrays.fill(out, null);
            final int depth = profile.riverPaintDepth(0, dist, out);
            for (int d = 0; d < depth; d++) {
                assertNotNull(out[d], profile + " left entry " + d + " unwritten at " + dist);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void writesNothingPastTheDepthItReturns(RosgenProfile profile) {
        // The buffer is reused across all 256 columns of a chunk, so a profile must not leave a previous
        // column's material where the next column's loop can read it.
        final SurfaceMaterial[] out = scratch();
        Arrays.fill(out, SurfaceMaterial.MUD);
        final int depth = profile.riverPaintDepth(0, 0.0f, out);
        for (int d = depth; d < out.length; d++) {
            assertSame(SurfaceMaterial.MUD, out[d], profile + " overwrote entry " + d);
        }
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void paintsNothingOutsideTheFloodPlain(RosgenProfile profile) {
        // The influence band is the outer blend; leaving it to the vanilla rules is what keeps a river's
        // valley looking like the biome it runs through.
        assertEquals(
                0,
                profile.riverPaintDepth(0, (float) RiverInfluenceCarve.FLOODPLAIN_EDGE + 1e-4f, scratch()),
                profile + " painted past the floodplain");
    }

    @ParameterizedTest
    @EnumSource(RosgenProfile.class)
    void paintsSomethingInTheBed(RosgenProfile profile) {
        // Without this every profile could satisfy the bounds above by painting nothing at all.
        assertTrue(profile.riverPaintDepth(0, 0.0f, scratch()) > 0, profile + " left its own bed unpainted");
    }

    @Test
    void anUnprofiledFeatureTypePaintsNothing() {
        assertEquals(0, DefaultProfile.INSTANCE.riverPaintDepth(0, 0.0f, scratch()));
    }

    @Test
    void aRiverTagResolvesToItsRosgenProfile() {
        // The surface path holds a packed tag and never a primitive instance, so tag-to-profile
        // resolution has to live on the family enum.
        for (RosgenType type : RosgenType.values()) {
            assertSame(
                    RosgenProfile.of(type),
                    HydrologicalFeature.RIVER.profileFor(type.ordinal()),
                    "RIVER sub-type " + type);
        }
    }

    @Test
    void aFeatureTypeWithNoProfileFallsBackToTheDefault() {
        assertSame(DefaultProfile.INSTANCE, HydrologicalFeature.SOURCE.profileFor(0));
        assertSame(DefaultProfile.INSTANCE, HydrologicalFeature.WATERFALL.profileFor(0));
    }

    @Test
    void rosgenTypesResolveByOrdinalWithoutCopyingTheEnum() {
        for (RosgenType type : RosgenType.values()) {
            assertSame(type, RosgenType.byOrdinal(type.ordinal()));
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "me.batata_1.fractal_terrain.hydrology.profile.RiverPaintDepthTest"`

Expected: compilation of the test source set FAILS with `cannot find symbol: class SurfaceMaterial`,
`cannot find symbol: variable MAX_RIVER_PAINT_DEPTH`, `cannot find symbol: method riverPaintDepth`,
`cannot find symbol: method profileFor`, `cannot find symbol: method byOrdinal`.

- [ ] **Step 3: Add the material vocabulary and the tuning bound**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/SurfaceMaterial.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * What a hydrology profile can ask to see at the surface of its own carve, as a token rather than a
 * block.
 *
 * <p>Exists so {@code hydrology/} stays free of {@code net.minecraft}: the profile names a material and
 * {@code world/gen/surfacebuilder/HydrologySurfacePalette} owns the mapping to a block state, including
 * any substitution a material with no vanilla counterpart needs.
 */
public enum SurfaceMaterial {

    /** Leave this depth to the vanilla surface rules — a claimed column need not paint every layer. */
    DEFER,
    GRAVEL,
    COBBLE,
    SAND,
    SILT,
    CLAY,
    MUD
}
```

In `HydrologyTuning.java`, add to the river width and carve-profile section, immediately after
`MARGIN_INFLUENCE_FACTOR`:

```java
    /** Contract bound on {@code HydrologyProfile.riverPaintDepth}: the length of the scratch column the
     *  surface builder hands it. A profile returning more is a programming error, not a runtime case. */
    public static final int MAX_RIVER_PAINT_DEPTH = 8;
```

- [ ] **Step 4: Add the profile contract and its Rosgen implementation**

In `HydrologyProfile.java`, add after `shellElevation`:

```java
    /**
     * The materials this profile paints down a claimed column, tabulated into {@code out}, surface
     * first. Tabulated rather than answered per block because the caller's 16x16 column loop sits below
     * the hot/cold line and cannot afford a virtual call per block.
     *
     * @param subType the family-specific classification from {@code HydrologicalFeature.unpackSub}
     * @param dist the banded footprint coordinate from {@code RiverInfluenceCarve.band}
     * @param out caller-owned scratch, at least {@code HydrologyTuning.MAX_RIVER_PAINT_DEPTH} long;
     *     implementations must not retain it
     * @return how many entries were filled; zero leaves the column to the vanilla surface rules
     */
    default int riverPaintDepth(int subType, float dist, SurfaceMaterial[] out) {
        return 0;
    }
```

In `RosgenProfile.java`, add the per-constant hook overrides. Each goes inside its constant's existing
body, alongside that constant's `bedDelta` / `floodPlainLength` overrides:

```java
    A {
        // ... existing floodPlainLength and bedDelta overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.COBBLE, SurfaceMaterial.COBBLE, SurfaceMaterial.GRAVEL
        };

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }
    },
    Aa {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.COBBLE, SurfaceMaterial.COBBLE, SurfaceMaterial.COBBLE
        };
        private static final SurfaceMaterial[] FLOOD_PLAIN = {SurfaceMaterial.COBBLE, SurfaceMaterial.GRAVEL};

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }

        @Override
        protected SurfaceMaterial[] floodPlainColumn() {
            return FLOOD_PLAIN;
        }
    },
    B {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL, SurfaceMaterial.COBBLE
        };

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }
    },
    C {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.SAND, SurfaceMaterial.SAND, SurfaceMaterial.GRAVEL
        };
        // DEFER at the surface keeps the biome's own top block on a lowland floodplain; the silt below
        // is what a meander actually leaves behind.
        private static final SurfaceMaterial[] FLOOD_PLAIN = {
            SurfaceMaterial.DEFER, SurfaceMaterial.SILT, SurfaceMaterial.SILT
        };

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }

        @Override
        protected SurfaceMaterial[] floodPlainColumn() {
            return FLOOD_PLAIN;
        }
    },
    D {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.GRAVEL, SurfaceMaterial.SAND, SurfaceMaterial.GRAVEL
        };
        private static final SurfaceMaterial[] FLOOD_PLAIN = {SurfaceMaterial.SAND, SurfaceMaterial.SAND};

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }

        @Override
        protected SurfaceMaterial[] floodPlainColumn() {
            return FLOOD_PLAIN;
        }
    },
    DA,
    E {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.SILT, SurfaceMaterial.CLAY, SurfaceMaterial.CLAY
        };
        private static final SurfaceMaterial[] FLOOD_PLAIN = {SurfaceMaterial.DEFER, SurfaceMaterial.SILT};

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }

        @Override
        protected SurfaceMaterial[] floodPlainColumn() {
            return FLOOD_PLAIN;
        }
    },
    F {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.SAND, SurfaceMaterial.SILT, SurfaceMaterial.SILT
        };
        private static final SurfaceMaterial[] FLOOD_PLAIN = {SurfaceMaterial.DEFER, SurfaceMaterial.SILT};

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }

        @Override
        protected SurfaceMaterial[] floodPlainColumn() {
            return FLOOD_PLAIN;
        }
    },
    G {
        // ... existing overrides ...

        private static final SurfaceMaterial[] BED = {
            SurfaceMaterial.COBBLE, SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL
        };

        @Override
        protected SurfaceMaterial[] bedColumn() {
            return BED;
        }
    };
```

`DA` keeps its bare `DA,` form and inherits the enum-level defaults.

Then add the shared body and the two hooks to the enum itself. Put `riverPaintDepth` with the other
public methods (after `sampleCrossSection`), and the hooks with the other `protected` hooks (after
`bedDelta`):

```java
    @Override
    public int riverPaintDepth(int subType, float dist, SurfaceMaterial[] out) {
        final SurfaceMaterial[] column;
        if (dist <= RiverInfluenceCarve.BED_EDGE) {
            column = bedColumn();
        } else if (dist <= RiverInfluenceCarve.FLOODPLAIN_EDGE) {
            column = floodPlainColumn();
        } else {
            return 0;
        }
        // :PERF: arraycopy out of a shared static column; this runs once per claimed column of every
        // chunk, and building the column per call would allocate on the surface path.
        System.arraycopy(column, 0, out, 0, column.length);
        return column.length;
    }
```

```java
    /** What this type's wetted bed exposes, surface first. Placeholder gravel shared by all types;
     *  override per constant. */
    protected SurfaceMaterial[] bedColumn() {
        return DEFAULT_BED;
    }

    /** What this type's floodplain exposes, surface first. Empty by default, leaving the valley floor
     *  to whatever biome it runs through; override per constant. */
    protected SurfaceMaterial[] floodPlainColumn() {
        return DEFAULT_FLOOD_PLAIN;
    }
```

and the two default columns beside `LOG`:

```java
    private static final SurfaceMaterial[] DEFAULT_BED = {
        SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL, SurfaceMaterial.GRAVEL
    };

    private static final SurfaceMaterial[] DEFAULT_FLOOD_PLAIN = {};
```

- [ ] **Step 5: Resolve a packed tag to a profile**

In `RiverPrimitive.java`, add to the `RosgenType` enum, after `orDefault`:

```java
        /** {@code values()} without the defensive array copy; indexed by ordinal, as the packed
         *  {@code RIVER_TYPE} sub-word stores it. */
        private static final RosgenType[] VALUES = values();

        /** The type a packed sub-word names. Read on the surface path, which holds an ordinal and never
         *  a primitive. */
        public static RosgenType byOrdinal(int ordinal) {
            return VALUES[ordinal];
        }
```

In `HydrologicalPrimitive.java`, add the shared default to `HydrologicalFeature`, next to
`prototype()`:

```java
        /** The profile a packed cell's sub-classification carves and paints with. Exists because the
         *  surface path holds a packed tag and never a primitive instance. */
        public HydrologyProfile profileFor(int sub) {
            return DefaultProfile.INSTANCE;
        }
```

and the override inside the `RIVER` constant's body, alongside its `addPrimitives`:

```java
            @Override
            public HydrologyProfile profileFor(int sub) {
                return RosgenProfile.of(RiverPrimitive.RosgenType.byOrdinal(sub));
            }
```

Add the imports `me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile` and
`me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile` to `HydrologicalPrimitive.java`;
`HydrologyProfile` is already imported. The `features` -> `profile` direction is the one
`RiverPrimitive.getProfile()` already takes, so this adds no new package edge.

- [ ] **Step 6: Run the test to verify it passes**

Run: `gradle test --tests "me.batata_1.fractal_terrain.hydrology.profile.RiverPaintDepthTest"`

Expected: PASS, 49 tests — 5 parameterized methods over 9 `RosgenProfile` constants, plus 4 plain tests.

- [ ] **Step 7: Run the full suite**

Run: `gradle test`

Expected: **160 tests, 9 failed, 1 skipped** — the same nine failures with the same messages.

- [ ] **Step 8: Update the profile index and README**

In `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md`, add a row after
`HydrologyProfile.java`:

| File | What | When to read |
| ---- | ---- | ------------ |
| `SurfaceMaterial.java` | The Minecraft-free material tokens a profile paints in, plus `DEFER` for a depth left to the vanilla rules | Adding a material a profile can ask for, tracing where a river's blocks are decided |

and amend two existing rows' What cells:

- `HydrologyProfile.java`: `The extension point: shellElevation is the carve half of its contract,
  riverPaintDepth the paint half — no zone radius, selection or weight member`
- `RosgenProfile.java`: append `, plus the per-type bed and floodplain material columns riverPaintDepth
  tabulates`

In `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md`, add after the existing
**Painter** paragraph:

```markdown
**Paint contract.** `HydrologyProfile.riverPaintDepth` is the paint half of the profile's contract, the
twin of `shellElevation`. It tabulates a column of `SurfaceMaterial` tokens into a caller-owned scratch
array and returns how many entries it filled, rather than answering per block: the caller's 16x16
surface loop sits below the hot/cold line and cannot afford a virtual call per block. This mirrors
`RosgenProfile.sampleCrossSection`, which tabulates a cross-section LUT once per primitive for the same
reason.

Tokens, not blocks, because nothing under `hydrology/` may import `net.minecraft` — that is what lets
the golden suite run as plain JUnit. `world/gen/surfacebuilder/HydrologySurfacePalette` owns the
mapping, including the substitution a token with no vanilla counterpart needs.

`RosgenProfile` splits the law the way `delta` is split: one shared body maps the banded coordinate to a
band, and per-constant `bedColumn`/`floodPlainColumn` hooks name that band's materials. The columns are
`static final` arrays so a constant's override allocates nothing. `DA` overrides neither and takes the
enum-level defaults, the same "overrides nothing" position it holds for the elevation laws.
`DEFAULT_FLOOD_PLAIN` is empty, so a type that has not been given a floodplain material leaves the
valley floor to whatever biome it runs through rather than guessing.

`DEFER` exists so a claimed column need not paint every layer: `C`, `E` and `F` defer their floodplain's
top block, keeping the biome's own grass while replacing the material underneath it.

`HydrologicalFeature.profileFor` resolves a packed `RIVER_TYPE` tag to a profile. It lives on the family
enum rather than on the primitive because the surface path holds a packed tag and never a primitive
instance. Every family but `RIVER` answers `DefaultProfile.INSTANCE`, whose `riverPaintDepth` returns
zero — so a newly added feature type stays as invisible to the surface as it already is to the carve.
```

- [ ] **Step 9: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/SurfaceMaterial.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfile.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RiverPaintDepthTest.java
git commit -F - <<'MSG'
feat(hydrology): give a profile the paint half of its contract

riverPaintDepth is the twin of shellElevation: it tabulates a column of
SurfaceMaterial tokens into caller-owned scratch and returns how many it
filled. Tabulated rather than answered per block because the surface loop that
reads it is below the hot/cold line; tokens rather than block states because
hydrology/ must stay free of net.minecraft.

RosgenProfile splits it the way delta is split -- one shared body over the
banded coordinate, per-constant bedColumn/floodPlainColumn hooks returning
static columns. DA inherits both, as it does every elevation law.

HydrologicalFeature.profileFor resolves a packed RIVER_TYPE tag, since the
surface path holds a tag and never a primitive.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01P3CeXwCzWLPfDJMrHdbvZC
MSG
```

---

## Task 4: The palette and the surface loop

The world side: one table mapping a token to a block state, and the branch in `buildSurface` that
consults it before falling through to the vanilla rule chain.

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/HydrologySurfacePalette.java`
- Create: `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/FractalTerrainSurfaceSystem.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/CLAUDE.md`
- Modify: `ARCHITECTURE.md`

**Interfaces:**
- Consumes: `SurfaceMaterial`, `HydrologyProfile.riverPaintDepth`, `HydrologicalFeature.profileFor`,
  `HydrologyTuning.MAX_RIVER_PAINT_DEPTH` (Task 3); `Types.RIVER_DIST` (Task 2).
- Produces: nothing further tasks depend on.

**No JUnit test.** Both files touch `net.minecraft.world.level.block.Blocks`, whose static initialiser
needs a bootstrapped registry (`Bootstrap.bootStrap()`), and `buildSurface` needs a live
`SurfaceRules.Context`, a `ChunkAccess` and a `NoiseChunk`. Nothing in `src/test/` bootstraps Minecraft
today, and standing that up is a larger change than this task. Verification is `gradle build` plus the
manual harnesses. This is the one place in the plan where the deliverable is not test-gated; the
material selection it wires up is already gated by `RiverPaintDepthTest`, and the palette is a total
`switch` over an enum, whose exhaustiveness the compiler checks.

- [ ] **Step 1: Write the palette**

Create `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/HydrologySurfacePalette.java`:

```java
package me.batata_1.fractal_terrain.world.gen.surfacebuilder;

import me.batata_1.fractal_terrain.hydrology.profile.SurfaceMaterial;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The one place a hydrology {@link SurfaceMaterial} meets a block state.
 *
 * <p>Exists so {@code hydrology/} can name what a river exposes without importing
 * {@code net.minecraft} — the profile picks a material, this table picks the block. A material with no
 * 1.20.1 counterpart is substituted here, where the substitution is visible, rather than by dropping the
 * token and losing the profile's ability to express the distinction.
 */
public final class HydrologySurfacePalette {

    private HydrologySurfacePalette() {}

    /**
     * The block a material places, or {@code null} where the profile defers to the vanilla surface
     * rules. {@code underwater} is measured against the river's own water surface, not sea level, so a
     * bank material can differ across the water line without the profile needing a Y coordinate.
     */
    static @Nullable BlockState of(SurfaceMaterial material, boolean underwater) {
        return switch (material) {
            case DEFER -> null;
            case GRAVEL -> GRAVEL;
            case COBBLE -> COBBLE;
            case SAND -> SAND;
            // 1.20.1 has no silt. Dry silt reads as dirt, submerged silt as clay: the two vanilla blocks
            // a fine cohesive sediment looks like on either side of a waterline.
            case SILT -> underwater ? CLAY : DIRT;
            case CLAY -> CLAY;
            case MUD -> MUD;
        };
    }

    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    private static final BlockState MUD = Blocks.MUD.defaultBlockState();
}
```

- [ ] **Step 2: Wire the paint branch into `buildSurface`**

In `FractalTerrainSurfaceSystem.java`, add the imports:

```java
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.profile.SurfaceMaterial;
```

Add the two channel handles and the scratch buffer after `float[] water_heightmap = ...`:

```java
        final long[] river_type = (long[]) heightmaps.get(Types.RIVER_TYPE);
        final float[] river_dist = (float[]) heightmaps.get(Types.RIVER_DIST);
        // :PERF: one scratch column per chunk, reused across all 256 of its columns; a per-column array
        // would allocate on the surface path.
        final SurfaceMaterial[] paint = new SurfaceMaterial[HydrologyTuning.MAX_RIVER_PAINT_DEPTH];
```

Replace the body of the `dz` loop, from `int relief_height = ...` through the closing brace of the depth
loop, with:

```java
                final int pos = 16 * dx + dz;
                int relief_height = (int) relief_heightmap[pos];
                int stoneDepthAbove;
                int stoneDepthBellow;
                int fluid_height;
                final int sedimentLayerDepth = sedimentDepth(dx, dz, heightmaps);

                final long tag = river_type[pos];
                final HydrologicalFeature family =
                        tag == HydrologicalFeature.NONE ? null : HydrologicalFeature.unpack(tag);
                int riverPaintDepth = 0;
                if (family != null) {
                    final int sub = HydrologicalFeature.unpackSub(tag);
                    riverPaintDepth = family.profileFor(sub).riverPaintDepth(sub, river_dist[pos], paint);
                }
                // A steep headwater -- the case that most wants cobble -- gets sedimentDepth -1 and no
                // loop iterations at all, so a claim has to be able to extend the column, not just
                // recolour it.
                final float waterY = water_heightmap[pos];
                final int lastDepth = Math.max(sedimentLayerDepth, riverPaintDepth - 1);

                for (int d = 0; d <= lastDepth; d++) {
                    final int y = relief_height - d;
                    stoneDepthAbove = d + 1;
                    stoneDepthBellow = relief_height + 128 - d;
                    // 64
                    final int fh = (int) (water_heightmap[pos] + seaLevel - relief_height);
                    fluid_height = fh;
                    // updateY runs on painted blocks too: SurfaceRules.Context caches per-column state
                    // lazily and its tolerance for gaps in y is unverified.
                    materialRuleContext.updateY(stoneDepthAbove, stoneDepthBellow, fluid_height, x, y, z);
                    BlockState newBlockState = null;
                    if (d < riverPaintDepth) {
                        newBlockState = HydrologySurfacePalette.of(paint[d], y < waterY);
                    }
                    if (newBlockState == null) {
                        newBlockState = blockStateRule.tryApply(x, y, z);
                    }
                    if (newBlockState != null) {
                        blockColumn.setBlock(y, newBlockState);
                    }
                }
```

Leave the `fh` expression exactly as it is. It appears to add `seaLevel` a second time, since
`PopulateNoiseStep` already folds `seaLevel` into both `relief_height` and `waterElev` — that is
recorded in the new README below and is out of scope here.

- [ ] **Step 3: Build**

Run: `gradle spotlessApply && gradle build`

Expected: BUILD SUCCESSFUL. `switch` exhaustiveness over `SurfaceMaterial` is compiler-checked, so a
token added later without a palette entry fails the build rather than silently falling through to grass.

- [ ] **Step 4: Run the full suite**

Run: `gradle test`

Expected: **160 tests, 9 failed, 1 skipped** — unchanged from Task 3, since nothing here is reachable
from JUnit.

- [ ] **Step 5: Document the directory**

Create `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/README.md`:

```markdown
# surfacebuilder/

## Overview

Replaces vanilla's `SurfaceSystem` so the block capping each column comes from this mod's own heightmaps
rather than from noise-derived density. Two things decide a column: the vanilla surface-rule chain from
`NoiseGeneratorSettings.surfaceRule()`, evaluated through a `SurfaceRules.Context` this class drives
itself, and — where a river claimed the column — the hydrology profile that carved it.

## Architecture

`buildSurface` walks the chunk's 256 columns. For each it computes a sediment depth from the refined
gradient, humidity, erosion and peaks/valleys channels, then walks down from the relief height placing
whatever the rule chain returns.

A river claim adds a second source. `PopulateNoiseStep.fineGrainedPrimitivePass` leaves two parallel
channels behind: `Types.RIVER_TYPE`, the winning primitive's packed family and sub-classification, and
`Types.RIVER_DIST`, that primitive's banded footprint coordinate at this column. Where `RIVER_TYPE` is
not `NONE`, `HydrologicalFeature.profileFor` resolves the tag to a `HydrologyProfile` and
`riverPaintDepth` tabulates a column of `SurfaceMaterial` tokens into a scratch array;
`HydrologySurfacePalette` turns each token into a block state.

Flow: heightmap channels -> profile -> `SurfaceMaterial[]` -> palette -> `BlockColumn.setBlock`, with
the vanilla rule chain underneath every depth the profile did not claim.

## Design decisions

**The profile tabulates a column; it is not asked per block.** The 16x16 column loop is below the
hot/cold line (`ARCHITECTURE.md`), and one virtual call per block would be 256 times the dispatch. One
call per claimed column fills a `SurfaceMaterial[]` allocated once per `buildSurface` and reused across
all 256 columns.

**A claim may extend the column, and defers by material.** `riverPaintDepth` overrides `sedimentDepth`
upward, and any depth whose token is `DEFER` falls through to the vanilla rule. Without the first half a
steep type-A headwater — the case that most wants cobble — gets `sedimentDepth == -1` and zero loop
iterations, so the claim would never be placed. Without the second, a profile could only replace a whole
column, never its lower layers.

**`updateY` still runs on painted blocks.** Skipping it where the material is not `DEFER` would be a
measurable win, but `SurfaceRules.Context` caches per-column state lazily and its tolerance for gaps in
`y` is unverified. Confirm that before optimising it.

**Materials, not blocks, cross the boundary.** Nothing under `hydrology/` imports `net.minecraft`, which
is what lets the golden suite run as plain JUnit. `HydrologySurfacePalette` is the single translation
point, and also where a material with no 1.20.1 counterpart is substituted — silt becomes dirt above the
water line and clay below it. Substituting here rather than dropping the token keeps the profile able to
express a distinction the block list cannot.

## Known limitations

**The fluid height passed to the rule context looks like a double `seaLevel` add.** `buildSurface`
computes `fh = water_heightmap[pos] + seaLevel - relief_height`, but `PopulateNoiseStep` already folds
`seaLevel` into both `Types.WATER_HEIGHT` and `Types.ELEVATION` before this reads them. Undiagnosed;
noticed while tracing the underwater test for the palette. Anything reading `fluid_height` out of the
rule context inherits it.

**Underwater is tested against the river's water surface, not sea level.** `Types.WATER_HEIGHT` holds
zero for any column no river claimed, so the test is only meaningful inside a claim — which is the only
place it is asked.
```

- [ ] **Step 6: Update the directory index**

In `src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/CLAUDE.md`, replace the Files
table with:

```markdown
## Files

| File                               | What                                                                                                                | When to read                                                             |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `README.md`                        | Why the paint column is tabulated once per column, why a claim may extend it, the silt substitution, the `fh` oddity | Changing surface painting, adding a material, diagnosing a fluid-height bug |
| `FractalTerrainSurfaceSystem.java` | `SurfaceSystem` override applying surface rules from heightmaps + biome params, plus the river paint branch reading `Types.RIVER_TYPE` / `Types.RIVER_DIST`. Below the hot/cold line — no heap allocation in the per-column loop | Surface block selection, material rules, allocation-cost review of the column loop |
| `HydrologySurfacePalette.java`     | The `SurfaceMaterial`-to-`BlockState` table; the only place a hydrology material meets Minecraft                     | Changing which block a river material places, adding a material          |
```

- [ ] **Step 7: Name the column loop as a hot site**

In `ARCHITECTURE.md`, under **Hot sites in this repo**, add after the `storage/ChunkChannelFill.java`
bullet:

```markdown
- `world/gen/surfacebuilder/FractalTerrainSurfaceSystem.java` `buildSurface`'s 16x16 column loop and the
  depth loop inside it — runs for every chunk generated. The river paint column is tabulated once per
  claimed column into a `SurfaceMaterial[]` allocated once per chunk, rather than the profile being
  asked per block; `HydrologySurfacePalette`'s block states are `static final`.
```

- [ ] **Step 8: Format and commit**

```bash
gradle spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/world/gen/surfacebuilder/ ARCHITECTURE.md
git commit -F - <<'MSG'
feat(surface): paint the blocks a river's own carve exposes

buildSurface consults the claiming primitive's profile before the vanilla rule
chain. RIVER_TYPE names the profile, RIVER_DIST says where in its cross-section
the column sits, and the profile tabulates a column of SurfaceMaterial tokens
that HydrologySurfacePalette turns into block states.

A claim may extend the column past sedimentDepth -- a steep type-A headwater
gets -1 and would otherwise never be painted at all -- and defers per depth, so
a lowland floodplain keeps the biome's grass over the profile's silt.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01P3CeXwCzWLPfDJMrHdbvZC
MSG
```

---

## Final verification

- [ ] `gradle spotlessApply && gradle build` — BUILD SUCCESSFUL.
- [ ] `gradle test` — 160 tests, 9 failed, 1 skipped. Diff the failure *messages* in
      `build/test-results/test/*.xml` against
      `.superpowers/conventions-alignment/post-migration-failures.txt`; they must be identical. A tenth
      failure, or a different message on one of the nine, is a defect in this change.
- [ ] `gradle riverTest` and `gradle meandersTest` — the manual harnesses still run and their PNG dumps
      under `run/debug` still show coherent channels. These exercise the tile shell carve, which this
      change does not touch, so they are a control: if they move, something reached
      `carvePrimitiveInfluence` that should not have.
- [ ] `gradle runClient`, generate a world, stand in a river. Confirm the bed material matches the
      reach's Rosgen type and that the floodplain of a `C` reach keeps its grass over changed subsoil.
      This is the only check that exercises Task 4 at all.
- [ ] Re-read each convention named in Global Constraints against the final diff and name any knowingly
      deviated from, with the reason.

## Out of scope

Carried from the spec, unchanged:

- **Water placement.** `HydrologyProfilePainter.riverWaterTop` stays uncalled; water continues to come
  from `fluid_height` in the rule context.
- **Biome parameters and the vegetation PDF.**
- **Unifying `carvePrimitiveInfluence` onto the three-piece band.** A separate change, so shell and bed
  terrain do not move together.
- **Skipping `updateY` for painted blocks.** Gated on confirming `SurfaceRules.Context` tolerates gaps
  in `y`.
- **`ZoneCategory`.** The banded coordinate makes the zones comparable numerically, so nothing here
  needs the enum. It stays reserved.
- **The `fh` double-`seaLevel` expression.** Recorded in the new surfacebuilder README; not diagnosed
  and not touched.

## Interaction with the radial-primitive spec

`docs/superpowers/specs/2026-09-02-radial-primitive-carve-design.md` proposes a second
`computeRiverGrid` pass and carries decisions about the carve merge arithmetic. Both specs change
`RiverInfluenceCarve`'s merge inputs. Whichever lands second must re-read the other's decisions before
touching the recurrence — in particular, a radial primitive has no `marginLen`/`floodPlainLen` and so
needs its own answer for what the banded coordinate means, or the paint side's two comparisons stop
being width-independent across families.
