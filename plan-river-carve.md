# Implementation Plan — River "Carve First, Detail Later" Redesign

> Status: ready to implement. Sequential milestones M-001 → M-006.
> Per direction, test re-baselining (M-006) is deferred / optional — the plan is design-complete without it.

## Overview

**Problem.** Rivers are currently shaped almost entirely per-pixel in `PopulateNoiseStep`
(`RosgenProfile` three-zone), with only global rivers pre-carved into ch0 (`carveGlobalRivers`,
nearest-sample lerp to bed). Valleys are discovered too late (after `fillSinks`/drainage), local rivers
get no valley/floodplain into the cached elevation, and confluences risk double-deepening.

**Approach — "carve first, detail later".** The tile stage carves a valley/floodplain **shell** for
**both** global and local rivers; `PopulateNoiseStep` carves only the bed trench + water; a no-blend
**lens** forces the carve delta to 0 at the shared outer radius.

- One `static void carveRiverShells(float[] elevation, HydrologicalUnit[] units, int paddedSize)` reusing
  today's `carveGlobalRivers` kernel (already an absolute-target lerp) + `min()` composite
  (order-independent → no confluence double-deepen) + swap target bed→**floor** (bank + freeboard).
- **RADII = Extend-shell** (user decision): keep the outer radius at `riverInfluence` (2.2·fpl). The lens
  forces mask=1 (flat floor) only out to `floodPlainLength`, then falls off to 0 at `riverInfluence`, so
  the tile carve and the per-pixel detail blend agree at the wider radius.
- `buildTile` reorders to: global net build → assemble global units → `carveRiverShells(global)` → fill →
  drainage → local trace → sample local reference from the globally-carved buffer (forced monotone
  descending) → `carveRiverShells(local)` → 2nd fill → assemble combined unit index.
- `RosgenProfile` redefined so **reference = bank**, **bed = reference − depth**, with a shell(floor) vs
  bed(residual) split and a lens mask.
- `PopulateNoiseStep` carves the shell→bed **residual** only and owns water; `RIVER_DIFFERENCE` is
  recomputed against the **shell**, not `preCarve`.

## Key Decisions

| ID | Decision | Rationale (short) |
|----|----------|-------------------|
| **DL-001** | One static `carveRiverShells(elevation, units, paddedSize)` for BOTH global & local; call sites differ only in arguments. | Two kernels would drift and double-code lens/min logic; reuse the existing absolute-target lerp, composing every unit disc via `min()` (associative), so global-then-local = one combined min. Ordering matters only because the local pass **samples** the already-globally-carved buffer. |
| **DL-002** | Shell floor target is a **third reference = bank + small freeboard**, distinct from bed (canyon) and drainage surface. | Bed-as-floor → edge-cliff; post-drainage surface → loses gradient. Define `RosgenProfile` reference=bank, bed=reference−depth; carve the shell to bank+freeboard so the per-pixel bed trench is the only deep cut. (P0 gate.) |
| **DL-003** | Lens no-blend region = intersection of two circles radius `r = fpl²/(2d) + d/2`, each center offset `±(r−d)` from the unit along the tangent (separation `2(r−d)`). **USER-CONFIRMED.** | Chord/sagitta construction (chord half = fpl, sagitta = d) → lens `±fpl` cross-channel, `±d` along-channel, tips touch fpl exactly; mask=1 inside by construction. `d` must satisfy `width/2 < d < fpl`. |
| **DL-004** | **RADII = Extend-shell (USER-CONFIRMED):** shell outer radius = `riverInfluence` (2.2·fpl); flat floor to `floodPlainLength`, then blend `fpl → riverInfluence`, mask reaching 0 at `riverInfluence`. **Supersedes** the literal intake MUST "delta = 0 at floodPlainLength." | The per-pixel detail blend already extends to `riverInfluence`; stopping the shell at fpl leaves a dry double-moat. Extend the shell blend + lens outer radius to `riverInfluence` so both stages reach 0 together. Accept wider valleys, larger halo, more carve work. |
| **DL-005** | Local reference elevation = sampled from the globally-carved tile then forced monotone descending downstream by a cheap 1-D pass (NOT a full `ChannelElevationAssigner`). | A shell needs only a plausible floodplain reference, not a full bed solve. Sample the already-globally-carved buffer per local unit and clamp monotone descending so confluences inherit a consistent floor. |
| **DL-006** | `buildTile` reorders: global-build → global units → `carveRiverShells(global)` → `fillSinks` → drainage → local trace → sample+monotone reference → `carveRiverShells(local, halo)` → 2nd `fillSinks` → combined unit index. | Local reference must sample the global shell; a local carve after the first fill can reopen basins → 2nd fill; local nets have no coarse halo → baking into PAD=1 seams at borders → local carve needs a halo/PAD sized to `riverInfluence`. |
| **DL-007** | Use real rescaled native global widths everywhere incl. the Meanders relax step; cap the Meanders border-damping margin; fix `maxNativeWidth` to include the ×20 rescale. | `getWidth` already returns native widths, but `maxNativeWidth` omits ×20 → visit/query radius under-covers wide trunks and margin `5·width` reaches ~1600 ≫ 514 → hard-zeros the grid. Feed rescaled widths consistently and clamp the margin. |
| **DL-008** | Cross-stage conservation: shell carves floor = bank+freeboard; detail carves only the **residual** bed = shell−depth; `RIVER_DIFFERENCE` recomputed against the shell. | `tileShellDelta + detailBedDelta` must equal the intended trench; if detail re-anchors on original terrain it compounds and `RIVER_DIFFERENCE` collapses across the floodplain. |
| **DL-009** | `d` scales with width within the band `width/2 < d < fpl`; a single global `d` constant is invalid across the width range. | Lens requires `d > width/2` (centers clear the channel) and `d < fpl` (sagitta below chord half). Derive `d` from width via a law that stays in-band for the full `MAX_WIDTH·20` range. |
| **DL-010** | Bump the `local_carved_elev` cache store version (new-worlds-only) and re-baseline affected hydrology goldens. | Carved shape changes for every tile → old cached tiles would seam against new ones. Rename the store literal so the frontier regenerates. |

### Rejected alternatives
- **Relative-subtract composition** — double-deepens at global/local confluences on the shared buffer. (→ DL-001)
- **Iterate raw `Channel` densified `RiverSample`s** — keeps two representations, loses the unit normal the lens needs; the unit stream already merges global+local. (→ DL-001)
- **Full `ChannelElevationAssigner` pass for local beds** — over-engineered; a shell needs only a sampled reference + cheap monotone pass. (→ DL-005)
- **Keep local shaping cross-tile (bake only global)** — dissolves seams but violates the explicit "carve locals into the tile" requirement; instead bake **with a halo**. (→ DL-006)

## Current-code constraints (facts the plan builds on)

- `carveGlobalRivers` (`HydrologyProfileCarver:178-238`): densify channels → `RiverSample`s, quadtree,
  nearest sample, absolute-target lerp `elevation[idx]=bed+(orig-bed)*frac` (`:232-235`); `MAX_CARVE_DELTA`
  skip; `riverInfluence` outer radius, `bedHalfWidth` inner.
- `buildTile` order today (`LocalRiverProvider:160-212`): decode → `GlobalNetworkBuilder.build` →
  `carveGlobalRivers` → `fillSinks` → `cropToTile` → drainage → `rasterizeGlobalMask` →
  `traceLocalNetwork` → `collectUnits(global)+addLocalChannelUnits` → `ImmutableRTree`.
- `RosgenProfile:52-160`: `A.bedElevation` returns `-2` placeholder, floodplain `0`, blend lerp; `A`
  `floodPlainLength=1+1.2*width`, base law `FLOODPLAIN_BASE+FLOODPLAIN_WIDTH_FACTOR*width`;
  `riverInfluence=min(MAX_INFLUENCE_RADIUS, fpl*INFLUENCE_BLEND_MULTIPLIER=2.2)`.
- `HydrologyProfile.computeForUnit:37-55`: reference `= min(unit.elevation, decodedElevAtUnit)`; returns
  `reference + RosgenProfile.elevationDelta(perp, radial, width, decodedAtPixel-reference)`.
- `PopulateNoiseStep.updateToFinalElev:43-72`: prefetch chunk units, `carvePrefetched` per block,
  `riverDifference[pos]=refined-preCarve`, `elev=max(bottom,refined)+seaLevel-1`.
- `HydrologyProfilePainter.riverWaterTop:31` keys water on `RIVER_DIFFERENCE<0`; `insideChannel:48` uses
  `maxNativeWidth/2` visit radius + `channelContains` (width/2).
- `HydrologicalUnit.getRadius:82 = riverInfluence(width)` is the R-tree membership circle; carries width,
  normal (nullable), elevation, id.
- `HydrologyTuning`: `FLOODPLAIN_BASE=0.6`, `FLOODPLAIN_WIDTH_FACTOR=1.0`, `INFLUENCE_BLEND_MULTIPLIER=2.2`,
  `MAX_INFLUENCE_RADIUS=64`, `MAX_CARVE_DELTA=100`, `MAX_WIDTH=16`, `GLOBAL_WIDTH_COORD_SCALE=20`
  (`maxNativeWidth` returns `MAX_WIDTH` only — **does not include the ×20 rescale**), `MARGIN_INFLUENCE_FACTOR=5`.
- `GlobalRiverProvider.getWidth` returns `widthFromFlow*GLOBAL_WIDTH_COORD_SCALE`, i.e. already native;
  `GlobalNetworkBuilder` feeds it to `EdgeSpec` width and the Meanders relax already sees rescaled widths.
- `Meanders.borderDamping` margin `= MARGIN_INFLUENCE_FACTOR(5)*width`; with native widths up to ~320
  margin = 1600 ≫ 514 → hard-zeros the grid → wide-trunk relax blowup.
- `collectUnits`/`addLocalChannelUnits` resample at `dx = narrowest width/2` so per-unit discs overlap
  gap-free (the floodplain radial-disc union relies on this).
- Cache store literals in `LocalRiverProvider` constructor: `local_river_units`, `local_carved_elev`.

## Risks & mitigations

| ID | Risk | Mitigation | Ref |
|----|------|-----------|-----|
| R-001 | Local floodplains baked into PAD=1 tile → tile-border seams. | Size the local-carve halo/PAD to cover `riverInfluence` (2.2·fpl), not just fpl. | DL-006 |
| R-002 | (RESOLVED by DL-004) shell/detail radius mismatch → dry double-moat. | Extend shell outer radius to `riverInfluence`; both reach 0 together. Cost: wider valleys, larger halo, more carve work. | DL-004 |
| R-003 | Local carve after `fillSinks` reopens basins. | 2nd `fillSinks` after the local carve. | DL-006 |
| R-004 | `RIVER_DIFFERENCE` collapses across floodplain once shell is in preCarve. | Recompute `RIVER_DIFFERENCE` as trench-vs-shell in `PopulateNoiseStep`. | DL-008 |
| R-005 | `tileShellDelta + detailBedDelta` must == intended trench. | Anchor detail bed on the carved shell and cut only `bed=shell-depth`. | DL-008 |
| R-006 | `MAX_CARVE_DELTA` now measured against the mutated buffer on the local pass → shifts uncarvable set. | Accept as intended; evaluate against the ambient (current) buffer value. | DL-006 |
| R-007 | `maxNativeWidth()` omits ×20 → visit/query radius under-covers wide channels. | Fix `maxNativeWidth` to include `GLOBAL_WIDTH_COORD_SCALE`. | DL-007 |
| R-008 | Wide-trunk width blows up Meanders margin (5·width) on 512 tiles. | Clamp the border-damping margin to a fraction of the grid. | DL-007 |
| R-009 | Cache-version bump is a world-frontier step. | New-worlds-only stance; bump the store name so the frontier regenerates. | DL-010 |

### Invariants (do not break)
- `buildTile` stays pure/static/tile-local; no shared mutable static state.
- `FloatTensor` frozen once cached — never mutate a Storage tensor; work on clones.
- `min`-composite is order-independent; correctness of global-then-local depends only on the local
  reference sampling the already-globally-carved buffer.
- Lens mask = 1 (flat floor) up to `floodPlainLength` by construction; outside the lens the falloff reaches
  0 at `riverInfluence` (multiply-by-falloff, not by hope).
- Unit disc spacing `dx ≤ narrowest width/2` must be preserved for a gap-free floodplain union.

---

## Milestones (sequential: W-001 → W-006)

Each file is assigned to exactly one milestone; the shared carve buffer + global-then-local ordering make
this an inherently pipelined refactor.

### M-001 — Width-correctness foundation

**Files:** `config/HydrologyTuning.java`, `hydrology/GlobalNetworkBuilder.java`,
`hydrology/meanders/Meanders.java`, `hydrology/meanders/RiverNetwork.java`

**Goal:** `maxNativeWidth` includes the ×20 rescale; Meanders relax + border-damping consume rescaled
native widths; the border-damping margin is clamped so wide trunks don't zero the 514 grid; `HydrologyTuning`
exposes `FREEBOARD` + a `d(width)` law (`width/2 < d < fpl`).

**Acceptance:** `maxNativeWidth` ≥ widest native channel used by `insideChannel`/query; `d(width)` stays
strictly inside `(width/2, fpl)` across `[narrowest, MAX_WIDTH·20]`; the widest trunk relaxes without a
hard-zeroed grid.

`HydrologyTuning.maxNativeWidth` (CI-M-001-001):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java
+++ b/src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java
@@
     public static double maxNativeWidth() {
-        return MAX_WIDTH;
+        return MAX_WIDTH * GLOBAL_WIDTH_COORD_SCALE;
     }
 }
```

`HydrologyTuning` — freeboard + `d(width)` law (CI-M-001-002):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java
+++ b/src/main/java/me/batata_1/fractal_terrain/config/HydrologyTuning.java
@@
     public static final double MAX_CARVE_DELTA = 100;

+    /**
+     * Depth (native px) the tile-carved shell floor sits below a feature's reference (bank) elevation --
+     * shallow, distinct from the much deeper per-pixel bed trench ({@link
+     * me.batata_1.fractal_terrain.hydrology.ChannelGeometry#depthForWidth}).
+     */
+    public static final double FREEBOARD = 0.3f;
+
+    /**
+     * Fraction of the way from {@code width/2} to a river's {@code floodPlainLength} that the lens
+     * sagitta {@link #d} sits -- keeps {@code d} strictly inside the required band {@code (width/2, fpl)}
+     * for every representable width, since {@code floodPlainLength(width) > width/2} always.
+     */
+    private static final double D_FRACTION = 0.5;
+
+    /**
+     * The lens sagitta (native px) for the shell-carve mask: the along-channel half-extent of a single
+     * unit's flat-floor footprint. Stays strictly inside the validity band {@code width/2 < d <
+     * floodPlainLength} across the whole width range (narrowest local channel to the widest
+     * native-rescaled global trunk) by construction -- see {@link #D_FRACTION}.
+     */
+    public static double d(double width, double floodPlainLength) {
+        final double halfWidth = width * 0.5;
+        return halfWidth + D_FRACTION * (floodPlainLength - halfWidth);
+    }
+
     /**
      * Floodplain half-extent for a river of the given width and Rosgen type (native px). Delegates to the
      * type's {@link RosgenProfile#floodPlainLength} -- the profile enum is the authority so extents can vary
```

`Meanders.borderDamping` — clamp the margin (CI-M-001-004):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Meanders.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/Meanders.java
@@
+    /**
+     * Cap on {@link #borderDamping}'s margin, as a fraction of the grid side -- so a wide native-rescaled
+     * global trunk's {@code MARGIN_INFLUENCE_FACTOR * width} margin cannot exceed the grid and hard-zero
+     * the whole damping field, leaving no interior for the channel to relax into.
+     */
+    private static final double MAX_MARGIN_FRACTION = 0.4;
+
     private double borderDamping(double x, double z, double width) {
-        final double margin = HydrologyTuning.MARGIN_INFLUENCE_FACTOR * width;
+        final double margin =
+                Math.min(HydrologyTuning.MARGIN_INFLUENCE_FACTOR * width, MAX_MARGIN_FRACTION * gridSize);
         if (margin <= 0) return 1.0;
         final double distToBorder = Math.min(Math.min(x, z), Math.min(gridSize - 1 - x, gridSize - 1 - z));
         // Inner padding [0, margin]: hard-zero. Beyond it, ramp 0->1 over the next margin.
         return Math.clamp((distToBorder - margin) / margin, 0.0, 1.0);
     }
```

`GlobalNetworkBuilder.build` — doc: widths already native-rescaled (CI-M-001-003):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/GlobalNetworkBuilder.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/GlobalNetworkBuilder.java
@@
  * passes rely on {@code centerIdx}/{@code edgeNodeIdx} populated by earlier ones.
+ *
+ * <p>{@link GlobalRiverProvider#getWidth} already returns native-rescaled widths (coarse-px flow width x
+ * {@code GLOBAL_WIDTH_COORD_SCALE}); every {@code EdgeSpec}/margin/seed computation below consumes that
+ * value directly, so the {@link Meanders} relax step and the border-confinement margin both operate in
+ * the same native-px frame as the local network -- do not re-scale it again here.
  */
 final class GlobalNetworkBuilder {
```

`RiverNetwork.addFeatureUnits` — doc: no re-scale (CI-M-001-005):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/meanders/RiverNetwork.java
@@
+    /**
+     * {@code width} here is already the native-rescaled global width fed into the {@code EdgeSpec} at
+     * network-construction time ({@link GlobalNetworkBuilder} multiplies coarse-px flow width by {@code
+     * GLOBAL_WIDTH_COORD_SCALE} before building edges) -- this method never re-scales it, so the emitted
+     * units and the shell-carve query radius both stay in the same native-px frame as local channels.
+     */
     private static void addFeatureUnits(
             List<HydrologicalUnit> out,
             QuinticHermiteSpline spline,
```

---

### M-002 — Lens + Rosgen profile geometry

**File:** `hydrology/profile/RosgenProfile.java`

**Goal:** reference = bank; bed = reference − depth; shell floor target = bank + freeboard; a two-circle
lens mask (`r=fpl²/(2d)+d/2`, centers `±(r−d)` on the tangent) = 1 inside, falling off from fpl to 0 at
`riverInfluence`; expose a `shellDelta(floor)` and a `bedResidualDelta` separately.

**Acceptance:** mask = 1 for all points inside the lens and exactly 0 at/beyond `riverInfluence`; shell
floor delta reaches 0 at `riverInfluence` by construction; bed residual delta = shell − depth only (does
not re-cut from original terrain); `shellDelta + bedResidualDelta` sums to the intended trench.

Full `RosgenProfile` rework (CC-M-002-001) — removes the old `elevationDelta`/`bedElevation`/
`floodplainElevation`/`blend`, adds `lensMask`, `shellFloor`, `shellDelta`, `bedResidualDelta`:
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/profile/RosgenProfile.java
@@
 package me.batata_1.fractal_terrain.hydrology.profile;

 import me.batata_1.fractal_terrain.FractalTerrainConfig;
+import me.batata_1.fractal_terrain.config.HydrologyTuning;
 import me.batata_1.fractal_terrain.hydrology.ChannelGeometry;
 import me.batata_1.fractal_terrain.hydrology.HydrologicalUnit.RosgenType;
-import org.slf4j.Logger;
-import org.slf4j.LoggerFactory;

 /**
- * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type. Given the
- * perpendicular (cross-channel) distance from a query point to the channel centreline ({@code perpDist})
- * and the radial (Euclidean) distance to the unit ({@code radialDist}), it returns an elevation <em>delta
- * relative to the feature's reference elevation</em> (the carver adds this to
- * {@code min(unit.elevation, decodedElevation)}).
+ * The cross-channel elevation profile of a hydrological feature, keyed by Rosgen stream type.
+ *
+ * <p>Two independent deltas are carved by two different stages, and their sum is the intended trench
+ * (cross-stage conservation):
+ *
+ * <ul>
+ *   <li>{@link #shellDelta} -- the tile-level valley/floodplain SHELL (a flat floor at
+ *       {@link #shellFloor}, {@link HydrologyTuning#FREEBOARD} below the feature's reference/bank
+ *       elevation), lens-masked so it reaches 0 (no change) at {@link #riverInfluence}. Carved once per
+ *       tile by {@code HydrologyProfileCarver#carveRiverShells}.</li>
+ *   <li>{@link #bedResidualDelta} -- the per-pixel bed TRENCH cut below the already-carved shell, within
+ *       the bed half-width only. Carved per block by {@code PopulateNoiseStep}.</li>
+ * </ul>
  *
  * <p>The profile is also the authority for the two horizontal extents of the cross-section --
  * {@link #floodPlainLength} and {@link #riverInfluence} ...
  *
- * <p>Symmetric cross-section, centre -> far:  (three-zone bed|floodplain|blend doc removed)
+ * <p>The lens mask ({@link #lensMask}) is the flat-floor footprint of a single unit: the intersection of
+ * two circles of radius {@code r = fpl^2 / (2d) + d/2} (chord half-length {@code fpl}, sagitta {@code d}),
+ * centred {@code +/-(r - d)} from the unit along its tangent. Inside the lens the mask is exactly {@code 1}
+ * (out to {@code +/-fpl} cross-channel, {@code +/-d} along-channel); outside it the mask falls off and
+ * reaches exactly {@code 0} at {@link #riverInfluence}. Consecutive units overlap along the channel (unit
+ * spacing {@code dx <= width/2}) into a continuous flat floodplain corridor.
  */
 public enum RosgenProfile {
     A {
-        @Override
-        public double bedElevation(double projectedDist, double width, double floodPlainLength) { return -2.0; }
-        @Override
-        public double floodplainElevation(double projectedDist, double width, double floodPlainLength) { return 0.0; }
         @Override
         public double floodPlainLength(double width) {
             return 1 + 1.2 * width;
         }
     },
     B, C, D;

-    // ---- Cross-section elevation deltas (shared placeholders) ----
-    private static final Logger LOG = LoggerFactory.getLogger(RosgenProfile.class);
-    public double bedElevation(...) { return -1.0; }
-    public double floodplainElevation(...) { return 0.0; }
-    public double blend(double floodplainDelta, double normalElevDelta, double t) { ... }

     // ---- Horizontal extents (unchanged floodPlainLength / riverInfluence laws) ----
     public double floodPlainLength(double width) {
         return FractalTerrainConfig.FLOODPLAIN_BASE + FractalTerrainConfig.FLOODPLAIN_WIDTH_FACTOR * width;
     }
     public double riverInfluence(double width) {
         return Math.min(FractalTerrainConfig.MAX_INFLUENCE_RADIUS,
                 floodPlainLength(width) * FractalTerrainConfig.INFLUENCE_BLEND_MULTIPLIER);
     }

-    public double elevationDelta(double perpDist, double radialDist, double width, double normalElevDelta) { ... }
+    // ---- Lens mask (flat-floor footprint of a single unit) ----
+    public double lensMask(double perpDist, double alongDist, double width, double floodPlainLength) {
+        final double radialDist = Math.hypot(perpDist, alongDist);
+        final double maxInfluence = riverInfluence(width);
+        if (radialDist >= maxInfluence) return 0.0;
+
+        final double d = HydrologyTuning.d(width, floodPlainLength);
+        final double r = (floodPlainLength * floodPlainLength) / (2.0 * d) + d / 2.0;
+        final double offset = r - d;
+        final double distToNearCenter = Math.hypot(alongDist - offset, perpDist);
+        final double distToFarCenter = Math.hypot(alongDist + offset, perpDist);
+        final double excess = Math.max(distToNearCenter, distToFarCenter) - r;
+        if (excess <= 0.0) return 1.0; // inside the two-circle intersection: flat floor
+
+        final double falloffSpan = Math.max(1e-9, maxInfluence - floodPlainLength);
+        return Math.max(0.0, 1.0 - excess / falloffSpan);
+    }
+
+    // ---- Shell (tile-level, lens-masked flat floor) ----
+    public double shellFloor(double referenceElev) {
+        return referenceElev - HydrologyTuning.FREEBOARD;
+    }
+    public double shellDelta(double perpDist, double alongDist, double width, double floodPlainLength,
+                             double ambientElev, double referenceElev) {
+        final double mask = lensMask(perpDist, alongDist, width, floodPlainLength);
+        return (shellFloor(referenceElev) - ambientElev) * mask;
+    }
+
+    // ---- Bed (per-pixel residual trench, cut below the already-carved shell) ----
+    public double bedResidualDelta(double perpDist, double width) {
+        if (perpDist > ChannelGeometry.bedHalfWidth(width)) return 0.0;
+        return -ChannelGeometry.depthForWidth(width);
+    }

     public static RosgenProfile of(RosgenType type) { ... }
```
> Note: `lensMask` uses `Math.max(distToNearCenter, distToFarCenter) - r` — a point is inside the
> intersection of the two discs iff it is inside **both**, i.e. its distance to the **farther** center is
> ≤ r. This yields the almond lens spanning `±fpl` cross-channel (`alongDist=0`) and `±d` along-channel.
> Inline-comment changes (CC-M-002-002/003) annotate the `d` band and the conservation guarantee.

---

### M-003 — Shell carve kernel

**File:** `hydrology/profile/HydrologyProfileCarver.java` (+ `HydrologicalUnit` gains `getCoords`)

**Goal:** generalize `carveGlobalRivers` → `static carveRiverShells(elevation, units, paddedSize)`; compose
each unit disc via `min()` of an absolute floor target (never relative subtract); apply the lens mask so
the carve delta is 0 at `riverInfluence`; `MAX_CARVE_DELTA` evaluated against the (possibly already-mutated)
buffer.

**Acceptance:** one method carves both global and local unit arrays; global-then-local = a single combined
min over all units; no confluence double-deepening on a shared buffer; delta == 0 outside `riverInfluence`.

`carveRiverShells` (CC-M-003-001) — replaces the `RiverSample`/`Channel`-based kernel:
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileCarver.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/profile/HydrologyProfileCarver.java
@@ (imports: drop Channel/VectorOps/SpatialIndexPoint/QuinticHermiteSpline/ChannelGeometry; add Arrays + RosgenType)
-    public static void carveGlobalRivers(float[] elevation, List<Channel> channels, int paddedSize) {
-        if (channels.isEmpty()) return;
-        final List<RiverSample> samples = new ArrayList<>();
-        for (Channel ch : channels) { ...densify to RiverSample every 2px... }
-        final ImmutableQuadTree<RiverSample> index = new ImmutableQuadTree<>(..., samples);
-        for (int pi ...) for (int pj ...) {
-            ...nearest sample; frac = 0 inside bedHalfWidth, linear to riverInfluence...
-            elevation[idx] = (float) (bedElev + (orig - bedElev) * frac);
-        }
-    }
-    private static double[] pointWidths(Channel ch) { ... }
+    public static void carveRiverShells(float[] elevation, HydrologicalUnit[] units, int paddedSize) {
+        if (units.length == 0) return;
+        final ImmutableQuadTree<HydrologicalUnit> index = new ImmutableQuadTree<>(
+                new double[] {-CARVE_INDEX_SLACK, -CARVE_INDEX_SLACK},
+                new double[] {paddedSize + CARVE_INDEX_SLACK, paddedSize + CARVE_INDEX_SLACK},
+                Arrays.asList(units));
+
+        for (int pi = 0; pi < paddedSize; pi++) {
+            for (int pj = 0; pj < paddedSize; pj++) {
+                final int idx = pi * paddedSize + pj;
+                final float ambient = elevation[idx];
+                if (ambient < 0) continue;
+                final double[] pixel = {pi, pj};
+                final List<HydrologicalUnit> nearby =
+                        index.getPointsInCircle(pixel, FractalTerrainConfig.MAX_INFLUENCE_RADIUS);
+                if (nearby.isEmpty()) continue;
+
+                double target = ambient;
+                for (HydrologicalUnit unit : nearby) {
+                    final double[] coord = unit.coord();
+                    final double dx = pixel[0] - coord[0];
+                    final double dz = pixel[1] - coord[1];
+                    final double radialDist = Math.hypot(dx, dz);
+                    if (radialDist >= unit.getRadius()) continue; // outside this unit's influence
+
+                    final double perpDist;
+                    final double alongDist;
+                    if (unit.normal() != null) {
+                        final double[] n = unit.normal();
+                        perpDist = Math.abs(dx * n[0] + dz * n[1]);
+                        alongDist = Math.sqrt(Math.max(0.0, radialDist * radialDist - perpDist * perpDist));
+                    } else {
+                        perpDist = radialDist;
+                        alongDist = 0.0;
+                    }
+
+                    final RosgenType type = unit.rosgenType() == null ? RosgenType.A : unit.rosgenType();
+                    final RosgenProfile profile = RosgenProfile.of(type);
+                    final double floodPlainLength = profile.floodPlainLength(unit.width());
+                    final double delta = profile.shellDelta(
+                            perpDist, alongDist, unit.width(), floodPlainLength, ambient, unit.elevation());
+                    target = Math.min(target, ambient + delta);
+                }
+
+                if (!withinCarveDelta(ambient, target)) continue; // uncarvable
+                elevation[idx] = (float) target;
+            }
+        }
+    }
```

`withinCarveDelta` guard (CC-M-003-002):
```diff
+    private static boolean withinCarveDelta(float ambient, double target) {
+        return Math.abs(ambient - target) <= FractalTerrainConfig.MAX_CARVE_DELTA;
+    }
```

`HydrologicalUnit` implements `SpatialIndexPoint` for direct quadtree indexing (CC-M-003-003):
```diff
-        implements SpatialIndexCircle, Persistable<HydrologicalUnit> {
+        implements SpatialIndexPoint, SpatialIndexCircle, Persistable<HydrologicalUnit> {
+    @Override
+    public double[] getCoords() { return coord; }  // same backing array as getCenter(); no wrapper alloc
```

---

### M-004 — `buildTile` reorder + local reference + cache bump

**Files:** `hydrology/LocalRiverProvider.java`, `hydrology/LocalDrainageTracer.java`,
`hydrology/HydrologicalUnit.java`

**Goal:** reorder `buildTile` to carve global then local shells on the shared buffer; local reference
sampled from the globally-carved buffer, forced monotone descending; local carve reads the globally-carved
buffer (halo caveat below); rename the `local_carved_elev` cache literal.

**Acceptance:** `buildTile` stays pure/static/tile-local; local units read a buffer that already contains
global valleys; local reference is monotone non-increasing downstream; new cache store name forces frontier
regeneration.

`buildTile` reorder + `shiftUnits` helper (CC-M-004-001):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/LocalRiverProvider.java
@@
         final Meanders sim = GlobalNetworkBuilder.build(tileX, tileZ, base, grp);

-        // 2. carve the decoded elevation toward the global river beds.
+        // 2. assemble global units (padded frame, offset 0) so they line up with `carvedElevation`.
+        final RiverNetwork.ElevationSampler decodedSampler = (x, z) -> sampleBilinear(base[0], x, z);
+        final int[] nextFeatureId = {0};
+        final List<HydrologicalUnit> globalUnitsPadded =
+                sim.getNetwork().collectUnits(0, decodedSampler, 0, 0, nextFeatureId);
+
+        // 3. carve the global valley/floodplain shell into the decoded elevation.
         final float[] carvedElevation = base[0].clone();
-        HydrologyProfileCarver.carveGlobalRivers(carvedElevation, sim.getChannels(), PADDED);
+        HydrologyProfileCarver.carveRiverShells(
+                carvedElevation, globalUnitsPadded.toArray(new HydrologicalUnit[0]), PADDED);

-        // 3. fill sinks, then crop to the inner 512 carved tile.
+        // 4. fill sinks, then drainage on the filled elevation; crop to the inner 512 grid.
         final float[] filled = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, HydrologyTuning.FILL_PADDING);
-        final FloatTensor carvedTile = cropToTile(filled);
-        // 4. drainage ...
         final int[] drainagePadded = PipelinePreprocessing.computeDrainageDirection(filled, uniformWeight, PADDED);
         ... (drainage/elev crop unchanged) ...

         final boolean[] globalMask = LocalDrainageTracer.rasterizeGlobalMask(sim.getChannels());
         final List<Channel> localChannels = LocalDrainageTracer.traceLocalNetwork(drainage, elev, globalMask, stages);

-        // 7. assemble the unit index (global + local) ...
-        final int[] nextFeatureId = {0};
-        final List<HydrologicalUnit> unitPoints = sim.getNetwork().collectUnits(0, decodedSampler, PAD, PAD, nextFeatureId);
-        for (Channel ch : localChannels) LocalDrainageTracer.addLocalChannelUnits(unitPoints, ch, elev, nextFeatureId);
-        final ImmutableRTree<HydrologicalUnit> unitIndex = new ImmutableRTree<>(unitPoints, HydrologicalUnit.PROTOTYPE);
+        // 6. assemble local units (tile-local GRID frame): reference sampled from the already-globally-
+        //    carved buffer (`elev`) and forced monotone non-increasing downstream (see M-004 tracer diff).
+        final List<HydrologicalUnit> localUnits = new ArrayList<>();
+        for (Channel ch : localChannels) LocalDrainageTracer.addLocalChannelUnits(localUnits, ch, elev, nextFeatureId);
+
+        // 7. carve the local shell on top of the globally-carved padded buffer, then re-fill sinks.
+        //    NOTE: local networks are traced with no coarse halo, so a local shell can be truncated at
+        //    this tile's PAD=1 border -- a known, currently-accepted seam risk for local floodplains that
+        //    straddle a tile edge (global floodplains use the 2x2-cell halo and are unaffected).
+        final List<HydrologicalUnit> localUnitsPadded = shiftUnits(localUnits, PAD, PAD);
+        HydrologyProfileCarver.carveRiverShells(
+                carvedElevation, localUnitsPadded.toArray(new HydrologicalUnit[0]), PADDED);
+        final float[] refilled = PipelinePreprocessing.fillSinks(carvedElevation, PADDED, HydrologyTuning.FILL_PADDING);
+        final FloatTensor carvedTile = cropToTile(refilled);
+
+        // 8. combined unit index: global units (shifted to tile-local) + local units.
+        final List<HydrologicalUnit> unitPoints = new ArrayList<>(shiftUnits(globalUnitsPadded, -PAD, -PAD));
+        unitPoints.addAll(localUnits);
+        final ImmutableRTree<HydrologicalUnit> unitIndex = new ImmutableRTree<>(unitPoints, HydrologicalUnit.PROTOTYPE);
         ... (stages + return unchanged) ...

+    /** Re-stamps every unit's coordinate by (dx, dz) -- moves a unit list between the padded (native,
+     *  PAD-inclusive) frame and the tile-local (cropped GRID) frame. */
+    private static List<HydrologicalUnit> shiftUnits(List<HydrologicalUnit> units, double dx, double dz) {
+        final List<HydrologicalUnit> shifted = new ArrayList<>(units.size());
+        for (HydrologicalUnit u : units)
+            shifted.add(new HydrologicalUnit(u.type(), u.rosgenType(),
+                    new double[] {u.coord()[0] + dx, u.coord()[1] + dz},
+                    u.normal(), u.width(), u.elevation(), u.time(), u.id()));
+        return shifted;
+    }
```
> Halo caveat (R-001): the diff carves the local shell over the same PADDED (PAD=1) buffer, so a local
> floodplain straddling a tile edge is truncated at the seam. The plan flags this as accepted for a first
> cut; the full mitigation is to decode/trace/carve the local pass over a wider halo sized to
> `riverInfluence`. Treat widening the local-carve halo as a follow-up within this milestone if seams show.

Cache-store version bump (CC-M-004-002):
```diff
-                path, "local_carved_elev", new int[] {1, GRID, GRID}, this::buildCarvedTile);
+                path, "local_carved_elev_v2", new int[] {1, GRID, GRID}, this::buildCarvedTile);
```

Local reference: sample carved buffer + monotone-descending (CC-M-004-003):
```diff
--- a/src/main/java/me/batata_1/fractal_terrain/hydrology/LocalDrainageTracer.java
+++ b/src/main/java/me/batata_1/fractal_terrain/hydrology/LocalDrainageTracer.java
@@
         final List<double[]> pts = resampled.points();
         final int n = pts.size();
+        // Sample each point's shell reference off the already-globally-carved buffer, then force it
+        // monotone non-increasing downstream (index 0..n-1 is upstream..downstream) so a local channel
+        // never floats above its own upstream point.
+        final double[] reference = new double[n];
+        for (int i = 0; i < n; i++) {
+            final double[] p = pts.get(i);
+            final double sampled = sampleLocal(elev, p[0], p[1]);
+            reference[i] = (i == 0) ? sampled : Math.min(reference[i - 1], sampled);
+        }
         final int featureId = nextFeatureId[0]++;
         for (int i = 0; i < n; i++) {
             ...
-            final double bed = sampleLocal(elev, p[0], p[1]);
             out.add(new HydrologicalUnit(HydrologicalFeature.RIVER, HydrologicalUnit.RosgenType.A,
                     new double[] {p[0], p[1]}, new double[] {nrm[0], nrm[1]}, w,
-                    bed, 0, featureId));
+                    reference[i], 0, featureId));
         }
```

`HydrologicalUnit` doc: `elevation` is now the reference (bank) level (CC-M-004-004) — javadoc-only.

---

### M-005 — Detail stage: per-pixel bed residual + water

**Files:** `hydrology/profile/HydrologyProfile.java`, `hydrology/profile/HydrologyProfileCarver.java`
(per-pixel side), `hydrology/profile/HydrologyProfilePainter.java`,
`world/gen/populatenoise/PopulateNoiseStep.java`

**Goal:** `computeForUnit` anchors on the carved shell and cuts only `bed = shell − depth` residual;
`PopulateNoiseStep` owns the bed trench + water and recomputes `RIVER_DIFFERENCE` as trench-vs-shell;
painter tracks the redefinition (water top on the recomputed `RIVER_DIFFERENCE`, `insideChannel` uses the
corrected `maxNativeWidth`).

**Acceptance:** `tileShellDelta + detailBedDelta` == intended trench; `RIVER_DIFFERENCE` stays negative
only within the bed trench (not across the whole floodplain); water surface + channel paint match the
redefined bed for both global and local rivers.

`HydrologyProfile.computeForUnit` — residual bed on the shell (CC-M-005-001):
```diff
-    public static double computeForUnit(double pixelX, double pixelZ, HydrologicalUnit unit,
-            double decodedElevAtPixel, double referenceElevAtUnit) {
+    public static double computeForUnit(double pixelX, double pixelZ, HydrologicalUnit unit,
+            double shellElevAtPixel) {
         final double[] unitCoord = unit.coord();
         final double dx = pixelX - unitCoord[0];
         final double dz = pixelZ - unitCoord[1];
-        final double radialDist = Math.hypot(dx, dz);
         final double perpDist;
         if (unit.normal() != null) {
             final double[] n = unit.normal();
             perpDist = Math.abs(dx * n[0] + dz * n[1]);
-        } else { perpDist = radialDist; }
+        } else { perpDist = Math.hypot(dx, dz); }

         final RosgenType type = unit.rosgenType() == null ? RosgenType.A : unit.rosgenType();
-        final double delta = RosgenProfile.of(type)
-                .elevationDelta(perpDist, radialDist, unit.width(), decodedElevAtPixel - referenceElevAtUnit);
-        return referenceElevAtUnit + delta;
+        return shellElevAtPixel + RosgenProfile.of(type).bedResidualDelta(perpDist, unit.width());
     }
```

`HydrologyProfileCarver` per-pixel side (CC-M-005-002) — drop the pre-carve elevation sampling; anchor on
the shell already in the buffer; `PrefetchedUnits` no longer carries `decodedElevAtUnit`; `carve`/
`carveAtPixel`/`carvePrefetched` take `shellElevAtPixel`; fallback returns `shellElevAtPixel` unchanged.
(Removes the `ReliefProvider`/`Interpolation` per-unit smoothstep entirely.)

`PopulateNoiseStep.updateToFinalElev` — `RIVER_DIFFERENCE` = trench-vs-shell (CC-M-005-003):
```diff
-                final float preCarveElev = interpolatedElevs[pos];
+                final float shellElev = interpolatedElevs[pos];
                 final float refinedElev = chunkUnits.units().length == 0
-                        ? preCarveElev
-                        : carver.carvePrefetched(chunkUnits, (startX + dx) / scale, (startZ + dz) / scale, preCarveElev);
-                riverDifference[pos] = refinedElev - preCarveElev;
+                        ? shellElev
+                        : carver.carvePrefetched(chunkUnits, (startX + dx) / scale, (startZ + dz) / scale, shellElev);
+                riverDifference[pos] = refinedElev - shellElev; // trench vs. shell, not vs. original terrain
                 interpolatedElevs[pos] = Math.max(bottom, refinedElev) + seaLevel - 1;
```

`HydrologyProfilePainter` — `riverWaterTop` doc (water fills to the shell surface) + `insideChannel` doc
(now covered by the corrected `maxNativeWidth`) (CC-M-005-004) — doc-only.

---

### M-006 — Golden re-baseline (deferred per direction)

**File:** `debug/tests/CLAUDE.md`

Record the deterministic seed, the new `local_carved_elev_v2` store name, and the re-baselined goldens for
`globalRiverTest`/`localRiverTest`/`meandersTest`/`pipelineTest`; note the new-worlds-only stance. **Testing
is deferred per direction — do this pass when validating end-to-end.**

```diff
+## Hydrology carve-first re-baseline
+
+The `local_carved_elev` cache store was renamed to `local_carved_elev_v2` when the tile carve moved to
+"carve first, detail later" (global AND local valley/floodplain shells carved into the cached elevation
+tile; the per-pixel stage now cuts only the bed residual). New-worlds-only: existing `local_carved_elev`
+tiles are left orphaned and the frontier regenerates under the new store name. The hydrology golden
+fixtures were re-baselined against the new carve output using each harness's existing deterministic seed.
```

---

## Execution order

```
W-001 (M-001) → W-002 (M-002) → W-003 (M-003) → W-004 (M-004) → W-005 (M-005) → W-006 (M-006)
```

Strictly sequential: M-002 depends on M-001's `FREEBOARD`/`d(width)`; M-003 calls M-002's
`shellDelta`/`lensMask`; M-004 calls M-003's `carveRiverShells`; M-005 consumes the shell M-004 bakes;
M-006 re-baselines everything.

## Deferred / open follow-ups

- **Local-carve halo (R-001):** the M-004 diff carves the local shell over the PAD=1 buffer. If tile-border
  seams appear in local floodplains, widen the local decode/trace/carve to a halo sized to `riverInfluence`.
- **`d(width)` law tuning (DL-009):** `D_FRACTION=0.5` is a starting point; tune with the debug PNG/TIFF
  dumps and the visualizer `HYDRO_ZONES` / `DECODED` modes.
- **`Meanders.MAX_MARGIN_FRACTION` tuning:** `MAX_MARGIN_FRACTION=0.4` is a first-cut, untuned cap on
  `borderDamping`'s margin. It is engaged across most of the (now ×20-rescaled) width range, leaving only
  ~25% max border-damping at grid center for wide trunks -- under-relaxed but not hard-zeroed. Tune during
  the M-006 visual/relaxation validation, analogous to the `D_FRACTION=0.5` follow-up above.
- **Testing (M-006):** deferred per direction.
