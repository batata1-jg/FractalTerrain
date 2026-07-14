# world/

Biome classification and vanilla-worldgen integration: the biome source, chunk/surface generation, and
vegetation.

## Subdirectories

| Directory   | What                                                                 | When to read                                          |
| ----------- | ------------------------------------------------------------------- | ----------------------------------------------------- |
| `biome/`    | Climate→biome-parameter transform, `BiomeProvider`, param enums     | Biome classification, density-function wiring         |
| `gen/`      | Chunk generator, surface system, noise-population step              | Chunk fill, surface rules, elevation override         |
| `features/` | Feature-placement helpers (vegetation)                              | Vegetation density/placement                          |
