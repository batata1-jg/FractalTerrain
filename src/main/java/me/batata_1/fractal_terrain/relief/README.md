# relief/

## Overview

Builds the final per-tile relief tensor (`[RELIEF_CHANNELS=7, 512, 512]`) from a weight-normalized decode
of the diffusion decoder's residual channels plus a Difference-of-Gaussians high-frequency channel.
Elevation (channel 0) is the exception: `hydrology.RiverProvider` decodes the same residual separately to
trace and carve rivers, and `ReliefProvider` imports that provider's `hydrology_relief` tile as channel 0
rather than decoding elevation itself.

## Architecture

`DecoderChannels.decode` is a stateless static helper — not an instance method on either provider — that
fetches a haloed decoder slice from the shared `WorldPipeline` and weight-normalizes it into
`BASE_CHANNELS` channels. Both `ReliefProvider` (for the relief channels and the residual DoG) and
`hydrology.RiverProvider` (for the elevation/gradients it traces and carves) call this same helper
directly.

## Design Decisions

**Why the decode is a stateless shared helper instead of living on one provider.** Both `ReliefProvider`
and `RiverProvider` need the same decoded decoder slice — `ReliefProvider` to fill its own relief
channels, `RiverProvider` to trace and carve off its own copy. The data dependency between them runs one
way only, `RiverProvider` → `ReliefProvider` (the carved channel 0), and it is a dependency on a published
tile store, not on a decode. Hanging the decode off either provider's instance would add a second,
opposite-direction dependency purely to obtain a slice neither has another reason to want from the other.
A stateless static helper both call directly keeps the instance graph acyclic: `ReliefProvider` depends on
`RiverProvider`, and both depend only on the shared pipeline for the decode.

**Why channel 0 is carved but the channel-6 DoG is not.** The Difference-of-Gaussians runs over the raw
decoded elevation, not the carved import, so the high-frequency band it extracts describes the terrain the
diffusion model produced. Running it over the carve would let every channel bank register as a
high-frequency feature and feed the cut back into the detail channels.
