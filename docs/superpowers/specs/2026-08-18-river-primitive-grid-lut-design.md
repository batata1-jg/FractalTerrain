# River-primitive grid carve: one LUT-backed lattice pass for both carve stages

Date: 2026-08-18
Status: proposed
Branch: `feature/hydrology`

## Problem

River-primitive carving happens twice, in two places, with two different merge laws and two different
performance characters:

- **Bed carve** — `PopulateNoiseStep.carveRiverColumns`, 256 columns per chunk, every chunk generated.
  Per column per primitive it makes a megamorphic `RosgenProfile.delta` call whose body runs
  `Math.pow`/`Math.exp`/`Math.sqrt`, recomputes the two projections from scratch, and takes three
  branches that break the loop into unpredictable fragments.
- **Shell carve** — `HydrologyProfileInprinter.carveRiverShells`, 514x514 = 264k pixels per tile. It
  builds a fresh `ImmutableRTree` per call and then **stabs that tree once per pixel**
  (`HydrologyProfileInprinter.java:100-111`), allocating a `double[2]` point and a result `List` for
  every one of those 264k pixels.

Both are below this repo's hot/cold line (`ARCHITECTURE.md` "Hot/cold line of abstraction"), and both
violate its allocation rules today.

The two stages compute nearly the same thing — a per-lattice-point merged river surface — from the same
primitives against the same `RosgenProfile.delta`. This spec replaces both with one function, and has
that function also produce the two per-column layers the bed path fetches but never writes today:
`Types.WATER_HEIGHT` and `Types.RIVER_TYPE` (`PopulateNoiseStep.java:72-74`).

## Decisions

Settled during brainstorming; recorded here with rationale so they are not relitigated.

| Decision | Choice | Why |
| --- | --- | --- |
| Merge law | The bed's sequential smoothed-min-distance recurrence | Confirmed as the intended behaviour; the shell adopts it |
| `influenceWeight` | Stays pinned at `1.0` | Keeps the bed path's merge arithmetic unchanged; `river.w` stays unused |
| Accumulator width | `float` | The result is truncated to `float` anyway, and it halves every lane of the working set |
| Buffer ownership | Plain array parameters, no scratch class | See "Why no scratch class" |
| Recurrence state | River-private `dist`, reseeded per call | Other primitive families will compute separately, so nothing threads through |
| LUT anchoring | Integer `baseIdx` on a global perp lattice | Makes overlapping primitives quantise coherently and removes a division from the inner loop |
| LUT length | Capped at the grid diagonal | Bounds per-primitive setup below per-primitive carve work |
| Explicit SIMD | Dropped | The Vector API is an incubator module in Java 21; requiring `--add-modules jdk.incubator.vector` at runtime would make every player add a launcher flag |
| Scope | One function subsumes both stages | Removes the shell path's per-pixel R-tree entirely |

"Drop SIMD" means: no `jdk.incubator.vector`, no lane-width alignment, no masked-tail handling. Flat
primitive arrays, the LUT, incremental strength reduction, and branch-free bodies stay — those are
ordinary scalar quality, and they leave C2's auto-vectorizer a loop it can work with.

## Buffers

Four buffers, and the split between the first three is the point: `dist` is the only genuinely
sequential state, `acc` is the mergeable product, and they must not be fused. `lut` is pure scratch.

Two weights appear throughout and are not the same quantity. Lowercase `w` is one primitive's blend
weight, live only within one iteration of the inner loop. Uppercase `W` is the accumulated weight in
`acc[3i + 2]`, the merge's output.

| Buffer | Length | Role |
| --- | --- | --- |
| `float[] acc` | `3 * N`, stride `[h, water, w]` | The **output**. Ambient-free, and never the caller's elevation array. |
| `long[] typeMask` | `N` | Packed type of the nearest primitive |
| `float[] dist` | `N` | Running smoothed-min-distance — the recurrence's state |
| `float[] lut` | `maxLutLen` | Per-primitive cross-section table |

`w` is a pure function of `(dist[i], d)`; `h`, `water` and `w` are then accumulations driven by it. So
`h`/`water`/`w` interleave — one inner iteration touches all three — while `dist` stays separate because
it is state rather than product, and `typeMask` because it is a different element type.

Keeping `acc` distinct from the caller's elevation array is what lets `fineGrainedPrimitivePass` merge
other primitive families into the result later, in its own loop, without the river pass ever having seen
`ambient`.

### Why no scratch class

An earlier draft of this spec bundled the buffers into a `GridScratch` holder, justified by
`gridSize`/`resolution` determining the buffer sizes. That justification does not survive the buffer
split above: a holder owns exactly one `acc`, so a second primitive family needs a second holder, and
the two must then agree on a `gridSize` the holder existed to keep singular.

What a holder actually buys is reuse across calls, and reuse is a property of whoever keeps the buffers
between calls — not of the parameter's type. So the buffers are plain parameters, and each call site
owns a private `ThreadLocal` bundle. The cost is a nine-parameter signature with three `float[]` in a
row; it is mitigated by ordering the list frame / input / output / scratch and saying so in the
docstring.

## The (height, water, weight) decomposition

The recurrence looks ambient-dependent because `mergedElevation` is seeded at `ambient`. It is not.
With `M0 = ambient` and `Mk = (1 - wk) * Mk-1 + wk * ck`, unrolling gives:

```
Mn = A * ambient + SUM_k [ wk * ck * PROD_{j>k} (1 - wj) ]        where A = PROD_k (1 - wk)
```

Define `W = 1 - A` and `H = (SUM_k [...]) / W`. Then:

```
Mn = (1 - W) * ambient + W * H
```

exactly. So `(H, W)` is a lossless repacking of the recurrence, and the function can be written without
ever seeing `ambient` — **provided `ck` carries no ambient**.

The one place it does today is the per-primitive `Math.min(height, ambient)` in
`PopulateNoiseStep.blendElevation` (`PopulateNoiseStep.java:192`). This spec hoists that clamp to the
caller, applied once to the merged `H` rather than per primitive. That is a deliberate semantic change
(see Behaviour changes).

`W` accumulates directly, with no product to unwind:

```
Wk = Wk-1 + wk * (1 - Wk-1)          which satisfies  1 - Wn = PROD_k (1 - wk)
```

**The water lane needs no normalisation.** It is the same recurrence with a default of `0` rather than
`ambient`, and `(1 - W) * 0 + W * (acc / W)` collapses to `acc`. So the final pass divides the `h` lane
by `W` and leaves the `water` lane exactly as accumulated.

## API

### `RosgenProfile.sampleCrossSection`

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
        double curvature);
```

Body is `for (int i = 0; i < n; i++) lut[i] = (float) (elevation + delta(seed, (baseIdx + i) * step,
floodPlainLen, marginLen, depth, curvature));`. It writes only `lut[0..n)` and allocates nothing.

The caller supplies `elevation`, `floodPlainLen`, `marginLen`, `depth` and `curvature` rather than a
`RiverPrimitive`, matching the existing hoisted-extents `delta` overload
(`RosgenProfile.java:205-227`) and keeping the enum free of a dependency on `features/`.

### `HydrologyProfileInprinter.computeRiverGrid`

```java
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
        float[] lut);
```

Lattice point `(row, col)` sits at pixel `(startX + row * resolution, startZ + col * resolution)` and
occupies flat index `i = row * gridSize + col`; its triple starts at `acc[3 * i]`. Row is the X axis,
column the Z axis — the layout both call sites already use (`pi * paddedSize + pj` in the shell,
`(dx << 4) + dz` in the bed).

### `HydrologicalFeature.pack`

```java
/** Packs this family with a family-specific sub-classification into one lattice cell.
 *  Same split as {@link RiverPrimitive#ids}: family in the high word, sub-type in the low. */
public long pack(int subOrdinal);

/** The family in a packed cell, or {@code null} for {@link #NONE}. */
public static @Nullable HydrologicalFeature unpack(long packed);

public static int unpackSub(long packed);
```

On the `HydrologicalFeature` enum rather than the `HydrologicalPrimitive` interface: an instance method
reads as `RIVER.pack(...)` at the call, and only the enum can cache `values()` in a private static field
so unpacking a lattice cell does not clone the constant array per call.

For a `RiverPrimitive` the sub-ordinal is `RosgenType.orDefault(rosgenType).ordinal()` — the profile
actually carved, not the unclassified `null`. Other families define their own low word later.

`HydrologicalFeature.NONE == -1L` is the "no primitive reached this point" sentinel. `0L` cannot be: it
reads as `RIVER` + `A`.

## Algorithm

### Seeding

`computeRiverGrid` reseeds on entry — callers reuse buffers across many calls and never clear them
themselves. Per lattice point: `acc[3i] = acc[3i+1] = acc[3i+2] = 0`, `typeMask[i] = -1`,
`dist[i] = UNSET_MIN_DIST` (64.0, moved from `PopulateNoiseStep`).

Reseeding `dist` here is safe precisely because rivers own it privately; see Out of scope for what
changes if another family ever has to continue the same chain.

### Per primitive (once, outside the lattice loop)

1. Skip a `null` `normal` — no tangent, and `containsPoint` would NPE. Unchanged from
   `carveRiverColumns` (`PopulateNoiseStep.java:130`).
2. Conservative AABB clip, generalised from the current block-frame form to the lattice frame:
   `halfExtent = influence * (|nx| + |nz|)`;
   `rowMin = clamp(floor((cx - halfExtent - startX) / resolution), 0, gridSize - 1)`, and the three
   analogous bounds. Bail if empty.
3. Reachable perp range. `perp` is affine in the lattice coordinates, so its extrema over the grid are
   at the four corners: evaluate `nx * (px - cx) + nz * (pz - cz)` at each, take min/max, intersect with
   `[-influence, +influence]`, bail if empty. This is what caps the LUT at the grid diagonal.
   Iteration is rows outer, columns inner, so each row's column range is a contiguous run of flat
   indices and the increments below hold across it.
4. Hoist the width-invariant extents exactly as `carveRiverColumns` does today
   (`PopulateNoiseStep.java:144-151`): `profile`, `seed`, `floodPlainLen`, `marginLen`, `depth`,
   `curvature`, `elevation`.
5. Hoist the two per-primitive constants this spec adds:
   `waterSurface = elevation + HydrologicalPrimitive.waterLine(width)` and
   `packed = HydrologicalFeature.RIVER.pack(RosgenType.orDefault(rosgenType).ordinal())`.
6. `baseIdx = (int) Math.floor(perpMin * invStep)`;
   `n = (int) Math.floor(perpMax * invStep) - baseIdx + 2`;
   `profile.sampleCrossSection(lut, n, step, baseIdx, ...)`.

### Per lattice point

Per column step, `perp`, `tang` and the LUT index all advance by constants:

```
perp += nz * resolution
tang -= nx * resolution
f    += nz                      // because step == resolution; see LUT contract
```

`normal` is unit length (`Centreline.normalAt:29-32` normalises before taking the perpendicular), so
the distance to the primitive's centre is `d = sqrt(tang * tang + perp * perp)` and the current separate
`sqrt(ddx^2 + ddz^2)` is redundant.

The body is branch-free, because `w = 0` is the recurrence's identity:

```
mask = (|tang| <= influence) && (|perp| <= influence)   -> 1.0 or 0.0
t    = clamp(((dist[i] - d) / SMOOTH_STEP_DIVISOR + 1) * 0.5, 0, 1)
w    = t * t * (3 - 2 * t) * mask
i0   = clamp((int) f, 0, n - 2)
h    = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0])
dist[i]     = (1 - w) * dist[i] + w * d
acc[3i]     = (1 - w) * acc[3i]     + w * h
acc[3i + 1] = (1 - w) * acc[3i + 1] + w * waterSurface
acc[3i + 2] = acc[3i + 2] + w * (1 - acc[3i + 2])
typeMask[i] = (w > 0.5) ? packed : typeMask[i]
```

`SMOOTH_STEP_DIVISOR` (0.1) moves from `PopulateNoiseStep` alongside `UNSET_MIN_DIST`. The `w == 0`
early-out is dropped: it is a no-op on all four updates, and removing it removes a branch.

The index clamp is a safety guard only — `mask` already zeroes any point outside `[-influence,
+influence]` — but it must be present, because the branch-free body evaluates `h` for masked-off lanes.

`w > 0.5` is the nearest-primitive test. It is one compare on a value already in a register, and it
subsumes the influence mask (a masked-off lane has `w == 0`). With `SMOOTH_STEP_DIVISOR` at 0.1 the
weight is effectively a hard 0/1 selector, so this is the true nearest everywhere outside a 0.1-wide
band; inside that band it defers to the incumbent, which is consistent with who owns the elevation
blend there.

### Normalisation

One final pass over the `h` lane, against the accumulated weight `W`:
`if (W > 0) acc[3i] /= W;`. Where `W == 0`, `h` stays `0` and the caller's blend degenerates to
`ambient`, so the guard also avoids a `0/0` NaN. The `water` lane is skipped — see the decomposition
section.

## LUT contract

The LUT is anchored on an integer index into a lattice of perp values that starts at `perp == 0` and is
shared by every primitive. `baseIdx` is that lattice index of `lut[0]`; the lookup subtracts it.

```
invStep = 1.0 / step                                   // step == resolution, a grid constant
baseIdx = (int) Math.floor(perpMin * invStep)          // per-primitive constant, an integer
n       = (int) Math.floor(perpMax * invStep) - baseIdx + 2
lut[i]  = elevation + delta(seed, (baseIdx + i) * step, ...)

f  = perp * invStep - baseIdx                          // per lattice point
i0 = clamp((int) f, 0, n - 2)
h  = lut[i0] + (f - i0) * (lut[i0 + 1] - lut[i0])
```

- **Anchoring.** Because `baseIdx` is an integer, every primitive samples `delta` at the *same* global
  perp lattice. Two overlapping primitives therefore quantise coherently, instead of each carrying its
  own arbitrary sub-cell offset. It also removes the division from the inner loop.
- **Step** equals `resolution`. Along a row `perp` advances by `nz * resolution`, and `|nz| <= 1`, so one
  LUT cell is never coarser than one lattice move. Because the two are equal, the index increment
  `nz * resolution * invStep` reduces to plain `nz`.
- **Buffer size** `maxLutLen = min(ceil((gridSize - 1) * sqrt(2)),
  ceil(2 * MAX_INFLUENCE_RADIUS / resolution)) + 3`. The `+3` covers the two `floor`s in `n` disagreeing
  by one plus the `+2` interpolation margin; over-allocating a few floats is free, being one short is a
  crash.
- **Interpolation** is linear.

Concretely, with `GLOBAL_SCALE_CORRECTION = 5` and `MAX_INFLUENCE_RADIUS = 64`:

| Grid | `gridSize` | `resolution` | Diagonal bound | Influence bound | `maxLutLen` | Points |
| --- | --- | --- | --- | --- | --- | --- |
| Bed (chunk) | 16 | 0.2 px | 25 | 643 | **25** | 256 |
| Shell (tile) | 514 | 1.0 px | 729 | 131 | **131** | 264,196 |

Per-primitive setup is an order of magnitude below per-primitive carve work on both grids, which is what
makes the LUT a win even for a primitive touching few points.

## Working set

Per generation thread, per call site:

| Buffer | Bed (256 points) | Shell (264,196 points) |
| --- | --- | --- |
| `acc` | 3 KB | 3.17 MB |
| `typeMask` | 2 KB | 2.11 MB |
| `dist` | 1 KB | 1.06 MB |
| `lut` | 100 B | 524 B |
| **Total** | **~6 KB** | **~6.34 MB** |

The shell figure is up from ~3.2 MB for an `acc`-plus-`dist` pair without the water lane or the type
mask. `carveRiverShells` wants neither of the two additions and pays 2.11 MB + 1.06 MB for them anyway.
The alternative — nullable `typeMask`, skipping the store — puts a check in the hot loop or forks the
loop in two; paying the memory is the better trade at 264k points. Re-examine if the tile grid grows.

## Call site changes

### `PopulateNoiseStep.fineGrainedPrimitivePass`

`carveRiverColumns`, `blendDistance` and `blendElevation` are deleted, along with the `PAIR`, `WEIGHT`,
`FULL_WEIGHT`, `SMOOTH_STEP_DIVISOR` and `UNSET_MIN_DIST` constants. The method keeps its
`prefetchChunk` call (already sorted) and becomes:

```java
computeRiverGrid(chunkMinX / scale, chunkMinZ / scale, 1.0 / scale, 16,
                 primitives, acc, typeMask, dist, lut);
for (int pos = 0; pos < COLUMNS; pos++) {
    final float ambient = interpolatedElevs[pos];
    final float weight = acc[3 * pos + 2];
    final double merged = weight > 0
            ? (1 - weight) * ambient + weight * Math.min(acc[3 * pos], ambient)
            : ambient;
    riverDifference[pos]   = (float) (merged - ambient);
    waterElev[pos]         = weight > 0 ? (float) (acc[3 * pos + 1] + seaLevel - 1) : 0f;
    riverType[pos]         = typeMask[pos];
    interpolatedElevs[pos] = (float) (Math.max(bottom, merged) + seaLevel - 1);
}
```

`acc[3 * pos]` is already `H` — the normalisation pass divided it by `W` — so the blend above does not
divide again.

The buffers are `ThreadLocal`, not fields: `PopulateNoiseStep` is one instance shared across chunk
generation threads.

`waterElev` and `riverType` are written for the first time; both are fetched and discarded today
(`PopulateNoiseStep.java:72-74`). A water height of `0` is inert at the consumer, which takes
`max(reliefHeight, max(seaLevel, waterHeight))` (`FractalTerrainChunkGenerator.doFill`).

No `min`-against-ambient clamp on the water lane: a water surface above local terrain is exactly what
fills a channel.

### `Types.RIVER_TYPE` is retyped

`HydrologicalPrimitive.HydrologicalFeature[]` becomes `long[]`, packed as `HydrologicalFeature.pack` above. Nothing
reads the layer today and it is recomputed per chunk rather than persisted, so the retype costs nothing
and no on-disk format changes. Mark the packing helper `:SCHEMA:`.

### `HydrologyProfileInprinter.carveRiverShells`

Reduced to a `computeRiverGrid` call plus the ambient blend, keeping its two existing guards:

- ocean skip — `if (ambient < 0) continue`, now caller-side
- the `weight <= 1e-8` no-op skip, now a caller-side `if (acc[3i + 2] <= 1e-8) continue`

It ignores the `water` lane and `typeMask`. `ImmutableRTree`, the per-pixel `double[]` point, the
`queryContaining` result lists and the `CARVE_INDEX_SLACK` constant all go away. It keeps reading and
writing the same buffer, so repeated calls still compound.

Signature changes from `(float[], HydrologicalPrimitive[], int)` to
`(float[] elevation, List<HydrologicalPrimitive> primitives, int paddedSize)`, with its own
`ThreadLocal` buffers. `LocalRiverProvider` (`LocalRiverProvider.java:166-169`) drops its
`.toArray(new HydrologicalPrimitive[0])` and sorts the list by `HydrologicalPrimitive.comparator`
first — required by `computeRiverGrid`'s ordering contract, and not something `collectPrimitives`
guarantees today.

## Behaviour changes

Ordered by blast radius.

1. **Shell merge law changes** — from an order-independent weighted average keyed on `river.w` to the
   distance recurrence with `influenceWeight = 1.0`. Shells lose their per-primitive footprint
   weighting and become purely distance-driven.
2. **Traced networks change.** `buildTile` carves shells into `carvedElevationGlobal`, which is the
   drainage input for the local trace (`LocalRiverProvider.java:166-178`). A different shell means a
   different drainage field, a different local trace, and different rivers. **Worlds regenerate
   differently.** This is the change to be sure about.
3. **Shells can no longer raise terrain.** They currently overwrite a pixel with a weighted average that
   may sit above ambient. The hoisted `min(h, ambient)` makes both stages cut-only.
4. **`Types.WATER_HEIGHT` is populated.** It reads `0` everywhere today, so every column falls through
   to `max(reliefHeight, seaLevel)`. River columns now get a real water surface, which is new water
   placed in the world — the first visible effect of this change beyond terrain shape.
5. **`Types.RIVER_TYPE` is populated and retyped** to `long[]`. No consumer exists yet, so this is
   inert until one is written.
6. **The `min` clamp moves from per-primitive to post-merge**, changing bed output where primitives of
   differing heights overlap a column.
7. **`float` accumulators and LUT quantisation** perturb bed output at the last significant digits.

Nothing here changes the on-disk primitive format, so cached tiles stay readable — but they hold
elevations carved under the old law, so a world in progress will show a seam at the boundary between
tiles cached before and after. Regenerate rather than mix.

## Known residuals

1. **LUT smearing at the margin.** At `step == resolution`, linear interpolation smears the
   `perpDist <= marginLen -> -10` discontinuity (`RosgenProfile.java:224`) across one lattice cell —
   **one block wide in the bed path**, one pixel in the shell path. Halving the step removes it at 2x LUT
   size (48 entries chunk-side) but breaks the grid-diagonal bound. Accepted, not overlooked; revisit if
   bed rims read as soft.
2. **Water fades toward `0` rather than toward a normalised surface.** With `SMOOTH_STEP_DIVISOR` at 0.1
   the weight is effectively 0 or 1 everywhere, so the two are indistinguishable today. They diverge only
   if the constant is restored to a real blend width — and fading out at the influence fringe is the
   behaviour you would want then, rather than a full-height surface standing at the edge of the
   floodplain. Recorded so the choice is not mistaken for an oversight later.

## Out of scope

- **Merging other primitive families.** Oxbow, delta, waterfall and confluence primitives will compute
  separately, into their own buffers, and `fineGrainedPrimitivePass` will merge them in its main loop.
  The cross-family merge law is not designed here. Note the consequence: if a family ever has to share
  the river `dist` chain rather than run its own, the reseed in `computeRiverGrid` must move out to the
  caller, because only the caller knows whether the chain continues.
- `HydrologyProfileInprinter.sampleNearestChannel` — no `src/main` caller, and the three test files
  referencing its old signature are among the four that already do not compile (root `CLAUDE.md`,
  Test section). Left exactly as it is.
- `RosgenProfile.blendMin` — still callerless; wiring it back in is its own change.
- `HydrologyProfilePainter` and its `queryInfluence` path.
- Restoring `river.w` as a real influence weight, and the three `Math.pow(x, 2)` calls in
  `RiverPrimitive.w` (`RiverPrimitive.java:99-101`). `w` stays unused.
- Reviving `ZoneCategory`.

## Verification

`gradle spotlessApply` then `gradle build`.

Because this is hydrology math, also `gradle test` — against the recorded baseline, not against zero.
Per root `CLAUDE.md`: the suite does not compile at `1d32c85`; deleting the four broken test files
locally yields **74 tests, 19 failed, 1 skipped**, distributed as `RosgenKeyTest` (6),
`ConfluencePrimitiveTest` (4), `ChannelGeometryTest` (3), `LocalRiverGoldenTest` (2),
`MeandersGoldenTest` (2), `GlobalRiverGoldenTest` (1), `ReachMetricsSamplerTest` (1).

Behaviour change 2 means `LocalRiverGoldenTest` and `GlobalRiverGoldenTest` are **expected to move**.
Compare the actual failure messages in `build/test-results/test/*.xml` against a `HEAD` worktree — a
worktree needs `libs/onnxruntime/teste.jar` copied in, since `libs/` is git-ignored and its absence
produces ~132 phantom errors.

Visual check: `gradle localRiverTest` and `gradle globalRiverTest` dump PNGs; compare shell shape
before and after. `gradle runClient` is the only check that exercises behaviour change 4 — water in a
river channel is not visible in any PNG dump.

## Doc updates

- `hydrology/profile/README.md` — the shell/bed split, the R-tree removal, the new merge law, the
  cut-only property, the LUT residual. Several claims there also go stale: it describes `carveRiverShells`
  as running twice per tile, but `LocalRiverProvider` calls it once (`LocalRiverProvider.java:166`).
- `hydrology/profile/CLAUDE.md` — `HydrologyProfileInprinter` and `RosgenProfile` rows.
- `world/gen/populatenoise/CLAUDE.md` — the row still describes a `resolveRiverColumns` that no longer
  exists; it becomes the `computeRiverGrid` + ambient-blend description.
- `storage/` docs — the `Types.RIVER_TYPE` retype and its packing.
- `ARCHITECTURE.md` "Hot sites in this repo" — the `PopulateNoiseStep` line/range and the
  `sampleNearestChannel`/`NearestChannelSample.carveInto` entry, which names symbols that are gone.
