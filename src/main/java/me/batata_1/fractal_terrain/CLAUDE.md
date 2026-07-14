# fractal_terrain/

Primary source package: the mod entry point plus the per-world provider graph and its static adapter.
See the repo-root `ARCHITECTURE.md` for how the pipeline, providers, and coordinate frames fit together.

## Files

| File                       | What                                                                                          | When to read                                                                 |
| -------------------------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `FractalTerrain.java`      | `ModInitializer` entry point: registers biome source + chunk generator, server lifecycle hooks | Wiring mod startup, registering generators, adding lifecycle listeners        |
| `GenerationContext.java`   | Per-world provider graph wired in dependency order (`global → local → relief → biome`)          | Adding a provider, changing build order, understanding per-world lifetime     |
| `FractalTerrainInstance.java` | Thin static adapter over the current `GenerationContext`; owns JVM-lifetime `WorldPipeline`  | Resolving a provider from static code, caller migration (M-008), pipeline init |
| `FractalTerrainConfig.java`  | Facade `record` re-exporting `config/` constants under historical names                       | Reading a legacy config constant; prefer the owning `config/` class in new code |

## Subdirectories

| Directory         | What                                                              | When to read                                                    |
| ----------------- | ---------------------------------------------------------------- | --------------------------------------------------------------- |
| `config/`         | Config split by concern (tensor layout, debug, tuning, loader)   | Adding a constant/flag, changing channel counts or tuning laws  |
| `ml/`             | ONNX diffusion pipeline + model loading                          | Diffusion inference, model assets, pipeline stages              |
| `hydrology/`      | River network tracing, carving, and per-tile hydrology providers | River generation, drainage, meanders, carve profiles            |
| `relief/`         | Decoder-residual relief provider + rock strata                   | Terrain elevation decode, rock layering                         |
| `world/`          | Biome classification, biome source, chunk/surface generation     | Biome params, chunk fill, surface rules, vegetation             |
| `noise/`          | Noise samplers (FastNoiseLite dispatcher, simplex, voronoi, RNG) | Sampling noise, portable RNG matching Python, noise strategies  |
| `math/`           | Numeric helpers: blur, gradients, splines, vectors, spatial index | Image ops, contour/skeleton tracing, spatial queries            |
| `infinitetensor/` | Tiled infinite-tensor abstraction over `Storage`                 | Slicing/accumulating windowed tensors, frozen cache tensors     |
| `storage/`        | Tile cache + disk persistence, heightmap cache, tile keys        | Cache lookups, persistence format, freeze/publication boundary  |
| `mixin/`          | SpongePowered mixins into vanilla worldgen                       | Hooking surface rules, feature placement, terrablender          |
| `debug/`          | Logging facade + PNG/TIFF visualizers + manual `main()` harnesses | Logging setup, dumping debug imagery, running manual tests      |
| `registry/`       | Registry-key + settings scaffolding                              | Adding registry keys or settings (currently minimal stubs)      |
| `references/`     | Mod ID, identifier/translate helpers, screen-handler stub        | Building resource locations, translation keys                   |
| `terrablender/`   | Terrablender integration entry point (stub)                      | Terrablender biome region setup                                 |
