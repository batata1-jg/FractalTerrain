# River Dynamics: Rosgen Level-I Classification from a DEM

Research notes for assigning a Rosgen stream type to every channel in `FractalTerrain`, given nothing
but the decoded elevation field. Scope is **Level I only** — the letters `Aa+ A B C D DA E F G`. The
Level-II substrate digit (`1`–`6`) is out of scope and, as argued in
[What a DEM cannot give you](#4-what-a-dem-cannot-give-you), not recoverable from elevation at all.

Everything below is written against the pipeline that already exists: `Drainage` (sink-fill, D8
direction, flow accumulation) → `RiverNetwork` (channel splines, `bedElevations`, per-node flow) →
`RosgenProfile` (per-type carve geometry).

---

## 1. The classification itself

Rosgen Level I types a reach on five measured attributes: **entrenchment ratio**, **width-to-depth
ratio**, **sinuosity**, **slope**, and **median bed-material size (D50)**. Level I uses the first four;
D50 only enters at Level II.

### 1.1 Delineative criteria

Reproduced verbatim from USDA-NRCS *National Engineering Handbook Part 654, Technical Supplement 3E*,
Table TS3E-4 (which condenses Rosgen 1994/1996). Slope is ft/ft, i.e. dimensionless.

| Type  | Entrench. ratio | W/D    | Sinuosity | Slope       | Description                                                    |
| ----- | --------------- | ------ | --------- | ----------- | -------------------------------------------------------------- |
| `Aa+` | < 1.4           | < 12   | 1.0 – 1.2 | > 0.10      | Very steep, deeply entrenched, debris transport; step/waterfall |
| `A`   | < 1.4           | < 12   | 1.0 – 1.2 | 0.04 – 0.10 | Steep, entrenched, cascading step-pool; confined                |
| `B`   | 1.4 – 2.2       | > 12   | > 1.2     | 0.02 – 0.039| Moderately entrenched, rapids-dominated, narrow sloping valley  |
| `C`   | > 2.2           | > 12   | > 1.4     | < 0.02      | Low-gradient meandering point-bar riffle-pool, broad floodplain |
| `D`   | n/a             | > 40   | n/a       | < 0.04      | Braided, longitudinal/transverse bars, very wide, eroding banks |
| `DA`  | > 4.0           | < 40   | variable  | < 0.005     | Anastomosing, narrow+deep multiple threads, wetland floodplain  |
| `E`   | > 2.2           | < 12   | > 1.5     | < 0.02      | Low-gradient, narrow+deep, highly sinuous, little deposition    |
| `F`   | < 1.4           | > 12   | > 1.4     | < 0.04      | Entrenched meandering riffle-pool, wide+shallow, bank erosion   |
| `G`   | < 1.4           | < 12   | > 1.2     | 0.02 – 0.039| Entrenched gully, step-pool, narrow+deep, moderate gradient     |

Published tolerances: **ER may vary ±0.2 units**; **W/D may vary ±2.0 units** without changing the
type. Treat threshold comparisons as fuzzy, not exact — this matters when you turn the key into code
(§3.5).

### 1.2 Metric definitions

- **Entrenchment ratio (ER)** = flood-prone width / bankfull width. The *flood-prone width* is measured
  at the elevation of **twice the maximum bankfull depth** above the bed. NRCS worked example: `d = 2.5
  ft`, so flood-prone stage is `2d = 5.0 ft` above the thalweg; bankfull width 30 ft, flood-prone width
  250 ft → ER = 8.3.
- **Width-to-depth ratio (W/D)** = bankfull top width / mean bankfull depth, where mean depth =
  cross-sectional area / width. Rosgen calls it "typically the most sensitive indicator" at Level II.
- **Sinuosity (K)** = channel length (along the thalweg) / valley length (along the local valley trend).
- **Slope (S)** = water-surface (or bed) drop / channel centreline length, measured over at least two
  meander wavelengths, conventionally 20–30 channel widths.

### 1.3 Plan view and valley type

Two auxiliary tables. Both are useful because both are DEM-derivable, and the valley table in
particular encodes almost the whole Level-I signal.

Plan view (TS3E-1):

| Plan form                       | Types    |
| ------------------------------- | -------- |
| Relatively straight             | `Aa+ A`  |
| Slightly sinuous                | `B`      |
| Moderately sinuous              | `F G`    |
| Sinuous with active point bars  | `C`      |
| Multiple thread, braided        | `D`      |
| Multiple thread, anastomosed    | `DA`     |
| Tortuous / highly sinuous       | `E`      |

Valley type (TS3E-3, condensed — the discriminating clause plus the associated stream types):

| Valley | Discriminator                                                   | Types            |
| ------ | --------------------------------------------------------------- | ---------------- |
| I      | Steep V-shaped confined, valley slope > 2%                        | `A Aa+`          |
| II     | Moderate relief, gently sloping sides, parabolic valley bottom    | `B`              |
| III    | Depositional alluvial/debris fan, valley slope > 2%               | `A B G D`        |
| IV     | Gentle-gradient canyon/gorge, confined alluvial, floor < 2%       | `F`              |
| V      | U-shaped glacial trough, slopes < 4%                              | `C D G`          |
| VI     | Fault-controlled, colluvial, moderately steep, < 4%               | `B` + `C F`, some `G` |
| VII    | Steep, highly dissected fluvial slopes (badlands)                 | `A G`            |
| VIII   | Mature wide gentle valley, terraces + floodplain                  | `C E` (`D F G`)  |
| IX     | Glacial outwash / eolian sand, moderate-gentle, high sediment     | `C D`            |
| X      | Very broad very gentle, extensive floodplain, lacustrine          | `E C DA` (`G F`) |
| XI     | Deltas and tidal flats, base level fixed by sea/lake              | `DA C E`         |

Note the structure: **valley confinement + valley slope alone already narrow the type to two or three
candidates.** Both are pure DEM quantities. That is the foundation of the scheme in §3.

---

## 2. Mapping each metric onto DEM-derived quantities

Start from elevation `z(x,y)`. `Drainage` already gives sink-filled elevation, D8 flow direction, and
accumulated flow; `RiverNetwork` gives channel splines with per-node `bedElevations` and flow.

| Rosgen metric | DEM-side source                                                              | Quality |
| ------------- | ---------------------------------------------------------------------------- | ------- |
| Slope `S`     | Bed-elevation drop along the channel spline over a reach window               | **Direct** |
| ER            | Perpendicular transects against the *uncarved* elevation at stage `2·d_max`   | **Direct** |
| Bankfull `W`  | Hydraulic-geometry regression on drainage area (or the existing flow law)     | Inferred |
| Bankfull `D`  | Hydraulic-geometry regression on drainage area                                | Inferred |
| W/D           | Ratio of the two inferred quantities → collapses to a function of `DA` alone  | Inferred |
| Sinuosity `K` | Spline arc length / chord length over a reach window                          | **Direct**, but see §4.2 |
| D50           | Nothing                                                                       | **Unavailable** |

### 2.1 Slope

Compute per reach, not per pixel. Hillslope gradient is irrelevant; only along-channel gradient enters
the classification, and hillslope gradient in a Minecraft-scale world is enormous compared to real
channel slopes (§5.1).

```
S(node i) = (bed[i - h] - bed[i + h]) / arcLength(i - h, i + h)
```

with the half-window `h` sized to span roughly 20 channel widths — Rosgen's own reach definition. With
`HydrologyTuning.DX = 1.5` px between spline points and a width `w` in px, `h ≈ 20·w / (2·1.5) ≈ 6.7·w`
nodes. Clamp to the channel's node count.

`ChannelElevationAssigner` propagates bed elevations monotonically downstream, so `S ≥ 0` by
construction and there is no need to guard against uphill reaches.

### 2.2 Entrenchment ratio — the transect algorithm

This is the field method transcribed to a raster, and it is exact rather than a proxy. Each
`HydrologicalPrimitive` already carries a **unit normal** (`normalVec`) and a **bed elevation**, so a
transect is a straight raster walk.

```
floodProneStage = bed + 2 * dMax
for side in {+1, -1}:
    step outward along (±normal) in 0.5-px increments
    stop when elevation(sample) > floodProneStage
       or when distance > MAX_FLOODPRONE_HALFWIDTH
    record halfWidth[side]
floodProneWidth = halfWidth[+1] + halfWidth[-1]
ER = floodProneWidth / bankfullWidth
```

Three implementation constraints, each of which will silently corrupt the result if ignored:

1. **Sample the raw decoded elevation, never the carved buffer.** `HydrologyProfileInprinter.carveRiverShells`
   *creates* the floodplain — measuring ER on its output measures the tuning constants
   (`FLOODPLAIN_BASE`, `FLOODPLAIN_WIDTH_FACTOR`), not the terrain. The classification is only
   meaningful upstream of the first carve.
2. **Cap the walk.** In a broad flat valley the flood-prone stage is never exceeded, and the walk runs
   to the tile edge. Cap at `MAX_INFLUENCE_RADIUS` (128 px) and treat "hit the cap on both sides" as
   `ER = ∞` → the slightly-entrenched branch. This is the correct semantic, not a failure.
3. **Average over the reach.** A single transect is noisy. Use the median ER over the same reach window
   used for slope; the median is more robust than the mean against a single transect that escapes
   through a tributary mouth.

`d_max` (maximum bankfull depth) is not modelled anywhere in the project. Use
`dMax = DEPTH_MAX_FACTOR · dMean` with `DEPTH_MAX_FACTOR ≈ 1.5`. **Caveat:** that factor is a rule of
thumb, not something I verified against a source — calibrate it visually rather than trusting it.

### 2.3 Bankfull width and depth from drainage area

Bieger, Rathjens, Allen & Arnold (2015), *Development and Evaluation of Bankfull Hydraulic Geometry
Relationships for the Physiographic Regions of the United States*, JAWRA. Drainage area `DA` in km²,
width and depth in m. The nationwide relations pool 1,254–1,279 sites:

| Quantity            | Nationwide (USA)   | R²   |
| ------------------- | ------------------ | ---- |
| Bankfull width      | `W = 2.70·DA^0.352` | 0.66 |
| Bankfull depth      | `D = 0.30·DA^0.213` | 0.43 |
| Cross-sectional area| `A = 0.95·DA^0.540` | 0.58 |

Regional spread, for a sense of how much the coefficient moves with terrain character (width only):

| Region                    | Equation             | R²   |
| ------------------------- | -------------------- | ---- |
| Appalachian Highlands     | `W = 3.12·DA^0.415`  | 0.87 |
| Atlantic Plain            | `W = 2.22·DA^0.363`  | 0.84 |
| Interior Plains           | `W = 2.56·DA^0.351`  | 0.75 |
| Rocky Mountain System     | `W = 1.24·DA^0.435`  | 0.76 |
| Intermontane Plateau      | `W = 1.11·DA^0.415`  | 0.62 |
| Pacific Mountain System   | `W = 2.76·DA^0.399`  | 0.74 |

The exponent is stable (0.35–0.44); the coefficient carries the regional signal, ranging 1.11–3.12 —
roughly a factor of 3. In a synthetic world this is a **free stylistic knob**: pick the coefficient to
set how wide rivers look, pick the exponent to set how fast they widen downstream.

### 2.4 The W/D collapse

Dividing the two nationwide relations:

```
W/D = (2.70/0.30) · DA^(0.352 − 0.213) = 9.0 · DA^0.139        (DA in km²)
```

This is the single most useful derived result in this document, because it says **W/D is not an
independent observable — it is a monotone function of drainage area.** Concretely:

| DA (km²) | 0.1 | 1   | 10   | 100  | 1 000 | 10 000 |
| -------- | --- | --- | ---- | ---- | ----- | ------ |
| W/D      | 6.5 | 9.0 | 12.4 | 17.0 | 23.5  | 32.4   |

- The `W/D = 12` boundary — the one that separates `E`/`G`/`A` (narrow-deep) from `C`/`F`/`B`
  (wide-shallow) — falls at **DA ≈ 8 km²**.
- The `W/D = 40` boundary (`D` vs `DA`) falls at **DA ≈ 46 000 km²**. At any world scale this project
  will plausibly use, that threshold is effectively unreachable, so it cannot discriminate `D` from
  `DA`. Use slope and valley form for that split instead (§3.4).

So the W/D criterion reduces to a **catchment-size threshold**: small streams are narrow and deep, large
rivers are wide and shallow, with the crossover at a single tunable drainage area.

### 2.5 Channel-pattern thresholds (for `D` and `DA`)

Rosgen's own criteria do not really discriminate braided from single-thread — `D` is defined by
`W/D > 40` and by plan form, both circular for a generator. The fluvial literature supplies external
discriminants:

- **Leopold & Wolman (1957)**, *River Channel Patterns: Braided, Meandering and Straight*, USGS
  Professional Paper 282-B. Braided above, meandering below a slope threshold:
  `S = 0.06 · Q^−0.44` with `Q` bankfull discharge in ft³/s; converting, `S ≈ 0.012 · Q^−0.44` for `Q`
  in m³/s. **Caveat:** I was unable to read the original 282-B to confirm the coefficient; secondary
  sources agree the exponent sits in −0.25…−0.44 and this form is the widely quoted one. Treat the
  coefficient as approximate.
- **van den Berg (1995)**: braiding when potential specific stream power exceeds
  `ω_v = 900 · D50^0.42` W/m² (`D50` in m), with `ω_v = ρ g Q_bf S_v / W`. Requires D50, so it is
  unusable here directly — but note that the related empirical result `P = 1.22 · ω^0.09` (sinuosity
  from potential specific stream power) is a *prescriptive* law you could invert to drive the meander
  relaxation per type.

For this project the honest position is: **braiding is a style choice, not a measurement.** There is no
sediment-transport model, so nothing in the DEM distinguishes a braided reach from a meandering one.
Gate `D` on the conditions under which braiding *would* be plausible (unconfined valley, mid slope,
large catchment) and accept it as an authored outcome.

---

## 3. Proposed classification scheme

### 3.1 The key insight: which metrics are inputs and which are outputs

Of Rosgen's five attributes, only **two are genuine observables of the generated terrain**:

| Metric      | Status in this project                                                                 |
| ----------- | --------------------------------------------------------------------------------------- |
| Slope       | **Observable.** Emergent from the diffusion elevation field via `ChannelElevationAssigner`. |
| ER          | **Observable.** Emergent from valley confinement in the decoded elevation.                |
| Drainage area | **Observable.** `Drainage.computeFlow` accumulation.                                    |
| W/D         | **Not observable.** `W` comes from `HydrologyTuning.widthFromFlow`; `D` is not modelled at all. Whatever W/D you compute, you chose it. |
| Sinuosity   | **Not observable.** Produced by the `Meanders` relaxation. Whatever sinuosity you measure, you chose it. |
| D50         | Absent.                                                                                    |

Trying to classify on all five is circular: three of them are outputs of tuning constants. The workable
architecture is therefore:

```
DEM  ──►  measure S, ER, DA  ──►  KEY  ──►  Rosgen type  ──►  prescribe W/D, sinuosity,
                                                              floodplain extent, bed form
```

Classification **diagnoses** from the two independent observables plus catchment size, and then
**prescribes** everything else. That is also exactly the shape `RosgenProfile` already has — a
per-type enum that dictates `floodPlainLength`, `riverInfluence` and the cross-section — so this
inverts cleanly onto the existing code.

### 3.2 Measured inputs

Per reach (a window of ~20 bankfull widths around each spline node):

| Symbol   | Definition                                                | Source                                   |
| -------- | --------------------------------------------------------- | ---------------------------------------- |
| `S`      | Along-channel bed slope, reach-averaged                    | §2.1, `Channel.bedElevations` + spline    |
| `ER`     | Median flood-prone width / bankfull width over the reach   | §2.2, transects on raw decoded elevation  |
| `DA`     | Drainage area at the reach's downstream end                | `Drainage` flow accumulation × cell area  |
| `W`      | Bankfull width                                             | `HydrologyTuning.widthFromFlow`, or §2.3  |
| `WD`     | `9.0 · DA_km2^0.139`, or `W / D` if you model depth        | §2.4                                     |
| `zSea`   | Reach bed elevation relative to sea level                  | For the `DA` (anastomosing) gate          |

### 3.3 The decision key

Ordered, deterministic, first match wins. This is the standard Rosgen key restructured so that every
test uses only a measured quantity.

```
classify(S, ER, WD, DA, zSea):

    # ---- Steep confined headwaters: slope alone decides -------------------
    if S >= S_AA:                       return Aa+          # S_AA  ≈ 0.10
    if S >= S_A:                        return A            # S_A   ≈ 0.04

    # ---- Entrenched: valley pinches the channel ---------------------------
    if ER < ER_ENTRENCHED:              # ≈ 1.4
        if WD < WD_NARROW:              return G            # WD_NARROW ≈ 12
        else:                           return F

    # ---- Moderately entrenched -------------------------------------------
    if ER < ER_SLIGHT:                  return B            # ER_SLIGHT ≈ 2.2

    # ---- Slightly entrenched: broad floodplain available ------------------
    if zSea < DELTA_ELEV and S < S_DA and ER > ER_ANASTOMOSE:
                                        return DA           # S_DA ≈ 0.005, ER_ANASTOMOSE ≈ 4.0
    if S > braidThreshold(DA) and DA > DA_BRAID:
                                        return D
    if WD < WD_NARROW:                  return E
    else:                               return C
```

Ordering rationale, in the order the tests fire:

1. **Slope first.** `Aa+` and `A` are defined by slope bands that no other type overlaps; both are
   entrenched by definition in their landform (valley types I, III, VII), so testing ER first would only
   add a way to get them wrong.
2. **ER second**, because it is the only test that separates the entrenched family (`F`, `G`) from
   everything with a floodplain. Within that family, W/D — i.e. catchment size — picks narrow-deep `G`
   (a gully) over wide-shallow `F` (an incised meandering riverPrimitive). Note `B`'s published slope band
   (0.02–0.039) overlaps `G`'s exactly; ER, not slope, is what distinguishes them.
3. **`DA` (anastomosing) before `D` (braided)**, because both want unconfined valleys and the
   anastomosing case is far more specific: near base level, essentially flat, extremely wide flood-prone
   area. Deltas and tidal flats (valley type XI). Test it first and `D` never steals it.
4. **`E` vs `C` last**, on W/D alone — exactly the DA ≈ 8 km² crossover of §2.4. Small meadow streams
   become `E`; trunk rivers become `C`.

`braidThreshold(DA)` is the Leopold–Wolman form with discharge substituted by drainage area. If you
adopt `Q_bf ∝ DA` (reasonable for a synthetic world with uniform runoff), the threshold collapses to
`S_braid = k · DA^−0.44` for a single calibration constant `k`. See §5.2.

### 3.4 What the key does *not* attempt

- **No `D` vs `DA` split on W/D.** The published `40` boundary is unreachable (§2.4). The split is on
  slope and base-level proximity.
- **No Level-II digit.** No substrate model exists (§4.1).
- **No plan-form input.** Braiding and anastomosis would have to be detected from the channel graph
  (parallel threads sharing a corridor), which `RiverNetwork.manageCollisions` prunes rather than
  preserves. Treat them as prescribed, not measured.

### 3.5 Hysteresis

Rosgen's own tolerances (ER ±0.2, W/D ±2.0) exist because the metrics are noisy. A raster
implementation is noisier still. If you classify per-node with hard thresholds you get types flickering
along a single riverPrimitive, and since `RosgenProfile` controls `floodPlainLength` and `riverInfluence`, a
flicker becomes a visible scalloped floodplain edge.

Two mitigations, both cheap:

- **Classify per channel segment, not per primitive.** Compute one type for a whole `Channel` (or for each
  contiguous run of `N` nodes) and stamp every primitive from that channel with it. `RiverNetwork.collectPrimitives`
  is already the single point where types are stamped.
- **Apply the published tolerances as a dead band.** When a reach's ER sits within ±0.2 of a threshold,
  keep the upstream neighbour's type. Same for W/D within ±2.0.

---

## 4. What a DEM cannot give you

### 4.1 D50 and the Level-II digit

The substrate classes are, from TS3E:

| Digit | Class     | D50 range           |
| ----- | --------- | ------------------- |
| 1     | Bedrock   | > 2048 mm           |
| 2     | Boulder   | 256 – 2047.9 mm     |
| 3     | Cobble    | 64 – 255.9 mm       |
| 4     | Gravel    | 2 – 63.9 mm         |
| 5     | Sand      | 0.062 – 1.99 mm     |
| 6     | Silt/clay | < 0.062 mm          |

Grain size is a function of lithology, transport history and sediment supply. None of these are in the
elevation field. If you ever want a Level-II digit for aesthetic purposes (bed block choice: stone vs
gravel vs sand vs clay), derive it *prescriptively* from slope and drainage area — coarse and steep
upstream, fine and flat downstream — via the `relief/` rock-strata channels. That is a plausible
substrate model, but it is authored, not classified, and should not be reported as a Rosgen Level-II
type.

### 4.2 Sinuosity, and why measuring it is a trap

Two distinct problems:

1. **D8 flow paths cannot represent sinuosity.** A D8-traced channel is constrained to eight directions,
   so its arc length is quantised — measured sinuosity on a raw D8 path is a discretisation artefact.
   This project sidesteps that: `LocalDrainageTracer` resamples to a `QuinticHermiteSpline` at
   `RESAMPLE_DIST = 2.0`, and sinuosity measured on the spline is meaningful.
2. **It is still an output.** The spline's sinuosity is whatever the `Meanders` relaxation produced.
   Feeding it back into classification means the meander tuning decides the Rosgen type, which then
   decides the floodplain width — a feedback loop with no external anchor.

Use sinuosity as a **validation metric**, not a classifier input: after assigning types, check that `E`
reaches really are more sinuous than `B` reaches. If they are not, the meander relaxation needs a
per-type target, which is the prescriptive direction (§3.1).

### 4.3 Bankfull stage

Every Rosgen dimension is referenced to bankfull discharge — a field-identified stage, typically the
1.5-year return interval (Williams 1978, per TS3E; ranges of 1.1–2.0 years are common, and a 1.1→1.5
year shift can mean 68% more flow). There is no hydrograph here, so bankfull is by definition whatever
`widthFromFlow` says it is. This is the deepest reason W/D cannot be an input.

---

## 5. Calibration for this project

### 5.1 Slope thresholds must be recalibrated, not copied

Rosgen's slope bands (0.02 / 0.04 / 0.10) are real-world channel slopes. A Minecraft-scale world is
vertically compressed relative to its horizontal extent in one direction and exaggerated in the other:
a 150-block mountain over 300 blocks of horizontal run is slope 0.5, five times the `Aa+` threshold.
Copy the literature numbers directly and you will classify most of the world as `Aa+`.

Two options, in order of preference:

1. **Percentile calibration.** Sample along-channel slope over a large batch of generated tiles
   (`localRiverTest` already dumps this geometry), build the distribution, and place `S_AA` and `S_A` at
   fixed percentiles. Rosgen's own bands split real channel networks roughly into "most reaches below
   0.02"; matching the *shape* of the distribution matters more than matching the numbers.
2. **Explicit scale constant.** Define `METRES_PER_PX` and convert. Cleaner dimensionally, but it
   assumes the diffusion model's relief is metrically faithful, which is not established.

Do this before anything else — the key in §3.3 fires on slope first, so slope miscalibration dominates
every other error.

### 5.2 Flow accumulation → drainage area

`Drainage.computeFlow` accumulates a weighted quantity, not a cell count: `FLOW_INITIAL_GLOBAL = 0.4`,
`FLOW_PER_CELL_GLOBAL = 2.0`, `FLOW_INITIAL_LOCAL = 0.002`, `FLOW_PER_CELL_LOCAL = 0.001`. Two
consequences:

- **Flow is not comparable between the global and local networks** — the per-cell weights differ by
  2000×. Any DA-derived threshold needs to know which network a channel came from, or the weights need
  unifying first.
- **Converting to km² needs one constant.** `DA_km2 = (flow / FLOW_PER_CELL) · (METRES_PER_PX² / 1e6)`.
  Since both `METRES_PER_PX` and the per-cell weight are arbitrary, fold them into a single
  `FLOW_TO_KM2` calibration constant and set it so that the `W/D = 12` crossover (§2.4) lands on the
  channel size where you *want* rivers to start looking wide and shallow. That is one knob controlling
  the `E`↔`C` and `G`↔`F` splits, which is the right level of control.

### 5.3 Note on the existing width law

`HydrologyTuning.widthFromFlow` is `W = 0.4·√flow`, clamped to `[0.2, 16]` px. If `flow ∝ DA`, that is
`W ∝ DA^0.50`, against the literature's `DA^0.35–0.44`. The project's rivers therefore widen downstream
noticeably faster than real ones. Not a bug — but if you adopt the hydraulic-geometry relations for W/D
(§2.4) while keeping `√flow` for width, the two are inconsistent, and the inconsistency shows up as
rivers whose apparent shape disagrees with their assigned type. Pick one exponent.

---

## 6. Integration points

**Status: implemented.** This section was written as a forward-looking plan; the Rosgen classification
work it describes has since been built out in `hydrology/rosgen/` (`ReachMetricsSampler`, `RosgenKey`,
`ReachRosgenClassifier`). Rows below are kept for their design rationale, with citations updated to
reflect what shipped.

| Where | What changes |
| ----- | ------------ |
| `hydrology/HydrologicalPrimitive.java`, the `RosgenType` enum | Enum currently holds `A B C D` only. Level I needs `Aa+ A B C D DA E F G`. Note the serialisation writes `rosgenType.ordinal()` (the `serialize()` method) and reads it back by index (the `deserialize()` method) — **appending is safe, reordering breaks every persisted tile**. |
| `hydrology/profile/RosgenProfile.java` | Mirror enum, currently only `A` overrides anything. New constants need `floodPlainLength` / `riverInfluence` / bed-profile overrides; the §3.1 prescription lives here. |
| `hydrology/meanders/RiverNetwork.java` — **RESOLVED**. | The `\ TODO: change this to the correct type` placeholder and its hardcoded `HydrologicalPrimitive.RosgenType.A` fallback are gone, resolved in commit 83e972f ("feat(hydrology): stamp Rosgen type and endpoint kind on primitives in collectPrimitives"). The stamping point is now the `final RosgenType rosgen = ...` assignment inside `collectPrimitives`, fed by the classifier in `hydrology/rosgen/`. |
| `hydrology/LocalRiverProvider.java`, in `buildTile` | First `ChannelElevationAssigner.assign` then first `HydrologyProfileInprinter.carveRiverShells`. **Classification must run between these two calls**: `assign` provides `bedElevations` (needed for slope and flood-prone stage), and the carve destroys the raw valley geometry ER depends on (§2.2). |
| `config/HydrologyTuning.java` | New home for `S_AA`, `S_A`, `ER_ENTRENCHED`, `ER_SLIGHT`, `ER_ANASTOMOSE`, `WD_NARROW`, `FLOW_TO_KM2`, `DEPTH_MAX_FACTOR`. |
| `hydrology/meanders/Meanders` | If sinuosity becomes prescriptive per type (§4.2), the relaxation needs a per-type target. |

One ordering hazard worth stating explicitly: `LocalRiverProvider.buildTile` runs `assign` and
`carveRiverShells` **twice**, and `ARCHITECTURE.md` flags the carve as order-dependent and not
refactor-safe. Classifying on the second pass would read an already-carved buffer and produce garbage
ER. Classify once, on the first pass, and cache the type on the `Channel`.

---

## 7. Objectivity: known limitations of the Rosgen system

Worth recording, because the system is contested and the criticisms bear directly on how much weight to
put on the output.

- **Form, not process.** Rosgen classifies morphology. Kasprak et al. (2016), *The Blurred Line between
  Form and Process*, PLOS ONE 11(3):e0150293, note it "has received criticism in the geomorphic
  literature for its methods, more so than the other classification frameworks", specifically over
  whether the types represent a distinct suite of processes or arbitrary cuts along a continuum. For a
  *generator* this criticism is largely moot — you want a form vocabulary, and form is exactly what it
  supplies.
- **Observer variance.** Types assigned to the same reach differ between trained observers (Roper et
  al., USFS, on northeastern Oregon mountain streams). An automated DEM implementation is at least
  perfectly reproducible.
- **Static.** TS3E itself: the classification "describes a static condition that is not necessarily
  related to a specific process or change", cannot predict a new stable form after disturbance, and
  "does not have the ability to take into account previous or anticipated hydrologic changes". Assigning
  a type does not imply the reach is stable.
- **Bankfull identification is the weak link**, especially in incising channels — and that is precisely
  the quantity this project has no independent access to (§4.3).
- **Kasprak et al.'s agreement figures**, for scale: comparing Natural Channel Classification against
  other frameworks across their sites, 61% good agreement, 19% moderate, 20% poor. Even between
  careful expert applications of competing schemes, a fifth of reaches disagree. Do not over-engineer
  precision that the underlying system does not have.

For this project the practical reading is: Rosgen Level I is a good **vocabulary of riverPrimitive shapes** with
published, quantitative, DEM-tractable boundaries. It is not a physical model, and the generated result
should be judged on whether it looks right, not on whether it would survive a geomorphologist's field
audit.

---

## Sources

- [USDA-NRCS, *National Engineering Handbook* Part 654, Technical Supplement 3E — Rosgen Stream Classification Technique](https://directives.nrcs.usda.gov/sites/default/files2/1712931124/7381.pdf) — Tables TS3E-1 (plan view), TS3E-3 (valley types), TS3E-4 (delineative criteria), TS3E-5 (sensitivity by type); metric definitions; substrate classes. Primary source for §1.
- [Rosgen, D.L. (1994), *A classification of natural rivers*, CATENA 22(3):169–199](https://en.wikipedia.org/wiki/Rosgen_Stream_Classification) — the original system that TS3E condenses.
- [Bieger, K., Rathjens, H., Allen, P.M., Arnold, J.G. (2015), *Development and Evaluation of Bankfull Hydraulic Geometry Relationships for the Physiographic Regions of the United States*, JAWRA](https://swat.tamu.edu/media/114657/bieger_etal_2015.pdf) — Table 3, the width/depth/area vs drainage-area regressions in §2.3.
- [Kasprak, A. et al. (2016), *The Blurred Line between Form and Process: A Comparison of Stream Channel Classification Frameworks*, PLOS ONE 11(3):e0150293](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0150293) — DEM-derived application of the Rosgen metrics (100+ cross sections per site via the River Bathymetry Toolkit); framework agreement rates; limitations.
- [Nagel, D.E., Buffington, J.M., Parkes, S.L., Wenger, S., Goode, J.R. (2014), *A landscape scale valley confinement algorithm*, USFS RMRS-GTR-321](https://www.fs.usda.gov/research/treesearch/45825) — the standard DEM approach to valley confinement (cost-weighted distance, flood height, ground slope, max valley width) at 10–30 m DEM resolution; the closest published analogue to §2.2.
- [Garousi-Nejad, I. et al. (2019), *Terrain Analysis Enhancements to the Height Above Nearest Drainage Flood Inundation Mapping Method*, Water Resources Research](https://agupubs.onlinelibrary.wiley.com/doi/full/10.1029/2019wr024837) — HAND as an alternative to per-transect flood-prone width; sensitivity to the flow-accumulation channel-initiation threshold.
- [Leopold, L.B. & Wolman, M.G. (1957), *River Channel Patterns: Braided, Meandering and Straight*, USGS Professional Paper 282-B](https://www.scirp.org/reference/referencespapers?referenceid=1691948) — the slope–discharge braiding threshold in §2.5. Coefficient unverified against the original.
- [Kleinhans, M.G. & van den Berg, J.H. (2011), *River channel and bar patterns explained and predicted by an empirical and a physics-based method*; van den Berg, J.H. (1995) threshold](https://www.sciencedirect.com/science/article/abs/pii/S0169555X10001893) — potential specific stream power `ω_v = 900·D50^0.42` W/m²; the sinuosity relation `P = 1.22·ω^0.09`.
- [Simon, A., Doyle, M., Kondolf, M., Shields, F.D. Jr., Rhoads, B., McPhillips, M. (2007), *Critical Evaluation of How the Rosgen Classification and Associated 'Natural Channel Design' Methods Fail to Integrate and Quantify Fluvial Processes and Channel Responses*, JAWRA](https://www.researchgate.net/publication/242721553_Discussion_Critical_Evaluation_of_How_the_Rosgen_Classification_and_Associated_'Natural_Channel_Design'_Methods_Fail_to_Integrate_and_Quantify_Fluvial_Processes_and_Channel_Responses) — the principal published critique referenced in §7.
- [US EPA Watershed Academy, *Fundamentals of Rosgen Stream Classification System*](https://cfpub.epa.gov/watertrain/moduleFrame.cfm?parent_object_id=1199) — entrenchment bands (1.0–1.4 entrenched, 1.41–2.2 moderately, > 2.2 slightly).
