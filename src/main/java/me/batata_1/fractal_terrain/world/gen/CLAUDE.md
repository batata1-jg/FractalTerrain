# gen/

Vanilla worldgen integration: chunk generation, surface rules, and the noise-population elevation step.
These are built after the provider graph and read it via `FractalTerrainInstance`/`GenerationContext`.

## Subdirectories

| Directory        | What                                                          | When to read                                  |
| ---------------- | ------------------------------------------------------------ | --------------------------------------------- |
| `chunk/`         | `ChunkGenerator` + noise sampler                             | Chunk fill, block placement, codec            |
| `surfacebuilder/`| `FractalTerrainSurfaceSystem` (surface-rule override)         | Surface blocks, material rules                 |
| `populatenoise/` | `PopulateNoiseStep` (second-pass elevation override)          | Elevation override from heightmaps             |
