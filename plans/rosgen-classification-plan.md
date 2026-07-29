# Rosgen Level-I Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Assign a Rosgen Level-I stream type (`Aa+ A B C D DA E F G`) to every `HydrologicalUnit` that represents a *river reach*, measured from the raw decoded elevation field, so `RosgenProfile` can drive per-type carve geometry. Units that are not reaches — the `SOURCE` and `DRAIN` points terminating each channel, and removed features (oxbow lakes, abandoned rivers) — are stamped with their real `HydrologicalFeature` kind and a `null` type, because Rosgen classifies reaches and there is nothing to measure a spring or a river mouth over.

**Architecture:** Classification is a *diagnosis* step that runs inside `Meanders.collectUnits`, which is the only object holding both the river graph and the raster it needs. It measures two genuine observables of the terrain — along-channel bed slope and entrenchment ratio — plus channel width, then runs an ordered decision key to pick a type. Everything else Rosgen names (W/D, sinuosity, floodplain extent, bed form) is *prescribed* by `RosgenProfile` from that type, never measured. No state is cached on `Channel`; each unit carries its own type so the carve can later blend between adjacent types.

**Tech Stack:** Java 21, Fabric/Loom, JUnit 5 (`useJUnitPlatform()`), palantirJavaFormat via Spotless.

## Global Constraints

- **Gradle:** no wrapper is checked in and the `gradle` on PATH (8.14) is too old. Invoke via
  `C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat`
  from the repo root.
- **Run `gradle spotlessApply` before every commit.** The build enforces `spotlessCheck`. Spotless is repo-wide — check `git diff` for incidental reformats outside your change scope.
- **The JUnit suite is already red on `feature/hydrology`: 20 tests, 8 failing, 1 skipped.** Baseline failures are 4 × `LocalRiverGoldenTest` (`ArrayIndexOutOfBoundsException: Index 262144 out of bounds for length 262144`), 1 × `GlobalRiverGoldenTest`, 1 × `SpatialIndexCorrectnessGoldenTest`, 2 × `MeandersGoldenTest`. **Do not treat these as your breakage.** After each task, the failure count must not increase.
- **`HydrologicalUnit` serialises `rosgenType.ordinal()` and reads it back by index.** The enum order `A, Aa, B, C, D, DA, E, F, G` is frozen. Appending is safe; reordering breaks every persisted tile. This plan adds no enum constants.
- **Do not modify `ChannelGeometry.depthForWidth`.** It is floored at `1.0` for every representable width (widths clamp to `[0.2, 16]`; the expression only exceeds 1 above 18.8 px) and it feeds `Meanders.java:144` (`alpha = 2 * FRICTION / ch.depth()`). Changing it perturbs meander geometry and would move the already-failing `MeandersGoldenTest`.
- **Coordinate frame:** classification runs in the *network* frame — padded, `gridSize = PADDED = 514`, row-major `x * gridSize + z`, identical to `Meanders.gradX`/`gradZ`. `collectUnits`'s `offsetX`/`offsetZ` are subtracted inside `addFeatureUnits`, *after* the type is decided. Never apply the offset before classifying.
- **All new tuning constants get a javadoc line stating they are first-cut and untuned**, matching the existing `LOCAL_ATTACH_RADIUS` / `MAX_ECCENTRICITY` pattern in `HydrologyTuning`.
- **Comments use the timeless present** (`.claude/conventions/temporal.md`): describe what the code does, not what it used to do or when it changed.

## Why this shape (decisions that are easy to get wrong)

1. **Classification must happen inside `collectUnits`, before any carve.** The Rosgen type feeds `RosgenProfile.riverInfluence` → `HydrologicalUnit.getRadius()` → the R-tree that `carveRiverShells` builds. Units must already be typed when the carve consumes them. `collectUnits` is the only point where that holds for all three call sites.

2. **The classifier reads a *raw* elevation snapshot, never `carvedElevation`.** `HydrologyProfileCarver.carveRiverShells` writes into the buffer it reads and compounds across calls. `LocalRiverProvider.buildTile` calls `collectUnits` three times: at `:217` the buffer is still raw, but at `:249` and `:252` it has been carved twice. Classifying against the carved buffer measures `FLOODPLAIN_BASE` and `FLOODPLAIN_WIDTH_FACTOR` — the carve's own tuning constants — instead of the terrain. `GlobalNetworkBuilder` already clones `base[2]`/`base[3]` at `:202-203`, before the first `assign` and first carve, so the same function is the right home for the snapshot — but the clone goes *above* the empty-network early return at `:194-198`, not beside the gradient clones, because that branch constructs its own `Meanders` and returns before `:202` ever runs. Task 7 Step 3 has the exact placement.

3. **W/D is derived from width, not from flow accumulation.** `FLOW_PER_CELL_GLOBAL = 2.0` and `FLOW_PER_CELL_LOCAL = 0.001` differ by 2000×, and after the collision pass you cannot tell which network a channel came from. `widthFromFlow` is the unifying quantity and is what actually drives the carve.

4. **One ER transect per *reach*, not per unit.** `collectUnits` resamples at `dx = max(intakeWidth/2, 0.5)`, and headwater channels floor at 0.5 px — a detailed tile emits ~60,000 units. Transecting every unit costs ~24M cache-missing samples per tile (~0.8 s). One transect per reach (~20 channel widths of arc) is ~1,500 transects, ~0.6M samples, ~6 ms. Rosgen types a reach, not a point, so this is the method rather than an approximation.

   Those are per-pass figures. `LocalRiverProvider.buildTile` calls `collectUnits` three times, each constructing a fresh `ReachRosgenClassifier` and running a full `prepare`, so the realised cost is ~18 ms/tile against ~2.4 s for the per-unit alternative. Reusing one classification across the two post-`update` calls (`:249`, `:252`) is a possible follow-on; it is not done here because the network changes between `:217` and `:249`, and proving the last two see an identical graph is more work than the 6 ms it saves.

5. **The ER walk is bounded by `2.05 × bankfullWidth` per side, not `MAX_INFLUENCE_RADIUS`.** The key resolves ER only at 1.4 / 2.2 / 4.0, so ~2× width per side suffices (ER = floodProneWidth / bankfullWidth, so ER > 4 needs 2W per side). `MAX_INFLUENCE_RADIUS` is a carve constant; borrowing it makes the walk 4× longer for no benefit and, because `Interpolation.sampleBilinear:38-41` *clamps* its indices rather than failing, an overrunning walk silently returns the tile-edge pixel repeated and reports `ER = ∞` — turning a 128 px band inside every tile border into spurious `C`/`E`/`DA`.

6. **No fields on `Channel`.** A per-point type array would need maintaining through `reSample` and `keepOnly`, and `keepOnly` (`Channel.java:167-172`) already slices `spline` and `flow` while leaving `bedElevations` behind — the array silently survives at its pre-slice length, misaligned to the spline. Nothing documents this: the `bedElevations` javadoc (`Channel.java:41-48`) claims only that `reSample` keeps it aligned and says nothing about `keepOnly`, so it is an unmaintained field rather than a javadoc that lies (the `keepOnly` mention at `Channel.java:28` belongs to `flow`, which *is* sliced). Keeping types out of `Channel` avoids adding a second array with the same exposure.

7. **A unit that is not a reach gets no type, and says so.** `HydrologicalFeature` has always had `SOURCE` and `DRAIN`; nothing has ever emitted them, so every live unit is `RIVER` (`RiverNetwork.java:806`) and the placeholder stamps `RosgenType.A` on all of them. Both halves are worth fixing together: the graph already knows which channel ends sit on `Endpoint.Type.SOURCE`/`DRAIN` nodes, and `rosgenType` is already nullable end to end (`HydrologicalUnit.java:177,195`, coalesced to `A` at all four read sites). Stamping `null` rather than `A` is behaviour-neutral in the carve but makes "never classified" distinguishable from "measured as `A`" — which matters precisely during P1, when the whole question is what fraction of the world each type actually claims. Deciding the kind belongs to `collectUnits`, not to `ChannelTyper`: the typer sees geometry and a raster, the network sees topology.

## File Structure

| File                                                   | Responsibility                                                                                                     |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| `hydrology/ChannelGeometry.java` (modify)              | Gains `widthDepthRatio(width)` — the W/D law, next to the existing depth law                                       |
| `config/HydrologyTuning.java` (modify)                 | Rosgen threshold + reach + transect constants                                                                      |
| `hydrology/rosgen/ReachMetrics.java` (create)          | Immutable measured tuple for one reach                                                                             |
| `hydrology/rosgen/RosgenKey.java` (create)             | Pure decision key + dead band. No raster, no graph.                                                                |
| `hydrology/rosgen/ReachMetricsSampler.java` (create)   | Raster side: slope from bed elevations, ER from transects                                                          |
| `hydrology/rosgen/ReachRosgenClassifier.java` (create) | Reach segmentation + downstream-first graph walk; implements `ChannelTyper`                                        |
| `hydrology/rosgen/CLAUDE.md` (create)                  | Package index                                                                                                      |
| `hydrology/meanders/ChannelTyper.java` (create)        | Interface `RiverNetwork` calls. Lives in `meanders` so the package dependency stays one-way (`rosgen → meanders`). |
| `hydrology/meanders/RiverNetwork.java` (modify)        | `collectUnits` gains a `ChannelTyper`; `addFeatureUnits` stamps the type and the per-point feature kind            |
| `hydrology/meanders/Meanders.java` (modify)            | Gains `elev` field + `collectUnits` delegate                                                                       |
| `hydrology/GlobalNetworkBuilder.java` (modify)         | Clones `base[0]` and passes it to `Meanders`                                                                       |
| `hydrology/LocalRiverProvider.java` (modify)           | Three `collectUnits` call sites route through `Meanders`                                                           |
| `debug/HydrologyUnitVisualizer.java` (modify)          | Type-coloured unit PNG — the visual regression check                                                               |
| `debug/tests/LocalRiverTest.java` (modify)             | Slope-percentile dump (input to P1 calibration) + type PNG                                                         |

---

### Task 1: The width-to-depth law

**Files:**

- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/ChannelGeometry.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/ChannelGeometryTest.java` (create)

**Interfaces:**

- Produces: `public static double ChannelGeometry.widthDepthRatio(double width)` — dimensionless W/D for a channel of the given native-px width. Monotone increasing.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/ChannelGeometryTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for the cross-section geometry laws. */
class ChannelGeometryTest {

    @Test
    void widthDepthRatioCrossesTwelveAtReferenceWidth() {
        // W_REF is defined as the width at which the narrow-deep / wide-shallow
        // boundary (W/D = 12) falls; this is the single calibration knob.
        assertEquals(12.0, ChannelGeometry.widthDepthRatio(ChannelGeometry.W_REF), 1e-9);
    }

    @Test
    void widthDepthRatioIsMonotoneIncreasing() {
        double previous = ChannelGeometry.widthDepthRatio(0.2);
        for (double w = 0.4; w <= 16.0; w += 0.2) {
            final double current = ChannelGeometry.widthDepthRatio(w);
            assertTrue(current > previous, "W/D must increase with width at w=" + w);
            previous = current;
        }
    }

    @Test
    void widthDepthRatioIsFiniteAtAndBelowTheWidthFloor() {
        assertTrue(Double.isFinite(ChannelGeometry.widthDepthRatio(0.0)));
        assertTrue(ChannelGeometry.widthDepthRatio(0.0) > 0.0);
    }

    @Test
    void depthForWidthRemainsUntouchedAcrossTheRepresentableRange() {
        // Guard: depthForWidth is floored at 1.0 for every representable width and
        // feeds the meander migration rate. widthDepthRatio must not have changed it.
        assertEquals(1.0, ChannelGeometry.depthForWidth(0.2), 1e-9);
        assertEquals(1.0, ChannelGeometry.depthForWidth(16.0), 1e-9);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.ChannelGeometryTest"
```

Expected: compile failure — `cannot find symbol: method widthDepthRatio` and `variable W_REF`.

- [ ] **Step 3: Write the implementation**

In `ChannelGeometry.java`, after `depthForWidth` (around line 29), add:

```java
    /**
     * Native-px width at which the width-to-depth ratio crosses {@code 12} — Rosgen's boundary between
     * the narrow-deep types ({@code E G A}) and the wide-shallow ones ({@code C F B}). The single
     * calibration knob for the {@code E}&harr;{@code C} and {@code G}&harr;{@code F} splits: lower it and
     * rivers start looking wide and shallow sooner. First-cut, untuned value pending visual calibration
     * via {@code localRiverTest}.
     */
    public static final double W_REF = 4.0;

    /** W/D at {@link #W_REF} — Rosgen's narrow-deep / wide-shallow boundary. */
    private static final double WD_AT_REF = 12.0;

    /**
     * Exponent of the width-to-depth law. Hydraulic geometry gives {@code W/D ∝ DA^0.139}
     * (Bieger et al. 2015, nationwide); this project's width law is {@code W = 0.4·√flow}, i.e.
     * {@code W ∝ DA^0.50}, so {@code W/D ∝ W^(0.139/0.50)}.
     */
    private static final double WD_EXPONENT = 0.278;

    /**
     * Dimensionless width-to-depth ratio for a channel of the given native-px width:
     * {@code 12 · (width / W_REF)^0.278}, monotone increasing, calibrated so {@code W_REF} maps to
     * {@code 12}. Used by the Rosgen classifier to pick narrow-deep types over wide-shallow ones.
     *
     * <p><b>Deliberately not derived from {@link #depthForWidth}.</b> That law is floored at {@code 1.0}
     * across the whole representable width range ({@code [0.2, 16]} px; the expression only exceeds 1
     * above {@link #DEPTH_WIDTH_SCALE} px), so {@code width / depthForWidth(width)} degenerates to
     * {@code width} — which would put the {@code W/D = 12} boundary at 12 px against a 16 px cap and
     * classify nearly every channel as narrow-deep. {@code depthForWidth} additionally feeds the meander
     * migration rate, so it is not safe to re-floor.
     */
    public static double widthDepthRatio(double width) {
        return WD_AT_REF * Math.pow(Math.max(width, MIN_RATIO_WIDTH) / W_REF, WD_EXPONENT);
    }

    /** Floor guarding {@link #widthDepthRatio} against {@code pow(0, …)} at degenerate widths. */
    private static final double MIN_RATIO_WIDTH = 0.05;
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.ChannelGeometryTest"
```

Expected: 4 tests, all PASS.

- [ ] **Step 5: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/ChannelGeometry.java src/test/java/me/batata_1/fractal_terrain/hydrology/ChannelGeometryTest.java
git commit -m "feat(hydrology): add width-to-depth ratio law for Rosgen classification

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Rosgen tuning constants

**Files:**

- Modify: `src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java`

**Interfaces:**

- Produces: the constants consumed by Tasks 3–5. Exact names and values below.

- [ ] **Step 1: Add the constants**

Append to `HydrologyTuning.java`, before the closing brace, after `maxNativeWidth()`:

```java
    // ──────────────────────────────────────────────────────────────────────────
    // Rosgen Level-I classification (see plans/rosgen-classification-plan.md)
    //
    // Slope bands: Rosgen's published values are real-world channel slopes. A
    // Minecraft-scale world is vertically exaggerated relative to its horizontal run
    // (a 150-block rise over 300 blocks is slope 0.5, five times S_AA), so copying the
    // literature directly classifies most of the world as Aa+. These are the literature
    // values as a starting point ONLY; recalibrate from the slope histogram dumped by
    // localRiverTest before judging any other threshold. The key tests slope first, so
    // slope miscalibration dominates every other error.
    // ──────────────────────────────────────────────────────────────────────────

    /** Slope at or above which a reach is {@code Aa+} (very steep, step/waterfall). Needs recalibration. */
    public static final double S_AA = 0.10;

    /** Slope at or above which a reach is {@code A} (steep, cascading step-pool). Needs recalibration. */
    public static final double S_A = 0.04;

    /**
     * Slope below which an anastomosing ({@code DA}) reach is plausible — essentially flat. Not a
     * published Rosgen figure: it is a gate this plan introduces so {@code DA} cannot claim a reach with
     * any real fall. First-cut, untuned value pending visual calibration via {@code localRiverTest}.
     */
    public static final double S_DA = 0.005;

    /** Entrenchment ratio below which a reach is entrenched ({@code F}/{@code G}). Rosgen: 1.0–1.4. */
    public static final double ER_ENTRENCHED = 1.4;

    /** Entrenchment ratio below which a reach is moderately entrenched ({@code B}). Rosgen: 1.41–2.2. */
    public static final double ER_SLIGHT = 2.2;

    /** Entrenchment ratio above which the flood-prone area is wide enough for {@code DA}. */
    public static final double ER_ANASTOMOSE = 4.0;

    /**
     * Width-to-depth ratio separating narrow-deep ({@code E G}) from wide-shallow ({@code C F}). Rosgen
     * publishes this boundary at {@code 12}, but the W/D it is compared against is prescribed by
     * {@link me.batata_1.fractal_terrain.hydrology.ChannelGeometry#widthDepthRatio} rather than measured,
     * so the pair only means what {@code W_REF} makes it mean. Calibrate {@code W_REF}, not this.
     */
    public static final double WD_NARROW = 12.0;

    /** Rosgen's published ER tolerance — the dead band that suppresses type flicker at a threshold. */
    public static final double ER_TOLERANCE = 0.2;

    /** Rosgen's published W/D tolerance — the dead band that suppresses type flicker at a threshold. */
    public static final double WD_TOLERANCE = 2.0;

    /**
     * Maximum bankfull depth as a multiple of mean bankfull depth, setting the flood-prone stage
     * ({@code bed + 2·dMax}). A rule of thumb rather than a sourced figure — calibrate visually.
     */
    public static final double DEPTH_MAX_FACTOR = 1.5;

    /**
     * Bed elevation (native px above sea level, which is {@code 0}) below which a reach counts as near
     * base level for the {@code DA} gate — deltas and tidal flats. First-cut, untuned.
     */
    public static final double DELTA_ELEV = 4.0;

    /**
     * Coefficient of the braiding threshold {@code S_braid = K_BRAID · width^-0.88}. Leopold &amp; Wolman
     * (1957) give {@code S = k·Q^-0.44}; with {@code W ∝ √flow ∝ √DA} this becomes a law in width.
     * Braiding is a style choice here, not a measurement — there is no sediment-transport model — so this
     * gates where braiding would be *plausible*. First-cut, untuned.
     */
    public static final double K_BRAID = 0.02;

    /** Exponent of the braiding threshold in width. Derived: {@code -0.44 / 0.50}. */
    public static final double BRAID_WIDTH_EXPONENT = -0.88;

    /**
     * Minimum native-px width for a {@code D} (braided) reach — braiding needs a large channel. Half the
     * width cap, chosen so {@code D} stays rare rather than from any published figure. First-cut,
     * untuned value pending visual calibration via {@code localRiverTest}.
     */
    public static final double BRAID_MIN_WIDTH = 8.0;

    /** Reach length as a multiple of bankfull width — Rosgen's own reach definition (20–30 widths). */
    public static final double REACH_WIDTHS = 20.0;

    /**
     * Hard cap (native px) on a reach window. {@code REACH_WIDTHS · MAX_WIDTH} is 320 px, over half a
     * 512 px tile, which would make a trunk river's reach span most of the tile. Shorter windows are
     * noisier, not wrong; the dead band absorbs the noise.
     */
    public static final double REACH_MAX_PX = 64.0;

    /**
     * Entrenchment transect half-walk as a multiple of bankfull width. The key resolves ER only at 1.4,
     * 2.2 and 4.0, and {@code ER = floodProneWidth / bankfullWidth}, so {@code 2.05·width} per side
     * resolves ER up to 4.1 — everything the key needs. <b>Deliberately not
     * {@link #MAX_INFLUENCE_RADIUS}</b>: that is a carve constant, and a 128 px walk on a 514 px padded
     * buffer overruns the tile for any channel within 128 px of a border. {@code sampleBilinear} clamps
     * rather than failing, so an overrun silently returns the edge pixel repeated and reports
     * {@code ER = ∞}.
     */
    public static final double ER_WALK_WIDTHS = 2.05;

    /**
     * Entrenchment transect step (native px) as a fraction of bankfull width, floored at
     * {@link #ER_STEP_MIN}. Scaling with width keeps the step count roughly constant (~16 per side)
     * across the width range; ER is a ratio compared at four coarse thresholds, so sub-pixel precision
     * buys nothing.
     */
    public static final double ER_STEP_WIDTH_FRACTION = 0.125;

    /** Floor (native px) on the entrenchment transect step. */
    public static final double ER_STEP_MIN = 0.5;
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" compileJava
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java
git commit -m "feat(config): add Rosgen classification tuning constants

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: `ReachMetrics` and the decision key

The highest-value test in this plan. `RosgenKey.classify` is a total pure function of five doubles, so it is table-testable with no fixtures, no raster and no graph.

**Files:**

- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetrics.java`
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/RosgenKey.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/RosgenKeyTest.java` (create)

**Interfaces:**

- Consumes: `HydrologyTuning` constants (Task 2), `ChannelGeometry.widthDepthRatio` (Task 1).
- Produces:
  - `public record ReachMetrics(double slope, double entrenchment, double widthDepth, double width, double bedElev)`
  - `public static RosgenType RosgenKey.classify(ReachMetrics m)`
  - `public static RosgenType RosgenKey.applyDeadBand(ReachMetrics m, RosgenType raw, @Nullable RosgenType previous)`
  - `public static double RosgenKey.braidThreshold(double width)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/RosgenKeyTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * Table-driven contract test for the Rosgen Level-I decision key. The key is a total pure function of
 * five measured doubles, so every case here is an exact input/output pair with no fixture, raster or
 * graph involved. Each type gets at least one case that reaches it; the ordering cases pin the tests
 * that must fire before others.
 */
class RosgenKeyTest {

    /** slope, ER, W/D, width, bedElev — a reach comfortably inside the C envelope. */
    private static ReachMetrics baseC() {
        return new ReachMetrics(0.005, 6.0, 20.0, 10.0, 60.0);
    }

    private static ReachMetrics with(ReachMetrics m, Double slope, Double er, Double wd, Double w, Double z) {
        return new ReachMetrics(
                slope == null ? m.slope() : slope,
                er == null ? m.entrenchment() : er,
                wd == null ? m.widthDepth() : wd,
                w == null ? m.width() : w,
                z == null ? m.bedElev() : z);
    }

    @Test
    void steepestSlopeGivesAaRegardlessOfEverythingElse() {
        // Slope is tested first: a very steep reach is Aa+ even with a broad floodplain.
        assertEquals(RosgenType.Aa, RosgenKey.classify(with(baseC(), 0.20, 8.0, 30.0, null, null)));
    }

    @Test
    void steepSlopeGivesA() {
        assertEquals(RosgenType.A, RosgenKey.classify(with(baseC(), 0.06, 8.0, 30.0, null, null)));
    }

    @Test
    void entrenchedAndNarrowGivesG() {
        assertEquals(RosgenType.G, RosgenKey.classify(with(baseC(), 0.03, 1.2, 8.0, null, null)));
    }

    @Test
    void entrenchedAndWideGivesF() {
        assertEquals(RosgenType.F, RosgenKey.classify(with(baseC(), 0.03, 1.2, 20.0, null, null)));
    }

    @Test
    void moderatelyEntrenchedGivesB() {
        assertEquals(RosgenType.B, RosgenKey.classify(with(baseC(), 0.03, 1.8, 20.0, null, null)));
    }

    @Test
    void bAndGShareASlopeBandAndAreSeparatedByEntrenchmentOnly() {
        // Rosgen's published slope bands for B and G overlap exactly (0.02-0.039); ER is the
        // discriminator. Same slope and W/D, different ER, different type.
        final ReachMetrics g = with(baseC(), 0.03, 1.2, 8.0, null, null);
        final ReachMetrics b = with(baseC(), 0.03, 1.8, 8.0, null, null);
        assertEquals(RosgenType.G, RosgenKey.classify(g));
        assertEquals(RosgenType.B, RosgenKey.classify(b));
    }

    @Test
    void nearBaseLevelFlatAndVeryWideFloodProneGivesDA() {
        assertEquals(RosgenType.DA, RosgenKey.classify(with(baseC(), 0.001, 6.0, 20.0, 10.0, 2.0)));
    }

    @Test
    void daIsTestedBeforeDSoBraidingNeverStealsAnAnastomosingReach() {
        // Same reach, both gates open: DA must win.
        final ReachMetrics both = new ReachMetrics(0.001, 6.0, 20.0, 12.0, 2.0);
        assertEquals(RosgenType.DA, RosgenKey.classify(both));
    }

    @Test
    void wideChannelAboveTheBraidThresholdGivesD() {
        final double width = 12.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        // High above sea level, so the DA gate is shut.
        assertEquals(RosgenType.D, RosgenKey.classify(new ReachMetrics(slope, 6.0, 20.0, width, 80.0)));
    }

    @Test
    void narrowChannelAboveTheBraidThresholdIsNotD() {
        final double width = 2.0;
        final double slope = RosgenKey.braidThreshold(width) * 2.0;
        assertEquals(RosgenType.E, RosgenKey.classify(new ReachMetrics(slope, 6.0, 8.0, width, 80.0)));
    }

    @Test
    void slightlyEntrenchedAndNarrowGivesE() {
        assertEquals(RosgenType.E, RosgenKey.classify(new ReachMetrics(0.001, 6.0, 8.0, 2.0, 80.0)));
    }

    @Test
    void slightlyEntrenchedAndWideGivesC() {
        assertEquals(RosgenType.C, RosgenKey.classify(new ReachMetrics(0.001, 6.0, 20.0, 2.0, 80.0)));
    }

    @Test
    void saturatedEntrenchmentIsHandledAsSlightlyEntrenched() {
        // A transect that never exceeds the flood-prone stage reports ER = +inf. That is the correct
        // semantic (a broad flat valley), not a failure, and must not throw or fall through.
        assertEquals(
                RosgenType.C,
                RosgenKey.classify(new ReachMetrics(0.001, Double.POSITIVE_INFINITY, 20.0, 2.0, 80.0)));
    }

    @Test
    void deadBandKeepsTheUpstreamTypeWhenEntrenchmentSitsOnAThreshold() {
        // ER 2.25 is within the published +/-0.2 of the 2.2 boundary, so a B neighbour holds.
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 2.25, 20.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.classify(onBoundary));
        assertEquals(RosgenType.B, RosgenKey.applyDeadBand(onBoundary, RosgenType.C, RosgenType.B));
    }

    @Test
    void deadBandKeepsTheUpstreamTypeWhenWidthDepthSitsOnAThreshold() {
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 6.0, 13.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.classify(onBoundary));
        assertEquals(RosgenType.E, RosgenKey.applyDeadBand(onBoundary, RosgenType.C, RosgenType.E));
    }

    @Test
    void deadBandDoesNotSuppressAChangeFarFromAnyThreshold() {
        final ReachMetrics clear = new ReachMetrics(0.001, 6.0, 20.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.applyDeadBand(clear, RosgenType.C, RosgenType.E));
    }

    @Test
    void deadBandPassesThroughWhenThereIsNoUpstreamNeighbour() {
        final ReachMetrics onBoundary = new ReachMetrics(0.001, 2.25, 20.0, 2.0, 80.0);
        assertEquals(RosgenType.C, RosgenKey.applyDeadBand(onBoundary, RosgenType.C, null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.rosgen.RosgenKeyTest"
```

Expected: compile failure — package `…hydrology.rosgen` does not exist.

- [ ] **Step 3: Write `ReachMetrics`**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetrics.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

/**
 * The measured attributes of one river reach, in the units the Rosgen Level-I key compares against.
 *
 * <p>Only {@code slope}, {@code entrenchment} and {@code width} are genuine observables of the generated
 * terrain: slope and entrenchment emerge from the diffusion elevation field, width from flow
 * accumulation. {@code widthDepth} is derived from {@code width}
 * ({@link me.batata_1.fractal_terrain.hydrology.ChannelGeometry#widthDepthRatio}) rather than measured —
 * no depth is modelled — and is therefore a prescription dressed as an input, kept here because the
 * published key tests it. Sinuosity is deliberately absent: it is produced by the meander relaxation, so
 * feeding it back would let meander tuning decide the Rosgen type, which then decides floodplain width.
 * Use sinuosity to validate the result, never to produce it.
 *
 * @param slope        along-channel bed slope, dimensionless (drop / arc length), never negative
 * @param entrenchment flood-prone width / bankfull width; {@code +inf} when the transect saturates
 * @param widthDepth   bankfull width / mean bankfull depth, dimensionless
 * @param width        bankfull width, native px
 * @param bedElev      reach bed elevation relative to sea level (which is {@code 0}), native px
 */
public record ReachMetrics(double slope, double entrenchment, double widthDepth, double width, double bedElev) {}
```

- [ ] **Step 4: Write `RosgenKey`**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/RosgenKey.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import org.jetbrains.annotations.Nullable;

/**
 * The Rosgen Level-I decision key: a total, pure, deterministic function from measured reach metrics to
 * a stream type. No raster, no graph, no state — everything this class needs arrives in a
 * {@link ReachMetrics}.
 *
 * <p>The key is ordered and first-match-wins, restructured from the published version so every test uses
 * a quantity this project can actually measure. Ordering is load-bearing:
 *
 * <ol>
 *   <li><b>Slope first.</b> {@code Aa+} and {@code A} occupy slope bands no other type overlaps, and both
 *       are entrenched by definition in their landform, so testing entrenchment first would only add a
 *       way to get them wrong.</li>
 *   <li><b>Entrenchment second</b> — the only test separating the entrenched family ({@code F}, {@code G})
 *       from everything with a floodplain. Within that family W/D picks narrow-deep {@code G} (a gully)
 *       over wide-shallow {@code F} (an incised meandering river). {@code B}'s published slope band
 *       overlaps {@code G}'s exactly, so entrenchment — not slope — is what distinguishes them.</li>
 *   <li><b>{@code DA} before {@code D}.</b> Both want unconfined valleys, but anastomosing is far more
 *       specific: near base level, essentially flat, extremely wide flood-prone area. Testing it first
 *       stops braiding from stealing it.</li>
 *   <li><b>{@code E} vs {@code C} last</b>, on W/D alone — small meadow streams become {@code E}, trunk
 *       rivers {@code C}.</li>
 * </ol>
 *
 * <p>Level II (the substrate digit {@code 1}–{@code 6}) is out of scope and not recoverable: grain size
 * is a function of lithology, transport history and sediment supply, none of which are in an elevation
 * field.
 */
public final class RosgenKey {

    private RosgenKey() {}

    /**
     * The Rosgen Level-I type for one reach. Total: every input, including a saturated
     * ({@code +inf}) entrenchment ratio, returns a type.
     */
    public static RosgenType classify(ReachMetrics m) {
        // Steep confined headwaters: slope alone decides.
        if (m.slope() >= HydrologyTuning.S_AA) return RosgenType.Aa;
        if (m.slope() >= HydrologyTuning.S_A) return RosgenType.A;

        // Entrenched: the valley pinches the channel.
        if (m.entrenchment() < HydrologyTuning.ER_ENTRENCHED) {
            return m.widthDepth() < HydrologyTuning.WD_NARROW ? RosgenType.G : RosgenType.F;
        }

        // Moderately entrenched.
        if (m.entrenchment() < HydrologyTuning.ER_SLIGHT) return RosgenType.B;

        // Slightly entrenched: a broad floodplain is available.
        if (m.bedElev() < HydrologyTuning.DELTA_ELEV
                && m.slope() < HydrologyTuning.S_DA
                && m.entrenchment() > HydrologyTuning.ER_ANASTOMOSE) {
            return RosgenType.DA;
        }
        if (m.width() > HydrologyTuning.BRAID_MIN_WIDTH && m.slope() > braidThreshold(m.width())) {
            return RosgenType.D;
        }
        return m.widthDepth() < HydrologyTuning.WD_NARROW ? RosgenType.E : RosgenType.C;
    }

    /**
     * Slope above which braiding is plausible for a channel of the given native-px width. Braiding is not
     * measurable here — there is no sediment-transport model, and nothing in an elevation field
     * distinguishes a braided reach from a meandering one — so this gates where braiding would be
     * plausible and accepts the outcome as authored.
     */
    public static double braidThreshold(double width) {
        return HydrologyTuning.K_BRAID * Math.pow(width, HydrologyTuning.BRAID_WIDTH_EXPONENT);
    }

    /**
     * Rosgen's published tolerances (ER &plusmn;0.2, W/D &plusmn;2.0) applied as a dead band: when a
     * reach's entrenchment ratio or width-to-depth ratio sits within tolerance of one of the thresholds
     * the key compares it against, keep {@code previous} — the type of the neighbouring reach — instead of
     * committing to {@code raw}.
     *
     * <p>The tolerances exist because the field metrics are noisy; a raster implementation is noisier
     * still. Without the dead band, types flicker along a single river, and because
     * {@link me.batata_1.fractal_terrain.hydrology.rosgen.RosgenProfile} controls {@code floodPlainLength}
     * and {@code riverInfluence}, a flicker becomes a visibly scalloped floodplain edge.
     *
     * <p><b>Scope: ER and W/D only.</b> The slope bands ({@code S_AA}, {@code S_A}, {@code S_DA}) and the
     * braiding threshold are deliberately outside the dead band. Slope is a real property of the
     * landform rather than a noisy transect measurement, and a reach genuinely crossing into the steep
     * bands should change type there; suppressing that would smear {@code Aa+}/{@code A} headwaters into
     * the reaches below them. Type variation driven by slope is intended behaviour, not flicker.
     *
     * @param previous the neighbouring reach's committed type, or {@code null} at a network leaf
     */
    public static RosgenType applyDeadBand(ReachMetrics m, RosgenType raw, @Nullable RosgenType previous) {
        if (previous == null || raw == previous) return raw;
        final boolean onThreshold = nearThreshold(m.entrenchment(), HydrologyTuning.ER_ENTRENCHED, HydrologyTuning.ER_TOLERANCE)
                || nearThreshold(m.entrenchment(), HydrologyTuning.ER_SLIGHT, HydrologyTuning.ER_TOLERANCE)
                || nearThreshold(m.entrenchment(), HydrologyTuning.ER_ANASTOMOSE, HydrologyTuning.ER_TOLERANCE)
                || nearThreshold(m.widthDepth(), HydrologyTuning.WD_NARROW, HydrologyTuning.WD_TOLERANCE);
        return onThreshold ? previous : raw;
    }

    /** Whether {@code value} sits within {@code tolerance} of {@code threshold}. Infinities are never near. */
    private static boolean nearThreshold(double value, double threshold, double tolerance) {
        if (!Double.isFinite(value)) return false;
        return Math.abs(value - threshold) <= tolerance;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.rosgen.RosgenKeyTest"
```

Expected: 17 tests, all PASS.

- [ ] **Step 6: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/
git commit -m "feat(rosgen): add Rosgen Level-I decision key and reach metrics

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `ReachMetricsSampler` — slope and the entrenchment transect

**Files:**

- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetricsSampler.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetricsSamplerTest.java` (create)

**Interfaces:**

- Consumes: `ReachMetrics` (Task 3), `HydrologyTuning` constants (Task 2), `ChannelGeometry.widthDepthRatio` (Task 1), `Interpolation.sampleBilinear`.
- Produces:
  - `public ReachMetricsSampler(float[] elev, int gridSize)`
  - `public double entrenchmentRatio(double[] point, double[] normal, double bedElev, double width)`
  - `public static double slope(double[] bedElevations, double arcLength, int fromIndex, int toIndex)`

Tests use analytic rasters with known answers, never captured checksums.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetricsSamplerTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Analytic tests for the raster side of classification. Every fixture is a synthetic field whose
 * expected answer is derived by hand, so a failure means the sampler is wrong rather than that a
 * captured expectation went stale.
 */
class ReachMetricsSamplerTest {

    private static final int SIDE = 128;

    /**
     * A symmetric V-shaped valley running along the z axis, floor at x = SIDE/2, elevation rising
     * {@code gradient} per px of horizontal distance from the floor.
     */
    private static float[] vValley(double gradient) {
        final float[] elev = new float[SIDE * SIDE];
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                elev[x * SIDE + z] = (float) (Math.abs(x - SIDE / 2.0) * gradient);
            }
        }
        return elev;
    }

    @Test
    void transectOnAVValleyRecoversTheAnalyticFloodProneWidth() {
        // Flood-prone stage sits at bed + 2*dMax. With bed = 0 and a gradient of 1.0 per px, the
        // stage elevation equals the horizontal distance at which the walk stops, so the flood-prone
        // half-width equals the stage and the full width is twice it.
        final double gradient = 1.0;
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(gradient), SIDE);
        final double width = 4.0;
        final double[] point = {SIDE / 2.0, SIDE / 2.0};
        final double[] normal = {1.0, 0.0}; // across the valley
        final double er = sampler.entrenchmentRatio(point, normal, 0.0, width);

        final double stage = 2.0 * me.batata_1.fractal_terrain.config.HydrologyTuning.DEPTH_MAX_FACTOR
                * me.batata_1.fractal_terrain.hydrology.ChannelGeometry.depthForWidth(width);
        final double expected = (2.0 * stage / gradient) / width;
        // One transect step of tolerance: the walk stops at the first sample above the stage.
        final double step = Math.max(
                me.batata_1.fractal_terrain.config.HydrologyTuning.ER_STEP_MIN,
                width * me.batata_1.fractal_terrain.config.HydrologyTuning.ER_STEP_WIDTH_FRACTION);
        assertEquals(expected, er, 2.0 * step / width);
    }

    @Test
    void transectOnAFlatFieldSaturatesToInfinity() {
        // A perfectly flat plain never exceeds the flood-prone stage, so the walk runs to its bound.
        // That is the correct semantic for a broad flat valley, not a failure.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(new float[SIDE * SIDE], SIDE);
        final double er = sampler.entrenchmentRatio(new double[] {64.0, 64.0}, new double[] {1.0, 0.0}, 0.0, 4.0);
        assertTrue(Double.isInfinite(er), "a saturated transect reports ER = +inf, got " + er);
    }

    @Test
    void narrowGorgeGivesAnEntrenchedRatio() {
        // A steep gorge: the walk terminates almost immediately, so ER lands under the entrenched
        // threshold.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(50.0), SIDE);
        final double er = sampler.entrenchmentRatio(new double[] {64.0, 64.0}, new double[] {1.0, 0.0}, 0.0, 4.0);
        assertTrue(er < me.batata_1.fractal_terrain.config.HydrologyTuning.ER_ENTRENCHED,
                "a gorge must classify as entrenched, got ER = " + er);
    }

    @Test
    void transectNeverReadsOutsideTheBuffer() {
        // A channel sitting on the very edge of the buffer must not overrun. sampleBilinear clamps,
        // so this asserts the walk terminates and returns a usable number rather than looping.
        final ReachMetricsSampler sampler = new ReachMetricsSampler(vValley(1.0), SIDE);
        final double er = sampler.entrenchmentRatio(new double[] {0.0, 0.0}, new double[] {1.0, 0.0}, 0.0, 16.0);
        assertTrue(er >= 0.0, "edge transect must return a non-negative ratio, got " + er);
    }

    @Test
    void slopeOnAConstantGradientBedIsThatGradient() {
        final double[] bed = new double[21];
        for (int i = 0; i < bed.length; i++) bed[i] = 100.0 - i * 0.5; // drops 0.5 per point
        // 20 intervals of 0.5 elevation over an arc length of 40 px -> slope 0.25.
        assertEquals(0.25, ReachMetricsSampler.slope(bed, 40.0, 0, 20), 1e-9);
    }

    @Test
    void slopeIsNeverNegative() {
        // ChannelElevationAssigner forces beds monotone non-increasing downstream, but a degenerate
        // reach must still not produce a negative slope that would skip the Aa+/A tests.
        final double[] bed = {10.0, 20.0};
        assertEquals(0.0, ReachMetricsSampler.slope(bed, 5.0, 0, 1), 1e-9);
    }

    @Test
    void slopeOnAZeroLengthReachIsZero() {
        final double[] bed = {10.0, 5.0};
        assertEquals(0.0, ReachMetricsSampler.slope(bed, 0.0, 0, 1), 1e-9);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.rosgen.ReachMetricsSamplerTest"
```

Expected: compile failure — `cannot find symbol: class ReachMetricsSampler`.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetricsSampler.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.math.Interpolation;

/**
 * The raster side of Rosgen classification: measures along-channel slope and the entrenchment ratio for
 * one reach.
 *
 * <p><b>The elevation field must be the raw decoded terrain, never a carved buffer.</b>
 * {@code HydrologyProfileCarver.carveRiverShells} <em>creates</em> the floodplain and writes in place;
 * measuring entrenchment on its output measures {@code FLOODPLAIN_BASE} and
 * {@code FLOODPLAIN_WIDTH_FACTOR} — the carve's own tuning constants — instead of the terrain.
 *
 * <p>Cost note: a transect walks perpendicular to the channel, so consecutive samples stride a whole
 * row ({@code gridSize} floats) and get no spatial locality — each sample is close to a cache miss.
 * Callers must therefore transect once per <em>reach</em>, not once per spline point;
 * {@link ReachRosgenClassifier} is responsible for that and the step scales with width to keep the step
 * count roughly constant.
 */
public final class ReachMetricsSampler {

    private final float[] elev;
    private final int gridSize;

    /**
     * @param elev     raw decoded elevation, {@code gridSize²}, row-major {@code x * gridSize + z}
     * @param gridSize side of the (padded) square field
     */
    public ReachMetricsSampler(float[] elev, int gridSize) {
        if (elev.length != gridSize * gridSize) {
            throw new IllegalArgumentException("elev length " + elev.length + " != gridSize² " + (gridSize * gridSize));
        }
        this.elev = elev;
        this.gridSize = gridSize;
    }

    /**
     * Along-channel bed slope over {@code [fromIndex, toIndex]}: elevation drop divided by the arc length
     * spanning those points, floored at {@code 0}.
     *
     * <p>{@code ChannelElevationAssigner} propagates beds monotone non-increasing downstream, so an
     * uphill reach means degenerate geometry rather than real terrain; flooring at zero keeps such a
     * reach out of the {@code Aa+}/{@code A} branches rather than producing a nonsense negative slope.
     */
    public static double slope(double[] bedElevations, double arcLength, int fromIndex, int toIndex) {
        if (arcLength <= 0.0) return 0.0;
        final double drop = bedElevations[fromIndex] - bedElevations[toIndex];
        return Math.max(0.0, drop / arcLength);
    }

    /**
     * Entrenchment ratio at one point: flood-prone width divided by bankfull width, where the flood-prone
     * width is measured at a stage of twice the maximum bankfull depth above the bed.
     *
     * <p>The field method transcribed to a raster. From the point, step outward along {@code ±normal}
     * until the sampled elevation exceeds the flood-prone stage; the two half-widths sum to the
     * flood-prone width. When <em>both</em> sides reach the walk bound without exceeding the stage the
     * result is {@code +inf} — the correct semantic for a broad flat valley (the slightly-entrenched
     * branch), not a failure. The bound is {@code ER_WALK_WIDTHS · width} per side, which resolves every
     * threshold the key tests.
     *
     * @param point   reach centre in the network frame
     * @param normal  unit normal to the centreline at {@code point}
     * @param bedElev bed elevation at {@code point}
     * @param width   bankfull width, native px
     */
    public double entrenchmentRatio(double[] point, double[] normal, double bedElev, double width) {
        final double safeWidth = Math.max(width, HydrologyTuning.MIN_WIDTH);
        final double dMax = HydrologyTuning.DEPTH_MAX_FACTOR * ChannelGeometry.depthForWidth(safeWidth);
        final double floodProneStage = bedElev + 2.0 * dMax;
        final double maxWalk = HydrologyTuning.ER_WALK_WIDTHS * safeWidth;
        final double step =
                Math.max(HydrologyTuning.ER_STEP_MIN, safeWidth * HydrologyTuning.ER_STEP_WIDTH_FRACTION);

        final double positive = halfWidth(point, normal, +1.0, floodProneStage, maxWalk, step);
        final double negative = halfWidth(point, normal, -1.0, floodProneStage, maxWalk, step);
        if (Double.isInfinite(positive) && Double.isInfinite(negative)) return Double.POSITIVE_INFINITY;

        final double positiveWidth = Double.isInfinite(positive) ? maxWalk : positive;
        final double negativeWidth = Double.isInfinite(negative) ? maxWalk : negative;
        return (positiveWidth + negativeWidth) / safeWidth;
    }

    /**
     * Distance from {@code point} along {@code side · normal} at which elevation first exceeds
     * {@code floodProneStage}, or {@code +inf} when the walk reaches {@code maxWalk} without doing so.
     */
    private double halfWidth(
            double[] point, double[] normal, double side, double floodProneStage, double maxWalk, double step) {
        for (double d = step; d <= maxWalk; d += step) {
            final double x = point[0] + side * d * normal[0];
            final double z = point[1] + side * d * normal[1];
            if (Interpolation.sampleBilinear(elev, x, z, gridSize) > floodProneStage) return d;
        }
        return Double.POSITIVE_INFINITY;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.rosgen.ReachMetricsSamplerTest"
```

Expected: 7 tests, all PASS.

If `transectOnAVValleyRecoversTheAnalyticFloodProneWidth` fails by roughly one step, widen the tolerance to `3.0 * step / width` — the walk terminates at the first sample *above* the stage, so it overshoots by up to one step per side. Do not change the implementation to chase the exact value; the key compares ER at coarse thresholds.

- [ ] **Step 5: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetricsSampler.java src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachMetricsSamplerTest.java
git commit -m "feat(rosgen): add slope and entrenchment-ratio sampler

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: `ChannelTyper` seam and `ReachRosgenClassifier`

Reach segmentation plus the downstream-first graph walk that makes the dead band work across junctions. `update()` splits trunk rivers at every confluence, so per-channel hysteresis would reset at every junction in the world — producing exactly the scalloped edge the dead band exists to prevent, on a grid.

**Files:**

- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/ChannelTyper.java`
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachRosgenClassifier.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachRosgenClassifierTest.java` (create)

**Interfaces:**

- Consumes: `RosgenKey`, `ReachMetricsSampler`, `ReachMetrics` (Tasks 3–4); `RiverNetwork.getChannels()`, `RiverNetwork.getNodes()`, `RiverNetwork.getNode(int)`, `Channel.spline`, `Channel.bedElevations`, `Channel.widthAt(int)`, `Channel.startNodeId`, `Channel.endNodeId`, `Endpoint.outgoing`, `Endpoint.type`.
- Produces:
  - `public interface ChannelTyper { void prepare(RiverNetwork network); RosgenType[] typesFor(Channel channel); }`
  - `public ReachRosgenClassifier(float[] elev, int gridSize)` implementing `ChannelTyper`

- [ ] **Step 1: Write the `ChannelTyper` interface**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/ChannelTyper.java`:

```java
package me.batata_1.fractal_terrain.hydrology.meanders;

import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;

/**
 * Assigns a Rosgen type to every spline point of every channel. Declared here rather than beside its
 * implementation so the package dependency stays one-way: {@code rosgen} depends on {@code meanders},
 * never the reverse.
 *
 * <p>Two-phase because a type depends on neighbouring channels: {@link RiverNetwork#collectUnits}
 * resamples every channel first, then calls {@link #prepare} once with the whole network, then calls
 * {@link #typesFor} per channel. An implementation that needs no cross-channel context may leave
 * {@link #prepare} empty.
 */
public interface ChannelTyper {

    /** Called once per {@code collectUnits}, after every channel is resampled and before any lookup. */
    void prepare(RiverNetwork network);

    /**
     * Types for {@code channel}, index-aligned to its <b>current</b> spline points (post-resample), one
     * entry per point. Must never return {@code null} or a shorter array; an individual entry may be
     * {@code null}, meaning no reach covered that point, which {@link RiverNetwork#collectUnits} emits
     * as an untyped unit.
     *
     * <p>An implementation types every point, including the two endpoints. Deciding that a point is a
     * source or a drain rather than a reach — and therefore carries no Rosgen type — belongs to
     * {@link RiverNetwork#collectUnits}, which owns the graph topology; a typer sees only geometry and
     * the raster.
     */
    RosgenType[] typesFor(Channel channel);
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachRosgenClassifierTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import org.junit.jupiter.api.Test;

/** Contract tests for reach segmentation and the graph-order classification walk. */
class ReachRosgenClassifierTest {

    private static final int SIDE = 512;

    /** A flat plain: every transect saturates, so entrenchment never selects an entrenched type. */
    private static float[] flat() {
        return new float[SIDE * SIDE];
    }

    /** A straight source-to-drain channel down the middle of the tile. */
    private static RiverNetwork straightNetwork() {
        final List<RiverNetwork.NodeSpec> nodes = List.of(
                new RiverNetwork.NodeSpec(100.0, 256.0, Endpoint.Type.SOURCE),
                new RiverNetwork.NodeSpec(400.0, 256.0, Endpoint.Type.DRAIN));
        final ArrayList<double[]> pts = new ArrayList<>();
        for (double x = 100.0; x <= 400.0; x += 2.0) pts.add(new double[] {x, 256.0});
        final List<RiverNetwork.EdgeSpec> edges = List.of(new RiverNetwork.EdgeSpec(0, 1, pts, 4.0));
        return new RiverNetwork(SIDE, nodes, edges, false, 0, 2.0);
    }

    @Test
    void everySplinePointReceivesAType() {
        // With no gap-filler in classifyChannel, a null entry means segment() left an index uncovered.
        // This is the coverage test for reach segmentation, not a formality.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);

        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            assertNotNull(types);
            assertEquals(ch.numPts(), types.length, "one type per spline point");
            for (RosgenType t : types) assertNotNull(t, "no null types");
        }
    }

    @Test
    void aChannelWithoutBedElevationsStillReceivesTypes() {
        // Removed features (oxbows, abandoned rivers) carry no bed elevations. Classification must
        // degrade rather than throw.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = null;

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);
        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            assertEquals(ch.numPts(), types.length);
            for (RosgenType t : types) assertNotNull(t);
        }
    }

    @Test
    void typesAreConstantWithinAReachSoAdjacentUnitsRarelyDisagree() {
        // Rosgen types a reach, not a point. Adjacent points inside one reach must share a type, so
        // the floodplain edge cannot scallop at unit spacing.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);

        for (Channel ch : net.getChannels()) {
            final RosgenType[] types = classifier.typesFor(ch);
            int changes = 0;
            for (int i = 1; i < types.length; i++) if (types[i] != types[i - 1]) changes++;
            // Reaches are min(20*width, 64) px long, so this 300 px channel has roughly 19 of them --
            // but on a uniform field every reach measures the same and commits the same type, so the
            // count must stay far below the point count (~150). Anything approaching per-point variation
            // means reach segmentation is not being applied.
            assertTrue(changes <= 5, "expected at most 5 type changes along one channel, got " + changes);
        }
    }

    @Test
    void repeatedPrepareOnAnUnchangedNetworkIsDeterministic() {
        // prepare() must be idempotent on its own: it clears and rebuilds typesByChannelId, and the walk
        // order must not depend on HashMap iteration order.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        classifier.prepare(net);
        final RosgenType[] first = classifier.typesFor(net.getChannels().getFirst());
        classifier.prepare(net);
        final RosgenType[] second = classifier.typesFor(net.getChannels().getFirst());
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) assertEquals(first[i], second[i], "point " + i);
    }

    @Test
    void reclassifyingAfterAResampleIsDeterministic() {
        // The production sequence, which the idempotency test above does not reach: collectUnits runs
        // three times per tile and reSamples every channel before each classification pass.
        // QuinticHermiteSpline.reSampleWithTs refits a fresh Catmull-Rom through the resampled points
        // each time, so resampling an already-resampled spline is not obviously a fixed point. If it
        // drifts a reach's metrics across a threshold, the shell carved into the terrain
        // (LocalRiverProvider:249) and the type persisted to the index (:252) commit different types for
        // the same physical reach.
        final RiverNetwork net = straightNetwork();
        for (Channel ch : net.getChannels()) ch.bedElevations = descendingBed(ch.numPts());

        final ReachRosgenClassifier classifier = new ReachRosgenClassifier(flat(), SIDE);
        resampleAll(net);
        classifier.prepare(net);
        final RosgenType[] first = classifier.typesFor(net.getChannels().getFirst());

        resampleAll(net);
        classifier.prepare(net);
        final RosgenType[] second = classifier.typesFor(net.getChannels().getFirst());

        assertEquals(first.length, second.length, "a second resample must not change the point count");
        for (int i = 0; i < first.length; i++) assertEquals(first[i], second[i], "point " + i);
    }

    /** Resamples at the spacing {@code collectUnits} uses, so the test walks the production path. */
    private static void resampleAll(RiverNetwork net) {
        for (Channel ch : net.getChannels()) {
            if (ch.isResampleable()) ch.reSample(Math.max(ch.intakeWidth() / 2.0, 0.5));
        }
    }

    private static double[] descendingBed(int n) {
        final double[] bed = new double[n];
        for (int i = 0; i < n; i++) bed[i] = 80.0 - i * 0.05;
        return bed;
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifierTest"
```

Expected: compile failure — `cannot find symbol: class ReachRosgenClassifier`.

- [ ] **Step 4: Write the implementation**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachRosgenClassifier.java`:

```java
package me.batata_1.fractal_terrain.hydrology.rosgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
import me.batata_1.fractal_terrain.hydrology.meanders.Channel;
import me.batata_1.fractal_terrain.hydrology.meanders.ChannelTyper;
import me.batata_1.fractal_terrain.hydrology.meanders.Endpoint;
import me.batata_1.fractal_terrain.hydrology.meanders.RiverNetwork;
import me.batata_1.fractal_terrain.math.VectorOps;

/**
 * Segments every channel into reaches, measures each reach, and runs the Rosgen key over the whole graph
 * in downstream-first order so the dead band carries across junctions.
 *
 * <p><b>Why the graph order matters.</b> {@code RiverNetwork.update} splits a trunk river into a separate
 * {@link Channel} at every confluence. Applying the dead band per channel would reset it at every
 * junction, so a trunk could change type at each confluence for no terrain reason — the scalloped
 * floodplain edge the dead band exists to prevent, reproduced at every junction in the world. Walking
 * from drains upstream lets each channel's downstream-most reach inherit its downstream neighbour's
 * upstream-most type.
 *
 * <p><b>Why per reach, not per point.</b> A transect walks across the channel and gets no cache locality,
 * and {@code collectUnits} resamples at a spacing that floors at 0.5 px — a detailed tile emits tens of
 * thousands of points. One transect per reach (Rosgen's own ~20-channel-width definition) is three orders
 * of magnitude cheaper and is what the classification scheme actually specifies.
 */
public final class ReachRosgenClassifier implements ChannelTyper {

    private final ReachMetricsSampler sampler;
    private final Map<Integer, RosgenType[]> typesByChannelId = new HashMap<>();

    /**
     * @param elev     <b>raw</b> decoded elevation, {@code gridSize²} — never a carved buffer
     * @param gridSize side of the (padded) square field
     */
    public ReachRosgenClassifier(float[] elev, int gridSize) {
        this.sampler = new ReachMetricsSampler(elev, gridSize);
    }

    @Override
    public void prepare(RiverNetwork network) {
        typesByChannelId.clear();
        for (Channel ch : orderDownstreamFirst(network)) {
            typesByChannelId.put(ch.channelId, classifyChannel(ch, seedFor(ch, network)));
        }
    }

    @Override
    public RosgenType[] typesFor(Channel channel) {
        final RosgenType[] cached = typesByChannelId.get(channel.channelId);
        if (cached != null && cached.length == channel.numPts()) return cached;
        // A channel resampled after prepare(), or one absent from the prepared network: classify it
        // standalone rather than returning a mis-sized array.
        return classifyChannel(channel, null);
    }

    /**
     * Channels ordered so that every channel appears after the channel it flows into: a BFS from the
     * drains upstream over the single-outflow in-tree, emitted in poll order.
     *
     * <p>No reversal is needed or wanted. Seeding the frontier with the DRAIN-adjacent channels and
     * expanding through {@code startNode.incoming} visits a channel before anything feeding it, which is
     * already the ordering contract. Reversing would invert it and break {@link #seedFor}, which reads the
     * downstream neighbour's committed types out of {@code typesByChannelId} and therefore requires that
     * neighbour to have been classified first.
     */
    private static List<Channel> orderDownstreamFirst(RiverNetwork network) {
        final List<Channel> order = new ArrayList<>();
        final ArrayDeque<Channel> frontier = new ArrayDeque<>();
        final Map<Integer, Boolean> seen = new HashMap<>();

        for (Endpoint node : network.getNodes()) {
            if (node.type != Endpoint.Type.DRAIN) continue;
            for (int incomingId : node.incoming) {
                final Channel ch = network.getChannel(incomingId);
                if (ch != null && seen.putIfAbsent(incomingId, Boolean.TRUE) == null) frontier.add(ch);
            }
        }
        // Any channel not reachable from a drain (a dangling branch) still needs a type. Sorted by id:
        // getChannels() is a view over a HashMap, and unlike the drain-rooted expansion below — where a
        // channel is always polled after the channel it flows into, whatever order its siblings arrive in
        // — a dangling branch can be classified before its own downstream neighbour, so this order
        // reaches the output through seedFor. RiverNetwork.viewAtomic and detectCrossings sort for the
        // same reason.
        final List<Channel> unreached = new ArrayList<>(network.getChannels());
        unreached.sort(Comparator.comparingInt(ch -> ch.channelId));
        for (Channel ch : unreached) {
            if (seen.putIfAbsent(ch.channelId, Boolean.TRUE) == null) frontier.add(ch);
        }
        while (!frontier.isEmpty()) {
            final Channel ch = frontier.poll();
            order.add(ch);
            final Endpoint start = network.getNode(ch.startNodeId);
            if (start == null) continue;
            for (int incomingId : start.incoming) {
                final Channel upstream = network.getChannel(incomingId);
                if (upstream != null && seen.putIfAbsent(incomingId, Boolean.TRUE) == null) frontier.add(upstream);
            }
        }
        return order;
    }

    /**
     * The type the downstream neighbour committed at its upstream end, or {@code null} when this channel
     * flows straight into a drain.
     */
    private RosgenType seedFor(Channel ch, RiverNetwork network) {
        final Endpoint end = network.getNode(ch.endNodeId);
        if (end == null || end.outgoing == -1) return null;
        final RosgenType[] downstream = typesByChannelId.get(end.outgoing);
        return (downstream == null || downstream.length == 0) ? null : downstream[0];
    }

    /**
     * One type per spline point. Reaches are cut at {@code min(REACH_WIDTHS · width, REACH_MAX_PX)} of
     * arc length, measured at the reach midpoint, and walked downstream-to-upstream so the dead band
     * flows the same direction as the graph walk.
     */
    private RosgenType[] classifyChannel(Channel ch, RosgenType seed) {
        final int n = ch.numPts();
        final RosgenType[] types = new RosgenType[n];
        if (n == 0) return types;

        final List<int[]> reaches = segment(ch);
        RosgenType previous = seed;
        for (int r = reaches.size() - 1; r >= 0; r--) {
            final int[] reach = reaches.get(r);
            final ReachMetrics metrics = measure(ch, reach[0], reach[1]);
            final RosgenType committed = RosgenKey.applyDeadBand(metrics, RosgenKey.classify(metrics), previous);
            for (int i = reach[0]; i <= reach[1]; i++) types[i] = committed;
            previous = committed;
        }
        // Any point no reach covered stays null. segment() is meant to cover every index, so a null here
        // is a segmentation gap; leaving it null keeps it visible (white in the type PNG, and never
        // counted as an A during calibration) rather than fabricating a type the terrain never produced.
        return types;
    }

    /** Inclusive {@code [from, to]} index pairs, cut at the reach length in arc length. */
    private static List<int[]> segment(Channel ch) {
        final List<int[]> reaches = new ArrayList<>();
        final List<double[]> pts = ch.spline.points();
        final int n = pts.size();
        if (n < 2) {
            reaches.add(new int[] {0, Math.max(0, n - 1)});
            return reaches;
        }
        int from = 0;
        double accumulated = 0.0;
        for (int i = 1; i < n; i++) {
            accumulated += VectorOps.distance(pts.get(i - 1), pts.get(i));
            if (accumulated >= reachLength(ch.widthAt(i))) {
                reaches.add(new int[] {from, i});
                from = i;
                accumulated = 0.0;
            }
        }
        if (from < n - 1) reaches.add(new int[] {from, n - 1});
        else if (reaches.isEmpty()) reaches.add(new int[] {0, n - 1});
        return reaches;
    }

    /** Reach length (native px): Rosgen's 20 channel widths, capped so it cannot span a whole tile. */
    private static double reachLength(double width) {
        return Math.min(HydrologyTuning.REACH_WIDTHS * width, HydrologyTuning.REACH_MAX_PX);
    }

    /** Measure one reach at its midpoint. One transect per reach — see the class javadoc. */
    private ReachMetrics measure(Channel ch, int from, int to) {
        final List<double[]> pts = ch.spline.points();
        final int mid = (from + to) / 2;
        final double width = ch.widthAt(mid);

        double arcLength = 0.0;
        for (int i = from + 1; i <= to; i++) arcLength += VectorOps.distance(pts.get(i - 1), pts.get(i));

        final double bedElev;
        final double slope;
        if (ch.bedElevations != null && ch.bedElevations.length == pts.size()) {
            bedElev = ch.bedElevations[mid];
            slope = ReachMetricsSampler.slope(ch.bedElevations, arcLength, from, to);
        } else {
            // Removed features (oxbows, abandoned rivers) carry no beds. Sea level with zero slope keeps
            // them out of the steep branches; their geometry is not a channel profile anyway.
            bedElev = 0.0;
            slope = 0.0;
        }

        // spline.normal never returns null: VectorOps.normalize returns a zero vector, not null, when the
        // tangent degenerates (duplicate consecutive spline points), and perpendicular preserves that. A
        // zero normal makes the transect resample the same pixel at every step, so it must be caught
        // here rather than walked.
        // TODO: 1.0 is a placeholder. ER = 1 means fully entrenched, which sends a degenerate reach to
        // F/G — visible types, deliberately, so the case shows up in the type PNG instead of hiding in
        // the C/E majority. Once the type mix is calibrated (P1), decide whether a degenerate reach
        // should instead inherit its downstream neighbour's type or be dropped from classification.
        final double[] normal = ch.spline.normal(mid);
        final double entrenchment = isDegenerate(normal)
                ? DEGENERATE_ENTRENCHMENT
                : sampler.entrenchmentRatio(pts.get(mid), normal, bedElev, width);

        return new ReachMetrics(slope, entrenchment, ChannelGeometry.widthDepthRatio(width), width, bedElev);
    }

    /** Entrenchment reported for a reach whose centreline tangent degenerates. See the TODO in {@link #measure}. */
    private static final double DEGENERATE_ENTRENCHMENT = 1.0;

    /** Squared length below which a normal counts as degenerate rather than a unit vector. */
    private static final double DEGENERATE_NORMAL_EPS_SQ = 1e-12;

    /** Whether the centreline tangent degenerated, leaving a zero-length normal instead of a unit one. */
    private static boolean isDegenerate(double[] normal) {
        return normal[0] * normal[0] + normal[1] * normal[1] < DEGENERATE_NORMAL_EPS_SQ;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test --tests "me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifierTest"
```

Expected: 5 tests, all PASS.

If `reclassifyingAfterAResampleIsDeterministic` fails, do **not** widen it — a second resample changing
the types is the real defect it exists to catch, and it means the carve and the persisted index disagree.

The null guards in `orderDownstreamFirst` and `seedFor` are load-bearing: `RiverNetwork.getChannel(int)` and `getNode(int)` are plain `Map.get` calls (`RiverNetwork.java:880`, `:888`) and return `null` for an id that was pruned. `Endpoint.incoming` is a `Set<Integer>`, so the enhanced-for unboxes.

- [ ] **Step 6: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/ChannelTyper.java src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachRosgenClassifier.java src/test/java/me/batata_1/fractal_terrain/hydrology/rosgen/ReachRosgenClassifierTest.java
git commit -m "feat(rosgen): add reach segmentation and graph-order classifier

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Stamp the type — and the feature kind — in `RiverNetwork.collectUnits`

Replaces the `// TODO: change this to the correct type` at `RiverNetwork.java:853-854`.

**A Rosgen type only applies to a river reach.** Every live unit today is stamped
`HydrologicalFeature.RIVER` (`RiverNetwork.java:806`) even though the enum has carried `SOURCE`, `DRAIN`
and `WATERFALL` since it was written and nothing has ever emitted them — a grep over `src/main` and
`src/test` finds `RIVER`, `ABANDONED_RIVER` and `OXBOW_LAKE` only. The graph already knows the
difference: `Endpoint.Type` is `{SOURCE, DRAIN, JUNCTION}`, so a channel's first point sits on a source
whenever `getNode(startNodeId).type == SOURCE`, and its last on a drain whenever
`getNode(endNodeId).type == DRAIN`. This task stamps those two points with their real kind and gives
them **no** Rosgen type: a spring and a river mouth are network endpoints, not reaches, and there is no
20-width window to measure either of them over.

For the same reason the `A` fallback goes away. `rosgenType` is already nullable end to end — it
serialises as `-1` and reads back as `null` (`HydrologicalUnit.java:177,195`), and all four consumers
(`HydrologicalUnit.getRadius:102`, `HydrologyProfile:42`, `HydrologyProfileCarver:174`,
`Infinite3DVisualizer:211`) already coalesce `null` to `A`. So stamping `null` changes no carve
behaviour, but it stops the index asserting that an oxbow lake, a spring, or an unclassified reach *is*
a steep entrenched headwater — which would otherwise be indistinguishable from a genuine `A` in the
type PNG and in any later calibration count.

Grouping is unaffected: a channel stays **one** feature with one `nextFeatureId`, because
`HydrologicalUnit.id()` is what the carve/paint query groups by, not `type()`. Only the debug
visualizer reads `type()` at all (`HydrologyUnitVisualizer.java:110,111,152`).

**Files:**

- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java:770-862`

**Interfaces:**

- Consumes: `ChannelTyper` (Task 5).
- Produces: `collectUnits(int, double, double, int[], ChannelTyper)` and `collectUnits(int, double, double, int[], IntPredicate, ChannelTyper)`. The 4-argument overload keeps its signature and delegates with a `null` typer, so `GlobalRiverGoldenTest` and any other current caller keep compiling. The existing 5-argument overload — `(int, double, double, int[], IntPredicate)`, filtered and untyped — is *replaced*, not kept: the new 5-argument form shares its arity but takes a `ChannelTyper` and applies no filter. That is safe only because nothing outside `RiverNetwork` calls the filtered form; its sole caller is the 4-argument method's own delegation, which this task rewrites. Confirm with a grep over `src/main` and `src/test` before starting.

- [ ] **Step 1: Restructure `collectUnits` into resample → prepare → emit**

In `RiverNetwork.java`, replace the body of the filtered `collectUnits` overload (currently at `:785-829`) so channels are resampled first, the typer is prepared once, then units are emitted:

```java
    public List<HydrologicalUnit> collectUnits(
            int time,
            double offsetX,
            double offsetZ,
            int[] nextFeatureId,
            IntPredicate channelIdFilter,
            @Nullable ChannelTyper typer) {
        final List<HydrologicalUnit> units = new ArrayList<>();

        // Phase 1: resample every emitting channel. Types depend on neighbouring channels, so every
        // channel must hold its final geometry before any of them is classified.
        final List<Channel> emitting = new ArrayList<>();
        for (Channel ch : channels.values()) {
            if (!channelIdFilter.test(ch.channelId)) continue;
            if (!ch.isResampleable()) continue; // degenerate geometry (too few points or NaN): skip
            // Spacing must be <= half the NARROWEST (intake) derived width, so consecutive units'
            // width/2 discs always overlap (gap-free membership test + girth rendering).
            final double dx = Math.max(ch.intakeWidth() / 2.0, MIN_CONVERT_SPACING);
            try {
                ch.reSample(dx);
            } catch (RuntimeException runaway) {
                // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); add no units.
                continue;
            }
            emitting.add(ch);
        }

        // Phase 2: one classification pass over the whole graph.
        if (typer != null) typer.prepare(this);

        // Phase 3: emit.
        for (Channel ch : emitting) {
            addFeatureUnits(
                    units,
                    ch.spline,
                    ch.bedElevations,
                    ch.flow, // per-point flow -> per-point derived width (natural taper)
                    0.0,
                    HydrologicalFeature.RIVER,
                    featureKinds(ch),
                    typer == null ? null : typer.typesFor(ch),
                    time,
                    offsetX,
                    offsetZ,
                    nextFeatureId);
        }

        // Removed features (oxbow lakes, abandoned rivers) are emitted unchanged and untyped: they carry
        // only a scalar width and no bed elevations, so there is no reach profile to classify.
        for (RemovedPath rp : removedPaths) {
            final QuinticHermiteSpline spline = QuinticHermiteSpline.createCatmullRom(rp.pts());
            if (!spline.isResampleable()) continue; // degenerate geometry (too few points or NaN): skip
            final double dx = Math.max(rp.width() / 2.0, MIN_CONVERT_SPACING);
            final QuinticHermiteSpline resampled;
            try {
                resampled = spline.reSample(dx);
            } catch (RuntimeException runaway) {
                // Pathological runaway geometry (spline exceeds MAX_SPLINE_LENGTH); add no units.
                continue;
            }
            addFeatureUnits(
                    units,
                    resampled,
                    null,
                    null,
                    rp.width(),
                    rp.type(),
                    null,
                    null,
                    rp.time(),
                    offsetX,
                    offsetZ,
                    nextFeatureId);
        }
        return units;
    }
```

- [ ] **Step 2: Add the delegating overloads**

```java
    /** {@link #collectUnits(int, double, double, int[], IntPredicate, ChannelTyper)} over every channel. */
    public List<HydrologicalUnit> collectUnits(
            int time, double offsetX, double offsetZ, int[] nextFeatureId, @Nullable ChannelTyper typer) {
        return collectUnits(time, offsetX, offsetZ, nextFeatureId, channelId -> true, typer);
    }

    /** Untyped collection — every unit falls back to {@link RosgenType#A}. */
    public List<HydrologicalUnit> collectUnits(int time, double offsetX, double offsetZ, int[] nextFeatureId) {
        return collectUnits(time, offsetX, offsetZ, nextFeatureId, channelId -> true, null);
    }
```

- [ ] **Step 3: Stamp the kind and the type in `addFeatureUnits`**

Add `@Nullable HydrologicalFeature[] kinds` and `@Nullable RosgenType[] types` parameters after
`type`, and replace the hardcoded Rosgen type:

```java
    private static void addFeatureUnits(
            List<HydrologicalUnit> out,
            QuinticHermiteSpline spline,
            double[] bedElevations,
            double[] flow,
            double fallbackWidth,
            HydrologicalFeature type,
            @Nullable HydrologicalFeature[] kinds,
            @Nullable RosgenType[] types,
            int time,
            double offsetX,
            double offsetZ,
            int[] nextFeatureId) {
        final List<double[]> pts = spline.points();
        final int n = pts.size();
        final int featureId = nextFeatureId[0]++;
        for (int i = 0; i < n; i++) {
            final double[] p = pts.get(i);
            // Per-point derived width (natural taper) when flow is present; else the scalar fallback.
            final double w = (flow != null) ? HydrologyTuning.widthFromFlow(flow[i]) : fallbackWidth;
            final double bed = (bedElevations != null) ? bedElevations[i] : 0;
            final double[] nrm = spline.normal(i);
            // A null or short kinds array means every point shares the feature's single kind, which is
            // the case for removed paths.
            final HydrologicalFeature kind = (kinds != null && i < kinds.length) ? kinds[i] : type;
            // Rosgen classifies stream reaches, so only RIVER points carry a type. A source and a drain
            // are network endpoints with no reach to measure; a removed path has no bed profile; and a
            // null typer means the caller collected untyped. All three stamp null, which every
            // RosgenProfile consumer already coalesces to A -- unlike stamping A here, which would make
            // "not classified" indistinguishable from "measured as a steep entrenched headwater".
            final RosgenType rosgen =
                    (kind == HydrologicalFeature.RIVER && types != null && i < types.length) ? types[i] : null;
            out.add(new HydrologicalUnit(
                    kind,
                    rosgen,
                    new double[] {p[0] - offsetX, p[1] - offsetZ},
                    new double[] {nrm[0], nrm[1]},
                    w,
                    bed,
                    time,
                    featureId));
        }
    }
```

Add the imports `me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType`, `java.util.Arrays`
and `org.jetbrains.annotations.Nullable` if not already present.

- [ ] **Step 4: Add the `featureKinds` helper**

Beside `addFeatureUnits`:

```java
    /**
     * Per-point feature kind for one live channel: {@link HydrologicalFeature#RIVER} throughout, except
     * that the first point becomes {@link HydrologicalFeature#SOURCE} when the channel starts at a
     * {@link Endpoint.Type#SOURCE} node and the last becomes {@link HydrologicalFeature#DRAIN} when it
     * ends at a {@link Endpoint.Type#DRAIN} node. Interior confluences stay {@code RIVER}: a
     * {@code JUNCTION} is a point on a river, not a distinct feature.
     *
     * <p>The channel is still emitted as a single feature under one id — {@link HydrologicalUnit#id()}
     * is what the carve/paint query groups by, and a source or drain is one point of the river it
     * belongs to, not a feature of its own.
     *
     * <p>Both node lookups tolerate {@code null}: {@code nodes} is a plain map and an id can have been
     * pruned, in which case the point keeps the {@code RIVER} default.
     */
    private HydrologicalFeature[] featureKinds(Channel ch) {
        final int n = ch.spline.points().size();
        final HydrologicalFeature[] kinds = new HydrologicalFeature[n];
        Arrays.fill(kinds, HydrologicalFeature.RIVER);
        if (n == 0) return kinds;
        final Endpoint start = nodes.get(ch.startNodeId);
        if (start != null && start.type == Endpoint.Type.SOURCE) kinds[0] = HydrologicalFeature.SOURCE;
        final Endpoint end = nodes.get(ch.endNodeId);
        if (end != null && end.type == Endpoint.Type.DRAIN) kinds[n - 1] = HydrologicalFeature.DRAIN;
        return kinds;
    }
```

`WATERFALL` stays unemitted — nothing in the pipeline detects one, and inventing a threshold for it is
not this plan's job.

- [ ] **Step 5: Update the `collectUnits` javadoc**

Replace the paragraph describing the unfiltered/filtered split with one that also documents the typer
and the kinds: state that classification runs once per call between resampling and emission; that a
`null` typer leaves every unit's `rosgenType` `null`, which consumers coalesce to `A`; that a channel's
endpoint points are emitted as `SOURCE`/`DRAIN` rather than `RIVER` and carry no Rosgen type; and that
removed features are never typed because they carry no bed elevations. The class javadoc's line about
"a graph channel's RIVER units" (`:775`) needs the same correction — a graph channel now emits `SOURCE`
and `DRAIN` units too, and the filter applies to all of them alike.

- [ ] **Step 6: Verify the suite has not regressed**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" test
```

Expected: compiles; failure count is still 8 (the documented baseline). If any *new* test fails, or the count rises above 8, stop and fix before committing.

`SpatialIndexCorrectnessGoldenTest` builds its own units directly (`:53`) rather than through
`collectUnits`, so it is unaffected by the kind/type change. Any golden that captured a checksum over
`collectUnits` output *will* shift, because `rosgenType` is part of `HydrologicalUnit.serialize` and
`equals` — check whether the moved checksum is one of the 8 already-red cases before assuming it is new
breakage.

- [ ] **Step 7: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java
git commit -m "feat(hydrology): stamp Rosgen type and endpoint kind on units in collectUnits

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Wire it up — `Meanders`, `GlobalNetworkBuilder`, `LocalRiverProvider`

These three must land together or nothing compiles.

**Files:**

- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Meanders.java:43-74` and its delegation section
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/GlobalNetworkBuilder.java:194-214`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java:203-268`

**Interfaces:**

- Consumes: `ReachRosgenClassifier` (Task 5), `RiverNetwork.collectUnits(…, ChannelTyper)` (Task 6).
- Produces: `public List<HydrologicalUnit> Meanders.collectUnits(int time, double offsetX, double offsetZ, int[] nextFeatureId)`.

- [ ] **Step 1: Add the elevation field and constructor to `Meanders`**

Add the field beside `gradX`/`gradZ` and a new primary constructor. **Keep the existing 5- and 7-argument constructors delegating with `null`** — six of the eight `new Meanders(...)` call sites are in `MeandersTest` and `MeandersGoldenTest`, and `MeandersGoldenTest` already has two baseline failures you must not entangle.

```java
    /**
     * Raw decoded elevation ({@code gridSize²}, row-major {@code x * gridSize + z} — the same layout and
     * frame as {@link #gradX}/{@link #gradZ}), used to classify Rosgen types in {@link #collectUnits}.
     * {@code null} when the simulation was constructed without one, in which case {@code collectUnits}
     * refuses to run rather than classifying against zeros.
     *
     * <p>Must be a snapshot taken <b>before</b> any carve: {@code HydrologyProfileCarver.carveRiverShells}
     * writes in place and compounds across calls, so a carved buffer would report the carve's own
     * floodplain constants as though they were terrain.
     */
    private final float[] elev;

    public Meanders(
            int gridSize,
            float[] gradX,
            float[] gradZ,
            float[] elev,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs,
            boolean savePreviousStates,
            int maxSavedStates) {
        if (elev != null && elev.length != gridSize * gridSize) {
            throw new IllegalArgumentException("elev length " + elev.length + " != gridSize² " + (gridSize * gridSize));
        }
        this.gridSize = gridSize;
        this.gradX = gradX;
        this.gradZ = gradZ;
        this.elev = elev;
        this.maxMigrationMagnetude = INF;
        this.network = new RiverNetwork(
                gridSize, nodeSpecs, edgeSpecs, savePreviousStates, maxSavedStates, HydrologyTuning.DX);
    }
```

Rewrite the two existing constructors to delegate: the 5-arg form (`Meanders.java:51-58`) calls the 8-arg with `elev = null, savePreviousStates = false, maxSavedStates = 0`; the 7-arg form (`:60-74`, whose body becomes the 8-arg's) calls it with `elev = null`.

Then add the 6-arg convenience form — this is the constructor both `GlobalNetworkBuilder` sites use in Step 3:

```java
    /**
     * The production construction path: the 5-argument form plus the raw elevation raster
     * {@link #collectUnits} classifies against. Builds a network that keeps no history.
     */
    public Meanders(
            int gridSize,
            float[] gradX,
            float[] gradZ,
            float[] elev,
            List<RiverNetwork.NodeSpec> nodeSpecs,
            List<RiverNetwork.EdgeSpec> edgeSpecs) {
        this(gridSize, gradX, gradZ, elev, nodeSpecs, edgeSpecs, false, 0);
    }
```

- [ ] **Step 2: Add `collectUnits` to the delegation section**

At the end of the "Graph delegation" section:

```java
    /**
     * The network's {@link HydrologicalUnit}s with a Rosgen type on every one.
     *
     * <p>Classification lives here rather than in {@link RiverNetwork} because this is the only object
     * holding both the graph and the raster the classifier needs, and it must happen inside unit
     * collection: the type selects the unit's {@code RosgenProfile}, which sets
     * {@code riverInfluence} — the unit's own R-tree membership radius — so the type has to exist before
     * {@code carveRiverShells} builds its index over these units.
     */
    public List<HydrologicalUnit> collectUnits(int time, double offsetX, double offsetZ, int[] nextFeatureId) {
        if (elev == null) {
            throw new IllegalStateException("collectUnits needs the raw elevation raster; construct Meanders with it");
        }
        return network.collectUnits(time, offsetX, offsetZ, nextFeatureId, new ReachRosgenClassifier(elev, gridSize));
    }
```

Import `me.batata_1.fractal_terrain.hydrology.HydrologicalUnit` and `me.batata_1.fractal_terrain.hydrology.rosgen.ReachRosgenClassifier`.

- [ ] **Step 3: Clone the raw elevation in `GlobalNetworkBuilder`**

The clone goes **above** the `if (edgeSpecs.isEmpty())` check at `:194`, not beside the gradient clones at `:202-203`: that branch builds a `Meanders` and returns at `:198`, so anything declared at `:202` is out of scope for it. Both `new Meanders(...)` sites take `rawElev` — a `Meanders` without it throws from `collectUnits` (Step 2), and `LocalRiverProvider.buildTile` calls `collectUnits` unconditionally, so every tile whose owned cells contain no global-river edges would crash on the routine path.

Replace `:194-211` with:

```java
        // Raw elevation snapshot for Rosgen classification. base[0] is the buffer carveRiverShells
        // mutates in place, so the snapshot is taken here -- before the first assign and first carve --
        // or entrenchment reads the carve's own floodplain instead of the terrain. It sits above the
        // empty-network return so both Meanders construction sites receive it.
        final float[] rawElev = base[0].clone();

        if (edgeSpecs.isEmpty()) {
            final Meanders empty = new Meanders(
                    PADDED, new float[PADDED * PADDED], new float[PADDED * PADDED], rawElev, nodeSpecs, edgeSpecs);
            clearBuildState(cells, nodeSpecs, edgeSpecs, centerIdx, edgeNodeIdx);
            return new Result(empty, boundaryElevByNodeIdx);
        }

        // Border confinement is now handled by the Meanders migration (per-channel, width-scaled).
        final float[] gradX = base[2].clone();
        final float[] gradZ = base[3].clone();

        // Relaxation steps vary with the elevation of the tile's primary owned cell (2*tileCoords):
        // higher terrain gets more steps, capped at MAX_RELAX_STEPS.
        final CellInfo primaryCell = cells.get(cellKey(tileX * 2, tileZ * 2));
        final double primaryElev = (primaryCell != null) ? grp.getElevation(primaryCell.ccx(), primaryCell.ccz()) : 0.0;
        final int relaxSteps = MIN_RELAX_STEPS + (int) Math.round(Math.max(0.0, primaryElev) * RELAX_STEPS_PER_ELEV);

        final Meanders sim = new Meanders(PADDED, gradX, gradZ, rawElev, nodeSpecs, edgeSpecs);
```

Both sites resolve to the 6-arg convenience constructor from Step 1, so a tile with no edges still constructs a simulation whose `collectUnits` works.

- [ ] **Step 4: Route the three `collectUnits` call sites through `Meanders`**

In `LocalRiverProvider.buildTile`, keep the `Meanders` reference that `:210` currently discards:

```java
        final GlobalNetworkBuilder.Result globalResult = GlobalNetworkBuilder.build(tileX, tileZ, base, grp);
        final Meanders sim = globalResult.network();
        final RiverNetwork network = sim.getNetwork();
```

Then replace `network.collectUnits(...)` with `sim.collectUnits(...)` at all three sites (`:217`, `:249`, `:252`). Import `me.batata_1.fractal_terrain.hydrology.meanders.Meanders`.

- [ ] **Step 5: Update the `buildTile` javadoc**

Add a bullet to the ordering list recording that every `collectUnits` call classifies, that all three agree because they read the same pre-carve snapshot, and that the type must exist before the carve because it sets the unit's influence radius.

- [ ] **Step 6: Verify the suite has not regressed**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" build
```

Expected: BUILD SUCCESSFUL through `compileJava`, `compileClientJava` and `spotlessCheck`; test failure count still 8.

- [ ] **Step 7: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add src/main/java/me/batata_1/fractal_terrain/hydrology/
git commit -m "feat(hydrology): classify Rosgen types during unit collection

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: Slope histogram, type visualisation, and docs

Slope calibration must come before judging any other threshold, because the key tests slope first. This task produces the evidence needed for it.

**Files:**

- Modify: `src/main/java/me/batata_1/fractal_terrain/debug/tests/LocalRiverTest.java:103-119`
- Modify: `src/main/java/me/batata_1/fractal_terrain/debug/HydrologyUnitVisualizer.java:54-90`
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileCarver.java` (javadoc only)

- [ ] **Step 1: Dump the slope distribution from `LocalRiverTest`**

Read `LocalRiverTest.java` first to match its existing dump idiom. Add a pass that walks every channel of the built network, computes the reach slopes exactly as `ReachRosgenClassifier` does, and prints percentiles:

```java
    /**
     * Prints the along-channel reach-slope distribution. Rosgen's published slope bands (0.02 / 0.04 /
     * 0.10) are real-world channel slopes; this world's relief is vertically exaggerated relative to its
     * horizontal run, so copying them classifies most of the world as Aa+. Place S_A and S_AA at
     * percentiles of this distribution instead -- matching the shape matters more than the numbers.
     */
    private static void dumpSlopeHistogram(RiverNetwork network) {
        final List<Double> slopes = new ArrayList<>();
        for (Channel ch : network.getChannels()) {
            if (ch.bedElevations == null || ch.numPts() < 2) continue;
            final List<double[]> pts = ch.spline.points();
            double arc = 0.0;
            for (int i = 1; i < pts.size(); i++) arc += VectorOps.distance(pts.get(i - 1), pts.get(i));
            slopes.add(ReachMetricsSampler.slope(ch.bedElevations, arc, 0, ch.numPts() - 1));
        }
        if (slopes.isEmpty()) return;
        Collections.sort(slopes);
        for (double p : new double[] {0.50, 0.75, 0.90, 0.95, 0.99}) {
            final int idx = Math.min(slopes.size() - 1, (int) (p * slopes.size()));
            System.out.printf("slope p%02d = %.6f%n", (int) (p * 100), slopes.get(idx));
        }
    }
```

Call it after the tile build, alongside the existing PNG dumps.

- [ ] **Step 2: Colour the unit PNG by Rosgen type**

`HydrologyUnitVisualizer.see(List, name, gridSize, upscale)` already renders every unit twice — a
translucent girth disc then a solid point square — taking its colour from a private `colorFor(unit)`.
Add a type-coloured variant beside it.

In `HydrologyUnitVisualizer.java`, add the palette and a public entry point:

```java
    /**
     * Fixed palette for {@link #seeByRosgenType}, indexed by {@link RosgenType#ordinal()}. Held constant
     * across runs so two dumps of the same tile are directly comparable; the enum order is frozen by
     * unit serialization, so the mapping cannot drift.
     */
    private static final Color[] ROSGEN_PALETTE = {
        new Color(0xE6194B), // A   steep entrenched
        new Color(0x800000), // Aa  very steep
        new Color(0xF58231), // B   moderately entrenched
        new Color(0x3CB44B), // C   meandering, broad floodplain
        new Color(0xFFE119), // D   braided
        new Color(0x42D4F4), // DA  anastomosing
        new Color(0x4363D8), // E   narrow, deep, highly sinuous
        new Color(0x911EB4), // F   entrenched meandering
        new Color(0xA9A9A9), // G   entrenched gully
    };

    /** Rendered for a unit with no Rosgen type: a source, a drain, an oxbow, or an unclassified reach. */
    private static final Color UNCLASSIFIED = new Color(0xFFFFFF);

    /**
     * Colour for a unit's Rosgen type. A {@code null} type renders white rather than as {@code A}:
     * downstream code coalesces {@code null} to {@code A}, but this dump exists to show what was
     * actually measured, and white against the palette makes an unexpected run of unclassified reaches
     * obvious instead of hiding it inside the {@code A} count.
     */
    private static Color rosgenColor(HydrologicalUnit unit) {
        final RosgenType type = unit.rosgenType();
        return type == null ? UNCLASSIFIED : ROSGEN_PALETTE[type.ordinal()];
    }

    /**
     * As {@link #see(List, String, int, int)}, but each unit is coloured by its Rosgen type rather than
     * its feature kind. The visual regression check for classification: a river should show long runs of
     * one colour, changing at valley transitions. Colour changing every few pixels means the reach
     * segmentation or the dead band is not working.
     */
    public void seeByRosgenType(List<HydrologicalUnit> units, String name, int gridSize, int upscale) {
        render(units, name, gridSize, upscale, HydrologyUnitVisualizer::rosgenColor);
    }
```

Then extract the existing body of `see(List, …)` into
`private void render(List<HydrologicalUnit> units, String name, int gridSize, int upscale, Function<HydrologicalUnit, Color> palette)`,
replacing both `colorFor(unit)` calls with `palette.apply(unit)`, and make `see(List, …)` delegate:

```java
    public void see(List<HydrologicalUnit> units, String name, int gridSize, int upscale) {
        render(units, name, gridSize, upscale, this::colorFor);
    }
```

Add imports `java.util.function.Function` and `me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType`.
`colorFor` is `static` (`HydrologyUnitVisualizer.java:150`), so use `HydrologyUnitVisualizer::colorFor`.

Also give the two new feature kinds their own hues in `colorFor`'s switch (`:152-157`), which currently
lists `RIVER`/`ABANDONED_RIVER`/`OXBOW_LAKE` and sends everything else to the `default -> 0.60f` river
blue — leaving a source and a drain indistinguishable from the channel they terminate:

```java
                    case SOURCE -> 0.33f; // green
                    case DRAIN -> 0.00f; // red
```

`logStats` (`:110-111`) needs no change: it counts by `unit.type()`, so `SOURCE`/`DRAIN` appear as their
own rows automatically — one source per headwater channel and one drain per outlet is the expected count,
and a wildly different number means `featureKinds` is reading the wrong node.

Then call it from `LocalRiverTest.dumpUnitTree` (`LocalRiverTest.java:110-114`), beside the existing dump:

```java
            Debug.units.see(units, prefix + "06_units", GRID, 4);
            Debug.units.seeByRosgenType(units, prefix + "07_rosgen", GRID, 4);
            Debug.units.logStats(units, "tile (" + tx + "," + tz + ")");
```

- [ ] **Step 3: Run the harness and record the percentiles**

Run:

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" localRiverTest
```

Expected: the harness completes and prints five `slope pNN = …` lines. Record them in the commit message — they are the input to the P1 calibration below.

- [ ] **Step 4: Write the package CLAUDE.md**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/rosgen/CLAUDE.md`:

```markdown
# rosgen/

Rosgen Level-I stream classification: measures reach slope and entrenchment from the raw decoded
elevation, then assigns the type that `RosgenProfile` uses to prescribe carve geometry.

## Files

| File                         | What                                                                        | When to read                                                       |
| ---------------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `RosgenKey.java`             | The ordered decision key + published dead band. Pure, no raster or graph    | Changing type boundaries, understanding why an ordering test fires |
| `ReachMetrics.java`          | The measured tuple one reach is classified from                             | Adding a measured input, checking what is observable vs prescribed |
| `ReachMetricsSampler.java`   | Slope from bed elevations; entrenchment from perpendicular transects        | Transect cost, walk bounds, why the raw buffer is required         |
| `ReachRosgenClassifier.java` | Reach segmentation + downstream-first graph walk; implements `ChannelTyper` | Reach length, cross-junction dead band, classification ordering    |
```

- [ ] **Step 5: Fix the stale entries in the neighbouring CLAUDE.md files**

- `hydrology/CLAUDE.md`: add a `rosgen/` row to the Subdirectories table.
- `hydrology/profile/CLAUDE.md`: the `HydrologyProfile.java` row says "**Currently a no-op** — body commented out". It is not: the early `return elevAtPixel;` at `HydrologyProfile.java:38` is commented out, so the body runs. Correct the row. Also correct the `RosgenProfile.java` row's "Only type `A` overrides anything" if it no longer holds after P2.
- `HydrologyProfileCarver.java` class javadoc claims both carve passes run "over GLOBAL units only (local channels are never shell-carved)", but `LocalRiverProvider` collects unfiltered, so pass 2 carves local shells too. Fix the javadoc to match the code.
- `HydrologicalUnit.java` class javadoc opens "a river (with its Rosgen channel type), an abandoned river, or an oxbow lake" — after Task 6 it is also a source or a drain. Update that sentence, and rewrite the `RosgenType` paragraph (`:37-40`): `null` no longer means "unset", it means *this unit is not a river reach* (a source, a drain, or a removed feature). Keep the note that consumers coalesce `null` to `A`, and keep the "observable only as A vs. not-A" caveat until P2 lands real per-type profile overrides.

- [ ] **Step 6: Format and commit**

```bash
"C:\Users\jgdev\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat" spotlessApply
git add -A
git commit -m "feat(debug): dump reach-slope histogram and per-type channel PNG

Slope percentiles from one tile: p50=<...> p75=<...> p90=<...> p95=<...> p99=<...>

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## After the plan: P1 calibration and P2 profiles

These are follow-on work, not tasks in this plan — they need the visual evidence Task 8 produces.

**P1 — calibrate, in this order.**

1. **Slope first.** Using the percentiles from Task 8, set `S_A` and `S_AA` so the type mix looks right. Rosgen's own bands put most real reaches below 0.02, so match the *shape* of the distribution rather than the numbers. Every other threshold is downstream of this one, because the key tests slope first.
2. **`ChannelGeometry.W_REF` second**, visually: it is the width at which rivers should start looking wide and shallow, and it is the only knob on both the `E`↔`C` and `G`↔`F` splits.
3. **`K_BRAID` and `DELTA_ELEV` last** — they gate the two rarest types.

Do not try to derive any of these from a metres-per-pixel constant; the diffusion model's relief is not established as metrically faithful.

**P2 — make the types visible.** Give each `RosgenProfile` constant real `floodPlainLength` / `riverInfluence` / `bedDelta` / `floodPlainDelta` overrides. Two cautions:

- **`floodPlainLength` and `riverInfluence` change `HydrologicalUnit.getRadius()`**, which is the unit's R-tree membership circle. Types with wider floodplains change which units a query returns, so P2 perturbs spatial-index results, not just elevation. Re-run `spatialIndexBenchmark` and the per-type PNG after each override.
- **`A` currently overrides `floodPlainDelta` to return `1` in the band `(-0.75, -0.25)` when `width > 1`** — a one-sided elevation bump that reads like a debug marker rather than a profile. Decide whether it is intentional before treating it as the baseline other types are compared against.

**Braiding (`D`).** The key emits `D`; the geometry comes later, as noise-generated parallel threads inside `bedDelta` applied during `populateNoiseStep`. Braiding is texture, not topology, so it does not need `RiverNetwork` to represent parallel channels — which is fortunate, since `manageCollisions` prunes them.

**Blending between types.** Each unit carries its own type, so adjacent units of different types can be blended by interpolating their contributed elevations. Until that lands, `carveRiverShells` picks the single nearest unit, and the dead band in `RosgenKey` is the only thing preventing a scalloped floodplain edge at type boundaries — so it is load-bearing, not an optimisation.

**Validation that matters most.** After types are assigned, check that `E` reaches really are more sinuous than `B` reaches (`Channel.computeSinuosity` already exists). Sinuosity must never be a classifier *input* — it is an output of the meander relaxation, so feeding it back would let meander tuning decide the floodplain width, a loop with no external anchor — but it is the right independent check that the scheme produces something coherent. If `E` is not more sinuous than `B`, the meander relaxation needs a per-type target.
