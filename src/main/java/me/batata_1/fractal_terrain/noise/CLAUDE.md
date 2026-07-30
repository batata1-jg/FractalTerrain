# noise/

Noise samplers: `FastNoiseLite` dispatcher over per-type strategies, plus `PortableRng` matching the Python inference RNG.

## Files

| File                            | What                                                                   | When to read                                    |
| ------------------------------- | ---------------------------------------------------------------------- | ----------------------------------------------- |
| `FastNoiseLite.java`            | Dispatcher over noise-type strategies in `strategy/`                   | Selecting a noise type, adding a strategy        |
| `NoiseSampler.java`             | Abstract sampler base with seeded init registry                        | Adding a sampler, seed initialization           |
| `OctaveSimplexNoiseSampler.java`| Multi-octave simplex noise                                             | Fractal simplex noise                           |
| `VoronoiNoiseSampler.java`      | Voronoi/cellular noise (SHA-256 hashed)                                | Cellular noise, erosion filter input            |
| `PortableRng.java`              | PCG64 + Marsaglia-polar RNG matching Python `portable_rng`             | Tile seeds/noise identical to Python            |

## Subdirectories

| Directory   | What                                                       | When to read                                  |
| ----------- | ---------------------------------------------------------- | --------------------------------------------- |
| `strategy/` | Per-noise-type implementations behind `FastNoiseLite`      | Implementing/editing a noise algorithm         |
| `filters/`  | Noise post-filters (`Filter`, `ErosionFilter` — stub)      | Adding a noise filter                          |
