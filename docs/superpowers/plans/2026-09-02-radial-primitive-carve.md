# Radial Primitive Carve Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** Make confluences and headwater sources carve terrain — a radial bowl at every river junction
and a cone at every spring — instead of leaving both to whatever river primitives happen to pass nearby.

**Architecture:** A new `RadialPrimitive` family (`ConfluencePrimitive`, and `SourcePrimitive` promoted
into it) is merged by a second pass inside `RiverInfluenceCarve.computeRiverGrid`, after the river pass
and against a refilled distance mask, into the same `acc` buffers. The shape difference between the two
families lives entirely in a `RadialProfile` enum constant. Only the per-chunk bed carve runs the new
pass; the tile-level shell carve is untouched.

**Tech Stack:** Java 21, Minecraft 1.20.1 / Fabric Loom, JUnit 5 (`useJUnitPlatform()`), Spotless with
palantirJavaFormat, fastutil (already on the classpath transitively).

**Spec:** `docs/superpowers/specs/2026-09-02-radial-primitive-carve-design.md`

---

## Global Constraints

Every task's requirements implicitly include this section.

- **Read the guidelines before the first edit.** Root `CLAUDE.md` -> the `README.md`/`CLAUDE.md` in or
  above the directory you are editing -> `ARCHITECTURE.md` (this change crosses the generation pipeline)
  -> `.claude/conventions/CLAUDE.md`, then `documentation.md`, `structural.md`, `code-quality/`,
  `class-structure.md`, `performance.md`, `temporal.md`, `intent-markers.md`. Open the message that first
  proposes or makes a change with `Guidelines: <paths read>`.
- **Docstring budgets are hard** (`.claude/conventions/documentation.md`, Tier 3): 1 line for a field,
  3 for a method, 10 for a class. At most one line describes the thing itself; every other line answers
  *why* or *where in the pipeline*. Do not state determinism, thread-safety, purity, or nullability
  already carried by `@Nullable`.
- **`hydrology/` imports no `net.minecraft`.** Nothing in this plan changes that; it is what lets the
  golden suite run as plain JUnit.
- **fastutil over `java.util`** for any Set/Map/List (`.claude/conventions/performance.md`), except where
  a primitive array fits better. `it.unimi.dsi:fastutil` must NOT be added to `build.gradle` — it is on
  the classpath transitively through Minecraft.
- **The per-lattice-cell loop in `RiverInfluenceCarve` is below the hot/cold line.** No `new`, no boxing,
  no iterators or streams in it. Hoist invariants to the per-primitive level. Mark a deliberate
  allocation or a deliberate non-simplification with `:PERF: [what]; [why]`.
- **`HydrologicalFeature` is append-only.** A constant's ordinal is the on-disk type tag. Never reorder,
  never remove.
- **Run before every commit:** `gradle spotlessApply`, then `gradle build`. `build` runs
  `spotlessCheck` and will fail on unformatted code.
- **Test baseline.** `gradle test` at `df7ca2e` gives **102 tests, 9 failed, 1 skipped**:
  `RosgenKeyTest` (4), `RiverGoldenTest` (2), `MeandersGoldenTest` (1), `CentrelineTest` (1),
  `ReachMetricsSamplerTest` (1). Compare *failure messages* against
  `.superpowers/conventions-alignment/post-migration-failures.txt`, not just the failing test names.
  Do not chase these nine; do not let their count grow.
- **Worktree setup.** `libs/` is git-ignored. A fresh worktree needs `libs/onnxruntime/teste.jar`
  copied in, or the build produces roughly 132 phantom compile errors that have nothing to do with
  this change.

## Constants this plan reads (do not re-derive)

| Constant | Value | Source |
| --- | --- | --- |
| `FractalTerrainConfig.GLOBAL_SCALE_CORRECTION` | `5f` | `config/ModConfig.java:22` |
| `HydrologyTuning.PRIMITIVE_BLEND_STRENGTH` | `0.05` | `config/HydrologyTuning.java:12` |
| `HydrologyTuning.MIN_WIDTH` / `MAX_WIDTH` | `0.6` / `16.0` | `config/HydrologyTuning.java:92,97` |
| `RiverInfluenceCarve.UNSET_MIN_DIST` | `64` | `hydrology/profile/RiverInfluenceCarve.java` |
| `HydrologicalFeature.NONE` | `-1L` | `hydrology/features/HydrologicalPrimitive.java` |

---

## Known collision with an in-flight plan — read before Task 5

`docs/superpowers/plans/2026-09-02-hydrology-surface-painting.md` is written but **not implemented**
(no `RIVER_DIST`, no `bandOf` exists in `src/` as of `4254d4e`). It touches the same code:

1. It remaps `carvePrimitive`'s `d` onto a banded coordinate with fixed breakpoints and **publishes
   `buffers.dist` into a new `Types.RIVER_DIST` heightmap channel**.
2. This plan's Task 5 refills `dist` to `UNSET_MIN_DIST` mid-`computeRiverGrid` and overwrites it with
   radial values. **That destroys exactly the data the other plan publishes.**
3. It also references `carvePrimitive` by name throughout, which Task 5 renames.

Today the refill is harmless: nothing reads `buffers.dist` after `computeRiverGrid` returns
(`shellDistanceField()` is `@TestOnly` and serves the shell path, which does not run the radial pass).
So this plan is correct and executable on its own **right now**.

Whichever plan lands second must reconcile them. The cheapest reconciliation, if surface-painting lands
second, is for `PopulateNoiseStep` to publish `RIVER_DIST` from a snapshot taken before the radial pass,
or for `GridBuffers` to gain a separate `radialDist` array. Do not build either speculatively — flag it
to the user when the second plan starts.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `hydrology/profile/RadialProfile.java` (create) | The two radial shape laws and the LUT that tabulates them |
| `hydrology/features/RadialPrimitive.java` (create) | The marker interface the carve's second pass dispatches on |
| `hydrology/features/ConfluencePrimitive.java` (create) | The junction pool record |
| `hydrology/features/SourcePrimitive.java` (modify) | Promoted from position-only into the radial family |
| `hydrology/features/HydrologicalPrimitive.java` (modify) | `CONFLUENCE` enum constant + both radial `addPrimitives` |
| `hydrology/features/PositionOnlyPrimitive.java` (modify) | Javadoc: drop `SourcePrimitive` from its implementors |
| `hydrology/network/RiverNetwork.java` (modify) | Emit confluences and width-carrying sources |
| `hydrology/profile/RiverInfluenceCarve.java` (modify) | Rename `carvePrimitive`; add the radial pass |
| `hydrology/providers/RiverProvider.java` (modify) | Primitive-store rename (schema break) |
| `test/.../features/RadialPrimitiveCodecTest.java` (create) | Round-trip + circle geometry, parameterized over both records |
| `test/.../profile/RadialProfileTest.java` (create) | The two shape laws |
| `test/.../profile/RadialCarveTest.java` (create) | The D4/D5/D6 merge properties |
| `test/.../profile/ComputeRiverGridTest.java` (modify) | Compile fix + the changed source semantics |

---

## Task 1: `RadialProfile` — the two shape laws

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfile.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfileTest.java`

**Interfaces:**
- Consumes: `HydrologyProfile` (existing, `hydrology/profile/HydrologyProfile.java`).
- Produces: `RadialProfile.CONFLUENCE`, `RadialProfile.SOURCE`;
  `void sampleRadialSection(float[] lut, int n, double step, int baseIdx, double elevation, double invRadius, double depth)`;
  `protected abstract double radialDelta(double normalizedRadius, double depth)`.

`radialDelta` is `protected`, which in Java also grants same-package access — the test lives in
`me.batata_1.fractal_terrain.hydrology.profile` and calls it directly. Do not add a test-only accessor.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfileTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The radial shape laws. Both must reach zero exactly at the rim, or a bowl steps against the shell
 * it is cut into; the two are otherwise free to differ, and the midpoint test pins that they do.
 */
class RadialProfileTest {

    private static final double DEPTH = 10.0;

    @ParameterizedTest
    @EnumSource(RadialProfile.class)
    void isFlushAtTheRim(RadialProfile profile) {
        assertEquals(0.0, profile.radialDelta(1.0, DEPTH), 1e-12, profile + " steps at the rim");
    }

    @ParameterizedTest
    @EnumSource(RadialProfile.class)
    void bottomsOutAtTheFullDepthInTheCentre(RadialProfile profile) {
        assertEquals(-DEPTH, profile.radialDelta(0.0, DEPTH), 1e-12, profile + " floor is not the depth");
    }

    @ParameterizedTest
    @EnumSource(RadialProfile.class)
    void risesMonotonicallyTowardTheRim(RadialProfile profile) {
        double previous = profile.radialDelta(0.0, DEPTH);
        for (int i = 1; i <= 100; i++) {
            final double current = profile.radialDelta(i / 100.0, DEPTH);
            assertTrue(current >= previous, profile + " fell at r = " + (i / 100.0));
            previous = current;
        }
    }

    @Test
    void theParabolaHoldsItsFloorWiderThanTheCone() {
        // Half way out, the confluence bowl has given up a quarter of its depth and the source cone
        // half of it. This is the whole visual difference between the two families.
        assertEquals(-0.75 * DEPTH, RadialProfile.CONFLUENCE.radialDelta(0.5, DEPTH), 1e-12);
        assertEquals(-0.5 * DEPTH, RadialProfile.SOURCE.radialDelta(0.5, DEPTH), 1e-12);
    }

    @Test
    void tabulatesTheLawOntoTheLutWithTheElevationFoldedIn() {
        // step 1.0, baseIdx 0, radius 4 -> lut[i] is the surface at radius i.
        final float[] lut = new float[8];
        RadialProfile.CONFLUENCE.sampleRadialSection(lut, 5, 1.0, 0, 100.0, 1.0 / 4.0, DEPTH);

        assertEquals(90.0f, lut[0], 1e-4f, "centre is the rim minus the full depth");
        assertEquals(100.0f, lut[4], 1e-4f, "the rim is the elevation itself");
    }

    @Test
    void clampsBeyondTheRimInsteadOfOvershooting() {
        // The carve's AABB is square, so cells past the disc still index the LUT; they must read the
        // rim value rather than a law evaluated outside its domain.
        final float[] lut = new float[8];
        RadialProfile.SOURCE.sampleRadialSection(lut, 7, 1.0, 0, 100.0, 1.0 / 4.0, DEPTH);

        assertEquals(100.0f, lut[6], 1e-4f, "radius 6 on a radius-4 disc must clamp to the rim");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*RadialProfileTest"`
Expected: FAIL — `:compileTestJava` errors, `RadialProfile` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfile.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

/**
 * The cross-section of a feature with no flow direction — what a junction pool cuts, and what a spring
 * cuts, as a function of distance from its centre alone.
 *
 * <p>The radial twin of {@link RosgenProfile}: same split, where the enum constant owns the shape and
 * the carve owns the walk. A radial primitive routes here rather than to a Rosgen type because a bowl
 * has no tangent to take a cross-section across.
 */
public enum RadialProfile implements HydrologyProfile {

    /** Converging flow scours a rounded floor, so the parabola holds its depth well out toward the rim. */
    CONFLUENCE {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * (1 - normalizedRadius * normalizedRadius);
        }
    },

    /** A spring cuts a notch rather than a pool, so the cone gives up depth linearly from a point. */
    SOURCE {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * (1 - normalizedRadius);
        }
    };

    /**
     * Tabulates this profile into {@code lut}, where entry {@code i} is the surface at radius
     * {@code (baseIdx + i) * step}. Runs once per primitive per grid, so the carve's per-cell loop
     * reads an array instead of evaluating the law.
     */
    public void sampleRadialSection(
            float[] lut, int n, double step, int baseIdx, double elevation, double invRadius, double depth) {
        for (int i = 0; i < n; i++) {
            // The carve's footprint is a square AABB, so entries past the rim are indexed and must
            // read the rim rather than an extrapolated law.
            final double r = Math.clamp((baseIdx + i) * step * invRadius, 0, 1);
            lut[i] = (float) (elevation + radialDelta(r, depth));
        }
    }

    /** The signed offset below the rim at a radius normalised to {@code [0, 1]}; zero at the rim. */
    protected abstract double radialDelta(double normalizedRadius, double depth);
}
```

This file compiles on its own — it references no type Task 2 adds.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle spotlessApply` then `gradle test --tests "*RadialProfileTest"`
Expected: PASS, 13 tests (5 methods, three of them parameterized over 2 constants).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfile.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialProfileTest.java
git commit -m "feat(hydrology): add RadialProfile, the confluence and source shape laws"
```

---

## Task 2: `RadialPrimitive` and `ConfluencePrimitive`

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitive.java`
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/ConfluencePrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java`
  (append `CONFLUENCE` to `HydrologicalFeature`)
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java`

**Interfaces:**
- Consumes: `RadialProfile` (Task 1); `HydrologicalPrimitive`, `SpatialIndexCircle`, `PrimitiveCodec`
  (all existing).
- Produces: `RadialPrimitive` with `double width()`, `double elevation()`,
  `RadialProfile getRadialProfile()`, and defaulted `getCenter()` / `getRadius()` / `getProfile()`;
  `ConfluencePrimitive(double[] coord, double width, double elevation, long seed)` plus a 3-arg
  convenience constructor and `ConfluencePrimitive.PROTOTYPE`;
  `HydrologicalFeature.CONFLUENCE` at ordinal 6.

`PrimitiveCodec` is package-private, so both new files must live in
`me.batata_1.fractal_terrain.hydrology.features`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.stream.Stream;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Persistence and index geometry for the radial family. Records compare {@code double[]} by reference,
 * so a primitive would otherwise be unequal to its own reloaded copy — which the round trip is here
 * to catch.
 */
class RadialPrimitiveCodecTest {

    /** Task 3 adds the SourcePrimitive entry; every test below is written to cover both. */
    private static Stream<RadialPrimitive> radialPrimitives() {
        return Stream.of(new ConfluencePrimitive(new double[] {12.5, -40.25}, 6.0, 71.5));
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void roundTripsThroughTheTypeTaggedPayload(RadialPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.getClass(), reloaded.getClass(), "the type tag did not select the record");
        assertNotSame(original, reloaded);
        assertEquals(original, reloaded, "contents did not survive the round trip");
        assertEquals(original.hashCode(), reloaded.hashCode());
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void reportsThePayloadSizeItActuallyWrites(RadialPrimitive primitive) {
        assertEquals(primitive.byteSize(), primitive.serialize().length);
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void isIndexedAsADiscOfItsOwnWidth(RadialPrimitive primitive) {
        assertEquals(primitive.width(), primitive.getRadius(), 1e-12);
        assertArrayEquals(primitive.coord(), primitive.getCenter());
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void containsThePointsInsideItsDiscAndNoOthers(RadialPrimitive primitive) {
        final double[] centre = primitive.coord();
        final double r = primitive.getRadius();

        assertTrue(primitive.containsPoint(new double[] {centre[0], centre[1]}));
        assertTrue(primitive.containsPoint(new double[] {centre[0] + r * 0.99, centre[1]}));
        assertFalse(primitive.containsPoint(new double[] {centre[0] + r * 1.01, centre[1]}));
        assertTrue(
                primitive.containsPointInflated(new double[] {centre[0] + r * 1.01, centre[1]}, r * 0.1),
                "an inflated stab must reach a disc the chunk prefetch would otherwise miss");
    }

    @ParameterizedTest
    @MethodSource("radialPrimitives")
    void writesAnMbrThatBoundsItsDisc(RadialPrimitive primitive) {
        final double[] lower = new double[2];
        final double[] upper = new double[2];
        primitive.writeMbrInto(lower, upper);
        final double[] centre = primitive.coord();
        final double r = primitive.getRadius();

        assertArrayEquals(new double[] {centre[0] - r, centre[1] - r}, lower, 1e-12);
        assertArrayEquals(new double[] {centre[0] + r, centre[1] + r}, upper, 1e-12);
    }

    @Test
    void confluenceHoldsTheAppendedTypeTag() {
        // The ordinal is the on-disk tag. If this number changes, every cached primitive is
        // reinterpreted as a different feature.
        assertEquals(6, HydrologicalFeature.CONFLUENCE.ordinal());
        assertEquals(
                HydrologicalFeature.CONFLUENCE,
                new ConfluencePrimitive(new double[] {0.0, 0.0}, 1.0, 0.0).getType());
    }

    @Test
    void sortsAfterEveryRiverPrimitive() {
        // computeRiverGrid's river loop stops at the first non-river entry; a radial family sorting
        // before RIVER would truncate the river run and silently drop carve.
        final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>(List.of(
                new ConfluencePrimitive(new double[] {0.0, 0.0}, 1.0, 0.0),
                new RiverPrimitive(new double[] {0.0, 0.0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0)));
        primitives.sort(HydrologicalPrimitive.comparator);

        assertTrue(primitives.get(0) instanceof RiverPrimitive, "RIVER must sort first");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*RadialPrimitiveCodecTest"`
Expected: FAIL — `:compileTestJava` errors, `RadialPrimitive` and `ConfluencePrimitive` do not exist.

- [ ] **Step 3: Create `RadialPrimitive`**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitive.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A feature the carve cuts radially rather than along a flow tangent — a junction pool or a spring.
 *
 * <p>The type {@code RiverInfluenceCarve}'s second pass dispatches on, which is why it is public where
 * {@link PositionOnlyPrimitive} is not: the carve lives in {@code hydrology.profile} and must name it.
 * Everything the pass needs is here, so the pass never switches on a concrete record type — the shape
 * comes from {@link RadialProfile}, the extents from {@link #width()}, the rim from {@link #elevation()}.
 */
public interface RadialPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    /** The largest channel width meeting at this node; the disc radius and the depth law's input. */
    double width();

    /** The rim the bowl is cut down from, taken from the node's assigned bed elevation. */
    double elevation();

    RadialProfile getRadialProfile();

    @Override
    default double[] getCenter() {
        return coord();
    }

    @Override
    default double getRadius() {
        return width();
    }

    @Override
    default HydrologyProfile getProfile() {
        return getRadialProfile();
    }
}
```

- [ ] **Step 4: Create `ConfluencePrimitive`**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/features/ConfluencePrimitive.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import org.jetbrains.annotations.NotNull;

/**
 * The pool where two or more channels merge — one disc per {@code JUNCTION} endpoint of degree three
 * or more.
 *
 * <p>Sized by the widest channel meeting at the node, so a trunk's junction reads larger than a
 * headwater's. Carved by the radial pass of {@code RiverInfluenceCarve.computeRiverGrid} after every
 * river, which is what lets it deepen a bed the converging channels already cut rather than fight them.
 *
 * <p>Unrelated to the junction ray-set of the same name removed before {@code df7ca2e}; see
 * {@code ARCHITECTURE.md}'s superseded-designs paragraph.
 */
public record ConfluencePrimitive(double[] coord, double width, double elevation, long seed)
        implements RadialPrimitive {

    static final ConfluencePrimitive PROTOTYPE = new ConfluencePrimitive(new double[] {0.0, 0.0}, 0, 0);

    public ConfluencePrimitive(double[] coord, double width, double elevation) {
        this(coord, width, elevation, computeHashCode(coord, width, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.CONFLUENCE;
    }

    @Override
    public RadialProfile getRadialProfile() {
        return RadialProfile.CONFLUENCE;
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord) + 2L * Double.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        PrimitiveCodec.putCoord(buf, coord);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new ConfluencePrimitive(coords, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfluencePrimitive other)) return false;
        return Arrays.equals(coord, other.coord)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(double[] coord, double width, double elevation) {
        return 31L * Objects.hash(width, elevation) + Arrays.hashCode(coord);
    }

    @Override
    public @NotNull String toString() {
        return "Confluence[coord=" + Arrays.toString(coord) + ", width=" + width + ", elevation=" + elevation + "]";
    }
}
```

- [ ] **Step 5: Append the `CONFLUENCE` enum constant**

In `HydrologicalPrimitive.java`, add after the `DELTA` constant (keeping `DELTA`'s trailing `;` on the
new last constant):

```java
        // :SCHEMA: appended, never reordered; the ordinal is the on-disk type tag, so moving a
        // constant reinterprets every primitive already cached.
        CONFLUENCE(() -> ConfluencePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {}
        };
```

Leave `addPrimitives` empty for now — Task 4 fills it. Change `DELTA`'s terminating `;` to `,`.

- [ ] **Step 6: Run the tests**

Run: `gradle spotlessApply` then `gradle test --tests "*RadialPrimitiveCodecTest"`
Expected: PASS, 7 tests (5 parameterized over the single `ConfluencePrimitive` entry, plus 2 plain).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/ConfluencePrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java
git commit -m "feat(hydrology): add the RadialPrimitive family and ConfluencePrimitive"
```

---

## Task 3: Promote `SourcePrimitive` into the radial family

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/SourcePrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/PositionOnlyPrimitive.java`
  (javadoc only)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/providers/RiverProvider.java:69-76`
  (store rename)
- Modify: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java:195-219`
  (compile fix)
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java`
  (restore the `SourcePrimitive` parameterization)

**Interfaces:**
- Consumes: `RadialPrimitive`, `RadialProfile` (Tasks 1-2).
- Produces: `SourcePrimitive(double[] coord, double width, double elevation, long seed)` plus a 3-arg
  convenience constructor. **The 1-arg `SourcePrimitive(double[])` constructor is removed** — one
  existing call site depends on it (`ComputeRiverGridTest:199`) and is fixed in this task.

This task changes the on-disk payload of an existing type tag. The store rename is not optional and
not deferrable: without it, a cached tile is read with a payload two doubles short.

- [ ] **Step 1: Add `SourcePrimitive` to the parameterized source**

In `RadialPrimitiveCodecTest`, extend `radialPrimitives()` to both records and drop the Task-3 note
above it:

```java
    private static Stream<RadialPrimitive> radialPrimitives() {
        return Stream.of(
                new ConfluencePrimitive(new double[] {12.5, -40.25}, 6.0, 71.5),
                new SourcePrimitive(new double[] {-3.0, 8.75}, 1.25, 130.0));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*RadialPrimitiveCodecTest"`
Expected: FAIL — `:compileTestJava`, no 3-arg `SourcePrimitive` constructor.

- [ ] **Step 3: Rewrite `SourcePrimitive`**

Replace `src/main/java/me/batata_1/fractal_terrain/hydrology/features/SourcePrimitive.java` entirely:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import me.batata_1.fractal_terrain.hydrology.profile.RadialProfile;
import org.jetbrains.annotations.NotNull;

/**
 * The head of a channel — the spring or seep a river starts at.
 *
 * <p>Cuts a cone rather than the confluence's bowl, so a headwater reads as a notch the channel
 * emerges from instead of a pool. Sized by the width of the single channel leaving it, which is the
 * narrowest in its network, so it is the smallest thing the radial pass carves.
 *
 * <p>Note this is a point of the river it heads, not an independent feature: the network still stamps
 * {@link HydrologicalFeature#SOURCE} on the first point of a channel that begins at a source node.
 */
public record SourcePrimitive(double[] coord, double width, double elevation, long seed)
        implements RadialPrimitive {

    static final SourcePrimitive PROTOTYPE = new SourcePrimitive(new double[] {0.0, 0.0}, 0, 0);

    public SourcePrimitive(double[] coord, double width, double elevation) {
        this(coord, width, elevation, computeHashCode(coord, width, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.SOURCE;
    }

    @Override
    public RadialProfile getRadialProfile() {
        return RadialProfile.SOURCE;
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.coordByteSize(coord) + 2L * Double.BYTES;
    }

    @Override
    public byte[] serializePrimitive() {
        final ByteBuffer buf = ByteBuffer.allocate((int) primitiveByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        PrimitiveCodec.putCoord(buf, coord);
        buf.putDouble(width);
        buf.putDouble(elevation);
        return buf.array();
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coords = PrimitiveCodec.getCoord(buf);
        final double w = buf.getDouble();
        final double e = buf.getDouble();
        return new SourcePrimitive(coords, w, e);
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcePrimitive other)) return false;
        return Arrays.equals(coord, other.coord)
                && Double.compare(width, other.width) == 0
                && Double.compare(elevation, other.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    private static long computeHashCode(double[] coord, double width, double elevation) {
        return 31L * Objects.hash(width, elevation) + Arrays.hashCode(coord);
    }

    @Override
    public @NotNull String toString() {
        return "Source[coord=" + Arrays.toString(coord) + ", width=" + width + ", elevation=" + elevation + "]";
    }
}
```

- [ ] **Step 4: Drop `SourcePrimitive` from the mixin's javadoc**

In `PositionOnlyPrimitive.java`, change the implementors sentence to read:

```java
 * <p>{@link DeltaPrimitive}, {@link WaterfallPrimitive}, {@link OxbowLakePrimitive} and {@link
 * AbandonedRiverPrimitive} implement this and keep only {@code getType()}, {@code
 * deserializePrimitive()} and their {@code coord} component — {@code equals}/{@code hashCode} stay on
 * each record because an interface default cannot override {@link Object}'s.
```

- [ ] **Step 5: Rename the primitive store**

In `RiverProvider.java`, replace the store name and extend the comment above it:

```java
        // The one-primitive prototype index keeps Storage's serializability probe exercising primitive
        // serialization, so the store stays disk-backed. Primitive coords are persisted in the WORLD
        // relief-pixel frame (see buildTile). The name carries the schema identity: "_v3" is what
        // orphans tiles written before SourcePrimitive's payload grew a width and an elevation, which
        // would otherwise be read two doubles short under an unchanged type tag.
        primitives = new NonIntersectingSpatialIndex<>(
                path,
                "local_river_units_v3",
```

- [ ] **Step 6: Fix the broken existing test**

In `ComputeRiverGridTest.java`, replace the body of `stopsAtTheFirstNonRiverPrimitiveAndReportsWhere`'s
source construction (line 198-199) and extend the assertion:

```java
    @Test
    void stopsAtTheFirstNonRiverPrimitiveAndReportsWhere() {
        final RiverPrimitive river = knot(8.0, 100.0, RosgenType.A, 0L);
        final HydrologicalPrimitive source =
                new me.batata_1.fractal_terrain.hydrology.features.SourcePrimitive(new double[] {8.0, 8.0}, 2.0, 100.0);
        final RiverInfluenceCarve.GridBuffers b = buffers();

        final int stop = RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                List.of(river, source),
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);

        // The return value bounds the RIVER run only; the source is carved by the radial pass that
        // runs after it, not skipped.
        assertEquals(1, stop, "the river run ends at index 1");
    }
```

- [ ] **Step 7: Run the tests**

Run: `gradle spotlessApply` then `gradle test`
Expected: `RadialPrimitiveCodecTest` fully PASSES. The whole suite runs; the failure set must still be
the nine baseline failures with the same messages as
`.superpowers/conventions-alignment/post-migration-failures.txt`. `ComputeRiverGridTest` must be green
— the radial pass does not exist yet, so the source still carves nothing and every existing assertion
holds.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/SourcePrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/PositionOnlyPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/providers/RiverProvider.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialPrimitiveCodecTest.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/ComputeRiverGridTest.java
git commit -m "feat(hydrology)!: SourcePrimitive carries width and elevation

Promotes SourcePrimitive out of the position-only mixin into RadialPrimitive.
The payload grows two doubles under an unchanged type tag, so the primitive
store is renamed to local_river_units_v3 to orphan stale tiles rather than
misparse them."
```

---

## Task 4: Emit confluences and width-carrying sources

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java`
  (`SOURCE.addPrimitives`, `CONFLUENCE.addPrimitives`, and a shared radius helper)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java:733-770`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialEmissionTest.java` (create)

**Interfaces:**
- Consumes: `ConfluencePrimitive`, `SourcePrimitive` (Tasks 2-3); `Endpoint`, `Channel`, `RiverNetwork`
  (existing).
- Produces: `HydrologicalFeature.SOURCE.addPrimitives(offset, out, Endpoint, RiverNetwork, IntSet)` and
  `HydrologicalFeature.CONFLUENCE.addPrimitives(offset, out, Endpoint, RiverNetwork, IntSet)`, both
  through the existing `Object... args`.

The arguments go through the existing varargs — do **not** add a second varargs or a boxed parameter to
`addPrimitives`, which `features/README.md` names as an invariant.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialEmissionTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import me.batata_1.fractal_terrain.config.HydrologyTuning;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive.HydrologicalFeature;
import me.batata_1.fractal_terrain.hydrology.network.Endpoint;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * What the network hands the radial family. The radius rule is the whole point: a bowl sized off a
 * channel that emitted no river primitives would carve into terrain nothing else touched.
 */
class RadialEmissionTest {

    private static final int GRID = 512;
    private static final double RESAMPLE_DIST = 4.0;

    private static final double[] SOURCE_A = {100.0, 150.0};
    private static final double[] SOURCE_B = {400.0, 150.0};
    private static final double[] JUNCTION = {250.0, 256.0};
    private static final double[] DRAIN = {250.0, 400.0};

    /** Two sources into one junction into a drain: the smallest graph with a real confluence. */
    private static RiverNetwork confluenceNetwork() {
        final List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(SOURCE_A[0], SOURCE_A[1], Endpoint.Type.SOURCE), // 0
                new NodeSpec(SOURCE_B[0], SOURCE_B[1], Endpoint.Type.SOURCE), // 1
                new NodeSpec(JUNCTION[0], JUNCTION[1], Endpoint.Type.JUNCTION), // 2
                new NodeSpec(DRAIN[0], DRAIN[1], Endpoint.Type.DRAIN)); // 3
        final List<EdgeSpec> edgeSpecs = List.of(
                new EdgeSpec(0, 2, segment(SOURCE_A, JUNCTION, 24), 8.0),
                new EdgeSpec(1, 2, segment(SOURCE_B, JUNCTION, 24), 8.0),
                new EdgeSpec(2, 3, segment(JUNCTION, DRAIN, 24), 12.0));
        return new RiverNetwork(GRID, nodeSpecs, edgeSpecs, false, 0, RESAMPLE_DIST);
    }

    private static ArrayList<double[]> segment(double[] a, double[] b, int n) {
        final ArrayList<double[]> pts = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            final double t = (double) i / n;
            pts.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
        }
        return pts;
    }

    private static IntSet allChannelIds(RiverNetwork net) {
        final IntSet ids = new IntOpenHashSet();
        net.getChannels().forEach(ch -> ids.add(ch.channelId));
        return ids;
    }

    private static Endpoint nodeOfType(RiverNetwork net, Endpoint.Type type) {
        return net.getNodes().stream()
                .filter(n -> n.type == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixture has no " + type + " node"));
    }

    @Test
    void confluenceTakesTheWidestChannelMeetingAtTheJunction() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        junction.elevation = 64.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.CONFLUENCE.addPrimitives(
                new double[] {0.0, 0.0}, out, junction, net, allChannelIds(net));

        assertEquals(1, out.size(), "one disc per junction");
        final ConfluencePrimitive bowl = (ConfluencePrimitive) out.get(0);
        // The trunk carries both tributaries' flow, so it is the widest thing meeting here.
        final double trunkWidth = net.getChannel(junction.outgoing).widthAt(0);
        assertEquals(trunkWidth, bowl.width(), 1e-12);
        assertEquals(64.0, bowl.elevation(), 1e-12);
    }

    @Test
    void confluenceSkipsAJunctionWhoseChannelsDidNotEmit() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        junction.elevation = 64.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        // Only the outgoing trunk emitted: one incident channel is not two, so there is no confluence.
        final IntSet trunkOnly = new IntOpenHashSet();
        trunkOnly.add(junction.outgoing);

        HydrologicalFeature.CONFLUENCE.addPrimitives(new double[] {0.0, 0.0}, out, junction, net, trunkOnly);

        assertTrue(out.isEmpty(), "a bowl needs at least two emitting channels to be sized from");
    }

    @Test
    void confluenceSkipsAJunctionWithNoAssignedElevation() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        // Endpoint.elevation is NaN until ChannelElevationAssigner runs; a NaN rim carves a NaN bowl.
        junction.elevation = Double.NaN;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.CONFLUENCE.addPrimitives(
                new double[] {0.0, 0.0}, out, junction, net, allChannelIds(net));

        assertTrue(out.isEmpty(), "an unassigned rim must not reach the carve");
    }

    @Test
    void sourceTakesTheWidthOfTheChannelLeavingIt() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint source = nodeOfType(net, Endpoint.Type.SOURCE);
        source.elevation = 96.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.SOURCE.addPrimitives(new double[] {0.0, 0.0}, out, source, net, allChannelIds(net));

        assertEquals(1, out.size());
        final SourcePrimitive spring = (SourcePrimitive) out.get(0);
        assertEquals(net.getChannel(source.outgoing).widthAt(0), spring.width(), 1e-12);
        assertEquals(96.0, spring.elevation(), 1e-12);
        assertTrue(spring.width() >= HydrologyTuning.MIN_WIDTH, "width comes through widthFromFlow");
    }

    @Test
    void shiftsTheStoredCoordIntoTheQueriedFrame() {
        final RiverNetwork net = confluenceNetwork();
        final Endpoint junction = nodeOfType(net, Endpoint.Type.JUNCTION);
        junction.elevation = 64.0;
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        HydrologicalFeature.CONFLUENCE.addPrimitives(
                new double[] {10.0, 20.0}, out, junction, net, allChannelIds(net));

        assertEquals(junction.coord[0] - 10.0, out.get(0).coord()[0], 1e-12);
        assertEquals(junction.coord[1] - 20.0, out.get(0).coord()[1], 1e-12);
    }
}
```

The fixture mirrors
`src/test/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetworkSeamGoldenTest.java:43-63`,
which builds the same graph. `NodeSpec` and `EdgeSpec` are public records nested in `RiverNetwork`
(`RiverNetwork.java:109,117`), and the six-argument constructor is at `RiverNetwork.java:127`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*RadialEmissionTest"`
Expected: FAIL — `CONFLUENCE.addPrimitives` is an empty body from Task 2, so `out` is empty and
`confluenceTakesTheWidestChannelMeetingAtTheJunction` fails on `assertEquals(1, out.size())`.

- [ ] **Step 3: Implement the two `addPrimitives` bodies**

In `HydrologicalPrimitive.java`, replace the `SOURCE` constant's body and the `CONFLUENCE` constant's
body:

```java
        SOURCE(() -> SourcePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                final Endpoint endpoint = (Endpoint) args[0];
                final RiverNetwork network = (RiverNetwork) args[1];
                final IntSet emitting = (IntSet) args[2];
                final double width = maxIncidentWidth(endpoint, network, emitting);
                if (width <= 0 || Double.isNaN(endpoint.elevation)) return;
                primitives.add(new SourcePrimitive(
                        VectorOps.sub(endpoint.coord, offset), width, endpoint.elevation));
            }
        },
```

```java
        // :SCHEMA: appended, never reordered; the ordinal is the on-disk type tag, so moving a
        // constant reinterprets every primitive already cached.
        CONFLUENCE(() -> ConfluencePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                final Endpoint endpoint = (Endpoint) args[0];
                final RiverNetwork network = (RiverNetwork) args[1];
                final IntSet emitting = (IntSet) args[2];
                if (countEmitting(endpoint, emitting) < 2) return;
                final double width = maxIncidentWidth(endpoint, network, emitting);
                if (width <= 0 || Double.isNaN(endpoint.elevation)) return;
                primitives.add(new ConfluencePrimitive(
                        VectorOps.sub(endpoint.coord, offset), width, endpoint.elevation));
            }
        };
```

Add both helpers as static methods on the `HydrologicalFeature` enum, after `addPrimitives`:

```java
        /** The widest channel meeting at a node, measured at the spline point touching it. Zero when
         *  no incident channel emitted — a bowl sized off a dropped channel would carve into nothing. */
        private static double maxIncidentWidth(Endpoint endpoint, RiverNetwork network, IntSet emitting) {
            double width = 0;
            for (final int id : endpoint.incoming) {
                if (!emitting.contains(id)) continue;
                final Channel ch = network.getChannel(id);
                width = Math.max(width, ch.widthAt(ch.numPts() - 1));
            }
            if (endpoint.outgoing != -1 && emitting.contains(endpoint.outgoing)) {
                width = Math.max(width, network.getChannel(endpoint.outgoing).widthAt(0));
            }
            return width;
        }

        /** Incident channels that actually emitted river primitives. */
        private static int countEmitting(Endpoint endpoint, IntSet emitting) {
            int count = 0;
            for (final int id : endpoint.incoming) if (emitting.contains(id)) count++;
            if (endpoint.outgoing != -1 && emitting.contains(endpoint.outgoing)) count++;
            return count;
        }
```

Add the imports `it.unimi.dsi.fastutil.ints.IntSet` and
`me.batata_1.fractal_terrain.hydrology.network.RiverNetwork` to `HydrologicalPrimitive.java`.

`for (final int id : endpoint.incoming)` over a fastutil `IntSet` boxes through the `Integer` iterator.
`addPrimitives` runs once per tile build, which `performance.md` places above the hot line, and
`features/README.md` already names it as the one member of this package allowed to allocate. Leave it
unmarked; do not hand-roll an `IntIterator` here.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle spotlessApply` then `gradle test --tests "*RadialEmissionTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Wire the emission into `collectPrimitives`**

In `RiverNetwork.java`, build the emitting-id set at the end of phase 1 (immediately after the
`emitting.add(ch)` loop closes):

```java
        final IntSet emittingIds = new IntOpenHashSet(emitting.size());
        for (final Channel ch : emitting) emittingIds.add(ch.channelId);
```

Then replace the phase-2 node loop:

```java
        for (Endpoint en : nodes.values()) {
            if (en.type == Endpoint.Type.SOURCE) {
                HydrologicalFeature.SOURCE.addPrimitives(offset, primitives, en, this, emittingIds);
            }

            // Degree three or more: two channels arriving is what makes a junction a confluence.
            if (en.type == Endpoint.Type.JUNCTION && en.incoming.size() >= 2) {
                HydrologicalFeature.CONFLUENCE.addPrimitives(offset, primitives, en, this, emittingIds);
            }

            // TODO: fix this, not all drains are deltas
            if (en.type == Endpoint.Type.DRAIN) HydrologicalFeature.DELTA.addPrimitives(offset, primitives, en);
        }
```

Add the import `it.unimi.dsi.fastutil.ints.IntOpenHashSet` (`IntSet` may already be imported; check
the existing fastutil import block at lines 7-11).

- [ ] **Step 6: Run the full suite**

Run: `gradle spotlessApply` then `gradle build` then `gradle test`
Expected: build green; the failure set is still the nine baseline failures with unchanged messages.
No carve behaviour has changed yet — the radial pass does not exist until Task 5 — so any *new*
failure here is a regression in emission, not in carving.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java \
        src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/features/RadialEmissionTest.java
git commit -m "feat(hydrology): emit confluence and sized source primitives"
```

---

## Task 5: The radial carve pass

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialCarveTest.java` (create)

**Interfaces:**
- Consumes: `RadialPrimitive`, `RadialProfile` (Tasks 1-3).
- Produces: `carvePrimitive` renamed to `carveRiverPrimitive`; new private
  `carveRadialPrimitive(RadialPrimitive, double startX, double startZ, double resolution, int gridSize, float[] acc, long[] typeMask, float[] dist, float[] lut, float[] elevs)`.
  `computeRiverGrid`'s public signature and return value are **unchanged** — it still returns the index
  bounding the river run.

**Read `docs/superpowers/specs/2026-09-02-radial-primitive-carve-design.md` decisions D3 through D6
before writing this task.** Each of the three merge properties is a place where the arithmetic silently
does the wrong thing, and the tests below are written to catch exactly those.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialCarveTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.batata_1.fractal_terrain.FractalTerrainConfig;
import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
import me.batata_1.fractal_terrain.hydrology.features.ConfluencePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.DeltaPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import org.junit.jupiter.api.Test;

/**
 * The three merge properties the radial pass depends on. Each is a case where the recurrence produces
 * a plausible-looking bowl in isolation and destroys the river carve where the two overlap.
 *
 * <p>Geometry mirrors {@code ComputeRiverGridTest}: resolution 1.0 on integer coordinates, so every
 * sampled radius lands on an exact LUT entry and interpolation is exact.
 */
class RadialCarveTest {

    private static final int GRID = 16;
    private static final double RES = 1.0;
    private static final double CENTRE = 8.0;

    /** A straight river knot whose normal points along +X, centred on the lattice. */
    private static RiverPrimitive knot(double cx, double elevation) {
        return new RiverPrimitive(
                new double[] {cx, CENTRE}, 5.0, RosgenType.A, new double[] {1.0, 0.0}, 0.0, 2.0, elevation, 0L);
    }

    private static ConfluencePrimitive bowl(double width, double elevation) {
        return new ConfluencePrimitive(new double[] {CENTRE, CENTRE}, width, elevation);
    }

    private static RiverInfluenceCarve.GridBuffers buffers() {
        final RiverInfluenceCarve.GridBuffers b = new RiverInfluenceCarve.GridBuffers();
        b.ensure(GRID, RiverInfluenceCarve.maxLutLen(GRID, RES));
        return b;
    }

    private static int idx(int row, int col) {
        return row * GRID + col;
    }

    private static double depthOf(double width) {
        return FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width);
    }

    private static void carve(RiverInfluenceCarve.GridBuffers b, List<HydrologicalPrimitive> primitives) {
        RiverInfluenceCarve.computeRiverGrid(
                0,
                0,
                RES,
                GRID,
                primitives,
                b.acc,
                b.typeMask,
                b.dist,
                b.lut,
                b.perpRow,
                b.perpCol,
                b.tangRow,
                b.tangCol,
                null);
    }

    /** D5: a bowl reaching ground no river touched carves to its own law, not toward the zero fill. */
    @Test
    void carvesToItsOwnLawWhereNoRiverReached() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertEquals(
                100.0 - depthOf(4.0),
                b.acc[3 * centre],
                1e-3,
                "an ungated min against the zero-filled acc would clamp the floor to 0");
        assertTrue(b.acc[3 * centre + 2] > 0, "the bowl must claim the cell it carved");
    }

    /** D4: a bowl whose rim sits above an already-carved river bed leaves that bed alone. */
    @Test
    void neverLiftsARiverBedItOverlaps() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        final RiverInfluenceCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float riverBed = riverOnly.acc[3 * idx(8, 8)];

        // Rim 20 above the river's, so the bowl floor still sits above the river bed it overlaps.
        carve(b, List.of(knot(CENTRE, 100.0), bowl(4.0, 120.0)));

        assertEquals(
                riverBed,
                b.acc[3 * idx(8, 8)],
                1e-3,
                "the first radial primitive takes weight 1, so without the clamp it overwrites the bed");
    }

    /** D6: a cell in the bowl's square footprint but outside its disc keeps the river's claim. */
    @Test
    void keepsTheRiverWeightAtCellsOutsideItsDisc() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        // The knot at x = 4 reaches (4, 4); the bowl's AABB covers rows/cols 4..12 but its disc,
        // radius 4 about (8, 8), does not reach the corner at distance sqrt(32).
        carve(b, List.of(knot(4.0, 100.0), bowl(4.0, 100.0)));

        assertTrue(
                b.acc[3 * idx(4, 4) + 2] > 0,
                "assigning the weight lane instead of maxing it would zero the river's claim here");
    }

    /** The bowl publishes a water surface, or the recurrence drains it toward zero. */
    @Test
    void publishesItsOwnWaterSurface() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        final int centre = idx(8, 8);
        assertEquals(
                100.0 + HydrologicalPrimitive.waterLine(4.0),
                b.acc[3 * centre + 1],
                1e-3,
                "water sits below the rim by the stepped waterLine offset");
    }

    /** The type mask names the family that won the cell, so the paint side can tell a pool from a bed. */
    @Test
    void stampsTheConfluenceFamilyOnTheCellsItWins() {
        final RiverInfluenceCarve.GridBuffers b = buffers();
        carve(b, List.of(bowl(4.0, 100.0)));

        assertEquals(
                HydrologicalPrimitive.HydrologicalFeature.CONFLUENCE,
                HydrologicalPrimitive.HydrologicalFeature.unpack(b.typeMask[idx(8, 8)]));
    }

    /** D2's filter: a non-river, non-radial tail entry must leave every lane byte-identical. */
    @Test
    void ignoresANonRadialTailPrimitive() {
        final RiverInfluenceCarve.GridBuffers riverOnly = buffers();
        carve(riverOnly, List.of(knot(CENTRE, 100.0)));
        final float[] accBefore = riverOnly.acc.clone();
        final long[] maskBefore = riverOnly.typeMask.clone();
        // dist is deliberately not compared: the radial pass reseeds it, so both runs leave it at
        // UNSET_MIN_DIST and the comparison would pass without asserting anything.

        // DELTA sorts between SOURCE and CONFLUENCE and implements no radial interface, so the second
        // pass must walk straight past it rather than treat the list tail as carveable.
        final RiverInfluenceCarve.GridBuffers withDelta = buffers();
        carve(withDelta, List.of(knot(CENTRE, 100.0), new DeltaPrimitive(new double[] {CENTRE, CENTRE})));

        assertArrayEquals(accBefore, withDelta.acc, "a delta in the tail perturbed the merged surface");
        assertArrayEquals(maskBefore, withDelta.typeMask, "a delta in the tail perturbed the type mask");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*RadialCarveTest"`
Expected: FAIL — `carvesToItsOwnLawWhereNoRiverReached` reports `0.0` against the expected floor,
because no radial pass runs and `acc` is still the zero fill.

- [ ] **Step 3: Rename `carvePrimitive`**

Rename the private method `carvePrimitive` to `carveRiverPrimitive` and update its single call site
inside `computeRiverGrid`. Leave `carvePrimitiveInfluence` alone — it serves the shell path, which this
plan does not touch. Update the method's javadoc first line to name the family it carves:

```java
    /** One river primitive's contribution, clipped to the lattice points its footprint reaches.
     *  Primitive-outer so the profile, the seed, the width-invariant extents and the LUT are computed
     *  once, not per point. */
```

- [ ] **Step 4: Add the second pass to `computeRiverGrid`**

Replace the tail of `computeRiverGrid` (from the `int stop = 0;` line to the `return stop;`):

```java
        int stop = 0;
        while (stop < primitives.size() && primitives.get(stop) instanceof RiverPrimitive river) {
            carveRiverPrimitive(
                    river,
                    startX,
                    startZ,
                    resolution,
                    gridSize,
                    acc,
                    typeMask,
                    dist,
                    lut,
                    perpRow,
                    perpCol,
                    tangRow,
                    tangCol,
                    elevs);
            stop++;
        }

        // Reseeded so radial penetration ranks among radial primitives only: a disc's radius scale and
        // a channel's rectangle scale are not comparable, and letting them compete would rank a bowl
        // against a bed by two different measures of "inside".
        Arrays.fill(dist, 0, points, (float) UNSET_MIN_DIST);

        // SOURCE and CONFLUENCE are not adjacent in comparator order (DELTA sorts between them), so
        // the walk runs to the end rather than resuming on a contiguous run.
        for (int i = stop; i < primitives.size(); i++) {
            if (primitives.get(i) instanceof RadialPrimitive radial) {
                carveRadialPrimitive(radial, startX, startZ, resolution, gridSize, acc, typeMask, dist, lut, elevs);
            }
        }
        return stop;
```

- [ ] **Step 5: Add `carveRadialPrimitive`**

Add to the private-method section, immediately after `carveRiverPrimitive`:

```java
    /**
     * One radial primitive's contribution, clipped to the lattice points its disc reaches. Runs after
     * every river against a reseeded {@code dist}, so it deepens the merged river surface rather than
     * competing with it for the same distance slot.
     */
    private static void carveRadialPrimitive(
            RadialPrimitive radial,
            double startX,
            double startZ,
            double resolution,
            int gridSize,
            float[] acc,
            long[] typeMask,
            float[] dist,
            float[] lut,
            float[] elevs) {
        final double cx = radial.coord()[0], cz = radial.coord()[1];
        final double radius = radial.getRadius();
        if (radius <= 0) return;

        // :PERF: conservative AABB clip; floor/ceil so a too-wide range is harmless while a too-narrow
        // one would silently drop carve -- the exact disc test still runs per lattice point.
        final long rowLo = (long) Math.floor((cx - radius - startX) / resolution);
        final long rowHi = (long) Math.ceil((cx + radius - startX) / resolution);
        final long colLo = (long) Math.floor((cz - radius - startZ) / resolution);
        final long colHi = (long) Math.ceil((cz + radius - startZ) / resolution);
        if (rowHi < 0 || rowLo > gridSize - 1 || colHi < 0 || colLo > gridSize - 1) return;
        final int rowMin = (int) Math.max(rowLo, 0);
        final int rowMax = (int) Math.min(rowHi, gridSize - 1);
        final int colMin = (int) Math.max(colLo, 0);
        final int colMax = (int) Math.min(colHi, gridSize - 1);

        // The LUT spans only the radii the clipped box actually reaches. This is what caps n at the
        // grid diagonal: a full-radius table would want radius/resolution entries, which at
        // GRID_RESOLUTION overruns what maxLutLen sizes the buffer for.
        final double x0 = startX + rowMin * resolution, x1 = startX + rowMax * resolution;
        final double z0 = startZ + colMin * resolution, z1 = startZ + colMax * resolution;
        final double nearX = Math.max(0.0, Math.max(x0 - cx, cx - x1));
        final double nearZ = Math.max(0.0, Math.max(z0 - cz, cz - z1));
        final double radMin = Math.sqrt(nearX * nearX + nearZ * nearZ);
        final double farX = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
        final double farZ = Math.max(Math.abs(z0 - cz), Math.abs(z1 - cz));
        final double radMax = Math.min(Math.sqrt(farX * farX + farZ * farZ), radius);
        if (radMin > radMax) return;

        final double invStep = 1.0 / resolution;
        final int baseIdx = (int) Math.floor(radMin * invStep);
        final int n = (int) Math.floor(radMax * invStep) - baseIdx + 2;

        final double width = radial.width();
        final double elevation = radial.elevation();
        final double invRadius = 1.0 / radius;
        final double depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width);
        final float waterSurface = (float) (elevation + HydrologicalPrimitive.waterLine(width));
        final long packed = radial.getType().pack(0);
        radial.getRadialProfile().sampleRadialSection(lut, n, resolution, baseIdx, elevation, invRadius, depth);

        for (int row = rowMin; row <= rowMax; row++) {
            final int rowBase = row * gridSize;
            final double ddx = (startX + row * resolution) - cx;
            for (int col = colMin; col <= colMax; col++) {
                final int i = rowBase + col;
                final int a = 3 * i;
                final double ddz = (startZ + col * resolution) - cz;
                // A circle admits no affine row/column split the way the river's two projections do,
                // so the true distance is computed per cell rather than tabulated per axis.
                final double rad = Math.sqrt(ddx * ddx + ddz * ddz);
                final double d = rad * invRadius;
                final double mask = d <= 1.0 ? 1.0 : 0.0;
                final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
                final double w = t * t * (3.0 - 2.0 * t) * mask;

                final double f = rad * invStep - baseIdx;
                final int i0 = Math.clamp((int) f, 0, n - 2);
                final double sampled = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
                final double bounded = (elevs != null) ? Math.min(elevs[i], sampled) : sampled;
                // Gated on the river pass's weight, not on acc alone: acc is zero-filled, so an
                // unconditional min would clamp a bowl standing on high ground down to zero.
                final double h = acc[a + 2] > 0 ? Math.min(acc[a], bounded) : bounded;

                dist[i] = (float) ((1 - w) * dist[i] + w * d);
                acc[a] = (float) ((1 - w) * acc[a] + w * h);
                acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
                typeMask[i] = w > 0.5 ? packed : typeMask[i];
                // Maxed rather than assigned: cells inside the square footprint but outside the disc
                // take w = 0, and assigning would erase the river's own claim on them.
                acc[a + 2] = Math.max(acc[a + 2], (float) (1 - Math.clamp(dist[i], 0, 1)));
            }
        }
    }
```

Add the import `me.batata_1.fractal_terrain.hydrology.features.RadialPrimitive` to
`RiverInfluenceCarve.java`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `gradle spotlessApply` then `gradle test --tests "*RadialCarveTest" --tests "*ComputeRiverGridTest"`
Expected: both classes PASS. `ComputeRiverGridTest` is the regression gate — its 12 existing
assertions cover the river pass, and the radial pass must leave every one of them untouched.

- [ ] **Step 7: Run the full suite and compare against the baseline**

Run: `gradle build` then `gradle test`
Expected: the nine baseline failures, with the messages recorded in
`.superpowers/conventions-alignment/post-migration-failures.txt`.

This is the task that changes generated terrain, so a golden shift here is real. `RiverGoldenTest`'s
two failures report `synthetic field produced no local channels — fixture is degenerate`, so they never
reach a traced network and cannot see the new pass; `MeandersGoldenTest` asserts network topology, not
carved output. If either changes its *message*, stop and report rather than re-baselining.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RiverInfluenceCarve.java \
        src/test/java/me/batata_1/fractal_terrain/hydrology/profile/RadialCarveTest.java
git commit -m "feat(hydrology): carve confluences and sources in a radial pass"
```

---

## Task 6: Documentation

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/CLAUDE.md`
- Modify: `docs/superpowers/specs/CLAUDE.md` (spec status: proposed -> implemented)

Follow `.claude/conventions/documentation.md`: `CLAUDE.md` is a pure index of What / When-to-read rows;
`README.md` carries the invisible knowledge — Overview, Architecture, Design decisions, Invariants.
`.claude/conventions/temporal.md` requires the timeless present: describe what the code *is*, never what
this change *did*.

- [ ] **Step 1: `features/CLAUDE.md`** — add rows for `RadialPrimitive.java` ("The interface the carve's
  radial pass dispatches on: disc extents, rim elevation, radial profile") and `ConfluencePrimitive.java`
  ("The pool at a junction of degree three or more; sized by the widest channel meeting there").
  `SourcePrimitive.java`'s row changes from "position only" to "a headwater spring; carves a cone sized
  by the channel leaving it". `PositionOnlyPrimitive.java`'s row drops `SourcePrimitive` from its list.

- [ ] **Step 2: `features/README.md`** — the Overview's "only two of them are ever minted and only one
  of those is ever carved" is now wrong: three families are minted (`RIVER`, `SOURCE`, `CONFLUENCE`,
  plus `DELTA`) and three are carved. The Architecture section's "Carve reaches river primitives only"
  paragraph becomes a two-pass description. Add to Invariants: a family that carves radially must
  implement `RadialPrimitive`, because the second pass dispatches on that interface and nothing else.
  Keep the existing invariant that an emitting family must sort after `RIVER`.

- [ ] **Step 3: `profile/CLAUDE.md`** — add a row for `RadialProfile.java` ("The two radial shape laws:
  the confluence's parabolic bowl and the source's cone; the radial twin of `RosgenProfile`").

- [ ] **Step 4: `profile/README.md`** — the Architecture section's merge-law paragraph gains the second
  pass and the three properties it rests on (spec D3-D6): the reseeded `dist`, the gated clamp against
  the merged river surface, and the maxed weight lane. Rename every `carvePrimitive` reference to
  `carveRiverPrimitive`. The "**`d` is a rectangle scale, not a radius**" heading needs a sentence
  saying that for a radial primitive it *is* a radius scale, and that both are dimensionless and read
  against the same `UNSET_MIN_DIST` and blend width.

- [ ] **Step 5: `network/CLAUDE.md`** — `RiverNetwork.java`'s row notes that `collectPrimitives` emits
  confluence and source discs alongside river primitives, gated on which channels actually emitted.

- [ ] **Step 6: `populatenoise/CLAUDE.md`** — `PopulateNoiseStep.java`'s row: `fineGrainedPrimitivePass`
  merges two primitive families, rivers then radials, not one.

- [ ] **Step 7: `ARCHITECTURE.md`** — the "Per-chunk bed carve" bullet gains the radial pass. In the
  superseded-designs paragraph, the phrase "the `ConfluencePrimitive` junction ray-set" must be amended
  so it reads as the removed *mechanism* rather than the removed name — the name is live again on
  different mechanics, and "None of those symbols exist any more" is now false for it.

- [ ] **Step 8: `docs/superpowers/specs/CLAUDE.md`** — change the radial-primitive spec row's status
  from *proposed* to *implemented*, naming the commit range.

- [ ] **Step 9: Verify and commit**

Run: `gradle build`
Expected: green. Re-read each edited `README.md` against the code as landed — `documentation.md`
requires every docstring and README claim to be grounded in the code as it is now.

```bash
git add ARCHITECTURE.md docs/superpowers/specs/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/features/README.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/profile/README.md \
        src/main/java/me/batata_1/fractal_terrain/hydrology/network/CLAUDE.md \
        src/main/java/me/batata_1/fractal_terrain/world/gen/populatenoise/CLAUDE.md
git commit -m "docs: record the radial carve pass and the RadialPrimitive family"
```

---

## Deferred, with the spec's reasoning

Do not implement these; they are recorded so an executor does not add them on initiative.

- **`channelContains`** stays defaulted on both radial records (spec D15). The wetted sub-disc is
  profile-dependent, and its only consumer is reached from `debug/Infinite3DVisualizer`.
- **The tile shell carve** keeps breaking at the first non-river entry (spec D12).
- **No `comparator` tie-break** for radial primitives (spec D14).
- **The source bowl shrinks** from ~10 blocks to ~3 at `MIN_WIDTH` (spec Q1). If it should stay
  visible, the fix is a per-constant radius multiplier on `RadialProfile`, not a special case in the
  emission path — raise it rather than applying it.
