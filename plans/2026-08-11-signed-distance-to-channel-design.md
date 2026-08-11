# Signed distance from a point to a channel centreline

**Date:** 2026-08-11
**Branch:** `feature/hydrology`
**Touches:** `math/VectorOps`, `hydrology/profile/HydrologyProfileInprinter`,
`hydrology/features/RiverPrimitive`, `world/gen/populatenoise/PopulateNoiseStep`

## Problem

`RiverPrimitive.d(pt)` returns `dot(normal, pt - coord)` — the signed distance to the *tangent line
at one knot*, not to the channel. Every primitive of the same channel therefore answers a different
distance for the same pixel.

The per-pixel carve blends those answers by weight. Disagreeing distances do not cancel: one
primitive reports the pixel is in the bed, another reports floodplain, and the weighted mean of two
different zones of `RosgenProfile.delta` is a value neither profile ever produces. The blend makes
the error worse, not smaller.

## Approach

One signed distance per channel, computed once, feeding one profile evaluation. Nearest channel
wins outright — confluences will be resolved later by dedicated junction primitives, not by
blending channels here.

Distance is measured to the **two-segment polyline** through the nearest knot and its neighbours,
not to the analytic quintic. The primitives carry `coord`, `normal` and `curvature` but not the
quintic's velocity/acceleration, so the true curve cannot be rebuilt at carve time without widening
the persisted payload. Resampling already holds knot spacing near `width/2`, which bounds the
chord-cuts-the-corner error well below a block.

## Naming

The design reuses the vocabulary already in the hydrology package rather than inventing a parallel
one. `signedPerpDist` is the name `RosgenProfile.delta`, `bedDelta` and `floodPlainDelta` already
give this quantity, and `ambientElevation` extends the `ambient` local that
`HydrologyProfileInprinter.carveRiverShells` uses for the pre-carve elevation.

Two index spaces run through this design and must not share a name:

- **`knotIndex`** — a position along the spline, the low word of `RiverPrimitive.ids`.
- **`primitiveIndex`** — a position in the prefetched, sorted primitive list.

Section 3 is entirely about the cases where the two disagree, so every identifier says which one it
means.

## Design

### 1. `projectPointOntoSegment` — the geometry kernel

New allocation-free static in `VectorOps`, matching the `mutablePt` scratch style of the hot loop:

```java
/** Projects point onto the segment segStart→segEnd. Writes {segParam, distSq} into outProjection. */
public static void projectPointOntoSegment(
        double[] point, double[] segStart, double[] segEnd, double[] outProjection)
```

```
segVec        = segEnd - segStart
segLenSq      = dot(segVec, segVec)
segParam      = segLenSq < 1e-12 ? 0 : clamp(dot(point - segStart, segVec) / segLenSq, 0, 1)
footPoint     = segStart + segParam * segVec
outProjection = { segParam, |point - footPoint|² }
```

`segParam` is the normalised position along the segment. It is handed back out of the helper rather
than recomputed, because section 4 needs it to interpolate the channel attributes at that same foot
point.

### 2. Signing the distance from the foot-point normal

From the interpolated normal at the foot, **not** from a cross product:

```
footNormal     = normalize(lerp(normal[knotIndex], normal[knotIndex + 1], segParam))
signedPerpDist = sign(dot(footNormal, point - footPoint)) * sqrt(distSq)
```

Two reasons:

- It agrees by construction with the existing `RiverPrimitive.d(pt) = dot(normal, pt - coord)`, so
  the asymmetric F/G profiles in `RosgenProfile` keep the sign they were tuned against.
- In the segment interior `dot(footNormal, point - footPoint)` *is* the signed distance
  (`point - footPoint ⊥ segVec` and `footNormal ⊥ segVec`). The two forms differ only in the corner
  wedge where `segParam` clamps, and there `sign(·) · |point - footPoint|` is the one that does not
  underestimate.

### 3. Choosing the segment pair — the channel-continuity guard

`nearestPrimitiveIndex` indexes the sorted prefetch list; `RiverPrimitive.ids` packs
`channelId << 32 | knotIndex`. A neighbouring entry at `nearestPrimitiveIndex ± 1` is usable as a
segment endpoint only if all three hold:

1. in range and `instanceof RiverPrimitive`
2. **same channel** — `ids >>> 32` matches
3. **knot-adjacent** — `(int) ids` differs by exactly 1

Check 3 exists because `prefetchChunk` runs a *spatial* query, so list adjacency does not imply knot
adjacency: a channel that grazes the chunk, or a meander that loops back into it, appears as several
non-consecutive runs of knots that sort into neighbouring list slots. Without the check a segment
would be built across the gap between two runs.

- both neighbours usable → project onto both segments, keep the smaller `distSq`
- one usable → single segment
- neither usable → fall back to `dot(normal[nearestKnot], point - coord[nearestKnot])`

The fallback is not a degradation: a lone influencing knot means only that knot influences the
pixel, and the tangent line is the correct answer there.

### 4. Interpolating channel attributes at the foot point

This is what makes one-evaluation-per-channel actually work. Given the winning segment
`(knotIndex, knotIndex + 1)` and its `segParam`:

| attribute | value at the foot point |
| --- | --- |
| `channelWidth` | `lerp(width[knotIndex], width[knotIndex + 1], segParam)` |
| `bedElevation` | `lerp(elevation[knotIndex], elevation[knotIndex + 1], segParam)` — monotone downstream |
| `channelCurvature` | `lerp(curvature[knotIndex], curvature[knotIndex + 1], segParam)` |
| `rosgenType` | discrete — nearest knot, `segParam < 0.5 ? knotIndex : knotIndex + 1` |

Reading `channelWidth` off the knot instead would step the bed half-width between knots,
reintroducing a smaller version of the original problem.

`RosgenProfile.delta` also takes a `randSeed`, today `primitive.hashCode()`, which jumps at every
knot boundary. Pass `channelId` so it stays constant along a reach. This is currently inert — no
`bedDelta` override consumes the seed (the one that did is commented out at `RosgenProfile.java:112`)
— but it must be right before bed noise is re-enabled.

### 5. `NearestChannelSample` — what the carve returns

```java
public record NearestChannelSample(
        double signedPerpDist,
        double channelWidth,
        double channelCurvature,
        double bedElevation,
        double carvedElevation,
        RosgenType rosgenType) {}
```

Two renames come with it, because the current names describe the old blend-everything shape:

| now | becomes | why |
| --- | --- | --- |
| `carveRiverPrimitives(primitives, id, pt)` | `sampleNearestChannel(primitives, nearestPrimitiveIndex, point)` | it resolves one channel's cross-section; carving is what the *caller* does with it |
| `resolveRiverNearestId(primitives, pt)` | `resolveNearestPrimitiveIndex(primitives, point)` | `id` collides with `knotIndex`; this returns a list index |

`sampleNearestChannel` returns `null` when `nearestPrimitiveIndex == -1`, rather than the
`double[]{0, 0}` sentinel the current stub returns — the caller cannot mistake absence for a
zero-distance hit.

The record is allocated once per pixel (256 per chunk), against the grain of a loop that reuses
`mutablePt` to avoid exactly that. It never escapes the loop body, so escape analysis should stack
allocate it, and the cost is noise beside a diffusion inference. Fallback if that bet proves wrong:
a caller-owned `double[5]` scratch, with the `RosgenType` returned as the method's value.

Knock-on: `HydrologicalPrimitive.waterLine()` is an instance method switching on `width()`. With an
interpolated width it becomes a static `waterLine(double channelWidth)`; otherwise the caller reads
the knot's width back after the interpolation deliberately avoided it.

### 6. Composing the carved elevation with ambient terrain

Nearest-channel-wins removes the `weight` that used to fade the river out, so the influence
boundary needs handling. It falls out for free:

```
carvedElevation = smoothMin(
        ambientElevation,
        bedElevation + profile.delta(channelId, signedPerpDist, channelWidth, channelCurvature),
        lambda)
```

Outside the floodplain `delta` returns `valleyDelta(perpDist - floodPlainLen)`, a cone rising away
from the channel. `min` selects ambient wherever that cone climbs above it, so the river tapers at
its own rim rather than at an arbitrary influence radius. `smoothMin` — already used by
`shellElevation` — rounds that rim.

This enforces the invariant that a river only ever cuts down, and composes correctly with the tile
shell stage: the shell's output *is* `ambientElevation` here, and the bed trench goes below it.

## Out of scope

- **Confluence blending.** Junctions get their own primitive type. Until then a confluence shows the
  seam where the nearest-channel winner flips.
- **`RosgenProfile.delta:232`,** `if (perpDist <= marginLen) return -10;`. Deliberate debug
  instrumentation, to be reverted separately. Note that this design makes it *more* visible: with
  one clean distance per channel it becomes a crisp flat-bottomed trench at fixed depth rather than
  a noisy one.
- **Reconstructing the true quintic.** Would require persisting velocity and acceleration per
  primitive. Revisit only if polyline chord error proves visible at the knot spacing resampling
  produces.

## Verification

- `gradle spotlessApply` before commit.
- The JUnit suite does not currently compile (`SpatialIndexCorrectnessGoldenTest.java:51` calls the
  deleted `FractalTerrainConfig.maxNativeWidth()`). That is pre-existing and unrelated; a red run is
  not evidence about this change until it is fixed.
- Primary check is visual: `gradle localRiverTest` PNG dumps, then `gradle runClient`. The specific
  thing to look for is that bed width no longer breathes between knots along a bend, and that the
  zone boundary between bed and floodplain is a single clean contour rather than a fringe.
