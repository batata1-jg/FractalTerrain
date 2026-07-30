# noise/

## Overview

Noise sampling used both for terrain/erosion features (`FastNoiseLite` and its per-type strategies) and
for reproducing Python-generated randomness bit-for-bit (`PortableRng`), so the JVM side of the pipeline
can regenerate exactly the same conditioning noise the Python inference/training side produces.

## Architecture

`FastNoiseLite` holds an `mNoiseType`/`mFractalType`/`mTransformType3D` enum selection and dispatches to
per-type logic via `switch` statements (single-point sample, fractal accumulation, domain-warp variants);
the per-noise-type implementations themselves live in `noise/strategy/` (`OpenSimplex2Strategy`,
`CellularStrategy`, `PerlinStrategy`, `ValueStrategy`, `ValueCubicStrategy`, the warp strategies, plus
lookup tables in `strategy/NoiseTables.java`).

`PortableRng` implements PCG64 (64-bit LCG + XSH-RR 64/32 output) and a Marsaglia-polar standard-normal
transform, matching `terrain_diffusion/inference/portable_rng.py` and `world_pipeline._tile_seed`
constant-for-constant. `ml/tensorProviders/GaussianNoisePatch` is the one caller today: it uses
`PortableRng` to generate deterministic tile-seeded Gaussian noise matching Python's
`world_pipeline.gaussian_noise_patch`.

## Invariants

**`PortableRng` must stay bit-exact with `terrain_diffusion/inference/portable_rng.py`.** The PCG64
multiplier/increment constants, the XSH-RR rotation, and the Marsaglia-polar formula are copied to match
the Python implementation exactly — not just approximate its distribution. `GaussianNoisePatch` depends
on this to regenerate the same conditioning noise the Python pipeline would produce for a given
`(seed, tile)` pair. Changing either side's algorithm without mirroring the change on the other silently
breaks that reproduction: the JVM and Python noise streams diverge with no compile-time or type-level
signal.
