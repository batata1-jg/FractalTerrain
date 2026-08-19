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
primitives against the same `RosgenProfile.delta`. This spec replaces both with one function.

## Decisions

Settled during brainstorming; recorded here with rationale so they are not relitigated.

| Decision | Choice | Why |
| --- | --- | --- |
| Merge law | The bed's sequential smoothed-min-distance recurrence | Confirmed as the intended behaviour; the shell adopts it |
| `influenceWeight` | Stays pinned at `1.0` | Keeps the bed path's merge arithmetic unchanged; `river.w` stays unused |
| Accumulator width | `float` | Halves the working set (the shell's `dist` array goes 2 MB -> 1 MB, a real cache effect at 264k points); the result is truncated to `float` anyway |
| LUT length | Capped at the grid diagonal | Bounds per-primitive setup below per-primitive carve work |
| Explicit SIMD | Dropped | The Vector API is an incubator module in Java 21; requiring `--add-modules jdk.incubator.vector` at runtime would make every player add a launcher flag |
| Scope | One function subsumes both stages | Removes the shell path's per-pixel R-tree entirely |

"Drop SIMD" means: no `jdk.incubator.vector`, no lane-width alignment, no masked-tail handling. Flat
primitive arrays, the LUT, incremental strength reduction, and branch-free bodies stay — those are
ordinary scalar quality, and they leave C2's auto-vectorizer a loop it can work with.

## The (height, weight) decomposition

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

## API

### `RosgenProfile.sampleCrossSection`

```java
/**
 * Tabulates this profile's cross-section into {@code lut}: {@code lut[i]} is the channel surface at
 * signed perpendicular distance {@code perpMin + i * step}, with the primitive's base elevation folded
 * in. Runs once per primitive per grid, so the branchy per-region logic in {@link #delta} leaves the
 * per-lattice-point loop entirely.
 */
public void sampleCrossSection(
        float[] lut,
        int n,
        double step,
        double perpMin,
        long seed,
        double elevation,
        double floodPlainLen,
        double marginLen,
        double depth,
        double curvature);
```

Body is `for (int i = 0; i < n; i++) lut[i] = (float) (elevation + delta(seed, perpMin + i * step,
floodPlainLen, marginLen, depth, curvature));`. It writes only `lut[0..n)` and allocates nothing.

The caller supplies `elevation`, `floodPlainLen`, `marginLen`, `depth` and `curvature` rather than a
`RiverPrimitive`, matching the existing hoisted-extents `delta` overload
(`RosgenProfile.java:205-227`) and keeping the enum free of a dependency on `features/`.

### `HydrologyProfileInprinter.GridScratch`

```java
/** Preallocated working set for {@link #computeGrid}. Sized once from the grid geometry; holds every
 *  buffer the carve mutates, so the carve itself allocates nothing.
 *
 *  <p>Not thread-safe by construction. Chunk generation is multithreaded, so each thread owns one. */
public static final class GridScratch {
    public GridScratch(int gridSize, double resolution);

    /** Interleaved (height, weight) pairs, {@code 2 * gridSize * gridSize} long. Valid after a
     *  {@link #computeGrid} call; index {@code 2 * (row * gridSize + col)} holds the height. */
    public float[] heightWeight();
}
```

Internally it owns:

- `float[] heightWeight` — `2 * gridSize * gridSize`
- `float[] dist` — `gridSize * gridSize`, the recurrence's running smoothed-min-distance state
- `float[] lut` — `maxLutLen(gridSize, resolution)` (see LUT contract)

`gridSize` and `resolution` live on the scratch rather than on the call because they are exactly the two
inputs that determine buffer sizes; `startX`/`startZ`/`primitives` vary per call and stay parameters.
Both are still inputs to the operation as a whole.

### `HydrologyProfileInprinter.computeGrid`

```java
/**
 * Merges every river primitive into a lattice of (height, weight) pairs, written to
 * {@code scratch.heightWeight()}. Ambient-free by construction: a caller recovers its carved elevation
 * as {@code (1 - w) * ambient + w * min(h, ambient)}.
 *
 * <p>{@code primitives} MUST be sorted by {@link HydrologicalPrimitive#comparator}. The merge is a
 * sequential recurrence, so order is load-bearing for determinism, and the loop stops at the first
 * non-{@link RiverPrimitive} entry — which lands at the true end of the river run only because that
 * comparator orders by feature-type ordinal first.
 */
public static void computeGrid(
        double startX, double startZ, List<HydrologicalPrimitive> primitives, GridScratch scratch);
```

Lattice point `(row, col)` sits at pixel `(startX + row * resolution, startZ + col * resolution)` and
occupies flat index `row * gridSize + col`. Row is the X axis, column the Z axis — the layout both call
sites already use (`pi * paddedSize + pj` in the shell, `(dx << 4) + dz` in the bed).

## Algorithm

### Seeding

`computeGrid` reseeds the scratch on entry — callers reuse one `GridScratch` across many calls and never
clear it themselves. Per lattice point: `heightWeight[2i] = 0`, `heightWeight[2i+1] = 0`,
`dist[i] = UNSET_MIN_DIST` (64.0, moved from `PopulateNoiseStep`).

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
   indices and the three increments below hold across it.
4. Hoist the width-invariant extents exactly as `carveRiverColumns` does today
   (`PopulateNoiseStep.java:144-151`): `profile`, `seed`, `floodPlainLen`, `marginLen`, `depth`,
   `curvature`, `elevation`.
5. `profile.sampleCrossSection(scratch.lut, n, step, perpMin, ...)`.

### Per lattice point

Per row, `perp`, `tang` and the LUT index all advance by constants:

```
perp  += nz * resolution
tang  -= nx * resolution
idx   += (nz * resolution) / step
```

`normal` is unit length (`Centreline.normalAt:29-32` normalises before taking the perpendicular), so
`dist^2 == tang^2 + perp^2` and the current separate `sqrt(ddx^2 + ddz^2)` is redundant.

The body is branch-free, because `w = 0` is the recurrence's identity:

```
mask = (|tang| <= influence) && (|perp| <= influence)   -> 1.0 or 0.0
t    = clamp(((dist[i] - d) / SMOOTH_STEP_DIVISOR + 1) * 0.5, 0, 1)
w    = t * t * (3 - 2 * t) * mask
h    = lerp(lut[floor(idx)], lut[floor(idx) + 1], frac(idx))     // idx clamped to [0, n - 2]
dist[i]            = (1 - w) * dist[i] + w * d
heightWeight[2i]   = (1 - w) * heightWeight[2i] + w * h
heightWeight[2i+1] = heightWeight[2i+1] + w * (1 - heightWeight[2i+1])
```

`SMOOTH_STEP_DIVISOR` (0.1) moves from `PopulateNoiseStep` alongside `UNSET_MIN_DIST`. The `w == 0`
early-out is dropped: it is a no-op on all three updates, and removing it removes a branch.

The index clamp is a safety guard only — `mask` already zeroes any point outside `[-influence,
+influence]` — but it must be present, because the branch-free body evaluates `h` for masked-off lanes.

### Normalisation

One final pass: `if (W > 0) H /= W;`. Where `W == 0`, `H` stays `0` and the caller's blend degenerates
to `ambient`, so the guard also avoids a `0/0` NaN.

## LUT contract

- **Step** equals `resolution`. Along a row `perp` advances by `nz * resolution`, and `|nz| <= 1`, so one
  LUT cell is never coarser than one lattice move. Because the two are equal, the index increment
  `(nz * resolution) / step` reduces to plain `nz`.
- **Length** `n = ceil((perpMax - perpMin) / step) + 1`.
- **Buffer size** `maxLutLen = min(ceil((gridSize - 1) * sqrt(2)) + 1,
  ceil(2 * MAX_INFLUENCE_RADIUS / resolution) + 1)`.
- **Interpolation** is linear.

Concretely, with `GLOBAL_SCALE_CORRECTION = 5`:

| Grid | `gridSize` | `resolution` | Diagonal bound | Influence bound | `maxLutLen` | Points |
| --- | --- | --- | --- | --- | --- | --- |
| Bed (chunk) | 16 | 0.2 px | 23 | 641 | **23** | 256 |
| Shell (tile) | 514 | 1.0 px | 727 | 129 | **129** | 264,196 |

Per-primitive setup is an order of magnitude below per-primitive carve work on both grids, which is what
makes the LUT a win even for a primitive touching few points.

## Call site changes

### `PopulateNoiseStep.fineGrainedPrimitivePass`

`carveRiverColumns`, `blendDistance` and `blendElevation` are deleted, along with the `PAIR`, `WEIGHT`,
`FULL_WEIGHT`, `SMOOTH_STEP_DIVISOR` and `UNSET_MIN_DIST` constants. The method keeps its
`prefetchChunk` call (already sorted) and becomes:

```
computeGrid(chunkMinX / scale, chunkMinZ / scale, primitives, scratch)
per column:
    h = heightWeight[2i]; w = heightWeight[2i+1]
    merged             = (1 - w) * ambient + w * min(h, ambient)
    riverDifference[i] = merged - ambient
    interpolatedElevs[i] = max(bottom, merged) + seaLevel - 1
```

`GridScratch` is `ThreadLocal`, not a field: `PopulateNoiseStep` is one instance shared across chunk
generation threads.

### `HydrologyProfileInprinter.carveRiverShells`

Reduced to a `computeGrid` call plus the ambient blend, keeping its two existing guards:

- ocean skip — `if (ambient < 0) continue`, now caller-side
- the `weight <= 1e-8` no-op skip, now a caller-side `if (w <= 1e-8) continue`

`ImmutableRTree`, the per-pixel `double[]` point, the `queryContaining` result lists and the
`CARVE_INDEX_SLACK` constant all go away. It keeps reading and writing the same buffer, so repeated
calls still compound.

Signature changes from `(float[], HydrologicalPrimitive[], int)` to
`(float[] elevation, List<HydrologicalPrimitive> primitives, GridScratch scratch)`. `LocalRiverProvider`
(`LocalRiverProvider.java:166-169`) drops its `.toArray(new HydrologicalPrimitive[0])` and sorts the
list by `HydrologicalPrimitive.comparator` first — required by `computeGrid`'s ordering contract, and
not something `collectPrimitives` guarantees today.

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
4. **The `min` clamp moves from per-primitive to post-merge**, changing bed output where primitives of
   differing heights overlap a column.
5. **`float` accumulators and LUT quantisation** perturb bed output at the last significant digits.

Nothing here changes the on-disk primitive format, so cached tiles stay readable — but they hold
elevations carved under the old law, so a world in progress will show a seam at the boundary between
tiles cached before and after. Regenerate rather than mix.

## Known residual

At `step == resolution`, linear interpolation smears the `perpDist <= marginLen -> -10` discontinuity
(`RosgenProfile.java:224`) across one lattice cell — **one block wide in the bed path**, one pixel in the
shell path. Halving the step removes it at 2x LUT size (46 entries chunk-side) but breaks the
grid-diagonal bound. Recorded as accepted, not overlooked; revisit if bed rims read as soft.

## Out of scope

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
before and after.

## Doc updates

- `hydrology/profile/README.md` — the shell/bed split, the R-tree removal, the new merge law, the
  cut-only property, the LUT residual. Several claims there also go stale: it describes `carveRiverShells`
  as running twice per tile, but `LocalRiverProvider` calls it once (`LocalRiverProvider.java:166`).
- `hydrology/profile/CLAUDE.md` — `HydrologyProfileInprinter` and `RosgenProfile` rows.
- `world/gen/populatenoise/CLAUDE.md` — the row still describes a `resolveRiverColumns` that no longer
  exists; it becomes the `computeGrid` + ambient-blend description.
- `ARCHITECTURE.md` "Hot sites in this repo" — the `PopulateNoiseStep` line/range and the
  `sampleNearestChannel`/`NearestChannelSample.carveInto` entry, which names symbols that are gone.
