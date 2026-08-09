# rosgen/

## Overview

Assigns each river reach a Rosgen Level-I stream type, which then selects the reach's `RosgenProfile` —
and therefore its floodplain half-extent and outer influence radius. The classification is not
decoration: it decides how wide the carve is.

Level II (the substrate digit `1`–`6`) is out of scope and not recoverable. Grain size is a function of
lithology, transport history and sediment supply, none of which exist in an elevation field.

## Key ordering

`RosgenKey` is first-match-wins and the order is load-bearing:

1. **Slope first.** `Aa+` and `A` occupy slope bands no other type overlaps, and both are entrenched by
   definition in their landform, so testing entrenchment first would only add a way to get them wrong.
2. **Entrenchment second** — the only test separating the entrenched family (`F`, `G`) from everything
   with a floodplain. Within that family, width-to-depth picks narrow-deep `G` (a gully) over
   wide-shallow `F` (an incised meandering river). `B`'s published slope band overlaps `G`'s exactly, so
   entrenchment, not slope, is what distinguishes them.
3. **`DA` before `D`.** Both want unconfined valleys, but anastomosing is far more specific: near base
   level, essentially flat, extremely wide flood-prone area. Testing it first stops braiding from
   stealing it.
4. **`E` vs `C` last**, on width-to-depth alone — small meadow streams become `E`, trunk rivers `C`.

## What is measured and what is prescribed

Only **slope**, **entrenchment** and **width** are genuine observables: slope and entrenchment emerge
from the diffusion elevation field, width from flow accumulation.

**`widthDepth` is prescribed, not measured.** No depth is modelled; it is derived from width by
`ChannelGeometry.widthDepthRatio`. It is kept in `ReachMetrics` only because the published key tests it.
Consequently `WD_NARROW` means whatever `W_REF` makes it mean — calibrate `W_REF`, never `WD_NARROW`.

**Sinuosity is deliberately absent.** The meander relaxation produces it, so feeding it back would let
meander tuning decide the Rosgen type, which decides floodplain width. Use sinuosity to validate the
result, never to produce it.

**Braiding is authored, not detected.** There is no sediment-transport model, and nothing in an
elevation field distinguishes a braided reach from a meandering one, so `braidThreshold` gates where
braiding would be *plausible* and the outcome is accepted as a style choice.

## Why per reach, not per point

A transect walks perpendicular to the channel, so consecutive samples stride a whole row and get no
spatial locality — each sample is close to a cache miss. `collectPrimitives` resamples at a spacing that
floors at 0.5 px, so a detailed tile emits tens of thousands of points. One transect per reach (Rosgen's
own ~20-channel-width definition) is three orders of magnitude cheaper and is what the scheme specifies.

## Why the graph walk is downstream-first

`RiverNetwork.update` splits a trunk river into a separate `Channel` at every confluence. Applying the
dead band per channel would reset it at every junction, so a trunk could change type at each confluence
for no terrain reason — reproducing, at every junction in the world, exactly the scalloped floodplain
edge the dead band exists to prevent. Walking from drains upstream lets each channel's downstream-most
reach inherit its downstream neighbour's upstream-most type.

`orderDownstreamFirst` seeds a BFS frontier with the DRAIN-adjacent channels and expands through
`startNode.incoming`, which visits a channel before anything feeding it. **No reversal is wanted** —
reversing would invert the order and break `seedFor`, which reads the downstream neighbour's committed
types and therefore requires that neighbour classified first.

### Known limitation: dangling components

The walk is not fully downstream-first for components with no reachable drain. The injected component
root is whichever unseen channel has the lowest id, not necessarily the component's outlet; since
channel ids run low-to-high upstream-to-downstream, a pruned-tail component such as `X(5)→Y(6)→Z(7)` is
emitted `X, Y, Z` — upstream-first, the wrong direction for that component.

`seedFor` then returns `null` for `X` and `Y`, which `applyDeadBand` handles by committing the raw type
rather than crashing, so the dead band simply resets at those boundaries. Chasing the true outlet would
mean following `getNode(endNodeId).outgoing` to the component root with a cycle guard — a dangling
component's single-outflow chain has no drain to stop at. Not worth it for a case that only arises in
pruned components.

## The dead band

Rosgen's published tolerances (ER ±0.2, W/D ±2.0) are applied as a dead band: when a reach's ratio sits
within tolerance of a threshold the key compares it against, the neighbouring reach's type is kept
instead of committing to the raw one.

The tolerances exist because field metrics are noisy; a raster implementation is noisier still. Without
the dead band, types flicker along a single river, and because the profile controls `floodPlainLength`
and `riverInfluence`, a flicker becomes a visibly scalloped floodplain edge.

**Scope is ER and W/D only.** The slope bands and the braiding threshold are deliberately outside it.
Slope is a real property of the landform rather than a noisy transect measurement, and a reach genuinely
crossing into the steep bands should change type there; suppressing that would smear `Aa+`/`A`
headwaters into the reaches below them. Slope-driven variation is intended behaviour, not flicker.

## Invariants

- **`ReachMetricsSampler` must be handed raw decoded elevation, never a carved buffer.**
  `carveRiverShells` *creates* the floodplain and writes in place, so measuring entrenchment on its
  output measures `FLOODPLAIN_BASE` and `FLOODPLAIN_WIDTH_FACTOR` — the carve's own tuning constants —
  instead of the terrain. `Meanders` holds a pre-carve snapshot for exactly this reason.
- **A saturating transect returning `+inf` is correct, not a failure.** It is the right reading for a
  broad flat valley and lands in the slightly-entrenched branch.
- **Slope is floored at 0.** Beds are propagated monotone non-increasing downstream, so an uphill reach
  means degenerate geometry rather than real terrain; flooring keeps it out of the steep branches.
- **Calibration is unfinished.** The slope bands are literature values for real-world channels, and a
  Minecraft-scale world is vertically exaggerated relative to its horizontal run, so they classify most
  of the world as `Aa+`. The key tests slope first, so slope miscalibration dominates every other error
  — recalibrate from the `localRiverTest` slope histogram before judging any other threshold. See
  `config/README.md`.
