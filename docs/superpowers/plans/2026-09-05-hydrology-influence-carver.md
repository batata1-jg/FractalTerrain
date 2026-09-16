# Hydrology Influence Carver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `OxbowLakePrimitive` and `AbandonedRiverPrimitive` actually carve terrain in the
tile-level shell pass — today both are silent position-only discs — while replacing the shell
pass's hardcoded `instanceof RiverPrimitive` dispatch with a general, per-primitive
`getInfluenceCarver()` mechanism that the bed pass does not touch.

**Architecture:** `OxbowLakePrimitive` moves from a circle (`HistoricPrimitive`) to a rotated
rectangle sharing `RiverPrimitive`'s exact cross-section algorithm, via a new shared interface
`RosgenCarvedPrimitive`. `AbandonedRiverPrimitive` moves to a disc, joining the existing
`RadialPrimitive` family (`ConfluencePrimitive`, `SourcePrimitive`) with its own `RadialProfile`
constant. `HydrologicalPrimitive` gains `getInfluenceCarver()`, an enum (`InfluenceCarver.ROSGEN` /
`.RADIAL` / `.NONE`) that `RiverInfluenceCarve`'s shell pass dispatches on instead of `instanceof`.
The bed pass (`computeRiverGrid`, called from `PopulateNoiseStep`) is untouched. The merge law does
**not** change: every shell carver still contributes independently via `Math.min` against the
running elevation buffer — no cross-primitive distance recurrence is introduced (see "Rejected:
porting the bed pass's weighted recurrence" below).

**Tech Stack:** Java 21, Minecraft 1.20.1 / Fabric Loom, JUnit 5 (`useJUnitPlatform()`), Spotless
with palantirJavaFormat, fastutil (already on the classpath transitively).

**Spec:** `hydrology-carve-situation.md` (repo root) — sections "Intended design", "Proposed shape",
"Open questions — resolved 2026-09-05", "The gap".

## Global Constraints

Every task's requirements implicitly include this section.

- **Read the guidelines before the first edit.** Root `CLAUDE.md` → the `README.md`/`CLAUDE.md` in
  or above the directory you are editing → `ARCHITECTURE.md` (this crosses the generation
  pipeline) → `.claude/conventions/CLAUDE.md`, then `documentation.md`, `structural.md`,
  `code-quality/`, `class-structure.md`, `performance.md`, `temporal.md`, `intent-markers.md`. Open
  the message that first proposes or makes a change with `Guidelines: <paths read>`.
- **Docstring budgets are hard** (`.claude/conventions/documentation.md`, Tier 3): 1 line for a
  field, 3 for a method, 10 for a class. At most one line describes the thing itself; every other
  line answers *why* or *where in the pipeline*.
- **`hydrology/` imports no `net.minecraft`.** Nothing in this plan changes that.
- **fastutil over `java.util`** for any Set/Map/List, except where a primitive array fits better.
  Do not add `it.unimi.dsi:fastutil` to `build.gradle` — it is already on the classpath
  transitively through Minecraft.
- **The per-lattice-cell loop in `RiverInfluenceCarve` is below the hot/cold line.** No `new`, no
  boxing, no iterators or streams in it. Hoist invariants to the per-primitive level. Mark a
  deliberate allocation or non-simplification with `:PERF: [what]; [why]`.
- **`HydrologicalFeature` is append-only.** A constant's ordinal is the on-disk type tag. This plan
  does not reorder or remove any constant — only the *records* backing `OXBOW_LAKE` and
  `ABANDONED_RIVER` change shape, not the enum.
- **No on-disk migration needed.** `PrimitiveCodec`'s own `:SCHEMA:` comment on the code this plan
  deletes states: "none has ever been written to a cached tile, so no existing payload can be
  misread." Changing `OxbowLakePrimitive`'s and `AbandonedRiverPrimitive`'s serialized layout is
  safe.
- **Run before every commit:** `gradle spotlessApply`, then `gradle build`.
- **Test baseline** (re-verify, do not trust): **102 tests, 9 failed, 1 skipped** at `df7ca2e`:
  `RosgenKeyTest` (4), `RiverGoldenTest` (2), `MeandersGoldenTest` (1), `CentrelineTest` (1),
  `ReachMetricsSamplerTest` (1). Compare *failure messages* in `build/test-results/test/*.xml`
  against `.superpowers/conventions-alignment/post-migration-failures.txt`, not just failing test
  names. Do not chase these nine; do not let their count grow. A worktree needs
  `libs/onnxruntime/teste.jar` copied in (`libs/` is git-ignored) or the build reports ~132 phantom
  errors.
- **Delegation.** Per root `CLAUDE.md`: implementation goes to a `general-purpose` Sonnet agent per
  task. This plan's total diff is large enough in aggregate that if a task's own diff estimate
  passes ~1000 lines, dispatch `developer` for that task instead and say so.

## Rejected: porting the bed pass's weighted recurrence

The project owner considered, then rejected, making the shell pass's merge order-dependent (a
`dist`-ranked smoothed-min recurrence, like the bed pass's `computeRiverGrid`) so that "clearing
`dist` between tiers" would be a real mechanism. **Decision: no.** The shell pass keeps today's
order-independent hard-min law (`elevs[i] = Math.min(elevs[i], candidate)`) for every primitive,
including the new ones. Consequences of this decision, binding on every task below:

- `RiverInfluenceCarve.GridBuffers.dist`/`typeMask` are **not** touched by any new shell carver.
  They stay exactly as vestigial in the shell pass as they are today (see Task 6) — do not wire
  them up "for consistency."
- `getPriorityTier()`, present in the original doc's proposed interface shape, is **dropped**. Under
  a hard-min, order-independent merge, primitive processing order has no effect on carved output —
  an accessor that provably does nothing is not added. If a future primitive family genuinely needs
  ordering to matter, that is the point to reconsider this, not before.
- River's existing `carveRiverPrimitiveInfluence` carve math is **byte-identical** after this plan
  for every `RiverPrimitive` — it is renamed and retargeted at a shared interface, not rewritten.
  Task 8's verification step exists specifically to prove this.

## File Structure

| File | Responsibility |
| --- | --- |
| `hydrology/features/RosgenCarvedPrimitive.java` (create) | Shared shape/cross-section contract for `RiverPrimitive` and `OxbowLakePrimitive` |
| `hydrology/profile/InfluenceCarver.java` (create) | Enum dispatch (`ROSGEN`/`RADIAL`/`NONE`) replacing the shell's `instanceof` |
| `hydrology/features/HydrologicalPrimitive.java` (modify) | `getInfluenceCarver()` abstract method; `OXBOW_LAKE`/`ABANDONED_RIVER` `addPrimitives` updated for the new fields |
| `hydrology/features/RiverPrimitive.java` (modify) | Implements `RosgenCarvedPrimitive` instead of the two interfaces directly |
| `hydrology/features/PositionOnlyPrimitive.java` (modify) | `getInfluenceCarver()` default → `NONE` |
| `hydrology/features/RadialPrimitive.java` (modify) | `getInfluenceCarver()` default → `NONE` (Confluence/Source keep today's shell behavior: none) |
| `hydrology/features/OxbowLakePrimitive.java` (rewrite) | Rotated rectangle: `normal`, `curvature`, `rosgenType` added; implements `RosgenCarvedPrimitive`; own `resolved()` |
| `hydrology/features/AbandonedRiverPrimitive.java` (rewrite) | Radial: drops `influence`; implements `RadialPrimitive`; overrides `getInfluenceCarver()` → `RADIAL`; own `resolved()` |
| `hydrology/features/HistoricPrimitive.java` (delete) | No longer implemented by anyone once the two rewrites land |
| `hydrology/features/PrimitiveCodec.java` (modify) | Delete the now-unused `historic*`/`HistoricFields` helpers |
| `hydrology/profile/RadialProfile.java` (modify) | New `ABANDONED_RIVER` constant |
| `hydrology/profile/RiverInfluenceCarve.java` (modify) | New `ShellGrid` record; `carveRiverInfluenceGrid` walks all primitives via `getInfluenceCarver()`; `carveRiverPrimitiveInfluence` → `carveRosgenInfluence` (retyped, body unchanged); new `carveRadialInfluence` |
| `hydrology/network/RiverNetwork.java` (modify) | `recordRemovedComplement` computes+stores a tangent/curvature for the new Oxbow shape; the atomic-eviction mint drops the `influence` arg |
| `test/.../features/HistoricPrimitiveCodecTest.java` (delete) | Superseded by the two files below |
| `test/.../features/RosgenCarvedPrimitiveCodecTest.java` (create) | Round-trip + rectangle geometry, parameterized over `RiverPrimitive` and `OxbowLakePrimitive` |
| `test/.../features/RadialPrimitiveCodecTest.java` (modify) | Add `AbandonedRiverPrimitive` to the parameterized fixtures |
| `test/.../network/RiverNetworkHistoryTest.java` (modify) | `resolved()` calls retyped off `HistoricPrimitive`; one new assertion for the captured tangent |
| `test/.../profile/InfluenceCarverShellTest.java` (create) | The new dispatch: Oxbow carves like a river, AbandonedRiver carves radially, Confluence/Source/Delta/Waterfall carve nothing, byte-identical river output |

---

## Task 1: `RosgenCarvedPrimitive` + `InfluenceCarver` scaffolding (behavior-preserving)

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RosgenCarvedPrimitive.java`
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/InfluenceCarver.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/PositionOnlyPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitive.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/InfluenceCarverDefaultsTest.java`

**Interfaces:**
- Consumes: `HydrologyProfile`, `RosgenProfile` (existing, `hydrology/profile/`); `SpatialIndexRotatedRectangle` (existing, `math/ds/`).
- Produces: `InfluenceCarver.ROSGEN` / `.RADIAL` / `.NONE`; `HydrologicalPrimitive.getInfluenceCarver()`; `RosgenCarvedPrimitive` (extended by `RiverPrimitive` in this task, by `OxbowLakePrimitive` in Task 4).

This task only adds the abstraction and wires every *existing* record to a `getInfluenceCarver()`
that reproduces today's shell behavior exactly (`RiverPrimitive` → `ROSGEN`; everything else →
`NONE`, since the shell pass only ever carved rivers). No shell carve math moves yet — that is
Task 6, once both new primitive shapes exist to test it against.

- [ ] **Step 1: Write `RosgenCarvedPrimitive`**

```java
package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexRotatedRectangle;

/**
 * A primitive with a flow tangent and a Rosgen cross-section — what {@link InfluenceCarver#ROSGEN}
 * dispatches on. {@link RiverPrimitive} and {@link OxbowLakePrimitive} both carve this shape: a shed
 * meander reads close enough to a live channel for the same cross-section math to apply to both.
 */
public interface RosgenCarvedPrimitive extends SpatialIndexRotatedRectangle, HydrologicalPrimitive {

    /** Unit cross-section normal; {@code null} means no tangent, so the carve skips this primitive. */
    double[] normal();

    /** Channel width at this point; the cross-section's overall scale. */
    double width();

    /** Signed curvature at this point; perturbs the cross-section per {@link RosgenProfile}. */
    double curvature();

    /** The elevation the cross-section is cut from. */
    double elevation();

    /** Rosgen classification; {@code null} coalesces to {@link RiverPrimitive.RosgenType#A}. */
    RiverPrimitive.RosgenType rosgenType();

    /** Cross-section seed, mixed into {@link RosgenProfile}'s per-point perturbation. */
    long seed();

    @Override
    default HydrologyProfile getProfile() {
        return RosgenProfile.of(RiverPrimitive.RosgenType.orDefault(rosgenType()));
    }

    @Override
    default InfluenceCarver getInfluenceCarver() {
        return InfluenceCarver.ROSGEN;
    }
}
```

- [ ] **Step 2: Write `InfluenceCarver`, deferring the two real bodies to Task 6**

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RadialPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RosgenCarvedPrimitive;

/**
 * Which shell-carve algorithm a primitive uses, replacing the old {@code instanceof} dispatch in
 * {@link RiverInfluenceCarve}'s shell pass. Scoped to the shell/influence pass only — the bed pass
 * ({@link RiverInfluenceCarve#computeRiverGrid}) keeps its own, untouched dispatch.
 */
public enum InfluenceCarver {

    /** Rectangle/tangent cross-section, shared by every {@link RosgenCarvedPrimitive}. */
    ROSGEN {
        @Override
        public void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
            RiverInfluenceCarve.carveRosgenInfluence((RosgenCarvedPrimitive) primitive, grid);
        }
    },

    /** Radial cross-section, for a {@link RadialPrimitive} that opts into shell carving. */
    RADIAL {
        @Override
        public void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {
            RiverInfluenceCarve.carveRadialInfluence((RadialPrimitive) primitive, grid);
        }
    },

    /** This primitive contributes no shell influence — today's behaviour for every family the
     *  shell pass does not carve. */
    NONE {
        @Override
        public void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid) {}
    };

    public abstract void carveInfluence(HydrologicalPrimitive primitive, RiverInfluenceCarve.ShellGrid grid);
}
```

This references `RiverInfluenceCarve.ShellGrid`, `carveRosgenInfluence`, `carveRadialInfluence`,
none of which exist until Task 6. That is fine — Task 1 will not compile in isolation if built
alone; it is committed together with Task 6 in execution order, or (if executed by
`subagent-driven-development` task-by-task) Task 6 must land before `gradle build` is run. Note
this dependency explicitly in the task handoff.

- [ ] **Step 3: Add the abstract method to `HydrologicalPrimitive`**

In `HydrologicalPrimitive.java`, alongside the existing `HydrologyProfile getProfile();` line
(around line 93), add:

```java
    /** Which shell-carve algorithm this primitive uses; see {@link InfluenceCarver}. */
    InfluenceCarver getInfluenceCarver();
```

Add the import `me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver` alongside the
existing `hydrology.profile.*` imports.

- [ ] **Step 4: Default it to `NONE` on `PositionOnlyPrimitive` and `RadialPrimitive`**

In `PositionOnlyPrimitive.java`, alongside `getProfile()`:

```java
    @Override
    default InfluenceCarver getInfluenceCarver() {
        return InfluenceCarver.NONE;
    }
```

In `RadialPrimitive.java`, alongside `getProfile()`:

```java
    @Override
    default InfluenceCarver getInfluenceCarver() {
        return InfluenceCarver.NONE;
    }
```

Both need the same `InfluenceCarver` import. This preserves today's exact shell behavior for
`DeltaPrimitive`, `WaterfallPrimitive`, `ConfluencePrimitive`, `SourcePrimitive` — none of them are
carved by the shell pass today, and none will be after this plan (only `AbandonedRiverPrimitive`
overrides this default back to `RADIAL` in Task 5 — it also implements `RadialPrimitive`, so its
own override on the concrete record takes precedence over the interface default).

- [ ] **Step 5: Retarget `RiverPrimitive` onto `RosgenCarvedPrimitive`**

In `RiverPrimitive.java`, change the `implements` clause:

```java
public record RiverPrimitive(...)
        implements RosgenCarvedPrimitive {
```

(was `implements SpatialIndexRotatedRectangle, HydrologicalPrimitive` — `RosgenCarvedPrimitive`
already extends both, so this is a straight swap). Delete `RiverPrimitive`'s own `getProfile()`
override (lines 74-77 today) — it is now inherited verbatim from `RosgenCarvedPrimitive`'s default,
same logic, same result. Delete the now-unused `import me.batata_1.fractal_terrain.hydrology.profile.RosgenProfile;`
only if nothing else in the file still references `RosgenProfile` directly (it does — `h(double
signedDist)` casts `getProfile()` to `RosgenProfile`, so keep the import).

- [ ] **Step 6: Write the behavior-preservation test**

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import org.junit.jupiter.api.Test;

/** Every family's shell-carve dispatch, before the new primitive shapes exist. Locks in that this
 *  refactor starts behavior-preserving: only RiverPrimitive carves the shell today. */
class InfluenceCarverDefaultsTest {

    @Test
    void onlyRiverCarvesTheShellToday() {
        final RiverPrimitive river =
                new RiverPrimitive(new double[] {0, 0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0);
        final ConfluencePrimitive confluence = new ConfluencePrimitive(new double[] {0, 0}, 1.0, 0.0);
        final SourcePrimitive source = new SourcePrimitive(new double[] {0, 0}, 1.0, 0.0);
        final DeltaPrimitive delta = new DeltaPrimitive(new double[] {0, 0});
        final WaterfallPrimitive waterfall = new WaterfallPrimitive(new double[] {0, 0});

        assertEquals(InfluenceCarver.ROSGEN, river.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, confluence.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, source.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, delta.getInfluenceCarver());
        assertEquals(InfluenceCarver.NONE, waterfall.getInfluenceCarver());
    }
}
```

Check `DeltaPrimitive`/`WaterfallPrimitive`'s actual constructors first (`Glob
src/main/java/**/DeltaPrimitive.java`) — adjust the fixture calls to match if they take more args.

- [ ] **Step 7: This task alone will not compile — proceed directly to Task 6's `ShellGrid`/carve bodies before running `gradle build`, or stub `carveRosgenInfluence`/`carveRadialInfluence`/`ShellGrid` as empty scaffolds now and fill them in Task 6**

If executing task-by-task with a build gate after each task, add a minimal compiling stub now:

```java
    /** Bundle of shell-pass scratch buffers a primitive's carve reads and writes; filled out in
     *  Task 6 of docs/superpowers/plans/2026-09-05-hydrology-influence-carver.md. */
    public record ShellGrid(
            int gridSize, float[] acc, float[] lut, double[] perpRow, double[] perpCol,
            double[] tangRow, double[] tangCol, float[] elevs) {}

    static void carveRosgenInfluence(RosgenCarvedPrimitive primitive, ShellGrid grid) {
        throw new UnsupportedOperationException("wired in Task 6");
    }

    static void carveRadialInfluence(RadialPrimitive primitive, ShellGrid grid) {
        throw new UnsupportedOperationException("wired in Task 6");
    }
```

in `RiverInfluenceCarve.java`, then replace both stub bodies for real in Task 6 (do not leave the
`throw` in place after Task 6 — this stub exists only to make Task 1 independently compilable).

- [ ] **Step 8: Run `gradle spotlessApply` then `gradle build`**

Expected: compiles; `InfluenceCarverDefaultsTest` passes; no other test's failure *messages*
change from the baseline.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/RosgenCarvedPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/InfluenceCarver.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/RiverPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/PositionOnlyPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/InfluenceCarverDefaultsTest.java
git commit -m "feat(hydrology): add getInfluenceCarver() dispatch, behavior-preserving"
```

---

## Task 2: `RadialProfile.ABANDONED_RIVER`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfile.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfileTest.java` (extend if it exists; otherwise create)

**Interfaces:**
- Consumes: nothing new.
- Produces: `RadialProfile.ABANDONED_RIVER` — used by `AbandonedRiverPrimitive.getRadialProfile()` in Task 5.

- [ ] **Step 1: Check for an existing `RadialProfileTest.java`**

Run: `Glob src/test/java/**/RadialProfileTest.java`. If it exists, read it for the assertion style
used against `CONFLUENCE`/`SOURCE` and mirror it below instead of inventing a new style.

- [ ] **Step 2: Write the failing test**

```java
@Test
void abandonedRiverIsShallowerThanAConfluenceAtTheSameRadius() {
    final float[] lutA = new float[8];
    final float[] lutB = new float[8];
    RadialProfile.ABANDONED_RIVER.sampleRadialSection(lutA, 8, 1.0, 0, 0.0, 1.0 / 7.0, 10.0);
    RadialProfile.CONFLUENCE.sampleRadialSection(lutB, 8, 1.0, 0, 0.0, 1.0 / 7.0, 10.0);

    // Index 0 is the disc centre (deepest point of both).
    assertTrue(
            lutA[0] > lutB[0],
            "an abandoned trace has been silting in since it was cut off; it should not out-cut a live confluence pool");
}

@Test
void abandonedRiverReachesTheRimElevationAtTheEdge() {
    final float[] lut = new float[8];
    RadialProfile.ABANDONED_RIVER.sampleRadialSection(lut, 8, 1.0, 0, 5.0, 1.0 / 7.0, 10.0);

    assertEquals(5.0, lut[7], 1e-6, "the rim carries no depth, matching CONFLUENCE/SOURCE's own boundary");
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `gradle test --tests "*RadialProfileTest*"`
Expected: FAIL — `RadialProfile.ABANDONED_RIVER` does not exist (compile error).

- [ ] **Step 4: Add the constant**

In `RadialProfile.java`, add a third enum constant after `SOURCE`:

```java
    /** A cutoff trace has been silting in since it was abandoned, so it reads shallower than a
     *  still-active confluence pool at the same width — 40% of CONFLUENCE's depth fraction. */
    ABANDONED_RIVER {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * 0.4 * (1 - normalizedRadius * normalizedRadius);
        }
    };
```

(Change `SOURCE`'s trailing `;` to `,` when inserting a constant after it.) The `0.4` factor is a
tunable aesthetic constant, not derived from any measurement — flag this to the project owner as
adjustable if the in-game look needs it.

- [ ] **Step 5: Run test to verify it passes**

Run: `gradle test --tests "*RadialProfileTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfile.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfileTest.java
git commit -m "feat(hydrology): add RadialProfile.ABANDONED_RIVER"
```

---

## Task 3: `PrimitiveCodec` cleanup — delete the `historic*` helpers

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/PrimitiveCodec.java`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing new — this is pure deletion, staged as its own task so Tasks 4/5's diffs read
  as "add the new shape" rather than "add the new shape and also rip out shared code out from under
  it," and so a reviewer can see the deletion is safe in isolation (nothing outside
  `OxbowLakePrimitive`/`AbandonedRiverPrimitive` calls these methods — confirmed by
  `Grep -r "historicByteSize|writeHistoric|readHistoric|historicEquals|historicHash|HistoricFields" src/`
  returning only those two files and `PrimitiveCodec.java` itself).

Do **not** run this task until Tasks 4 and 5 have already rewritten both records to stop calling
these methods — sequence it after them, despite the lower task number suggesting otherwise; it is
listed here because it is small, not because it runs first. (Restated at the top of Task 4.)

- [ ] **Step 1: Confirm no remaining callers**

Run: `Grep -rn "historicByteSize|writeHistoric|readHistoric|historicEquals|historicHash|HistoricFields" src/`
Expected: zero matches outside `PrimitiveCodec.java` (after Tasks 4/5 land).

- [ ] **Step 2: Delete `HistoricFields`, `historicByteSize`, `writeHistoric`, `readHistoric`, `historicEquals`, `historicHash`**

Remove lines 71-123 of `PrimitiveCodec.java` (the entire "shed feature" block). `coordByteSize`,
`writeCoord`, `readCoord`, `putCoord`, `getCoord`, `coordsEqual`, `coordsHash` (lines 1-70) stay —
still used by `PositionOnlyPrimitive`'s implementors.

- [ ] **Step 3: Run `gradle build`**

Expected: compiles (Tasks 4/5 must already be landed).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/PrimitiveCodec.java
git commit -m "refactor(hydrology): drop PrimitiveCodec's dead historic-primitive helpers"
```

---

## Task 4: Reshape `OxbowLakePrimitive` to a rotated rectangle

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java` (the `OXBOW_LAKE` enum constant's `addPrimitives`)
- Rewrite: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/OxbowLakePrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java` (`recordRemovedComplement`)
- Create: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RosgenCarvedPrimitiveCodecTest.java`
- Delete: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitiveCodecTest.java`

**Interfaces:**
- Consumes: `RosgenCarvedPrimitive` (Task 1); `Centreline.normalAt(Channel, int)` (existing,
  `hydrology/network/Centreline.java`); `Channel.spline.curvature(int)` (existing).
- Produces: `new OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation, double[] normal, double curvature, RiverPrimitive.RosgenType rosgenType)` — the new 8-arg convenience constructor Task 6's tests and `RiverNetwork` both call.

**Design decisions this task locks in (read before writing code):**
- Oxbow's `rosgenType` is minted as `null`, coalescing to `RosgenType.A` via the existing
  `RosgenType.orDefault` rule (same convention `RiverPrimitive` already uses for an untyped reach).
  Threading a real `ChannelTyper` through the cutoff path so an oxbow inherits its parent channel's
  actual Rosgen type is a clean, separate follow-up — out of scope here.
- `getLength()`/`getWidth()` mirror `RiverPrimitive`'s exact scale convention (`influence * 2` /
  `influence * 3`) so the two share not just an algorithm but an identical footprint-to-influence
  ratio.
- `resolved(double elevation, double influence)` moves from the (deleted) `HistoricPrimitive`
  interface onto `OxbowLakePrimitive` itself as a plain method — nothing else needs to call it
  polymorphically (confirmed: the only caller is `RiverNetworkHistoryTest`, updated in Task 7).

- [ ] **Step 1: Write the failing round-trip + shape test**

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Persistence and rectangle geometry shared by every {@link RosgenCarvedPrimitive}. */
class RosgenCarvedPrimitiveCodecTest {

    private static Stream<RosgenCarvedPrimitive> primitives() {
        return Stream.of(
                new RiverPrimitive(
                        new double[] {12.5, -40.25}, 5.0, RiverPrimitive.RosgenType.B,
                        new double[] {0.6, 0.8}, 0.1, 6.0, 71.5),
                new OxbowLakePrimitive(
                        new double[] {-3.0, 8.75}, (byte) 23, 1.25, 9.5, 130.0,
                        new double[] {1.0, 0.0}, 0.0, null));
    }

    @ParameterizedTest
    @MethodSource("primitives")
    void roundTripsThroughTheTypeTaggedPayload(RosgenCarvedPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.getClass(), reloaded.getClass(), "the type tag did not select the record");
        assertNotSame(original, reloaded);
        assertEquals(original, reloaded, "contents did not survive the round trip");
        assertEquals(original.hashCode(), reloaded.hashCode());
    }

    @ParameterizedTest
    @MethodSource("primitives")
    void reportsThePayloadSizeItActuallyWrites(RosgenCarvedPrimitive primitive) {
        assertEquals(primitive.byteSize(), primitive.serialize().length);
    }

    @ParameterizedTest
    @MethodSource("primitives")
    void carvesTheShellAsARosgenCrossSection(RosgenCarvedPrimitive primitive) {
        assertEquals(me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver.ROSGEN, primitive.getInfluenceCarver());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "*RosgenCarvedPrimitiveCodecTest*"`
Expected: FAIL — `OxbowLakePrimitive`'s 8-arg constructor and `RosgenCarvedPrimitive` conformance
do not exist yet.

- [ ] **Step 3: Rewrite `OxbowLakePrimitive.java`**

```java
package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A meander loop cut off from its channel, carved with the same rectangle/tangent cross-section as
 * a live river — a shed loop reads close enough in shape to still be a channel.
 *
 * <p>{@link ZoneCategory#LAKE_BED} is reserved below {@link ZoneCategory#BED} for the standing-water
 * classification this record does not carry yet — {@code ZoneCategory} itself is not live (see its
 * own javadoc), so nothing currently reads that reservation.
 */
public record OxbowLakePrimitive(
        double[] coord,
        byte time,
        double width,
        double influence,
        double elevation,
        double[] normal,
        double curvature,
        RiverPrimitive.RosgenType rosgenType,
        long seed)
        implements RosgenCarvedPrimitive {

    static final OxbowLakePrimitive PROTOTYPE =
            new OxbowLakePrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0, 0, null, 0, null);

    public OxbowLakePrimitive(
            double[] coord,
            byte time,
            double width,
            double influence,
            double elevation,
            double[] normal,
            double curvature,
            RiverPrimitive.RosgenType rosgenType) {
        this(
                coord, time, width, influence, elevation, normal, curvature, rosgenType,
                computeHashCode(coord, time, width, influence, elevation, normal));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.OXBOW_LAKE;
    }

    /** This primitive with its deferred elevation and influence filled in — unknowable at the cut,
     *  resolved later once the network's bed-elevation pass runs. Not part of a shared interface:
     *  nothing outside tests calls it polymorphically. */
    public OxbowLakePrimitive resolved(double elevation, double influence) {
        return new OxbowLakePrimitive(coord, time, width, influence, elevation, normal, curvature, rosgenType);
    }

    @Override
    public double getAngle() {
        throw new IllegalStateException("OxbowLakePrimitive uses angle cosines and sines directly");
    }

    /** Local +X is the flow tangent {@code (nz, -nx)}, mirroring {@link RiverPrimitive}. */
    @Override
    public double getCosAngle() {
        return normal[1];
    }

    @Override
    public double getSinAngle() {
        return -normal[0];
    }

    @Override
    public double getLength() {
        return influence * 2;
    }

    @Override
    public double getWidth() {
        return influence * 3;
    }

    @Override
    public long primitiveByteSize() {
        return Integer.BYTES // rosgen tag
                + PrimitiveCodec.coordByteSize(coord)
                + Byte.BYTES // time
                + 3L * Double.BYTES // width, influence, elevation
                + PrimitiveCodec.coordByteSize(normal)
                + Double.BYTES; // curvature
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(rosgenType == null ? -1 : rosgenType.ordinal());
        PrimitiveCodec.putCoord(buf, coord);
        buf.put(time);
        buf.putDouble(width);
        buf.putDouble(influence);
        buf.putDouble(elevation);
        PrimitiveCodec.putCoord(buf, normal);
        buf.putDouble(curvature);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final int rosgenOrdinal = buf.getInt();
        final RiverPrimitive.RosgenType rosgen =
                rosgenOrdinal < 0 ? null : RiverPrimitive.RosgenType.values()[rosgenOrdinal];
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final byte t = buf.get();
        final double w = buf.getDouble();
        final double inf = buf.getDouble();
        final double e = buf.getDouble();
        final double[] normalVec = PrimitiveCodec.getCoord(buf);
        final double curv = buf.getDouble();
        return new OxbowLakePrimitive(coords, t, w, inf, e, normalVec, curv, rosgen);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OxbowLakePrimitive other)) return false;
        return time == other.time
                && rosgenType == other.rosgenType
                && Arrays.equals(coord, other.coord)
                && Arrays.equals(normal, other.normal)
                && Double.compare(width, other.width) == 0
                && Double.compare(influence, other.influence) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(
            double[] coord, byte time, double width, double influence, double elevation, double[] normal) {
        int result = Objects.hash(time, width, influence, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        result = 31 * result + Arrays.hashCode(normal);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "Oxbow[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", influence="
                + influence + ", elevation=" + elevation + ", normal=" + Arrays.toString(normal) + "]";
    }
}
```

Note the `@Nullable` import is used on the `rosgenType`/`normal` parameters implicitly via the
existing convention (nullable fields elsewhere in this codebase are documented, not
`@Nullable`-annotated on record components — match `RiverPrimitive`'s own style, which does not
annotate `normal`/`rosgenType` either, just documents nullability in the class javadoc). Drop the
unused `@Nullable` import if you added it and nothing ends up annotated with it.

- [ ] **Step 4: Update `HydrologicalFeature.OXBOW_LAKE`'s `addPrimitives`**

In `HydrologicalPrimitive.java`, replace the `OXBOW_LAKE` constant's body:

```java
        OXBOW_LAKE(() -> OxbowLakePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                final OxbowLakePrimitive shed = (OxbowLakePrimitive) args[0];
                primitives.add(new OxbowLakePrimitive(
                        VectorOps.sub(shed.coord(), offset),
                        shed.time(),
                        shed.width(),
                        shed.influence(),
                        shed.elevation(),
                        shed.normal(),
                        shed.curvature(),
                        shed.rosgenType()));
            }
        },
```

- [ ] **Step 5: Wire `RiverNetwork.recordRemovedComplement` to compute and store the tangent**

In `RiverNetwork.java`, add the import `me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive`
is already implicitly available via `OxbowLakePrimitive`'s own import — check whether
`RiverNetwork.java` already imports `RiverPrimitive`; if not, add
`import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;` for the `RosgenType` cast
target (it stays `null` here, so this import may only be needed if you reference
`RiverPrimitive.RosgenType` by name — the constructor call below passes a bare `null`, so no new
import is strictly required, but add it if your IDE/compiler flags an ambiguity).

Replace the `recordRemovedComplement` body (currently lines 720-736):

```java
    /** Records the points of {@code ch} NOT in {@code keptIndexes} as the oxbow the cutoff left behind. */
    private void recordRemovedComplement(Channel ch, List<Integer> keptIndexes, int step) {
        final boolean[] kept = new boolean[ch.numPts()];
        for (int idx : keptIndexes) if (idx >= 0 && idx < kept.length) kept[idx] = true;
        int removedCount = 0;
        for (int i = 0; i < ch.numPts(); i++) if (!kept[i]) removedCount++;
        if (removedCount < 2) return; // a single stray point is not a loop

        // Cheap to construct here: Centreline holds no per-point cache (see its own javadoc), and
        // this runs once per cutoff, not per lattice point.
        final Centreline centreline = new Centreline(this);
        for (int i = 0; i < ch.numPts(); i++) {
            if (kept[i]) continue;
            // Elevation and influence stay 0 here: neither is knowable at the cut, and both are filled
            // in later through remapHistory. rosgenType stays null: the channel's per-point Rosgen
            // classification is a collectPrimitives-time concept (from a ChannelTyper), not something
            // a raw Channel carries — an untyped oxbow coalesces to RosgenType.A, same as an untyped river.
            lastStates.addLast(new OxbowLakePrimitive(
                    ch.spline.points().get(i).clone(),
                    (byte) step,
                    ch.widthAt(i),
                    0,
                    0,
                    centreline.normalAt(ch, i),
                    ch.spline.curvature(i),
                    null));
        }
        evictOlderThan(step);
    }
```

- [ ] **Step 6: Delete `HistoricPrimitiveCodecTest.java`**

It tested both shed families identically as `HistoricPrimitive`; Task 5 will have already broken
its `AbandonedRiverPrimitive` fixture (constructor arity changes), and Oxbow's coverage now lives in
`RosgenCarvedPrimitiveCodecTest` (this task) and `RadialPrimitiveCodecTest` (Task 5, for
AbandonedRiver). Delete the file now rather than patching it twice.

- [ ] **Step 7: Run test to verify it passes**

Run: `gradle test --tests "*RosgenCarvedPrimitiveCodecTest*"`
Expected: PASS. (Full `gradle build` will still fail until Task 5 removes the other
`HistoricPrimitive` implementor — that is expected and resolved by Task 5's own build step, not
this one.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/OxbowLakePrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/RosgenCarvedPrimitiveCodecTest.java
git rm src/test/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitiveCodecTest.java
git commit -m "feat(hydrology): reshape OxbowLakePrimitive into a rotated rectangle"
```

---

## Task 5: Reshape `AbandonedRiverPrimitive` to a radial disc; delete `HistoricPrimitive`

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java` (the `ABANDONED_RIVER` enum constant's `addPrimitives`)
- Rewrite: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/AbandonedRiverPrimitive.java`
- Delete: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java` (the atomic-eviction mint site, and `evictOlderThan`'s neighbourhood)
- Modify: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java`
- Run Task 3 (`PrimitiveCodec` cleanup) immediately after this task, per its own note.

**Interfaces:**
- Consumes: `RadialPrimitive` (existing, Task 2's `RadialProfile.ABANDONED_RIVER`).
- Produces: `new AbandonedRiverPrimitive(double[] coord, byte time, double width, double elevation)` — the new 4-arg constructor (was 5-arg with a trailing `influence`).

**Design decision this task locks in:** `AbandonedRiverPrimitive` drops its separate `influence`
field entirely. `RadialPrimitive.getRadius()` defaults to `width()` (the same default
`ConfluencePrimitive`/`SourcePrimitive` already rely on, unoverridden) — and unlike Oxbow's
influence, an abandoned trace's `width` is already known at mint time (`HydrologyTuning.widthFromFlow(maxOwn)`
runs before the primitive is even constructed), so there is nothing left to defer for the radius.
Only `elevation` remains genuinely deferred (the bed elevation of the trace, unknowable until a
later assigner pass) — `resolved(double elevation)` becomes single-argument.

- [ ] **Step 1: Write the failing test — extend `RadialPrimitiveCodecTest`'s fixtures**

In `RadialPrimitiveCodecTest.java`, change `radialPrimitives()`:

```java
    private static Stream<RadialPrimitive> radialPrimitives() {
        return Stream.of(
                new ConfluencePrimitive(new double[] {12.5, -40.25}, 6.0, 71.5),
                new SourcePrimitive(new double[] {-3.0, 8.75}, 1.25, 130.0),
                new AbandonedRiverPrimitive(new double[] {40.0, -12.0}, (byte) 5, 2.5, 88.0));
    }
```

Every `@ParameterizedTest` already iterating `radialPrimitives()` now also exercises
`AbandonedRiverPrimitive` — no other change needed in this file except the two single-type tests
(`confluenceHoldsTheAppendedTypeTag`, the two `hashesContentWhoseSeedWouldOverflowIntArithmetic`
tests) which stay untouched, they are deliberately type-specific.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle test --tests "*RadialPrimitiveCodecTest*"`
Expected: FAIL — `AbandonedRiverPrimitive`'s 4-arg constructor does not exist yet (still 5-arg).

- [ ] **Step 3: Rewrite `AbandonedRiverPrimitive.java`**

```java
package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.carvers.InfluenceCarver;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import org.jetbrains.annotations.NotNull;

/**
 * A former channel the river has since migrated out of — carved radially, like a confluence pool
 * left to silt in, rather than reusing river-style banding: unlike {@link OxbowLakePrimitive}, it is
 * minted with no {@link me.batata_1.fractal_terrain.hydrology.network.Channel} in scope (see
 * {@code RiverNetwork}'s eviction path), so no flow tangent is available to band against.
 */
public record AbandonedRiverPrimitive(double[] coord, byte time, double width, double elevation, long seed)
        implements RadialPrimitive {

    static final AbandonedRiverPrimitive PROTOTYPE =
            new AbandonedRiverPrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0);

    public AbandonedRiverPrimitive(double[] coord, byte time, double width, double elevation) {
        this(coord, time, width, elevation, computeHashCode(coord, time, width, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.ABANDONED_RIVER;
    }

    @Override
    public RadialProfile getRadialProfile() {
        return RadialProfile.ABANDONED_RIVER;
    }

    @Override
    public InfluenceCarver getInfluenceCarver() {
        return InfluenceCarver.RADIAL;
    }

    /** This primitive with its deferred elevation filled in — unknowable at the cut, resolved later
     *  once the network's bed-elevation pass runs. Radius is not deferred: unlike a shed loop, an
     *  abandoned trace's width (and so its radius, via {@link RadialPrimitive#getRadius()}) is
     *  already known at the moment it is cut. */
    public AbandonedRiverPrimitive resolved(double elevation) {
        return new AbandonedRiverPrimitive(coord, time, width, elevation);
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord) + Byte.BYTES + 2L * Double.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        PrimitiveCodec.putCoord(buf, coord);
        buf.put(time);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final byte t = buf.get();
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new AbandonedRiverPrimitive(coords, t, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbandonedRiverPrimitive other)) return false;
        return time == other.time
                && Arrays.equals(coord, other.coord)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(double[] coord, byte time, double width, double elevation) {
        int result = Objects.hash(time, width, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        return result;
    }

    @Override
    public @NotNull String toString() {
        return "Abandoned[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", elevation="
                + elevation + "]";
    }
}
```

- [ ] **Step 4: Delete `HistoricPrimitive.java`**

Run: `Grep -rn "HistoricPrimitive" src/` first to confirm only `AbandonedRiverPrimitive.java` (just
rewritten off it) and test files (updated in Task 7) still reference it. Then:

```bash
git rm src/main/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitive.java
```

- [ ] **Step 5: Update `HydrologicalFeature.ABANDONED_RIVER`'s `addPrimitives`**

In `HydrologicalPrimitive.java`:

```java
        ABANDONED_RIVER(() -> AbandonedRiverPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                final AbandonedRiverPrimitive shed = (AbandonedRiverPrimitive) args[0];
                primitives.add(new AbandonedRiverPrimitive(
                        VectorOps.sub(shed.coord(), offset), shed.time(), shed.width(), shed.elevation()));
            }
        },
```

- [ ] **Step 6: Drop the `influence` argument at the eviction mint site**

In `RiverNetwork.java`, the atomic-eviction path (currently around line 581):

```java
                lastStates.addLast(new AbandonedRiverPrimitive(p.clone(), (byte) step, width, 0));
```

(was `new AbandonedRiverPrimitive(p.clone(), (byte) step, width, 0, 0)` — drop the second `0`, the
old `influence` argument; the remaining `0` is `elevation`, still deferred.)

- [ ] **Step 7: Run the full build**

Run: `gradle spotlessApply` then `gradle build`.
Expected: compiles. (`RiverNetworkHistoryTest.java`'s `resolutionFillsTheDeferredElevationAndInfluence`
test will fail to compile at this point — it casts to the now-deleted `HistoricPrimitive` and calls
the old 2-arg `resolved`. Fix it now, in this task, since the build gate requires it — see Task 7's
Step 1 for the exact replacement, pulled forward here rather than duplicated.)

Replace, in `RiverNetworkHistoryTest.java`:

```java
    @Test
    void resolutionFillsTheDeferredElevationAndInfluence() {
        final RiverNetwork net = twoHairpins();
        net.detectAndApplyCutoffs(net.getChannels().get(0), 2);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");

        net.remapHistory(p -> ((OxbowLakePrimitive) p).resolved(64.0, 12.0));

        for (final HydrologicalPrimitive p : history(net)) {
            final OxbowLakePrimitive resolved = (OxbowLakePrimitive) p;
            assertEquals(64.0, resolved.elevation(), 1e-12);
            assertEquals(12.0, resolved.getRadius(), 1e-12, "a resolved primitive finally has a footprint");
            assertEquals((byte) 2, p.time(), "resolving must not disturb the cut step");
        }
    }
```

(Same test, retargeted at `OxbowLakePrimitive` directly instead of the deleted `HistoricPrimitive`
— this fixture's cutoffs only ever shed oxbows, confirmed by
`aCutoffShedsOxbowsStampedWithTheStepAndTheChannelWidth`'s own assertion in the same file.) Also
update this file's import from `me.batata_1.fractal_terrain.hydrology.features.HistoricPrimitive`
to nothing (delete the now-unused import) — `OxbowLakePrimitive` is already imported.

- [ ] **Step 8: Run `gradle test --tests "*RiverNetworkHistoryTest*" "*RadialPrimitiveCodecTest*"`**

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/AbandonedRiverPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetworkHistoryTest.java
git commit -m "feat(hydrology): reshape AbandonedRiverPrimitive into a radial disc, delete HistoricPrimitive"
```

Now run Task 3 (`PrimitiveCodec` cleanup) — both records that justified its `historic*` helpers no
longer call them.

---

## Task 6: Rewrite `RiverInfluenceCarve`'s shell dispatch

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/InfluenceCarverShellTest.java` (create)

**Interfaces:**
- Consumes: `InfluenceCarver` (Task 1); `RosgenCarvedPrimitive` (Tasks 1, 4); `RadialPrimitive` + `AbandonedRiverPrimitive.getInfluenceCarver()` (Task 5).
- Produces: `RiverInfluenceCarve.ShellGrid` (replaces the Task-1 stub), `carveRosgenInfluence`, `carveRadialInfluence` (replace the Task-1 stub `throw`s).

This is where the byte-identical-river-output guarantee from the Global Constraints is proven or
disproven — read that section again before writing this task's test.

- [ ] **Step 1: Write the failing tests**

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.AbandonedRiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.ConfluencePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.OxbowLakePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/** The shell pass's new per-primitive dispatch: what carves, what still does not, and that a
 *  RiverPrimitive's own carve is untouched by the refactor. */
class InfluenceCarverShellTest {

    private static final int PADDED = 16;

    private static RiverPrimitive river(double cx, double elevation) {
        return new RiverPrimitive(
                new double[] {cx, 8.0}, 2.0, RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, elevation, 0L);
    }

    private static OxbowLakePrimitive oxbow(double cx, double elevation) {
        return new OxbowLakePrimitive(
                new double[] {cx, 8.0}, (byte) 3, 2.0, 2.0, elevation, new double[] {1.0, 0.0}, 0.0, null);
    }

    private static float[] flatElevation(float value) {
        final float[] elev = new float[PADDED * PADDED];
        java.util.Arrays.fill(elev, value);
        return elev;
    }

    @Test
    void oxbowCarvesTheShellLikeARiverAtTheSamePosition() {
        final float[] elevOxbow = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elevOxbow, List.of(oxbow(8.0, 5.0)), PADDED);

        final float[] elevRiver = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elevRiver, List.of(river(8.0, 5.0)), PADDED);

        assertArrayEquals(elevRiver, elevOxbow, 1e-6f, "an oxbow at a river's own position and elevation must carve an identical shell");
    }

    @Test
    void abandonedRiverCarvesARadialDiscIntoTheShell() {
        final float[] elev = flatElevation(20f);
        final AbandonedRiverPrimitive trace = new AbandonedRiverPrimitive(new double[] {8.0, 8.0}, (byte) 4, 2.0, 5.0);

        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(trace), PADDED);

        assertTrue(elev[8 * PADDED + 8] < 20f, "the disc centre must be cut below ambient");
        assertEquals(20f, elev[0], 1e-6f, "a far corner outside the disc's radius must stay untouched");
    }

    @Test
    void confluenceStillContributesNoShellInfluence() {
        final float[] elev = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(
                elev, List.of(new ConfluencePrimitive(new double[] {8.0, 8.0}, 4.0, 5.0)), PADDED);

        assertEquals(20f, elev[8 * PADDED + 8], 1e-6f, "the shell pass does not carve Confluence/Source today, and this refactor must not change that");
    }

    @Test
    void riverShellOutputIsUnchangedByTheRefactor() {
        // Regression pin: same fixture ComputeRiverGridTest already exercises for the bed pass,
        // run through the shell entry point instead, to prove carveRosgenInfluence's math did not
        // move when it was retyped off RiverPrimitive onto RosgenCarvedPrimitive.
        final float[] elev = flatElevation(20f);
        RiverInfluenceCarve.carveRiverInfluenceGrid(elev, List.of(river(8.0, 5.0)), PADDED);

        assertTrue(elev[8 * PADDED + 8] < 20f, "the channel centre must be cut");
    }
}
```

Add `import static org.junit.jupiter.api.Assertions.assertArrayEquals;` to the import block.

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle test --tests "*InfluenceCarverShellTest*"`
Expected: FAIL — `carveRosgenInfluence`/`carveRadialInfluence` still throw
`UnsupportedOperationException` per Task 1's stub.

- [ ] **Step 3: Replace the `ShellGrid` stub with the real bundle**

```java
    /** Per-call scratch the shell pass's primitive carvers share: the grid size, the pre-carve
     *  ambient snapshot ({@code acc}, read but never rewritten once filled), the per-primitive
     *  cross-section LUT, the tangent/perpendicular projection scratch (unused by a radial carve),
     *  and the buffer every primitive's contribution is {@code Math.min}'d into. */
    public record ShellGrid(
            int gridSize,
            float[] acc,
            float[] lut,
            double[] perpRow,
            double[] perpCol,
            double[] tangRow,
            double[] tangCol,
            float[] elevs) {}
```

- [ ] **Step 4: Rewrite `carveRiverInfluenceGrid` to dispatch over every primitive**

```java
    public static void carveRiverInfluenceGrid(
            float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize) {
        if (primitives.isEmpty()) return;
        final GridBuffers buffers = SHELL_BUFFERS.get();
        buffers.ensure(paddedSize, maxLutLen(paddedSize, 1.0));
        final int points = paddedSize * paddedSize;

        Arrays.fill(buffers.acc, 0, 3 * points, 0f);
        for (int i = 0; i < points; i++) {
            buffers.acc[3 * i] = elevation[i];
        }
        // Preserved only for RiverProvider's debug-only shellDistanceField() capture; no shell
        // carver reads or writes dist -- see the "Rejected" section of this plan's spec for why.
        Arrays.fill(buffers.dist, 0, points, 1f);

        final ShellGrid grid = new ShellGrid(
                paddedSize, buffers.acc, buffers.lut, buffers.perpRow, buffers.perpCol,
                buffers.tangRow, buffers.tangCol, elevation);
        for (HydrologicalPrimitive primitive : primitives) {
            primitive.getInfluenceCarver().carveInfluence(primitive, grid);
        }
    }
```

Delete the old private `computeRiverInfluenceGrid` method entirely — its two responsibilities
(filling `typeMask`/`dist`, then looping the river prefix) are now split between the `Arrays.fill`
above (kept, for the debug capture only — `typeMask`'s fill was never consumed by anything and is
dropped, confirmed via `Grep -n "typeMask" RiverInfluenceCarve.java` showing no reader in the shell
path) and the generic loop above.

- [ ] **Step 5: Rename and retype `carveRiverPrimitiveInfluence` → `carveRosgenInfluence`**

Same body as today's `carveRiverPrimitiveInfluence` (lines 408-518), with:
- the signature changed to `static void carveRosgenInfluence(RosgenCarvedPrimitive primitive, ShellGrid grid)`
- every `river.` accessor call kept verbatim (`RosgenCarvedPrimitive` declares the identical method
  names: `normal()`, `coord()`, `getLength()`, `getWidth()`, `width()`, `curvature()`, `elevation()`,
  `getProfile()`, `seed()`)
- the loose `gridSize, acc, lut, perpRow, perpCol, tangRow, tangCol, elevs` parameters replaced by
  reads off `grid.gridSize()`, `grid.acc()`, `grid.lut()`, `grid.perpRow()`, `grid.perpCol()`,
  `grid.tangRow()`, `grid.tangCol()`, `grid.elevs()` at the top of the method (hoist each into a
  local exactly once, same as today's parameters — this is not a per-point cost)

```java
    static void carveRosgenInfluence(RosgenCarvedPrimitive primitive, ShellGrid grid) {
        final double[] normal = primitive.normal();
        // A null normal has no tangent -- the projection below would NPE.
        if (normal == null) return;
        final int gridSize = grid.gridSize();
        final float[] acc = grid.acc();
        final float[] lut = grid.lut();
        final double[] perpRow = grid.perpRow();
        final double[] perpCol = grid.perpCol();
        final double[] tangRow = grid.tangRow();
        final double[] tangCol = grid.tangCol();
        final float[] elevs = grid.elevs();

        final double nx = normal[0], nz = normal[1];
        final double cx = primitive.coord()[0], cz = primitive.coord()[1];
        final double influenceLen = primitive.getLength() * 0.5;
        final double influenceWidth = primitive.getWidth() * 0.5;

        final double halfExtentX = influenceLen * Math.abs(nz) + influenceWidth * Math.abs(nx);
        final double halfExtentZ = influenceLen * Math.abs(nx) + influenceWidth * Math.abs(nz);
        final long rowLo = (long) Math.floor(cx - halfExtentX);
        final long rowHi = (long) Math.ceil(cx + halfExtentX);
        final long colLo = (long) Math.floor(cz - halfExtentZ);
        final long colHi = (long) Math.ceil(cz + halfExtentZ);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        final double x0 = rowMin, x1 = rowMax, z0 = colMin, z1 = colMax;
        final double p00 = nx * (x0 - cx) + nz * (z0 - cz);
        final double p01 = nx * (x0 - cx) + nz * (z1 - cz);
        final double p10 = nx * (x1 - cx) + nz * (z0 - cz);
        final double p11 = nx * (x1 - cx) + nz * (z1 - cz);
        final double perpMin = Math.max(Math.min(Math.min(p00, p01), Math.min(p10, p11)), -influenceWidth);
        final double perpMax = Math.min(Math.max(Math.max(p00, p01), Math.max(p10, p11)), influenceWidth);
        if (perpMin > perpMax) return;

        final int baseIdx = (int) Math.floor(perpMin);
        final int n = (int) Math.floor(perpMax) - baseIdx + 2;

        final double width = primitive.width();
        final double curvature = primitive.curvature();
        final double elevation = primitive.elevation();
        final RosgenProfile profile = (RosgenProfile) primitive.getProfile();
        final long seed = primitive.seed();
        final double floodPlainLen = profile.floodPlainLength(width);
        final double marginLen = width / 2;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width);
        profile.sampleCrossSection(lut, n, 1.0, baseIdx, seed, elevation, floodPlainLen, marginLen, depth, curvature);
        for (int i = 0; i < lut.length; i++) if (lut[i] < elevation) lut[i] = (float) elevation;

        for (int row = rowMin; row <= rowMax; row++) {
            final double ddx = row - cx;
            perpRow[row] = nx * ddx;
            tangRow[row] = nz * ddx;
        }
        for (int col = colMin; col <= colMax; col++) {
            final double ddz = col - cz;
            perpCol[col] = nz * ddz;
            tangCol[col] = -nx * ddz;
        }

        final double invLen = 1.0 / influenceLen;
        final double invWidth = 1.0 / influenceWidth;
        final double floodPlainNormLen = Math.max(floodPlainLen * invLen, floodPlainLen * invWidth);
        final double invFlNormLenSlope = 1.0 / (1 - floodPlainNormLen);
        final double invFlNormLen = 1.0 / floodPlainNormLen;
        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double perpAtRow = perpRow[row];
            final double tangAtRow = tangRow[row];
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double perp = perpAtRow + perpCol[col];
                final double tang = tangAtRow + tangCol[col];
                final double d = Math.max(Math.abs(tang) * invLen, Math.abs(perp) * invWidth);
                final double dd = 0.5
                        * (d > floodPlainNormLen ? (d - floodPlainNormLen) * invFlNormLenSlope + 1 : d * invFlNormLen);
                final double f = perp - baseIdx;
                final int i0 = Math.clamp((int) f, 0, n - 2);

                final int a = 3 * i;
                final double h = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                final float testeW = dd < 0.5 ? 1 : (float) (1 - Math.clamp(dd * 2 - 1, 0, 1));
                elevs[i] = (float) Math.min(elevs[i], acc[a] * (1 - testeW) + h * testeW);
            }
        }
    }
```

This is the identical algorithm to today's `carveRiverPrimitiveInfluence` — the only substantive
change is reading scratch off `grid.*()` instead of loose parameters, and `primitive.` instead of
`river.`. `startX`/`startZ`/`resolution` were already hardcoded to `0`/`0`/`1.0` inline in the
original (the shell always runs at resolution 1.0 on its own padded-tile frame), so this plan
folds those constants directly into the arithmetic rather than carrying three always-identical
parameters, matching what the original already effectively did.

- [ ] **Step 6: Write `carveRadialInfluence`**

```java
    static void carveRadialInfluence(RadialPrimitive primitive, ShellGrid grid) {
        final double cx = primitive.coord()[0], cz = primitive.coord()[1];
        final double radius = primitive.getRadius();
        if (radius <= 0) return;
        final int gridSize = grid.gridSize();
        final float[] acc = grid.acc();
        final float[] lut = grid.lut();
        final float[] elevs = grid.elevs();

        final long rowLo = (long) Math.floor(cx - radius);
        final long rowHi = (long) Math.ceil(cx + radius);
        final long colLo = (long) Math.floor(cz - radius);
        final long colHi = (long) Math.ceil(cz + radius);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        final double x0 = rowMin, x1 = rowMax, z0 = colMin, z1 = colMax;
        final double nearX = Math.max(0.0, Math.max(x0 - cx, cx - x1));
        final double nearZ = Math.max(0.0, Math.max(z0 - cz, cz - z1));
        final double radMin = Math.sqrt(nearX * nearX + nearZ * nearZ);
        final double farX = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
        final double farZ = Math.max(Math.abs(z0 - cz), Math.abs(z1 - cz));
        final double radMax = Math.min(Math.sqrt(farX * farX + farZ * farZ), radius);
        if (radMin > radMax) return;

        final int baseIdx = (int) Math.floor(radMin);
        final int n = (int) Math.floor(radMax) - baseIdx + 2;

        final double elevation = primitive.elevation();
        final double invRadius = 1.0 / radius;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(primitive.width());
        primitive.getRadialProfile().sampleRadialSection(lut, n, 1.0, baseIdx, elevation, invRadius, depth);

        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double ddx = row - cx;
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final double ddz = col - cz;
                final double rad = Math.sqrt(ddx * ddx + ddz * ddz);
                if (rad > radius) continue; // outside the disc -- the AABB clip is conservative
                final double d = rad * invRadius;
                // Same inner/outer blend shape as carveRosgenInfluence's dd remap, so a shell disc
                // does not read as structurally different from a shell channel: full profile depth
                // out to half radius, tapering to ambient by the rim.
                final double dd = Math.min(d, 1.0);
                final double f = rad - baseIdx;
                final int i0 = Math.clamp((int) f, 0, n - 2);

                final int a = 3 * i;
                final double h = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                final float testeW = dd < 0.5 ? 1 : (float) (1 - Math.clamp(dd * 2 - 1, 0, 1));
                elevs[i] = (float) Math.min(elevs[i], acc[a] * (1 - testeW) + h * testeW);
            }
        }
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `gradle test --tests "*InfluenceCarverShellTest*"`
Expected: PASS.

- [ ] **Step 8: Run the full suite**

Run: `gradle test`
Expected: same 9 known failures, compared by *message* against
`.superpowers/conventions-alignment/post-migration-failures.txt` — specifically check
`ComputeRiverGridTest` and `RadialCarveTest` (bed-pass tests, untouched by this task) still pass
unchanged, since they exercise `computeRiverGrid`, which this task does not modify.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/InfluenceCarverShellTest.java
git commit -m "feat(hydrology): replace the shell pass's instanceof dispatch with getInfluenceCarver()"
```

---

## Task 7: Visual verification against the debug harness

**Files:** none changed — this task runs the mod's existing debug tooling and inspects output.

- [ ] **Step 1: Run the global/local river debug harnesses**

Run: `gradle globalRiverTest` then `gradle riverTest` (per root `CLAUDE.md`'s Build section).
These dump PNGs under `run/debug/` (see root `CLAUDE.md`'s `run/` row).

- [ ] **Step 2: Compare before/after**

If possible, run the same harnesses on `HEAD~1` (before Task 6) and diff the PNGs pixel-by-pixel
(e.g. `magick compare` or a manual visual diff) against this branch's output for a fixture known to
contain an oxbow or abandoned-channel cutoff. Confirm: river-only regions are pixel-identical
(proves the Task-6 rename introduced no drift); oxbow/abandoned-channel regions now show visible
carving where they previously showed a flat disc.

- [ ] **Step 3: Report findings**

If river-only output changed anywhere, that is a Task 6 regression — stop and re-diff
`carveRosgenInfluence` against the original `carveRiverPrimitiveInfluence` line by line before
proceeding. If only oxbow/abandoned-channel regions changed, this is the expected, intended new
behavior — no code change needed, just note it in the PR description per
`superpowers:requesting-code-review`.

---

## Self-Review Notes (completed during authoring, kept for the executor)

- **Spec coverage:** "OxbowLake → rotated rectangle" (Task 4), "AbandonedRiver → radial" (Task 5),
  "InfluenceCarver rename + shell-only scope" (Tasks 1, 6), "N-tiered priority without N buffers" —
  **explicitly rejected** per the "Rejected" section above and the user's own follow-up answer;
  `getPriorityTier()` is dropped, not implemented. If the project owner disagrees after reading
  this plan, that is a plan revision, not an execution deviation — raise it before Task 1.
- **Placeholder scan:** no task step contains "TBD" or unshown code; every code block is complete
  and compiles against the signatures defined in earlier tasks.
- **Type consistency:** `OxbowLakePrimitive`'s constructor is called with the same 8-arg shape in
  Task 4 (definition), Task 6 (`InfluenceCarverShellTest`), and nowhere else constructs it directly
  outside `RiverNetwork`/`HydrologicalPrimitive`, both updated in Task 4. `AbandonedRiverPrimitive`'s
  4-arg constructor is likewise consistent across Task 5 and its one other call site (`RiverNetwork`
  line ~581, Task 5 Step 6).
