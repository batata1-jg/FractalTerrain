# mixin/

## Overview

SpongePowered mixins and accessors that hook vanilla worldgen (surface material rules, feature
placement, a Terrablender marker). All four classes here are instantiated by the Mixin/Fabric framework,
not by mod code.

## Architecture

Every mixin resolves the per-world provider graph through `FractalTerrainInstance`'s static adapter
(e.g. `FractalTerrainInstance.exists()` / `getHeightmapCache()`), not through a constructor-injected
`GenerationContext`. `SteepSlopePredicateMixin` and `PlacedFeatureMixin` both guard their injected logic
with `FractalTerrainInstance.exists()` before touching the adapter, since Mixin can construct/invoke them
before a world (and its `GenerationContext`) has finished loading.

## Design Decisions

**Why mixins reach through the static adapter instead of taking a constructor-injected
`GenerationContext`.** Mixin-instantiated types have no constructor the mod controls — Mixin/Fabric owns
their lifecycle and instantiates them by its own contract, so there is no injection point for the mod to
hand them a `GenerationContext` reference the way mod-constructed classes (providers, `PopulateNoiseStep`,
etc.) can take one directly. The broader "caller migration" effort moving mod-constructed classes off the
static adapter and onto direct `GenerationContext` references treats this directory as a permanent
exception: mixins (along with other Fabric-instantiated types like `FractalTerrainBiomeSource` and
`FractalTerrainChunkGenerator`) are expected to keep resolving through `FractalTerrainInstance`
indefinitely. Full removal of the static-getter reach-through is scoped only to the remaining
mod-constructed callers, not to this directory.

The `exists()` guard each mixin uses is safe against a mid-load or mid-reload world because
`FractalTerrainInstance` publishes its `GenerationContext` behind a single `volatile
CompletableFuture<GenerationContext>`, completed only after the constructor has fully run — so a mixin
callback on a worker thread never observes a partially-constructed context, only "not yet published" (in
which case `exists()` is false) or "fully published."
