# River-network history: one deque of minted primitives

Date: 2026-09-03
Status: proposed — nothing here has been implemented
Branch: `feature/hydrology`
Measured at: `755d731`, plus one uncommitted line in `RiverNetwork.recordAbandoned` raising the
abandoned-path minimum from `pts.size() >= 2` to `>= 10`. This design preserves that line.

Re-measured 2026-09-03 after `780356f` and `755d731` (confluence/source emission and the radial carve
pass) landed mid-design. They changed nothing this design touches — `RiverNetwork`'s history fields and
both empty `addPrimitives` bodies are as described — but they did establish `RadialPrimitive` as the
template D3 follows, and they left `features/README.md` describing a codebase that no longer exists.

## Problem

`RiverNetwork` keeps history in two fields that do not agree on what history is:

```java
private final ArrayDeque<List<List<double[]>>> previousStates = new ArrayDeque<>();
private final List<RemovedPath> removedPaths = new ObjectArrayList<>();
```

Three things are wrong with that.

**`previousStates` is write-only.** `recordState(int)` snapshots every live channel's spline points and
appends the snapshot; `ChannelMigrator.step` calls it once per migration step. Nothing anywhere reads the
field. It is also the wrong subject: it copies the *current* network, and the current network is not the
past. What the pipeline actually loses to time is the geometry that gets cut away — oxbow loops and
captured channels — and those are the one thing the snapshot does not distinguish.

**`removedPaths` is read, but the read does nothing.** `collectPrimitives` ends with

```java
for (RemovedPath rp : removedPaths) {
    HydrologicalFeature.ABANDONED_RIVER.addPrimitives(offset, primitives, rp);
}
```

and `ABANDONED_RIVER.addPrimitives` is an empty body. `OXBOW_LAKE`'s is too, and nothing calls it at all,
so cutoff loops are recorded and then dropped without even the gesture of a call. No history feature has
ever reached the spatial index. Of the seven families, `RIVER`, `SOURCE` and `CONFLUENCE` mint; these two
and `WATERFALL`/`DELTA` do not.

**The two shapes disagree, and the useful one is discarded at the boundary.** `RemovedPath` already
carries the step it was created at:

```java
private record RemovedPath(HydrologicalFeature type, List<double[]> pts, double width, int time) {}
```

That `time` — and the `width` beside it — dies at the record boundary, because the primitive it would
become has room for neither. `AbandonedRiverPrimitive`'s own javadoc names the gap: *"no width, no
normal, no record of how long ago it was abandoned."*

All of this is gated on `savePreviousStates`, which is `false` at **every** construction site in the
repo. The three-argument production constructor hardcodes it; every test, golden fixture and debug
harness passes `false` explicitly. Nothing described above executes today.

## Decisions

**D1. One `ArrayDeque<HydrologicalPrimitive> lastStates` replaces both fields.** History is the
primitives the network has already shed — oxbow lakes and abandoned channels — held as finished
`HydrologicalPrimitive` instances rather than as raw geometry awaiting a conversion that never happens.
Minting at the moment of the cut is what lets the primitive record *when* it was cut; a later conversion
pass has no way to know.

`RemovedPath` and `recordState(int)` are deleted, along with `recordState`'s call site at
`ChannelMigrator.java:86`. The `savePreviousStates` field and constructor parameter are renamed
`saveHistory` — same position, same type, so no call site changes.

Rejected: keeping raw geometry in the deque and minting at `collectPrimitives`. It would make elevation
and influence (D4) available at mint time, but it puts the deque back to storing something that is not a
primitive, which is the shape this design exists to remove.

**D2. `byte time` is an interface default, stored only by the two history records.**

```java
/** Migration step this primitive was cut at; 0 means it belongs to the live network. */
default byte time() { return 0; }
```

Every other record inherits the default and its serialized body is byte-identical to today. The
alternative — a real component on all seven families — would change the on-disk payload length of every
cached primitive, and would add a byte to `RiverPrimitive`, which is minted once per spline point and is
by far the bulk of any tile's index.

The step counters this reads from are bounded well inside a byte: `GlobalNetworkBuilder.MAX_RELAX_STEPS`
is 50 and `RiverProvider` runs `lateralErosionSim.simulate(25)`.

Rejected: not persisting `time` at all. A reloaded oxbow would forget its age, so any future age-driven
profile would work only before the tile is cached — the one case that never happens in a real world.

**D3. The two history records leave `PositionOnlyPrimitive` for a shared `HistoricPrimitive`.** With
width, elevation and influence they are no longer position-only. A new interface beside
`PositionOnlyPrimitive.java` owns the layout once, so neither record duplicates the codec calls, the
byte-size arithmetic or the contents-equality override.

It is **public**, unlike `PositionOnlyPrimitive`: `ChannelElevationAssigner` lives in `hydrology/`, not
`hydrology/features/`, and `resolved` is the method it drives.

This is not a new pattern. `RadialPrimitive` (added in `780356f`) is the same shape for the same reason —
a public interface over `HydrologicalPrimitive, SpatialIndexCircle`, exposing `width()` and `elevation()`,
defaulting `getCenter`/`getRadius`/`getProfile`. `HistoricPrimitive` mirrors it, and both history records
follow `SourcePrimitive`'s record shape: a canonical constructor carrying a `long seed`, a public
convenience constructor computing it, `hashCode` as `Math.toIntExact(seed)`, and `seed` excluded from
the serialized body.

```java
/**
 * Shared behaviour for a feature the network has shed: an influence circle that remembers the step it
 * was cut at, the channel width it was cut from, and a bed elevation the cut itself cannot know.
 *
 * <p>Minted mid-simulation, so {@code elevation} and {@code influence} arrive later through
 * {@link #resolved}. Until that pass runs, {@code getRadius()} is 0 — a primitive indexed before
 * resolution has no footprint and silently matches nothing.
 */
public interface HistoricPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    double width();

    double elevation();

    double influence();

    @Override
    default double[] getCenter() {
        return coord();
    }

    @Override
    default double getRadius() {
        return influence();
    }

    @Override
    default HydrologyProfile getProfile() {
        return DefaultProfile.INSTANCE;
    }

    /** This primitive with its deferred quantities filled in. Abstract because only the record knows
     *  its own canonical constructor. */
    HistoricPrimitive resolved(double elevation, double influence);
}
```

`getProfile()` stays `DefaultProfile`, so carve behaviour does not move: width and elevation are carried
for a cross-section that does not exist yet, not consumed by one.

`PositionOnlyPrimitive`'s javadoc lists both records as implementers and must drop them.

**D4. Elevation and influence are deferred, and the records stay immutable.**
`OxbowLakePrimitive` and `AbandonedRiverPrimitive` become

```java
public record OxbowLakePrimitive(double[] coord, byte time, double width, double influence, double elevation)
        implements HistoricPrimitive
```

minted with `influence = 0, elevation = 0`. `ChannelElevationAssigner` fills both later, through a seam
on the network:

```java
/** Rewrites every stored history primitive, preserving deque order. Exists because elevation and
 *  influence are known only long after the cutoff that minted the primitive. */
public void remapHistory(UnaryOperator<HydrologicalPrimitive> resolver)
```

Drain to a list, clear, refill — `ArrayDeque` has no `replaceAll`. The assigner sees each primitive
whole, so it can read `coord` and `width` to decide what to hand back.

Rejected: mutable non-record classes with a settable elevation field. It matches how `Channel`'s
`bedElevations` and `Endpoint.elevation` already work and would need no seam, but it would make these the
only two `HydrologicalPrimitive` implementations that are not records, and a primitive already handed to
a spatial index could change underneath a query.

Rejected: deriving influence from width at mint time. It would leave only one deferred quantity, but
influence for a river comes from an `InfluenceSampler` reading terrain at the primitive's elevation, and
that elevation is exactly what is missing at mint time.

The zero-radius window this opens is the design's sharpest edge, and it imposes an ordering rule:
**`remapHistory` must run before `collectPrimitives`.** Collect emits offset *copies* (D6), so resolving
afterwards updates the deque and not the primitives already handed to the index — the failure is silent,
producing history entries that match no query. Today that rule cannot be violated, because history is off
(D8) and `collectPrimitives` is the sole path into the index. The `HistoricPrimitive` javadoc carries the
warning.

**D5. Eviction is by age, using `time`.**

```java
/** Drops history older than the step window; called after every mint. */
private void evictOlderThan(int step) {
    while (!lastStates.isEmpty() && step - lastStates.peekFirst().time() > maxSavedStates) {
        lastStates.removeFirst();
    }
}
```

`maxSavedStates` keeps the meaning it had against `previousStates` — remember the last N *steps* — rather
than silently becoming a primitive budget. Steps are non-decreasing across mints, so the deque's FIFO
order is sorted by `time` and eviction only ever inspects the head.

Rejected: bounding by `lastStates.size()`. One cutoff loop is one primitive per removed point, so a
single large cutoff would evict every older feature in one step.

**D6. History is minted in network frame and re-minted through `addPrimitives` at collect.**
`collectPrimitives` takes an offset, and in production that offset is not zero: `RiverProvider` passes
`PAD - tileOriginX`. Stored primitives therefore cannot be handed out as-is.

```java
for (HydrologicalPrimitive p : lastStates) {
    p.getType().addPrimitives(offset, primitives, p);
}
```

filling in the two empty `addPrimitives` bodies:

```java
OXBOW_LAKE(() -> OxbowLakePrimitive.PROTOTYPE) {
    @Override
    public void addPrimitives(double[] offset, List<HydrologicalPrimitive> primitives, Object... args) {
        final OxbowLakePrimitive p = (OxbowLakePrimitive) args[0];
        primitives.add(new OxbowLakePrimitive(
                VectorOps.sub(p.coord(), offset), p.time(), p.width(), p.influence(), p.elevation()));
    }
}
```

This keeps the rule that every primitive is minted through `HydrologicalFeature.addPrimitives`, and adds
no second varargs parameter, which `features/README.md` names as an invariant. Both families sort after
`RIVER` (ordinals 1 and 2 against 0), so `computeRiverGrid`'s stop-at-first-non-river condition is
unaffected.

**D7. Width is per-point for oxbows and path-wide for abandoned channels.**
`recordRemovedComplement` already holds the channel and reads `ch.widthAt(firstRemovedIndex)` for the
whole path; per removed point it becomes `ch.widthAt(i)` at no cost.

`recordAbandoned` cannot do the same. It walks the pre-prune `AtomicView`, where `ownFlow` is the per-cell
constant `FLOW_PER_CELL` on every interior node — a per-point `widthFromFlow(ownFlow)` would give a
uniform hairline. It therefore keeps today's `widthFromFlow(maxOwn)` as one width for the whole path,
with an inline comment saying why the value is a path proxy.

Rejected: calling `AtomicView.accumulateAndCorrectFlow()` on the pre-prune view to get real per-node
flow. It would be correct and observationally inert, but it adds an O(V+E) pass per collision step to
serve a feature that is switched off.

**D8. History stays off.** The three-argument production constructor still passes `false`. Every carve
path, every golden fixture and every cached tile is therefore unaffected: this change makes history
correct and emitting, and enabling it stays a separate one-line decision for after
`OxbowLakePrimitive` and `AbandonedRiverPrimitive` grow real profiles. Turning it on today would put
`DefaultProfile` influence discs into the carve at every abandoned trace.

## Serialized layout

Only `OXBOW_LAKE` and `ABANDONED_RIVER` change. Both bodies become, little-endian, after the
`HydrologicalPrimitive.serialize()` type tag:

| Field       | Bytes | Written by                        |
| ----------- | ----- | --------------------------------- |
| `coord`     | 4 + 16 | `PrimitiveCodec.putCoord`        |
| `time`      | 1     | `buf.put`                         |
| `width`     | 8     | `buf.putDouble`                   |
| `influence` | 8     | `buf.putDouble`                   |
| `elevation` | 8     | `buf.putDouble`                   |

45 bytes against today's 20. The divergence from the position-only layout carries a `:SCHEMA:` marker
per `.claude/conventions/intent-markers.md`. `RIVER`, `SOURCE`, `DELTA`, `WATERFALL` and `CONFLUENCE`
bodies are untouched, so existing cached tiles stay readable — no history primitive has ever been written
to one.

`equals`/`hashCode` on both records cover every component: two oxbows cut at the same point in different
steps must not collide.

## Files

| File | Change |
| ---- | ------ |
| `features/HistoricPrimitive.java` | New. The shared layout of D3. |
| `features/HydrologicalPrimitive.java` | `default byte time()`; fills in the two empty `addPrimitives` bodies. |
| `features/OxbowLakePrimitive.java` | Four new components, `resolved`, codec, equality. |
| `features/AbandonedRiverPrimitive.java` | Same. |
| `features/PositionOnlyPrimitive.java` | Javadoc drops the two records. |
| `features/PrimitiveCodec.java` | Read/write helpers for the D3 layout. |
| `network/RiverNetwork.java` | The whole of D1, D5, D6, D7; `remapHistory`. |
| `meanders/ChannelMigrator.java` | Drops the `network.recordState(i)` call. |
| `features/CLAUDE.md` | A row for `HistoricPrimitive.java`. |
| `features/README.md` | Overview: the empty-`addPrimitives` list drops the two history families. |

## Out of scope

- **`ChannelElevationAssigner` does not change here.** D4 supplies `remapHistory` as the seam; the pass
  that drives it is the user's, and this design makes no claim about what elevation a shed primitive
  should get.
- **No carve or profile work.** `getProfile()` stays `DefaultProfile` for both families. `ZoneCategory.LAKE_BED`
  remains reserved and unused.
- **`WATERFALL`, `DELTA` and `CONFLUENCE`** keep their empty `addPrimitives` bodies.
- **Enabling history** (D8).
- **`features/README.md`'s pre-existing drift.** Its Overview still says six families, only two minted,
  and "no such pass exists" of the radial carve — all of which `780356f` and `755d731` falsified. This
  change corrects only the sentence naming the empty `addPrimitives` bodies, because that sentence is the
  one it makes wrong. Repairing the rest is separate work.

## Verification

`gradle spotlessApply`, then `gradle build`.

Then `gradle test`, compared against `.superpowers/conventions-alignment/post-migration-failures.txt`.
The root `CLAUDE.md` records the baseline as 102 tests / 9 failed / 1 skipped at `df7ca2e` and warns that
the quoted number is a claim to re-verify, not a fact; comparing the *failure messages* in
`build/test-results/test/*.xml` against that file is what proves nothing moved. Because history stays off
(D8), any change in that comparison is a regression, not a rebaseline.

One new test, extending an existing file rather than adding one per
`.claude/conventions/structural.md`: build a network with `saveHistory = true`, run enough migration
steps to force a cutoff, and assert that `collectPrimitives` returns `OxbowLakePrimitive`s carrying the
step they were cut at, with a non-zero offset actually applied to their coordinates. Without it nothing
in this change is executed by any test — every production caller passes `false`.
