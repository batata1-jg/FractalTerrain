# config/

## Overview

Configuration split by concern, with `FractalTerrainConfig` at package root as a backward-compatible
facade re-exporting these constants under historical names. New code should read the owning class here.

## Hydrology Tuning Calibration

Most `HydrologyTuning` values are first-cut and untuned. They were chosen to get the pipeline running,
not from measurement, and they are expected to change. This section records what each uncalibrated knob
controls and how its miscalibration shows up, because none of that is visible from the value.

Calibrate visually via the `localRiverTest` harness.

### Rosgen slope bands are the dominant error source

Rosgen's published slope values describe real-world channels. A Minecraft-scale world is vertically
exaggerated relative to its horizontal run — a 150-block rise over 300 blocks is slope 0.5, five times
`S_AA` — so the literature values classify most of the world as `Aa+`. `S_AA` and `S_A` are the
literature figures used as a starting point only.

The classification key tests slope first, so slope miscalibration dominates every other error. Recalibrate
from the slope histogram `localRiverTest` dumps before judging any other threshold.

### Failure signatures

| Constant | Too small | Too large |
| --- | --- | --- |
| `LOCAL_ATTACH_RADIUS` | Parallel double rivers — the local walk runs alongside the global channel instead of joining it | Local detail truncated; interior tributaries excluded well before they would naturally reach the global channel |
| `MAX_ECCENTRICITY` | Full-strength delta widens along the channel; at the limit it applies unfaded over the whole floodplain disc | Full strength confined to the unit's own cross-section line; everything else fades |
| `BRAID_MIN_WIDTH` / `K_BRAID` | Braiding becomes common | Braiding never appears |
| `ER_MIN_STEPS_PER_SIDE` | A reach at the `MIN_WIDTH` floor has a walk shorter than one step, samples nothing, and reports as unconfined | Step shrinks below useful resolution |

`S_DA` and `DELTA_ELEV` gate anastomosing (`DA`) reaches. Neither is a published Rosgen figure; both were
introduced so `DA` cannot claim a reach with any real fall or any elevation above near-base-level.

### Derived rather than free

- `BRAID_WIDTH_EXPONENT` follows from Leopold & Wolman (1957) `S = k·Q^-0.44`; with `W ∝ √flow ∝ √DA`
  the threshold becomes a law in width, giving `-0.44 / 0.50`.
- `WD_NARROW` is Rosgen's published boundary at 12, but the width-to-depth ratio it is compared against
  is *prescribed* by `ChannelGeometry.widthDepthRatio`, not measured. The pair only means what `W_REF`
  makes it mean — calibrate `W_REF`, never `WD_NARROW`.
- `INFLUENCE_BLEND_MULTIPLIER` is dimensionless, not a native-px width. The blend band's actual width is
  `riverInfluence − floodPlainLength`.

## Invariants

**`ER_WALK_WIDTHS` must not be replaced by `MAX_INFLUENCE_RADIUS`.** They look interchangeable and are
not: `MAX_INFLUENCE_RADIUS` is a carve constant, and a 128 px walk on a 514 px padded buffer overruns the
tile for any channel within 128 px of a border. The overrun is silent — `sampleBilinear` clamps rather
than failing, so the transect reads the edge pixel repeated and reports `ER = ∞`.

**`MARGIN_INFLUENCE_FACTOR` is deliberately wider than the influence radius**, so a channel's entire carve
band stays inside the grid rather than only its centreline.

**`MAX_LOCAL_WIDTH` is dead.** No live code reads it, including through the facade. It is retained only
because removing a facade re-export is a separate migration.
