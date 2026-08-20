# relief/

## Overview

Builds the final per-tile relief tensor (`[RELIEF_CHANNELS=7, 512, 512]`) from a weight-normalized decode
of the diffusion decoder's residual channels — elevation included — plus a Difference-of-Gaussians
high-frequency channel. `hydrology.LocalRiverProvider` decodes the same residual separately to trace and
carve rivers, but publishes only its primitive index, not an elevation tensor; the two providers never
share an elevation buffer.

## Architecture

`DecoderChannels.decode` is a stateless static helper — not an instance method on either provider — that
fetches a haloed decoder slice from the shared `WorldPipeline` and weight-normalizes it into
`BASE_CHANNELS` channels. Both `ReliefProvider` (for the relief channels and the residual DoG) and
`hydrology.LocalRiverProvider` (for the elevation/gradients it traces and carves) call this same helper
directly.

## Design Decisions

**Why the decode is a stateless shared helper instead of living on one provider.** Both `ReliefProvider`
and `LocalRiverProvider` need the same decoded decoder slice — `ReliefProvider` to fill its own relief
channels, `LocalRiverProvider` to trace and carve off its own copy — but neither provider's output feeds
the other's input, so there is no data dependency between the two providers to hang the decode on. If the
decode lived as instance state on one provider, the other would have to reach through that provider's
instance just to obtain a slice it has no other reason to depend on, creating an artificial two-way
instance dependency. Factoring the decode into a stateless static helper both providers call directly
sidesteps that: neither provider instance depends on the other's instance, only on the shared pipeline.
