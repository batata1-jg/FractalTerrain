# relief/

## Overview

Builds the final per-tile relief tensor (`[RELIEF_CHANNELS=7, 512, 512]`) by combining carved elevation
imported from the hydrology pipeline with a weight-normalized decode of the diffusion decoder's residual
channels, plus a Difference-of-Gaussians high-frequency channel.

## Architecture

`DecoderChannels.decode` is a stateless static helper — not an instance method on either provider — that
fetches a haloed decoder slice from the shared `WorldPipeline` and weight-normalizes it into
`BASE_CHANNELS` channels. Both `ReliefProvider` (for the relief channels and the residual DoG) and
`hydrology.LocalRiverProvider` (for the elevation/gradients it traces and carves) call this same helper
directly.

## Design Decisions

**Why the decode is a stateless shared helper instead of living on one provider.** In the
`GenerationContext` build order (`global → local → relief → biome`), `ReliefProvider` reads
`LocalRiverProvider`'s carved elevation (`localRiverProvider().getCarvedElev(...)`) to fill relief
channel 0. If the decoder-slice decode itself lived as instance state on `ReliefProvider`, and
`LocalRiverProvider` needed that same decode (it does, for tracing and carving), `LocalRiverProvider`
would have to depend back on `ReliefProvider` — creating a two-way instance dependency between the two
providers on top of the one-way `relief → local riverPrimitive` data dependency the build order already has.
Factoring the decode into a stateless static helper both providers call directly sidesteps that: neither
provider instance depends on the other's instance, only on the shared pipeline and (for `ReliefProvider`)
on `LocalRiverProvider`'s carved-elevation output.
