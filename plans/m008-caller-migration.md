# M-008 caller migration checklist

Keystone M-008 introduced `GenerationContext` (the per-world provider graph) and reduced
`FractalTerrainInstance` to a thin static adapter that delegates its historical getters to the current
context. The adapter is deliberately retained so the reach-throughs below keep working unchanged; this
file is the **tracked owner** (plan acceptance qa-013 / DL-007) for migrating each caller from
`FractalTerrainInstance.getX()` to holding an injected `GenerationContext` and calling `ctx.getX()`.

Only when every row is migrated may the static getters (and `getInstance`-style access) be removed. The
shared `WorldPipeline` (`FractalTerrainInstance.pipeline`) is JVM-lifetime, not per-world, and is **not**
part of this migration — those static-import consumers stay as they are for now.

## Provider-graph getter reach-throughs (migrate these)

| # | Caller file | Getters used | Status |
| - | ----------- | ------------ | ------ |
| 1 | `debug/Debug.java` | getServer | not migrated |
| 2 | `debug/Infinite3DVisualizer.java` | getReliefProvider, getInfinite3DVisualizer, getBiomeProvider, getHydrologyPainter, getGlobalRiverProvider | not migrated |
| 3 | `FractalTerrain.java` | init, close, dumpDebugStages, getHeightmapCache | not migrated (lifecycle entry point — keeps static init/close) |
| 4 | `hydrology/LocalRiverProvider.java` | getGlobalRiverProvider (fallback when no override) | not migrated |
| 5 | `mixin/SteepSlopePredicateMixin.java` | exists, getHeightmapCache | not migrated (mixin — no ctor injection seam) |
| 6 | `world/gen/surfacebuilder/FractalTerrainSurfaceSystem.java` | getHeightmapCache | not migrated |
| 7 | `hydrology/profile/HydrologyProfileCarver.java` | getReliefProvider | not migrated |
| 8 | `mixin/PlacedFeatureMixin.java` | exists | not migrated (mixin) |
| 9 | `world/gen/populatenoise/PopulateNoiseStep.java` | getHydrologyCarver | not migrated |
| 10 | `storage/FractalTerrainHeightmapCache.java` | getPopulateNoiseStep | not migrated |
| 11 | `storage/FractalTerrainHeightmap.java` | getBiomeProvider, getReliefProvider | not migrated |
| 12 | `world/biome/BiomeProvider.java` | getReliefProvider, getLocalRiverProvider, getBiomeProvider | not migrated |
| 13 | `relief/ReliefProvider.java` | getLocalRiverProvider (fallback when no override) | not migrated |
| 14 | `world/biome/source/FractalTerrainBiomeSource.java` | getBiomeProvider | not migrated |
| 15 | `world/gen/chunk/FractalTerrainChunkGenerator.java` | getPopulateNoiseStep, getHeightmapCache, getInfinite3DVisualizer, getNoiseConfig, getSurfaceBuilder | not migrated |

Notes:
- Mixins (#5, #8) and Fabric-instantiated types have no constructor the mod controls, so they will likely
  keep resolving the context through the adapter (or a passed-in accessor) rather than field injection —
  the adapter may therefore remain permanently for those, and full static-getter removal is scoped to the
  mod-constructed providers (#2, #4, #6, #7, #9–#15).
- `LocalRiverProvider`/`ReliefProvider` already accept an override provider for tests; extend that seam to
  take the context so the production fallback no longer reaches the static adapter.
