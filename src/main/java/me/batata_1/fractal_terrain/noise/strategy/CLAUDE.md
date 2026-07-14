# strategy/

Per-noise-type strategies dispatched by `noise/FastNoiseLite` (M-012).

## Files

| File                             | What                                              | When to read                          |
| -------------------------------- | ------------------------------------------------- | ------------------------------------- |
| `PerlinStrategy.java`            | Perlin noise                                      | Perlin sampling                       |
| `OpenSimplex2Strategy.java`      | OpenSimplex2 (fast) noise                         | OpenSimplex2 sampling                 |
| `OpenSimplex2SStrategy.java`     | OpenSimplex2S (smooth) noise                      | OpenSimplex2S sampling                |
| `CellularStrategy.java`          | Cellular/Worley noise                             | Cellular sampling                     |
| `ValueStrategy.java`             | Value noise                                       | Value sampling                        |
| `ValueCubicStrategy.java`        | Cubic-interpolated value noise                    | Value-cubic sampling                  |
| `BasicGridWarpStrategy.java`     | Grid-based domain warp                            | Domain warping                        |
| `SimplexGradientWarpStrategy.java`| Simplex gradient domain warp                     | Gradient domain warping               |
| `NoiseTables.java`               | Shared gradient/lookup tables                     | Lookup constants for strategies       |
