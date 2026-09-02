# Radial primitives: confluences and sources carve a bowl of their own

Date: 2026-09-02
Status: proposed — nothing here has been implemented
Branch: `feature/hydrology`
Measured at: `df7ca2e`, with a dirty working tree (docs-only modifications)

## Problem

Every hydrological feature that reaches terrain today is a `RiverPrimitive`. `features/README.md` states
it plainly: six families share one index, only two are ever minted, and only one of those is ever carved.
A `SourcePrimitive` is indexed, persisted and queryable, and contributes nothing to any elevation.

Two places in the network are geometrically radial and are currently rendered by whatever river
primitives happen to pass near them:

- A **confluence** (a `JUNCTION` endpoint with `incoming.size() >= 2`) is where two or more channels
  merge. The river primitives converging on it each cut their own rotated rectangle; nothing cuts the
  pool the merge implies, and nothing coordinates the beds meeting there.
- A **source** (a `SOURCE` endpoint) is a headwater. `SourcePrimitive` exists, is emitted at every one
  (`RiverNetwork.collectPrimitives`), and carves nothing — the channel simply begins mid-hillside.

A prior `ConfluencePrimitive` existed on a different mechanism — a junction ray-set — and was removed;
`ARCHITECTURE.md`'s superseded-designs paragraph lists it, and it is reachable in history at `ad118e3`
and `8d92e23`. This design shares that name and nothing else: the mechanism here is a radial parabola
merged by the same recurrence the river carve already runs.

The carve is structurally ready for this and says so. `computeRiverGrid` returns "the index of the first
non-river primitive, where a later family pass resumes"; `features/README.md` describes the same hook.
No such pass has ever existed.

## Decisions

**D1. One radial family, not two one-off primitives.** A new marker interface `RadialPrimitive` carries
what a radial carve needs — `width()`, `elevation()`, `getRadialProfile()` — and both
`ConfluencePrimitive` and `SourcePrimitive` implement it. `RiverInfluenceCarve` grows exactly one
`carveRadialPrimitive`, and the shape difference between a confluence and a source lives entirely in a
`RadialProfile` constant. This preserves the rule `features/README.md` states as an invariant: the carve
never switches on a concrete record type; the profile decides what to cut.

**D2. The pass is a filtered walk to the end of the list, not a resume-at-`stop`.** With `SOURCE`
(ordinal 3) and `CONFLUENCE` (ordinal 6) both carving and `DELTA` (ordinal 5) emitted between them, the
radial families are not contiguous in `comparator` order. The pass walks from `computeRiverGrid`'s river
stop index to the end and carves any entry matching `instanceof RadialPrimitive`. Cost is one
`instanceof` per emitted non-river primitive per chunk, and no allocation.

Rejected: reordering `HydrologicalFeature` so the carving families are adjacent. The enum is documented
append-only because a constant's ordinal is the on-disk type tag; reordering silently reinterprets every
primitive already cached.

Rejected: a second R-tree query or a partitioned prefetch returning two lists. `ARCHITECTURE.md` holds
the line at one tree stab per chunk, and partitioning allocates on the per-chunk path.

**D3. The radial pass shares `acc` and refills `dist`.** Rivers merge first into `acc` and `dist`;
`dist` is then refilled to `UNSET_MIN_DIST` and the radial pass runs into the same `acc`. Radial
penetration is therefore ranked among radial primitives only, and never competes with a river's
rectangle scale for the same slot. One buffer set, one closing blend in
`PopulateNoiseStep.fineGrainedPrimitivePass`, no new allocation.

**D4. The radial pass clamps to the river surface, and the clamp is load-bearing.**
`UNSET_MIN_DIST = 64` against `PRIMITIVE_BLEND_STRENGTH = 0.05` gives the first primitive to touch a
lattice cell a weight of exactly `1`. Because D3 refills `dist`, the first radial primitive to reach a
cell would otherwise *overwrite* the river bed there rather than blend with it. Clamping the sampled
height to `min(acc[a], hC)` turns that `w == 1` overwrite into "deeper of the two wins", which is the
intended behaviour, and makes the pass cut-only with respect to the river surface as a side effect.

**D5. The clamp is gated on the weight lane.** `computeRiverGrid` zero-fills `acc`, so a cell no river
reached holds height `0`, and an unconditional `min(0, hC)` would clamp a bowl whose rim sits at
elevation 50 down to 0. The river-reached test is the weight lane written by the river pass, read before
the radial pass rewrites it:

```java
final double h = acc[a + 2] > 0 ? Math.min(acc[a], hC) : hC;
```

**D6. The radial pass accumulates the weight lane rather than assigning it.** Cells inside a radial
primitive's clipped AABB but outside its disc take `w == 0`, and the river pass's assignment form
(`acc[a + 2] = 1 - clamp(dist[i], 0, 1)`) would write `0` over the river's own weight at those cells.
The radial pass writes `acc[a + 2] = max(acc[a + 2], 1 - clamp(dist[i], 0, 1))`: a radial primitive can
add hydrological claim to a cell, never remove it.

**D7. Radius is the largest width meeting at the node, uniformly.** For each channel incident on the
endpoint, take the spline point adjacent to that endpoint — index `numPts() - 1` for an incoming
channel, index `0` for the outgoing one — and read `widthAt` there. The radius is the maximum over
those. A source degenerates to the single outgoing channel's `widthAt(0)`. No half-width, no multiplier:
`radius == width`, so the disc's diameter is two channel widths.

**D8. Depth is the river depth law at that width.**
`depth = FractalTerrainConfig.GLOBAL_SCALE_CORRECTION * ChannelGeometry.depth(width)` — the identical
expression `carveRiverPrimitive` uses. A bowl is exactly as deep as a channel of its own radius-defining
width.

**D9. The rim sits on `Endpoint.elevation`.** `ChannelElevationAssigner` writes it for both junctions and
sources. It is the propagation input rather than the per-point `bedElev` the surrounding `RiverPrimitive`s
were emitted with, so the rim is not guaranteed flush with the beds meeting there. D4's clamp absorbs the
case where it lands too high; the case where it lands too low deepens the pool, which is acceptable.

**D10. Shape lives in `RadialProfile`, one constant per family.**

| Constant | Law | Reading |
| --- | --- | --- |
| `CONFLUENCE` | `-depth * (1 - r*r)` | Inverted parabola: rounded floor, flush rim — a pool scoured by converging flow |
| `SOURCE` | `-depth * (1 - r)` | Cone: sharp centre, straight walls, flush rim — a spring cutting its own notch |

`r` is the normalized radius, clamped to `[0, 1]`. Both are flush at `r == 1` by construction, so
neither steps against the shell at the rim.

**D11. Each radial primitive publishes its own water surface.**
`waterSurface = elevation + HydrologicalPrimitive.waterLine(width)`, blended into `acc[3i + 1]` on the
same recurrence as the height. Symmetric with `RiverPrimitive`, and it is what keeps a bowl from draining:
the recurrence would otherwise carry the river's water level toward `0`.

**D12. The bed carve only. The tile shell carve is untouched.** `carveRiverInfluence` and
`computeRiverInfluenceGrid` keep breaking at the first non-river entry, so `hydrology_relief` carries the
valley shell with no bowl in it. The bowl is cut per chunk against that shell, exactly as the river trench
already is. `profile/README.md`'s "there is no tile-level bed carve" statement stays true.

**D13. `SourcePrimitive`'s payload change forces a store rename.** See "Persistence" below.

**D14. No tie-break key is added to `comparator`.** Radial primitives of the same family compare equal
under it — equal ordinals, and `asRiver` returns `null` for both. This is the tolerance the river path
already has for equal-influence primitives: `List.sort` is stable and the R-tree's traversal order is
deterministic, so the merge order is reproducible without a new key. Recorded because it is a deliberate
omission, not an oversight.

**D15. `channelContains` stays defaulted on both radial records.** The wetted part of a bowl is not the
whole disc: water sits at `elevation + waterLine(width)` while the rim sits at `elevation`, so open water
covers only the sub-disc where the carved bed falls below the water line — `r² < 1 + waterLine/depth` for
the parabola, a different expression for the cone. Encoding that would put a profile-dependent constant on
the record, and its only consumer, `HydrologyProfilePainter.insideChannel`, is called solely from
`debug/Infinite3DVisualizer` (`profile/README.md`). Water in the pool comes from `WATER_HEIGHT` via D11,
which is the live path. Deferred rather than guessed.

## Architecture

### New: `hydrology/features/RadialPrimitive.java`

```java
public interface RadialPrimitive extends HydrologicalPrimitive, SpatialIndexCircle {

    double width();

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

Public, not package-private like `PositionOnlyPrimitive`: `RiverInfluenceCarve` lives in `profile/` and
must name the type its second pass dispatches on.

### New: `hydrology/profile/RadialProfile.java`

A single enum with two constants, mirroring `RosgenProfile`'s shape — per-constant overrides of a delta
law, plus one `sampleRadialSection` that tabulates it. The LUT is indexed by unsigned radius on an integer
radial-lattice index, the analogue of `sampleCrossSection`'s shared integer perp index.

```java
public enum RadialProfile implements HydrologyProfile {
    CONFLUENCE {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * (1 - normalizedRadius * normalizedRadius);
        }
    },
    SOURCE {
        @Override
        protected double radialDelta(double normalizedRadius, double depth) {
            return -depth * (1 - normalizedRadius);
        }
    };

    public void sampleRadialSection(
            float[] lut, int n, double step, int baseIdx, double elevation, double invRadius, double depth) {
        for (int i = 0; i < n; i++) {
            final double r = Math.clamp((baseIdx + i) * step * invRadius, 0, 1);
            lut[i] = (float) (elevation + radialDelta(r, depth));
        }
    }

    protected abstract double radialDelta(double normalizedRadius, double depth);
}
```

### New: `hydrology/features/ConfluencePrimitive.java`

```java
public record ConfluencePrimitive(double[] coord, double width, double elevation, long seed)
        implements RadialPrimitive
```

Convenience constructor computing `seed` from content, as `RiverPrimitive` does, plus a `PROTOTYPE`
constant for the codec. `getType()` returns `CONFLUENCE`; `getRadialProfile()` returns
`RadialProfile.CONFLUENCE`; `channelContains` stays at its default per D15.

### Changed: `hydrology/features/SourcePrimitive.java`

Leaves the `PositionOnlyPrimitive` mixin — it now needs a real radius, a real profile, and two new
components:

```java
public record SourcePrimitive(double[] coord, double width, double elevation, long seed)
        implements RadialPrimitive
```

`PositionOnlyPrimitive` survives for `DELTA`, `WATERFALL`, `OXBOW_LAKE` and `ABANDONED_RIVER`; its
javadoc's list of implementors drops `SourcePrimitive`.

### Changed: `hydrology/features/HydrologicalPrimitive.java`

`CONFLUENCE` appended to `HydrologicalFeature` at ordinal 6, carrying a `:SCHEMA:` marker on the append
recording that the ordinal is the on-disk tag. `SOURCE.addPrimitives` and `CONFLUENCE.addPrimitives` both
take `(offset, primitives, Endpoint, RiverNetwork, IntSet emittingChannelIds)` through the existing
`Object... args` — one array, no second varargs, so `features/README.md`'s invariant holds.

Both share a helper resolving the radius: walk the endpoint's incident channels, keep only those whose id
is in `emittingChannelIds`, read `widthAt` at the endpoint-adjacent index, and take the max. `CONFLUENCE`
emits only when at least two incident channels emitted; `SOURCE` only when its outgoing channel did. Both
skip a `NaN` `Endpoint.elevation`. A bowl sized off a channel that produced no river primitives would
carve into terrain nothing else touched.

### Changed: `hydrology/network/RiverNetwork.java`

`collectPrimitives`' phase-2 node loop, after the phase-1 resample so widths match the resampled splines:

```java
for (Endpoint en : nodes.values()) {
    if (en.type == Endpoint.Type.SOURCE) {
        HydrologicalFeature.SOURCE.addPrimitives(offset, primitives, en, this, emittingIds);
    }
    if (en.type == Endpoint.Type.JUNCTION && en.incoming.size() >= 2) {
        HydrologicalFeature.CONFLUENCE.addPrimitives(offset, primitives, en, this, emittingIds);
    }
    if (en.type == Endpoint.Type.DRAIN) {
        HydrologicalFeature.DELTA.addPrimitives(offset, primitives, en);
    }
}
```

`emittingIds` is an `IntOpenHashSet` built from the `emitting` list in phase 1 — fastutil per
`performance.md`, allocated once per `collectPrimitives` call, which is cold.

### Changed: `hydrology/profile/RiverInfluenceCarve.java`

`carvePrimitive` is renamed `carveRiverPrimitive`. The name has been wrong since the family split: it
carves a river and nothing else, and the new sibling makes the ambiguity actively misleading.

`computeRiverGrid` gains the second pass:

```java
int stop = 0;
while (stop < primitives.size() && primitives.get(stop) instanceof RiverPrimitive river) {
    carveRiverPrimitive(river, ...);
    stop++;
}

// D3: radial penetration ranks among radial primitives only, never against a river's rectangle scale.
Arrays.fill(dist, 0, points, (float) UNSET_MIN_DIST);

for (int i = stop; i < primitives.size(); i++) {
    if (primitives.get(i) instanceof RadialPrimitive radial) carveRadialPrimitive(radial, ...);
}
return stop;
```

`carveRadialPrimitive` mirrors `carveRiverPrimitive`'s structure — AABB clip, LUT once, walk the clipped
cells — with three substitutions:

- **Footprint clip** is `[cx - radius, cx + radius] x [cz - radius, cz + radius]`, no rotation.
- **Radial LUT range is clipped, and this is load-bearing rather than an optimization.**
  `GRID_RESOLUTION` is `1 / GLOBAL_SCALE_CORRECTION = 0.2` px per block, so a `MAX_WIDTH = 16` disc
  would want roughly 80 LUT entries while `maxLutLen(16, 0.2)` supplies 25. The LUT covers only the
  radial range the clipped box actually reaches — `radMin` the distance from the centre to the nearest
  point of the clipped box (0 when the centre is inside), `radMax` the distance to its farthest corner,
  capped at `radius` — which bounds `n` by the grid diagonal exactly as the river's perp clip does. No
  buffer resizing anywhere.
- **`d` is `rad / radius`**, a dimensionless penetration factor that is 1 at the rim — the same meaning
  the river's rectangle scale carries, so `UNSET_MIN_DIST` and `PRIMITIVE_BLEND_STRENGTH` read
  identically in both passes.

The per-cell body, with D4/D5/D6 folded in:

```java
// Hoisted per primitive: invStep = 1 / resolution, invRadius = 1 / radius.
final int i = rowBase + col;
final int a = 3 * i;
final double dx = (startX + row * resolution) - cx;
final double dz = (startZ + col * resolution) - cz;
// A circle has no affine split into row and column terms the way the river's two projections do,
// so the true distance is computed per cell; the LUT still absorbs the profile's shape.
final double rad = Math.sqrt(dx * dx + dz * dz);
final double d = rad * invRadius;
final double mask = d <= 1.0 ? 1.0 : 0.0;
final double t = Math.clamp(((dist[i] - d) / HydrologyTuning.PRIMITIVE_BLEND_STRENGTH + 1) * 0.5, 0, 1);
final double w = t * t * (3.0 - 2.0 * t) * mask;

final double f = rad * invStep - baseIdx;
final int i0 = Math.clamp((int) f, 0, n - 2);
final double hC = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0]);
final double h = acc[a + 2] > 0 ? Math.min(acc[a], hC) : hC;

dist[i] = (float) ((1 - w) * dist[i] + w * d);
acc[a] = (float) ((1 - w) * acc[a] + w * h);
acc[a + 1] = (float) ((1 - w) * acc[a + 1] + w * waterSurface);
typeMask[i] = w > 0.5 ? packed : typeMask[i];
acc[a + 2] = Math.max(acc[a + 2], (float) (1 - Math.clamp(dist[i], 0, 1)));
```

`packed` is `getType().pack(0)`; neither family has a sub-classification.

## Data flow

```
RiverNetwork.collectPrimitives          (cold, once per tile build)
  phase 1  resample emitting channels -> emittingIds
  phase 2  RIVER      per spline point
           SOURCE     per SOURCE endpoint        <- now carries width + elevation
           CONFLUENCE per JUNCTION, degree >= 3  <- new
           DELTA      per DRAIN endpoint
                     |
RiverProvider.primitives  (ImmutableRTree, disk-backed)
                     |
HydrologyProfileInprinter.prefetchChunk  (one stab per chunk, sorted by comparator)
                     |
RiverInfluenceCarve.computeRiverGrid
    pass 1  RiverPrimitive  -> acc, typeMask, dist
    refill  dist = UNSET_MIN_DIST
    pass 2  RadialPrimitive -> acc, typeMask, dist
                     |
PopulateNoiseStep.fineGrainedPrimitivePass
    (1 - w) * ambient + w * min(acc, ambient)  -> ELEVATION, RIVER_DIFFERENCE,
                                                  WATER_HEIGHT, RIVER_TYPE
```

The tile shell carve is absent from this diagram deliberately (D12).

## Persistence

`ConfluencePrimitive` is a new type tag; nothing on disk claims ordinal 6, so it needs no migration.

`SourcePrimitive` does. Its payload today is `coord` alone; it becomes `coord` + `width` + `elevation`
under the **same** type tag 3, and `RiverProvider.java:69` keeps the primitive store deliberately
disk-backed. A stale tile would be read with a payload two doubles short — a misparse, not a clean
failure.

The fix is the mechanism this repo already uses and documents at `RiverProvider.java:71`: the store name
carries the schema identity. `"local_river_units"` becomes `"local_river_units_v3"`, which orphans stale
files rather than reading them wrong, and the comment above it gains the reason. `hydrology_relief` is
untouched — D12 leaves the shell carve alone, so its contents do not change.

## Testing

Per `structural.md`, behaviour over implementation, and parameterized over duplicated bodies.

1. `ConfluencePrimitiveTest` — codec round-trip through `serialize`/`deserialize` including the type tag,
   `equals`/`hashCode` on array *contents* (`PrimitiveCodec.coordsEqual`), and `SpatialIndexCircle` MBR /
   containment / inflated-containment. The name is free: `CLAUDE.md` records the old one as deleted.
   Parameterized across both radial records rather than duplicated for `SourcePrimitive`.
2. `RadialProfileTest` — the two laws at `r = 0`, `r = 1` and midpoint, asserting both are flush at the
   rim (`radialDelta(1, depth) == 0`) and monotone in `r`. Property-style over a generated width range.
3. `RiverInfluenceCarveTest` — the three merge properties D4/D5/D6 state, because each is a place the
   arithmetic silently does the wrong thing:
   - a bowl whose rim sits above an already-carved river bed leaves that cell's height unchanged;
   - a bowl reaching a cell no river reached carves to its own law, not toward `0`;
   - a cell inside a bowl's AABB but outside its disc keeps the river's weight.
4. A no-regression guard: a primitive list containing no `RadialPrimitive` produces byte-identical `acc`,
   `dist` and `typeMask` to the current implementation.

Baseline: `gradle test` in a worktree at `HEAD` with `libs/onnxruntime/teste.jar` copied in, compared
against `.superpowers/conventions-alignment/post-migration-failures.txt` — the archived failure *messages*,
not the nine test names. The existing goldens are expected unaffected: `RiverGoldenTest`'s fixtures yield
zero local channels (so no sources), and `MeandersGoldenTest` asserts network topology rather than carved
output. Expected rather than assumed — it must be measured.

## Out of scope

- The tile-level shell carve (D12). If confluence bowls should shape the drainage field the local trace
  reads, that is a separate change to `computeRiverInfluenceGrid` and to all three shell call sites.
- `DELTA`, `WATERFALL`, `OXBOW_LAKE`, `ABANDONED_RIVER`. They stay position-only and carve nothing.
- `ZoneCategory`. Still reserved and dead; the radial pass does not read it.
- `RosgenProfile.blendMin` and the crease at the carve boundary that `profile/README.md` documents. The
  radial rim inherits the same crease behaviour and no more.

## Open questions

**Q1. The source bowl shrinks.** D7 makes `radius == width`, and `widthFromFlow` floors at
`MIN_WIDTH = 0.6` px — roughly 3 blocks, against the 2.0 px (~10 blocks) `DEFAULT_RADIUS` a
`SourcePrimitive` is indexed under today. A headwater spring will read as a small notch. The cone's
steeper walls offset this partially. If sources should stay visible at the top of the network, the fix is
a per-constant radius multiplier on `RadialProfile` rather than a special case in the emission path.
Raised with the user during design and deferred, not overlooked.

**Q2. A confluence at `MAX_WIDTH` is large.** `radius == width == 16` px puts the disc 32 px across —
about 160 blocks — and D8 makes it roughly 54 deep. Correct per D7 as specified; flagged because it is
the extreme of the chosen law and only shows up on the largest trunks.

## Docs to update

| File | Change |
| --- | --- |
| `ARCHITECTURE.md` | The bed-carve bullet gains the second pass; the superseded-designs paragraph is amended so "the `ConfluencePrimitive` junction ray-set" reads as the removed *mechanism*, not the removed name |
| `hydrology/features/CLAUDE.md` | Rows for `RadialPrimitive` and `ConfluencePrimitive`; `SourcePrimitive`'s row stops saying "position only" |
| `hydrology/features/README.md` | "only one of those is ever carved" is now false; the sort-order invariant gains the radial pass's walk-to-end rule |
| `hydrology/profile/CLAUDE.md` | Row for `RadialProfile` |
| `hydrology/profile/README.md` | The merge-law section gains D3–D6; `carvePrimitive` is renamed throughout |
| `world/gen/populatenoise/CLAUDE.md` | `fineGrainedPrimitivePass` merges two families, not one |
| `hydrology/network/CLAUDE.md` | `collectPrimitives` emits confluences |
