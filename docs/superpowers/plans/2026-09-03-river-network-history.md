# River-Network History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `RiverNetwork`'s write-only `previousStates` and never-emitted `removedPaths` with one `ArrayDeque<HydrologicalPrimitive>` of already-minted oxbow and abandoned-channel primitives that carry the step they were cut at.

**Architecture:** A new public `HistoricPrimitive` interface — modelled on the existing `RadialPrimitive` — gives `OxbowLakePrimitive` and `AbandonedRiverPrimitive` a `time`/`width`/`influence`/`elevation` payload and an influence-circle footprint. `RiverNetwork` mints them at the cutoff and collision that shed them, evicts by age, and re-mints them into the caller's frame through `HydrologicalFeature.addPrimitives` at collect. Elevation and influence are deferred to a later pass driven through a new `remapHistory` seam.

**Tech Stack:** Java 21, Fabric 1.20.1, JUnit 5, fastutil, palantirJavaFormat via Spotless.

**Spec:** `docs/superpowers/specs/2026-09-03-river-network-history-design.md`

## Global Constraints

- **History stays off.** The three-argument `RiverNetwork` constructor keeps passing `false`. Do not enable it anywhere. Spec D8.
- **Generated terrain must not move.** No golden or carve output may change. The verification bar is failure-*message* equality against `.superpowers/conventions-alignment/post-migration-failures.txt`, not just matching test names.
- **`HydrologicalFeature` is append-only.** Never reorder or remove a constant; the ordinal is the on-disk type tag.
- **Only `OXBOW_LAKE` and `ABANDONED_RIVER` bodies change on disk.** `RIVER`, `SOURCE`, `DELTA`, `WATERFALL` and `CONFLUENCE` payloads stay byte-identical.
- **Docstring budgets are hard** (`.claude/conventions/documentation.md` Tier 3): field 1 line, method 3, class 10. At most one line says what the thing is; the rest say why or where in the pipeline.
- **Run `gradle spotlessApply` before every commit.** The build enforces palantirJavaFormat.
- **A worktree needs `libs/onnxruntime/teste.jar` copied in** (`libs/` is git-ignored) or the build reports ~132 phantom errors.

**Read before your first edit** (`CLAUDE.md` "Read the guidelines before implementing"): the repo-root `CLAUDE.md`; the `CLAUDE.md` and `README.md` in the directory you are editing; `.claude/conventions/CLAUDE.md`, then `documentation.md`, `intent-markers.md`, `structural.md`, `class-structure.md`, `temporal.md`.

---

## File Structure

| File | Responsibility |
| ---- | -------------- |
| `hydrology/features/HistoricPrimitive.java` | **New.** The shed-feature contract: `width`/`elevation`/`influence`, circle geometry, `DefaultProfile`, and the `resolved` seam. |
| `hydrology/features/OxbowLakePrimitive.java` | A cutoff meander. Record shape follows `SourcePrimitive`. |
| `hydrology/features/AbandonedRiverPrimitive.java` | A captured channel. Same shape. |
| `hydrology/features/PrimitiveCodec.java` | The shared byte layout for the two above, plus their contents-equality and hash. |
| `hydrology/features/HydrologicalPrimitive.java` | `default byte time()`; the two `addPrimitives` bodies that re-mint into the caller's frame. |
| `hydrology/features/PositionOnlyPrimitive.java` | Javadoc only — it no longer names the two history records. |
| `hydrology/network/RiverNetwork.java` | The deque, the mint sites, age eviction, `remapHistory`, the collect loop. |
| `hydrology/meanders/ChannelMigrator.java` | Drops the `recordState` call. |

---

## Task 1: The historic-primitive layer

Gives the two shed families a real payload and a persistent form. Nothing constructs them yet, so this task compiles and tests standalone.

**Files:**
- Create: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/PrimitiveCodec.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/OxbowLakePrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/AbandonedRiverPrimitive.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java` (add `time()` only; the enum bodies are Task 2)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/PositionOnlyPrimitive.java` (javadoc only)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md`
- Modify: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitiveCodecTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, for Task 2:
  - `public interface HistoricPrimitive extends HydrologicalPrimitive, SpatialIndexCircle` with `double width()`, `double elevation()`, `double influence()`, `HistoricPrimitive resolved(double elevation, double influence)`
  - `public record OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation, long seed)`, plus the public 5-arg constructor `OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation)`
  - `public record AbandonedRiverPrimitive(...)` with the same two constructor shapes
  - `HydrologicalPrimitive.time()` returning `byte`, defaulting to `0`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitiveCodecTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Persistence and index geometry for the shed families. The cut step is the point: a reloaded oxbow
 * that forgot its age could never grow the aged profile the record exists to carry.
 */
class HistoricPrimitiveCodecTest {

    private static Stream<HistoricPrimitive> historicPrimitives() {
        return Stream.of(
                new OxbowLakePrimitive(new double[] {12.5, -40.25}, (byte) 7, 6.0, 30.0, 71.5),
                new AbandonedRiverPrimitive(new double[] {-3.0, 8.75}, (byte) 23, 1.25, 9.5, 130.0));
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void roundTripsThroughTheTypeTaggedPayload(HistoricPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.getClass(), reloaded.getClass(), "the type tag did not select the record");
        assertNotSame(original, reloaded);
        assertEquals(original, reloaded, "contents did not survive the round trip");
        assertEquals(original.hashCode(), reloaded.hashCode());
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void keepsTheStepItWasCutAt(HistoricPrimitive original) {
        final HydrologicalPrimitive reloaded = HydrologicalPrimitive.PROTOTYPE.deserialize(original.serialize());

        assertEquals(original.time(), reloaded.time());
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void reportsThePayloadSizeItActuallyWrites(HistoricPrimitive primitive) {
        assertEquals(primitive.byteSize(), primitive.serialize().length);
    }

    @ParameterizedTest
    @MethodSource("historicPrimitives")
    void isIndexedAsADiscOfItsInfluence(HistoricPrimitive primitive) {
        assertEquals(primitive.influence(), primitive.getRadius(), 1e-12);
        assertArrayEquals(primitive.coord(), primitive.getCenter());
    }

    @Test
    void hasNoFootprintUntilItIsResolved() {
        // Minted mid-simulation with influence 0: indexing one before the resolve pass gives it a disc
        // that matches nothing, which is why that pass must run before collectPrimitives.
        final OxbowLakePrimitive unresolved = new OxbowLakePrimitive(new double[] {5.0, 5.0}, (byte) 2, 4.0, 0, 0);
        assertEquals(0.0, unresolved.getRadius(), 1e-12);

        final HistoricPrimitive resolved = unresolved.resolved(64.0, 20.0);

        assertEquals(20.0, resolved.getRadius(), 1e-12);
        assertEquals(64.0, resolved.elevation(), 1e-12);
        assertEquals(4.0, resolved.width(), 1e-12, "resolving must not disturb the width it was cut with");
        assertEquals((byte) 2, resolved.time(), "resolving must not disturb the cut step");
    }

    @Test
    void everyOtherFamilyReportsTheLiveNetworkStep() {
        final RiverPrimitive live =
                new RiverPrimitive(new double[] {0.0, 0.0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0);

        assertEquals((byte) 0, live.time());
    }

    @Test
    void sortsAfterEveryRiverPrimitive() {
        // computeRiverGrid's river loop stops at the first non-river entry; a shed family sorting before
        // RIVER would truncate the river run and silently drop carve.
        final List<HydrologicalPrimitive> primitives = new ObjectArrayList<>(List.of(
                new OxbowLakePrimitive(new double[] {0.0, 0.0}, (byte) 1, 1.0, 1.0, 0.0),
                new RiverPrimitive(new double[] {0.0, 0.0}, 5.0, RiverPrimitive.RosgenType.A, null, 0, 2, 0)));
        primitives.sort(HydrologicalPrimitive.comparator);

        assertTrue(primitives.get(0) instanceof RiverPrimitive, "RIVER must sort first");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*HistoricPrimitiveCodecTest*"`
Expected: FAIL at compilation — `cannot find symbol: class HistoricPrimitive`, and the `OxbowLakePrimitive`/`AbandonedRiverPrimitive` constructors take one argument, not five.

- [ ] **Step 3: Add the codec helpers**

In `PrimitiveCodec.java`, add `import java.util.Objects;` beside the existing imports, then append these members before the closing brace:

```java
    /** A {@link #writeHistoric} payload read back whole, so a record's deserialize stays one statement. */
    record HistoricFields(double[] coord, byte time, double width, double influence, double elevation) {}

    /** Byte cost of a shed feature's body: the coordinate, the cut step, then width/influence/elevation. */
    static long historicByteSize(double[] coord) {
        return coordByteSize(coord) + Byte.BYTES + 3L * Double.BYTES;
    }

    // :SCHEMA: the shed families' body carries time/width/influence/elevation where the other
    // position-only families carry a coordinate alone; only these two are ever written with it, and
    // none has ever been written to a cached tile, so no existing payload can be misread.
    /** Serialized form of a shed feature. {@code seed} is derived from these, so it is never written. */
    static byte[] writeHistoric(double[] coord, byte time, double width, double influence, double elevation) {
        final ByteBuffer buf = ByteBuffer.allocate((int) historicByteSize(coord)).order(ByteOrder.LITTLE_ENDIAN);
        putCoord(buf, coord);
        buf.put(time);
        buf.putDouble(width);
        buf.putDouble(influence);
        buf.putDouble(elevation);
        return buf.array();
    }

    /** Reads back a {@link #writeHistoric} payload. */
    static HistoricFields readHistoric(byte[] rawBytes) {
        final ByteBuffer buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final double[] coord = getCoord(buf);
        final byte time = buf.get();
        final double width = buf.getDouble();
        final double influence = buf.getDouble();
        final double elevation = buf.getDouble();
        return new HistoricFields(coord, time, width, influence, elevation);
    }

    /** Content equality for a shed feature. Exists because records compare {@code double[]} by reference,
     *  which would make every primitive unequal to its own reloaded copy. */
    static boolean historicEquals(HistoricPrimitive self, Object other) {
        if (self == other) return true;
        if (other == null || self.getClass() != other.getClass()) return false;
        final HistoricPrimitive that = (HistoricPrimitive) other;
        return Arrays.equals(self.coord(), that.coord())
                && self.time() == that.time()
                && Double.compare(self.width(), that.width()) == 0
                && Double.compare(self.influence(), that.influence()) == 0
                && Double.compare(self.elevation(), that.elevation()) == 0;
    }

    /** The {@link #historicEquals} counterpart, cached in the record's {@code seed} component. */
    static long historicHash(double[] coord, byte time, double width, double influence, double elevation) {
        int result = Objects.hash(time, width, influence, elevation);
        result = 31 * result + Arrays.hashCode(coord);
        return result;
    }
```

- [ ] **Step 4: Create `HistoricPrimitive`**

Create `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HistoricPrimitive.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.HydrologyProfile;
import me.batata_1.fractal_terrain.math.ds.SpatialIndexCircle;

/**
 * A feature the network has shed — a cutoff meander or a captured channel — carrying the step it was cut
 * at and the channel it was cut from.
 *
 * <p>Public for the same reason {@link RadialPrimitive} is: the pass that resolves these lives outside
 * this package and must name {@link #resolved}. Minted mid-simulation, so elevation and influence arrive
 * only through that call — until it runs {@link #getRadius()} is 0, and a primitive indexed before then
 * has no footprint and silently matches nothing.
 */
public interface HistoricPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    /** Width of the channel this was cut out of; input to the cross-section it does not have yet. */
    double width();

    /** The bed the shed feature sits at; 0 until {@link #resolved}. */
    double elevation();

    /** Index radius, so a shed feature spans what its channel did; 0 until {@link #resolved}. */
    double influence();

    @Override
    default double[] getCenter() {
        return coord();
    }

    @Override
    default double getRadius() {
        return influence();
    }

    /** Skeleton: no cross-section of its own yet, so it blends as a plain influence disc. */
    @Override
    default HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    /** This primitive with its deferred quantities filled in. Abstract because only the record knows its
     *  own canonical constructor. */
    HistoricPrimitive resolved(double elevation, double influence);
}
```

- [ ] **Step 5: Rewrite `OxbowLakePrimitive`**

Replace the whole of `src/main/java/me/batata_1/fractal_terrain/hydrology/features/OxbowLakePrimitive.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import java.util.Arrays;
import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import me.batata_1.fractal_terrain.hydrology.profile.ZoneCategory;
import org.jetbrains.annotations.NotNull;

/**
 * A meander loop cut off from its channel and left as standing water.
 *
 * <p><b>Skeleton.</b> It carries the step that cut it and the width it was cut at, but no water level and
 * no loop geometry, so it carves nothing of its own and blends as a plain {@link DefaultProfile}
 * influence disc. {@link ZoneCategory#LAKE_BED} is reserved below {@link ZoneCategory#BED} for it, so a
 * channel still running through the loop will keep governing the cross-section once this record grows a
 * real profile.
 */
public record OxbowLakePrimitive(
        double[] coord, byte time, double width, double influence, double elevation, long seed)
        implements HistoricPrimitive {

    static final OxbowLakePrimitive PROTOTYPE = new OxbowLakePrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0, 0);

    public OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation) {
        this(
                coord,
                time,
                width,
                influence,
                elevation,
                PrimitiveCodec.historicHash(coord, time, width, influence, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.OXBOW_LAKE;
    }

    @Override
    public HistoricPrimitive resolved(double elevation, double influence) {
        return new OxbowLakePrimitive(coord, time, width, influence, elevation);
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.historicByteSize(coord);
    }

    @Override
    public byte[] serializePrimitive() {
        return PrimitiveCodec.writeHistoric(coord, time, width, influence, elevation);
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final PrimitiveCodec.HistoricFields fields = PrimitiveCodec.readHistoric(rawBytes);
        return new OxbowLakePrimitive(
                fields.coord(), fields.time(), fields.width(), fields.influence(), fields.elevation());
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        return PrimitiveCodec.historicEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    @Override
    public @NotNull String toString() {
        return "Oxbow[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", influence="
                + influence + ", elevation=" + elevation + "]";
    }
}
```

- [ ] **Step 6: Rewrite `AbandonedRiverPrimitive`**

Replace the whole of `src/main/java/me/batata_1/fractal_terrain/hydrology/features/AbandonedRiverPrimitive.java`:

```java
package me.batata_1.fractal_terrain.hydrology.features;

import java.util.Arrays;
import me.batata_1.fractal_terrain.hydrology.profile.DefaultProfile;
import org.jetbrains.annotations.NotNull;

/**
 * A former channel the river has since migrated out of — a dry trace the terrain still remembers.
 *
 * <p><b>Skeleton.</b> It now records how long ago it was abandoned and how wide it ran, but still has no
 * normal and no cross-section, so it carves nothing of its own and blends as a plain
 * {@link DefaultProfile} influence disc. It will most likely end up claiming a shallow, aged variant of
 * the river zones rather than one of its own.
 */
public record AbandonedRiverPrimitive(
        double[] coord, byte time, double width, double influence, double elevation, long seed)
        implements HistoricPrimitive {

    static final AbandonedRiverPrimitive PROTOTYPE =
            new AbandonedRiverPrimitive(new double[] {0.0, 0.0}, (byte) 0, 0, 0, 0);

    public AbandonedRiverPrimitive(double[] coord, byte time, double width, double influence, double elevation) {
        this(
                coord,
                time,
                width,
                influence,
                elevation,
                PrimitiveCodec.historicHash(coord, time, width, influence, elevation));
    }

    @Override
    public HydrologicalFeature getType() {
        return HydrologicalFeature.ABANDONED_RIVER;
    }

    @Override
    public HistoricPrimitive resolved(double elevation, double influence) {
        return new AbandonedRiverPrimitive(coord, time, width, influence, elevation);
    }

    @Override
    public long primitiveByteSize() {
        return PrimitiveCodec.historicByteSize(coord);
    }

    @Override
    public byte[] serializePrimitive() {
        return PrimitiveCodec.writeHistoric(coord, time, width, influence, elevation);
    }

    @Override
    public HydrologicalPrimitive deserializePrimitive(byte[] rawBytes) {
        final PrimitiveCodec.HistoricFields fields = PrimitiveCodec.readHistoric(rawBytes);
        return new AbandonedRiverPrimitive(
                fields.coord(), fields.time(), fields.width(), fields.influence(), fields.elevation());
    }

    // Records compare array components by reference; these compare contents instead.
    @Override
    public boolean equals(Object o) {
        return PrimitiveCodec.historicEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Math.toIntExact(seed);
    }

    @Override
    public @NotNull String toString() {
        return "Abandoned[coord=" + Arrays.toString(coord) + ", time=" + time + ", width=" + width + ", influence="
                + influence + ", elevation=" + elevation + "]";
    }
}
```

- [ ] **Step 7: Add `time()` to the interface**

In `HydrologicalPrimitive.java`, immediately after the existing `double[] coord();` declaration, insert:

```java
    /** Migration step this primitive was cut at; 0 means it belongs to the live network. */
    default byte time() {
        return 0;
    }
```

- [ ] **Step 8: Drop the two records from `PositionOnlyPrimitive`'s javadoc**

In `PositionOnlyPrimitive.java`, replace this paragraph:

```java
 * <p>{@link DeltaPrimitive}, {@link WaterfallPrimitive}, {@link OxbowLakePrimitive} and {@link
 * AbandonedRiverPrimitive} implement this and keep only {@code getType()}, {@code
 * deserializePrimitive()} and their {@code coord} component — {@code equals}/{@code hashCode} stay on
 * each record because an interface default cannot override {@link Object}'s.
```

with:

```java
 * <p>{@link DeltaPrimitive} and {@link WaterfallPrimitive} implement this and keep only {@code
 * getType()}, {@code deserializePrimitive()} and their {@code coord} component — {@code equals}/{@code
 * hashCode} stay on each record because an interface default cannot override {@link Object}'s. The shed
 * families outgrew it and moved to {@link HistoricPrimitive}.
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `gradle spotlessApply` then `gradle test --tests "*HistoricPrimitiveCodecTest*"`
Expected: PASS, 11 tests — four parameterized methods over two fixtures, plus three singles.

If `roundTripsThroughTheTypeTaggedPayload` fails on `hashCode`, the `seed` component is being serialized — it must not be; check `writeHistoric` writes exactly five values.

- [ ] **Step 10: Update the two index files**

In `src/main/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md`, add this row directly beneath the `PositionOnlyPrimitive.java` row:

```markdown
| `HistoricPrimitive.java`        | Public mixin for the shed features: an influence circle carrying the cut step, the channel width, and the elevation/influence a later pass resolves | Adding a shed feature, or wiring the pass that resolves their deferred elevation and influence |
```

In `src/test/java/me/batata_1/fractal_terrain/hydrology/features/CLAUDE.md`, add this row beneath the `HydrologicalFeaturePackTest.java` row:

```markdown
| `HistoricPrimitiveCodecTest.java` | The shed families' payload: type-tagged round trip, the cut step surviving it, the byte-size claim, and the zero-radius window before `resolved` | Changing the shed-feature payload, `HistoricPrimitive`'s geometry, or what `resolved` is allowed to disturb |
```

Note: this index is already missing rows for `RadialEmissionTest.java` and `RadialPrimitiveCodecTest.java`. That is pre-existing drift from the radial work — leave it; do not expand this task into repairing it.

- [ ] **Step 11: Commit**

```bash
gradle spotlessApply
gradle build
git add src/main/java/me/batata_1/fractal_terrain/hydrology/features src/test/java/me/batata_1/fractal_terrain/hydrology/features
git commit -m "feat(hydrology): give the shed primitives a cut step, width and deferred bed

OxbowLakePrimitive and AbandonedRiverPrimitive outgrew PositionOnlyPrimitive:
both now carry the migration step that cut them, the channel width they were
cut at, and an elevation and influence a later pass resolves. HistoricPrimitive
holds that layout once, mirroring RadialPrimitive.

byte time() defaults to 0 on the interface, so no other family's on-disk body
changes.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01J6NtL3Lq3UWPfEpHBKAuES"
```

---

## Task 2: The network history rewrite

Replaces both history fields with the deque, mints into it at the two sites that shed geometry, evicts by age, and emits through the feature enum.

**Files:**
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetwork.java`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/HydrologicalPrimitive.java` (the two `addPrimitives` bodies)
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/ChannelMigrator.java:86`
- Modify: `src/main/java/me/batata_1/fractal_terrain/hydrology/features/README.md`
- Modify: `src/test/java/me/batata_1/fractal_terrain/hydrology/network/CLAUDE.md`
- Test: `src/test/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetworkHistoryTest.java`

**Interfaces:**
- Consumes from Task 1: `HistoricPrimitive`, both records' 5-argument constructors, `HydrologicalPrimitive.time()`.
- Produces: `public void RiverNetwork.remapHistory(UnaryOperator<HydrologicalPrimitive> resolver)` — the seam `ChannelElevationAssigner` will later drive. Nothing else in this plan consumes it; it is the deliverable's public surface.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/batata_1/fractal_terrain/hydrology/network/RiverNetworkHistoryTest.java`:

```java
package me.batata_1.fractal_terrain.hydrology.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.batata_1.fractal_terrain.hydrology.features.HistoricPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.HydrologicalPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.OxbowLakePrimitive;
import me.batata_1.fractal_terrain.hydrology.features.RiverPrimitive.RosgenType;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.EdgeSpec;
import me.batata_1.fractal_terrain.hydrology.network.RiverNetwork.NodeSpec;
import org.junit.jupiter.api.Test;

/**
 * The history deque: what a cutoff sheds, how long it is kept, and what frame it is emitted in.
 *
 * <p>Every production caller builds with history off, so without this gate nothing in the history path
 * is executed by any test at all.
 */
class RiverNetworkHistoryTest {

    private static final int GRID = 512;
    private static final double RESAMPLE_DIST = 2.0;
    private static final int MAX_SAVED_STATES = 3;

    /** Flow 100 gives width 4.0, so manageCutoffs searches a radius of sqrt(4) = 2.0. */
    private static final double FLOW = 100.0;

    /** Return-leg offset, well inside that 2.0 radius so the fold is found whatever the resample does. */
    private static final double HAIRPIN_GAP = 1.0;

    /** A channel that folds back over itself inside the cutoff search radius, so a cut is forced. */
    private static List<double[]> hairpin(double baseX, double baseZ) {
        final List<double[]> pts = new ArrayList<>();
        for (double x = baseX; x <= baseX + 100.0; x += 2.0) pts.add(new double[] {x, baseZ});
        for (double x = baseX + 100.0; x >= baseX + 10.0; x -= 2.0) pts.add(new double[] {x, baseZ + HAIRPIN_GAP});
        for (double z = baseZ + HAIRPIN_GAP; z <= baseZ + 100.0; z += 2.0) pts.add(new double[] {baseX + 10.0, z});
        return pts;
    }

    /** Two disjoint hairpins, so two cutoffs can be driven at two different steps. */
    private static RiverNetwork twoHairpins() {
        final List<double[]> a = hairpin(100.0, 100.0);
        final List<double[]> b = hairpin(300.0, 300.0);
        final double[] aHead = a.get(0);
        final double[] aTail = a.get(a.size() - 1);
        final double[] bHead = b.get(0);
        final double[] bTail = b.get(b.size() - 1);
        final List<NodeSpec> nodeSpecs = List.of(
                new NodeSpec(aHead[0], aHead[1], Endpoint.Type.SOURCE),
                new NodeSpec(aTail[0], aTail[1], Endpoint.Type.DRAIN),
                new NodeSpec(bHead[0], bHead[1], Endpoint.Type.SOURCE),
                new NodeSpec(bTail[0], bTail[1], Endpoint.Type.DRAIN));
        final List<EdgeSpec> edgeSpecs = List.of(new EdgeSpec(0, 1, a, FLOW), new EdgeSpec(2, 3, b, FLOW));
        return new RiverNetwork(GRID, nodeSpecs, edgeSpecs, true, MAX_SAVED_STATES, RESAMPLE_DIST);
    }

    /** Reads the deque without changing it; remapHistory is the only window onto history. */
    private static List<HydrologicalPrimitive> history(RiverNetwork net) {
        final List<HydrologicalPrimitive> seen = new ArrayList<>();
        net.remapHistory(p -> {
            seen.add(p);
            return p;
        });
        return seen;
    }

    /** Types every point A; the classifier is not what these tests are about. */
    private static ChannelTyper flatTyper() {
        return new ChannelTyper() {
            @Override
            public void prepare(RiverNetwork network) {}

            @Override
            public RosgenType[] typesFor(Channel channel) {
                final RosgenType[] types = new RosgenType[channel.numPts()];
                Arrays.fill(types, RosgenType.A);
                return types;
            }
        };
    }

    @Test
    void aCutoffShedsOxbowsStampedWithTheStepAndTheChannelWidth() {
        final RiverNetwork net = twoHairpins();

        net.manageCutoffs(net.getChannels().get(0), 3);

        final List<HydrologicalPrimitive> shed = history(net);
        assertFalse(shed.isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");
        for (final HydrologicalPrimitive p : shed) {
            assertTrue(p instanceof OxbowLakePrimitive, "a cutoff sheds oxbows, not " + p.getClass());
            final OxbowLakePrimitive oxbow = (OxbowLakePrimitive) p;
            assertEquals((byte) 3, oxbow.time(), "the cut step is the primitive's age");
            assertTrue(oxbow.width() > 0, "width comes from the channel it was cut out of");
            assertEquals(0.0, oxbow.influence(), 1e-12, "influence is resolved later, not at mint");
            assertEquals(0.0, oxbow.elevation(), 1e-12, "elevation is resolved later, not at mint");
        }
    }

    @Test
    void dropsHistoryOlderThanTheStepWindow() {
        final RiverNetwork net = twoHairpins();
        final List<Channel> channels = net.getChannels();

        net.manageCutoffs(channels.get(0), 1);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the first hairpin produced no cutoff");

        // MAX_SAVED_STATES is 3, so at step 9 the step-1 entries are five steps past the window.
        net.manageCutoffs(channels.get(1), 9);

        final List<HydrologicalPrimitive> shed = history(net);
        assertFalse(shed.isEmpty(), "fixture is degenerate: the second hairpin produced no cutoff");
        for (final HydrologicalPrimitive p : shed) {
            assertEquals((byte) 9, p.time(), "everything outside the window must have been evicted");
        }
    }

    @Test
    void resolutionFillsTheDeferredElevationAndInfluence() {
        final RiverNetwork net = twoHairpins();
        net.manageCutoffs(net.getChannels().get(0), 2);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");

        net.remapHistory(p -> ((HistoricPrimitive) p).resolved(64.0, 12.0));

        for (final HydrologicalPrimitive p : history(net)) {
            assertEquals(64.0, ((HistoricPrimitive) p).elevation(), 1e-12);
            assertEquals(12.0, p.getRadius(), 1e-12, "a resolved primitive finally has a footprint");
            assertEquals((byte) 2, p.time(), "resolving must not disturb the cut step");
        }
    }

    @Test
    void emissionShiftsShedPrimitivesIntoTheQueriedFrame() {
        final RiverNetwork net = twoHairpins();
        net.manageCutoffs(net.getChannels().get(0), 4);
        final List<HydrologicalPrimitive> shed = history(net);
        assertFalse(shed.isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");
        final HydrologicalPrimitive stored = shed.get(0);
        final List<HydrologicalPrimitive> out = new ArrayList<>();

        stored.getType().addPrimitives(new double[] {10.0, 20.0}, out, stored);

        assertEquals(1, out.size());
        assertEquals(stored.coord()[0] - 10.0, out.get(0).coord()[0], 1e-12);
        assertEquals(stored.coord()[1] - 20.0, out.get(0).coord()[1], 1e-12);
        assertEquals(stored.time(), out.get(0).time(), "the shift must not disturb the cut step");
    }

    @Test
    void collectEmitsShedPrimitivesAlongsideTheLiveNetwork() {
        final RiverNetwork net = twoHairpins();
        net.manageCutoffs(net.getChannels().get(0), 6);
        assertFalse(history(net).isEmpty(), "fixture is degenerate: the hairpin produced no cutoff");
        // collectPrimitives reads a bed elevation per emitted river point; no assigner ran here.
        for (final Channel ch : net.getChannels()) ch.bedElevations = new double[ch.numPts()];

        final List<HydrologicalPrimitive> out =
                net.collectPrimitives(10.0, 20.0, id -> true, flatTyper(), (x, z, bed, w, normal, type) -> 8.0);

        assertTrue(
                out.stream().anyMatch(p -> p instanceof OxbowLakePrimitive),
                "history must reach the same list the carve collects through");
    }
}
```

If every test fails on `fixture is degenerate`, the hairpin is not folding tightly enough for
`manageCutoffs` to find it — tighten `HAIRPIN_GAP` toward 0.5 rather than weakening any assertion. The
search radius is `sqrt(channel.widthAt(i))`, and `widthAt` is `HydrologyTuning.widthFromFlow(flow)`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle test --tests "*RiverNetworkHistoryTest*"`
Expected: FAIL at compilation — `cannot find symbol: method remapHistory(...)`.

- [ ] **Step 3: Replace the history fields**

In `RiverNetwork.java`, replace this block (currently lines 60-64):

```java
    // history (only populated when savePreviousStates is true)
    private final boolean savePreviousStates;
    private final int maxSavedStates;
    private final ArrayDeque<List<List<double[]>>> previousStates = new ArrayDeque<>();
    private final List<RemovedPath> removedPaths = new ObjectArrayList<>();
```

with:

```java
    // history (only populated when saveHistory is true)
    private final boolean saveHistory;
    private final int maxSavedStates;

    /** Oxbow and abandoned-channel primitives already shed, oldest first; drained by {@link #collectPrimitives}. */
    private final ArrayDeque<HydrologicalPrimitive> lastStates = new ArrayDeque<>();
```

Delete the `RemovedPath` record declaration entirely:

```java
    /** A geometry removed from the active network, retained for {@link #collectPrimitives}. */
    private record RemovedPath(HydrologicalFeature type, List<double[]> pts, double width, int time) {}
```

Rename the constructor parameter `savePreviousStates` to `saveHistory` in the six-argument constructor and in its assignment (`this.saveHistory = saveHistory;`). Leave the parameter's position and type alone — every caller passes it positionally, so no call site changes. Update the two guards to read `if (saveHistory)`.

Add these imports:

```java
import java.util.function.UnaryOperator;
import me.batata_1.fractal_terrain.hydrology.features.AbandonedRiverPrimitive;
import me.batata_1.fractal_terrain.hydrology.features.OxbowLakePrimitive;
```

- [ ] **Step 4: Mint oxbows at the cutoff**

In `RiverNetwork.java`, replace `recordRemovedComplement` in full:

```java
    /** Records the points of {@code ch} NOT in {@code keptIndexes} as the oxbow the cutoff left behind. */
    private void recordRemovedComplement(Channel ch, List<Integer> keptIndexes, int step) {
        final boolean[] kept = new boolean[ch.numPts()];
        for (int idx : keptIndexes) if (idx >= 0 && idx < kept.length) kept[idx] = true;
        int removedCount = 0;
        for (int i = 0; i < ch.numPts(); i++) if (!kept[i]) removedCount++;
        if (removedCount < 2) return; // a single stray point is not a loop

        for (int i = 0; i < ch.numPts(); i++) {
            if (kept[i]) continue;
            // Elevation and influence stay 0 here: neither is knowable at the cut, and both are filled
            // in later through remapHistory.
            lastStates.addLast(new OxbowLakePrimitive(
                    ch.spline.points().get(i).clone(), (byte) step, ch.widthAt(i), 0, 0));
        }
        evictOlderThan(step);
    }
```

and update its call site inside `manageCutoffs` — the `HydrologicalFeature` argument is gone, since it was always `OXBOW_LAKE`:

```java
        if (saveHistory) recordRemovedComplement(ch, newPathIndexes, step);
```

- [ ] **Step 5: Mint abandoned channels at the collision pass**

In `RiverNetwork.java`, replace the tail of `recordAbandoned`'s loop body — this:

```java
            if (pts.size() >= 10)
                removedPaths.add(new RemovedPath(
                        HydrologicalFeature.ABANDONED_RIVER, pts, HydrologyTuning.widthFromFlow(maxOwn), step));
        }
    }
```

with:

```java
            if (pts.size() < 10) continue;
            // One width for the whole path: ownFlow is the per-cell constant on interior atomic nodes,
            // so a per-point widthFromFlow would give a uniform hairline instead of the channel's size.
            final double width = HydrologyTuning.widthFromFlow(maxOwn);
            for (double[] p : pts) {
                lastStates.addLast(new AbandonedRiverPrimitive(p.clone(), (byte) step, width, 0, 0));
            }
        }
        evictOlderThan(step);
    }
```

Also update the method's docstring, which still names the staging record:

```java
    /** Mints each pruned sub-path the collision pass drops as {@link HydrologicalFeature#ABANDONED_RIVER}
     *  history, so a captured channel survives as a trace rather than vanishing. */
```

- [ ] **Step 6: Replace the History section**

In `RiverNetwork.java`, replace `recordState` in full (the whole method under the `// History` banner):

```java
    /** Drops history older than the step window; runs after every mint, and only ever inspects the head
     *  because steps are non-decreasing across mints. */
    private void evictOlderThan(int step) {
        while (!lastStates.isEmpty() && step - lastStates.peekFirst().time() > maxSavedStates) {
            lastStates.removeFirst();
        }
    }

    /** Rewrites every stored history primitive, preserving deque order. Exists because elevation and
     *  influence are known only long after the cutoff that minted the primitive, and must be filled in
     *  before {@link #collectPrimitives} copies them into the index. */
    public void remapHistory(UnaryOperator<HydrologicalPrimitive> resolver) {
        final List<HydrologicalPrimitive> staged = new ObjectArrayList<>(lastStates);
        lastStates.clear();
        for (HydrologicalPrimitive p : staged) lastStates.addLast(resolver.apply(p));
    }
```

- [ ] **Step 7: Emit history from `collectPrimitives`**

In `RiverNetwork.java`, replace the tail of `collectPrimitives`:

```java
        for (RemovedPath rp : removedPaths) {
            HydrologicalFeature.ABANDONED_RIVER.addPrimitives(offset, primitives, rp);
        }
        return primitives;
```

with:

```java
        // Shed features were minted in network frame at the step that cut them; addPrimitives shifts each
        // into the frame this collect emits in.
        for (HydrologicalPrimitive shed : lastStates) {
            shed.getType().addPrimitives(offset, primitives, shed);
        }
        return primitives;
```

- [ ] **Step 8: Fill in the two `addPrimitives` bodies**

In `HydrologicalPrimitive.java`, replace the `ABANDONED_RIVER` and `OXBOW_LAKE` enum constants:

```java
        ABANDONED_RIVER(() -> AbandonedRiverPrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                final AbandonedRiverPrimitive shed = (AbandonedRiverPrimitive) args[0];
                primitives.add(new AbandonedRiverPrimitive(
                        VectorOps.sub(shed.coord(), offset),
                        shed.time(),
                        shed.width(),
                        shed.influence(),
                        shed.elevation()));
            }
        },
        OXBOW_LAKE(() -> OxbowLakePrimitive.PROTOTYPE) {
            @Override
            public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
                final OxbowLakePrimitive shed = (OxbowLakePrimitive) args[0];
                primitives.add(new OxbowLakePrimitive(
                        VectorOps.sub(shed.coord(), offset),
                        shed.time(),
                        shed.width(),
                        shed.influence(),
                        shed.elevation()));
            }
        },
```

- [ ] **Step 9: Drop the `recordState` call**

In `ChannelMigrator.java`, delete line 86 — the last statement of `step`:

```java
        network.recordState(i);
```

Leave `dumpNetwork("05_final");` as the method's final statement.

- [ ] **Step 10: Run the test to verify it passes**

Run: `gradle spotlessApply` then `gradle test --tests "*RiverNetworkHistoryTest*"`
Expected: PASS, 5 tests.

- [ ] **Step 11: Correct the features README sentence**

In `src/main/java/me/batata_1/fractal_terrain/hydrology/features/README.md`, replace:

```markdown
`ABANDONED_RIVER`, `OXBOW_LAKE`, `WATERFALL` and `DELTA` all override `addPrimitives` with an
empty body. The records for those four exist so the type tag, the codec and the `HydrologyProfile`
extension point are already in place when they grow real behaviour — not because anything produces them
today.
```

with:

```markdown
`ABANDONED_RIVER` and `OXBOW_LAKE` re-mint what `RiverNetwork`'s history deque already shed, shifting it
into the collect frame; `WATERFALL` and `DELTA` still override `addPrimitives` with an empty body, so
their records exist only to hold the type tag, the codec and the `HydrologyProfile` extension point until
they grow real behaviour. Nothing produces a history primitive in practice: every `RiverNetwork` in the
pipeline is built with history disabled.
```

Do not touch the rest of this README. Its Overview still says "six feature families" and its Architecture
still says the radial pass does not exist — both falsified by `780356f` and `755d731`. That is
pre-existing drift and repairing it is separate work.

- [ ] **Step 12: Add the test index row**

In `src/test/java/me/batata_1/fractal_terrain/hydrology/network/CLAUDE.md`, add beneath the `CentrelineTest.java` row:

```markdown
| `RiverNetworkHistoryTest.java` | The history deque: what a cutoff sheds, the age window that evicts it, `remapHistory` resolution, and the frame shift on emission | Changing what the network records as history, the eviction window, or how shed primitives reach `collectPrimitives` |
```

- [ ] **Step 13: Verify nothing else moved**

Run: `gradle build` — expect success including `spotlessCheck`.

Run: `gradle test`

Compare `build/test-results/test/*.xml` against `.superpowers/conventions-alignment/post-migration-failures.txt`. The recorded baseline is 102 tests / 9 failed / 1 skipped (`RosgenKeyTest` ×4, `RiverGoldenTest` ×2, `MeandersGoldenTest` ×1, `CentrelineTest` ×1, `ReachMetricsSamplerTest` ×1), but the root `CLAUDE.md` warns that figure is a claim to re-verify, not a fact — `HEAD` has moved since it was taken.

The bar: the failure *messages* for those nine must be unchanged, and the only new tests must be this plan's. History is disabled at every construction site, so any other movement is a regression in this change, not a rebaseline.

If a pre-existing failure's message differs, re-measure at `HEAD` in a clean worktree (copying `libs/onnxruntime/teste.jar` in first) before attributing it to this change.

- [ ] **Step 14: Commit**

```bash
gradle spotlessApply
gradle build
git add src/main/java/me/batata_1/fractal_terrain/hydrology src/test/java/me/batata_1/fractal_terrain/hydrology
git commit -m "refactor(hydrology): make RiverNetwork history one deque of shed primitives

previousStates was written by recordState and read by nothing, and removedPaths
was read into an addPrimitives body that was empty, so no history feature had
ever reached the index. Both are gone.

lastStates holds already-minted oxbow and abandoned-channel primitives, stamped
with the step that cut them, evicted once they fall outside the maxSavedStates
window, and re-minted into the caller's frame at collect. remapHistory is the
seam that fills in the elevation and influence a cut cannot know.

History stays disabled at every construction site, so carve output is unmoved.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01J6NtL3Lq3UWPfEpHBKAuES"
```

---

## Deviations from the spec

Both are deliberate; neither changes a decision.

1. **The spec's verification section promises one new test; this plan writes two files.** The primitive
   payload and the network deque fail independently and are reviewed independently, so they get one gate
   each (`.claude/conventions/structural.md`: a new test file is justified by a distinct module boundary
   or different fixtures — these have both).

2. **`recordRemovedComplement` loses its `HydrologicalFeature type` parameter.** It had exactly one caller
   passing exactly one constant. Keeping it would mean a parameter no call site varies.
